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

import java.util.List;

import org.exist.dom.QName;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

public interface Function extends IPathExpr {

	/**
	 * Set the parent expression of this function, i.e. the
	 * expression from which the function is called.
	 * 
	 * @param parent the parent expression.
	 */
	public void setParent(Expression parent);

	/**
	 * Returns the expression from which this function
	 * gets called.
	 *
	 * @return the parent expression.
	 */
	public Expression getParent();

	/**
	 * Set the (static) arguments for this function from a list of expressions.
	 * 
	 * This will also check the type and cardinality of the
	 * passed argument expressions.
	 * 
	 * @param arguments the statis arguments to the function.
	 *
	 * @throws XPathException if an error occurs whilst setting the arguments.
	 */
	public void setArguments(List<Expression> arguments) throws XPathException;

	public Sequence[] getArguments(Sequence contextSequence, Item contextItem) throws XPathException;

	/**
	 * Get an argument expression by its position in the
	 * argument list.
	 * 
	 * @param pos the position of the argument
	 *
	 * @return the argument at the position
	 */
	public Expression getArgument(int pos);

	/**
	 * Get the number of arguments passed to this function.
	 * 
	 * @return number of arguments
	 */
	public int getArgumentCount();

	/**
	 * Return the name of this function.
	 * 
	 * @return name of this function
	 */
	public QName getName();

	/**
	 * Get the signature of this function.
	 * 
	 * @return signature of this function
	 */
	public FunctionSignature getSignature();

	public boolean isCalledAs(String localName);

}