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
 */
package org.exist.xquery.modules.ngram.query;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.exist.dom.DocumentSet;
import org.exist.dom.EmptyNodeSet;
import org.exist.dom.Match;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.ngram.NGramIndexWorker;
import org.exist.xquery.XPathException;
import org.exist.xquery.modules.ngram.utils.F;
import org.exist.xquery.modules.ngram.utils.NodeProxies;
import org.exist.xquery.modules.ngram.utils.NodeSets;

public class WildcardedExpressionSequence implements EvaluatableExpression {

    /**
     *
     */
    private final List<WildcardedExpression> expressions;
    private static Logger LOG = Logger.getLogger(WildcardedExpressionSequence.class);

    public WildcardedExpressionSequence(final List<WildcardedExpression> expressions) {

        this.expressions = new ArrayList<WildcardedExpression>(expressions.size());

        WildcardedExpression currentExpression = expressions.remove(0);

        for (WildcardedExpression expression : expressions) {
            if (currentExpression instanceof MergeableExpression
                && ((MergeableExpression) currentExpression).mergeableWith(expression)) {
                currentExpression = ((MergeableExpression) currentExpression).mergeWith(expression);
            } else {
                this.expressions.add(currentExpression);
                currentExpression = expression;
            }
        }

        this.expressions.add(currentExpression);

    }

    @Override
    public NodeSet eval(
        final NGramIndexWorker index, final DocumentSet docs, final List<QName> qnames, final NodeSet nodeSet,
        final int axis, final int expressionId) throws XPathException {

        boolean startAnchorPresent = false;
        if (!expressions.isEmpty() && expressions.get(0) instanceof StartAnchor) {
            startAnchorPresent = true;
            expressions.remove(0);
        }

        Wildcard leadingWildcard = null;
        if (!expressions.isEmpty() && expressions.get(0) instanceof Wildcard)
            leadingWildcard = (Wildcard) expressions.remove(0);

        boolean endAnchorPresent = false;
        if (!expressions.isEmpty() && expressions.get(expressions.size() - 1) instanceof EndAnchor) {
            endAnchorPresent = true;
            expressions.remove(expressions.size() - 1);
        }

        Wildcard trailingWildcard = null;
        if (!expressions.isEmpty() && expressions.get(expressions.size() - 1) instanceof Wildcard)
            trailingWildcard = (Wildcard) expressions.remove(expressions.size() - 1);

        while (expressions.size() >= 3) {
            formEvaluatableTriples(expressionId);
        }

        if (expressions.isEmpty())
            return new EmptyNodeSet();
        // TODO: Should probably return nodes the satisfying the size constraint when wildcards are present

        if (expressions.size() != 1 || !(expressions.get(0) instanceof EvaluatableExpression)) { // Should not happen.
            LOG.error("Expression " + toString() + " could not be evaluated");
            throw new XPathException("Could not evaluate wildcarded query.");
        }

        LOG.trace("Evaluating expression " + toString());
        NodeSet result = ((EvaluatableExpression) expressions.get(0)).eval(index, docs, qnames, nodeSet, axis,
            expressionId);

        if (leadingWildcard != null)
            result = expandMatchesBackward(leadingWildcard, result, expressionId);
        if (startAnchorPresent)
            result = NodeSets.getNodesMatchingAtStart(result, expressionId);

        if (trailingWildcard != null)
            result = expandMatchesForward(trailingWildcard, result, expressionId);
        if (endAnchorPresent)
            result = NodeSets.getNodesMatchingAtEnd(result, expressionId);

        return result;
    }

    private NodeSet expandMatchesForward(final Wildcard trailingWildcard, final NodeSet nodes, final int expressionId)
        throws XPathException {
        return NodeSets.fmapNodes(nodes, new F<NodeProxy, NodeProxy>() {

            @Override
            public NodeProxy f(NodeProxy node) {
                final int nodeLength = node.getNodeValue().length();
                return NodeProxies.fmapOwnMatches(node, new F<Match, Match>() {

                    @Override
                    public Match f(Match a) {
                        return a.expandForward(trailingWildcard.minimumLength, trailingWildcard.maximumLength,
                            nodeLength);
                    }
                }, expressionId);
            }
        });
    }

    private NodeSet expandMatchesBackward(final Wildcard leadingWildcard, final NodeSet nodes, final int expressionId)
        throws XPathException {
        return NodeSets.fmapNodes(nodes, new F<NodeProxy, NodeProxy>() {

            @Override
            public NodeProxy f(NodeProxy node) {
                return NodeProxies.fmapOwnMatches(node, new F<Match, Match>() {

                    @Override
                    public Match f(Match a) {
                        return a.expandBackward(leadingWildcard.minimumLength, leadingWildcard.maximumLength);
                    }
                }, expressionId);
            }
        });
    }



    /**
     *
     */
    private void formEvaluatableTriples(final int expressionId) {
        WildcardedExpression first = expressions.get(0);
        WildcardedExpression second = expressions.get(1);
        WildcardedExpression third = expressions.get(2);

        if (first instanceof EvaluatableExpression && second instanceof Wildcard
            && third instanceof EvaluatableExpression) {
            WildcardedExpressionTriple triple = new WildcardedExpressionTriple((EvaluatableExpression) first,
                (Wildcard) second, (EvaluatableExpression) third);
            expressions.subList(0, 3).clear();
            expressions.add(0, triple);
        } else {
            throw new IllegalArgumentException("Could not form evaluatable triples at the beginning of "
                + toString());
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("WildcardedExpressionSequence(");
        for (WildcardedExpression expression : expressions) {
            builder.append(expression.toString());
            builder.append(", ");
        }
        builder.append(")");
        return builder.toString();
    }

}