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
package org.exist.interpreter;

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.QName;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

public interface FunctionCall extends Function {

	/**
	 * Called by {@link Context} to resolve a call to a function that has not
	 * yet been declared. XQueryContext remembers all calls to undeclared functions
	 * and tries to resolve them after parsing has completed.
	 * 
	 * @param functionDef the function definition.
	 *
	 * @throws XPathException if an error occurs whilst resolving the forward references
	 */
	public void resolveForwardReference(Function functionDef) throws XPathException;

	public QName getQName();

	/**
	 * Evaluate the function.
	 *
	 * @param contextSequence the context sequence.
	 * @param contextItem the context item
	 * @param seq the sequence
	 *
	 * @return the result sequence.
	 *
	 * @throws XPathException if an error occurs during evaluatiion
	 */
	public Sequence evalFunction(Sequence contextSequence, Item contextItem, Sequence[] seq) throws XPathException;

	public Sequence evalFunction(Sequence contextSequence, Item contextItem, Sequence[] seq, DocumentSet[] contextDocs) throws XPathException;

	public boolean isRecursive();

}