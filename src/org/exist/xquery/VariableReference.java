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

import org.exist.dom.DocumentSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Represents a reference to an in-scope variable.
 * 
 * @author wolf
 */
public class VariableReference extends AbstractExpression {

	private final String qname;

	public VariableReference(XQueryContext context, String qname) {
		super(context);
		this.qname = qname;
	}

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        Variable var = getVariable();
        if (var == null)
            throw new XPathException(this, "XPDY0002 : variable '$" + qname + "' is not set.");
        if (!var.isInitialized())
            throw new XPathException(this, "XQST0054: variable declaration of '$" + qname + "' cannot " +
            "be executed because of a circularity.");
        contextInfo.setStaticReturnType(var.getStaticType());
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
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
        
		Variable var = getVariable();
        if (var == null)
            throw new XPathException(this, "XPDY0002 : variable '$" + qname + "' is not set.");
        Sequence seq = var.getValue();
		if (seq == null)
			throw new XPathException(this, "XPDY0002 : undefined value for variable '$" + qname + "'");
        Sequence result = seq;
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);
        
        return result;         
	}

	protected Variable getVariable() throws XPathException {
	    try {
            return context.resolveVariable(qname);
        } catch (XPathException e) {
            e.setLocation(line, column);
            throw e;
        }
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#preselect(org.exist.dom.DocumentSet, org.exist.xquery.StaticContext)
	 */
	public DocumentSet preselect(DocumentSet in_docs) throws XPathException {
		return in_docs;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display('$').display(qname);
    }
    
    public String toString() {
        return "$" + qname;
    }    
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		try {
			Variable var = context.resolveVariable(qname);
            if(var != null) {
                if (var.getValue() != null) {
                    int type = var.getValue().getItemType();
                    return type;
                } else {
                    return var.getType();
                }
            }
		} catch (XPathException e) {
		}
		return Type.ITEM;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		try {
			Variable var = context.resolveVariable(qname);
			if (var != null) {
				int deps = var.getDependencies(context);
				return deps;
			}
		} catch (XPathException e) {
		}
		return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
		try {
			Variable var = context.resolveVariable(qname);
			if (var != null && var.getValue() != null) {
				int card = var.getValue().getCardinality();
				return card;
			}
		} catch (XPathException e) {
		}
		return Cardinality.ZERO_OR_MORE;		// unknown cardinality
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#resetState()
	 */
	public void resetState(boolean postOptimization) {
	}

    public void accept(ExpressionVisitor visitor) {
        visitor.visitVariableReference(this);
    }
}
