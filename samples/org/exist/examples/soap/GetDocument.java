package org.exist.examples.soap;

import org.exist.soap.*;

public class GetDocument {

    public static void main( String[] args ) throws Exception {
        QueryService service = new QueryServiceLocator();
        Query query = service.getQuery();
		String session = query.connect("guest", "guest");
		String data = query.getResource(session, 
			"/db/shakespeare/plays/much_ado.xml",
			true, false);
		System.out.println(data);
		query.disconnect(session);
    }
}

