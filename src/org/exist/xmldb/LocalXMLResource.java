package org.exist.xmldb;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;

import javax.xml.transform.TransformerException;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.XMLUtil;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.DOMSerializer;
import org.exist.util.serializer.DOMSerializerPool;
import org.exist.util.serializer.DOMStreamer;
import org.exist.util.serializer.DOMStreamerPool;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.AtomicValue;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * Local implementation of XMLResource.
 */
public class LocalXMLResource implements XMLResourceImpl {

	private static Logger LOG = Logger.getLogger(LocalXMLResource.class);

	protected BrokerPool brokerPool;
	protected String docId = null;
	protected DocumentImpl document = null;
	protected LocalCollection parent;
	protected NodeProxy proxy = null;
	protected long id = -1;
	protected User user;
	
	// those are the different types of content this resource
	// may have to deal with
	protected String content = null;
	protected File file = null;
	protected Node root = null;
	protected AtomicValue value = null;

	public LocalXMLResource(
		User user,
		BrokerPool pool,
		LocalCollection parent,
		String did,
		long id)
		throws XMLDBException {
		this.user = user;
		this.brokerPool = pool;
		this.parent = parent;
		this.id = id;
		if (did != null && did.indexOf('/') > -1)
			did = did.substring(did.lastIndexOf('/') + 1);

		this.docId = did;
	}

	public LocalXMLResource(
		User user,
		BrokerPool pool,
		LocalCollection parent,
		DocumentImpl doc,
		long id)
		throws XMLDBException {
		this.user = user;
		this.brokerPool = pool;
		this.parent = parent;
		this.id = id;
		this.document = doc;
		this.docId = doc.getFileName();
		if (docId.indexOf('/') > -1)
			docId = docId.substring(docId.lastIndexOf('/') + 1);
	}

	public LocalXMLResource(
		User user,
		BrokerPool pool,
		LocalCollection parent,
		NodeProxy p)
		throws XMLDBException {
		this(user, pool, parent, p.doc, p.gid);
		this.proxy = p;
	}

