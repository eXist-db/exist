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

package org.exist.xpath.functions;

import org.exist.dom.QName;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.XQueryContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.BooleanValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

public class FunStartsWith extends Function {

	public final static FunctionSignature signature =
				new FunctionSignature(
					new QName("starts-with", BUILTIN_FUNCTION_NS),
					new SequenceType[] {
						 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
						 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
					},
					new SequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE));
					
	public FunStartsWith(XQueryContext context) {
		super(context, signature);
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		if(contextItem != null)
			contextSequence = contextItem.toSequence();

		Sequence s1 = getArgument(0).eval(contextSequence);
		Sequence s2 = getArgument(1).eval(contextSequence);
		if(s1.getLength() == 0 || s2.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		if(s1.getStringValue().startsWith(s2.getStringValue()))
			return BooleanValue.TRUE;
		else
			return BooleanValue.FALSE;
	}
}
