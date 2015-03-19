/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.modules.datetime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AbstractDateTimeValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class Timestamp extends BasicFunction {

	protected static final Logger logger = LogManager.getLogger(Timestamp.class);

	public final static FunctionSignature signature[] = 
	{
		new FunctionSignature(
			new QName("timestamp", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
			"The current time as UTC milliseconds since the Epoch. ",
			null,
			new FunctionReturnSequenceType(Type.UNSIGNED_LONG, Cardinality.EXACTLY_ONE, "milliseconds")
		),
		new FunctionSignature(
			new QName("timestamp", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
			"Return the number of UTC milliseconds since the Epoch for given date-time value. ",
			new SequenceType[] {
				new FunctionParameterSequenceType("date-time", Type.DATE_TIME, Cardinality.EXACTLY_ONE, "The dateTime to be converted."),
			},
			new FunctionReturnSequenceType(Type.UNSIGNED_LONG, Cardinality.EXACTLY_ONE, "milliseconds")
		)
	};
			

	public Timestamp(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		long ms;
		if (args.length == 1) {
	        AbstractDateTimeValue value = (AbstractDateTimeValue) args[0].itemAt(0);
	        ms = value.getTimeInMillis();
		} else {
	        ms = System.currentTimeMillis();
		}
		
		return new IntegerValue( ms );
	}
}
