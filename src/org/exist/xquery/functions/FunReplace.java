/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
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
package org.exist.xquery.functions;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.exist.dom.QName;
import org.exist.xquery.Atomize;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.DynamicCardinalityCheck;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.Error;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunReplace extends FunMatches {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("replace", Function.BUILTIN_FUNCTION_NS),
			"The function returns the xs:string that is obtained by replacing all non-overlapping "
				+ "substrings of $a that match the given pattern $b with an occurrence of the $c replacement string.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
		),
		new FunctionSignature(
			new QName("replace", Function.BUILTIN_FUNCTION_NS),
			"The function returns the xs:string that is obtained by replacing all non-overlapping "
				+ "substrings of $a that match the given pattern $b with an occurrence of the $c replacement string.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
		)
	};

	/**
	 * @param context
	 */
	public FunReplace(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#setArguments(java.util.List)
	 */
	public void setArguments(List arguments) throws XPathException {
        Expression arg = (Expression) arguments.get(0);
        arg = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_ONE, arg,
                new Error(Error.FUNC_PARAM_CARDINALITY, "1", mySignature));    
        if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
            arg = new Atomize(context, arg);
        steps.add(arg);
        
        arg = (Expression) arguments.get(1);
        arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                new Error(Error.FUNC_PARAM_CARDINALITY, "2", mySignature)); 
        if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
            arg = new Atomize(context, arg);
        steps.add(arg);
        
        arg = (Expression) arguments.get(2);
        arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                new Error(Error.FUNC_PARAM_CARDINALITY, "3", mySignature)); 
        if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
            arg = new Atomize(context, arg);
        steps.add(arg);
        
        if (arguments.size() == 4) {
            arg = (Expression) arguments.get(3);
            arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                    new Error(Error.FUNC_PARAM_CARDINALITY, "4", mySignature)); 
            if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
                arg = new Atomize(context, arg);
            steps.add(arg);            
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

        Sequence result;
		Sequence stringArg = getArgument(0).eval(contextSequence, contextItem);
		if (stringArg.isEmpty())
            result = StringValue.EMPTY_STRING;
        else {        
    		String string = stringArg.getStringValue();
    		String pattern =
    			translateRegexp(getArgument(1).eval(contextSequence, contextItem).getStringValue());
            String replace =
    			((StringValue) getArgument(2).eval(contextSequence, contextItem)).getStringValue(true);
            //An error is raised [err:FORX0004] if the value of $replacement contains a "$" character that is not immediately followed by a digit 0-9 and not immediately preceded by a "\".
            //An error is raised [err:FORX0004] if the value of $replacement contains a "\" character that is not part of a "\\" pair, unless it is immediately followed by a "$" character.            
            for (int i = 0 ; i < replace.length() ; i++) {
            	//Commented out : this seems to be a total non sense
            	/*
            	if (replace.charAt(i) == '$') {
            		try {
            			if (!(replace.charAt(i - 1) == '\\' || Character.isDigit(replace.charAt(i + 1))))
            				throw new XPathException("err:FORX0004 The value of $replacement contains a '$' character that is not immediately followed by a digit 0-9 and not immediately preceded by a '\\'.");
            		//Handle index exceptions
            		} catch (Exception e){
            			throw new XPathException("err:FORX0004 The value of $replacement contains a '$' character that is not immediately followed by a digit 0-9 and not immediately preceded by a '\\'.");
            		}
            	}
            	*/
            	if (replace.charAt(i) == '\\') {
            		try {
            			if (!(replace.charAt(i + 1) == '\\' || replace.charAt(i + 1) == '$'))
            				throw new XPathException("err:FORX0004 The value of $replacement contains a '\\' character that is not part of a '\\\\' pair, unless it is immediately followed by a '$' character.");
            			i++;
            		//Handle index exceptions
            		} catch (Exception e){
            			throw new XPathException("err:FORX0004 The value of $replacement contains a '\\' character that is not part of a '\\\\' pair, unless it is immediately followed by a '$' character.");
            		}
            	}
            	
            }

            int flags = 0;
    		if (getSignature().getArgumentCount() == 4)
    			flags =	parseFlags(getArgument(3).eval(contextSequence, contextItem).getStringValue());
    		try {
    			if (pat == null || (!pattern.equals(pat.pattern())) || flags != pat.flags()) {
    				pat = Pattern.compile(pattern, flags);
                    matcher = pat.matcher(string);
                } else {
                    matcher.reset(string);
                }
                String r = matcher.replaceAll(replace);
    			result = new StringValue(r);
    		} catch (PatternSyntaxException e) {
    			throw new XPathException(getASTNode(), "Invalid regular expression: " + e.getMessage(), e);
    		} catch (IndexOutOfBoundsException e) {
    		    throw new XPathException(getASTNode(), e.getMessage(), e);
       		//Some JVMs seem to raise this one
    		} catch (IllegalArgumentException e) {
    			throw new XPathException(getASTNode(), "Invalid replace expression: " + e.getMessage(), e);
            }
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;   
        
	}

}
