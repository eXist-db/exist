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

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.exist.xquery.value.ValueSequence;

/**
 * @author wolf
 */
public class GetUploadedFileName extends BasicFunction {

	protected static final Logger logger = LogManager.getLogger(GetUploadedFileName.class);

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
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "the file name of the uploaded files"));
	
	public GetUploadedFileName(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		
		final RequestModule myModule =
			(RequestModule) context.getModule(RequestModule.NAMESPACE_URI);

		// request object is read from global variable $request
		final Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);
		if(var == null || var.getValue() == null)
			{throw new XPathException(this, "No request object found in the current XQuery context.");}
		if (var.getValue().getItemType() != Type.JAVA_OBJECT)
			{throw new XPathException(this, "Variable $request is not bound to an Java object.");}

		// get parameters
		final String uploadParamName = args[0].getStringValue();

		final JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		if (value.getObject() instanceof RequestWrapper) {
			final RequestWrapper request = (RequestWrapper)value.getObject();
			final List<String> fnames = request.getUploadedFileName(uploadParamName);
			if(fnames == null) {
				return Sequence.EMPTY_SEQUENCE;
			}
			final ValueSequence result = new ValueSequence();
			for (final String name: fnames) {
				result.add(new StringValue(name));
			}
			return result;
		} else
			{throw new XPathException(this, "Variable $request is not bound to a Request object.");}
	}

}
