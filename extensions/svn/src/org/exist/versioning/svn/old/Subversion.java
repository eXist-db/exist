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
package org.exist.versioning.svn.old;

import java.io.File;
import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.xmldb.XmldbURI;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.wc.ISVNCommitParameters;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCommitPacket;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class Subversion implements ISVNEventHandler {

	public final static Logger LOG = LogManager.getLogger(Subversion.class);

	private SVNURL svnurl = null;
	private XmldbURI collection = null;
	private org.tmatesoft.svn.core.io.SVNRepository repository = null;

	private ISVNAuthenticationManager authManager;
	private SVNClientManager clientManager;
	private SVNUpdateClient updateClient;
	private SVNCommitClient commitClient;
	private SVNWCClient wcClient;

	public Subversion(XmldbURI collection, String url) throws SVNException {
		this(collection, url, "anonymous", "anonymous");
	}

	public Subversion(XmldbURI collection, String url, String username,
			String password) throws SVNException {

		this.collection = collection;

		setupType(url);

		authManager = SVNWCUtil.createDefaultAuthenticationManager(username,
				password);
		((DefaultSVNAuthenticationManager) authManager)
				.setAuthenticationForced(true);
		repository.setAuthenticationManager(authManager);

		checkRoot();

		if (LOG.isDebugEnabled()) {
			LOG.debug("Connected to " + svnurl);
			LOG.debug("Repository latest revision: " + latestRevision());
		}
	}

	private void setupType(String url) throws SVNException {
		svnurl = SVNURL.parseURIDecoded(url);

		// over http:// and https://
		if (url.startsWith("http")) {
			DAVRepositoryFactory.setup();
			repository = DAVRepositoryFactory.create(svnurl);

			// over svn:// and svn+xxx://
		} else if (url.startsWith("svn")) {
			SVNRepositoryFactoryImpl.setup();
			repository = SVNRepositoryFactoryImpl.create(svnurl);

			// over file:///
		} else {
			FSRepositoryFactory.setup();
			repository = FSRepositoryFactory.create(svnurl);
		}
	}

	private void checkRoot() throws SVNException {
		SVNNodeKind nodeKind = repository.checkPath("", -1);
		if (nodeKind == SVNNodeKind.NONE) {
			SVNErrorMessage error = SVNErrorMessage.create(
					SVNErrorCode.UNKNOWN, "No entry at URL ''{0}''", svnurl);
			throw new SVNException(error);
		} else if (nodeKind == SVNNodeKind.FILE) {
			SVNErrorMessage error = SVNErrorMessage
					.create(
							SVNErrorCode.UNKNOWN,
							"Entry at URL ''{0}'' is a file while directory was expected",
							svnurl);
			throw new SVNException(error);
		}
	}

	public long latestRevision() throws SVNException {
		return repository.getLatestRevision();
	}

	public SVNCommitInfo commit(File dstPath, String message)
			throws SVNException {
		getWCClient().doAdd(dstPath, true, false, true, SVNDepth.INFINITY,
				false, false);

		SVNCommitPacket packet = getCommitClient().doCollectCommitItems(
				new File[] { dstPath }, false, false, SVNDepth.INFINITY, null);

		getCommitClient().doCommit(packet, true, false, message, null);

		return null;
	}

	public boolean update() throws SVNException {
		return update(SVNRevision.HEAD);
	}

	public boolean update(SVNRevision toRevision) throws SVNException {
		ISVNReporterBaton reporterBaton = new ExportReporterBaton(toRevision.getNumber());

		ISVNEditor exportEditor;

		try {
			exportEditor = new ExportEditor(collection);

			repository.update(toRevision.getNumber(), null, true, reporterBaton,
					exportEditor);

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

	private synchronized SVNClientManager getClientManager() {
		if (clientManager == null) {
			clientManager = SVNClientManager.newInstance(
					SVNWCUtil.createDefaultOptions(true), authManager);
		}
		return clientManager;
	}

	public SVNWCClient getWCClient() {
		if (wcClient == null) {
			wcClient = getClientManager().getWCClient();
			wcClient.setEventHandler(this);
		}

		return wcClient;
	}

    private SVNUpdateClient getUpdateClient() {
        if (updateClient == null) {
            updateClient = getClientManager().getUpdateClient();
            updateClient.setEventHandler(this);
        }

        return updateClient;
    }
	
	private SVNCommitClient getCommitClient() {

		if (commitClient == null) {
			commitClient = getClientManager().getCommitClient();
			commitClient.setEventHandler(this);
			commitClient.setCommitParameters(new ISVNCommitParameters() {

				public boolean onDirectoryDeletion(File directory) {
					return false;
				}

				public boolean onFileDeletion(File file) {
					return false;
				}

				public Action onMissingDirectory(File file) {
					return ISVNCommitParameters.DELETE;
				}

				public Action onMissingFile(File file) {
					return ISVNCommitParameters.DELETE;
				}
			});
		}
		return commitClient;

	}

	@Override
	public void checkCancelled() throws SVNCancelException {
	}

	@Override
	public void handleEvent(SVNEvent event, double progress) throws SVNException {
        String nullString = " ";
        SVNEventAction action = event.getAction();
        String pathChangeType = nullString;
        if (action == SVNEventAction.ADD) {
            
        	LOG.info("A     " + event.getFile());
            return;
        
        } else if (action == SVNEventAction.COPY) {
        	
        	LOG.info("A  +  " + event.getFile());
            return;

        } else if (action == SVNEventAction.DELETE) {
        	
        	LOG.info("D     " + event.getFile());
            return;

        } else if (action == SVNEventAction.LOCKED) {

        	LOG.info("L     " + event.getFile());
            return;
            
        } else if (action == SVNEventAction.LOCK_FAILED) {

        	LOG.info("failed to lock    " + event.getFile());
            return;

        }

        if (action == SVNEventAction.UPDATE_ADD) {
            pathChangeType = "A";
        } else if (action == SVNEventAction.UPDATE_DELETE) {
            pathChangeType = "D";
        } else if (action == SVNEventAction.UPDATE_UPDATE) {
            SVNStatusType contentsStatus = event.getContentsStatus();
            if (contentsStatus == SVNStatusType.CHANGED) {
                pathChangeType = "U";
            } else if (contentsStatus == SVNStatusType.CONFLICTED) {
                pathChangeType = "C";
            } else if (contentsStatus == SVNStatusType.MERGED) {
                pathChangeType = "G";
            }
        } else if (action == SVNEventAction.UPDATE_EXTERNAL) {
        
        	LOG.info("Fetching external item into '" + event.getFile().getAbsolutePath() + "'");
        	LOG.info("External at revision " + event.getRevision());
            return;

        } else if (action == SVNEventAction.UPDATE_COMPLETED) {

        	LOG.info("At revision " + event.getRevision());
            return;

        }

        SVNStatusType propertiesStatus = event.getPropertiesStatus();
        String propertiesChangeType = nullString;
        if (propertiesStatus == SVNStatusType.CHANGED) {
            propertiesChangeType = "U";
        } else if (propertiesStatus == SVNStatusType.CONFLICTED) {
            propertiesChangeType = "C";
        } else if (propertiesStatus == SVNStatusType.MERGED) {
            propertiesChangeType = "G";
        }

        String lockLabel = nullString;
        SVNStatusType lockType = event.getLockStatus();

        if (lockType == SVNStatusType.LOCK_UNLOCKED) {
            lockLabel = "B";
        }
        if (pathChangeType != nullString || propertiesChangeType != nullString || lockLabel != nullString) {
        	LOG.info(pathChangeType + propertiesChangeType + lockLabel + "       " + event.getFile());
        }

        if (action == SVNEventAction.COMMIT_MODIFIED) {
        	
        	LOG.info("Sending   " + event.getFile());

        } else if (action == SVNEventAction.COMMIT_DELETED) {
        	
        	LOG.info("Deleting   " + event.getFile());

        } else if (action == SVNEventAction.COMMIT_REPLACED) {
        	
        	LOG.info("Replacing   " + event.getFile());

        } else if (action == SVNEventAction.COMMIT_DELTA_SENT) {
        	
        	LOG.info("Transmitting file data....");

        } else if (action == SVNEventAction.COMMIT_ADDED) {
            String mimeType = event.getMimeType();
            if (SVNProperty.isBinaryMimeType(mimeType)) {

            	LOG.info("Adding  (bin)  " + event.getFile());

            } else {
                
            	LOG.info("Adding         " + event.getFile());

            }
        }
     }
}
