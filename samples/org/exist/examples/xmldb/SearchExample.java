package org.exist.examples.xmldb;

import javax.xml.transform.OutputKeys;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

/**
 *  Do a query on the root-Collection.
 *  To run this example enter: 
 * 
 *  bin/run.sh examples.xmldb.SearchExample xpath-query
 *  
 *  in the root directory of the distribution.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    20. September 2002
 */
public class SearchExample {

    protected static String URI = "xmldb:exist://localhost:8080/exist/xmlrpc";
    //protected static String URI = "xmldb:exist://";

    protected static String driver = "org.exist.xmldb.DatabaseImpl";

    public static void main( String args[] ) {
        try {
            if ( args.length < 1 )
                usage();

            Class cl = Class.forName( driver );
            Database database = (Database) cl.newInstance();
            database.setProperty( "create-database", "true" );
            DatabaseManager.registerDatabase( database );
            String collection = "/db";
            String query;
            if ( args.length == 2 ) {
                // if collection does not start with "/" add it
                collection = ( args[0].charAt( 0 ) == '/' ) ? args[0] : "/" + args[0];
                query = args[1];
            }
            else
                query = args[0];
            
            // get root-collection
            Collection col =
                DatabaseManager.getCollection( URI + collection );
            // get query-service
            XPathQueryService service =
                (XPathQueryService) col.getService( "XPathQueryService", "1.0" );
            // set pretty-printing on
            service.setProperty( OutputKeys.INDENT, "yes" );
            service.setProperty( OutputKeys.ENCODING, "ISO-8859-1" );

            long start = System.currentTimeMillis();
            // execute query and get results in ResourceSet
            ResourceSet result = service.query( query );

            long qtime = System.currentTimeMillis() - start;
            start = System.currentTimeMillis();

            for ( int i = 0; i < (int) result.getSize(); i++ ) {
                XMLResource resource = (XMLResource) result.getResource( (long) i ); 
                System.out.println( resource.getContent().toString() );
            }
            long rtime = System.currentTimeMillis() - start;
			System.out.println("query:         " + query);
			System.out.println("hits:          " + result.getSize());
            System.out.println("query time:    " + qtime);
            System.out.println("retrieve time: " + rtime);
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }


    protected static void usage() {
        System.out.println( "usage: examples.xmldb.ExampleSearch [ collection ] xpath-query" );
        System.exit( 0 );
    }
}

