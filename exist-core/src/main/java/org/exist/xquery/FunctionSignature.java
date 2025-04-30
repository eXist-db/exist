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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

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
    public final static SequenceType DEFAULT_TYPE = new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE);

    /**
     * Empty array to specify if a function doesn't take any arguments.
     */
    public final static SequenceType[] NO_ARGS = new SequenceType[0];

    private static final String DEPRECATION_REMOVAL_MESSAGE = "\nThis function could be removed in the next major release version.";

    public static SequenceType[] singleArgument(final SequenceType arg) {
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

    public FunctionSignature(final FunctionSignature other) {
        this.name = other.name;
        this.arguments = other.arguments != null ? Arrays.copyOf(other.arguments, other.arguments.length) : null;
        this.returnType = other.returnType;
        this.annotations = other.annotations != null ? Arrays.copyOf(other.annotations, other.annotations.length) : null;
        this.isOverloaded = other.isOverloaded;
        this.deprecated = other.deprecated;
        this.description = other.description;
        this.metadata = other.metadata != null ? new HashMap<>(other.metadata) : null;
    }

    public FunctionSignature(final QName name) {
        this(name, null, DEFAULT_TYPE, false);
    }

    public FunctionSignature(final QName name, final SequenceType[] arguments, final SequenceType returnType) {
        this(name, null, arguments, returnType);
    }

    public FunctionSignature(final QName name, final SequenceType[] arguments, final SequenceType returnType, final boolean overloaded) {
        this(name, null, arguments, returnType, overloaded);
    }

    public FunctionSignature(final QName name, final String description, final SequenceType[] arguments, final SequenceType returnType) {
        this(name, description, arguments, returnType, false);	
    }

    public FunctionSignature(final QName name, final String description, final SequenceType[] arguments, final SequenceType returnType, final String deprecated) {
        this(name, description, arguments, returnType, false);
        setDeprecated(deprecated);
    }
        
//    public FunctionSignature(final QName name, final String description, final SequenceType[] arguments, final SequenceType returnType, final FunctionSignature deprecatedBy) {
//        this(name, description, arguments, returnType, false, "Moved to the module: " + deprecatedBy.getName().getNamespaceURI() + ", you should now use '" + deprecatedBy.getName().getPrefix() + ":" + deprecatedBy.getName().getLocalPart() + "' instead!");
//    }

    public FunctionSignature(final QName name, final String description, final SequenceType[] arguments, final SequenceType returnType, final boolean overloaded, final String deprecated) {
        this(name, description, arguments, returnType, overloaded);
        setDeprecated(deprecated);
    }
	
    /**
     * Create a new function signature.
     * 
     * @param name the QName of the function.
     * @param description documentation string describing the function
     * @param arguments the sequence types of all expected arguments
     * @param returnType the sequence type returned by the function
     * @param overloaded set to true if the function may expect additional parameters
     */		
    public FunctionSignature(final QName name, final String description, final SequenceType[] arguments, final SequenceType returnType, final boolean overloaded) {
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
        if(isOverloaded) {
            return -1;
        }
        return arguments != null ? arguments.length : 0;
    }
	
    public FunctionId getFunctionId() {
        return new FunctionId(name, getArgumentCount());
    }
	
    public SequenceType getReturnType() {
        return returnType;
    }

    public void setReturnType(final SequenceType type) {
        returnType = type;
    }

    public SequenceType[] getArgumentTypes() {
        return arguments;
    }

    public void setArgumentTypes(final SequenceType[] types) {
        this.arguments = types;
    }
        
    public void setAnnotations(final Annotation[] annotations) {
        this.annotations = annotations;
    }
	
    public String getDescription() {
            return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public void addMetadata(final String key, String value) {
        if(metadata == null) {
            metadata = new HashMap<>(5);
        }
        final String old = metadata.get(key);
        if (old != null) {
            // if the key exists, simply append the new value
            value = old + ", " + value;
        }
        metadata.put(key, value);
    }

    public String getMetadata(final String key) {
        if (metadata == null) {
            return null;
        }
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
        if (deprecated != null && deprecated.length() > 0) {
            return deprecated + DEPRECATION_REMOVAL_MESSAGE;
        } else {
            return null;
        }
    }
	
    public final void setDeprecated(final String message) {
        deprecated = message;
    }

    public boolean isPrivate() {
        final Annotation[] annotations = getAnnotations();
        if(annotations != null) {
            for(final Annotation annot : annotations) {
                final QName qn = annot.getName();
                if(qn.getNamespaceURI().equals(Namespaces.XPATH_FUNCTIONS_NS) && "private".equals(qn.getLocalPart())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(name.getStringValue());
        buf.append('(');
        if(arguments != null) {
            final char var = 'a';
            for(int i = 0; i < arguments.length; i++) {
                if(i > 0) {
                    buf.append(", ");
                }
                buf.append('$');
                if(arguments[i] instanceof FunctionParameterSequenceType) {
                    buf.append(((FunctionParameterSequenceType)arguments[i]).getAttributeName());
                } else {
                    buf.append((char)(var + i));
                }
                buf.append(" as ");
                buf.append(arguments[i].toString());
            }
            
            if(isOverloaded) {
                buf.append(", ...");
            }
        }
        buf.append(") ");
        buf.append(returnType.toString());
        
        return buf.toString();
    }

    @Override
    public boolean equals(final Object obj) {
        if(obj == null || !(obj instanceof FunctionSignature)) {
            return false;
        }
        // quick comparison by object identity
        if (this == obj) { return true; }

        // anonymous functions cannot be compared by name and argument count
        final FunctionSignature other = (FunctionSignature) obj;
        if (
                name == null || other.name == null ||
                name.getLocalPart().equals("") ||
                other.name.getLocalPart().equals("")
        ) {
            return false;
        }

        if (name.equals(other.name)) {
            return getArgumentCount() == other.getArgumentCount();
        }
        
        return false;
    }

    /**
     * Creates a new FunctionSignature with the same
     * properties albeit a new name.
     *
     * @param newName the new name for the function signature.
     * @return the new function signature.
     */
    public FunctionSignature rename(final QName newName) {
        final SequenceType[] argumentsCopy = arguments != null ? Arrays.copyOf(arguments, arguments.length) : null;
        final FunctionSignature newFunctionSignature = new FunctionSignature(newName, description, argumentsCopy, returnType, deprecated);
        newFunctionSignature.annotations = annotations != null ? Arrays.copyOf(annotations, annotations.length) : null;
        newFunctionSignature.isOverloaded = isOverloaded;
        newFunctionSignature.metadata = metadata != null ? new HashMap<>(metadata) : null;
        return newFunctionSignature;
    }
}
