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
 * The <b>SVNConflictAction</b> represents the type of action being attempted on an object which leads to 
 * a conflict.  
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNConflictAction {
    /**
     * Constant representing an attempt to change text or props.
     */
    public static final SVNConflictAction EDIT = new SVNConflictAction("edited");
    /**
     * Constant representing an attempt to add an object.
     */
    public static final SVNConflictAction ADD = new SVNConflictAction("added");
    /**
     * Constant representing an attempt to delete an object.
     */
    public static final SVNConflictAction DELETE = new SVNConflictAction("deleted");

    /**
     * Converts string to an <code>SVNConflictAction</code> object when possible.
     * @param   action    action name
     * @return  <code>SVNConflictAction</code> object which {@link #getName()} 
     *          equals to <code>action</code>; <code>null</code> in case <code>action</code>
     *          matches no <code>SVNConflictAction</code> constant 
     * @since   1.3
     */
    public static SVNConflictAction fromString(String action) {
        if (EDIT.getName().equals(action)) {
            return EDIT;
        }
        if (ADD.getName().equals(action)) {
            return ADD;
        }
        if (DELETE.getName().equals(action)) {
            return DELETE;
        }
        return null;
    }

    private final String myName;

    private SVNConflictAction(String name) {
        myName = name;
    }

    /**
     * Returns the string representation of this action.
     * @return string representation
     * @since  1.3
     */
    public String getName() {
        return myName;
    }

    /**
     * Returns the string representation of this action.
     * @return string representation
     * @since 1.3
     */
    public String toString() {
        return getName();
    }
}
