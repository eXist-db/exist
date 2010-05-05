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

import org.exist.versioning.svn.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.SVNNodeKind;


/**
 * <b>SVNTreeConflictDescription</b> brings information on a tree conflict.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.3
 */
public class SVNTreeConflictDescription extends SVNConflictDescription {

    private SVNOperation myOperation;
    private SVNConflictVersion mySourceLeftVersion;
    private SVNConflictVersion mySourceRightVersion;

    /**
     * Creates a new <code>SVNTreeConflictDescription</code>.
     * 
     * @param path                wc path
     * @param nodeKind            kind of the node, on which the tree conflict occurred
     * @param conflictAction      action which lead to the conflict
     * @param conflictReason      reason why the conflict occurred
     * @param operation           user operation which exposed the conflict
     * @param sourceLeftVersion   info on the "merge-left source" or "older" version of incoming change
     * @param sourceRightVersion  info on the "merge-right source" or "their" version of incoming change 
     * @since 1.3
     */
    public SVNTreeConflictDescription(File path, SVNNodeKind nodeKind, SVNConflictAction conflictAction, SVNConflictReason conflictReason, 
            SVNOperation operation, SVNConflictVersion sourceLeftVersion, SVNConflictVersion sourceRightVersion) {
        super(new SVNMergeFileSet(null, null, null, path, null, null, null, null, null), nodeKind, conflictAction, conflictReason);
        myOperation = operation;
        mySourceLeftVersion = sourceLeftVersion;
        mySourceRightVersion = sourceRightVersion;
    }

    /**
     * Returns <code>false</code>.
     * @return <code>false</code>
     * @since 1.3
     */
    public boolean isTextConflict() {
        return false;
    }

    /**
     * Returns <code>false</code>.
     * @return <code>false</code>
     * @since 1.3
     */
    public boolean isPropertyConflict() {
        return false;
    }

    /**
     * Returns <code>true</code>.
     * @return <code>true</code>
     * @since 1.3
     */
    public boolean isTreeConflict() {
        return true;
    }

    /**
     * Returns the wc file.
     * @return detranslated wc file
     * @since  1.3
     */
    public File getPath() {
        return getMergeFiles().getLocalFile();
    }

    /**
     * Returns the user operation that exposed this tree conflict. 
     * @return user operation
     * @since  1.3
     */
    public SVNOperation getOperation() {
        return myOperation;
    }

    /**
     * Returns info on the "merge-left source" or "older" version of incoming change.
     * @return left version info
     * @since 1.3
     */
    public SVNConflictVersion getSourceLeftVersion() {
        return mySourceLeftVersion;
    }

    /**
     * Returns info on the "merge-right source" or "their" version of incoming change.
     * @return right version info
     * @since 1.3
     */
    public SVNConflictVersion getSourceRightVersion() {
        return mySourceRightVersion;
    }

    /**
     * Returns <code>null</code>.
     * @return <code>null</code>
     * @since  1.3
     */
    public String getPropertyName() {
        return null;
    }
    
}