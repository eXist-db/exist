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
package org.exist.xpath.functions;

import java.util.ArrayList;
import java.util.List;

import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Util;
import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xpath.Cardinality;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;
import org.exist.xpath.value.ValueSequence;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunTokenize extends FunMatches {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("tokenize", BUILTIN_FUNCTION_NS),
			"This function breaks the input string $a into a sequence of strings, "
				+ "treating any substring that matches pattern $b as a separator. The "
				+ "separators themselves are not returned.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
			new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE),
			true);

	/**
	 * @param context
	 */
	public FunTokenize(StaticContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence)
	 */
	public Sequence eval(DocumentSet docs, Sequence contextSequence, Item contextItem)
		throws XPathException {
		Sequence stringArg = getArgument(0).eval(docs, contextSequence, contextItem);
		if (stringArg.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		String string = stringArg.getStringValue();
		String pattern =
			getArgument(1).eval(docs, contextSequence, contextItem).getStringValue();
		int flags = 0;
		if (getArgumentCount() == 3)
			flags =
				parseFlags(
					getArgument(2)
						.eval(docs, contextSequence, contextItem)
						.getStringValue());
		try {
			if (prevPattern == null
				|| (!pattern.equals(prevPattern))
				|| flags != prevFlags)
				pat = compiler.compile(pattern, flags);
			prevPattern = pattern;
			prevFlags = flags;
			List result = new ArrayList(10);
			Util.split(result, matcher, pat, string);
			ValueSequence r = new ValueSequence();
			for(int i = 0; i < result.size(); i++)
				r.add(new StringValue((String)result.get(i)));
			return r;
		} catch (MalformedPatternException e) {
			throw new XPathException("Invalid regular expression: " + e.getMessage(), e);
		}
	}

}
