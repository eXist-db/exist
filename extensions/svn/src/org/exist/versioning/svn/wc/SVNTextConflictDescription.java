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

import org.tmatesoft.svn.core.SVNNodeKind;


/**
 * <b>SVNTextConflictDescription</b> brings information about conflict on a file.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.3
 */
public class SVNTextConflictDescription extends SVNConflictDescription {

    /**
     * Creates a new <code>SVNTextConflictDescription</code> object.
     *
     * @param mergeFiles            files involved in the merge
     * @param nodeKind              node kind of the item which the conflict occurred on
     *                              conflict; otherwise <span class="javakeyword">false</span>
     * @param conflictAction        action which lead to the conflict
     * @param conflictReason        why the conflict ever occurred
     * @since 1.3
     */
    public SVNTextConflictDescription(SVNMergeFileSet mergeFiles, SVNNodeKind nodeKind, SVNConflictAction conflictAction, SVNConflictReason conflictReason) {
        super(mergeFiles, nodeKind, conflictAction, conflictReason);
    }

    /**
     * Returns <code>true</code>.
     * 
     * @return <code>true</code>
     * @since 1.3
     */
    public boolean isTextConflict() {
        return true;
    }

    /**
     * Returns <code>false</code>.
     * 
     * @return <code>false</code>
     * @since 1.3
     */
    public boolean isPropertyConflict() {
        return false;
    }

    /**
     * Returns <code>false</code>.
     * 
     * @return <code>false</code>
     * @since 1.3
     */
    public boolean isTreeConflict() {
        return false;
    }
    
    /**
     * Returns <code>null</code>.
     * 
     * @return <code>null</code>
     * @since 1.3
     */
    public String getPropertyName() {
        return null;
    }
}