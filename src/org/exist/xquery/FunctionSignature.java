/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.HashMap;
import java.util.Map;

/**
 * Describes the signature of a built-in or user-defined function, i.e.
 * its name, the type and cardinality of its arguments and its return type.
 *  
 * @author wolf
 * @author lcahlander
 * @version 1.3
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

	private static final String DEPRECATION_REMOVAL_MESSAGE = "\nThis function could be removed at anytime during the 1.5 development and will be removed in the 1.6 release.";
	
	public final static SequenceType[] singleArgument(SequenceType arg) {
		return new SequenceType[] { arg };
	}
	
    private Annotation[] annotations;
	private final QName name;
	private SequenceType[] arguments;
	private SequenceType returnType;
	private boolean isOverloaded = false;
	private String description = null;
	private String deprecated = null;
    private Map<String, String> metadata = null;
	
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
	
	public FunctionSignature(QName name, String description, SequenceType[] arguments, SequenceType returnType,
			String deprecated) {
		this(name, description, arguments, returnType, false);
		setDeprecated(deprecated);
	}
	
	public FunctionSignature(QName name, String description, SequenceType[] arguments, SequenceType returnType,
			boolean overloaded, String deprecated) {
		this(name, description, arguments, returnType, overloaded);
		setDeprecated(deprecated);
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
	
        public Annotation[] getAnnotations() {
            return annotations;
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
        
    public void setAnnotations(Annotation[] annotations) {
        this.annotations = annotations;
    }
	
	public String getDescription() {
		return description;
	}

    public void setDescription(String description) {
        this.description = description;
    }

    public void addMetadata(String key, String value) {
        if (metadata == null) {
            metadata = new HashMap<String, String>(5);
        }
        String old = metadata.get(key);
        if (old != null)
            // if the key exists, simply append the new value
            value = old + ", " + value;
        metadata.put(key, value);
    }

    public String getMetadata(String key) {
        if (metadata == null)
            return null;
        return metadata.get(key);
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

	public boolean isOverloaded() {
		return isOverloaded;
	}
	
	public boolean isDeprecated() {
		return deprecated != null;
	}
	
	public String getDeprecated() {
		if (deprecated != null && deprecated.length() > 0)
			return deprecated + DEPRECATION_REMOVAL_MESSAGE;
		else
			return null;
	}
	
	public void setDeprecated(String message) {
		deprecated = message;
	}
	
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append(name.getStringValue());
		buf.append('(');
		if(arguments != null) {
            char var = 'a';
			for(int i = 0; i < arguments.length; i++) {
				if(i > 0)
					buf.append(", ");
                buf.append('$');
                if (arguments[i] instanceof FunctionParameterSequenceType) {
                    buf.append(((FunctionParameterSequenceType)arguments[i]).getAttributeName());
                } else {
                    buf.append((char)(var + i));
                }
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


    @Override
    public boolean equals(Object obj) {
        if(obj == null || !(obj instanceof FunctionSignature)) {
            return false;
        }
        
        final FunctionSignature other = (FunctionSignature) obj;
        if (name == null) {
            if(other.name != null) {
                return false;
            }    
            return getArgumentCount() == other.getArgumentCount();
        }
        
        if(name.equalsSimple(other.name)) {
            return getArgumentCount() == other.getArgumentCount();
        }
        
        return false;
    }
}
