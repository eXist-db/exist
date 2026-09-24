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
import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.ngram.NGramIndexWorker;
import org.exist.indexing.ngram.NGramMatch;
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
            if (currentExpression instanceof MergeableExpression mergeableExpression
                && mergeableExpression.mergeableWith(expression)) {
                currentExpression = mergeableExpression.mergeWith(expression);
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

        if (expressions.isEmpty()) {
            // The pattern was made up of nothing but wildcards and/or anchors (e.g. ".", ".*", "^.+$"):
            // there is no literal text left to seed an ngram index lookup with, so fall back to
            // evaluating the wildcard's length constraint directly against the candidate nodes.
            final Wildcard soleWildcard = leadingWildcard != null ? leadingWildcard : trailingWildcard;
            if (soleWildcard == null || nodeSet == null) {
                return new EmptyNodeSet();
            }
            return matchWildcardOnly(soleWildcard, startAnchorPresent, endAnchorPresent, nodeSet, expressionId);
        }

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

    /**
     * Matches a wildcard-only pattern (no literal text, e.g. ".", ".*", "^.+$") against each candidate
     * node directly, since there is no term to look up in the ngram index.
     *
     * <p>Length is compared in Unicode codepoints, not UTF-16 code units, so that a lone supplementary
     * character (a surrogate pair, i.e. {@code String.length() == 2}) is correctly counted as a single
     * "character" and not split across two wildcard positions.
     *
     * @param wildcard the (possibly merged) length constraint of the pattern
     * @param startAnchorPresent whether the pattern is anchored at the start ('^')
     * @param endAnchorPresent whether the pattern is anchored at the end ('$')
     * @param nodeSet the candidate nodes to check
     * @param expressionId the context id to tag the synthesized matches with
     *
     * @return the nodes whose content satisfies the wildcard's length constraint, each with a match
     *         covering the qualifying span attached
     */
    private static NodeSet matchWildcardOnly(
            final Wildcard wildcard, final boolean startAnchorPresent, final boolean endAnchorPresent,
            final NodeSet nodeSet, final int expressionId) {
        final NodeSet result = new ExtArrayNodeSet();
        for (final NodeProxy proxy : nodeSet) {
            final String value = proxy.getNodeValue();
            final int codepointLength = value.codePointCount(0, value.length());
            if (codepointLength < wildcard.getMinimumLength()) {
                continue;
            }
            if (startAnchorPresent && endAnchorPresent && codepointLength > wildcard.getMaximumLength()) {
                continue;
            }

            final int matchCodepoints = (startAnchorPresent && endAnchorPresent)
                    ? codepointLength
                    : Math.min(codepointLength, wildcard.getMaximumLength());
            final int matchStartCodepoint = (endAnchorPresent && !startAnchorPresent)
                    ? codepointLength - matchCodepoints
                    : 0;

            final int startOffset = value.offsetByCodePoints(0, matchStartCodepoint);
            final int endOffset = value.offsetByCodePoints(startOffset, matchCodepoints);

            final NGramMatch match = new NGramMatch(expressionId, proxy.getNodeId(), value.substring(startOffset, endOffset));
            match.addOffset(startOffset, endOffset - startOffset);
            proxy.addMatch(match);
            result.add(proxy);
        }
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
        WildcardedExpression first = expressions.getFirst();
        WildcardedExpression second = expressions.get(1);
        WildcardedExpression third = expressions.get(2);

        if (first instanceof EvaluatableExpression expression && second instanceof Wildcard wildcard
            && third instanceof EvaluatableExpression expression1) {
            WildcardedExpressionTriple triple = new WildcardedExpressionTriple(expression,
                wildcard, expression1);
            expressions.subList(0, 3).clear();
            expressions.addFirst(triple);
        } else {
            throw new IllegalArgumentException("Could not form evaluatable triples at the beginning of "
                + this);
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
