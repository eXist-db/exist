
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
package org.exist.xpath.functions;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Dependency;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.IntegerValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

/**
 * xpath-library function: position()
 *
 */
public class FunPosition extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("position", BUILTIN_FUNCTION_NS),
			null,
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));
	
    public FunPosition(StaticContext context) {
        super(context, signature);
    }

	/* (non-Javadoc)
	 * @see org.exist.xpath.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM +
			Dependency.CONTEXT_POSITION;
	}

    public Sequence eval(DocumentSet docs, Sequence contextSequence, Item contextItem) throws XPathException {
		if(contextSequence == null || contextSequence.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		return new IntegerValue(context.getContextPosition() + 1);
    }

    public String pprint() {
        return "position()";
    }
}
