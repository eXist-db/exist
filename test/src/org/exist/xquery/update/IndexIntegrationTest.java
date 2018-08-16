/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
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
import static org.easymock.EasyMock.anyBoolean;


public class IndexIntegrationTest extends AbstractTestUpdate {

    private void run(final XmldbURI docUri, final String data, final BiConsumer<IndexWorker, StreamListener> setup, ConsumerE<XQueryService, XMLDBException> test) throws Exception {
        final XQueryService service = storeXMLStringAndGetQueryService(docUri.lastSegment().toString(), data);

        final IMocksControl control = createStrictControl();

        final IndexWorker worker = control.createMock(IndexWorker.class);
        final StreamListener stream = control.createMock(AbstractStreamListener.class);

        final AbstractIndex index = new TestIndex(worker);

        final BrokerPool pool = BrokerPool.getInstance();

        expect(worker.getIndexId()).andReturn("TestIndex").anyTimes();

        control.replay();

        pool.getIndexManager().registerIndex(index);

        // acquire the broker to reload the Index Manager config so registerIndex is noticed

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            control.verify();
            control.resetToStrict();

            //common
            expect(worker.getQueryRewriter(anyObject(XQueryContext.class))).andStubReturn(null);
            expect(worker.getIndexName()).andStubReturn("TestIndex");
            expect(worker.getListener()).andStubReturn(stream);

            stream.setNextInChain(anyObject()); expectLastCall().asStub();

            setup.accept(worker, stream);

            control.replay();

            test.accept(service);

            control.verify();
            control.resetToStrict();

            index.close();
            pool.getIndexManager().unregisterIndex(index);
        } finally {
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
            if (argument instanceof DocumentImpl) {
                final DocumentImpl doc = (DocumentImpl)argument;
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
            if (argument instanceof AttrImpl) {
                final AttrImpl attr = (AttrImpl)argument;
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
