package org.exist.examples.soap;

import org.exist.soap.Query;
import org.exist.soap.QueryService;
import org.exist.soap.QueryServiceLocator;

public class GetDocument {

    public static void main( String[] args ) throws Exception {
        QueryService service = new QueryServiceLocator();
        Query query = service.getQuery();
		String session = query.connect("guest", "guest");
        
		byte[] data = query.getResourceData(session, 
			"/db/shakespeare/plays/hamlet.xml",
			true, false, false);
		System.out.println(new String(data, "UTF-8"));
		query.disconnect(session);
    }
}

