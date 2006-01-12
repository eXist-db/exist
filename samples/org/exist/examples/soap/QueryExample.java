package org.exist.examples.soap;

import org.exist.soap.*;
import java.io.*;

public class QueryExample {

    /**
     * Read the xquery file and return as string.
     */
    protected static String readFile(String file) throws IOException {
    	BufferedReader f = new BufferedReader(new FileReader(file));
    	String line;
    	StringBuffer xml = new StringBuffer();
    	while((line = f.readLine()) != null) {
    		xml.append(line);
            xml.append('\n');
        }
    	f.close();
    	return xml.toString();
    }

    public static void main( String[] args ) throws Exception {
        if(args.length != 1) {
            System.out.println("Usage: org.exist.examples.soap.QueryExample xquery-file");
            System.exit(0);
        }
        
    	String xquery = readFile(args[0]);
    	
        // we send the data in base64 encoding to avoid
        // difficulties with XML special chars in the query.
        byte[] queryData;
        try {
            queryData = xquery.getBytes("UTF-8");
        } catch(UnsupportedEncodingException e) {
            // should never happen
            queryData = xquery.getBytes();
        }

        QueryService service = new QueryServiceLocator();
        Query query = service.getQuery();

		// connect to the database
		String sessionId = query.connect("guest", "guest");
		
		// execute the query
        QueryResponse resp =
        	query.xquery( sessionId, queryData );
        System.out.println( "found: " + resp.getHits() );
		if(resp.getHits() == 0)	// nothing found
			return;
        
        // retrieve first 10 results, indenting is on, xinclude is off, matches in elements 
        // are highlighted (tagged)
		byte[][] hits = query.retrieveData( sessionId, 1, 10,
			true, false, "elements").getElements();
		for(int i = 0; i < hits.length; i++) {
			System.out.println(new String(hits[i], "UTF-8"));
		}
		
		// close the connection (release session on the server)
		query.disconnect(sessionId);
    }
}

