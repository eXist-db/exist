package org.exist.examples.xmlrpc;

import java.util.Vector;
import java.util.Hashtable;

import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.exist.storage.serializers.EXistOutputKeys;
import javax.xml.transform.OutputKeys;

/**
 *  Retrieve a document from the database using XMLRPC.
 *
 *  @author     Giulio Valentino
 */
public class MyRetrieve {

    protected final static String uri = "http://localhost:8080/exist/xmlrpc";

    protected static void usage() {
        System.out.println( "usage: org.exist.examples.xmlrpc.Retrieve path-to-document" );
        System.exit( 0 );
    }

    public static void main( String args[] ) throws Exception {
        if ( args.length < 1 ) {
            usage();
        }
        XmlRpc.setEncoding("UTF-8");
        XmlRpcClient xmlrpc = new XmlRpcClientLite( uri );
        Vector params = new Vector();

        params.addElement( args[0] );

        Hashtable parametri = new Hashtable();
        parametri.put(OutputKeys.INDENT,"yes");
        parametri.put(OutputKeys.ENCODING,"UTF-8");
        parametri.put(EXistOutputKeys.EXPAND_XINCLUDES,"yes");
        parametri.put(EXistOutputKeys.STYLESHEET,"file:samples/shakespeare/shakes.xsl");

        Hashtable paramstyle = new Hashtable();
        paramstyle.put("parma_name","param_value");  //param to pass at stylesheet

        parametri.put(EXistOutputKeys.STYLESHEET_PARAM,paramstyle);


        params.addElement( parametri );

        byte[] data = (byte[]) xmlrpc.execute( "getDocument", params ) ;
        String xml = new String( data );
        System.out.println( xml );
    }
}

