/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.interpreter;

import org.exist.source.Source;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

public interface Compiled extends IPathExpr {

    public void reset();
    
    /**
     * @return the {@link Context} used to create this query
     */
    public XQueryContext getContext();
    
    public void setContext(Context context);
    
    /**
     * Execute the compiled query, optionally using the specified
     * sequence as context.
     *
     * @param contextSequence the context sequence.
     *
     * @throws XPathException if an error occurs during evaluation.
     */
    public Sequence eval(Sequence contextSequence) throws XPathException;

    /**
     * Is the compiled expression still valid? Returns false if, for example,
     * the source code of one of the imported modules has changed.
     */
    public boolean isValid();

    /**
     * Gets the source of this query.
     *
     * @return This query's source
     */
    public Source getSource();
}
