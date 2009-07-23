package org.exist.dom;

import org.exist.numbering.NodeId;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;

import java.util.Iterator;
import java.util.Stack;

public class AVLTreeNodeSet extends AbstractNodeSet {

    private Node root;
    private int size = 0;
    private int state = 0;
    
    public AVLTreeNodeSet() {
    }

    /* (non-Javadoc)
      * @see org.exist.dom.NodeSet#iterate()
      */
    public SequenceIterator iterate() throws XPathException {
        return new InorderTraversal();
    }

    public SequenceIterator unorderedIterator() throws XPathException {
        return new InorderTraversal();
    }

    /* (non-Javadoc)
      * @see org.exist.dom.NodeSet#addAll(org.exist.dom.NodeSet)
      */
    public void addAll(NodeSet other) {
        for (Iterator i = other.iterator(); i.hasNext();)
            add((NodeProxy) i.next());
    }

    /* (non-Javadoc)
      * @see org.exist.dom.NodeSet#getLength()
      */
    public int getLength() {
        return size;
    }
    
    //TODO : evaluate both semantics
    public int getItemCount() {
    	return size;
    }

    /* (non-Javadoc)
      * @see org.exist.dom.NodeSet#item(int)
      */
    public org.w3c.dom.Node item(int pos) {
        int i = 0;
        for(Iterator it = iterator(); it.hasNext(); i++) {
            NodeProxy p = (NodeProxy) it.next();
            if(i == pos)
                return p.getNode();
        }
        return null;
    }

    /* (non-Javadoc)
      * @see org.exist.dom.NodeSet#get(int)
      */
    public NodeProxy get(int pos) {
        return (NodeProxy)itemAt(pos);
    }

    /* (non-Javadoc)
      * @see org.exist.dom.NodeSet#get(org.exist.dom.NodeProxy)
      */
    public final NodeProxy get(NodeProxy p) {
        Node n = searchData(p);
        return n == null ? null : n.getData();
    }
	
    public boolean isEmpty() {		
		return (size == 0);
    }
    
    public boolean hasOne() {
    	return (size == 1);
    }   

    /* (non-Javadoc)
      * @see org.exist.xquery.value.Sequence#itemAt(int)
      */
    public Item itemAt(int pos) {
        int i = 0;
        for(Iterator it = iterator(); it.hasNext(); i++) {
            NodeProxy p = (NodeProxy) it.next();
            if(i == pos)
                return p;
        }
        return null;
    }

	public final void add(NodeProxy proxy) {
		if(proxy == null)
			return;
        setHasChanged();
        if (root == null) {
			root = new Node(proxy);
			++size;
			return;
		}
		Node tempNode = root;
		while (true) {
			int c = tempNode.data.compareTo(proxy);
			if (c == 0) {
				return;
			}
			if (c > 0) { // inserts s into left subtree.
				if (tempNode.hasLeftChild()) {
					tempNode = tempNode.leftChild;
					continue;
				} else {
					Node newNode = tempNode.addLeft(proxy);
					balance(newNode);
					++size;
					return;
				}
			} else { // inserts s to right subtree
				if (tempNode.hasRightChild()) {
					tempNode = tempNode.rightChild;
					continue;
				} else {
					Node newNode = tempNode.addRight(proxy);
					balance(newNode);
					++size;
					return;
				}
			}
		}		
	}

    public Node getMinNode() {
        if (root == null)
            return null;
        Node tempNode = root;
        while (tempNode.hasLeftChild())
            tempNode = tempNode.getLeftChild();
        return tempNode;
    }

    public Node getMaxNode() {
        if (root == null)
            return null;
        Node tempNode = root;
        while (tempNode.hasRightChild())
            tempNode = tempNode.getRightChild();
        return tempNode;
    }

