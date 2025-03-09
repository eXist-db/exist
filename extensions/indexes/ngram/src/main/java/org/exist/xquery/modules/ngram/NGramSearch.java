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
package org.exist.xquery.modules.ngram;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.EmptyNodeSet;
import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.ngram.NGramIndex;
import org.exist.indexing.ngram.NGramIndexWorker;
import org.exist.storage.ElementValue;
import org.exist.xquery.*;
import org.exist.xquery.modules.ngram.query.AlternativeStrings;
import org.exist.xquery.modules.ngram.query.EmptyExpression;
import org.exist.xquery.modules.ngram.query.EndAnchor;
import org.exist.xquery.modules.ngram.query.EvaluatableExpression;
import org.exist.xquery.modules.ngram.query.FixedString;
import org.exist.xquery.modules.ngram.query.StartAnchor;
import org.exist.xquery.modules.ngram.query.Wildcard;
import org.exist.xquery.modules.ngram.query.WildcardedExpression;
import org.exist.xquery.modules.ngram.query.WildcardedExpressionSequence;
import org.exist.xquery.modules.ngram.utils.NodeProxies;
import org.exist.xquery.modules.ngram.utils.NodeSets;
import org.exist.xquery.util.Error;
import org.exist.xquery.value.*;

public class NGramSearch extends Function implements Optimizable {

    private static final String INTERVAL_QUALIFIER_PATTERN = "\\{([0-9]+),([0-9]+)\\}";

    private static final String SEARCH_DESCRIPTION = "Searches the given $queryString in the index "
        + "defined on the input node set $nodes. "
        + "String comparison is case insensitive. Nodes need to have an ngram index to be searched.";

    private static final String WILDCARD_PATTERN_DESCRIPTION = "The string to search for."
        + "A full stop, '.', (not between brackets), without any qualifiers: Matches a single arbitrary character."
        + "A full stop, '.', (not between brackets), immediately followed by a single question mark, '?': Matches either no characters or one character."
        + "A full stop, '.', (not between brackets), immediately followed by a single asterisk, '*': Matches zero or more characters."
        + "A full stop, '.', (not between brackets), immediately followed by a single plus sign, '+': Matches one or more characters."
        + "A full stop, '.', immediately followed by a sequence of characters that matches the regular expression {[0-9]+,[0-9]+}: Matches a number of characters, where the number is no less than the number represented by the series of digits before the comma, and no greater than the number represented by the series of digits following the comma."
        + "An  expression  \"[…]\"  matches a single character, namely any of the characters"
        + "enclosed by the brackets.  The string enclosed by the brackets cannot be empty; "
        + "therefore ']' can be allowed between  the brackets, provided that it is the first character."
        + "(Thus, \"[][?]\" matches the three characters '[', ']' and '?'.)"
        + "A circumflex accent, '^', at the start of the search string matches the start of the element content."
        + "A dollar sign, '$', at the end of the search string matches the end of the element content."
        + "One can remove the special meaning of any character mentioned above by preceding them by a backslash."
        + "Between brackets these characters stand for themselves.  Thus, \"[[?*\\]\" matches"
        + "the four characters '[', '?', '*' and '\\'."
        + "'?', '*', '+' and character sequences matching the regular expression {[0-9]+,[0-9]+} not immediately preceeded by an unescaped period, '.', stand for themselves."
        + "'^' and '$' not at the very beginning or end of the search string, respectively, stand for themselves.";

