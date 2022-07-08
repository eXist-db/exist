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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Sequence;

public class TimerPragma extends Pragma {

    public  final static QName TIMER_PRAGMA = new QName("timer", Namespaces.EXIST_NS, "exist");
    
    private Logger log = null;
    
    private long start;
    private boolean verbose = true;

    public TimerPragma(QName qname, String contents) throws XPathException {
        this(null, qname, contents);
    }

    public TimerPragma(final Expression expression, QName qname, String contents) throws XPathException {
        super(expression, qname, contents);
        if (contents != null && contents.length() > 0) {
            final String options[] = Option.tokenize(contents);
            for (String option : options) {
                final String param[] = Option.parseKeyValuePair(option);
                if (param == null) {
                    throw new XPathException((Expression) null, "Invalid content found for pragma " + TIMER_PRAGMA.getStringValue() +
                            ": " + contents);
                }
                if ("verbose".equals(param[0])) {
                    verbose = "yes".equals(param[1]);
                } else if ("logger".equals(param[0])) {
                    log = LogManager.getLogger(param[1]);
                }
            }
        }
        if (log == null)
            {log = LogManager.getLogger(TimerPragma.class);}
    }

    public void after(XQueryContext context) throws XPathException {
        after(context, null);
    }

    public void after(XQueryContext context, Expression expression) throws XPathException {
        final long elapsed = System.currentTimeMillis() - start;
        if (log.isTraceEnabled()) {
            if (verbose)
                {
                    log.trace("Elapsed: {}ms. for expression:\n{}", elapsed, ExpressionDumper.dump(expression));}
            else
                {
                    log.trace("Elapsed: {}ms.", elapsed);}
        }
    }

    public void before(XQueryContext context, Sequence contextSequence) throws XPathException {
        before(context, null, contextSequence);
    }

    public void before(XQueryContext context, Expression expression, Sequence contextSequence) throws XPathException {
        start = System.currentTimeMillis();
    }
}
