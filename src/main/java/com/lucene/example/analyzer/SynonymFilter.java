package com.lucene.example.analyzer;

import java.io.IOException;
import java.util.Stack;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.AttributeSource;

/**
 * ͬ��� ������ ʵ��
 * @author qiaolin
 *
 */
public class SynonymFilter extends TokenFilter{
	
	//ͬ��� ջ
	private Stack<String> synonymStack = null;
	// ��
	private CharTermAttribute charTerm ;
	//λ������
	private PositionIncrementAttribute  positionIncrement;
	//��ȡͬ��ʵĽӿ�
	private SynonymEngine engine ;
	//��ǰ״̬
	private AttributeSource.State current;
	protected SynonymFilter(TokenStream input,SynonymEngine engine) {
		super(input);
		this.engine = engine;
		synonymStack = new Stack<String>();
		charTerm = input.addAttribute(CharTermAttribute.class);
		positionIncrement = input.addAttribute(PositionIncrementAttribute.class);
	}

	@Override
	public boolean incrementToken() throws IOException {
		if(synonymStack.size() > 0){
			restoreState(current);
			charTerm.setEmpty();
			charTerm.append(synonymStack.pop());//���ͬ���
			positionIncrement.setPositionIncrement(0);//����ͬ���λ��
			return true;//����ط�Ҫ���أ�����ִʳ���
		}
		if(!input.incrementToken()){
			return false;
		}
		if(addSynonymToStack()){//���ͬ���
			current = captureState();
		}
		return true;
	}
	
	public boolean addSynonymToStack(){
		String[] synonyms = engine.getSynonyms(charTerm.toString());
		if(synonyms == null){
			return false;
		}
		for(String synonym:synonyms){
			synonymStack.push(synonym);//���ͬ���
		}
		return true;
	}

}
