/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * $Id$
 */
package org.exist.xquery.pragmas;

import org.exist.xquery.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

public class NoIndexPragma extends Pragma {

    private final static Logger LOG = LogManager.getLogger(NoIndexPragma.class);

    public  final static QName NO_INDEX_PRAGMA = new QName("no-index", Namespaces.EXIST_NS, "exist");

    public NoIndexPragma(QName qname, String contents) throws XPathException {
        super(qname, contents);
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Switching indexes off ...");
        }
        contextInfo.addFlag(Expression.USE_TREE_TRAVERSAL);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        return null;
    }

    public void before(XQueryContext context, Expression expression, Sequence contextSequence) throws XPathException {
    }

    public void after(XQueryContext context, Expression expression) throws XPathException {
    }
}
