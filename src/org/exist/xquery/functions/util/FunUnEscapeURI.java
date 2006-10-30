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
 *  $Id
 */
package org.exist.xquery.functions.util;

import java.net.URLDecoder;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Adam Retter (adam.retter@devon.gov.uk)
 */
public class FunUnEscapeURI extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("unescape-uri", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns an un-escaped URL escaped string identified by $a with the encoding scheme indicated by the string $b (e.g. \"UTF-8\"). Decodes encoded sensitive characters from a URL, for example \"%2F\" becomes \"/\", i.e. does the oposite to escape-uri()",
			new SequenceType[]
			{
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE));

	public final static FunctionSignature deprecated =
		new FunctionSignature(
			new QName("unescape-uri", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Returns an un-escaped URL escaped string identified by $a with the encoding scheme indicated by the string $b (e.g. \"UTF-8\"). Decodes encoded sensitive characters from a URL, for example \"%2F\" becomes \"/\", i.e. does the oposite to escape-uri()",
			new SequenceType[]
			{
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
			"Moved to the util module. See util:unescape-uri.");
	
	/**
	 * @param context
	 */
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
		catch(java.io.UnsupportedEncodingException e)
		{
			throw new XPathException("Unsupported Encoding Scheme: " + e.getMessage(), e);
		}
	}
	
}
