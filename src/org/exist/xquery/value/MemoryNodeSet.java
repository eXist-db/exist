package org.exist.xquery.value;

import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.memtree.NodeImpl;


public interface MemoryNodeSet extends Sequence {

    public final static MemoryNodeSet EMPTY = new ValueSequence(1);
    
    public Sequence getAttributes(NodeTest test) throws XPathException;

    public Sequence getDescendantAttributes(NodeTest test) throws XPathException;

    public Sequence getChildren(NodeTest test) throws XPathException;

    public Sequence getDescendants(boolean includeSelf, NodeTest test) throws XPathException;

    public Sequence getAncestors(boolean includeSelf, NodeTest test) throws XPathException;

    public Sequence getParents(NodeTest test) throws XPathException;

    public Sequence getSelf(NodeTest test) throws XPathException;

    public Sequence getPrecedingSiblings(NodeTest test) throws XPathException;

    public Sequence getPreceding(NodeTest test, int position) throws XPathException;

    public Sequence getFollowingSiblings(NodeTest test) throws XPathException;
    
    public Sequence getFollowing(NodeTest test, int position) throws XPathException;

    public Sequence getChildrenForParent(NodeImpl parent);

    public Sequence selectDescendants(MemoryNodeSet descendants);

    public Sequence selectChildren(MemoryNodeSet children);
    
    public int size();

    public NodeImpl get(int which);
}
