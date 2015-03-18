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
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.Date;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class TimestampToDateTime extends BasicFunction {

	protected static final Logger logger = LogManager.getLogger(TimestampToDateTime.class);

	public final static FunctionSignature signature[] = 
	{
		new FunctionSignature(
			new QName("timestamp-to-datetime", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
			"Return the date-time from value of number of UTC milliseconds since the Epoch. ",
			new SequenceType[] {
				new FunctionParameterSequenceType("ms", Type.UNSIGNED_LONG, Cardinality.EXACTLY_ONE, "The number of UTC milliseconds since the Epoch."),
			},
			new FunctionReturnSequenceType(Type.DATE, Cardinality.EXACTLY_ONE, "the xs:dateTime")
		)
	};
			

	public TimestampToDateTime(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

		IntegerValue value = (IntegerValue) args[0].itemAt(0);
		Date date = new Date(value.getLong());
		
		return new DateTimeValue( date );
	}
}
