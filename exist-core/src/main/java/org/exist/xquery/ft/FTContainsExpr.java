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
package org.exist.xquery.ft;

import org.exist.xquery.AbstractExpression;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * W3C XQuery and XPath Full Text 3.0 — FTContainsExpr.
 *
 * <pre>FTContainsExpr ::= StringConcatExpr ( "contains" "text" FTSelection FTIgnoreOption? )?</pre>
 *
 * Evaluates whether the string value of the left-hand expression, after
 * tokenization, matches the FTSelection. Returns xs:boolean.
 *
 * @see <a href="https://www.w3.org/TR/xpath-full-text-30/#ftcontains">XQFT 3.0 §2.1</a>
 */
public class FTContainsExpr extends AbstractExpression {

    private Expression source;
    private FTSelection ftSelection;
    private Expression ignoreExpr;

    // Cached URI maps — captured during analyze() to avoid reading from
    // context attributes during eval() (context may be reset concurrently)
    private Map<String, Path> cachedStopWordURIMap;
    private Map<String, Path> cachedThesaurusURIMap;

    public FTContainsExpr(final XQueryContext context) {
        super(context);
    }

    public void setSearchSource(final Expression source) {
        this.source = source;
    }

    public Expression getSearchSource() {
        return source;
    }

    public void setFTSelection(final FTSelection ftSelection) {
        this.ftSelection = ftSelection;
    }

    public FTSelection getFTSelection() {
        return ftSelection;
    }

    public void setIgnoreExpr(final Expression ignoreExpr) {
        this.ignoreExpr = ignoreExpr;
    }

    public Expression getIgnoreExpr() {
        return ignoreExpr;
    }

    @Override
    public int getDependencies() {
        // The source expression (left-hand side of "contains text") is always
        // evaluated against the context item, so we must report CONTEXT_ITEM
        // dependency. Without this, Predicate.evalPredicate may pass null
        // as the context sequence, causing XPDY0002 errors on step expressions.
        return source.getDependencies() | Dependency.CONTEXT_ITEM;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        source.analyze(contextInfo);
        ftSelection.analyze(contextInfo);
        if (ignoreExpr != null) {
            ignoreExpr.analyze(contextInfo);
        }
        // Cache URI maps from context attributes at analyze time.
        // Reading them during eval() is unreliable because context.reset()
        // (called between test executions in the XQTS runner) clears attributes.
        cachedStopWordURIMap = (Map<String, Path>) context.getAttribute("ft.stopWordURIMap");
        cachedThesaurusURIMap = (Map<String, Path>) context.getAttribute("ft.thesaurusURIMap");
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        final Sequence effectiveContext = contextItem != null ? contextItem.toSequence() : contextSequence;
        final Sequence sourceSeq = source.eval(effectiveContext, null);
        // Per XQFT 3.0 §2.1: empty source -> no text to search -> false.
        if (sourceSeq.isEmpty()) {
            return BooleanValue.FALSE;
        }

        final Set<Node> ignoredNodes = collectIgnoredNodes(effectiveContext);

        // Per XQFT 3.0 §2.1: source items are tested independently; the overall
        // expression is true if ANY item matches.
        for (int i = 0; i < sourceSeq.getItemCount(); i++) {
            if (matchSourceItem(sourceSeq.itemAt(i), ignoredNodes, contextSequence)) {
                return BooleanValue.TRUE;
            }
        }
        return BooleanValue.FALSE;
    }

