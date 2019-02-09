/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2005-2011 The eXist-db Project
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
 *  $Id: Restore.java 15109 2011-08-09 13:03:09Z deliriumsky $
 */
package org.exist.backup.restore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.backup.BackupDescriptor;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.dom.persistent.DocumentTypeImpl;
import org.exist.security.ACLPermission.ACE_ACCESS_TYPE;
import org.exist.security.ACLPermission.ACE_TARGET;
import org.exist.security.SecurityManager;
import org.exist.util.EXistInputSource;
import org.exist.util.ExistSAXParserFactory;
import org.exist.xmldb.EXistCollection;
import org.exist.xmldb.EXistCollectionManagementService;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.DateTimeValue;
import org.w3c.dom.DocumentType;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;


/**
 * Handler for parsing __contents.xml__ files during
 * restoration of a db backup
 *
 * @author  Adam Retter <adam@exist-db.org>
 */
public class RestoreHandler extends DefaultHandler {
    
    private final static Logger LOG = LogManager.getLogger(RestoreHandler.class);
    private final static SAXParserFactory saxFactory = ExistSAXParserFactory.getSAXParserFactory();
    static {
        saxFactory.setNamespaceAware(true);
        saxFactory.setValidating(false);
    }
    private static final int STRICT_URI_VERSION = 1;
    private static final int BLOB_STORE_VERSION = 2;
    
    private final RestoreListener listener;
    private final String dbBaseUri;
    private final String dbUsername;
    private final String dbPassword;
    private final BackupDescriptor descriptor;
    
    //handler state
    private int version = 0;
    private boolean deduplicateBlobs = false;
    private EXistCollection currentCollection;
    private final Deque<DeferredPermission> deferredPermissions = new ArrayDeque<>();
    
    
    public RestoreHandler(final RestoreListener listener, final String dbBaseUri, final String dbUsername, final String dbPassword, final BackupDescriptor descriptor) {
        this.listener = listener;
        this.dbBaseUri = dbBaseUri;
        this.dbUsername = dbUsername;
        this.dbPassword = dbPassword;
        this.descriptor = descriptor;
    }

    @Override
    public void startDocument() throws SAXException {
        listener.setCurrentBackup(descriptor.getSymbolicPath());
    }
    
