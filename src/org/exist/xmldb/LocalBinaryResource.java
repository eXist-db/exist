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
import org.w3c.dom.DocumentType;
import org.xml.sax.InputSource;
import org.xml.sax.ext.LexicalHandler;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

import java.io.*;

public class LocalBinaryResource extends AbstractEXistResource implements ExtendedResource, BinaryResource, EXistResource {

    protected InputSource inputSource = null;
    protected File file = null;
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
            if(res instanceof File) {
                    return readFile((File)res);
            } else if(res instanceof InputSource) {
                    return readFile((InputSource)res);
            } else if(res instanceof InputStream) {
                    return readFile((InputStream)res);
            }
        }

        return res;
    }

    @Override
    public void setContent(final Object value) throws XMLDBException {
        if(value instanceof File) {
            file = (File)value;
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
                retval = new FileInputStream(file);
            } catch(final FileNotFoundException fnfe) {
                // Cannot fire it :-(
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, fnfe.getMessage(), fnfe);
            }
        } else if(inputSource!=null) {
            retval = inputSource.getByteStream();
        } else if(rawData!=null) {
            retval = new ByteArrayInputStream(rawData);
        } else {
            retval = read((document, broker, transaction) -> broker.getBinaryResource(((BinaryDocument) document)));
        }

        return retval;
    }

    @Override
    public void getContentIntoAFile(final File tmpFile) throws XMLDBException {
        try(final OutputStream bos = new BufferedOutputStream(new FileOutputStream(tmpFile))) {
            getContentIntoAStream(bos);
        } catch(final IOException ioe) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "error while loading binary resource " + getId(), ioe);
        }
    }

    @Override
    public void getContentIntoAStream(final OutputStream os) throws XMLDBException {
        read((document, broker, transaction) -> {
            if(os instanceof FileOutputStream) {
                try(final OutputStream bos = new BufferedOutputStream(os, 655360)) {
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
            retval = file.length();
        } else if(inputSource != null && inputSource instanceof EXistInputSource) {
            retval = ((EXistInputSource)inputSource).getByteStreamLength();
        } else if(rawData != null) {
            retval = rawData.length;
        } else {
            retval = getContentLength();
        }
		
        return retval;
    }
	
    private byte[] readFile(final File file) throws XMLDBException {
        try {
            return readFile(new FileInputStream(file));
        } catch (final FileNotFoundException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "file " + file.getAbsolutePath() + " could not be found", e);
        }
    }

    private byte[] readFile(final InputSource is) throws XMLDBException {
        return readFile(is.getByteStream());
    }

    private byte[] readFile(final InputStream is) throws XMLDBException {
        try(final ByteArrayOutputStream bos = new ByteArrayOutputStream(2048)) {
            final byte[] temp = new byte[1024];
            int count = 0;
            while((count = is.read(temp)) > -1) {
                    bos.write(temp, 0, count);
            }
            return bos.toByteArray();
        } catch (final FileNotFoundException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "file " + file.getAbsolutePath() + " could not be found", e);
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "IO exception while reading file " + file.getAbsolutePath(), e);
        } finally {
            try {
                is.close();
            } catch (final IOException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "IO exception while closing stream of file " + file.getAbsolutePath(), e);
            }
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
