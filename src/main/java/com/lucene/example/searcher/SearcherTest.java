package com.lucene.example.searcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TrackingIndexWriter;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.lucene.example.util.TestUtil;

public class SearcherTest {
	
	private ControlledRealTimeReopenThread<IndexSearcher> tCRTRThread;

	@Before 
	public void init() throws IOException{
		TestUtil.index();//���������Ĳ���
	}
	
	@Test
	public void testTerm() throws IOException{
		Directory directory = TestUtil.getIndexDirectory();
		IndexReader reader = DirectoryReader.open(directory);
		IndexSearcher searcher = new IndexSearcher(reader);
		
		Term term = new Term("subject","ant");
		Query query = new TermQuery(term);
//		query = new WildcardQuery(term);
		TopDocs docs = searcher.search(query, 20);
		for(ScoreDoc scoreDoc :docs.scoreDocs){
			Document doc = searcher.doc(scoreDoc.doc);
			System.out.println(doc.get("subject") + "," + doc.get("isbn"));
		}
		assertEquals("Ant in Action",1,docs.totalHits);
		term = new Term("subject","junit");
		query = new TermQuery(term);
		docs = searcher.search(query, 20);
		assertEquals(2, docs.totalHits);
		reader.close();
		directory.close();
	}
	
	@Test
	public void testQueryParser() throws IOException, ParseException{
		Directory directory = TestUtil.getIndexDirectory();
		IndexReader reader = DirectoryReader.open(directory);
		IndexSearcher searcher = new IndexSearcher(reader);
		
		Analyzer analyzer = new SimpleAnalyzer();
		//Ĭ�϶�subject���field���������
		QueryParser parser = new QueryParser("subject",analyzer);
		//���ö�title field�� ��������
		Query query = parser.parse("java*");
		TopDocs topDocs = searcher.search(query, 20);
		for(ScoreDoc scoreDoc :topDocs.scoreDocs){
			Document doc = searcher.doc(scoreDoc.doc);
			System.out.println(doc.get("title"));
		}
		reader.close();
		directory.close();
	}
	
