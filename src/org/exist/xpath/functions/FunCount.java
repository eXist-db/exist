
/* eXist Native XML Database
 * Copyright (C) 2001-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
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
package org.exist.xpath.functions;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xpath.Cardinality;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.IntegerValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

public class FunCount extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("count", BUILTIN_FUNCTION_NS),
			new SequenceType[] { new SequenceType(Type.ATOMIC, Cardinality.ZERO_OR_MORE) },
			new SequenceType(Type.INTEGER, Cardinality.ONE)
		);
			
    public FunCount(StaticContext context) {
		super(context, signature);
    }

    public int returnsType() {
		return Type.INTEGER;
    }
	
    public DocumentSet preselect(DocumentSet in_docs) throws XPathException {
		return getArgument(0).preselect(in_docs);
    }

    public Sequence eval(DocumentSet docs, Sequence contextSequence, Item contextItem) throws XPathException {
    	if(getArgumentCount() == 0)
    		return IntegerValue.ZERO;
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		return new IntegerValue(getArgument(0).eval(docs, contextSequence).getLength());
	}
}
