/*
 * Created on Apr 10, 2004
 */
package org.exist.schema;

import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author seb
 */
public interface SchemaService extends Service, SchemaAccess {
	/** find the whole schema as an XMLResource */
	XMLResource getSchema(String targetNamespace) throws XMLDBException;
	/** Stores a new schema given its contents */
	void putSchema(String schemaContents) throws XMLDBException;
	/** Validates a resource in the current collection */
	boolean validateResource(String id) throws XMLDBException;
	/** Validates a resource given its contents */
	boolean validateContents(String contents) throws XMLDBException;
	/** Add a schema on-the-fly. This schema will not be made persistent. This is usefull to validate
	 * documents where one knows that a schema is not in the schema store.
	 * @param schema
	 * @throws XMLDBException
	 */
	void registerTransientSchema(String schema) throws XMLDBException;
	/**
	 * Delete and recreate the index file based on the schema resources stored in the 
	 * /db/system/schema collection
	 * @throws XMLDBException
	 */
	void rebuildIndex() throws XMLDBException;

}
