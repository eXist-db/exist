/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.backup;

import org.exist.storage.StorageAddress;
import org.w3c.dom.Node;

import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.StoredNode;
import org.exist.management.Agent;
import org.exist.management.AgentFactory;
import org.exist.numbering.NodeId;
import org.exist.security.internal.AccountImpl;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.Value;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.dom.DOMTransaction;
import org.exist.storage.index.CollectionStore;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.TerminatedException;

import java.io.IOException;

import java.util.*;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.exist.security.PermissionDeniedException;


public class ConsistencyCheck
{
    private Stack<ElementNode> elementStack      = new Stack<ElementNode>();
    private int                documentCount     = -1;

    private DBBroker           broker;
    private int                defaultIndexDepth;
    private boolean            directAccess      = false;
    private boolean            checkDocs        = false;

    /**
     * @param broker the db broker to use
     * @param directAccess set to true to bypass the collections.dbx index and perform a low-level scan instead
     * @param checkDocs set to true to perform additional checks on every document (slow)
     */
    public ConsistencyCheck( DBBroker broker, boolean directAccess, boolean checkDocs)
    {
        this.broker            = broker;
        this.defaultIndexDepth = ( (NativeBroker)broker ).getDefaultIndexDepth();
        this.directAccess      = directAccess;
        this.checkDocs = checkDocs;
    }

    /**
     * Combines {@link #checkCollectionTree(org.exist.backup.ConsistencyCheck.ProgressCallback)} and {@link
     * #checkDocuments(org.exist.backup.ConsistencyCheck.ProgressCallback)}.
     *
     * @param   callback  the callback object to report to
     *
     * @return  a list of {@link ErrorReport} objects or an empty list if no errors were found
     *
     * @throws  TerminatedException  DOCUMENT ME!
     */
    public List<ErrorReport> checkAll( ProgressCallback callback ) throws TerminatedException, PermissionDeniedException
    {
        final List<ErrorReport> errors = checkCollectionTree( callback );
        checkDocuments( callback, errors );
        return( errors );
    }


    /**
     * Run some tests on the collection hierarchy, starting at the root collection /db.
     *
     * @param   callback  callback object
     *
     * @return  a list of {@link ErrorReport} instances describing the errors found
     *
     * @throws  TerminatedException  DOCUMENT ME!
     */
    public List<ErrorReport> checkCollectionTree( ProgressCallback callback ) throws TerminatedException, PermissionDeniedException
    {
        AccountImpl.getSecurityProperties().enableCheckPasswords(false);

        try {
            final List<ErrorReport> errors = new ArrayList<ErrorReport>();
            final Collection        root   = broker.getCollection( XmldbURI.ROOT_COLLECTION_URI );
            checkCollection( root, errors, callback );
            return( errors );
        }
        finally {
            AccountImpl.getSecurityProperties().enableCheckPasswords(true);
        }
    }


    private void checkCollection( Collection collection, List<ErrorReport> errors, ProgressCallback callback ) throws TerminatedException
    {
        final XmldbURI uri = collection.getURI();
        if (callback != null)
        	{callback.startCollection( uri.toString() );}

        try {
            for(final Iterator<XmldbURI> i = collection.collectionIteratorNoLock(broker); i.hasNext(); ) {
                final XmldbURI childUri = i.next();

                try {
                    final Collection child = broker.getCollection( uri.append( childUri ) );

                    if( child == null ) {
                        final ErrorReport.CollectionError error = new org.exist.backup.ErrorReport.CollectionError( org.exist.backup.ErrorReport.CHILD_COLLECTION, "Child collection not found: " + childUri + ", parent is " + uri );
                        error.setCollectionId( collection.getId() );
                        error.setCollectionURI( childUri );
                        errors.add( error );
                        if (callback != null)
                            {callback.error( error );}
                        continue;
                    }
                    if (child.getId() != collection.getId())
                            {checkCollection( child, errors, callback );}
                }
                catch( final Exception e ) {
                    final ErrorReport.CollectionError error = new ErrorReport.CollectionError( org.exist.backup.ErrorReport.CHILD_COLLECTION, "Error while loading child collection: " + childUri + ", parent is " + uri );
                    error.setCollectionId( collection.getId() );
                    error.setCollectionURI( childUri );
                    errors.add( error );
                    if (callback != null)
                            {callback.error( error );}
                }
            }
        } catch(final PermissionDeniedException pde) {
            final ErrorReport.CollectionError error = new ErrorReport.CollectionError( org.exist.backup.ErrorReport.CHILD_COLLECTION, "Error while loading collection: " + collection.getURI() + ", parent is " + uri );
            error.setCollectionId(collection.getId() );
            error.setCollectionURI(collection.getURI());
            errors.add(error);
            if(callback != null) {
                callback.error(error);
            }
        }
    }


