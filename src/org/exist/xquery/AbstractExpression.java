
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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.xquery;

import org.exist.dom.DocumentSet;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.security.xacml.XACMLSource;

public abstract class AbstractExpression implements Expression {

	private int expressionId = EXPRESSION_ID_INVALID;
		
	protected XQueryContext context;

    protected int line = -1;
    protected int column = -1;
    
	protected DocumentSet contextDocSet = null;
    
	/**
	 * Holds the context id for the context of this expression.
	 */
    protected int contextId = Expression.NO_CONTEXT_ID;
	
	public AbstractExpression(XQueryContext context) {
		this.context = context;
		this.expressionId = context.nextExpressionId();
	}

    public int getExpressionId() {
		return expressionId;
	}
    
    public int getContextId() {
        return contextId;
    }
	
	public Sequence eval(Sequence contextSequence)
		throws XPathException {
		return eval(contextSequence, null);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public abstract Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException;

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public abstract int returnsType();

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#resetState()
	 */
	public void resetState(boolean postOptimization) {
		contextDocSet = null;
	}

	/**
	 * The default cardinality is {@link Cardinality#EXACTLY_ONE}.
	 */
	public int getCardinality() {
		return Cardinality.EXACTLY_ONE; // default cardinality
	}

	/**
	 * Returns {@link Dependency#DEFAULT_DEPENDENCIES}.
	 * 
	 * @see org.exist.xquery.Expression#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.DEFAULT_DEPENDENCIES;
	}
	
	public void setPrimaryAxis(int axis) {
	}

    public int getPrimaryAxis() {
        return Constants.UNKNOWN_AXIS;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#setContextDocSet(org.exist.dom.DocumentSet)
     */
    public void setContextDocSet(DocumentSet contextSet) {
        this.contextDocSet = contextSet;
    }
    
    public DocumentSet getContextDocSet() {
        return contextDocSet;
    }
    
    public void accept(ExpressionVisitor visitor) {
    	visitor.visit(this);    	
    }
    
	public void setASTNode(XQueryAST ast) {
        if (ast != null) {
            line = ast.getLine();
            column = ast.getColumn();
        }
	}

    public void setLocation(int line, int column) {
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public XACMLSource getSource() {
        return context.getSource();
    }

    public XQueryContext getContext() {
        return context;
    }
}
