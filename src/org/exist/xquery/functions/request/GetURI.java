/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.urlrewrite.XQueryURLRewrite;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class GetURI extends BasicFunction {

	protected static final Logger logger = LogManager.getLogger(GetURI.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("get-uri", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Returns the URI of the current request. This will be the original URI as received from " +
            "the client. Possible modifications done by the URL rewriter will not be visible.",
			null,
			new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE, "the URI of the request")),
        new FunctionSignature(
			new QName("get-effective-uri", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Returns the URI of the current request. If the request was forwarded via URL rewriting, " +
            "the function returns the effective, rewritten URI, not the original URI which was received " +
            "from the client.",
			null,
			new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE, "the URI of the request"))
    };

	/**
	 * @param context
	 */
	public GetURI(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		
		final RequestModule myModule = (RequestModule)context.getModule(RequestModule.NAMESPACE_URI);
		
		// request object is read from global variable $request
		final Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);
		if(var == null || var.getValue() == null)
			{throw new XPathException(this, "No request object found in the current XQuery context.");}
		if (var.getValue().getItemType() != Type.JAVA_OBJECT)
			{throw new XPathException(this, "Variable $request is not bound to an Java object.");}

		final JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		if (value.getObject() instanceof RequestWrapper) {
            final RequestWrapper wrapper = (RequestWrapper) value.getObject();
            final Object attr = wrapper.getAttribute(XQueryURLRewrite.RQ_ATTR_REQUEST_URI);
            if (attr == null || isCalledAs("get-effective-uri"))
			    {return new AnyURIValue(wrapper.getRequestURI());}
            else
                {return new AnyURIValue(attr.toString());}
		} else
			{throw new XPathException(this, "Variable $request is not bound to a Request object.");}
	}	
}
