
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id:
 */
package org.exist.dom;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Document;
import org.w3c.dom.DOMException;
import org.apache.log4j.Category;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import java.util.ArrayList;
import org.exist.storage.*;

/**
 * NodeImpl.java
 * 
 * @author Wolfgang Meier
 */
/**
 *  The base class for all DOM objects.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    8. Juli 2002
 */
public class NodeImpl implements Node {
    private final static Category LOG = Category.getInstance( NodeImpl.class.getName() );
    
    protected short attributes = 0;
    protected long gid;
    protected long internalAddress = -1;
    protected String nodeName = null;
    protected int nodeNameRef = -1;
    protected short nodeType = 0;
    protected DocumentImpl ownerDocument = null;


    /**  Constructor for the NodeImpl object */
    public NodeImpl() { }


    /**
     *  Constructor for the NodeImpl object
     *
     *@param  nodeType  Description of the Parameter
     */
    public NodeImpl( short nodeType ) {
        this( nodeType, "", 0 );
    }


    /**
     *  Constructor for the NodeImpl object
     *
     *@param  n  Description of the Parameter
     */
    public NodeImpl( Node n ) {
        this( n.getNodeType(), n.getNodeName(), 0 );
        ownerDocument = (DocumentImpl) n.getOwnerDocument();
    }


    /**
     *  Constructor for the NodeImpl object
     *
     *@param  gid  Description of the Parameter
     */
    public NodeImpl( long gid ) {
        this( (short) 0, "", gid );
    }


    /**
     *  Constructor for the NodeImpl object
     *
     *@param  nodeType  Description of the Parameter
     *@param  gid       Description of the Parameter
     */
    public NodeImpl( short nodeType, long gid ) {
        this( nodeType, "", gid );
    }


    /**
     *  Constructor for the NodeImpl object
     *
     *@param  nodeType  Description of the Parameter
     *@param  nodeName  Description of the Parameter
     */
    public NodeImpl( short nodeType, String nodeName ) {
        this( nodeType, nodeName, 0 );
    }


    /**
     *  Constructor for the NodeImpl object
     *
     *@param  nodeType  Description of the Parameter
     *@param  nodeName  Description of the Parameter
     *@param  gid       Description of the Parameter
     */
    public NodeImpl( short nodeType, String nodeName, long gid ) {
        this.nodeType = nodeType;
        this.nodeName = nodeName;
        this.gid = gid;
    }


    /**
     *  Description of the Method
     *
     *@param  data  Description of the Parameter
     *@param  doc   Description of the Parameter
     *@return       Description of the Return Value
     */
    public static NodeImpl deserialize( byte[] data, DocumentImpl doc ) {
        //short type = (short)data[0];
        short type = Signatures.getType( data[0] );
        switch ( type ) {
            case Node.TEXT_NODE:
                return TextImpl.deserialize( data );
            case Node.ELEMENT_NODE:
                return ElementImpl.deserialize( data, doc );
            case Node.ATTRIBUTE_NODE:
                return AttrImpl.deserialize( data, doc );
            case Node.PROCESSING_INSTRUCTION_NODE:
                return ProcessingInstructionImpl.deserialize( data );
            case Node.COMMENT_NODE:
                return CommentImpl.deserialize( data );
            default:
                LOG.debug( "not implemented" );
                return null;
        }
    }

    /**
     * Reset this object to its initial state. Required by the
     * parser to be able to reuse node objects.
     */
    public void clear() {
        attributes = 0;
        gid = 0;
        internalAddress = 0;
        nodeName = null;
        ownerDocument = null;
    }
    
    /**
	 * @see org.w3c.dom.Node#appendChild(org.w3c.dom.Node)
	 */
	public Node appendChild( Node child ) throws DOMException {
        return null;
    }

    /**
	 * @see org.w3c.dom.Node#cloneNode(boolean)
	 */
	public Node cloneNode( boolean deep ) {
        return this;
    }

    /**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals( Object obj ) {
        if ( !( obj instanceof NodeImpl ) )
            return false;
        if ( ( (NodeImpl) obj ).gid == gid )
            return true;
        return false;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public long firstChildID() {
        return 0;
    }

    /**
	 * @see org.w3c.dom.Node#getAttributes()
	 */
	public NamedNodeMap getAttributes() {
        return null;
    }

    /**
	 * Method getAttributesCount.
	 * @return short
	 */
	public short getAttributesCount() {
        return attributes;
    }


