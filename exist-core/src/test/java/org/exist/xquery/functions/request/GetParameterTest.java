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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.exist.http.RESTTest;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.UserManagementService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

import javax.annotation.Nullable;

/**
 * Tests expected behaviour of request:get-parameter() XQuery function
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @version 1.0
 */
public class GetParameterTest extends RESTTest {

    private final static String XQUERY = "for $param-name in request:get-parameter-names() return for $param-value in request:get-parameter($param-name, ()) return fn:concat($param-name, '=', $param-value)";
    private final static String XQUERY_FILENAME = "test-get-parameter.xql";

    private final static String TEST_FILE_CONTENT = "hello world";
    private final static String TEST_FILE_NAME = "helloworld.txt";

    private static Collection root;

    
    @BeforeClass
    public static void beforeClass() throws XMLDBException {
        root = DatabaseManager.getCollection("xmldb:exist://localhost:" + existWebServer.getPort() + "/xmlrpc/db", "admin", "");
        BinaryResource res = root.createResource(XQUERY_FILENAME, BinaryResource.class);
        ((EXistResource) res).setMimeType("application/xquery");
        res.setContent(XQUERY);
        root.storeResource(res);
        UserManagementService ums = root.getService(UserManagementService.class);
        ums.chmod(res, 0777);
    }

    @AfterClass
    public static void afterClass() throws XMLDBException {
        BinaryResource res = (BinaryResource)root.getResource(XQUERY_FILENAME);
        root.removeResource(res);
    }

    @Test
    public void testGetNoParameter() throws IOException {
        testGet(null);
    }

    @Test
    public void testPostNoParameter() throws IOException {
        testPost(null);
    }

    @Test
    public void testGetEmptyParameter() throws IOException{
        testGet(new NameValues[] {
            new NameValues("param1", new String[]{})
        });
    }

    @Test
    public void testPostEmptyParameter() throws IOException{
        testPost(new NameValues[] {
            new NameValues("param1", new String[]{})
        });
    }

    @Test
    public void testGetSingleValueParameter() throws IOException {
        testGet(new NameValues[] {
            new NameValues("param1", new String[] {
                "value1"
            })
        });
    }

    @Test
    public void testPostSingleValueParameter() throws IOException {
        testPost(new NameValues[] {
            new NameValues("param1", new String[] {
                "value1"
            })
        });
    }

    @Test
    public void testGetMultiValueParameter() throws IOException {
        testGet(new NameValues[]{
            new NameValues("param1", new String[] {
                "value1",
                "value2",
                "value3",
                "value4"
            })
        });
    }

    @Test
    public void testPostMultiValueParameter() throws IOException {
        testPost(new NameValues[]{
            new NameValues("param1", new String[] {
                "value1",
                "value2",
                "value3",
                "value4"
            })
        });
    }

    @Test
    public void testPostMultiValueParameterWithQueryStringMultiValueParameter() throws IOException {
        testPost(
            new NameValues[]{
                new NameValues("param1", new String[] {
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                }),
            },
            new NameValues[]{
                new NameValues("param2", new String[] {
                    "valueA",
                    "valueB",
                    "valueC",
                    "valueD"
                }),
            }
        );
    }

   @Test
    public void testPostMultiValueParameterWithQueryStringMultiValueParameterMerge() throws IOException {
        testPost(
            new NameValues[]{
                new NameValues("param1", new String[] {
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                }),
            },
            new NameValues[]{
                new NameValues("param1", new String[] {
                    "valueA",
                    "valueB",
                    "valueC",
                    "valueD"
                }),
            }
        );
    }

    @Test
    public void testMultipartPostMultiValueParameterAndFile() throws IOException {
        testMultipartPost(
            new Param[]{
                new NameValues("param1", new String[] {
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                }),
                new TextFileUpload(TEST_FILE_NAME, TEST_FILE_CONTENT)
            }
        );
    }