    /**
     * @see  org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(final String namespaceURI, final String localName, final String qName, final Attributes atts) throws SAXException {

        //only process entries in the exist namespace
        if(namespaceURI != null && !namespaceURI.equals(Namespaces.EXIST_NS)) {
            return;
        }

        if("collection".equals(localName) || "resource".equals(localName)) {
            
            final DeferredPermission df;
            
            if("collection".equals(localName)) {
                df = restoreCollectionEntry(atts);
            } else {
                df = restoreResourceEntry(atts);
            }
            
            deferredPermissions.push(df);
            
        } else if("subcollection".equals(localName)) {
            restoreSubCollectionEntry(atts);
        } else if("deleted".equals(localName)) {
            restoreDeletedEntry(atts);
        } else if("ace".equals(localName)) {
            addACEToDeferredPermissions(atts);
        }
    }
    
    @Override
    public void endElement(final String namespaceURI, final String localName, final String qName) throws SAXException {

        if(namespaceURI.equals(Namespaces.EXIST_NS) && ("collection".equals(localName) || "resource".equals(localName))) {
            setDeferredPermissions();
        }

        super.endElement(namespaceURI, localName, qName);
    }

    private String getAttr(final Attributes atts, final String name, final String fallback) {
        final String value = atts.getValue(name);
        if(value == null) {
            return fallback;
        }
        return value;
    }

    private DeferredPermission restoreCollectionEntry(final Attributes atts) throws SAXException {
        
        final String name = atts.getValue("name");
        
        if(name == null) {
            throw new SAXException("Collection requires a name attribute");
        }
        
        final String owner = getAttr(atts, "owner", SecurityManager.SYSTEM);
        final String group = getAttr(atts, "group", SecurityManager.DBA_GROUP);
        final String mode = getAttr(atts, "mode", "644");
        final String created = atts.getValue("created");
        final String strVersion = atts.getValue("version");

        if(strVersion != null) {
            try {
                this.version = Integer.parseInt(strVersion);
            } catch(final NumberFormatException nfe) {
                final String msg = "Could not parse version number for Collection '" + name + "', defaulting to version 0";
                listener.warn(msg);
                LOG.warn(msg);
                
                this.version = 0;
            }
        }
        
        try {
            listener.createCollection(name);
            final XmldbURI collUri;

            if(version >= STRICT_URI_VERSION) {
                collUri = XmldbURI.create(name);
            } else {
                try {
                    collUri = URIUtils.encodeXmldbUriFor(name);
                } catch(final URISyntaxException e) {
                    listener.warn("Could not parse document name into a URI: " + e.getMessage());
                    return new SkippedEntryDeferredPermission();
                }
            }

            if (version >= BLOB_STORE_VERSION) {
                this.deduplicateBlobs = Boolean.valueOf(atts.getValue("deduplicate-blobs"));
            } else {
                this.deduplicateBlobs = false;
            }

            currentCollection = mkcol(collUri, getDateFromXSDateTimeStringForItem(created, name));

            listener.setCurrentCollection(name);
            
            if(currentCollection == null) {
                throw new SAXException("Collection not found: " + collUri);
            }

            final DeferredPermission deferredPermission;
            if(name.startsWith(XmldbURI.SYSTEM_COLLECTION)) {
                //prevents restore of a backup from changing System collection ownership
                deferredPermission = new CollectionDeferredPermission(listener, currentCollection, SecurityManager.SYSTEM, SecurityManager.DBA_GROUP, Integer.parseInt(mode, 8));
            } else {
                deferredPermission = new CollectionDeferredPermission(listener, currentCollection, owner, group, Integer.parseInt(mode, 8));
            }
            return deferredPermission;
            
        } catch(final Exception e) {
            final String msg = "An unrecoverable error occurred while restoring\ncollection '" + name + "': "  + e.getMessage() + ". Aborting restore!";
            LOG.error(msg, e);
            listener.warn(msg);
            throw new SAXException(msg, e);
        }
    }

    private void restoreSubCollectionEntry(final Attributes atts) throws SAXException {
        
        final String name;
        if(atts.getValue("filename") != null) {
            name = atts.getValue("filename");
        } else {
            name = atts.getValue("name");
        }
        
        //exclude /db/system collection and sub-collections, as these have already been restored
        try {
            final String currentCollectionName = currentCollection.getName();
            if(("/db".equals(currentCollectionName) && "system".equals(name)) || ("/db/system".equals(currentCollectionName) && "security".equals(name))) {
                return;
            }
        } catch(final XMLDBException xe) {
            throw new RuntimeException(xe.getMessage(), xe);
        }
        
        //parse the sub-collection descriptor and restore
        final BackupDescriptor subDescriptor = descriptor.getChildBackupDescriptor(name);
        if(subDescriptor != null) {
            
            final SAXParser sax;
            try { 
                sax = saxFactory.newSAXParser();
            
                final XMLReader reader = sax.getXMLReader();

                final EXistInputSource is = subDescriptor.getInputSource();
                is.setEncoding( "UTF-8" );

                final RestoreHandler handler = new RestoreHandler(listener, dbBaseUri, dbUsername, dbPassword, subDescriptor);

                reader.setContentHandler(handler);
                reader.parse(is);
            } catch(final ParserConfigurationException pce) {
                listener.error("Could not initalise SAXParser for processing sub-collection: " + descriptor.getSymbolicPath(name, false));
            } catch(final IOException ioe) {
                listener.error("Could not read sub-collection for processing: " + ioe.getMessage());
            } catch(final SAXException se) {
                listener.error("SAX exception while reading sub-collection " + subDescriptor.getSymbolicPath() + " for processing: " + se.getMessage());
            }
        } else {
            listener.error("Collection " + descriptor.getSymbolicPath(name, false) + " does not exist or is not readable.");
        }
    }
    
    private DeferredPermission restoreResourceEntry(final Attributes atts) throws SAXException {
        
        final String skip = atts.getValue( "skip" );

        //dont process entries which should be skipped
        if(skip != null && !"no".equals(skip)) {
            return new SkippedEntryDeferredPermission();
        }
        
        final String name = atts.getValue("name");
        if(name == null) {
            throw new SAXException("Resource requires a name attribute");
        }
        
        //triggers should NOT be disabled, because it do used by the system tasks (like security manager)
        //UNDERSTAND: split triggers: user & system
        //current.setTriggersEnabled(false);
        /*
        try {
            if(currentCollection.getName().equals("/db/system") && name.equals("users.xml") && currentCollection.getChildCollection("security") != null) {
                listener.warn("Skipped resource '" + name + "'\nfrom file '" + descriptor.getSymbolicPath(name, false) + "'.");
                return new SkippedEntryDeferredPermission();
            }
        } catch(XMLDBException xe) {
            LOG.error(xe.getMessage(), xe);
            listener.error(xe.getMessage());
            return new SkippedEntryDeferredPermission();
        }*/

