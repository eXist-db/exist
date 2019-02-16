/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2005-2012 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist;

import org.exist.dom.QName;

import javax.xml.XMLConstants;

/**
 * Global namespace declarations.
 * 
 * @author wolf
 *
 */
public interface Namespaces {

    String DTD_NS = XMLConstants.XML_DTD_NS_URI;

	String SCHEMA_NS = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    String SCHEMA_DATATYPES_NS = "http://www.w3.org/2001/XMLSchema-datatypes";
	String SCHEMA_INSTANCE_NS = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
    
    // Move this here from Function.BUILTIN_FUNCTION_NS? /ljo
	String XPATH_FUNCTIONS_NS = "http://www.w3.org/2005/xpath-functions";	
    String XQUERY_LOCAL_NS = "http://www.w3.org/2005/xquery-local-functions";
	String XPATH_DATATYPES_NS = "http://www.w3.org/2003/05/xpath-datatypes";
        
    String XPATH_FUNCTIONS_MATH_NS = "http://www.w3.org/2005/xpath-functions/math";
    String XQUERY_OPTIONS_NS = "http://www.w3.org/2011/xquery-options";
        
    String XSLT_XQUERY_SERIALIZATION_NS = "http://www.w3.org/2010/xslt-xquery-serialization";

    String W3C_XQUERY_XPATH_ERROR_NS = "http://www.w3.org/2005/xqt-errors";
    String W3C_XQUERY_XPATH_ERROR_PREFIX = "err";

	String XSL_NS = "http://www.w3.org/1999/XSL/Transform";
    String EXIST_XQUERY_XPATH_ERROR_NS = "http://www.exist-db.org/xqt-errors/";
    String EXIST_XQUERY_XPATH_ERROR_PREFIX = "exerr";
        
	String EXIST_NS = "http://exist.sourceforge.net/NS/exist";
	String EXIST_NS_PREFIX = "exist";
	String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	String DC_NS = "http://purl.org/dc/elements/1.1/";

	String EXIST_JAVA_BINDING_NS = "http://exist.sourceforge.net/NS/exist/java-binding";
	String EXIST_JAVA_BINDING_NS_PREFIX = "java";


    String XML_NS = XMLConstants.XML_NS_URI;
	String XMLNS_NS = XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
	
	/** QName representing xml:id */
	QName XML_ID_QNAME = new QName("id", XML_NS, "xml");
	
	/** QName representing xml:space */
	QName XML_SPACE_QNAME = new QName("space", XML_NS, "xml");
	
	String SOAP_ENVELOPE = "http://schemas.xmlsoap.org/soap/envelope/";

	//SAXfeatures / properties : move toadedicated package
	String SAX_LEXICAL_HANDLER = "http://xml.org/sax/properties/lexical-handler";
	String SAX_NAMESPACES = "http://xml.org/sax/features/namespaces";	
	String SAX_NAMESPACES_PREFIXES = "http://xml.org/sax/features/namespace-prefixes";	
	String SAX_VALIDATION = "http://xml.org/sax/features/validation";	
	String SAX_VALIDATION_DYNAMIC =  "http://apache.org/xml/features/validation/dynamic";
        
    String XHTML_NS = "http://www.w3.org/1999/xhtml";

	String XINCLUDE_NS = "http://www.w3.org/2001/XInclude";
}
