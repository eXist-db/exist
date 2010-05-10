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

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * This examples demonstrate how you can run WORKING:HEAD diff.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class DiffBASEToHEAD {
    
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
            System.out.println(info);
            
            //checkout the entire repository tree
            SVNURL reposURL = SVNURL.fromFile(reposRoot);
            SamplesUtility.checkOutWorkingCopy(reposURL, wcRoot);

            
            //now make some changes to the working copy
            SamplesUtility.writeToFile(new File(wcRoot, "iota"), "New text appended to 'iota'", true);
            SamplesUtility.writeToFile(new File(wcRoot, "A/mu"), "New text in 'mu'", false);
            
            SVNClientManager clientManager = SVNClientManager.newInstance();
            SVNWCClient wcClient = SVNClientManager.newInstance().getWCClient();
            wcClient.doSetProperty(new File(wcRoot, "A/B"), "spam", SVNPropertyValue.create("egg"), false, 
                    SVNDepth.EMPTY, null, null);

            //commit local changes
            SVNCommitClient commitClient = clientManager.getCommitClient();
            commitClient.doCommit(new File[] { wcRoot }, false, "committing changes", null, null, false, false, SVNDepth.INFINITY);
            
            //roll back changes in the working copy - update to revision 1
            SVNUpdateClient updateClient = clientManager.getUpdateClient();
            updateClient.doUpdate(wcRoot, SVNRevision.create(1), SVNDepth.INFINITY, false, false);
            
            //now diff the base revision of the working copy against the repository
            SVNDiffClient diffClient = clientManager.getDiffClient();

            /*
             * This corresponds to 'svn diff -rBASE:HEAD'.
             */
            diffClient.doDiff(wcRoot, SVNRevision.UNDEFINED, SVNRevision.BASE, SVNRevision.HEAD, 
                    SVNDepth.INFINITY, true, System.out, null);
        } catch (SVNException svne) {
            System.out.println(svne.getErrorMessage());
            System.exit(1);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(1);
        }
    }
    
}
