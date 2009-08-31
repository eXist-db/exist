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
package org.exist.xquery.functions;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/** 
 * @see <a href="http://www.w3.org/TR/xpath-functions/#func-tokenize">http://www.w3.org/TR/xpath-functions/#func-tokenize</a>
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunTokenize extends FunMatches {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("tokenize", Function.BUILTIN_FUNCTION_NS),
			"Breaks the input string $input into a sequence of strings, "
				+ "treating any substring that matches pattern $pattern as a separator. The "
				+ "separators themselves are not returned.",
			new SequenceType[] {
				new FunctionParameterSequenceType("input", Type.STRING, Cardinality.ZERO_OR_ONE, "The input string"),
				new FunctionParameterSequenceType("pattern", Type.STRING, Cardinality.EXACTLY_ONE, "The tokenization pattern")},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "the token sequence")
		),
		new FunctionSignature(
			new QName("tokenize", Function.BUILTIN_FUNCTION_NS),
			"Breaks the input string $input into a sequence of strings, "
			+ "treating any substring that matches pattern $pattern as a separator using $flags, see http://www.w3.org/TR/xpath-functions/#flags. The "
			+ "separators themselves are not returned.",
			new SequenceType[] {
				new FunctionParameterSequenceType("input", Type.STRING, Cardinality.ZERO_OR_ONE, "The input string"),
				new FunctionParameterSequenceType("pattern", Type.STRING, Cardinality.EXACTLY_ONE, "The tokenization pattern"),
				new FunctionParameterSequenceType("flags", Type.STRING, Cardinality.EXACTLY_ONE, "The flags")},
				new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "the token sequence")
		)
	};

	/**
	 * @param context
	 */
	public FunTokenize(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/**
	 * @see org.exist.xquery.Expression#eval(Sequence, Item)
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
            result = Sequence.EMPTY_SEQUENCE;
        else {
            String string = stringArg.getStringValue();
            if (string.length() == 0 )
                result = Sequence.EMPTY_SEQUENCE;

            else {
            	//TODO : make fn:tokenize("Some unparsed <br> HTML <BR> text", "\s*<br>\s*", "") work  
                String pattern = translateRegexp(getArgument(1).eval(contextSequence, contextItem).getStringValue());
                if (Pattern.matches(pattern, "")) {
                	throw new XPathException(this, "FORX0003: regular expression could match empty string");
                }
		
        		int flags = 0;
        		if (getSignature().getArgumentCount() == 3)
        			flags = parseFlags(getArgument(2).eval(contextSequence, contextItem)
        						.getStringValue());
        		try {
        			if (pat == null || (!pattern.equals(pat.pattern())) || flags != pat.flags()) {
        				pat = Pattern.compile(pattern, flags);
                    }
                    String[] tokens = pat.split(string, -1);
                    result = new ValueSequence();
        			for (int i = 0; i < tokens.length; i++)
                        result.add(new StringValue(tokens[i]));        			
        		} catch (PatternSyntaxException e) {
        			throw new XPathException(this, "Invalid regular expression: " + e.getMessage(), e);
        		}
            }
        }

        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);        
        
        return result;    
	}

}
