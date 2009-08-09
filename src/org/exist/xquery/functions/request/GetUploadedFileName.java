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

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class GetUploadedFileName extends BasicFunction {

	protected static final Logger logger = Logger.getLogger(GetUploadedFileName.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-uploaded-file-name", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Retrieve the file name of an uploaded file from a multi-part request. This returns the file " +
			"name of the file on the client (without path). " +
			"Returns the empty sequence if the request is not a multi-part request or the parameter name " +
			"does not point to a file part.",
			new SequenceType[] {
				new FunctionParameterSequenceType("upload-param-name", Type.STRING, Cardinality.EXACTLY_ONE, "The parameter name")
			},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the file name of the uploaded file"));
	
	public GetUploadedFileName(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		
		RequestModule myModule =
			(RequestModule) context.getModule(RequestModule.NAMESPACE_URI);

		// request object is read from global variable $request
		Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);
		if(var == null || var.getValue() == null)
			throw new XPathException(this, "No request object found in the current XQuery context.");
		if (var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException(this, "Variable $request is not bound to an Java object.");

		// get parameters
		String uploadParamName = args[0].getStringValue();

		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		if (value.getObject() instanceof RequestWrapper) {
			RequestWrapper request = (RequestWrapper)value.getObject();
			String fname = request.getUploadedFileName(uploadParamName);
			if(fname == null) {
				return Sequence.EMPTY_SEQUENCE;
			}
			return new StringValue(fname);
		} else
			throw new XPathException(this, "Variable $request is not bound to a Request object.");
	}

}
