
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
 *  $Id:
 */
package org.exist.storage;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Observer;
import java.util.StringTokenizer;

import org.apache.log4j.Category;
import org.exist.EXistException;
import org.exist.dom.ArraySet;
import org.exist.dom.AttrImpl;
import org.exist.dom.Collection;
import org.exist.dom.CommentImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.DocumentTypeImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeListImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.ProcessingInstructionImpl;
import org.exist.dom.TextImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.serializers.PostgresSerializer;
import org.exist.storage.serializers.Serializer;
import org.exist.util.ByteConversion;
import org.exist.util.Configuration;
import org.exist.util.VariableByteInputStream;
import org.exist.util.VariableByteOutputStream;
import org.exist.xpath.Constants;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  This is the central class for the database backend. It inherits from
 *  DBBroker. It's main purpose is to store nodes to the database but it does
 *  also provide general retrieval methods which are used by the xpath backend.
 *  Nodes are stored through the methods storeDocument and store. These are
 *  called by the document-object.
 *
 *@author     wolf
 *@created    3. Juni 2002
 */
public class RelationalBroker extends DBBroker {

    private static Category LOG = Category.getInstance( RelationalBroker.class.getName() );

    // constants for retrieval mode
    /**  Description of the Field */
    public final static int PRELOAD = 0;
    /**  Description of the Field */
    public final static int SINGLE = 1;
    protected static TableLock collectionsLock = new TableLock();

    protected static TableLock documentsLock = new TableLock();
    protected static TableLock elementIdsLock = new TableLock();
    protected String ATTR_FILE = "attr";

    // file names for temporary table data
    protected String CDATA_FILE = "cdata";
    protected String DOM_FILE = "dom";

    // fields for caching temporary data
    protected ObjectPool cache = null;
    protected HashMap elementIds = new HashMap();
    protected HashMap elementNames = new HashMap();
    protected ElementPool elementPool = new ElementPool( 50 );

    protected String enc;

    protected ElementIdsWorkerThread idWorker;
    protected int lastElementId = -1, lastDocId = -1, lastCDATAId = -1;
    protected int mDatabaseType = MYSQL;
    protected int mRetrvMode = SINGLE;
    protected PreparedStatement m_getAttr, m_getAttrByName;
    protected PreparedStatement m_getDocsByType, m_retrvDoc;
    protected PreparedStatement m_getNamespaceURI, m_getNamespacePrefix, m_storeNamespace;
    protected PreparedStatement m_getNextElementId, m_getElementStmt, m_getElementId,
        m_insertElementId;
    protected PreparedStatement m_getNodeRange, m_getNodeDataStmt, m_insertNodeStmt;
    protected PreparedStatement m_getTextStmt, m_getTextPreload;
    protected PreparedStatement m_insertCollectionStmt, m_updateCollectionStmt,
        m_getCollectionStmt, m_deleteCollectionStmt, m_getSubcollectionStmt,
        m_getAllCollectionsStmt;
    protected PreparedStatement m_insertDocStmt, m_getNextDocId, m_getDocStmt, m_getAllDocsStmt,
        m_setNextDocId;
    protected PreparedStatement m_insertTreeInfo, m_getTreeInfo;
    protected PreparedStatement m_storePI, m_getPI, m_storeComment, m_getComment, m_delComment;

    protected boolean m_toFiles = true;
    protected HashMap namespaces = new HashMap();

    // the connection pool used
    protected DBConnectionPool pool = null;
    protected TableOutputStream s_attr = null;

    // output streams used when writing to files
    protected TableOutputStream s_cdata = null;
    protected TableOutputStream s_dom = null;
    protected Statement stmt;
    protected ElementIndex t_elementIndex = null;

    // SQL statements used by this class
    protected TableInsert t_insertTextStmt,
        t_insertAttr;
    // the fulltext-engine used
    protected RelationalTextEngine textEngine;
    protected String tmpDir;
    protected boolean useCompression = false;
    // hides some mysql specific features
    protected DBWrapper wrapper;

    protected Serializer xmlSerializer;


    /**
     *  Constructor for the RelationalBroker object
     *
     *@param  config              Description of the Parameter
     *@exception  EXistException  Description of the Exception
     */
    public RelationalBroker( BrokerPool brokerPool, Configuration config )
         throws EXistException {
        super( brokerPool, config );
        String driver;
        String url;
        String user;
        String pass;
        String stopword;
        String compress;
        String
            dbType;
        String pathSep;
        Boolean batch;
        pathSep = System.getProperty( "file.separator", "/" );
        // get some jdbc configuration parameters
        if ( ( tmpDir = (String) config.getProperty( "tmpDir" ) ) == null )
            tmpDir = null;

        if ( ( driver = (String) config.getProperty( "driver" ) ) == null )
            driver = "org.gjt.mm.mysql.Driver";

        if ( ( url = (String) config.getProperty( "url" ) ) == null )
            url = "jdbc:mysql://localhost/test";

        if ( ( user = (String) config.getProperty( "user" ) ) == null )
            user = "test";

        if ( ( pass = (String) config.getProperty( "password" ) ) == null )
            pass = "test";

        // should temporary files be used to store table data?
        if ( ( batch = (Boolean) config.getProperty( "batchLoad" ) ) != null )
            m_toFiles = batch.booleanValue();

        if ( ( compress = (String) config.getProperty( "compression" ) ) == null )
            useCompression = false;
        else
            useCompression = ( compress.equals( "on" ) ? true : false );

        if ( ( enc = (String) config.getProperty( "encoding" ) ) == null )
            enc = "UTF-8";

        // get database-type: MYSQL or ORACLE
        if ( ( dbType = (String) config.getProperty( "database" ) ) != null )
            mDatabaseType = ( dbType.equalsIgnoreCase( "mysql" ) ? MYSQL :
                ( dbType.equalsIgnoreCase( "postgresql" ) ?
                POSTGRESQL : ORACLE ) );

        if ( mDatabaseType == MYSQL )
            pool = new DBConnectionPool( url, user, pass, driver, 1, 10, true );
        else
            pool = new DBConnectionPool( url, user, pass, driver, 1, 10, false );

        // get a database connection
        Connection con = pool.get();
        if ( con == null )
            throw new EXistException( "cannot initialize database connection" );

        // create serializers
        //xmlSerializer = new Serializer( this, pool );

        switch ( mDatabaseType ) {
            case POSTGRESQL:
                xmlSerializer = new PostgresSerializer( this, config, pool );
                break;
            default:
                xmlSerializer = new Serializer( this, config );
        }

        // create element index writer
        t_elementIndex = new ElementIndex( this, config, pool, useCompression );

        // create an object-pool where retrieved nodes are cached
        cache = new ObjectPool();

        // create database-specific wrappers, used for loading temporary files
        switch ( mDatabaseType ) {
            case MYSQL:
                wrapper = new MysqlWrapper( config, pool );
                break;
            case POSTGRESQL:
                wrapper = new PostgresqlWrapper( config, pool );
                break;
            case ORACLE:
                wrapper = new OracleWrapper( config, pool );
                break;
            default:
                throw new EXistException( "unknown database type" );
        }

        // create a worker-thread, used by method findElementsByTagName()
        idWorker = new ElementIdsWorkerThread();
        idWorker.start();

        // prepare statements
        try {
            // statements for table collections
            m_insertCollectionStmt =
                con.prepareStatement( "insert into collections " +
                "(name, owner, owner_group, permissions, data) " +
                "values (?, ?, ?, ?, ?)" );
            m_getCollectionStmt =
                con.prepareStatement( "select name, owner, owner_group, permissions, data " +
                "from collections where name like ?" );
            m_getAllCollectionsStmt =
                con.prepareStatement( "select * from collections" );
            m_getSubcollectionStmt =
                con.prepareStatement( "select name from collections where name like ?" );
            m_deleteCollectionStmt =
                con.prepareStatement( "delete from collections where name=?" );
            m_updateCollectionStmt =
                con.prepareStatement( "update collections set owner=?, " +
                "owner_group=?, permissions=?, data=? where name=?" );
            // statements for table element_id
            m_getNextElementId =
                con.prepareStatement( "select max(element_id) from element_names" );
            m_getElementId =
                con.prepareStatement( "select element_id from element_names where name=?" );
            m_insertElementId =
                con.prepareStatement( "insert into element_names (element_id, name) values (?, ?)" );
            m_getElementStmt =
                con.prepareStatement( "select name from element_names where element_id=?" );

            // statements for table documents
            m_insertDocStmt =
                con.prepareStatement( "insert into documents (doc_id, name, children, " +
                "doctype, " +
                "public_id, system_id) " +
                "values (?, ?, ?, ?, ?, ?)" );
            m_getNextDocId =
                con.prepareStatement( "select id from identifiers where name='doc_id'" );
            m_setNextDocId =
                con.prepareStatement( "update identifiers set id=? where name='doc_id'" );
            m_getDocStmt =
                con.prepareStatement( "select doc_id, doctype, public_id, system_id, " +
                "children, " +
                "name " +
                "from documents where name like ?" );
            m_getAllDocsStmt =
                con.prepareStatement( "select doc_id, name, doctype, public_id,"
                 + "system_id, children from documents" );
            m_getDocsByType =
                con.prepareStatement( "select doc_id, name, doctype, public_id, system_id, " +
                "children from documents " +
                "where doctype=?" );
            // statements for table xtree
            m_insertTreeInfo =
                con.prepareStatement( "insert into xtree (doc_id, leafs, tree_level, " +
                "start_point, end_point) values (?, ?, ?, ?, ?)" );
            m_getTreeInfo =
                con.prepareStatement( "select leafs from xtree where doc_id=? " +
                "order by tree_level" );

            // statements for table cdata
            t_insertTextStmt =
                new TableInsert( pool, "cdata", "insert into cdata values ",
                ( mDatabaseType != MYSQL ) );

            // statements for table attr
            t_insertAttr = new TableInsert( pool, "attr", "insert into attr values ",
                ( mDatabaseType != MYSQL ) );
            m_getAttr =
                con.prepareStatement( "select name, value from attr where doc_id=? " +
                "and node_id=?" );
            m_getAttrByName =
                con.prepareStatement( "select doc_id, node_id from attr where name=?" );

            // statements for table namespaces
            m_storeNamespace =
                con.prepareStatement( "insert into namespaces (uri, prefix) values (?, ?)" );
            m_getNamespaceURI =
                con.prepareStatement( "select uri from namespaces where prefix=?" );
            m_getNamespacePrefix =
                con.prepareStatement( "select prefix from namespaces where uri=?" );

            // statements for table dom, used by method objectWith
            m_insertNodeStmt =
                con.prepareStatement( "insert into dom values (?, ?, ?, ?, ?, ?, ?)" );
            if ( mDatabaseType == MYSQL || mDatabaseType == POSTGRESQL )
                // for mysql and postgresql we use "join"
                m_getNodeDataStmt =
                    con.prepareStatement( "select dom.gid, dom.type, dom.children, " +
                    "dom.element_id, dom.attrib, dom.prefixes, " +
                    "cdata.cdata from dom " +
                    "left join cdata using (doc_id, gid) " +
                    "where dom.doc_id=? and dom.gid=?" );
            else
                // otherwise we use sub-selects
                m_getNodeDataStmt =
                    con.prepareStatement( "select d.gid, d.type, d.children, " +
                    "d.element_id, d.attrib, dom.prefixes, " +
                    "(select c.cdata from cdata c where " +
                    "c.doc_id=d.doc_id and " +
                    "c.gid=d.gid) from dom d where " +
                    "d.doc_id=? and d.gid=?" );

            // statements used by method getRange()
            if ( mDatabaseType == MYSQL )
                m_getNodeRange =
                    con.prepareStatement( "select dom.gid, dom.type, dom.children, " +
                    "dom.element_id, dom.attrib, dom.prefixes, cdata.cdata from dom " +
                    "left join cdata using (doc_id, gid) where " +
                    "dom.doc_id=? " +
                    "and (dom.gid between ? and ?)" );
            else
                m_getNodeRange =
                    con.prepareStatement( "select d.gid, d.type, d.children, " +
                    "d.element_id, d.attrib, dom.prefixes, " +
                    "(select c.cdata from cdata c where " +
                    "c.doc_id=d.doc_id and " +
                    "c.gid=d.gid) from dom d where d.doc_id=? " +
                    " and (d.gid between ? and ?)" );

            m_storePI =
                con.prepareStatement( "insert into processing_instruction (doc_id, gid, " +
                "target, data) " +
                " values (?, ?, ?, ?)" );
            m_getPI =
                con.prepareStatement( "select target, data from processing_instruction " +
                "where doc_id=? and gid=?" );
            m_storeComment =
                con.prepareStatement( "insert into comment_node (doc_id, gid, cdata) " +
                "values (?, ?, ?)" );
            m_getComment =
                con.prepareStatement( "select cdata from comment_node where doc_id=? " +
                "and gid=?" );

            stmt = con.createStatement();
            pool.release( con );

            // create the text-engine
            textEngine = new RelationalTextEngine( this, config, pool );

            // if table data is written to temporary files,
            // open files
            if ( m_toFiles )
                openTempFiles( tmpDir );

            // load all element-names into cache
            preloadElementNames();
            try {
                getOrCreateCollection( "/db" );
            } catch ( PermissionDeniedException e ) {
                LOG.debug( e );
            }
        } catch ( SQLException e ) {
            LOG.debug( e );
            pool.release( con );
            throw new EXistException( "database error" );
        }
    }


