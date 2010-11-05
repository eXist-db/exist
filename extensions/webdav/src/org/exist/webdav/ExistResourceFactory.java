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


import org.apache.log4j.Logger;

import com.bradmcevoy.http.Resource;
import com.bradmcevoy.http.ResourceFactory;

import java.net.URISyntaxException;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;

/**
 * Class for constructing Milton WebDAV framework resource objects  .
 *
 * @author Dannes Wessels (dizzzz_at_exist-db.org)
 */
public class ExistResourceFactory implements ResourceFactory {

    private final static Logger LOG = Logger.getLogger(ExistResourceFactory.class);
    private BrokerPool brokerPool = null;

    private enum ResourceType {
        DOCUMENT, COLLECTION, IGNORABLE, NOT_EXISTING
    };

    /**
     * Default constructor. Get access to instance of exist-db brokerpool.
     */
    public ExistResourceFactory() {

        try {
            brokerPool = BrokerPool.getInstance(BrokerPool.DEFAULT_INSTANCE_NAME);

        } catch (EXistException e) {
            LOG.error("Unable to initialize WebDAV interface.", e);
        }

    }

    /*
     * Construct Resource for path. A Document or Collection resource is returned, NULL if type
     * could not be detected.
     */
    @Override
    public Resource getResource(String host, String path) {

        // DWES: work around if no /db is available return nothing.
        if (!path.contains("/db")) {
            LOG.error("path should at least contain /db");
            return null;
        }

        // Construct path as eXist-db XmldbURI
        XmldbURI uri = null;
        try {
            // Strip preceding path, all up to /db
            path = path.substring(path.indexOf("/db"));

            // Strip last slash if available
            if (path.endsWith("/")) {
                path = path.substring(0, path.lastIndexOf("/"));
            }

            LOG.debug("host='" + host + "' path='" + path + "'");
          
            // Create uri inside database
            uri = XmldbURI.xmldbUriFor(path);

            // MacOsX finder specific files
            String documentSeqment = uri.lastSegment().toString();
            if(documentSeqment.startsWith("._") || documentSeqment.equals(".DS_Store")){
                LOG.debug("skipping MacOsX file '"+uri.lastSegment().toString()+"'");
            }

        } catch (URISyntaxException e) {
            LOG.error("Unable to convert path '" + path + "'into a XmldbURI representation.");
            return null;
        }

        // Return appropriate resource
        switch (getResourceType(brokerPool, uri)) {
            case DOCUMENT:
                return new MiltonDocument(host, uri, brokerPool);

            case COLLECTION:
                return new MiltonCollection(host, uri, brokerPool);

            case IGNORABLE:
                LOG.debug("ignoring file");
                return null;

            default:
                LOG.error("Unkown resource type for " + uri);
                return null;
        }
    }

    /*
     * Returns the resource type indicated by the path: either COLLECTION, DOCUMENT or NOT_EXISTING.
     */
    private ResourceType getResourceType(BrokerPool brokerPool, XmldbURI uri) {

        DBBroker broker = null;
        Collection collection = null;
        DocumentImpl document = null;
        ResourceType type = ResourceType.NOT_EXISTING;

        try {
            LOG.debug("Path: " + uri.toString());
            
            // Try to read as system user. Note that the actual user is not know
            // yet. In MiltonResource the actual authentication and authorization
            // is performed.
            broker = brokerPool.get(brokerPool.getSecurityManager().SYSTEM_USER);

            
            // First check if resource is a collection
            collection = broker.openCollection(uri, Lock.READ_LOCK);
            if (collection != null) {
                type = ResourceType.COLLECTION;
                collection.release(Lock.READ_LOCK);
                collection = null;

            } else {
                // If it is not a collection, check if it is a document
                document = broker.getXMLResource(uri, Lock.READ_LOCK);

                if (document != null) {
                    // Document is found
                    type = ResourceType.DOCUMENT;
                    document.getUpdateLock().release(Lock.READ_LOCK);
                    document = null;

                } else {
                    // No document and no collection.
                    type = ResourceType.NOT_EXISTING;
                }
            }
           

        } catch (Exception ex) {
            LOG.error("Error determining nature of resource " + uri.toString(), ex);
            type = ResourceType.NOT_EXISTING;

        } finally {

            // Clean-up, just in case
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }

            // Clean-up, just in case
            if (document != null) {
                document.getUpdateLock().release(Lock.READ_LOCK);
            }

            // Return broker to pool
            brokerPool.release(broker);
        }

        LOG.debug("Resource type=" + type.toString());
        return type;
    }

}
