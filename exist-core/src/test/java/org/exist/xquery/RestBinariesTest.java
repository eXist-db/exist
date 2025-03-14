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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.exist.http.jaxb.Query;
import org.exist.http.jaxb.Result;
import org.exist.test.ExistWebServer;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.xmldb.XmldbURI;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;


import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.IOException;
import java.io.InputStream;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.exist.TestUtils.ADMIN_DB_PWD;
import static org.exist.TestUtils.ADMIN_DB_USER;
import static org.junit.Assert.assertArrayEquals;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class RestBinariesTest extends AbstractBinariesTest<Result, Result.Value, Exception> {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private static Executor executor = null;

    @BeforeClass
    public static void setupExecutor() {
         executor = Executor.newInstance()
                .auth(new HttpHost("localhost", existWebServer.getPort()), ADMIN_DB_USER, ADMIN_DB_PWD)
                .authPreemptive(new HttpHost("localhost", existWebServer.getPort()));
    }

    /**
     * {@see https://github.com/eXist-db/exist/issues/790#error-case-2}
     *
     * response:stream is used to return Base64 encoded binary.
     */
    @Test
    public void streamBinarySax() throws JAXBException, IOException {
        final String query = "import module namespace util = \"http://exist-db.org/xquery/util\";\n" +
                "import module namespace response = \"http://exist-db.org/xquery/response\";\n" +
                "let $bin := util:binary-doc('" + TEST_COLLECTION.append(BIN1_FILENAME).toString() + "')\n" +
                "return response:stream($bin, 'media-type=application/octet-stream')";

        final HttpResponse response = postXquery(query);

        final HttpEntity entity = response.getEntity();
        try(final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream()) {
            entity.writeTo(baos);

            assertArrayEquals(BIN1_CONTENT, Base64.decodeBase64(baos.toByteArray()));
        }
    }

    /**
     * {@see https://github.com/eXist-db/exist/issues/790#error-case-2}
     *
     * response:stream-binary is used to return raw binary.
     */
    @Test
    public void streamBinaryRaw() throws JAXBException, IOException {
        final String query = "import module namespace util = \"http://exist-db.org/xquery/util\";\n" +
                "import module namespace response = \"http://exist-db.org/xquery/response\";\n" +
                "let $bin := util:binary-doc('" + TEST_COLLECTION.append(BIN1_FILENAME).toString() + "')\n" +
                "return response:stream-binary($bin, 'media-type=application/octet-stream', ())";

        final HttpResponse response = postXquery(query);

        final HttpEntity entity = response.getEntity();
        try(final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream()) {
            entity.writeTo(baos);

            assertArrayEquals(BIN1_CONTENT, baos.toByteArray());
        }
    }

    @Override
    protected void storeBinaryFile(final XmldbURI filePath, final byte[] content) throws Exception {
        final HttpResponse response = executor.execute(Request.Put(getRestUrl() + filePath.toString())
                .setHeader("Content-Type", "application/octet-stream")
                .bodyByteArray(content)
        ).returnResponse();

        if(response.getStatusLine().getStatusCode() != SC_CREATED) {
            throw new Exception("Unable to store binary file: " + filePath);
        }
    }

    private String getRestUrl() {
        return "http://localhost:" + existWebServer.getPort() + "/rest";
    }

    @Override
    protected void removeCollection(final XmldbURI collectionUri) throws Exception {
        final HttpResponse response = executor.execute(Request.Delete(getRestUrl() + collectionUri.toString()))
                .returnResponse();

        if(response.getStatusLine().getStatusCode() != SC_OK) {
            throw new Exception("Unable to delete collection: " + collectionUri);
        }
    }

    @Override
    protected QueryResultAccessor<Result, Exception> executeXQuery(final String xquery) throws Exception {
        final HttpResponse response = postXquery(xquery);
        final HttpEntity entity = response.getEntity();
        try(final InputStream is = entity.getContent()) {
            final JAXBContext jaxbContext = JAXBContext.newInstance("org.exist.http.jaxb");
            final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            final Result result = (Result)unmarshaller.unmarshal(is);

            return consumer -> consumer.accept(result);
        }
    }

    private HttpResponse postXquery(final String xquery) throws JAXBException, IOException {
        final Query query = new Query();
        query.setText(xquery);

        final JAXBContext jaxbContext = JAXBContext.newInstance("org.exist.http.jaxb");
        final Marshaller marshaller = jaxbContext.createMarshaller();

        final HttpResponse response;
        try(final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream()) {
            marshaller.marshal(query, baos);
            response = executor.execute(Request.Post(getRestUrl() + "/db/")
                    .bodyByteArray(baos.toByteArray(), ContentType.APPLICATION_XML)
            ).returnResponse();
        }

        if(response.getStatusLine().getStatusCode() != SC_OK) {
            throw new IOException("Unable to query, HTTP response code: " + response.getStatusLine().getStatusCode());
        }

        return response;
    }

    @Override
    protected long size(final Result result) throws Exception {
        return result.getCount();
    }

    @Override
    protected Result.Value item(final Result results, final int index) throws Exception {
        return results.getValue().get(index);
    }

    @Override
    protected boolean isBinaryType(final Result.Value item) throws Exception {
        final String type = item.getType();
        return "xs:base64Binary".equals(type) || "xs:hexBinary".equals(type);
    }

    @Override
    protected boolean isBooleanType(Result.Value item) throws Exception {
        return "xs:boolean".equals(item.getType());
    }

    @Override
    protected byte[] getBytes(final Result.Value item) throws Exception {
        return switch (item.getType()) {
            case "xs:base64Binary" -> Base64.decodeBase64(item.getContent().getFirst().toString());
            case "xs:hexBinary" -> Hex.decodeHex(item.getContent().getFirst().toString());
            default -> throw new UnsupportedOperationException();
        };
    }

    @Override
    protected boolean getBoolean(final Result.Value item) throws Exception {
        return Boolean.parseBoolean(item.getContent().getFirst().toString());
    }
}
