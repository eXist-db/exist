package org.exist.storage.analysis;


public interface Tokenizer {
	
	public void setText(CharSequence text);
	public TextToken nextToken();
	public TextToken nextToken(boolean allowWildcards);
	public void setStemming(boolean stem);
}
