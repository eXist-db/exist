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
package org.exist.xquery.pragmas;

import org.exist.xquery.*;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.value.Sequence;

public class ProfilePragma extends Pragma {

    public final static QName PROFILING_PRAGMA = new QName("profiling", Namespaces.EXIST_NS, "exist");
    
    public ProfilePragma(QName qname, String contents) throws XPathException {
        super(qname, contents);
    }

    public void after(XQueryContext context, Expression expression) throws XPathException {
    	final Profiler profiler = context.getProfiler();
    	profiler.setEnabled(false);
    }

    public void before(XQueryContext context, Expression expression, Sequence contextSequence) throws XPathException {
    	final Profiler profiler = context.getProfiler();
    	final Option pragma = new Option(getQName(), getContents());
    	profiler.configure(pragma);
    }
}
