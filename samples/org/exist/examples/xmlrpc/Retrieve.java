package org.exist.examples.xmlrpc;

import org.apache.xmlrpc.*;
import java.util.Vector;
import java.io.UnsupportedEncodingException;

/**
 *  Retrieve a document from the database using XMLRPC.
 *
 *  @author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *  @created    August 1, 2002
 */
public class Retrieve {

    protected final static String uri = "http://localhost:8080/exist/xmlrpc";

    protected static void usage() {
        System.out.println( "usage: org.exist.examples.xmlrpc.Retrieve path-to-document" );
        System.exit( 0 );
    }

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

