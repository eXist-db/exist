
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
package org.exist.dom;

import java.util.Comparator;
import org.w3c.dom.Node;

/**
 *  Placeholder class for any DOM-node. NodeProxy stores a node's unique id and
 *  the document a node belongs to. eXist will always try to use a NodeProxy
 *  instead of the actual node. Using a NodeProxy is much cheaper than loading
 *  the actual node from the database. All sets of type NodeSet operate on
 *  NodeProxys. NodeProxy implements Comparable, which is needed by all
 *  node-sets. To convert a NodeProxy to a real node, simply call getNode().
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    22. Juli 2002
 */
public final class NodeProxy implements Comparable, Cloneable {
    /**  Description of the Field */
    public DocumentImpl doc = null;

    /**  Description of the Field */
    public long gid = 0;
    /**  Description of the Field */
    public long internalAddress = -1;
    /**  Description of the Field */
    public short nodeType = -1;

    public NodeProxy() {
    }
        
    /**
     *  Constructor for the NodeProxy object
     *
     *@param  doc      Description of the Parameter
     *@param  gid      Description of the Parameter
     *@param  address  Description of the Parameter
     */
    public NodeProxy( DocumentImpl doc, long gid, long address ) {
        this.gid = gid;
        this.doc = doc;
        this.internalAddress = address;
    }


    /**
     *  Construct a node proxy with unique id gid and owned by document doc.
     *
     *@param  doc  Description of the Parameter
     *@param  gid  Description of the Parameter
     */
    public NodeProxy( DocumentImpl doc, long gid ) {
        this.doc = doc;
        this.gid = gid;
    }


    /**
     *  as above, but a hint is given about the node type of this proxy-object.
     *
     *@param  doc       Description of the Parameter
     *@param  gid       Description of the Parameter
     *@param  nodeType  Description of the Parameter
     */
    public NodeProxy( DocumentImpl doc, long gid, short nodeType ) {
        this( doc, gid );
        this.nodeType = nodeType;
    }


    /**
     *  Constructor for the NodeProxy object
     *
     *@param  doc       Description of the Parameter
     *@param  gid       Description of the Parameter
     *@param  nodeType  Description of the Parameter
     *@param  address   Description of the Parameter
     */
    public NodeProxy( DocumentImpl doc, long gid, short nodeType,
                      long address ) {
        this( doc, gid, address );
        this.internalAddress = address;
    }


    /**
     *  Constructor for the NodeProxy object
     *
     *@param  p  Description of the Parameter
     */
    public NodeProxy( NodeProxy p ) {
        this.gid = p.gid;
        this.doc = p.doc;
	this.internalAddress = p.internalAddress;
    }


    /**
     *  Constructor for the NodeProxy object
     *
     *@param  node  Description of the Parameter
     */
    public NodeProxy( NodeImpl node ) {
        this( (DocumentImpl) node.getOwnerDocument(), node.getGID() );
    }


    public void clear() {
        doc = null;
        gid = 0;
        internalAddress = -1;
        nodeType = -1;
    }
    
    /**
     *  Description of the Method
     *
     *@param  other  Description of the Parameter
     *@return        Description of the Return Value
     */
    public int compareTo( Object other ) {
        return NodeProxyComparator.instance.compare( this, other );
    }


    /**
     *  Description of the Method
     *
     *@param  other  Description of the Parameter
     *@return        Description of the Return Value
     */
    public boolean equals( Object other ) {
        if ( !( other instanceof NodeProxy ) )
            throw new RuntimeException( "cannot compare nodes from different implementations" );
        NodeProxy node = (NodeProxy) other;
        if ( node.doc.getDocId() == doc.getDocId() &&
            node.gid == gid )
            return true;
        return false;
    }


    /**
     *  Gets the address attribute of the NodeProxy object
     *
     *@return    The address value
     */
    public long getAddress() {
        return internalAddress;
    }


    /**
     *  Gets the brokerType attribute of the NodeProxy object
     *
     *@return    The brokerType value
     */
    public int getBrokerType() {
        return doc.broker.getDatabaseType();
    }


    /**
     *  Gets the doc attribute of the NodeProxy object
     *
     *@return    The doc value
     */
    public DocumentImpl getDoc() {
        return doc;
    }


