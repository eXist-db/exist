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

import org.exist.dom.QName;

/**
 * Represents a local variable as declared by for and let.
 * 
 * Local variables are stored as a linked list.
 * 
 * @author wolf
 */
public class LocalVariable extends VariableImpl {

	protected LocalVariable before = null;
	protected LocalVariable after = null;

	/**
	 * For O(1) variable resolution: the previous LocalVariable with the same
	 * {@link #getQName()} that was the topmost binding before this one was
	 * declared. Used by {@link XQueryContext#popLocalVariables} to restore the
	 * shadowed binding when this variable goes out of scope.
	 */
	LocalVariable prevSameName = null;

	/**
	 * The {@link #contextStack} marker that was on top when this variable was
	 * declared. A variable is visible in the current scope iff
	 * {@code markedUnder == contextStack.peek()}; the previous linked-list
	 * walk used to express this by stopping at {@code contextStack.peek()}.
	 */
	LocalVariable markedUnder = null;

    public LocalVariable(QName qname) {
        super(qname);
    }

	public LocalVariable(LocalVariable other) {
		super(other);
	}
	
	public void addAfter(LocalVariable var) {
		this.after = var;
		var.before = this;
	}

    public boolean isClosureVar() {
        return false;
    }
}