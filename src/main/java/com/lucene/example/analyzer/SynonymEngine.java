package com.lucene.example.analyzer;

/**
 * ��ȡͬ��ʵĽӿ�
 * @author qiaolin
 *
 */
public interface SynonymEngine {
	public String[] getSynonyms(String word);
}
