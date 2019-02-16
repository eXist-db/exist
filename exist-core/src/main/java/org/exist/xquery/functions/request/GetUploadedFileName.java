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
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import javax.annotation.Nonnull;

/**
 * @author wolf
 */
public class GetUploadedFileName extends StrictRequestFunction {

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
	
	public GetUploadedFileName(final XQueryContext context) {
		super(context, signature);
	}
	
	@Override
	public Sequence eval(final Sequence[] args, @Nonnull final RequestWrapper request)
			throws XPathException {
		final String uploadParamName = args[0].getStringValue();
		final List<String> fnames = request.getUploadedFileName(uploadParamName);
		if (fnames == null || fnames.isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}

		final ValueSequence result = new ValueSequence();
		for (final String name: fnames) {
			result.add(new StringValue(name));
		}
		return result;
	}
}
