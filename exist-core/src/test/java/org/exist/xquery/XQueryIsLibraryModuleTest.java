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

import antlr.BaseAST;
import antlr.CommonAST;
import org.exist.xquery.parser.XQueryTreeParser;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class XQueryIsLibraryModuleTest {

    @Test
    public void isLibraryModuleAstVersionDeclAndLibraryModule() {
        final BaseAST xqueryModuleDecl = new CommonAST();
        xqueryModuleDecl.setType(XQueryTreeParser.MODULE_DECL);

        final BaseAST xqueryDecl = new CommonAST();
        xqueryDecl.setType(XQueryTreeParser.VERSION_DECL);
        xqueryDecl.setText("3.1");
        xqueryDecl.setNextSibling(xqueryModuleDecl);

        assertTrue(XQuery.isLibraryModule(xqueryDecl));
    }

    @Test
    public void isLibraryModuleAstVersionDeclAndMainModule() {
        final BaseAST namespaceDecl = new CommonAST();
        namespaceDecl.setType(XQueryTreeParser.NAMESPACE_DECL);

        final BaseAST xqueryDecl = new CommonAST();
        xqueryDecl.setType(XQueryTreeParser.VERSION_DECL);
        xqueryDecl.setText("3.1");
        xqueryDecl.setNextSibling(namespaceDecl);

        assertFalse(XQuery.isLibraryModule(xqueryDecl));
    }

    @Test
    public void isLibraryModuleAstLibraryModule() {
        final BaseAST xqueryModuleDecl = new CommonAST();
        xqueryModuleDecl.setType(XQueryTreeParser.MODULE_DECL);

        assertTrue(XQuery.isLibraryModule(xqueryModuleDecl));
    }

    @Test
    public void isLibraryModuleAstMainModule() {
        final BaseAST namespaceDecl = new CommonAST();
        namespaceDecl.setType(XQueryTreeParser.NAMESPACE_DECL);

        assertFalse(XQuery.isLibraryModule(namespaceDecl));
    }

    @Test
    public void isLibraryModuleAstVersionDeclAndLibraryModuleAndProlog() {
        final BaseAST eof = new CommonAST();
        eof.setType(XQueryTreeParser.EOF);

        final BaseAST functionDecl = new CommonAST();
        functionDecl.setType(XQueryTreeParser.FUNCTION_DECL);
        functionDecl.setNextSibling(eof);

        final BaseAST importModuleDecl = new CommonAST();
        importModuleDecl.setType(XQueryTreeParser.MODULE_IMPORT);
        importModuleDecl.setNextSibling(functionDecl);

        final BaseAST importNamespaceDecl = new CommonAST();
        importNamespaceDecl.setType(XQueryTreeParser.NAMESPACE_DECL);
        importNamespaceDecl.setNextSibling(importModuleDecl);

        final BaseAST xqueryModuleDecl = new CommonAST();
        xqueryModuleDecl.setType(XQueryTreeParser.MODULE_DECL);
        xqueryModuleDecl.setNextSibling(importNamespaceDecl);

        final BaseAST xqueryDecl = new CommonAST();
        xqueryDecl.setType(XQueryTreeParser.VERSION_DECL);
        xqueryDecl.setText("3.1");
        xqueryDecl.setNextSibling(xqueryModuleDecl);

        assertTrue(XQuery.isLibraryModule(xqueryDecl));
    }

    @Test
    public void isLibraryModuleAstVersionDeclAndMainModuleAndProlog() {
        final BaseAST eof = new CommonAST();
        eof.setType(XQueryTreeParser.EOF);

        final BaseAST functionDecl = new CommonAST();
        functionDecl.setType(XQueryTreeParser.FUNCTION_DECL);
        functionDecl.setNextSibling(eof);

        final BaseAST importModuleDecl = new CommonAST();
        importModuleDecl.setType(XQueryTreeParser.MODULE_IMPORT);
        importModuleDecl.setNextSibling(functionDecl);

        final BaseAST importNamespaceDecl = new CommonAST();
        importNamespaceDecl.setType(XQueryTreeParser.NAMESPACE_DECL);
        importNamespaceDecl.setNextSibling(importModuleDecl);

        final BaseAST xqueryDecl = new CommonAST();
        xqueryDecl.setType(XQueryTreeParser.VERSION_DECL);
        xqueryDecl.setText("3.1");
        xqueryDecl.setNextSibling(importNamespaceDecl);

        assertFalse(XQuery.isLibraryModule(xqueryDecl));
    }
}
