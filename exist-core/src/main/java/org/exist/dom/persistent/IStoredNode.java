/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
package org.exist.dom.persistent;

import org.exist.dom.INode;
import org.exist.storage.NodePath;


//TODO do we really need to extend Visitable any more?
public interface IStoredNode<T extends IStoredNode> extends INode<DocumentImpl, T>, NodeHandle, Visitable {

    //<editor-fold desc="serialization">

    /**
     * Serialize the state of this node
     * into a byte array.
     *
     * @return A byte array containing the
     * serialization of the node
     */
    public byte[] serialize();

    //public static StoredNode deserialize(byte[] data, int start, int len);
    //IStoredNode deserialize(); //TODO perhaps use package protected method?

    //</editor-fold>

    /**
     * Set the Document that this node belongs to
     *
     * Counterpart to @see org.exist.dom.INode#getOwnerDocument()
     *
     * @param doc The document that this node belongs to
     */
    public void setOwnerDocument(DocumentImpl doc);


    //<editor-fold desc="temp">

    //TODO see StoredNode.getParentStoredNode and StoredNode.getParentNode, should be able to remove in favour of getParentNode() in future.
    public IStoredNode getParentStoredNode();
    //</editor-fold>


    /**
     * @return a count of the number of children
     *
     */
    public int getChildCount(); //TODO also available in memtree.ElementImpl - consider moving to org.exist.dom.INode (also this is only really used for ElementImpl and DocumentImpl)

    /**
     * Returns true if the node was modified recently and nodes
     * were inserted at the start or in the middle of its children.
     *
     * TODO: 2019-07-11 can't we not detect this?
     **/
    //public boolean isDirty();


    /**
     * Set the node to dirty to indicated
     * that nodes were inserted at the start
     * or in the middle of its children.
     * @param dirty set to true if node is dirty
     */
    public void setDirty(boolean dirty);


    public NodePath getPath();

    public NodePath getPath(NodePath parentPath); //TODO seems to be ElementImpl specific see StoredNode

    //TODO clean this up

    /**
     * @see StoredNode#release()
     * this seems to do two things
     * clear the state, and then return the object
     * to NodePool - all a bit of a mess really!
     *
     * org.exist.Indexer seems to borrow and return to the pool
     * org.exist.memtree.DOMIndexer only seems to borrow nodes
     * org.exist.serializers.NativeSerializer only seems to return nodes
     * org.exist.dom.persistent.*Impl#deserialize(...) seem to have support for pooling
     * yet this is set to false in the invoking code!
     */
    public void release();
}
