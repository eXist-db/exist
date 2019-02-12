/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xmldb;

import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.blob.BlobId;
import org.exist.util.EXistInputSource;
import org.exist.util.FileUtils;
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.MessageDigest;
import org.exist.util.io.FastByteArrayInputStream;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.xquery.value.BinaryValue;
import org.w3c.dom.DocumentType;
import org.xml.sax.InputSource;
import org.xml.sax.ext.LexicalHandler;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

import javax.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import com.evolvedbinary.j8fu.function.SupplierE;

public class LocalBinaryResource extends AbstractEXistResource implements ExtendedResource, EXistBinaryResource, EXistResource {

    protected InputSource inputSource = null;
    protected Path file = null;
    protected byte[] rawData = null;
    private BinaryValue binaryValue = null;
    private boolean isExternal = false;

    public LocalBinaryResource(final Subject user, final BrokerPool brokerPool, final LocalCollection collection, final XmldbURI docId) {
        super(user, brokerPool, collection, docId, null);
    }

    @Override
    public String getResourceType() throws XMLDBException {
        return BinaryResource.RESOURCE_TYPE;
    }

    @Override
    public BlobId getBlobId() throws XMLDBException {
        return read((document, broker, transaction) -> ((BinaryDocument)document).getBlobId());
    }

    @Override
    public MessageDigest getContentDigest(final DigestType digestType) throws XMLDBException {
        return read((document, broker, transaction) ->
                broker.getBinaryResourceContentDigest(transaction, (BinaryDocument)document, digestType));
    }

    @Override
    public Object getExtendedContent() throws XMLDBException {
        return getExtendedContent(() -> read((document, broker, transaction) -> broker.getBinaryResource(((BinaryDocument) document))));
    }

    /**
     * Similar to {@link org.exist.xmldb.ExtendedResource#getExtendedContent()}
     * but useful for operations within the XML:DB Local API
     * that are already working within a transaction
     */
    Object getExtendedContent(final DBBroker broker, final Txn transaction) throws XMLDBException {
        return getExtendedContent(() -> read(broker, transaction).apply((document, broker1, transaction1) -> broker1.getBinaryResource(((BinaryDocument) document))));
    }

    private Object getExtendedContent(final SupplierE<Object, XMLDBException> binaryResourceRead) throws XMLDBException {
        if (file != null) {
            return file;
        }
        if (inputSource != null) {
            return inputSource;
        }
        if (rawData != null) {
            return rawData;
        }
        if(binaryValue != null) {
            return binaryValue;
        }

        return read((document, broker, transaction) -> broker.getBinaryResource(transaction, ((BinaryDocument) document)));
    }

    @Override
    public Object getContent() throws XMLDBException {
        final Object res = getExtendedContent();
        return getContent(res);
    }

    /**
     * Similar to {@link org.exist.xmldb.LocalBinaryResource#getContent()}
     * but useful for operations within the XML:DB Local API
     * that are already working within a transaction
     */
    Object getContent(final DBBroker broker, final Txn transaction) throws XMLDBException {
        final Object res = getExtendedContent(broker, transaction);
        return getContent(res);
    }

    private Object getContent(final Object res) throws XMLDBException {
        if(res != null) {
            if(res instanceof Path) {
                return readFile((Path)res);
            } else if(res instanceof java.io.File) {
                return readFile(((java.io.File)res).toPath());
            } else if(res instanceof InputSource) {
                return readFile((InputSource) res);
            } else if(res instanceof byte[]) {
                return res;
            } else if(res instanceof BinaryValue) {
                try(final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {
                    ((BinaryValue) res).streamBinaryTo(baos);
                    return baos.toByteArray();
                } catch (final IOException e) {
                    throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
                }
            } else if(res instanceof InputStream) {
                try(final InputStream is = (InputStream)res) {
                    return readFile(is);
                } catch (final IOException e) {
                    throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
                }
            }
        }
        return res;
    }

    @Override
    public void setContent(final Object value) throws XMLDBException {
        if(value instanceof Path) {
            file = (Path)value;
        } else if(value instanceof java.io.File) {
            file = ((java.io.File)value).toPath();
        } else if(value instanceof InputSource) {
            inputSource = (InputSource)value;
        } else if(value instanceof byte[]) {
            rawData = (byte[])value;
        } else if(value instanceof String) {
            rawData = ((String) value).getBytes();
        } else if(value instanceof BinaryValue) {
            binaryValue = (BinaryValue)value;
        } else {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "don't know how to handle value of type " + value.getClass().getName());
        }

        isExternal = true;
    }

