/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.memtree;

import org.apache.log4j.Logger;

import org.w3c.dom.DOMException;
import org.w3c.dom.Node;

import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.collections.CollectionConfiguration;
import org.exist.dom.*;
import org.exist.dom.CommentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.ProcessingInstructionImpl;
import org.exist.dom.TextImpl;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;
import org.exist.util.pool.NodePool;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Helper class to make a in-memory document fragment persistent. The class directly accesses the in-memory document structure and writes it into a
 * temporary doc on the database. This is much faster than first serializing the document tree to SAX and passing it to {@link
 * org.exist.collections.Collection#store(org.exist.storage.txn.Txn, org.exist.storage.DBBroker, org.exist.collections.IndexInfo,
 * org.xml.sax.InputSource, boolean)}.
 *
 * <p>As the in-memory document fragment may not be a well-formed XML doc (having more than one root element), a wrapper element is put around the
 * content nodes.</p>
 *
 * @author  wolf
 */
public class DOMIndexer {
    private static final Logger        LOG         = Logger.getLogger(DOMIndexer.class);

    public final static QName          ROOT_QNAME  = new QName("temp", Namespaces.EXIST_NS, "exist");

    private DBBroker                   broker;
    private Txn                        transaction;
    private DocumentImpl               doc;
    private org.exist.dom.DocumentImpl targetDoc;
    private IndexSpec                  indexSpec   = null;
    private Stack<ElementImpl>         stack       = new Stack<ElementImpl>();
    private TextImpl                   text        = new TextImpl();
    private StoredNode                 prevNode    = null;

    private CommentImpl                comment     = new CommentImpl();
    private ProcessingInstructionImpl  pi          = new ProcessingInstructionImpl();

    public DOMIndexer(DBBroker broker, Txn transaction, DocumentImpl doc,
            org.exist.dom.DocumentImpl targetDoc) {
        this.broker      = broker;
        this.transaction = transaction;
        this.doc         = doc;
        this.targetDoc   = targetDoc;
        final CollectionConfiguration config = targetDoc.getCollection().getConfiguration(broker);
        if (config != null) {
            this.indexSpec = config.getIndexConfiguration();
        }
    }

    /**
     * Scan the DOM tree once to determine its structure.
     *
     * @throws  EXistException
     */
    public void scan() throws EXistException {
        //Creates a dummy DOCTYPE
        final DocumentTypeImpl dt = new DocumentTypeImpl("temp", null, "");
        targetDoc.setDocumentType(dt);
    }

    /**
     * Store the nodes.
     */
    public void store() {
        //Create a wrapper element as root node
        final ElementImpl elem = new ElementImpl(ROOT_QNAME, broker.getBrokerPool().getSymbols());
        elem.setNodeId(broker.getBrokerPool().getNodeFactory().createInstance());
        elem.setOwnerDocument(targetDoc);
        elem.setChildCount(doc.getChildCount());
        elem.addNamespaceMapping("exist", Namespaces.EXIST_NS);
        final NodePath path = new NodePath();
        path.addComponent(ROOT_QNAME);
        stack.push(elem);
        broker.storeNode(transaction, elem, path, indexSpec);
        targetDoc.appendChild(elem);
        elem.setChildCount(0);
        // store the document nodes
        int top = (doc.size > 1) ? 1 : -1;
        while (top > 0) {
            store(top, path);
            top = doc.getNextSiblingFor(top);
        }
        //Close the wrapper element
        stack.pop();
        broker.endElement(elem, path, null);
        path.removeLastComponent();
    }

    private void store( int top, NodePath currentPath )
    {
        int nodeNr = top;

        while( nodeNr > 0 ) {
            startNode( nodeNr, currentPath );
            int nextNode = doc.getFirstChildFor( nodeNr );

            while( nextNode == -1 ) {
                endNode( nodeNr, currentPath );

                if( top == nodeNr ) {
                    break;
                }
                nextNode = doc.getNextSiblingFor( nodeNr );

                if( nextNode == -1 ) {
                    nodeNr = doc.getParentNodeFor( nodeNr );

                    if( ( nodeNr == -1 ) || ( top == nodeNr ) ) {
                        endNode( nodeNr, currentPath );
                        nextNode = -1;
                        break;
                    }
                }
            }
            nodeNr = nextNode;
        }
    }


