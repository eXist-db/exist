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

import jakarta.servlet.http.Cookie;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.junit.Test;

import java.util.Optional;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

/**
 * Pins {@code GetCookieValue#decode}'s behavior on the shapes that distinguish it from a plain
 * {@code URLDecoder.decode}: a literal {@code '+'} in a cookie value this eXist instance did not
 * itself percent-encode (a browser cookie, another app on the domain, or one this instance wrote
 * before this fix), and a foreign {@code %XX} sequence that happens to be well-formed. See the
 * javadoc on {@code decode} for why the {@code '+'} case matters -- {@link java.net.URLDecoder}
 * alone would indistinguishably decode it as a space, corrupting values such as base64 session
 * tokens that use {@code '+'} in their alphabet.
 */
public class GetCookieValueDecodeTest {

    private static Sequence getCookieValue(final String cookieValue) throws XPathException {
        // Left unreplayed: constructing a BasicFunction calls XQueryContext#nextExpressionId(),
        // which a strict mock in replay mode would reject as an unexpected call.
        final XQueryContext mockContext = createMock(XQueryContext.class);
        final GetCookieValue getCookieValue = new GetCookieValue(mockContext);

        final RequestWrapper mockRequest = createMock(RequestWrapper.class);
        expect(mockRequest.getCookies()).andReturn(new Cookie[]{new Cookie("test-cookie", cookieValue)});
        replay(mockRequest);

        final Sequence[] args = {new StringValue("test-cookie")};
        return getCookieValue.eval(args, Optional.of(mockRequest));
    }

    @Test
    public void literalPlusInForeignCookieDecodesAsLiteral() throws XPathException {
        // Never percent-encoded by this instance -- e.g. a base64 payload set by a browser or
        // another app on the domain. Must not be corrupted into a space.
        final Sequence result = getCookieValue("aGVsbG8+d29ybGQ=");
        assertEquals("aGVsbG8+d29ybGQ=", result.getStringValue());
    }

    @Test
    public void spaceEncodedByThisInstanceDecodesCorrectly() throws XPathException {
        // What HttpResponseWrapper#encodeCookieValue actually emits for a space today.
        final Sequence result = getCookieValue("hello%20world");
        assertEquals("hello world", result.getStringValue());
    }

    @Test
    public void wellFormedForeignPercentEscapeDecodesOptimistically() throws XPathException {
        // A foreign cookie whose value coincidentally contains a well-formed %XX sequence that
        // was never actually percent-encoded. Inherent, documented ambiguity -- decoding a
        // namespace this instance doesn't own -- rather than a bug; pinned here so a future change
        // doesn't alter this corner case unnoticed.
        final Sequence result = getCookieValue("tok%C3%A9n");
        assertEquals("tokén", result.getStringValue());
    }

    @Test
    public void malformedPercentEscapeFallsBackToRawValue() throws XPathException {
        // A lone '%' not followed by two hex digits is not a valid escape -- URLDecoder throws,
        // and decode() falls back to the untouched raw value rather than propagating the error.
        final Sequence result = getCookieValue("50%");
        assertEquals("50%", result.getStringValue());
    }
}
