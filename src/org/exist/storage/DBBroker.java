
/*
 *  DBBroker.java - eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
 *  http://exist.sourceforge.net
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.exist.storage;
import java.io.DataInput;
import java.util.Iterator;
import java.util.Observable;

import org.exist.dom.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SymbolTable;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.util.Occurrences;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    20. Mai 2002
 */
public abstract class DBBroker extends Observable {
    /**  Description of the Field */
    public final static int DBM = 3;

    /**  Description of the Field */
    public final static int MATCH_EXACT = 0;
    /**  Description of the Field */
    public final static int MATCH_REGEXP = 1;
    public final static int MATCH_WILDCARDS = 2;

    // constants for database type
    /**  Description of the Field */
    public final static int MYSQL = 0;
    /**  Description of the Field */
    public final static int NATIVE = 4;
    /**  Description of the Field */
    public final static int ORACLE = 1;
    /**  Description of the Field */
    public final static int POSTGRESQL = 2;

    protected boolean caseSensitive = true;

    protected Configuration config;
	protected BrokerPool pool;

    /**
     *  Constructor for the DBBroker object
     *
     *@param  config  Description of the Parameter
     */
    public DBBroker( BrokerPool pool, Configuration config ) {
        this.config = config;
        Boolean temp;
        if ( ( temp = (Boolean) config.getProperty( "indexer.case-sensitive" ) )
             != null )
            caseSensitive = temp.booleanValue();
		this.pool = pool;
    }


    /**
     *  lock this broker instance for writing. The broker instance is
     *  responsible for locking underlying files. Right now, BDBBroker has no
     *  clean locking mechanism, so all files will be locked when this method is
     *  called. Class RelationalBroker is safe and will ignore calls to
     *  acquireWriteLock().
     *
     *@return    Description of the Return Value
     */
    public abstract Object acquireWriteLock();


    /**
     *  load all fields of element from the database. Note that loading of nodes
     *  is sometimes deferred until information is really needed. Method
     *  objectWith will only load fields common to all node types. elementWith
     *  is called by ElementImpl if needed.
     *
     *@param  element  Description of the Parameter
     *@return          Description of the Return Value
     */
    public boolean elementWith( ElementImpl element ) {
    	return true;
    }


    /**
     *  find elements by their tag name. This method is comparable to the DOM's
     *  method call getElementsByTagName. All elements matching tagName and
     *  belonging to one of the documents in the DocumentSet docs are returned.
     *
     *@param  docs     Description of the Parameter
     *@param  tagName  Description of the Parameter
     *@return          Description of the Return Value
     */
    public abstract NodeSet findElementsByTagName( DocumentSet docs, String tagName );


    /**  flush all data that has not been written before. */
    public void flush() {
        /*
         *  do nothing
         */
    }


    /**
     *  Gets the allDocuments attribute of the DBBroker object
     *
     *@return    The allDocuments value
     */
    public DocumentSet getAllDocuments() {
        return getAllDocuments( new User( "admin", null, "dba" ) );
    }


    /**
     *  get all the documents in this database repository. The documents are
     *  returned as a DocumentSet.
     *
     *@param  user  Description of the Parameter
     *@return       The allDocuments value
     */
    public abstract DocumentSet getAllDocuments( User user );


    /**
     *  find elements by their tag name. This method is comparable to the DOM's
     *  method call getElementsByTagName. All elements matching tagName and
     *  belonging to one of the documents in the DocumentSet docs are returned.
     *
     *@param  docs  Description of the Parameter
     *@param  name  Description of the Parameter
     *@return       The attributesByName value
     */
    public abstract NodeSet getAttributesByName( DocumentSet docs, String name );


    /**
     *  Gets the collection attribute of the DBBroker object
     *
     *@param  name  Description of the Parameter
     *@return       The collection value
     */
    public abstract Collection getCollection( String name );


    /**
     *  get the configuration.
     *
     *@return    The configuration value
     */
    public Configuration getConfiguration() {
        return config;
    }


