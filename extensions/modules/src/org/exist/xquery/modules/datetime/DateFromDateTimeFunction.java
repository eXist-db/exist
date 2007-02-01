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
 *  $Id: EchoFunction.java 3063 2006-04-05 20:49:44Z brihaye $
 */
package org.exist.xquery.modules.datetime;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class DateFromDateTimeFunction extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("date-from-dateTime", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
			"Returns the xs:date portion of the xs:dateTime given in $a",
			new SequenceType[] { 
				new SequenceType(Type.DATE_TIME, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.DATE, Cardinality.EXACTLY_ONE));

	public DateFromDateTimeFunction(XQueryContext context)
	{
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{	
		return args[0].itemAt(0).convertTo(Type.DATE);
	}
}
