
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
public class APISearch {

    protected static String URI = "xmldb:exist://";

    protected static String driver = "org.exist.xmldb.DatabaseImpl";


    /**
     *  Description of the Method
     *
     *@param  args           Description of the Parameter
     */
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
            service.setProperty( "pretty", "true" );
            service.setProperty( "encoding", "ISO-8859-1" );

            long start = System.currentTimeMillis();
            // execute query and get results in ResourceSet
            ResourceSet result = service.query( query );
            // create iterator
            //ResourceIterator i = result.getIterator();
            long qtime = System.currentTimeMillis() - start;
            start = System.currentTimeMillis();

            for ( int i = 0; i < (int) result.getSize(); i++ ) {
                XMLResource resource = (XMLResource) result.getResource( (long) i );
                String xml = resource.getContent().toString();
                System.out.println( xml );
            }
            long rtime = System.currentTimeMillis() - start;
            System.out.println( "query time:    " + qtime );
            System.out.println( "retrieve time: " + rtime );
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

