/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
package org.exist.xquery.functions.request;

import java.util.Date;

import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/** Returns a date-value in the given HTTP header : example :
<pre>
let $datetime := '2005-07-22T22:22:22'
let $dummy := request:set-date-header ( 'Last-Modified', xs:dateTime($datetime) )
return $datetime
</pre>
This Last-Modified header can be used by some servers, e.g. Cocoon, to update their cache.

 * @author J.M. Vanel (http://jmvanel.free.fr)
 */
public class SetDateHeader extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("set-date-header", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Sets an HTTP response header with the given name and date value",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.DATE_TIME, Cardinality.ZERO_OR_ONE) },
			new SequenceType(Type.ITEM, Cardinality.EMPTY));

	/**
	 * @param context
	 * @param signature
	 */
	public SetDateHeader(XQueryContext context) {
		super(context, signature);
	}

	/**
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		RequestModule myModule = (RequestModule)context.getModule(RequestModule.NAMESPACE_URI);
		
		String headerName = args[0].getStringValue();
		DateTimeValue dateValue = (DateTimeValue) args[1];
		Date date =	dateValue.getDate();
		
		// response object is read from global variable $response
		Variable var = myModule.resolveVariable(RequestModule.RESPONSE_VAR);
		if(var == null)
			throw new XPathException("No response object found in the current XQuery context.");
		if (var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $response is not bound to an Java object.");

		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		if (value.getObject() instanceof ResponseWrapper)
			((ResponseWrapper) value.getObject()).setDateHeader(headerName, date.getTime() );
		else
			throw new XPathException("Variable response is not bound to a response object.");
		return Sequence.EMPTY_SEQUENCE;
	}
	
}
