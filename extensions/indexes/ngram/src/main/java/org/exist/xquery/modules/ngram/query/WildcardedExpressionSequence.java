/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.ngram.query;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.EmptyNodeSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.ngram.NGramIndexWorker;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.modules.ngram.utils.NodeProxies;
import org.exist.xquery.modules.ngram.utils.NodeSets;

public class WildcardedExpressionSequence implements EvaluatableExpression {

    /**
     *
     */
    private final List<WildcardedExpression> expressions;
    private static Logger LOG = LogManager.getLogger(WildcardedExpressionSequence.class);

    public WildcardedExpressionSequence(final List<WildcardedExpression> expressions) {

        this.expressions = new ArrayList<>(expressions.size());

        WildcardedExpression currentExpression = expressions.removeFirst();

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
        if (!expressions.isEmpty() && expressions.getFirst() instanceof StartAnchor) {
            startAnchorPresent = true;
            expressions.removeFirst();
        }

        Wildcard leadingWildcard = null;
        if (!expressions.isEmpty() && expressions.getFirst() instanceof Wildcard)
            leadingWildcard = (Wildcard) expressions.removeFirst();

        boolean endAnchorPresent = false;
        if (!expressions.isEmpty() && expressions.getLast() instanceof EndAnchor) {
            endAnchorPresent = true;
            expressions.removeLast();
        }

        Wildcard trailingWildcard = null;
        if (!expressions.isEmpty() && expressions.getLast() instanceof Wildcard)
            trailingWildcard = (Wildcard) expressions.removeLast();

        while (expressions.size() >= 3) {
            formEvaluatableTriples(expressionId);
        }

        if (expressions.isEmpty())
            return new EmptyNodeSet();
        // TODO: Should probably return nodes the satisfying the size constraint when wildcards are present

        if (expressions.size() != 1 || !(expressions.getFirst() instanceof EvaluatableExpression)) { // Should not happen.
            LOG.error("Expression {} could not be evaluated", toString());
            throw new XPathException((Expression) null, "Could not evaluate wildcarded query.");
        }

        LOG.trace("Evaluating expression {}", toString());
        NodeSet result = ((EvaluatableExpression) expressions.getFirst()).eval(index, docs, qnames, nodeSet, axis,
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

    private NodeSet expandMatchesForward(final Wildcard trailingWildcard, final NodeSet nodes, final int expressionId) throws XPathException {
        return NodeSets.transformNodes(nodes, proxy ->
                NodeProxies.transformOwnMatches(
                        proxy,
                        match -> match.expandForward(trailingWildcard.minimumLength, trailingWildcard.maximumLength, proxy.getNodeValue().length()),
                        expressionId
                )
        );
    }

    private NodeSet expandMatchesBackward(final Wildcard leadingWildcard, final NodeSet nodes, final int expressionId) throws XPathException {
        return NodeSets.transformNodes(nodes, proxy ->
                NodeProxies.transformOwnMatches(
                        proxy,
                        match -> match.expandBackward(leadingWildcard.minimumLength, leadingWildcard.maximumLength),
                        expressionId
                )
        );
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
            expressions.addFirst(triple);
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
