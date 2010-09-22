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

import com.bradmcevoy.http.Auth;
import com.bradmcevoy.http.CollectionResource;
import com.bradmcevoy.http.CopyableResource;
import com.bradmcevoy.http.DeletableResource;
import com.bradmcevoy.http.GetableResource;
import com.bradmcevoy.http.LockInfo;
import com.bradmcevoy.http.LockResult;
import com.bradmcevoy.http.LockTimeout;
import com.bradmcevoy.http.LockToken;
import com.bradmcevoy.http.LockingCollectionResource;
import com.bradmcevoy.http.MakeCollectionableResource;
import com.bradmcevoy.http.MoveableResource;
import com.bradmcevoy.http.PropFindableResource;
import com.bradmcevoy.http.PutableResource;
import com.bradmcevoy.http.Range;
import com.bradmcevoy.http.Resource;
import com.bradmcevoy.http.exceptions.BadRequestException;
import com.bradmcevoy.http.exceptions.ConflictException;
import com.bradmcevoy.http.exceptions.LockedException;
import com.bradmcevoy.http.exceptions.NotAuthorizedException;
import com.bradmcevoy.http.exceptions.PreConditionFailedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.webdav.ExistResource.Mode;
import org.exist.webdav.exceptions.CollectionDoesNotExistException;
import org.exist.webdav.exceptions.CollectionExistsException;
import org.exist.xmldb.XmldbURI;

/**
 * Class for representing an eXist-db collection as a Milton WebDAV collection.
 * See <a href="http://milton.ettrema.com">Milton</a>.
 *
 * @author Dannes Wessels (dizzzz_at_exist-db.org)
 */
