
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 *  $Id$
 */
package org.exist;
import gnu.getopt.Getopt;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import javax.xml.parsers.*;
import org.apache.log4j.PropertyConfigurator;
import org.apache.xmlrpc.*;
import org.apache.xmlrpc.secure.*;
import org.exist.dom.*;

import org.exist.parser.*;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.*;
import org.exist.storage.serializers.Serializer;
import org.exist.util.*;
import org.exist.xpath.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 *  This is a simple command line interface to exist. It can be used to parse a
 *  file or all files in a directory and to query a local or remote database.
 *  Call it with -h to get a list of options.
 *
 *@author     wolf
 *@created    28. Mai 2002
 */
public class CommandLine {
    protected final static int BENCH = 4;
    protected final static int GET = 2;
    protected final static int PARSE = 1;

    protected final static int QUERY = 0;
    protected final static int REMOVE = 3;
    protected final static int REMOVE_COLLECTION = 5;
    DBBroker broker;
    XmlRpcClient client;
    String collection = null;
    String dataDir = null;
    String database = "http://localhost:8081";
    String delCollection = null;
    String delDocument = null;
    String encoding = "UTF-8";
	User admin = new User("admin", "", "admin");
    int howmany = 15;
    boolean indent = false;
    String listCollection = null;
    String parseFile = null;
    String pathSep = System.getProperty( "file.separator", "/" );
    ProgressBar progress;
    String query = null;
    String queryFile = null;
    boolean quiet = false;
    boolean secure = false;
    String sortExpr = null;
    boolean sync = false;
    URL url = null;
    String viewDocument = null;
    String xslStyle = null;
	BrokerPool pool = null;