    @Test
    public void testMultipartPostFileAndMultiValueParameter() throws IOException {
        testMultipartPost(
            new Param[]{
                new TextFileUpload(TEST_FILE_NAME, TEST_FILE_CONTENT),
                new NameValues("param1", new String[] {
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                })
            }
        );
    }

    @Test
    public void testMultipartPostMultiValueParameterAndFileAndMultiValueParameter() throws IOException {
        testMultipartPost(
            new Param[]{
                new NameValues("param1", new String[] {
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                }),
                new TextFileUpload(TEST_FILE_NAME, TEST_FILE_CONTENT),
                new NameValues("param2", new String[] {
                    "valueA",
                    "valueB",
                    "valueC",
                    "valueD"
                })
            }
        );
    }

    @Test
    public void testMultipartPostAndMultiValueParameterAndFileAndMultiValueParameterWithQueryStringMultiValueParameters() throws IOException {
        testMultipartPost(
            new NameValues[]{
                new NameValues("param1", new String[] {
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                })
            },
            new Param[]{
                new NameValues("param2", new String[] {
                    "valueA",
                    "valueB",
                    "valueC",
                    "valueD"
                }),
                new TextFileUpload(TEST_FILE_NAME, TEST_FILE_CONTENT),
                new NameValues("param3", new String[] {
                    "valueZ",
                    "valueY",
                    "valueX",
                    "valueW"
                })
            }
        );
    }

    @Test
    public void testMultipartPostAndMultiValueParameterAndFileAndMultiValueParameterWithQueryStringMultiValueParametersMerged() throws IOException {
        testMultipartPost(
            new NameValues[]{
                new NameValues("param1", new String[] {
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                })
            },
            new Param[]{
                new NameValues("param1", new String[] {
                    "value5",
                    "value6",
                    "value7",
                    "value8"
                }),
                new TextFileUpload(TEST_FILE_NAME, TEST_FILE_CONTENT),
                new NameValues("param2", new String[] {
                    "valueA",
                    "valueB",
                    "valueC",
                    "valueD"
                })
            }
        );
    }

    private void testGet(@Nullable final NameValues[] queryStringParams) throws IOException {
        final StringBuilder buf = new StringBuilder();

        if (queryStringParams != null) {
            boolean first = true;
            for (final NameValues queryStringParam : queryStringParams) {
                if (!first) {
                    buf.append('&');
                }
                buf.append(queryStringParam.toString());
                first = false;
            }
        }

        Request get = Request.Get(getCollectionRootUri() + "/" + XQUERY_FILENAME + (queryStringParams == null || queryStringParams.length == 0 ? "" : "?" + buf));

        testRequest(get, buf.toString().replaceAll("&", ""));
    }

    private void testPost(@Nullable final NameValues[] formParams) throws IOException {
        final StringBuilder buf = new StringBuilder();
        Request post = Request.Post(getCollectionRootUri() + "/" + XQUERY_FILENAME);

        if (formParams != null) {
            final List<NameValuePair> bodyPairs = new ArrayList<>();
            for (final NameValues formParam : formParams) {
                for (final String value : formParam.getData()) {
                    bodyPairs.add(new BasicNameValuePair(formParam.getName(), value));
                    buf.append(formParam.getName()).append('=').append(value);
                }
            }

            post = post.bodyForm(bodyPairs);
        }

        testRequest(post, buf.toString());
    }

    private void testPost(final NameValues[] queryStringParams, final NameValues[] formParams) throws IOException {
        StringBuilder queryStringBuf = new StringBuilder();
        StringBuilder formBuf = new StringBuilder();

        boolean first = true;
        for (final NameValues queryStringParam : queryStringParams) {
            if (!first) {
                queryStringBuf.append('&');
            }
            queryStringBuf.append(queryStringParam.toString());
            first = false;
        }

        Request post = Request.Post(getCollectionRootUri() + "/" + XQUERY_FILENAME + (queryStringParams.length == 0 ? "" : "?" + queryStringBuf));

        final List<NameValuePair> bodyPairs = new ArrayList<>();
        for (final NameValues formParam : formParams) {
            for (final String value : formParam.getData()) {
                bodyPairs.add(new BasicNameValuePair(formParam.getName(), value));
                formBuf.append(formParam.getName()).append('=').append(value);
            }
        }

        post = post.bodyForm(bodyPairs);

        testRequest(post, queryStringBuf.toString().replaceAll("&", "") + formBuf);
    }

