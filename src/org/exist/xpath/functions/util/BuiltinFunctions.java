/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
package org.exist.xpath.functions.util;

import java.util.Iterator;

import org.exist.dom.QName;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.Module;
import org.exist.xpath.XPathException;
import org.exist.xpath.XQueryContext;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.QNameValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;
import org.exist.xpath.value.ValueSequence;

/**
 * Returns a sequence containing the QNames of all built-in functions
 * currently registered in the query engine.
 * 
 * @author wolf
 */
public class BuiltinFunctions extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("builtin-functions", UTIL_FUNCTION_NS, "util"),
			"Returns a sequence containing the QNames of all built-in functions " +
			"currently known to the system.",
			null,
			new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE));

	public BuiltinFunctions(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		ValueSequence resultSeq = new ValueSequence();
		for(Iterator i = context.getModules(); i.hasNext(); ) {
			Module module = (Module)i.next();
			FunctionSignature signatures[] = module.listFunctions();
			for(int j = 0; j < signatures.length; j++) {
				QName qname = signatures[j].getName();
				QNameValue value = new QNameValue(context, qname);
				resultSeq.add(value);
			}
		}
		return resultSeq;
	}

}
