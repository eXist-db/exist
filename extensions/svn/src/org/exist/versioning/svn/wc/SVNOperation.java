/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.exist.versioning.svn.wc;

/**
 * <b>SVNOperation</b> represents the user operation that exposed a conflict.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.3
 */
public class SVNOperation {
    /**
     * Update operation.
     * @since 1.3
     */
    public static final SVNOperation UPDATE = new SVNOperation("update");
    
    /**
     * Switch operation.
     * @since 1.3
     */
    public static final SVNOperation SWITCH = new SVNOperation("switch");
    
    /**
     * Merge operation.
     * @since 1.3
     */
    public static final SVNOperation MERGE = new SVNOperation("merge");
    
    /**
     * None.
     * @since 1.3
     */
    public static final SVNOperation NONE = new SVNOperation("none");
    
    /**
     * Converts a string operation name to an <code>SVNOperation</code> object.
     *  
     * @param operation
     * @return <code>SVNOperation</code> constant or <code>null</code>, if no
     *         <code>SVNOperation</code> constant matches the given name  
     * @since   1.3
     */
    public static SVNOperation fromString(String operation) {
        if (UPDATE.getName().equals(operation)) {
            return UPDATE;
        }
        if (SWITCH.getName().equals(operation)) {
            return SWITCH;
        }
        if (MERGE.getName().equals(operation)) {
            return MERGE;
        }
        if (NONE.getName().equals(operation)) {
            return NONE;
        }
        return null;
    }
    
    private final String myName;

    private SVNOperation(String name) {
        myName = name;
    }

    /**
     * Returns the string representation of this object.
     * @return string representation
     * @since  1.3
     */
    public String getName() {
        return myName;
    }

    /**
     * Returns the string representation of this object.
     * @return string representation
     * @since  1.3
     */
    public String toString() {
        return getName();
    }
}
