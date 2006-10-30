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
package org.exist.xquery.functions.xmldb;

import java.net.URISyntaxException;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author cgeorg
 */
public class XMLDBURIFunctions extends BasicFunction {
	
	public final static FunctionSignature signatures[] = new FunctionSignature[] {
		new FunctionSignature(
				new QName("encode", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Encodes the string provided in $a such that it will be a valid collection or resource path.  Provides similar functionality to java's URLEncoder.encode() function, with some enhancements",
				new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				},
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
		),
		new FunctionSignature(
				new QName("encode-uri", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Encodes the string provided in $a such that it will be a valid collection or resource path.  Provides similar functionality to java's URLEncoder.encode() function, with some enhancements.  Returns an xs:anyURI object representing a valid XmldbURI",
				new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				},
				new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE)
		),
		new FunctionSignature(
				new QName("decode", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Decodes the string provided in $a such that any percent encoded octets will be translated to their decoded UTF-8 representation.",
				new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				},
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
		),
		new FunctionSignature(
				new QName("decode-uri", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Decodes the URI provided in $a such that any percent encoded octets will be translated to their decoded UTF-8 representation.",
				new SequenceType[] {
					new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE),
				},
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
		)
	};
	
	/**
	 * @param context
	 * @param signature
	 */
	public XMLDBURIFunctions(XQueryContext context, FunctionSignature signature)
	{
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		try {
			if(isCalledAs("encode")) {
				return new StringValue(URIUtils.urlEncodePartsUtf8(args[0].getStringValue()));
			} else if(isCalledAs("encode-uri")) {
				return new AnyURIValue(URIUtils.encodeXmldbUriFor(args[0].getStringValue()));
			} else {
				return new StringValue(URIUtils.urlDecodeUtf8(args[0].getStringValue()));
			}
		} catch(URISyntaxException e) {
			throw new XPathException("URI Syntax Exception: " + e.getMessage(), e);
		}
	}
	
}
