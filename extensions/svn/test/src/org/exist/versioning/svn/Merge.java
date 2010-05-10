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
import java.io.IOException;
import java.util.Collections;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class Merge {

    /**
     * Pass the absolute path of the base directory where all example data will be created in 
     * arg[0]. The sample will create:
     *  
     *  - arg[0]/exampleRepository - repository with some test data
     *  - arg[0]/exampleWC         - working copy checked out from exampleRepository
     */
    public static void main (String[] args) {
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

            SVNClientManager clientManager = SVNClientManager.newInstance();
            clientManager.setEventHandler(new EventHandler());
            
            SVNURL reposURL = SVNURL.fromFile(reposRoot);

            //copy A to A_copy in repository (url-to-url copy)
            SVNCopyClient copyClient = clientManager.getCopyClient();
            SVNURL A_URL = reposURL.appendPath("A", true);
            SVNURL copyTargetURL = reposURL.appendPath("A_copy", true);
            SVNCopySource copySource = new SVNCopySource(SVNRevision.UNDEFINED, SVNRevision.HEAD, A_URL); 
            info = copyClient.doCopy(new SVNCopySource[] { copySource }, copyTargetURL, false, false, true, 
                    "copy A to A_copy", null);
            //print out new revision info
            System.out.println(info);
            
            //checkout the entire repository tree
            SamplesUtility.checkOutWorkingCopy(reposURL, wcRoot);

            
            //now make some changes to the A tree
            SamplesUtility.writeToFile(new File(wcRoot, "iota"), "New text appended to 'iota'", true);
            SamplesUtility.writeToFile(new File(wcRoot, "A/mu"), "New text in 'mu'", false);
            
            SVNWCClient wcClient = SVNClientManager.newInstance().getWCClient();
            wcClient.doSetProperty(new File(wcRoot, "A/B"), "spam", SVNPropertyValue.create("egg"), false, 
                    SVNDepth.EMPTY, null, null);

            //commit local changes
            SVNCommitClient commitClient = clientManager.getCommitClient();
            commitClient.doCommit(new File[] { wcRoot }, false, "committing changes", null, null, false, false, SVNDepth.INFINITY);
            
            //now diff the base revision of the working copy against the repository
            SVNDiffClient diffClient = clientManager.getDiffClient();
            SVNRevisionRange rangeToMerge = new SVNRevisionRange(SVNRevision.create(1), SVNRevision.HEAD);
            
            diffClient.doMerge(A_URL, SVNRevision.HEAD, Collections.singleton(rangeToMerge), 
                    new File(wcRoot, "A_copy"), SVNDepth.UNKNOWN, true, false, false, false);
            
            //now make some changes to the A tree again
            //change file contents of iota and A/D/gamma
            SamplesUtility.writeToFile(new File(wcRoot, "iota"), "New text2 appended to 'iota'", true);
            SamplesUtility.writeToFile(new File(wcRoot, "A/D/gamma"), "New text in 'gamma'", false);
            //remove A/C from version control
            wcClient.doDelete(new File(wcRoot, "A/C"), false, true, false);

            //commit local changes
            commitClient.doCommit(new File[] { wcRoot }, false, "committing changes again", null, null, false, false, SVNDepth.INFINITY);

            /* do the same merge call, merge-tracking feature will merge only those revisions
             * which were not still merged.
             */ 
            diffClient.doMerge(A_URL, SVNRevision.HEAD, Collections.singleton(rangeToMerge), 
                    new File(wcRoot, "A_copy"), SVNDepth.UNKNOWN, true, false, false, false);
            
        } catch (SVNException svne) {
            System.out.println(svne.getErrorMessage());
            System.exit(1);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(1);
        }
    }

    private static class EventHandler implements ISVNEventHandler {

        public void handleEvent(SVNEvent event, double progress) throws SVNException {
            SVNEventAction action = event.getAction();
            System.out.println(event.getFile() + " " + action); 
        }

        public void checkCancelled() throws SVNCancelException {
        }
        
    }
}
