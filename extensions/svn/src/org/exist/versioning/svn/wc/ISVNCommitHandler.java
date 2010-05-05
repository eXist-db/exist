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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;

/**
 * The <b>ISVNCommitHandler</b> should be implemented to 
 * provide an ability to manage commit log messages for items to be committed in
 * a common transaction.
 * 
 * <p>
 * The interface defines the only one method which takes the initial log message
 * and an array of items that are intended for a commit. For example, an implementor's 
 * code can process those items and add some generated additional comment to that one 
 * passed into the method. There could be plenty of scenarios.  
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     DefaultSVNCommitHandler      
 */
public interface ISVNCommitHandler {
    
    /**
     * Handles the incoming initial log message and items intended for a commit and 
     * returns a new commit log message.
     *  
     * @param  message			an initial log message
     * @param  commitables		an array of items to be committed
     * @return					a new log message string or NULL to cancel commit operation.
     * @throws SVNException
     */
    public String getCommitMessage(String message, SVNCommitItem[] commitables) throws SVNException;
 
    /**
     * Handles the incoming revision properties and returns filtered revision properties given the paths 
     * (represented by <code>commitables</code>) collected for committing and the commit log message.
     * 
     * <p>
     * Only the returned filtered revision properties will be set on a new committed revision.
     * 
     * @param  message             log message for commit    
     * @param  commitables         paths to commit
     * @param  revisionProperties  initial revision properties
     * @return                     filtered revision properties
     * @throws SVNException 
     */
    public SVNProperties getRevisionProperties(String message, SVNCommitItem[] commitables, SVNProperties revisionProperties) throws SVNException;
}
