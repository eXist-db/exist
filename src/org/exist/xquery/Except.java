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
package org.exist.xquery;

import org.exist.dom.NodeSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class Except extends CombiningExpression {
	
	public Except(XQueryContext context, PathExpr left, PathExpr right) {
		super(context, left, right);
	}
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.CombiningExpression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
		throws XPathException {
		Sequence lval = left.eval(contextSequence, contextItem);
		Sequence rval = right.eval(contextSequence, contextItem);
		if(rval.getLength() == 0 || lval.getLength() == 0)
		    return lval;
		if(lval.getItemType() != Type.NODE || rval.getItemType() != Type.NODE)
			throw new XPathException("except operand is not a node sequence");
		NodeSet result = lval.toNodeSet().except(rval.toNodeSet());
		return result;
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        left.dump(dumper);
        dumper.display(" except ");
        right.dump(dumper);
    }
}
