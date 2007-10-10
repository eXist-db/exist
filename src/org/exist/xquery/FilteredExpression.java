/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * FilteredExpression represents a primary expression with a predicate. Examples:
 * for $i in (1 to 10)[$i mod 2 = 0], $a[1], (doc("test.xml")//section)[2]. Other predicate
 * expressions are handled by class {@link org.exist.xquery.LocationStep}.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FilteredExpression extends AbstractExpression {

	final protected Expression expression;
	final protected List predicates = new ArrayList(2);

	/**
	 * @param context
	 */
	public FilteredExpression(XQueryContext context, Expression expr) {
		super(context);
		this.expression = expr;
	}

	public void addPredicate(Predicate pred) {
		predicates.add(pred);
	}

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	contextInfo.setParent(this);
        expression.analyze(contextInfo);
        for (Iterator i = predicates.iterator(); i.hasNext();) {
			Predicate pred = (Predicate) i.next();
			pred.analyze(contextInfo);
        }
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
        
        Sequence result;
		Sequence seq = expression.eval(contextSequence, contextItem);
		if (seq.isEmpty())
			result = Sequence.EMPTY_SEQUENCE;
        else {            
//    		seq.setSelfAsContext();
    		result = seq;           
    		for (Iterator i = predicates.iterator(); i.hasNext();) {
                Predicate pred = (Predicate) i.next();
    			result = pred.evalPredicate(contextSequence, result, Constants.DESCENDANT_SELF_AXIS);
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
        expression.dump(dumper);
        for (Iterator i = predicates.iterator(); i.hasNext();) {
            ((Expression)i.next()).dump(dumper);
        }
    }
    
    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append(expression.toString());
        for (Iterator i = predicates.iterator(); i.hasNext();) {
        	result.append(((Expression)i.next()).toString());
        }
        return result.toString();
    }    
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		return expression.returnsType();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#resetState()
	 */
	public void resetState() {
		super.resetState();
		expression.resetState();
		for (Iterator i = predicates.iterator(); i.hasNext();) {
			Predicate pred = (Predicate) i.next();
			pred.resetState();
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#setPrimaryAxis(int)
	 */
	public void setPrimaryAxis(int axis) {
		expression.setPrimaryAxis(axis);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		int deps = Dependency.CONTEXT_SET;
		for (Iterator i = predicates.iterator(); i.hasNext();) {
			deps |= ((Predicate) i.next()).getDependencies();
		}
		return deps;
	}
}
