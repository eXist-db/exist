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

import java.io.Writer;

import org.exist.source.Source;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.xmldb.api.base.CompiledExpression;


/**
 * @author wolf
 */
public interface CompiledXQuery extends CompiledExpression {
    
    /**
     * Reset the compiled expression tree. Discard all
     * temporary expression results.
     */
    public void reset();
    
    /**
     * @return the {@link XQueryContext} used to create this query
     */
    public XQueryContext getContext();
    
    public void setContext(XQueryContext context);
    
    /**
     * Execute the compiled query, optionally using the specified
     * sequence as context.
     * 
     * @param contextSequence the context sequence
     * @param contextItem a single item, taken from context. This defines the item,
     * the expression should work on.
     *
     * @return the result.
     *
     * @throws XPathException if an error occurs during evaluation.
     */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException;

    /**
     * Is the compiled expression still valid? Returns false if, for example,
     * the source code of one of the imported modules has changed.
     *
     * @return true if the compiled query is valid
     */
    public boolean isValid();

    /**
     * Writes a diagnostic dump of the expression structure to the
     * specified writer.
     *
     * @param writer the writer to dump the query to.
     */
    public void dump(Writer writer);

    /**
     * Gets the source of this query.
     *
     * @return This query's source
     */
    public Source getSource();
} 