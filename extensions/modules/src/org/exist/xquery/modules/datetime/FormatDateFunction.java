/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2006-2008 The eXist Project
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
package org.exist.xquery.modules.datetime;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class FormatDateFunction extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("format-date", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
			"Returns a xs:string of the xs:date in $a formatted according to the template specification in $b as in java.text.SimpleDateFormat.",
			new SequenceType[] { 
				new SequenceType(Type.DATE, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE));

	public FormatDateFunction(XQueryContext context)
	{
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{	
		DateValue d = (DateValue)args[0].itemAt(0);
		String dateFormat = args[1].itemAt(0).toString();
		
		SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
		
		
		GregorianCalendar cal = d.calendar.toGregorianCalendar();
		String formattedDate = sdf.format(cal.getTime());
		
		return new StringValue(formattedDate);
	}
}
