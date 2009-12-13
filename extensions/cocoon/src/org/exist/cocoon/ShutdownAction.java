package org.exist.cocoon;

import java.util.HashMap;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.thread.ThreadSafe;
import org.apache.cocoon.acting.ServiceableAction;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Redirector;
import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.SourceResolver;
import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * Cocoon action to shut down a running database instance of eXist.
 * 
 * @author wolf
 */
public class ShutdownAction extends ServiceableAction implements ThreadSafe {

	public ShutdownAction() {
		super();
	}

	/* (non-Javadoc)
	 * @see org.apache.cocoon.acting.Action#act(org.apache.cocoon.environment.Redirector, org.apache.cocoon.environment.SourceResolver, java.util.Map, java.lang.String, org.apache.avalon.framework.parameters.Parameters)
	 */
	public Map act(
		Redirector redirector,
		SourceResolver resolver,
		Map objectModel,
		String source,
		Parameters parameters)
		throws Exception {
		Map result = new HashMap();
		Request request = ObjectModelHelper.getRequest( objectModel );
		if ( request == null ) {
			getLogger().error( "No request!" );
			return null;
		}
		String user = request.getParameter("user");
		String passwd = request.getParameter("password");
		if(user == null) {
			getLogger().error("no user specified!");
			return null;
		}
		if ( source == null ) {
			getLogger().debug( "No source specified! Using default." );
			source = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
		}
		Collection collection = null;
		// try to access collection specified in source
		 try {
			 collection =
				 DatabaseManager.getCollection( source, user, passwd );
		 } catch ( XMLDBException e ) {
			 getLogger().error( "login denied: " + e.getMessage() );
			 return null;
		 }
		 try {
			DatabaseInstanceManager mgr = (DatabaseInstanceManager)
			 	collection.getService("DatabaseInstanceManager", "1.0");
			 if(mgr == null) {
			 	getLogger().error("access to DatabaseInstanceManager failed");
			 	return null;
			 }
			 mgr.shutdown();
		} catch (XMLDBException e) {
			getLogger().warn("An error occurred: " + e.getMessage(), e);
			return null;
		}
		 return result;
	}
}
