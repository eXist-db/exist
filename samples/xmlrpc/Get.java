
package samples.xmlrpc;

import org.apache.xmlrpc.*;
import java.util.Vector;
import java.io.UnsupportedEncodingException;

/**
 *  retrieve a document from the repository using XML-RPC.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    August 1, 2002
 */
public class Get {

    protected final static String uri = "http://localhost:8080/exist/xmlrpc";


    /**  Description of the Method */
    protected static void usage() {
        System.out.println( "usage: samples.Get docName" );
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
        String encoding = "ISO-8859-1";
        XmlRpcClient xmlrpc = new XmlRpcClientLite( uri );
        Vector params = new Vector();
        params.addElement( args[0] );
        params.addElement( encoding );
        params.addElement( new Integer( 1 ) );
        byte[] data = (byte[]) xmlrpc.execute( "getDocument", params );
        String xml;
        try {
            xml = new String( data, encoding );
        } catch ( UnsupportedEncodingException uee ) {
            xml = new String( data );
        }
        System.out.println( xml );
    }
}

