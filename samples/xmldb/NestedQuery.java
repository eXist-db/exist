
package samples.xmldb;

import org.xmldb.api.*;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;
import org.w3c.dom.Element;
import org.apache.xml.serialize.*;

/**
 *  Perform a nested query on the root-Collection.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    20. September 2002
 */
public class NestedQuery {

    protected static String URI = "xmldb:exist:///db";

    protected static String driver = "org.exist.xmldb.DatabaseImpl";

    protected static String query1 = "document(*)//rdf:Description[dc:subject&='computer']";
    protected static String query2 = "/dc:title";
    
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
            service.setProperty( "pretty", "true" );
            service.setProperty( "encoding", "ISO-8859-1" );
            service.setProperty( "create-container-elements", "false");
            // execute first query
            ResourceSet result = service.query( query1 );
            XMLResource resource;
            ResourceSet result2;
            String xml;
            System.out.println(query1 + " found: " + result.getSize());
            // iterate through the results and execute the second query
            // using the current result node as context
            for ( int i = 0; i < (int) result.getSize(); i++ ) {
                resource = (XMLResource) result.getResource( (long) i );
                result2 = ((org.exist.xmldb.XPathQueryServiceImpl)service)
                    .query( resource, query2 );
                // print the results found by the second query
                for( int j = 0; j < (int)result2.getSize(); j++ ) {
                    xml = (String)result2.getResource( (long)j ).getContent();
                    System.out.println( xml );
                }
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }


    /**  Description of the Method */
    protected static void usage() {
        System.out.println( "usage: samples.APISearch [ collection ] xpath-query" );
        System.exit( 0 );
    }
}