    /**
     *  Gets the dOMIterator attribute of the DBBroker object
     *
     *@param  doc  Description of the Parameter
     *@param  gid  Description of the Parameter
     *@return      The dOMIterator value
     */
    public Iterator getDOMIterator( Document doc, long gid ) {
        throw new RuntimeException( "not implemented for this storage backend" );
    }


    /**
     *  Gets the dOMIterator attribute of the DBBroker object
     *
     *@param  proxy  Description of the Parameter
     *@return        The dOMIterator value
     */
    public Iterator getDOMIterator( NodeProxy proxy ) {
        throw new RuntimeException( "not implemented for this storage backend" );
    }


    /**
     *  return the type of database this broker is connected to.
     *
     *@return    one of the constants defined above.
     */
    public abstract int getDatabaseType();


    /**
     *  Gets the document attribute of the DBBroker object
     *
     *@param  fileName                       Description of the Parameter
     *@return                                The document value
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public Document getDocument( String fileName )
         throws PermissionDeniedException {
        return getDocument( new User( "admin", null, "dba" ), fileName );
    }


    /**
     *  get a document by it's file name. The document's file name is used to
     *  identify a document. File names are stored without the leading path.
     *
     *@param  fileName                       Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                The document value
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public abstract Document getDocument( User user, String fileName )
         throws PermissionDeniedException;


    /**
     *  Gets the documentsByCollection attribute of the DBBroker object
     *
     *@param  collection                     Description of the Parameter
     *@return                                The documentsByCollection value
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public DocumentSet getDocumentsByCollection( String collection )
         throws PermissionDeniedException {
        return getDocumentsByCollection( new User( "admin", null, "dba" ), collection );
    }


    /**
     *  Gets the documentsByCollection attribute of the DBBroker object
     *
     *@param  collection                     Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                The documentsByCollection value
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public abstract DocumentSet getDocumentsByCollection( User user, String collection )
         throws PermissionDeniedException;


    /**
     *  Gets the documentsByCollection attribute of the DBBroker object
     *
     *@param  collection                     Description of the Parameter
     *@param  inclusive                      Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                The documentsByCollection value
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public abstract DocumentSet getDocumentsByCollection( User user,
                                                          String collection,
                                                          boolean inclusive )
         throws PermissionDeniedException;


    /**
     *  Gets the documentsByDoctype attribute of the DBBroker object
     *
     *@param  doctype  Description of the Parameter
     *@return          The documentsByDoctype value
     */
    public DocumentSet getDocumentsByDoctype( String doctype ) {
        return getDocumentsByDoctype( new User( "admin", null, "dba" ), doctype );
    }


    /**
     *  get all the documents in this database matching the given
     *  document-type's name.
     *
     *@param  doctypeName  Description of the Parameter
     *@param  user         Description of the Parameter
     *@return              The documentsByDoctype value
     */
    public abstract DocumentSet getDocumentsByDoctype( User user, String doctypeName );


    /**
     *  get a common prefix for a namespace URI. It should be guaranteed that
     *  only one prefix is associated with one namespace URI throughout the
     *  database.
     *
     *@param  namespace  Description of the Parameter
     *@return            The namespacePrefix value
     */
    public String getNamespacePrefix( String namespace ) {
        return "";
    }


    /**
     *  get the namespace associated with the given prefix. Every broker
     *  subclass should keep an internal map, where it stores the prefixes used
     *  for different namespaces. It should be guaranteed that only one prefix
     *  is associated with one namespace URI.
     *
     *@param  prefix  Description of the Parameter
     *@return         The namespaceURI value
     */
    public String getNamespaceURI( String prefix ) {
        return "";
    }


    /**
     *  Gets the nextDocId attribute of the DBBroker object
     *
     *@param  collection  Description of the Parameter
     *@return             The nextDocId value
     */
    public abstract int getNextDocId( Collection collection );


    /**
     *  Gets the nodeValue attribute of the DBBroker object
     *
     *@param  proxy  Description of the Parameter
     *@return        The nodeValue value
     */
    public String getNodeValue( NodeProxy proxy ) {
        throw new RuntimeException( "not implemented for this storage backend" );
    }