    /**
     * DOCUMENT ME!
     *
     * @param  nodeNr
     * @param  currentPath  DOCUMENT ME!
     */
    private void startNode( int nodeNr, NodePath currentPath )
    {
        ElementImpl last;

        switch( doc.nodeKind[nodeNr] ) {

            case Node.ELEMENT_NODE: {
                final ElementImpl elem = (ElementImpl)NodePool.getInstance().borrowNode( Node.ELEMENT_NODE );
                if( stack.empty() ) {
                    elem.setNodeId( broker.getBrokerPool().getNodeFactory().createInstance() );
                    initElement( nodeNr, elem );
                    stack.push( elem );
                    broker.storeNode( transaction, elem, currentPath, indexSpec );
                    targetDoc.appendChild( elem );
                    elem.setChildCount( 0 );
                } else {
                    last = stack.peek();
                    initElement( nodeNr, elem );
                    last.appendChildInternal( prevNode, elem );
                    stack.push( elem );
                    broker.storeNode( transaction, elem, currentPath, indexSpec );
                    elem.setChildCount( 0 );
                }
                setPrevious( null );
                currentPath.addComponent( elem.getQName() );
                storeAttributes( nodeNr, elem, currentPath );
                break;
            }

            case Node.TEXT_NODE: {
                if( ( prevNode != null ) && ( ( prevNode.getNodeType() == Node.TEXT_NODE ) || ( prevNode.getNodeType() == Node.CDATA_SECTION_NODE ) ) ) {
                    break;
                }
                last = stack.peek();
                text.setData( new String( doc.characters, doc.alpha[nodeNr], doc.alphaLen[nodeNr] ) );
                text.setOwnerDocument( targetDoc );
                last.appendChildInternal( prevNode, text );
                setPrevious( text );
                broker.storeNode( transaction, text, null, indexSpec );
                break;
            }

            case Node.CDATA_SECTION_NODE: {
                last = stack.peek();
                final org.exist.dom.CDATASectionImpl cdata = (org.exist.dom.CDATASectionImpl)NodePool.getInstance().borrowNode( Node.CDATA_SECTION_NODE );
                cdata.setData( doc.characters, doc.alpha[nodeNr], doc.alphaLen[nodeNr] );
                cdata.setOwnerDocument( targetDoc );
                last.appendChildInternal( prevNode, cdata );
                setPrevious( cdata );
                broker.storeNode( transaction, cdata, null, indexSpec );
                break;
            }

            case Node.COMMENT_NODE: {
                comment.setData( doc.characters, doc.alpha[nodeNr], doc.alphaLen[nodeNr] );
                comment.setOwnerDocument( targetDoc );
                if( stack.empty() ) {
                    comment.setNodeId( NodeId.DOCUMENT_NODE );
                    targetDoc.appendChild( comment );
                    broker.storeNode( transaction, comment, null, indexSpec );
                } else {
                    last = stack.peek();
                    last.appendChildInternal( prevNode, comment );
                    broker.storeNode( transaction, comment, null, indexSpec );
                    setPrevious( comment );
                }
                break;
            }

            case Node.PROCESSING_INSTRUCTION_NODE: {
                final QName qn = doc.nodeName[nodeNr];
                pi.setTarget( qn.getLocalName() );
                pi.setData( new String( doc.characters, doc.alpha[nodeNr], doc.alphaLen[nodeNr] ) );
                pi.setOwnerDocument( targetDoc );
                if( stack.empty() ) {
                    pi.setNodeId( NodeId.DOCUMENT_NODE );
                    targetDoc.appendChild( pi );
                } else {
                    last = stack.peek();
                    last.appendChildInternal( prevNode, pi );
                    setPrevious( pi );
                }
                broker.storeNode( transaction, pi, null, indexSpec );
                break;
            }

            default: {
                LOG.debug( "Skipped indexing of in-memory node of type " + doc.nodeKind[nodeNr] );
            }
        }
    }


    /**
     * DOCUMENT ME!
     *
     * @param  nodeNr
     * @param  elem
     */
    private void initElement( int nodeNr, ElementImpl elem )
    {
        final short attribs = (short)doc.getAttributesCountFor( nodeNr );
        elem.setOwnerDocument( targetDoc );
        elem.setAttributes( attribs );
        elem.setChildCount( doc.getChildCountFor( nodeNr ) + attribs );
        elem.setNodeName( doc.nodeName[nodeNr], broker.getBrokerPool().getSymbols() );
        final Map<String, String> ns = getNamespaces( nodeNr );

        if( ns != null ) {
            elem.setNamespaceMappings( ns );
        }
    }


    private Map<String, String> getNamespaces( int nodeNr )
    {
        int ns = doc.alphaLen[nodeNr];

        if( ns < 0 ) {
            return( null );
        }
        final Map<String, String> map = new HashMap<String, String>();

        while( ( ns < doc.nextNamespace ) && ( doc.namespaceParent[ns] == nodeNr ) ) {
            final QName qn = doc.namespaceCode[ns];

            if( "xmlns".equals( qn.getLocalName() ) ) {
                map.put( "", qn.getNamespaceURI() );
            } else {
                map.put( qn.getLocalName(), qn.getNamespaceURI() );
            }
            ++ns;
        }
        return( map );
    }


    /**
     * DOCUMENT ME!
     *
     * @param   nodeNr
     * @param   elem
     * @param   path    DOCUMENT ME!
     *
     * @throws  DOMException
     */
    private void storeAttributes( int nodeNr, ElementImpl elem, NodePath path ) throws DOMException
    {
        int attr = doc.alpha[nodeNr];

        if( -1 < attr ) {

            while( ( attr < doc.nextAttr ) && ( doc.attrParent[attr] == nodeNr ) ) {
                final QName    qn     = doc.attrName[attr];
                final AttrImpl attrib = (AttrImpl)NodePool.getInstance().borrowNode( Node.ATTRIBUTE_NODE );
                attrib.setNodeName( qn, broker.getBrokerPool().getSymbols() );
                attrib.setValue( doc.attrValue[attr] );
                attrib.setOwnerDocument( targetDoc );
                elem.appendChildInternal( prevNode, attrib );
                setPrevious( attrib );
                broker.storeNode( transaction, attrib, path, indexSpec );
                ++attr;
            }
        }
    }


    /**
     * DOCUMENT ME!
     *
     * @param  nodeNr
     * @param  currentPath  DOCUMENT ME!
     */
    private void endNode( int nodeNr, NodePath currentPath )
    {
        if( doc.nodeKind[nodeNr] == Node.ELEMENT_NODE ) {
            final ElementImpl last = stack.pop();
            broker.endElement( last, currentPath, null );
            currentPath.removeLastComponent();
            setPrevious( last );
        }
    }


    private void setPrevious( StoredNode previous )
    {
        if( prevNode != null ) {

            if( ( prevNode.getNodeType() == Node.TEXT_NODE ) || ( prevNode.getNodeType() == Node.COMMENT_NODE ) || ( prevNode.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE ) ) {

                if( ( previous == null ) || ( prevNode.getNodeType() != previous.getNodeType() ) ) {
                    prevNode.clear();
                }
            }
        }
        prevNode = previous;
    }
}
