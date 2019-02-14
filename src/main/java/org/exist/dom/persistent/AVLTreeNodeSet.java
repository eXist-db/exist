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
 *  $Id$
 */
package org.exist.dom.persistent;

import org.exist.numbering.NodeId;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class AVLTreeNodeSet extends AbstractNodeSet {

    private Node root;
    private int size = 0;
    private int state = 0;

    @Override
    public SequenceIterator iterate() {
        return new InorderTraversal(root);
    }

    @Override
    public SequenceIterator unorderedIterator() {
        return new InorderTraversal(root);
    }

    @Override
    public void addAll(final NodeSet other) {
        for(final Iterator<NodeProxy> i = other.iterator(); i.hasNext(); ) {
            add(i.next());
        }
    }

    @Override
    public int getLength() {
        return size;
    }

    @Override
    public long getItemCountLong() {
        return size;
    }


    //TODO could we not just use itemAt(index) here or get(pos)?
    @Override
    public org.w3c.dom.Node item(final int pos) {
        final NodeProxy proxy = get(pos);
        return proxy == null ? null : proxy.getNode();
    }

    @Override
    public NodeProxy get(final int pos) {
        return (NodeProxy) itemAt(pos);
    }

    @Override
    public final NodeProxy get(final NodeProxy p) {
        final Node n = searchData(root, p);
        return n == null ? null : n.getData();
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean hasOne() {
        return size == 1;
    }

    @Override
    public Item itemAt(final int pos) {
        int i = 0;
        for(final Iterator<NodeProxy> it = iterator(); it.hasNext(); i++) {
            final NodeProxy p = it.next();
            if(i == pos) {
                return p;
            }
        }
        return null;
    }

    @Override
    public final void add(final NodeProxy proxy) {
        if(proxy == null) {
            return;
        }

        setHasChanged();
        if(root == null) {
            root = new Node(proxy);
            ++size;
            return;
        }

        Node tempNode = root;
        while(true) {
            final int c = tempNode.data.compareTo(proxy);
            if(c == 0) {
                return;
            }
            if(c > 0) { // inserts s into left subtree.
                if(tempNode.hasLeftChild()) {
                    tempNode = tempNode.leftChild;
                    continue;
                }
                final Node newNode = tempNode.addLeft(proxy);
                balance(newNode);
                ++size;
                return;
            }
            // inserts s to right subtree
            if(tempNode.hasRightChild()) {
                tempNode = tempNode.rightChild;
                continue;
            }
            final Node newNode = tempNode.addRight(proxy);
            balance(newNode);
            ++size;
            return;
        }
    }

    public Node getMinNode() {
        if(root == null) {
            return null;
        }
        Node tempNode = root;
        while(tempNode.hasLeftChild()) {
            tempNode = tempNode.getLeftChild();
        }
        return tempNode;
    }

    public Node getMaxNode() {
        if(root == null) {
            return null;
        }
        Node tempNode = root;
        while(tempNode.hasRightChild()) {
            tempNode = tempNode.getRightChild();
        }
        return tempNode;
    }

    private void balance(final Node node) {
        Node currentNode = node;
        Node currentParent = node.parent;
        while(currentNode != root) {
            final int h = currentParent.height;
            currentParent.setHeight();
            if(h == currentParent.height) {
                return;
            } // Case 1
            if(currentParent.balanced()) {
                currentNode = currentParent;
                currentParent = currentNode.parent;
                continue;
            }
            if(currentParent.leftHeight() - currentParent.rightHeight()
                == 2) {
                Node nodeA = currentParent,
                    nodeB = nodeA.getLeftChild(),
                    //nodeC = nodeB.getLeftChild(),
                    nodeD = nodeB.getRightChild();
                if(nodeB.leftHeight() > nodeB.rightHeight()) {
                    // right rotation for Case 2
                    nodeA.addLeftChild(nodeD);
                    if(nodeA != root) {
                        if(nodeA.isLeftChild()) {
                            nodeA.parent.addLeftChild(nodeB);
                        } else {
                            nodeA.parent.addRightChild(nodeB);
                        }
                    } else {
                        root = nodeB;
                    }

                    nodeB.addRightChild(nodeA);
                    nodeA.setHeight();
                    nodeB.setHeight();
                    currentNode = nodeB;
                    currentParent = currentNode.parent;
                    continue;
                }
                // Case 3 and Case 4
                if(nodeD.hasLeftChild()) {
                    nodeB.addRightChild(nodeD.getLeftChild());
                } else {
                    nodeB.removeRightChild();
                }
                if(nodeD.hasRightChild()) {
                    nodeA.addLeftChild(nodeD.getRightChild());
                } else {
                    nodeA.removeLeftChild();
                }

                if(currentParent != root) {
                    if(nodeA.isLeftChild()) {
                        nodeA.parent.addLeftChild(nodeD);
                    } else {
                        nodeA.parent.addRightChild(nodeD);
                    }
                } else {
                    root = nodeD;
                }
                nodeD.addLeftChild(nodeB);
                nodeD.addRightChild(nodeA);
                nodeB.setHeight();
                nodeA.setHeight();
                nodeD.setHeight();
                currentNode = nodeD;
                currentParent = currentNode.parent;
                continue;
            }

            if(currentParent.leftHeight() - currentParent.rightHeight()
                == -2) {
                final Node nodeA = currentParent;
                Node nodeB = nodeA.getRightChild();
                Node nodeC = nodeB.getLeftChild();
                //Node nodeD = nodeB.getRightChild();

                if(nodeB.leftHeight() < nodeB.rightHeight()) {
                    // left rotation for Case 2
                    nodeA.addRightChild(nodeC);
                    if(nodeA != root) {
                        if(nodeA.isLeftChild()) {
                            nodeA.parent.addLeftChild(nodeB);
                        } else {
                            nodeA.parent.addRightChild(nodeB);
                        }
                    } else {
                        root = nodeB;
                    }
                    
                    nodeB.addLeftChild(nodeA);
                    nodeA.setHeight();
                    nodeB.setHeight();
                    currentNode = nodeB;
                    currentParent = currentNode.parent;
                    continue;
                }
                // Case 3 and Case 4
                if(nodeC.hasLeftChild()) {
                    nodeA.addRightChild(nodeC.getLeftChild());
                } else {
                    nodeA.removeRightChild();
                }
                if(nodeC.hasRightChild()) {
                    nodeB.addLeftChild(nodeC.getRightChild());
                } else {
                    nodeB.removeLeftChild();
                }

                if(nodeA != root) {
                    if(nodeA.isLeftChild()) {
                        nodeA.parent.addLeftChild(nodeC);
                    } else {
                        nodeA.parent.addRightChild(nodeC);
                    }

                } else {
                    root = nodeC;
                }
                nodeC.addLeftChild(nodeA);
                nodeC.addRightChild(nodeB);
                nodeB.setHeight();
                nodeA.setHeight();
                nodeC.setHeight();
                currentNode = nodeC;
                currentParent = currentNode.parent;
                continue;
            }

        }
    }

    private static @Nullable Node searchData(@Nullable final Node root, final NodeProxy proxy) {
        if(root == null) {
            return null;
        }

        Node tempNode = root;
        while(tempNode != null) {
            final int c = tempNode.data.compareTo(proxy);
            if(c == 0) {
                return tempNode;
            }
            if(c < 0) {
                tempNode = tempNode.rightChild;
            } else {
                tempNode = tempNode.leftChild;
            }
        }
        return null;
    }

    @Override
    public final NodeProxy get(final DocumentImpl doc, final NodeId nodeId) {
        if(root == null) {
            return null;
        }

        Node tempNode = root;
        int cmp;
        while(tempNode != null) {
            if(tempNode.data.getOwnerDocument().getDocId() == doc.getDocId()) {
                cmp = tempNode.data.getNodeId().compareTo(nodeId);
                if(cmp == 0) {
                    return tempNode.data;
                } else if(cmp < 0) {
                    tempNode = tempNode.rightChild;
                } else {
                    tempNode = tempNode.leftChild;
                }
            } else if(tempNode.data.getOwnerDocument().getDocId() < doc.getDocId()) {
                tempNode = tempNode.rightChild;
            } else {
                tempNode = tempNode.leftChild;
            }
        }
        return null;
    }

    @Override
    public final boolean contains(final NodeProxy proxy) {
        return searchData(root, proxy) != null;
    }

    public void removeNode(final Node node) {
        --size;
        Node tempNode = node;
        while(tempNode.hasLeftChild() || tempNode.hasRightChild()) {
            if(tempNode.hasLeftChild()) {
                tempNode = tempNode.getLeftChild();
                while(tempNode.hasRightChild()) {
                    tempNode = tempNode.getRightChild();
                }
            } else {
                tempNode = tempNode.getRightChild();
                while(tempNode.hasLeftChild()) {
                    tempNode = tempNode.getLeftChild();
                }
            }
            node.setData(tempNode.getData());
        }
        if(tempNode == root) {
            root = null;
            return;
        }

        final Node parent = tempNode.parent;
        if(tempNode.isLeftChild()) {
            parent.removeLeftChild();
            if(parent.hasRightChild()) {
                balance(parent.getRightChild());
            } else {
                balance(parent);
            }
        } else {
            parent.removeRightChild();
            if(parent.hasLeftChild()) {
                balance(parent.getLeftChild());
            } else {
                balance(parent);
            }
        }
    }

    @Override
    public NodeSetIterator iterator() {
        return new InorderTraversal(root);
    }

    @Override
    public boolean hasChanged(final int previousState) {
        return state != previousState;
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    private void setHasChanged() {
        state = (state == Integer.MAX_VALUE ? 0 : state + 1);
    }

    private static class InorderTraversal implements NodeSetIterator, SequenceIterator {
        @Nullable private final Node root;
        private final Deque<Node> nodes = new ArrayDeque<>();

        public InorderTraversal(@Nullable final Node root) {
            this.root = root;
            if(root != null) {
                Node tempNode = root;
                do {
                    nodes.push(tempNode);
                    tempNode = tempNode.leftChild;
                } while(tempNode != null);
            }
        }

        @Override
        public boolean hasNext() {
            return !nodes.isEmpty();
        }

        @Override
        public NodeProxy next() {
            if(nodes.isEmpty()) {
                return null;
            }
            final Node currentNode = nodes.pop();
            if(currentNode.hasRightChild()) {
                Node tempNode = currentNode.rightChild;
                do {
                    nodes.push(tempNode);
                    tempNode = tempNode.leftChild;
                } while(tempNode != null);
            }
            return currentNode.getData();
        }

        @Override
        public NodeProxy peekNode() {
            final Node currentNode = nodes.peek();
            if (currentNode == null) {
                return null;
            }
            return currentNode.getData();
        }

        @Override
        public void setPosition(final NodeProxy proxy) {
            final Node n = searchData(root, proxy);
            nodes.clear();
            if(n != null) {
                Node tempNode = n;
                do {
                    nodes.push(tempNode);
                    tempNode = tempNode.leftChild;
                } while(tempNode != null);
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove is not supported on InorderTraversal");
        }

        @Override
        public Item nextItem() {
            if(nodes.isEmpty()) {
                return null;
            }
            final Node currentNode = nodes.pop();
            if (currentNode.hasRightChild()) {
                Node tempNode = currentNode.rightChild;
                do {
                    nodes.push(tempNode);
                    tempNode = tempNode.leftChild;
                } while(tempNode != null);
            }
            return currentNode.getData();
        }
    }

    @Override
    public String toString() {
        return "AVLTree#" + super.toString();
    }

    private static final class Node {
        private NodeProxy data;
        private Node parent;
        private Node leftChild;
        private Node rightChild;
        private int height;

        public Node(final NodeProxy data) {
            this.data = data;
        }

        public void setData(final NodeProxy data) {
            this.data = data;
        }

        public NodeProxy getData() {
            return data;
        }

        public boolean hasLeftChild() {
            return (leftChild != null);
        }

        public boolean hasRightChild() {
            return (rightChild != null);
        }

        public Node getLeftChild() {
            return leftChild;
        }

        public Node getRightChild() {
            return rightChild;
        }

        public boolean balanced() {
            return (Math.abs(leftHeight() - rightHeight()) <= 1);
        }

        public Node addLeft(final NodeProxy data) {
            final Node tempNode = new Node(data);
            this.leftChild = tempNode;
            tempNode.parent = this;
            return tempNode;
        }

        public Node addLeftChild(final Node node) {
            this.leftChild = node;
            if(node != null) {
                node.parent = this;
            }
            return node;
        }

        public Node addRight(final NodeProxy data) {
            final Node tempNode = new Node(data);
            this.rightChild = tempNode;
            tempNode.parent = this;
            return tempNode;
        }

        public Node addRightChild(final Node node) {
            this.rightChild = node;
            if(node != null) {
                node.parent = this;
            }
            return node;
        }

        public Node removeLeftChild() {
            final Node tempNode = leftChild;
            this.leftChild = null;
            return tempNode;
        }

        public Node removeRightChild() {
            final Node tempNode = rightChild;
            this.rightChild = null;
            return tempNode;
        }

        public int degree() {
            int i = 0;
            if(leftChild != null) {
                i++;
            }
            if(rightChild != null) {
                i++;
            }
            return i;
        }

        public void setHeight() {
            this.height = Math.max(leftHeight(), rightHeight());
        }

        public boolean isLeftChild() {
            return (this == parent.leftChild);
        }

        public boolean isRightChild() {
            return (this == parent.rightChild);
        }

        public int leftHeight() {
            if(hasLeftChild()) {
                return (1 + leftChild.height);
            }
            return 0;
        }

        public int rightHeight() {
            if(hasRightChild()) {
                return (1 + rightChild.height);
            }
            return 0;
        }

        public int height() {
            return height;
        }

    }

}
