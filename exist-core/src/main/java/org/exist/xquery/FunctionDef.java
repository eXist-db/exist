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

/**
 * A function definition, consisting of a signature and the implementing class.
 * 
 * Used by modules to define the available functions. A single implementation class
 * can be mapped to more than one function signature, given that the signatures differ
 * in name or the number of expected arguments.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FunctionDef {

    protected final FunctionSignature signature;
    protected final Class<? extends Function> implementingClass;

    public FunctionDef(final FunctionSignature signature, final Class<? extends Function> implementingClass) {
        this.signature = signature;
        this.implementingClass = implementingClass;
    }

    public FunctionSignature getSignature() {
        return signature;
    }

    public Class<? extends Function> getImplementingClass() {
        return implementingClass;
    }
}
