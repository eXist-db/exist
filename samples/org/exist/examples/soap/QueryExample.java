package org.exist.examples.soap;

import org.exist.soap.*;

public class QueryExample {

    public static void main( String[] args ) throws Exception {
        QueryService service = new QueryServiceLocator();
        Query query = service.getQuery();

        QueryResponse resp =
                query.query( "//SPEECH[LINE &= 'cursed spite']" );
        System.out.println( "found: " + resp.getHits() );
		if(resp.getHits() == 0)
			return;
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
        byte[] record =
                query.retrieve( resp.getResultSetId(), 1,
                "ISO-8859-1",
                true );
        System.out.println( new String( record, "ISO-8859-1" ) );
    }
}

