/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.exist.versioning.svn;

import java.io.File;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class WorkingCopyFormatTesting {

    public static void main(String[] args) {
        //initialize SVNKit to work through file:/// protocol
        SamplesUtility.initializeFSFSprotocol();
        
        File baseDirectory = new File(args[0]);
        File reposRoot = new File(baseDirectory, "exampleRepository");
        File wcRoot = new File(baseDirectory, "exampleWC");

        try {
            //first create a repository and fill it with data
            SamplesUtility.createRepository(reposRoot);
            SVNCommitInfo info = SamplesUtility.createRepositoryTree(reposRoot);
            //print out new revision info
            System.out.println(info);

            SVNURL reposURL = SVNURL.fromFile(reposRoot);
            //checkout the entire repository tree
            SamplesUtility.checkOutWorkingCopy(reposURL, wcRoot);

            SVNClientManager clientManager = SVNClientManager.newInstance();
            SVNStatusClient statusClient = clientManager.getStatusClient();
            ISVNStatusHandler handler = new StatusHandler();

            statusClient.doStatus(wcRoot, SVNRevision.WORKING, SVNDepth.INFINITY, true, true, false, false, 
                    handler, null);
            
            SVNWCClient wcClient = clientManager.getWCClient();
            wcClient.doSetWCFormat(wcRoot, 4);
            
            statusClient.doStatus(wcRoot, SVNRevision.WORKING, SVNDepth.INFINITY, false, true, false, false, 
                    handler, null);
            
        } catch (SVNException svne) {
            System.out.println(svne.getErrorMessage());
            System.exit(1);
        }
    }

    private static class StatusHandler implements ISVNStatusHandler {
        public void handleStatus(SVNStatus status) throws SVNException {
            System.out.println("Path: " + status.getFile() + ", wc format: " + 
                    status.getWorkingCopyFormat());
        }
    }
}
