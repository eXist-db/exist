package org.exist.examples.soap;

import org.exist.soap.*;

public class QueryExample {

    public static void main( String[] args ) throws Exception {
    	String xpath = args.length > 0 ? args[0] : "//SPEECH[LINE &= 'curse*']";
    	 
        QueryService service = new QueryServiceLocator();
        Query query = service.getQuery();

		// connect to the database
		String sessionId = query.connect("guest", "guest");
		
		// execute the query
        QueryResponse resp =
        	query.query( sessionId, xpath );
        System.out.println( "found: " + resp.getHits() );
		if(resp.getHits() == 0)	// nothing found
			return;
			
		// iterate through collections and print hits for each document
        QueryResponseCollection collections[] = resp.getCollections();
        for ( int i = 0; i < collections.length; i++ ) {
            System.out.println( "Collection: " +
                    collections[i].getCollectionName() );
            QueryResponseDocument documents[] = collections[i].getDocuments();
            for ( int j = 0; j < documents.length; j++ ) {
                System.out.println( "\t" + documents[j].getDocumentName() +
                        "\t" + documents[j].getHitCount() );
            }

        }
        
        // retrieve first 10 results, indenting is on, xinclude is off, matches in elements 
        // are highlighted (tagged)
		String[] hits = query.retrieve( sessionId, 1, 10,
			true, false, "elements");
		for(int i = 0; i < hits.length; i++) {
			System.out.println(hits[i]);
		}
		
		// close the connection (release session on the server)
		query.disconnect(sessionId);
    }
}

