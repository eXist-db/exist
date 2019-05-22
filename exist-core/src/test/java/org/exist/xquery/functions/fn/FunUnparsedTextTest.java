/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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

package org.exist.xquery.functions.fn;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(ParallelRunner.class)
public class FunUnparsedTextTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Test
    public void unparsedText_dynamicallyAvailableDocument_absoluteUri() throws XPathException, EXistException, PermissionDeniedException {
        final BrokerPool pool = BrokerPool.getInstance();

        final String text = "hello, the time is: " + System.currentTimeMillis();
        final String textUri = "http://from-dynamic-context/doc1";
        final String query = "fn:unparsed-text('" + textUri + "')";

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);
            context.addDynamicallyAvailableTextResource(textUri, UTF_8, (broker2, transaction, uri, charset) -> new InputStreamReader(new ByteArrayInputStream(text.getBytes(UTF_8)), charset));

            final XQuery xqueryService = pool.getXQueryService();
            final CompiledXQuery compiled = xqueryService.compile(broker, context, query);
            final Sequence result = xqueryService.execute(broker, compiled, null);

            assertFalse(result.isEmpty());
            assertEquals(1, result.getItemCount());
            assertEquals(Type.STRING, result.itemAt(0).getType());
            assertEquals(text, result.itemAt(0).getStringValue());
        }
    }

    @Test
    public void unparsedText_dynamicallyAvailableDocument_relativeUri() throws XPathException, EXistException, PermissionDeniedException, URISyntaxException {
        final BrokerPool pool = BrokerPool.getInstance();

        final String text = "hello, the time is: " + System.currentTimeMillis();
        final String baseUri = "http://from-dynamic-context/";
        final String textRelativeUri = "doc1";
        final String query = "fn:unparsed-text('" + textRelativeUri + "')";

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);
            context.setBaseURI(new AnyURIValue(new URI(baseUri)));
            context.addDynamicallyAvailableTextResource(baseUri + textRelativeUri, UTF_8, (broker2, transaction, uri, charset) -> new InputStreamReader(new ByteArrayInputStream(text.getBytes(UTF_8)), charset));

            final XQuery xqueryService = pool.getXQueryService();
            final CompiledXQuery compiled = xqueryService.compile(broker, context, query);
            final Sequence result = xqueryService.execute(broker, compiled, null);

            assertFalse(result.isEmpty());
            assertEquals(1, result.getItemCount());
            assertEquals(Type.STRING, result.itemAt(0).getType());
            assertEquals(text, result.itemAt(0).getStringValue());
        }
    }

    @Test
    public void unparsedTextAvailable_dynamicallyAvailableDocument_absoluteUri() throws XPathException, EXistException, PermissionDeniedException {
        final BrokerPool pool = BrokerPool.getInstance();

        final String text = "hello, the time is: " + System.currentTimeMillis();
        final String textUri = "http://from-dynamic-context/doc1";
        final String query = "fn:unparsed-text-available('" + textUri + "')";

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);
            context.addDynamicallyAvailableTextResource(textUri, UTF_8, (broker2, transaction, uri, charset) -> new InputStreamReader(new ByteArrayInputStream(text.getBytes(UTF_8)), charset));

            final XQuery xqueryService = pool.getXQueryService();
            final CompiledXQuery compiled = xqueryService.compile(broker, context, query);
            final Sequence result = xqueryService.execute(broker, compiled, null);

            assertFalse(result.isEmpty());
            assertEquals(1, result.getItemCount());
            assertTrue(result.itemAt(0).toJavaObject(Boolean.class).booleanValue());
        }
    }

    @Test
    public void unparsedTextAvailable_dynamicallyAvailableDocument_relativeUri() throws XPathException, EXistException, PermissionDeniedException, URISyntaxException {
        final BrokerPool pool = BrokerPool.getInstance();

        final String text = "hello, the time is: " + System.currentTimeMillis();
        final String baseUri = "http://from-dynamic-context/";
        final String textRelativeUri = "doc1";
        final String query = "fn:unparsed-text-available('" + textRelativeUri + "')";

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);
            context.setBaseURI(new AnyURIValue(new URI(baseUri)));
            context.addDynamicallyAvailableTextResource(baseUri + textRelativeUri, UTF_8, (broker2, transaction, uri, charset) -> new InputStreamReader(new ByteArrayInputStream(text.getBytes(UTF_8)), charset));

            final XQuery xqueryService = pool.getXQueryService();
            final CompiledXQuery compiled = xqueryService.compile(broker, context, query);
            final Sequence result = xqueryService.execute(broker, compiled, null);

            assertFalse(result.isEmpty());
            assertEquals(1, result.getItemCount());
            assertTrue(result.itemAt(0).toJavaObject(Boolean.class).booleanValue());
        }
    }
}
