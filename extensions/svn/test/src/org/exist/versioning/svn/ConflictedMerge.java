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

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.DefaultSVNOptions;
import org.exist.versioning.svn.wc.ISVNConflictHandler;
import org.exist.versioning.svn.wc.SVNClientManager;
import org.exist.versioning.svn.wc.SVNCommitClient;
import org.exist.versioning.svn.wc.SVNConflictChoice;
import org.exist.versioning.svn.wc.SVNConflictDescription;
import org.exist.versioning.svn.wc.SVNConflictReason;
import org.exist.versioning.svn.wc.SVNConflictResult;
import org.exist.versioning.svn.wc.SVNCopyClient;
import org.exist.versioning.svn.wc.SVNCopySource;
import org.exist.versioning.svn.wc.SVNDiffClient;
import org.exist.versioning.svn.wc.SVNMergeFileSet;
import org.exist.versioning.svn.wc.SVNWCClient;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class ConflictedMerge {
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
        
        File baseDirectory = new Resource(args[0]);
        File reposRoot = new Resource(baseDirectory, "exampleRepository");
        File wcRoot = new Resource(baseDirectory, "exampleWC");
        
        try {
            //first create a repository and fill it with data
            SamplesUtility.createRepository(reposRoot);
            SVNCommitInfo info = SamplesUtility.createRepositoryTree(reposRoot);
            //print out new revision info
            System.out.println(info);

            SVNClientManager clientManager = SVNClientManager.newInstance();
            
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
            SamplesUtility.writeToFile(new File(wcRoot, "A/B/lambda"), "New text appended to 'lambda'", true);
            SamplesUtility.writeToFile(new File(wcRoot, "A/mu"), "New text in 'mu'", false);
            
            SVNWCClient wcClient = SVNClientManager.newInstance().getWCClient();
            wcClient.doSetProperty(new File(wcRoot, "A/B"), "spam", SVNPropertyValue.create("egg"), false, 
                    SVNDepth.EMPTY, null, null);

            //commit local changes
            SVNCommitClient commitClient = clientManager.getCommitClient();
            commitClient.doCommit(new File[] { wcRoot }, false, "committing changes", null, null, false, false, SVNDepth.INFINITY);

            //now make some local changes to the A_copy tree 
            //change file contents of A_copy/B/lambda and A_copy/mu
            SamplesUtility.writeToFile(new File(wcRoot, "A_copy/B/lambda"), "New text in copied 'lambda'", true);
            SamplesUtility.writeToFile(new File(wcRoot, "A_copy/mu"), "New text in copied 'mu'", false);

            //now diff the base revision of the working copy against the repository
            SVNDiffClient diffClient = clientManager.getDiffClient();

            /*
             * Since we provided no custom ISVNOptions implementation to SVNClientManager, our 
             * manager uses DefaultSVNOptions, which is set to all SVN*Client classes which the 
             * manager produces. So, we can cast ISVNOptions to DefaultSVNOptions.
             */
            DefaultSVNOptions options = (DefaultSVNOptions) diffClient.getOptions();
            //This way we set a conflict handler which will automatically resolve conflicts for those 
            //cases that we would like
            options.setConflictHandler(new ConflictResolverHandler());
            
            /* do the same merge call, merge-tracking feature will merge only those revisions
             * which were not still merged.
             */ 
            SVNRevisionRange rangeToMerge = new SVNRevisionRange(SVNRevision.create(1), SVNRevision.HEAD);
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

    /**
     * Conflict resolver which always selects the local version of a file.
     * 
     * @version 1.3
     * @author  TMate Software Ltd.
     */
    private static class ConflictResolverHandler implements ISVNConflictHandler {
    
        public SVNConflictResult handleConflict(SVNConflictDescription conflictDescription) throws SVNException {
            SVNConflictReason reason = conflictDescription.getConflictReason();
            SVNMergeFileSet mergeFiles = conflictDescription.getMergeFiles();
            
            SVNConflictChoice choice = SVNConflictChoice.THEIRS_FULL;
            if (reason == SVNConflictReason.EDITED) {
                //If the reason why conflict occurred is local edits, chose local version of the file
                //Otherwise the repository version of the file will be chosen.
                choice = SVNConflictChoice.MINE_FULL;
            }
            System.out.println("Automatically resolving conflict for " + mergeFiles.getWCFile() + 
                    ", choosing " + (choice == SVNConflictChoice.MINE_FULL ? "local file" : "repository file"));
            return new SVNConflictResult(choice, mergeFiles.getResultFile()); 
        }   
    
    }
}
