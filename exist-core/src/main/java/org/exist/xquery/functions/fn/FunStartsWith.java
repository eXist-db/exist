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
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunStartsWith extends CollatingFunction {
	
	protected static final String FUNCTION_DESCRIPTION =
		"Returns an xs:boolean indicating whether or not " +
		"the value of $source starts with a sequence of collation " +
		"units that provides a minimal match to the collation " +
		"units of $prefix according to the collation that is used.\n\n" +
		"Note:\n\n" +
		"\"Minimal match\" is defined in [Unicode Collation Algorithm].\n\n" +
		"If the value of $source or $prefix is the empty sequence, or " +
		"contains only ignorable collation units, it is interpreted " +
		"as the zero-length string.\n\nIf the value of $prefix is the " +
		"zero-length string, then the function returns true. If the " +
		"value of $source is the zero-length string and the value of " +
		"$prefix is not the zero-length string, then the function " +
		"returns false.\n\n" +
		"The collation used by the invocation of this function is " +
		"determined according to the rules in 7.3.1 Collations. " +
		"If the specified collation does not support collation " +
		"units an error may be raised [err:FOCH0004]. ";

	protected static final FunctionParameterSequenceType ARG1_PARAM = new FunctionParameterSequenceType("source", Type.STRING, Cardinality.ZERO_OR_ONE, "The source string");
	protected static final FunctionParameterSequenceType ARG2_PARAM = new FunctionParameterSequenceType("prefix", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to determine if is a prefix of $source");
	protected static final FunctionParameterSequenceType COLLATION_PARAM = new FunctionParameterSequenceType("collation-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collation URI");
	protected static final FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if $prefix is a prefix of the string $source");
	
    public final static FunctionSignature signatures[] = {
	new FunctionSignature (
			       new QName("starts-with", Function.BUILTIN_FUNCTION_NS),
			       FUNCTION_DESCRIPTION,
			       new SequenceType[] { ARG1_PARAM, ARG2_PARAM },
			       RETURN_TYPE),
	new FunctionSignature (
			       new QName("starts-with", Function.BUILTIN_FUNCTION_NS),
			       FUNCTION_DESCRIPTION + THIRD_REL_COLLATION_ARG_EXAMPLE,
			       new SequenceType[] { ARG1_PARAM, ARG2_PARAM, COLLATION_PARAM },
			       RETURN_TYPE)
    };
					
    public FunStartsWith(XQueryContext context, FunctionSignature signature) {
	super(context, signature);
    }
	
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        
	if(contextItem != null)
	    {contextSequence = contextItem.toSequence();}

        Sequence result;
	final String s1 = getArgument(0).eval(contextSequence).getStringValue();
	final String s2 = getArgument(1).eval(contextSequence).getStringValue();        
	if(s1.length() == 0 || s2.length() == 0)
            {result = Sequence.EMPTY_SEQUENCE;}
        else {
	    final Collator collator = getCollator(contextSequence, contextItem, 3);
	    if(Collations.startsWith(collator, s1, s2))
                {result = BooleanValue.TRUE;}
	    else
                {result = BooleanValue.FALSE;}
        }

        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);} 
        
        return result;
    }
}
