package com.lucene.example.indexing;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Before;
import org.junit.Test;

/**
 * ��������
 * @author qiaolin
 *
 */
public class IndexingTest{
	
	private Directory directory;
	
	protected String[] ids = {"1","2"};
	
	protected String[] unindexed = {"China ","USA "};
	
	protected String[] unstored = {"This is China content. ","This is USA content "};
	
	protected String[] text = {"BeiJing","LSA "};
	
	@Before
	public void init() throws Exception{
		directory = new RAMDirectory();//�ڴ�洢
//		directory = FSDirectory.open("d:index.txt");
		IndexWriter writer = getWriter();//����IndexWriter����
		for(int i = 0; i<ids.length; i++){
			Document doc = new Document();
			doc.add(new Field("id",ids[i],TextField.TYPE_STORED));
			doc.add(new Field("country",unindexed[i],TextField.TYPE_STORED));
			doc.add(new Field("contents",unstored[i],TextField.TYPE_STORED));
			doc.add(new Field("city",text[i],TextField.TYPE_STORED));
			writer.addDocument(doc);//���ĵ����뵽������
		}
		writer.close();
	}
	
	private IndexWriter getWriter() throws IOException{
		// Lucene 4.0֮�������
		Analyzer analyzer = new StandardAnalyzer();
		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		return new IndexWriter(directory,config);
	}
	
	/**
	 * 
	 * @param fieldName  ����������
	 * @param searchString ����������
	 * @return
	 * @throws IOException
	 */
	protected int getHitCount(String fieldName,String searchString) throws IOException{
		IndexReader ireader = DirectoryReader.open(directory); //����IndexReader����
		IndexSearcher searcher = new IndexSearcher(ireader); //����IndexSearcher����
		// Term�������ı���һ�����ʣ��������ĵ�Ԫ������������ɣ������ֵ
		Term term = new Term(fieldName,searchString);  
		Query query = new TermQuery(term);
		int hitCount = searcher.search(query, 1).totalHits;
		return hitCount;
	}

	@Test
	public void textIndexWriter() throws IOException{
		IndexWriter writer = getWriter();
		assertEquals(ids.length, writer.numDocs());//д���ĵ�
		writer.close();
	}
	
	@Test
	public void testHitCount() throws IOException{
		System.out.println(getHitCount("contents","usa"));
	}
	
	@Test
	public void testDeleteBeforeOptimize() throws IOException{
		IndexWriter writer = getWriter();
		assertEquals(ids.length, writer.numDocs());//�������ļ�����
		writer.deleteDocuments(new Term[]{new Term("id","1")});//ɾ��ID���ĵ�
		writer.commit();
		assertEquals(true, writer.hasDeletions());//ȷ����ɾ���ĵ��ı��
		assertEquals(2, writer.maxDoc());
		assertEquals(1, writer.numDocs()); // ɾ��һ���ĵ�
		writer.close();
	}
	
	@Test
	public void testDeleteAfterOptimize() throws IOException{
		IndexWriter writer = getWriter();
		writer.deleteDocuments(new Term("id","2"));
		writer.forceMerge(1);//ɾ����ϲ�����
		writer.commit();
		assertEquals(false, writer.hasDeletions());//û��ɾ���ĵ��ı��
		assertEquals(1, writer.maxDoc());
		assertEquals(1, writer.numDocs());
		writer.close();
	}
	
	@Test
	public void testUpdateIndex() throws IOException{
		IndexWriter writer = getWriter();
		Document doc = new Document();
		doc.add(new Field("id","2",TextField.TYPE_STORED));
		doc.add(new Field("country","UK",TextField.TYPE_STORED));
		doc.add(new Field("contents","This is a UK content ",TextField.TYPE_STORED));
		doc.add(new Field("city","Lodon",TextField.TYPE_STORED));
		writer.updateDocument(new Term("id","1"), doc);//���ĵ��滻
		writer.commit();
		writer.close();
		assertEquals(0, getHitCount("country", "china"));//�˴��������ֶ�ֻ��ΪСд
		assertEquals(1, getHitCount("country","uk"));//�˴��������ֶ�ֻ��ΪСд
		assertEquals(2, getHitCount("id","2"));
		
	}
}
