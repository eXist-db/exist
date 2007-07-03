package org.exist.xmldb;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.exist.Namespaces;
import org.exist.dom.DocumentTypeImpl;
import org.exist.security.Permission;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.Compressor;
import org.exist.util.MimeType;
import org.exist.util.serializer.DOMSerializer;
import org.exist.util.serializer.SAXSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

public class RemoteXMLResource implements XMLResource, EXistResource {
	
    private final static Properties emptyProperties = new Properties();
	
    /**
     *  Use external XMLReader to parse XML.
     */
    private XMLReader xmlReader = null;
	
    protected String id;
    protected XmldbURI path = null ;
    private String mimeType = MimeType.XML_TYPE.getName();
    protected int handle = -1;
    protected int pos = -1;
    protected RemoteCollection parent;
    protected String content = null;
    protected File file = null;
	
    protected Permission permissions = null;
    protected int contentLen = 0;
	
    protected Properties outputProperties = null;
    protected LexicalHandler lexicalHandler = null;
	
    protected Date dateCreated= null;
    protected Date dateModified= null;
    
	private static Logger LOG = Logger.getLogger(RemoteXMLResource.class.getName());
	
    public RemoteXMLResource(RemoteCollection parent, XmldbURI docId, String id)
	throws XMLDBException {
	this(parent, -1, -1, docId, id);
    }

    public RemoteXMLResource(
			     RemoteCollection parent,
			     int handle,
			     int pos,
			     XmldbURI docId,
			     String id)
	throws XMLDBException {
		this.handle = handle;
		this.pos = pos;
		this.parent = parent;
		this.id = id;
		if (docId.numSegments()>1) {
			this.path = docId;
		} else {
			this.path = parent.getPathURI().append(docId);
		}
    }

    public Date getCreationTime() throws XMLDBException {
        return dateCreated;
    }

    public Date getLastModificationTime() throws XMLDBException {
        return dateModified;
    }

    public Object getContent() throws XMLDBException {
	if (content != null) {
	    return content;
	}
	if (file != null) {
	    return file;
	}
	Properties properties = parent.getProperties();
	byte[] data = null;
	if (id == null) {
	    Vector params = new Vector();
	    params.addElement(path.toString());
	    params.addElement(properties);
	    try {
		Hashtable table = (Hashtable) parent.getClient().execute("getDocumentData", params);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		int offset = ((Integer)table.get("offset")).intValue();
		data = (byte[])table.get("data");
		os.write(data);
		while(offset > 0) {
		    params.clear();
		    params.addElement(table.get("handle"));
		    params.addElement(new Integer(offset));
		    table = (Hashtable) parent.getClient().execute("getNextChunk", params);
		    offset = ((Integer)table.get("offset")).intValue();
		    data = (byte[])table.get("data");
		    os.write(data);
		}
		data = os.toByteArray();
	    } catch (XmlRpcException xre) {
		throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
	    } catch (IOException ioe) {
		throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
	    }
	} else {
	    Vector params = new Vector();
	    params.addElement(new Integer(handle));
	    params.addElement(new Integer(pos));
	    params.addElement(properties);
	    try {
		data = (byte[]) parent.getClient().execute("retrieve", params);
	    } catch (XmlRpcException xre) {
		throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
	    } catch (IOException ioe) {
		throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
	    }
	}

	if (properties.getProperty(EXistOutputKeys.COMPRESS_OUTPUT, "no").equals("yes")) {
	    try {
		data = Compressor.uncompress(data);
	    } catch (IOException e) {

	    }


	}


	try {
	    content = new String(data, properties.getProperty(OutputKeys.ENCODING, "UTF-8"));
	} catch (UnsupportedEncodingException ue) {
		LOG.warn(ue);
	    content = new String(data);
	}
	return content;
    }