    private static String documentSet( DocumentSet docs ) {
        StringBuffer r = new StringBuffer();
        r.append( '(' );
        for ( int i = 0; i < docs.getLength(); i++ ) {
            if ( r.length() > 1 )
                r.append( ',' );

            int docId = ( (DocumentImpl) docs.item( i ) ).getDocId();
            r.append( docId );
        }
        r.append( ')' );
        return r.toString();
    }


    /**
     *  Gets the firstChildID attribute of the RelationalBroker class
     *
     *@param  doc    Description of the Parameter
     *@param  level  Description of the Parameter
     *@param  gid    Description of the Parameter
     *@return        The firstChildID value
     */
    protected static long getFirstChildID( DocumentImpl doc, int level, long gid ) {
        return ( gid - doc.getLevelStartPoint( level ) ) *
            doc.getTreeLevelOrder( level + 1 ) +
            doc.getLevelStartPoint( level + 1 );
    }


    /**
     *  Gets the lastChildID attribute of the RelationalBroker class
     *
     *@param  doc    Description of the Parameter
     *@param  level  Description of the Parameter
     *@param  gid    Description of the Parameter
     *@return        The lastChildID value
     */
    protected static long getLastChildID( DocumentImpl doc, int level, long gid ) {
        long start = ( gid - doc.getLevelStartPoint( level ) ) *
            doc.getTreeLevelOrder( level + 1 ) +
            doc.getLevelStartPoint( level + 1 );
        return start + doc.getTreeLevelOrder( level + 1 );
    }