    /**
     * Evaluate the FTIgnoreOption ('without content') expression if present and
     * return the set of nodes to skip. Returns {@code null} when no ignore
     * expression is declared and an empty set when the expression evaluates to
     * the empty sequence. XPTY0004 is raised for non-node items per XQFT 3.0 §3.7.
     */
    private Set<Node> collectIgnoredNodes(final Sequence effectiveContext) throws XPathException {
        if (ignoreExpr == null) {
            return null;
        }
        final Sequence ignoredSeq = ignoreExpr.eval(effectiveContext, null);
        if (ignoredSeq.isEmpty()) {
            return null;
        }
        final Set<Node> ignored = new HashSet<>();
        for (int i = 0; i < ignoredSeq.getItemCount(); i++) {
            final Item item = ignoredSeq.itemAt(i);
            if (!Type.subTypeOf(item.getType(), Type.NODE)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "FTIgnoreOption 'without content' expression must evaluate to nodes, got: "
                                + Type.getTypeName(item.getType()));
            }
            if (item instanceof Node) {
                ignored.add((Node) item);
            }
        }
        return ignored;
    }

    /**
     * Test a single source item against the FT selection. Extracts the source
     * text (honouring any FTIgnoreOption) and runs {@link FTEvaluator#evaluate}
     * with the per-evaluator URI-map caches.
     */
    private boolean matchSourceItem(final Item sourceItem, final Set<Node> ignoredNodes,
                                    final Sequence contextSequence) throws XPathException {
        final SourceText st = extractSourceText(sourceItem, ignoredNodes);
        final FTEvaluator evaluator = new FTEvaluator(st.text, resolveStopWordURIMap(),
                resolveThesaurusURIMap(), st.elementBoundaries);
        // Provide XQuery context for dynamic expressions in positional filters
        // (e.g., window-size expressions that reference the predicate context item).
        evaluator.setContextSequence(contextSequence);
        return evaluator.evaluate(ftSelection, context.getDefaultFTMatchOptions());
    }

    private record SourceText(String text, List<Integer> elementBoundaries) { }

    /**
     * Extract the search-text and the corresponding element-boundary offsets
     * for a single source item. Element-boundary offsets are used downstream
     * for sentence/paragraph detection — the string value itself is unchanged.
     */
    private SourceText extractSourceText(final Item sourceItem, final Set<Node> ignoredNodes) throws XPathException {
        if (ignoredNodes != null && !ignoredNodes.isEmpty() && sourceItem instanceof Node) {
            return new SourceText(extractTextWithoutIgnored((Node) sourceItem, ignoredNodes), null);
        }
        if (sourceItem instanceof Node) {
            final List<Integer> boundaries = new ArrayList<>();
            collectElementBoundaries((Node) sourceItem, boundaries, new int[]{0});
            return new SourceText(sourceItem.getStringValue(), boundaries);
        }
        return new SourceText(sourceItem.getStringValue(), null);
    }

    /**
     * Prefer the analyze-time cached map; fall back to the context attribute
     * (e.g. when this expression was constructed without calling analyze).
     * The cache avoids the race where {@code context.reset()} clears
     * attributes between analyze and eval in concurrent test runner scenarios.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Path> resolveStopWordURIMap() {
        return cachedStopWordURIMap != null
                ? cachedStopWordURIMap
                : (Map<String, Path>) context.getAttribute("ft.stopWordURIMap");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Path> resolveThesaurusURIMap() {
        return cachedThesaurusURIMap != null
                ? cachedThesaurusURIMap
                : (Map<String, Path>) context.getAttribute("ft.thesaurusURIMap");
    }

    @Override
    public int returnsType() {
        return Type.BOOLEAN;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        source.dump(dumper);
        dumper.display(" contains text ");
        ftSelection.dump(dumper);
        if (ignoreExpr != null) {
            dumper.display(" without content ");
            ignoreExpr.dump(dumper);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(source.toString());
        sb.append(" contains text ");
        sb.append(ftSelection.toString());
        if (ignoreExpr != null) {
            sb.append(" without content ");
            sb.append(ignoreExpr.toString());
        }
        return sb.toString();
    }

    /**
     * Extract text content from a DOM node, skipping any descendant nodes
     * that are in the ignored set. This implements XQFT 3.0 §3.7 FTIgnoreOption
     * at the DOM level rather than by string replacement.
     */
    private static String extractTextWithoutIgnored(final Node node, final Set<Node> ignoredNodes) {
        final StringBuilder sb = new StringBuilder();
        collectText(node, ignoredNodes, sb);
        return sb.toString();
    }

    /**
     * Collect character offsets within the string value where element boundaries occur.
     * These offsets are used by FTEvaluator for sentence/paragraph boundary detection
     * without modifying the actual text (which would change tokenization and matching).
     *
     * @param node the DOM node to walk
     * @param boundaries list to collect boundary offsets into
     * @param offset mutable offset tracker (single-element array)
     */
    private static void collectElementBoundaries(final Node node,
                                                  final List<Integer> boundaries,
                                                  final int[] offset) {
        if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
            offset[0] += node.getNodeValue().length();
        } else if (node.getNodeType() == Node.ELEMENT_NODE) {
            final NodeList children = node.getChildNodes();
            if (children != null) {
                for (int i = 0; i < children.getLength(); i++) {
                    final Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        // Record the current offset as an element boundary
                        boundaries.add(offset[0]);
                    }
                    collectElementBoundaries(child, boundaries, offset);
                }
            }
        }
    }

    /**
     * Check if a node is in the ignored set using equals() with linear scan.
     * HashSet.contains() may fail for eXist's DOM nodes where equals() is
     * overridden (comparing document + nodeNumber) but hashCode() isn't,
     * causing hash bucket mismatch.
     */
    private static boolean isIgnored(final Node node, final Set<Node> ignoredNodes) {
        for (final Node ignored : ignoredNodes) {
            if (node.equals(ignored)) {
                return true;
            }
        }
        return false;
    }

    private static void collectText(final Node node, final Set<Node> ignoredNodes,
                                     final StringBuilder sb) {
        if (isIgnored(node, ignoredNodes)) {
            // Replace ignored node's contribution with a space to maintain token boundaries
            sb.append(' ');
            return;
        }
        if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
            sb.append(node.getNodeValue());
        } else {
            final NodeList children = node.getChildNodes();
            if (children != null) {
                for (int i = 0; i < children.getLength(); i++) {
                    collectText(children.item(i), ignoredNodes, sb);
                }
            }
        }
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        source.resetState(postOptimization);
        ftSelection.resetState(postOptimization);
        if (ignoreExpr != null) {
            ignoreExpr.resetState(postOptimization);
        }
    }
}
