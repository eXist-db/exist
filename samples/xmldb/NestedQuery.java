
package samples.xmldb;

import org.xmldb.api.*;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;
import org.w3c.dom.Element;
import org.apache.xml.serialize.*;

/**
 *  Do a query on the root-Collection To run this example enter: bin/run.sh
 *  samples.APISearch xpath-query in the root directory of the distribution.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    20. September 2002
 */
public class NestedQuery {

    protected static String URI = "xmldb:exist:///db";

    protected static String driver = "org.exist.xmldb.DatabaseImpl";

    protected static String query1 = "document(*)/rdf:RDF/sn:WebPage";
    protected static String query2 = "@dc:title";
    
    /**
     *  Description of the Method
     *
     *@param  args           Description of the Parameter
     */
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
            // set pretty-printing on
            service.setProperty( "pretty", "true" );
            service.setProperty( "encoding", "ISO-8859-1" );

            // execute query and get results in ResourceSet
            ResourceSet result = service.query( query1 );
            XMLResource resource;
            ResourceSet result2;
            String xml;
            System.out.println(query1 + " found: " + result.getSize());
            for ( int i = 0; i < (int) result.getSize(); i++ ) {
                resource = (XMLResource) result.getResource( (long) i );
                result2 = 
                    ((org.exist.xmldb.XPathQueryServiceImpl)service).query( resource, query2 );
                System.out.println(query2 + " found: " + result2.getSize());
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

