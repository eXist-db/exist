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

import org.exist.repo.ExistRepository;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Enumerates every XQuery module visible to a live {@link XQueryContext}: Java built-in
 * modules, Java/XQuery EXPath package modules, and conf.xml-mapped XQuery modules - the "live"
 * module sources, as opposed to {@code enabled="no"}-suppressed built-in modules, which never
 * reach a live {@link Module} instance and so can't be enumerated this way (callers that need
 * those must consult {@link ModuleRegistration} directly).
 *
 * <p>Shared by {@code system:get-registered-modules()} and the deprecated
 * {@code util:registered-modules()}/{@code util:registered-modules-info()}, so all three walk the
 * exact same enumeration instead of maintaining separate copies that can drift.
 */
public final class LiveModules {

    private LiveModules() {
    }

    /** One live module: its namespace URI, default prefix, and where it came from. */
    public record Entry(String uri, String prefix, String source) {
        public static final String SOURCE_BUILT_IN = "built-in";
        public static final String SOURCE_PACKAGE = "package";
        public static final String SOURCE_MAPPED = "mapped";
    }

    /**
     * Walks {@code context}'s built-in modules, EXPath package modules, and conf.xml-mapped
     * modules, in that precedence order, de-duplicated by namespace URI.
     *
     * @param context a live XQueryContext - every built-in module is already loaded on it (done
     *                eagerly at construction, see {@code XQueryContext#loadDefaults()}), so
     *                callers should pass the calling context rather than constructing a
     *                throwaway one, which would reflectively re-instantiate every built-in
     *                module class and re-run its {@code prepare()} hook
     * @return one {@link Entry} per distinct namespace URI
     */
    public static List<Entry> collect(final XQueryContext context) {
        final List<Entry> result = new ArrayList<>();
        final Set<String> seen = new HashSet<>();

        for (final Iterator<Module> i = context.getRootModules(); i.hasNext(); ) {
            final Module module = i.next();
            final String nsUri = module.getNamespaceURI();
            if (seen.add(nsUri)) {
                result.add(new Entry(nsUri, module.getDefaultPrefix(), Entry.SOURCE_BUILT_IN));
            }
        }

        if (context.getRepository().isPresent()) {
            final ExistRepository repo = context.getRepository().get();
            for (final URI uri : repo.getJavaModules()) {
                addPackageEntry(context, uri, seen, result);
            }
            for (final URI uri : repo.getXQueryModules()) {
                addPackageEntry(context, uri, seen, result);
            }
        }

        for (final Iterator<String> i = context.getMappedModuleURIs(); i.hasNext(); ) {
            final String nsUri = i.next();
            if (seen.add(nsUri)) {
                result.add(new Entry(nsUri, modulePrefix(context, nsUri), Entry.SOURCE_MAPPED));
            }
        }

        return result;
    }

    private static void addPackageEntry(final XQueryContext context, final URI uri, final Set<String> seen,
            final List<Entry> result) {
        final String nsUri = uri.toString();
        if (seen.add(nsUri)) {
            result.add(new Entry(nsUri, modulePrefix(context, nsUri), Entry.SOURCE_PACKAGE));
        }
    }

    private static String modulePrefix(final XQueryContext context, final String namespaceURI) {
        final Module[] modules = context.getRootModules(namespaceURI);
        if (modules != null && modules.length > 0) {
            return modules[0].getDefaultPrefix();
        }
        return "";
    }
}
