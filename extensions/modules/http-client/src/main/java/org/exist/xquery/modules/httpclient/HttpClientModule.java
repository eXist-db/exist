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

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

import static org.exist.xquery.FunctionDSL.functionDefs;

/**
 * Native EXPath HTTP Client Module for eXist-db.
 *
 * <p>Implements the EXPath HTTP Client 1.0 specification using Java's
 * built-in {@code java.net.http.HttpClient}, with zero external dependencies.</p>
 *
 * <p>Key improvement over the previous implementation: text responses
 * (JSON, plain text) are correctly returned as {@code xs:string} instead of
 * {@code xs:base64Binary}.</p>
 *
 * @see <a href="http://expath.org/spec/http-client">EXPath HTTP Client 1.0</a>
 */
public class HttpClientModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://expath.org/ns/http-client";
    public static final String PREFIX = "http";
    public static final String RELEASE = "0.9.0-SNAPSHOT";
    public static final String ERROR_NS = "http://expath.org/ns/error";

    // EXPath HTTP Client error codes

    /** An HTTP error occurred. */
    public static final ErrorCodes.ErrorCode HC001 = new ErrorCodes.ErrorCode(
            new QName("HC001", ERROR_NS, "err"), "An HTTP error occurred");

    /** Error parsing entity content as XML or HTML. */
    public static final ErrorCodes.ErrorCode HC002 = new ErrorCodes.ErrorCode(
            new QName("HC002", ERROR_NS, "err"), "Error parsing entity content as XML or HTML");

    /** Multipart override-media-type violation. */
    public static final ErrorCodes.ErrorCode HC003 = new ErrorCodes.ErrorCode(
            new QName("HC003", ERROR_NS, "err"), "Multipart override-media-type violation");

    /** src attribute conflicts with body content. */
    public static final ErrorCodes.ErrorCode HC004 = new ErrorCodes.ErrorCode(
            new QName("HC004", ERROR_NS, "err"), "src attribute conflicts with body content");

    /** Invalid request element structure. */
    public static final ErrorCodes.ErrorCode HC005 = new ErrorCodes.ErrorCode(
            new QName("HC005", ERROR_NS, "err"), "Invalid request element structure");

    /** Timeout waiting for response. */
    public static final ErrorCodes.ErrorCode HC006 = new ErrorCodes.ErrorCode(
            new QName("HC006", ERROR_NS, "err"), "Timeout waiting for response");

    /** The functions defined in this module. */
    public static final FunctionDef[] functions = functionDefs(
            functionDefs(SendRequestFunction.class,
                    SendRequestFunction.FS_SEND_REQUEST)
    );

    /**
     * Creates a new module instance.
     * @param parameters the module parameters
     */
    public HttpClientModule(final Map<String, List<?>> parameters) {
        super(functions, parameters);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "EXPath HTTP Client Module — sends HTTP requests and returns responses";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASE;
    }

    static QName qname(final String localPart) {
        return new QName(localPart, NAMESPACE_URI, PREFIX);
    }
}
