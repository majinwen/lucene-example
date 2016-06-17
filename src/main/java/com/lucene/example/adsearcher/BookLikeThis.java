package com.lucene.example.adsearcher;

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;

import com.lucene.example.util.TestUtil;

public class BookLikeThis {
	
	private IndexReader reader;
	
	private IndexSearcher searcher;
	
	private BookLikeThis(IndexReader reader){
		this.reader = reader;
		this.searcher = new IndexSearcher(reader);
	}
	
	/**
	 * ���������鼮
	 * @param id
	 * @param max
	 * @return
	 * @throws IOException
	 */
	public Document[] docsLike(int id,int max) throws IOException{
		Document[] results = null;
		Document source = reader.document(id);
		String[] authors = source.getValues("author");
		Builder builder = new BooleanQuery.Builder();
		//�ų���ǰ�鼮 �˴���isbn����Сд ��Ϊ�ִ���������Сд�Ĳ��� �����е��鼮�����޷��ų�
		builder.add(new TermQuery(new 
				Term("isbn",source.get("isbn").toLowerCase())),
				Occur.MUST_NOT);
		for(String author:authors){
			builder.add(new TermQuery(new Term("author",author)),Occur.SHOULD);
		}
		//��ȡsubject�еķִ�
		Terms terms =  MultiFields.getTerms(reader, "subject");
		TermsEnum termsEnum = terms.iterator();
		BytesRef term = null;
		while((term = termsEnum.next()) != null){
			//ת����string
			builder.add(new TermQuery(new Term("subject",term.utf8ToString())),Occur.SHOULD);
		}
		BooleanQuery query = builder.build();
		TopDocs topDocs = searcher.search(query, max);
		results = new Document[topDocs.totalHits];
		for(int i =0; i<results.length;i++){
			results[i] = searcher.doc(topDocs.scoreDocs[i].doc);
		}
		return results;
	}
	
	public static void main(String[] args) throws IOException {
		TestUtil.index();
		IndexReader reader = DirectoryReader.open(TestUtil.getIndexDirectory());
		BookLikeThis blt = new BookLikeThis(reader) ;
		int max = reader.maxDoc();
		for(int i = 0; i<max;i++){
			System.out.println("======================");
			Document doc = reader.document(i);
			System.out.println(doc.get("title") + ",isbn:" + doc.get("isbn") );
			Document[] docs = blt.docsLike(i, max);
			for(Document likeThis:docs){
				System.out.println("---> " + likeThis.get("title") + ",isbn:" + likeThis.get("isbn"));
			}
		}
		TestUtil.deleteIndexFiles();
	}
}
