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
import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class GetUploadedFile extends BasicFunction {

	protected static final Logger logger = Logger.getLogger(GetUploadedFile.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("get-uploaded-file", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Retrieve the Java file object where the file part of a multi-part request has been stored. " +
			"Returns the empty sequence if the request is not a multi-part request or the parameter name " +
			"does not point to a file part.",
			new SequenceType[] {
				new FunctionParameterSequenceType("upload-param-name", Type.STRING, Cardinality.EXACTLY_ONE, "The parameter name")
			},
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE),
			"Deprecated in favour of get-uploaded-file-data()"
		),
			
		new FunctionSignature(
				new QName("get-uploaded-file-data", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
				"Retrieve the base64 encoded data where the file part of a multi-part request has been stored. " +
				"Returns the empty sequence if the request is not a multi-part request or the parameter name " +
				"does not point to a file part.",
				new SequenceType[] {
					new FunctionParameterSequenceType("upload-param-name", Type.STRING, Cardinality.EXACTLY_ONE, "The parameter name")
				},
				new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the base64 encoded data from the uploaded file")
		)
		
	};
	
	public GetUploadedFile(XQueryContext context, FunctionSignature signature) {
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
			File file = request.getFileUploadParam(uploadParamName);
			if(file == null) {
				logger.debug("File param not found: " + uploadParamName);
				return Sequence.EMPTY_SEQUENCE;
			} else
				logger.debug("Uploaded file: " + file.getAbsolutePath());
			
			
			if(isCalledAs("get-uploaded-file-data"))
			{
				InputStream is = null;
				try
				{
					is = new BufferedInputStream(new FileInputStream(file));
					byte buf[] = new byte[1024];
					int read = -1;
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					while((read = is.read(buf)) != -1)
					{
						baos.write(buf, 0, read);
					}
					
					return new Base64Binary(baos.toByteArray());
					
				}
				catch(FileNotFoundException fnfe)
				{
					throw new XPathException(this, fnfe.getMessage(), fnfe);
				}
				catch (IOException ioe)
				{
					throw new XPathException(this, ioe.getMessage(), ioe);
				}
				finally
				{
					if(is != null)
					{
						try
						{
							is.close();
						}
						catch (IOException ioe)
						{
							logger.warn(ioe.getMessage(), ioe);
						}
					}
				}
			}
			else
			{
				return new JavaObjectValue(file);
			}
		}
		else
		{
			throw new XPathException(this, "Variable $request is not bound to a Request object.");
		}
	}
}
