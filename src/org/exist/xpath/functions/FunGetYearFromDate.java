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

import org.exist.dom.QName;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.DateValue;
import org.exist.xpath.value.IntegerValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunGetYearFromDate extends Function {

	public final static FunctionSignature signature =
			new FunctionSignature(
				new QName("get-year-from-date", BUILTIN_FUNCTION_NS),
				"Returns an xs:integer representing the year in the localized value of $a. The value may be negative.",
				new SequenceType[] {
					 new SequenceType(Type.DATE, Cardinality.ZERO_OR_ONE)
				},
				new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));
				
	/**
	 * @param context
	 * @param signature
	 */
	public FunGetYearFromDate(StaticContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
		throws XPathException {
		Sequence arg = getArgument(0).eval(contextSequence, contextItem);
		if (arg.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		DateValue date = (DateValue)arg.itemAt(0);
		return new IntegerValue(date.getPart(DateValue.YEAR), Type.INTEGER);
	}
}
