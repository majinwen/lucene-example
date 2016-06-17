package com.lucene.example.sort;

import java.io.IOException;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.lucene.example.util.TestUtil;

/**
 * ����������������
 * @author qiaolin
 *
 */
public class SortTest {
	
	@Before
	public void init() throws IOException{
		TestUtil.index();
	}
	
	@After
	public void delete() throws IOException{
		TestUtil.deleteIndexFiles();
	}
	
	/**
	 * �����������ֶ�
	 * @throws IOException
	 */
	@Test
	public void testFieldSort() throws IOException{
		Directory directory = TestUtil.getIndexDirectory();
		IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(directory));
		Query allBooks = new MatchAllDocsQuery();
		//���� ����Ҫ�������ֶα�����ʹ�ÿ������������ֶν��д洢������LongPoint�Ĵ洢
		TopFieldDocs topFieldDocs = searcher.search(allBooks, 10, 
				new Sort(new SortField("title", SortField.Type.STRING,false)));//true ���� false ����
//		TopFieldDocs topFieldDocs = searcher.search(allBooks,10,new Sort().INDEXORDER);
		ScoreDoc[] scoreDocs = topFieldDocs.scoreDocs;
		System.out.println(topFieldDocs.fields.length);
		for(int i = 0; i<scoreDocs.length;i++){
			System.out.println(searcher.doc(scoreDocs[i].doc).get("title"));
		}
		
	}
}
