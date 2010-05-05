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
 * The <b>SVNConflictReason</b> class represents an enumeration of constants describing the reason of a 
 * conflict state in a working copy produced by a merge operation. 
 *  
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNConflictReason {
    /**
     * Constant saying that local edits are already present. 
     */
    public static final SVNConflictReason EDITED = new SVNConflictReason("edited");
    /**
     * Constant saying that another object is in the way.
     */
    public static final SVNConflictReason OBSTRUCTED = new SVNConflictReason("obstructed");
    /**
     * Constant saying that an object is already schedule-delete.
     */
    public static final SVNConflictReason DELETED = new SVNConflictReason("deleted");
    /**
     * Constant saying that an object is unknown or missing. Reserved (never passed currently).
     */
    public static final SVNConflictReason MISSING = new SVNConflictReason("missing");
    /**
     * Constant saying that an object is unversioned. Reserved (never passed currently).
     */
    public static final SVNConflictReason UNVERSIONED = new SVNConflictReason("unversioned");
    /**
     * Constant saying that an object is already added or schedule-add
     */
    public static final SVNConflictReason ADDED = new SVNConflictReason("added");

    /**
     * Converts a string reason name to an <code>SVNConflictReason</code> object.
     *  
     * @param   reason name
     * @return  an <code>SVNConflictReason</code> that matches the <code>reason</code> name;
     *          <code>null</code> if no match is found 
     * @since   1.3
     */
    public static SVNConflictReason fromString(String reason) {
        if (EDITED.getName().equals(reason)) {
            return EDITED;
        }
        if (OBSTRUCTED.getName().equals(reason)) {
            return OBSTRUCTED;
        }
        if (DELETED.getName().equals(reason)) {
            return DELETED;
        }
        if (MISSING.getName().equals(reason)) {
            return MISSING;
        }
        if (UNVERSIONED.getName().equals(reason)) {
            return UNVERSIONED;
        }
        if (ADDED.getName().equals(reason)) {
            return ADDED;
        }
        return null;
    }

    private final String myName;

    private SVNConflictReason(String name) {
        myName = name;
    }

    /**
     * Retunrns a string representation of this object.
     * 
     * @return conflict reason name
     * @since  1.3
     */
    public String getName() {
        return myName;
    }

    /**
     * Retunrns a string representation of this object.
     * 
     * @return conflict reason name
     * @since  1.3
     */
    public String toString() {
        return getName();
    }
}