    public int getDocumentCount() throws TerminatedException
    {
        if( documentCount == -1 ) {
            AccountImpl.getSecurityProperties().enableCheckPasswords(false);

            try {
                final DocumentCallback cb = new DocumentCallback( null, null, false );
                broker.getResourcesFailsafe( cb, directAccess );
                documentCount = cb.docCount;
            }
            finally {
                AccountImpl.getSecurityProperties().enableCheckPasswords(true);
            }
        }
        return( documentCount );
    }


    /**
     * Run some tests on all documents stored in the database. The method checks if a document is readable and if its DOM representation is
     * consistent.
     *
     * @param   progress  progress callback
     *
     * @return  a list of {@link ErrorReport} instances describing the errors found
     *
     * @throws  TerminatedException  DOCUMENT ME!
     */
    public List<ErrorReport> checkDocuments( ProgressCallback progress ) throws TerminatedException
    {
        final List<ErrorReport> errors = new ArrayList<ErrorReport>();
        checkDocuments( progress, errors );
        return( errors );
    }


    /**
     * Run some tests on all documents stored in the database. The method checks if a document is readable and if its DOM representation is
     * consistent.
     *
     * @param   progress   progress callback
     * @param   errorList  error reports will be added to this list, using instances of class {@link ErrorReport}.
     *
     * @throws  TerminatedException  DOCUMENT ME!
     */
    public void checkDocuments( ProgressCallback progress, List<ErrorReport> errorList ) throws TerminatedException
    {
        AccountImpl.getSecurityProperties().enableCheckPasswords(false);

        try {
            final DocumentCallback cb = new DocumentCallback( errorList, progress, true );
            broker.getResourcesFailsafe( cb, directAccess );
            cb.checkDocs();
        }
        finally {
            AccountImpl.getSecurityProperties().enableCheckPasswords(true);
        }
    }

