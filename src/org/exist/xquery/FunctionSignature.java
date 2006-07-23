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

import org.exist.dom.QName;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Describes the signature of a built-in or user-defined function, i.e.
 * its name, the type and cardinality of its arguments and its return type.
 *  
 * @author wolf
 */
public class FunctionSignature {

	/**
	 * Default sequence type for function parameters.
	 */
	public final static SequenceType DEFAULT_TYPE =
		new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE);
	
	/**
	 * Empty array to specify if a function doesn't take any arguments.
	 */
	public final static SequenceType[] NO_ARGS = new SequenceType[0];
	
	public final static SequenceType[] singleArgument(SequenceType arg) {
		return new SequenceType[] { arg };
	}
	
	private final QName name;
	private SequenceType[] arguments;
	private SequenceType returnType;
	private boolean isOverloaded = false;
	private String description = null;
	
	public FunctionSignature(QName name) {
		this(name, null, DEFAULT_TYPE, false);
	}
	
	public FunctionSignature(QName name, SequenceType[] arguments, SequenceType returnType) {
		this(name, null, arguments, returnType);
	}
	
	public FunctionSignature(QName name, SequenceType[] arguments, SequenceType returnType,
		boolean overloaded) {
		this(name, null, arguments, returnType, overloaded);
	}
		
	public FunctionSignature(QName name, String description, SequenceType[] arguments, SequenceType returnType) {
		this(name, description, arguments, returnType, false);	
	}
	
	/**
	 * Create a new function signature.
	 * 
	 * @param name the QName of the function.
	 * @param arguments the sequence types of all expected arguments
	 * @param returnType the sequence type returned by the function
	 * @param overloaded set to true if the function may expect additional parameters
	 */		
	public FunctionSignature(QName name, String description, SequenceType[] arguments, SequenceType returnType,
		boolean overloaded) {
		this.name = name;
		this.arguments = arguments;
		this.returnType = returnType;
		this.isOverloaded = overloaded;
		this.description = description;
	}
	
	public QName getName() {
		return name;
	}
	
	public int getArgumentCount() {
		if(isOverloaded)
			return -1;
		return arguments != null ? arguments.length : 0;
	}
	
	public FunctionId getFunctionId() {
		return new FunctionId(name, getArgumentCount());
	}
	
	public SequenceType getReturnType() {
		return returnType;
	}
	
	public void setReturnType(SequenceType type) {
		returnType = type;
	}
	
	public SequenceType[] getArgumentTypes() {
		return arguments;
	}
	
	public void setArgumentTypes(SequenceType[] types) {
		this.arguments = types;
	}
	
	public String getDescription() {
		return description;
	}
	
	public boolean isOverloaded() {
		return isOverloaded;
	}
	
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append(name.toString());
		buf.append('(');
		if(arguments != null) {
            char var = 'a';
			for(int i = 0; i < arguments.length; i++) {
				if(i > 0)
					buf.append(", ");
                buf.append('$');
                buf.append((char)(var + i));
                buf.append(" as ");
				buf.append(arguments[i].toString());
			}
            if(isOverloaded)
                buf.append(", ...");
		}
		buf.append(") ");
		buf.append(returnType.toString());
		return buf.toString();
	}
    
    public boolean equals(Object obj) {
        FunctionSignature other = (FunctionSignature) obj;
        if (name.equalsSimple(other.name))
            return getArgumentCount() == other.getArgumentCount();
        return false;
    }
}
