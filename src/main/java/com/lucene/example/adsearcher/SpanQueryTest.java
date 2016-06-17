package com.lucene.example.adsearcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.spans.SpanFirstQuery;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanNotQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.SpanWeight.Postings;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Before;
import org.junit.Test;

public class SpanQueryTest {
	
	private Directory directory;
	private Analyzer analyzer;
	private IndexSearcher searcher;
	
	@Before
	public void init() throws IOException{
		directory = new RAMDirectory();
		analyzer = new WhitespaceAnalyzer();
		IndexWriter writer = new IndexWriter(directory,new IndexWriterConfig(analyzer));
		Document document = new Document();
		document.add(new TextField("content","the quick brown fox jumps over the lazy dog",Store.YES));
		writer.addDocument(document);
		writer.close();
		searcher = new IndexSearcher(DirectoryReader.open(directory));
	}
	
	/**
	 * SpanTermQuery��ѯ���� ����������㵥Ԫ����
	 * @throws IOException
	 */
	@Test
	public void testSpanTermQuery() throws IOException{
		SpanQuery query = new SpanTermQuery(new Term("content","the"));
		analyzerIndex(query);
	}
	
	/**
	 * ǰ�������в�ѯ��������
	 * @throws IOException
	 */
	@Test
	public void testSpanFirstQuery() throws IOException{
		//��Ϊ������λ������Ϊ1,������������,���������������ǰ3��λ�ÿ���������brown,�����2��λ��,���޷�����
		SpanQuery query = new SpanFirstQuery(new SpanTermQuery(
					new Term("content","brown")), 3);
		analyzerIndex(query);
	}
	
	/**
	 * ƥ�����Ŀ�ȷ�Χ�Ǵӵ�һ����ȵ���ʼλ�õ����һ����ȵĽ���λ�á�
	 * inOrder����Ϊtrue,��˳�򲻿ɵߵ�,���һ����ȼ�Ϊ���һ��SpanQuery��ѯ��
	 * @throws IOException
	 */
	@Test
	public void testSpanNearQuery() throws IOException{
		SpanQuery quick = new SpanTermQuery(new Term("content","quick"));
		SpanQuery brown = new SpanTermQuery(new Term("content","brown"));
		SpanQuery dog = new SpanTermQuery(new Term("content","dog"));
		//inOrder=true��ʾ��SpanQuery[]�е�˳��һ�£���ʱ����ߵ���SpanQuery�Ĳ���˳�����޷���ѯ quick,dog,brown 5 true
//		SpanQuery quick_brown_dog = new SpanNearQuery(new SpanQuery[]{quick,brown,dog}, 5, true);
		//=false��ʾ˳����Բ�һ�£���ʱ�Ϳ��Բ�ѯ��
		SpanQuery quick_brown_dog = new SpanNearQuery(new SpanQuery[]{quick,dog,brown}, 5, false);
		System.out.println(quick_brown_dog.toString());
		analyzerIndex(quick_brown_dog);
	}
	
	/**
	 * ������������ų��ص��Ŀ��
	 * @throws IOException
	 */
	@Test
	public void testSpanNotQuery() throws IOException{
		SpanQuery quick = new SpanTermQuery(new Term("content","quick"));
		SpanQuery fox = new SpanTermQuery(new Term("content","fox"));
		@SuppressWarnings("unused")
		SpanQuery dog = new SpanTermQuery(new Term("content","dog"));
		SpanQuery brown = new SpanTermQuery(new Term("content","brown"));
		SpanQuery spanQuery = new SpanNearQuery(new SpanQuery[]{quick,fox},1,true);
		//��һ����ѯ���� �ڶ���������ѯ �����ų��Ĳ�ѯ���ܵĲ�ѯ��  ��ѯ�ĵ�
//		SpanQuery query = new SpanNotQuery(spanQuery, dog);
		//���������ѯ��Ϊbrown��spanQuery�Ĳ�ѯ�����,�����ų��˲�ѯ��
		SpanQuery query = new SpanNotQuery(spanQuery, brown);
		IndexReader reader = searcher.getIndexReader();
		List<LeafReaderContext> contexts = reader.getContext().leaves();
		assert(contexts.size() == 1);
		SpanWeight weight = spanQuery.createWeight(searcher, true);
		Spans spans = weight.getSpans(contexts.get(0), Postings.POSITIONS);
		assertNotNull(spans);
		TopDocs topDocs = searcher.search(query, 10);
		assertEquals(0,topDocs.totalHits);//��������
		analyzerIndex(query);
	}
	
	/**
	 * SpanOrQuery
	 * @throws IOException
	 */
	@Test
	public void testSpanOrQuery() throws IOException{
		SpanQuery quick = new SpanTermQuery(new Term("content","quick"));
		SpanQuery fox = new SpanTermQuery(new Term("content","fox"));
		SpanQuery lazy = new SpanTermQuery(new Term("content","lazy"));
		SpanQuery dog = new SpanTermQuery(new Term("content","dog"));
		SpanQuery quick_fox = new SpanNearQuery(new SpanQuery[]{quick,fox}, 1, true);
		SpanQuery lazy_dog = new SpanNearQuery(new SpanQuery[]{lazy,dog}, 0, true);
		SpanQuery qf_near_ld = new SpanNearQuery(new SpanQuery[]{quick_fox,lazy_dog},3,true);
		System.out.println("ʹ��spanNearQueryģ��SpanOrQuery");
		analyzerIndex(qf_near_ld);
		//------SpanOrQuery
		SpanQuery spanOrQuery = new SpanOrQuery(quick_fox,lazy_dog);
		System.out.println("ʹ��SpanOrQuery");
		analyzerIndex(spanOrQuery);
	}
	
	/**
	 * ������㵥Ԫ����
	 * @param reader
	 * @param spans
	 * @param topDocs 
	 * @return
	 * @throws IOException
	 */
	private void analyzerIndex(SpanQuery query) throws IOException{
		IndexReader reader = searcher.getIndexReader();
		List<LeafReaderContext> contexts = reader.getContext().leaves();
		assert(contexts.size() == 1);
		SpanWeight weight = query.createWeight(searcher, true);
		Spans spans = weight.getSpans(contexts.get(0), Postings.POSITIONS);
		assertNotNull(spans);
		TopDocs topDocs = searcher.search(query, 10);
		float[] scores = new float[topDocs.scoreDocs.length];
		for(ScoreDoc scoreDoc:topDocs.scoreDocs){
			scores[scoreDoc.doc] = scoreDoc.score;
		}
		while(spans.nextDoc() != Spans.NO_MORE_DOCS){
			while(spans.nextStartPosition() != Spans.NO_MORE_POSITIONS){
				int i = 0,docID = -1;
				docID = spans.docID();
				Document doc = reader.document(docID);
				//������㵥Ԫ����
				TokenStream tokenStream = analyzer.tokenStream("content", 
							new StringReader(doc.get("content")));
				CharTermAttribute charTerm = tokenStream.addAttribute(CharTermAttribute.class);
				StringBuilder builder = new StringBuilder();
				tokenStream.reset();
				while(tokenStream.incrementToken()){
					if(i==spans.startPosition()){
						builder.append("<");
					}
					builder.append(charTerm.toString());
					if(++i ==spans.endPosition()){
						builder.append(">");
					}
					builder.append(" ");
				}
				tokenStream.close();
				if(topDocs.totalHits != 0){
					builder.append("(").append(scores[docID]).append(")");
				}
				System.out.println(builder.toString());
			}
		}
		
	}
}
