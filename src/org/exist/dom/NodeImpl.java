
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
     * Reset this object to its initial state.
     */
    public void clear() {
        attributes = 0;
        gid = 0;
        internalAddress = 0;
        nodeName = null;
        ownerDocument = null;
    }
    
    /**
     *  Description of the Method
     *
     *@param  child             Description of the Parameter
     *@return                   Description of the Return Value
     *@exception  DOMException  Description of the Exception
     */
    public Node appendChild( Node child ) throws DOMException {
        return null;
    }


    /**
     *  Description of the Method
     *
     *@param  deep  Description of the Parameter
     *@return       Description of the Return Value
     */
    public Node cloneNode( boolean deep ) {
        return this;
    }


    /**
     *  Description of the Method
     *
     *@param  obj  Description of the Parameter
     *@return      Description of the Return Value
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
     *  Gets the allChildCount attribute of the NodeImpl object
     *
     *@return    The allChildCount value
     */
    public int getAllChildCount() {
        return 0;
    }


    /**
     *  Gets the attributes attribute of the NodeImpl object
     *
     *@return    The attributes value
     */
    public NamedNodeMap getAttributes() {
        return null;
    }


    /**
     *  Gets the attributesCount attribute of the NodeImpl object
     *
     *@return    The attributesCount value
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
     *  Gets the childCount attribute of the NodeImpl object
     *
     *@return    The childCount value
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
     *  Gets the firstChild attribute of the NodeImpl object
     *
     *@return    The firstChild value
     */
    public Node getFirstChild() {
        return null;
    }


    /**
     *  Gets the gID attribute of the NodeImpl object
     *
     *@return    The gID value
     */
    public long getGID() {
        return gid;
    }


    /**
     *  Gets the internalAddress attribute of the NodeImpl object
     *
     *@return    The internalAddress value
     */
    public long getInternalAddress() {
        return internalAddress;
    }


    /**
     *  Gets the lastChild attribute of the NodeImpl object
     *
     *@return    The lastChild value
     */
    public Node getLastChild() {
        return null;
    }


    /**
     *  Gets the localName attribute of the NodeImpl object
     *
     *@return    The localName value
     */
    public String getLocalName() {
        if ( nodeName != null && nodeName.indexOf( ':' ) > -1 )
            return nodeName.substring( nodeName.indexOf( ':' ) + 1 );
        return nodeName;
    }


    /**
     *  Gets the namespaceURI attribute of the NodeImpl object
     *
     *@return    The namespaceURI value
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
     *  Gets the nextSibling attribute of the NodeImpl object
     *
     *@return    The nextSibling value
     */
    public Node getNextSibling() {
        NodeImpl parent = (NodeImpl) getParentNode();
        if ( gid < parent.lastChildID() )
            return ownerDocument.getNode( gid + 1 );
        return null;
    }


    /**
     *  Gets the nodeName attribute of the NodeImpl object
     *
     *@return    The nodeName value
     */
    public String getNodeName() {
        return nodeName;
    }


    /**
     *  Gets the nodeType attribute of the NodeImpl object
     *
     *@return    The nodeType value
     */
    public short getNodeType() {
        return nodeType;
    }


    /**
     *  Gets the nodeValue attribute of the NodeImpl object
     *
     *@return                   The nodeValue value
     *@exception  DOMException  Description of the Exception
     */
    public String getNodeValue() throws DOMException {
        return "";
    }


    /**
     *  Gets the ownerDocument attribute of the NodeImpl object
     *
     *@return    The ownerDocument value
     */
    public Document getOwnerDocument() {
        return ownerDocument;
    }


    /**
     *  Gets the parentByName attribute of the NodeImpl object
     *
     *@param  parentName  Description of the Parameter
     *@return             The parentByName value
     */
    public Node getParentByName( String parentName ) {
        NodeImpl p = this;
        while ( p != null ) {
            if ( p.getNodeName().equals( parentName ) )
                return p;
            p = (NodeImpl) p.getParentNode();
        }
        return null;
    }


    /**
     *  Gets the parentGID attribute of the NodeImpl object
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
     *  Gets the parentNode attribute of the NodeImpl object
     *
     *@return    The parentNode value
     */
    public Node getParentNode() {
        if ( gid < 2 )
            return ownerDocument;
        long pid = getParentGID();
        return ownerDocument.getNode( pid );
    }


    /**
     *  Gets the prefix attribute of the NodeImpl object
     *
     *@return    The prefix value
     */
    public String getPrefix() {
        if ( nodeName != null && nodeName.indexOf( ':' ) > -1 )
            return nodeName.substring( 0, nodeName.indexOf( ':' ) );
        return "";
    }


    /**
     *  Gets the previousSibling attribute of the NodeImpl object
     *
     *@return    The previousSibling value
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
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public boolean hasAttributes() {
        return false;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public boolean hasChildNodes() {
        return false;
    }


    /**
     *  Description of the Method
     *
     *@param  newChild          Description of the Parameter
     *@param  refChild          Description of the Parameter
     *@return                   Description of the Return Value
     *@exception  DOMException  Description of the Exception
     */
    public Node insertBefore( Node newChild, Node refChild )
         throws DOMException {
        return null;
    }


    /**
     *  Gets the supported attribute of the NodeImpl object
     *
     *@param  key    Description of the Parameter
     *@param  value  Description of the Parameter
     *@return        The supported value
     */
    public boolean isSupported( String key, String value ) {
        return false;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public long lastChildID() {
        return 0;
    }


    /**  Description of the Method */
    public void normalize() {
        return;
    }


    /**
     *  Description of the Method
     *
     *@param  node              Description of the Parameter
     *@return                   Description of the Return Value
     *@exception  DOMException  Description of the Exception
     */
    public Node removeChild( Node node ) throws DOMException {
        return null;
    }


    /**
     *  Description of the Method
     *
     *@param  newChild          Description of the Parameter
     *@param  oldChild          Description of the Parameter
     *@return                   Description of the Return Value
     *@exception  DOMException  Description of the Exception
     */
    public Node replaceChild( Node newChild, Node oldChild ) throws DOMException {
        return null;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public byte[] serialize() {
        return null;
    }


    /**
     *  Sets the attributes attribute of the NodeImpl object
     *
     *@param  attribNum  The new attributes value
     */
    public void setAttributes( short attribNum ) {
        attributes = attribNum;
    }


    /**
     *  Sets the childCount attribute of the NodeImpl object
     *
     *@param  count  The new childCount value
     */
    protected void setChildCount( int count ) {
        return;
    }


    /**
     *  Sets the gID attribute of the NodeImpl object
     *
     *@param  gid  The new gID value
     */
    public void setGID( long gid ) {
        this.gid = gid;
    }


    /**
     *  Sets the internalAddress attribute of the NodeImpl object
     *
     *@param  address  The new internalAddress value
     */
    public void setInternalAddress( long address ) {
        internalAddress = address;
    }


    /**
     *  Sets the nodeName attribute of the NodeImpl object
     *
     *@param  name  The new nodeName value
     */
    public void setNodeName( String name ) {
        nodeName = name;
    }


    /**
     *  Sets the nodeValue attribute of the NodeImpl object
     *
     *@param  value             The new nodeValue value
     *@exception  DOMException  Description of the Exception
     */
    public void setNodeValue( String value ) throws DOMException {
    }


    /**
     *  Sets the ownerDocument attribute of the NodeImpl object
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
     *  Description of the Method
     *
     *@param  feature  Description of the Parameter
     *@param  version  Description of the Parameter
     *@return          Description of the Return Value
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

