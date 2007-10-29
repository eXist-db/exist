package org.exist.storage.analysis;


public interface Tokenizer {
	
	public void setText(CharSequence text);
    public void setText(CharSequence text, int offset);
    public TextToken nextToken();
	public TextToken nextToken(boolean allowWildcards);
	public void setStemming(boolean stem);
}
