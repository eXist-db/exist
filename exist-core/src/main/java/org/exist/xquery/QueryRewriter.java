/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery;

/**
 * Base class to be implemented by an index module if it wants to rewrite
 * certain query expressions. Subclasses should overwrite the rewriteXXX methods
 * they are interested in.
 *
 * @author Wolfgang Meier
 */
public class QueryRewriter {

    private final XQueryContext context;

    public QueryRewriter(XQueryContext context) {
        this.context = context;
    }

    /**
     * Rewrite the expression to make use of indexes. The method may also return an additional
     * pragma to be added to the extension expression which is inserted by the optimizer.
     *
     * @param locationStep the location step to rewrite
     * @return a pragma expression to replace the step or null if not applicable
     * @throws XPathException in case of a static error
     */
    public Pragma rewriteLocationStep(LocationStep locationStep) throws XPathException {
        return null;
    }

    protected XQueryContext getContext() {
        return context;
    }
}