    /**
     *  Description of the Method
     *
     *@param  name  Description of the Parameter
     *@return       Description of the Return Value
     */
    protected final static String normalizeCollectionName( String name ) {
        StringBuffer out = new StringBuffer();
        for ( int i = 0; i < name.length(); i++ )
            if ( name.charAt( i ) == '/' && name.length() > i + 1 &&
                name.charAt( i + 1 ) == '/' )
                i++;
            else
                out.append( name.charAt( i ) );

        return out.toString();
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public Object acquireWriteLock() {
        return null;
    }


    /**
     *  Adds a feature to the Observer attribute of the RelationalBroker object
     *
     *@param  o  The feature to be added to the Observer attribute
     */
    public void addObserver( Observer o ) {
        super.addObserver( o );
        textEngine.addObserver( o );
        t_elementIndex.addObserver( o );
    }


    /**
     *  Description of the Method
     *
     *@param  doc  Description of the Parameter
     *@param  r    Description of the Parameter
     *@return      Description of the Return Value
     */
    protected Node createNode( DocumentImpl doc, ResultSet r ) {
        long current;
        short type;
        int children;
        int nodeNameRef;
        short attribCount;
        String cdata = null;
        String prefixes = null;
        try {
            current = r.getLong( 1 );
            type = r.getShort( 2 );
            children = r.getInt( 3 );
            nodeNameRef = r.getInt( 4 );
            attribCount = r.getShort( 5 );
            prefixes = r.getString( 6 );
            cdata = r.getString( 7 );
            return createNode( doc, current, type, children, nodeNameRef,
                attribCount, prefixes, cdata );
        } catch ( SQLException sqe ) {
            LOG.debug( sqe );
            return null;
        }
    }


    /**
     *  utility-method to create a node
     *
     *@param  doc          Description of the Parameter
     *@param  gid          Description of the Parameter
     *@param  type         Description of the Parameter
     *@param  children     Description of the Parameter
     *@param  nodeNameRef  Description of the Parameter
     *@param  attribCount  Description of the Parameter
     *@param  cdata        Description of the Parameter
     *@param  prefixes     Description of the Parameter
     *@return              Description of the Return Value
     */
    protected Node createNode( DocumentImpl doc, long gid, short type, int children,
                               int nodeNameRef, short attribCount, String prefixes, String cdata ) {
        Node node = null;
        switch ( type ) {
            case Node.ELEMENT_NODE:
            {
                node = new ElementImpl( gid );
                ( (ElementImpl) node ).setOwnerDocument( doc );
                ( (ElementImpl) node ).setChildCount( children );
                ( (ElementImpl) node ).setNodeNameRef( nodeNameRef );
                ( (ElementImpl) node ).setAttributes( attribCount );
                if ( prefixes != null ) {
                    // read list of namespace prefixes used in this doc
                    StringTokenizer tok = new StringTokenizer( prefixes, ";" );
                    String pfx;
                    while ( tok.hasMoreTokens() ) {
                        pfx = tok.nextToken();
                        ( (ElementImpl) node ).addNamespacePrefix( pfx );
                    }
                }
                break;
            }
            case Node.TEXT_NODE:
            {
                node = new TextImpl( gid, cdata );
                ( (NodeImpl) node ).setOwnerDocument( doc );
                break;
            }
            case Node.ATTRIBUTE_NODE:
            {
                node = new AttrImpl( gid );
                ( (AttrImpl) node ).setOwnerDocument( doc );
                try {
                    m_getAttr.setInt( 1, doc.getDocId() );
                    m_getAttr.setLong( 2, gid );
                    ResultSet rs = m_getAttr.executeQuery();
                    if ( rs.next() ) {
                        ( (AttrImpl) node ).setNodeName( rs.getString( 1 ) );
                        ( (AttrImpl) node ).setValue( rs.getString( 2 ) );
                    }
                    rs.close();
                } catch ( SQLException sqe ) {
                    LOG.debug( sqe );
                    return null;
                }
                break;
            }
            case Node.COMMENT_NODE:
            {
                node = new CommentImpl( gid );
                ( (CommentImpl) node ).setOwnerDocument( doc );
                try {
                    m_getComment.setInt( 1, doc.getDocId() );
                    m_getComment.setLong( 2, gid );
                    ResultSet rs = m_getComment.executeQuery();
                    if ( rs.next() )
                        ( (CommentImpl) node ).appendData( rs.getString( 1 ) );

                    rs.close();
                } catch ( SQLException sqe ) {
                    LOG.debug( sqe );
                    return null;
                }
                break;
            }
            case Node.PROCESSING_INSTRUCTION_NODE:
            {
                node = new ProcessingInstructionImpl( gid );
                ( (ProcessingInstructionImpl) node ).setOwnerDocument( doc );
                try {
                    m_getPI.setInt( 1, doc.getDocId() );
                    m_getPI.setLong( 2, gid );
                    ResultSet rs = m_getPI.executeQuery();
                    if ( rs.next() ) {
                        ( (ProcessingInstructionImpl) node ).setTarget( rs.getString( 1 ) );
                        ( (ProcessingInstructionImpl) node ).setData( rs.getString( 2 ) );
                    }
                } catch ( SQLException sqe ) {
                    LOG.debug( sqe );
                    return null;
                }
                break;
            }
            default:
                LOG.debug( "unknown node " + gid );
                return null;
        }
        if ( node == null )
            LOG.debug( "could not read node " + gid + " from db." );

        return node;
    }


    /**
     *  get the element-name of an element-object. Method object-with just loads
     *  element-id's from the database, but not the element's actual name.
     *  ElementImpl calls this method whenever the actual name is accessed.
     *
     *@param  element  Description of the Parameter
     *@return          Description of the Return Value
     */
    public boolean elementWith( ElementImpl element ) {
        try {
            String name;
            Integer ref = new Integer( element.getNodeNameRef() );
            if ( elementNames.containsKey( ref ) )
                name = (String) elementNames.get( ref );
            else {
                m_getElementStmt.setInt( 1, element.getNodeNameRef() );
                ResultSet re = m_getElementStmt.executeQuery();
                if ( !re.next() ) {
                    LOG.debug( "element " + element.getGID() + "not found" );
                    return false;
                }
                name = re.getString( 1 );
                elementNames.put( ref, name );
                re.close();
            }
            element.setNodeName( name );
        } catch ( SQLException e ) {
            LOG.debug( e );
        }
        return true;
    }


    /**
     *  Description of the Method
     *
     *@param  in  Description of the Parameter
     *@return     Description of the Return Value
     */
    protected String escapeCharacters( String in ) {
        int l = in.length();
        StringBuffer buf = new StringBuffer( l );
        for ( int i = 0; i < l; i++ )
            switch ( in.charAt( i ) ) {
                case '\'':
                    buf.append( '\\' );
                    buf.append( '\'' );
                    break;
                case '`':
                    buf.append( "\\`" );
                    break;
                case '\\':
                    buf.append( '\\' );
                    buf.append( '\\' );
                    break;
                case '{':
                    buf.append( "\\{" );
                    break;
                case '}':
                    buf.append( "\\}" );
                    break;
                default:
                    buf.append( in.charAt( i ) );
            }

        return buf.toString();
    }


    /**
     *  collects all occurrences of an element in the document set. Element
     *  occurrences are stored in binary large objects (blobs) to speed up
     *  retrieval. It is much faster to scan through a large binary block of a
     *  few thousand long ids than to store each of them in its own table row.
     *  Additionally the blob may be compressed with gzip. The method recognizes
     *  this.
     *
     *@param  docs     Description of the Parameter
     *@param  tagName  Description of the Parameter
     *@return          Description of the Return Value
     */
    public NodeSet findElementsByTagName( DocumentSet docs, String tagName ) {
        long start = System.currentTimeMillis();
        if ( elementPool != null && elementPool.inCache( docs, tagName ) ) {
            LOG.debug( "cache hit" );
            return elementPool.getNodeSet( docs, tagName );
        }
        NodeSet result = new ArraySet( 10000 );
        int elementId;
        if ( elementIds.containsKey( tagName ) )
            elementId = ( (Integer) elementIds.get( tagName.toLowerCase() ) ).intValue();
        else
            try {
                m_getElementId.setString( 1, tagName );
                ResultSet r = m_getElementId.executeQuery();
                if ( !r.next() ) {
                    LOG.warn( "element not found" );
                    return result;
                }
                elementId = r.getInt( 1 );
                r.close();
            } catch ( SQLException e ) {
                LOG.warn( e );
                return result;
            }

        try {
            StringBuffer sql = new StringBuffer();
            sql.append( "select doc_id, data from b_element where doc_id in " );
            sql.append( documentSet( docs ) );
            sql.append( " and element_id=" );
            sql.append( elementId );
            sql.append( " order by doc_id" );
            //stmt.setFetchSize(stmt.getMaxRows());
            ResultSet r = stmt.executeQuery( sql.toString() );
            int doc_id;
            int len = 0;
            long gid;
            byte[] data;
            DocumentImpl lastDoc = null;
            VariableByteInputStream is;
            while ( r.next() ) {
                doc_id = r.getInt( 1 );
                lastDoc = docs.getDoc( doc_id );
                data = r.getBytes( 2 );
                is = new VariableByteInputStream( data );
                len = is.readInt();
                for( int i = 0; i < len; i++ ) {
                    gid = is.readLong();
                    result.add( new NodeProxy( lastDoc, gid ) );    
                }
            }
            r.close();
        } catch ( SQLException s ) {
            LOG.warn( s );
        } catch ( EOFException e ) {
            LOG.error( e );
        } catch (IOException e) {
        	LOG.error( e );
        }
        LOG.info( "found: " + result.getLength() + " in " + ( System.currentTimeMillis() - start ) );

        elementPool.add( docs, result, tagName );
        return result;
    }


    /**  flush the database and all open files */
    public void flush() {
        try {
            if ( t_elementIndex != null )
                t_elementIndex.flush();

            t_insertAttr.flush();
            t_insertTextStmt.flush();
            if ( m_toFiles ) {
		try {
		    s_cdata.close();
                    s_dom.close();
                    s_attr.close();

                    wrapper.loadFromFile( s_dom.getFileName(), "dom" );
                    wrapper.loadFromFile( s_cdata.getFileName(), "cdata" );
                    wrapper.loadFromFile( s_attr.getFileName(), "attr" );
                } catch ( IOException io ) {
                    LOG.debug( io );
                }
                openTempFiles( tmpDir );
            }
            if ( !( mDatabaseType == MYSQL && m_toFiles ) )
                m_insertNodeStmt.getConnection().commit();

            textEngine.flush();
            lastDocId = -1;
            lastElementId = -1;
            elementIds = new HashMap();
            elementNames = new HashMap();
            namespaces = new HashMap();
        } catch ( SQLException e ) {
            LOG.debug( e );
        }
    }


    /**
     *  get a set of all documents stored in the database.
     *
     *@param  user  Description of the Parameter
     *@return       The allDocuments value
     */
    public DocumentSet getAllDocuments( User user ) {
        DocumentSet docs = new DocumentSet();
        docs.setAllDocuments( true );
        try {
            ResultSet r = m_getAllCollectionsStmt.executeQuery();
            while ( r.next() ) {
                String name = r.getString( 1 );
                Collection collection = new Collection( this, name );
                Permission perm = collection.getPermissions();
                perm.setOwner( r.getString( 2 ) );
                perm.setGroup( r.getString( 3 ) );
                perm.setPermissions( r.getInt( 4 ) );
                if ( !perm.validate( user, Permission.READ ) )
                    continue;
                // read documents
                byte[] data = r.getBytes( 5 );
                //ByteArrayInputStream bstream = new ByteArrayInputStream( data );
                //DataInputStream istream = new DataInputStream( bstream );
                VariableByteInputStream istream = 
                    new VariableByteInputStream( data );
                try {
                    DocumentImpl doc;
                    int c = istream.readInt();
                    for ( int i = 0; i < c; i++ ) {
                        doc = new DocumentImpl( this, collection );
                        doc.read( istream );
                        collection.addDocument( doc );
                    }
                } catch ( IOException ioe ) {
                    LOG.warn( ioe );
                    return null;
                }
                DocumentImpl doc;
                for ( Iterator i = collection.iterator(); i.hasNext();  ) {
                    doc = (DocumentImpl) i.next();
                    if ( doc.getPermissions().validate( user, Permission.READ ) )
                        docs.add( doc );
                }
            }
        } catch ( SQLException e ) {
            LOG.debug( e );
        }
        return docs;
    }


    /**
     *  Gets the attributesByName attribute of the RelationalBroker object
     *
     *@param  docs  Description of the Parameter
     *@param  name  Description of the Parameter
     *@return       The attributesByName value
     */
    public NodeSet getAttributesByName( DocumentSet docs, String name ) {
        Connection con = pool.get();
        ArraySet result = new ArraySet( 100 );
        try {
            m_getAttrByName.setString( 1, name );
            ResultSet rs = m_getAttrByName.executeQuery();
            int doc_id;
            long gid;
            while ( rs.next() ) {
                doc_id = rs.getInt( 1 );
                gid = rs.getLong( 2 );
                result.add( docs.getDoc( doc_id ), gid );
            }
            rs.close();
        } catch ( SQLException e ) {
            LOG.debug( e );
        }
        pool.release( con );
        return result;
    }


    /**
     *  get collection object If the collection does not exist, null is
     *  returned.
     *
     *@param  name  Description of the Parameter
     *@return       The collection value
     */
    public Collection getCollection( String name ) {
        name = normalizeCollectionName( name );
        if ( name.length() > 0 && name.charAt( 0 ) != '/' )
            name = "/" + name;

        if ( !name.startsWith( "/db" ) )
            name = "/db" + name;

        if ( name.endsWith( "/" ) && name.length() > 1 )
            name = name.substring( 0, name.length() - 1 );

        try {
            // load the collection
            m_getCollectionStmt.setString( 1, name );
            ResultSet r = m_getCollectionStmt.executeQuery();
            if ( !r.next() ) {
                LOG.debug( "collection " + name + " does not exist!" );
                return null;
            }
            Collection collection = new Collection( this, name );
            Permission perm = collection.getPermissions();
            perm.setOwner( r.getString( 2 ) );
            perm.setGroup( r.getString( 3 ) );
            perm.setPermissions( r.getInt( 4 ) );
            // read documents
            byte[] data = r.getBytes( 5 );
            //ByteArrayInputStream bstream = new ByteArrayInputStream( data );
            //DataInputStream istream = new DataInputStream( bstream );
            VariableByteInputStream istream = new VariableByteInputStream( data );
            try {
                DocumentImpl doc;
                int c = istream.readInt();
                for ( int i = 0; i < c; i++ ) {
                    doc = new DocumentImpl( this, collection );
                    doc.read( istream );
                    collection.addDocument( doc );
                }
            } catch ( IOException ioe ) {
                LOG.warn( ioe );
                return null;
            }
            // get existing subcollections
            m_getSubcollectionStmt.setString( 1, name + "/%" );
            r = m_getSubcollectionStmt.executeQuery();
            String collName;
            String relPath;
            String subColl;
            while ( r.next() ) {
                collName = r.getString( 1 );
                relPath = collName.substring( name.length() );
                if ( relPath.length() > 0 && relPath.charAt( 0 ) == '/' ) {
                    relPath = relPath.substring( 1 );
                    // add a subcollection
                    subColl =
                        ( relPath.indexOf( '/' ) > -1 ) ?
                        relPath.substring( 0, relPath.indexOf( '/' ) )
                         : relPath;
                    collection.addCollection( subColl );
                }
            }

            return collection;
        } catch ( SQLException sqe ) {
            LOG.debug( sqe );
            return null;
        }
    }


    /**
     *  get the type of database-engine, this broker is talking to. Should be
     *  one out of MYSQL or ORACLE
     *
     *@return    The databaseType value
     */
    public int getDatabaseType() {
        return mDatabaseType;
    }


    /**
     *  get a Document-Node by it's file name.
     *
     *@param  fileName                       Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                The document value
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public Document getDocument( User user, String fileName )
         throws PermissionDeniedException {
        if ( !fileName.startsWith( "/" ) )
            fileName = '/' + fileName;
        if ( !fileName.startsWith( "/db" ) )
            fileName = "/db" + fileName;
        int pos = fileName.lastIndexOf( '/' );
        String collName =
            ( pos > 0 ) ? fileName.substring( 0, pos ) : "/";
        Collection collection = getCollection( collName );
        if ( collection == null ) {
            LOG.debug( "collection " + collName + " not found!" );
            return null;
        }
        if ( !collection.getPermissions().validate( user, Permission.READ ) )
            throw new PermissionDeniedException( "permission denied to read collection" );
        DocumentImpl doc = collection.getDocument( fileName );
        if ( doc == null ) {
            LOG.debug( "document " + fileName + " not found!" );
            return null;
        }
        if ( !doc.getPermissions().validate( user, Permission.READ ) )
            throw new PermissionDeniedException( "not allowed to read document" );
        return doc;
    }


    /**
     *  Gets the documentsByCollection attribute of the RelationalBroker object
     *
     *@param  collection                     Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                The documentsByCollection value
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public DocumentSet getDocumentsByCollection( User user, String collection )
         throws PermissionDeniedException {
        return getDocumentsByCollection( user, collection, true );
    }


    /**
     *  Gets the documentsByCollection attribute of the RelationalBroker object
     *
     *@param  collection                     Description of the Parameter
     *@param  inclusive                      Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                The documentsByCollection value
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public DocumentSet getDocumentsByCollection( User user, String collection,
                                                 boolean inclusive )
         throws PermissionDeniedException {
        DocumentSet docs = new DocumentSet();
        long start = System.currentTimeMillis();
        if ( collection == null || collection.length() == 0 )
            return docs;
        if ( collection.charAt( 0 ) != '/' )
            collection = "/" + collection;
        if ( !collection.startsWith( "/db" ) )
            collection = "/db" + collection;

        Collection coll = getCollection( collection );
        if ( coll == null ) {
            LOG.debug( "collection " + collection + " not found" );
            return docs;
        }
        if ( !coll.getPermissions().validate( user, Permission.READ ) )
            throw new PermissionDeniedException( "permission to read collection denied" );
        DocumentImpl doc;
        for ( Iterator j = coll.iterator(); j.hasNext();  ) {
            doc = (DocumentImpl) j.next();
            if ( doc.getPermissions().validate( user, Permission.READ ) )
                docs.add( doc );

        }
        if ( inclusive ) {
            String childName;
            DocumentSet childDocs;
            for ( Iterator i = coll.collectionIterator(); i.hasNext();  ) {
                childName = (String) i.next();
                childName = coll.getName() + '/' + childName;
                childDocs = getDocumentsByCollection( user, childName, inclusive );
                docs.addAll( childDocs );
            }
        }
        LOG.debug( "loading " + docs.getLength() +
            " documents from collection " + collection +
            " took " + ( System.currentTimeMillis() - start ) + "ms." );
        return docs;
    }


    /**
     *  get a set of all documents matching a doctype
     *
     *@param  doctypeName  Description of the Parameter
     *@param  user         Description of the Parameter
     *@return              The documentsByDoctype value
     */
    public DocumentSet getDocumentsByDoctype( User user, String doctypeName ) {
        DocumentSet result = new DocumentSet();
        DocumentSet docs = getAllDocuments( user );
        DocumentImpl doc;
        DocumentType doctype;
        for ( Iterator i = docs.iterator(); i.hasNext();  ) {
            doc = (DocumentImpl) i.next();
            doctype = doc.getDoctype();
            if ( doctype == null )
                continue;
            if ( doctypeName.equals( doctype.getName() ) &&
                doc.getCollection().getPermissions().validate( user, Permission.READ ) &&
                doc.getPermissions().validate( user, Permission.READ ) )
                result.add( doc );

        }
        return result;
    }


    /**
     *  get the namespace associated with the given prefix. Every broker
     *  subclass should keep an internal map, where it stores the prefixes used
     *  for different namespaces. It should be guaranteed that only one prefix
     *  is associated with one namespace URI.
     *
     *@param  namespace  Description of the Parameter
     *@return            The namespacePrefix value
     */
    public String getNamespacePrefix( String namespace ) {
        if ( namespaces.containsKey( namespace ) )
            return (String) namespaces.get( namespace );
        try {
            m_getNamespacePrefix.setString( 1, namespace );
            ResultSet r = m_getNamespacePrefix.executeQuery();
            if ( r.next() ) {
                String prefix = r.getString( 1 );
                namespaces.put( namespace, prefix );
            }
            r.close();
        } catch ( SQLException sqe ) {
            LOG.debug( sqe );
        }
        return null;
    }


    /**
     *  get common prefix for a namespace URI. It should be guaranteed that only
     *  one prefix is associated with one namespace URI throughout the database.
     *
     *@param  prefix  Description of the Parameter
     *@return         The namespaceURI value
     */
    public String getNamespaceURI( String prefix ) {
        try {
            m_getNamespaceURI.setString( 1, prefix );
            ResultSet r = m_getNamespaceURI.executeQuery();
            if ( r.next() ) {
                String ns = r.getString( 1 );
                return ns;
            }
            r.close();
        } catch ( SQLException sqe ) {
            LOG.debug( sqe );
        }
        return null;
    }


    /**
     *  the following methods are used to determine the next valid value for a
     *  internal id. We use this to be database-independant.
     *
     *@param  collection  Description of the Parameter
     *@return             The nextDocId value
     */
    public int getNextDocId( Collection collection ) {
        try {
            ResultSet r = m_getNextDocId.executeQuery();
            if ( !r.next() )
                return 0;
            lastDocId = r.getInt( 1 ) + 1;
            m_setNextDocId.setInt( 1, lastDocId );
            m_setNextDocId.executeUpdate();
            return lastDocId;
        } catch ( SQLException e ) {
            LOG.error( e );
            return -1;
        }
    }


    private int getNextElementId() {
        try {
            ResultSet r = m_getNextElementId.executeQuery();
            if ( !r.next() )
                return 0;
            lastElementId = r.getInt( 1 ) + 1;
            return lastElementId;
        } catch ( SQLException e ) {
            LOG.warn( "sql exception while getting element id: " + e );
            return -1;
        }
    }


    /**
     *  Gets the nodeValue attribute of the RelationalBroker object
     *
     *@param  proxy  Description of the Parameter
     *@return        The nodeValue value
     */
    public String getNodeValue( NodeProxy proxy ) {
        Node n = objectWith( proxy.doc, proxy.gid );
        return n.getNodeValue();
    }


    /**
     *  get set of all nodes containing term This method actually calls the
     *  corresponding method of TextSearchEngine.
     *
     *@param  docs  Description of the Parameter
     *@param  term  Description of the Parameter
     *@return       The nodesContaining value
     */
    public NodeSet getNodesContaining( DocumentSet docs, String term ) {
        String termList[] = {term};
        NodeSet[] result = getNodesContaining( docs, termList );
        if ( result.length == 0 )
            return new ArraySet( 1 );
        else
            return result[0];
    }


    /**
     *  get set of all nodes containing a list of terms This method actually
     *  calls the corresponding method of TextSearchEngine.
     *
     *@param  docs      Description of the Parameter
     *@param  termList  Description of the Parameter
     *@return           The nodesContaining value
     */
    public NodeSet[] getNodesContaining( DocumentSet docs, String[] termList ) {
        long start = System.currentTimeMillis();
        NodeSet[] result = textEngine.getNodesContaining( docs, termList );
        return result;
    }


    /*
     *  find all Nodes whose string value is equal to expr in the document
     *  set. The method uses a simple select statement to retrieve the
     *  matching columns from the cdata table.
     */
    /**
     *  Gets the nodesEqualTo attribute of the RelationalBroker object
     *
     *@param  context  Description of the Parameter
     *@param  docs     Description of the Parameter
     *@param  type     Description of the Parameter
     *@param  expr     Description of the Parameter
     *@return          The nodesEqualTo value
     */
    public NodeSet getNodesEqualTo( NodeSet context, DocumentSet docs, int type, String expr ) {
        long start = System.currentTimeMillis();
        Connection con = pool.get();
        ArraySet temp = new ArraySet( 100 );
        ArraySet result = new ArraySet( 100 );
        String comparator;
        switch ( type ) {
            case Constants.LT:
                comparator = "<";
                break;
            case Constants.GT:
                comparator = ">";
                break;
            case Constants.LTEQ:
                comparator = "<=";
                break;
            case Constants.GTEQ:
                comparator = ">=";
                break;
            case Constants.NEQ:
                comparator = "!=";
                break;
            case Constants.EQ:
            default:
                comparator = " like ";
                break;
        }
        try {
            StringBuffer sql = new StringBuffer();
            sql.append( "select doc_id, gid from cdata where doc_id in " );
            sql.append( documentSet( docs ) );
            sql.append( " and cdata" );
            sql.append( comparator );
            sql.append( '\'' );
            sql.append( expr );
            sql.append( '\'' );
            Statement stmt = con.createStatement();
            ResultSet r = stmt.executeQuery( sql.toString() );
            long gid;
            int doc_id;
            while ( r.next() ) {
                doc_id = r.getInt( 1 );
                gid = r.getLong( 2 );
                temp.add( new NodeProxy( docs.getDoc( doc_id ), gid, Node.TEXT_NODE ) );
            }
            r.close();
            StringBuffer sql2 = new StringBuffer();
            sql2.append( "select doc_id, node_id from attr where doc_id in " );
            sql2.append( documentSet( docs ) );
            sql2.append( " and value" );
            sql2.append( comparator );
            sql2.append( '\'' );
            sql2.append( expr );
            sql2.append( '\'' );
            r = stmt.executeQuery( sql2.toString() );
            while ( r.next() ) {
                doc_id = r.getInt( 1 );
                gid = r.getLong( 2 );
                temp.add( new NodeProxy( docs.getDoc( doc_id ), gid, Node.ATTRIBUTE_NODE ) );
            }
            r.close();
        } catch ( SQLException e ) {
            LOG.debug( e );
        }
        pool.release( con );
        NodeProxy parent;
        NodeProxy l;
        for ( Iterator i = temp.iterator(); i.hasNext();  ) {
            l = (NodeProxy) i.next();
            // if this node has a parent that belongs to the
            parent =
                context.parentWithChild( l, false, true );
            if ( parent != null )
                result.add( parent );

        }
        LOG.info( "getNodeIdsEqualTo took " + ( System.currentTimeMillis() - start ) );
        LOG.info( "found: " + result.getLength() );
        return result;
    }


    /**
     *  Get a collection or create it if necessary.
     *
     *@param  name                           Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                The orCreateCollection value
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public Collection getOrCreateCollection( User user, String name )
         throws PermissionDeniedException {
        if ( name.length() > 0 && name.charAt( 0 ) != '/' )
            name = "/" + name;

        if ( !name.startsWith( "/db" ) )
            name = "/db" + name;

        if ( name.endsWith( "/" ) && name.length() > 1 )
            name = name.substring( 0, name.length() - 1 );

        StringTokenizer tok = new StringTokenizer( name, "/" );
        String temp = tok.nextToken();
        String path = "/db";
        Collection sub;
        Collection current = getCollection( "/db" );
        if ( current == null ) {
            LOG.debug( "creating root collection /db" );
            current = new Collection( this, "/db" );
            current.getPermissions().setPermissions( 0775 );
            current.getPermissions().setOwner( user );
            current.getPermissions().setGroup( user.getPrimaryGroup() );
            //current.setId( getNextCollectionId() );
            saveCollection( current );
        }
        while ( tok.hasMoreTokens() ) {
            if ( !current.getPermissions().validate( user, Permission.WRITE ) )
                throw new PermissionDeniedException( "collection " + current.getName() +
                    " write permission denied" );
            temp = tok.nextToken();
            path = path + "/" + temp;
            if ( current.hasSubcollection( temp ) )
                current = getCollection( path );
            else {
                LOG.debug( "creating collection " + path );
                sub = new Collection( this, path );
                sub.getPermissions().setOwner( user );
                sub.getPermissions().setGroup( user.getPrimaryGroup() );
                //sub.setId( getNextCollectionId() );
                current.addCollection( temp );
                saveCollection( current );
                current = sub;
            }
        }
        return current;
    }


    /**
     *  get a node-range from the database. This method is mainly called by
     *  ElementImpl.getChildNodes() to retrieve child-nodes. It is faster than
     *  calling objectWith() for every node, since only one sql-statement is
     *  needed. If the broker's retrieval-mode is set to PRELOAD, this method
     *  will do a read-ahead by calling "preloadChildren()" and preload all the
     *  child nodes of this node-range.
     *
     *@param  doc    Description of the Parameter
     *@param  first  Description of the Parameter
     *@param  last   Description of the Parameter
     *@return        The range value
     */
    public NodeList getRange( Document doc, long first, long last ) {
        DocumentImpl d = (DocumentImpl) doc;
        NodeListImpl result = new NodeListImpl( (int) ( last - first + 1 ) );
        Node node;
        for ( ; first <= last; first++ )
            if ( ( node = cache.get( d, first ) ) != null )
                result.add( node );
            else
                break;

        if ( first > last )
            return result;

        try {
            ResultSet r;
            m_getNodeRange.setInt( 1, d.getDocId() );
            m_getNodeRange.setLong( 2, first );
            m_getNodeRange.setLong( 3, last );
            r = m_getNodeRange.executeQuery();

            while ( r.next() ) {
                node = createNode( d, r );
                if ( node == null )
                    continue;
                cache.add( (NodeImpl) node );
                result.add( node );
            }
            if ( mRetrvMode == PRELOAD )
                preloadChildren( d, first, last );

            return result;
        } catch ( SQLException sqe ) {
            LOG.debug( sqe );
            return result;
        }
    }


    /**
     *  Gets the serializer attribute of the RelationalBroker object
     *
     *@return    The serializer value
     */
    public Serializer getSerializer() {
        xmlSerializer.reset();
        return xmlSerializer;
    }


    /**
     *  returns the TextSearchEngine responsible for this broker.
     *
     *@return    The textEngine value
     */
    public TextSearchEngine getTextEngine() {
        return textEngine;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public Serializer newSerializer() {
        return new Serializer( this, config );
    }


    /**
     *  read a single object from the database. This method retrieves a single
     *  node. If the ObjectPool does already contain the node, it will get it
     *  from there. There are other, more specialized methods for retrieving a
     *  range of nodes or a whole document.
     *
     *@param  doc  Description of the Parameter
     *@param  gid  Description of the Parameter
     *@return      Description of the Return Value
     */
    public Node objectWith( Document doc, long gid ) {
        DocumentImpl d = (DocumentImpl) doc;
        Node node;
        if ( ( node = cache.get( d, gid ) ) != null )
            return node;
        try {
            ResultSet r;
            m_getNodeDataStmt.setInt( 1, d.getDocId() );
            m_getNodeDataStmt.setLong( 2, gid );
            r = m_getNodeDataStmt.executeQuery();

            if ( !r.next() ) {
                LOG.debug( "Node " + gid + " not found." );
                return null;
            }

            node = createNode( d, r );
            if ( node == null ) {
                LOG.debug( "node " + gid + " is null." );
                return null;
            }
            cache.add( (NodeImpl) node );

            r.close();
            return node;
        } catch ( SQLException sqe ) {
            LOG.debug( sqe );
            return null;
        }
    }


    private void openTempFiles( String tmpDir ) {
        try {
            // delete old files
            //new File(DOM_FILE).delete();
            //new File(CDATA_FILE).delete();
            //new File(ATTR_FILE).delete();
            File tmp = new File( tmpDir );
            File f = File.createTempFile( CDATA_FILE, ".txt", tmp );
            FileOutputStream outf = new FileOutputStream( f );
            s_cdata = new TableOutputStream( f.getCanonicalPath(), outf, enc );
            f = File.createTempFile( DOM_FILE, ".txt", tmp );
            outf = new FileOutputStream( f );
            s_dom = new TableOutputStream( f.getCanonicalPath(), outf, enc );
            f = File.createTempFile( ATTR_FILE, ".txt", tmp );
            outf = new FileOutputStream( f );
            s_attr = new TableOutputStream( f.getCanonicalPath(), outf, enc );
        } catch ( FileNotFoundException fne ) {
            LOG.debug( fne );
        } catch ( IOException ioe ) {
            LOG.debug( ioe );
        }
    }


    /**
     *  for every node in a range of nodes, starting with first and ending with
     *  last, retrieve it's child nodes and store them into the ObjectPool.
     *
     *@param  doc    Description of the Parameter
     *@param  first  Description of the Parameter
     *@param  last   Description of the Parameter
     */
    protected void preloadChildren( DocumentImpl doc, long first, long last ) {
        int level = doc.getTreeLevel( first );
        if ( level == doc.getMaxDepth() )
            return;
        long start = getFirstChildID( doc, level, first );
        long end = getLastChildID( doc, level, last );
        try {
            ResultSet r;
            m_getNodeRange.setInt( 1, doc.getDocId() );
            m_getNodeRange.setLong( 2, start );
            m_getNodeRange.setLong( 3, end );
            r = m_getNodeRange.executeQuery();

            Node node;
            while ( r.next() ) {
                node = createNode( doc, r );
                if ( node == null )
                    continue;

                cache.add( (NodeImpl) node );
            }
        } catch ( SQLException sqe ) {
            LOG.debug( sqe );
        }
    }


    private void preloadElementNames() {
        String sql = "select element_id, name from element_names";
        try {
            ResultSet r = stmt.executeQuery( sql );
            while ( r.next() )
                elementIds.put( r.getString( 2 ).toLowerCase(), new Integer( r.getInt( 1 ) ) );

            r.close();
        } catch ( SQLException se ) {
            LOG.debug( se );
        }
    }


    private DocumentImpl readDocument( ResultSet r ) throws SQLException {
        int doc_id = r.getShort( 1 );
        String doctype = r.getString( 2 );
        String publicId = r.getString( 3 );
        String systemId = r.getString( 4 );
        int children = r.getInt( 5 );
        String name = r.getString( 6 );

        DocumentImpl doc = new DocumentImpl( this, name );
        doc.setChildCount( children );
        doc.setDocId( doc_id );

        doc.setBroker( this );
        if ( doctype != null && doctype.length() > 0 ) {
            DocumentTypeImpl docType =
                new DocumentTypeImpl( doctype, publicId, systemId );
            doc.setDocumentType( docType );
        }

        m_getTreeInfo.setInt( 1, doc_id );
        ResultSet rs = m_getTreeInfo.executeQuery();
        int o;
        int level = 0;
        while ( rs.next() ) {
            o = rs.getInt( 1 );
            doc.setTreeLevelOrder( level++, o );
        }
        doc.setMaxDepth( level + 1 );
        try {
        	doc.calculateTreeLevelStartPoints();
        } catch(EXistException e) {
        }
        return doc;
    }


    /**
     *  associate a prefix with a given namespace. Every broker subclass should
     *  keep an internal map, where it stores the prefixes used for different
     *  namespaces. It should be guaranteed that only one prefix is associated
     *  with one namespace URI.
     *
     *@param  namespace  Description of the Parameter
     *@param  prefix     Description of the Parameter
     */
    public void registerNamespace( String namespace, String prefix ) {
        // there's already a prefix for this namespace
        if ( getNamespacePrefix( namespace ) != null )
            return;
        try {
            m_storeNamespace.setString( 1, namespace );
            m_storeNamespace.setString( 2, prefix );
            m_storeNamespace.executeUpdate();
            namespaces.put( namespace, prefix );
        } catch ( SQLException sqe ) {
            LOG.debug( sqe );
        }
    }


    /**
     *  Description of the Method
     *
     *@param  lock  Description of the Parameter
     */
    public void releaseWriteLock( Object lock ) { }


    /**
     *  Description of the Method
     *
     *@param  collName                       Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                Description of the Return Value
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public boolean removeCollection( User user, String collName )
         throws PermissionDeniedException {
        Collection collection = getCollection( collName );
        if ( collection == null ) {
            LOG.debug( "collection " + collName + " not found" );
            return false;
        }
        LOG.info( "removing collection " + collName );
        Collection parent = collection.getParent();
        if ( !collection.getPermissions().validate( user, Permission.WRITE ) )
            throw new PermissionDeniedException( "not allowed to remove collection" );
        if ( parent != null ) {
            parent.removeCollection( collName.substring( collName.lastIndexOf( "/" ) + 1 ) );
            saveCollection( parent );
        }
        String childCollection;
        LOG.debug( "removing sub-collections" );
        for ( Iterator i = collection.collectionIterator(); i.hasNext();  ) {
            childCollection = (String) i.next();
            removeCollection( user, collName + '/' + childCollection );
        }
        try {
            m_deleteCollectionStmt.setString( 1, collName );
            m_deleteCollectionStmt.executeUpdate();
        } catch ( SQLException e ) {
            LOG.error( e.getMessage() );
        }
        LOG.info( "collection " + collName + " removed." );
        return true;
    }


    /**
     *  Description of the Method
     *
     *@param  name                           Description of the Parameter
     *@param  user                           Description of the Parameter
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public void removeDocument( User user, String name )
         throws PermissionDeniedException {
        DocumentImpl doc = (DocumentImpl) getDocument( name );
        if ( doc == null ) {
            LOG.debug( "document " + name + " not found!" );
            return;
        }
        removeDocument( user, doc );
    }


    /**
     *  remove a document and all it's content from the database
     *
     *@param  doc                            Description of the Parameter
     *@param  user                           Description of the Parameter
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public void removeDocument( User user, DocumentImpl doc )
         throws PermissionDeniedException {
        LOG.debug("removing document: " + doc.getFileName());
        String delNodeStmt = "delete from dom where doc_id=" +
            doc.getDocId();
        String delElementStmt = "delete from b_element where doc_id=" +
            doc.getDocId();
        String delTextStmt = "delete from cdata where doc_id=" +
            doc.getDocId();
        String delInvStmt = "delete from inv_idx where doc_id=" +
            doc.getDocId();
        String delAttrStmt = "delete from attr where doc_id=" +
            doc.getDocId();
        String delXTreeStmt = "delete from xtree where doc_id=" +
            doc.getDocId();
        String delDocStmt = "delete from documents where doc_id=" +
            doc.getDocId();
        String delCommentStmt = "delete from comment_node where doc_id="
             + doc.getDocId();
        String delPiStmt = "delete from processing_instruction where doc_id="
             + doc.getDocId();
        try {
            stmt.executeUpdate( delNodeStmt );
            stmt.executeUpdate( delElementStmt );
            stmt.executeUpdate( delTextStmt );
            stmt.executeUpdate( delInvStmt );
            stmt.executeUpdate( delAttrStmt );
            stmt.executeUpdate( delXTreeStmt );
            stmt.executeUpdate( delCommentStmt );
            stmt.executeUpdate( delPiStmt );
            stmt.executeUpdate( delDocStmt );
            Collection collection = doc.getCollection();
            collection.removeDocument( doc.getFileName() );
            saveCollection( collection );
        } catch ( SQLException e ) {
            LOG.debug( e );
        }
        elementPool.clear();
        cache.clear();
    }


    /**
     *  Description of the Method
     *
     *@param  collection  Description of the Parameter
     */
    public void saveCollection( Collection collection ) 
    throws PermissionDeniedException {
        //ByteArrayOutputStream bstream = new ByteArrayOutputStream();
        //DataOutputStream ostream = new DataOutputStream( bstream );
        VariableByteOutputStream ostream = new VariableByteOutputStream( );
        try {
            ostream.writeInt( collection.getDocumentCount() );
            if ( collection.getDocumentCount() > 0 ) {
                DocumentImpl doc;
                for ( Iterator i = collection.iterator(); i.hasNext();  ) {
                    doc = (DocumentImpl) i.next();
                    doc.write( ostream );
                }
            }
            ostream.close();
        } catch ( IOException e ) {
            LOG.debug( e.getMessage() );
        }
        byte[] data = ostream.toByteArray();
        Object lock = collectionsLock.acquire();
        try {
            m_getCollectionStmt.setString( 1, collection.getName() );
            ResultSet r = m_getCollectionStmt.executeQuery();
            if ( !r.next() ) {
                m_insertCollectionStmt.setString( 1, collection.getName() );
                m_insertCollectionStmt.setString( 2, collection.getPermissions().getOwner() );
                m_insertCollectionStmt.setString( 3, collection.getPermissions().getOwnerGroup() );
                m_insertCollectionStmt.setInt( 4, collection.getPermissions().getPermissions() );
                m_insertCollectionStmt.setBytes( 5, data );
                m_insertCollectionStmt.executeUpdate();
            }
            else {
                m_updateCollectionStmt.setString( 1, collection.getPermissions().getOwner() );
                m_updateCollectionStmt.setString( 2, collection.getPermissions().getOwnerGroup() );
                m_updateCollectionStmt.setInt( 3, collection.getPermissions().getPermissions() );
                m_updateCollectionStmt.setBytes( 4, data );
                m_updateCollectionStmt.setString( 5, collection.getName() );
                m_updateCollectionStmt.executeUpdate();
            }
            if ( mDatabaseType != MYSQL )
                m_insertCollectionStmt.getConnection().commit();
        } catch ( SQLException e ) {
            LOG.debug( e.getMessage() );
        } finally {
            collectionsLock.release( lock );
        }
    }


    /**
     *  set the current retrieval-mode There are two retrieval modes: SINGLE and
     *  PRELOAD. With retrieval mode set to PRELOAD, the broker will try to do a
     *  read ahead when retrieving nodes. This means, that it will not only
     *  retrieve the actual nodes, but also their children. The additional nodes
     *  will be put into the ObjectPool where they will be found by subsequent
     *  calls to objectWith. The advantage is that we need less sql-statements
     *  to retrieve a certain portion of the document. On the other hand, nodes
     *  may be read which are not really needed.
     *
     *@param  mode  The new retrvMode value
     */
    public void setRetrvMode( int mode ) {
        mRetrvMode = mode;
    }


    /**
     *  shutdown the database This will also process all temporary files.
     */
    public void shutdown() {
        super.shutdown();
        idWorker.terminate();
        flush();
        textEngine.close();
        LOG.debug( "closing all connections" );
        pool.closeAll();
    }


    /**
     *  store a Node-Object into the database
     *
     *@param  node  Description of the Parameter
     *@param  path  Description of the Parameter
     */
    public void store( NodeImpl node, CharSequence path ) {
        switch ( node.getNodeType() ) {
            case Node.ELEMENT_NODE:
            {
                ElementImpl temp = (ElementImpl) node;
                int elementId = -1;
                String nodeName = temp.getNodeName();
                // first, try to get an elementId for this node-name.
                // elementId's are cached
                if ( elementIds.containsKey( nodeName ) )
                    elementId = ( (Integer) elementIds.get( nodeName ) ).intValue();
                else {
                    Object lock = elementIdsLock.acquire();
                    try {
                        if ( mDatabaseType != MYSQL )
                            m_insertElementId.getConnection().setAutoCommit( true );

                        m_getElementId.setString( 1, nodeName );
                        ResultSet r = m_getElementId.executeQuery();
                        if ( !r.next() ) {
                            elementId = getNextElementId();
                            m_insertElementId.setInt( 1, elementId );
                            m_insertElementId.setString( 2, nodeName );
                            m_insertElementId.executeUpdate();
                            if ( mDatabaseType != MYSQL )
                                m_insertElementId.getConnection().setAutoCommit( false );

                        }
                        else
                            elementId = r.getInt( 1 );

                        elementIds.put( nodeName, new Integer( elementId ) );
                    } catch ( SQLException e ) {
                        LOG.warn( "sql exception while storing element id: " + e );
                    } finally {
                        elementIdsLock.release( lock );
                    }
                }
                node.setNodeNameRef( elementId );

                // save element by calling ElementIndex
                NodeProxy proxy =
                    new NodeProxy( (DocumentImpl) temp.getOwnerDocument(),
                    temp.getGID() );
                t_elementIndex.setDocument( (DocumentImpl) temp.getOwnerDocument() );
                t_elementIndex.addRow( elementId, proxy );
                break;
            }
            case Node.TEXT_NODE:
            {
                TextImpl temp = (TextImpl) node;
                Object[] textData = new Object[3];
                textData[0] = new Integer( ( (DocumentImpl) temp.getOwnerDocument() ).getDocId() );
                textData[1] = new Long( temp.getGID() );
                // save text-data to file if user want's this
                if ( m_toFiles ) {
                    textData[2] = temp.getData();
                    try {
                        s_cdata.writeRow( textData );
                    } catch ( IOException io ) {
                        LOG.debug( io );
                    }
                }
                else {
                    // otherwise, store it directly to the database
                    textData[2] = escapeCharacters( temp.getData() );
                    t_insertTextStmt.append( textData );
                }
                // check if this textual content should be fulltext-indexed
                // by calling IndexPaths.match(path)
                IndexPaths idx =
                    (IndexPaths) config.getProperty( "indexScheme." +
                    temp.getOwnerDocument().getDoctype().getName() );
                if ( idx == null || idx.match( path ) )
                    textEngine.storeText( idx, temp );

                break;
            }
            case Node.ATTRIBUTE_NODE:
            {
                AttrImpl attrib = (AttrImpl) node;
                Object[] attrData = {
                    new Integer( ( (DocumentImpl) attrib.getOwnerDocument() ).getDocId() ),
                    new Long( attrib.getGID() ),
                    attrib.getName(),
                    attrib.getValue()
                    };
                if ( m_toFiles )
                    try {
                        s_attr.writeRow( attrData );
                    } catch ( IOException io ) {
                        LOG.debug( io );
                    }
                else
                    t_insertAttr.append( attrData );

                break;
            }
            case Node.PROCESSING_INSTRUCTION_NODE:
            {
                try {
                    ProcessingInstructionImpl pi = (ProcessingInstructionImpl) node;
                    m_storePI.setInt( 1,
                        ( (DocumentImpl) pi.getOwnerDocument() ).getDocId() );
                    m_storePI.setLong( 2, pi.getGID() );
                    m_storePI.setString( 3, pi.getTarget() );
                    m_storePI.setString( 4, pi.getData() );
                    m_storePI.executeUpdate();
                    if ( mDatabaseType != MYSQL )
                        m_storePI.getConnection().commit();

                } catch ( SQLException sqe ) {
                    LOG.debug( "sql exception while storing pi: " + sqe );
                    return;
                }
                break;
            }
            case Node.COMMENT_NODE:
            {
                try {
                    CommentImpl comment = (CommentImpl) node;
                    m_storeComment.setInt( 1,
                        ( (DocumentImpl) comment.getOwnerDocument() ).getDocId() );
                    m_storeComment.setLong( 2, comment.getGID() );
                    m_storeComment.setString( 3, comment.getData() );
                    m_storeComment.executeUpdate();
                    if ( mDatabaseType != MYSQL )
                        m_storePI.getConnection().commit();

                } catch ( SQLException sqe ) {
                    LOG.debug( "sql exception while storing comment: " + sqe );
                }
                break;
            }
            default:
                return;
        }
        // get namespace prefixes used in this element
        StringBuffer prefixes = new StringBuffer();
        if ( node.getNodeType() == Node.ELEMENT_NODE &&
            ( (ElementImpl) node ).declaresNamespacePrefixes() )
            for ( Iterator i = ( (ElementImpl) node ).getNamespacePrefixes(); i.hasNext();  ) {
                if ( prefixes.length() > 0 )
                    prefixes.append( ';' );

                prefixes.append( (String) i.next() );
            }

        // now store the node's data to the dom-table
        if ( m_toFiles ) {
            Object[] row = {
                new Integer( ( (DocumentImpl) node.getOwnerDocument() ).getDocId() ),
                new Long( node.getGID() ), new Short( node.getNodeType() ),
                new Integer( node.getChildCount() ),
                new Integer( node.getNodeNameRef() ),
                new Integer( node.getAttributesCount() ), prefixes.toString()
                };
            try {
                s_dom.writeRow( row );
            } catch ( IOException io ) {
                LOG.debug( io );
            }
        }
        else
            try {
                m_insertNodeStmt.setInt( 1, ( (DocumentImpl) node.getOwnerDocument() ).getDocId() );
                m_insertNodeStmt.setLong( 2, node.getGID() );
                m_insertNodeStmt.setShort( 3, node.getNodeType() );
                m_insertNodeStmt.setInt( 4, node.getChildCount() );
                m_insertNodeStmt.setInt( 5, node.getNodeNameRef() );
                m_insertNodeStmt.setInt( 6, node.getAttributesCount() );
                m_insertNodeStmt.setString( 7, prefixes.toString() );
                m_insertNodeStmt.executeUpdate();
            } catch ( SQLException e ) {
                LOG.debug( e );
            }

    }


    /**
     *  store a Document-Object into the database
     *
     *@param  doc  Description of the Parameter
     */
    public void storeDocument( DocumentImpl doc ) {
        Object lock = documentsLock.acquire();
        String docName = doc.getFileName();
        docName = NativeBroker.normalizeCollectionName( docName );
        if ( docName.length() > 0 && docName.charAt( 0 ) != '/' )
            docName = "/" + docName;

        if ( !docName.startsWith( "/db" ) )
            docName = "/db" + docName;

        if ( docName.endsWith( "/" ) && docName.length() > 1 )
            docName = docName.substring( 0, docName.length() - 1 );

        try {
            if ( mDatabaseType != MYSQL )
                m_insertDocStmt.getConnection().setAutoCommit( true );

            int doc_id = doc.getDocId();
            if(doc_id < 0)
                doc_id = getNextDocId(null);
            m_insertDocStmt.setInt( 1, doc_id );
            m_insertDocStmt.setString( 2, docName );
            m_insertDocStmt.setInt( 3, doc.getChildCount() );
            DocumentTypeImpl doctype = (DocumentTypeImpl) doc.getDoctype();
            if ( doctype == null ) {
                m_insertDocStmt.setString( 4, null );
                m_insertDocStmt.setString( 5, null );
                m_insertDocStmt.setString( 6, null );
            }
            else {
                m_insertDocStmt.setString( 4, doctype.getName() );
                m_insertDocStmt.setString( 5, doctype.getPublicId() );
                m_insertDocStmt.setString( 6, doctype.getSystemId() );
            }
            m_insertDocStmt.execute();
            if ( mDatabaseType != MYSQL )
                m_insertDocStmt.getConnection().commit();

            doc.setDocId( doc_id );
            try {
            	doc.calculateTreeLevelStartPoints();
            } catch(EXistException e) {
            }
            for ( int i = 0; i < doc.getMaxDepth(); i++ ) {
                m_insertTreeInfo.setInt( 1, doc_id );
                m_insertTreeInfo.setInt( 2, doc.getTreeLevelOrder( i ) );
                m_insertTreeInfo.setInt( 3, i );
                m_insertTreeInfo.setLong( 4, doc.getLevelStartPoint( i ) );
                m_insertTreeInfo.setLong( 5, doc.getLevelStartPoint( i + 1 ) );
                m_insertTreeInfo.executeUpdate();
            }
            if ( mDatabaseType != MYSQL )
                m_insertDocStmt.getConnection().setAutoCommit( false );

        } catch ( SQLException e ) {
            LOG.debug( "storeDocument(): " + e );
        } finally {
            documentsLock.release( lock );
        }
    }


    /**
     *  Description of the Method
     *
     *@param  in  Description of the Parameter
     *@return     Description of the Return Value
     */
    protected String unescapeCharacters( String in ) {
        int l = in.length();
        StringBuffer buf = new StringBuffer( l );
        for ( int i = 0; i < l; i++ )
            if ( in.charAt( i ) == '\\' )
                switch ( in.charAt( ++i ) ) {
                    case '\'':
                        buf.append( '\'' );
                        break;
                    case '`':
                        buf.append( '`' );
                        break;
                    case '\\':
                        buf.append( '\\' );
                }
            else
                buf.append( in.charAt( i ) );

        return buf.toString();
    }


    /**
     *  Description of the Class
     *
     *@author     wolf
     *@created    3. Juni 2002
     */
    protected final static class TableLock {

        protected Object _lock;


        /**  Constructor for the TableLock object */
        public TableLock() {
            _lock = new Object();
        }


        /**
         *  Description of the Method
         *
         *@return    Description of the Return Value
         */
        public synchronized Object acquire() {
            while ( _lock == null )
                try {
                    wait();
                } catch ( InterruptedException ex ) {
                }

            Object key = _lock;
            _lock = null;
            return key;
        }


        /**
         *  Description of the Method
         *
         *@param  lock  Description of the Parameter
         */
        public synchronized void release( Object lock ) {
            _lock = lock;
            notifyAll();
        }
    }


    /**
     *  special output-stream, used for writing temporary files.
     *
     *@author     wolf
     *@created    3. Juni 2002
     */
    protected final static class TableOutputStream extends BufferedOutputStream {

        protected final static int EOL = 10;
        protected final static int FIELD_SEP = '|';
        protected String encoding = "UTF-8";
        protected String fileName;


        /**
         *  Constructor for the TableOutputStream object
         *
         *@param  fileName  Description of the Parameter
         *@param  out       Description of the Parameter
         *@param  encoding  Description of the Parameter
         */
        public TableOutputStream( String fileName, OutputStream out, String encoding ) {
            super( out );
            this.fileName = fileName;
            this.encoding = encoding;
        }


        /**
         *  Constructor for the TableOutputStream object
         *
         *@param  fileName  Description of the Parameter
         *@param  out       Description of the Parameter
         *@param  size      Description of the Parameter
         */
        public TableOutputStream( String fileName, OutputStream out, int size ) {
            super( out, size );
            this.fileName = fileName;
        }


        /**
         *  Gets the fileName attribute of the TableOutputStream object
         *
         *@return    The fileName value
         */
        public String getFileName() {
            return fileName;
        }


        /**
         *  Description of the Method
         *
         *@param  bytes            Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void writeBytes( byte[] bytes ) throws IOException {
            writeEscaped( bytes );
        }


        /**
         *  Description of the Method
         *
         *@param  data             Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        protected void writeEscaped( byte[] data ) throws IOException {
            for ( int i = 0; i < data.length; i++ )
                switch ( (char) data[i] ) {
                    case 0:
                        write( '\\' );
                        write( '0' );
                        break;
                    case '\\':
                        write( '\\' );
                        write( '\\' );
                        break;
                    case '\'':
                        write( '\\' );
                        write( '\'' );
                        break;
                    case '"':
                        write( '\\' );
                        write( '"' );
                        break;
                    case FIELD_SEP:
                        write( '\\' );
                        write( FIELD_SEP );
                        break;
                    case EOL:
                        write( ' ' );
                        //write('\\');
                        //write(EOL);
                        break;
                    default:
                        write( data[i] );
                }

        }


        /**
         *  Description of the Method
         *
         *@param  value            Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void writeInt( Integer value ) throws IOException {
            writeString( value.toString() );
        }


        /**
         *  Description of the Method
         *
         *@param  value            Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void writeLong( Long value ) throws IOException {
            writeString( value.toString() );
        }


        /**
         *  Description of the Method
         *
         *@param  row              Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void writeRow( Object[] row ) throws IOException {
            for ( int i = 0; i < row.length; i++ ) {
                if ( row[i] instanceof String )
                    writeString( (String) row[i] );
                else if ( row[i] instanceof Integer )
                    writeInt( (Integer) row[i] );
                else if ( row[i] instanceof byte[] )
                    writeBytes( (byte[]) row[i] );
                else if ( row[i] instanceof Long )
                    writeLong( (Long) row[i] );
                else if ( row[i] instanceof Short )
                    writeShort( (Short) row[i] );

                if ( i < row.length - 1 )
                    write( FIELD_SEP );

            }
            write( EOL );
        }


        /**
         *  Description of the Method
         *
         *@param  value            Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void writeShort( Short value ) throws IOException {
            writeString( value.toString() );
        }


        /**
         *  Description of the Method
         *
         *@param  data             Description of the Parameter
         *@exception  IOException  Description of the Exception
         */
        public void writeString( String data ) throws IOException {
            writeBytes( data.getBytes( encoding ) );
        }
    }


    /**
     *  this class is used by getElementsByTagName It runs in it's own thread
     *  and converts the byte-stream it gets from getElementsByTagName to Nodes.
     *
     *@author     wolf
     *@created    3. Juni 2002
     */
    class ElementIdsWorkerThread extends Thread {
        protected DocumentSet docs;
        protected DocumentImpl lastDoc = null;
        protected int lastDocId = -1;

        protected LinkedList queue = new LinkedList();
        protected NodeSet result = null;
        protected boolean terminate = false;


        /**  Constructor for the ElementIdsWorkerThread object */
        public ElementIdsWorkerThread() { }


        /**
         *  Description of the Method
         *
         *@param  docs    Description of the Parameter
         *@param  result  Description of the Parameter
         *@param  docId   Description of the Parameter
         *@param  data    Description of the Parameter
         */
        public synchronized void add( DocumentSet docs, NodeSet result, int docId, byte[] data ) {
            this.result = result;
            this.docs = docs;
            queue.addFirst( new QueuedData( docId, data ) );
            this.notifyAll();
        }


        /**
         *  Gets the result attribute of the ElementIdsWorkerThread object
         *
         *@return    The result value
         */
        public synchronized NodeSet getResult() {
            while ( queue.size() > 0 ) {
                LOG.debug( "waiting for worker to finish" );
                try {
                    this.wait();
                } catch ( InterruptedException ie ) {
                }
            }
            return result;
        }


        /**  Main processing method for the ElementIdsWorkerThread object */
        public void run() {
            synchronized ( this ) {
                while ( true ) {
                    try {
                        this.wait();
                    } catch ( InterruptedException ie ) {
                    }
                    if ( terminate )
                        return;
                    if ( queue.size() > 0 )
                        work();

                }
            }
        }


        /**  Description of the Method */
        public synchronized void terminate() {
            terminate = true;
            this.notifyAll();
        }


        private void work() {
            while ( queue.size() > 0 ) {
                QueuedData qd = (QueuedData) queue.removeLast();
                long gid;
                for ( int i = 0; i < qd.data.length; i += 8 ) {
                    gid = ByteConversion.byteToLong( qd.data, i );
                    // save some time here by avoiding calls to docs.get()
                    if ( qd.docId != lastDocId ) {
                        lastDoc = docs.getDoc( qd.docId );
                        lastDocId = qd.docId;
                    }
                    result.add( new NodeProxy( lastDoc, gid, Node.ELEMENT_NODE ) );
                }
            }
            this.notifyAll();
        }


        /**
         *  Description of the Class
         *
         *@author     wolf
         *@created    3. Juni 2002
         */
        class QueuedData {
            byte[] data;
            int docId;


            /**
             *  Constructor for the QueuedData object
             *
             *@param  docId  Description of the Parameter
             *@param  data   Description of the Parameter
             */
            public QueuedData( int docId, byte[] data ) {
                this.docId = docId;
                this.data = data;
            }
        }
    }


	/* (non-Javadoc)
	 * @see org.exist.storage.DBBroker#objectWith(org.exist.dom.NodeProxy)
	 */
	public Node objectWith(NodeProxy p) {
		// TODO Auto-generated method stub
		return null;
	}

}