	public Object getContent() throws XMLDBException {
		if (content != null)
			return content;
		else if (root != null) {
			DOMSerializer serializer = DOMSerializerPool.getInstance().borrowDOMSerializer();
			try {
				StringWriter writer = new StringWriter();
				serializer.setOutputProperties(parent.properties);
				serializer.setWriter(writer);
				serializer.serialize(root);
				content = writer.toString();
			} catch (TransformerException e) {
				throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
			} finally {
				DOMSerializerPool.getInstance().returnDOMSerializer(serializer);
			}
			return content;
		} else if (value != null) {
			try {
				return value.getStringValue();
			} catch (XPathException e) {
				throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
			}
		} else if (file != null) {
			try {
				content = XMLUtil.readFile(file);
				return content;
			} catch (IOException e) {
				throw new XMLDBException(
					ErrorCodes.VENDOR_ERROR,
					"error while reading resource contents",
					e);
			}
		} else {
			DBBroker broker = null;
			try {
				broker = brokerPool.get(user);
				if (document == null)
					getDocument(broker);
				if (!document.getPermissions().validate(user, Permission.READ))
					throw new XMLDBException(
						ErrorCodes.PERMISSION_DENIED,
						"permission denied to read resource");
				Serializer serializer = broker.getSerializer();
				serializer.setUser(user);
				serializer.setProperties(parent.properties);
				if (id < 0)
					content = serializer.serialize(document);
				else {
					if (proxy == null)
						proxy = new NodeProxy(document, id);
					content = serializer.serialize(proxy);
				}
				return content;
			} catch (SAXException saxe) {
				saxe.printStackTrace();
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, saxe.getMessage(), saxe);
			} catch (EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
			} catch (Exception e) {
				e.printStackTrace();
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
			} finally {
				brokerPool.release(broker);
			}
		}
	}

	public Node getContentAsDOM() throws XMLDBException {
		if(root != null)
			return root;
		else if(value != null) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"cannot return an atomic value as DOM node");
		} else {
			DBBroker broker = null;
			try {
				broker = brokerPool.get(user);
				if (document == null)
					getDocument(broker);
				if (!document.getPermissions().validate(user, Permission.READ))
					throw new XMLDBException(
						ErrorCodes.PERMISSION_DENIED,
						"permission denied to read resource");
				if (id < 0)
					return document.getDocumentElement();
				else if (proxy != null)
					return document.getNode(proxy);
				else
					return document.getNode(id);
			} catch (EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
			} finally {
				brokerPool.release(broker);
			}
		}
	}

	public void getContentAsSAX(ContentHandler handler) throws XMLDBException {
		DBBroker broker = null;
        if (root != null) {
			try {
				String option = parent.properties.getProperty(Serializer.GENERATE_DOC_EVENTS, "false");
                DOMStreamer streamer = DOMStreamerPool.getInstance().borrowDOMStreamer();
                streamer.setContentHandler(handler);
                streamer.serialize(root, option.equalsIgnoreCase("true"));
                DOMStreamerPool.getInstance().returnDOMStreamer(streamer);
			} catch (Exception e) {
				throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
			}
        } else if(value != null) {
        	try {
        		broker = brokerPool.get(user);
        		value.toSAX(broker, handler);
        	} catch (EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
			} catch (SAXException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
			} finally {
        		brokerPool.release(broker);
        	}
        } else {
            try {
                broker = brokerPool.get(user);
                if (document == null)
                    getDocument(broker);
                if (!document.getPermissions().validate(user, Permission.READ))
                    throw new XMLDBException(
                        ErrorCodes.PERMISSION_DENIED,
                        "permission denied to read resource");
                Serializer serializer = broker.getSerializer();
                serializer.setUser(user);
                
                String xml;
                try {
					serializer.setProperties(parent.properties);
					serializer.setContentHandler(handler);
                    if (id < 0)
                        serializer.toSAX(document);
                    else {
                        if (proxy == null)
                            proxy = new NodeProxy(document, id);
    
                        serializer.toSAX(proxy);
                    }
                } catch (SAXException saxe) {
                    saxe.printStackTrace();
                    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, saxe.getMessage(), saxe);
                }
            } catch (EXistException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            } finally {
                brokerPool.release(broker);
            }
        }
	}

	protected DocumentImpl getDocument() {
		if(document == null)
			LOG.warn("document object is null");
		return document;
	}

	protected void getDocument(DBBroker broker) throws XMLDBException {
		if (document != null)
			return;
		try {
			String path =
				(parent.getPath().equals("/") ? '/' + docId : parent.getPath() + '/' + docId);
			document = (DocumentImpl) broker.getDocument(path);
			if (document == null)
				throw new XMLDBException(ErrorCodes.INVALID_RESOURCE);
		} catch (PermissionDeniedException e) {
			throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e);
		}
	}

	protected NodeProxy getNode() {
		getDocument();
		if (id < 0)
			// this XMLResource represents a document
			return new NodeProxy(document, 1);
		return proxy == null ? new NodeProxy(document, id) : proxy;
	}

	public String getDocumentId() throws XMLDBException {
		return docId;
	}

	public String getId() throws XMLDBException {
		return id < 0 ? docId : Long.toString(id);
	}

	public Collection getParentCollection() throws XMLDBException {
		if (parent == null)
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "collection parent is null");
		return parent;
	}

	public String getResourceType() throws XMLDBException {
		return "XMLResource";
	}

	public Date getCreationTime() throws XMLDBException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			if (document == null)
				getDocument(broker);
			if (!document.getPermissions().validate(user, Permission.READ))
				throw new XMLDBException(
					ErrorCodes.PERMISSION_DENIED,
					"permission denied to read resource");
			return new Date(document.getCreated());
		} catch (EXistException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
		} finally {
			brokerPool.release(broker);
		}
	}

	public Date getLastModificationTime() throws XMLDBException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get(user);
			if (document == null)
				getDocument(broker);
			if (!document.getPermissions().validate(user, Permission.READ))
				throw new XMLDBException(
					ErrorCodes.PERMISSION_DENIED,
					"permission denied to read resource");
			return new Date(document.getLastModified());
		} catch (EXistException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
		} finally {
			brokerPool.release(broker);
		}
	}

	/**
	 * Sets the content for this resource. If value is of type
		* File, it is directly passed to the parser when 
		* Collection.storeResource is called. Otherwise the method
		* tries to convert the value to String.
		*
		* Passing a File object should be preferred if the document
		* is large. The file's content will not be loaded into memory
		* but directly passed to a SAX parser.
	 *
	 * @param value the content value to set for the resource.
	 * @exception XMLDBException with expected error codes.<br />
	 *  <code>ErrorCodes.VENDOR_ERROR</code> for any vendor
	 *  specific errors that occur.<br /> 
	 */
	public void setContent(Object obj) throws XMLDBException {
		content = null;
		if (obj instanceof File)
			file = (File) obj;
		else if (obj instanceof AtomicValue)
			value = (AtomicValue) obj;
		else {
			content = obj.toString();
		}
	}

	public void setContentAsDOM(Node root) throws XMLDBException {
		this.root = root;
	}

	public ContentHandler setContentAsSAX() throws XMLDBException {
		return new InternalXMLSerializer();
	}

	private class InternalXMLSerializer extends SAXSerializer {

		public InternalXMLSerializer() {
			super(new StringWriter(), null);
		}

		/**
		 * @see org.xml.sax.DocumentHandler#endDocument()
		 */
		public void endDocument() throws SAXException {
			super.endDocument();
			content = getWriter().toString();
		}
	}

	public boolean getSAXFeature(String arg0)
		throws SAXNotRecognizedException, SAXNotSupportedException {
		return false;
	}

	public void setSAXFeature(String arg0, boolean arg1)
		throws SAXNotRecognizedException, SAXNotSupportedException {
	}

}
