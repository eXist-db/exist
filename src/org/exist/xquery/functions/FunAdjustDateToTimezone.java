/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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

import java.util.TimeZone;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.DayTimeDurationValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class FunAdjustDateToTimezone extends BasicFunction {

	public final static FunctionSignature fnAdjustDateToTimezone[] = {
		new FunctionSignature(
			new QName("adjust-date-to-timezone", Module.BUILTIN_FUNCTION_NS),
			"Adjusts an xs:date value to the implicit timezone of the current locale.",
			new SequenceType[] { 
					new SequenceType(Type.DATE, Cardinality.ZERO_OR_ONE)
			},
			new SequenceType(Type.DATE, Cardinality.ZERO_OR_ONE)),
		new FunctionSignature(
				new QName("adjust-date-to-timezone", Module.BUILTIN_FUNCTION_NS),
				"Adjusts an xs:date value to a specific timezone, or to no timezone at all. " +
				"If $b is the empty sequence, returns an xs:date without a timezone.",
				new SequenceType[] { 
						new SequenceType(Type.DATE, Cardinality.ZERO_OR_ONE),
						new SequenceType(Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE)
				},
				new SequenceType(Type.DATE, Cardinality.ZERO_OR_ONE))
	};
	
	/**
	 * @param context
	 * @param signature
	 */
	public FunAdjustDateToTimezone(XQueryContext context,
			FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		if(args[0].getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		DateValue date = (DateValue) args[0].itemAt(0);
		int tzOffset;
		if(getSignature().getArgumentCount() == 2) {
			if(args[1].getLength() == 0)
				tzOffset = 0;
			else {
				DayTimeDurationValue duration = (DayTimeDurationValue)args[1].itemAt(0);
				tzOffset = (int)(duration.getValueInMilliseconds() / 60000);
			}
		} else {
			TimeZone zone = TimeZone.getDefault();
			tzOffset = (int)(zone.getRawOffset() / 60000);
		}
		return date.adjustToTimezone(tzOffset);
	}
}
