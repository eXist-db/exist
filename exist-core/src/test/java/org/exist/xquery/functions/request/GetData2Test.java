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
package org.exist.xquery.functions.request;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.http.servlets.RequestWrapper;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.util.XMLReaderPool;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * Unlike {@link GetDataTest} this test tries to test the code of
 * the {@link GetData} class directly.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class GetData2Test {

    @Test
    public void xmlChunkedTransferNonBlockingAvailable() throws XPathException, IOException, SAXException {
        final String content = "<hello>world</hello>";
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {

            final RequestWrapper mockRequestWrapper = createNiceMock(RequestWrapper.class);
            final Configuration mockConfiguration = createNiceMock(Configuration.class);
            final BrokerPool mockBrokerPool = createNiceMock(BrokerPool.class);
            final DBBroker mockBroker = createNiceMock(DBBroker.class);
            final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
            final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 20, 0);

            expect(mockRequestWrapper.getSession(false)).andReturn(null);
            expect(mockRequestWrapper.getContentLength()).andReturn(-1l);
            expect(mockRequestWrapper.getHeader("Transfer-Encoding")).andReturn("chunked");
            expect(mockRequestWrapper.getInputStream()).andReturn(is);
            expect(mockRequestWrapper.getContentType()).andReturn("application/xml");

            expect(mockConfiguration.getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY)).andReturn("org.exist.util.io.FileFilterInputStreamCache");
            expect(mockConfiguration.getProperty(XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL, Boolean.FALSE)).andReturn(Boolean.FALSE);

            expect(mockBrokerPool.getConfiguration()).andReturn(mockConfiguration);
            expect(mockBrokerPool.getActiveBroker()).andReturn(mockBroker).anyTimes();
            expect(mockBrokerPool.getParserPool()).andReturn(xmlReaderPool).anyTimes();
            expect(mockBroker.getBrokerPool()).andReturn(mockBrokerPool).anyTimes();

            replay(mockRequestWrapper, mockConfiguration, mockBrokerPool, mockBroker);

            xmlReaderObjectFactory.configure(mockConfiguration);

            final XQueryContext context = new XQueryContext(mockBrokerPool, mockConfiguration, null);
            context.setHttpContext(new XQueryContext.HttpContext(mockRequestWrapper, null));

            final GetData getData = new GetData(context);
            final Sequence result = getData.eval((Sequence[]) null, (Sequence) null);
            assertNotNull(result);
            assertFalse(result.isEmpty());
            assertEquals(1, result.getItemCount());
            assertTrue(result.itemAt(0) instanceof Document);
            assertEquals("hello", ((Document) result.itemAt(0)).getDocumentElement().getLocalName());
            assertEquals("world", ((Document) result.itemAt(0)).getDocumentElement().getTextContent());

            verify(mockRequestWrapper, mockConfiguration, mockBrokerPool, mockBroker);
        }
    }

    @Test
    public void xmlChunkedTransferNonBlockingNoneAvailable() throws XPathException, IOException {
        final String content = "<hello>world</hello>";
        try (final InputStream is = new ZeroAvailableInputStream(content.getBytes(StandardCharsets.UTF_8))) {

            final RequestWrapper mockRequestWrapper = createNiceMock(RequestWrapper.class);
            final Configuration mockConfiguration = createNiceMock(Configuration.class);
            final BrokerPool mockBrokerPool = createNiceMock(BrokerPool.class);
            final DBBroker mockBroker = createNiceMock(DBBroker.class);
            final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
            final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 20, 0);

            expect(mockRequestWrapper.getSession(false)).andReturn(null);
            expect(mockRequestWrapper.getContentLength()).andReturn(-1l);
            expect(mockRequestWrapper.getHeader("Transfer-Encoding")).andReturn("chunked");
            expect(mockRequestWrapper.getInputStream()).andReturn(is);
            expect(mockRequestWrapper.getContentType()).andReturn("application/xml");

            expect(mockConfiguration.getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY)).andReturn("org.exist.util.io.FileFilterInputStreamCache");
            expect(mockConfiguration.getProperty(XQueryContext.PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL, Boolean.FALSE)).andReturn(Boolean.FALSE);

            expect(mockBrokerPool.getConfiguration()).andReturn(mockConfiguration);
            expect(mockBrokerPool.getActiveBroker()).andReturn(mockBroker).anyTimes();
            expect(mockBrokerPool.getParserPool()).andReturn(xmlReaderPool).anyTimes();
            expect(mockBroker.getBrokerPool()).andReturn(mockBrokerPool).anyTimes();

            replay(mockRequestWrapper, mockConfiguration, mockBrokerPool, mockBroker);

            xmlReaderObjectFactory.configure(mockConfiguration);

            final XQueryContext context = new XQueryContext(mockBrokerPool, mockConfiguration, null);
            context.setHttpContext(new XQueryContext.HttpContext(mockRequestWrapper, null));

            final GetData getData = new GetData(context);
            final Sequence result = getData.eval((Sequence[]) null, (Sequence) null);
            assertNotNull(result);
            assertFalse(result.isEmpty());
            assertEquals(1, result.getItemCount());
            assertTrue(result.itemAt(0) instanceof Document);
            assertEquals("hello", ((Document) result.itemAt(0)).getDocumentElement().getLocalName());
            assertEquals("world", ((Document) result.itemAt(0)).getDocumentElement().getTextContent());

            verify(mockRequestWrapper, mockConfiguration, mockBrokerPool, mockBroker);
        }
    }

    public static class ZeroAvailableInputStream extends UnsynchronizedByteArrayInputStream {

        public ZeroAvailableInputStream(final byte[] data) {
            super(data);
        }

        @Override
        public int available() {
            return 0;
        }
    }
}
