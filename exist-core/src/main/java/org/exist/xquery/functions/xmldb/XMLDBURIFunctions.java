/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2006-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.xmldb;

import java.net.URISyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author cgeorg
 */
public class XMLDBURIFunctions extends BasicFunction {
	protected static final Logger logger = LogManager.getLogger(XMLDBURIFunctions.class);
	public final static FunctionSignature signatures[] = new FunctionSignature[] {
		new FunctionSignature(
				new QName("encode", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Encodes the string $string such that it will be a valid collection or resource path. Provides similar functionality to java's URLEncoder.encode() function, with some enhancements.",
				new SequenceType[] {
					new FunctionParameterSequenceType("string", Type.STRING, Cardinality.EXACTLY_ONE, "The input string"),
				},
				new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the URL encoded string")
		),
		new FunctionSignature(
				new QName("encode-uri", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Encodes the string $string such that it will be a valid collection or resource path. Provides similar functionality to java's URLEncoder.encode() function, with some enhancements. Returns an xs:anyURI object representing a valid XmldbURI",
				new SequenceType[] {
					new FunctionParameterSequenceType("string", Type.STRING, Cardinality.EXACTLY_ONE, "The input string"),
				},
				new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE, "the XmldbURI encoded from $string")
		),
		new FunctionSignature(
				new QName("decode", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Decodes the string $string such that any percent encoded octets will be translated to their decoded UTF-8 representation.",
				new SequenceType[] {
					new FunctionParameterSequenceType("string",Type.STRING, Cardinality.EXACTLY_ONE, "The input string"),
				},
				new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the decoded string")
		),
		new FunctionSignature(
				new QName("decode-uri", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Decodes the URI $uri such that any percent encoded octets will be translated to their decoded UTF-8 representation.",
				new SequenceType[] {
					new FunctionParameterSequenceType("uri", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The URI"),
				},
				new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the decoded $uri as xs:string")
		)
	};

	public XMLDBURIFunctions(XQueryContext context, FunctionSignature signature)
	{
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
        throws XPathException {

		try {
			if(isCalledAs("encode")) {
				return new StringValue(URIUtils.urlEncodePartsUtf8(args[0].getStringValue()));
			} else if(isCalledAs("encode-uri")) {
				return new AnyURIValue(URIUtils.encodeXmldbUriFor(args[0].getStringValue()));
			} else {
				return new StringValue(URIUtils.urlDecodeUtf8(args[0].getStringValue()));
			}
		} catch(final URISyntaxException e) {
            logger.error(e.getMessage(), e);
			throw new XPathException(this, "URI Syntax Exception: " + e.getMessage(), e);
		} finally {
        }
	}
	
}