    private void testMultipartPost(final Param[] multipartParams) throws IOException {
        MultipartEntityBuilder multipart = MultipartEntityBuilder.create();

        StringBuilder buf = new StringBuilder();

        for (final Param multipartParam : multipartParams) {
            if(multipartParam instanceof NameValues nameValues) {
                for(final String value : nameValues.getData()) {
                    multipart = multipart.addTextBody(nameValues.getName(), value);
                    buf.append(nameValues.getName()).append('=').append(value);
                }
            } else if(multipartParam instanceof TextFileUpload textFileUpload) {
                multipart = multipart.addBinaryBody("fileUpload", textFileUpload.getData().getBytes(UTF_8), ContentType.TEXT_PLAIN, textFileUpload.getName());
                buf.append("fileUpload=" + textFileUpload.getData());
            }
        }

        Request post = Request.Post(getCollectionRootUri() + "/" + XQUERY_FILENAME)
            .body(multipart.build());

        testRequest(post, buf.toString());
    }

    private void testMultipartPost(final NameValues[] queryStringParams, final Param[] multipartParams) throws IOException {
        final StringBuilder queryStringBuf = new StringBuilder();

        boolean first = true;
        for (final NameValues queryStringParam : queryStringParams) {
            if (!first) {
                queryStringBuf.append('&');
            }
            queryStringBuf.append(queryStringParam.toString());
            first = false;
        }

        MultipartEntityBuilder multipart = MultipartEntityBuilder.create();

        final StringBuilder bodyBuf = new StringBuilder();

        for (final Param multipartParam : multipartParams) {
            if(multipartParam instanceof NameValues nameValues) {
                for(final String value : nameValues.getData()) {
                    multipart = multipart.addTextBody(nameValues.getName(), value);
                    bodyBuf.append(nameValues.getName()).append('=').append(value);
                }
            } else if(multipartParam instanceof TextFileUpload textFileUpload) {
                multipart = multipart.addBinaryBody("fileUpload", textFileUpload.getData().getBytes(UTF_8), ContentType.TEXT_PLAIN, textFileUpload.getName());
                bodyBuf.append("fileUpload=" + textFileUpload.getData());
            }
        }

        Request post = Request.Post(getCollectionRootUri() + "/" + XQUERY_FILENAME + (queryStringParams.length == 0 ? "" : "?" + queryStringBuf))
                .body(multipart.build());

        testRequest(post, queryStringBuf.toString().replaceAll("&", "") + bodyBuf);
    }

    private void testRequest(final Request request, final String expected) throws IOException {
        final HttpResponse response = request.execute().returnResponse();
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

        try (final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream()) {
            response.getEntity().writeTo(os);
            assertEquals(expected, new String(os.toByteArray(), UTF_8));
        }
    }

    public class NameValues implements Param<String[]> {

        final String name;
        final String values[];

        public NameValues(final String name, final String values[]) {
            this.name = name;
            this.values = values;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String[] getData() {
            return values;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            boolean first = true;
            for (final String value : values) {
                if (!first) {
                    builder.append('&');
                }
                builder.append(name).append('=').append(value);
                first = false;
            }
            return builder.toString();
        }
    }

    public class TextFileUpload implements Param<String> {
        final String name;
        final String content;

        public TextFileUpload(final String name, final String content) {
            this.name = name;
            this.content = content;
        }

        @Override
        public String getData() {
            return content;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public interface Param<T> {
        String getName();
        T getData();
    }
}
