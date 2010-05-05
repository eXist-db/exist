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
 * The <b>SVNConflictChoice</b> is an enumeration of constants representing the way in which the conflict 
 * {@link ISVNConflictHandler callback} chooses a course of action.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNConflictChoice {
    /**
     * Constant saying: don't resolve the conflict now. The path will be marked as in a state of conflict.
     */
    public static SVNConflictChoice POSTPONE = new SVNConflictChoice(0);
    /**
     * Constant saying to choose the base version of the file to resolve the conflict here and now.
     */
    public static SVNConflictChoice BASE = new SVNConflictChoice(1);
    /**
     * Constant saying to choose the incoming version of the file to resolve the conflict here and now.
     */
    public static SVNConflictChoice THEIRS_FULL = new SVNConflictChoice(2);
    /**
     * Constant saying to choose the own version of the file to resolve the conflict here and now.
     */
    public static SVNConflictChoice MINE_FULL = new SVNConflictChoice(3);
    /**
     * Constant saying to choose the incoming (for conflicted hunks) version of the file to resolve the conflict here and now.
     */
    public static SVNConflictChoice THEIRS_CONFLICT = new SVNConflictChoice(4);
    /**
     * Constant saying to choose the own (for conflicted hunks) version of the file to resolve the conflict here and now.
     */
    public static SVNConflictChoice MINE_CONFLICT = new SVNConflictChoice(5);
    /**
     * Constant saying to choose the merged version of the file to resolve the conflict here and now.
     */
    public static SVNConflictChoice MERGED = new SVNConflictChoice(6);

    private int myID;

    private SVNConflictChoice (int id) {
        myID = id;
    }

    /**
     * Returns a unique ID number for this object.
     * @return id number
     */
    public int getID() {
        return myID;
    }
}
