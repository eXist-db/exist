package org.exist.xmldb;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.apache.xmlrpc.XmlRpcException;
import org.exist.security.Permission;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

public class RemoteXMLResource implements XMLResourceImpl {
	/**
	 *  if this class is used from Cocoon, use the Cocoon parser component
	 *  instead of JAXP
	 */
	private org.apache.excalibur.xml.sax.SAXParser cocoonParser = null;
	protected String id, documentName, path = null;
	protected int handle = -1;
	protected int pos = -1;
	protected RemoteCollection parent;
	protected String content = null;
	protected File file = null;
	protected Permission permissions = null;
	protected Map properties = new HashMap();

	public RemoteXMLResource(RemoteCollection parent, String docId, String id)
		throws XMLDBException {
		this(parent, -1, -1, docId, id);
	}

	public RemoteXMLResource(
		RemoteCollection parent,
		int handle,
		int pos,
		String docId,
		String id)
		throws XMLDBException {
		this.handle = handle;
		this.pos = pos;
		this.parent = parent;
		this.id = id;
		int p;
		if (docId != null && (p = docId.lastIndexOf('/')) > -1) {
			path = docId;
			documentName = docId.substring(p + 1);
		} else {
			path = parent.getPath() + '/' + docId;
			documentName = docId;
		}
	}

	public Date getCreationTime() throws XMLDBException {
		Vector params = new Vector(1);
		params.addElement(path);
		try {
			return (Date) ((Vector) parent.getClient().execute("getTimestamps", params)).get(0);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
		} catch (IOException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
		}
	}

	public Date getLastModificationTime() throws XMLDBException {
		Vector params = new Vector(1);
		params.addElement(path);
		try {
			return (Date) ((Vector) parent.getClient().execute("getTimestamps", params)).get(1);
		} catch (XmlRpcException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
		} catch (IOException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
		}
	}

	public Object getContent() throws XMLDBException {
		if (content != null)
			return content;
		if (file != null)
			return file;
		Properties properties = parent.getProperties();
		byte[] data = null;
		if (id == null) {
			Vector params = new Vector();
			params.addElement(path);
			params.addElement(properties);
			try {
				data = (byte[]) parent.getClient().execute("getDocument", params);
			} catch (XmlRpcException xre) {
				throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
			} catch (IOException ioe) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
			}
		} else {
			System.out.println("indent = " + properties.getProperty("indent"));
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
		try {
			content = new String(data, properties.getProperty(OutputKeys.ENCODING, "UTF-8"));
		} catch (UnsupportedEncodingException ue) {
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
			return doc.getDocumentElement();
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
		if (cocoonParser == null) {
			SAXParserFactory saxFactory = SAXParserFactory.newInstance();
			saxFactory.setNamespaceAware(true);
			saxFactory.setValidating(false);
			try {
				SAXParser sax = saxFactory.newSAXParser();
				XMLReader reader = sax.getXMLReader();
				reader.setContentHandler(handler);
				reader.parse(new InputSource(new StringReader(content)));
			} catch (SAXException saxe) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, saxe.getMessage(), saxe);
			} catch (ParserConfigurationException pce) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, pce.getMessage(), pce);
			} catch (IOException ioe) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
			}
		} else
			try {
				cocoonParser.parse(new InputSource(new StringReader(content)), handler);
			} catch (SAXException saxe) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, saxe.getMessage(), saxe);
			} catch (IOException ioe) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
			}

	}

	public String getDocumentId() throws XMLDBException {
		return documentName;
	}

	public String getId() throws XMLDBException {
		return (id == null) ? documentName : documentName + '_' + id;
	}

	public Collection getParentCollection() throws XMLDBException {
		return parent;
	}

	public String getResourceType() throws XMLDBException {
		return "XMLResource";
	}

	/**
	 *  Sets the cocoonParser to be used.
	 *
	 *@param  parser  The new cocoonParser value
	 */
	public void setCocoonParser(org.apache.excalibur.xml.sax.SAXParser parser) {
		this.cocoonParser = parser;
	}

	public void setContent(Object value) throws XMLDBException {
		if (value instanceof File) {
			file = (File) value;
		} else
			content = value.toString();
	}

	public void setContentAsDOM(Node root) throws XMLDBException {
		StringWriter sout = new StringWriter();
		OutputFormat format = new OutputFormat("xml", "UTF-8", false);
		XMLSerializer xmlout = new XMLSerializer(sout, format);
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
		} catch (IOException ioe) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
		}
	}

	public ContentHandler setContentAsSAX() throws XMLDBException {
		OutputFormat format = new OutputFormat("xml", "UTF-8", false);
		return new InternalXMLSerializer(format);
	}

	private class InternalXMLSerializer extends XMLSerializer {

		StringWriter writer = new StringWriter();

		public InternalXMLSerializer(OutputFormat format) {
			super(format);
			setOutputCharStream(writer);
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
				byte[] chunk = new byte[512];
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				FileInputStream in = new FileInputStream(file);
				int l;
				do {
					l = in.read(chunk);
					if (l > 0)
						out.write(chunk, 0, l);

				} while (l > -1);
				in.close();
				byte[] data = out.toByteArray();
				content = new String(data);
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
			}
		return null;
	} 

	public void setPermissions(Permission perms) {
		permissions = perms;
	}

	public Permission getPermissions() {
		return permissions;
	}
}
