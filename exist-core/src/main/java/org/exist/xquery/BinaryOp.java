/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import org.exist.dom.persistent.DocumentSet;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

public abstract class BinaryOp extends PathExpr {

    protected boolean inWhereClause = false;
    
    public BinaryOp(XQueryContext context) {
        super(context);
    }

    public int returnsType() {
        return Type.NODE;
    }

    public Expression getLeft() {
        return getExpression(0);
    }

    public Expression getRight() {
        return getExpression(1);
    }

    public void setLeft(Expression expr) {
        steps.addFirst(expr);
    }

    public void setRight(Expression expr) {
        steps.add(1, expr);
    }

    public void setContextDocSet(DocumentSet contextSet) {
    	super.setContextDocSet(contextSet);
    	getLeft().setContextDocSet(contextSet);
    	getRight().setContextDocSet(contextSet);
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.PathExpr#analyze(org.exist.xquery.Expression)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	inPredicate = (contextInfo.getFlags() & IN_PREDICATE) != 0;
    	contextId = contextInfo.getContextId();
    	inWhereClause = (contextInfo.getFlags() & IN_WHERE_CLAUSE) != 0;
    	getLeft().analyze(new AnalyzeContextInfo(contextInfo));
    	getRight().analyze(new AnalyzeContextInfo(contextInfo));
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.value.Sequence,
     *          org.exist.xquery.value.Item)
     */
    public abstract Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException;
    
    public Expression simplify() {
    	return this;
    }
}
