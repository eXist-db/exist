/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * Created on Apr 10, 2004; Sebastian Bossung, TUHH
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
import org.exolab.castor.xml.schema.AttributeDecl;
import org.exolab.castor.xml.schema.ElementDecl;
import org.exolab.castor.xml.schema.Schema;
import org.exolab.castor.xml.schema.XMLType;
import org.exolab.castor.xml.schema.reader.SchemaReader;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XUpdateQueryService;

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

	protected final static String INDEX_COLLECTION_NAME = "/db/system/schema/"; 
	protected final static String INDEX_RESOURCE_NAME = ".index";

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

	/**
	 * Test whether the index resource ".index" already exists. If not, creates it and
	 * fills it with skeleton contents. Returns the (possible newly created) resource.
	 * @return
	 * @throws XMLDBException
	 */
	protected XMLResource testAndCreateIndexResource() throws XMLDBException {
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


	protected Collection getSchemasCollection() throws XMLDBException {
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

	/**
	 * Finds the target namespace in the given schema.
	 * @param schemaContents
	 * @return
	 * @throws XMLDBException
	 */
	protected String findTargetNamespace(String schemaContents) throws XMLDBException {
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

	/**
	 * Add a schema to the schema store. The schema must have a target namespace because it can otherwise not
	 * be indexed.
	 */
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
	
	/**
	 * Retrieves the schema as an XML resources.
	 * @return the schema with targetNamespace or null if that schema is not known.
	 */
	public XMLResource getSchema(String targetNamespace) throws XMLDBException {
		String filename = getSchemaFilename(targetNamespace);
		if (filename != null)
			return (XMLResource) getSchemasCollection().getResource(filename);
		else
			return null;
	}
	
	/**
	 * Validates the passed contents. Schemas are automatically obtained from the schema store. You can
	 * add transient ("temporary") schemas with the <code>registerTransientSchema</code> method.
	 * @throws XMLDBException if a database error occurs or the contents contains validation errors (these are
	 * wrapped in XMLDBExceptions).
	 */
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

	/**
	 * Validates a resource given its id. Uses <code>validateContents</code>.
	 */
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
	 * Searches an instance document for namespaces. All discovered namespaces are added to the 
	 * <code>namespaces</code> Set. 
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
		return getCastorSchema(qname.getNamespaceURI()).getAttribute(qname.getLocalPart());
	}

	/**
	 * @return the element by name qname or null if no such element is known.
	 */
	public ElementDecl getElement(QName qname) throws XMLDBException {
		return getCastorSchema(qname.getNamespaceURI()).getElementDecl(qname.getLocalPart());
	}

	/**
	 * @return the type-definition by name qname or null if no such type-definition is known.
	 */
	public XMLType getType(QName qname) throws XMLDBException {
		return getCastorSchema(qname.getNamespaceURI()).getType(qname.getLocalPart());
	}

	private Schema getCastorSchema(String namespaceURI) throws XMLDBException {
		XMLResource resource = getSchema(namespaceURI);
		try {
			Schema schema = (new SchemaReader((String) resource.getContent())).read();
			return schema;
		} catch (IOException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Error reading schema information for target namespace: " + namespaceURI, e);
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

  XUpdateQueryService updateService = null;

  XPathQueryService queryService = null;

  protected String getDocumentExpression() {
    return "document('" + INDEX_COLLECTION_NAME + INDEX_RESOURCE_NAME + "')";
  }

  protected String getRetrieveIndexRecordQuery(String targetNamespace) {
    return "/schema-index/schema[@targetNamespace=\"" + targetNamespace + "\"]";
  }

  protected String getAppendSchemaXUpdate(String targetNamespace, String resourceName) {
    return "<xupdate:modifications version=\"1.0\" xmlns:xupdate=\"http://www.xmldb.org/xupdate\">"
        + "<xupdate:append select=\"" + getDocumentExpression() + "/schema-index\">"
        + "<xupdate:element name=\"schema\">" + "<xupdate:attribute name=\"targetNamespace\">" + targetNamespace
        + "</xupdate:attribute>" + "<xupdate:attribute name=\"resourceName\">" + resourceName + "</xupdate:attribute>"
        + "</xupdate:element>" + "</xupdate:append>" + "</xupdate:modifications>";
  }
  
	/**
	 * Retrieve the filename of the resource that stores the schema for <code>targetNamespace</code>
	 * @param targetNamespace
	 * @return the resource name or null if the schema is not in the index.
	 * @throws XMLDBException
	 */
  protected String getSchemaFilename(String targetNamespace) throws XMLDBException {
    if (targetNamespace == null)
      throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "null is not a valid namespace!");
    // make sure, the index resource exists:
    testAndCreateIndexResource();
    // try to find the name of the resource that stores the respective schema:  
    String query = getRetrieveIndexRecordQuery(targetNamespace) + "/@resourceName";
    ResourceSet set = getXQueryService().queryResource(INDEX_RESOURCE_NAME, query);
    if (set.getSize() == 1) {
      ResourceIterator iterator = set.getIterator();
      return iterator.nextResource().getContent().toString();
    } else if (set.getSize() == 0) {
      return null;
    } else
      throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
          "Multiple index entries for one targetNamespace in the schema index. The index is corrupt.");
  }

	/**
	 * Insert a new element in the schema-index. This method is called only if a new schema with a previously
	 * unknown target namespace is indexed. Known schemas are indexed by updating the existent resource.  
	 * @param targetNamespace of the schema
	 * @param resourceName the name of the resource that stores the schema
	 * @throws XMLDBException
	 *
   */
  protected void addToIndex(String targetNamespace, String resourceName) throws XMLDBException {
    getXUpdateService().update(getAppendSchemaXUpdate(targetNamespace, resourceName));
  }

  protected XUpdateQueryService getXUpdateService() throws XMLDBException {
    if (updateService == null)
      updateService = (XUpdateQueryService) getSchemasCollection().getService("XUpdateQueryService", "1.0");
    ;
    return updateService;
  }

  protected XPathQueryService getXQueryService() throws XMLDBException {
    if (queryService == null)
      queryService = (XPathQueryService) getSchemasCollection().getService("XPathQueryService", "1.0");
    ;
    return queryService;
  }

}
