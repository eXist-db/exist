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

/**
 * Global namespace declarations.
 * 
 * @author wolf
 *
 */
public interface Namespaces {

	public final static String SCHEMA_NS = "http://www.w3.org/2001/XMLSchema";	
    public final static String DTD_NS = "http://www.w3.org/TR/REC-xml";	
	public final static String SCHEMA_DATATYPES_NS = "http://www.w3.org/2001/XMLSchema-datatypes";	
	public final static String SCHEMA_INSTANCE_NS = "http://www.w3.org/2001/XMLSchema-instance";	
    
    // Move this here from Function.BUILTIN_FUNCTION_NS? /ljo
	public final static String XPATH_FUNCTIONS_NS = "http://www.w3.org/2005/xpath-functions";	
    public final static String XQUERY_LOCAL_NS = "http://www.w3.org/2005/xquery-local-functions";
	public final static String XPATH_DATATYPES_NS = "http://www.w3.org/2003/05/xpath-datatypes";
        
    public final static String XPATH_FUNCTIONS_MATH_NS = "http://www.w3.org/2005/xpath-functions/math";
    public final static String XQUERY_OPTIONS_NS = "http://www.w3.org/2011/xquery-options";
        
    public final static String XSLT_XQUERY_SERIALIZATION_NS = "http://www.w3.org/2010/xslt-xquery-serialization";

    public final static String W3C_XQUERY_XPATH_ERROR_NS = "http://www.w3.org/2005/xqt-errors/";
    public final static String W3C_XQUERY_XPATH_ERROR_PREFIX = "err";

	public final static String XSL_NS = "http://www.w3.org/1999/XSL/Transform";
    public final static String EXIST_XQUERY_XPATH_ERROR_NS = "http://www.exist-db.org/xqt-errors/";
    public final static String EXIST_XQUERY_XPATH_ERROR_PREFIX = "exerr";
        
	public final static String EXIST_NS = "http://exist.sourceforge.net/NS/exist";
	public final static String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	public final static String DC_NS = "http://purl.org/dc/elements/1.1/";

	public final static String XML_NS = "http://www.w3.org/XML/1998/namespace";	
	public final static String XMLNS_NS = "http://www.w3.org/2000/xmlns/";
	
	/** QName representing xml:id */
	public final static QName XML_ID_QNAME = new QName("id", XML_NS, "xml");
	
	/** QName representing xml:space */
	public final static QName XML_SPACE_QNAME = new QName("space", XML_NS, "xml");
	
	public final static String SOAP_ENVELOPE = "http://schemas.xmlsoap.org/soap/envelope/";

	//SAXfeatures / properties : move toadedicated package
	public final static String SAX_LEXICAL_HANDLER = "http://xml.org/sax/properties/lexical-handler";
	public final static String SAX_NAMESPACES = "http://xml.org/sax/features/namespaces";	
	public final static String SAX_NAMESPACES_PREFIXES = "http://xml.org/sax/features/namespace-prefixes";	
	public final static String SAX_VALIDATION = "http://xml.org/sax/features/validation";	
	public final static String SAX_VALIDATION_DYNAMIC =  "http://apache.org/xml/features/validation/dynamic";
        
    public final static String XHTML_NS = "http://www.w3.org/1999/xhtml";
}
