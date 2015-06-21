/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.storage;

import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * @author wolf
 *
 */
public class RecoverBinaryTest {
    
    private BrokerPool pool;

    @Test
    public void storeAndLoad() throws LockException, TriggerException, PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        store();
        tearDown();
        setUp();
        load();
    }

    private void store() throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {

    	BrokerPool.FORCE_CORRUPTION = true;

        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            try(final Txn transaction = transact.beginTransaction()) {
            
    	        final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
        	    assertNotNull(root);
            	broker.saveCollection(transaction, root);
    
	            final String existHome = System.getProperty("exist.home");
    	        final File existDir = existHome==null ? new File(".") : new File(existHome);
        	    try(final InputStream is = new FileInputStream(new File(existDir,"LICENSE")); final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            	    byte[] buf = new byte[512];
	                int count = 0;
    	            while((count = is.read(buf)) > -1) {
        	            os.write(buf, 0, count);
            	    }
                	BinaryDocument doc =
                    	root.addBinaryResource(transaction, broker, TestConstants.TEST_BINARY_URI, os.toByteArray(), "text/text");
	                assertNotNull(doc);
    	        }

				transact.commit(transaction);
			}
            
            // the following transaction will not be committed. It will thus be rolled back by recovery
//          final Txn transaction = transact.beginTransaction();
//          root.removeBinaryResource(transaction, broker, doc);
            
            //TODO : remove ?
            transact.getJournal().flushToLog(true);
		}
    }
    
    private void load() throws EXistException, PermissionDeniedException, IOException {

        BrokerPool.FORCE_CORRUPTION = false;

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());) {
            final BinaryDocument binDoc = (BinaryDocument) broker.getXMLResource(TestConstants.TEST_COLLECTION_URI.append(TestConstants.TEST_BINARY_URI), Lock.READ_LOCK);
            assertNotNull("Binary document is null", binDoc);

            try(final InputStream is = broker.getBinaryResource(binDoc)) {
                final byte[] bdata = new byte[(int) broker.getBinaryResourceSize(binDoc)];
                is.read(bdata);
                final String data = new String(bdata);
                assertNotNull(data);
            }
	    }
    }

    @Before
    public void setUp() throws DatabaseConfigurationException, EXistException {
        final Configuration config = new Configuration();
        BrokerPool.configure(1, 5, config);
        pool = BrokerPool.getInstance();
    }

    @After
    public void tearDown() {
        BrokerPool.stopAll(false);
        pool = null;
    }
}
