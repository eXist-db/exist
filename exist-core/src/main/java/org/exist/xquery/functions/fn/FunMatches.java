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
package org.exist.xquery.functions.fn;

import org.exist.EXistException;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.storage.SaxonRegexTermMatcher;
import org.exist.storage.TermMatcher;
import org.exist.storage.ElementValue;
import org.exist.storage.NativeValueIndex;
import org.exist.xquery.pragmas.Optimize;
import org.exist.xquery.*;
import org.exist.xquery.util.Error;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import java.util.List;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.str.StringView;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.fn.FnModule.functionSignatures;
import static org.exist.xquery.regex.RegexUtil.*;
import static org.exist.xquery.regex.SaxonRegex.*;

/**
 * Implements the fn:matches() function.
 * <p>
 * Based on the java.util.regex package for regular expression support.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public final class FunMatches extends Function implements BoundSequenceOptimizable, IndexUseReporter {

    private static final FunctionParameterSequenceType FS_PARAM_INPUT = optParam("input", Type.STRING, "The input string");
    private static final FunctionParameterSequenceType FS_PARAM_PATTERN = param("pattern", Type.STRING, "The pattern");
    private static final FunctionParameterSequenceType FS_PARAM_FLAGS = param("flags", Type.STRING, "The flags");

    private static final String FS_MATCHES_NAME = "matches";
    private static final String FS_DESCRIPTION =
            """
            The function returns true if $input matches the regular expression \
            supplied as $pattern as influenced by the value of $flags, if present; \
            otherwise, it returns false.
            
            The effect of calling this version of the function with the $flags argument set to a\
            zero-length string is the same as using the other two argument version. \
            Flags are defined in 7.6.1.1 Flags.
            
            If $input is the empty sequence, it is interpreted as the zero-length string.
            
            Unless the metacharacters ^ and $ are used as anchors, the string is considered \
            to match the pattern if any substring matches the pattern. But if anchors are used, \
            the anchors must match the start/end of the string (in string mode), or the \
            start/end of a line (in multiline mode).
            
            Note:
            
            This is different from the behavior of patterns in [XML Schema Part 2: Datatypes \
            Second Edition], where regular expressions are implicitly anchored.
            
            Please note that - in contrast - with the \
            specification - this method allows zero or more items for the string argument.
            
            An error is raised [err:FORX0002] if the value of $pattern is invalid \
            according to the rules described in section 7.6.1 Regular Expression Syntax.
            
            An error is raised [err:FORX0001] if the value of $flags is invalid \
            according to the rules described in section 7.6.1 Regular Expression Syntax.""";

    public final static FunctionSignature[] signatures = functionSignatures(
            FS_MATCHES_NAME,
            FS_DESCRIPTION,
            returns(Type.BOOLEAN, "true if the pattern is a match, false otherwise"),
            arities(
                    arity(
                            FS_PARAM_INPUT,
                            FS_PARAM_PATTERN
                    ),
                    arity(
                            FS_PARAM_INPUT,
                            FS_PARAM_PATTERN,
                            FS_PARAM_FLAGS
                    )
            )
    );


    protected boolean hasUsedIndex = false;

    private LocationStep contextStep = null;
    private QName contextQName = null;
    private int axis = Constants.UNKNOWN_AXIS;
    private NodeSet preselectResult = null;
    private final GeneralComparison.IndexFlags idxflags = new GeneralComparison.IndexFlags();

    public FunMatches(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void setArguments(final List<Expression> arguments) throws XPathException {
        steps.clear();
        final Expression path = arguments.getFirst();
        steps.add(path);

        if (arguments.size() >= 2) {
            Expression arg = arguments.get(1);
            arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                    new Error(Error.FUNC_PARAM_CARDINALITY, "2", getSignature()));
            if (!Type.subTypeOf(arg.returnsType(), Type.ANY_ATOMIC_TYPE)) {
                arg = new Atomize(context, arg);
            }
            steps.add(arg);
        }

        if (arguments.size() >= 3) {
            Expression arg = arguments.get(2);
            arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                    new Error(Error.FUNC_PARAM_CARDINALITY, "3", getSignature()));
            if (!Type.subTypeOf(arg.returnsType(), Type.ANY_ATOMIC_TYPE)) {
                arg = new Atomize(context, arg);
            }
            steps.add(arg);
        }

        deriveIndexTargetFrom(path);
    }

    /**
     * Works out which QName the range index should be consulted for, and on which axis, by walking
     * the location steps of {@code path}.
     *
     * <p>Normally {@code path} is the function's own first argument -- {@code matches(val, 'a')}
     * indexes on {@code val}. It can also be supplied from outside, by
     * {@link #optimizeOverBoundSequence(Expression)}, when the path is not the argument but the
     * sequence a quantified expression binds the argument to.</p>
     *
     * @param path the expression whose location steps name the indexed node
     */
    private void deriveIndexTargetFrom(final Expression path) {
        contextQName = null;
        contextStep = null;
        axis = Constants.UNKNOWN_AXIS;

        final List<LocationStep> steps = BasicExpressionVisitor.findLocationSteps(path);
        if (steps.isEmpty()) {
            return;
        }

        final LocationStep firstStep = steps.getFirst();
        final LocationStep lastStep = steps.getLast();
        if (firstStep == null || lastStep == null) {
            return;
        }

        final NodeTest test = lastStep.getTest();
        if (test.isWildcardTest() || test.getName() == null) {
            return;
        }

        final int resolvedAxis = resolveAxis(firstStep, steps);
        if (resolvedAxis == Constants.UNKNOWN_AXIS) {
            return;
        }

        contextQName = indexedQNameFor(lastStep, test);
        contextStep = lastStep;
        axis = resolvedAxis;
    }

    /**
     * The index is split into an element half and an attribute half, so an attribute step has to
     * be looked up under an attribute QName.
     */
    private static QName indexedQNameFor(final LocationStep step, final NodeTest test) {
        if (step.getAxis() == Constants.ATTRIBUTE_AXIS || step.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS) {
            return new QName(test.getName(), ElementValue.ATTRIBUTE);
        }
        return new QName(test.getName());
    }

    /**
     * The axis the optimizer should search along. A leading self step tells us nothing, so the
     * axis of the step after it is used instead.
     *
     * @return the axis, or {@link Constants#UNKNOWN_AXIS} if it cannot be determined
     */
    private static int resolveAxis(final LocationStep firstStep, final List<LocationStep> steps) {
        final int axis = firstStep.getAxis();
        if (axis != Constants.SELF_AXIS || steps.size() <= 1) {
            return axis;
        }
        final LocationStep second = steps.get(1);
        return second == null ? Constants.UNKNOWN_AXIS : second.getAxis();
    }

    /**
     * Makes this call optimizable when its input arrives through a quantified binding rather than
     * directly as a path -- {@code some $v in val satisfies matches($v, 'a')} rather than
     * {@code matches(val, 'a')}.
     *
     * <p>The two select the same nodes, but only the first says so in a way the specification
     * allows: {@code fn:matches} takes {@code xs:string?}, so a multi-valued path is a type error
     * rather than an existential test. The index can serve the quantified spelling just as well --
     * it only needs to be told which QName to look up, which is the quantifier's input sequence
     * instead of this function's first argument.</p>
     *
     * <p>Called by the optimizer during static analysis, before {@code canOptimizeSequence}.</p>
     *
     * @param boundSequence the sequence the quantified expression binds the variable to
     */
    @Override
    public void optimizeOverBoundSequence(final Expression boundSequence) {
        deriveIndexTargetFrom(boundSequence);
    }

    @Override
    public Sequence canOptimizeSequence(final Sequence contextSequence) {
        if (contextQName != null && Type.subTypeOf(Optimize.getQNameIndexType(context, contextSequence, contextQName), Type.STRING)) {
            return contextSequence;
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    @Override
    public boolean optimizeOnSelf() {
        return false;
    }

    @Override
    public boolean optimizeOnChild() {
        return false;
    }

    @Override
    public int getOptimizeAxis() {
        return axis;
    }

    @Override
    public NodeSet preSelect(final Sequence contextSequence, final boolean useContext) throws XPathException {
        final long start = System.currentTimeMillis();
        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;

        final int indexType = Optimize.getQNameIndexType(context, contextSequence, contextQName);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Using QName index on type {}", Type.getTypeName(indexType));
        }

        final String flags = validateFlags(this, getSignature().getArgumentCount() == 3
                ? getArgument(2).eval(contextSequence, null).getStringValue() : "");
        final boolean caseSensitive = !isCaseInsensitive(flags);
        final String pattern = getArgument(1).eval(contextSequence, null).getStringValue();
        final RegularExpression regex = compile(this,
                context.getBroker().getBrokerPool().getSaxonConfiguration(), pattern, flags);

        try {
            preselectResult = context.getBroker().getValueIndex().matchRegex(context.getWatchDog(), contextSequence.getDocumentSet(),
                    useContext ? contextSequence.toNodeSet() : null, NodeSet.DESCENDANT, pattern,
                    contextQName, new SaxonRegexTermMatcher(regex), caseSensitive);
            hasUsedIndex = true;
        } catch (final EXistException e) {
            throw new XPathException(this, "Error during index lookup: " + e.getMessage(), e);
        }
        if (context.getProfiler().traceFunctions()) {
            context.getProfiler().traceIndexUsage(context, PerformanceStats.RANGE_IDX_TYPE, this,
                    PerformanceStats.IndexOptimizationLevel.OPTIMIZED, System.currentTimeMillis() - start);
        }
        return preselectResult;
    }

    @Override
    public int getDependencies() {
        final Expression stringArg = getArgument(0);
        final Expression patternArg;
        if (getArgumentCount() >= 2) {
            patternArg = getArgument(1);
        } else {
            patternArg = null;
        }

        if (Type.subTypeOf(stringArg.returnsType(), Type.NODE) &&
                !Dependency.dependsOn(stringArg, Dependency.CONTEXT_ITEM) &&
                (patternArg == null || !Dependency.dependsOn(patternArg, Dependency.CONTEXT_ITEM))) {
            return Dependency.CONTEXT_SET;
        } else {
            return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
        }
    }

    @Override
    public int returnsType() {
        if (inPredicate && (getDependencies() & Dependency.CONTEXT_ITEM) == 0) {
            /* If one argument is a node set we directly
             * return the matching nodes from the context set. This works
             * only inside predicates.
             */
            return Type.NODE;
        }
        // In all other cases, we return boolean
        return Type.BOOLEAN;
    }

    @Override
    public boolean hasUsedIndex() {
        return hasUsedIndex;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
        newContextInfo.setParent(this);
        //  call analyze for each argument
        inPredicate = (newContextInfo.getFlags() & IN_PREDICATE) > 0;
        // FunMatches implements Optimizable and depends on IN_PREDICATE
        // being visible to the range/Lucene index optimizers via its
        // arguments. Do not strip it here. The general Function.analyze()
        // strips it for non-Optimizable functions (issue #4958).
        for (int i = 0; i < getArgumentCount(); i++) {
            getArgument(i).analyze(newContextInfo);
        }
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        final long start = System.currentTimeMillis();
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
            }
        }

        // if we were optimizing and the preselect did not return anything,
        // we won't have any matches and can return
        if (preselectResult != null && preselectResult.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        final Sequence result;
        if (contextStep == null || preselectResult == null) {
            final Sequence input = getArgument(0).eval(contextSequence, contextItem);

            if (input.isPersistentSet() && inPredicate && !Dependency.dependsOn(this, Dependency.CONTEXT_ITEM)) {
                if (context.isProfilingEnabled()) {
                    context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "", "Index evaluation");
                }
                if (input.isEmpty()) {
                    result = Sequence.EMPTY_SEQUENCE;
                } else {
                    result = evalWithIndex(contextSequence, contextItem, input);
                }
                if (context.getProfiler().traceFunctions()) {
                    context.getProfiler().traceIndexUsage(context, PerformanceStats.RANGE_IDX_TYPE, this,
                            PerformanceStats.IndexOptimizationLevel.BASIC, System.currentTimeMillis() - start);
                }
            } else {
                if (context.isProfilingEnabled()) {
                    context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "", "Generic evaluation");
                }
                if (input.isEmpty()) {
                    result = BooleanValue.FALSE;
                } else {
                    result = evalGeneric(contextSequence, contextItem, input);
                }
                if (context.getProfiler().traceFunctions()) {
                    context.getProfiler().traceIndexUsage(context, PerformanceStats.RANGE_IDX_TYPE, this,
                            PerformanceStats.IndexOptimizationLevel.NONE, System.currentTimeMillis() - start);
                }
            }
        } else {
            contextStep.setPreloadedData(contextSequence.getDocumentSet(), preselectResult);
            result = getArgument(0).eval(contextSequence, null).toNodeSet();
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;
    }

    /**
     * @param contextSequence the context sequence
     * @param contextItem the context item
     * @param input the value of the $input arg
     * @return The resulting sequence
     * @throws XPathException if an error occurs
     */
    private Sequence evalWithIndex(final Sequence contextSequence, final Item contextItem, final Sequence input) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
            }
        }

        final String flags = validateFlags(this, getSignature().getArgumentCount() == 3
                ? getArgument(2).eval(contextSequence, contextItem).getStringValue() : "");
        final boolean caseSensitive = !isCaseInsensitive(flags);
        final String pattern = getArgument(1).eval(contextSequence, contextItem).getStringValue();
        final RegularExpression regex = compile(this,
                context.getBroker().getBrokerPool().getSaxonConfiguration(), pattern, flags);
        final TermMatcher matcher = new SaxonRegexTermMatcher(regex);

        Sequence result = null;

        final NodeSet nodes = input.toNodeSet();
        // get the type of a possible index
        final int indexType = nodes.getIndexType();
        if (LOG.isTraceEnabled()) {
            LOG.trace("found an index of type: {}", Type.getTypeName(indexType));
        }
        if (Type.subTypeOf(indexType, Type.STRING)) {
            boolean indexScan = false;
            if (contextSequence != null) {
                final GeneralComparison.IndexFlags iflags = GeneralComparison.checkForQNameIndex(idxflags, context, contextSequence, contextQName);
                boolean indexFound = false;
                if (!iflags.indexOnQName()) {
                    // if contextQName != null and no index is defined on
                    // contextQName, we don't need to scan other QName indexes
                    // and can just use the generic range index
                    indexFound = contextQName != null;
                    // set contextQName to null so the index lookup below is not
                    // restricted to that QName
                    contextQName = null;
                }
                // if there are some indexes defined on a qname, we need to check them all;
                // otherwise use range index defined on path by default
                if (!indexFound && contextQName == null && iflags.hasIndexOnQNames()) {
                    indexScan = true;
                }
            } else {
                result = evalFallback(nodes, regex, indexType);
            }

            if (result == null) {
                final DocumentSet docs = nodes.getDocumentSet();
                try {
                    final NativeValueIndex index = context.getBroker().getValueIndex();
                    hasUsedIndex = true;
                    //TODO : check index' case compatibility with flags' one ? -pb 
                    if (context.isProfilingEnabled()) {
                        context.getProfiler().message(this, Profiler.OPTIMIZATIONS, "Using value index '" + index.toString() + "'", "Regex: " + pattern);
                    }
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Using range index for fn:matches expression: {}", pattern);
                    }
                    if (indexScan) {
                        result = index.matchAllRegex(context.getWatchDog(), docs, nodes, NodeSet.ANCESTOR, pattern, matcher, caseSensitive);
                    } else {
                        result = index.matchRegex(context.getWatchDog(), docs, nodes, NodeSet.ANCESTOR, pattern, contextQName, matcher, caseSensitive);
                    }
                } catch (final EXistException e) {
                    throw new XPathException(this, e);
                }
            }
        } else {
            result = evalFallback(nodes, regex, indexType);
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;

    }

    private Sequence evalFallback(final NodeSet nodes, final RegularExpression regex, final int indexType) throws XPathException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("fn:matches: can't use existing range index of type {}. Need a string index.", Type.getTypeName(indexType));
        }
        final TermMatcher matcher = new SaxonRegexTermMatcher(regex);
        final Sequence result = new ExtArrayNodeSet();
        for (final NodeProxy node : nodes) {
            if (matcher.matches(node.getStringValue())) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * @param contextSequence the context sequence
     * @param contextItem the context item
     * @param input the value of the $input arg
     * @return The resulting sequence
     * @throws XPathException if an error occurs
     */
    private Sequence evalGeneric(final Sequence contextSequence, final Item contextItem, final Sequence input) throws XPathException {
        // fn:matches takes xs:string?, so more than one item is a type error. Sequence.getStringValue
        // below would otherwise quietly return the first item's value and test that alone, which is
        // how matches(('x','a'), 'a') came to answer false rather than raising.
        if (input.getItemCount() > 1) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Type error: the first argument of " + getName() + " must be a single item; got "
                            + input.getItemCount() + " items", input);
        }

        final String string = input.getStringValue();

        final String xmlRegexFlags;
        if (getSignature().getArgumentCount() == 3) {
            xmlRegexFlags = getArgument(2).eval(contextSequence, contextItem).getStringValue();
        } else {
            xmlRegexFlags = "";
        }

        final String pattern = getArgument(1).eval(contextSequence, contextItem).getStringValue();
        return BooleanValue.valueOf(matchXmlRegex(string, pattern, xmlRegexFlags));
    }


    private boolean matchXmlRegex(final String string, final String pattern, final String rawFlags) throws XPathException {
        final String flags = validateFlags(this, rawFlags);

        // XPath 4.0 lookaround syntax is not yet implemented in eXist's XQuery 3.1 runtime.
        // When XQuery 4.0 lands (v2/xq4-core-functions), replace this guard with the
        // translateXPath4Lookaround / ;j dispatch path.
        if (hasXPath4Lookaround(pattern)) {
            throw new XPathException(this, ErrorCodes.XPST0017,
                    "XPath 4.0 lookaround syntax in regex patterns (e.g. (*positive_lookahead:...)) "
                            + "is not yet implemented in this XQuery 3.1 build. Rewrite the regex without lookaround.");
        }

        // Pre-validate: reject constructs that are not valid in XPath 3.1 regex
        // but that Saxon's XP30 mode accepts (Java/Perl extensions)
        // Java syntax is the point of ';j', so the XPath-syntax check does not apply to it.
        if (!hasLiteral(flags) && !usesJavaEngine(flags)) {
            validateXPathRegex(this, pattern, false);
        }

        final RegularExpression regex = compile(this,
                context.getBroker().getBrokerPool().getSaxonConfiguration(), pattern, flags);
        return regex.containsMatch(StringView.of(string));
    }


    @Override
    public void reset() {
        super.reset();
        hasUsedIndex = false;
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        if (!postOptimization) {
            preselectResult = null;
        }
    }

    /** Whether the validated flags include {@code i}. */
    private static boolean isCaseInsensitive(final String flags) {
        return flags.indexOf('i') >= 0;
    }
}
