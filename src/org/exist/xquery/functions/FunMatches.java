/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
package org.exist.xquery.functions;

import org.exist.EXistException;
import org.exist.dom.*;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.NativeValueIndex;
import org.exist.xquery.*;
import org.exist.xquery.NodeTest;
import org.exist.xquery.util.Error;
import org.exist.xquery.util.RegexTranslator;
import org.exist.xquery.util.RegexTranslator.RegexSyntaxException;
import org.exist.xquery.value.*;

import java.util.Iterator;
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

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("matches", Function.BUILTIN_FUNCTION_NS),
			"Returns true if the first argument string matches the regular expression specified " +
			"by the second argument. This function is optimized internally if a range index of type xs:string " +
			"is defined on the nodes passed to the first argument. Please note that - in contrast - with the " +
            "specification - this method allows zero or more items for the string argument.",
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE),
				 new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
		),
		new FunctionSignature(
			new QName("matches", Function.BUILTIN_FUNCTION_NS),
			"Returns true if the first argument string matches the regular expression specified " +
			"by the second argument. This function is optimized internally if a range index of type xs:string " +
			"is defined on the nodes passed to the first argument. Please note that - in contrast - with the " +
            "specification - this method allows zero or more items for the string argument.",
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE),
				 new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				 new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
		)
	};
	
	protected Matcher matcher = null;
	protected Pattern pat = null;
	
	protected boolean hasUsedIndex = false;

    private LocationStep contextStep = null;
    private QName contextQName = null;
    private int axis = Constants.UNKNOWN_AXIS;
    private NodeSet preselectResult = null;

    /**
	 * @param context
	 */
	public FunMatches(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#setArguments(java.util.List)
	 */
	public void setArguments(List arguments) throws XPathException {
        Expression path = (Expression) arguments.get(0);
        steps.add(path);
        
        Expression arg = (Expression) arguments.get(1);
        arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                new Error(Error.FUNC_PARAM_CARDINALITY, "2", mySignature)); 
        if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
            arg = new Atomize(context, arg);
        steps.add(arg);
        
        if (arguments.size() == 3) {
            arg = (Expression) arguments.get(2);
            arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                    new Error(Error.FUNC_PARAM_CARDINALITY, "3", mySignature)); 
            if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
                arg = new Atomize(context, arg);
            steps.add(arg);
        }

        List steps = BasicExpressionVisitor.findLocationSteps(path);
        if (!steps.isEmpty()) {
            LocationStep firstStep = (LocationStep) steps.get(0);
            LocationStep lastStep = (LocationStep) steps.get(steps.size() - 1);
            NodeTest test = lastStep.getTest();
            if (!test.isWildcardTest() && test.getName() != null) {
                contextQName = new QName(test.getName());
                if (lastStep.getAxis() == Constants.ATTRIBUTE_AXIS || lastStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS)
                    contextQName.setNameType(ElementValue.ATTRIBUTE);
                axis = firstStep.getAxis();
                contextStep = lastStep;
            }
        }
    }

    public boolean canOptimize(Sequence contextSequence) {
        if (contextQName == null)
            return false;
        return Type.subTypeOf(Optimize.getQNameIndexType(context, contextSequence, contextQName), Type.STRING);
    }

    public boolean optimizeOnSelf() {
        return false;
    }

    public int getOptimizeAxis() {
        return axis;
    }

    public NodeSet preSelect(Sequence contextSequence, boolean useContext) throws XPathException {
        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;
        
        int indexType = Optimize.getQNameIndexType(context, contextSequence, contextQName);
        if (LOG.isTraceEnabled())
            LOG.trace("Using QName index on type " + Type.getTypeName(indexType));
        String pattern = translateRegexp(getArgument(1).eval(contextSequence).getStringValue());
        boolean caseSensitive = true;
        int flags = 0;
        if(getSignature().getArgumentCount() == 3) {
            String flagsArg = getArgument(2).eval(contextSequence).getStringValue();
            caseSensitive = (flagsArg.indexOf('i') == Constants.STRING_NOT_FOUND);
            flags = parseFlags(flagsArg);
        }
        try {
            preselectResult = context.getBroker().getValueIndex().match(contextSequence.getDocumentSet(),
                    useContext ? contextSequence.toNodeSet() : null, NodeSet.DESCENDANT, pattern,
                    contextQName, DBBroker.MATCH_REGEXP, flags, caseSensitive);
            hasUsedIndex = true;
        } catch (EXistException e) {
            throw new XPathException(getASTNode(), "Error during index lookup: " + e.getMessage(), e);
        }
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
    	contextInfo.setParent(this);
    	//  call analyze for each argument
        inPredicate = (contextInfo.getFlags() & IN_PREDICATE) > 0;
        for(int i = 0; i < getArgumentCount(); i++) {
            getArgument(i).analyze(contextInfo);            
        }        
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }

        // if we were optimizing and the preselect did not return anything,
        // we won't have any matches and can return
        if (preselectResult != null && preselectResult.isEmpty())
            return Sequence.EMPTY_SEQUENCE;

        if (contextItem != null)
			contextSequence = contextItem.toSequence();
	    if (preselectResult == null &&
            !Type.subTypeOf(Optimize.getQNameIndexType(context, contextSequence, contextQName), Type.STRING))
            contextQName = null;
        
        Sequence result;
        if (contextStep == null || preselectResult == null) {
            Sequence input = getArgument(0).eval(contextSequence, contextItem);
            if (input.isEmpty())
                result = Sequence.EMPTY_SEQUENCE;
            else if (inPredicate && !Dependency.dependsOn(this, Dependency.CONTEXT_ITEM)) {
                if (context.isProfilingEnabled())
                    context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "", "Index evaluation");
                result = evalWithIndex(contextSequence, contextItem, input);
            } else {
                if (context.isProfilingEnabled())
                    context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "", "Generic evaluation");
                result = evalGeneric(contextSequence, contextItem, input);
            }
        } else {
            contextStep.setPreloadedData(contextSequence.getDocumentSet(), preselectResult);
            result = getArgument(0).eval(contextSequence).toNodeSet();
        }

        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
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
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }  
        
        boolean caseSensitive = true;
        int flags = 0;       
        if(getSignature().getArgumentCount() == 3) {
            String flagsArg = getArgument(2).eval(contextSequence, contextItem).getStringValue();
            caseSensitive = (flagsArg.indexOf('i') == Constants.STRING_NOT_FOUND);
            flags = parseFlags(flagsArg);
        }
        
        Sequence result;
        String pattern = translateRegexp(getArgument(1).eval(contextSequence, contextItem).getStringValue());
        NodeSet nodes = input.toNodeSet();
        // get the type of a possible index
		int indexType = nodes.getIndexType();
		if (LOG.isTraceEnabled())
    		LOG.trace("found an index of type: " + Type.getTypeName(indexType));
		if(Type.subTypeOf(indexType, Type.STRING)) {
		    DocumentSet docs = nodes.getDocumentSet();
		    try {
                NativeValueIndex index = context.getBroker().getValueIndex();
                hasUsedIndex = true;
                //TODO : check index' case compatibility with flags' one ? -pb 
		    	if (context.isProfilingEnabled())
		    		context.getProfiler().message(this, Profiler.OPTIMIZATIONS, "Using vlaue index '" + index.toString() + "'", "Regex: " + pattern);
		    	if (LOG.isTraceEnabled())
		    		LOG.trace("Using range index for fn:matches expression: " + pattern);
                result = index.match(docs, nodes, NodeSet.ANCESTOR, pattern, contextQName, DBBroker.MATCH_REGEXP, flags, caseSensitive);
			} catch (EXistException e) {
				throw new XPathException(getASTNode(), e.getMessage(), e);
			}
		} else {
			if (LOG.isTraceEnabled())
            	LOG.trace("fn:matches: can't use existing range index of type " + Type.getTypeName(indexType) + ". Need a string index.");
		    result = new ExtArrayNodeSet();
		    for(Iterator i = nodes.iterator(); i.hasNext(); ) {
		        NodeProxy node = (NodeProxy) i.next();
		        if (match(node.getStringValue(), pattern, flags))
		            result.add(node);
		    }          
		}
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
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
			pattern = RegexTranslator.translate(pattern, true);
		} catch (RegexSyntaxException e) {
			throw new XPathException(getASTNode(), "Conversion from XPath2 to Java regular expression " +
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
        String string = stringArg.getStringValue();
		String pattern = translateRegexp(getArgument(1).eval(contextSequence, contextItem).getStringValue());
        
		int flags = 0;
        if(getSignature().getArgumentCount() == 3)
            flags = parseFlags(getArgument(2).eval(contextSequence, contextItem).getStringValue());
        
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
			
		} catch (PatternSyntaxException e) {
			throw new XPathException("Invalid regular expression: " + e.getMessage(), e);
		}
    }

    protected final static int parseFlags(String s) throws XPathException {
		int flags = 0;
		for(int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
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
					throw new XPathException("Invalid regular expression flag: " + ch);
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
            preselectResult = null;
    }
}
