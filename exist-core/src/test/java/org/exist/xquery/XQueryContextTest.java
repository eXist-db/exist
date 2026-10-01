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
import org.exist.xquery.value.Sequence;
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

    /**
     * A frame releases the values registered while it was innermost, and only those: values the
     * enclosing scope registered are below its boundary and must be left alone. This is what stops a
     * called function from closing its caller's value.
     *
     * @see <a href="https://github.com/eXist-db/exist/issues/6725">Passing a binary value to a user-defined function closes it for the caller</a>
     */
    @Test
    public void frameReleasesOnlyItsOwnValues() throws NoSuchFieldException, IllegalAccessException, IOException {
        final XQueryContext context = new XQueryContext();

        final BinaryValue callersValue = createMock(BinaryValue.class);
        final BinaryValue calleesValue = createMock(BinaryValue.class);

        // only the value the inner frame registered is closed
        calleesValue.close();
        expectLastCall().times(1);
        replay(callersValue, calleesValue);

        context.pushBinaryValueFrame();
        context.registerBinaryValueInstance(callersValue);

        context.pushBinaryValueFrame();
        context.registerBinaryValueInstance(calleesValue);
        context.popBinaryValueFrame(null);

        assertEquals(1, countBinaryValueInstances(context));

        verify(callersValue, calleesValue);
    }

    /**
     * A value reachable from the sequence a frame returns escapes it, and belongs to the enclosing
     * frame from then on - so it is released when <em>that</em> frame is left.
     */
    @Test
    public void frameHandsEscapingValueToEnclosingFrame() throws NoSuchFieldException, IllegalAccessException, IOException {
        final XQueryContext context = new XQueryContext();

        final BinaryValue escaping = createMock(BinaryValue.class);
        final Sequence result = createMock(Sequence.class);
        expect(result.containsReference(escaping)).andReturn(true).anyTimes();

        escaping.close();
        expectLastCall().times(1);
        replay(escaping, result);

        context.pushBinaryValueFrame();
        context.pushBinaryValueFrame();
        context.registerBinaryValueInstance(escaping);

        // the inner frame returns it: not released, still registered
        context.popBinaryValueFrame(result);
        assertEquals(1, countBinaryValueInstances(context));

        // the enclosing frame now owns it
        context.popBinaryValueFrame(null);
        assertEquals(0, countBinaryValueInstances(context));

        verify(escaping, result);
    }

    /**
     * A frame whose escape set is unknown promotes rather than releases: deferring release to the end
     * of the query is recoverable, closing a value the query still needs is not.
     */
    @Test
    public void framePromoteReleasesNothing() throws NoSuchFieldException, IllegalAccessException, IOException {
        final XQueryContext context = new XQueryContext();
        final XQueryWatchDog mockWatchdog = createMock(XQueryWatchDog.class);
        context.setWatchDog(mockWatchdog);

        final BinaryValue binaryValue = createMock(BinaryValue.class);

        // closed once, by the end-of-query cleanup rather than by the frame
        binaryValue.close();
        expectLastCall().times(1);
        replay(binaryValue, mockWatchdog);

        context.pushBinaryValueFrame();
        context.registerBinaryValueInstance(binaryValue);
        context.promoteBinaryValueFrame();

        assertEquals(1, countBinaryValueInstances(context));

        context.runCleanupTasks();
        assertEquals(0, countBinaryValueInstances(context));

        verify(binaryValue, mockWatchdog);
    }

    /**
     * Marks and pops are not always balanced - WindowExpr opens frames it does not always close - so
     * the registry must tolerate both directions: a frame left open at the end of the query is drained
     * by the cleanup, and a pop with no frame open releases nothing at all.
     */
    @Test
    public void unbalancedFramesDeferReleaseRatherThanOverReach() throws NoSuchFieldException, IllegalAccessException, IOException {
        final XQueryContext context = new XQueryContext();
        final XQueryWatchDog mockWatchdog = createMock(XQueryWatchDog.class);
        context.setWatchDog(mockWatchdog);

        final BinaryValue neverPopped = createMock(BinaryValue.class);
        final BinaryValue afterUnbalancedPop = createMock(BinaryValue.class);

        neverPopped.close();
        expectLastCall().times(1);
        afterUnbalancedPop.close();
        expectLastCall().times(1);
        replay(neverPopped, afterUnbalancedPop, mockWatchdog);

        // a frame that is opened and never closed strands nothing: the cleanup drains it
        context.pushBinaryValueFrame();
        context.registerBinaryValueInstance(neverPopped);
        context.runCleanupTasks();
        assertEquals(0, countBinaryValueInstances(context));

        // and a pop with no frame open releases nothing, rather than reaching into what is left
        context.popBinaryValueFrame(null);
        context.registerBinaryValueInstance(afterUnbalancedPop);
        context.popBinaryValueFrame(null);
        assertEquals(1, countBinaryValueInstances(context));

        context.runCleanupTasks();
        assertEquals(0, countBinaryValueInstances(context));

        verify(neverPopped, afterUnbalancedPop, mockWatchdog);
    }

    /**
     * A value registered before any frame was opened (a global, or a top-level expression) belongs to
     * no frame, and is released only by the end-of-query cleanup.
     */
    @Test
    public void valueRegisteredBeforeAnyFrameSurvivesPops() throws NoSuchFieldException, IllegalAccessException, IOException {
        final XQueryContext context = new XQueryContext();
        final XQueryWatchDog mockWatchdog = createMock(XQueryWatchDog.class);
        context.setWatchDog(mockWatchdog);

        final BinaryValue global = createMock(BinaryValue.class);

        global.close();
        expectLastCall().times(1);
        replay(global, mockWatchdog);

        context.registerBinaryValueInstance(global);

        context.pushBinaryValueFrame();
        context.popBinaryValueFrame(null);
        assertEquals(1, countBinaryValueInstances(context));

        context.runCleanupTasks();
        assertEquals(0, countBinaryValueInstances(context));

        verify(global, mockWatchdog);
    }

    /**
     * Deregistering a value shifts the values after it down, so the frames that start after it must
     * shift with them - otherwise a frame boundary drifts and a later pop releases the wrong values.
     */
    @Test
    public void deregisteringAValueKeepsFrameBoundariesAligned() throws NoSuchFieldException, IllegalAccessException, IOException {
        final XQueryContext context = new XQueryContext();

        final BinaryValue outerValue = createMock(BinaryValue.class);
        final BinaryValue innerValue = createMock(BinaryValue.class);

        // only the inner frame's value is closed; the outer one was deregistered by hand
        innerValue.close();
        expectLastCall().times(1);
        replay(outerValue, innerValue);

        context.pushBinaryValueFrame();
        context.registerBinaryValueInstance(outerValue);

        context.pushBinaryValueFrame();
        context.registerBinaryValueInstance(innerValue);

        // remove the value that sits *before* the inner frame's boundary
        context.destroyBinaryValue(outerValue);
        assertEquals(1, countBinaryValueInstances(context));

        context.popBinaryValueFrame(null);
        assertEquals(0, countBinaryValueInstances(context));

        verify(outerValue, innerValue);
    }

    private int countBinaryValueInstances(final XQueryContext context) throws NoSuchFieldException, IllegalAccessException {
        final Field fldBinaryValueInstances = context.getClass().getDeclaredField("binaryValueInstances");
        fldBinaryValueInstances.setAccessible(true);
        final Collection<BinaryValue> binaryValueInstances = (Collection<BinaryValue>) fldBinaryValueInstances.get(context);
        return binaryValueInstances.size();
    }

    private int countCleanupTasks(final XQueryContext context) throws NoSuchFieldException, IllegalAccessException {
        final Field fldCleanupTasks = context.getClass().getDeclaredField("cleanupTasks");
        fldCleanupTasks.setAccessible(true);
        final List<XQueryContext.CleanupTask> cleanupTasks = (List<XQueryContext.CleanupTask>) fldCleanupTasks.get(context);
        return cleanupTasks.size();
    }

    @Test
    public void testDeclareNamespace() throws XPathException {
        final XQueryContext context = new XQueryContext();
        context.declareNamespace("first", "ns/a");
        context.declareNamespace("second", "ns/b");
        // declare third namespace bound to a URI already used
        context.declareNamespace("third", "ns/a");
        final Set<String> expected = new HashSet<>(INITIAL_NAMESPACES);
        expected.addAll(Arrays.asList("first", "second", "third"));
        assertEquals(expected, context.staticNamespaces.keySet());
    }

    @Test
    public void testReDeclareNamespaceAllowed() throws XPathException {
        final XQueryContext context = new XQueryContext();
        final String nsAllowedToBeRebound = "xs";
        assertEquals("http://www.w3.org/2001/XMLSchema",
                context.staticNamespaces.get(nsAllowedToBeRebound));

        context.declareNamespace(nsAllowedToBeRebound, "schemaless");

        final Set<String> expected = new HashSet<>(INITIAL_NAMESPACES);
        assertEquals(expected, context.staticNamespaces.keySet());
        assertEquals("schemaless", context.staticNamespaces.get(nsAllowedToBeRebound));
    }

    @Test
    public void testReDeclareNamespaceNullNull() throws XPathException {
        final XQueryContext context = new XQueryContext();
        context.declareNamespace(null, null);
        final Set<String> expected = new HashSet<>(INITIAL_NAMESPACES);
        assertEquals(expected, context.staticNamespaces.keySet());
    }

    @Test
    public void testDeclareNamespaceEmptyPrefix() throws XPathException {
        final XQueryContext context = new XQueryContext();
        context.declareNamespace("", "default");
        final Set<String> expected = new HashSet<>(INITIAL_NAMESPACES);
        expected.add("");
        assertEquals(expected, context.staticNamespaces.keySet());
        assertEquals("default", context.staticNamespaces.get(""));
    }

    @Test
    public void testDeclareNamespaceNullPrefix() throws XPathException {
        final XQueryContext context = new XQueryContext();
        context.declareNamespace(null, "default");
        final Set<String> expected = new HashSet<>(INITIAL_NAMESPACES);
        expected.add("");
        assertEquals(expected, context.staticNamespaces.keySet());
        assertEquals("default", context.staticNamespaces.get(""));
    }

    @Test
    public void testReDeclareNamespaceEmptyPrefixFail() throws XPathException {
        final XQueryContext context = new XQueryContext();
        context.declareNamespace("", "default");
        // context.declareNamespace("", "");

        try {
            context.declareNamespace("", "new-default");
            fail("empty prefix was rebound");
        } catch (XPathException e) {
            assertEquals("err:XQST0066 Cannot bind prefix '' to 'new-default' it is already bound to 'default'",
                    e.getMessage());
            assertEquals("default", context.staticNamespaces.get(""));
        }
    }

    @Test
    public void testReDeclareNamespaceEmptyPrefixSuccess() throws XPathException {
        final XQueryContext context = new XQueryContext();
        context.declareNamespace("mutable", "ns/initial");
        context.declareNamespace("mutable", "");
        context.declareNamespace("mutable", null);
        context.declareNamespace("mutable", "ns/new");
        assertEquals("ns/new", context.staticNamespaces.get("mutable"));
    }

    @Test
    public void testReDeclareNamespaceForbidden() {
        try {
            final XQueryContext context = new XQueryContext();
            context.declareNamespace("xml", "html");
            fail("XML prefix was rebound");
        } catch (XPathException e) {
            assertEquals("err:XQST0070 Namespace predefined prefix 'xml' can not be bound", e.getMessage());
        }
    }

    @Test
    public void testReDeclareNamespaceForbiddenEmpty() {
        try {
            final XQueryContext context = new XQueryContext();
            context.declareNamespace("xml", "");
            fail("XML prefix was rebound");
        } catch (XPathException e) {
            assertEquals("err:XQST0070 Namespace predefined prefix 'xml' can not be bound", e.getMessage());
        }
    }

    @Test
    public void testReDeclareNamespaceForbiddenNull() {
        try {
            final XQueryContext context = new XQueryContext();
            context.declareNamespace("xml", null);
            fail("XML prefix was rebound");
        } catch (XPathException e) {
            assertEquals("err:XQST0070 Namespace predefined prefix 'xml' can not be bound", e.getMessage());
        }
    }

    @Test
    public void testXmlNsProtected() {
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

    @Test
    public void testSetDefaultFunctionNamespaceRejectsXmlNs() {
        try {
            final XQueryContext context = new XQueryContext();
            context.setDefaultFunctionNamespace(XMLConstants.XML_NS_URI);
            fail("XML namespace was accepted as default function namespace");
        } catch (XPathException e) {
            assertEquals(ErrorCodes.XQST0070, e.getErrorCode());
        }
    }

    @Test
    public void testSetDefaultFunctionNamespaceRejectsXmlnsNs() {
        try {
            final XQueryContext context = new XQueryContext();
            context.setDefaultFunctionNamespace(XMLConstants.XMLNS_ATTRIBUTE_NS_URI);
            fail("XMLNS namespace was accepted as default function namespace");
        } catch (XPathException e) {
            assertEquals(ErrorCodes.XQST0070, e.getErrorCode());
        }
    }

    @Test
    public void testSetDefaultElementNamespaceRejectsXmlNs() {
        try {
            final XQueryContext context = new XQueryContext();
            context.setDefaultElementNamespace(XMLConstants.XML_NS_URI, null);
            fail("XML namespace was accepted as default element namespace");
        } catch (XPathException e) {
            assertEquals(ErrorCodes.XQST0070, e.getErrorCode());
        }
    }

    @Test
    public void testSetDefaultElementNamespaceRejectsXmlnsNs() {
        try {
            final XQueryContext context = new XQueryContext();
            context.setDefaultElementNamespace(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, null);
            fail("XMLNS namespace was accepted as default element namespace");
        } catch (XPathException e) {
            assertEquals(ErrorCodes.XQST0070, e.getErrorCode());
        }
    }

    @Test
    public void testRelativizeOrFallbackRealCollectionLoadPathRelativizes() {
        assertEquals("../bar",
                XQueryContext.relativizeOrFallback("xmldb:exist:///db/apps/foo", "/db/apps/bar"));
    }

    @Test
    public void testRelativizeOrFallbackSyntheticLoadPathFallsBackToSourceCollection() {
        // Reproduces the eXide unsaved-buffer crash: when a client sends a synthetic load
        // path like "xmldb:exist://__new__1" for an in-memory query, Path.relativize threw
        // IllegalArgumentException, surfacing as an XPath compile error and blocking module
        // imports from unsaved eXide buffers. The fallback restores import resolution.
        assertEquals("/db/apps/foo",
                XQueryContext.relativizeOrFallback("xmldb:exist://__new__1", "/db/apps/foo"));
    }

    @Test
    public void testRelativizeOrFallbackEmptyLoadPathFallsBackToSourceCollection() {
        assertEquals("/db/apps/foo",
                XQueryContext.relativizeOrFallback("", "/db/apps/foo"));
    }
}