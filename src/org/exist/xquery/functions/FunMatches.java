/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.exist.EXistException;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.RegexTranslator;
import org.exist.xquery.util.RegexTranslator.RegexSyntaxException;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implements the fn:matches() function.
 * 
 * Based on the jakarta ORO package for regular expression support.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunMatches extends Function {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("matches", Module.BUILTIN_FUNCTION_NS),
			"Returns true if the first argument string matches the regular expression specified " +
			"by the second argument. This function is optimized internally if a range index of type xs:string " +
			"is defined on the nodes passed to the first argument.",
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				 new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
		),
		new FunctionSignature(
			new QName("matches", Module.BUILTIN_FUNCTION_NS),
			"Returns true if the first argument string matches the regular expression specified " +
			"by the second argument. This function is optimized internally if a range index of type xs:string " +
			"is defined on the nodes passed to the first argument.",
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				 new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				 new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
		)
	};
	
	protected Matcher matcher = null;
	protected Pattern pat = null;
	
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
		steps.addAll(arguments);
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Function#getDependencies()
     */
    public int getDependencies() {
        final Expression stringArg = getArgument(0);
        final Expression patternArg = getArgument(1);
        if(Type.subTypeOf(stringArg.returnsType(), Type.NODE) &&
            (stringArg.getDependencies() & Dependency.CONTEXT_ITEM) == 0 &&
            (patternArg.getDependencies() & Dependency.CONTEXT_ITEM) == 0) {
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
    
    public void analyze(Expression parent, int flags) throws XPathException {
    	//  call analyze for each argument
        inPredicate = (flags & IN_PREDICATE) > 0;
        for(int i = 0; i < getArgumentCount(); i++) {
            getArgument(i).analyze(this, flags);
        }
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
	    if(contextItem != null)
			contextSequence = contextItem.toSequence();
	    
		Sequence input = getArgument(0).eval(contextSequence, contextItem);
		if(input.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
        
		if(inPredicate && (getDependencies() & Dependency.CONTEXT_ITEM) == 0) {
		    return evalWithIndex(contextSequence, contextItem, input);
        } else {
		    return evalGeneric(contextSequence, contextItem, input);
        }
	}

	/**
     * @param contextSequence
     * @param contextItem
     * @param stringArg
     * @return
	 * @throws XPathException
     */
    private Sequence evalWithIndex(Sequence contextSequence, Item contextItem, Sequence input) throws XPathException {
        String pattern = translateRegexp(getArgument(1).eval(contextSequence, contextItem).getStringValue());
        NodeSet nodes = input.toNodeSet();
        
        int flags = 0; 
        boolean caseSensitive = true;
        if(getSignature().getArgumentCount() == 3) {
            String flagsArg = getArgument(2).eval(contextSequence, contextItem).getStringValue();
            caseSensitive = flagsArg.indexOf('i') < 0;
            flags = parseFlags(flagsArg);
        }
        
        // get the type of a possible index
		int indexType = nodes.getIndexType();
		if(Type.subTypeOf(indexType, Type.STRING)) {
			LOG.debug("Using xs:string index for fn:matches. Regex: " + pattern);
		    DocumentSet docs = nodes.getDocumentSet();
		    try {
				return context.getBroker().getValueIndex().match(docs, nodes, pattern, 
						DBBroker.MATCH_REGEXP, flags, caseSensitive);
			} catch (EXistException e) {
				throw new XPathException(getASTNode(), e.getMessage(), e);
			}
		} else {
		    ExtArrayNodeSet result = new ExtArrayNodeSet();
		    for(Iterator i = nodes.iterator(); i.hasNext(); ) {
		        NodeProxy node = (NodeProxy) i.next();
		        if(match(node.getStringValue(), pattern, flags))
		            result.add(node);
		    }
		    return result;
		}
    }

	/**
	 * Translates the regular expression from XPath2 syntax to java regex
	 * syntax.
	 * 
	 * @param pattern
	 * @return
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
     * @return
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
     * @return
     * @throws XPathException
     */
    private boolean match(String string, String pattern, int flags) throws XPathException {
        try {
			if(pat == null || (!pattern.equals(pat.pattern())) || flags != pat.flags()) {
				pat = Pattern.compile(pattern, flags);
                matcher = pat.matcher(string);
            } else {
                matcher.reset(string);
            }
            
			if(matcher.find())
				return true;
			else
				return false;
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
}