    protected static final Logger LOG = LogManager.getLogger(NGramSearch.class);

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(new QName("contains", NGramModule.NAMESPACE_URI, NGramModule.PREFIX),
            "Similar to the standard XQuery fn:contains function, but based on the NGram index. " + SEARCH_DESCRIPTION
                + "The string may appear at any position within the node content.", new SequenceType[] {
                new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                    "The input node set to search"),
                new FunctionParameterSequenceType("queryString", Type.STRING, Cardinality.ZERO_OR_ONE,
                    "The exact string to search for") }, new FunctionReturnSequenceType(Type.NODE,
                Cardinality.ZERO_OR_MORE, "a set of nodes from the input node set $nodes containing the query string "
                    + "or the empty sequence")),
        new FunctionSignature(new QName("ends-with", NGramModule.NAMESPACE_URI, NGramModule.PREFIX),
            "Similar to the standard XQuery fn:ends-with function, but based on the NGram index. " + SEARCH_DESCRIPTION
                + "The string has to appear at the end of the node's content.", new SequenceType[] {
                new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                    "The input node set to search"),
                new FunctionParameterSequenceType("queryString", Type.STRING, Cardinality.ZERO_OR_ONE,
                    "The exact string to search for") }, new FunctionReturnSequenceType(Type.NODE,
                Cardinality.ZERO_OR_MORE, "a set of nodes from the input node set $nodes ending with the query string "
                    + "or the empty sequence")),
        new FunctionSignature(new QName("starts-with", NGramModule.NAMESPACE_URI, NGramModule.PREFIX),
            "Similar to the standard XQuery fn:starts-with function, but based on the NGram index. "
                + SEARCH_DESCRIPTION + "The string has to appear at the start of the node's content.",
            new SequenceType[] {
                new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                    "The input node set to search"),
                new FunctionParameterSequenceType("queryString", Type.STRING, Cardinality.ZERO_OR_ONE,
                    "The exact string to search for") }, new FunctionReturnSequenceType(Type.NODE,
                Cardinality.ZERO_OR_MORE,
                "a set of nodes from the input node set $nodes starting with the query string "
                    + "or the empty sequence")),
        new FunctionSignature(new QName("wildcard-contains", NGramModule.NAMESPACE_URI, NGramModule.PREFIX),
            "Similar to the standard XQuery fn:matches function, but based on the NGram index and "
                + "allowing wildcards in the query string. " + SEARCH_DESCRIPTION
                + "The string has to match the whole node's content.", new SequenceType[] {
                new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                    "The input node set to search"),
                new FunctionParameterSequenceType("queryString", Type.STRING, Cardinality.ZERO_OR_ONE,
                    WILDCARD_PATTERN_DESCRIPTION) }, new FunctionReturnSequenceType(Type.NODE,
                Cardinality.ZERO_OR_MORE, "a set of nodes from the input node set $nodes matching the query string "
                    + "or the empty sequence")) };

    private LocationStep contextStep = null;
    protected QName contextQName = null;
    protected int axis = Constants.UNKNOWN_AXIS;
    private NodeSet preselectResult = null;
    protected boolean optimizeSelf = false;
    protected boolean optimizeChild = false;
    
    public NGramSearch(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void setArguments(List<Expression> arguments) throws XPathException {
        steps.clear();
        Expression path = arguments.get(0);
        steps.add(path);

        Expression arg = arguments.get(1);
        arg = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_ONE, arg, new org.exist.xquery.util.Error(
            Error.FUNC_PARAM_CARDINALITY, "2", getSignature()));
        if(!Type.subTypeOf(arg.returnsType(), Type.ANY_ATOMIC_TYPE))
            arg = new Atomize(context, arg);
        steps.add(arg);
    }

    /*
     * (non-Javadoc)
     *
    * @see org.exist.xquery.PathExpr#analyze(org.exist.xquery.Expression)
    */
    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        List<LocationStep> steps = BasicExpressionVisitor.findLocationSteps(getArgument(0));
        if (!steps.isEmpty()) {
            LocationStep firstStep = steps.getFirst();
            LocationStep lastStep = steps.getLast();
            if (firstStep != null && steps.size() == 1 && firstStep.getAxis() == Constants.SELF_AXIS) {
                final Expression outerExpr = contextInfo.getContextStep();
                if (outerExpr instanceof LocationStep outerStep) {
                    final NodeTest test = outerStep.getTest();
                    if (!test.isWildcardTest() && test.getName() != null) {
                        if (outerStep.getAxis() == Constants.ATTRIBUTE_AXIS
                            || outerStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS) {
                            contextQName = new QName(test.getName(), ElementValue.ATTRIBUTE);
                        } else {
                            contextQName = new QName(test.getName());
                        }
                        contextStep = firstStep;
                        axis = outerStep.getAxis();
                        optimizeSelf = true;
                    }
                }
            } else if (lastStep != null && firstStep != null) {
                final NodeTest test = lastStep.getTest();
                if (!test.isWildcardTest() && test.getName() != null) {
                    if (lastStep.getAxis() == Constants.ATTRIBUTE_AXIS
                        || lastStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS) {
                        contextQName = new QName(test.getName(), ElementValue.ATTRIBUTE);
                    } else {
                        contextQName = new QName(test.getName());
                    }
                    axis = firstStep.getAxis();
                    optimizeChild = steps.size() == 1 &&
                        (axis == Constants.CHILD_AXIS || axis == Constants.ATTRIBUTE_AXIS);
                    contextStep = lastStep;
                }
            }
        }
    }

    @Override
    public Sequence canOptimizeSequence(final Sequence contextSequence) {
        if (contextQName != null) {
            return contextSequence;
        } else {
            return Sequence.EMPTY_SEQUENCE;
        }
    }

    @Override
    public boolean optimizeOnSelf() {
        return optimizeSelf;
    }

    public boolean optimizeOnChild() {
        return optimizeChild;
    }

    @Override
    public int getOptimizeAxis() {
        return axis;
    }

    @Override
    public NodeSet preSelect(Sequence contextSequence, boolean useContext) throws XPathException {
        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;

        long start = System.currentTimeMillis();

        NGramIndexWorker index = (NGramIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(
            NGramIndex.ID);
        DocumentSet docs = contextSequence.getDocumentSet();
        String key = getArgument(1).eval(contextSequence, null).getStringValue();
        List<QName> qnames = new ArrayList<>(1);
        qnames.add(contextQName);
        preselectResult = processMatches(index, docs, qnames, key, useContext ? contextSequence.toNodeSet() : null,
            NodeSet.DESCENDANT);

        if( context.getProfiler().traceFunctions() ) {
            // report index use
            context.getProfiler().traceIndexUsage( context, "ngram", this, PerformanceStats.IndexOptimizationLevel.OPTIMIZED, System.currentTimeMillis() - start );
        }
        return preselectResult;
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (contextItem != null)
			contextSequence = contextItem.toSequence();

        NodeSet result;
        if (preselectResult == null) {
            Sequence input = getArgument(0).eval(contextSequence, contextItem);
            if (input.isEmpty())
                result = NodeSet.EMPTY_SET;
            else {
                long start = System.currentTimeMillis();
                NodeSet inNodes = input.toNodeSet();
                DocumentSet docs = inNodes.getDocumentSet();
                NGramIndexWorker index = (NGramIndexWorker) context.getBroker().getIndexController()
                    .getWorkerByIndexId(NGramIndex.ID);
                //Alternate design
                // NGramIndexWorker index =
                // (NGramIndexWorker)context.getBroker().getBrokerPool().getIndexManager().getIndexById(NGramIndex.ID).getWorker();

                String key = getArgument(1).eval(contextSequence, contextItem).getStringValue();
                List<QName> qnames = null;
                if (contextQName != null) {
                    qnames = new ArrayList<>(1);
                    qnames.add(contextQName);
                }
                result = processMatches(index, docs, qnames, key, inNodes, NodeSet.ANCESTOR);
                if( context.getProfiler().traceFunctions() ) {
                    // report index use
                    context.getProfiler().traceIndexUsage( context, "ngram", this, PerformanceStats.IndexOptimizationLevel.BASIC, System.currentTimeMillis() - start );
                }
            }
        } else {
            contextStep.setPreloadedData(contextSequence.getDocumentSet(), preselectResult);
            result = getArgument(0).eval(contextSequence, null).toNodeSet();
        }
        return result;
    }

    private String getLocalName() {
        return getSignature().getName().getLocalPart();
    }

    private NodeSet processMatches(
        NGramIndexWorker index, DocumentSet docs, List<QName> qnames, String query, NodeSet nodeSet, int axis)
        throws XPathException {

        EvaluatableExpression parsedQuery = null;

        if ("wildcard-contains".equals(getLocalName()))
            parsedQuery = parseQuery(query);
            	else
            parsedQuery = new FixedString(this, query);

        LOG.debug("Parsed Query: {}", parsedQuery);
        NodeSet result = parsedQuery.eval(index, docs, qnames, nodeSet, axis, this
                .getExpressionId());

        if (getLocalName().startsWith("starts-with"))
            result = NodeSets.getNodesMatchingAtStart(result, getExpressionId());
        else if (getLocalName().startsWith("ends-with"))
            result = NodeSets.getNodesMatchingAtEnd(result, getExpressionId());

        result = NodeSets.transformNodes(result, proxy ->
                NodeProxies.transformOwnMatches(
                        proxy,
                        Match::filterOutOverlappingOffsets,
                        getExpressionId()
                )
        );

        return result;
    }

    private EvaluatableExpression parseQuery(final String query) throws XPathException {
        List<String> queryTokens = tokenizeQuery(query, this);

        LOG.trace("Tokenized query: {}", queryTokens);

        if (queryTokens.isEmpty())
            return new EmptyExpression();

        List<WildcardedExpression> expressions = new ArrayList<>();

        if ("^".equals(queryTokens.getFirst())) {
            expressions.add(new StartAnchor());
            queryTokens.removeFirst();
        }

        if (queryTokens.isEmpty())
            return new EmptyExpression();

        boolean endAnchorPresent = false;
        if ("$".equals(queryTokens.getLast())) {
            endAnchorPresent = true;
            queryTokens.removeLast();
        }

        if (queryTokens.isEmpty())
            return new EmptyExpression();

        for (String token : queryTokens) {
            if (token.startsWith(".")) {
                Wildcard wildcard = null;
                if (token.length() == 1) {
                    wildcard = new Wildcard(1, 1);
            } else {
                    String qualifier = token.substring(1);
                    switch (qualifier) {
                        case "?":
                            wildcard = new Wildcard(0, 1);
                            break;
                        case "*":
                            wildcard = new Wildcard(0, Integer.MAX_VALUE);
                            break;
                        case "+":
                            wildcard = new Wildcard(1, Integer.MAX_VALUE);
                            break;
                        default:
                            Pattern p = Pattern.compile(INTERVAL_QUALIFIER_PATTERN);
                            Matcher m = p.matcher(qualifier);
                            if (!m.matches()) // Should not happen
                                throw new XPathException(
                                        this,
                                        ErrorCodes.FTDY0020,
                                        "query string violates wildcard qualifier syntax"
                                );
                            try {
                                wildcard = new Wildcard(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                            } catch (NumberFormatException nfe) {
                                throw new XPathException(this,
                                        ErrorCodes.FTDY0020,
                                        "query string violates wildcard qualifier syntax",
                                        new StringValue(this, query),
                                        nfe
                                );
                            }
                            break;
                    }
                }
                expressions.add(wildcard);
            } else {
                if (token.startsWith("[")) {
                    Set<String> strings = new HashSet<>(token.length() - 2);
                    for (int i = 1; i < token.length() - 1; i++)
                        strings.add(Character.toString(token.charAt(i)));
                    expressions.add(new AlternativeStrings(this, strings));
                } else {
                    expressions.add(new FixedString(this, unescape(token)));
                }
            }
        }

        if (endAnchorPresent)
            expressions.add(new EndAnchor());

        return new WildcardedExpressionSequence(expressions);
    }

    private static String unescape(final String s) {
        return s.replaceAll("\\\\(.)", "$1");
    }

    private static List<String> tokenizeQuery(final String query, final Expression expression) throws XPathException {
        List<String> result = new ArrayList<>();

        StringBuilder token = new StringBuilder();

        for (int i = 0; i < query.length(); i++) {
            char currentChar = query.charAt(i);
            if (currentChar == '\\') {
                // Escape sequence
                if ((i + 1) < query.length()) {
                    token.append(query.substring(i, i + 2));
                    i++;
                } else {
                    throw new XPathException(expression, ErrorCodes.FTDY0020, "Query string is terminated by an unescaped backslash");
                }
            } else {
                if (currentChar == '.') {
                    int wildcardEnd = i;
                    if (!token.isEmpty()) {
                        result.add(token.toString());
                        token = new StringBuilder();
                    }
                    if ((i + 1) < query.length()) {
                        char peek = query.charAt(i + 1);
                        if (peek == '?' || peek == '*' || peek == '+')
                            wildcardEnd = i + 1;
                        if (peek == '{') {
                            wildcardEnd = query.indexOf('}', i + 2);
                            if (wildcardEnd == -1)
                                throw new XPathException(expression,
                                    "err:FTDY0020: query string violates wildcard syntax: Unmatched qualifier start { in query string; marked by <-- HERE in \""
                                        + query.substring(0, i + 2) + " <-- HERE " + query.substring(i + 2) + "\"");
                            if (!query.substring(i + 1, wildcardEnd + 1).matches(INTERVAL_QUALIFIER_PATTERN))
                                throw new XPathException(expression,
                                    "err:FTDY0020: query string violates wildcard qualifier syntax;  marked by <-- HERE in \""
                                        + query.substring(0, wildcardEnd + 1) + " <-- HERE "
                                        + query.substring(wildcardEnd + 1) + "\"");
                        }
                    }
                    result.add(query.substring(i, wildcardEnd + 1));
                    i = wildcardEnd;
                } else {
                    if (currentChar == '[') {
                        int characterClassEnd = query.indexOf(']', i + 2); // Character classses can not be empty, thus
                        // start search for end at i+2
                        if (characterClassEnd == -1)
                            throw new XPathException(expression,
                                "err:FTDY0020: query string violates wildcard syntax: Unmatched [ in query string; marked by <-- HERE in \""
                                    + query.substring(0, i + 1) + " <-- HERE " + query.substring(i + 1) + "\"");
                        if (!token.isEmpty()) {
                            result.add(token.toString());
                            token = new StringBuilder();
                        }
                        result.add(query.substring(i, characterClassEnd + 1));
                        i = characterClassEnd;
                    } else {
                        if (currentChar == '^') {
                            if (!token.isEmpty()) {
                                result.add(token.toString());
                                token = new StringBuilder();

                            }
                            result.add("^");
                        } else if (currentChar == '$') {
                            if (!token.isEmpty()) {
                                result.add(token.toString());
                                token = new StringBuilder();
                            }
                            result.add("$");
                        } else
                            // default case
                            token.append(currentChar);
                        }
                    }
                }
            }

        if (!token.isEmpty()) {
            result.add(token.toString());
        }

        return result;
    }

    public NodeSet fixedStringSearch(
        final NGramIndexWorker index, final DocumentSet docs, final List<QName> qnames, final String query,
        final NodeSet nodeSet, final int axis) throws XPathException {
        String[] ngrams = NGramSearch.getDistinctNGrams(query, index.getN());

        // Nothing to search for? The find nothing.
        if (ngrams.length == 0)
            return new EmptyNodeSet();

        String firstNgramm = ngrams[0];
        LOG.trace("First NGRAM: {}", firstNgramm);
        NodeSet result = index.search(getExpressionId(), docs, qnames, firstNgramm, firstNgramm, context, nodeSet, axis);

        for (int i = 1; i < ngrams.length; i++) {
            String ngram = ngrams[i];
            int len = ngram.codePointCount(0, ngram.length());
            int fillSize = index.getN() - len;
            String filledNgram = ngram;

            // if this ngram is shorter than n,
            // fill it up with characters from the previous ngram. too short
            // ngrams lead to a considerable performance loss.
            if (fillSize > 0) {
                String filler = ngrams[i - 1];
                StringBuilder buf = new StringBuilder();
                int pos = filler.offsetByCodePoints(0, len);
                for (int j = 0; j < fillSize; j++) {
                    int codepoint = filler.codePointAt(pos);
                    pos += Character.charCount(codepoint);
                    buf.appendCodePoint(codepoint);
                }
                buf.append(ngram);
                filledNgram = buf.toString();
                LOG.debug("Filled: {}", filledNgram);
            }

            NodeSet nodes = index.search(getExpressionId(), docs, qnames, filledNgram, ngram, context, nodeSet, axis);

            final NodeSet nodesContainingFirstINgrams = result;

            result = NodeSets.transformNodes(nodes, proxy ->
                    Optional.ofNullable(nodesContainingFirstINgrams.get(proxy))
                            .map(before -> getContinuousMatches(before, proxy))
                            .orElse(null));
        }
        return result;
	}

    /**
     * Finds all matches in head which are followed by matches in tail in the specified distance.
     *
     * @param head
     *            a nodeset with matches
     * @param tail
     *            another nodeset with matches
     * @return a nodeset containing all matches from the head which are directly followed by matches in the tail
     */
    private NodeProxy getContinuousMatches(final NodeProxy head, final NodeProxy tail) {
        // NodeSet result = new ExtArrayNodeSet();
        Match continuousMatch = null;
        
        Match headMatch = head.getMatches();
        while (headMatch != null && continuousMatch == null) {
            Match tailMatch = tail.getMatches();
            while (tailMatch != null && continuousMatch == null) {
                continuousMatch = headMatch.continuedBy(tailMatch);
                tailMatch = tailMatch.getNextMatch();
            	}
            headMatch = headMatch.getNextMatch();
            }
        if (continuousMatch != null) {
            NodeProxies.filterMatches(tail, match -> match.getContextId() != getExpressionId());

            tail.addMatch(continuousMatch);
            return tail;
        }
        return null;
    }
    
    @Override
	public int getDependencies() {
        final Expression stringArg = getArgument(0);
        if (Type.subTypeOf(stringArg.returnsType(), Type.NODE)
            && !Dependency.dependsOn(stringArg, Dependency.CONTEXT_ITEM)) {
            return Dependency.CONTEXT_SET;
        } else {
            return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
        }
    }

    @Override
    public int returnsType() {
        return Type.NODE;
    }

    /**
     * Split the specified string into a sequence of ngrams to be used for querying the index. For example, if we have a
     * 3-gram index, the string 'distinct' will be split into the ngrams 'dis', 'tin' and 'ct'.
     *
     * @param text
     *            the character sequence to split
     * @return a sequence of ngrams. the last item might be shorter than n.
     */
    private static String[] getDistinctNGrams(final String text, final int ngramSize) {
        int len = text.codePointCount(0, text.length());
        int count = len / ngramSize;
        int remainder = len % ngramSize;

        String[] n = new String[(remainder > 0 ? count + 1 : count)];
        int pos = 0;
        for (int i = 0; i < count; i++) {
            StringBuilder bld = new StringBuilder(ngramSize);
            for (int j = 0; j < ngramSize; j++) {
                int next = Character.toLowerCase(text.codePointAt(pos));
                pos += Character.charCount(next);
                bld.appendCodePoint(next);
            }
            n[i] = bld.toString();
        }
        if (remainder > 0) {
            StringBuilder bld = new StringBuilder(remainder);
            for (int j = 0; j < remainder; j++) {
                int next = Character.toLowerCase(text.codePointAt(pos));
                pos += Character.charCount(next);
                bld.appendCodePoint(next);
            }
            n[count] = bld.toString();
        }
        return n;
    }
}
