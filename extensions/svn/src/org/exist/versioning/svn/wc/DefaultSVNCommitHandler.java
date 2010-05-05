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
 * This is a default implementation for <b>ISVNCommitHandler</b>.
 * 
 * <p>
 * Since methods of those <b>SVN</b>*<b>Client</b> 
 * classes that can initiate a commit operation use <b>ISVNCommitHandler</b> 
 * to process user's commit log messages there should be a default implementation. If no
 * special implementation of <b>ISVNCommitHandler</b> is provided into those 
 * classes then <b>DefaultSVNCommitHandler</b> is the one that is used by default.
 * 
 * @version 1.3
 * @since   1.2
 * @author  TMate Software Ltd.
 * @see		ISVNCommitHandler
 */
public class DefaultSVNCommitHandler implements ISVNCommitHandler {
    /**
     * Returns the <code>message</code> itself without any modifications to it 
     * or <code>""</code> if the <code>message</code> is <span class="javakeyword">null</span>.
     * 
     * <p>
     * In other words this method does nothing except of replacing <span class="javakeyword">null</span>
     * for <code>""</code>.
     * 
     * @param  message			a user's initial commit log message 
     * @param  commitables		an array of <b>SVNCommitItem</b> objects
     * 							that represent Working Copy items which have local modifications
     * 							and so need to be committed to the repository
     * @return 					the user's initial commit log message or <code>""</code>
     * 							if the message is <span class="javakeyword">null</span>
     * @throws SVNException  
     */
    public String getCommitMessage(String message, SVNCommitItem[] commitables)
            throws SVNException {
        return message == null ? "" : message;
    }

    /**
     * Returns <code>revisionProperties</code> as is if it's not <span class="javakeyword">null</span>, or
     * an empty {@link SVNProperties} object if <code>revisionProperties</code> is <span class="javakeyword">null</span>.
     * 
     * @param  message                 log message for commit
     * @param  commitables             paths to commit
     * @param  revisionProperties      initial revision properties to set
     * @return                         <code>revisionProperties</code> itself or an empty {@link SVNProperties} 
     *                                 object if <code>revisionProperties</code> is <span class="javakeyword">null</span> 
     * @throws SVNException 
     */
    public SVNProperties getRevisionProperties(String message, SVNCommitItem[] commitables, SVNProperties revisionProperties) throws SVNException {
        return revisionProperties == null ? new SVNProperties() : revisionProperties;
    }
}
