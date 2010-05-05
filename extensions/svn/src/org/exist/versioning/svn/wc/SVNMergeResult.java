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


/**
 * The <b>SVNMergeResult</b> represents a result of a text or properties merge operation. 
 * This class combines the following information about a merge result: a status type indicating how merge 
 * finished and, if the merge finished with a conflict, the reason of the conflict (why did the conflict ever 
 * occur?).    
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNMergeResult {
    
    private SVNStatusType myMergeStatus;
    private SVNConflictReason myConflictReason;

    private SVNMergeResult(SVNStatusType status, SVNConflictReason conflictReason) {
        myMergeStatus = status;
        myConflictReason = conflictReason;
    }
    
    /**
     * Creates a new merge result object.
     * 
     * <p/>
     * If <code>status</code> is not {@link SVNStatusType#CONFLICTED}, <code>reason</code> is irrelevant and 
     * always set to <span class="javakeyword">null</span>. If <code>status</code> is {@link SVNStatusType#CONFLICTED} 
     * and <code>reason</code> is <span class="javakeyword">null</span>, then <code>reason</code> defaults to 
     * {@link SVNConflictReason#EDITED}. 
     * 
     * @param  status   status of merge operation  
     * @param  reason   reason of the conflict (if any)
     * @return          merge result object
     */
    public static SVNMergeResult createMergeResult(SVNStatusType status, SVNConflictReason reason) {
        if (status == SVNStatusType.CONFLICTED) {
            if (reason == null) {
                reason = SVNConflictReason.EDITED;
            }
        } else {
            reason = null;
        }
        return new SVNMergeResult(status, reason);
    }
    
    /**
     * Returns merge status.
     * 
     * @return merge status type object. 
     */
    public SVNStatusType getMergeStatus() {
        return myMergeStatus;
    }

    /**
     * Returns conflict reason.
     * 
     * @return conflict reason object.  
     */
    public SVNConflictReason getConflictReason() {
        return myConflictReason;
    }

}
