package org.exist.xquery.modules.ngram.utils;

import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.Match;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.NodeSetIterator;
import org.exist.xquery.XPathException;

public final class NodeSets {

    private NodeSets() {
    }

    /**
     * Builds a new NodeSet by applying a function to all NodeProxys of the supplied NodeSet and returning all non-null
     * results.
     * 
     * @param nodes
     *            the NodeSet containig the NodeProys to be transformed
     * @param f
     *            the function to be applied to all NodeProxys in nodes
     * @return a new NodeSet containing the non-null results of f applied to the NodeProxys in nodes
     * @throws XPathException
     */
    public static NodeSet fmapNodes(final NodeSet nodes, final F<NodeProxy, NodeProxy> f) throws XPathException {
        NodeSet result = new ExtArrayNodeSet();
        for (NodeSetIterator iterator = nodes.iterator(); iterator.hasNext();) {
            NodeProxy node = f.f(iterator.next());
            if (node != null)
                result.add(node);
        }
        result.iterate(); // ensure result is ready to use
        return result;
    }

    public static NodeSet getNodesMatchingAtStart(final NodeSet nodes, final int expressionId) throws XPathException {
        return fmapNodes(nodes, new F<NodeProxy, NodeProxy>() {

            @Override
            public NodeProxy f(NodeProxy a) {
                return NodeProxies.fmapOwnMatches(a, new F<Match, Match>() {

                    @Override
                    public Match f(Match a) {
                        return a.filterOffsetsStartingAt(0);
                    }
                }, expressionId);
            }
        });
    }

    public static NodeSet getNodesMatchingAtEnd(final NodeSet nodes, final int expressionId) throws XPathException {
        return fmapNodes(nodes, new F<NodeProxy, NodeProxy>() {

            @Override
            public NodeProxy f(NodeProxy a) {
                final int len = a.getNodeValue().length();
                return NodeProxies.fmapOwnMatches(a, new F<Match, Match>() {

                    @Override
                    public Match f(Match a) {
                        return a.filterOffsetsEndingAt(len);
                    }
                }, expressionId);
            }
        });
    }

}
