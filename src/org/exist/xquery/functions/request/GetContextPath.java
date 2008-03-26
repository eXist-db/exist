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
 *  $Id: RequestServername.java 2436 2006-01-07 21:47:15 +0000 (Sat, 07 Jan 2006) brihaye $
 */
package org.exist.xquery.functions.request;

import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class GetContextPath extends BasicFunction {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("get-context-path", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Returns the context path of the current request, i.e. the portion of the request URI that " +
            "indicates the context of the request.",
			null,
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)),
        new FunctionSignature(
			new QName("get-servlet-path", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Returns the servlet path of the current request, i.e. the portion of the request URI that " +
            "points to the servlet which is handling the request.\n"+
			"For example an xquery GET or POST to /some/path/myfile.xq/extra/path will return /some/path/myfile.xq when myfile.xq is executed.",
			null,
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE))
    };

    /**
	 * @param context
	 */
	public GetContextPath(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		RequestModule myModule = (RequestModule)context.getModule(RequestModule.NAMESPACE_URI);
		
		// request object is read from global variable $request
		Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);
		if(var == null || var.getValue() == null)
			throw new XPathException("No request object found in the current XQuery context.");
		if (var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $request is not bound to an Java object.");

		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		if (value.getObject() instanceof RequestWrapper) {
            if (isCalledAs("get-context-path"))
                return new StringValue(((RequestWrapper) value.getObject()).getContextPath());
            else
                return new StringValue(((RequestWrapper) value.getObject()).getServletPath());
        } else
			throw new XPathException("Variable $request is not bound to a Request object.");
	}
	
}
