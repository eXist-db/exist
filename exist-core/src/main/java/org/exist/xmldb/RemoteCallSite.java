/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xmldb;

import org.xmldb.api.base.XMLDBException;

import java.util.List;

/**
 * Represents a remote method call site.
 */
@FunctionalInterface
public interface RemoteCallSite {
    /**
     * Executes the given {@code methodName} with the given {@code parameters} and returns the result. In case of
     * an error a {@link XMLDBException} will be thrown.
     *
     * @param methodName the method name to be invoked
     * @param parameters the list of method arguments
     * @return the result of the method
     * @throws XMLDBException in case of the invocation fails
     */
    Object execute(String methodName, List<Object> parameters) throws XMLDBException;
}
