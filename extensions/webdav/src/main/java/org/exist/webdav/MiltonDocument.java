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
package org.exist.webdav;

import com.bradmcevoy.http.*;
import com.bradmcevoy.http.exceptions.*;
import com.bradmcevoy.http.webdav.DefaultUserAgentHelper;
import com.bradmcevoy.http.webdav.UserAgentHelper;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.webdav.ExistResource.Mode;
import org.exist.webdav.exceptions.DocumentAlreadyLockedException;
import org.exist.webdav.exceptions.DocumentNotLockedException;
import org.exist.xmldb.XmldbURI;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

/**
 * Class for representing an eXist-db document as a Milton WebDAV document.
 * See <a href="http://milton.ettrema.com">Milton</a>.
 *
 * @author Dannes Wessels (dizzzz_at_exist-db.org)
 */
public class MiltonDocument extends MiltonResource
        implements GetableResource, PropFindableResource,
        DeletableResource, LockableResource, MoveableResource, CopyableResource {

    public static final String PROPFIND_METHOD_XML_SIZE = "org.exist.webdav.PROPFIND_METHOD_XML_SIZE";
    public static final String GET_METHOD_XML_SIZE = "org.exist.webdav.GET_METHOD_XML_SIZE";
    private static SIZE_METHOD propfindSizeMethod = null;
    private static SIZE_METHOD getSizeMethod = null;

    private static UserAgentHelper userAgentHelper = null;
    private final ExistDocument existDocument;

    // Only for PROPFIND the estimate size for an XML document must be shown
    private boolean isPropFind = false;

    /**
     * Constructor of representation of a Document in the Milton framework, without subject information.
     * To be called by the resource factory.
     *
     * @param configuration any configuration properties.
     * @param host       FQ host name including port number.
     * @param uri        Path on server indicating path of resource
     * @param brokerPool Handle to Exist database.
     */
    public MiltonDocument(final Properties configuration, String host, XmldbURI uri, BrokerPool brokerPool) {
        this(configuration, host, uri, brokerPool, null);
    }

    /**
     * Constructor of representation of a Document in the Milton framework, with subject information.
     * To be called by the resource factory.
     *
     * @param configuration any configuration properties.
     * @param host    FQ host name including port number.
     * @param uri     Path on server indicating path of resource.
     * @param subject An Exist operation is performed with  User. Can be NULL.
     * @param pool    Handle to Exist database.
     */
    public MiltonDocument(final Properties configuration, String host, XmldbURI uri, BrokerPool pool, Subject subject) {
        super(configuration);

        if (userAgentHelper == null) {
            userAgentHelper = new DefaultUserAgentHelper();
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("DOCUMENT:{}", uri.toString());
        }

        resourceXmldbUri = uri;
        brokerPool = pool;
        this.host = host;

        existDocument = new ExistDocument(configuration, uri, brokerPool);

        // store simpler type
        existResource = existDocument;

        if (subject != null) {
            existDocument.setUser(subject);
            existDocument.initMetadata();
        }

        // PROPFIND method
        if (propfindSizeMethod == null) {
            LOG.info("Try to obtain {} from System Property", PROPFIND_METHOD_XML_SIZE);
            String systemProp = System.getProperty(PROPFIND_METHOD_XML_SIZE);
            propfindSizeMethod = getSizeMethod(systemProp);
        }

        if (propfindSizeMethod == null) {
            LOG.info("Alternatively try to obtain {} from properties file", PROPFIND_METHOD_XML_SIZE);
            String fileProp = configuration.getProperty(PROPFIND_METHOD_XML_SIZE);
            propfindSizeMethod = getSizeMethod(fileProp);
        }

        if (propfindSizeMethod == null) {
            LOG.info("Use default value {}", SIZE_METHOD.APPROXIMATE);
            propfindSizeMethod = SIZE_METHOD.APPROXIMATE;
        }

        // GET method
        if (getSizeMethod == null) {
            LOG.info("Try to obtain {} from System Property", GET_METHOD_XML_SIZE);
            String systemProp = System.getProperty(GET_METHOD_XML_SIZE);
            getSizeMethod = getSizeMethod(systemProp);
        }

        if (getSizeMethod == null) {
            LOG.info("Alternatively try to obtain {} from properties file", GET_METHOD_XML_SIZE);
            String fileProp = configuration.getProperty(GET_METHOD_XML_SIZE);
            getSizeMethod = getSizeMethod(fileProp);
        }

        if (getSizeMethod == null) {
            LOG.info("Use default value {}", SIZE_METHOD.NULL);
            getSizeMethod = SIZE_METHOD.NULL;
        }

    }

    /**
     * Determine what size methodology shall be applied.
     *
     * @param value Properties value
     * @return Corresponding SIZE_METHOD, or else NULL.
     */
    SIZE_METHOD getSizeMethod(String value) {
        if (value == null || value.strip().isEmpty()) {
            return null;
        }

        try {
            final SIZE_METHOD sizeMethod = SIZE_METHOD.valueOf(value.toUpperCase());
            LOG.info("Found value {}", sizeMethod);
            return sizeMethod;
        } catch (IllegalArgumentException ex) {
            LOG.debug(ex.getMessage());
            return null;
        }
    }

    /**
     * Set to TRUE if getContentLength is used for PROPFIND.
     *
     * @param isPropFind Set to TRUE if request is PropFind request.
     */
    public void setIsPropFind(boolean isPropFind) {
        this.isPropFind = isPropFind;
    }

    @Override
    public void sendContent(OutputStream out, Range range, Map<String, String> params, String contentType)
            throws IOException, NotAuthorizedException {
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Serializing from database");
            }
            existDocument.stream(out);

        } catch (PermissionDeniedException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(e.getMessage());
            }
            throw new NotAuthorizedException(this);
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    /* ================
     * GettableResource
     * ================ */

    @Override
    public Long getMaxAgeSeconds(Auth auth) {
        return null;
    }

    @Override
    public String getContentType(String accepts) {
        return existDocument.getMimeType();
    }

    @Override
    public Long getContentLength() {

        /*
            ## Due to the way eXist-db stores XML, the exact size of an XML document when
            ## it is serialized (e.g., sent to a WebDAV client) may vary depending upon
            ## serialization parameters.
            ##
            ## For performance reasons, eXist by default only reports an approximate file size
            ## for XML documents. (eXist reports accurate sizes for binary documents,
            ## which aren't subject to serialization parameters.)
            ##
            ## The approximate size is a good indication of the size of document
            ## but some WebDAV clients, in particular the macOS Finder version, can
            ## not deal with this estimate, resulting in incomplete or overcomplete
            ## documents.
            ##
            ## To address these various possibilities, two system variables can be set
            ## to change the way the size is calculated.
            ##
            ## Supported values are APPROXIMATE, EXACT, NULL
            ##
            ## PROPFIND:
            ## Unfortunately both NULL and APPROXIMATE do not work for
            ## macOS Finder. The default behaviour for the Finder 'user-agent' is
            ## exact, for the others it is approximate.
            ##
            ## GET:
            ## The NULL value seems to be working well for macOS too.
            ##
            ## The system properties are:
            ## -Dorg.exist.webdav.PROPFIND_METHOD_XML_SIZE=..  (used for listing documents in collection)
            ## -Dorg.exist.webdav.GET_METHOD_XML_SIZE=...      (used during download of one document)
            ##
            ## Supported values are:
            ## NULL         - document sizes are NOT reported
            ## EXACT        - document sizes are reported using document pre-serialization [Slow]
            ## APPROXIMATE  - document sizes are reported as (pagesize * number of pages)
            ##
            ## Depending on the WebDAV client needs, one or both properties can be set.
            #
            # org.exist.webdav.PROPFIND_METHOD_XML_SIZE=APPROXIMATE
            # org.exist.webdav.GET_METHOD_XML_SIZE=NULL
        */

        Long size = null;

        // MacOsX has a bad reputation
        boolean isMacFinder = userAgentHelper.isMacFinder(HttpManager.request().getUserAgentHeader());

        if (existDocument.isXmlDocument()) {
            // XML document, exact size is not (directly) known)
            if (isMacFinder || SIZE_METHOD.EXACT == propfindSizeMethod) {

                // Returns the exact size, default behaviour for Finder,
                // or when set by a system property

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Serializing XML to /dev/null to determine size ({}) MacFinder={}", resourceXmldbUri, isMacFinder);
                }

                // Stream document to '/dev/null' and count bytes
                try (final CountingOutputStream counter = new CountingOutputStream(OutputStream.nullOutputStream())) {
                    existDocument.stream(counter);
                    size = counter.getByteCount();
                } catch (Exception ex) {
                    LOG.error(ex);
                }

            } else if (SIZE_METHOD.NULL == propfindSizeMethod) {

                // Returns size unknown. This is not supported
                // by MacOsX finder

                size = null;

            } else {
                // Returns the estimated document size. This is the
                // default value, but not suitable for MacOsX Finder.
                size = existDocument.getContentLength();
            }
        } else {
            // Non XML document, actual size is known
            size = existDocument.getContentLength();
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Size={} ({})", size, resourceXmldbUri);
        }
        return size;

    }

    @Override
    public Date getCreateDate() {
        Date createDate = null;

        Long time = existDocument.getCreationTime();
        if (time != null) {
            createDate = new Date(time);
        }

        return createDate;
    }

    /* ====================
     * PropFindableResource
     * ==================== */

    @Override
    public void delete() throws NotAuthorizedException, ConflictException, BadRequestException {
        existDocument.delete();
    }

    /* =================
     * DeletableResource
     * ================= */

    @Override
    public LockResult lock(LockTimeout timeout, LockInfo lockInfo)
            throws NotAuthorizedException, PreConditionFailedException, LockedException {

        org.exist.dom.persistent.LockToken inputToken = convertToken(timeout, lockInfo);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Lock: {}", resourceXmldbUri);
        }

        LockResult lr = null;
        try {
            org.exist.dom.persistent.LockToken existLT = existDocument.lock(inputToken);

            // Process result
            LockToken mltonLT = convertToken(existLT);
            lr = LockResult.success(mltonLT);

        } catch (PermissionDeniedException ex) {
            LOG.debug(ex.getMessage());
            throw new NotAuthorizedException(this);

        } catch (DocumentAlreadyLockedException ex) {
            // set result iso throw new LockedException(this);
            LOG.debug(ex.getMessage());
            lr = LockResult.failed(LockResult.FailureReason.ALREADY_LOCKED);

        } catch (EXistException ex) {
            LOG.debug(ex.getMessage());
            lr = LockResult.failed(LockResult.FailureReason.PRECONDITION_FAILED);

        }
        return lr;
    }

    
    /* ================
     * LockableResource
     * ================ */

    @Override
    public LockResult refreshLock(String token) throws NotAuthorizedException, PreConditionFailedException {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Refresh: {} token={}", resourceXmldbUri, token);
        }

        LockResult lr = null;
        try {
            org.exist.dom.persistent.LockToken existLT = existDocument.refreshLock(token);

            // Process result
            LockToken mltonLT = convertToken(existLT);
            lr = LockResult.success(mltonLT);

        } catch (PermissionDeniedException ex) {
            LOG.debug(ex.getMessage());
            throw new NotAuthorizedException(this);

        } catch (DocumentNotLockedException | EXistException ex) {
            LOG.debug(ex.getMessage());
            lr = LockResult.failed(LockResult.FailureReason.PRECONDITION_FAILED);

        } catch (DocumentAlreadyLockedException ex) {
            //throw new LockedException(this);
            LOG.debug(ex.getMessage());
            lr = LockResult.failed(LockResult.FailureReason.ALREADY_LOCKED);

        }
        return lr;
    }

    @Override
    public void unlock(String tokenId) throws NotAuthorizedException, PreConditionFailedException {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Unlock: {}", resourceXmldbUri);
        }

        try {
            existDocument.unlock();
        } catch (PermissionDeniedException ex) {
            LOG.debug(ex.getMessage());
            throw new NotAuthorizedException(this);

        } catch (DocumentNotLockedException | EXistException ex) {
            LOG.debug(ex.getMessage());
            throw new PreConditionFailedException(this);

        }
    }

    @Override
    public LockToken getCurrentLock() {

        if (LOG.isDebugEnabled()) {
            LOG.debug("getCurrentLock: {}", resourceXmldbUri);
        }

        org.exist.dom.persistent.LockToken existLT = existDocument.getCurrentLock();

        if (existLT == null) {
            LOG.debug("No database lock token.");
            return null;
        }

        // Construct Lock Info
        LockToken miltonLT = convertToken(existLT);

        // Return values in Milton object
        return miltonLT;
    }

    @Override
    public void moveTo(CollectionResource rDest, String newName) throws ConflictException {

        if (LOG.isDebugEnabled()) {
            LOG.debug("moveTo: {} newName={}", resourceXmldbUri, newName);
        }

        XmldbURI destCollection = ((MiltonCollection) rDest).getXmldbUri();
        try {
            existDocument.resourceCopyMove(destCollection, newName, Mode.MOVE);

        } catch (EXistException ex) {
            throw new ConflictException(this, "Move '" + getXmldbUri() + "' to '" + destCollection.append(newName) + "' failed: " + ex.getMessage());
        }
    }


    /* ================
     * MoveableResource
     * ================ */

    @Override
    public void copyTo(CollectionResource rDest, String newName) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("copyTo: {} newName={}", resourceXmldbUri, newName);
        }

        XmldbURI destCollection = ((MiltonCollection) rDest).getXmldbUri();
        try {
            existDocument.resourceCopyMove(destCollection, newName, Mode.COPY);

        } catch (EXistException ex) {
            // unable to throw new ConflictException(this);
            LOG.error(ex.getMessage());
        }
    }


    /* ================
     * CopyableResource
     * ================ */

    /**
     * Serialize document properties
     *
     * @param writer STAX writer
     * @throws XMLStreamException Thrown when writing data failed
     */
    public void writeXML(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("exist", "document", "http://exist.sourceforge.net/NS/exist");
        writer.writeAttribute("name", resourceXmldbUri.lastSegment().toString());
        writer.writeAttribute("created", getXmlDateTime(existDocument.getCreationTime()));
        writer.writeAttribute("last-modified", getXmlDateTime(existDocument.getLastModified()));
        writer.writeAttribute("owner", existDocument.getOwnerUser());
        writer.writeAttribute("group", existDocument.getOwnerGroup());
        writer.writeAttribute("permissions", "" + existDocument.getPermissions().toString());
        writer.writeAttribute("size", "" + existDocument.getContentLength());
        writer.writeEndElement();
    }


    /* ================
     * StAX serializer
     * ================ */

    private enum SIZE_METHOD {NULL, EXACT, APPROXIMATE}
}
