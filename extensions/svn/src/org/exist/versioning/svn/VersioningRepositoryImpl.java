/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2010 The eXist Project
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
package org.exist.versioning.svn;

import java.util.Collection;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.xmldb.XmldbURI;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class VersioningRepositoryImpl {

	public final static Logger LOG = Logger.getLogger(VersioningRepositoryImpl.class);

	private SVNURL svnurl = null;
	private XmldbURI collection = null;
	private org.tmatesoft.svn.core.io.SVNRepository repository = null;
	
	private ISVNAuthenticationManager authManager;

	public VersioningRepositoryImpl(XmldbURI collection, String url)
			throws SVNException {
		this(collection, url, "anonymous", "anonymous");
	}

	public VersioningRepositoryImpl(XmldbURI collection, String url,
			String username, String password) throws SVNException {

		this.collection = collection;

		setupType(url);
		
		authManager = SVNWCUtil.createDefaultAuthenticationManager(username, password);
		((DefaultSVNAuthenticationManager) authManager).setAuthenticationForced(true);
		repository.setAuthenticationManager(authManager);
		
		checkRoot();

		if (LOG.isDebugEnabled()) {
			LOG.debug("Connected to " + svnurl);
			LOG.debug("Repository latest revision: " + latestRevision());
		}
	}

	private void setupType(String url) throws SVNException {
		svnurl = SVNURL.parseURIDecoded(url);
		
		//over http:// and https://
		if (url.startsWith("http")) {
			DAVRepositoryFactory.setup(); 
			repository = DAVRepositoryFactory.create(svnurl);
		
		//over svn:// and svn+xxx://
		} else if (url.startsWith("svn")) {
			SVNRepositoryFactoryImpl.setup(); 
			repository = SVNRepositoryFactoryImpl.create(svnurl);
		
		//over file:///
		} else {
			FSRepositoryFactory.setup(); 
			repository = FSRepositoryFactory.create(svnurl);
		}
	}

	private void checkRoot() throws SVNException {
		SVNNodeKind nodeKind = repository.checkPath("", -1);
		if (nodeKind == SVNNodeKind.NONE) {
			SVNErrorMessage error = SVNErrorMessage
				.create(SVNErrorCode.UNKNOWN,
						"No entry at URL ''{0}''", svnurl);
			throw new SVNException(error);
		} else if (nodeKind == SVNNodeKind.FILE) {
			SVNErrorMessage error = SVNErrorMessage
					.create(SVNErrorCode.UNKNOWN,
						"Entry at URL ''{0}'' is a file while directory was expected", svnurl);
			throw new SVNException(error);
		}
	}
	
	public long latestRevision() throws SVNException {
		return repository.getLatestRevision();
	}

	protected boolean commit() {
		return false;
	}

	protected boolean update() throws SVNException {
		return update(latestRevision());
	}

	protected boolean update(long toRevision)  throws SVNException {
		ISVNReporterBaton reporterBaton = new ExportReporterBaton(toRevision);

		ISVNEditor exportEditor;

		try {
			exportEditor = new ExportEditor(collection);

			repository.update(toRevision, null, true, reporterBaton, exportEditor);

		} catch (EXistException e) {
			e.printStackTrace();

			return false;

		}

		System.out.println("Exported revision: " + toRevision);

		return true;
	}

	@SuppressWarnings("unchecked")
	public Collection<SVNLogEntry> log(String[] targetPaths,
			Collection<SVNLogEntry> entries, long startRevision,
			long endRevision, boolean changedPath, boolean strictNode)
			throws SVNException {
		
		return repository.log(new String[] { "" }, entries, startRevision,
				endRevision, changedPath, strictNode);
	}
}
