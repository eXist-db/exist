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
package org.exist.xquery.update;

import org.easymock.IArgumentMatcher;
import org.easymock.IMocksControl;
import org.exist.dom.persistent.*;
import org.exist.indexing.*;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import com.evolvedbinary.j8fu.function.ConsumerE;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQueryContext;
import org.junit.Test;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import java.util.Optional;
import java.util.function.BiConsumer;

import static org.easymock.EasyMock.*;


public class IndexIntegrationTest extends AbstractTestUpdate {

    private void run(final XmldbURI docUri, final String data, final BiConsumer<IndexWorker, StreamListener> setup, ConsumerE<XQueryService, XMLDBException> test) throws Exception {
        final XQueryService service = storeXMLStringAndGetQueryService(docUri.lastSegment().toString(), data);

        final IMocksControl control = createStrictControl();

        final IndexWorker worker = control.createMock(IndexWorker.class);
        final StreamListener stream = control.createMock(AbstractStreamListener.class);

        final AbstractIndex index = new TestIndex(worker);

        final BrokerPool pool = BrokerPool.getInstance();

        // IndexController sorts workers via Comparator.comparingInt(IndexWorker::getChainPriority)
        // .thenComparing(IndexWorker::getIndexId), and a fresh IndexController is constructed every
        // time a broker is leased (NativeBroker.loadIndexModules). Stub both calls for the lifetime
        // of the test, so that BrokerPool.get(...) below — and any internal broker leases done by
        // queryResource / setUp / tearDown — do not bark with "Unexpected method call" and corrupt
        // the BrokerPool singleton.
        expect(worker.getIndexId()).andStubReturn("TestIndex");
        expect(worker.getChainPriority()).andStubReturn(Integer.MAX_VALUE);

        control.replay();

        pool.getIndexManager().registerIndex(index);

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            control.verify();
            control.resetToStrict();

            //common
            // Stub the void setNextInChain before any non-void expectations on the
            // shared strict control: EasyMock's expectLastCall() tracks the last
            // recorded call across all mocks of a control, and interleaving a
            // class-mock void call with interface-mock non-void expects can leave
            // expectLastCall() pointing at a non-void method ("last method called
            // on mock is not a void method") under some JVM/test orderings.
            stream.setNextInChain(anyObject()); expectLastCall().asStub();

            // Re-stub the worker identity / sorting calls after resetToStrict so subsequent
            // broker leases driven by the test body (and by JUnit's @After tearDown) keep
            // IndexController.<init> happy.
            expect(worker.getIndexId()).andStubReturn("TestIndex");
            expect(worker.getChainPriority()).andStubReturn(Integer.MAX_VALUE);
            expect(worker.getQueryRewriter(anyObject(XQueryContext.class))).andStubReturn(null);
            expect(worker.getIndexName()).andStubReturn("TestIndex");
            expect(worker.getListener()).andStubReturn(stream);

            setup.accept(worker, stream);

            control.replay();

            test.accept(service);

            control.verify();
            control.resetToStrict();

            index.close();
        } finally {
            // Always unregister the test index, even if the body above threw. Otherwise a stale
            // TestIndex with a strict mock worker stays in the IndexManager, and the next test's
            // @Before setUp() triggers IndexController.<init> against that stale mock — corrupting
            // BrokerPool and cascading NPEs into hundreds of unrelated tests.
            pool.getIndexManager().unregisterIndex(index);
            control.resetToStrict();
        }

    }

    @Test
    public void insertElement() throws Exception {

        final String docName = "pathNs2.xml";
        final XmldbURI docUri = XmldbURI.create("/db/test/"+docName);

        run(docUri, "<test/>",
            (worker, stream) -> {
                //set document
                worker.setDocument(eqDocument(docUri)); expectLastCall();

                //get top reindex node
                expect(worker.getReindexRoot(anyObject(), anyObject(), anyBoolean(), anyBoolean())).andStubReturn(null);

                //set mode
                worker.setMode(StreamListener.ReindexMode.STORE); expectLastCall();

                //get stream listener
                //setup chain

                //stream
                stream.startIndexDocument(anyObject()); expectLastCall();
                stream.startElement(anyObject(), anyObject(), anyObject()); expectLastCall();
                stream.attribute(anyObject(), anyObject(), anyObject()); expectLastCall();
                stream.endElement(anyObject(), anyObject(), anyObject()); expectLastCall();
                stream.endIndexDocument(anyObject()); expectLastCall();

                //flush
                worker.flush(); expectLastCall();
            },
            service -> queryResource(service, docName, "update insert <t xml:id=\"id1\"/> into /test", 0)
        );
    }

    @Test
    public void updateAttribute() throws Exception {

        final String docName = "pathNs2.xml";
        final XmldbURI docUri = XmldbURI.create("/db/test/"+docName);

        run(docUri, "<test><t xml:id=\"id1\"/></test>",
            (worker, stream) -> {
                //get top reindex node
                expect(worker.getReindexRoot(anyObject(), anyObject(), anyBoolean(), anyBoolean())).andStubReturn(null);

                //REMOVE STAGE
                //set document
                worker.setDocument(eqDocument(docUri)); expectLastCall();

                //set mode
                worker.setMode(StreamListener.ReindexMode.REMOVE_SOME_NODES); expectLastCall();

                //get stream listener
                worker.setDocument(eqDocument(docUri)); expectLastCall();
                worker.setMode(StreamListener.ReindexMode.REMOVE_SOME_NODES); expectLastCall();
                //setup chain

                //stream
                stream.startIndexDocument(anyObject()); expectLastCall();
                stream.attribute(anyObject(), eqAttr("xml:id", "id1"), anyObject()); expectLastCall();
                stream.endIndexDocument(anyObject()); expectLastCall();
                worker.flush(); expectLastCall();

                //STORE STAGE

                //get stream listener
                worker.setDocument(eqDocument(docUri)); expectLastCall();
                worker.setMode(StreamListener.ReindexMode.STORE); expectLastCall();
                //setup chain

                //stream
                stream.startIndexDocument(anyObject()); expectLastCall();
                stream.attribute(anyObject(), eqAttr("xml:id", "id2"), anyObject()); expectLastCall();
                stream.endIndexDocument(anyObject()); expectLastCall();
                worker.flush(); expectLastCall();

                //flush
                worker.flush(); expectLastCall();
            },
            service -> queryResource(service, docName, "update value //t/@xml:id with 'id2'", 0)
        );
    }

    @Test
    public void removeAttribute() throws Exception {

        final String docName = "pathNs2.xml";
        final XmldbURI docUri = XmldbURI.create("/db/test/"+docName);

        run(docUri, "<test><t xml:id=\"id2\"/></test>",
            (worker, stream) -> {
                //get top reindex node
                expect(worker.getReindexRoot(anyObject(), anyObject(), anyBoolean(), anyBoolean())).andStubReturn(null);

                //REMOVE STAGE
                //set document
                worker.setDocument(eqDocument(docUri)); expectLastCall();

                //set mode
                worker.setMode(StreamListener.ReindexMode.REMOVE_SOME_NODES); expectLastCall();

                //get stream listener
                worker.setDocument(eqDocument(docUri)); expectLastCall();
                worker.setMode(StreamListener.ReindexMode.REMOVE_SOME_NODES); expectLastCall();
                //setup chain

                //stream
                stream.startIndexDocument(anyObject()); expectLastCall();
                stream.attribute(anyObject(), eqAttr("xml:id", "id2"), anyObject()); expectLastCall();
                stream.endIndexDocument(anyObject()); expectLastCall();
                worker.flush(); expectLastCall();

                //flush
                worker.flush(); expectLastCall();
            },
            service -> queryResource(service, docName, "update delete //t/@xml:id", 0)
        );
    }

    private static DocumentImpl eqDocument(final XmldbURI url) {
        reportMatcher(new DocumentMatcher(url));
        return null;
    }

    private static class DocumentMatcher implements IArgumentMatcher {
        final XmldbURI url;

        DocumentMatcher(final XmldbURI url) {
            this.url = url;
        }

        @Override
        public boolean matches(final Object argument) {
            if (argument instanceof DocumentImpl doc) {
                return url.equals(doc.getURI());
            }
            return false;
        }

        @Override
        public void appendTo(final StringBuffer buffer) {
            buffer.append("eqDocument(").append(url.toString()).append(", ?)");
        }
    }

    private static AttrImpl eqAttr(final String name, final String value) {
        reportMatcher(new AttributeMatcher(name, value));
        return null;
    }

    private static class AttributeMatcher implements IArgumentMatcher {
        private final String name;
        private final String value;

        AttributeMatcher(final String name, final String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean matches(final Object argument) {
            if (argument instanceof AttrImpl attr) {
                return attr.getName().equals(name) && attr.getValue().equals(value);
            }
            return false;
        }

        @Override
        public void appendTo(final StringBuffer buffer) {
            buffer.append("eqAttr(@").append(name).append("(").append(value).append("), ?)");
        }
    }

    private static class TestIndex extends AbstractIndex {
        final IndexWorker worker;

        TestIndex(final IndexWorker worker) {
            this.worker = worker;
        }

        @Override
        public String getIndexId() {
            return "TestIndex";
        }

        @Override
        public String getIndexName() {
            return "TestIndex";
        }

        @Override
        public void open() {
        }

        @Override
        public void close() {
        }

        @Override
        public void sync() {
        }

        @Override
        public void remove() {
        }

        @Override
        public IndexWorker getWorker(final DBBroker broker) {
            return worker;
        }

        @Override
        public boolean checkIndex(final DBBroker broker) {
            return false;
        }
    }
}
