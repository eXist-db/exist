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
package org.exist.xquery.functions.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.MessageDigest;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.util.UtilModule.functionSignature;

public class BinaryDoc extends BasicFunction {

    protected static final Logger logger = LogManager.getLogger(BinaryDoc.class);

    private static final FunctionParameterSequenceType FS_PARAM_BINARY_RESOURCE = optParam("binary-resource", Type.STRING, "The path to the binary resource");

    private static final String FS_BINARY_DOC_NAME = "binary-doc";
    static final FunctionSignature FS_BINARY_DOC = functionSignature(
            FS_BINARY_DOC_NAME,
            "Retrieves the binary resource and returns its contents as a " +
                    "value of type xs:base64Binary. An empty sequence is returned if the resource " +
                    "could not be found or $binary-resource was empty.",
            returnsOpt(Type.BASE64_BINARY, "the binary document"),
            FS_PARAM_BINARY_RESOURCE
    );

    private static final String FS_BINARY_DOC_AVAILABLE_NAME = "binary-doc-available";
    static final FunctionSignature FS_BINARY_DOC_AVAILABLE = functionSignature(
            FS_BINARY_DOC_AVAILABLE_NAME,
            "Checks if the binary resource identified by $binary-resource is available.",
            returns(Type.BOOLEAN, "true if the binary document is available"),
            FS_PARAM_BINARY_RESOURCE
    );

    private static final String FS_IS_BINARY_DOC_NAME = "is-binary-doc";
    static final FunctionSignature FS_IS_BINARY_DOC = functionSignature(
            FS_IS_BINARY_DOC_NAME,
            "Checks if the resource identified by $binary-resource is a binary resource.",
            returns(Type.BOOLEAN, "true if the resource is a binary document"),
            FS_PARAM_BINARY_RESOURCE
    );

    private static final String FS_BINARY_DOC_CONTENT_DIGEST_NAME = "binary-doc-content-digest";
    static final FunctionSignature FS_BINARY_DOC_CONTENT_DIGEST = functionSignature(
            FS_BINARY_DOC_CONTENT_DIGEST_NAME,
            "Gets the digest of the content of the resource identified by $binary-resource.",
            returnsOpt(Type.HEX_BINARY, "the digest of the content of the Binary Resource"),
            FS_PARAM_BINARY_RESOURCE,
            param("algorithm", Type.STRING, "The name of the algorithm to use for calculating the digest. Supports: " + supportedAlgorithms())
    );

    private static String supportedAlgorithms() {
        final StringBuilder builder = new StringBuilder();
        for (final DigestType digestType : DigestType.values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(digestType.getCommonNames()[0]);
        }

        return builder.toString();
    }

    public BinaryDoc(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence)
            throws XPathException {

        final Sequence emptyParamReturnValue = (isCalledAs(FS_BINARY_DOC_NAME) || isCalledAs(FS_BINARY_DOC_CONTENT_DIGEST_NAME)) ? Sequence.EMPTY_SEQUENCE : BooleanValue.FALSE;
        if (args[0].isEmpty()) {
            return emptyParamReturnValue;
        }

        final String path = args[0].getStringValue();
        try(final LockedDocument lockedDoc = context.getBroker().getXMLResource(XmldbURI.xmldbUriFor(path), LockMode.READ_LOCK)) {
            if(lockedDoc == null) {
                return emptyParamReturnValue;
            }

            final DocumentImpl doc = lockedDoc.getDocument();

            if(doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                return emptyParamReturnValue;
            } else if(isCalledAs(FS_BINARY_DOC_NAME)) {
                try (final Txn transaction = context.getBroker().continueOrBeginTransaction()) {
                    final BinaryDocument bin = (BinaryDocument) doc;
                    final InputStream is = context.getBroker().getBinaryResource(transaction, bin);
                    final Base64BinaryDocument b64doc = Base64BinaryDocument.getInstance(context, is, this);
                    b64doc.setUrl(path);

                    transaction.commit();

                    return b64doc;
                }
            } else if (isCalledAs(FS_BINARY_DOC_CONTENT_DIGEST_NAME)) {
                final String algorithm = args[1].getStringValue();
                final DigestType digestType;
                try {
                    digestType = DigestType.forCommonName(algorithm);
                } catch (final IllegalArgumentException e) {
                    throw new XPathException(this, "Invalid algorithm: " + algorithm, e);
                }

                try (final Txn transaction = context.getBroker().getBrokerPool().getTransactionManager().beginTransaction()) {
                    final BinaryDocument bin = (BinaryDocument) doc;
                    final MessageDigest messageDigest = context.getBroker().getBinaryResourceContentDigest(transaction, bin, digestType);

                    final InputStream is = new UnsynchronizedByteArrayInputStream(messageDigest.getValue());
                    final Sequence result = BinaryValueFromInputStream.getInstance(context, new HexBinaryValueType(), is, this);

                    transaction.commit();

                    return result;
                }
            } else {
                return BooleanValue.TRUE;
            }
        } catch (final URISyntaxException e) {
            logger.error("Invalid resource URI", e);
            throw new XPathException(this, "Invalid resource uri", e);
        } catch (final PermissionDeniedException e) {
            logger.error("{}: permission denied to read resource", path, e);
            throw new XPathException(this, path + ": permission denied to read resource");
        } catch (final IOException | TransactionException e) {
            logger.error("{}: I/O error while reading resource", path, e);
            throw new XPathException(this, path + ": I/O error while reading resource", e);
        }
    }
}
