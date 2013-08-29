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
import java.io.File;
import java.io.FileInputStream;

import java.net.URISyntaxException;
import java.util.Properties;
import javax.xml.transform.OutputKeys;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.xmldb.XmldbURI;

/**
 * Class for constructing Milton WebDAV framework resource objects  .
 *
 * @author Dannes Wessels (dizzzz_at_exist-db.org)
 */
public class ExistResourceFactory implements ResourceFactory {

    private final static Logger LOG = Logger.getLogger(ExistResourceFactory.class);
    private BrokerPool brokerPool = null;
    
    //	default output properties for the XML serialization
    public final static Properties DEFAULT_WEBDAV_OPTIONS = new Properties();
    
    /** XML serialization options */
    private Properties webDavOptions = new Properties(); 

    /**
     * Default serialization options
     */
    static {
        DEFAULT_WEBDAV_OPTIONS.setProperty(OutputKeys.INDENT, "yes");
        DEFAULT_WEBDAV_OPTIONS.setProperty(OutputKeys.ENCODING, "UTF-8");
        DEFAULT_WEBDAV_OPTIONS.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        DEFAULT_WEBDAV_OPTIONS.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
        DEFAULT_WEBDAV_OPTIONS.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
    }

    private enum ResourceType {
        DOCUMENT, COLLECTION, IGNORABLE, NOT_EXISTING
    };

    /**
     * Default constructor. Get access to instance of exist-db broker pool.
     */
    public ExistResourceFactory() {

        try {
            brokerPool = BrokerPool.getInstance(BrokerPool.DEFAULT_INSTANCE_NAME);

        } catch (EXistException e) {
            LOG.error("Unable to initialize WebDAV interface.", e);
        }
        
        // Set default values
        webDavOptions.putAll(DEFAULT_WEBDAV_OPTIONS);
        
        // load specific options
        try {
            // Find right file
            File eXistHome = brokerPool.getConfiguration().getExistHome();
            File config = new File(eXistHome, "webdav.properties");
            
            // Read from file if existent
            if(config.canRead()){
                LOG.info(String.format("Read WebDAV configuration from %s", config.getCanonicalPath()));
                FileInputStream fis = new FileInputStream(config);
                webDavOptions.load(fis);
                fis.close();
                
            } else {
                LOG.info("Using WebDAV default serialization options.");
            }
            
        } catch (Throwable ex) {
            LOG.error(ex.getMessage());
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
        XmldbURI xmldbUri = null;
        try {
            // Strip preceding path, all up to /db
            path = path.substring(path.indexOf("/db"));

            // Strip last slash if available
            if (path.endsWith("/")) {
                path = path.substring(0, path.lastIndexOf("/"));
            }

            if(LOG.isDebugEnabled()) {
                LOG.debug(String.format("host='%s' path='%s'", host, path));
            }

            // Create uri inside database
            xmldbUri = XmldbURI.xmldbUriFor(path);

        } catch (URISyntaxException e) {
            LOG.error(String.format("Unable to convert path '%s'into a XmldbURI representation.", path));
            return null;
        }

        // Return appropriate resource
        switch (getResourceType(brokerPool, xmldbUri)) {
            case DOCUMENT:
                MiltonDocument doc = new MiltonDocument(host, xmldbUri, brokerPool);
                doc.setConfiguration(webDavOptions);
                return doc;

            case COLLECTION:
                return new MiltonCollection(host, xmldbUri, brokerPool);

            case IGNORABLE:
                if (LOG.isDebugEnabled()) {
                    LOG.debug("ignoring file");
                }
                return null;

            case NOT_EXISTING:
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format("Resource does not exist: '%s'", xmldbUri));
                }
                return null;

            default:
                LOG.error(String.format("Unkown resource type for %s", xmldbUri));
                return null;
        }
    }

    /*
     * Returns the resource type indicated by the path: either COLLECTION, DOCUMENT or NOT_EXISTING.
     */
    private ResourceType getResourceType(BrokerPool brokerPool, XmldbURI xmldbUri) {

        DBBroker broker = null;
        Collection collection = null;
        DocumentImpl document = null;
        ResourceType type = ResourceType.NOT_EXISTING;
        
        // MacOsX finder specific files
        String documentSeqment = xmldbUri.lastSegment().toString();
        if(documentSeqment.startsWith("._") || documentSeqment.equals(".DS_Store")){
            //LOG.debug(String.format("Ignoring MacOSX file '%s'", xmldbUri.lastSegment().toString()));
            //return ResourceType.IGNORABLE;
        }
        
        // Documents that start with a dot 
        if(documentSeqment.startsWith(".")){
            //LOG.debug(String.format("Ignoring '.' file '%s'", xmldbUri.lastSegment().toString()));
            //return ResourceType.IGNORABLE;
        }

        try {
            if(LOG.isDebugEnabled()) {
                LOG.debug(String.format("Path: %s", xmldbUri.toString()));
            }
            
            // Try to read as system user. Note that the actual user is not know
            // yet. In MiltonResource the actual authentication and authorization
            // is performed.
            broker = brokerPool.get(brokerPool.getSecurityManager().getSystemSubject());

            
            // First check if resource is a collection
            collection = broker.openCollection(xmldbUri, Lock.READ_LOCK);
            if (collection != null) {
                type = ResourceType.COLLECTION;
                collection.release(Lock.READ_LOCK);
                collection = null;

            } else {
                // If it is not a collection, check if it is a document
                document = broker.getXMLResource(xmldbUri, Lock.READ_LOCK);

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
            LOG.error(String.format("Error determining nature of resource %s", xmldbUri.toString()), ex);
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
            if(broker != null) {
                brokerPool.release(broker);
            }
        }

        if(LOG.isDebugEnabled()) {
            LOG.debug(String.format("Resource type=%s", type.toString()));
        }
        
        return type;
    }
    
}
