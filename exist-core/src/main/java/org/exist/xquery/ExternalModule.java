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
import org.exist.source.Source;

import java.util.Collection;
import java.util.Map;

/**
 * An external library module implemented in XQuery and loaded
 * through the "import module" directive.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public interface ExternalModule extends Module {

    void setNamespace(String prefix, String namespace);

    void setDescription(String desc);

    void addMetadata(String key, String value);

    Map<String, String> getMetadata();

    /**
     * Declare a new function. Called by the XQuery compiler
     * when parsing a library module for every function declaration.
     *
     * @param func the function to add
     */
    void declareFunction(UserDefinedFunction func) throws XPathException;

    /**
     * Try to find the function identified by qname. Returns null
     * if the function is undefined.
     *
     * @param qname         the name of the function to look for
     * @param arity         arity of the function to look for
     * @param callerContext context of the caller - needed to check if
     *                      found function should be visible
     * @return the function found
     * @throws XPathException in case of a dynamic error
     */
    UserDefinedFunction getFunction(QName qname, int arity, XQueryContext callerContext) throws XPathException;

    void declareVariable(QName qname, VariableDeclaration decl) throws XPathException;

    /**
     * Analyze declared variables. Needs to be called when the module was imported dynamically.
     *
     * @throws XPathException in case of static errors
     */
    void analyzeGlobalVars() throws XPathException;

    Collection<VariableDeclaration> getVariableDeclarations();

    /**
     * Get the source object this module has been read from.
     * <p>
     * This is required for query access control.
     *
     * @return The source object this module has been read from.
     */
    Source getSource();

    /**
     * Set the source object this module has been read from.
     * <p>
     * This is required to check the validity of a compiled expression.
     *
     * @param source the source instance
     */
    void setSource(Source source);

    XQueryContext getContext();

    /**
     * Set the XQueryContext of this module. This will be a sub-context
     * of the main context as parts of the static context are shared.
     *
     * @param context the context to set
     */
    void setContext(XQueryContext context);

    /**
     * Is this module still valid or should it be reloaded from its source?
     *
     * @return true if module should be reloaded
     */
    boolean moduleIsValid();

    /**
     * Returns the root expression associated with this context.
     *
     * @return root expression
     */
    Expression getRootExpression();
}
