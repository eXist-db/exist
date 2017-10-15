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

import org.exist.dom.persistent.BinaryDocument;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.util.EXistInputSource;
import org.exist.util.FileUtils;
import org.w3c.dom.DocumentType;
import org.xml.sax.InputSource;
import org.xml.sax.ext.LexicalHandler;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalBinaryResource extends AbstractEXistResource implements ExtendedResource, BinaryResource, EXistResource {

    protected InputSource inputSource = null;
    protected Path file = null;
    protected byte[] rawData = null;
    private boolean isExternal = false;

    public LocalBinaryResource(final Subject user, final BrokerPool brokerPool, final LocalCollection collection, final XmldbURI docId) {
        super(user, brokerPool, collection, docId, null);
    }

    @Override
    public String getResourceType() throws XMLDBException {
        return BinaryResource.RESOURCE_TYPE;
    }

    @Override
    public Object getExtendedContent() throws XMLDBException {
        if (file != null) {
            return file;
        }
        if (inputSource != null) {
            return inputSource;
        }

        return read((document, broker, transaction) -> broker.getBinaryResource(((BinaryDocument) document)));
    }

    @Override
    public Object getContent() throws XMLDBException {
        final Object res = getExtendedContent();
        if(res != null) {
            if(res instanceof Path) {
                return readFile((Path)res);
            } else if(res instanceof java.io.File) {
                return readFile(((java.io.File)res).toPath());
            } else if(res instanceof InputSource) {
                return readFile((InputSource)res);
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
            rawData = ((String)value).getBytes();
        } else {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "don't know how to handle value of type " + value.getClass().getName());
        }
        isExternal = true;
    }

    @Override
    public InputStream getStreamContent() throws XMLDBException {
        final InputStream retval;
        if(file != null) {
            try {
                retval = Files.newInputStream(file);
            } catch(final IOException fnfe) {
                // Cannot fire it :-(
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, fnfe.getMessage(), fnfe);
            }
        } else if(inputSource != null) {
            retval = inputSource.getByteStream();
        } else if(rawData != null) {
            retval = new ByteArrayInputStream(rawData);
        } else {
            retval = read((document, broker, transaction) -> broker.getBinaryResource(((BinaryDocument) document)));
        }

        return retval;
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
                    broker.readBinaryResource((BinaryDocument) document, bos);
                }
            } else {
                broker.readBinaryResource((BinaryDocument) document, os);
            }
            return null;
        });
    }
	
    @Override
    public void freeResources() {
        if(!isExternal && file != null) {
            file = null;
        }
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
        } else {
            retval = getContentLength();
        }
		
        return retval;
    }
	
    private byte[] readFile(final Path file) throws XMLDBException {
        try(final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            Files.copy(file, os);
            return os.toByteArray();
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
        try(final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            final byte[] buf = new byte[2048];
            int read = -1;
            while((read = is.read(buf)) > -1) {
                bos.write(buf, 0, read);
            }
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
}
