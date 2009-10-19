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

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.xmldb.XmldbURI;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class VersioningRepositoryImpl {
	
    public final static Logger LOG = Logger.getLogger(VersioningRepositoryImpl.class);

    private String url = "https://exist.svn.sourceforge.net/svnroot/exist/trunk/eXist/webapp/admin/";
    private String name = "anonymous";
    private String password = "anonymous";
    
    private boolean connected = false;
    private long latestRevision = -1;
    
    org.tmatesoft.svn.core.io.SVNRepository repository = null;

    protected VersioningRepositoryImpl() {
    	//FEATURE: different collections connected with different repository 
    }

    public boolean connect() {

    	if (connected)
    		return true;
    	
    	setupLibrary();

        try {
            repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url));
        } catch (SVNException svne) {
            LOG.error("error while creating an SVNRepository for location '" + url + "': " + svne.getMessage());
            return false;
        }

        ISVNAuthenticationManager authManager = new BasicAuthenticationManager(name, password);
        repository.setAuthenticationManager(authManager);

        try {
            SVNNodeKind nodeKind = repository.checkPath("", -1);
            if (nodeKind == SVNNodeKind.NONE) {
            	LOG.error("There is no entry at '" + url + "'.");
                return false;
            } else if (nodeKind == SVNNodeKind.FILE) {
            	LOG.error("The entry at '" + url + "' is a file while a directory was expected.");
                return false;
            }

            System.out.println("Repository Root: " + repository.getRepositoryRoot(true));
            System.out.println("Repository UUID: " + repository.getRepositoryUUID(true));
            System.out.println("");

        } catch (SVNException svne) {
        	LOG.error("error while listing entries: " + svne.getMessage());
            return false;
        }

        try {
            latestRevision = repository.getLatestRevision();
        } catch (SVNException svne) {
        	LOG.error("error while fetching the latest repository revision: " + svne.getMessage());
            return false;
        }

        System.out.println("");
        System.out.println("---------------------------------------------");
        System.out.println("Repository latest revision: " + latestRevision);
        
        connected = true;
        
        return true;
    }

    private void setupLibrary() {
        /*
         * For using over http:// and https://
         */
        DAVRepositoryFactory.setup();
        
        /*
         * For using over svn:// and svn+xxx://
         */
        SVNRepositoryFactoryImpl.setup();
        
        /*
         * For using over file:///
         */
        FSRepositoryFactory.setup();
    }
    
    protected boolean update(XmldbURI path) {
        ISVNReporterBaton reporterBaton = new ExportReporterBaton(latestRevision);
        
        ISVNEditor exportEditor;
		
        try {
			exportEditor = new ExportEditor(path);

	        repository.update(latestRevision, null, true, reporterBaton, exportEditor);
		
		} catch (EXistException e) {
			e.printStackTrace();
			
			return false;
			
		} catch (SVNException e) {
			e.printStackTrace();

			return false;
		}
        
        System.out.println("Exported revision: " + latestRevision);
        
        return true;
    }
}
