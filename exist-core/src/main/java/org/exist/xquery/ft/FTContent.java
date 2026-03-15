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
package org.exist.xquery.ft;

import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;

/**
 * W3C XQFT 3.0 — FTContent positional filter.
 *
 * <pre>FTContent ::= ("at" "start") | ("at" "end") | ("entire" "content")</pre>
 */
public class FTContent extends FTAbstractExpr {

    public enum ContentType { AT_START, AT_END, ENTIRE_CONTENT }

    private ContentType contentType;

    public FTContent(final XQueryContext context) {
        super(context);
    }

    public void setContentType(final ContentType contentType) {
        this.contentType = contentType;
    }

    public ContentType getContentType() {
        return contentType;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        // no children to analyze
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        switch (contentType) {
            case AT_START: dumper.display("at start"); break;
            case AT_END: dumper.display("at end"); break;
            case ENTIRE_CONTENT: dumper.display("entire content"); break;
        }
    }

    @Override
    public String toString() {
        switch (contentType) {
            case AT_START: return "at start";
            case AT_END: return "at end";
            case ENTIRE_CONTENT: return "entire content";
            default: return "";
        }
    }
}
