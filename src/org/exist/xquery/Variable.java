/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
package org.exist.xquery;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xquery.value.Sequence;

/**
 * An XQuery/XPath variable, consisting of a QName and a value.
 * 
 * @author wolf
 */
public class Variable {

	// the name of the variable
	private QName qname;
	
	// the current value assigned to the variable
	private Sequence value = null;
	
	// the context position of this variable in the local variable stack
	// this can be used to determine if a variable has been declared
	// before another
	private int positionInStack = 0;
	
	// the cardinality of this variable
	private int cardinality = Cardinality.ZERO_OR_MORE;
	
	// the context document set
	private DocumentSet contextDocs = null;
	
	/**
	 * 
	 */
	public Variable(QName qname) {
		this.qname = qname;
	}

	public void setValue(Sequence value) {
		this.value = value;
	}
	
	public Sequence getValue() {
		return value;
	}
	
	public QName getQName() {
		return qname;
	}
	
	public String toString() {
		return "$" + qname.toString();
	}
	
	public int getDependencies(XQueryContext context) {
		if(context.getCurrentStackSize() > positionInStack)
			return Dependency.CONTEXT_SET + Dependency.GLOBAL_VARS+ Dependency.CONTEXT_ITEM;
		else
			return Dependency.CONTEXT_SET + Dependency.LOCAL_VARS;
	}
	
	public int getCardinality() {
		return cardinality;
	}
	
	public void setCardinality(int card) {
		this.cardinality = card;
	}
	
	public void setStackPosition(int position) {
		positionInStack = position;
	}
	
	public DocumentSet getContextDocs() {
	    return contextDocs;
	}
	
	public void setContextDocs(DocumentSet docs) {
	    this.contextDocs = docs;
	}
}
