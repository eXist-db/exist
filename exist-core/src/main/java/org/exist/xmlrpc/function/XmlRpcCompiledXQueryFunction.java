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
package org.exist.xmlrpc.function;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import com.evolvedbinary.j8fu.function.Function2E;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;

/**
 * Specialisation of FunctionE which deals with
 * XML-RPC server operations; Predominantly converts exceptions
 * from the database into EXistException types
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@FunctionalInterface
public interface XmlRpcCompiledXQueryFunction<R> extends Function2E<CompiledXQuery, R, EXistException, PermissionDeniedException> {

    @Override
    default R apply(final CompiledXQuery compiledQuery) throws EXistException, PermissionDeniedException {
        try {
            return applyXmlRpc(compiledQuery);
        } catch(final XPathException e) {
            throw new EXistException(e);
        }
    }

    /**
     * Signature for lambda function which takes a compiled XQuery
     *
     * @param compiledQuery The compiled XQuery
     *
     * @return the result of the function
     *
     * @throws EXistException if an error occurs with the database
     * @throws PermissionDeniedException if the caller has insufficient priviledges
     * @throws XPathException if executing the XQuery raises an error
     */
    R applyXmlRpc(final CompiledXQuery compiledQuery) throws EXistException, PermissionDeniedException, XPathException;
}
