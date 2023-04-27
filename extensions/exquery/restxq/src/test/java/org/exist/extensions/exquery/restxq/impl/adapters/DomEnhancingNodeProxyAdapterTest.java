/*
 * Copyright © 2001, Adam Retter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl.adapters;

import com.evolvedbinary.j8fu.function.ConsumerE;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.*;
import org.exist.numbering.DLN;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class DomEnhancingNodeProxyAdapterTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final XmldbURI TEST_COLLECTION_URI = XmldbURI.DB.append("dom-enhancing-test");
    private static final XmldbURI TEST_DOC_URI = XmldbURI.create("test-doc.xml");

    private static final String TEST_DOC = """
            <test-doc>
                <element1 attr1="val1">text1</element1>
                <!--comment1-->
                <![CDATA[cdata1]]>
                <?test-pi pi1?>
            </test-doc>""";

    @BeforeClass
    public static void setup() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try (final Txn transaction = brokerPool.getTransactionManager().beginTransaction();
             final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Collection collection = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI)) {

            collection.storeDocument(transaction, broker, TEST_DOC_URI, new StringInputSource(TEST_DOC), MimeType.XML_TYPE);

            // async release of collection lock
            collection.close();

            transaction.commit();
        }
    }


    @Test
    public void asElement() throws PermissionDeniedException, EXistException {
        final NodeId elementId1 = new DLN("1");
        withTestDocument(doc ->
                assertProxiedCorrectly(doc, elementId1, Element.class, ElementImpl.class, elementProxy ->
                        assertEquals("test-doc", elementProxy.getNodeName())
                )
        );

        final NodeId elementId2 = new DLN("1.2");
        withTestDocument(doc ->
                assertProxiedCorrectly(doc, elementId2, Element.class, ElementImpl.class, elementProxy ->
                        assertEquals("element1", elementProxy.getNodeName())
                )
        );
    }

    @Test
    public void asAttr() throws PermissionDeniedException, EXistException {
        final NodeId attrId = new DLN("1.2.1");
        withTestDocument(doc ->
                assertProxiedCorrectly(doc, attrId, Attr.class, AttrImpl.class, attrProxy ->
                        assertEquals("attr1", attrProxy.getNodeName())
                )
        );
    }

    @Test
    public void asText() throws PermissionDeniedException, EXistException {
        final NodeId textId = new DLN("1.2.2");
        withTestDocument(doc ->
                assertProxiedCorrectly(doc, textId, Text.class, TextImpl.class, textProxy ->
                        assertEquals("text1", textProxy.getTextContent())
                )
        );
    }

    @Test
    public void asComment() throws PermissionDeniedException, EXistException {
        final NodeId commentId = new DLN("1.4");
        withTestDocument(doc ->
                assertProxiedCorrectly(doc, commentId, Comment.class, CommentImpl.class, commentProxy ->
                        assertEquals("comment1", commentProxy.getTextContent())
                )
        );
    }

    @Test
    public void asCdataSection() throws PermissionDeniedException, EXistException {
        final DLN cdataSectionId = new DLN("1.6");
        withTestDocument(doc ->
                assertProxiedCorrectly(doc, cdataSectionId, CDATASection.class, CDATASectionImpl.class, cdataSectionProxy ->
                        assertEquals("cdata1", cdataSectionProxy.getTextContent())
                )
        );
    }

    @Test
    public void asProcessingInstruction() throws PermissionDeniedException, EXistException {
        final DLN processingInstructionId = new DLN("1.8");
        withTestDocument(doc ->
                assertProxiedCorrectly(doc, processingInstructionId, ProcessingInstruction.class, ProcessingInstructionImpl.class, processingInstructionProxy ->
                        assertEquals("pi1", processingInstructionProxy.getData())
                )
        );
    }

    private static void withTestDocument(final ConsumerE<DocumentImpl, AssertionError> fnDocument) throws AssertionError, EXistException, PermissionDeniedException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try (final Txn transaction = brokerPool.getTransactionManager().beginTransaction();
             final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final LockedDocument lockedDocument = broker.getXMLResource(TEST_COLLECTION_URI.append(TEST_DOC_URI), Lock.LockMode.READ_LOCK)) {

            final DocumentImpl doc = lockedDocument.getDocument();

            fnDocument.accept(doc);

            transaction.commit();
        }
    }

    private static <DT extends Node> void assertProxiedCorrectly(final DocumentImpl doc, final NodeId nodeId, final Class<DT> domInterfaceType, final Class<? extends NodeImpl> existDomImplementationType, final ConsumerE<DT, AssertionError> proxiedDomTypeAssertions) throws AssertionError {
        final NodeProxy nodeProxy = new NodeProxy(doc, nodeId);
        nodeProxy.getNode();  // NOTE(AR) causes type of the node proxy to be set

        // check type of original
        assertTrue("Expected instanceof NodeProxy", nodeProxy instanceof NodeProxy);
        assertFalse("Expected not(instanceof " + domInterfaceType.getSimpleName() + ")", domInterfaceType.isInstance(nodeProxy));

        // the function under test
        final NodeProxy nodeProxyProxy = DomEnhancingNodeProxyAdapter.create(nodeProxy);

        // check type of proxy
        assertTrue("Expected instanceof NodeProxy", nodeProxyProxy instanceof NodeProxy);
        assertTrue("Expected instanceof " + domInterfaceType.getSimpleName() + "; W3C Node type was: " + nodeProxyProxy.getNodeType(), domInterfaceType.isInstance(nodeProxyProxy));

        // check W3C DOM Interface methods of proxy
        proxiedDomTypeAssertions.accept((DT) nodeProxyProxy);

        // check eXist-db NodeProxy methods of proxy
        assertEquals(nodeId, nodeProxyProxy.getNodeId());
        assertTrue("Expected instanceof " + existDomImplementationType.getSimpleName(), existDomImplementationType.isInstance(nodeProxyProxy.getNode()));
    }
}