    /**
     *  Gets the gID attribute of the NodeProxy object
     *
     *@return    The gID value
     */
    public long getGID() {
        return gid;
    }


    /**
     *  Gets the node attribute of the NodeProxy object
     *
     *@return    The node value
     */
    public Node getNode() {
        return doc.getNode( gid );
    }


    /**
     *  Gets the nodeType attribute of the NodeProxy object
     *
     *@return    The nodeType value
     */
    public short getNodeType() {
        return nodeType;
    }


    /**
     *  Gets the nodeValue attribute of the NodeProxy object
     *
     *@return    The nodeValue value
     */
    public String getNodeValue() {
        return doc.getBroker().getNodeValue( this );
    }


    /**
     *  Gets the parent attribute of the NodeProxy object
     *
     *@return    The parent value
     */
    public long getParent() {
        // calculate parent's gid
        int level = getTreeLevel();
        return ( gid - doc.getLevelStartPoint( level ) ) /
            doc.getTreeLevelOrder( level )
             + doc.getLevelStartPoint( level - 1 );
    }


    /**
     *  Gets the parentAtLevel attribute of the NodeProxy object
     *
     *@param  level  Description of the Parameter
     *@return        The parentAtLevel value
     */
    public long getParentAtLevel( int level ) {
        int currentLevel = getTreeLevel();
        long pid = this.gid;
        if ( currentLevel < level )
            return -1;
        while ( currentLevel > level ) {
            pid = ( pid - doc.getLevelStartPoint( currentLevel ) ) /
                doc.getTreeLevelOrder( currentLevel )
                 + doc.getLevelStartPoint( currentLevel - 1 );
            currentLevel = currentLevel - 1;
        }
        return pid;
    }


    /**
     *  Gets the treeLevel attribute of the NodeProxy object
     *
     *@return    The treeLevel value
     */
    public int getTreeLevel() {
        return doc.getTreeLevel( gid );
    }


    /**
     *  Sets the address attribute of the NodeProxy object
     *
     *@param  address  The new address value
     */
    public void setAddress( long address ) {
        internalAddress = address;
    }


    /**
     *  Sets the gID attribute of the NodeProxy object
     *
     *@param  gid  The new gID value
     */
    public void setGID( long gid ) {
        this.gid = gid;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        return doc.getNode( gid ).toString();
    }


    /**
     *  Description of the Class
     *
     *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
     *@created    22. Juli 2002
     */
    public static class NodeProxyComparator implements Comparator {

        /**  Description of the Field */
        public static NodeProxyComparator instance =
            new NodeProxyComparator();


        /**
         *  Description of the Method
         *
         *@param  obj1  Description of the Parameter
         *@param  obj2  Description of the Parameter
         *@return       Description of the Return Value
         */
        public int compare( Object obj1, Object obj2 ) {
            if ( obj1 == null || obj2 == null )
                throw new NullPointerException( "cannot compare null values" );
            if ( !( obj1 instanceof NodeProxy && obj2 instanceof NodeProxy ) )
                throw new RuntimeException( "cannot compare nodes " +
                    "from different implementations" );
            NodeProxy p1 = (NodeProxy) obj1;
            NodeProxy p2 = (NodeProxy) obj2;
            if ( p1.doc.docId == p2.doc.docId ) {
                if ( p1.gid == p2.gid )
                    return 0;
                else if ( p1.gid < p2.gid )
                    return -1;
                else
                    return 1;
            }
            else if ( p1.doc.docId < p2.doc.docId )
                return -1;
            else
                return 1;
        }
    }


    /**
     * Returns the internalAddress.
     * @return long
     */
    public long getInternalAddress() {
        return internalAddress;
    }

    /**
     * Sets the doc.
     * @param doc The doc to set
     */
    public void setDoc(DocumentImpl doc) {
        this.doc = doc;
    }

    /**
     * Sets the internalAddress.
     * @param internalAddress The internalAddress to set
     */
    public void setInternalAddress(long internalAddress) {
        this.internalAddress = internalAddress;
    }

    /**
     * Sets the nodeType.
     * @param nodeType The nodeType to set
     */
    public void setNodeType(short nodeType) {
        this.nodeType = nodeType;
    }

	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}
}

