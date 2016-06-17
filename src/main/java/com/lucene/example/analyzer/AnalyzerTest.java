package com.lucene.example.analyzer;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringReader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Test;

public class AnalyzerTest {

	
	/**
	 * ��ͬ�������ķ����Ĳ�ͬ���
	 * @throws IOException
	 */
	@Test
	public void testAnalyzerTokens() throws IOException{
		String[] examples = {"The quick brown fox jumped over the lazy dog",
					"XY&Z Corporation - xyz@example.com"};
		Analyzer[] analyzers = new Analyzer[]{
				new WhitespaceAnalyzer(),new SimpleAnalyzer(),
				new StopAnalyzer(),new StandardAnalyzer()};
		for(Analyzer analyzer :analyzers){
			System.out.println("========" + analyzer.getClass().getName()+ "=====");
			for(String text:examples){
				TokenStream tokenStream = analyzer.tokenStream("contents", new StringReader(text));
				CharTermAttribute token = tokenStream.addAttribute(CharTermAttribute.class);
				tokenStream.reset();//���� IllegalStateException
				while(tokenStream.incrementToken()){//�Ƿ��к��������
					System.out.print("[" + token.toString() + "]");
				}
				tokenStream.close();//����رգ��´�ѭ�����´�
				System.out.println();
			}
		}
	}
	
	/**
	 * �����������������㵥Ԫ�����ԣ��ƫ���������͡�λ������
	 * @throws IOException 
	 */
	@Test
	public void testTokensWithFullDetails() throws IOException{
		String[] examples = {"The quick brown fox jumped over the lazy dog",
		"XY&Z Corporation - xyz@example.com"};
		Analyzer[] analyzers = new Analyzer[]{
			new WhitespaceAnalyzer(),new SimpleAnalyzer(),
			new StopAnalyzer(),new StandardAnalyzer()};
		for(Analyzer analyzer:analyzers){
			System.out.println("========" + analyzer.getClass().getName()+ "=====");
			for(String text:examples){
				System.out.println("==============================");
				TokenStream tokenStream = analyzer.tokenStream("content", new StringReader(text));
				//��
				CharTermAttribute term = tokenStream.addAttribute(CharTermAttribute.class);
				//λ������
				PositionIncrementAttribute position = tokenStream.addAttribute(PositionIncrementAttribute.class);
				//����
				TypeAttribute type = tokenStream.addAttribute(TypeAttribute.class);
				//ƫ����
				OffsetAttribute offset = tokenStream.addAttribute(OffsetAttribute.class);
				tokenStream.reset();
				int posi = 0;
				while(tokenStream.incrementToken()){
					//����λ�õ�������Ĭ��Ϊ1����ʼλ�ô�������ʼ��ÿ����������������
					position.setPositionIncrement(2);
					int increament = position.getPositionIncrement();
					posi += increament;
					System.out.print("[��:" + term.toString());
					System.out.print(",λ��:" + posi);
					System.out.print(",����:" + type.type());
					System.out.print(",ƫ������ʼ:" + offset.startOffset());
					System.out.println(",ƫ��������:" + offset.endOffset() + "]");
				}
				tokenStream.close();
			}
		}
	}
	
	/**
	 * ʹ��Metaphone ��ɽ����ʵ������� Metaphoneͬ���㷨
	 * @throws IOException
	 * @throws ParseException
	 */
	@Test
	public void testMetaphoneAnalyzer() throws IOException, ParseException{
		Directory directory = new RAMDirectory();
		Analyzer analyzer = new MetaphoneReplacementAnalyzer();
		IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
		Document doc = new Document();
		doc.add(new TextField("content","cool cat",Store.YES));
		writer.addDocument(doc);
		writer.close();
		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(directory));
		Query query = new QueryParser("content", analyzer).parse("kool kat");
		TopDocs topDoc = searcher.search(query, 1);
		assertEquals(1,topDoc.totalHits);//��ȷ��ѯ��
		directory.close();
		//���´ʻ�Ĺ���
		TokenStream stream = analyzer.tokenStream("content", new StringReader("cool cat"));
		CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
		TypeAttribute type = stream.addAttribute(TypeAttribute.class);
		stream.reset();
		while(stream.incrementToken()){
			System.out.print("[" + term.toString() + "," + type.type() + "]");
		}
		stream.close();
	}
	
	/**
	 * ͬ��ʵĲ���
	 * @throws IOException
	 * @throws ParseException 
	 */
	@Test
	public void testSynonymAnalyzer() throws IOException, ParseException{
		Directory directory = new RAMDirectory();
		SynonymEngine engine = new TestSynonymEngineImpl();
		Analyzer analyzer = new SynonymAnalyzer(engine);
		IndexWriter writer = new IndexWriter(directory,new IndexWriterConfig(analyzer));
		Document document = new Document();
		document.add(new TextField("content","The quick brown fox jumps over the lazy dog",Store.YES));
		writer.addDocument(document);
		writer.close();
		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(directory));
		Query query = new TermQuery(new Term("content","fast"));
		//����Ĳ��� query:  content:"fox (jumps hops leaps)"
		query = new QueryParser("content",analyzer).parse("\"fox jumps\"");
		System.out.println(query.toString());
		TopDocs topDocs = searcher.search(query, 1);
		for(ScoreDoc scoreDoc:topDocs.scoreDocs){
			System.out.println(searcher.doc(scoreDoc.doc));
		}
		directory.close();
		//���´ʻ�Ĺ���
		TokenStream stream = analyzer.tokenStream("content", new StringReader("The quick brown fox jumps over the lazy dog"));
		CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
		PositionIncrementAttribute positionIncrement = stream.addAttribute(PositionIncrementAttribute.class);
		stream.reset();
		int position = 0;
		while(stream.incrementToken()){
			position += positionIncrement.getPositionIncrement();
			System.out.print("[" + term.toString() + "," + position + "]");
		}
		stream.close();
	}
	
	/**
	 * �ʸ���ȡ��ͣ�ô�
	 * @throws IOException
	 * @throws ParseException
	 */
	@Test
	public void testPositionalPorterStopAnalyzer() throws IOException, ParseException{
		Directory directory = new RAMDirectory();
		Analyzer analyzer = new PositionalPorterStopAnalyzer();
		IndexWriter writer = new IndexWriter(directory,new IndexWriterConfig(analyzer));
		Document document = new Document();
		document.add(new TextField("content","The quickly brown fox jumps over the lazy dog",Store.YES));
		writer.addDocument(document);
		writer.close();
		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(directory));
		//����Ĳ��� query:  laziness �����ɴʸ� lazi
		Query query = new QueryParser("content",analyzer).parse("laziness");
		System.out.println(query.toString());
		TopDocs topDocs = searcher.search(query, 1);
		for(ScoreDoc scoreDoc:topDocs.scoreDocs){
			System.out.println(searcher.doc(scoreDoc.doc));
		}
		directory.close();
		//���´ʻ�Ĺ���
		TokenStream stream = analyzer.tokenStream("content", new StringReader("The quick brown fox jumps over the lazy dog"));
		CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
		PositionIncrementAttribute positionIncrement = stream.addAttribute(PositionIncrementAttribute.class);
		stream.reset();
		int position = 0;
		while(stream.incrementToken()){
			position += positionIncrement.getPositionIncrement();
			System.out.print("[" + term.toString() + "," + position + "]");
		}
		stream.close();
	}
}
