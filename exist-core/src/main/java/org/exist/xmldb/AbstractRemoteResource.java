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
package org.exist.xmldb;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.evolvedbinary.j8fu.lazy.LazyVal;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.exist.security.Permission;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.EXistInputSource;
import org.exist.util.FileUtils;
import org.exist.util.Leasable;
import org.exist.util.io.ByteArrayContent;
import org.exist.util.io.ContentFile;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.util.io.TemporaryFileManager;
import org.exist.util.io.VirtualTempPath;
import org.xml.sax.InputSource;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.util.io.InputStreamUtil.copy;

public abstract class AbstractRemoteResource extends AbstractRemote
        implements EXistResource, ExtendedResource, Resource {
    protected final XmldbURI path;
    private String mimeType;

    protected Path file = null;
    private ContentFile contentFile = null;
    protected InputSource inputSource = null;
    private long contentLen = -1L;
    private Permission permissions = null;
    private boolean closed;
    private LazyVal<Integer> inMemoryBufferSize;

    Date dateCreated = null;
    Date dateModified = null;

    protected AbstractRemoteResource(final RemoteCollection parent, final XmldbURI documentName, final String mimeType) {
        super(parent);
        if (documentName.numSegments() > 1) {
            this.path = documentName;
        } else {
            this.path = parent.getPathURI().append(documentName);
        }
        this.mimeType = mimeType;
    }

    @Override
    public Properties getProperties() {
        return collection.getProperties();
    }

    @Override
    public Object getContent()
            throws XMLDBException {
        final Object res = getExtendedContent();
        if (res != null) {
            if (res instanceof byte[]) {
                return res;
            } else if (res instanceof Path) {
                return readFile((Path) res);
            } else if (res instanceof java.io.File) {
                return readFile(((java.io.File) res).toPath());
            } else if (res instanceof InputSource) {
                return readFile((InputSource) res);
            } else if (res instanceof ContentFile) {
                return ((ContentFile) res).getBytes();
            }
        }
        return res;
    }

    /**
     * Get the data.
     *
     * @return the data
     *
     * @throws XMLDBException if an error occurs whilst retrieving the data.
     *
     * @deprecated instead use {@link org.xmldb.api.base.Resource#getContent()}
     */
    @Deprecated
    protected byte[] getData()
            throws XMLDBException {
        final Object res = getExtendedContent();
        if (res != null) {
            if (res instanceof Path) {
                return readFile((Path) res);
            } else if (res instanceof java.io.File) {
                return readFile(((java.io.File) res).toPath());
            } else if (res instanceof InputSource) {
                return readFile((InputSource) res);
            } else if (res instanceof String) {
                return ((String) res).getBytes(UTF_8);
            } else if (res instanceof ContentFile) {
                return ((ContentFile) res).getBytes();
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
            if (dateModified.before(getCreationTime())) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "Modification time must be after creation time.");
            }

            final List<Object> params = new ArrayList<>(2);
            params.add(path.toString());
            params.add(dateModified.getTime());

            collection.execute("setLastModified", params);

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

        boolean wasSet = false;
        try {
            freeResources();
            if (value instanceof ContentFile) {
                contentFile = (ContentFile)value;
                setExtendendContentLength(contentFile.size());
                wasSet = true;
            } else  if (value instanceof Path) {
                file = (Path) value;
                setExtendendContentLength(Files.size(file));
                wasSet = true;
            } else if (value instanceof java.io.File) {
                file = ((java.io.File) value).toPath();
                setExtendendContentLength(Files.size(file));
                wasSet = true;
            } else if (value instanceof InputSource) {
                inputSource = (InputSource) value;
                if (inputSource instanceof EXistInputSource) {
                    setExtendendContentLength(((EXistInputSource) inputSource).getByteStreamLength());
                }
                wasSet = true;
            } else if (value instanceof byte[]) {
                contentFile = ByteArrayContent.of((byte[]) value);
                setExtendendContentLength(contentFile.size());
                wasSet = true;
            } else if (value instanceof String) {
                contentFile = ByteArrayContent.of((String) value);
                setExtendendContentLength(contentFile.size());
                wasSet = true;
            }
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e);
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
    public void getContentIntoAFile(final Path localfile)
            throws XMLDBException {
        try (final OutputStream os = new BufferedOutputStream(Files.newOutputStream(localfile))) {
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
            params.add(handle);
            params.add(pos);
        } else {
            command = "getDocumentData";
            params.add(path.toString());
        }
        Properties properties = getProperties();
        params.add(properties);

        try {
            final TemporaryFileManager tempFileManager = TemporaryFileManager.getInstance();
            final VirtualTempPath tempFile = new VirtualTempPath(getInMemorySize(properties), tempFileManager);

            Map<?, ?> table = (Map<?, ?>) collection.execute(command, params);

            final String method;
            final boolean useLongOffset;
            if (table.containsKey("supports-long-offset") && (Boolean) table.get("supports-long-offset")) {
                useLongOffset = true;
                method = "getNextExtendedChunk";
            } else {
                useLongOffset = false;
                method = "getNextChunk";
            }

            long offset = (Integer) table.get("offset");
            byte[] data = (byte[]) table.get("data");
            final boolean isCompressed = "yes".equals(properties.getProperty(EXistOutputKeys.COMPRESS_OUTPUT, "no"));

            try (final OutputStream osTempFile = tempFile.newOutputStream()) {

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
                        osTempFile.write(decResult, 0, decLength);
                        // And other for the stream where we want to save it!
                        if (os != null) {
                            os.write(decResult, 0, decLength);
                        }
                    } while (decLength == decResult.length || !dec.needsInput());

                } else {
                    osTempFile.write(data);
                    // And other for the stream where we want to save it!
                    if (os != null) {
                        os.write(data);
                    }
                }

                while (offset > 0) {
                    params.clear();
                    params.add(table.get("handle"));
                    params.add(useLongOffset ? Long.toString(offset) : Integer.valueOf((int) offset));
                    table = (Map<?, ?>) collection.execute(method, params);
                    offset = useLongOffset ? Long.parseLong((String) table.get("offset")) : ((Integer) table.get("offset"));
                    data = (byte[]) table.get("data");

                    // One for the local cached file
                    if (isCompressed) {
                        dec.setInput(data);
                        do {
                            decLength = dec.inflate(decResult);
                            osTempFile.write(decResult, 0, decLength);
                            // And other for the stream where we want to save it!
                            if (os != null) {
                                os.write(decResult, 0, decLength);
                            }
                        } while (decLength == decResult.length || !dec.needsInput());
                    } else {
                        osTempFile.write(data);
                        // And other for the stream where we want to save it!
                        if (os != null) {
                            os.write(data);
                        }
                    }
                }

                if (dec != null) {
                    dec.end();
                }
            }

            contentFile = tempFile;
        } catch (final IOException | DataFormatException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    protected final int getInMemorySize(Properties properties) {
        if (inMemoryBufferSize == null) {
            inMemoryBufferSize = new LazyVal<>(() -> Integer.parseInt(properties.getProperty("in-memory-buffer-size", Integer.toString(VirtualTempPath.DEFAULT_IN_MEMORY_SIZE))));
        }
        return inMemoryBufferSize.get();
    }

    protected static InputStream getAnyStream(final Object obj)
            throws XMLDBException {
        if (obj instanceof String) {
            return new UnsynchronizedByteArrayInputStream(((String) obj).getBytes(UTF_8));
        } else if (obj instanceof byte[]) {
            return new UnsynchronizedByteArrayInputStream((byte[]) obj);
        } else {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "don't know how to handle value of type " + obj.getClass().getName());
        }
    }

    protected void getContentIntoAStreamInternal(final OutputStream os, final Object obj, final boolean isRetrieve, final int handle, final int pos)
            throws XMLDBException {
        if (file != null || contentFile != null || inputSource != null || obj != null) {
            InputStream bis = null;
            try {
                // First, the local content, then the remote one!!!!
                if (file != null) {
                    bis = new BufferedInputStream(Files.newInputStream(file));
                } else if (inputSource != null) {
                    bis = inputSource.getByteStream();
                } else if (obj != null) {
                    bis = getAnyStream(obj);
                } else {
                    bis = contentFile.newInputStream();
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
        } else if (file != null) {
            return file;
        } else if (inputSource != null) {
            return inputSource;
        } else {
            if (contentFile == null) {
                getRemoteContentIntoLocalFile(null, isRetrieve, handle, pos);
            }
            return contentFile;
        }
    }

    protected InputStream getStreamContentInternal(final Object obj, final boolean isRetrieve, final int handle, final int pos)
            throws XMLDBException {
        final InputStream retval;
        try {
            if (file != null) {
                retval = new BufferedInputStream(Files.newInputStream(file));
            } else if (inputSource != null) {
                retval = inputSource.getByteStream();
            } else if (obj != null) {
                retval = getAnyStream(obj);
            } else {
                // At least one value, please!!!
                if (contentFile == null) {
                    getRemoteContentIntoLocalFile(null, isRetrieve, handle, pos);
                }
                retval = contentFile.newInputStream();
            }
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }

        return retval;
    }

    protected long getStreamLengthInternal(final Object obj)
            throws XMLDBException {

        final long retval;
        if (file != null) {
            retval = FileUtils.sizeQuietly(file);
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
        } else if (contentFile != null) {
            retval = contentFile.size();
        } else {
            final List<Object> params = new ArrayList<>();
            params.add(path.toString());
            params.add(getProperties());
            final Map<?, ?> table = (Map<?, ?>) collection.execute("describeResource", params);
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
        }

        return retval;
    }


    protected byte[] readFile(final Path file)
            throws XMLDBException {
        try {
            return Files.readAllBytes(file);
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    protected byte[] readFile(final InputSource in) throws XMLDBException {
        final InputStream bis = in.getByteStream();
        try {
            return readFile(bis);
        } finally {
            //TODO(AR) why do we do this? should probably close it?

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
        try (final UnsynchronizedByteArrayOutputStream bos = new UnsynchronizedByteArrayOutputStream()) {
            bos.write(is);
            return bos.toByteArray();
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public final boolean isClosed() {
        return closed;
    }

    @Override
    public final void close() {
        if (!isClosed()) {
            try {
                file = null;
                inputSource = null;
                if (contentFile != null) {
                    contentFile.close();
                    contentFile = null;
                }
            } finally {
                closed = true;
            }
        }
    }

    @Override
    public final void freeResources() {
        close();
    }
}
