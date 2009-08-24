/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
package org.exist.xquery.functions;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

public class FunError extends BasicFunction {
	protected static final Logger logger = Logger.getLogger(FunError.class);
	public final static FunctionSignature signature[] = {
		new FunctionSignature(
			new QName("error", Function.BUILTIN_FUNCTION_NS),
			"Indicates that an irrecoverable error has occurred. The "
				+ "script will terminate immediately with an exception using the default qname, 'http://www.w3.org/2004/07/xqt-errors#err:FOER0000', and the default error message, 'An error has been raised by the query'.",
			null,
			new SequenceType(Type.EMPTY, Cardinality.ZERO)),
		new FunctionSignature(
			new QName("error", Function.BUILTIN_FUNCTION_NS),
			"Indicates that an irrecoverable error has occurred. The "
				+ "script will terminate immediately with an exception using $qname and the default message, 'An error has been raised by the query'.",
            new SequenceType[] {
				new FunctionParameterSequenceType("qname", Type.QNAME, Cardinality.EXACTLY_ONE, "The qname")
			},
			new SequenceType(Type.EMPTY, Cardinality.ZERO)),
		new FunctionSignature(
			new QName("error", Function.BUILTIN_FUNCTION_NS),
			"Indicates that an irrecoverable error has occurred. The "
				+ "script will terminate immediately with an exception using $qname and $message.",
            new SequenceType[] {
				new FunctionParameterSequenceType("qname", Type.QNAME, Cardinality.ZERO_OR_ONE, "The qname"),
				new FunctionParameterSequenceType("message", Type.STRING, Cardinality.EXACTLY_ONE, "The message")
			},
			new SequenceType(Type.EMPTY, Cardinality.ZERO)),
		new FunctionSignature(
			new QName("error", Function.BUILTIN_FUNCTION_NS),
			"Indicates that an irrecoverable error has occurred. The "
				+ "script will terminate immediately with an exception using $qname and $message with $error-object appended.",
            new SequenceType[] {
				new FunctionParameterSequenceType("qname", Type.QNAME, Cardinality.ZERO_OR_ONE, "The qname"),
				new FunctionParameterSequenceType("message", Type.STRING, Cardinality.EXACTLY_ONE, "The message"),
				new FunctionParameterSequenceType("error-object", Type.ITEM, Cardinality.ZERO_OR_MORE, "The error object")
			},
			new SequenceType(Type.EMPTY, Cardinality.ZERO)),
	};

	public final static QName DEFAULT_ERR =
		new QName("FOER0000", "http://www.w3.org/2004/07/xqt-errors", "err");
	
	public FunError(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.EMPTY;
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)
        throws XPathException {
		QName errQName = DEFAULT_ERR;
		String message = "An error has been raised by the query";
		if (args.length > 0) {
			if (!args[0].isEmpty())
				errQName = ((QNameValue) args[0].itemAt(0)).getQName();
			if (args.length > 1)
				message = args[1].getStringValue();
			if (args.length == 3)
				message += ": " + serializeErrorObject(args[2]);
		}
        logger.error(message + " (" +
				errQName.getNamespaceURI() + '#' + errQName.getLocalName() + ')');
		throw new XPathException(this, message + " (" +
				errQName.getNamespaceURI() + '#' + errQName.getLocalName() + ')');
	}
	
	private String serializeErrorObject(Sequence seq)
        throws XPathException {
		StringBuilder buf = new StringBuilder();
		for(SequenceIterator i = seq.unorderedIterator(); i.hasNext(); ) {
			Item next = i.nextItem();
			if (Type.subTypeOf(next.getType(), Type.NODE)) {
				Serializer serializer = context.getBroker().getSerializer();
				serializer.reset();
				try {
					buf.append(serializer.serialize((NodeValue) next));
				} catch (SAXException e) {
                    logger.error("An exception occurred while serializing node to log: " + e.getMessage());
					throw new XPathException(this, "An exception occurred while serializing node to log: " +
							"e.getMessage()", e);
				}
			} else
				buf.append(next.getStringValue());
		}
		return buf.toString();
	}
}
