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

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import java.util.List;

/**
 * A simple hierarchy of URI Resolvers.
 *
 * The first resolver that matches returns the result.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class URIResolverHierarchy implements URIResolver {

    private final URIResolver[] uriResolvers;

    /**
     * @param uriResolvers the URI resolvers in order of precedence, most significant first.
     */
    public URIResolverHierarchy(final URIResolver... uriResolvers) {
        this.uriResolvers = uriResolvers;
    }

    /**
     * @param uriResolvers the URI resolvers in order of precedence, most significant first.
     */
    public URIResolverHierarchy(final List<URIResolver> uriResolvers) {
        this.uriResolvers = uriResolvers.toArray(new URIResolver[0]);
    }


    @Override
    public Source resolve(final String href, final String base) throws TransformerException {
        if (uriResolvers == null) {
            return null;
        }

        TransformerException firstTransformerException = null;

        for (final URIResolver uriResolver : uriResolvers) {
            try {
                final Source source = uriResolver.resolve(href, base);
                if (source != null) {
                    return source;
                }
            } catch (final TransformerException e) {
                if (firstTransformerException == null) {
                    firstTransformerException = e;
                }
            }
        }

        if (firstTransformerException != null) {
            throw firstTransformerException;
        } else {
            return null;
        }
    }
}