    public Node getContentAsDOM() throws XMLDBException {
	if (content == null)
	    getContent();
	// content can be a file
	if (file != null)
	    getData();
	try {
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	    factory.setNamespaceAware(true);
	    factory.setValidating(false);
	    DocumentBuilder builder = factory.newDocumentBuilder();
	    Document doc = builder.parse(new InputSource(new StringReader(content)));
        // <frederic.glorieux@ajlsm.com> return a full DOM doc, with root PI and comments
	    return doc;
	} catch (SAXException saxe) {
	    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, saxe.getMessage(), saxe);
	} catch (ParserConfigurationException pce) {
	    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, pce.getMessage(), pce);
	} catch (IOException ioe) {
	    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
	}
    }

    public void getContentAsSAX(ContentHandler handler) throws XMLDBException {
	if (content == null)
	    getContent();
	//		content can be a file
	if (file != null)
	    getData();
        
        XMLReader reader = null;
	if (xmlReader == null) {
	    SAXParserFactory saxFactory = SAXParserFactory.newInstance();
	    saxFactory.setNamespaceAware(true);
	    saxFactory.setValidating(false);
            try {
                SAXParser sax = saxFactory.newSAXParser();
                reader = sax.getXMLReader();
            } catch (ParserConfigurationException pce) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, pce.getMessage(), pce);
            } catch (SAXException saxe) {
                saxe.printStackTrace();
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, saxe.getMessage(), saxe);
            }
        } else {
            reader = xmlReader;
        }
	try {
	    reader.setContentHandler(handler);
	    if(lexicalHandler != null) {
	    	reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, lexicalHandler);
        }
	    reader.parse(new InputSource(new StringReader(content)));
        } catch (SAXException saxe) {
            saxe.printStackTrace();
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, saxe.getMessage(), saxe);
        } catch (IOException ioe) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
        }
    }
    
    public String getNodeId() {
        return id == null ? "1" : id;
    }

    public String getDocumentId() throws XMLDBException {
	return path.lastSegment().toString();
    }

    public String getId() throws XMLDBException {
	if (id == null || id.equals("1")) 
	    return getDocumentId(); 
	return getDocumentId() + '_' + id;
    }

    public Collection getParentCollection() throws XMLDBException {
	return parent;
    }

    public String getResourceType() throws XMLDBException {
	return "XMLResource";
    }

    /**
     * Sets the external XMLReader to use.
     *
     * @param xmlReader the XMLReader
     */
    public void setXMLReader(XMLReader xmlReader) {
	this.xmlReader = xmlReader;
    }

    public void setContent(Object value) throws XMLDBException {
	if (value instanceof File) {
	    file = (File) value;
	} else
	    content = value.toString();
    }

    public void setContentAsDOM(Node root) throws XMLDBException {
	StringWriter sout = new StringWriter();
	DOMSerializer xmlout = new DOMSerializer(sout, getProperties());
	try {
	    switch (root.getNodeType()) {
	    case Node.ELEMENT_NODE :
		xmlout.serialize((Element) root);
		break;
	    case Node.DOCUMENT_FRAGMENT_NODE :
		xmlout.serialize((DocumentFragment) root);
		break;
	    case Node.DOCUMENT_NODE :
		xmlout.serialize((Document) root);
		break;
	    default :
		throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "invalid node type");
	    }
	    content = sout.toString();
	} catch (TransformerException e) {
	    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
	}
    }

    public ContentHandler setContentAsSAX() throws XMLDBException {
	return new InternalXMLSerializer();
    }

    private class InternalXMLSerializer extends SAXSerializer {

	StringWriter writer = new StringWriter();

	public InternalXMLSerializer() {
	    super();
	    setOutput(writer, emptyProperties);
	}

	/**
	 * @see org.xml.sax.DocumentHandler#endDocument()
	 */
	public void endDocument() throws SAXException {
	    super.endDocument();
	    content = writer.toString();
	}
    }

    /* (non-Javadoc)
     * @see org.xmldb.api.modules.XMLResource#getSAXFeature(java.lang.String)
     */
    public boolean getSAXFeature(String arg0)
	throws SAXNotRecognizedException, SAXNotSupportedException {
	return false;
    }

    /* (non-Javadoc)
     * @see org.xmldb.api.modules.XMLResource#setSAXFeature(java.lang.String, boolean)
     */
    public void setSAXFeature(String arg0, boolean arg1)
	throws SAXNotRecognizedException, SAXNotSupportedException {
    }

    /**
     * Force content to be loaded into mem
     * 
     * @throws XMLDBException
     */
    protected byte[] getData() throws XMLDBException {
	if (file != null) {
	    if (!file.canRead())
		throw new XMLDBException(
					 ErrorCodes.INVALID_RESOURCE,
					 "failed to read resource content from file " + file.getAbsolutePath());
	    try {
		final byte[] chunk = new byte[512];
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final FileInputStream in = new FileInputStream(file);
		int l;
		do {
		    l = in.read(chunk);
		    if (l > 0)
			out.write(chunk, 0, l);

		} while (l > -1);
		in.close();
		final byte[] data = out.toByteArray();
		//				content = new String(data);
		file = null;
		return data;
	    } catch (IOException e) {
		throw new XMLDBException(
					 ErrorCodes.INVALID_RESOURCE,
					 "failed to read resource content from file " + file.getAbsolutePath(),
					 e);
	    }
	} else if(content != null)
	    try {
		return content.getBytes("UTF-8");
	    } catch (UnsupportedEncodingException e) {
	    	LOG.warn(e);
	    }
	return null;
    } 

    public void setContentLength(int len) {
	this.contentLen = len;
    }
	
    public int getContentLength() throws XMLDBException {
	return contentLen;
    }
	
    public void setPermissions(Permission perms) {
	permissions = perms;
    }

    public Permission getPermissions() {
	return permissions;
    }
	
    public void setLexicalHandler(LexicalHandler handler) {
	lexicalHandler = handler;
    }
	
    protected void setProperties(Properties properties) {
	this.outputProperties = properties;
    }
	
    private Properties getProperties() {
	return outputProperties == null ? parent.properties : outputProperties;
    }

    /* (non-Javadoc)
     * @see org.exist.xmldb.EXistResource#setMimeType(java.lang.String)
     */
    public void setMimeType(String mime)
    {
    	this.mimeType = mime;
    }

    /* (non-Javadoc)
     * @see org.exist.xmldb.EXistResource#getMimeType()
     */
    public String getMimeType() {
        return mimeType;
    }


    public  DocumentType getDocType() throws XMLDBException {
    	DocumentType result = null;
    	Vector params = new Vector(1);
    	Vector request = null;
    	params.addElement(path.toString());
    	try {
    		
    		request = (Vector) parent.getClient().execute("getDocType", params);
    		
    		if (!((String)request.get(0)).equals("")) {
    			result = new DocumentTypeImpl((String)request.get(0),(String)request.get(1),(String)request.get(2) );
    		}
    		
    	    return result;
    	    
    	} catch (XmlRpcException e) {
    	    throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
    	} catch (IOException e) {
    	    throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
    	}
        }
    
    public void setDocType(DocumentType doctype) throws XMLDBException {
    	if (doctype != null ) {
    		Vector params = new Vector(4);
    		params.addElement(path.toString());
    		params.addElement(doctype.getName());
    		params.addElement(doctype.getPublicId() == null ? "" : doctype.getPublicId());
    		params.addElement(doctype.getSystemId() == null ? "" : doctype.getSystemId());
    		
    		try {
    		    parent.getClient().execute("setDocType", params);
    		} catch (XmlRpcException e) {
    		    throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
    		} catch (IOException e) {
    		    throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
    		}
    		

    	}
		
}

    protected void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    protected void setDateModified(Date dateModified) {
        this.dateModified = dateModified;
    }
}
