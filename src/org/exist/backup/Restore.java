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
 *  $Id$
 */
package org.exist.backup;

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

import org.exist.Namespaces;
import org.exist.client.ClientFrame;
import org.exist.dom.DocumentTypeImpl;
import org.exist.security.SecurityManager;
import org.exist.security.Account;
import org.exist.storage.DBBroker;
import org.exist.util.EXistInputSource;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.DateTimeValue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.net.URISyntaxException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Observable;
import java.util.Properties;
import java.util.Stack;

import javax.swing.*;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.exist.security.ACLPermission.ACE_ACCESS_TYPE;
import org.exist.security.ACLPermission.ACE_TARGET;
import org.exist.security.Permission;
import org.exist.security.internal.aider.ACEAider;


/**
 * Restore.java.
 *
 * @author  Wolfgang Meier
 */
public class Restore extends DefaultHandler
{
    private static final int strictUriVersion = 1;

    private BackupDescriptor contents;
    private String uri;
    private String username;
    private String pass;
    private XMLReader reader;
    private CollectionImpl current;
    private Stack<BackupDescriptor> backupDescriptors = new Stack<BackupDescriptor>();
    private RestoreDialog dialog = null;
    private int version = 0;
    private RestoreListener listener;
    private List<String> errors = new ArrayList<String>();
    
    private boolean skipSecurityV1 = false;

    private Stack<DeferredPermission> deferredPermissions = new Stack<DeferredPermission>();
    
    /**
     * Constructor for Restore.
     *
     * @param   user          DOCUMENT ME!
     * @param   pass          DOCUMENT ME!
     * @param   newAdminPass  DOCUMENT ME!
     * @param   contents      DOCUMENT ME!
     * @param   uri           DOCUMENT ME!
     *
     * @throws  ParserConfigurationException  DOCUMENT ME!
     * @throws  SAXException                  DOCUMENT ME!
     * @throws  XMLDBException
     * @throws  URISyntaxException
     */
    public Restore( String user, String pass, String newAdminPass, File contents, String uri ) throws ParserConfigurationException, SAXException, XMLDBException, URISyntaxException
    {
        this.username = user;
        this.pass     = pass;
        this.uri      = uri;

        this.listener = new DefaultListener();

        if( newAdminPass != null ) {
            setAdminCredentials( newAdminPass );
        }

        SAXParserFactory saxFactory = SAXParserFactory.newInstance();
        saxFactory.setNamespaceAware( true );
        saxFactory.setValidating( false );
        SAXParser sax = saxFactory.newSAXParser();
        reader = sax.getXMLReader();
        reader.setContentHandler( this );


        do {
            BackupDescriptor bd         = null;
            Properties       properties = null;

            try {

                if( contents.isDirectory() ) {
                	bd=new FileSystemBackupDescriptor(new File(new File(contents, "db"), BackupDescriptor.COLLECTION_DESCRIPTOR));
                } else if( contents.getName().endsWith( ".zip" ) || contents.getName().endsWith( ".ZIP" ) ) {
                    bd = new ZipArchiveBackupDescriptor( contents );
                } else {
                    bd = new FileSystemBackupDescriptor( contents );
                }
                properties = bd.getProperties();
            }
            catch( Exception e ) {
                e.printStackTrace();
                throw( new SAXException( "Unable to create backup descriptor object from " + contents, e ) );
            }

            backupDescriptors.push( bd );

            // check if the system collection is in the backup. We have to process
            // this first to create users.
            //TODO : find a way to make a corespondance with DBRoker's named constants
            BackupDescriptor sysbd = bd.getChildBackupDescriptor( "system" );

            if( sysbd != null ) {
                backupDescriptors.push( sysbd );
                
                skipSecurityV1 = (sysbd.getChildBackupDescriptor( "security" ) != null);
            }

            contents = null;

            if( ( properties != null ) && properties.getProperty( "incremental", "no" ).equals( "yes" ) ) {
                String previous = properties.getProperty( "previous", "" );

                if( previous.length() > 0 ) {
                    contents = new File( bd.getParentDir(), previous );

                    if( !contents.canRead() ) {
                        throw( new SAXException( "Required part of incremental backup not found: " + contents.getAbsolutePath() ) );
                    }
                }
            }
        } while( contents != null );
    }

    public void setListener( RestoreListener listener )
    {
        this.listener = listener;
    }


