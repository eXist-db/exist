/* eXist Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
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
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * xpath-library function: boolean(object)
 *
 */
public class FunBoolean extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("boolean", Module.BUILTIN_FUNCTION_NS),
            "Computes the xs:boolean value of the sequence argument.",
			new SequenceType[] { new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE) },
			new SequenceType(Type.BOOLEAN, Cardinality.ONE)
		);
		
	public FunBoolean(XQueryContext context) {
		super(context, signature);
	}
	
	public int returnsType() {
		return Type.BOOLEAN;
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		contextSequence = getArgument(0).eval(contextSequence);
		return contextSequence.effectiveBooleanValue() ? BooleanValue.TRUE : BooleanValue.FALSE;
	}
}
