package org.exist.examples.xmldb;

import javax.xml.transform.OutputKeys;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

/**
 *  Performs a nested query on the root-Collection.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    20. September 2002
 */
public class NestedQuery {

    protected static String URI = "xmldb:exist://localhost:8080/exist/xmlrpc/db";

    protected static String driver = "org.exist.xmldb.DatabaseImpl";

    protected static String query1 = "document()//SPEECH[LINE &= 'corrupt*']";
    protected static String query2 = "ancestor::SCENE/TITLE";
    
    public static void main( String args[] ) {
        try {
            Class cl = Class.forName( driver );
            Database database = (Database) cl.newInstance();
            database.setProperty( "create-database", "true" );
            DatabaseManager.registerDatabase( database );
            
            // get collection
            Collection col =
                DatabaseManager.getCollection( URI );
            // get query-service
            XPathQueryService service =
                (XPathQueryService) col.getService( "XPathQueryService", "1.0" );
                
            // set properties
            service.setProperty( OutputKeys.INDENT, "yes" );
            service.setProperty( OutputKeys.ENCODING, "ISO-8859-1" );
            
            // execute first query
            ResourceSet result = service.query( query1 );
			System.out.println(query1 + " found: " + result.getSize());
			
            XMLResource resource;
            ResourceSet result2;
            String xml;
            
            // iterate through the results and execute the second query
            // using the current result node as context
            for (int i = 0; i < 10; i++) {
            //for ( int i = 0; i < (int) result.getSize(); i++ ) {
                resource = (XMLResource) result.getResource( (long) i );
                result2 = ((org.exist.xmldb.XPathQueryServiceImpl)service)
                    .query( resource, query2 );

                // print the results found by the second query
                for( int j = 0; j < (int)result2.getSize(); j++ ) {
                    xml = (String)result2.getResource( (long)j ).getContent();
                    System.out.println( "Scene: " + xml );
                }
				System.out.println((String)resource.getContent());
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }

}