    /**
     *  Constructor for the CommandLine object
     *
     *@param  args  Description of the Parameter
     */
    public CommandLine( String[] args ) {
        Getopt opt = new Getopt( "exist.CommandLine", args,
            "hFx:qs:lpb:e:n:f:d:r:g:c:iR:" );
        int c;
        String arg;
        File f;
        int command = QUERY;
        while ( ( c = opt.getopt() ) != -1 )
            switch ( c ) {
                case 'q':
                    quiet = true;
                    break;
                case 'h':
                    printNotice();
                    printUsage();
                    return;
                case 'r':
                    // remove document
                    delDocument = opt.getOptarg();
                    command = REMOVE;
                    break;
                case 'R':
                    // remove collection
                    delCollection = opt.getOptarg();
                    command = REMOVE_COLLECTION;
                    break;
                case 'n':
                    arg = opt.getOptarg();
                    try {
                        howmany = Integer.parseInt( arg );
                    } catch ( NumberFormatException e ) {
                        System.err.println( "option -n requires int parameter" );
                        printUsage();
                        return;
                    }
                    break;
                case 's':
                    sortExpr = opt.getOptarg();
                    break;
                case 'x':
                    xslStyle = opt.getOptarg();
                    break;
                case 'i':
                    // pretty print results:
                    indent = true;
                    break;
                case 'e':
                    // set encoding:
                    encoding = opt.getOptarg();
                    break;
                case 'D':
                    // set data directory
                    dataDir = opt.getOptarg();
                    break;
                case 'b':
                    command = BENCH;
                    parseFile = opt.getOptarg();
                    break;
                case 'g':
                    command = GET;
                    viewDocument = opt.getOptarg();
                    break;
                case 'd':
                    // database uri specified
                    database = opt.getOptarg();
                    break;
                case 'l':
                    // use local instance of eXist broker:
                    database = null;
                    break;
                case 'p':
                    command = PARSE;
                    break;
                case 'c':
                    // collection specified
                    collection = opt.getOptarg();
                    break;
                case 'F':
                    sync = false;
                    break;
            }

        if ( !quiet )
            printNotice();

        try {
            XmlRpc.setEncoding( "UTF-8" );
            String home = System.getProperty( "exist.home" );
            if ( home == null )
                home = System.getProperty( "user.dir" );

            if ( !quiet )
                System.out.println( "loading configuration from " +
                    home + pathSep + "conf.xml" );

            Configuration config = new Configuration( home + pathSep + "conf.xml" );
            if ( dataDir != null )
                // rewrite data directory
                config.setProperty( "db-connection.data-dir", dataDir );

            // if no database-url is given, create a local broker instance
            if ( database == null ) {
                if ( !quiet )
                    System.out.println( "starting local instance of broker ..." );
				BrokerPool.configure(1, 10, config);
				pool = BrokerPool.getInstance();
                broker = pool.get();
                if ( indent )
                    System.out.println( "\n> pretty printing is turned off when using local broker! <\n" );

            }
            else
                client = new XmlRpcClient( database );

            switch ( command ) {
                case REMOVE:
                    if ( !quiet )
                        System.out.println( "removing document " + delDocument );

                    if ( collection != null )
                        delDocument = collection + "/" + delDocument;

                    if ( database == null )
                        broker.removeDocument( admin, delDocument );
                    else {
                        Vector params = new Vector();
                        params.addElement( delDocument );
                        try {
                            client.execute( "remove", params );
                        } catch ( Exception e ) {
                            System.out.println( "could not remove document!" );
                            System.out.println( e );
                            return;
                        }
                    }
                    break;
                case REMOVE_COLLECTION:
                    if ( !quiet )
                        System.out.println( "removing collection " +
                            delCollection );

                    if ( database == null )
                        broker.removeCollection( admin, delCollection );
                    else {
                        Vector params = new Vector();
                        params.addElement( delCollection );
                        try {
                            client.execute( "removeCollection", params );
                        } catch ( Exception e ) {
                            System.out.println( "could not remove document!" );
                            System.out.println( e );
                            return;
                        }
                    }
                    break;
                case PARSE:
                    if ( opt.getOptind() < args.length ) {
                        parseFile = args[opt.getOptind()];
                        f = new File( parseFile );
                        if ( !f.canRead() ) {
                            System.err.println(
                                "cannot read xml file or directory: " +
                                parseFile );
                            printUsage();
                            return;
                        }
                        parse();
                    }
                    else {
                        System.err.println( "please specify a file or directory to parse" );
                        printUsage();
                        return;
                    }
                    break;
                case GET:
                    if ( collection != null )
                        viewDocument = collection + "/" + viewDocument;

                    getDocument( viewDocument );
                    break;
                case QUERY:
                    if ( opt.getOptind() < args.length )
                        query = args[opt.getOptind()];
                    else {
                        BufferedReader stdin = new BufferedReader(
                            new InputStreamReader( System.in ) );
                        query = stdin.readLine();
                    }
                    query( query );
                    break;
                case BENCH:
                    benchmark( parseFile );
                    break;
            }
            if ( database == null )
                broker.shutdown();
        } catch ( Exception e ) {
            System.err.println( e );
            e.printStackTrace();
        } finally {
        	if(pool != null)
				pool.release(broker);
        }
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    protected static Document createNewDocument() {
        DocumentBuilderFactory docFactory =
            DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder;
        try {
            docBuilder = docFactory.newDocumentBuilder();
        } catch ( ParserConfigurationException e ) {
            System.err.println( e );
            return null;
        }
        Document dest = docBuilder.newDocument();
        return dest;
    }


    /**
     *  Description of the Method
     *
     *@param  args  Description of the Parameter
     */
    public static void main( String args[] ) {
        CommandLine c = new CommandLine( args );
    }


    /**  Description of the Method */
    protected static void printNotice() {
        System.out.println( "eXist version 0.8.1, Copyright (C) 2002 Wolfgang M. Meier" );
        System.out.println( "eXist comes with ABSOLUTELY NO WARRANTY." );
        System.out.println(
            "This is free software, and you are welcome to " +
            "redistribute it\nunder certain conditions; " + "for details read the license file.\n" );
    }


    /**  Description of the Method */
    protected static void printUsage() {
        System.out.println( "exist.CommandLine [-h] [-q] [-i] [-l] [-d databaseURL] [-p xmlFile]" );
        System.out.println( "                  [-c collection] [-g doc] [-r doc] [-R collection]" );
        System.out.println( "                  [-n numberOfResults] query\n" );
        System.out.println( "Parameters:" );
        System.out.println( "  -d remoteUrl\taddress of XML-RPC server" );
        System.out.println( "  -l use a local instance of eXist broker" );
        System.out.println( "  -c collection\tuse collection" );
        System.out.println( "  -g doc\tretrieve document identified by it's name" );
        System.out.println( "  -h\t\tprints this help text" );
        System.out.println( "  -i\t\tindent query results" );
        System.out.println( "  -n number\tnumber of results to display" );
        System.out.println( "  -p xmlFile\tparse xml file or directory" );
        System.out.println( "  -q\t\tbe quiet - do not print any messages" );
        System.out.println( "  -r doc\tremove document identified by it's name" );
        System.out.println( "  -R collection\tremove collection" );
        System.out.println( "\nIf no query is presented on the command line, the query is read " );
        System.out.println( "from standard-input." );
        System.out.println( "By default queries are send to the XMLRPC server. The program " );
        System.out.println( "assumes http://localhost:8081 as address of the server, if no " );
        System.out.println( "other uri is specified with option -d. If -l is specified, a " );
        System.out.println( "local instance of the eXist broker is started (may result in " );
        System.out.println( "corrupted data if a server is doing inserts at the same time)." );
    }


    /**
     *  Description of the Method
     *
     *@param  benchFile      Description of the Parameter
     *@exception  Exception  Description of the Exception
     */
    protected void benchmark( String benchFile ) throws Exception {
        BufferedReader in = new BufferedReader( new FileReader( benchFile ) );
        ArrayList queries = new ArrayList();
        HashMap results = new HashMap();
        String query;
        while ( ( query = in.readLine() ) != null )
            queries.add( query );

        in.close();

        long start;

        long diff;
        byte[] raw;
        String xml;
        Vector params;

        for ( int i = 0; i < 25; i++ ) {
            System.out.println( "\npass: " + ( i + 1 ) );
            for ( int j = 0; j < queries.size(); j++ ) {
                query = (String) queries.get( j );
                System.out.print( query );

                start = System.currentTimeMillis();
                params = new Vector();
                params.addElement( query );
                params.addElement( new Integer( howmany ) );
                params.addElement( new Integer( 1 ) );
                params.addElement( new Integer( indent ? 1 : -1 ) );
                params.addElement( encoding );
                xml = (String) client.execute( "query", params );

                diff = System.currentTimeMillis() - start;
                System.out.println( "\t" + diff );

                Long old = (Long) results.get( query );
                if ( old == null )
                    results.put( query, new Long( diff ) );
                else
                    results.put( query, new Long( old.longValue() + diff ) );

            }
        }

        System.out.println( "\n\n" );
        for ( int i = 0; i < queries.size(); i++ ) {
            query = (String) queries.get( i );
            long t = ( (Long) results.get( query ) ).longValue();
            System.out.println( ( t / 10 ) + " : " + query );
        }
    }


    private void getDocument( String docName ) {
        if ( database != null ) {
            Vector params = new Vector();
            params.addElement( docName );
            params.addElement( "ISO-8859-1" );
            params.addElement( new Integer( indent ? 1 : -1 ) );
            if ( xslStyle != null )
                params.addElement( xslStyle );

            try {
                byte[] data =
                    (byte[]) client.execute( "getDocument", params );
                String xml = new String( data );
                System.out.println( xml );
            } catch ( Exception xre ) {
                System.out.println( "error occured in XMLRPC call" );
                System.out.println( xre );
            }
        }
        else {
        	try {
            	DocumentImpl doc = (DocumentImpl) broker.getDocument( admin, docName );
            	if ( doc == null ) {
                	System.out.println( "document " + docName + " not found in the repository." );
                	return;
            	}
            	Serializer serializer = broker.getSerializer();
            	try {
                	System.out.println( serializer.serialize( doc ) );
            	} catch ( SAXException se ) {
                	System.out.println( se );
            	}
        	} catch(PermissionDeniedException e) {
        		System.err.println("permission denied: " + e);
        	}
        }
    }


    /**  Description of the Method */
    protected void parse() {
        File file = new File( parseFile );
        if ( database == null ) {
            // parse using local broker
            Parser parser = null;
            try {
                parser = new Parser( broker, admin, true );
            } catch ( EXistException ee ) {
                System.out.println( "could not create eXist parser:" );
                System.out.println( ee );
                return;
            }
            if ( !quiet ) {
                ProgressObserver observer = new ProgressObserver();
                parser.addObserver( observer );
                broker.addObserver( observer );
            }
            if ( file.isDirectory() )
                try {
                    System.out.println(
                        "reading xml files from directory " +
                        parseFile );
                    long t0 = System.currentTimeMillis();
                    File files[] = file.listFiles( new XMLFilenameFilter() );
                    String docName;
                    for ( int i = 0; i < files.length; i++ )
                        if ( files[i].isFile() ) {
                            System.out.println( "\nparsing file " +
                                files[i].getName() + " (" +
                                ( i + 1 ) + " of " + files.length + ")" );
                            long start = System.currentTimeMillis();
                            InputSource src = new InputSource(
                                files[i].getAbsolutePath() );
                            if ( encoding != null )
                                src.setEncoding( encoding );

                            docName = files[i].getName();
                            if ( collection != null )
                                docName =
                                    ( collection.equals( "/" ) ? "/" + docName :
                                    collection + "/" + docName );

                            System.out.println( "first pass: scanning document to determine tree structure ..." );
                            parser.scan( src, docName );
                            System.out.println( "second pass: storing nodes ..." );
                            DocumentImpl doc = parser.store(src);
                            broker.flush();
                            if ( sync )
                                broker.sync();

                            System.out.println( "\nparsing took " +
                                ( System.currentTimeMillis() - start ) );
                        }
                    System.out.println( "\nparsing " + files.length +
                        " files took " + ( System.currentTimeMillis() - t0 ) +
                        "ms." );
                } catch ( SAXParseException e ) {
                    System.out.println( e );
                    System.out.println( "Parsing exception at line " +
                        e.getLineNumber() + " column " +
                        e.getColumnNumber() );
                } catch ( SAXException e ) {
                    Exception o = e.getException();
                    o.printStackTrace();
                    System.out.println( o );
                } catch ( Exception e ) {
                    e.printStackTrace();
                    System.out.println( e );
                }
            else {
                long start = System.currentTimeMillis();
                try {
                    InputSource src = new InputSource( parseFile );
                    String docName = new File( parseFile ).getName();
                    if ( encoding != null )
                        src.setEncoding( encoding );

                    if ( collection != null )
                        docName =
                            ( collection.equals( "/" ) ? "/" + docName :
                            collection + "/" + docName );

                    System.out.println( "first pass: scanning document to determine tree structure ..." );
                    parser.scan( src, docName );
                    System.out.println( "second pass: storing nodes ..." );
                    DocumentImpl doc = parser.store(src);
                    broker.flush();
                    System.out.println();
                    System.out.println( "\nparsing took " +
                        ( System.currentTimeMillis() - start ) );
                } catch ( SAXParseException e ) {
                    System.out.println( e );
                    System.out.println( "Parsing exception at line " +
                        e.getLineNumber() + " column " +
                        e.getColumnNumber() );
                } catch ( SAXException e ) {
                    Exception o = e.getException();
                    o.printStackTrace();
                    System.out.println( o );
                } catch ( Exception e ) {
                    e.printStackTrace();
                    System.out.println( e );
                }
            }
        }
        else
            // parse using XMLRPC
            try {
                if ( file.isDirectory() ) {
                    // parse files in directory
                    System.out.println(
                        "reading xml files from directory " +
                        parseFile );
                    File files[] = file.listFiles( new XMLFilenameFilter() );
                    String docName;
                    for ( int i = 0; i < files.length; i++ )
                        if ( files[i].isFile() ) {
                            System.out.println( "\nloading file " +
                                files[i].getName() );
                            try {
                                long start = System.currentTimeMillis();
                                docName = files[i].getName();
                                if ( collection != null )
                                    docName =
                                        ( collection.equals( "/" ) ? "/" + docName :
                                        collection + "/" + docName );

                                String xml = XMLUtil.readFile(
                                    new File( files[i].getAbsolutePath() ) );
                                Vector params = new Vector();
                                byte[] data;
                                try {
                                    data = xml.getBytes( "UTF-8" );
                                } catch ( UnsupportedEncodingException uee ) {
                                    data = xml.getBytes();
                                }
                                params.addElement( data );
                                params.addElement( docName );
                                params.addElement( new Integer( 1 ) );
                                System.out.print( "sending data to server ..." );
                                Boolean result =
                                    (Boolean) client.execute( "parse",
                                    params );
                                if ( !result.booleanValue() )
                                    System.out.println( "Failed to parse " +
                                        files[i].getName() );
                                else
                                    System.out.println( "done." );

                                System.out.println( "\nparsing took " +
                                    ( System.currentTimeMillis() -
                                    start ) );
                            } catch ( Exception e ) {
                                e.printStackTrace();
                                System.out.println( e );
                            }
                        }
                }
                else {
                    // parse single XML file
                    long start = System.currentTimeMillis();
                    try {
                        String docName = file.getName();
                        if ( collection != null )
                            docName =
                                ( collection.equals( "/" ) ? "/" + docName :
                                collection + "/" + docName );

                        String xml = XMLUtil.readFile( new File( parseFile ) );
                        Vector params = new Vector();
                        byte[] data;
                        try {
                            data = xml.getBytes( "UTF-8" );
                        } catch ( UnsupportedEncodingException uee ) {
                            data = xml.getBytes();
                        }
                        params.addElement( data );
                        params.addElement( docName );
                        params.addElement( new Integer( 1 ) );
                        System.out.print( "sending data to server ..." );
                        Boolean result =
                            (Boolean) client.execute( "parse", params );
                        if ( !result.booleanValue() )
                            System.out.println( "Failed to parse " +
                                file.getName() );
                        else
                            System.out.println( "ok." );

                        System.out.println( "\nparsing took " +
                            ( System.currentTimeMillis() - start ) );
                    } catch ( Exception e ) {
                        e.printStackTrace();
                        System.out.println( e );
                    }
                }
            } catch ( Exception e ) {
                e.printStackTrace();
                System.out.println( e );
            }

    }


    private void printResults( NodeList resultSet ) {
        Serializer serializer = broker.getSerializer();
        try {
            System.out.println( serializer.serialize( (NodeSet) resultSet,
                1, howmany ) );
        } catch ( SAXException se ) {
            System.out.println( se );
        }
    }


    /**
     *  Description of the Method
     *
     *@param  request  Description of the Parameter
     *@return          Description of the Return Value
     */
    protected String processRequest( String request ) {
        try {
            URL url = new URL( database );
            HttpURLConnection con =
                (HttpURLConnection) url.openConnection();
            con.setRequestMethod( "POST" );
            con.setDoOutput( true );

            PrintStream out = new PrintStream( con.getOutputStream() );
            out.println( request );
            con.connect();
            BufferedReader in = new BufferedReader(
                new InputStreamReader( con.getInputStream() ) );
            StringBuffer buf = new StringBuffer();
            String line;
            while ( ( line = in.readLine() ) != null ) {
                buf.append( line );
                buf.append( '\n' );
            }
            return buf.toString();
        } catch ( Exception e ) {
            System.out.println( e );
            return null;
        }
    }


    /**
     *  Description of the Method
     *
     *@param  query          Description of the Parameter
     *@exception  Exception  Description of the Exception
     */
    protected void query( String query ) throws Exception {
        if ( !quiet )
            System.out.println( "query: " + query );

        if ( database != null ) {
            Vector params = new Vector();
            params.addElement( query );
            params.addElement( encoding );
            params.addElement( new Integer( howmany ) );
            params.addElement( new Integer( 1 ) );
            params.addElement( new Integer( indent ? 1 : -1 ) );
            if ( sortExpr != null ) {
                if ( !quiet )
                    System.out.println( "sorting results by: " + sortExpr );

                params.addElement( sortExpr );
            }
            byte[] raw = (byte[]) client.execute( "query", params );
            String xml = new String( raw, encoding );
            System.out.println( xml );
        }
        else {
            XPathLexer lexer = new XPathLexer( new StringReader( query ) );
            XPathParser parser = new XPathParser( pool, admin, lexer );
            PathExpr expr = new PathExpr(pool);
            parser.expr( expr );
            if ( !quiet )
                System.out.println( "query: " + expr.pprint() );

            long start = System.currentTimeMillis();
            DocumentSet ndocs = expr.preselect();
            NodeList resultSet = expr.eval( ndocs, null, null ).getNodeList();
            if ( !quiet )
                System.out.println( "query took: " +
                    ( System.currentTimeMillis() - start ) + "ms.\n" );

            printResults( resultSet );
        }
    }


    /**
     *  Description of the Class
     *
     *@author     wolf
     *@created    28. Mai 2002
     */
    public static class ProgressObserver implements Observer {

        ProgressBar elementsProgress = new ProgressBar( "storing elements" );
        Observable lastObservable = null;
        ProgressBar parseProgress = new ProgressBar( "parsing         " );
        ProgressBar wordsProgress = new ProgressBar( "storing words   " );


        /**
         *  Description of the Method
         *
         *@param  o    Description of the Parameter
         *@param  obj  Description of the Parameter
         */
        public void update( Observable o, Object obj ) {
            ProgressIndicator ind = (ProgressIndicator) obj;
            if ( lastObservable == null || o != lastObservable )
                System.out.println();

            if ( o instanceof ElementIndex )
                elementsProgress.set( ind.getValue(), ind.getMax() );
            else if ( o instanceof TextSearchEngine )
                wordsProgress.set( ind.getValue(), ind.getMax() );
            else
                parseProgress.set( ind.getValue(), ind.getMax() );

            lastObservable = o;
        }
    }
}