        final String type;
        if(atts.getValue("type") != null) {
            type = atts.getValue("type");
        } else {
            type = "XMLResource";
        }
        
        final String owner = getAttr(atts, "owner", SecurityManager.SYSTEM);
        final String group = getAttr(atts, "group", SecurityManager.DBA_GROUP);
        final String perms = getAttr(atts, "mode", "644");

        final String filename;
        if(atts.getValue("filename") != null) {
            filename = atts.getValue("filename");
        } else  {
            filename = name;
        }

        final String mimetype = atts.getValue("mimetype");
        final String created = atts.getValue("created");
        final String modified = atts.getValue("modified");

        final String publicid = atts.getValue("publicid");
        final String systemid = atts.getValue("systemid");
        final String namedoctype = atts.getValue("namedoctype");

        final XmldbURI docUri;
        if (version >= STRICT_URI_VERSION) {
            docUri = XmldbURI.create(name);
        } else {
            try {
                docUri = URIUtils.encodeXmldbUriFor(name);
            } catch(final URISyntaxException e) {
                final String msg = "Could not parse document name into a URI: " + e.getMessage();
                listener.error(msg);
                LOG.error(msg, e);
                return new SkippedEntryDeferredPermission();
            }
        }

        final EXistInputSource is;
        if (deduplicateBlobs && type.equals("BinaryResource")) {
            final String blobId = atts.getValue("blob-id");
            is = descriptor.getBlobInputSource(blobId);

            if (is == null) {
                final String msg = "Failed to restore resource '" + name + "'\nfrom BLOB '" + blobId + "'.\nReason: Unable to obtain its EXistInputSource";
                listener.warn(msg);
                return new SkippedEntryDeferredPermission();
            }
        } else {
            is = descriptor.getInputSource(filename);

            if (is == null) {
                final String msg = "Failed to restore resource '" + name + "'\nfrom file '" + descriptor.getSymbolicPath( name, false ) + "'.\nReason: Unable to obtain its EXistInputSource";
                listener.warn(msg);
                return new SkippedEntryDeferredPermission();
            }
        }

