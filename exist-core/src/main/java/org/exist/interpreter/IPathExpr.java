/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
 *  http://exist-db.org
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
package org.exist.interpreter;

import java.io.Writer;
import java.util.List;

import org.exist.dom.persistent.DocumentSet;
import org.exist.xquery.Expression;

public interface IPathExpr extends Expression {

	/**
	 * Add an arbitrary expression to this object's list of child-expressions.
	 * 
	 * @param expr the expression.
	 */
	public void add(Expression expr);

	/**
	 * Add all the child-expressions from another PathExpr to this object's
	 * child-expressions.
	 * 
	 * @param path the path expression.
	 */
	public void add(IPathExpr path);

	/**
	 * Add another PathExpr to this object's expression list.
	 *
	 * @param path the path expression.
	 */
	public void addPath(IPathExpr path);

	/**
	 * Add a predicate expression to the list of expressions. The predicate is
	 * added to the last expression in the list.
	 * 
	 * @param pred the predicate.
	 */
	public void addPredicate(IPredicate pred);

	/**
	 * Replace the given expression by a new expression.
	 *
	 * @param oldExpr the old expression
	 * @param newExpr the new expression to replace the old
	 */
	public void replaceExpression(Expression oldExpr, Expression newExpr);

	public Expression getParent();

	public DocumentSet getDocumentSet();

	/**
	 * @deprecated use {@link #getSubExpression(int)}
	 *
	 * @param pos the position of the expression.
	 *
	 * @return the expression
	 */
	@Deprecated
	public Expression getExpression(int pos);

	public Expression getLastExpression();

	/**
	 * @deprecated use {@link #getSubExpressionCount()}
	 *
	 * @return the length of the path expression
	 */
	@Deprecated
	public int getLength();

	public void setUseStaticContext(boolean staticContext);

	public void accept(ExpressionVisitor visitor);

	public void replaceLastExpression(Expression s);

	public String getLiteralValue();

	public void reset();

	public boolean isValid();

	public void dump(Writer writer);

	public void setContext(Context context);

	public List<Expression> getSteps();

}