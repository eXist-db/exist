package org.exist.xquery.modules.ngram.query;

import java.util.List;

import org.apache.log4j.Logger;
import org.exist.dom.DocumentSet;
import org.exist.dom.Match;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.ngram.NGramIndexWorker;
import org.exist.xquery.XPathException;
import org.exist.xquery.modules.ngram.utils.F;
import org.exist.xquery.modules.ngram.utils.NodeProxies;
import org.exist.xquery.modules.ngram.utils.NodeSets;

public class WildcardedExpressionTriple implements EvaluatableExpression {
    /**
     *
     */
    private final EvaluatableExpression head;
    private final Wildcard wildcard;
    private final EvaluatableExpression tail;

    protected static Logger LOG = Logger.getLogger(WildcardedExpressionTriple.class);


    public WildcardedExpressionTriple(final EvaluatableExpression head,
        final Wildcard wildcard,
        final EvaluatableExpression tail) {
        this.head = head;
        this.wildcard = wildcard;
        this.tail = tail;
    }

    @Override
    public NodeSet eval(
        final NGramIndexWorker index, final DocumentSet docs, final List<QName> qnames, final NodeSet nodeSet,
        final int axis, final int expressionId) throws XPathException {

        final NodeSet headNodes = head.eval(index, docs, qnames, nodeSet, axis, expressionId);
        if (headNodes.isEmpty()) {
            return headNodes;
        }

        final NodeSet tailNodes = tail.eval(index, docs, qnames, nodeSet, axis, expressionId);
        if (tailNodes.isEmpty()) {
            return tailNodes;
        }

        NodeSet result = NodeSets.fmapNodes(headNodes, new F<NodeProxy, NodeProxy>() {

            @Override
            public NodeProxy f(NodeProxy headNode) {
                NodeProxy tailNode = tailNodes.get(headNode);
                if (tailNode != null) {
                    return getMatchingNode(headNode, tailNode, expressionId);
                } else {
                    return null;
                }
            }
        });

        return result;
    }

    private NodeProxy getMatchingNode(NodeProxy headNode, NodeProxy tailNode, final int expressionId) {
        NodeProxy result = null;

        Match match = null;
        boolean found = false;

        for (Match headMatch = headNode.getMatches(); headMatch != null && !found; headMatch = headMatch
            .getNextMatch()) {

            for (Match tailMatch = tailNode.getMatches(); tailMatch != null && !found; tailMatch = tailMatch
                .getNextMatch()) {

                match = headMatch.followedBy(tailMatch, wildcard.getMinimumLength(), wildcard.getMaximumLength());
                found = (match != null);

            }
        }

        // preserve other matches, add new match

        if (found) {
            // Remove own (partial) matches and add new complete match
            NodeProxies.filterMatches(tailNode, new F<Match, Boolean>() {

                @Override
                public Boolean f(Match a) {
                    return a.getContextId() != expressionId;
                }
            });

            tailNode.addMatch(match);
            result = tailNode;
        }


        return result;
    }

    @Override
    public String toString() {
        return "WildcardedExpressionTriple(" + head + ", " + wildcard + ", " + tail + ")";
    }

}