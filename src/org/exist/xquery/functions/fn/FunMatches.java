/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.fn;

import org.exist.EXistException;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.NativeValueIndex;
import org.exist.xquery.pragmas.Optimize;
import org.exist.xquery.regex.JDK15RegexTranslator;
import org.exist.xquery.regex.RegexSyntaxException;
import org.exist.xquery.*;
import org.exist.xquery.util.Error;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.functions.text.TextModule;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Implements the fn:matches() function.
 * 
 * Based on the java.util.regex package for regular expression support.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunMatches extends Function implements Optimizable, IndexUseReporter {

	protected static final String FUNCTION_DESCRIPTION_1_PARAM =
		"The function returns true if $input matches the regular expression " +
		"supplied as $pattern, if present; otherwise, it returns false.\n\n";
	protected static final String FUNCTION_DESCRIPTION_2_PARAM =
		"The function returns true if $input matches the regular expression " +
		"supplied as $pattern as influenced by the value of $flags, if present; " +
		"otherwise, it returns false.\n\n" +
		"The effect of calling this version of the function with the $flags argument set to a zero-length string is the same as using the other two argument version. " +
		"Flags are defined in 7.6.1.1 Flags.\n\n";

	protected static final String FUNCTION_DESCRIPTION_COMMON =
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
		"according to the rules described in section 7.6.1 Regular Expression Syntax.\n\n";
	protected static final String FUNCTION_DESCRIPTION_2_PARAM_2 =
		"An error is raised [err:FORX0001] if the value of $flags is invalid " +
		"according to the rules described in section 7.6.1 Regular Expression Syntax.";
	
	protected static final String FUNCTION_DESCRIPTION_REGEX =
		"If $input is the empty sequence, it is interpreted as the zero-length string.\n\n" +
		"Note:\n\n" +
		"The text:matches-regex() variants of the fn:matches() functions are identical except that they avoid the translation of the specified regular expression from XPath2 to Java syntax. " +
		"That is, the regular expression is evaluated as is, and must be valid according to Java regular expression syntax, rather than the more restrictive XPath2 syntax.";

	protected static final FunctionParameterSequenceType INPUT_ARG = new FunctionParameterSequenceType("input", Type.STRING, Cardinality.ZERO_OR_MORE, "The input string");
	protected static final FunctionParameterSequenceType PATTERN_ARG = new FunctionParameterSequenceType("pattern", Type.STRING, Cardinality.EXACTLY_ONE, "The pattern");
	protected static final FunctionParameterSequenceType FLAGS_ARG = new FunctionParameterSequenceType("flags", Type.STRING, Cardinality.EXACTLY_ONE, "The flags");

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("matches", Function.BUILTIN_FUNCTION_NS),
			FUNCTION_DESCRIPTION_1_PARAM + FUNCTION_DESCRIPTION_COMMON,
			new SequenceType[] { INPUT_ARG, PATTERN_ARG },
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the pattern is a match, false otherwise")
		),
		new FunctionSignature(
			new QName("matches", Function.BUILTIN_FUNCTION_NS),
			FUNCTION_DESCRIPTION_2_PARAM + FUNCTION_DESCRIPTION_COMMON +
            FUNCTION_DESCRIPTION_2_PARAM_2,
			new SequenceType[] { INPUT_ARG, PATTERN_ARG, FLAGS_ARG },
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the pattern is a match, false otherwise")
		)
	};
		
	// The following alternative functions are located in the text namespace! If the indexes of the signatures change then change TextModule as well!
		
	public final static FunctionSignature text_signatures[] = {
		new FunctionSignature(
			new QName("matches-regex", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			FUNCTION_DESCRIPTION_1_PARAM + FUNCTION_DESCRIPTION_REGEX,
			new SequenceType[] { INPUT_ARG, PATTERN_ARG },
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the pattern is a match, false otherwise")
		),
		new FunctionSignature(
			new QName("matches-regex", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			FUNCTION_DESCRIPTION_2_PARAM + FUNCTION_DESCRIPTION_REGEX +
            FUNCTION_DESCRIPTION_2_PARAM_2,
			new SequenceType[] { INPUT_ARG, PATTERN_ARG, FLAGS_ARG },
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the pattern is a match, false otherwise")
		)
	};
	
	protected Matcher matcher = null;
	protected Pattern pat = null;
	
	protected boolean hasUsedIndex = false;

    private LocationStep contextStep = null;
    private QName contextQName = null;
    private int axis = Constants.UNKNOWN_AXIS;
    private NodeSet preselectResult = null;
    private GeneralComparison.IndexFlags idxflags = new GeneralComparison.IndexFlags();

    /**
	 * @param context
	 */
	public FunMatches(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#setArguments(java.util.List)
	 */
	public void setArguments(List<Expression> arguments) throws XPathException {
        steps.clear();
        final Expression path = arguments.get(0);
        steps.add(path);
        
        Expression arg = arguments.get(1);
        arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                new Error(Error.FUNC_PARAM_CARDINALITY, "2", mySignature)); 
        if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
            {arg = new Atomize(context, arg);}
        steps.add(arg);
        
        if (arguments.size() == 3) {
            arg = arguments.get(2);
            arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                    new Error(Error.FUNC_PARAM_CARDINALITY, "3", mySignature)); 
            if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
                {arg = new Atomize(context, arg);}
            steps.add(arg);
        }

        final List<LocationStep> steps = BasicExpressionVisitor.findLocationSteps(path);
        if (!steps.isEmpty()) {
            final LocationStep firstStep = steps.get(0);
            LocationStep lastStep = steps.get(steps.size() - 1);
            if (firstStep != null && lastStep != null) {
	            final NodeTest test = lastStep.getTest();
	            if (!test.isWildcardTest() && test.getName() != null) {
	                contextQName = new QName(test.getName());
	                if (lastStep.getAxis() == Constants.ATTRIBUTE_AXIS || lastStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS)
	                    {contextQName.setNameType(ElementValue.ATTRIBUTE);}
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

    public boolean canOptimize(Sequence contextSequence) {
        if (contextQName == null)
            {return false;}
        return Type.subTypeOf(Optimize.getQNameIndexType(context, contextSequence, contextQName), Type.STRING);
    }

    public boolean optimizeOnSelf() {
        return false;
    }

    public boolean optimizeOnChild() {
        return false;
    }

    public int getOptimizeAxis() {
        return axis;
    }

    public NodeSet preSelect(Sequence contextSequence, boolean useContext) throws XPathException {
        final long start = System.currentTimeMillis();
        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;
        
        final int indexType = Optimize.getQNameIndexType(context, contextSequence, contextQName);
        if (LOG.isTraceEnabled())
            {LOG.trace("Using QName index on type " + Type.getTypeName(indexType));}
		
        String pattern;
		
		if( isCalledAs( "matches-regex" ) ) {
			pattern = getArgument(1).eval(contextSequence).getStringValue();
		} else {
			pattern = translateRegexp(getArgument(1).eval(contextSequence).getStringValue());
		}
		
        boolean caseSensitive = true;
        int flags = 0;
        if(getSignature().getArgumentCount() == 3) {
            final String flagsArg = getArgument(2).eval(contextSequence).getStringValue();
            caseSensitive = (flagsArg.indexOf('i') == Constants.STRING_NOT_FOUND);
            flags = parseFlags(flagsArg);
        }
        try {
            preselectResult = context.getBroker().getValueIndex().match(context.getWatchDog(), contextSequence.getDocumentSet(),
                    useContext ? contextSequence.toNodeSet() : null, NodeSet.DESCENDANT, pattern,
                    contextQName, DBBroker.MATCH_REGEXP, flags, caseSensitive);
            hasUsedIndex = true;
        } catch (final EXistException e) {
            throw new XPathException(this, "Error during index lookup: " + e.getMessage(), e);
        }
        if (context.getProfiler().traceFunctions())
            {context.getProfiler().traceIndexUsage(context, PerformanceStats.RANGE_IDX_TYPE, this,
                PerformanceStats.OPTIMIZED_INDEX, System.currentTimeMillis() - start);}
        return preselectResult;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Function#getDependencies()
     */
    public int getDependencies() {
        final Expression stringArg = getArgument(0);
        final Expression patternArg = getArgument(1);
        if (Type.subTypeOf(stringArg.returnsType(), Type.NODE) &&
            !Dependency.dependsOn(stringArg, Dependency.CONTEXT_ITEM) &&
            !Dependency.dependsOn(patternArg, Dependency.CONTEXT_ITEM)) {
            return Dependency.CONTEXT_SET;
        } else {
            return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
        }
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Function#returnsType()
     */
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
    
    public boolean hasUsedIndex() {
    	return hasUsedIndex;
    }
    
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
        newContextInfo.setParent(this);
        //  call analyze for each argument
        inPredicate = (newContextInfo.getFlags() & IN_PREDICATE) > 0;
        for (int i = 0; i < getArgumentCount(); i++) {
            getArgument(i).analyze(newContextInfo);
        }
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        final long start = System.currentTimeMillis();
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }

        // if we were optimizing and the preselect did not return anything,
        // we won't have any matches and can return
        if (preselectResult != null && preselectResult.isEmpty())
            {return Sequence.EMPTY_SEQUENCE;}

        if (contextItem != null)
			{contextSequence = contextItem.toSequence();}
        
        Sequence result;
        if (contextStep == null || preselectResult == null) {
            final Sequence input = getArgument(0).eval(contextSequence, contextItem);

            if (input.isPersistentSet() && inPredicate && !Dependency.dependsOn(this, Dependency.CONTEXT_ITEM)) {
                if (context.isProfilingEnabled())
                    {context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "", "Index evaluation");}
                if (input.isEmpty())
                    {result = Sequence.EMPTY_SEQUENCE;}
                else
                    {result = evalWithIndex(contextSequence, contextItem, input);}
                if (context.getProfiler().traceFunctions())
                    {context.getProfiler().traceIndexUsage(context, PerformanceStats.RANGE_IDX_TYPE, this,
                        PerformanceStats.BASIC_INDEX, System.currentTimeMillis() - start);}
            } else {
                if (context.isProfilingEnabled())
                    {context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "", "Generic evaluation");}
                if (input.isEmpty())
                    {result = BooleanValue.FALSE;}
                else
                    {result = evalGeneric(contextSequence, contextItem, input);}
                if (context.getProfiler().traceFunctions())
                    {context.getProfiler().traceIndexUsage(context, PerformanceStats.RANGE_IDX_TYPE, this,
                        PerformanceStats.NO_INDEX, System.currentTimeMillis() - start);}
            }
        } else {
            contextStep.setPreloadedData(contextSequence.getDocumentSet(), preselectResult);
            result = getArgument(0).eval(contextSequence).toNodeSet();
        }

        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;          
	}

	/**
     * @param contextSequence
     * @param contextItem
     * @param input
     * @return The resulting sequence
	 * @throws XPathException
     */
    private Sequence evalWithIndex(Sequence contextSequence, Item contextItem, Sequence input) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }  
        
        boolean caseSensitive = true;
        int flags = 0;       
        if(getSignature().getArgumentCount() == 3) {
            final String flagsArg = getArgument(2).eval(contextSequence, contextItem).getStringValue();
            caseSensitive = (flagsArg.indexOf('i') == Constants.STRING_NOT_FOUND);
            flags = parseFlags(flagsArg);
        }
        
        Sequence result = null;
		
        String pattern;
		
		if( isCalledAs( "matches-regex" ) ) {
			pattern = getArgument(1).eval(contextSequence, contextItem).getStringValue();
		} else {
			pattern = translateRegexp(getArgument(1).eval(contextSequence, contextItem).getStringValue());
		}
		
        final NodeSet nodes = input.toNodeSet();
        // get the type of a possible index
		final int indexType = nodes.getIndexType();
		if (LOG.isTraceEnabled())
    		{LOG.trace("found an index of type: " + Type.getTypeName(indexType));}
		if(Type.subTypeOf(indexType, Type.STRING)) {
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
                    if (iflags.hasIndexOnQNames())
                        {indexScan = true;}
                    // else use range index defined on path by default
                }
            } else
                {result = evalFallback(nodes, pattern, flags, indexType);}

            if (result == null) {
                final DocumentSet docs = nodes.getDocumentSet();
                try {
                    final NativeValueIndex index = context.getBroker().getValueIndex();
                    hasUsedIndex = true;
                    //TODO : check index' case compatibility with flags' one ? -pb 
                    if (context.isProfilingEnabled())
                        {context.getProfiler().message(this, Profiler.OPTIMIZATIONS, "Using vlaue index '" + index.toString() + "'", "Regex: " + pattern);}
                    if (LOG.isTraceEnabled())
                        {LOG.trace("Using range index for fn:matches expression: " + pattern);}
                    if (indexScan)
                        {result = index.matchAll(context.getWatchDog(), docs, nodes, NodeSet.ANCESTOR, pattern, DBBroker.MATCH_REGEXP, flags, caseSensitive);}
                    else
                        {result = index.match(context.getWatchDog(), docs, nodes, NodeSet.ANCESTOR, pattern, contextQName, DBBroker.MATCH_REGEXP, flags, caseSensitive);}
                } catch (final EXistException e) {
                    throw new XPathException(this, e);
                }
            }
        } else {
            result = evalFallback(nodes, pattern, flags, indexType);
		}
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;           
        
    }

    private Sequence evalFallback(NodeSet nodes, String pattern, int flags, int indexType) throws XPathException {
        Sequence result;
        if (LOG.isTraceEnabled())
            {LOG.trace("fn:matches: can't use existing range index of type " + Type.getTypeName(indexType) + ". Need a string index.");}
        result = new ExtArrayNodeSet();
        for(final NodeProxy node : nodes) {
            if (match(node.getStringValue(), pattern, flags))
                {result.add(node);}
        }
        return result;
    }

    /**
	 * Translates the regular expression from XPath2 syntax to java regex
	 * syntax.
	 * 
	 * @param pattern
	 * @return The translated regexp
	 * @throws XPathException
	 */
	protected String translateRegexp(String pattern) throws XPathException {
		// convert pattern to Java regex syntax
        try {
        	final int xmlVersion = 11;
        	final boolean ignoreWhitespace = false;
        	final boolean caseBlind = false;
			pattern = JDK15RegexTranslator.translate(pattern, xmlVersion, true, ignoreWhitespace, caseBlind);
		} catch (final RegexSyntaxException e) {
			throw new XPathException(this, "Conversion from XPath2 to Java regular expression " +
					"syntax failed: " + e.getMessage(), e);
		}
		return pattern;
	}

    /**
     * @param contextSequence
     * @param contextItem
     * @param stringArg
     * @return The resulting sequence
     * @throws XPathException
     */
    private Sequence evalGeneric(Sequence contextSequence, Item contextItem, Sequence stringArg) throws XPathException {
        final String string = stringArg.getStringValue();
		String pattern;
		
		if( isCalledAs( "matches-regex" ) ) {
			pattern = getArgument(1).eval(contextSequence, contextItem).getStringValue();
		} else {
			pattern = translateRegexp(getArgument(1).eval(contextSequence, contextItem).getStringValue());
		}
        
		int flags = 0;
        if(getSignature().getArgumentCount() == 3)
            {flags = parseFlags(getArgument(2).eval(contextSequence, contextItem).getStringValue());}
        
		return BooleanValue.valueOf(match(string, pattern, flags));
    }

    /**
     * @param string
     * @param pattern
     * @param flags
     * @return Whether or not the string matches the given pattern with the given flags     
     * @throws XPathException
     */
    private boolean match(String string, String pattern, int flags) throws XPathException {
        try {
			if(pat == null || (!pattern.equals(pat.pattern())) || flags != pat.flags()) {
				pat = Pattern.compile(pattern, flags);
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

    protected final static int parseFlags(String s) throws XPathException {
		int flags = 0;
		for(int i = 0; i < s.length(); i++) {
			final char ch = s.charAt(i);
			switch(ch) {
				case 'm':
					flags |= Pattern.MULTILINE;
					break;
				case 'i':
					flags = flags | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
					break;
                case 'x':
                    flags |= Pattern.COMMENTS;
                    break;
                case 's':
                    flags |= Pattern.DOTALL;
                    break;
				default:
					throw new XPathException("err:FORX0001: Invalid regular expression flag: " + ch);
			}
		}
		return flags;
	}
    
    public void reset() { 
    	super.reset();
		hasUsedIndex = false;
	}

    /*
    * (non-Javadoc)
    *
    * @see org.exist.xquery.AbstractExpression#resetState()
    */
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        if (!postOptimization)
            {preselectResult = null;}
    }
}
