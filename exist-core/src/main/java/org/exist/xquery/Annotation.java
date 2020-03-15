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
 * Represents an XQuery 3.0 Annotation
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class Annotation {
    
    private final QName name;
    private final LiteralValue value[];
    private final FunctionSignature signature;
    
    public Annotation(final QName name, final FunctionSignature signature) {
        this(name, new LiteralValue[0], signature);
    }
    
    public Annotation(final QName name, final LiteralValue[] value, final FunctionSignature signature) {
       this.name = name;
       this.value = value;
       this.signature = signature;
    }
    
    public QName getName() {
        return name;
    }
    
    public LiteralValue[] getValue() {
        return value;
    }
    
    /**
     * Get the signature of the function on which this
     * annotation was placed
     *
     * @return the function signature
     */
    public FunctionSignature getFunctionSignature() {
        return signature;
    }
}