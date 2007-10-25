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
 *  $Id: RequestParameter.java 2895 2006-03-15 19:11:34 +0000 (Wed, 15 Mar 2006) wolfgang_m $
 */
package org.exist.xquery.functions.request;

import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XPathUtil;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class GetParameter extends BasicFunction {
	
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName(
			"get-parameter",
			RequestModule.NAMESPACE_URI,
			RequestModule.PREFIX),
			"Returns the HTTP request parameter identified by $a. If the parameter could not be found, " +
			"the default value specified in $b is returned instead. Note: this function will not try to expand " +
			"predefined entities like &amp; or &lt;, so a &amp; passed through a parameter will indeed " +
			"be treated as an &amp; character.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)),
	
		new FunctionSignature(
			new QName(
			"get-parameter",
			RequestModule.NAMESPACE_URI,
			RequestModule.PREFIX),
			"Returns the HTTP request parameter identified by $a. If the parameter could not be found, " +
			"the default value specified in $b is returned instead. Note: this function will not try to expand " +
			"predefined entities like &amp; or &lt;, so a &amp; passed through a parameter will indeed " +
			"be treated as an &amp; character. If the  $c (failonerror) value is set to false, then the function " +
			"will not fail if there is no request in scope.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE),
				new SequenceType( Type.BOOLEAN, Cardinality.ZERO_OR_MORE )},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE))
		
	};
	
	public final static FunctionSignature deprecated =
		new FunctionSignature(
		new QName(
		"request-parameter",
		RequestModule.NAMESPACE_URI,
		RequestModule.PREFIX),
		"Returns the HTTP request parameter identified by $a. If the parameter could not be found, " +
		"the default value specified in $b is returned instead. Note: this function will not try to expand " +
		"predefined entities like &amp; or &lt;, so a &amp; passed through a parameter will indeed " +
		"be treated as an &amp; character.",
		new SequenceType[] {
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)},
		new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE),
		"Renamed to request:get-parameter.");
	
	public GetParameter(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		RequestModule myModule =
			(RequestModule) context.getModule(RequestModule.NAMESPACE_URI);
		
		boolean failOnError = true;
		
		if( getSignature().getArgumentCount() == 3 ) {
			failOnError = args[2].effectiveBooleanValue();
		}
		
		// request object is read from global variable $request
		Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);
		if (var == null || var.getValue() == null || var.getValue().getItemType() != Type.JAVA_OBJECT) {
			if( failOnError ) {
				throw new XPathException(getASTNode(), "Variable $request is not bound to an Java object.");
			} else {
				return args[1];
			}
		}
		
		// get parameters
		String param = args[0].getStringValue();
		
		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		if (value.getObject() instanceof RequestWrapper) {
			String[] values = ((RequestWrapper)value.getObject()).getParameterValues(param);
			if (values == null || values.length == 0) {
				return args[1];
			}
			if (values.length == 1) {
				return XPathUtil.javaObjectToXPath(values[0], null, false);
			} else {
				return XPathUtil.javaObjectToXPath(values, null, false);
			}
		} else {
			if( failOnError ) {
				throw new XPathException("Variable $request is not bound to a Request object.");
			} else {
				return args[1];				
			}
		}
	}
}
