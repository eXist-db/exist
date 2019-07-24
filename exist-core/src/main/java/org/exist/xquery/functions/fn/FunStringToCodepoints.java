/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Team
 *
 *  http://exist-db.org
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

import org.exist.dom.QName;
import org.exist.util.XMLChar;
import org.exist.xquery.BasicFunction;
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
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

public class FunStringToCodepoints extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
				new QName("string-to-codepoints", Function.BUILTIN_FUNCTION_NS),
				"Returns the sequence of unicode code points that constitute an xs:string. If $arg is a zero-length " +
				"string or the empty sequence, the empty sequence is returned.",
				new SequenceType[] {
						new FunctionParameterSequenceType("arg", Type.STRING, Cardinality.ZERO_OR_ONE, "The input string"),
				},
				new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_MORE, "the sequence of code points"));
	
	public FunStringToCodepoints(XQueryContext context) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)	throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
       }    
       
        Sequence result;
        if (args[0].isEmpty())
			{result =  Sequence.EMPTY_SEQUENCE;}
        else {
    		result = getCodePoints(args[0].getStringValue());
        }
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        return result;             
        
	}

    /**
     * The method <code>getCodePoints</code>
     *
     * @param s a <code>String</code> value
     * @return a <code>ValueSequence</code> value
     */
    public static ValueSequence getCodePoints(final String s) {
        final ValueSequence codepoints = new ValueSequence();
        char ch;
        IntegerValue next;
        for (int i = 0; i < s.length(); i++) {
            ch = s.charAt(i);
            if (XMLChar.isSurrogate(ch)) {
                final int supp = XMLChar.supplemental(ch, s.charAt(++i));
                next = new IntegerValue(supp);
            } else {
                next = new IntegerValue((int) ch);
            }
            codepoints.add(next);
        }
        return codepoints;
    }

    /** The method <code>getCodePointCount</code>
     *
     * @param s a <code>String</code> value
     * @return a <code>ValueSequence</code> value
     */
    public static int getCodePointCount(final String s) {
        int count = 0;
        char ch;
        for (int i = 0; i < s.length(); i++) {
            ch = s.charAt(i);
            if (XMLChar.isSurrogate(ch)) {
                i++;
            }
            count++;
        }
        return count;
    }

    /**
     * The method <code>subSequence</code>
     *
     * @param seq a <code>ValueSequence</code> value
     * @param start an <code>int</code> value
     * @return a <code>ValueSequence</code> value
     */
    public static String subSequence(final ValueSequence seq, final int start) {
        final StringBuilder substring = new StringBuilder(seq.getItemCount());
        for (int i = start >= 0 ? start : 0; i < seq.getItemCount(); i++) {
            final int ch = ((IntegerValue) seq.itemAt(i)).getInt();
            if (XMLChar.isSupplemental(ch)) {
                substring.append(XMLChar.highSurrogate(ch));
                substring.append(XMLChar.lowSurrogate(ch));
            } else {
                substring.append((char) ch);
            }
        }
        return substring.toString();
    }

    /**
     * The method <code>subSequence</code>
     *
     * @param seq a <code>ValueSequence</code> value
     * @param start an <code>int</code> value
     * @param end an <code>int</code> value
     * @return a <code>ValueSequence</code> value
     * @throws XPathException if an error occurs
     */
    public static String subSequence(final ValueSequence seq, final int start, final int end) 
        throws XPathException {
        final StringBuilder substring = new StringBuilder(seq.getItemCount());
        if (seq.getItemCount() < end) {
            return subSequence(seq, start);
        }

        for (int i = start >= 0 ? start : 0; i < end; i++) {
            final int ch = ((IntegerValue) seq.itemAt(i)).getInt();
            if (XMLChar.isSupplemental(ch)) {
                substring.append(XMLChar.highSurrogate(ch));
                substring.append(XMLChar.lowSurrogate(ch));
            } else {
                substring.append((char) ch);
            }
        }
        return substring.toString();
    }

    /**
     * The method <code>codePointToString</code>
     *
     * @param value an <code>IntegerValue</code> value
     *
     * @return a <code>String</code> value
     */
    public static String codePointToString(final IntegerValue value) {
        final StringBuilder string = new StringBuilder(2);
        final int intValue = value.getInt();
        if (XMLChar.isSupplemental(intValue)) {
            string.append(XMLChar.highSurrogate(intValue));
            string.append(XMLChar.lowSurrogate(intValue));
        } else {
            string.append((char) intValue);
        }
        return string.toString();
    }

    /**
     * The method <code>indexOf</code>
     *
     * @param seq a <code>ValueSequence</code> value
     * @param value an <code>IntegerValue</code> value
     * @return a <code>int</code> value
     */
    public static int indexOf(final ValueSequence seq, final IntegerValue value) {
        final int index = Constants.STRING_NOT_FOUND;
        final @SuppressWarnings("unused")
		char ch;
        for (int i = 0; i < seq.getItemCount(); i++) {
            if (value.compareTo(seq.itemAt(i)) == Constants.EQUAL) {
                return i;
            }
        }
        return index;
    }

}
