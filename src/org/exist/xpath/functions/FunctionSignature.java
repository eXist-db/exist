/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
package org.exist.xpath.functions;

import org.exist.dom.QName;
import org.exist.xpath.value.SequenceType;

public class FunctionSignature {

	private QName name;
	private SequenceType[] arguments;
	private SequenceType returnType;
	private boolean isOverloaded = false;
	
	public FunctionSignature(QName name, SequenceType[] arguments, SequenceType returnType,
		boolean overloaded) {
		this.name = name;
		this.arguments = arguments;
		this.returnType = returnType;
		this.isOverloaded = overloaded;
	}
		
	public FunctionSignature(QName name, SequenceType[] arguments, SequenceType returnType) {
		this(name, arguments, returnType, false);	
	}
	
	public QName getName() {
		return name;
	}
	
	public int getArgumentCount() {
		return arguments != null ? arguments.length : 0;
	}
	
	public SequenceType getReturnType() {
		return returnType;
	}
	
	public SequenceType[] getArgumentTypes() {
		return arguments;
	}
	
	public boolean isOverloaded() {
		return isOverloaded;
	}
}
