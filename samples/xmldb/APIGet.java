
package samples.xmldb;

import org.xmldb.api.*;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.apache.xml.serialize.*;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeImpl;

/**
 *  Retrieve a document from the repository. To run this example enter:
 *  bin/run.sh samples.APISearch xpath-query in the root directory of the
 *  distribution.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    10. Juli 2002
 */
public class APIGet {

    protected static String driver = "org.exist.xmldb.DatabaseImpl";

    protected static String URI = "xmldb:exist://";


    /**  Description of the Method */
    protected static void usage() {
        System.out.println( "usage: samples.APIGet [ collection ] docName" );
        System.exit( 0 );
    }


    /**
     *  Description of the Method
     *
     *@param  args           Description of the Parameter
     *@exception  Exception  Description of the Exception
     */
    public static void main( String args[] ) throws Exception {
        if ( args.length < 1 ) {
            usage();
        }

        String collection = "/db";

        String file;

        if ( args.length == 2 ) {
            // if collection does not start with "/" add it
            collection = ( args[0].charAt( 0 ) == '/' ) ? args[0] : "/db/" + args[0];
            file = args[1];
        } else {
            file = args[0];
        }

        // initialize database drivers
        Class cl = Class.forName( driver );
        Database database = (Database) cl.newInstance();
        database.setProperty( "create-database", "true" );
        DatabaseManager.registerDatabase( database );

        // get the collection
        Collection col =
                DatabaseManager.getCollection( URI + collection );
        col.setProperty( "pretty", "true" );
        col.setProperty( "encoding", "ISO-8859-1" );

        // get the resource
        XMLResource res = (XMLResource) col.getResource( file );

        if ( res == null ) {
            System.err.println( "could not retrieve document " + file + "!" );
            return;
        }
        Element root = (Element)res.getContentAsDOM();
		NodeList children = root.getElementsByTagName("PERSONAE");
		for(int i = 0; i < children.getLength(); i++) {
			ElementImpl child = (ElementImpl)children.item(i);
			ElementImpl elem = new ElementImpl("PERSONA");
			child.appendChild(elem);
			System.out.println(child);
		}
    }
}