    @Override
    public InputStream getStreamContent() throws XMLDBException {
        return getStreamContent(() -> read((document, broker, transaction) -> broker.getBinaryResource(((BinaryDocument) document))));
    }

    /**
     * Similar to {@link org.exist.xmldb.LocalBinaryResource#getStreamContent()}
     * but useful for operations within the XML:DB Local API
     * that are already working within a transaction
     */
    InputStream getStreamContent(final DBBroker broker, final Txn transaction) throws XMLDBException {
        return getStreamContent(() -> this.<InputStream>read(broker, transaction).apply((document, broker1, transaction1) -> broker.getBinaryResource(transaction, ((BinaryDocument) document))));
    }

    private InputStream getStreamContent(final SupplierE<InputStream, XMLDBException> streamContentRead) throws XMLDBException {
        final InputStream is;
        if(file != null) {
            try {
                is = Files.newInputStream(file);
            } catch(final IOException e) {
                // Cannot fire it :-(
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            }
        } else if(inputSource != null) {
            is = inputSource.getByteStream();
        } else if(rawData != null) {
            is = new FastByteArrayInputStream(rawData);
        } else if(binaryValue != null) {
            is = binaryValue.getInputStream();
        } else {
            is = streamContentRead.get();
        }

        return is;
    }

    @Override
    public void getContentIntoAFile(final Path tmpFile) throws XMLDBException {
        try(final OutputStream bos = Files.newOutputStream(tmpFile)) {
            getContentIntoAStream(bos);
        } catch(final IOException ioe) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "error while loading binary resource " + getId(), ioe);
        }
    }

    @Override
    public void getContentIntoAStream(final OutputStream os) throws XMLDBException {
        read((document, broker, transaction) -> {
            if(os instanceof FileOutputStream) {
                try(final OutputStream bos = new BufferedOutputStream(os, 65536)) {
                    broker.readBinaryResource(transaction, (BinaryDocument) document, bos);
                }
            } else {
                broker.readBinaryResource(transaction, (BinaryDocument) document, os);
            }
            return null;
        });
    }

    @Override
    public long getStreamLength() throws XMLDBException {
        final long retval;

        if(file != null) {
            retval = FileUtils.sizeQuietly(file);
        } else if(inputSource != null && inputSource instanceof EXistInputSource) {
            retval = ((EXistInputSource)inputSource).getByteStreamLength();
        } else if(rawData != null) {
            retval = rawData.length;
        } else if(binaryValue != null) {
            try(final CountingOutputStream os = new CountingOutputStream(new NullOutputStream())) {
                binaryValue.streamBinaryTo(os);
                retval = os.getByteCount();
            } catch(final IOException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "error while obtaining length of binary value " + getId(), e);
            }
        } else {
            retval = getContentLength();
        }
		
        return retval;
    }
	
    private byte[] readFile(final Path file) throws XMLDBException {
        try {
            return Files.readAllBytes(file);
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "file " + file.toAbsolutePath() + " could not be found", e);
        }
    }

    private byte[] readFile(final InputSource inSrc) throws XMLDBException {
        try(final InputStream is = inSrc.getByteStream()) {
            return readFile(is);
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Could not read InputSource", e);
        }
    }

    private byte[] readFile(final InputStream is) throws XMLDBException {
        try(final FastByteArrayOutputStream bos = new FastByteArrayOutputStream()) {
            bos.write(is);
            return bos.toByteArray();
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "IO exception while reading file " + file.toAbsolutePath(), e);
        }
    }

    @Override
    public DocumentType getDocType() throws XMLDBException {
        return null;
    }

    @Override
    public void setDocType(final DocumentType doctype) throws XMLDBException {
    }

    @Override
    public void setLexicalHandler(final LexicalHandler handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setProperties(final Properties properties) {
    }

    @Override
    @Nullable public Properties getProperties() {
        return null;
    }

    @Override
    protected void doClose() throws XMLDBException {
        if(!isExternal && file != null) {
            file = null;
        }

        if(binaryValue != null) {
            try {
                binaryValue.close();
            } catch(final IOException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "error while closing binary resource " + getId(), e);
            }
        }
    }
}
