/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2020 The eXist-db Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.util;

import org.exist.xslt.EXistURIResolver;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

/**
 * URI Resolver, that first rewrites URLs like:
 *  exist://localhost/db -&gt; /db.
 *  exist://localhost:1234/db -&gt; xmldb:exist://localhost:1234/db
 *  exist://some-other-host/db -&gt; xmldb:exist://some-other-host/db
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class EXistURISchemeURIResolver implements URIResolver {
    private final EXistURIResolver eXistURIResolver;

    public EXistURISchemeURIResolver(final EXistURIResolver eXistURIResolver) {
        this.eXistURIResolver = eXistURIResolver;
    }

    @Override
    public Source resolve(final String href, final String base) throws TransformerException {
        return eXistURIResolver.resolve(
                rewriteScheme(href),
                rewriteScheme(base)
        );
    }

    private String rewriteScheme(String uri) {
        if (uri != null) {
            if (uri.startsWith("exist://localhost")) {
                uri = uri.replace("exist://localhost/db", "/db");
            } else if (uri.startsWith("exist://")) {
                uri = uri.replace("exist://", "xmldb:exist://");
            }
        }

        return uri;
    }
}
