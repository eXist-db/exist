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
package org.exist.xquery.functions.lsp;

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

import static org.exist.xquery.FunctionDSL.functionDefs;

/**
 * XQuery function module providing Language Server Protocol support.
 *
 * <p>This module exposes eXist-db's XQuery compiler internals as XQuery functions,
 * enabling LSP servers to provide diagnostics, symbol information, and other
 * language intelligence features.</p>
 *
 * @author eXist-db
 */
public class LspModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://exist-db.org/xquery/lsp";

    public static final String PREFIX = "lsp";

    public static final String RELEASE = "7.0.0";

    public static final FunctionDef[] functions = functionDefs(
            functionDefs(Completions.class,
                    Completions.FS_COMPLETIONS),
            functionDefs(Definition.class,
                    Definition.FS_DEFINITION),
            functionDefs(Diagnostics.class,
                    Diagnostics.FS_DIAGNOSTICS),
            functionDefs(Hover.class,
                    Hover.FS_HOVER),
            functionDefs(Symbols.class,
                    Symbols.FS_SYMBOLS)
    );

    public LspModule(final Map<String, List<?>> parameters) {
        super(functions, parameters, true);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "Functions for Language Server Protocol support, exposing compiler diagnostics and symbol information";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASE;
    }

    static QName qname(final String localPart) {
        return new QName(localPart, NAMESPACE_URI, PREFIX);
    }
}
