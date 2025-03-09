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
package org.exist.exiftool.xquery;

import java.nio.file.Path;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.QName;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.storage.BrokerPool;
import org.exist.storage.blob.BlobStore;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import static com.evolvedbinary.j8fu.Try.TaggedTryUnchecked;

/**
 * @author <a href="mailto:dulip.withanage@gmail.com">Dulip Withanage</a>
 * @version 1.0
 */
public class MetadataFunctions extends BasicFunction {

    @SuppressWarnings("unused")
    private final static Logger logger = LogManager.getLogger(MetadataFunctions.class);

    public final static FunctionSignature getMetadata = new FunctionSignature(
            new QName("get-metadata", ExiftoolModule.NAMESPACE_URI, ExiftoolModule.PREFIX),
            "extracts the metadata",
            new SequenceType[]{
                new FunctionParameterSequenceType("binary", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The binary file from which to extract from")
            },
            new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.EXACTLY_ONE, "Extracted metadata")
    );

    /*
    public final static FunctionSignature writeMetadata = new FunctionSignature(
        new QName("write-metadata", ExiftoolModule.NAMESPACE_URI, ExiftoolModule.PREFIX),
        "write the metadata into a binary document",
        new SequenceType[]{
            new FunctionParameterSequenceType("doc",Type.DOCUMENT, Cardinality.EXACTLY_ONE, " XML file containing file"),
            new FunctionParameterSequenceType("binary", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The binary data into where metadata is written")
        },
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.EXACTLY_ONE, "Extracted metadata")
    );
    */
    
    public MetadataFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        String uri = args[0].itemAt(0).getStringValue();

        try {
            if (uri.toLowerCase().startsWith("http")) {
                //document from the web
                return extractMetadataFromWebResource(uri);


            } else {
                //document from the db
                XmldbURI docUri = XmldbURI.xmldbUriFor(uri);
                return extractMetadataFromLocalResource(docUri);
            }

        } catch (URISyntaxException use) {
            throw new XPathException(this, "Could not parse document URI: " + use.getMessage(), use);
        }

    }

    private Sequence extractMetadataFromLocalResource(final XmldbURI docUri) throws XPathException {
        try(final LockedDocument lockedDoc = context.getBroker().getXMLResource(docUri, LockMode.READ_LOCK)) {

            if (lockedDoc != null && lockedDoc.getDocument() instanceof BinaryDocument binDoc) {

                final BrokerPool pool = context.getBroker().getBrokerPool();
                final BlobStore blobStore = pool.getBlobStore();
                try (final Txn transaction = pool.getTransactionManager().beginTransaction()) {
                    final Sequence result =
                            blobStore
                                    .with(transaction, binDoc.getBlobId(), blobFile -> TaggedTryUnchecked(XPathException.class, () -> exifToolExtract(blobFile)))
                                    .get();
                    transaction.commit();
                    return result;
                }
            } else {
                throw new XPathException(this, "The binary document at " + docUri.toString() + " cannot be found.");
            }
        } catch (PermissionDeniedException | IOException | TransactionException e) {
            throw new XPathException(this, "Could not access binary document: " + e.getMessage(), e);
        }
    }

    private Sequence extractMetadataFromWebResource(String uri) throws XPathException {
        //parse the string uri into a URI object to make sure its valid
        URI u;
        try {
            u = new URI(uri);
            return exifToolWebExtract(u);
        } catch (URISyntaxException ex) {
            throw new XPathException(this, "URI syntax error" + ex.getMessage(), ex);
        }
       
    }

    private Sequence exifToolExtract(final Path binaryFile) throws XPathException {
        final ExiftoolModule module = (ExiftoolModule) getParentModule();
        try {
            final Process p = Runtime.getRuntime().exec(module.getPerlPath() + " " + module.getExiftoolPath() + " -X -struct " + binaryFile.toAbsolutePath());
            try(final InputStream stdIn = p.getInputStream();
                    final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream()) {

                //buffer stdin
                baos.write(stdIn);

                //make sure process is complete
                p.waitFor();

                return ModuleUtils.inputSourceToXML(context, new InputSource(baos.toInputStream()), this);
            }
        } catch (final IOException | InterruptedException ex) {
            throw new XPathException(this, "Could not execute the Exiftool " + ex.getMessage(), ex);
        } catch (final SAXException saxe) {
            throw new XPathException(this, "Could not parse output from the Exiftool " + saxe.getMessage(), saxe);
        }
    }

    private Sequence exifToolWebExtract(final URI uri) throws XPathException {
        final ExiftoolModule module = (ExiftoolModule) getParentModule();
        try {
            final Process p = Runtime.getRuntime().exec(module.getExiftoolPath()+" -fast -X -");

            try(final InputStream stdIn = p.getInputStream();
                    final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream()) {

                try(final OutputStream stdOut = p.getOutputStream()) {
                    final Source src = SourceFactory.getSource(context.getBroker(), null, uri.toString(), false);
                    if (src == null) {
                        throw new XPathException(this, "Could not read source for the Exiftool: " + uri);
                    }
                    try(final InputStream isSrc = src.getInputStream()) {

                        //write the remote data to stdOut
                        int read = -1;
                        byte buf[] = new byte[4096];
                        while ((read = isSrc.read(buf)) > -1) {
                            stdOut.write(buf, 0, read);
                        }
                    }
                }

                //read stdin to buffer
                baos.write(stdIn);

                //make sure process is complete
                p.waitFor();

                return ModuleUtils.inputSourceToXML(context, new InputSource(baos.toInputStream()), this);
            }

        } catch (final IOException | InterruptedException | PermissionDeniedException ex) {
            throw new XPathException(this, "Could not execute the Exiftool " + ex.getMessage(), ex);
        } catch (final SAXException saxe) {
            throw new XPathException(this, "Could not parse output from the Exiftool " + saxe.getMessage(), saxe);
        }
    }
}
