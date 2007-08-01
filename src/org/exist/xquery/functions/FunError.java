/* eXist Open Source Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

public class FunError extends BasicFunction {

	public final static FunctionSignature signature[] = {
		new FunctionSignature(
			new QName("error", Function.BUILTIN_FUNCTION_NS),
			"Indicates that an irrecoverable error has occurred. The "
				+ "script will terminate immediately with an exception.",
			null,
			new SequenceType(Type.EMPTY, Cardinality.ZERO)),
		new FunctionSignature(
			new QName("error", Function.BUILTIN_FUNCTION_NS),
			"Indicates that an irrecoverable error has occurred. The "
				+ "script will terminate immediately with an exception.",
            new SequenceType[] {
				new SequenceType(Type.QNAME, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.EMPTY, Cardinality.ZERO)),
		new FunctionSignature(
			new QName("error", Function.BUILTIN_FUNCTION_NS),
			"Indicates that an irrecoverable error has occurred. The "
				+ "script will terminate immediately with an exception.",
            new SequenceType[] {
				new SequenceType(Type.QNAME, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.EMPTY, Cardinality.ZERO)),
		new FunctionSignature(
			new QName("error", Function.BUILTIN_FUNCTION_NS),
			"Indicates that an irrecoverable error has occurred. The "
				+ "script will terminate immediately with an exception.",
            new SequenceType[] {
				new SequenceType(Type.QNAME, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
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

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
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
		throw new XPathException(getASTNode(), message + " (" +
				errQName.getNamespaceURI() + '#' + errQName.getLocalName() + ')');
	}
	
	private String serializeErrorObject(Sequence seq) throws XPathException {
		StringBuffer buf = new StringBuffer();
		for(SequenceIterator i = seq.unorderedIterator(); i.hasNext(); ) {
			Item next = i.nextItem();
			if (Type.subTypeOf(next.getType(), Type.NODE)) {
				Serializer serializer = context.getBroker().getSerializer();
				serializer.reset();
				try {
					buf.append(serializer.serialize((NodeValue) next));
				} catch (SAXException e) {
					throw new XPathException(getASTNode(), "An exception occurred while serializing node to log: " +
							"e.getMessage()", e);
				}
			} else
				buf.append(next.getStringValue());
		}
		return buf.toString();
	}
}
