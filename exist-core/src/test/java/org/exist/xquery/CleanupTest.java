/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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

package org.exist.xquery;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.dom.persistent.DocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.EXistXQueryService;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Sequence;
import org.junit.*;
import org.junit.runner.RunWith;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;

/**
 * Test if inline functions and functions defined in imported modules are properly reset.
 *
 * @author Wolfgang
 */
public class CleanupTest {

    private final static String MODULE_NS = "http://exist-db.org/test";

    private final static String TEST_MODULE = "module namespace t=\"" + MODULE_NS + "\";\n" +
            "declare variable $t:VAR := 123;\n" +
            "declare variable $t:VAR2 := 456;\n" +
            "declare function t:test($a) { $a || $t:VAR };\n" +
            "declare function t:inline($a) { function() { $a } };";

    private final static String TEST_QUERY = "import module namespace t=\"" + MODULE_NS + "\" at " +
            "\"xmldb:exist:///db/test/test-module.xql\";" +
            "t:test('Hello world')";

    private final static String TEST_INLINE = "let $a := \"a\"\n" +
            "let $func := function() { $a }\n" +
            "return\n" +
            "   $func";

    private final static String INTERNAL_MODULE_TEST = "import module namespace tt=\"" + MODULE_NS + "\" at " +
            "\"java:org.exist.xquery.TestModule\";" +
            "tt:test()";

    private final static String INTERNAL_MODULE_EVAL_TEST = "import module namespace tt=\"" + MODULE_NS + "\" at " +
            "\"java:org.exist.xquery.TestModule\";" +
            "util:eval('123')," +
            "tt:test()";

    private Collection collection;

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Before
    public void setup() throws XMLDBException {
        final CollectionManagementService service =
                (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        collection = service.createCollection("test");
        final Resource doc = collection.createResource("test-module.xql", "BinaryResource");
        doc.setContent(TEST_MODULE);
        ((EXistResource) doc).setMimeType("application/xquery");
        collection.storeResource(doc);
    }

    @After
    public void tearDown() throws XMLDBException {
        final CollectionManagementService service =
                (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        service.removeCollection("test");
    }

    @Test
    public void resetStateOfModuleVars() throws XMLDBException, XPathException {
        final EXistXQueryService service = (EXistXQueryService)collection.getService("XQueryService", "1.0");
        final CompiledExpression compiled = service.compile(TEST_QUERY);

        final Module module = ((PathExpr) compiled).getContext().getModule(MODULE_NS);
        final java.util.Collection<VariableDeclaration> varDecls = ((ExternalModule) module).getVariableDeclarations();
        final Iterator<VariableDeclaration> vi = varDecls.iterator();
        final VariableDeclaration var1 = vi.next();
        final VariableDeclaration var2 = vi.next();
        final FunctionCall root = (FunctionCall) ((PathExpr) compiled).getFirst();
        final UserDefinedFunction calledFunc = root.getFunction();
        final Expression calledBody = calledFunc.getFunctionBody();

        // set some property so we can test if it gets cleared
        calledFunc.setContextDocSet(DocumentSet.EMPTY_DOCUMENT_SET);
        calledBody.setContextDocSet(DocumentSet.EMPTY_DOCUMENT_SET);
        var1.setContextDocSet(DocumentSet.EMPTY_DOCUMENT_SET);
        var2.setContextDocSet(DocumentSet.EMPTY_DOCUMENT_SET);

        // execute query and check result
        final ResourceSet result = service.execute(compiled);
        assertEquals(result.getSize(), 1);
        assertEquals(result.getResource(0).getContent(), "Hello world123");

        Sequence[] args = calledFunc.getCurrentArguments();
        assertNull(args);
        assertNull(calledFunc.getContextDocSet());
        assertNull(calledBody.getContextDocSet());
        assertNull(var1.getContextDocSet());
        assertNull(var2.getContextDocSet());
    }

    @Test
    public void resetStateOfInlineFunc() throws XMLDBException, EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = BrokerPool.getInstance();
        final XQuery xquery = pool.getXQueryService();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            // execute query to get a function item
            final Sequence result = xquery.execute(broker, TEST_INLINE, Sequence.EMPTY_SEQUENCE);
            assertEquals(result.getItemCount(), 1);
            final FunctionCall call = ((FunctionReference)result.itemAt(0)).getCall();
            // closure variables are set when function item is created, but should be cleared after query
            final List<ClosureVariable> closure = call.getFunction().getClosureVariables();
            assertNull(closure);
        }
    }

    @Test
    public void preserveExternalVariable() throws XMLDBException, XPathException {
        // see https://github.com/eXist-db/exist/pull/1512 and use of util:eval
        final EXistXQueryService service = (EXistXQueryService)collection.getService("XQueryService", "1.0");

        final CompiledExpression compiled = service.compile(INTERNAL_MODULE_EVAL_TEST);
        final Module module = ((PathExpr) compiled).getContext().getModule(MODULE_NS);
        module.declareVariable(new QName("VAR", MODULE_NS, "t"), "TEST");

        final ResourceSet result = service.execute(compiled);
        assertEquals(result.getSize(), 2);
        assertEquals(result.getResource(1).getContent(), "TEST");

        final Variable var = module.resolveVariable(new QName("VAR", MODULE_NS, "t"));
        assertNull(var);
    }

    @Test
    public void resetStateofInternalModule() throws XMLDBException, XPathException {
        final EXistXQueryService service = (EXistXQueryService)collection.getService("XQueryService", "1.0");

        final CompiledExpression compiled = service.compile(INTERNAL_MODULE_TEST);
        final Module module = ((PathExpr) compiled).getContext().getModule(MODULE_NS);
        module.declareVariable(new QName("VAR", MODULE_NS, "t"), "TEST");
        final InternalFunctionCall root = (InternalFunctionCall) ((PathExpr) compiled).getFirst();
        final TestModule.TestFunction func = (TestModule.TestFunction) root.getFunction();

        final ResourceSet result = service.execute(compiled);
        assertEquals(result.getSize(), 1);
        assertEquals(result.getResource(0).getContent(), "TEST");
        assertFalse(func.dummyProperty);
    }

}
