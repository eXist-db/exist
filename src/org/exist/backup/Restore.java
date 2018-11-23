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

import org.exist.backup.restore.RestoreHandler;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.security.Account;
import org.exist.security.SecurityManager;
import org.exist.util.EXistInputSource;
import org.exist.util.ExistSAXParserFactory;
import org.exist.util.FileUtils;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Properties;

/**
 * Restore.java.
 *
 * @author Adam Retter <adam@exist-db.org>
 * @author  Wolfgang Meier
 */
public class Restore {
    
//    private final static Logger LOG = LogManager.getLogger(Restore.class);

    public void restore(final RestoreListener listener, final String username, String password, final String newAdminPass, final Path f, final String uri) throws XMLDBException, IOException, SAXException, ParserConfigurationException, URISyntaxException {
        
        //set the admin password
        if(newAdminPass != null) {
            password = setAdminCredentials(uri, username, password, newAdminPass);
        }

        //get the backup descriptors, can be more than one if it was an incremental backup
        final Deque<BackupDescriptor> descriptors = getBackupDescriptors(f);

        // count all files
        long totalNrOfFiles=0;
        Iterator<BackupDescriptor> bdIterator = descriptors.iterator();
        while(bdIterator.hasNext()){
            totalNrOfFiles += bdIterator.next().getNumberOfFiles();
        }
        listener.setNumberOfFiles(totalNrOfFiles);

        // continue restore
        final SAXParserFactory saxFactory = ExistSAXParserFactory.getSAXParserFactory();
        saxFactory.setNamespaceAware(true);
        saxFactory.setValidating(false);
        final SAXParser sax = saxFactory.newSAXParser();
        final XMLReader reader = sax.getXMLReader();
        
        try {
            listener.restoreStarting();

            while(!descriptors.isEmpty()) {
                final BackupDescriptor descriptor = descriptors.pop();
                final EXistInputSource is = descriptor.getInputSource();
                is.setEncoding( "UTF-8" );

                final RestoreHandler handler = new RestoreHandler(listener, uri, username, password, descriptor);
                
                reader.setContentHandler(handler);
                reader.parse(is);
            }

        } finally {
            listener.restoreFinished();
        }
    }
    
    private Deque<BackupDescriptor> getBackupDescriptors(Path contents) throws XMLDBException, IOException {
        
        final Deque<BackupDescriptor> descriptors = new ArrayDeque<>();
        
        do {

            final BackupDescriptor bd = getBackupDescriptor(contents);
            
            
            descriptors.push(bd);

            // check if the system collection is in the backup. This should be processed first
            //TODO : find a way to make a corespondance with DBRoker's named constants
            final BackupDescriptor sysDescriptor = bd.getChildBackupDescriptor("system");

            // check if the system/security collection is in the backup, this must be the first system collection processed
            if(sysDescriptor != null) {
                descriptors.push(sysDescriptor);
                
                final BackupDescriptor secDescriptor = sysDescriptor.getChildBackupDescriptor("security");
                if(secDescriptor != null) {
                    descriptors.push(secDescriptor);
                }
            }

            contents = null;

            final Properties properties = bd.getProperties();
            if((properties != null ) && "yes".equals(properties.getProperty("incremental", "no"))) {
                final String previous = properties.getProperty("previous", "");

                if(!previous.isEmpty()) {
                    contents = bd.getParentDir().resolve(previous);

                    if(!Files.isReadable(contents)) {
                        throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "Required part of incremental backup not found: " + contents.toAbsolutePath().toString());
                    }
                }
            }
        } while(contents != null);
        
        return descriptors;
    }
    
    private BackupDescriptor getBackupDescriptor(final Path f) throws IOException {
        final BackupDescriptor bd;
        if(Files.isDirectory(f)) {
            bd = new FileSystemBackupDescriptor(f, f.resolve("db").resolve(BackupDescriptor.COLLECTION_DESCRIPTOR));
        } else if(FileUtils.fileName(f).toLowerCase().endsWith(".zip")) {
            bd = new ZipArchiveBackupDescriptor(f);
        } else {
            bd = new FileSystemBackupDescriptor(f, f);
        }
        return bd;
    }
    
    private String setAdminCredentials(final String uri, final String username, final String password, final String adminPassword) throws XMLDBException, URISyntaxException {
        final XmldbURI dbUri;

        if(!uri.endsWith(XmldbURI.ROOT_COLLECTION)) {
            dbUri = XmldbURI.xmldbUriFor(uri + XmldbURI.ROOT_COLLECTION);
        } else {
            dbUri = XmldbURI.xmldbUriFor(uri);
        }
        
        final Collection root = DatabaseManager.getCollection(dbUri.toString(), username, password);
        final UserManagementService mgmt = (UserManagementService)root.getService("UserManagementService", "1.0");
        final Account dba  = mgmt.getAccount(SecurityManager.DBA_USER);
        if (dba == null) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "'"+SecurityManager.DBA_USER+"' account can't be found.");
        }
        dba.setPassword(adminPassword);
        mgmt.updateAccount(dba);

        return adminPassword;
    }
}