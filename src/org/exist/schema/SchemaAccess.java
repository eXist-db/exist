/*
 * Created on May 25, 2004
 *
 */
package org.exist.schema;

import javax.xml.namespace.QName;

import org.exolab.castor.xml.schema.AttributeDecl;
import org.exolab.castor.xml.schema.ElementDecl;
import org.exolab.castor.xml.schema.XMLType;
import org.xmldb.api.base.XMLDBException;

/**
 * @author seb
 *
 */
public interface SchemaAccess {
	XMLType getType(QName qname) throws XMLDBException;

	ElementDecl getElement(QName qname) throws XMLDBException;
	//void getElement(String xpath) throws XMLDBException;

	AttributeDecl getAttribute(QName qname) throws XMLDBException;
	/**
	 * Is a schema defining this namespace known 
	 * @param namespaceURI
	 * @return
	 * @throws XMLDBException
	 */
	boolean isKnownNamespace(String namespaceURI) throws XMLDBException;
}