/*
 * Created on Apr 10, 2004
 *
 */
package org.exist.schema;

import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.xmldb.LocalCollection;

/**
 * @author seb
 */
public class LocalSchemaService extends GenericSchemaService {


	public LocalSchemaService(User user, BrokerPool pool, LocalCollection collection) {
		super(collection);
	}

}
