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
import org.exist.source.Source;
import org.exist.storage.DBBroker;

import java.util.Collection;
import java.util.Map;

/**
 * An external library module implemented in XQuery and loaded
 * through the "import module" directive.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public interface ExternalModule extends Module {

    public void setNamespace(String prefix, String namespace);

    public void setDescription(String desc);

    public void addMetadata(String key, String value);

    public Map<String, String> getMetadata();

    /**
     * Declare a new function. Called by the XQuery compiler
     * when parsing a library module for every function declaration.
     * 
     * @param func
     */
    public void declareFunction(UserDefinedFunction func);

    /**
     * Try to find the function identified by qname. Returns null
     * if the function is undefined.
     * 
     * @param qname
     */
    public UserDefinedFunction getFunction(QName qname, int arity, XQueryContext callerContext) throws XPathException;

    public void declareVariable(QName qname, VariableDeclaration decl) throws XPathException;

    public void analyzeGlobalVars() throws XPathException;

    public Collection<VariableDeclaration> getVariableDeclarations();

    /**
     * Get the source object this module has been read from.
     *
     * This is required for query access control.
     * @return The source object this module has been read from.
     */
    public Source getSource();

    /**
     * Set the source object this module has been read from.
     * 
     * This is required to check the validity of a compiled expression.
     * @param source
     */
    public void setSource(Source source);

    public XQueryContext getContext();

    /**
     * Set the XQueryContext of this module. This will be a sub-context
     * of the main context as parts of the static context are shared. 
     * 
     * @param context
     */
    public void setContext(XQueryContext context);

    /**
     * Is this module still valid or should it be reloaded from its source?
     */
    public boolean moduleIsValid(DBBroker broker);

    /**
     * Returns the root expression associated with this context.
     *
     * @return  root expression
     */
    public Expression getRootExpression();
}
