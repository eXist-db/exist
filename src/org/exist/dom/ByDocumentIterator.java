package org.exist.dom;

public interface ByDocumentIterator {

	public void nextDocument(DocumentImpl document);
	
	public boolean hasNextNode();
	
	public NodeProxy nextNode();
}