    private void balance(Node node) {
        Node currentNode, currentParent;
        currentNode = node;
        currentParent = node.parent;
        while (currentNode != root) {
            int h = currentParent.height;
            currentParent.setHeight();
            if (h == currentParent.height)
                return; // Case 1
            if (currentParent.balanced()) {
                currentNode = currentParent;
                currentParent = currentNode.parent;
                continue;
            }
            if (currentParent.leftHeight() - currentParent.rightHeight()
                == 2) {
                Node nodeA = currentParent,
                    nodeB = nodeA.getLeftChild(),
                    //nodeC = nodeB.getLeftChild(),
                    nodeD = nodeB.getRightChild();
                if (nodeB.leftHeight() > nodeB.rightHeight()) {
                    // right rotation for Case 2
                    nodeA.addLeftChild(nodeD);
                    if (nodeA != root) {
                        if (nodeA.isLeftChild())
                            nodeA.parent.addLeftChild(nodeB);
                        else
                            nodeA.parent.addRightChild(nodeB);
                    } else {
                        root = nodeB;
                    };
                    nodeB.addRightChild(nodeA);
                    nodeA.setHeight();
                    nodeB.setHeight();
                    currentNode = nodeB;
                    currentParent = currentNode.parent;
                    continue;
                }
                // Case 3 and Case 4
                Node nodeE = null, nodeF = null;
                if (nodeD.hasLeftChild()) {
                    nodeE = nodeD.getLeftChild();
                    nodeB.addRightChild(nodeE);
                } else
                    nodeB.removeRightChild();
                if (nodeD.hasRightChild()) {
                    nodeF = nodeD.getRightChild();
                    nodeA.addLeftChild(nodeF);
                } else
                    nodeA.removeLeftChild();

                if (currentParent != root) {
                    if (nodeA.isLeftChild())
                        nodeA.parent.addLeftChild(nodeD);
                    else
                        nodeA.parent.addRightChild(nodeD);
                } else
                    root = nodeD;
                nodeD.addLeftChild(nodeB);
                nodeD.addRightChild(nodeA);
                nodeB.setHeight();
                nodeA.setHeight();
                nodeD.setHeight();
                currentNode = nodeD;
                currentParent = currentNode.parent;
                continue;
            }

            if (currentParent.leftHeight() - currentParent.rightHeight()
                == -2) {
                Node nodeA = currentParent;
                Node nodeB = nodeA.getRightChild();
                Node nodeC = nodeB.getLeftChild();
                //Node nodeD = nodeB.getRightChild();

                if (nodeB.leftHeight() < nodeB.rightHeight()) {
                    // left rotation for Case 2
                    nodeA.addRightChild(nodeC);
                    if (nodeA != root) {
                        if (nodeA.isLeftChild())
                            nodeA.parent.addLeftChild(nodeB);
                        else
                            nodeA.parent.addRightChild(nodeB);
                    } else {
                        root = nodeB;
                    };
                    nodeB.addLeftChild(nodeA);
                    nodeA.setHeight();
                    nodeB.setHeight();
                    currentNode = nodeB;
                    currentParent = currentNode.parent;
                    continue;
                }
                // Case 3 and Case 4
                Node nodeE = null, nodeF = null;
                if (nodeC.hasLeftChild()) {
                    nodeE = nodeC.getLeftChild();
                    nodeA.addRightChild(nodeE);
                } else
                    nodeA.removeRightChild();
                if (nodeC.hasRightChild()) {
                    nodeF = nodeC.getRightChild();
                    nodeB.addLeftChild(nodeF);
                } else
                    nodeB.removeLeftChild();

                if (nodeA != root) {
                    if (nodeA.isLeftChild())
                        nodeA.parent.addLeftChild(nodeC);
                    else
                        nodeA.parent.addRightChild(nodeC);

                } else
                    root = nodeC;
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

    public final Node searchData(NodeProxy proxy) {
        if (root == null)
            return null;
        Node tempNode = root;
        while (tempNode != null) {
            int c = tempNode.data.compareTo(proxy);
            if (c == 0)
                return tempNode;
            if (c < 0)
                tempNode = tempNode.rightChild;
            else
                tempNode = tempNode.leftChild;
        }
        return null;
    }

    public final NodeProxy get(DocumentImpl doc, NodeId nodeId) {
        if (root == null)
            return null;
        Node tempNode = root;
        int cmp;
        while (tempNode != null) {
            if (tempNode.data.getDocument().getDocId() == doc.getDocId()) {
            	cmp = tempNode.data.getNodeId().compareTo(nodeId);
                if (cmp == 0)
                    return tempNode.data;
                else if (cmp < 0)
                    tempNode = tempNode.rightChild;
                else
                    tempNode = tempNode.leftChild;
            } else if (tempNode.data.getDocument().getDocId() < doc.getDocId())
                tempNode = tempNode.rightChild;
            else
                tempNode = tempNode.leftChild;
        }
        return null;
    }

    /* (non-Javadoc)
      * @see org.exist.dom.NodeSet#contains(org.exist.dom.NodeProxy)
      */
    public final boolean contains(NodeProxy proxy) {
        return searchData(proxy) != null;
    }

    public void removeNode(Node node) {
        --size;
        Node tempNode = node;
        while (tempNode.hasLeftChild() || tempNode.hasRightChild()) {
            if (tempNode.hasLeftChild()) {
                tempNode = tempNode.getLeftChild();
                while (tempNode.hasRightChild())
                    tempNode = tempNode.getRightChild();
            } else {
                tempNode = tempNode.getRightChild();
                while (tempNode.hasLeftChild())
                    tempNode = tempNode.getLeftChild();
            }
            node.setData(tempNode.getData());
        }
        if (tempNode == root) {
            root = null;
            return;
        }
        if (tempNode.isLeftChild()) {
            node = tempNode.parent;
            node.removeLeftChild();
            if (node.hasRightChild())
                balance(node.getRightChild());
            else
                balance(node);
        } else {
            node = tempNode.parent;
            node.removeRightChild();
            if (node.hasLeftChild())
                balance(node.getLeftChild());
            else
                balance(node);
        }
    }

    public NodeSetIterator iterator() {
        return (this.new InorderTraversal());
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.dom.AbstractNodeSet#hasChanged(int)
     */
    public boolean hasChanged(int previousState) {
        return state != previousState;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.dom.AbstractNodeSet#getState()
     */
    public int getState() {
        return state;
    }

    public boolean isCacheable() {
        return true;
    }

    private void setHasChanged() {
        state = (state == Integer.MAX_VALUE ? state = 0 : state + 1);
    }

    class InorderTraversal implements NodeSetIterator, SequenceIterator {

        private Stack nodes;

        public InorderTraversal() {
            nodes = new Stack();
            if (root != null) {
                Node tempNode = root;
                do {
                    nodes.push(tempNode);
                    tempNode = tempNode.leftChild;
                } while (tempNode != null);
            }
        }

        public boolean hasNext() {
            if (nodes.size() == 0)
                return false;
            else
                return true;
        }

        public Object next() {
            if(nodes.isEmpty())
                return null;
            Node currentNode = (Node) nodes.peek();
            nodes.pop();
            if (currentNode.hasRightChild()) {
                Node tempNode = currentNode.rightChild;
                do {
                    nodes.push(tempNode);
                    tempNode = tempNode.leftChild;
                } while (tempNode != null);
            }
            return currentNode.getData();
        }

        public NodeProxy peekNode() {
            if(nodes.isEmpty())
                return null;
            Node currentNode = (Node) nodes.peek();
            return currentNode.getData();
        }
        
        public void setPosition(NodeProxy proxy) {
            Node n = searchData(proxy);
            nodes.clear();
            if (n != null) {
                Node tempNode = n;
                do {
                    nodes.push(tempNode);
                    tempNode = tempNode.leftChild;
                } while (tempNode != null);
            }
        }

        /* (non-Javadoc)
           * @see java.util.Iterator#remove()
           */
        public void remove() {
            throw new RuntimeException("Method remove is not implemented");
        }

        /* (non-Javadoc)
           * @see org.exist.xquery.value.SequenceIterator#nextItem()
           */
        public Item nextItem() {
            if(nodes.isEmpty())
                return null;
            Node currentNode = (Node) nodes.peek();
            nodes.pop();
            if (currentNode.hasRightChild()) {
                Node tempNode = currentNode.rightChild;
                do {
                    nodes.push(tempNode);
                    tempNode = tempNode.leftChild;
                } while (tempNode != null);
            }
            return currentNode.getData();
        }
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("AVLTree#").append(super.toString());
        return result.toString();
    }

    private final static class Node {

        public Node(NodeProxy data) {
            this.data = data;
        }

        public void setData(NodeProxy data) {
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

        public Node addLeft(NodeProxy data) {
            Node tempNode = new Node(data);
            leftChild = tempNode;
            tempNode.parent = this;
            return tempNode;
        }

        public Node addLeftChild(Node node) {
            leftChild = node;
            if (node != null)
                node.parent = this;
            return node;
        }

        public Node addRight(NodeProxy data) {
            Node tempNode = new Node(data);
            rightChild = tempNode;
            tempNode.parent = this;
            return tempNode;
        }

        public Node addRightChild(Node node) {
            rightChild = node;
            if (node != null)
                node.parent = this;
            return node;
        }

        public Node removeLeftChild() {
            Node tempNode = leftChild;
            leftChild = null;
            return tempNode;
        }

        public Node removeRightChild() {
            Node tempNode = rightChild;
            rightChild = null;
            return tempNode;
        }

        public int degree() {
            int i = 0;
            if (leftChild != null)
                i++;
            if (rightChild != null)
                i++;
            return i;
        }

        public void setHeight() {
            height = Math.max(leftHeight(), rightHeight());
        }

        public boolean isLeftChild() {
            return (this == parent.leftChild);
        }

        public boolean isRightChild() {
            return (this == parent.rightChild);
        }

        public int leftHeight() {
            if (hasLeftChild())
                return (1 + leftChild.height);
            else
                return 0;
        }

        public int rightHeight() {
            if (hasRightChild())
                return (1 + rightChild.height);
            else
                return 0;
        }

        public int height() {
            return height;
        }

        NodeProxy data;
        Node parent;
        Node leftChild;
        Node rightChild;
        int height;
    }

}
