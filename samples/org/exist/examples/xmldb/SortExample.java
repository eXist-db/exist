package org.exist.examples.xmldb;

import org.exist.xmldb.XPathQueryServiceImpl;
import org.xmldb.api.*;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;

/**
 *  Do a query on the root-Collection To run this example enter: bin/run.sh
 *  samples.APISearch xpath-query in the root directory of the distribution.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    20. September 2002
 */
public class SortExample {

    protected static String URI = "xmldb:exist://localhost:8080/exist/xmlrpc";

    protected static String driver = "org.exist.xmldb.DatabaseImpl";

	protected static String query = "//SPEECH[LINE &= 'marriage']";
	protected static String sortBy = "/SPEAKER[1]";
	
    public static void main( String args[] ) {
    	if(args.length == 2) {
    		query = args[0];
    		sortBy = args[1];
    	}
    	System.out.println("Query: " + query);
    	System.out.println("Sort-by: " + sortBy);
        try {
            Class cl = Class.forName( driver );
            Database database = (Database) cl.newInstance();
            database.setProperty( "create-database", "true" );
            DatabaseManager.registerDatabase( database );
            // get root-collection
            Collection col =
                DatabaseManager.getCollection( URI + "/db" );
            // get query-service
            XPathQueryServiceImpl service = (XPathQueryServiceImpl) 
            	col.getService( "XPathQueryService", "1.0" );
            // set pretty-printing on
            service.setProperty( "pretty", "true" );
            service.setProperty( "encoding", "ISO-8859-1" );
            // execute query and get results in ResourceSet
            ResourceSet result = service.query( query, sortBy );
            // create iterator
            for(ResourceIterator i = result.getIterator(); i.hasMoreResources(); ) {
                XMLResource resource = (XMLResource) i.nextResource();
                String xml = resource.getContent().toString();
                System.out.println( xml );
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
}

