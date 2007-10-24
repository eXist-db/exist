/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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
 *  $Id: GetUploadedFile.java 790 2004-10-29 09:20:54 +0100 (Fri, 29 Oct 2004) wolfgang_m $
 */
package org.exist.xquery.functions.request;

import java.io.File;

import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class GetUploadedFile extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-uploaded-file", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Retrieve the Java file object where the file part of a multi-part request has been stored. " +
			"Returns the empty sequence if the request is not a multi-part request or the parameter name " +
			"does not point to a file part.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE));
	
	public GetUploadedFile(XQueryContext context) {
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
			throw new XPathException("No request object found in the current XQuery context.");
		if (var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $request is not bound to an Java object.");

		// get parameters
		String uploadParamName = args[0].getStringValue();

		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		if (value.getObject() instanceof RequestWrapper) {
			RequestWrapper request = (RequestWrapper)value.getObject();
			File file = request.getFileUploadParam(uploadParamName);
			if(file == null) {
				LOG.debug("File param not found: " + uploadParamName);
				return Sequence.EMPTY_SEQUENCE;
			} else
				LOG.debug("Uploaded file: " + file.getAbsolutePath());
			return new JavaObjectValue(file);
		} else
			throw new XPathException("Variable $request is not bound to a Request object.");
	}
}