    public void restore( boolean showGUI, JFrame parent ) throws XMLDBException, FileNotFoundException, IOException, SAXException
    {
        if( showGUI ) {
            dialog = new RestoreDialog( parent, "Restoring data ...", false );
            dialog.setVisible( true );
            Thread restoreThread = new Thread() {
                public void run()
                {
                    while( !backupDescriptors.isEmpty() ) {

                        try {
                            contents = backupDescriptors.pop();
                            dialog.setBackup( contents.getSymbolicPath() );
                            reader.parse( contents.getInputSource() );
                        }
                        catch( FileNotFoundException e ) {
                            dialog.displayMessage( e.getMessage() );
                        }
                        catch( IOException e ) {
                            dialog.displayMessage( e.getMessage() );
                        }
                        catch( SAXException e ) {
                            dialog.displayMessage( e.getMessage() );
                        } 
                    }
                    if (errors.size() > 0) {
                    	ClientFrame.showErrorMessage(formatErrors(), null);
                    }
                    dialog.setVisible(false);
                }
            };
            restoreThread.start();

            if( parent == null ) {

                while( restoreThread.isAlive() ) {

                    synchronized( this ) {

                        try {
                            wait( 20 );
                        }
                        catch( InterruptedException e ) {
                        }
                    }
                }
            }
        } else {

            while( !backupDescriptors.isEmpty() ) {
                contents = backupDescriptors.pop();
                EXistInputSource is = contents.getInputSource();
                is.setEncoding( "UTF-8" );
                //restoring sysId
                reader.parse( is );
            }
            if (errors.size() > 0)
            	System.err.println(formatErrors());
        }

    }

