/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import org.exist.xquery.util.ExpressionDumper;

import java.text.Collator;

/**
 * An XQuery order specifier as specified in an "order by" clause.
 * 
 * @author wolf
 */
public class OrderSpec {

	public static final int ASCENDING_ORDER = 0;
	public static final int DESCENDING_ORDER = 1;
	
	public static final int EMPTY_GREATEST = 0;
	public static final int EMPTY_LEAST = 4;
	
	private final XQueryContext context;
	private Expression expression;
	private int modifiers = 0;
	private Collator collator = null;
	
	/**
	 * 
	 */
	public OrderSpec(XQueryContext context, Expression sortExpr) {
		this.expression = sortExpr;
		this.context = context;
	}

	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
	    expression.analyze(contextInfo);
	}
	
	public void setModifiers(int modifiers) {
		this.modifiers = modifiers;
	}
	
	public void setCollation(String collationURI) throws XPathException {
		this.collator = context.getCollator(collationURI);
	}
	
	public Expression getSortExpression() {
		return expression;
	}
	
	public int getModifiers() {
		return modifiers;
	}
	
	public Collator getCollator() {
		return collator == null ? context.getDefaultCollator() : collator;
	}
	
	public String toString() {
		final StringBuilder buf = new StringBuilder();
		buf.append(ExpressionDumper.dump(expression));
		buf.append(' ');
		buf.append((modifiers & DESCENDING_ORDER) == 0 ? "ascending" : "descending");
        buf.append((modifiers & EMPTY_LEAST) == 0 ? "empty greatest" : "empty least");
		return buf.toString();
	}
	
	public void resetState(boolean postOptimization) {
	    expression.resetState(postOptimization);
	}

    public void replace(Expression oldExpr, Expression newExpr) {
        if (expression == oldExpr) {
            expression = newExpr;
        }
    }
}
