package org.exist.xquery.value;

import org.exist.dom.memtree.NodeImpl;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;


public interface MemoryNodeSet extends Sequence {

    MemoryNodeSet EMPTY = new ValueSequence(1);

    Sequence getAttributes(NodeTest test) throws XPathException;

    Sequence getDescendantAttributes(NodeTest test) throws XPathException;

    Sequence getChildren(NodeTest test) throws XPathException;

    Sequence getDescendants(boolean includeSelf, NodeTest test) throws XPathException;

    Sequence getAncestors(boolean includeSelf, NodeTest test) throws XPathException;

    Sequence getParents(NodeTest test) throws XPathException;

    Sequence getSelf(NodeTest test) throws XPathException;

    Sequence getPrecedingSiblings(NodeTest test) throws XPathException;

    Sequence getPreceding(NodeTest test, int position) throws XPathException;

    Sequence getFollowingSiblings(NodeTest test) throws XPathException;

    Sequence getFollowing(NodeTest test, int position) throws XPathException;

    Sequence getChildrenForParent(NodeImpl parent);

    Sequence selectDescendants(MemoryNodeSet descendants);

    Sequence selectChildren(MemoryNodeSet children);

    int size();

    NodeImpl get(int which);

    boolean matchAttributes(NodeTest test) throws XPathException;

    boolean matchDescendantAttributes(NodeTest test) throws XPathException;

    boolean matchChildren(NodeTest test) throws XPathException;

//    public Sequence matchDescendants(boolean includeSelf, NodeTest test) throws XPathException;
//
//    public Sequence matchAncestors(boolean includeSelf, NodeTest test) throws XPathException;
//
//    public Sequence matchParents(NodeTest test) throws XPathException;

    boolean matchSelf(NodeTest test) throws XPathException;

//    public Sequence matchPrecedingSiblings(NodeTest test) throws XPathException;
//
//    public Sequence matchPreceding(NodeTest test, int position) throws XPathException;
//
//    public Sequence matchFollowingSiblings(NodeTest test) throws XPathException;
//    
//    public Sequence matchFollowing(NodeTest test, int position) throws XPathException;
//
//    public Sequence matchChildrenForParent(NodeImpl parent);
}