    /**
     * @see  org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {

        //only process entries in the exist namespace
        if(!namespaceURI.equals(Namespaces.EXIST_NS)) {
            return;
        }

        if(localName.equals("collection")) {
            restoreCollectionEntry(atts);
        } else if( localName.equals("subcollection")) {
            restoreSubCollectionEntry(atts);
        } else if(localName.equals("resource")) {
            restoreResourceEntry(atts);
        } else if(localName.equals("deleted" )) {
            restoreDeletedEntry(atts);
        } else if(localName.equals("ace")) {
            addACEToDeferredPermissions(atts);
        }
    }

    private void addACEToDeferredPermissions(Attributes atts) {
        final int index = Integer.parseInt(atts.getValue("index"));
        final ACE_TARGET target = ACE_TARGET.valueOf(atts.getValue("target"));
        final String who = atts.getValue("who");
        final ACE_ACCESS_TYPE access_type = ACE_ACCESS_TYPE.valueOf(atts.getValue("access_type"));
        final int mode = Integer.parseInt(atts.getValue("mode"), 8);

        deferredPermissions.peek().addACE(index, target, who, access_type, mode);
    }

    private void setDeferredPermissions() {
        
        DeferredPermission deferredPermission = deferredPermissions.pop();
        deferredPermission.apply();
    }

    @Override
    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {

        if(namespaceURI.equals(Namespaces.EXIST_NS) && (localName.equals("collection") || localName.equals("resource"))) {
            setDeferredPermissions();
        }

        super.endElement(namespaceURI, localName, qName);
    }

    private void restoreCollectionEntry(Attributes atts) throws SAXException {
        final String name = atts.getValue("name");
        final String owner = atts.getValue("owner");
        final String group = atts.getValue("group");
        final String mode = atts.getValue("mode");
        final String created = atts.getValue("created");
        final String strVersion = atts.getValue("version");

        if(strVersion != null) {
            try {
                this.version = Integer.parseInt(strVersion);
            } catch(NumberFormatException e) {
                this.version = 0;
            }
        }

        if(name == null) {
            throw new SAXException("Collection requires a name attribute");
        }

        try {
            listener.createCollection(name);
            XmldbURI collUri;

            if(version >= strictUriVersion) {
                collUri = XmldbURI.create(name);
            } else {
                try {
                    collUri = URIUtils.encodeXmldbUriFor(name);
                } catch(URISyntaxException e) {
                    listener.warn("Could not parse document name into a URI: " + e.getMessage());
                    return;
                }
            }

            Date date_created = null;

            if(created != null) {
                try {
                    date_created = new DateTimeValue(created).getDate();
                } catch(XPathException e2) {
                }
            }

            current = mkcol(collUri, date_created);

            if(current == null) {
                throw new SAXException("Collection not found: " + collUri);
            }
            
            final DeferredPermission deferredPermission;
            if(name.startsWith(XmldbURI.SYSTEM_COLLECTION)) {
                //prevents restore of a backup from changing system collection ownership
                deferredPermission = new CollectionDeferredPermission(current, SecurityManager.SYSTEM, SecurityManager.DBA_GROUP, Integer.parseInt(mode, 8));
            } else {
                deferredPermission = new CollectionDeferredPermission(current, owner, group, Integer.parseInt(mode, 8));
            }
            deferredPermissions.push(deferredPermission);
            
        } catch(Exception e) {
            listener.warn("An unrecoverable error occurred while restoring\ncollection '" + name + "'. " + "Aborting restore!");
            e.printStackTrace();
            throw new SAXException(e.getMessage(), e);
        }

        if(dialog != null) {
            dialog.setCollection(name);
        }
    }

    private void restoreSubCollectionEntry(Attributes atts) {
        String name = atts.getValue("filename");

        if(name == null) {
            name = atts.getValue("name");
        }

        BackupDescriptor subbd = contents.getChildBackupDescriptor( name );

        if(subbd != null) {
            backupDescriptors.push(subbd);
        } else {
            listener.warn("collection " + contents.getSymbolicPath(name, false) + " does not exist or is not readable.");
        }
    }

    private void restoreResourceEntry(Attributes atts) {
        final String skip = atts.getValue( "skip" );

        //dont process entries which should be skipped
        if(skip != null && !skip.equals("no")) {
            return;
        }


        String type = atts.getValue("type");
        if(type == null) {
            type = "XMLResource";
        }
        final String name = atts.getValue("name");
        if(name == null ) {
            listener.warn( "Wrong entry in backup descriptor: resource requires a name attribute."); //TODO consider an exception here!
        }

        final String owner = atts.getValue("owner");
        final String group = atts.getValue("group");
        final String perms = atts.getValue("mode");

        String filename = atts.getValue("filename");
        if(filename == null) {
            filename = name;
        }

        final String mimetype = atts.getValue("mimetype");
        final String created = atts.getValue("created");
        final String modified = atts.getValue("modified");

        final String publicid = atts.getValue("publicid");
        final String systemid = atts.getValue("systemid");
        final String namedoctype = atts.getValue("namedoctype");


        final XmldbURI docUri;

        if(version >= strictUriVersion) {
            docUri = XmldbURI.create(name);
        } else {
            try {
                docUri = URIUtils.encodeXmldbUriFor(name);
            } catch(URISyntaxException e) {
                listener.warn("Could not parse document name into a URI: " + e.getMessage());
                return;
            }
        }

        final EXistInputSource is = contents.getInputSource(filename);
        if(is == null) {
            String msg = "Failed to restore resource '" + name + "'\nfrom file '" + contents.getSymbolicPath( name, false ) + "'.\nReason: Unable to obtain its EXistInputSource";
            listener.warn( msg );
            throw new RuntimeException(msg);
        }

        try {
            if(dialog != null) {
                if(current instanceof Observable) {
                    ((Observable)current).addObserver(dialog.getObserver());
                }
                dialog.setResource(name);
            }

            //triggers should NOT be disabled, because it do used by the system tasks (like security manager)
            //UNDERSTAND: split triggers: user & system
            //current.setTriggersEnabled(false);
            if(skipSecurityV1 && ("/db/system/users.xml".equals(current.getPathURI().append(docUri).toString()))) {
                listener.warn("skip resource '" + name + "'\nfrom file '" + contents.getSymbolicPath(name, false) + "'.");
                return;
            }

            Resource res = current.createResource(docUri.toString(), type);

            if(mimetype != null) {
                ((EXistResource)res).setMimeType(mimetype);
            }

            if(is.getByteStreamLength() > 0) {
                res.setContent(is);
            } else {
                if(type.equals("BinaryResource")) {
                    res.setContent( "" );
                } else {
                    res = null;
                }
            }

            // Restoring name
            if(res != null) {
                Date date_created = null;
                Date date_modified = null;

                if(created != null) {
                    try {
                        date_created = (new DateTimeValue(created)).getDate();
                    } catch(XPathException e2) {
                        listener.warn("Illegal creation date. Skipping ...");
                    }
                }
                if(modified != null) {
                    try {
                        date_modified = (Date) (new DateTimeValue(modified)).getDate();
                    } catch(XPathException e2) {
                        listener.warn("Illegal modification date. Skipping ...");
                    }
                }

                current.storeResource(res, date_created, date_modified);

                if((publicid != null) || (systemid != null)) {
                    DocumentType doctype = new DocumentTypeImpl(namedoctype, publicid, systemid);

                    try {
                        ((EXistResource) res).setDocType(doctype);
                    } catch (XMLDBException e1) {
                        e1.printStackTrace();
                    }
                }

                final DeferredPermission deferredPermission;
                if(name.startsWith(XmldbURI.SYSTEM_COLLECTION)) {
                    //prevents restore of a backup from changing system collection resource ownership
                    deferredPermission = new ResourceDeferredPermission(res, SecurityManager.SYSTEM, SecurityManager.DBA_GROUP, Integer.parseInt(perms, 8));
                } else {
                    deferredPermission = new ResourceDeferredPermission(res, owner, group, Integer.parseInt(perms, 8));
                }
                deferredPermissions.push(deferredPermission); 
//	                    	current.setTriggersEnabled(true);

            } else {
                listener.warn("Failed to restore resource '" + name + "'\nfrom file '" + contents.getSymbolicPath(name, false) + "'. The resource is empty.");
            }
            listener.restored(name);

        } catch(Exception e) {
            listener.warn("Failed to restore resource '" + name + "'\nfrom file '" + contents.getSymbolicPath(name, false) + "'.\nReason: " + e.getMessage());
            e.printStackTrace();
//                        throw( new RuntimeException( e ) );
        } finally {
            is.close();
        }
    }

    private void restoreDeletedEntry(Attributes atts) {
        final String name = atts.getValue("name");
        final String type = atts.getValue("type");

        if(type.equals("collection")) {

            try {
                Collection child = current.getChildCollection(name);

                if(child != null) {
                    current.setTriggersEnabled(false);
                    CollectionManagementService cmgt = (CollectionManagementService)current.getService("CollectionManagementService", "1.0");
                    cmgt.removeCollection(name);
                    current.setTriggersEnabled(true);
                }
            } catch(XMLDBException e) {
                listener.warn("Failed to remove deleted collection: " + name + ": " + e.getMessage());
            }
        } else if(type.equals("resource")) {

            try {
                Resource resource = current.getResource(name);

                if(resource != null) {
                    current.setTriggersEnabled(false);
                    current.removeResource(resource);
                    current.setTriggersEnabled(true);
                }
            } catch(XMLDBException e) {
                listener.warn("Failed to remove deleted resource: " + name + ": " + e.getMessage());
            }
        }
    }

    private CollectionImpl mkcol( XmldbURI collPath, Date created ) throws XMLDBException, URISyntaxException {
        XmldbURI[]                      segments   = collPath.getPathSegments();
        CollectionManagementServiceImpl mgtService;
        CollectionImpl                  c;
        XmldbURI                        dbUri;

        if( !uri.endsWith( DBBroker.ROOT_COLLECTION ) ) {
            dbUri = XmldbURI.xmldbUriFor( uri + DBBroker.ROOT_COLLECTION );
        } else {
            dbUri = XmldbURI.xmldbUriFor( uri );
        }
        CollectionImpl current = (CollectionImpl)DatabaseManager.getCollection( dbUri.toString(), username, pass );
        XmldbURI       p       = XmldbURI.ROOT_COLLECTION_URI;

        for( int i = 1; i < segments.length; i++ ) {
            p = p.append( segments[i] );
            XmldbURI xmldbURI = dbUri.resolveCollectionPath( p );
            c = (CollectionImpl)DatabaseManager.getCollection( xmldbURI.toString(), username, pass );
            if( c == null ) {
            	current.setTriggersEnabled(false);
                mgtService = (CollectionManagementServiceImpl)current.getService( "CollectionManagementService", "1.0" );
                c = (CollectionImpl)mgtService.createCollection( segments[i], created );
                current.setTriggersEnabled(true);
            }
            current = c;
        }
        return current;
    }


    private void setAdminCredentials( String adminPassword ) throws XMLDBException, URISyntaxException {
        XmldbURI dbUri;

        if( !uri.endsWith( DBBroker.ROOT_COLLECTION ) ) {
            dbUri = XmldbURI.xmldbUriFor( uri + DBBroker.ROOT_COLLECTION );
        } else {
            dbUri = XmldbURI.xmldbUriFor( uri );
        }
        Collection            root = DatabaseManager.getCollection( dbUri.toString(), username, pass );
        UserManagementService mgmt = (UserManagementService)root.getService( "UserManagementService", "1.0" );
        Account                  dba  = mgmt.getAccount( SecurityManager.DBA_USER );
        dba.setPassword( adminPassword );
        mgmt.updateAccount( dba );

        pass = adminPassword;
    }

    private String formatErrors() {
        StringBuilder builder = new StringBuilder();
        builder.append("------------------------------------\n");
        builder.append("Warnings were found during restore:\n");
        for (String error : errors) {
                builder.append("WARN: ").append(error).append('\n');
        }
        return builder.toString();
    }

    public static void showErrorMessage(String message) {
        JTextArea msgArea = new JTextArea( message );
        msgArea.setEditable( false );
        msgArea.setBackground( null );
        JScrollPane scroll     = new JScrollPane( msgArea );
        JOptionPane optionPane = new JOptionPane();
        optionPane.setMessage( new Object[] { scroll } );
        optionPane.setMessageType( JOptionPane.ERROR_MESSAGE );
        JDialog dialog = optionPane.createDialog( null, "Error" );
        dialog.setResizable( true );
        dialog.pack();
        dialog.setVisible( true );
        return;
    }

    private class CollectionDeferredPermission extends DeferredPermission<Collection> {
        public CollectionDeferredPermission(Collection collection, String owner, String group, Integer mode) {
            super(collection, owner, group, mode);
        }

        @Override
        public void apply() {
            try {

                UserManagementService service;
                if(getTarget().getName().equals(XmldbURI.ROOT_COLLECTION)) {
                    service = (UserManagementService)getTarget().getService("UserManagementService", "1.0");
                } else {
                    Collection parent = getTarget().getParentCollection();
                    service = (UserManagementService)parent.getService("UserManagementService", "1.0");
                }
                
                service.setPermissions(getTarget(), getOwner(), getGroup(), getMode(), getAces()); //persist
            } catch (XMLDBException xe) {
                xe.printStackTrace();
                listener.warn("Failed to change owner on collection skipping ...");
            }
        }
    }

    private class ResourceDeferredPermission extends DeferredPermission<Resource> {

        public ResourceDeferredPermission(Resource resource, String owner, String group, Integer mode) {
            super(resource, owner, group, mode);
        }

        @Override
        public void apply() {
            try {
                UserManagementService service = (UserManagementService)getTarget().getParentCollection().getService("UserManagementService", "1.0");
                Permission permissions = service.getPermissions(getTarget());
                service.setPermissions(getTarget(), getOwner(), getGroup(), getMode(), getAces()); //persist
            } catch(XMLDBException xe) {
                xe.printStackTrace();
                listener.warn("Failed to change owner on document skipping ...");
            }
        }
    }

    private abstract class DeferredPermission<T> {

        final private T target;
        final private String owner;
        final private String group;
        final private int mode;
        final List<ACEAider> aces = new ArrayList<ACEAider>();

        public DeferredPermission(T target, String owner, String group, int mode) {
            this.target = target;
            this.owner = owner;
            this.group = group;
            this.mode = mode;
        }

        protected T getTarget() {
            return target;
        }

        protected List<ACEAider> getAces() {
            return aces;
        }

        protected String getGroup() {
            return group;
        }

        protected int getMode() {
            return mode;
        }

        protected String getOwner() {
            return owner;
        }

        private void addACE(int index, ACE_TARGET target, String who, ACE_ACCESS_TYPE access_type, int mode) {
            aces.add(new ACEAider(access_type, target, who, mode));
        }
        
        public abstract void apply();
    }

    public interface RestoreListener {
        void createCollection(String collection);
        void restored(String resource);
        void info(String message);
        void warn(String message);
    }

    private class DefaultListener implements RestoreListener {

        @Override
        public void createCollection(String collection) {
            info("creating collection " + collection);
        }

        @Override
        public void restored(String resource) {
            info("restored " + resource);
        }

        @Override
        public void info(String message) {
            if(dialog != null) {
                dialog.displayMessage(message);
            } else {
                System.err.println(message);
            }
        }

        @Override
        public void warn(String message) {
            if(dialog != null) {
                dialog.displayMessage(message);
            } else {
                System.err.println(message);
            }
            errors.add(message);
        }
    }
}