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

package org.exist.xquery;

import org.apache.log4j.Logger;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

public class Union extends CombiningExpression {

	private final static Logger LOG = Logger.getLogger(Union.class);
	
    public Union(XQueryContext context, PathExpr left, PathExpr right) {
        super(context, left, right);
    }
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
		Sequence lval = left.eval(contextSequence, contextItem);		
		Sequence rval = right.eval(contextSequence, contextItem);
        lval.removeDuplicates();
		rval.removeDuplicates();
       
        Sequence result;
        if (lval.isEmpty() && rval.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else if(rval.isEmpty()) {
            if(!Type.subTypeOf(lval.getItemType(), Type.NODE))
                throw new XPathException(this, "Error XPTY0004 : union operand is not a node sequence");
            result = lval;
        } else if(lval.isEmpty()) {
            if(!Type.subTypeOf(rval.getItemType(), Type.NODE))
                throw new XPathException(this, "Error XPTY0004 : union operand is not a node sequence");
            result = rval;            
        } else {
            if(!(Type.subTypeOf(lval.getItemType(), Type.NODE) && Type.subTypeOf(rval.getItemType(), Type.NODE)))
                throw new XPathException(this, "Error XPTY0004 : union operand is not a node sequence");            
            if (lval.isPersistentSet() && rval.isPersistentSet()) {        
                result = lval.toNodeSet().union(rval.toNodeSet());
            } else {
                ValueSequence values = new ValueSequence(true);
                values.addAll(lval);
                values.addAll(rval);
                values.sortInDocumentOrder();
                values.removeDuplicates();
                result = values;
            }
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);
        
        return result;           
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        left.dump(dumper);
        dumper.display(" union ");
        right.dump(dumper);
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append(left.toString());
    	result.append(" union ");
    	result.append(right.toString());
    	return result.toString();
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visitUnionExpr(this);
    }
}
