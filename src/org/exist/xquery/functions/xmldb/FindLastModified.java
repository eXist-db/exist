/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery.functions.xmldb;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NewArrayNodeSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.persistent.NodeSetIterator;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FindLastModified extends BasicFunction {

	public final static FunctionSignature signatures[] = new FunctionSignature[]{
			new FunctionSignature(
					new QName("find-last-modified-since", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
					"Filters the given node set to only include nodes from resources which were modified since the specified " +
							"date time.",
					new SequenceType[]{
							new FunctionParameterSequenceType("node-set", Type.NODE, Cardinality.ZERO_OR_MORE,
									"A node set"),
							new FunctionParameterSequenceType("since", Type.DATE_TIME, Cardinality.EXACTLY_ONE,
									"Date")
					},
					new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the filtered node set.")
			),
			new FunctionSignature(
					new QName("find-last-modified-until", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
					"Filters the given node set to only include nodes from resources which were modified until the specified " +
							"date time.",
					new SequenceType[]{
							new FunctionParameterSequenceType("node-set", Type.NODE, Cardinality.ZERO_OR_MORE,
									"A node set"),
							new FunctionParameterSequenceType("until", Type.DATE_TIME, Cardinality.EXACTLY_ONE,
									"Date")
					},
					new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the filtered node set.")
			)
	};

	public FindLastModified(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		final NodeSet nodes = args[0].toNodeSet();
		if (nodes.isEmpty())
			{return Sequence.EMPTY_SEQUENCE;}
		
		final NodeSet result = new NewArrayNodeSet();
		final DateTimeValue dtv = (DateTimeValue) args[1].itemAt(0);
		final long lastModified = dtv.getDate().getTime();

		for (final NodeSetIterator i = nodes.iterator(); i.hasNext(); ) {
			final NodeProxy proxy = i.next();
			final DocumentImpl doc = proxy.getOwnerDocument();
			final long modified = doc.getMetadata().getLastModified();

			boolean matches;
			if (this.isCalledAs("find-last-modified-since")) {
				matches = modified > lastModified;
			} else {
				matches = modified <= lastModified;
			}

			if (matches)
				{result.add(proxy);}
		}
		return result;
	}

}
