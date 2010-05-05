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

import java.io.File;

import org.tmatesoft.svn.core.wc.ISVNCommitParameters;



/**
 * <b>DefaultSVNCommitParameters</b> is the default commit parameters 
 * implementation. 
 * 
 * @version 1.3
 * @since   1.2
 * @author  TMate Software Ltd.
 */
public class DefaultSVNCommitParameters implements ISVNCommitParameters {

    /**
     * Says a committer to skip a missing file.
     * 
     * @param  file a missing file
     * @return      {@link ISVNCommitParameters#SKIP SKIP}
     */
    public Action onMissingFile(File file) {
        return SKIP;
    }

    /**
     * Says a committer to abort the operation.
     * 
     * @param  file a missing directory
     * @return      {@link ISVNCommitParameters#ERROR ERROR}
     */
    public Action onMissingDirectory(File file) {
        return ERROR;
    }

    /**
     * Returns <span class="javakeyword">true</span>.
     * 
     * @param directory working copy directory
     * @return          <span class="javakeyword">true</span>
     */
    public boolean onDirectoryDeletion(File directory) {
        return true;
    }

    /**
     * Returns <span class="javakeyword">true</span>.
     * @param file   working copy file
     * @return <span class="javakeyword">true</span>
     * 
     */
    public boolean onFileDeletion(File file) {
        return true;
    }
}
