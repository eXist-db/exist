package org.exist.xmldb;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.XMLUtil;
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
	protected Map properties = null;
	protected User user;
	protected String content = null;
	protected File file = null;
	protected Node root = null;

	public LocalXMLResource(
		User user,
		BrokerPool pool,
		LocalCollection parent,
		String docId,
		long id)
		throws XMLDBException {
		this(user, pool, parent, docId, id, null);
	}

	public LocalXMLResource(
		User user,
		BrokerPool pool,
		LocalCollection parent,
		String did,
		long id,
		Map properties)
		throws XMLDBException {
		this.user = user;
		this.brokerPool = pool;
		this.parent = parent;
		this.id = id;
		this.properties = properties;
		if (did != null && did.indexOf('/') > -1)
			did = did.substring(did.lastIndexOf('/') + 1);

		this.docId = did;
	}

	public LocalXMLResource(
		User user,
		BrokerPool pool,
		LocalCollection parent,
		DocumentImpl doc,
		long id,
		Map properties)
		throws XMLDBException {
		this.user = user;
		this.brokerPool = pool;
		this.parent = parent;
		this.id = id;
		this.document = doc;
		this.docId = doc.getFileName();
		if (docId.indexOf('/') > -1)
			docId = docId.substring(docId.lastIndexOf('/') + 1);

		this.properties = properties;
	}

	public LocalXMLResource(
		User user,
		BrokerPool pool,
		LocalCollection parent,
		NodeProxy p,
		Map properties)
		throws XMLDBException {
		this(user, pool, parent, p.doc, p.gid, properties);
		this.proxy = p;
	}

	public Object getContent() throws XMLDBException {
		if (content != null)
			return content;
		else if (file != null) {
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
				broker = brokerPool.get();
				if (document == null)
					getDocument(broker);
				if (!document.getPermissions().validate(user, Permission.READ))
					throw new XMLDBException(
						ErrorCodes.PERMISSION_DENIED,
						"permission denied to read resource");
				Serializer serializer = broker.getSerializer();
				serializer.setUser(user);
				serializer.setProperties(properties);
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
		DBBroker broker = null;
		try {
			broker = brokerPool.get();
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

	public void getContentAsSAX(ContentHandler handler) throws XMLDBException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get();
			if (document == null)
				getDocument(broker);
			if (!document.getPermissions().validate(user, Permission.READ))
				throw new XMLDBException(
					ErrorCodes.PERMISSION_DENIED,
					"permission denied to read resource");
			if (!properties.containsKey(Serializer.GENERATE_DOC_EVENTS))
				properties.put(Serializer.GENERATE_DOC_EVENTS, "true");
			Serializer serializer = broker.getSerializer();
			serializer.setContentHandler(handler);
			serializer.setUser(user);
			serializer.setProperties(properties);
			String xml;
			try {
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

	protected DocumentImpl getDocument() {
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
			broker = brokerPool.get();
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
			broker = brokerPool.get();
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
	public void setContent(Object value) throws XMLDBException {
		content = null;
		if (value instanceof File)
			file = (File) value;
		else {
			content = value.toString();
		}
	}

	public void setContentAsDOM(Node root) throws XMLDBException {
		this.root = root;
	}

	public ContentHandler setContentAsSAX() throws XMLDBException {
		String encoding = "UTF-8";
		if (properties != null && properties.containsKey("encoding"))
			encoding = (String) properties.get("encoding");
		OutputFormat format = new OutputFormat("xml", encoding, false);
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

	public boolean getSAXFeature(String arg0)
		throws SAXNotRecognizedException, SAXNotSupportedException {
		return false;
	}

	public void setSAXFeature(String arg0, boolean arg1)
		throws SAXNotRecognizedException, SAXNotSupportedException {
	}

}
