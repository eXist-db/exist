/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *
 *  $Id$
 */
package org.exist.memtree;

import org.exist.xquery.*;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.UserDataHandler;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentAtExist;
import org.exist.dom.DocumentSet;
import org.exist.dom.EmptyNodeSet;
import org.exist.dom.NodeAtExist;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.Receiver;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.MemoryNodeSet;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.UntypedAtomicValue;
import org.exist.xquery.value.ValueSequence;

import java.util.Iterator;
import java.util.Properties;


public abstract class NodeImpl implements NodeAtExist, NodeValue {

    public final static short REFERENCE_NODE = 100;
    public final static short NAMESPACE_NODE = 101;

    protected int             nodeNumber;
    protected DocumentImpl    document;

    public NodeImpl( DocumentImpl doc, int nodeNumber ) {
        this.document   = doc;
        this.nodeNumber = nodeNumber;
    }

    public int getNodeNumber() {
        return( nodeNumber );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NodeValue#getImplementation()
     */
    public int getImplementationType() {
        return( NodeValue.IN_MEMORY_NODE );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getDocumentSet()
     */
    public DocumentSet getDocumentSet() {
        return( DocumentSet.EMPTY_DOCUMENT_SET );
    }

    public Iterator<Collection> getCollectionIterator() {
        return( EmptyNodeSet.EMPTY_COLLECTION_ITERATOR );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NodeValue#getNode()
     */
    public Node getNode() {
        return( this );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getNodeName()
     */
    public String getNodeName() {
        switch( getType() ) {
            case Type.DOCUMENT:
                return( "#document" );
            case Type.ELEMENT:
            case Type.PROCESSING_INSTRUCTION:
                QName qn = document.nodeName[nodeNumber];
                //TODO : check !
                return( qn.getStringValue() );
            case Type.ATTRIBUTE:
                return( ( document.attrName[nodeNumber] ).getStringValue() );
            case Type.NAMESPACE:
                return( ( document.namespaceCode[nodeNumber] ).getStringValue() );
            case Type.TEXT:
                return( "#text" );
            case Type.COMMENT:
                return( "#comment" );
            case Type.CDATA_SECTION:
                return( "#cdata-section" );
            default:
                return( "#unknown" );
        }
    }

    public QName getQName() {
        switch( getNodeType() ) {
            case Node.ATTRIBUTE_NODE:
            case Node.ELEMENT_NODE:
            case Node.PROCESSING_INSTRUCTION_NODE:
                QName qn = document.nodeName[nodeNumber];
                return( qn );
            case Node.DOCUMENT_NODE:
                return( QName.EMPTY_QNAME );
            case Node.COMMENT_NODE:
                return( QName.EMPTY_QNAME );
            case Node.TEXT_NODE:
                return( QName.EMPTY_QNAME );
            case Node.CDATA_SECTION_NODE:
                return( QName.EMPTY_QNAME );
            default:
                return( null );
        }
    }

    public NodeId getNodeId() {
        expand();
        return( document.nodeId[nodeNumber] );
    }

    public void expand() throws DOMException {
        document.expand();
    }

    public void deepCopy() throws DOMException {
        DocumentImpl newDoc = document.expandRefs( this );
        if( newDoc != document ) {
            // we received a new document
            this.nodeNumber = 1;
            this.document   = newDoc;
        }
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getNodeValue()
     */
    public String getNodeValue() throws DOMException {
        throw( new RuntimeException( getClass().getName() + ": can not call getNodeValue() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#setNodeValue(java.lang.String)
     */
    public void setNodeValue( String arg0 ) throws DOMException {
        throw( new RuntimeException( "Can not call setNodeValue() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getNodeType()
     */
    public short getNodeType() {
        //Workaround for fn:string-length(fn:node-name(document {""}))
        if( this.document == null ) {
            return( Node.DOCUMENT_NODE );
        }
        return( document.nodeKind[nodeNumber] );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getParentNode()
     */
    public Node getParentNode() {
        int next = document.next[nodeNumber];
        while( next > nodeNumber ) {
            next = document.next[next];
        }
        if( next < 0 ) {
            return( null );
        }
        return( document.getNode( next ) );
    }

    public Node selectParentNode() {
        // as getParentNode() but doesn't return the document itself
        if( nodeNumber == 0 ) {
            return( null );
        }
        int next = document.next[nodeNumber];
        while( next > nodeNumber ) {
            next = document.next[next];
        }
        if( next < 0 ) { //Is this even possible ?
            return( null );
        }
        if( next == 0 ) {
            return( this.document.explicitCreation ? this.document : null );
        }
        return( document.getNode( next ) );
    }

    public void addContextNode( int contextId, NodeValue node ) {
        throw( new RuntimeException( "Can not call addContextNode() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals( Object obj ) {
        if( !( obj instanceof NodeImpl ) ) {
            return( false );
        }
        NodeImpl o = (NodeImpl)obj;
        return( ( document == o.document ) && ( nodeNumber == o.nodeNumber ) && 
                ( getNodeType() == o.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NodeValue#equals(org.exist.xquery.value.NodeValue)
     */
    public boolean equals( NodeValue other ) throws XPathException {
        if( other.getImplementationType() != NodeValue.IN_MEMORY_NODE ) {
            return( false );
        }
        NodeImpl o = (NodeImpl)other;
        return( ( document == o.document ) && ( nodeNumber == o.nodeNumber ) && 
                ( getNodeType() == o.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NodeValue#after(org.exist.xquery.value.NodeValue)
     */
    public boolean after( NodeValue other, boolean isFollowing ) throws XPathException {
        if( other.getImplementationType() != NodeValue.IN_MEMORY_NODE ) {
            throw( new XPathException( "cannot compare persistent node with in-memory node" ) );
        }
        return( nodeNumber > ( (NodeImpl)other ).nodeNumber );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NodeValue#before(org.exist.xquery.value.NodeValue)
     */
    public boolean before( NodeValue other, boolean isPreceding ) throws XPathException {
        if( other.getImplementationType() != NodeValue.IN_MEMORY_NODE ) {
            throw( new XPathException( "cannot compare persistent node with in-memory node" ) );
        }
        return( nodeNumber < ( (NodeImpl)other ).nodeNumber );
    }

    public int compareTo( Object other ) {
        if( !( other instanceof NodeImpl ) ) {
            return( Constants.INFERIOR );
        }
        NodeImpl n = (NodeImpl)other;
        if( n.document == document ) {
            if( ( nodeNumber == n.nodeNumber ) && ( getNodeType() == n.getNodeType() ) ) {
                return( Constants.EQUAL );
            } else if( nodeNumber < n.nodeNumber ) {
                return( Constants.INFERIOR );
            } else {
                return( Constants.SUPERIOR );
            }
        } else if( document.docId < n.document.docId ) {
            return( Constants.INFERIOR );
        } else {
            return( Constants.SUPERIOR );
        }
    }

    public Sequence tail() throws XPathException {
    	return Sequence.EMPTY_SEQUENCE;
    }
    
    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getChildNodes()
     */
    public NodeList getChildNodes() {
        throw( new RuntimeException( "Can not call getChildNodes() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getFirstChild()
     */
    public Node getFirstChild() {
        throw( new RuntimeException( "Can not call getFirstChild() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getLastChild()
     */
    public Node getLastChild() {
        throw( new RuntimeException( "Can not call getLastChild() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getPreviousSibling()
     */
    public Node getPreviousSibling() {
        if( nodeNumber == 0 ) {
            return( null );
        }
        int parent   = document.getParentNodeFor( nodeNumber );
        int nextNode = document.getFirstChildFor( parent );
        while( ( nextNode >= parent ) && ( nextNode < nodeNumber ) ) {
            int following = document.next[nextNode];
            if( following == nodeNumber ) {
                return( document.getNode( nextNode ) );
            }
            nextNode = following;
        }
        return( null );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getNextSibling()
     */
    public Node getNextSibling() {
        int nextNr = document.next[nodeNumber];
        return( ( nextNr < nodeNumber ) ? null : document.getNode( nextNr ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getAttributes()
     */
    public NamedNodeMap getAttributes() {
        throw( new RuntimeException( "Can not call getAttributes() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getOwnerDocument()
     */
    public Document getOwnerDocument() {
        return( document );
    }

    public DocumentImpl getDocument() {
        return( document );
    }

    public DocumentAtExist getDocumentAtExist() {
        return( document );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#insertBefore(org.w3c.dom.Node, org.w3c.dom.Node)
     */
    public Node insertBefore( Node arg0, Node arg1 ) throws DOMException {
        throw( new RuntimeException( "Can not call insertBefore() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#replaceChild(org.w3c.dom.Node, org.w3c.dom.Node)
     */
    public Node replaceChild( Node arg0, Node arg1 ) throws DOMException {
        throw( new RuntimeException( "Can not call replaceChild() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#removeChild(org.w3c.dom.Node)
     */
    public Node removeChild( Node arg0 ) throws DOMException {
        throw( new RuntimeException( "Can not call removeChild() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#newChild(org.w3c.dom.Node)
     */
    public Node appendChild( Node arg0 ) throws DOMException {
        throw( new RuntimeException( "Can not call appendChild() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#hasChildNodes()
     */
    public boolean hasChildNodes() {
        //throw new RuntimeException("Can not call hasChildNodes() on node type " + this.getNodeType());
        //the default value is
        return( false );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#cloneNode(boolean)
     */
    public Node cloneNode( boolean arg0 ) {
        throw( new RuntimeException( "Can not call cloneNode() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#normalize()
     */
    public void normalize() {
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#isSupported(java.lang.String, java.lang.String)
     */
    public boolean isSupported( String arg0, String arg1 ) {
        throw( new RuntimeException( "Can not call isSupported() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getNamespaceURI()
     */
    public String getNamespaceURI() {
        throw( new RuntimeException( "Can not call getNamespaceURI() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getPrefix()
     */
    public String getPrefix() {
        throw( new RuntimeException( "Can not call getPrefix() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#setPrefix(java.lang.String)
     */
    public void setPrefix( String arg0 ) throws DOMException {
        throw( new RuntimeException( "Can not call setPrefix() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getLocalName()
     */
    public String getLocalName() {
        throw( new RuntimeException( "Can not call getLocalName() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#hasAttributes()
     */
    public boolean hasAttributes() {
        throw( new RuntimeException( "Can not call hasAttributes() on node type " + this.getNodeType() ) );
    }

    /*
     * Methods of interface Item
     */

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#getType()
     */
    public int getType() {
        //Workaround for fn:string-length(fn:node-name(document {""}))
        if( this.document == null ) {
            return( Type.DOCUMENT );
        }
        switch(getNodeType()) {
            case Node.DOCUMENT_NODE: 
                return( Type.DOCUMENT );
            case Node.COMMENT_NODE: 
                return( Type.COMMENT );
            case Node.PROCESSING_INSTRUCTION_NODE: 
                return( Type.PROCESSING_INSTRUCTION );
            case Node.ELEMENT_NODE: 
                return( Type.ELEMENT );
            case Node.ATTRIBUTE_NODE: 
                return( Type.ATTRIBUTE );
            case Node.TEXT_NODE: 
                return( Type.TEXT );
            case Node.CDATA_SECTION_NODE: 
                return( Type.CDATA_SECTION );
            default: 
                return( Type.NODE );
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#getStringValue()
     */
    public String getStringValue() {
        int level       = document.treeLevel[nodeNumber];
        int next        = nodeNumber + 1;
        int startOffset = 0;
        int len         = -1;

        while( ( next < document.size ) && ( document.treeLevel[next] > level ) ) {
            if( 
            		   ( document.nodeKind[next] == Node.TEXT_NODE )
            		|| ( document.nodeKind[next] == Node.CDATA_SECTION_NODE )
            		|| ( document.nodeKind[next] == Node.PROCESSING_INSTRUCTION_NODE )
                ) {
                if( len < 0 ) {
                    startOffset = document.alpha[next];
                    len         = document.alphaLen[next];
                } else {
                    len += document.alphaLen[next];
                }
            } else {
                return( getStringValueSlow() );
            }
            ++next;
        }
        return( ( len < 0 ) ? "" : new String( document.characters, startOffset, len ) );
    }

    private String getStringValueSlow() {
        int           level = document.treeLevel[nodeNumber];
        StringBuilder buf   = null;
        int           next  = nodeNumber + 1;

        while( ( next < document.size ) && ( document.treeLevel[next] > level ) ) {
            switch( document.nodeKind[next] ) {
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
            	case Node.PROCESSING_INSTRUCTION_NODE: {
                    if( buf == null ) {
                        buf = new StringBuilder();
                    }
                    buf.append( document.characters, document.alpha[next], document.alphaLen[next] );
                    break;
                }
                case REFERENCE_NODE: {
                    if( buf == null ) {
                        buf = new StringBuilder();
                    }
                    buf.append( document.references[document.alpha[next]].getStringValue() );
                    break;
                }
            }
            ++next;
        }
        return( ( buf == null ) ? "" : buf.toString() );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#toSequence()
     */
    public Sequence toSequence() {
        return( this );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#convertTo(int)
     */
    public AtomicValue convertTo( int requiredType ) throws XPathException {
        return( UntypedAtomicValue.convertTo( null, getStringValue(), requiredType ) );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#atomize()
     */
    public AtomicValue atomize() throws XPathException {
        return( new UntypedAtomicValue( getStringValue() ) );
    }

    /*
     * Methods of interface Sequence
     */

    public boolean isEmpty() {
        return( false );
    }

    public boolean hasOne() {
        return( true );
    }

    public boolean hasMany() {
        return( false );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#add(org.exist.xquery.value.Item)
     */
    public void add( Item item ) throws XPathException {
        throw( new RuntimeException( "Can not call add() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#addAll(org.exist.xquery.value.Sequence)
     */
    public void addAll( Sequence other ) throws XPathException {
        throw( new RuntimeException( "Can not call addAll() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getItemType()
     */
    public int getItemType() {
        return( Type.NODE );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#iterate()
     */
    public SequenceIterator iterate() throws XPathException {
        return( new SingleNodeIterator( this ) );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#unorderedIterator()
     */
    public SequenceIterator unorderedIterator() {
        return( new SingleNodeIterator( this ) );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getItemCount()
     */
    public int getItemCount() {
        return( 1 );
    }

    public int getLength() {
        //Let the derived classes do it...
        throw( new RuntimeException( "Can not call getLength() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getCardinality()
     */
    public int getCardinality() {
        return( Cardinality.EXACTLY_ONE );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#itemAt(int)
     */
    public Item itemAt( int pos ) {
        return( ( pos == 0 ) ? this : null );
    }


    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#effectiveBooleanValue()
     */
    public boolean effectiveBooleanValue() throws XPathException {
        //A node evaluates to true()
        return( true );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#toNodeSet()
     */
    public NodeSet toNodeSet() throws XPathException {
        ValueSequence seq = new ValueSequence();
        seq.add( this );
        return( seq.toNodeSet() );
    }

    public MemoryNodeSet toMemNodeSet() throws XPathException {
        return( new ValueSequence( this ).toMemNodeSet() );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#toSAX(org.exist.storage.DBBroker, org.xml.sax.ContentHandler)
     */
    public void toSAX( DBBroker broker, ContentHandler handler, Properties properties )
        throws SAXException {
        Serializer serializer = broker.getSerializer();
        serializer.reset();
        serializer.setProperty( Serializer.GENERATE_DOC_EVENTS, "false" );

        if( properties != null ) {
            serializer.setProperties( properties );
        }

        if( handler instanceof LexicalHandler ) {
            serializer.setSAXHandlers( handler, (LexicalHandler)handler );
        } else {
            serializer.setSAXHandlers( handler, null );
        }

        serializer.toSAX( this );
    }


    public void copyTo( DBBroker broker, DocumentBuilderReceiver receiver ) throws SAXException {
        //Null test for document nodes
        if( document != null ) {
            document.copyTo( this, receiver );
        }
    }

    public void streamTo( Serializer serializer, Receiver receiver ) throws SAXException {
        //Null test for document nodes
        if( document != null ) {
            document.streamTo( serializer, this, receiver );
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
     */
    public int conversionPreference( Class<?> javaClass ) {
        if( javaClass.isAssignableFrom( NodeImpl.class ) ) {
            return( 0 );
        }
        if( javaClass.isAssignableFrom( Node.class ) ) {
            return( 1 );
        }
        if( ( javaClass == String.class ) || ( javaClass == CharSequence.class ) ) {
            return( 2 );
        }
        if( ( javaClass == Character.class ) || ( javaClass == char.class ) ) {
            return( 2 );
        }
        if( ( javaClass == Double.class ) || ( javaClass == double.class ) ) {
            return( 10 );
        }
        if( ( javaClass == Float.class ) || ( javaClass == float.class ) ) {
            return( 11 );
        }
        if( ( javaClass == Long.class ) || ( javaClass == long.class ) ) {
            return( 12 );
        }
        if( ( javaClass == Integer.class ) || ( javaClass == int.class ) ) {
            return( 13 );
        }
        if( ( javaClass == Short.class ) || ( javaClass == short.class ) ) {
            return( 14 );
        }
        if( ( javaClass == Byte.class ) || ( javaClass == byte.class ) ) {
            return( 15 );
        }
        if( ( javaClass == Boolean.class ) || ( javaClass == boolean.class ) ) {
            return( 16 );
        }
        if( javaClass == Object.class ) {
            return( 20 );
        }
        return( Integer.MAX_VALUE );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
     */
    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if(target.isAssignableFrom(NodeImpl.class)) {
            return (T)this;
        } else if(target.isAssignableFrom(Node.class)) {
            return (T)this;
        } else if(target == Object.class) {
            return (T)this;
        } else {
            final StringValue v = new StringValue( getStringValue() );
            return v.toJavaObject(target);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#setSelfAsContext(int)
     */
    public void setSelfAsContext( int contextId ) {
        throw( new RuntimeException( "Can not call setSelfAsContext() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#isCached()
     */
    public boolean isCached() {
        // always return false
        return( false );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#setIsCached(boolean)
     */
    public void setIsCached( boolean cached ) {
        // ignore
        throw( new RuntimeException( "Can not call setIsCached() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#removeDuplicates()
     */
    public void removeDuplicates() {
        // do nothing: this is a single node
    }

    public String getBaseURI() {
        return( null );
    }

    public void destroy(XQueryContext context, Sequence contextSequence) {
        // nothing to do
    }

    protected XmldbURI calculateBaseURI() {
    	return null;
    }

    public abstract void selectAttributes( NodeTest test, Sequence result ) throws XPathException;
    
    public abstract void selectDescendantAttributes( NodeTest test, Sequence result ) throws XPathException;

    public abstract void selectChildren( NodeTest test, Sequence result ) throws XPathException;

    public void selectDescendants( boolean includeSelf, NodeTest test, Sequence result ) 
            throws XPathException {
        if( includeSelf && test.matches( this ) ) {
            result.add( this );
        }
    }

    public void selectAncestors( boolean includeSelf, NodeTest test, Sequence result ) 
            throws XPathException {
        if( nodeNumber < 1 ) {
            return;
        }
        if( includeSelf ) {
            NodeImpl n = document.getNode( nodeNumber );
            if( test.matches( n ) ) {
                result.add( n );
            }
        }
        int nextNode = document.getParentNodeFor( nodeNumber );
        while( nextNode > 0 ) {
            NodeImpl n = document.getNode( nextNode );
            if( test.matches( n ) ) {
                result.add( n );
            }
            nextNode = document.getParentNodeFor( nextNode );
        }
    }

    public void selectPrecedingSiblings( NodeTest test, Sequence result )
            throws XPathException {
        int parent   = document.getParentNodeFor( nodeNumber );
        int nextNode = document.getFirstChildFor( parent );
        while( ( nextNode >= parent ) && ( nextNode < nodeNumber ) ) {
            NodeImpl n = document.getNode( nextNode );
            if( test.matches( n ) ) {
                result.add( n );
            }
            nextNode = document.next[nextNode];
        }
    }

    public void selectPreceding( NodeTest test, Sequence result, int position ) 
            throws XPathException {
        NodeId myNodeId = getNodeId();
        int    count    = 0;

        for( int i = nodeNumber - 1; i > 0; i-- ) {
            NodeImpl n = document.getNode( i );
            if( !myNodeId.isDescendantOf( n.getNodeId() ) && test.matches( n ) ) {
                if( ( position < 0 ) || ( ++count == position ) ) {
                    result.add( n );
                }
                if( count == position ) {
                    break;
                }
            }
        }
    }

    public void selectFollowingSiblings( NodeTest test, Sequence result ) 
            throws XPathException {
        int parent = document.getParentNodeFor( nodeNumber );
        if( parent == 0 ) {
            // parent is the document node
            if( getNodeType() == Node.ELEMENT_NODE ) {
                return;
            }
            NodeImpl next = (NodeImpl)getNextSibling();
            while( next != null ) {
                if( test.matches( next ) ) {
                    result.add( next );
                }
                if( next.getNodeType() == Node.ELEMENT_NODE ) {
                    break;
                }
                next = (NodeImpl)next.getNextSibling();
            }
        } else {
            int nextNode = document.getFirstChildFor( parent );
            while( nextNode > parent ) {
                NodeImpl n = document.getNode( nextNode );
                if( ( nextNode > nodeNumber ) && test.matches( n ) ) {
                    result.add( n );
                }
                nextNode = document.next[nextNode];
            }
        }
    }

    public void selectFollowing( NodeTest test, Sequence result, int position )
            throws XPathException {
        int parent = document.getParentNodeFor( nodeNumber );
        if( parent == 0 ) {
            // parent is the document node
            if( getNodeType() == Node.ELEMENT_NODE ) {
                return;
            }
            NodeImpl next = (NodeImpl)getNextSibling();
            while( next != null ) {
                if( test.matches( next ) ) {
                    next.selectDescendants( true, test, result );
                }
                if( next.getNodeType() == Node.ELEMENT_NODE ) {
                    break;
                }
                next = (NodeImpl)next.getNextSibling();
            }
        } else {
            NodeId myNodeId = getNodeId();
            int    count    = 0;
            int    nextNode = nodeNumber + 1;
            while( nextNode < document.size ) {
                NodeImpl n = document.getNode( nextNode );
                if( !n.getNodeId().isDescendantOf( myNodeId ) && test.matches( n ) ) {
                    if( ( position < 0 ) || ( ++count == position ) ) {
                        result.add( n );
                    }
                    if( count == position ) {
                        break;
                    }
                }
                nextNode++;
            }
        }
    }

    public boolean matchAttributes( NodeTest test ) {
        // do nothing
        return( false );
        //TODO : make abstract
    }

    public boolean matchDescendantAttributes( NodeTest test ) throws XPathException {
        // do nothing
        return( false );
        //TODO : make abstract
    }

    public boolean matchChildren( NodeTest test ) throws XPathException {
        // do nothing
        return( false );
        //TODO : make abstract
    }

    public boolean matchDescendants( boolean includeSelf, NodeTest test ) throws XPathException {
        return( includeSelf && test.matches( this ) );
    }

    public boolean matchAncestors( boolean includeSelf, NodeTest test ) {
        if( nodeNumber < 2 ) {
            return( false );
        }
        if( includeSelf ) {
            NodeImpl n = document.getNode( nodeNumber );
            if( test.matches( n ) ) {
                return( true );
            }
        }
        int nextNode = document.getParentNodeFor( nodeNumber );
        while( nextNode > 0 ) {
            NodeImpl n = document.getNode( nextNode );
            if( test.matches( n ) ) {
                return( true );
            }
            nextNode = document.getParentNodeFor( nextNode );
        }
        return( false );
    }

    public boolean matchPrecedingSiblings( NodeTest test ) {
        int parent   = document.getParentNodeFor( nodeNumber );
        int nextNode = document.getFirstChildFor( parent );
        while( ( nextNode >= parent ) && ( nextNode < nodeNumber ) ) {
            NodeImpl n = document.getNode( nextNode );
            if( test.matches( n ) ) {
                return( true );
            }
            nextNode = document.next[nextNode];
        }
        return( false );
    }

    public boolean matchPreceding( NodeTest test, int position ) 
            throws EXistException {
        NodeId myNodeId = getNodeId();
        int    count    = 0;
        for( int i = nodeNumber - 1; i > 0; i-- ) {
            NodeImpl n = document.getNode( i );
            if( !myNodeId.isDescendantOf( n.getNodeId() ) && test.matches( n ) ) {
                if( ( position < 0 ) || ( ++count == position ) ) {
                    return( true );
                }
                if( count == position ) {
                    break;
                }
            }
        }
        return( false );
    }

    public boolean matchFollowingSiblings( NodeTest test ) {
        int parent = document.getParentNodeFor( nodeNumber );
        if( parent == 0 ) {
            // parent is the document node
            if( getNodeType() == Node.ELEMENT_NODE ) {
                return( false );
            }
            NodeImpl next = (NodeImpl)getNextSibling();
            while( next != null ) {
                if( test.matches( next ) ) {
                    return( true );
                }
                if( next.getNodeType() == Node.ELEMENT_NODE ) {
                    break;
                }
                next = (NodeImpl)next.getNextSibling();
            }
        } else {
            int nextNode = document.getFirstChildFor( parent );
            while( nextNode > parent ) {
                NodeImpl n = document.getNode( nextNode );
                if( ( nextNode > nodeNumber ) && test.matches( n ) ) {
                    return( true );
                }
                nextNode = document.next[nextNode];
            }
        }
        return( false );
    }

    public boolean matchFollowing( NodeTest test, int position ) 
            throws XPathException, EXistException {
        int parent = document.getParentNodeFor( nodeNumber );
        if( parent == 0 ) {
            // parent is the document node
            if( getNodeType() == Node.ELEMENT_NODE ) {
                return( false );
            }
            NodeImpl next = (NodeImpl)getNextSibling();
            while( next != null ) {
                if( test.matches( next ) ) {
                    if( next.matchDescendants( true, test ) ) {
                        return( true );
                    }
                }
                if( next.getNodeType() == Node.ELEMENT_NODE ) {
                    break;
                }
                next = (NodeImpl)next.getNextSibling();
            }
        } else {
            NodeId myNodeId = getNodeId();
            int    count    = 0;
            int    nextNode = nodeNumber + 1;
            while( nextNode < document.size ) {
                NodeImpl n = document.getNode( nextNode );
                if( !n.getNodeId().isDescendantOf( myNodeId ) && test.matches( n ) ) {
                    if( ( position < 0 ) || ( ++count == position ) ) {
                        return( true );
                    }
                    if( count == position ) {
                        break;
                    }
                }
                nextNode++;
            }
        }
        return( false );
    }

    /**
     * ? @see org.w3c.dom.Node#compareDocumentPosition(org.w3c.dom.Node)
     *
     * @param   other  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  DOMException  DOCUMENT ME!
     */
    public short compareDocumentPosition( Node other ) throws DOMException {
        throw( new RuntimeException( "Can not call compareDocumentPosition() on node type " + this.getNodeType() ) );
    }

    /**
     * ? @see org.w3c.dom.Node#getTextContent()
     *
     * @return  DOCUMENT ME!
     *
     * @throws  DOMException  DOCUMENT ME!
     */
    public String getTextContent() throws DOMException {
        throw( new RuntimeException( "Can not call getTextContent() on node type " + this.getNodeType() ) );
    }

    /**
     * ? @see org.w3c.dom.Node#setTextContent(java.lang.String)
     *
     * @param   textContent  DOCUMENT ME!
     *
     * @throws  DOMException  DOCUMENT ME!
     */
    public void setTextContent( String textContent ) throws DOMException {
        throw( new RuntimeException( "Can not call setTextContent() on node type " + this.getNodeType() ) );
    }

    /**
     * ? @see org.w3c.dom.Node#isSameNode(org.w3c.dom.Node)
     *
     * @param   other  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public boolean isSameNode( Node other ) {
        throw( new RuntimeException( "Can not call isSameNode() on node type " + this.getNodeType() ) );
    }

    /**
     * ? @see org.w3c.dom.Node#lookupPrefix(java.lang.String)
     *
     * @param   namespaceURI  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public String lookupPrefix( String namespaceURI ) {
        throw( new RuntimeException( "Can not call lookupPrefix() on node type " + this.getNodeType() ) );
    }

    /**
     * ? @see org.w3c.dom.Node#isDefaultNamespace(java.lang.String)
     *
     * @param   namespaceURI  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public boolean isDefaultNamespace( String namespaceURI ) {
        throw( new RuntimeException( "Can not call isDefaultNamespace() on node type " + this.getNodeType() ) );
    }

    /**
     * ? @see org.w3c.dom.Node#lookupNamespaceURI(java.lang.String)
     *
     * @param   prefix  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public String lookupNamespaceURI( String prefix ) {
        throw( new RuntimeException( "Can not call lookupNamespaceURI() on node type " + this.getNodeType() ) );
    }

    /**
     * ? @see org.w3c.dom.Node#isEqualNode(org.w3c.dom.Node)
     *
     * @param   arg  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public boolean isEqualNode( Node arg ) {
        throw( new RuntimeException( "Can not call isEqualNode() on node type " + this.getNodeType() ) );
    }

    /**
     * ? @see org.w3c.dom.Node#getFeature(java.lang.String, java.lang.String)
     *
     * @param   feature  DOCUMENT ME!
     * @param   version  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public Object getFeature( String feature, String version ) {
        throw( new RuntimeException( "Can not call getFeature() on node type " + this.getNodeType() ) );
    }

    /**
     * ? @see org.w3c.dom.Node#setUserData(java.lang.String, java.lang.Object, org.w3c.dom.UserDataHandler)
     *
     * @param   key      DOCUMENT ME!
     * @param   data     DOCUMENT ME!
     * @param   handler  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public Object setUserData( String key, Object data, UserDataHandler handler ) {
        throw( new RuntimeException( "Can not call setUserData() on node type " + this.getNodeType() ) );
    }

    /**
     * ? @see org.w3c.dom.Node#getUserData(java.lang.String)
     *
     * @param   key  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public Object getUserData( String key ) {
        throw( new RuntimeException( "Can not call getUserData() on node type " + this.getNodeType() ) );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#isPersistentSet()
     */
    public boolean isPersistentSet() {
        //See package's name ;-)
        return( false );
    }

    public void nodeMoved( NodeId oldNodeId, StoredNode newNode ) {
        // can not be applied to in-memory nodes
    }

    public void clearContext( int contextId ) {
        //Nothing to do
    }

    public int getState() {
        return( 0 );
    }

    public boolean isCacheable() {
        return( true );
    }

    public boolean hasChanged( int previousState ) {
        return( false ); // will never change
    }

    private final static class SingleNodeIterator implements SequenceIterator {
        NodeImpl node;
        
        public SingleNodeIterator( NodeImpl node ) {
            this.node = node;
        }

        /* (non-Javadoc)
         * @see org.exist.xquery.value.SequenceIterator#hasNext()
         */
        public boolean hasNext() {
            return( node != null );
        }

        /* (non-Javadoc)
         * @see org.exist.xquery.value.SequenceIterator#nextItem()
         */
        public Item nextItem() {
            NodeImpl next = node;
            node = null;
            return( next );
        }

    }
}
