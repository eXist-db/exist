package org.exist.xmldb;

import org.exist.util.Occurrences;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;

public interface IndexQueryService extends Service {

    public void reindexCollection() throws XMLDBException;
    
	public Occurrences[] getIndexedElements(boolean inclusive) throws XMLDBException;
	
	public Occurrences[] scanIndexTerms(String start, String end, 
	boolean inclusive) throws XMLDBException;
}
	
