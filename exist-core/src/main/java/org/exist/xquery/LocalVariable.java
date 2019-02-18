/*
*  eXist Open Source Native XML Database
*  Copyright (C) 2001-04 Wolfgang M. Meier (wolfgang@exist-db.org) 
*  and others (see http://exist-db.org)
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