    /**
     *  get all the nodes containing the search terms given by the array expr
     *  using the fulltext-index. Calls to this method are normally delegated to
     *  the associated instance of class TextSearchEngine.
     *
     *@param  doc   the set of documents to search through
     *@param  expr  an array of search terms. a query is executed for each of
     *      them
     *@return       NodeSet[] an array of node sets, one for each search term
     */
    public abstract NodeSet[] getNodesContaining( DocumentSet doc, String[] expr );


    /**
     *  Gets the nodesContaining attribute of the DBBroker object
     *
     *@param  doc   Description of the Parameter
     *@param  expr  Description of the Parameter
     *@param  type  Description of the Parameter
     *@return       The nodesContaining value
     */
    public NodeSet[] getNodesContaining( DocumentSet doc, String[] expr,
                                         int type ) {
        return getNodesContaining( doc, expr, MATCH_EXACT );
    }


    /**
     *  find all Nodes whose string value is equal to expr in the document set.
     *
     *@param  context   Description of the Parameter
     *@param  docs      Description of the Parameter
     *@param  relation  Description of the Parameter
     *@param  expr      Description of the Parameter
     *@return           The nodesEqualTo value
     */
    public abstract NodeSet getNodesEqualTo( NodeSet context, DocumentSet docs, int relation, String expr );


    /**
     *  Gets the orCreateCollection attribute of the DBBroker object
     *
     *@param  name                           Description of the Parameter
     *@return                                The orCreateCollection value
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public Collection getOrCreateCollection( String name )
         throws PermissionDeniedException {
        User user = new User( "admin", null, "dba" );
        return getOrCreateCollection( user, name );
    }


    /**
     *  Retrieve a collection by name. This method is used by NativeBroker.java.
     *
     *@param  name                           Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                The orCreateCollection value
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public Collection getOrCreateCollection( User user, String name )
         throws PermissionDeniedException {
        return null;
    }


    /**
     *  get a range of nodes with given owner document from the database,
     *  starting at first and ending at last.
     *
     *@param  doc    the document the node's belong to
     *@param  first  unique id of the first node to retrieve
     *@param  last   unique id of the last node to retrieve
     *@return        The range value
     */
    public abstract NodeList getRange( Document doc, long first, long last );


    /**
     *  get an instance of the Serializer used for converting nodes back to XML.
     *  Subclasses of DBBroker may have specialized subclasses of Serializer to
     *  convert a node into an XML-string
     *
     *@return    The serializer value
     */
    public abstract Serializer getSerializer();


    /**
     *  Gets the stream attribute of the DBBroker object
     *
     *@param  doc  Description of the Parameter
     *@param  gid  Description of the Parameter
     *@return      The stream value
     */
    public DataInput getStream( Document doc, long gid ) {
        throw new RuntimeException( "not implemented for this storage backend" );
    }


    /**
     *  get the TextSearchEngine associated with this broker. Every subclass of
     *  DBBroker will have it's own implementation of TextSearchEngine.
     *
     *@return    The textEngine value
     */
    public abstract TextSearchEngine getTextEngine();


    /**
     *  Gets the caseSensitive attribute of the DBBroker object
     *
     *@return    The caseSensitive value
     */
    public boolean isCaseSensitive() {
        return caseSensitive;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public abstract Serializer newSerializer();


    /**
     *  get a node with given owner document and id from the database.
     *
     *@param  doc  the document the node belongs to
     *@param  gid  the node's unique identifier
     *@return      Description of the Return Value
     */
    public abstract Node objectWith( Document doc, long gid );


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
        // do nothing
    }


    /**
     *  release a lock. The parameter object should be the one returned by a
     *  previous call to acquireWriteLock().
     *
     *@param  lock  Description of the Parameter
     */
    public abstract void releaseWriteLock( Object lock );