	/**
	 * ʹ���߳̽��м��writer�ı仯 
	 * @throws IOException
	 * @throws InterruptedException 
	 */
	@Test
	public void testNearRealTimeOne() throws IOException, InterruptedException{
		Directory dir = new RAMDirectory();
		IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()));
		//IndexWriter�Ĺ켣��¼
		TrackingIndexWriter trackWriter = new TrackingIndexWriter(writer);
		//������������ SearcherFactory ������ȡIndexSearcher 
		SearcherManager searcherManager = new SearcherManager(writer,new SearcherFactory());
		tCRTRThread = new ControlledRealTimeReopenThread<IndexSearcher>(trackWriter, searcherManager, 5.0, 0);
		tCRTRThread.setDaemon(true);//���ú�̨����
		tCRTRThread.setName("��̨ˢ�·���");
		tCRTRThread.start();//�߳�����
		for(int i = 0; i<10; i++){
			Document doc = new Document();
			doc.add(new Field("id",i+"",TextField.TYPE_STORED));
			doc.add(new Field("text","aaa",TextField.TYPE_STORED));
			trackWriter.addDocument(doc);
		}
		//ˢ�²������Ա��ȡ��searcher��reader�����µ�  ����ˢ��ǰ�������ĸı���ύ  ����ʹ��Thread.sleep����
		searcherManager.maybeRefresh();
		IndexSearcher searcher = searcherManager.acquire();
		Query query = new TermQuery(new Term("text","aaa"));
		TopDocs topDocs = searcher.search(query, 10);
		assertEquals(10, topDocs.totalHits);
		//��ȡIndexSearcher��ǰʵ����ÿ����һ�α�����ö�Ӧ��release
		searcherManager.release(searcher);
		trackWriter.deleteDocuments(new Term("id","5"));//ɾ����5���ĵ�
		searcherManager.maybeRefresh();
		searcher = searcherManager.acquire();
		topDocs = searcher.search(query, 10);
		assertEquals(9, topDocs.totalHits);
		searcherManager.release(searcher);//�ͷ�
		writer.close();//�ر�
		dir.close();//�ر�
	}
	
	/**
	 * ʹ��DirectoryReader.openIfChanged��������ʵ��
	 * @throws IOException
	 */
	@Test
	public void testNearRealTimeTwo() throws IOException{
		Directory dir = new RAMDirectory();
		IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()));
		for(int i = 0; i<10; i++){
			Document doc = new Document();
			doc.add(new Field("id",i+"",TextField.TYPE_STORED));
			doc.add(new Field("text","aaa",TextField.TYPE_STORED));
			writer.addDocument(doc);
		}
		IndexReader reader = DirectoryReader.open(writer);
		IndexSearcher searcher = new IndexSearcher(reader);
		Query query = new TermQuery(new Term("text","aaa"));
		TopDocs topDocs = searcher.search(query, 10);
		assertEquals(10, topDocs.totalHits);
		writer.deleteDocuments(new Term("id","5"));//ɾ����5���ĵ�
		Document doc = new Document();
		doc.add(new Field("id","11",TextField.TYPE_STORED));
		doc.add(new TextField("text","bbb",Store.YES)); //text���ݲ�ͬ
		writer.addDocument(doc);//���һ�����ĵ�
		//���´�һ��reader �����������ı�ʱ������һ���µ�reader,���򷵻�null
		IndexReader newReader = DirectoryReader.openIfChanged((DirectoryReader)reader);
		reader.close();//�ر�֮ǰ��reader
		assertFalse(newReader == reader);
		searcher = new IndexSearcher(newReader);//���¹���������
		topDocs = searcher.search(query, 10);
		assertEquals(9, topDocs.totalHits);
		query = new TermQuery(new Term("text","bbb"));
		topDocs = searcher.search(query, 10);
		assertEquals(1, topDocs.totalHits);
		//�ر���������
		writer.close();
	}
	
	/**
	 * Lucene�����ֻ���
	 * @throws IOException 
	 * @throws ParseException 
	 */
	@Test
	public void testExplain() throws IOException, ParseException{
		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(TestUtil.getIndexDirectory()));
		QueryParser parser = new QueryParser("content", new SimpleAnalyzer());
		Query query = parser.parse("junit");
		TopDocs topDocs = searcher.search(query, 10);
		for(ScoreDoc scoreDoc:topDocs.scoreDocs){
			Explanation explan = searcher.explain(query, scoreDoc.doc);
			System.out.println("----------------");
			System.out.println(explan);
		}
	}
	
	@Test
	public void testTermQuery() throws IOException{
		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(TestUtil.getIndexDirectory()));
		//��title�а���action������ �����ִ�Сд
		Term term = new Term("title","��");
		Query query = new TermQuery(term);
		TopDocs topDocs = searcher.search(query, 10);
		for(ScoreDoc scoreDoc:topDocs.scoreDocs){
			System.out.println(searcher.doc(scoreDoc.doc).get("title"));
		}
	}
	
	@Test
	public void testTermRangeQuery() throws IOException{
		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(TestUtil.getIndexDirectory()));
		// new TermRangeQuery(field, lowerTerm, upperTerm, includeLower, includeUpper);
		Query query = new TermRangeQuery("title2",new BytesRef("d".getBytes()) , new BytesRef("j".getBytes()), false, true);
		TopDocs topDocs = searcher.search(query, 10);
		System.out.println(query);
		for(ScoreDoc scoreDoc:topDocs.scoreDocs){
			System.out.println(searcher.doc(scoreDoc.doc).get("title2"));
		}
	}
	
	/**
	 * ������ֵ���͵ķ�Χ���� ��LongPointΪ��
	 * @throws IOException
	 */
	@Test
	public void testNumericRangeQuery() throws IOException{
		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(TestUtil.getIndexDirectory()));
		Query query = LongPoint.newRangeQuery("pubmonth",200403,201611);
		TopDocs topDocs = searcher.search(query, 10);
		System.out.println(query);
		for(ScoreDoc scoreDoc:topDocs.scoreDocs){
			//ʹ�ô˷������Ի�ȡ��ֵ���е�����
			String[] author = searcher.doc(scoreDoc.doc).getValues("author");
			System.out.println(Arrays.toString(author));
			System.out.println(searcher.doc(scoreDoc.doc).get("title"));
		}
	}
	
	/**
	 * ǰ׺��ѯ ʹ�ô� ������������WhitespaceAnalyzer ����/�޷�ʶ��
	 * @throws IOException
	 */
	@Test
	public void testPrefixQuery() throws IOException{
		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(TestUtil.getIndexDirectory()));
		Term term = new Term("category","/technology/computers/programming");
		Query query = new PrefixQuery(term);
		TopDocs topDocs = searcher.search(query, 10);
		int prefixCount = topDocs.totalHits;
		///technology/computers/programming/education ����Ϊһ���ʻ���ֵ� ���Բ�ѯ����
		topDocs = searcher.search(new TermQuery(term), 10);
		int termQueryCount = topDocs.totalHits;
		assertTrue(prefixCount > termQueryCount);
	}
	
	/**
	 * Boolean���߼��Ͳ���
	 * @throws IOException
	 */
	@Test
	public void testBooleanAnd() throws IOException{
		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(TestUtil.getIndexDirectory()));
		Query query1 = new TermQuery(new Term("subject","search"));
		BooleanClause clause1 = new BooleanClause(query1, Occur.MUST);
		Query query2 = LongPoint.newRangeQuery("pubmonth", 200403,200811);
		BooleanClause clause2 = new BooleanClause(query2,Occur.MUST);
		//booleanQuery������
		Builder builder = new BooleanQuery.Builder();
		builder.add(clause1).add(clause2);
		Query query3 = builder.build();
		TopDocs topDocs = searcher.search(query3, 10);
		for(ScoreDoc scoreDoc:topDocs.scoreDocs){
			Document doc = searcher.doc(scoreDoc.doc);
			System.out.println("subject:" + doc.get("subject") + ",pubmonth:" + doc.get("pubmonth"));
		}
	}
	
	/**
	 * �����ѯ
	 * @throws IOException 
	 */
	@Test
	public void testPhraseQuery() throws IOException{
		Directory dir = new RAMDirectory();
		IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new WhitespaceAnalyzer()));
		Document doc = new Document();
		doc.add(new Field("field","the quick brown fox jumped over the lazy dog",TextField.TYPE_STORED));
		writer.addDocument(doc);
		writer.forceMerge(1);
		writer.close();
		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(dir));
		//Ĭ������£�slop����Ϊ0,����ʾ������ȫƥ��
		Query query = new PhraseQuery(1,"field", "quick","fox");
		TopDocs topDocs = searcher.search(query, 1);
		assertEquals(1, topDocs.totalHits);
	}
	
	/**
	 * ������ѯ
	 * @throws IOException 
	 */
	@Test
	public void testMultiPhraseQuery() throws IOException{
		Directory dir = new RAMDirectory();
		IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new WhitespaceAnalyzer()));
		Document doc = new Document();
		doc.add(new Field("field","the quick brown fox jumped over the lazy dog",TextField.TYPE_STORED));
		writer.addDocument(doc);
		writer.forceMerge(1);
		writer.close();
		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(dir));
		org.apache.lucene.search.MultiPhraseQuery.Builder builder = new MultiPhraseQuery.Builder();
		builder.setSlop(1);
		builder.add(new Term("field","quick"));//ǰ׺
		builder.add(new Term[]{new Term("field","fox"),new Term("field","dog")});
		Query query = builder.build();
		System.out.println(query.toString());
		TopDocs topDocs = searcher.search(query, 1);
		assertEquals(1, topDocs.totalHits);
	}
	
	
	
	/**
	 * ͨ����Ĳ�ѯ
	 * @throws IOException
	 */
	@Test
	public void testWildcardQuery() throws IOException{
		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(TestUtil.getIndexDirectory()));
		Term term = new Term("subject","*agile*");
		Query query = new WildcardQuery(term);
		TopDocs topDocs = searcher.search(query, 10);
		for(ScoreDoc scoreDoc:topDocs.scoreDocs){
			Document doc = searcher.doc(scoreDoc.doc);
			System.out.println("subject:" + doc.get("subject") );
		}
	}
	
	@Test
	public void testFuzzyQuery() throws IOException{
		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(TestUtil.getIndexDirectory()));
		Term term = new Term("subject","jaba");
		Query query = new FuzzyQuery(term);
		TopDocs topDocs = searcher.search(query, 10);
		for(ScoreDoc scoreDoc:topDocs.scoreDocs){
			Document doc = searcher.doc(scoreDoc.doc);
			System.out.println("subject:" + doc.get("subject") );
		}
	}
	
	@After
	public void after(){
		TestUtil.deleteIndexFiles();
	}
	
}
