/*
 * Created on Apr 10, 2004
 *
 */
package org.exist.schema;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.apache.tools.ant.filters.StringInputStream;
import org.apache.xerces.parsers.DOMParser;
import org.exist.xmldb.XQueryService;
import org.exolab.castor.xml.schema.AttributeDecl;
import org.exolab.castor.xml.schema.ElementDecl;
import org.exolab.castor.xml.schema.Schema;
import org.exolab.castor.xml.schema.XMLType;
import org.exolab.castor.xml.schema.reader.SchemaReader;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

/**
 * @author seb
 */
public abstract class GenericSchemaService implements SchemaService {

	private class ValidationErrorHandler implements ErrorHandler {

		private ArrayList warnings = new ArrayList();
		private ArrayList errors = new ArrayList();

		public void error(SAXParseException exception) throws SAXException {
			addError(exception);
		}

		public void fatalError(SAXParseException exception) throws SAXException {
			addError(exception);
		}

		public void warning(SAXParseException exception) throws SAXException {
			addWarning(exception);
		}

		/**
		 * @return
		 */
		public ArrayList getErrors() {
			return errors;
		}

		private void addError(SAXParseException e) {
			getErrors().add("Error: (" + e.getLineNumber() + ", " + e.getColumnNumber() + "): " + e.getMessage());
		}

		/**
		 * @return
		 */
		public ArrayList getWarnings() {
			return warnings;
		}

		private void addWarning(SAXParseException e) {
			getWarnings().add("Warning: (" + e.getLineNumber() + ", " + e.getColumnNumber() + "): " + e.getMessage());
		}

		public XMLDBException toException() {
			String errors = "";
			for (Iterator i = getErrors().iterator(); i.hasNext();)
				errors += (String) i.next() + "\n";
			return new XMLDBException(ErrorCodes.VENDOR_ERROR, "Error validating: \n" + errors, null);
		}

	}
	
	protected Logger LOG = Logger.getLogger(GenericSchemaService.class);

	private final static String INDEX_RESOURCE_NAME = ".index";

	private static final String JAXP_SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
	private static final String W3C_XML_SCHEMA = "http://www.w3.org/2001/XMLSchema";
	private static final String JAXP_SCHEMA_SOURCE = "http://java.sun.com/xml/jaxp/properties/schemaSource";

	private Collection parentCollection;
	private Collection schemasCollection;
	
	private ArrayList transientSchemas = null;

	public GenericSchemaService(Collection collection) {
		setParentCollection(collection);
	}

	public String getName() throws XMLDBException {
		return "SchemaService";
	}

	public String getVersion() throws XMLDBException {
		return "1.0";
	}

	public void setCollection(Collection arg0) throws XMLDBException {
	}

	public String getProperty(String arg0) throws XMLDBException {
		return null;
	}

	public void setProperty(String arg0, String arg1) throws XMLDBException {
	}

	private XMLResource getIndexResource() throws XMLDBException {
		XMLResource index = null;
		try {
			index = (XMLResource) getSchemasCollection().getResource(INDEX_RESOURCE_NAME);
		} catch (XMLDBException e) {
		}
		if (index == null) {
			index = (XMLResource) schemasCollection.createResource(INDEX_RESOURCE_NAME, "XMLResource");
			index.setContent("<?xml version=\"1.0\" encoding=\"UTF-8\"?><schema-index/>");
			getSchemasCollection().storeResource(index);
		}
		return index;
	}

	private String getSchemaFilename(String targetNamespace) throws XMLDBException {
		if (targetNamespace == null)
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "null is not a valid namespace!");

		XMLResource index = getIndexResource();
		String filename = null;

