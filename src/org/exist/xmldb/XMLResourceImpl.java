/*
 * XMLResourceImpl.java - Aug 4, 2003
 * 
 * @author wolf
 */
package org.exist.xmldb;

import java.util.Date;

import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * Extends org.xmldb.api.modules.XMLResource with eXist specific extensions.
 */
public interface XMLResourceImpl extends XMLResource {

	Date getCreationTime() throws XMLDBException;
	
	Date getLastModificationTime() throws XMLDBException;
}
