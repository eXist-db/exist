/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2003-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xmldb;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;

import org.exist.Namespaces;
import org.exist.dom.DocumentTypeImpl;
import org.exist.util.MimeType;
import org.exist.util.serializer.DOMSerializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xquery.value.StringValue;

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
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class RemoteXMLResource
	extends AbstractRemoteResource
	implements XMLResource
{
	
    private final static Properties emptyProperties = new Properties();
	
    /**
     *  Use external XMLReader to parse XML.
     */
    private XMLReader xmlReader = null;
	
    protected String id;
    protected int handle = -1;
    protected int pos = -1;
    private String content = null;
	
    protected Properties outputProperties = null;
    protected LexicalHandler lexicalHandler = null;
	
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
    	super(parent,docId);
		this.handle = handle;
		this.pos = pos;
		this.id = id;
		this.mimeType=MimeType.XML_TYPE.getName();
    }

    public Object getContent() throws XMLDBException {
        if (content != null) {
            return new StringValue(content).getStringValue(true);
        }
        Object res=super.getContent();
        if(res!=null) {
		if(res instanceof byte[]) {
			try {
				return new String((byte[])res,"UTF-8");
			} catch(UnsupportedEncodingException uee) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, uee.getMessage(), uee);
			}
		} else {
			return res;
		}
	}
	return null;
        
        // Backward compatible code (perhaps it is not needed?)
        /*
        if (properties.getProperty(EXistOutputKeys.COMPRESS_OUTPUT, "no").equals("yes")) {
            try {
                data = Compressor.uncompress(data);
            } catch (IOException e) {
                
            }
        }
        
        try {
            content = new String(data, properties.getProperty(OutputKeys.ENCODING, "UTF-8"));
            // fixme! - this should probably be earlier in the chain before serialisation. /ljo
            content = new StringValue(content).getStringValue(true);
        } catch (UnsupportedEncodingException ue) {
            LOG.warn(ue);
            content = new String(data);
            content = new StringValue(content).getStringValue(true);
        }
        return content;
	*/
    }

    public Node getContentAsDOM()
    	throws XMLDBException
    {
    	InputSource is=null;
	
    	if(content!=null) {
    		is=new InputSource(new StringReader(content));
    	} else {
    		is=new InputSource(getStreamContent());
    	}
    	
		try {
		    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		    factory.setNamespaceAware(true);
		    factory.setValidating(false);
		    DocumentBuilder builder = factory.newDocumentBuilder();
		    Document doc = builder.parse(is);
	        // <frederic.glorieux@ajlsm.com> return a full DOM doc, with root PI and comments
		    return doc;
		} catch (SAXException saxe) {
		    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, saxe.getMessage(), saxe);
		} catch (ParserConfigurationException pce) {
		    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, pce.getMessage(), pce);
		} catch(IOException ioe) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
		}
    }

    public void getContentAsSAX(ContentHandler handler)
    	throws XMLDBException
    {
    	InputSource is=null;
	
    	if(content!=null) {
    		is=new InputSource(new StringReader(content));
    	} else {
    		is=new InputSource(getStreamContent());
    	}
    	
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
		    reader.parse(is);
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
	content = null;
    	if(!super.setContentInternal(value)) {
		if(value instanceof String) {
			content = new String((String)value);
		} else if(value instanceof byte[]) {
			try {
				content = new String((byte[])value,"UTF-8");
			} catch(UnsupportedEncodingException uee) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, uee.getMessage(), uee);
			}
		} else {
	    		content = value.toString();
		}
    	}
    }

    public void setContentAsDOM(Node root) throws XMLDBException {
    	try {
	    	File tmpfile=File.createTempFile("eXistRXR", ".xml");
	    	tmpfile.deleteOnExit();
	    	FileOutputStream fos=new FileOutputStream(tmpfile);
	    	BufferedOutputStream bos=new BufferedOutputStream(fos);
	    	OutputStreamWriter osw=new OutputStreamWriter(bos,"UTF-8");
			DOMSerializer xmlout = new DOMSerializer(osw, getProperties());
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
				setContent(tmpfile);
			} catch (TransformerException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
			} finally {
				try {
					osw.close();
				} catch(IOException ioe) {
					// IgnoreIT(R)
				}
				try {
					bos.close();
				} catch(IOException ioe) {
					// IgnoreIT(R)
				}
				try {
					fos.close();
				} catch(IOException ioe) {
					// IgnoreIT(R)
				}
			}
    	} catch(IOException ioe) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
    	}
    }

    public ContentHandler setContentAsSAX()
    	throws XMLDBException
    {
    	freeLocalResources();
    	content = null;
    	return new InternalXMLSerializer();
    }

    private class InternalXMLSerializer
    	extends SAXSerializer
    {
    	File tmpfile=null;
    	OutputStreamWriter writer = null;
    	
		public InternalXMLSerializer() {
			super();
		}
		
		public void startDocument() throws SAXException {
			try {
		    	File tmpfile=File.createTempFile("eXistRXR", ".xml");
		    	tmpfile.deleteOnExit();
		    	FileOutputStream fos=new FileOutputStream(tmpfile);
		    	BufferedOutputStream bos=new BufferedOutputStream(fos);
		    	writer=new OutputStreamWriter(bos,"UTF-8");
				setOutput(writer, emptyProperties);
			} catch(IOException ioe) {
				throw new SAXException("Unable to create temp file for serialization data",ioe);
			}
			
			super.startDocument();
		}

		/**
		 * @see org.xml.sax.DocumentHandler#endDocument()
		 */
		public void endDocument() throws SAXException
		{
		    super.endDocument();
		    try {
		    	setContent(tmpfile);
		    } catch(XMLDBException xe) {
		    	throw new SAXException("Unable to close temp file containing serialized data",xe);
		    }
		    
		    try {
		    	if (writer != null)
		    		writer.close();
		    } catch (IOException e) {
		    	throw new SAXException("Unable to close temp file containing serialized data",e);
		    }
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

    public void setLexicalHandler(LexicalHandler handler) {
	lexicalHandler = handler;
    }
	
    protected void setProperties(Properties properties) {
	this.outputProperties = properties;
    }
	
    protected Properties getProperties() {
	return outputProperties == null ? parent.properties : outputProperties;
    }

    public  DocumentType getDocType() throws XMLDBException {
    	DocumentType result = null;
        List params = new ArrayList(1);
    	Object[] request = null;
    	params.add(path.toString());
    	try {
    		
    		request = (Object[]) parent.getClient().execute("getDocType", params);
    		
    		if (!request[0].equals("")) {
    			result = new DocumentTypeImpl((String)request[0],(String)request[1],(String)request[2]);
    		}
    		
    	    return result;
    	    
    	} catch (XmlRpcException e) {
    	    throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
    	}
    }
    
    public void setDocType(DocumentType doctype) throws XMLDBException {
    	if (doctype != null ) {
            List params = new ArrayList(4);
    		params.add(path.toString());
    		params.add(doctype.getName());
    		params.add(doctype.getPublicId() == null ? "" : doctype.getPublicId());
    		params.add(doctype.getSystemId() == null ? "" : doctype.getSystemId());
    		
    		try {
    		    parent.getClient().execute("setDocType", params);
    		} catch (XmlRpcException e) {
    		    throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
    		}


        }
		
    }

	public void getContentIntoAStream(OutputStream os)
		throws XMLDBException
	{
		getContentIntoAStreamInternal(os,content,id!=null,handle,pos);
	}

	public Object getExtendedContent()
		throws XMLDBException
	{
		return getExtendedContentInternal(content,id!=null,handle,pos);
	}

	public InputStream getStreamContent() throws XMLDBException {
		return getStreamContentInternal(content,id!=null,handle,pos);
	}

	public long getStreamLength()
		throws XMLDBException
	{
		return getStreamLengthInternal(content);
	}
}