    /**
     *  Gets the broker attribute of the NodeImpl object
     *
     *@return    The broker value
     */
    public DBBroker getBroker() {
        return (DBBroker) ownerDocument.broker;
    }

    /**
	 * Method getChildCount.
	 * @return int
	 */
	public int getChildCount() {
        return 0;
    }


    /**
     *  Gets the childNodes attribute of the NodeImpl object
     *
     *@return    The childNodes value
     */
    public NodeList getChildNodes() {
        return (NodeList) new NodeListImpl();
    }


    /**
	 * @see org.w3c.dom.Node#getFirstChild()
	 */
	public Node getFirstChild() {
        return null;
    }


    /**
     *  Get the unique identifier assigned to this node
     *
     *@return    The gID value
     */
    public long getGID() {
        return gid;
    }


    /**
     *  Get the internal storage address of this node
     *
     *@return    The internalAddress value
     */
    public long getInternalAddress() {
        return internalAddress;
    }

    /**
	 * @see org.w3c.dom.Node#getLastChild()
	 */
	public Node getLastChild() {
        return null;
    }

    /**
	 * @see org.w3c.dom.Node#getLocalName()
	 */
	public String getLocalName() {
        if ( nodeName != null && nodeName.indexOf( ':' ) > -1 )
            return nodeName.substring( nodeName.indexOf( ':' ) + 1 );
        return nodeName;
    }

    /**
	 * @see org.w3c.dom.Node#getNamespaceURI()
	 */
	public String getNamespaceURI() {
        if ( nodeName != null && nodeName.indexOf( ':' ) > -1 ) {
            String prefix = nodeName.substring( 0, nodeName.indexOf( ':' ) );
            if ( !prefix.equals( "xml" ) ) {
                return ownerDocument.broker.getNamespaceURI( prefix );
            }
        }
        return "";
    }

    /**
	 * @see org.w3c.dom.Node#getNextSibling()
	 */
	public Node getNextSibling() {
        NodeImpl parent = (NodeImpl) getParentNode();
        if ( gid < parent.lastChildID() )
            return ownerDocument.getNode( gid + 1 );
        return null;
    }
    
    /**
	 * @see org.w3c.dom.Node#getNodeName()
	 */
	public String getNodeName() {
        return nodeName;
    }

    /**
	 * @see org.w3c.dom.Node#getNodeType()
	 */
	public short getNodeType() {
        return nodeType;
    }

    /**
	 * @see org.w3c.dom.Node#getNodeValue()
	 */
	public String getNodeValue() throws DOMException {
        return "";
    }

    /**
	 * @see org.w3c.dom.Node#getOwnerDocument()
	 */
	public Document getOwnerDocument() {
        return ownerDocument;
    }

    /**
     *  Get the unique node identifier of this node's parent node.
     *
     *@return    The parentGID value
     */
    public long getParentGID() {
        int level = ownerDocument.getTreeLevel( gid );
        return ( gid - ownerDocument.getLevelStartPoint( level ) ) / ownerDocument.getTreeLevelOrder( level )
             + ownerDocument.getLevelStartPoint( level - 1 );
        //return (gid - 2) / ownerDocument.getOrder() + 1;
    }


    /**
	 * @see org.w3c.dom.Node#getParentNode()
	 */
	public Node getParentNode() {
        if ( gid < 2 )
            return ownerDocument;
        long pid = getParentGID();
        return ownerDocument.getNode( pid );
    }

    /**
	 * @see org.w3c.dom.Node#getPrefix()
	 */
	public String getPrefix() {
        if ( nodeName != null && nodeName.indexOf( ':' ) > -1 )
            return nodeName.substring( 0, nodeName.indexOf( ':' ) );
        return "";
    }


    /**
	 * @see org.w3c.dom.Node#getPreviousSibling()
	 */
	public Node getPreviousSibling() {
        int level = ownerDocument.getTreeLevel( gid );
        long pid = ( gid - ownerDocument.getLevelStartPoint( level ) ) / ownerDocument.getTreeLevelOrder( level )
             + ownerDocument.getLevelStartPoint( level - 1 );
        long firstChildId = ( pid - ownerDocument.getLevelStartPoint( level - 1 ) ) *
            ownerDocument.getTreeLevelOrder( level ) +
            ownerDocument.getLevelStartPoint( level );
        if ( gid > firstChildId )
            return ownerDocument.getNode( gid - 1 );
        return null;
    }
     


    /**
	 * @see org.w3c.dom.Node#hasAttributes()
	 */
	public boolean hasAttributes() {
        return false;
    }

