/**
 *  QueryServiceSoapBindingImpl.java This file was auto-generated from WSDL by
 *  the Apache Axis Wsdl2java emitter.
 */

package org.exist.soap;

import java.io.StringReader;
import java.io.StringWriter;
import java.rmi.RemoteException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Category;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.exist.EXistException;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.parser.*;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.xpath.PathExpr;
import org.exist.xpath.Value;
import org.exist.xpath.ValueSet;

import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    30. April 2002
 */
public class QuerySoapBindingImpl
     implements org.exist.soap.Query {

    private static Category LOG =
        Category.getInstance( "QueryService" );

    private BrokerPool pool;


    /**  Constructor for the QuerySoapBindingImpl object */
    public QuerySoapBindingImpl() {
        try {
            if ( !BrokerPool.isConfigured() )
                configure();
            pool = BrokerPool.getInstance();
        } catch ( Exception e ) {
            throw new RuntimeException( "failed to initialize broker pool" );
        }
    }


    private QueryResponseCollection[] collectQueryInfo( TreeMap collections ) {
        QueryResponseCollection c[] =
            new QueryResponseCollection[collections.size()];
        QueryResponseDocument doc;
        QueryResponseDocument docs[];
        String docId;
        int k = 0;
        int l;
        TreeMap documents;
        for ( Iterator i = collections.entrySet().iterator(); i.hasNext(); k++ ) {
            Map.Entry entry = (Map.Entry) i.next();
            c[k] = new QueryResponseCollection();
            c[k].setCollectionName( (String) entry.getKey() );
            documents = (TreeMap) entry.getValue();
            docs = new QueryResponseDocument[documents.size()];
            c[k].setDocuments( docs );
            l = 0;
            for ( Iterator j = documents.entrySet().iterator();
                j.hasNext(); l++ ) {
                Map.Entry docEntry = (Map.Entry) j.next();
                doc =
                    new QueryResponseDocument();
                docId = (String) docEntry.getKey();
                if ( docId.indexOf( '/' ) > -1 )
                    docId = docId.substring( docId.lastIndexOf( '/' ) + 1 );
                doc.setDocumentName( docId );
                doc.setHitCount( ( (Integer) docEntry.getValue() ).intValue() );
                docs[l] = doc;
            }
        }
        return c;
    }


    private void configure() throws Exception {
        String pathSep = System.getProperty( "file.separator", "/" );
        String home = System.getProperty( "exist.home" );
        if ( home == null )
            home = System.getProperty( "user.dir" );
        Configuration config =
            new Configuration( home + pathSep + "conf.xml" );
        BrokerPool.configure( 1, 5, config );
    }


    /**
     *  Gets the resource attribute of the QuerySoapBindingImpl object
     *
     *@param  name                          Description of the Parameter
     *@param  prettyPrint                   Description of the Parameter
     *@param  encoding                      Description of the Parameter
     *@return                               The resource value
     *@exception  java.rmi.RemoteException  Description of the Exception
     */
    public byte[] getResource( String name, String encoding,
                               boolean prettyPrint )
         throws java.rmi.RemoteException {
        DBBroker broker = null;
        try {
            broker = pool.get();
            DocumentImpl document =
                (DocumentImpl) broker.getDocument( name );
            if ( document == null )
                throw new RemoteException( "resource " + name + " not found" );
            Serializer serializer = broker.getSerializer();
            String xml = null;
            if ( prettyPrint ) {
                StringWriter sout = new StringWriter();
                OutputFormat format =
                    new OutputFormat( "xml", encoding, true );
                format.setOmitXMLDeclaration( false );
                format.setOmitComments( false );
                format.setLineWidth( 60 );
                XMLSerializer xmlout =
                    new XMLSerializer( sout, format );
                serializer.setContentHandler( xmlout );
                serializer.setLexicalHandler( xmlout );
                serializer.toSAX( document );
                xml = sout.toString();
            }
            else
                xml = serializer.serialize( document );
            if ( xml != null )
                try {
                    return xml.getBytes( encoding );
                } catch ( java.io.UnsupportedEncodingException e ) {
                    return xml.getBytes();
                }

            return null;
        } catch ( SAXException saxe ) {
            saxe.printStackTrace();
            throw new RemoteException( saxe.getMessage() );
        } catch ( EXistException e ) {
            throw new RemoteException( e.getMessage() );
        } catch ( PermissionDeniedException e ) {
            throw new RemoteException( e.getMessage() );
        } finally {
            pool.release( broker );
        }
    }


    /**
     *  Description of the Method
     *
     *@param  path                 Description of the Parameter
     *@return                      Description of the Return Value
     *@exception  RemoteException  Description of the Exception
     */
    public Collection listCollection( String path )
         throws RemoteException {
        DBBroker broker = null;
        try {
            broker = pool.get();
            if ( path == null )
                path = "/db";
            org.exist.collections.Collection collection =
                broker.getCollection( path );
            if ( collection == null )
                throw new RemoteException( "collection " + path + " not found" );
            Collection c = new Collection();

            // Sub-collections
            String childCollections[] =
                new String[collection.getChildCollectionCount()];
            int j = 0;
            for ( Iterator i = collection.collectionIterator();
                i.hasNext(); j++ )
                childCollections[j] = (String) i.next();

            // Resources
            String[] resources =
                new String[collection.getDocumentCount()];
            j = 0;
            int p;
            String resource;
            for ( Iterator i = collection.iterator(); i.hasNext(); j++ ) {
                resource = ( (DocumentImpl) i.next() ).getFileName();
                p = resource.lastIndexOf( '/' );
                resources[j] = p < 0 ? resource : resource.substring( p + 1 );
            }
            c.setResources( resources );
            c.setCollections( childCollections );
            return c;
        } catch ( EXistException e ) {
            throw new RemoteException( e.getMessage() );
        } finally {
            pool.release( broker );
        }
    }


    /**
     *  Description of the Method
     *
     *@param  query                         Description of the Parameter
     *@return                               Description of the Return Value
     *@exception  java.rmi.RemoteException  Description of the Exception
     */
    public org.exist.soap.QueryResponse query( java.lang.String query )
         throws java.rmi.RemoteException {
        if ( !( query.startsWith( "document(" ) ||
            query.startsWith( "collection(" ) ) )
            query = "document(*)" + query;

        QueryResponse resp = new QueryResponse();
        resp.setHits( 0 );
        Sessions sessions = Sessions.getInstance();
        try {
            XPathLexer lexer = new XPathLexer( new StringReader( query ) );
            XPathParser parser = new XPathParser( pool, new User( "admin", null, "dba" ), lexer );
            PathExpr expr = new PathExpr(pool);
            parser.expr( expr );
            LOG.info( "query: " + expr.pprint() );
            long start = System.currentTimeMillis();
            if ( parser.foundErrors() )
                throw new RemoteException( parser.getErrorMsg() );
            DocumentSet ndocs = expr.preselect();
            if ( ndocs.getLength() == 0 )
                return resp;
            Value value = expr.eval( ndocs, null, null );

            QueryResponseCollection[] collections = null;
            if ( value.getType() == Value.isNodeList )
                collections = collectQueryInfo( scanResults( value.getNodeList() ) );
            int resultSetId = sessions.addQueryResult( value );
            resp.setCollections( collections );
            resp.setHits( value.getNodeList().getLength() );
            resp.setResultSetId( resultSetId );
            resp.setQueryTime( System.currentTimeMillis() - start );
        } catch ( Exception e ) {
            throw new RemoteException( "query execution failed: " +
                e.getMessage() );
        }
        return resp;
    }


    /**
     *  Description of the Method
     *
     *@param  resultId                      Description of the Parameter
     *@param  num                           Description of the Parameter
     *@param  encoding                      Description of the Parameter
     *@param  prettyPrint                   Description of the Parameter
     *@return                               Description of the Return Value
     *@exception  java.rmi.RemoteException  Description of the Exception
     */
    public byte[] retrieve( int resultId, int num, java.lang.String encoding,
                            boolean prettyPrint )
         throws java.rmi.RemoteException {
        Sessions sessions = Sessions.getInstance();
        DBBroker broker = null;
        try {
            broker = pool.get();
            Value qr = (Value) sessions.getQueryResult( resultId );
            if ( qr == null )
                throw new RemoteException( "result set unknown or timed out" );
            String xml = null;
            switch ( qr.getType() ) {
                case Value.isNodeList:
                    NodeList resultSet = qr.getNodeList();
                    --num;
                    if ( num < 0 && num >= resultSet.getLength() )
                        throw new RuntimeException( "index " + num +
                            " out of bounds (" +
                            resultSet.getLength() +
                            ")" );
                    NodeProxy proxy = ( (NodeSet) resultSet ).get( num );
                    if ( proxy == null )
                        throw new RuntimeException( "not found: " + num );
                    LOG.debug( "loaded node " + proxy.gid );
                    Serializer serializer = broker.getSerializer();
                    Map properties = new TreeMap();
                    properties.put(Serializer.ENCODING, encoding);
                    properties.put(Serializer.PRETTY_PRINT, Boolean.toString(prettyPrint));
                    serializer.setProperties( properties );
                    xml = serializer.serialize( proxy );
                    break;
                default:
                    ValueSet valueSet = qr.getValueSet();
                    Value val = valueSet.get( num );
                    xml = val.getStringValue();
            }
            return xml.getBytes( encoding );
        } catch ( Exception e ) {
            LOG.warn( e );
            e.printStackTrace();
            throw new RemoteException( e.getMessage(), e );
        } finally {
            pool.release( broker );
        }
    }


    /**
     *  Description of the Method
     *
     *@param  resultId             Description of the Parameter
     *@param  pos                  Description of the Parameter
     *@param  docPath              Description of the Parameter
     *@param  encoding             Description of the Parameter
     *@param  prettyPrint          Description of the Parameter
     *@return                      Description of the Return Value
     *@exception  RemoteException  Description of the Exception
     */
    public byte[] retrieveByDocument( int resultId, int pos, String docPath,
                                      String encoding, boolean prettyPrint )
         throws RemoteException {
        Sessions sessions = Sessions.getInstance();
        DBBroker broker = null;
        try {
            broker = pool.get();
            Value qr = (Value) sessions.getQueryResult( resultId );
            if ( qr == null )
                throw new RemoteException( "result set unknown or timed out" );
            String xml = null;
            switch ( qr.getType() ) {
                case Value.isNodeList:
                    NodeList resultSet = qr.getNodeList();
                    ArraySet hitsByDoc = new ArraySet( 50 );
                    NodeProxy p;
                    for ( Iterator i = ( (NodeSet) resultSet ).iterator();
                        i.hasNext();  ) {
                        p = (NodeProxy) i.next();
                        if ( p.doc.getFileName().equals( docPath ) )
                            hitsByDoc.add( p );
                    }
                    if ( pos < 1 || pos > hitsByDoc.getLength() )
                        throw new RemoteException( "index " + pos +
                            "out of bounds (" +
                            hitsByDoc.getLength() + ")" );
                    --pos;
                    NodeProxy proxy = ( (NodeSet) hitsByDoc ).get( pos );
                    if ( proxy == null )
                        throw new RuntimeException( "not found: " + pos );
                    LOG.debug( "loaded node " + proxy.gid );
                    Serializer serializer = broker.getSerializer();
                    Map properties = new TreeMap();
                    properties.put(Serializer.ENCODING, encoding);
                    properties.put(Serializer.PRETTY_PRINT, Boolean.toString(prettyPrint));
                    serializer.setProperties( properties );
                    xml = serializer.serialize( proxy );
                    break;
                default:
                    throw new RemoteException( "result set is not a node list" );
            }
            return xml.getBytes( encoding );
        } catch ( Exception e ) {
            LOG.warn( e );
            e.printStackTrace();
            throw new RemoteException( e.getMessage(), e );
        } finally {
            pool.release( broker );
        }
    }


    private TreeMap scanResults( NodeList results ) {
        TreeMap collections = new TreeMap();
        TreeMap documents;
        NodeProxy p;
        Integer hits;
        for ( Iterator i = ( (NodeSet) results ).iterator(); i.hasNext();  ) {
            p = (NodeProxy) i.next();
            if ( ( documents =
                (TreeMap) collections.get( p.doc.getCollection().getName() ) )
                 == null ) {
                documents = new TreeMap();
                collections.put( p.doc.getCollection().getName(), documents );
                System.out.println( "added " + p.doc.getCollection().getName() );
            }
            if ( ( hits = (Integer) documents.get( p.doc.getFileName() ) ) == null )
                documents.put( p.doc.getFileName(), new Integer( 1 ) );
            else
                documents.put( p.doc.getFileName(),
                    new Integer( hits.intValue() + 1 ) );
        }
        return collections;
    }
}

