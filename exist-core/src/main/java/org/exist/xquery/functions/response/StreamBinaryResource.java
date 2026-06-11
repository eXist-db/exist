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

import org.exist.dom.QName;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.storage.txn.TransactionException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;

import javax.annotation.Nonnull;

/**
 * {@code response:stream-binary-resource($binary-resource-path, $content-type, $filename?)} streams a
 * stored binary resource straight from the database to the servlet response output stream.
 *
 * <p>Unlike {@code response:stream-binary}, which takes a fully-materialized {@code xs:base64Binary}
 * (so the caller must first load the whole resource into the JVM heap, e.g. via {@code util:binary-doc}),
 * this function copies from the stored {@link BinaryDocument} directly to the response — the same
 * zero-copy path the REST server uses — without ever materializing the bytes. It is intended for large
 * downloads, where materializing the whole resource would exhaust the heap.</p>
 */
public class StreamBinaryResource extends StrictResponseFunction {

    private static final FunctionParameterSequenceType RESOURCE_PATH_PARAM = new FunctionParameterSequenceType("binary-resource-path", Type.STRING, Cardinality.EXACTLY_ONE, "The path to the stored binary resource, e.g. /db/path/to/image.png");
    private static final FunctionParameterSequenceType CONTENT_TYPE_PARAM = new FunctionParameterSequenceType("content-type", Type.STRING, Cardinality.EXACTLY_ONE, "The ContentType HTTP header value");
    private static final FunctionParameterSequenceType FILENAME_PARAM = new FunctionParameterSequenceType("filename", Type.STRING, Cardinality.ZERO_OR_ONE, "The filename. If provided, a Content-Disposition header is set for the filename in the HTTP Response");

    private static final String DESCRIPTION =
            "Streams a stored binary resource directly to the current servlet response output stream, "
            + "copying from the database to the response without first materializing the whole resource "
            + "in memory (unlike response:stream-binary, which takes a fully-loaded xs:base64Binary). "
            + "Suited to large downloads. The ContentType HTTP header is set to the value given in "
            + "$content-type. Note: the servlet output stream is closed afterwards.";

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("stream-binary-resource", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
                    DESCRIPTION,
                    new SequenceType[]{RESOURCE_PATH_PARAM, CONTENT_TYPE_PARAM},
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
            ),
            new FunctionSignature(
                    new QName("stream-binary-resource", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
                    DESCRIPTION + " A Content-Disposition header is set from $filename.",
                    new SequenceType[]{RESOURCE_PATH_PARAM, CONTENT_TYPE_PARAM, FILENAME_PARAM},
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
            )
    };

    public StreamBinaryResource(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nonnull final ResponseWrapper response) throws XPathException {
        if (args[0].isEmpty() || args[1].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String path = args[0].getStringValue();
        final String contentType = args[1].getStringValue();
        final String filename = (args.length > 2 && !args[2].isEmpty()) ? args[2].getStringValue() : null;

        if (!"org.exist.http.servlets.HttpResponseWrapper".equals(response.getClass().getName())) {
            throw new XPathException(this, ErrorCodes.XPDY0002, getSignature().toString() + " can only be used within the EXistServlet or XQueryServlet");
        }

        final XmldbURI uri;
        try {
            uri = XmldbURI.xmldbUriFor(path);
        } catch (final URISyntaxException e) {
            throw new XPathException(this, ErrorCodes.FODC0002, "Invalid binary resource path: " + path, e);
        }

        streamResource(uri, path, contentType, filename, response);
        return Sequence.EMPTY_SEQUENCE;
    }

    private void streamResource(final XmldbURI uri, final String path, final String contentType,
            final String filename, final ResponseWrapper response) throws XPathException {
        final DBBroker broker = context.getBroker();
        try (final LockedDocument lockedDoc = broker.getXMLResource(uri, LockMode.READ_LOCK)) {
            if (lockedDoc == null) {
                throw new XPathException(this, ErrorCodes.FODC0002, "Binary resource not found: " + path);
            }

            final DocumentImpl doc = lockedDoc.getDocument();
            if (doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "Resource is not a binary document: " + path);
            }

            response.setHeader("Content-Type", contentType);
            if (filename != null) {
                response.setHeader("Content-Disposition", "inline; filename=\"" + filename + "\"");
            }

            // zero-copy: stream the stored resource straight to the response, never
            // materializing it (the same path RESTServer uses for binary downloads).
            try (final Txn transaction = broker.continueOrBeginTransaction()) {
                final OutputStream os = response.getOutputStream();
                broker.readBinaryResource(transaction, (BinaryDocument) doc, os);
                os.close();
                transaction.commit();
            }

            // commit the response
            response.flushBuffer();
        } catch (final PermissionDeniedException e) {
            throw new XPathException(this, ErrorCodes.FODC0002, "Permission denied to read binary resource '" + path + "': " + e.getMessage(), e);
        } catch (final TransactionException e) {
            throw new XPathException(this, ErrorCodes.EXXQDY0007, "Transaction error while streaming binary resource '" + path + "': " + e.getMessage(), e);
        } catch (final IOException e) {
            throw new XPathException(this, ErrorCodes.EXXQDY0007, "IO exception while streaming binary resource '" + path + "': " + e.getMessage(), e);
        }
    }
}