    /**
     *  Description of the Method
     *
     *@param  name                           Description of the Parameter
     *@return                                Description of the Return Value
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public boolean removeCollection( String name )
         throws PermissionDeniedException {
        return removeCollection( new User( "admin", null, "dba" ), name );
    }


    /**
     *  Description of the Method
     *
     *@param  name                           Description of the Parameter
     *@param  user                           Description of the Parameter
     *@return                                Description of the Return Value
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public abstract boolean removeCollection( User user, String name )
         throws PermissionDeniedException;


    /**
     *  Description of the Method
     *
     *@param  docName                        Description of the Parameter
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public void removeDocument( String docName )
         throws PermissionDeniedException {
        removeDocument( new User( "admin", null, "dba" ), docName );
    }


    /**
     *  remove the document with the given document name.
     *
     *@param  docName                        Description of the Parameter
     *@param  user                           Description of the Parameter
     *@exception  PermissionDeniedException  Description of the Exception
     */
    public abstract void removeDocument( User user, String docName )
         throws PermissionDeniedException;


    /**
     *  Store a collection into the database.
     *
     *@param  collection  Description of the Parameter
     */
    public abstract void saveCollection( Collection collection )
    throws PermissionDeniedException;

    public void addDocument( Collection collection, DocumentImpl doc ) 
    throws PermissionDeniedException {
    }

    public static SymbolTable getSymbols() {
	    return null;
    }
    
    /**
     *  set the retrieval-mode used by all subsequent requests. The retrieval
     *  mode setting is only used by RelationalBroker. There are two retrieval
     *  modes: RelationalBroker.SINGLE and RelationalBroker.PRELOAD. With
     *  retrieval mode set to PRELOAD, the broker will try to do a read ahead
     *  when retrieving nodes. This means, that it will not only retrieve the
     *  actual nodes, but also their children. The additional nodes will be put
     *  into the ObjectPool where they will be found by subsequent calls to
     *  objectWith. The advantage is that we need less sql-statements to
     *  retrieve a certain portion of the document. On the other hand, nodes may
     *  be read which are not really needed.
     *
     *@param  mode  one of RelationalBroker.SINGLE or RelationalBroker.PRELOAD
     */
    public abstract void setRetrvMode( int mode );


    /**
     *  shutdown the broker. All open files, jdbc connections etc. should be
     *  closed.
     */
    public void shutdown() {
    }


    /**
     *  Store a node into the database. This method is called by the parser to
     *  write a node to the storage backend.
     *
     *@param  node         the node to be stored
     *@param  currentPath  path expression which points to this node's
     *      element-parent or to itself if it is an element (currently used by
     *      the Broker to determine if a node's content should be
     *      fulltext-indexed).
     */
    public abstract void store( NodeImpl node, String currentPath );


    /**
     *  Store a document into the database.
     *
     *@param  doc  Description of the Parameter
     */
    public abstract void storeDocument( DocumentImpl doc );


    /**  Description of the Method */
    public void sync() {
        /*
         *  do nothing
         */
    }


    /**
     *  Update a node's data. This method is only used by the NativeBroker. To
     *  keep nodes in a correct sequential order, it sometimes needs to update a
     *  previous written node. Warning: don't use it for other purposes.
     *  RelationalBroker does not implement this method.
     *
     *@param  node  Description of the Parameter
     */
    public void update( NodeImpl node ) {
        throw new RuntimeException( "not implemented" );
    }
    
    /**
     * Is the database running read-only? Returns false by default.
     * Storage backends should override this if they support read-only
     * mode.
     * 
     * @return boolean
     */
    public boolean isReadOnly() {
    	return false;
    }
    
    public BrokerPool getBrokerPool() {
    	return pool;
    }
    
	public void insertAfter(final NodeImpl previous, final NodeImpl node) {
		throw new RuntimeException( "not implemented" );
	}
	
	public void reindex(DocumentImpl oldDoc, DocumentImpl doc) {
		throw new RuntimeException( "not implemented" );
	}
    
    public void index(NodeImpl node) {
        throw new RuntimeException( "not implemented" );
    }
    
    public void removeNode(final NodeImpl node) {
        throw new RuntimeException( "not implemented" );
    }
    
	public Occurrences[] scanIndexedElements(User user, Collection collection, 
		boolean inclusive) throws PermissionDeniedException {
		throw new RuntimeException( "not implemented" );
	}
	
	public void readDocumentMetadata(final DocumentImpl doc) {
	}
}

