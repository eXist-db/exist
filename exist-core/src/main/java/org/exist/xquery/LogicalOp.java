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

import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Base class for the boolean operators "and" and "or".
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang</a>
 */
public abstract class LogicalOp extends BinaryOp {

	/**
	 * If set to true, the boolean operation is processed as
	 * a set operation on two node sets. This is only possible
	 * within a predicate expression and if both operands return
	 * nodes. The predicate class can then filter out the matching
	 * nodes from the context set.
	 */
	protected boolean optimize = false;
	protected boolean rewritable = false;
	
    protected Expression parent;

	public LogicalOp(XQueryContext context) {
		super(context);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public abstract Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException;

	/* (non-Javadoc)
	 * @see org.exist.xquery.BinaryOp#analyze(org.exist.xquery.Expression, int)
	 */
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        this.parent = contextInfo.getParent();
        super.analyze(contextInfo);		
		//To optimize, we want nodes
		if(Type.subTypeOf(getLeft().returnsType(), Type.NODE) &&
				Type.subTypeOf(getRight().returnsType(), Type.NODE) &&
				//No dependency on the context item
				!Dependency.dependsOn(getLeft(), Dependency.CONTEXT_ITEM) &&
				!Dependency.dependsOn(getRight(), Dependency.CONTEXT_ITEM) &&	
				//and no dependency on *local* variables (context variables are OK)
				!Dependency.dependsOn(getLeft(), Dependency.LOCAL_VARS) &&							
				!Dependency.dependsOn(getRight(), Dependency.LOCAL_VARS) /* && 
				//If in an enclosed expression, return the boolean value, not a NodeSet
				//Commented out since we don't want to lose the benefit of the optimization
				//The boolean value will be returned by derived classes
				//See below, returnsType() however... 
				!(getParent() instanceof EnclosedExpr)*/)
			{optimize = true;}
		else
			{optimize = false;}
		rewritable = (contextInfo.getFlags() & Expression.IN_PREDICATE) == 0;
	}
	
	public int returnsType() {		
		return optimize ? 
			//Possibly more expression types to add there
			(getParent() instanceof EnclosedExpr ||
			//First, the intermediate PathExpr
			(getParent() == null)) ?
			Type.BOOLEAN : Type.NODE 
			:
			Type.BOOLEAN;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#getDependencies()
	 */
	public int getDependencies() {
		if(!optimize)
			{return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;}
		else
			{return Dependency.CONTEXT_SET;}
	}

    public Expression getParent() {
        return this.parent;
    }
    
    public boolean isRewritable() {
    	return rewritable;
    }
}
