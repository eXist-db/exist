/*
 * Created on May 25, 2004
 *
 */
package org.exist.schema.internalaccess;

import java.io.IOException;
import java.io.StringReader;
import java.util.Hashtable;

import javax.xml.namespace.QName;

import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.schema.SchemaAccess;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exolab.castor.xml.schema.AttributeDecl;
import org.exolab.castor.xml.schema.ElementDecl;
import org.exolab.castor.xml.schema.Schema;
import org.exolab.castor.xml.schema.XMLType;
import org.exolab.castor.xml.schema.reader.SchemaReader;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * Provides server-internal access to the schema store. This has the advantage of being a lot faster than
 * taking the detour via the SchemaService. In addition to that you can also access schema definitions from 
 * places where you have lost track of the context collection.
 * 
 * @author seb
 *
 */
public class InternalSchemaAccess implements SchemaAccess {
	/**
	* singleton instance:
	*/
	private static InternalSchemaAccess singleInstance = null;

	private BrokerPool brokerPool = null;
	/**
	 * Key: String targetNamespace
	 */
	private Hashtable schemaCache = new Hashtable();
	/**
	 * Key: QName of type
	 */
	private Hashtable typeCache = new Hashtable();
	/**
	 * Key: QName of element
	 */
	private Hashtable elementCache = new Hashtable();
	/**
	 * Key QName of attribute
	 */
	private Hashtable attributeCache = new Hashtable();

	/**
	 * 
	 */
	private InternalSchemaAccess() throws EXistException {
		super();
		brokerPool = BrokerPool.getInstance();
	}

	public static InternalSchemaAccess getSingleInstance() throws EXistException {
		if (singleInstance == null) {
			singleInstance = new InternalSchemaAccess();
		}
		return singleInstance;
	}

