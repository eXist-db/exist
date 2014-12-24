/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.versioning.svn.xquery;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.util.io.Resource;
import org.exist.versioning.svn.wc.ISVNStatusHandler;
import org.exist.versioning.svn.wc.SVNClientManager;
import org.exist.versioning.svn.wc.SVNStatusClient;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.exist.versioning.svn.wc.SVNWCClient;
import org.exist.versioning.svn.wc.SVNWCUtil;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * Status information on Working Copy items.
 *
 * @author <a href="mailto:amir.akhmedov@gmail.com">Amir Akhmedov</a>
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SVNStatus extends AbstractSVNFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("status", SVNModule.NAMESPACE_URI, SVNModule.PREFIX), "Status information on Working Copy items.",
			new SequenceType[] {
				DB_PATH
            },
            new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE, ""));

	public SVNStatus(XQueryContext context) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		String uri = args[0].getStringValue();
		SVNRepositoryFactoryImpl.setup();
		SVNClientManager manager = SVNClientManager.newInstance(SVNWCUtil.createDefaultOptions(false), "", "");

		SVNStatusClient statusClient = manager.getStatusClient();
		
		SVNWCClient wcClient = manager.getWCClient();

		MemTreeBuilder builder = context.getDocumentBuilder();
        builder.startDocument();
        builder.startElement(new QName("status", null, null), null);
		
        try {
			statusClient.doStatus(
					new Resource(uri), 
					SVNRevision.HEAD, 
					SVNDepth.getInfinityOrFilesDepth(true), 
					true, true, false, false,  
					new AddStatusHandler(false, builder), 
					null);
		} catch (SVNException e1) {
	        builder.startElement(new QName("error", null, null), null);
	        builder.characters(e1.getMessage());
	        builder.endElement();
		}
		
        builder.endElement();
        builder.endDocument();

        return (NodeValue) builder.getDocument().getDocumentElement();
	}
	
    private static class AddStatusHandler implements ISVNStatusHandler {
        private boolean isRemote;

        private final MemTreeBuilder builder;

        public AddStatusHandler(boolean isRemote, MemTreeBuilder builder) {
        	this.isRemote = isRemote;
        	this.builder = builder;
        }

		@Override
        public void handleStatus(org.exist.versioning.svn.wc.SVNStatus status) throws SVNException {
	        boolean isAddedWithHistory = status.isCopied();
	        
	        /*
	         * If SVNStatusClient.doStatus(..) was invoked with  remote = true  the 
	         * following code finds out whether the current item had  been  changed 
	         * in the repository   
	         */
	        String remoteChangeType = " ";

	        if(status.getRemotePropertiesStatus() != SVNStatusType.STATUS_NONE || 
	           status.getRemoteContentsStatus() != SVNStatusType.STATUS_NONE) {
	            /*
	             * the local item is out of date
	             */
	            remoteChangeType = "*";
	        }
	        /*
	         * Now getting the status of properties of an item. SVNStatusType  also 
	         * contains information on the properties state.
	         */
	        SVNStatusType propertiesStatus = status.getPropertiesStatus();
	        /*
	         * Default - properties are normal (unmodified).
	         */
	        String propertiesChangeType = " ";
	        if (propertiesStatus == SVNStatusType.STATUS_MODIFIED) {
	            /*
	             * Properties were modified.
	             */
	            propertiesChangeType = "M";
	        } else if (propertiesStatus == SVNStatusType.STATUS_CONFLICTED) {
	            /*
	             * Properties are in conflict with the repository.
	             */
	            propertiesChangeType = "C";
	        }

	        /*
	         * Whether the item is switched to a different URL (branch).
	         */
	        boolean isSwitched = status.isSwitched();
	        /*
	         * If the item is a file it may be locked.
	         */
	        SVNLock localLock = status.getLocalLock();
	        /*
	         * If  doStatus()  was  run  with  remote=true  and the item is a file, 
	         * checks whether a remote lock presents.
	         */
	        SVNLock remoteLock = status.getRemoteLock();
	        String lockLabel = " ";

	        if (localLock != null) {
	            /*
	             * at first suppose the file is locKed
	             */
	            lockLabel = "K";
	            if (remoteLock != null) {
	                /*
	                 * if the lock-token of the local lock differs from  the  lock-
	                 * token of the remote lock - the lock was sTolen!
	                 */
	                if (!remoteLock.getID().equals(localLock.getID())) {
	                    lockLabel = "T";
	                }
	            } else {
	                if(isRemote){
		                /*
		                 * the  local  lock presents but there's  no  lock  in  the
		                 * repository - the lock was Broken. This  is  true only if 
	                     * doStatus() was invoked with remote=true.
		                 */
		                lockLabel = "B";
	                }
	            }
	        } else if (remoteLock != null) {
	            /*
	             * the file is not locally locked but locked  in  the  repository -
	             * the lock token is in some Other working copy.
	             */
	            lockLabel = "O";
	        }

	        /*
	         * Obtains the number of the revision when the item was last changed. 
	         */
	        long lastChangedRevision = status.getCommittedRevision().getNumber();

	        /*
	         * status is shown in the manner of the native Subversion command  line
	         * client's command "svn status"
	         */
//    		propertiesChangeType
//            + (isAddedWithHistory ? "+" : " ")
//            + (isSwitched ? "S" : " ")
//            + lockLabel
//            + remoteChangeType


	        builder.startElement(ENTRY_ELEMENT, null);
	        
	        builder.addAttribute(STATUS_ATTRIBUTE, status.getContentsStatus().toString());
	        builder.addAttribute(LOCKED_ATTRIBUTE, status.isLocked() ? "true" : "false");
	        builder.addAttribute(WORKING_REVISION_ATTRIBUTE, String.valueOf(status.getRevision().getNumber()));
	        builder.addAttribute(LAST_CHANGED_REVISION_ATTRIBUTE, lastChangedRevision >= 0 ? String.valueOf(lastChangedRevision) : "?");
	        builder.addAttribute(AUTHOR_ATTRIBUTE, status.getAuthor() != null ? status.getAuthor() : "?");
	        builder.addAttribute(PATH_ATTRIBUTE, status.getFile().getPath());
	        
	        builder.endElement();
        }
    }

    private final static QName ENTRY_ELEMENT = new QName("entry", "", "");
    private final static QName STATUS_ATTRIBUTE = new QName("status", "", "");
    private final static QName LOCKED_ATTRIBUTE = new QName("locked", "", "");
    private final static QName WORKING_REVISION_ATTRIBUTE = new QName("working-revision", "", "");
    private final static QName LAST_CHANGED_REVISION_ATTRIBUTE = new QName("last-changed-revision", "", "");
    private final static QName AUTHOR_ATTRIBUTE = new QName("author", "", "");
    private final static QName PATH_ATTRIBUTE = new QName("path", "", "");
}
