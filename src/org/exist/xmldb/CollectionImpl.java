/*
 * CollectionImpl.java - Aug 4, 2003
 * 
 * @author wolf
 */
package org.exist.xmldb;

import java.util.Date;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * This interface extends org.xmldb.api.base.Collection with extensions specific to eXist.
 */
public interface CollectionImpl extends Collection {

	/**
	 * Returns the time of creation of the collection.
	 * @return
	 */
	Date getCreationTime() throws XMLDBException;
	
}