public class MiltonCollection extends MiltonResource
        implements CollectionResource, GetableResource, PropFindableResource,
        DeletableResource, MakeCollectionableResource, PutableResource, LockingCollectionResource /*, DigestResource */, MoveableResource, CopyableResource {

    private ExistCollection existCollection;

    /**
     *  Constructor of representation of a Collection in the Milton framework, without subject information.
     * To be called by the resource factory.
     *
     * @param host  FQ host name including port number.
     * @param uri   Path on server indicating path of resource
     * @param brokerPool Handle to Exist database.
     */
    public MiltonCollection(String host, XmldbURI uri, BrokerPool pool) {
        this(host, uri, pool, null);
    }

    /**
     *  Constructor of representation of a Document in the Milton framework, with subject information.
     * To be called by the resource factory.
     *
     * @param host  FQ host name including port number.
     * @param uri   Path on server indicating path of resource.
     * @param subject  An Exist operation is performed with  User. Can be NULL.
     * @param brokerPool Handle to Exist database.
     */
    public MiltonCollection(String host, XmldbURI uri, BrokerPool pool, Subject user) {

        super();

        LOG.debug("COLLECTION:" + uri.toString());
        resourceXmldbUri = uri;
        brokerPool = pool;
        this.host = host;

        existCollection = new ExistCollection(uri, brokerPool);

        // store simpler type
        existResource = existCollection;

        // If subject is available, additional data can be retrieved.
        if (user != null) {
            existCollection.setUser(user);
            existCollection.initMetadata();
        }
    }

    /* ===================
     * Collection Resource
     * =================== */
    @Override
    public Resource child(String childName) {

        LOG.debug("get child=" + childName);

        // Safe guard value
        if (childName == null) {
            return null;
        }

        // Search all resources, return resource upon match
        List<? extends Resource> allResources = getChildren();
        for (Resource resource : allResources) {
            if (childName.equals(resource.getName())) {
                return resource;
            }
        }

        // Not found
        return null;
    }

    private List<MiltonCollection> getCollectionResources() {
        List<MiltonCollection> allResources = new ArrayList<MiltonCollection>();
        for (XmldbURI path : existCollection.getCollectionURIs()) {
            allResources.add(new MiltonCollection(this.host, path, brokerPool, subject));
        }
        return allResources;
    }

    private List<MiltonDocument> getDocumentResources() {
        List<MiltonDocument> allResources = new ArrayList<MiltonDocument>();
        for (XmldbURI path : existCollection.getDocumentURIs()) {
            allResources.add(new MiltonDocument(this.host, path, brokerPool, subject));
        }
        return allResources;
    }

    @Override
    public List<? extends Resource> getChildren() {
        List<Resource> allResources = new ArrayList<Resource>();

        allResources.addAll(getCollectionResources());
        allResources.addAll(getDocumentResources());

        LOG.debug("Nr of children=" + allResources.size());

        return allResources;
    }


    /* ====================
     * PropFindableResource
     * ==================== */
    @Override
    public Date getCreateDate() {

        Date createDate = null;

        Long time = existCollection.getCreationTime();
        if (time != null) {
            createDate = new Date(time);
        }

        LOG.debug("Create date=" + createDate);

        return createDate;
    }

    /* ====================
     * DeletableResource
     * ==================== */
    @Override
    public void delete() throws NotAuthorizedException, ConflictException, BadRequestException {
        existCollection.delete();
    }

    /* ==========================
     * MakeCollectionableResource
     * ========================== */
    @Override
    public CollectionResource createCollection(String name)
            throws NotAuthorizedException, ConflictException {

        LOG.debug("Create collection '" + name + "' in '" + resourceXmldbUri.toString() + "'.");

        CollectionResource collection = null;
        try {
            XmldbURI collectionURI = existCollection.createCollection(name);
            collection = new MiltonCollection(host, collectionURI, brokerPool, subject);

        } catch (PermissionDeniedException ex) {
            LOG.debug(ex.getMessage());
            throw new NotAuthorizedException(this);

        } catch (CollectionExistsException ex) {
            LOG.debug(ex.getMessage());
            throw new ConflictException(this);

        } catch (EXistException ex) {
            LOG.debug(ex.getMessage());
            throw new ConflictException(this);
        }

        return collection;
    }


    /* ===============
     * PutableResource
     * =============== */
    @Override
    public Resource createNew(String newName, InputStream is, Long length, String contentType)
            throws IOException, ConflictException {

        LOG.debug("Create " + newName + " in " + resourceXmldbUri);

        Resource resource = null;
        try {
            // submit
            XmldbURI resourceURI = existCollection.createFile(newName, is, length, contentType);

            resource = new MiltonDocument(host, resourceURI, brokerPool, subject);

        } catch (PermissionDeniedException e) {
            LOG.debug(e.getMessage());
            throw new ConflictException(this);

        } catch (CollectionDoesNotExistException e) {
            LOG.debug(e.getMessage());
            throw new ConflictException(this);

        }
        return resource;
    }

    /* =========================
     * LockingCollectionResource
     * ========================= */
    @Override
    public LockToken createAndLock(String name, LockTimeout timeout, LockInfo lockInfo) throws NotAuthorizedException {
        // DWES: not sure if required to implement
        LOG.debug(resourceXmldbUri.toString() + " name=" + name + " NOT IMPLEMENTED YET");
        return null;
    }


    /* ================
     * LockableResource
     * ================ */
    @Override
    public LockResult lock(LockTimeout timeout, LockInfo lockInfo)
            throws NotAuthorizedException, PreConditionFailedException, LockedException {
        // DWES: not sure if required to implement
        LOG.debug(resourceXmldbUri.toString());
        throw new RuntimeException("didnt lock");
    }

    @Override
    public LockResult refreshLock(String token) throws NotAuthorizedException, PreConditionFailedException {
        // DWES: not sure if required to implement
        LOG.debug(resourceXmldbUri.toString() + " token=" + token);
        throw new RuntimeException("didnt lock");
    }

    @Override
    public void unlock(String tokenId) throws NotAuthorizedException, PreConditionFailedException {
        // DWES: not sure if required to implement
        LOG.debug(resourceXmldbUri.toString() + " token=" + tokenId);
        throw new RuntimeException("didnt lock");
    }

    @Override
    public LockToken getCurrentLock() {
        // DWES: not sure if required to implement
        LOG.debug(resourceXmldbUri.toString());
        return null;
    }


    /* ===============
     * MovableResource
     * =============== */
    @Override
    public void moveTo(CollectionResource rDest, String newName) throws ConflictException {
        LOG.debug("Move '" + rDest.getName() + "'---'" + newName + "'");
        XmldbURI destCollection = ((MiltonCollection) rDest).getXmldbUri();
        try {
            existCollection.resourceCopyMove(destCollection, newName, Mode.MOVE);

        } catch (EXistException ex) {
            throw new ConflictException(this);
        }
    }

    /* ================
     * CopyableResource
     * ================ */

    @Override
    public void copyTo(CollectionResource toCollection, String newName) {
        LOG.debug("Copy '" + toCollection.getName() + "'---'" + newName + "'");
        XmldbURI destCollection = ((MiltonCollection) toCollection).getXmldbUri();
        try {
            existCollection.resourceCopyMove(destCollection, newName, Mode.COPY);

        } catch (EXistException ex) {
            // copyTo does not throw COnflictException
            LOG.error(ex.getMessage());
        }
    }

    /* ================
     * GettableResource
     * ================ */
    
    @Override
    public void sendContent(OutputStream out, Range range, Map<String, String> params,
            String contentType) throws IOException, NotAuthorizedException, BadRequestException {

        try {
            XMLOutputFactory xf = XMLOutputFactory.newInstance();
            xf.setProperty("javax.xml.stream.isRepairingNamespaces", Boolean.TRUE);

            XMLStreamWriter writer = xf.createXMLStreamWriter(out);
            writer.setDefaultNamespace("http://exist.sourceforge.net/NS/exist");

            // Begin document
            writer.writeStartDocument();

            // Root element
            writer.writeStartElement("exist", "result", "http://exist.sourceforge.net/NS/exist");

            // Root collection
            writer.writeStartElement("exist", "collection", "http://exist.sourceforge.net/NS/exist");
            writer.writeAttribute("name", resourceXmldbUri.lastSegment().toString());
            writer.writeAttribute("created", getXmlDateTime(existCollection.getCreationTime()));
            writer.writeAttribute("owner", existCollection.getOwnerUser());
            writer.writeAttribute("group", existCollection.getOwnerGroup());
            writer.writeAttribute("permissions", "" + existCollection.getPermissions().toString());


            // Iterate over all collections in collection
            for (MiltonCollection collection : getCollectionResources()) {
                collection.writeXML(writer);
            }

            // Iterate over all documents in collection
            for (MiltonDocument document : getDocumentResources()) {
                document.writeXML(writer);
            }

            // Finish top collection
            writer.writeEndElement();

            // Finish root element
            writer.writeEndElement();

            // Finish document
            writer.writeEndDocument();

        } catch (XMLStreamException ex) {
            LOG.error(ex);
            throw new IOException(ex.getMessage());
        }
    }

    @Override
    public Long getMaxAgeSeconds(Auth auth) {
        return null;
    }

    @Override
    public String getContentType(String accepts) {
        return "application/xml";
    }

    @Override
    public Long getContentLength() {
        return null;
    }

    /* ================
     * StAX serializer
     * ================ */

    private void writeXML(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("exist", "collection", "http://exist.sourceforge.net/NS/exist");
        writer.writeAttribute("name", resourceXmldbUri.lastSegment().toString());
        writer.writeAttribute("created", getXmlDateTime(existCollection.getCreationTime()));
        writer.writeAttribute("owner", existCollection.getOwnerUser());
        writer.writeAttribute("group", existCollection.getOwnerGroup());
        writer.writeAttribute("permissions", existCollection.getPermissions().toString());
        writer.writeEndElement();
    }

}
