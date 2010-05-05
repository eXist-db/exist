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
 * <b>SVNPropertyConflictDescription</b> brings a property conflict description.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.3
 */
public class SVNPropertyConflictDescription extends SVNConflictDescription {

    private String myPropertyName;

    /**
     * Creates a new <code>SVNPropertyConflictDescription</code> object.
     * 
     * @param mergeFiles      files involved in a property conflict
     * @param nodeKind        kind of the conflicted node
     * @param propertyName    versioned property name
     * @param conflictAction  action lead to the conflict
     * @param conflictReason  the reason why the conflict occurred
     * @since 1.3
     */
    public SVNPropertyConflictDescription(SVNMergeFileSet mergeFiles, SVNNodeKind nodeKind, String propertyName,
            SVNConflictAction conflictAction, SVNConflictReason conflictReason) {
        super(mergeFiles, nodeKind, conflictAction, conflictReason);
        myPropertyName = propertyName;
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
     * Returns <code>true</code>.
     * @return <code>true</code>
     * @since 1.3 
     */
    public boolean isPropertyConflict() {
        return true;
    }

    /**
     * Returns <code>false</code>.
     * @return <code>false</code>
     * @since 1.3 
     */
    public boolean isTreeConflict() {
        return false;
    }

    /**
     * Returns the name of the property, on which the conflict occurred.
     * @return conflicted property name
     * @since 1.3
     */
    public String getPropertyName() {
        return myPropertyName;
    }
}