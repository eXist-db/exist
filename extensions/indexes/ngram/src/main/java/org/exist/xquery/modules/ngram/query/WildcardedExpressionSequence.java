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
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.EmptyNodeSet;
import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.ngram.NGramIndexWorker;
import org.exist.indexing.ngram.NGramMatch;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.modules.ngram.utils.NodeProxies;
import org.exist.xquery.modules.ngram.utils.NodeSets;

import javax.annotation.Nullable;

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
            // nothing but wildcards and anchors (".", ".*", "^.+$"): no text to look up, so check the length
            // of every indexed node. Consecutive wildcards are merged, so the one wildcard is the leading one.
            if (leadingWildcard == null) {
                return new EmptyNodeSet();
            }
            return matchWildcardOnly(index, docs, qnames, nodeSet, axis, leadingWildcard, startAnchorPresent,
                    endAnchorPresent, expressionId);
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
     * Matches a wildcard-only pattern (".", ".*", "^.+$") against the indexed nodes, as {@link FixedString}
     * would match a term: on the ancestor axis with a context set, the context node an indexed node is in
     * gets the match; otherwise the indexed node. Lengths are counted in code points, so a supplementary
     * character is one character.
     *
     * @param index the ngram index
     * @param docs the documents to look in
     * @param qnames the indexed qnames to look at, or null for all
     * @param contextSet the context nodes, or null
     * @param axis {@link NodeSet#ANCESTOR} to return the context nodes
     * @param wildcard the pattern's length constraint
     * @param startAnchorPresent whether the pattern is anchored at the start ('^')
     * @param endAnchorPresent whether the pattern is anchored at the end ('$')
     * @param expressionId the context id of the matches
     * @return the matching nodes, each with a match on the span of the indexed node that the wildcard covers
     */
    private static NodeSet matchWildcardOnly(
            final NGramIndexWorker index, final DocumentSet docs, final List<QName> qnames,
            @Nullable final NodeSet contextSet, final int axis, final Wildcard wildcard,
            final boolean startAnchorPresent, final boolean endAnchorPresent, final int expressionId)
            throws XPathException {
        final boolean returnAncestor = contextSet != null && axis == NodeSet.ANCESTOR;
        final NodeSet result = new ExtArrayNodeSet();
        for (final NodeProxy indexed : index.indexedNodes(docs, qnames)) {
            final NodeProxy target = returnAncestor
                    ? contextSet.parentWithChild(indexed, false, true, NodeProxy.UNKNOWN_NODE_LEVEL)
                    : indexed;
            if (target == null) {
                continue;
            }
            final String value = indexed.getNodeValue();
            final int length = value.codePointCount(0, value.length());
            if (length < wildcard.getMinimumLength()
                    || (startAnchorPresent && endAnchorPresent && length > wildcard.getMaximumLength())) {
                continue;
            }
            // the longest span the wildcard covers: a suffix when anchored at the end only, else a prefix
            final int matchLength = Math.min(length, wildcard.getMaximumLength());
            final int startOffset = endAnchorPresent && !startAnchorPresent
                    ? value.offsetByCodePoints(value.length(), -matchLength) : 0;
            final int endOffset = value.offsetByCodePoints(startOffset, matchLength);
            final NGramMatch match = new NGramMatch(expressionId, indexed.getNodeId(), value.substring(startOffset, endOffset));
            match.addOffset(startOffset, endOffset - startOffset);
            target.addMatch(match);
            result.add(target);
        }
        result.iterate(); // ensure result is ready to use
        return result;
    }

    private NodeSet expandMatchesForward(final Wildcard trailingWildcard, final NodeSet nodes, final int expressionId) throws XPathException {
        return NodeSets.transformNodes(nodes, proxy -> {
            final Function<Match, String> values = NodeProxies.matchedValues(proxy);
            return NodeProxies.transformOwnMatches(
                    proxy,
                    match -> match.expandForward(trailingWildcard.minimumLength, trailingWildcard.maximumLength, values.apply(match)),
                    expressionId
            );
        });
    }

    private NodeSet expandMatchesBackward(final Wildcard leadingWildcard, final NodeSet nodes, final int expressionId) throws XPathException {
        return NodeSets.transformNodes(nodes, proxy -> {
            final Function<Match, String> values = NodeProxies.matchedValues(proxy);
            return NodeProxies.transformOwnMatches(
                    proxy,
                    match -> match.expandBackward(leadingWildcard.minimumLength, leadingWildcard.maximumLength, values.apply(match)),
                    expressionId
            );
        });
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
