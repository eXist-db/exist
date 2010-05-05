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

import org.tmatesoft.svn.core.SVNException;


/**
 * The <b>ISVNConflictHandler</b> interface defines a callback for resolving conflicts during the application  
 * of a tree delta to a working copy.
 * 
 * Implementations of this callback are free to present the conflict using any user interface. This may include 
 * simple contextual conflicts in a file's text or properties, or more complex 'tree'-based conflcts related to 
 * obstructed additions, deletions, and edits. The callback implementation is free to decide which sorts of 
 * conflicts to handle; it's also free to decide which types of conflicts are automatically resolvable and which 
 * require user interaction.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public interface ISVNConflictHandler {
   
    /**
     * Handles the conflict given the conflict description <code>conflictDescription</code> and returns 
     * 
     * <p/>
     * {@link SVNConflictResult#getConflictChoice()} values of {@link SVNConflictChoice#MINE_CONFLICT} and 
     * {@link SVNConflictChoice#THEIRS_CONFLICT} are not legal for conflicts in binary files or properties.
     * 
     * @param  conflictDescription    describes the exact nature of the conflict, and provides information
     *                                to help resolve it
     * @return                        result for the conflict described by <code>conflictDescription</code>  
     * @throws SVNException 
     */
    public SVNConflictResult handleConflict(SVNConflictDescription conflictDescription) throws SVNException;
}
