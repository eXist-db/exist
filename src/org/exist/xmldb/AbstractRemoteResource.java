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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.commons.io.output.ByteArrayOutputStream;

import org.exist.security.Permission;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.EXistInputSource;
import org.exist.util.VirtualTempFile;
import org.xml.sax.InputSource;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class AbstractRemoteResource extends AbstractRemote
        implements EXistResource, ExtendedResource, Resource {

    protected final XmldbURI path;
    private String mimeType;

    protected VirtualTempFile vfile = null;
    private VirtualTempFile contentVFile = null;
    protected InputSource inputSource = null;
    private boolean isLocal = false;
    private long contentLen = 0L;
    private Permission permissions = null;

    Date dateCreated = null;
    Date dateModified = null;

    protected AbstractRemoteResource(final RemoteCollection parent, final XmldbURI documentName, final String mimeType)
            throws XMLDBException {
        super(parent);
        if (documentName.numSegments() > 1) {
            this.path = documentName;
        } else {
            this.path = parent.getPathURI().append(documentName);
        }
        this.mimeType = mimeType;
    }

    protected Properties getProperties() {
        return collection.getProperties();
    }

    @Override
    public Object getContent()
            throws XMLDBException {
        final Object res = getExtendedContent();
        // Backward compatibility
        if (isLocal) {
            return res;
        } else if (res != null) {
            if (res instanceof File) {
                return readFile((File) res);
            } else if (res instanceof InputSource) {
                return readFile((InputSource) res);
            }
        }
        return res;
    }

    @Override
    protected void finalize()
            throws Throwable {
        freeResources();
        super.finalize();
    }

    @Override
    public void freeResources() {
        vfile = null;
        inputSource = null;
        if (contentVFile != null) {
            contentVFile.delete();
            contentVFile = null;
        }
        isLocal = true;
    }

    /**
     * @deprecated Here for backward compatibility, instead use {@see org.xmldb.api.base.Resource#getContent()}
     */
    @Deprecated
    protected byte[] getData()
            throws XMLDBException {
        final Object res = getExtendedContent();
        if (res != null) {
            if (res instanceof File) {
                return readFile((File) res);
            } else if (res instanceof InputSource) {
                return readFile((InputSource) res);
            } else if (res instanceof String) {
                return ((String) res).getBytes(UTF_8);
            }
        }

        return (byte[]) res;
    }

    @Override
    public long getContentLength()
            throws XMLDBException {
        return contentLen;
    }

    @Override
    public Date getCreationTime()
            throws XMLDBException {
        return dateCreated;
    }

    @Override
    public Date getLastModificationTime()
            throws XMLDBException {
        return dateModified;
    }

    @Override
    public void setLastModificationTime(final Date dateModified) throws XMLDBException {
        if (dateModified != null) {
            if(dateModified.before(getCreationTime())) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "Modification time must be after creation time.");
            }

            final List params = new ArrayList(2);
            params.add(path.toString());
            params.add(dateModified.getTime());

            try {
                collection.getClient().execute("setLastModified", params);
            } catch (final XmlRpcException e) {
                throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
            }

            this.dateModified = dateModified;
        }
    }

    public long getExtendedContentLength()
            throws XMLDBException {
        return contentLen;
    }

    @Override
    public String getMimeType() {
        return mimeType;
    }

    @Override
    public Collection getParentCollection()
            throws XMLDBException {
        return collection;
    }

    @Override
    public Permission getPermissions() {
        return permissions;
    }

    protected boolean setContentInternal(final Object value)
            throws XMLDBException {
        freeResources();
        boolean wasSet = false;
        if (value instanceof VirtualTempFile) {
            vfile = (VirtualTempFile) value;
            // Assuring the virtual file is close state
            try {
                vfile.close();
            } catch (final IOException ioe) {
                // IgnoreIT(R)
            }
            setExtendendContentLength(vfile.length());
            wasSet = true;
        } else if (value instanceof File) {
            vfile = new VirtualTempFile((File) value);
            setExtendendContentLength(vfile.length());
            wasSet = true;
        } else if (value instanceof InputSource) {
            inputSource = (InputSource) value;
            wasSet = true;
        } else if (value instanceof byte[]) {
            vfile = new VirtualTempFile((byte[]) value);
            setExtendendContentLength(vfile.length());
            wasSet = true;
        } else if (value instanceof String) {
            vfile = new VirtualTempFile(((String) value).getBytes(UTF_8));
            setExtendendContentLength(vfile.length());
            wasSet = true;
        }

        return wasSet;
    }

    protected void setExtendendContentLength(final long len) {
        this.contentLen = len;
    }

    public void setContentLength(final int len) {
        this.contentLen = len;
    }

    public void setContentLength(final long len) {
        this.contentLen = len;
    }

    @Override
    public void setMimeType(final String mimeType) {
        this.mimeType = mimeType;
    }

    public void setPermissions(final Permission perms) {
        permissions = perms;
    }

    @Override
    public void getContentIntoAFile(final File localfile)
            throws XMLDBException {
        try(final OutputStream os = new BufferedOutputStream(new FileOutputStream(localfile))) {
            getContentIntoAStream(os);
        } catch (final IOException ioe) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
        }
    }

    protected void getRemoteContentIntoLocalFile(final OutputStream os, final boolean isRetrieve, final int handle, final int pos) throws XMLDBException {
        final String command;
        final List<Object> params = new ArrayList<>();
        if (isRetrieve) {
            command = "retrieveFirstChunk";
            params.add(Integer.valueOf(handle));
            params.add(Integer.valueOf(pos));
        } else {
            command = "getDocumentData";
            params.add(path.toString());
        }
        params.add(getProperties());

        try {
            final VirtualTempFile vtmpfile = new VirtualTempFile();
            vtmpfile.setTempPrefix("eXistARR");
            vtmpfile.setTempPostfix("XMLResource".equals(getResourceType()) ? ".xml" : ".bin");

            Map table = (Map) collection.getClient().execute(command, params);

            final String method;
            final boolean useLongOffset;
            if (table.containsKey("supports-long-offset") && (Boolean)table.get("supports-long-offset")) {
                useLongOffset = true;
                method = "getNextExtendedChunk";
            } else {
                useLongOffset = false;
                method = "getNextChunk";
            }

            long offset = ((Integer) table.get("offset")).intValue();
            byte[] data = (byte[]) table.get("data");
            final boolean isCompressed = "yes".equals(getProperties().getProperty(EXistOutputKeys.COMPRESS_OUTPUT, "no"));

            // One for the local cached file
            Inflater dec = null;
            byte[] decResult = null;
            int decLength;
            if (isCompressed) {
                dec = new Inflater();
                decResult = new byte[65536];
                dec.setInput(data);
                do {
                    decLength = dec.inflate(decResult);
                    vtmpfile.write(decResult, 0, decLength);
                    // And other for the stream where we want to save it!
                    if (os != null) {
                        os.write(decResult, 0, decLength);
                    }
                } while (decLength == decResult.length || !dec.needsInput());

            } else {
                vtmpfile.write(data);
                // And other for the stream where we want to save it!
                if (os != null) {
                    os.write(data);
                }
            }

            while (offset > 0) {
                params.clear();
                params.add(table.get("handle"));
                params.add(useLongOffset ? Long.toString(offset) : Integer.valueOf((int) offset));
                table = (Map<?, ?>) collection.getClient().execute(method, params);
                offset = useLongOffset ? Long.valueOf((String) table.get("offset")).longValue() : ((Integer) table.get("offset")).longValue();
                data = (byte[]) table.get("data");

                // One for the local cached file
                if (isCompressed) {
                    dec.setInput(data);
                    do {
                        decLength = dec.inflate(decResult);
                        vtmpfile.write(decResult, 0, decLength);
                        // And other for the stream where we want to save it!
                        if (os != null) {
                            os.write(decResult, 0, decLength);
                        }
                    } while (decLength == decResult.length || !dec.needsInput());
                } else {
                    vtmpfile.write(data);
                    // And other for the stream where we want to save it!
                    if (os != null) {
                        os.write(data);
                    }
                }
            }

            if (dec != null) {
                dec.end();
            }

            isLocal = false;
            contentVFile = vtmpfile;
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
        } catch (final IOException | DataFormatException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            if (contentVFile != null) {
                try {
                    contentVFile.close();
                } catch (final IOException ioe) {
                    //IgnoreIT(R)
                }
            }
        }
    }

    protected static InputStream getAnyStream(final Object obj)
            throws XMLDBException {
        if (obj instanceof String) {
            return new ByteArrayInputStream(((String) obj).getBytes(UTF_8));
        } else if (obj instanceof byte[]) {
            return new ByteArrayInputStream((byte[]) obj);
        } else {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "don't know how to handle value of type " + obj.getClass().getName());
        }
    }

    protected void getContentIntoAStreamInternal(final OutputStream os, final Object obj, final boolean isRetrieve, final int handle, final int pos)
            throws XMLDBException {
        if (vfile != null || contentVFile != null || inputSource != null || obj != null) {
            InputStream bis = null;
            try {
                // First, the local content, then the remote one!!!!
                if (vfile != null) {
                    bis = vfile.getByteStream();
                } else if (inputSource != null) {
                    bis = inputSource.getByteStream();
                } else if (obj != null) {
                    bis = getAnyStream(obj);
                } else {
                    bis = contentVFile.getByteStream();
                }
                copy(bis, os);
            } catch (final IOException ioe) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
            } finally {
                if (inputSource != null) {
                    if (bis != null) {
                        // As it comes from an input source, we cannot blindly close it,
                        // but at least let's reset it! (if it is possible)
                        if (bis.markSupported()) {
                            try {
                                bis.reset();
                            } catch (final IOException ioe) {
                                //IgnoreIT(R)
                            }
                        }
                    }
                } else {
                    if (bis != null) {
                        try {
                            bis.close();
                        } catch (final IOException ioe) {
                            //IgnoreIT(R)
                        }
                    }
                }
            }
        } else {
            // Let's fetch it, and save just in time!!!
            getRemoteContentIntoLocalFile(os, isRetrieve, handle, pos);
        }
    }

    protected Object getExtendedContentInternal(final Object obj, final boolean isRetrieve, final int handle, final int pos)
            throws XMLDBException {
        if (obj != null) {
            return obj;
        } else if (vfile != null) {
            return vfile.getContent();
        } else if (inputSource != null) {
            return inputSource;
        } else {
            if (contentVFile == null) {
                getRemoteContentIntoLocalFile(null, isRetrieve, handle, pos);
            }
            return contentVFile.getContent();
        }
    }

    protected InputStream getStreamContentInternal(final Object obj, final boolean isRetrieve, final int handle, final int pos)
            throws XMLDBException {
        final InputStream retval;
        try {
            if (vfile != null) {
                retval = vfile.getByteStream();
            } else if (inputSource != null) {
                retval = inputSource.getByteStream();
            } else if (obj != null) {
                retval = getAnyStream(obj);
            } else {
                // At least one value, please!!!
                if (contentVFile == null) {
                    getRemoteContentIntoLocalFile(null, isRetrieve, handle, pos);
                }
                retval = contentVFile.getByteStream();
            }
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }

        return retval;
    }

    protected long getStreamLengthInternal(final Object obj)
            throws XMLDBException {

        final long retval;
        if (vfile != null) {
            retval = vfile.length();
        } else if (inputSource != null && inputSource instanceof EXistInputSource) {
            retval = ((EXistInputSource) inputSource).getByteStreamLength();
        } else if (obj != null) {
            if (obj instanceof String) {
                retval = ((String) obj).getBytes(UTF_8).length;
            } else if (obj instanceof byte[]) {
                retval = ((byte[]) obj).length;
            } else {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "don't know how to handle value of type " + obj.getClass().getName());
            }
        } else if (contentVFile != null) {
            retval = contentVFile.length();
        } else {
            final List<Object> params = new ArrayList<>();
            params.add(path.toString());
            params.add(getProperties());
            try {
                final Map table = (Map) collection.getClient().execute("describeResource", params);
                if (table.containsKey("content-length-64bit")) {
                    final Object o = table.get("content-length-64bit");
                    if (o instanceof Long) {
                        retval = ((Long) o);
                    } else {
                        retval = Long.parseLong((String) o);
                    }
                } else {
                    retval = ((Integer) table.get("content-length"));
                }
            } catch (final XmlRpcException xre) {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
            }
        }

        return retval;
    }


    protected byte[] readFile(final File file)
            throws XMLDBException {
        try(final InputStream is = new FileInputStream(file)) {
            return readFile(is);
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    protected byte[] readFile(final InputSource in) throws XMLDBException {
        final InputStream bis = in.getByteStream();
        try {
            return readFile(bis);
        } finally {
            // As it comes from an input source, we cannot blindly close it,
            // but at least let's reset it! (if it is possible)
            if (bis.markSupported()) {
                try {
                    bis.reset();
                } catch (final IOException ioe) {
                    //IgnoreIT(R)
                }
            }
        }
    }

    private byte[] readFile(final InputStream is)
            throws XMLDBException {
        try(final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            copy(is, bos);
            return bos.toByteArray();
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    private void copy(final InputStream is, final OutputStream os) throws IOException {
        int read;
        final byte buffer[] = new byte[65536]; //64KB
        while ((read = is.read(buffer)) > -1) {
            os.write(buffer, 0, read);
        }
    }
}
