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
 *  $Id
 */
package org.exist.xquery.functions.util;

import java.net.URLDecoder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Adam Retter (adam.retter@devon.gov.uk)
 */
public class FunUnEscapeURI extends BasicFunction {
	
	protected static final Logger logger = LogManager.getLogger(FunUnEscapeURI.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("unescape-uri", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns an un-escaped URL escaped string with the encoding scheme (e.g. \"UTF-8\"). Decodes encoded sensitive characters from a URL, for example \"%2F\" becomes \"/\", i.e. does the oposite to escape-uri()",
			new SequenceType[]
			{
				new FunctionParameterSequenceType("escaped-string", Type.STRING, Cardinality.EXACTLY_ONE, "The escaped string to be un-escaped"),
				new FunctionParameterSequenceType("encoding", Type.STRING, Cardinality.EXACTLY_ONE, "The encoding scheme to use in the un-escaping of the string")
			},
			new FunctionParameterSequenceType("result", Type.STRING, Cardinality.EXACTLY_ONE, "the un-escaped string"));

	public FunUnEscapeURI(XQueryContext context)
	{
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		try
		{
			return new StringValue(URLDecoder.decode(args[0].getStringValue(), args[1].getStringValue()));
		}
		catch(final java.io.UnsupportedEncodingException e)
		{
			throw new XPathException(this, "Unsupported Encoding Scheme: " + e.getMessage(), e);
		}
	}
	
}
