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
import org.exist.storage.ElementValue;
import org.exist.storage.NativeValueIndex;
import org.exist.util.PatternFactory;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.fn.FnModule.functionSignatures;
import static org.exist.xquery.regex.RegexUtil.*;

/**
 * Implements the fn:matches() function.
 * <p>
 * Based on the java.util.regex package for regular expression support.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public final class FunMatches extends Function implements Optimizable, IndexUseReporter {

    private static final FunctionParameterSequenceType FS_PARAM_INPUT = optParam("input", Type.STRING, "The input string");
    private static final FunctionParameterSequenceType FS_PARAM_PATTERN = param("pattern", Type.STRING, "The pattern");
    private static final FunctionParameterSequenceType FS_PARAM_FLAGS = param("flags", Type.STRING, "The flags");

    private static final String FS_MATCHES_NAME = "matches";
    private static final String FS_DESCRIPTION =
            "The function returns true if $input matches the regular expression " +
            "supplied as $pattern as influenced by the value of $flags, if present; " +
            "otherwise, it returns false.\n\n" +
            "The effect of calling this version of the function with the $flags argument set to a" +
            "zero-length string is the same as using the other two argument version. " +
            "Flags are defined in 7.6.1.1 Flags.\n\n" +
            "If $input is the empty sequence, it is interpreted as the zero-length string.\n\n" +
            "Unless the metacharacters ^ and $ are used as anchors, the string is considered " +
            "to match the pattern if any substring matches the pattern. But if anchors are used, " +
            "the anchors must match the start/end of the string (in string mode), or the " +
            "start/end of a line (in multiline mode).\n\n" +
            "Note:\n\n" +
            "This is different from the behavior of patterns in [XML Schema Part 2: Datatypes " +
            "Second Edition], where regular expressions are implicitly anchored.\n\n" +
            "Please note that - in contrast - with the " +
            "specification - this method allows zero or more items for the string argument.\n\n" +
            "An error is raised [err:FORX0002] if the value of $pattern is invalid " +
            "according to the rules described in section 7.6.1 Regular Expression Syntax.\n\n" +
            "An error is raised [err:FORX0001] if the value of $flags is invalid " +
            "according to the rules described in section 7.6.1 Regular Expression Syntax.";

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

    protected Matcher matcher = null;
    protected Pattern pat = null;

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
        final Expression path = arguments.get(0);
        steps.add(path);

        if (arguments.size() >= 2) {
            Expression arg = arguments.get(1);
            arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                    new Error(Error.FUNC_PARAM_CARDINALITY, "2", getSignature()));
            if (!Type.subTypeOf(arg.returnsType(), Type.ATOMIC)) {
                arg = new Atomize(context, arg);
            }
            steps.add(arg);
        }

        if (arguments.size() >= 3) {
            Expression arg = arguments.get(2);
            arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                    new Error(Error.FUNC_PARAM_CARDINALITY, "3", getSignature()));
            if (!Type.subTypeOf(arg.returnsType(), Type.ATOMIC)) {
                arg = new Atomize(context, arg);
            }
            steps.add(arg);
        }

        final List<LocationStep> steps = BasicExpressionVisitor.findLocationSteps(path);
        if (!steps.isEmpty()) {
            final LocationStep firstStep = steps.get(0);
            LocationStep lastStep = steps.get(steps.size() - 1);
            if (firstStep != null && lastStep != null) {
                final NodeTest test = lastStep.getTest();
                if (!test.isWildcardTest() && test.getName() != null) {

                    if (lastStep.getAxis() == Constants.ATTRIBUTE_AXIS || lastStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS) {
                        contextQName = new QName(test.getName(), ElementValue.ATTRIBUTE);
                    } else {
                        contextQName = new QName(test.getName());
                    }
                    contextStep = lastStep;
                    axis = firstStep.getAxis();
                    if (axis == Constants.SELF_AXIS && steps.size() > 1) {
                        if (steps.get(1) != null) {
                            axis = steps.get(1).getAxis();
                        } else {
                            contextQName = null;
                            contextStep = null;
                            axis = Constants.UNKNOWN_AXIS;
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean canOptimize(final Sequence contextSequence) {
        if (contextQName == null) {
            return false;
        }
        return Type.subTypeOf(Optimize.getQNameIndexType(context, contextSequence, contextQName), Type.STRING);
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

        final int flags;
        if (getSignature().getArgumentCount() == 3) {
            final String flagsArg = getArgument(2).eval(contextSequence).getStringValue();
            flags = parseFlags(this, flagsArg);
        } else {
            flags = 0;
        }

        final boolean caseSensitive = !hasCaseInsensitive(flags);

        final String pattern;
        final boolean literal = hasLiteral(flags);
        if (literal) {
            // no need to change anything
            pattern = getArgument(1).eval(contextSequence).getStringValue();
        } else {
            final boolean ignoreWhitespace = hasIgnoreWhitespace(flags);
            final boolean caseBlind = !caseSensitive;
            pattern = translateRegexp(this, getArgument(1).eval(contextSequence).getStringValue(), ignoreWhitespace, caseBlind);
        }

        try {
            preselectResult = context.getBroker().getValueIndex().match(context.getWatchDog(), contextSequence.getDocumentSet(),
                    useContext ? contextSequence.toNodeSet() : null, NodeSet.DESCENDANT, pattern,
                    contextQName, DBBroker.MATCH_REGEXP, flags, caseSensitive);
            hasUsedIndex = true;
        } catch (final EXistException e) {
            throw new XPathException(this, "Error during index lookup: " + e.getMessage(), e);
        }
        if (context.getProfiler().traceFunctions()) {
            context.getProfiler().traceIndexUsage(context, PerformanceStats.RANGE_IDX_TYPE, this,
                    PerformanceStats.OPTIMIZED_INDEX, System.currentTimeMillis() - start);
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
                            PerformanceStats.BASIC_INDEX, System.currentTimeMillis() - start);
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
                            PerformanceStats.NO_INDEX, System.currentTimeMillis() - start);
                }
            }
        } else {
            contextStep.setPreloadedData(contextSequence.getDocumentSet(), preselectResult);
            result = getArgument(0).eval(contextSequence).toNodeSet();
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

        final int flags;
        if (getSignature().getArgumentCount() == 3) {
            final String flagsArg = getArgument(2).eval(contextSequence, contextItem).getStringValue();
            flags = parseFlags(this, flagsArg);
        } else {
            flags = 0;
        }

        final boolean caseSensitive = !hasCaseInsensitive(flags);

        Sequence result = null;

        final String pattern;
        if (isCalledAs("matches-regex")) {
            pattern = getArgument(1).eval(contextSequence, contextItem).getStringValue();
        } else {
            final boolean literal = hasLiteral(flags);
            if (literal) {
                // no need to change anything
                pattern = getArgument(1).eval(contextSequence, contextItem).getStringValue();
            } else {
                final boolean ignoreWhitespace = hasIgnoreWhitespace(flags);
                final boolean caseBlind = !caseSensitive;
                pattern = translateRegexp(this, getArgument(1).eval(contextSequence, contextItem).getStringValue(), ignoreWhitespace, caseBlind);
            }
        }

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
                if (!indexFound && contextQName == null) {
                    // if there are some indexes defined on a qname,
                    // we need to check them all
                    if (iflags.hasIndexOnQNames()) {
                        indexScan = true;
                    }
                    // else use range index defined on path by default
                }
            } else {
                result = evalFallback(nodes, pattern, flags, indexType);
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
                        result = index.matchAll(context.getWatchDog(), docs, nodes, NodeSet.ANCESTOR, pattern, DBBroker.MATCH_REGEXP, flags, caseSensitive);
                    } else {
                        result = index.match(context.getWatchDog(), docs, nodes, NodeSet.ANCESTOR, pattern, contextQName, DBBroker.MATCH_REGEXP, flags, caseSensitive);
                    }
                } catch (final EXistException e) {
                    throw new XPathException(this, e);
                }
            }
        } else {
            result = evalFallback(nodes, pattern, flags, indexType);
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;

    }

    private Sequence evalFallback(final NodeSet nodes, final String pattern, final int flags, final int indexType) throws XPathException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("fn:matches: can't use existing range index of type {}. Need a string index.", Type.getTypeName(indexType));
        }
        final Sequence result = new ExtArrayNodeSet();
        for (final NodeProxy node : nodes) {
            if (match(node.getStringValue(), pattern, flags)) {
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
        final String string = input.getStringValue();

        final int flags;
        if (getSignature().getArgumentCount() == 3) {
            flags = parseFlags(this, getArgument(2).eval(contextSequence, contextItem).getStringValue());
        } else {
            flags = 0;
        }

        final String pattern;
        if (isCalledAs("matches-regex")) {
            pattern = getArgument(1).eval(contextSequence, contextItem).getStringValue();
        } else {
            final boolean literal = hasLiteral(flags);
            if (literal) {
                // no need to change anything
                pattern = getArgument(1).eval(contextSequence, contextItem).getStringValue();
            } else {
                final boolean ignoreWhitespace = hasIgnoreWhitespace(flags);
                final boolean caseBlind = hasCaseInsensitive(flags);
                pattern = translateRegexp(this, getArgument(1).eval(contextSequence, contextItem).getStringValue(), ignoreWhitespace, caseBlind);
            }
        }

        return BooleanValue.valueOf(match(string, pattern, flags));
    }

    /**
     * @param string the value
     * @param pattern the pattern
     * @param flags the flags
     * @return Whether or not the string matches the given pattern with the given flags
     * @throws XPathException if an error occurs
     */
    private boolean match(final String string, final String pattern, final int flags) throws XPathException {
        try {
            if (pat == null || (!pattern.equals(pat.pattern())) || flags != pat.flags()) {
                pat = PatternFactory.getInstance().getPattern(pattern, flags);
                //TODO : make matches('&#x212A;', '[A-Z]', 'i') work !
                matcher = pat.matcher(string);
            } else {
                matcher.reset(string);
            }

            return matcher.find();

        } catch (final PatternSyntaxException e) {
            throw new XPathException(this, ErrorCodes.FORX0001, "Invalid regular expression: " + e.getMessage(), new StringValue(pattern), e);
        }
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
}
