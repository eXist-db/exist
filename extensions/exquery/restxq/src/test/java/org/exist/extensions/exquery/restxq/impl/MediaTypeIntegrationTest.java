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
package org.exist.extensions.exquery.restxq.impl;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static java.nio.charset.StandardCharsets.UTF_8;

public class MediaTypeIntegrationTest extends AbstractClassIntegrationTest {

    private static String TEST_COLLECTION = "/db/restxq/media-type-integration-test";

    private static String XQUERY1 =
            """
            xquery version "3.0";
            
            module namespace mod1 = "http://mod1";
            
            declare namespace rest = "http://exquery.org/ns/restxq";
            declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
            
            declare %private variable $mod1:data := document { <person><firstName>Adam</firstName><lastName>Retter</lastName></person> } ;
            
            declare
                %rest:GET
                %rest:path("/media-type-json1")
                %output:media-type("application/json")
                %output:method("json")
            function mod1:media-type-json1() {
                $mod1:data
            };
            
            declare
                %rest:GET
                %rest:path("/media-type-json2")
                %output:media-type("application/json")
                %output:method("json")
            function mod1:media-type-json2() {
                $mod1:data/person
            };
            
            declare
                %rest:GET
                %rest:path("/media-type-xml1")
                %output:media-type("application/xml")
                %output:method("xml")
                %output:indent("no")
            function mod1:media-type-xml1() {
                $mod1:data
            };
            
            declare
                %rest:GET
                %rest:path("/media-type-xml2")
                %output:media-type("application/xml")
                %output:method("xml")
                %output:indent("no")
            function mod1:media-type-xml2() {
                $mod1:data/person
            };
            """;
    private static String XQUERY1_FILENAME = "restxq-tests1.xqm";

    @BeforeClass
    public static void storeResourceFunctions() throws IOException {
        enableRestXqTrigger(TEST_COLLECTION);
        storeXquery(TEST_COLLECTION, XQUERY1_FILENAME, XQUERY1);
        assertRestXqResourceFunctionsCount(4);
    }

    @Test
    public void mediaTypeJson1() throws IOException {
        assertMediaTypeResponse("/media-type-json1", ContentType.APPLICATION_JSON,
                "application/json; charset=utf-8",
                "{ \"firstName\" : \"Adam\", \"lastName\" : \"Retter\" }");
    }

    @Test
    public void mediaTypeJson2() throws IOException {
        assertMediaTypeResponse("/media-type-json2", ContentType.APPLICATION_JSON,
                "application/json; charset=utf-8",
                "{ \"firstName\" : \"Adam\", \"lastName\" : \"Retter\" }");
    }

    @Test
    public void mediaTypeXml1() throws IOException {
        assertMediaTypeResponse("/media-type-xml1", ContentType.APPLICATION_XML.withCharset(UTF_8),
                ContentType.APPLICATION_XML.withCharset(UTF_8).toString(),
                "<person><firstName>Adam</firstName><lastName>Retter</lastName></person>");
    }

    @Test
    public void mediaTypeXml2() throws IOException {
        assertMediaTypeResponse("/media-type-xml2", ContentType.APPLICATION_XML.withCharset(UTF_8),
                ContentType.APPLICATION_XML.withCharset(UTF_8).toString(),
                "<person><firstName>Adam</firstName><lastName>Retter</lastName></person>");
    }

    private void assertMediaTypeResponse(final String uriEndpoint, final ContentType acceptContentType, final String expectedResponseContentType, final String expectedResponseBody) throws IOException {
        final ClassicHttpResponse response = (ClassicHttpResponse) executor.execute(Request
                .get(getRestXqUri() + uriEndpoint)
                .addHeader(new BasicHeader("Accept", acceptContentType.toString()))
        ).returnResponse();

        assertEquals(HttpStatus.SC_OK, response.getCode());

        final HttpEntity responseEntity = response.getEntity();
        assertEquals(expectedResponseContentType, responseEntity.getContentType());

        final String responseBody;
        try (final InputStream is = responseEntity.getContent()) {
            responseBody = asString(is);
        }
        assertEquals(expectedResponseBody, responseBody);
    }
}
