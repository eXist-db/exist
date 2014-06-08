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

import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.XMLUtil;
import org.exist.memtree.AttributeImpl;
import org.exist.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.security.Permission;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.serializer.DOMSerializer;
import org.exist.util.serializer.DOMStreamer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.xml.sax.*;
import org.xml.sax.ext.LexicalHandler;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.Properties;
import org.exist.security.PermissionDeniedException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Local implementation of XMLResource.
 */
public class LocalXMLResource extends AbstractEXistResource implements XMLResource {

	//protected DocumentImpl document = null;
	protected NodeProxy proxy = null;
	
	protected Properties outputProperties = null;
	protected LexicalHandler lexicalHandler = null;
	
	// those are the different types of content this resource
	// may have to deal with
	protected String content = null;
	protected File file = null;
	protected InputSource inputSource = null;
	protected Node root = null;
	protected AtomicValue value = null;
	
	protected Date datecreated= null;
	protected Date datemodified= null;

	public LocalXMLResource(Subject user, BrokerPool pool, LocalCollection parent,
			XmldbURI did) throws XMLDBException {
		super(user, pool, parent, did, MimeType.XML_TYPE.getName());
	}

	public LocalXMLResource(Subject user, BrokerPool pool, LocalCollection parent,
			NodeProxy p) throws XMLDBException {
		this(user, pool, parent, p.getDocument().getFileURI());
		this.proxy = p;
	}

	public Object getContent() throws XMLDBException {
		if (content != null) {            
			return content;
        }

		// Case 1: content is an external DOM node
		else if (root != null && !(root instanceof NodeValue)) {
            final StringWriter writer = new StringWriter();
			final DOMSerializer serializer = new DOMSerializer(writer, getProperties());
			try {
				serializer.serialize(root);
				content = writer.toString();
			} catch (final TransformerException e) {
				throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e
						.getMessage(), e);
			}
			return content;

			// Case 2: content is an atomic value
		} else if (value != null) {
			try {
                if (Type.subTypeOf(value.getType(),Type.STRING)) {
                    return ((StringValue)value).getStringValue(true);
                }
                else {
				return value.getStringValue();
                }



			} catch (final XPathException e) {
				throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e
						.getMessage(), e);
			}

