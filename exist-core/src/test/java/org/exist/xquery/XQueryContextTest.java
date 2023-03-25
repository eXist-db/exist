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

import org.exist.storage.DBBroker;
import org.exist.security.Subject;
import org.exist.xquery.value.BinaryValue;
import org.junit.Test;
import org.easymock.EasyMock;

import javax.xml.XMLConstants;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class XQueryContextTest {
    private static final List<String> INITIAL_NAMESPACES = Arrays.asList(
            "err", "fn", "xdt", "dbgp", "local", "xsi", "exist", "java", "exerr", "xml", "xs");

    @Test
    public void prepareForExecution_setsUserFromSession() {

        //partial mock context
        XQueryContext context = EasyMock.createMockBuilder(XQueryContext.class)
                .withConstructor()
                .addMockedMethod("getUserFromHttpSession")
                .addMockedMethod("getBroker")
                .createMock();

        DBBroker mockBroker = createMock(DBBroker.class);

        Subject mockSubject = createMock(Subject.class);

        //expectations
        expect(context.getUserFromHttpSession()).andReturn(mockSubject);
        expect(context.getBroker()).andReturn(mockBroker).times(2);
        mockBroker.pushSubject(mockSubject);

        //test
        replay(context);

        context.prepareForExecution();

        verify(context);
    }

    /**
     * Test to ensure that BinaryValueInstances are
     * correctly cleaned up by the XQueryContext
     * between reuse of the context
     */
    @Test
    public void cleanUp_BinaryValueInstances() throws NoSuchFieldException, IllegalAccessException, IOException {
        final XQueryContext context = new XQueryContext();
        final XQueryWatchDog mockWatchdog = createMock(XQueryWatchDog.class);
        context.setWatchDog(mockWatchdog);

        final BinaryValue mockBin1 = createMock(BinaryValue.class);
        final BinaryValue mockBin2 = createMock(BinaryValue.class);
        final BinaryValue mockBin3 = createMock(BinaryValue.class);
        final BinaryValue mockBin4 = createMock(BinaryValue.class);
        final BinaryValue mockBin5 = createMock(BinaryValue.class);
        final BinaryValue mockBin6 = createMock(BinaryValue.class);
        final BinaryValue mockBin7 = createMock(BinaryValue.class);

        // expectations on our mocks
        mockBin1.close();
        expectLastCall().times(1);
        mockBin2.close();
        expectLastCall().times(1);
        mockBin3.close();
        expectLastCall().times(1);
        mockBin4.close();
        expectLastCall().times(1);
        mockBin5.close();
        expectLastCall().times(1);
        mockBin6.close();
        expectLastCall().times(1);
        mockBin7.close();
        expectLastCall().times(1);
        mockWatchdog.reset();
        expectLastCall().times(3);

        // prepare our mocks for our test
        replay(mockBin1, mockBin2, mockBin3, mockBin4, mockBin5, mockBin6, mockBin7, mockWatchdog);


        /* round 1 */

        // use some binary streams
        context.registerBinaryValueInstance(mockBin1);
        context.registerBinaryValueInstance(mockBin2);
        context.registerBinaryValueInstance(mockBin3);
        assertEquals(3, countBinaryValueInstances(context));
        assertEquals(1, countCleanupTasks(context));

        // cleanup those streams
        context.runCleanupTasks();
        assertEquals(0, countBinaryValueInstances(context));

        //reset the context (for reuse(), just as XQueryPool#returnCompiledXQuery(org.exist.source.Source, CompiledXQuery) would do)
        context.reset();
        assertEquals(0, countCleanupTasks(context));


        /* round 2, let's reuse the context... */

        // use some more binary streams
        context.registerBinaryValueInstance(mockBin4);
        context.registerBinaryValueInstance(mockBin5);
        assertEquals(2, countBinaryValueInstances(context));
        assertEquals(1, countCleanupTasks(context));

        // cleanup those streams
        context.runCleanupTasks();
        assertEquals(0, countBinaryValueInstances(context));

        //reset the context (for reuse(), just as XQueryPool#returnCompiledXQuery(org.exist.source.Source, CompiledXQuery) would do)
        context.reset();
        assertEquals(0, countCleanupTasks(context));


        /* round 3, let's reuse the context a second time... */

        // again, use some more binary streams
        context.registerBinaryValueInstance(mockBin6);
        context.registerBinaryValueInstance(mockBin7);
        assertEquals(2, countBinaryValueInstances(context));
        assertEquals(1, countCleanupTasks(context));

        // cleanup those streams
        context.runCleanupTasks();
        assertEquals(0, countBinaryValueInstances(context));

        //reset the context (for reuse(), just as XQueryPool#returnCompiledXQuery(org.exist.source.Source, CompiledXQuery) would do)
        context.reset();
        assertEquals(0, countCleanupTasks(context));


        // verify the expectations of our mocks
        verify(mockBin1, mockBin2, mockBin3, mockBin4, mockBin5, mockBin6, mockBin7, mockWatchdog);
    }

    private int countBinaryValueInstances(final XQueryContext context) throws NoSuchFieldException, IllegalAccessException {
        final Field fldBinaryValueInstances = context.getClass().getDeclaredField("binaryValueInstances");
        fldBinaryValueInstances.setAccessible(true);
        final Deque<BinaryValue> binaryValueInstances = (Deque<BinaryValue>) fldBinaryValueInstances.get(context);
        return binaryValueInstances.size();
    }

    private int countCleanupTasks(final XQueryContext context) throws NoSuchFieldException, IllegalAccessException {
        final Field fldCleanupTasks = context.getClass().getDeclaredField("cleanupTasks");
        fldCleanupTasks.setAccessible(true);
        final List<XQueryContext.CleanupTask> cleanupTasks = (List<XQueryContext.CleanupTask>) fldCleanupTasks.get(context);
        return cleanupTasks.size();
    }

    @Test
    public void testDeclareNamespace () throws XPathException {
        final XQueryContext context = new XQueryContext();
        context.declareNamespace("first", "ns/a");
        context.declareNamespace("second", "ns/b");
        // declare third namespace bound to a URI already used
        context.declareNamespace("third", "ns/a");
        final Set<String> expected = new HashSet<>(INITIAL_NAMESPACES);
        expected.addAll(Arrays.asList("first", "second", "third"));
        assertEquals(expected,  context.staticNamespaces.keySet());
    }
    @Test
    public void testReDeclareNamespaceAllowed () throws XPathException {
        final XQueryContext context = new XQueryContext();
        final String nsAllowedToBeRebound = "xs";
        assertEquals("http://www.w3.org/2001/XMLSchema",
                context.staticNamespaces.get(nsAllowedToBeRebound));

        context.declareNamespace(nsAllowedToBeRebound, "schemaless");

        final Set<String> expected = new HashSet<>(INITIAL_NAMESPACES);
        assertEquals(expected,  context.staticNamespaces.keySet());
        assertEquals("schemaless",  context.staticNamespaces.get(nsAllowedToBeRebound));
    }
    @Test
    public void testReDeclareNamespaceNullNull () throws XPathException {
        final XQueryContext context = new XQueryContext();
        context.declareNamespace(null, null);
        final Set<String> expected = new HashSet<>(INITIAL_NAMESPACES);
        assertEquals(expected,  context.staticNamespaces.keySet());
    }

    @Test
    public void testDeclareNamespaceEmptyPrefix () throws XPathException {
        final XQueryContext context = new XQueryContext();
        context.declareNamespace("", "default");
        final Set<String> expected = new HashSet<>(INITIAL_NAMESPACES);
        expected.add("");
        assertEquals(expected,  context.staticNamespaces.keySet());
        assertEquals("default",  context.staticNamespaces.get(""));
    }

    @Test
    public void testDeclareNamespaceNullPrefix () throws XPathException {
        final XQueryContext context = new XQueryContext();
        context.declareNamespace(null, "default");
        final Set<String> expected = new HashSet<>(INITIAL_NAMESPACES);
        expected.add("");
        assertEquals(expected,  context.staticNamespaces.keySet());
        assertEquals("default",  context.staticNamespaces.get(""));
    }

    @Test
    public void testReDeclareNamespaceEmptyPrefixFail () throws XPathException {
        final XQueryContext context = new XQueryContext();
        context.declareNamespace("", "default");
        // context.declareNamespace("", "");

        try {
            context.declareNamespace("", "new-default");
            fail("empty prefix was rebound");
        } catch (XPathException e) {
            assertEquals("err:XQST0033 Cannot bind prefix '' to 'new-default' it is already bound to 'default'",
                    e.getMessage());
            assertEquals("default",  context.staticNamespaces.get(""));
        }
    }

    @Test
    public void testReDeclareNamespaceEmptyPrefixSuccess () throws XPathException {
        final XQueryContext context = new XQueryContext();
        context.declareNamespace("mutable", "ns/initial");
        context.declareNamespace("mutable", "");
        context.declareNamespace("mutable", null);
        context.declareNamespace("mutable", "ns/new");
        assertEquals("ns/new",  context.staticNamespaces.get("mutable"));
    }

    @Test
    public void testReDeclareNamespaceForbidden () {
        try {
            final XQueryContext context = new XQueryContext();
            context.declareNamespace("xml", "html");
            fail("XML prefix was rebound");
        } catch (XPathException e) {
            assertEquals("err:XQST0070 Namespace predefined prefix 'xml' can not be bound", e.getMessage());
        }
    }

    @Test
    public void testReDeclareNamespaceForbiddenEmpty () {
        try {
            final XQueryContext context = new XQueryContext();
            context.declareNamespace("xml", "");
            fail("XML prefix was rebound");
        } catch (XPathException e) {
            assertEquals("err:XQST0070 Namespace predefined prefix 'xml' can not be bound", e.getMessage());
        }
    }

    @Test
    public void testReDeclareNamespaceForbiddenNull () {
        try {
            final XQueryContext context = new XQueryContext();
            context.declareNamespace("xml", null);
            fail("XML prefix was rebound");
        } catch (XPathException e) {
            assertEquals("err:XQST0070 Namespace predefined prefix 'xml' can not be bound", e.getMessage());
        }
    }

    @Test
    public void testXmlNsProtected () {
        try {
            final XQueryContext context = new XQueryContext();
            context.declareNamespace("test", XMLConstants.XML_NS_URI);
            fail("XML namespace was rebound");
        } catch (XPathException e) {
            assertEquals(
                    "err:XQST0070 Namespace URI 'http://www.w3.org/XML/1998/namespace' must be bound to the 'xml' prefix",
                    e.getMessage());
        }
    }
}