		Node root = index.getContentAsDOM();

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
		return filename;
	}

	private Collection getSchemasCollection() throws XMLDBException {
		if (schemasCollection == null) {
			Collection parent = getParentCollection();
			while (parent.getParentCollection() != null)
				parent = parent.getParentCollection();

			schemasCollection = parent.getChildCollection("system").getChildCollection("schema");
			if (schemasCollection == null) {
				CollectionManagementService cms =
					(CollectionManagementService) getParentCollection().getService("CollectionManagementService", "1.0");
				if (cms != null) {
					cms.setCollection(parent.getChildCollection("system"));
					schemasCollection = cms.createCollection("schema");
				} else {
					throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Could not get CollectionManagementService.", null);
				}
			}
		}
		return schemasCollection;
	}

	private String findTargetNamespace(String schemaContents) throws XMLDBException {
		String targetNamespace = null;
		DOMParser parser = new DOMParser();
		try {
			parser.parse(new InputSource(new StringReader(schemaContents)));
			Node rootNode = parser.getDocument().getDocumentElement();
			Node targetNamespaceAttr = rootNode.getAttributes().getNamedItem("targetNamespace");
			if (targetNamespaceAttr != null)
				targetNamespace = targetNamespaceAttr.getNodeValue();
		} catch (SAXException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Error parsing schema: " + e.getMessage(), e);
		} catch (IOException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Error parsing schema: " + e.getMessage(), e);
		}
		return targetNamespace;
	}

	public void putSchema(String schemaContents) throws XMLDBException {

		Collection schemasCollection = getSchemasCollection();
		String targetNamespace = findTargetNamespace(schemaContents);
		String filename = getSchemaFilename(targetNamespace);
		Resource schemaResource = null;
		if (filename == null) {
			filename = String.valueOf(System.currentTimeMillis());
			schemaResource = schemasCollection.createResource(filename, "XMLResource");
			addToIndex(targetNamespace, filename);
		} else
			schemaResource = (XMLResource) schemasCollection.getResource(filename);
		schemaResource.setContent(schemaContents);
		schemasCollection.storeResource(schemaResource);
		schemasCollection.close();
	}

	private void addToIndex(String targetNamespace, String filename) throws XMLDBException {
		XMLResource index = getIndexResource();
		Node rootNode = index.getContentAsDOM();
		Document doc = rootNode.getOwnerDocument();
		Element schemaNode = doc.createElement("schema");
//		Attr targetNamespaceAttr = doc.createAttribute("targetNamespace");
//		// jmv: targetNamespaceAttr.setNodeValue(targetNamespace);
//		targetNamespaceAttr.setValue(targetNamespace); // jmv
//		Attr resourceNameAttr = doc.createAttribute("resourceName");
//		resourceNameAttr.setValue(filename); // jmv
//		schemaNode.getAttributes().setNamedItem(targetNamespaceAttr);
//		schemaNode.getAttributes().setNamedItem(resourceNameAttr);
		schemaNode.setAttribute("targetNamespace", targetNamespace);
		schemaNode.setAttribute("resourceName", filename);
		
		rootNode.appendChild(schemaNode);
		index.setContentAsDOM(rootNode);
		getSchemasCollection().storeResource(index); // jmv: this doesn't update the .index document ???!!!
	}

	public XMLResource getSchema(String targetNamespace) throws XMLDBException {
		String filename = getSchemaFilename(targetNamespace);
		if (filename != null)
			return (XMLResource) getSchemasCollection().getResource(filename);
		else
			return null;
	}
	public boolean validateContents(String contents) throws XMLDBException {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			DocumentBuilder docBuilder = factory.newDocumentBuilder();
			ValidationErrorHandler errorHandler = new ValidationErrorHandler();
			docBuilder.setErrorHandler(errorHandler);
			Document document = docBuilder.parse(new StringInputStream(contents));
			Set namespaces = new TreeSet();
			findNamespaces(document.getDocumentElement(), namespaces);
			SchemaService schemaService = (SchemaService) getParentCollection().getService("SchemaService", "1.0");
			ArrayList schemas = new ArrayList();
			LOG.debug("Getting schemas for validation (" + namespaces.size() + "): ");
			for (Iterator i = namespaces.iterator(); i.hasNext();) {
				String namespaceURI = (String) i.next();
				XMLResource resource = schemaService.getSchema(namespaceURI);
				if (resource != null) {
					schemas.add((String) resource.getContent());
					LOG.info(namespaceURI);
				} else
					LOG.warn("No schema for target namespace " + namespaceURI + " found.");
			}

			factory.setValidating(true);
			try {
				factory.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
				InputSource[] schemaSources = new InputSource[schemas.size() + getTransientSchemas().size()];
				int i;
				for (i = 0; i < schemas.size(); i++)
					schemaSources[i] = new InputSource(new StringReader((String) schemas.get(i)));
				for (Iterator iter = getTransientSchemas().iterator(); iter.hasNext(); i++)
					schemaSources[i] = new InputSource(new StringReader((String) iter.next()));
				factory.setAttribute(JAXP_SCHEMA_SOURCE, schemaSources);
			} catch (IllegalArgumentException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Parser does not support JAXP 1.2", e);
			}
			docBuilder = factory.newDocumentBuilder();
			docBuilder.setErrorHandler(errorHandler);
			docBuilder.parse(new StringInputStream(contents));
			if (errorHandler.getErrors().size() > 0)
				throw errorHandler.toException();
			return true;
		} catch (ParserConfigurationException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Parser config error validating contents.", e);
		} catch (SAXException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "SAX error reading contents.", e);
		} catch (IOException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "IO error reading contents", e);
		}
	}

	public boolean validateResource(String id) throws XMLDBException {
		Resource doc = getParentCollection().getResource(id);
		if (("XMLResource".equals(doc.getResourceType())) || ("XMLView".equals(doc.getResourceType()))) {
			XMLResource xmlResource = (XMLResource) doc;
			Node root = xmlResource.getContentAsDOM();
			try {
				return validateContents((String) xmlResource.getContent());
			} catch (XMLDBException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Error validating resource " + id, e);
			}
		} else {
			throw new XMLDBException(
				ErrorCodes.WRONG_CONTENT_TYPE,
				"Can only validate XML documents, but " + id + " is a " + doc.getResourceType(),
				null);
		}
	}

	/**
	 * @param root
	 * @return
	 */
	private void findNamespaces(Node root, Set namespaces) {
		if (root != null) {
			LOG.debug("locating namespace in " + root.getNodeName());
			String namespace = root.getNamespaceURI();
			if (namespace != null && !"".equals(namespace))
				namespaces.add(namespace);
			findNamespaces(root.getFirstChild(), namespaces);
			findNamespaces(root.getNextSibling(), namespaces);
		}
	}

	public Collection getParentCollection() {
		return parentCollection;
	}

	public void setParentCollection(Collection collection) {
		parentCollection = collection;
	}

	/**
	 * @return the attribute by name qname or null if no such attribute is known.
	 */
	public AttributeDecl getAttribute(QName qname) throws XMLDBException {
		String xquery =
			"declare namespace xs=\""
				+ W3C_XML_SCHEMA
				+ "\";"
				+ "/xs:schema[@targetNamespace=\""
				+ qname.getNamespaceURI()
				+ "\"]/xs:attribute[@name=\""
				+ qname.getLocalPart()
				+ "\"]";
		XQueryService service = (XQueryService) getSchemasCollection().getService("XQueryService", "1.0");
		ResourceSet result = service.query(xquery);
		if (result.getSize() == 0)
			return null;
		else if (result.getSize() > 1)
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Found multiple types by name " + qname, null);
		else {
			//return result.getResource(0);
			return null;
		}
	}

	/**
	 * @return the element by name qname or null if no such element is known.
	 */
	public ElementDecl getElement(QName qname) throws XMLDBException {
		String xquery =
			"declare namespace xs=\""
				+ W3C_XML_SCHEMA
				+ "\";"
				+ "/xs:schema[@targetNamespace=\""
				+ qname.getNamespaceURI()
				+ "\"]/xs:element[@name=\""
				+ qname.getLocalPart()
				+ "\"]";
		XQueryService service = (XQueryService) getSchemasCollection().getService("XQueryService", "1.0");
		ResourceSet result = service.query(xquery);
		if (result.getSize() == 0)
			return null;
		else if (result.getSize() > 1)
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Found multiple types by name " + qname, null);
		else {
			//return result.getResource(0);
			return null;
		}
	}

	/**
	 * @return the type-definition by name qname or null if no such type-definition is known.
	 */
	public XMLType getType(QName qname) throws XMLDBException {
		/*String xquery = "declare namespace xs=\"" + W3C_XML_SCHEMA + "\";" + 
			"/xs:schema[@targetNamespace=\"" + qname.getNamespaceURI() + 
			"\"]/(xs:complexType|xs:simpleType)[@name=\"" + qname.getLocalPart() + "\"]";
		XQueryService service = (XQueryService) getSchemasCollection().getService("XQueryService", "1.0");
		ResourceSet result = service.query(xquery);
		if (result.getSize() == 0) return null;
		else if (result.getSize() > 1) throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Found multiple types by name " + qname, null);
		else {
			//return result.getResource(0);
			return new Object();			
		}*/
		XMLResource resource = getSchema(qname.getNamespaceURI());
		try {
			Schema schema = (new SchemaReader((String) resource.getContent())).read();
			return schema.getType(qname.getLocalPart());
		} catch (IOException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Error reading schema information for target namespace: " + qname.getNamespaceURI(), e);
		}
	}

	/**
	 *
	 */

	public boolean isKnownNamespace(String namespaceURI) throws XMLDBException {
		return getSchema(namespaceURI) != null;
	}
	
	public void registerTransientSchema(String schema) throws XMLDBException {
		getTransientSchemas().add(schema);
	}

	/**
	 * @return
	 */
	public ArrayList getTransientSchemas() {
		if (transientSchemas == null) {
			transientSchemas = new ArrayList();
		}
		return transientSchemas;
	}

}
