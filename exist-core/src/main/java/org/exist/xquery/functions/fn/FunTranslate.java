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

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.ValueSequence;
import org.exist.xquery.value.Type;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FunTranslate extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("translate", Function.BUILTIN_FUNCTION_NS),
			"Returns the value of $arg modified so that every character in the value of $arg that occurs at some position N in the " +
			"value of $map has been replaced by the character that occurs at position N in the value of $trans.\n\n" +
			"If the value of $arg is the empty sequence, the zero-length string is returned.\n\n" +
			"Every character in the value of $arg that does not appear in the value of $map is unchanged.\n\n" +
			"Every character in the value of $arg that appears at some position M in the value of $map, where the value of " +
			"$trans is less than M characters in length, is omitted from the returned value. If $map is the zero-length " +
			"string $arg is returned.\n\nIf a character occurs more than once in $map, then the first occurrence determines " +
			"the replacement character. If $trans is longer than $map, the excess characters are ignored.\n\n" +
			"i.e. fn:translate(\"bar\",\"abc\",\"ABC\") returns \"BAr\"",
			new SequenceType[] { 
				new FunctionParameterSequenceType("arg", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to be translated"),
				new FunctionParameterSequenceType("map", Type.STRING, Cardinality.EXACTLY_ONE, "The map string"),
				new FunctionParameterSequenceType("trans", Type.STRING, Cardinality.EXACTLY_ONE, "The translation string")
			},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the translated string"));

	public FunTranslate(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
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
        final Sequence seq = getArgument(0).eval(contextSequence);
		if(seq.isEmpty())
            {result = StringValue.EMPTY_STRING;}
        else {
    		final ValueSequence arg = FunStringToCodepoints.getCodePoints(seq.getStringValue());
    		final ValueSequence mapStr = FunStringToCodepoints.getCodePoints(getArgument(1).eval(contextSequence).getStringValue());
            final ValueSequence transStr = FunStringToCodepoints.getCodePoints(getArgument(2).eval(contextSequence).getStringValue());
    		int p;
    		IntegerValue ch;
    		final StringBuilder buf = new StringBuilder(arg.getItemCount());
    		for(int i = 0; i < arg.getItemCount(); i++) {
    			ch = (IntegerValue) arg.itemAt(i);
    			p = FunStringToCodepoints.indexOf(mapStr, ch);
    			if(p == Constants.STRING_NOT_FOUND) {
                    buf.append(FunStringToCodepoints.codePointToString(ch));
                } else {
    				if (p < transStr.getItemCount())
    					{buf.append(FunStringToCodepoints.codePointToString((IntegerValue) transStr.itemAt(p)));}
    			}
    		}
            result = new StringValue(buf.toString());
        }

        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}        
        
        return result; 
	}
}