	/**
	 * Retrieve a document by name and serialize it into a string.
	 * @param docName the full path to the document
	 * @return the serialization of the XML document referred to by <code>docuName</code>
	 * @throws XMLDBException if there is a database error in the process
	 */
	private String getDocumentAsString(String docName) throws XMLDBException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get();
			Serializer serializer = broker.getSerializer();
			serializer.reset();
			DocumentImpl doc = (DocumentImpl) getDocumentAsDOM(docName);
			if (doc != null)
				return serializer.serialize(doc);
			else
				return null;
		} catch (Exception e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Error getting document " + docName, e);
		} finally {
			if (broker != null)
				brokerPool.release(broker);
		}
	}

	/**
	 * Retrieve a document by name and return it as a Document.
	 * @param docName the full path to the document
	 * @return
	 * @throws XMLDBException
	 */
	private Document getDocumentAsDOM(String docName) throws XMLDBException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get();
			DocumentImpl doc;
			return (DocumentImpl) broker.getDocument(docName);
		} catch (Exception e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Error getting document " + docName, e);
		} finally {
			if (broker != null)
				brokerPool.release(broker);
		}
	}

	/** Collection in which the schemas are stored */
	private static final String SCHEMA_COLLECTION_PATH = "/db/system/schema";
	/** Index file mapping targetNamespace to resource name */
	private static final String INDEX_NAME = SCHEMA_COLLECTION_PATH + "/.index";

	/**
	 * Determines the filename (resource name) of the resource that stores the schema defining target namespace.
	 * @param targetNamespace
	 * @return the resource name without path
	 * @throws XMLDBException
	 */
	private String getSchemaFilename(String targetNamespace) throws XMLDBException {
		//FIXME almost duplicate code with GenericSchemaService#getSchemaFilename!
		if (targetNamespace == null)
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "null is not a valid namespace!");

		Node root = getDocumentAsDOM(INDEX_NAME).getDocumentElement();

		if ("schema-index".equals(root.getNodeName())) {
			NodeList schemas = root.getChildNodes();
			for (int i = 0; i < schemas.getLength(); i++) {
				Node schema = schemas.item(i);
				if ("schema".equals(schema.getNodeName())) {
					Node targetNamespaceAttr = schema.getAttributes().getNamedItem("targetNamespace");
					if ((targetNamespaceAttr != null) && (targetNamespace.equals(targetNamespaceAttr.getNodeValue())))
						return schema.getAttributes().getNamedItem("resourceName").getNodeValue();
				}
			}
		} else {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "invalid schema index. Unexpected root element " + root.getNodeName(), null);
		}
		return null;
	}

	/**
	 * Retrieve a Castor representation of the schema
	 * @param targetNamespace
	 * @return Schema (as per the Castor implementation), null if the schema corresponding to NS targetNamespace is not known)
	 * @throws XMLDBException
	 */
	public Schema getCastorSchema(String targetNamespace) throws XMLDBException {
		Schema castorSchema = (Schema) schemaCache.get(targetNamespace);
		if (castorSchema == null) {
			String schemaFilename = getSchemaFilename(targetNamespace);
			if (schemaFilename != null) {
				try {
					String schema = getDocumentAsString(SCHEMA_COLLECTION_PATH + "/" + schemaFilename);
					if (schema != null) {
						castorSchema = (new SchemaReader(new InputSource(new StringReader(schema)))).read();
						if (castorSchema != null)
							schemaCache.put(targetNamespace, castorSchema);
					} else
						throw new XMLDBException(
							ErrorCodes.VENDOR_ERROR,
							"Schema document for target namespace " + targetNamespace + " not found even though it is in the index.",
							null);
				} catch (IOException e) {
					throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Error parsing schema " + targetNamespace, e);
				}
			}
		}
		return castorSchema;
	}

	public void putSchema(String schemaContents) throws XMLDBException {
		throw new UnsupportedOperationException("InternalSchemaAccess does not support storing of schemas.");
	}

	public boolean validate(String id) throws XMLDBException {
		return false;
	}

	/**
	 * Find the (globally declared) type refered to by qname
	 */
	public XMLType getType(QName qname) throws XMLDBException {
		if ((qname.getNamespaceURI() == null) || ("".equals(qname.getNamespaceURI())))
			throw new IllegalArgumentException("QName " + qname.toString() + " is not fully qualified.");
		XMLType type = (XMLType) typeCache.get(qname);
		if (type == null) {
			Schema schema = getCastorSchema(qname.getNamespaceURI());
			type = schema.getType(qname.getLocalPart());
			typeCache.put(qname, type);
		}
		return type;
	}

	/**
	 * @return ElementDecl The element declaration (Castor) or null if the element qname is not known.
	 */
	public ElementDecl getElement(QName qname) throws XMLDBException {
		if ((qname.getNamespaceURI() == null) || ("".equals(qname.getNamespaceURI())))
			throw new IllegalArgumentException("QName " + qname.toString() + " is not fully qualified.");
		ElementDecl element = (ElementDecl) elementCache.get(qname);
		if (element == null) {
			Schema schema = getCastorSchema(qname.getNamespaceURI());
			if (schema != null) {
				element = schema.getElementDecl(qname.getLocalPart());
				if (element != null)
					elementCache.put(qname, element);
				else
					return null;
			} else {
				return null;
			}
		}
		return element;
	}

	public AttributeDecl getAttribute(QName qname) throws XMLDBException {
		if ((qname.getNamespaceURI() == null) || ("".equals(qname.getNamespaceURI())))
			throw new IllegalArgumentException("QName " + qname.toString() + " is not fully qualified.");
		AttributeDecl attribute = (AttributeDecl) attributeCache.get(qname);
		if (attribute == null) {
			Schema schema = getCastorSchema(qname.getNamespaceURI());
			attribute = schema.getAttribute(qname.getLocalPart());
			attributeCache.put(qname, attribute);
		}
		return attribute;
	}

	public boolean isKnownNamespace(String namespaceURI) throws XMLDBException {
		return getCastorSchema(namespaceURI) != null;
	}

}
