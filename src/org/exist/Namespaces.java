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
	
	public final static String SCHEMA_DATATYPES_NS = "http://www.w3.org/2001/XMLSchema-datatypes";
	
	public final static String SCHEMA_INSTANCE_NS = "http://www.w3.org/2001/XMLSchema-instance";
	
	public final static String XPATH_DATATYPES_NS = "http://www.w3.org/2003/05/xpath-datatypes";
	
	public final static String EXIST_NS = "http://exist.sourceforge.net/NS/exist";
	
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
}
