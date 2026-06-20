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
package org.exist.xquery.modules.httpclient;

import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.httpclient.config.RequestOptions;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.exist.xquery.FunctionDSL.*;

/**
 * Implements the EXPath HTTP Client {@code http:send-request} function.
 *
 * <p>Sends an HTTP request and returns a sequence where the first item is an
 * {@code <http:response>} element and subsequent items are response body content.</p>
 *
 * @see <a href="http://expath.org/spec/http-client">EXPath HTTP Client 1.0</a>
 */
public class SendRequestFunction extends BasicFunction {


    private static final String FS_SEND_REQUEST_NAME = "send-request";
    private static final String FS_SEND_REQUEST_DESCRIPTION =
            "Sends an HTTP request to a server and returns the response. " +
            "The response is a sequence where the first item is an http:response element " +
            "with status, message, and header information, followed by the response body content.";

    public static final FunctionSignature[] FS_SEND_REQUEST = functionSignatures(
            HttpClientModule.qname(FS_SEND_REQUEST_NAME),
            FS_SEND_REQUEST_DESCRIPTION,
            returnsMany(Type.ITEM,
                    "the response sequence: http:response element followed by body content"),
            arities(
                    arity(
                            optParam("request", Type.ELEMENT, "The http:request element describing the request.")
                    ),
                    arity(
                            optParam("request", Type.ELEMENT, "The http:request element describing the request."),
                            optParam("href", Type.STRING, "The target URI (overrides @href on request element).")
                    ),
                    arity(
                            optParam("request", Type.ELEMENT, "The http:request element describing the request."),
                            optParam("href", Type.STRING, "The target URI (overrides @href on request element)."),
                            optManyParam("bodies", Type.ITEM, "The request body content for methods like POST/PUT.")
                    )
            )
    );

    public SendRequestFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        // Extract arguments
        final NodeValue requestNode = args[0].isEmpty() ? null : (NodeValue) args[0].itemAt(0);
        final String hrefParam = getArgumentCount() >= 2 && !args[1].isEmpty()
                ? args[1].getStringValue() : null;
        final Sequence bodiesParam = getArgumentCount() >= 3 ? args[2] : Sequence.EMPTY_SEQUENCE;

        // Parse request element
        final RequestBuilder requestBuilder = new RequestBuilder();
        requestBuilder.parse(requestNode, hrefParam, bodiesParam);
        final HttpRequest httpRequest = requestBuilder.build();

        final RequestOptions allOptions = requestBuilder.getOptions();

        final HttpClient client = HttpClientFactory.get(allOptions.requestOptions());

        try {
            final HttpResponse<byte[]> response = send(client, requestBuilder, httpRequest);
            return ResponseHandler.buildResult(response, context, this,
                    allOptions.responseOptions().statusOnly(), allOptions.responseOptions().overrideMediaType());

        } catch (final java.net.http.HttpTimeoutException e) {
            throw new XPathException(this, HttpClientModule.HC006,
                    "Timeout: " + e.getMessage());
        } catch (final java.net.ConnectException e) {
            throw new XPathException(this, HttpClientModule.HC001,
                    "Connection error: " + e.getMessage());
        } catch (final IOException e) {
            throw new XPathException(this, HttpClientModule.HC001,
                    "HTTP error: " + e.getMessage());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new XPathException(this, HttpClientModule.HC001,
                    "Request interrupted: " + e.getMessage());
        }
    }

    /**
     * Sends the request, and — per the EXPath challenge-response model — if credentials were not
     * sent preemptively and the server answers 401 with a WWW-Authenticate challenge, re-sends once
     * with the computed Authorization header (Basic or Digest).
     */
    private static HttpResponse<byte[]> send(final HttpClient client, final RequestBuilder reqBuilder,
            final HttpRequest httpRequest) throws IOException, InterruptedException, XPathException {
        final HttpResponse<byte[]> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 401 && reqBuilder.shouldAttemptChallenge()) {
            final String challenge = response.headers().firstValue("WWW-Authenticate").orElse(null);
            final String authorization = reqBuilder.challengeResponse(challenge);
            if (authorization != null) {
                return client.send(reqBuilder.build(authorization), HttpResponse.BodyHandlers.ofByteArray());
            }
        }
        return response;
    }
}
