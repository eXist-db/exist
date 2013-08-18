/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.webdav;

import com.bradmcevoy.http.CollectionResource;
import com.bradmcevoy.http.Auth;
import com.bradmcevoy.http.CopyableResource;
import com.bradmcevoy.http.DeletableResource;
import com.bradmcevoy.http.GetableResource;
import com.bradmcevoy.http.HttpManager;
import com.bradmcevoy.http.LockInfo;
import com.bradmcevoy.http.LockResult;
import com.bradmcevoy.http.LockTimeout;
import com.bradmcevoy.http.LockToken;
import com.bradmcevoy.http.LockableResource;
import com.bradmcevoy.http.MoveableResource;
import com.bradmcevoy.http.PropFindableResource;
import com.bradmcevoy.http.Range;
import com.bradmcevoy.http.exceptions.BadRequestException;
import com.bradmcevoy.http.exceptions.ConflictException;
import com.bradmcevoy.http.exceptions.LockedException;
import com.bradmcevoy.http.exceptions.NotAuthorizedException;
import com.bradmcevoy.http.exceptions.PreConditionFailedException;
import com.bradmcevoy.http.webdav.DefaultUserAgentHelper;
import com.bradmcevoy.http.webdav.UserAgentHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.io.IOUtils;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.util.VirtualTempFile;
import org.exist.webdav.ExistResource.Mode;
import org.exist.webdav.exceptions.DocumentAlreadyLockedException;
import org.exist.webdav.exceptions.DocumentNotLockedException;
import org.exist.xmldb.XmldbURI;

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

    private ExistDocument existDocument;
    
    private VirtualTempFile vtf = null;;

    // Only for PROPFIND the estimate size for an XML document must be shown
    private boolean isPropFind=false;
    
    private enum SIZE_METHOD { NULL, EXACT, APPROXIMATE }; 
    
    private static SIZE_METHOD propfindSizeMethod=null;
    private static SIZE_METHOD getSizeMethod=null;
    
    private static UserAgentHelper userAgentHelper = null;

    /**
     * Set to TRUE if getContentLength is used for PROPFIND.
     */
    public void setIsPropFind(boolean isPropFind) {
        this.isPropFind = isPropFind;
    }

    /**
     *  Constructor of representation of a Document in the Milton framework, without subject information.
     * To be called by the resource factory.
     *
     * @param host  FQ host name including port number.
     * @param uri   Path on server indicating path of resource
     * @param brokerPool Handle to Exist database.
     */
    public MiltonDocument(String host, XmldbURI uri, BrokerPool brokerPool) {
        this(host, uri, brokerPool, null);
    }

    /**
     *  Constructor of representation of a Document in the Milton framework, with subject information.
     * To be called by the resource factory.
     *
     * @param host  FQ host name including port number.
     * @param uri   Path on server indicating path of resource.
     * @param subject  An Exist operation is performed with  User. Can be NULL.
     * @param pool Handle to Exist database.
     */
    public MiltonDocument(String host, XmldbURI uri, BrokerPool pool, Subject subject) {

        super();
        
        if(userAgentHelper==null){
            userAgentHelper=new DefaultUserAgentHelper();
        }

        if(LOG.isTraceEnabled()) {
            LOG.trace(String.format("DOCUMENT:%s", uri.toString()));
        }
        
        resourceXmldbUri = uri;
        brokerPool = pool;
        this.host = host;

        existDocument = new ExistDocument(uri, brokerPool);

        // store simpler type
        existResource = existDocument;

        if (subject != null) {
            existDocument.setUser(subject);
            existDocument.initMetadata();
        }
        
   
        // PROPFIND method 
        if (propfindSizeMethod == null) {
            // get user supplied preferred size determination approach
            String systemProp = System.getProperty(PROPFIND_METHOD_XML_SIZE);
            
            if (systemProp == null) {
                // Default method is approximate
                propfindSizeMethod = SIZE_METHOD.APPROXIMATE;

            } else {
                // Try to parse from environment property
                try {
                    propfindSizeMethod = SIZE_METHOD.valueOf(systemProp.toUpperCase());
                    
                } catch (IllegalArgumentException ex) {
                    LOG.debug(ex.getMessage());
                    // Set preffered default
                    propfindSizeMethod = SIZE_METHOD.APPROXIMATE;
                }
             }
        }
        
        // GET method
        if (getSizeMethod == null) {
            // get user supplied preferred size determination approach
            String systemProp = System.getProperty(GET_METHOD_XML_SIZE);
            
            if (systemProp == null) {
                // Default method is NULL
                getSizeMethod = SIZE_METHOD.NULL;

            } else {
                // Try to parse from environment property
                try {
                    getSizeMethod = SIZE_METHOD.valueOf(systemProp.toUpperCase());
                    
                } catch (IllegalArgumentException ex) {
                    LOG.debug(ex.getMessage());
                    // Set preffered default
                    getSizeMethod = SIZE_METHOD.APPROXIMATE;
                }
             }
        }

        
    }

    /* ================
     * GettableResource
     * ================ */

    @Override
    public void sendContent(OutputStream out, Range range, Map<String, String> params, String contentType)
            throws IOException, NotAuthorizedException, BadRequestException {
        try {
            if(vtf==null){
                LOG.debug("Serializing from database");
                existDocument.stream(out);
                
            } else {
                // Experimental. Does not work right, the virtual file
                // Often does not contain the right amount of bytes.
                
                LOG.debug("Serializing from buffer");
                InputStream is = vtf.getByteStream();
                IOUtils.copy(is, out);
                out.flush();
                IOUtils.closeQuietly(is);
                vtf.delete();
                vtf=null;
            }
            
        } catch (PermissionDeniedException e) {
            LOG.debug(e.getMessage());
            throw new NotAuthorizedException(this);
            
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

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
        
        
        // Note
        // Whilst for non-XML documents the exact size of the documents can
        // be determined by checking the administration, this is not possible
        // for XML documents.
        //
        // For XML documents by default the 'approximate' size is available
        // which can be sufficient (pagesize * nr of pages). Exact size
        // is dependant on many factors, the serialization parameters.
        //
        // The approximate size is a good indication of the size of document
        // but some WebDAV client, mainly the MacOsX Finder version, can
        // not deal with this guesstimate, resulting in incomplete or overcomplete
        // documents.
        //
        // Special for this, two system variables can be set to change the
        // way the size is calculated. Supported values are
        // NULL, EXACT, APPROXIMATE
        //
        // PROPFIND: Unfortunately both NULL and APPROXIMATE do not work for 
        // MacOsX Finder. The default behaviour for the Finder 'user-agent' is 
        // exact, for the others it is approximate. 
        // This behaviour is swiched by the system properties.
        //
        // GET: the NULL value seems to be working well for macosx too.
        
        Long size=null;       
        
        // MacOsX has a bad reputation
        boolean isMacFinder = userAgentHelper.isMacFinder( HttpManager.request().getUserAgentHeader() );
        
        if(existDocument.isXmlDocument()){
            // XML document, exact size is not (directly) known)

            if (isPropFind) {
                
                // PROPFIND
                // In this scensario the XML document is not actually
                // downloaded, only the size needs to be known.
                // This is the most expensive scenario
                
                if(isMacFinder || SIZE_METHOD.EXACT==propfindSizeMethod) {
                    
                    // Returns the exact size, default behaviour for Finder, 
                    // or when set by a system property
                    
                    if(LOG.isDebugEnabled()){
                        LOG.debug(String.format("Serializing XML to /dev/null to determine size (%s) MacFinder=%s", 
                                resourceXmldbUri, isMacFinder));
                    }

                    // Stream document to '/dev/null' and count bytes
                    ByteCountOutputStream counter = new ByteCountOutputStream();
                    try {
                        existDocument.stream(counter);

                    } catch (Exception ex) {
                        LOG.error(ex);
                    }

                    size = counter.getByteCount();
                    
                    
                } else if (SIZE_METHOD.NULL==propfindSizeMethod) {
                    
                    // Returns size unknown. This is not supported
                    // by MacOsX finder
           
                    size = null;
                    
                
                } else { 
                    // Returns the estimated document size. This is the
                    // default value, but not suitable for MacOsX Finder.
                    size = existDocument.getContentLength();
                }
                
                
            } else {
                
                // GET
                // In this scenario, the document will actually be downloaded
                // in the next step.
                
                if (SIZE_METHOD.EXACT == getSizeMethod) {

                    // Return the exact size by pre-serializing the document
                    // to a buffer first. isMacFinder is not needed

                    try {
                        if(LOG.isDebugEnabled()) {
                            LOG.debug(String.format("Serializing XML to virtual file (%s)", resourceXmldbUri));
                        }

                        vtf = new VirtualTempFile();
                        existDocument.stream(vtf);
                        vtf.close();

                    } catch (Exception ex) {
                        LOG.error(ex);
                    }

                    size = vtf.length();
                    
                    
                } else if (SIZE_METHOD.APPROXIMATE == getSizeMethod) {
                    
                    // Return approximate size, be warned to use this
                    
                    size = existDocument.getContentLength();
                    vtf = null; // force live serialization
                    
                } else {
                    
                    // Return no size, the whole file will be downloaded
                    // Works well for macosx finder
                    
                    size = null;
                    vtf = null; // force live serialization
                }
            }
            
            
        } else {
            // Non XML document, actual size is known
            size = existDocument.getContentLength();
        }
        
        if(LOG.isDebugEnabled()) {
            LOG.debug(String.format("Size=%s (%s)", size, resourceXmldbUri));
        }
        return size;
        
    }

    /* ====================
     * PropFindableResource
     * ==================== */

    @Override
    public Date getCreateDate() {
        Date createDate = null;

        Long time = existDocument.getCreationTime();
        if (time != null) {
            createDate = new Date(time);
        }

        return createDate;
    }

    /* =================
     * DeletableResource
     * ================= */

    @Override
    public void delete() throws NotAuthorizedException, ConflictException, BadRequestException {
        existDocument.delete();
    }

    
    /* ================
     * LockableResource
     * ================ */

    @Override
    public LockResult lock(LockTimeout timeout, LockInfo lockInfo)
            throws NotAuthorizedException, PreConditionFailedException, LockedException {

        org.exist.dom.LockToken inputToken = convertToken(timeout, lockInfo);

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Lock: %s", resourceXmldbUri));
        }
        
        LockResult lr = null;
        try {
            org.exist.dom.LockToken existLT = existDocument.lock(inputToken);

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

    @Override
    public LockResult refreshLock(String token) throws NotAuthorizedException, PreConditionFailedException {
        
        if(LOG.isDebugEnabled()) {
            LOG.debug(String.format("Refresh: %s token=%s", resourceXmldbUri, token));
        }

        LockResult lr = null;
        try {
            org.exist.dom.LockToken existLT = existDocument.refreshLock(token);

            // Process result
            LockToken mltonLT = convertToken(existLT);
            lr = LockResult.success(mltonLT);

        } catch (PermissionDeniedException ex) {
            LOG.debug(ex.getMessage());
            throw new NotAuthorizedException(this);

        } catch (DocumentNotLockedException ex) {
            LOG.debug(ex.getMessage());
            lr = LockResult.failed(LockResult.FailureReason.PRECONDITION_FAILED);

        } catch (DocumentAlreadyLockedException ex) {
            //throw new LockedException(this);
            LOG.debug(ex.getMessage());
            lr = LockResult.failed(LockResult.FailureReason.ALREADY_LOCKED);

        } catch (EXistException ex) {
            LOG.debug(ex.getMessage());
            lr = LockResult.failed(LockResult.FailureReason.PRECONDITION_FAILED);

        }
        return lr;
    }

    @Override
    public void unlock(String tokenId) throws NotAuthorizedException, PreConditionFailedException {

        if(LOG.isDebugEnabled()) {
            LOG.debug(String.format("Unlock: %s", resourceXmldbUri));
        }
        
        try {
            existDocument.unlock();
        } catch (PermissionDeniedException ex) {
            LOG.debug(ex.getMessage());
            throw new NotAuthorizedException(this);

        } catch (DocumentNotLockedException ex) {
            LOG.debug(ex.getMessage());
            throw new PreConditionFailedException(this);

        } catch (EXistException ex) {
            LOG.debug(ex.getMessage());
            throw new PreConditionFailedException(this);
        }
    }

    @Override
    public LockToken getCurrentLock() {

        if(LOG.isDebugEnabled()) {
            LOG.debug(String.format("getCurrentLock: %s", resourceXmldbUri));
        }
        
        org.exist.dom.LockToken existLT = existDocument.getCurrentLock();

        if (existLT == null) {
            LOG.debug("No database lock token.");
            return null;
        }

        // Construct Lock Info
        LockToken miltonLT = convertToken(existLT);

        // Return values in Milton object
        return miltonLT;
    }


    /* ================
     * MoveableResource
     * ================ */

    @Override
    public void moveTo(CollectionResource rDest, String newName) throws ConflictException {

        if(LOG.isDebugEnabled()) {
            LOG.debug(String.format("moveTo: %s newName=%s", resourceXmldbUri, newName));
        }

        XmldbURI destCollection = ((MiltonCollection) rDest).getXmldbUri();
        try {
            existDocument.resourceCopyMove(destCollection, newName, Mode.MOVE);

        } catch (EXistException ex) {
            throw new ConflictException(this);
        }
    }


    /* ================
     * CopyableResource
     * ================ */

    @Override
    public void copyTo(CollectionResource rDest, String newName) {

        if(LOG.isDebugEnabled()) {
            LOG.debug(String.format("copyTo: %s newName=%s", resourceXmldbUri, newName));
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
     * StAX serializer
     * ================ */
    
    /**
     *  Serialize document properties
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
    
    /**
     * Set specific WebDAV serialization options
     * 
     * @param config XML serialization options
     */
    public void setConfiguration(Properties config){
        existDocument.setConfiguration(config);
    }
}
