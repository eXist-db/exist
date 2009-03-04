/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-08 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.xquery.modules.cache;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

/**
 * Global cache cache. Remove function
 * 
 * @author Evgeny Gazdovsky <gazdovsky@gmail.com>
 * @version 1.0
 */
public class RemoveFunction extends BasicFunction {

	public final static FunctionSignature signatures[] = { 
		new FunctionSignature(
			new QName("remove", CacheModule.NAMESPACE_URI, CacheModule.PREFIX),
			"Reove data from global cache by key $a",
			new SequenceType[] { new SequenceType(Type.ANY_TYPE, Cardinality.ONE_OR_MORE) }, 
	        new SequenceType(Type.EMPTY, Cardinality.EMPTY)
		)
	};

	public RemoveFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		try {
			Cache.remove(args[0], context);
		} catch (SAXException e) {
			e.printStackTrace();
		}
		return Sequence.EMPTY_SEQUENCE;
	}
}