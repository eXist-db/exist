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
package org.exist.versioning.svn.wc;

import java.io.File;


/**
 * The <b>SVNConflictResult</b> represents the decision of the user's {@link ISVNConflictHandler conflict handler}
 * regarding a conflict situation.   
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNConflictResult {

    private SVNConflictChoice myConflictChoice;
    private File myMergedFile;
    
    /**
     * Creates a new <code>SVNConflictChoice</code> object.
     * 
     * @param conflictChoice way that the conflict should be resolved in 
     * @param mergedFile     file containing the merge result      
     */
    public SVNConflictResult(SVNConflictChoice conflictChoice, File mergedFile) {
        myConflictChoice = conflictChoice;
        myMergedFile = mergedFile;
    }

    /**
     * Returns the conflict handler's choice. This way implementor can manage conflicts providing a choice 
     * object defining what to do with the conflict.
     * 
     * @return  conflict choice
     */
    public SVNConflictChoice getConflictChoice() {
        return myConflictChoice;
    }

    /**
     * Returns the file with the merge result.
     * 
     * <p/>
     * Usually this will be the {@link SVNMergeFileSet#getResultFile() result file} obtained by the 
     * user's {@link ISVNConflictHandler conflict handler} from the {@link SVNConflictDescription description}'s 
     * {@link SVNMergeFileSet merge file set} object.
     * 
     * @return merged file
     */
    public File getMergedFile() {
        return myMergedFile;
    }
    
}