    /**
     * Check if data for the given XML document exists. Tries to load the document's root element.
     * This check is certainly not as comprehensive as {@link #checkXMLTree(org.exist.dom.DocumentImpl)},
     * but much faster.
     *
     * @param doc the document object to check
     * @return
     */
    public ErrorReport checkDocument(final DocumentImpl doc) {
        final DOMFile domDb = ( (NativeBroker)broker ).getDOMFile();
        return (ErrorReport)new DOMTransaction( this, domDb, Lock.WRITE_LOCK, doc ) {
            public Object start() {
                EmbeddedXMLStreamReader reader = null;
                try {
                    final ElementImpl root = (ElementImpl)doc.getDocumentElement();
                    if (root == null) {
                        return new ErrorReport.ResourceError(ErrorReport.RESOURCE_ACCESS_FAILED, "Failed to access document data");
                    }
                } catch( final Exception e ) {
                    e.printStackTrace();
                    return( new ErrorReport.ResourceError( org.exist.backup.ErrorReport.RESOURCE_ACCESS_FAILED, e.getMessage(), e ) );
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (XMLStreamException e) {
                            e.printStackTrace();
                        }
                    }
                }
                return null;
            }
        }.run();
    }

    /**
     * Check the persistent DOM of a document. The method traverses the entire node tree and checks it for consistency, including node relationships,
     * child and attribute counts etc.
     *
     * @param   doc  the document to check
     *
     * @return  null if the document is consistent, an error report otherwise.
     */
    public ErrorReport checkXMLTree( final DocumentImpl doc )
    {
        final DOMFile domDb = ( (NativeBroker)broker ).getDOMFile();
        return( (ErrorReport)new DOMTransaction( this, domDb, Lock.WRITE_LOCK, doc ) {
                    public Object start() {
                        EmbeddedXMLStreamReader reader = null;
                        try {
                            final ElementImpl             root            = (ElementImpl)doc.getDocumentElement();
                            reader = broker.getXMLStreamReader( root, true );
                            NodeId                  nodeId;
                            boolean                 attribsAllowed  = false;
                            int                     expectedAttribs = 0;
                            int                     attributeCount  = 0;

                            while( reader.hasNext() ) {
                                final int status = reader.next();

                                nodeId = (NodeId)reader.getProperty( EmbeddedXMLStreamReader.PROPERTY_NODE_ID );
                                ElementNode parent = null;

                                if( ( status != XMLStreamReader.END_ELEMENT ) && !elementStack.isEmpty() ) {
                                    parent = elementStack.peek();
                                    parent.childCount++;

                                    // test parent-child relation
                                    if( !nodeId.isChildOf( parent.elem.getNodeId() ) ) {
                                        return( new ErrorReport.ResourceError( ErrorReport.NODE_HIERARCHY, "Node " + nodeId + " is not a child of " + parent.elem.getNodeId() ) );
                                    }

                                    // test sibling relation
                                    if( ( parent.prevSibling != null ) && !( nodeId.isSiblingOf( parent.prevSibling ) && ( nodeId.compareTo( parent.prevSibling ) > 0 ) ) ) {
                                        return( new ErrorReport.ResourceError( ErrorReport.INCORRECT_NODE_ID, "Node " + nodeId + " is not a sibling of " + parent.prevSibling ) );
                                    }
                                    parent.prevSibling = nodeId;
                                }

                                switch( status ) {

                                    case XMLStreamReader.ATTRIBUTE: {
                                        attributeCount++;
                                        break;
                                    }

                                    case XMLStreamReader.END_ELEMENT: {
                                        if( elementStack.isEmpty() ) {
                                            return( new org.exist.backup.ErrorReport.ResourceError( ErrorReport.NODE_HIERARCHY, "Error in node hierarchy: received END_ELEMENT event " + "but stack was empty!" ) );
                                        }
                                        final ElementNode lastElem = elementStack.pop();
                                        if( lastElem.childCount != lastElem.elem.getChildCount() ) {
                                            return( new ErrorReport.ResourceError( org.exist.backup.ErrorReport.NODE_HIERARCHY, "Element reports incorrect child count: expected " + lastElem.elem.getChildCount() + " but found " + lastElem.childCount ) );
                                        }
                                        break;
                                    }

                                    case XMLStreamReader.START_ELEMENT: {
                                        if( nodeId.getTreeLevel() <= defaultIndexDepth ) {

                                            // check dom.dbx btree, which maps the node
                                            // id to the node's storage address
                                            // look up the node id and check if the
                                            // returned storage address is correct
                                            final NativeBroker.NodeRef nodeRef = new NativeBroker.NodeRef( doc.getDocId(), nodeId );

                                            try {
                                                final long p = domDb.findValue( nodeRef );

                                                if( p != reader.getCurrentPosition() ) {
                                                    final Value v = domDb.get( p );

                                                    if( v == null ) {
                                                        return( new ErrorReport.IndexError( ErrorReport.DOM_INDEX, "Failed to access node " + nodeId + " through dom.dbx index. Wrong storage address. Expected: " + p + "; got: " + reader.getCurrentPosition() + " - ", doc.getDocId() ) );
                                                    }
                                                }
                                            }
                                            catch( final Exception e ) {
                                                e.printStackTrace();
                                                return( new ErrorReport.IndexError( ErrorReport.DOM_INDEX, "Failed to access node " + nodeId + " through dom.dbx index.", e, doc.getDocId() ) );
                                            }
                                        }

                                        final StoredNode node = reader.getNode();
                                        if( node.getNodeType() != Node.ELEMENT_NODE ) {
                                            return( new org.exist.backup.ErrorReport.ResourceError( ErrorReport.INCORRECT_NODE_TYPE, "Expected an element node, received node of type " + node.getNodeType() ) );
                                        }
                                        elementStack.push( new ElementNode( (ElementImpl)node ) );
                                        attribsAllowed  = true;
                                        attributeCount  = 0;
                                        expectedAttribs = reader.getAttributeCount();
                                        break;
                                    }

                                    default: {
                                        if( attribsAllowed ) {

                                            if( attributeCount != expectedAttribs ) {
                                                return( new org.exist.backup.ErrorReport.ResourceError( ErrorReport.INCORRECT_NODE_TYPE, "Wrong number of attributes. Expected: " + expectedAttribs + "; found: " + attributeCount ) );
                                            }
                                        }
                                        attribsAllowed = false;
                                        break;
                                    }
                                }
                            }

                            if( !elementStack.isEmpty() ) {
                                return( new org.exist.backup.ErrorReport.ResourceError( ErrorReport.NODE_HIERARCHY, "Error in node hierarchy: reached end of tree but " + "stack was not empty!" ) );
                            }
                            return( null );
                        }
                        catch( final IOException e ) {
                            e.printStackTrace();
                            return( new org.exist.backup.ErrorReport.ResourceError( ErrorReport.RESOURCE_ACCESS_FAILED, e.getMessage(), e ) );
                        }
                        catch( final XMLStreamException e ) {
                            e.printStackTrace();
                            return( new ErrorReport.ResourceError( org.exist.backup.ErrorReport.RESOURCE_ACCESS_FAILED, e.getMessage(), e ) );
                        }
                        finally {
                            elementStack.clear();
                            if (reader != null) {
                                try {
                                    reader.close();
                                } catch (XMLStreamException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }.run() );
    }

    public interface ProgressCallback
    {
        void startDocument( String name, int current, int count ) throws TerminatedException;


        void startCollection( String path ) throws TerminatedException;


        void error( org.exist.backup.ErrorReport error );
    }

    private static class ElementNode
    {
        ElementImpl elem;
        int         childCount  = 0;
        NodeId      prevSibling = null;

        ElementNode( ElementImpl element )
        {
            this.elem = element;
        }
    }


    private class DocumentCallback implements BTreeCallback
    {
        private List<ErrorReport> errors;
        private ProgressCallback  progress;
        private int               docCount       = 0;
        private boolean           checkDocs;
        private int               lastPercentage = -1;
        private Agent             jmxAgent       = AgentFactory.getInstance();
        private ArrayList<DocumentImpl> docs = new ArrayList<>(8192);

        private DocumentCallback( List<ErrorReport> errors, ProgressCallback progress, boolean checkDocs )
        {
            this.errors    = errors;
            this.progress  = progress;
            this.checkDocs = checkDocs;
        }

        public boolean indexInfo( Value key, long pointer ) throws TerminatedException
        {
            final CollectionStore store        = (CollectionStore)( (NativeBroker)broker ).getStorage( NativeBroker.COLLECTIONS_DBX_ID );
            final @SuppressWarnings( "unused" )
            int             collectionId = CollectionStore.DocumentKey.getCollectionId( key );
            final int             docId        = CollectionStore.DocumentKey.getDocumentId( key );

            try {
                final byte              type    = key.data()[key.start() + Collection.LENGTH_COLLECTION_ID + DocumentImpl.LENGTH_DOCUMENT_TYPE];
                final VariableByteInput istream = store.getAsStream( pointer );
                DocumentImpl      doc     = null;

                if( type == DocumentImpl.BINARY_FILE ) {
                    doc = new BinaryDocument( broker.getBrokerPool() );
                } else {
                    doc = new DocumentImpl( broker.getBrokerPool() );
                }
                doc.read( istream );
                docCount++;

                if( checkDocs ) {

                    if( progress != null ) {
                        progress.startDocument( doc.getFileURI().toString(), docCount, getDocumentCount() );
                    }
                    int percentage = 100 * ( docCount + 1 ) / ( getDocumentCount() + 1 );

                    if( ( jmxAgent != null ) && ( percentage != lastPercentage ) ) {
                        lastPercentage = percentage;
                        jmxAgent.updateStatus( broker.getBrokerPool(), percentage );
                    }

                    if( ( type == DocumentImpl.XML_FILE ) && !directAccess ) {
                        // add to the list of pending documents. They will be checked later
                        docs.add(doc);
                    }
                }
            }
            catch( final TerminatedException e ) {
                throw( e );
            }
            catch( final Exception e ) {
                e.printStackTrace();
                final org.exist.backup.ErrorReport.ResourceError error = new org.exist.backup.ErrorReport.ResourceError( org.exist.backup.ErrorReport.RESOURCE_ACCESS_FAILED, e.getMessage(), e );
                error.setDocumentId( docId );

                if( errors != null ) {
                    errors.add( error );
                }

                if( progress != null ) {
                    progress.error( error );
                }
            }
            return( true );
        }

        /**
         * Sort the documents in the pending list by their storage page, then
         * check each of them.
         */
        public void checkDocs() {
            DocumentImpl documents[] = new DocumentImpl[docs.size()];
            docs.toArray(documents);
            Arrays.sort(documents, new Comparator<DocumentImpl>() {
                @Override
                public int compare(DocumentImpl d1, DocumentImpl d2) {
                    final long a1 = StorageAddress.pageFromPointer(d1.getFirstChildAddress());
                    final long a2 = StorageAddress.pageFromPointer(d2.getFirstChildAddress());
                    return Long.compare(a1, a2);
                }
            });
            for (DocumentImpl doc : documents) {
                final ErrorReport report;
                if (ConsistencyCheck.this.checkDocs) {
                    report = checkXMLTree(doc);
                } else {
                    report = checkDocument(doc);
                }
                if( report != null ) {
                    if(report instanceof ErrorReport.ResourceError) {
                        ( (ErrorReport.ResourceError)report ).setDocumentId( doc.getDocId() );
                    }

                    if(errors != null) {
                        errors.add(report);
                    }

                    if(progress != null) {
                        progress.error(report);
                    }
                }
            }
        }
    }
}