    /**
	 * @see org.w3c.dom.Node#hasChildNodes()
	 */
	public boolean hasChildNodes() {
        return false;
    }


    /**
	 * @see org.w3c.dom.Node#insertBefore(org.w3c.dom.Node, org.w3c.dom.Node)
	 */
    public Node insertBefore( Node newChild, Node refChild )
         throws DOMException {
        return null;
    }

    public Node insertAfter( Node newChild, Node refChild )
        throws DOMException {
        return null;
    }

    /**
	 * @see org.w3c.dom.Node#isSupported(java.lang.String, java.lang.String)
	 */
	public boolean isSupported( String key, String value ) {
        return false;
    }


    /**
     *  Get the unique node identifier of the last child of this node.
     *
     *@return    Description of the Return Value
     */
    public long lastChildID() {
        return 0;
    }

    /**
	 * @see org.w3c.dom.Node#normalize()
	 */
	public void normalize() {
        return;
    }


    
    /**
	 * @see org.w3c.dom.Node#removeChild(org.w3c.dom.Node)
	 */
	public Node removeChild( Node node ) throws DOMException {
        return null;
    }


    /**
	 * @see org.w3c.dom.Node#replaceChild(org.w3c.dom.Node, org.w3c.dom.Node)
	 */
    public Node replaceChild( Node newChild, Node oldChild ) throws DOMException {
        return null;
    }

    public byte[] serialize() {
        return null;
    }


    /**
     *  Set the attributes that belong to this node.
     *
     *@param  attribNum  The new attributes value
     */
    public void setAttributes( short attribNum ) {
        attributes = attribNum;
    }


    /**
     *  Set the number of children.
     *
     *@param  count  The new childCount value
     */
    protected void setChildCount( int count ) {
        return;
    }


    /**
     *  Set the unique node identifier of this node.
     *
     *@param  gid  The new gID value
     */
    public void setGID( long gid ) {
        this.gid = gid;
    }


    /**
     *  Set the internal storage address of this node.
     *
     *@param  address  The new internalAddress value
     */
    public void setInternalAddress( long address ) {
        internalAddress = address;
    }


    /**
     *  Set the node name.
     *
     *@param  name  The new nodeName value
     */
    public void setNodeName( String name ) {
        nodeName = name;
    }


    /**
     *  Set the node value.
     *
     *@param  value             The new nodeValue value
     *@exception  DOMException  Description of the Exception
     */
    public void setNodeValue( String value ) throws DOMException {
    }


    /**
     *  Set the owner document.
     *
     *@param  doc  The new ownerDocument value
     */
    public void setOwnerDocument( Document doc ) {
        ownerDocument = (DocumentImpl) doc;
    }


    /**
     *  Sets the prefix attribute of the NodeImpl object
     *
     *@param  prefix            The new prefix value
     *@exception  DOMException  Description of the Exception
     */
    public void setPrefix( String prefix ) throws DOMException {
    }


    /**
	 * Method supports.
	 * @param feature
	 * @param version
	 * @return boolean
	 */
	public boolean supports( String feature, String version ) {
        return false;
    }


    /**
     *  Description of the Method
     *
     *@param  contentHandler    Description of the Parameter
     *@param  lexicalHandler    Description of the Parameter
     *@param  first             Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void toSAX( ContentHandler contentHandler, LexicalHandler lexicalHandler,
                       boolean first ) throws SAXException {
        toSAX( contentHandler, lexicalHandler, first, new ArrayList( 5 ) );
    }


    /**
     *  Description of the Method
     *
     *@param  contentHandler    Description of the Parameter
     *@param  lexicalHandler    Description of the Parameter
     *@param  first             Description of the Parameter
     *@param  prefixes          Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void toSAX( ContentHandler contentHandler, LexicalHandler lexicalHandler,
                       boolean first, ArrayList prefixes ) throws SAXException {
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append( Long.toString( gid ) );
        buf.append( '\t' );
        buf.append( nodeName );
        return buf.toString();
    }


    /**
     *  Description of the Method
     *
     *@param  top  Description of the Parameter
     *@return      Description of the Return Value
     */
    public String toString( boolean top ) {
        return toString();
    }
    
        /**
     * Returns the nodeNameRef.
     * @return int
     */
    public int getNodeNameRef() {
        return nodeNameRef;
    }

    /**
     * Sets the nodeNameRef.
     * @param nodeNameRef The nodeNameRef to set
     */
    public void setNodeNameRef(int nodeNameRef) {
        this.nodeNameRef = nodeNameRef;
    }
}

