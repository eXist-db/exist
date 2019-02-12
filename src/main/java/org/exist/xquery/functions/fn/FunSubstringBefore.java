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

import com.ibm.icu.text.Collator;
import org.exist.dom.QName;
import org.exist.util.Collations;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
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

/**
 * Built-in function fn:substring-before($operand1 as xs:string?, $operand2 as xs:string?) as xs:string?
 *
 */
public class FunSubstringBefore extends CollatingFunction {

	protected static final FunctionParameterSequenceType COLLATOR_ARG = new FunctionParameterSequenceType("collation-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collation URI");
	protected static final FunctionParameterSequenceType SEARCH_ARG = new FunctionParameterSequenceType("search", Type.STRING, Cardinality.ZERO_OR_ONE, "The search string");
	protected static final FunctionParameterSequenceType SOURCE_ARG = new FunctionParameterSequenceType("source", Type.STRING, Cardinality.ZERO_OR_ONE, "The input string");
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("substring-before", Function.BUILTIN_FUNCTION_NS),
			"Returns the substring of the value of $source that precedes the first occurrence " +
			"of a sequence of the value of $search. If the value of $source or $search is the empty " +
			"sequence it is interpreted as the zero-length string. If the value of " +
			"$search is the zero-length string, the zero-length string is returned. " +
			"If the value of $source does not contain a string that is equal to the value " +
			"of $search, the zero-length string is returned.",
			new SequenceType[] {
				 SOURCE_ARG,
				 SEARCH_ARG
				},
				new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the substring before $search")),
		new FunctionSignature(
				new QName("substring-before", Function.BUILTIN_FUNCTION_NS),
			"Returns the substring of the value of $source that precedes the first occurrence " +
			"of a sequence of the value of $search in the collation $collation-uri. If the value of $source or $search is the empty " +
			"sequence it is interpreted as the zero-length string. If the value of " +
			"$search is the zero-length string, the zero-length string is returned. " +
			"If the value of $source does not contain a string that is equal to the value " +
			"of $search, the zero-length string is returned. " +
                THIRD_REL_COLLATION_ARG_EXAMPLE,
				new SequenceType[] {
					 SOURCE_ARG,
					 SEARCH_ARG,
					 COLLATOR_ARG
				},
				new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the substring before $search"))
	};

	public FunSubstringBefore(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                    "DEPENDENCIES",
                    Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT ITEM", contextItem.toSequence());}
        }
        
		final Expression arg0 = getArgument(0);
		final Expression arg1 = getArgument(1);

		if (contextItem != null)
			{contextSequence = contextItem.toSequence();}

		final Sequence seq1 = arg0.eval(contextSequence);
		final Sequence seq2 = arg1.eval(contextSequence);

        String value;
        String cmp;
        Sequence result;
		if (seq1.isEmpty()) {
            value =  StringValue.EMPTY_STRING.getStringValue();
        } else {
            value = seq1.getStringValue();
        }
        
        if (seq2.isEmpty()) {
            cmp = StringValue.EMPTY_STRING.getStringValue();
        } else {
            cmp = seq2.getStringValue();
        }

        if (cmp.length() == 0)
            {result = StringValue.EMPTY_STRING;}
        else {
            final Collator collator = getCollator(contextSequence, contextItem, 3);
            final int p = Collations.indexOf(collator, value, cmp);
            if (p == Constants.STRING_NOT_FOUND)
                {result = StringValue.EMPTY_STRING;}
            else
                {result = new StringValue(value.substring(0, p));}        			
        }
        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);}
        
        return result;        
	}
}
