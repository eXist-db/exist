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
package org.exist.backup;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Database;
import org.exist.backup.restore.SystemImportHandler;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.config.ConfigurationException;
import org.exist.security.AuthenticationException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.util.EXistInputSource;
import org.exist.util.FileUtils;
import org.exist.util.XMLReaderPool;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Restore 
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class SystemImport {
    
    public final static Logger LOG = LogManager.getLogger( SystemImport.class );

    private final Database db;
    
    public SystemImport(final Database db) {
    	this.db = db;
	}

    public void restore(final String username, final Object credentials, @Nullable final String newCredentials, final Path f, final RestoreListener listener) throws IOException, SAXException, AuthenticationException, ConfigurationException, PermissionDeniedException, TransactionException {
        
        //login
        try(final DBBroker broker = db.authenticate(username, credentials);
                final Txn transaction = broker.continueOrBeginTransaction()) {

            //set the new password
            if (newCredentials != null) {
                setAdminCredentials(broker, newCredentials);
            }
	
	        //get the backup descriptors, can be more than one if it was an incremental backup
	        final Deque<BackupDescriptor> descriptors = getBackupDescriptors(f);

            final XMLReaderPool parserPool = broker.getBrokerPool().getParserPool();
	        XMLReader reader = null;
	        try {
                reader = parserPool.borrowXMLReader();

                listener.started(0);
	
	            while(!descriptors.isEmpty()) {
	                final BackupDescriptor descriptor = descriptors.pop();
	                final EXistInputSource is = descriptor.getInputSource();
	                is.setEncoding( UTF_8.name() );
	
	                final SystemImportHandler handler = new SystemImportHandler(broker, transaction, descriptor, listener);
	                
	                reader.setContentHandler(handler);
	                reader.parse(is);
	            }
	        } finally {
	            listener.finished();

                if (reader != null) {
                    parserPool.returnXMLReader(reader);
                }
	        }

	        transaction.commit();
        }
    }
    
    private Deque<BackupDescriptor> getBackupDescriptors(Path contents) throws IOException {
        
        final Deque<BackupDescriptor> descriptors = new ArrayDeque<>();
        
        do {
            final BackupDescriptor bd = getBackupDescriptor(contents);
            
            
            descriptors.push(bd);

            // check if the system collection is in the backup. This should be processed first
            final BackupDescriptor sysDescriptor = bd.getChildBackupDescriptor(XmldbURI.SYSTEM_COLLECTION_NAME);

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
                        throw new IOException("Required part of incremental backup not found: " + contents.toAbsolutePath());
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
    
    private void setAdminCredentials(final DBBroker broker, final String newCredentials) throws ConfigurationException, PermissionDeniedException {
    	final Subject subject = broker.getCurrentSubject();
    	subject.setPassword(newCredentials);
    	subject.save(broker);
    }
}