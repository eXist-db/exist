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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.internal.aider.ACEAider;
import org.exist.util.Compressor;
import org.exist.util.EXistInputSource;
import org.xml.sax.InputSource;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.XMLResource;

/**
 * A remote implementation of the Collection interface. This implementation
 * communicates with the server through the XMLRPC protocol.
 *
 * @author wolf Updated Andy Foster - Updated code to allow child collection
 * cache to resync with the remote collection.
 */
public class RemoteCollection extends AbstractRemote implements EXistCollection {

    protected final static Logger LOG = LogManager.getLogger(RemoteCollection.class);

    // Max size of a resource to be send to the server.
    // If the resource exceeds this limit, the data is split into
    // junks and uploaded to the server via the update() call
    private static final int MAX_CHUNK_LENGTH = 512 * 1024; //512KB
    private static final int MAX_UPLOAD_CHUNK = 10 * 1024 * 1024; //10 MB

    private final XmldbURI path;
    private final XmlRpcClient rpcClient;
    private Properties properties = null;

    public static RemoteCollection instance(final XmlRpcClient xmlRpcClient, final XmldbURI path) throws XMLDBException {
        return instance(xmlRpcClient, null, path);
    }

    public static RemoteCollection instance(final XmlRpcClient xmlRpcClient, final RemoteCollection parent, final XmldbURI path) throws XMLDBException {
        final List<String> params = new ArrayList<>(1);
        params.add(path.toString());

        try {
            //check we can open the collection i.e. that we have permission!
            final boolean existsAndCanOpen = (Boolean) xmlRpcClient.execute("existsAndCanOpenCollection", params);

            if (existsAndCanOpen) {
                return new RemoteCollection(xmlRpcClient, parent, path);
            } else {
                return null;
            }
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre);
        }
    }

    private RemoteCollection(final XmlRpcClient client, final RemoteCollection parent, final XmldbURI path) throws XMLDBException {
        super(parent);
        this.path = path.toCollectionPathURI();
        this.rpcClient = client;
    }

    protected XmlRpcClient getClient() {
        return rpcClient;
    }

    @Override
    public void close() throws XMLDBException {
        try {
            rpcClient.execute("sync", Collections.EMPTY_LIST);
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, "failed to close collection", e);
        }
    }

    @Override
    public String createId() throws XMLDBException {
        final List<String> params = new ArrayList<>();
        params.add(getPath());
        try {
            return (String) rpcClient.execute("createResourceId", params);
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, "Failed to close collection", e);
        }
    }

    @Override
    public Resource createResource(final String id, final String type) throws XMLDBException {
        try {
            final XmldbURI newId = (id == null) ? XmldbURI.xmldbUriFor(createId()) : XmldbURI.xmldbUriFor(id);
            if (XMLResource.RESOURCE_TYPE.equals(type)) {
                return new RemoteXMLResource(this, -1, -1, newId, Optional.empty());
            } else if (BinaryResource.RESOURCE_TYPE.equals(type)) {
                return new RemoteBinaryResource(this, newId);
            } else {
                throw new XMLDBException(ErrorCodes.UNKNOWN_RESOURCE_TYPE, "Unknown resource type: " + type);
            }
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }
    }

    @Override
    public Collection getChildCollection(final String name) throws XMLDBException {
        try {
            return getChildCollection(XmldbURI.xmldbUriFor(name));
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }
    }

    public Collection getChildCollection(final XmldbURI name) throws XMLDBException {
        // AF: get the child collection refreshing cache from server if not found
        return getChildCollection(name, true);
    }

    // AF: NEW METHOD
    protected Collection getChildCollection(final XmldbURI name, final boolean refreshCacheIfNotFound) throws XMLDBException {
        return instance(rpcClient, this, name.numSegments() > 1 ? name : getPathURI().append(name));
    }

    @Override
    public int getChildCollectionCount() throws XMLDBException {
        return listChildCollections().length;
    }

    @Override
    public String getName() throws XMLDBException {
        return path.toString();
    }

    @Override
    public Collection getParentCollection() throws XMLDBException {
        if (collection == null && !path.equals(XmldbURI.ROOT_COLLECTION_URI)) {
            final XmldbURI parentUri = path.removeLastSegment();
            return new RemoteCollection(rpcClient, null, parentUri);
        }
        return collection;
    }

    public String getPath() throws XMLDBException {
        return getPathURI().toString();
    }

    @Override
    public XmldbURI getPathURI() {
        if (collection == null) {
            return XmldbURI.ROOT_COLLECTION_URI;
        }
        return path;
    }

    @Override
    public String getProperty(final String property) throws XMLDBException {
        if (properties == null) {
            return null;
        }
        return (String) properties.get(property);
    }

    public Properties getProperties() {
        if (properties == null) {
            properties = new Properties();
        }
        return properties;
    }

    public void setProperties(final Properties properties) {
        this.properties = properties;
    }

    @Override
    public int getResourceCount() throws XMLDBException {
        final List<String> params = new ArrayList<>(1);
        params.add(getPath());
        try {
            return (Integer) rpcClient.execute("getResourceCount", params);
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, "failed to close collection", e);
        }
    }

    @Override
    public Service getService(final String name, final String version) throws XMLDBException {
        final Service service;
        switch (name) {
            case "XPathQueryService":
            case "XQueryService":
                service = new RemoteXPathQueryService(this);
                break;

            case "CollectionManagementService":
            case "CollectionManager":
                service = new RemoteCollectionManagementService(this, rpcClient);
                break;

            case "UserManagementService":
                service = new RemoteUserManagementService(this);
                break;

            case "DatabaseInstanceManager":
                service = new RemoteDatabaseInstanceManager(rpcClient);
                break;

            case "IndexQueryService":
                service = new RemoteIndexQueryService(rpcClient, this);
                break;

            case "XUpdateQueryService":
                service = new RemoteXUpdateQueryService(this);
                break;

            default:
                throw new XMLDBException(ErrorCodes.NO_SUCH_SERVICE);
        }
        return service;
    }

    @Override
    public Service[] getServices() throws XMLDBException {
        return new Service[]{
            new RemoteXPathQueryService(this),
            new RemoteCollectionManagementService(this, rpcClient),
            new RemoteUserManagementService(this),
            new RemoteDatabaseInstanceManager(rpcClient),
            new RemoteIndexQueryService(rpcClient, this),
            new RemoteXUpdateQueryService(this)
        };
    }

    protected boolean hasChildCollection(final String name) throws XMLDBException {
        for (final String child : listChildCollections()) {
            if (child.equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isOpen() throws XMLDBException {
        return true;
    }

    @Override
    public String[] listChildCollections() throws XMLDBException {
        final List<String> params = new ArrayList<>(1);
        params.add(getPath());
        try {
            final Object[] r = (Object[]) rpcClient.execute("getCollectionListing", params);
            final String[] collections = new String[r.length];
            System.arraycopy(r, 0, collections, 0, r.length);
            return collections;
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre);
        }
    }

    @Override
    public String[] getChildCollections() throws XMLDBException {
        return listChildCollections();
    }

    @Override
    public String[] listResources() throws XMLDBException {
        final List<String> params = new ArrayList<>(1);
        params.add(getPath());
        try {
            final Object[] r = (Object[]) rpcClient.execute("getDocumentListing", params);
            final String[] resources = new String[r.length];
            System.arraycopy(r, 0, resources, 0, r.length);
            return resources;
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre);
        }
    }

    @Override
    public String[] getResources() throws XMLDBException {
        return listResources();
    }

    public Permission getSubCollectionPermissions(final String name) throws PermissionDeniedException, XMLDBException {
        final List<String> params = new ArrayList<>(2);
        params.add(getPath());
        params.add(name);
        try {
            final Map result = (Map) rpcClient.execute("getSubCollectionPermissions", params);

            final String owner = (String) result.get("owner");
            final String group = (String) result.get("group");
            final int mode = (Integer) result.get("permissions");
            final Stream<ACEAider> aces = extractAces(result.get("acl"));

            return getPermission(owner, group, mode, aces);
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre);
        }
    }

    public Permission getSubResourcePermissions(final String name) throws PermissionDeniedException, XMLDBException {
        final List<String> params = new ArrayList<>(2);
        params.add(getPath());
        params.add(name);
        try {
            final Map result = (Map) rpcClient.execute("getSubResourcePermissions", params);

            final String owner = (String) result.get("owner");
            final String group = (String) result.get("group");
            final int mode = (Integer) result.get("permissions");
            final Stream<ACEAider> aces = extractAces(result.get("acl"));

            return getPermission(owner, group, mode, aces);
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre);
        }
    }

    public Long getSubCollectionCreationTime(final String name) throws XMLDBException {

        final List params = new ArrayList(2);
        params.add(getPath());
        params.add(name);

        try {
            return (Long) rpcClient.execute("getSubCollectionCreationTime", params);
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre);
        }
    }

    @Override
    public Resource getResource(final String name) throws XMLDBException {
        final List<String> params = new ArrayList<>(1);
        XmldbURI docUri;
        try {
            docUri = XmldbURI.xmldbUriFor(name);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }
        params.add(getPathURI().append(docUri).toString());
        final Map hash;
        try {
            hash = (Map) rpcClient.execute("describeResource", params);
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre);
        }
        final String docName = (String) hash.get("name");
        if (docName == null) {
            return null; // resource does not exist!
        }
        try {
            docUri = XmldbURI.xmldbUriFor(docName).lastSegment();
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }
        final String owner = (String) hash.get("owner");
        final String group = (String) hash.get("group");
        final int mode = (Integer) hash.get("permissions");
        final Stream<ACEAider> aces = extractAces(hash.get("acl"));

        final Permission perm;
        try {
            perm = getPermission(owner, group, mode, aces);
        } catch (final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "Unable to retrieve permissions for resource '" + name + "': " + pde.getMessage(), pde);
        }
        final String type = (String) hash.get("type");
        long contentLen = 0;
        if (hash.containsKey("content-length-64bit")) {
            final Object o = hash.get("content-length-64bit");
            if (o instanceof Long) {
                contentLen = (Long) o;
            } else {
                contentLen = Long.parseLong((String) o);
            }
        } else if (hash.containsKey("content-length")) {
            contentLen = (Integer) hash.get("content-length");
        }

        final AbstractRemoteResource r;
        if (type == null || "XMLResource".equals(type)) {
            r = new RemoteXMLResource(this, -1, -1, docUri, Optional.empty());
        } else {
            r = new RemoteBinaryResource(this, docUri);
        }
        r.setPermissions(perm);
        r.setContentLength(contentLen);
        r.dateCreated = (Date) hash.get("created");
        r.dateModified = (Date) hash.get("modified");
        if (hash.containsKey("mime-type")) {
            r.setMimeType((String) hash.get("mime-type"));
        }
        return r;
    }

    public void registerService(final Service serv) throws XMLDBException {
        throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED);
    }

    @Override
    public void removeResource(final Resource res) throws XMLDBException {
        final List<String> params = new ArrayList<>(1);
        try {
            params.add(getPathURI().append(XmldbURI.xmldbUriFor(res.getId())).toString());
            rpcClient.execute("remove", params);
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
        }
    }

    @Override
    public Date getCreationTime() throws XMLDBException {
        final List<String> params = new ArrayList<>(1);
        params.add(getPath());
        try {
            return (Date) rpcClient.execute("getCreationDate", params);
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public void setProperty(final String property, final String value) throws XMLDBException {
        if (properties == null) {
            properties = new Properties();
        }
        properties.setProperty(property, value);
    }

    @Override
    public void storeResource(final Resource res) throws XMLDBException {
        storeResource(res, null, null);
    }

    @Override
    public void storeResource(final Resource res, final Date a, final Date b) throws XMLDBException {
        final Object content = (res instanceof ExtendedResource) ? ((ExtendedResource) res).getExtendedContent() : res.getContent();
        if (content instanceof File || content instanceof InputSource) {
            long fileLength = -1;
            if (content instanceof File) {
                final File file = (File) content;
                if (!file.canRead()) {
                    throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Failed to read resource from file " + file.getAbsolutePath());
                }
                fileLength = file.length();
            } else if (content instanceof EXistInputSource) {
                fileLength = ((EXistInputSource) content).getByteStreamLength();
            }
            if (res instanceof AbstractRemoteResource) {
                ((AbstractRemoteResource) res).dateCreated = a;
                ((AbstractRemoteResource) res).dateModified = b;
            }
            if (!BinaryResource.RESOURCE_TYPE.equals(res.getResourceType()) && fileLength != -1
                    && fileLength < MAX_CHUNK_LENGTH) {
                store((RemoteXMLResource) res);
            } else {
                uploadAndStore(res);
            }
        } else {
            ((AbstractRemoteResource) res).dateCreated = a;
            ((AbstractRemoteResource) res).dateModified = b;
            if (XMLResource.RESOURCE_TYPE.equals(res.getResourceType())) {
                store((RemoteXMLResource) res);
            } else {
                store((RemoteBinaryResource) res);
            }
        }
    }

    private void store(final RemoteXMLResource res) throws XMLDBException {
        final byte[] data = res.getData();
        final List<Object> params = new ArrayList<>();
        params.add(data);
        try {
            params.add(getPathURI().append(XmldbURI.xmldbUriFor(res.getId())).toString());
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }
        params.add(1);
        if (res.getCreationTime() != null) {
            params.add(res.getCreationTime());
            params.add(res.getLastModificationTime());
        }
        try {
            rpcClient.execute("parse", params);
        } catch (final XmlRpcException xre) {
            throw new XMLDBException(
                    ErrorCodes.INVALID_RESOURCE,
                    xre == null ? "Unknown error" : xre.getMessage(),
                    xre);
        }
    }

    private void store(final RemoteBinaryResource res) throws XMLDBException {
        final byte[] data = (byte[]) res.getContent();
        final List<Object> params = new ArrayList<>();
        params.add(data);
        try {
            params.add(getPathURI().append(XmldbURI.xmldbUriFor(res.getId())).toString());
        } catch (final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }
        params.add(res.getMimeType());
        params.add(Boolean.TRUE);
        if (res.getCreationTime() != null) {
            params.add(res.getCreationTime());
            params.add(res.getLastModificationTime());
        }
        try {
            rpcClient.execute("storeBinary", params);
        } catch (final XmlRpcException xre) {
            /* the error code previously was INVALID_RESOURCE, but this was also thrown
             * in case of insufficient permissions. As you cannot tell here any more what the
             * error really was, use UNKNOWN_ERROR. 
             * The reason is in XmlRpcResponseProcessor#processException
             * which will only pass on the error message.
             */
            throw new XMLDBException(
                    ErrorCodes.UNKNOWN_ERROR,
                    xre == null ? "unknown error" : xre.getMessage(), xre
            );
        }
    }

    private void uploadAndStore(final Resource res) throws XMLDBException {
        InputStream is = null;
        String descString = "<unknown>";
        try {
            if (res instanceof RemoteBinaryResource) {
                is = ((RemoteBinaryResource) res).getStreamContent();
                descString = ((RemoteBinaryResource) res).getStreamSymbolicPath();
            } else {
                final Object content = res.getContent();
                if (content instanceof File) {
                    final File file = (File) content;
                    try {
                        is = new BufferedInputStream(new FileInputStream(file));
                    } catch (final FileNotFoundException e) {
                        throw new XMLDBException(
                                ErrorCodes.INVALID_RESOURCE,
                                "could not read resource from file " + file.getAbsolutePath(),
                                e);
                    }
                } else if (content instanceof InputSource) {
                    is = ((InputSource) content).getByteStream();
                    if (content instanceof EXistInputSource) {
                        descString = ((EXistInputSource) content).getSymbolicPath();
                    }
                }
            }

            final byte[] chunk = new byte[MAX_UPLOAD_CHUNK];
            try {
                int len;
                String fileName = null;
                List<Object> params;
                byte[] compressed;
                while ((len = is.read(chunk)) > -1) {
                    compressed = Compressor.compress(chunk, len);
                    params = new ArrayList<>();
                    if (fileName != null) {
                        params.add(fileName);
                    }
                    params.add(compressed);
                    params.add(len);
                    fileName = (String) rpcClient.execute("uploadCompressed", params);
                }
                // Zero length stream? Let's get a fileName!
                if (fileName == null) {
                    compressed = Compressor.compress(new byte[0], 0);
                    params = new ArrayList<>();
                    params.add(compressed);
                    params.add(0);
                    fileName = (String) rpcClient.execute("uploadCompressed", params);
                }
                params = new ArrayList<>();
                final List<Object> paramsEx = new ArrayList<>();
                params.add(fileName);
                paramsEx.add(fileName);
                try {
                    final String resURI = getPathURI().append(XmldbURI.xmldbUriFor(res.getId())).toString();
                    params.add(resURI);
                    paramsEx.add(resURI);
                } catch (final URISyntaxException e) {
                    throw new XMLDBException(ErrorCodes.INVALID_URI, e);
                }
                params.add(Boolean.TRUE);
                paramsEx.add(Boolean.TRUE);
                if (res instanceof EXistResource) {
                    final EXistResource rxres = (EXistResource) res;
                    params.add(rxres.getMimeType());
                    paramsEx.add(rxres.getMimeType());
                    // This one is only for the new style!!!!
                    paramsEx.add((BinaryResource.RESOURCE_TYPE.equals(res.getResourceType()))
                            ? Boolean.FALSE : Boolean.TRUE);
                    if (rxres.getCreationTime() != null) {
                        params.add(rxres.getCreationTime());
                        paramsEx.add(rxres.getCreationTime());
                        params.add(rxres.getLastModificationTime());
                        paramsEx.add(rxres.getLastModificationTime());
                    }
                }
                try {
                    rpcClient.execute("parseLocalExt", paramsEx);
                } catch (final XmlRpcException e) {
                    // Identifying old versions
                    final String excMsg = e.getMessage();
                    if (excMsg.contains("No such handler") || excMsg.contains("No method matching")) {
                        rpcClient.execute("parseLocal", params);
                    } else {
                        throw e;
                    }
                }
            } catch (final IOException e) {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "failed to read resource from " + descString, e);
            } catch (final XmlRpcException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "networking error", e);
            }
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch (final IOException ioe) {
                    LOG.warn(ioe.getMessage(), ioe);
                }
            }
        }
    }

    @Override
    public boolean isRemoteCollection() throws XMLDBException {
        return true;
    }

    @Override
    public void setTriggersEnabled(final boolean triggersEnabled) throws XMLDBException {
        final List<String> params = new ArrayList<>();
        params.add(this.getPath());
        params.add(Boolean.toString(triggersEnabled));
        try {
            rpcClient.execute("setTriggersEnabled", params);
        } catch (final XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "networking error", e);
        }
    }
}