			// Case 3: content is a file
		} else if (file != null) {
			try {
				content = XMLUtil.readFile(file);
				return content;
			} catch (final IOException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
						"error while reading resource contents", e);
			}

			// Case 4: content is an input source
		} else if (inputSource != null) {
			try {
				content = XMLUtil.readFile(inputSource);
				return content;
			} catch (final IOException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
						"error while reading resource contents", e);
			}

			// Case 5: content is a document or internal node
		} else {
		    DocumentImpl document = null;
			final Subject preserveSubject = pool.getSubject();
			DBBroker broker = null;
			try {
				broker = pool.get(user);
				final Serializer serializer = broker.getSerializer();
				serializer.setUser(user);
				serializer.setProperties(getProperties());
				if (root != null) {
					content = serializer.serialize((NodeValue) root);
                    
                } else if (proxy != null) {
                    content = serializer.serialize(proxy);
                    
                } else {
				    document = openDocument(broker, Lock.READ_LOCK);
					if (!document.getPermissions().validate(user,
							Permission.READ))
						{throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
								"permission denied to read resource");}
					content = serializer.serialize(document);
                }
				return content;
			} catch (final SAXException saxe) {
				saxe.printStackTrace();
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, saxe
						.getMessage(), saxe);
			} catch (final EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e
						.getMessage(), e);
			} catch (final Exception e) {
				e.printStackTrace();
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e
						.getMessage(), e);
			} finally {
			    closeDocument(document, Lock.READ_LOCK);
				pool.release(broker);
				pool.setSubject(preserveSubject);
			}
		}
	}

	public Node getContentAsDOM() throws XMLDBException {
		if (root != null) {
            if(root instanceof NodeImpl) {
        		final Subject preserveSubject = pool.getSubject();
    			DBBroker broker = null;
    			try {
    				broker = pool.get(user);
    				
    				((NodeImpl)root).expand();
    			} catch (final EXistException e) {
    				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
    			} finally {
    				pool.release(broker);
    				pool.setSubject(preserveSubject);
    			}
            }
			return root;
        } else if (value != null) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					"cannot return an atomic value as DOM node");
		} else {
		    DocumentImpl document = null;
			final Subject preserveSubject = pool.getSubject();
			DBBroker broker = null;
			try {
				broker = pool.get(user);
				document = getDocument(broker, Lock.READ_LOCK);
				if (!document.getPermissions().validate(user, Permission.READ))
					{throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
							"permission denied to read resource");}
				if (proxy != null)
					{return document.getNode(proxy);}
                // <frederic.glorieux@ajlsm.com> return a full to get root PI and comments 
                return document;
			} catch (final EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e
						.getMessage(), e);
			} finally {
			    parent.getCollection().releaseDocument(document, Lock.READ_LOCK);
				pool.release(broker);
				pool.setSubject(preserveSubject);
			}
		}
	}

	public void getContentAsSAX(ContentHandler handler) throws XMLDBException {
		final Subject preserveSubject = pool.getSubject();
		DBBroker broker = null;
		// case 1: content is an external DOM node
		if (root != null && !(root instanceof NodeValue)) {
			try {
				final String option = parent.properties.getProperty(
						Serializer.GENERATE_DOC_EVENTS, "false");
                final DOMStreamer streamer = (DOMStreamer) SerializerPool.getInstance().borrowObject(DOMStreamer.class);
				streamer.setContentHandler(handler);
				streamer.setLexicalHandler(lexicalHandler);
				streamer.serialize(root, option.equalsIgnoreCase("true"));
				SerializerPool.getInstance().returnObject(streamer);
			} catch (final Exception e) {
				throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e
						.getMessage(), e);
			}
			
		// case 2: content is an atomic value
		} else if (value != null) {
			try {
				broker = pool.get(user);
				value.toSAX(broker, handler, getProperties());
			} catch (final EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e
						.getMessage(), e);
			} catch (final SAXException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e
						.getMessage(), e);
			} finally {
				pool.release(broker);
				pool.setSubject(preserveSubject);
			}
			
		// case 3: content is an internal node or a document
		} else {
			try {
				broker = pool.get(user);
				final Serializer serializer = broker.getSerializer();
				serializer.setUser(user);
				serializer.setProperties(getProperties());
				serializer.setSAXHandlers(handler, lexicalHandler);
				if (root != null) {
					serializer.toSAX((NodeValue) root);
                    
                } else if (proxy != null) {
                    serializer.toSAX(proxy);
                    
                } else {
					DocumentImpl document = null;
					try {
						document = openDocument(broker, Lock.READ_LOCK);
						if (!document.getPermissions().validate(user,
								Permission.READ))
							{throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
							"permission denied to read resource");}
						serializer.toSAX(document);
					} finally {
					    closeDocument(document, Lock.READ_LOCK);
					}
				}
			} catch (final EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e
						.getMessage(), e);
			} catch (final SAXException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e
						.getMessage(), e);
			} finally {
				pool.release(broker);
				pool.setSubject(preserveSubject);
			}
		}
	}

	//TODO: use xmldbURI?
	public String getDocumentId() throws XMLDBException {
		return docId.toString();
	}

	//TODO: use xmldbURI?
	public String getId() throws XMLDBException {
		return docId.toString();
	}

	public Collection getParentCollection() throws XMLDBException {
		if (parent == null)
			{throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					"collection parent is null");}
		return parent;
	}

	public String getResourceType() throws XMLDBException {
		return "XMLResource";
	}

	public Date getCreationTime() throws XMLDBException {
		final Subject preserveSubject = pool.getSubject();
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			final DocumentImpl document = getDocument(broker, Lock.NO_LOCK);
			return new Date(document.getMetadata().getCreated());
		} catch (final EXistException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(),
					e);
		} finally {
			pool.release(broker);
			pool.setSubject(preserveSubject);
		}
	}

	public Date getLastModificationTime() throws XMLDBException {
		final Subject preserveSubject = pool.getSubject();
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			final DocumentImpl document = getDocument(broker, Lock.NO_LOCK);
			return new Date(document.getMetadata().getLastModified());
		} catch (final EXistException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(),
					e);
		} finally {
			pool.release(broker);
			pool.setSubject(preserveSubject);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#getContentLength()
	 */
	public long getContentLength() throws XMLDBException {
		final Subject preserveSubject = pool.getSubject();
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			final DocumentImpl document = getDocument(broker, Lock.NO_LOCK);
			return document.getContentLength();
		} catch (final EXistException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(),
					e);
		} finally {
			pool.release(broker);
			pool.setSubject(preserveSubject);
		}
	}

	/**
	 * Sets the content for this resource. If value is of type File, it is
	 * directly passed to the parser when Collection.storeResource is called.
	 * Otherwise the method tries to convert the value to String.
	 * 
	 * Passing a File object should be preferred if the document is large. The
	 * file's content will not be loaded into memory but directly passed to a
	 * SAX parser.
	 * 
	 * @param obj
	 *                   the content value to set for the resource.
	 * @exception XMLDBException
	 *                         with expected error codes. <br /><code>ErrorCodes.VENDOR_ERROR</code>
	 *                         for any vendor specific errors that occur. <br />
	 */
	public void setContent(Object obj) throws XMLDBException {
		content = null;
		file = null;
		value = null;
		inputSource = null;
		root = null;
		if (obj instanceof File)
			{file = (File) obj;}

		else if (obj instanceof AtomicValue)
			{value = (AtomicValue) obj;}

		else if (obj instanceof InputSource)
			{inputSource=(InputSource) obj;}

        else if (obj instanceof byte[]){
            content = new String((byte[])obj, UTF_8);

        } else {
			content = obj.toString();
		}
	}

	public void setContentAsDOM(Node root) throws XMLDBException {
		if (root instanceof AttributeImpl)
			{throw new XMLDBException(ErrorCodes.WRONG_CONTENT_TYPE,
					"SENR0001: can not serialize a standalone attribute");}
		content = null;
		file = null;
		value = null;
		inputSource = null;
		this.root = root;
	}

	public ContentHandler setContentAsSAX() throws XMLDBException {
		file = null;
		value = null;
		inputSource = null;
		root = null;
		return new InternalXMLSerializer();
	}
        
        @Override
        public void freeResources() throws XMLDBException {
            //dO nothing
            //TODO consider unifying closeDocument() code into freeResources()
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

	public boolean getSAXFeature(String arg0) throws SAXNotRecognizedException,
			SAXNotSupportedException {
		return false;
	}

	public void setSAXFeature(String arg0, boolean arg1)
			throws SAXNotRecognizedException, SAXNotSupportedException {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xmldb.EXistResource#getMode()
	 */
	public Permission getPermissions() throws XMLDBException {
		final Subject preserveSubject = pool.getSubject();
	    DBBroker broker = null;
	    try {
	        broker = pool.get(user);
		    final DocumentImpl document = getDocument(broker, Lock.NO_LOCK);
			return document != null ? document.getPermissions() : null;
	    } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
        } finally {
	        pool.release(broker);
			pool.setSubject(preserveSubject);
	    }
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#setLexicalHandler(org.xml.sax.ext.LexicalHandler)
	 */
	public void setLexicalHandler(LexicalHandler handler) {
		lexicalHandler = handler;
	}
	
	protected void setProperties(Properties properties) {
		this.outputProperties = properties;
	}
	
	private Properties getProperties() {
		return outputProperties == null ? parent.properties : outputProperties;
	}

	protected DocumentImpl getDocument(DBBroker broker, int lock) throws XMLDBException {
	    DocumentImpl document = null;
            try {
                if(lock != Lock.NO_LOCK) {
                    document = parent.getCollection().getDocumentWithLock(broker, docId, lock);
                 } else {
                    document = parent.getCollection().getDocument(broker, docId);
                }
            } catch (final LockException e) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
                        "Failed to acquire lock on document " + docId);
            } catch (final PermissionDeniedException pde) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
                        "Permission denied on document " + docId);
            }
	   
	    if (document == null) {
	        throw new XMLDBException(ErrorCodes.INVALID_RESOURCE);
	    }
	    return document;
	}
	
	public NodeProxy getNode() throws XMLDBException {
	    if(proxy != null)
	        {return proxy;}
		final Subject preserveSubject = pool.getSubject();
	    DBBroker broker = null;
	    try {
	        broker = pool.get(user);
	        final DocumentImpl document = getDocument(broker, Lock.NO_LOCK);
	        // this XMLResource represents a document
			return new NodeProxy(document, NodeId.DOCUMENT_NODE);
	    } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
        } finally {
	        pool.release(broker);
			pool.setSubject(preserveSubject);
	    }
	}

	public  DocumentType getDocType() throws XMLDBException {
		final Subject preserveSubject = pool.getSubject();
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			final DocumentImpl document = getDocument(broker, Lock.NO_LOCK);
			if (!document.getPermissions().validate(user, Permission.READ))
				{throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
						"permission denied to read resource");}

			return  document.getDoctype();			
		} catch (final EXistException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(),
					e);
		} finally {
			pool.release(broker);
			pool.setSubject(preserveSubject);
		}
}
	
	public void setDocType(DocumentType doctype) throws XMLDBException {
		final Subject preserveSubject = pool.getSubject();
		DBBroker broker = null;
		DocumentImpl document = null;
		 final TransactionManager transact = pool.getTransactionManager();
	        final Txn transaction = transact.beginTransaction();
		try {
			broker = pool.get(user);
			document = openDocument(broker, Lock.WRITE_LOCK);
           	
			if (document == null) {
                throw new EXistException("Resource "
                        + docId + " not found");
            }
			
			if (!document.getPermissions().validate(user, Permission.WRITE))
				{throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, 
						"User is not allowed to lock resource " + document.getFileURI());}
			
			document.setDocumentType(doctype);
         	broker.storeXMLResource(transaction, document);
            transact.commit(transaction);


		} catch (final EXistException e) {
            transact.abort(transaction);
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(),
					e);
		} finally {
            transact.close(transaction);
			closeDocument(document, Lock.WRITE_LOCK);
			pool.release(broker);
			pool.setSubject(preserveSubject);
		}
}
}
