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
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class GetContextPath extends StrictRequestFunction {

	protected static final Logger logger = LogManager.getLogger(GetContextPath.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("get-context-path", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Returns the context path of the current request, i.e. the portion of the request URI that " +
            "indicates the context of the request.",
			null,
			new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the context path of the current request")),
        new FunctionSignature(
			new QName("get-servlet-path", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Returns the servlet path of the current request, i.e. the portion of the request URI that " +
            "points to the servlet which is handling the request.\n"+
			"For example an xquery GET or POST to /some/path/myfile.xq/extra/path will return /some/path/myfile.xq when myfile.xq is executed.",
			null,
			new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the servlet path of the current request"))
    };

	public GetContextPath(final XQueryContext context, final FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(final Sequence[] args, @Nonnull final RequestWrapper request)
			throws XPathException {
		if (isCalledAs("get-context-path")) {
			return new StringValue(request.getContextPath());
		} else {
			return new StringValue(request.getServletPath());
		}
	}
}
