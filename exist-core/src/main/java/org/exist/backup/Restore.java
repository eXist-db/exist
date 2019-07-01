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

import org.exist.EXistException;
import org.exist.backup.restore.RestoreHandler;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.security.Account;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.internal.Password;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.EXistInputSource;
import org.exist.util.FileUtils;
import org.exist.util.XMLReaderPool;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Restore.java.
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author  Wolfgang Meier
 */
public class Restore {

    private static final byte[] ZIP_FILE_MAGIC_NUMBER = {0x50, 0x4B, 0x03, 0x04};

    public void restore(final DBBroker broker, @Nullable final Txn transaction, final String newAdminPass, final Path f,
                        final RestoreListener listener) throws EXistException, IOException, SAXException, PermissionDeniedException {
        
        //set the admin password
        if (newAdminPass != null) {
            setAdminCredentials(broker, newAdminPass);
        }

        //get the backup descriptors, can be more than one if it was an incremental backup
        final Deque<BackupDescriptor> descriptors = getBackupDescriptors(f);

        // count all files
        long totalNrOfFiles = 0;
        Iterator<BackupDescriptor> bdIterator = descriptors.iterator();
        while(bdIterator.hasNext()){
            totalNrOfFiles += bdIterator.next().getNumberOfFiles();
        }

        // continue restore
        final XMLReaderPool parserPool = broker.getBrokerPool().getParserPool();
        XMLReader reader = null;
        try {
            reader = parserPool.borrowXMLReader();
            listener.started(totalNrOfFiles);

            while(!descriptors.isEmpty()) {
                final BackupDescriptor descriptor = descriptors.pop();
                final EXistInputSource is = descriptor.getInputSource();
                is.setEncoding(UTF_8.displayName());

                final RestoreHandler handler = new RestoreHandler(broker, transaction, descriptor, listener);
                
                reader.setContentHandler(handler);
                reader.parse(is);
            }

        } finally {
            listener.finished();

            if (reader != null) {
                parserPool.returnXMLReader(reader);
            }
        }
    }
    
    private Deque<BackupDescriptor> getBackupDescriptors(Path contents) throws IOException {
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
                        throw new IOException("Required part of incremental backup not found: " + contents.toAbsolutePath().toString());
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
        } else if(FileUtils.fileName(f).toLowerCase().endsWith(".zip") || hasZipMagicNumber(f)) {
            bd = new ZipArchiveBackupDescriptor(f);
        } else {
            bd = new FileSystemBackupDescriptor(f, f);
        }
        return bd;
    }

    /**
     * Determines if a file starts with the magic number
     * which indicates that it is a Zip file.
     *
     * @param path the path to the file.
     *
     * @return true if the file is likely a Zip file.
     */
    private boolean hasZipMagicNumber(final Path path) throws IOException {
        try (final InputStream is = Files.newInputStream(path)) {
            final byte[] buf = new byte[4];
            if (is.read(buf) != 4) {
                return false;
            }
            return Arrays.equals(ZIP_FILE_MAGIC_NUMBER, buf);
        }
    }

    private void setAdminCredentials(final DBBroker broker, final String adminPassword) throws EXistException, PermissionDeniedException {
        final SecurityManager securityManager = broker.getBrokerPool().getSecurityManager();
        final Account dba = securityManager.getAccount(SecurityManager.DBA_USER);
        if (dba == null) {
            throw new EXistException("'" + SecurityManager.DBA_USER + "' account can't be found.");
        }
        dba.setCredential(new Password(dba, adminPassword));
        securityManager.updateAccount(dba);
    }
}