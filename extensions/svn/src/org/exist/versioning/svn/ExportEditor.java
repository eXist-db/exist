/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 *  $Id:$
 */
package org.exist.versioning.svn;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.OutputStream;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ExportEditor implements ISVNEditor {
	private XmldbURI rootPath;
	
	private Collection myRootDirectory;
	private SVNDeltaProcessor myDeltaProcessor;
    
    BrokerPool pool = null;
    DBBroker broker = null;
    TransactionManager transact = null;
    
    public ExportEditor(XmldbURI path) throws EXistException {
    	rootPath = path;
        
        myDeltaProcessor = new SVNDeltaProcessor();
    }

    public void targetRevision(long revision) throws SVNException {
    	System.out.println("targetRevision");
    }

    public void openRoot(long revision) throws SVNException {
    	System.out.println("openRoot");

    	try {
			pool = BrokerPool.getInstance();  assertNotNull(pool);
			broker = pool.get(SecurityManager.SYSTEM_USER);  assertNotNull(broker);
			transact = pool.getTransactionManager();  assertNotNull(transact);
			
			myRootDirectory = broker.getCollection(rootPath);
			
		} catch (EXistException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
            		"error: failed to initialize database.");
            throw new SVNException(err);
		}
	}
    
    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    	System.out.println("addDir");

    	Txn transaction = transact.beginTransaction();
		Collection child;

		try {
			child = broker.getOrCreateCollection(transaction, myRootDirectory.getURI().append(path));
			broker.saveCollection(transaction, child);
			transact.commit(transaction);
		} catch (PermissionDeniedException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
            		"error: failed on permission.");
            throw new SVNException(err);
		
		} catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
    				"error: failed on IO.");
            throw new SVNException(err);
		
		} catch (TransactionException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
    				"error: failed on transaction.");
            throw new SVNException(err);
		}

        System.out.println("collection added: " + path);
    }
    
    public void openDir(String path, long revision) throws SVNException {
    	System.out.println("openDir");
    }

    public void changeDirProperty(String name, SVNPropertyValue property) throws SVNException {
    	System.out.println("changeDirProperty name = "+name+" :: property = "+property);
	}

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    	System.out.println("addFile path = "+path+" :: copyFromPath = "+copyFromPath+" :: copyFromRevision = "+copyFromRevision);

    	Txn transaction = transact.beginTransaction();
//		myRootDirectory.addDocument(transaction, broker, doc);
//		myRootDirectory.store(transaction, broker, info, is, false);
		try {
			transact.commit(transaction);
		} catch (TransactionException e) {
          SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
        		  "error: exported file ''{0}'' already exists!", path);
          throw new SVNException(err);
		}
    }
    
    public void openFile(String path, long revision) throws SVNException {
    	System.out.println("openFile");
    }

    public void changeFileProperty(String path, String name, SVNPropertyValue property) throws SVNException {
    	System.out.println("changeFileProperty path = "+path+" :: name = "+name+" :: property = "+property);
	}

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
    	System.out.println("applyTextDelta path = "+path);
    	
    	DocumentImpl doc = myRootDirectory.getDocument(broker, XmldbURI.create(path));
    	
//        myDeltaProcessor.applyTextDelta(SVNFileUtil.DUMMY_IN, doc, false);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow)   throws SVNException {
    	System.out.println("textDeltaChunk path = "+path+" :: diffWindow = "+diffWindow);
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }
    
    public void textDeltaEnd(String path) throws SVNException {
    	System.out.println("textDeltaEnd");
        myDeltaProcessor.textDeltaEnd();
    }
    
    public void closeFile(String path, String textChecksum) throws SVNException {
    	System.out.println("closeFile");
        System.out.println("file added: " + path);
    }

    public void closeDir() throws SVNException {
    	System.out.println("closeDir");
    }

    public void deleteEntry(String path, long revision) throws SVNException {
    	System.out.println("deleteEntry");
    }
    
    public void absentDir(String path) throws SVNException {
    	System.out.println("absentDir");
    }

    public void absentFile(String path) throws SVNException {
    	System.out.println("absentFile");
    }        
    
    public SVNCommitInfo closeEdit() throws SVNException {
    	System.out.println("closeEdit");
        return null;
    }
    
    public void abortEdit() throws SVNException {
    	System.out.println("abortEdit");
    }
}
