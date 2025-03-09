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
package org.exist.xquery.functions.response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Base64;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.exist.http.RESTTest;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.junit.Test;

/**
 * Tests expected behaviour of response:stream-binary() XQuery function
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @version 1.0
 */
public class StreamBinaryTest extends RESTTest {

	@Test
	public void testStreamBinary() throws IOException {
		
		final String testValue = "hello world";
		final String xquery = "response:stream-binary(xs:base64Binary('" +  Base64.encodeBase64String(testValue.getBytes())  + "'), 'application/octet-stream', 'test.bin')";

		final Request get = Request.Get(getCollectionRootUri() + "?_query=" + URLEncoder.encode(xquery, StandardCharsets.UTF_8) + "&_indent=no");

		final HttpResponse response = get.execute().returnResponse();
		assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

		try (final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream()) {
			response.getEntity().writeTo(os);

			assertArrayEquals(testValue.getBytes(), os.toByteArray());
		}
	}
}