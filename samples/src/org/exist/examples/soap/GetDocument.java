package org.exist.examples.soap;

import org.exist.soap.Query;
import org.exist.soap.QueryService;
import org.exist.soap.QueryServiceLocator;
import org.exist.storage.DBBroker;

public class GetDocument {

    public static void main( String[] args ) throws Exception {
        QueryService service = new QueryServiceLocator();
        Query query = service.getQuery();
		String session = query.connect("guest", "guest");
        
		byte[] data = query.getResourceData(session, 
			DBBroker.ROOT_COLLECTION + "/shakespeare/plays/hamlet.xml",
			true, false, false);
		System.out.println(new String(data, "UTF-8"));
		query.disconnect(session);
    }
}

