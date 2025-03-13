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
package org.expath.exist;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

public class HttpClientTest {

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void readResponse() throws XPathException, PermissionDeniedException, EXistException {
        assumeTrue("No Internet access: skipping 'readResponse' test", hasInternetAccess());

        final String query =
                "xquery version \"3.1\";\n" +
                "import module namespace http=\"http://expath.org/ns/http-client\";\n" +
                "let $url := \"http://www.exist-db.org/exist/apps/homepage/resources/img/existdb.gif\"\n" +
                "let $request :=\n" +
                "    <http:request method=\"GET\" href=\"{$url}\"/>\n" +
                "let $response := http:send-request($request)\n" +
                "let $str := util:binary-to-string($response[2])\n" +
                "return\n" +
                "    $str";

        final Sequence result = executeQuery(query);
        assertEquals(1, result.getItemCount());
    }

    private Sequence executeQuery(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = brokerPool.getXQueryService();
        try (final DBBroker broker = brokerPool.getBroker()) {
            return xquery.execute(broker, query, null);
        }
    }

    private boolean hasInternetAccess() {
        boolean hasInternetAccess = false;

        //Checking that we have an Internet Access
        try {
            final URL url = new URL("http://www.exist-db.org");
            final URLConnection con = url.openConnection();
            if (con instanceof HttpURLConnection httpConnection) {
                hasInternetAccess = (httpConnection.getResponseCode() == HttpURLConnection.HTTP_OK);
            }
        } catch(final MalformedURLException e) {
            fail(e.getMessage());
        } catch (final IOException e) {
            //Ignore
        }

        return hasInternetAccess;
    }
}