        try {
            listener.setCurrentResource(name);
            if(currentCollection instanceof Observable) {
                listener.observe((Observable)currentCollection);
            }

            Resource res = currentCollection.createResource(docUri.toString(), type);

            if(mimetype != null) {
                ((EXistResource)res).setMimeType(mimetype);
            }

            if(is.getByteStreamLength() > 0 || "BinaryResource".equals(type)) {
                // TODO(AR) we could add an optimisation here so that we need only send the content for duplicate blobs once!
                res.setContent(is);
            } else {
                res = null;
            }

            // Restoring name
            if(res == null) {
                listener.warn("Failed to restore resource '" + name + "'\nfrom file '" + descriptor.getSymbolicPath(name, false) + "'. The resource is empty.");
                return new SkippedEntryDeferredPermission();
            } else {
                Date date_created = null;
                Date date_modified = null;

                if(created != null) {
                    try {
                        date_created = (new DateTimeValue(created)).getDate();
                    } catch(final XPathException xpe) {
                        listener.warn("Illegal creation date. Ignoring date...");
                    }
                }
                if(modified != null) {
                    try {
                        date_modified = (new DateTimeValue(modified)).getDate();
                    } catch(final XPathException xpe) {
                        listener.warn("Illegal modification date. Ignoring date...");
                    }
                }

                currentCollection.storeResource(res, date_created, date_modified);

                if((publicid != null) || (systemid != null)) {
                    final DocumentType doctype = new DocumentTypeImpl(namedoctype, publicid, systemid);

                    try {
                        ((EXistResource) res).setDocType(doctype);
                    } catch(final XMLDBException e1) {
                        LOG.error(e1.getMessage(), e1);
                    }
                }

                final DeferredPermission deferredPermission;
                if(name.startsWith(XmldbURI.SYSTEM_COLLECTION)) {
                    //prevents restore of a backup from changing system collection resource ownership
                    deferredPermission = new ResourceDeferredPermission(listener, res, SecurityManager.SYSTEM, SecurityManager.DBA_GROUP, Integer.parseInt(perms, 8));
                } else {
                    deferredPermission = new ResourceDeferredPermission(listener, res, owner, group, Integer.parseInt(perms, 8));
                }
                
                listener.restored(name);
                listener.incrementFileCounter();
                
                return deferredPermission;
            }
        } catch(final Exception e) {
            listener.warn(String.format("Failed to restore resource '%s'\nfrom file '%s'.\nReason: %s", name, descriptor.getSymbolicPath(name, false), e.getMessage()));
            LOG.error(e.getMessage(), e);
            return new SkippedEntryDeferredPermission();
        } finally {
            is.close();
        }
    }

    private void restoreDeletedEntry(final Attributes atts) {
        final String name = atts.getValue("name");
        final String type = atts.getValue("type");

        if("collection".equals(type)) {

            try {
                final Collection child = currentCollection.getChildCollection(name);

                if(child != null) {
                    currentCollection.setTriggersEnabled(false);
                    final CollectionManagementService cmgt = (CollectionManagementService)currentCollection.getService("CollectionManagementService", "1.0");
                    cmgt.removeCollection(name);
                    currentCollection.setTriggersEnabled(true);
                }
            } catch(final XMLDBException e) {
                listener.warn("Failed to remove deleted collection: " + name + ": " + e.getMessage());
            }
        } else if("resource".equals(type)) {

            try {
                final Resource resource = currentCollection.getResource(name);

                if(resource != null) {
                    currentCollection.setTriggersEnabled(false);
                    currentCollection.removeResource(resource);
                    currentCollection.setTriggersEnabled(true);
                }
            } catch(final XMLDBException e) {
                listener.warn("Failed to remove deleted resource: " + name + ": " + e.getMessage());
            }
        }
    }

    private void addACEToDeferredPermissions(final Attributes atts) {
        final int index = Integer.parseInt(atts.getValue("index"));
        final ACE_TARGET target = ACE_TARGET.valueOf(atts.getValue("target"));
        final String who = atts.getValue("who");
        final ACE_ACCESS_TYPE access_type = ACE_ACCESS_TYPE.valueOf(atts.getValue("access_type"));
        final int mode = Integer.parseInt(atts.getValue("mode"), 8);

        deferredPermissions.peek().addACE(index, target, who, access_type, mode);
    }

    private void setDeferredPermissions() {
        
        final DeferredPermission deferredPermission = deferredPermissions.pop();
        deferredPermission.apply();
    }
    
    private Date getDateFromXSDateTimeStringForItem(final String strXSDateTime, final String itemName) {
        Date date_created = null;

        if(strXSDateTime != null) {
            try {
                date_created = new DateTimeValue(strXSDateTime).getDate();
            } catch(final XPathException e2) {
            }
        }

        if(date_created == null) {
            final String msg = "Could not parse created date '" + strXSDateTime + "' from backup for: '" + itemName + "', using current time!";
            listener.error(msg);
            LOG.error(msg);

            date_created = Calendar.getInstance().getTime();
        }

        return date_created;
    }
    
    private EXistCollection mkcol(final XmldbURI collPath, final Date created) throws XMLDBException, URISyntaxException {
        final XmldbURI[] allSegments = collPath.getPathSegments();
        final XmldbURI[] segments = Arrays.copyOfRange(allSegments, 1, allSegments.length); //drop the first 'db' segment
        final XmldbURI dbUri;

        if(!dbBaseUri.endsWith(XmldbURI.ROOT_COLLECTION)) {
            dbUri = XmldbURI.xmldbUriFor(dbBaseUri + XmldbURI.ROOT_COLLECTION);
        } else {
            dbUri = XmldbURI.xmldbUriFor(dbBaseUri);
        }
        
        EXistCollection current = (EXistCollection)DatabaseManager.getCollection(dbUri.toString(), dbUsername, dbPassword);
        XmldbURI p = XmldbURI.ROOT_COLLECTION_URI;
        
        for(final XmldbURI segment : segments) {
            p = p.append(segment);
            final XmldbURI xmldbURI = dbUri.resolveCollectionPath(p);
            EXistCollection c = (EXistCollection)DatabaseManager.getCollection(xmldbURI.toString(), dbUsername, dbPassword);
            if(c == null) {
            	current.setTriggersEnabled(false);
                final EXistCollectionManagementService mgtService = (EXistCollectionManagementService)current.getService("CollectionManagementService", "1.0");
                c = (EXistCollection)mgtService.createCollection(segment, created);
                current.setTriggersEnabled(true);
            }
            current = c;
        }
        
        return current;
    }
}
