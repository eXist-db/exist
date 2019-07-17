/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.fn;

import java.util.List;
import java.util.regex.PatternSyntaxException;

import org.exist.dom.QName;
import org.exist.util.PatternFactory;
import org.exist.xquery.Atomize;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.DynamicCardinalityCheck;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.Error;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import static org.exist.xquery.regex.RegexUtil.*;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FunReplace extends FunMatches {
	
	private static final String FUNCTION_DESCRIPTION_3_PARAM =
        "The function returns the xs:string that is obtained by replacing each non-overlapping substring " +
        "of $input that matches the given $pattern with an occurrence of the $replacement string.\n\n";
	private static final String FUNCTION_DESCRIPTION_4_PARAM =
        "The function returns the xs:string that is obtained by replacing each non-overlapping substring " +
        "of $input that matches the given $pattern with an occurrence of the $replacement string.\n\n" + 
        "The $flags argument is interpreted in the same manner as for the fn:matches() function.\n\n" +
        "Calling the four argument version with the $flags argument set to a " +
        "zero-length string gives the same effect as using the three argument version.\n\n";
	private static final String FUNCTION_DESCRIPTION_COMMON = 
        "If $input is the empty sequence, it is interpreted as the zero-length string.\n\nIf two overlapping " +
        "substrings of $input both match the $pattern, then only the first one (that is, the one whose first " +
        "character comes first in the $input string) is replaced.\n\nWithin the $replacement string, a variable " +
        "$N may be used to refer to the substring captured by the Nth parenthesized sub-expression in the " +
        "regular expression. For each match of the pattern, these variables are assigned the value of the " +
        "content matched by the relevant sub-expression, and the modified replacement string is then " +
        "substituted for the characters in $input that matched the pattern. $0 refers to the substring " +
        "captured by the regular expression as a whole.\n\nMore specifically, the rules are as follows, " +
        "where S is the number of parenthesized sub-expressions in the regular expression, and N is the " +
        "decimal number formed by taking all the digits that consecutively follow the $ character:\n\n" +
        "1.  If N=0, then the variable is replaced by the substring matched by the regular expression as a whole.\n\n" +
        "2.  If 1<=N<=S, then the variable is replaced by the substring captured by the Nth parenthesized " +
        "sub-expression. If the Nth parenthesized sub-expression was not matched, then the variable " +
        "is replaced by the zero-length string.\n\n" +
        "3.  If S<N<=9, then the variable is replaced by the zero-length string.\n\n" +
        "4.  Otherwise (if N>S and N>9), the last digit of N is taken to be a literal character to be " +
        "included \"as is\" in the replacement string, and the rules are reapplied using the number N " +
        "formed by stripping off this last digit.";

	protected static final FunctionParameterSequenceType INPUT_ARG = new FunctionParameterSequenceType("input", Type.STRING, Cardinality.ZERO_OR_ONE, "The input string");
	protected static final FunctionParameterSequenceType PATTERN_ARG = new FunctionParameterSequenceType("pattern", Type.STRING, Cardinality.EXACTLY_ONE, "The pattern to match");
	protected static final FunctionParameterSequenceType REPLACEMENT_ARG = new FunctionParameterSequenceType("replacement", Type.STRING, Cardinality.EXACTLY_ONE, "The string to replace the pattern with");
	protected static final FunctionParameterSequenceType FLAGS_ARG = new FunctionParameterSequenceType("flags", Type.STRING, Cardinality.EXACTLY_ONE, "The flags");
	protected static final FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the altered string");
			
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("replace", Function.BUILTIN_FUNCTION_NS),
			FUNCTION_DESCRIPTION_3_PARAM + FUNCTION_DESCRIPTION_COMMON,
			new SequenceType[] { INPUT_ARG, PATTERN_ARG, REPLACEMENT_ARG },
			RETURN_TYPE
		),
		new FunctionSignature(
			new QName("replace", Function.BUILTIN_FUNCTION_NS),
			FUNCTION_DESCRIPTION_4_PARAM + FUNCTION_DESCRIPTION_COMMON,
			new SequenceType[] { INPUT_ARG, PATTERN_ARG, REPLACEMENT_ARG, FLAGS_ARG },
			RETURN_TYPE
		)
	};

	public FunReplace(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#setArguments(java.util.List)
	 */
	public void setArguments(List<Expression> arguments) throws XPathException {
	    steps.clear();
        Expression arg = arguments.get(0);
        arg = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_ONE, arg,
                new Error(Error.FUNC_PARAM_CARDINALITY, "1", mySignature));    
        if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
            {arg = new Atomize(context, arg);}
        steps.add(arg);
        
        arg = arguments.get(1);
        arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                new Error(Error.FUNC_PARAM_CARDINALITY, "2", mySignature)); 
        if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
            {arg = new Atomize(context, arg);}
        steps.add(arg);
        
        arg = arguments.get(2);
        arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                new Error(Error.FUNC_PARAM_CARDINALITY, "3", mySignature)); 
        if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
            {arg = new Atomize(context, arg);}
        steps.add(arg);
        
        if (arguments.size() == 4) {
            arg = arguments.get(3);
            arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                    new Error(Error.FUNC_PARAM_CARDINALITY, "4", mySignature)); 
            if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
                {arg = new Atomize(context, arg);}
            steps.add(arg);            
        }
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
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

		Sequence result;
		final Sequence stringArg = getArgument(0).eval(contextSequence, contextItem);
		if (stringArg.isEmpty()) {
			result = StringValue.EMPTY_STRING;
		} else {
			final int flags;
			if (getSignature().getArgumentCount() == 4) {
				flags =	parseFlags(this, getArgument(3).eval(contextSequence, contextItem).getStringValue());
			} else {
				flags = 0;
			}

    		final String string = stringArg.getStringValue();
    		final Sequence patternSeq = getArgument(1).eval(contextSequence, contextItem);

			final Sequence replaceSeq = getArgument(2).eval(contextSequence, contextItem);
			String replace = replaceSeq.getStringValue();

    		final String pattern;
			if(hasLiteral(flags)) {
				// no need to change anything in the pattern
				pattern = patternSeq.getStringValue();

				// however, $ and \ now have no special significance
				replace = replace
						.replace("\\", "\\\\")
						.replace("$", "\\$");
			} else {
				pattern = translateRegexp(this, patternSeq.getStringValue(), hasIgnoreWhitespace(flags), hasCaseInsensitive(flags));
			}

            //An error is raised [err:FORX0004] if the value of $replacement contains a "$" character that is not immediately followed by a digit 0-9 and not immediately preceded by a "\".
            //An error is raised [err:FORX0004] if the value of $replacement contains a "\" character that is not part of a "\\" pair, unless it is immediately followed by a "$" character.
            for (int i = 0 ; i < replace.length() ; i++) {
            	//Commented out : this seems to be a total non sense
            	/*
            	if (replace.charAt(i) == '$') {
            		try {
            			if (!(replace.charAt(i - 1) == '\\' || Character.isDigit(replace.charAt(i + 1))))
            				throw new XPathException(this, ErrorCodes.FORX0004, "The value of $replacement contains a '$' character that is not immediately followed by a digit 0-9 and not immediately preceded by a '\\'.");
            		//Handle index exceptions
            		} catch (Exception e){
            			throw new XPathException(this, ErrorCodes.FORX0004, "The value of $replacement contains a '$' character that is not immediately followed by a digit 0-9 and not immediately preceded by a '\\'.");
            		}
            	}
            	*/
            	if (replace.charAt(i) == '\\') {
            		try {
            			if (!(replace.charAt(i + 1) == '\\' || replace.charAt(i + 1) == '$')) {
            				throw new XPathException(this, ErrorCodes.FORX0004, "The value of $replacement contains a '\\' character that is not part of a '\\\\' pair, unless it is immediately followed by a '$' character.", replaceSeq);
            			}
            			i++;
            		//Handle index exceptions
            		} catch (final Exception e){
            			throw new XPathException(this, ErrorCodes.FORX0004, "The value of $replacement contains a '\\' character that is not part of a '\\\\' pair, unless it is immediately followed by a '$' character.", replaceSeq);
            		}
            	}
            	
            }

    		try {
    			if (pat == null || (!pattern.equals(pat.pattern())) || flags != pat.flags()) {
    				pat = PatternFactory.getInstance().getPattern(pattern, flags);
                    matcher = pat.matcher(string);
                } else {
                    matcher.reset(string);
                }
                final String r = matcher.replaceAll(replace);
    			result = new StringValue(r);
    		} catch (final PatternSyntaxException e) {
    			throw new XPathException(this, ErrorCodes.FORX0001, "Invalid regular expression: " + e.getMessage(), patternSeq, e);
    		} catch (final IndexOutOfBoundsException e) {
    		    throw new XPathException(this, ErrorCodes.FORX0001, e.getMessage(), patternSeq, e);
       		//Some JVMs seem to raise this one
    		} catch (final IllegalArgumentException e) {
    			throw new XPathException(this, ErrorCodes.FORX0004, "Invalid replace expression: " + e.getMessage(), replaceSeq, e);
            }
        }
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;   
        
	}

}
