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

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * The <b>SVNCopySource</b> class is used to provide copy source information in copy operations.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNCopySource {
    
    private SVNRevision myPegRevision;
    private SVNRevision myRevision;
    private SVNURL myURL;
    private File myPath;
    private boolean myIsCopyContents;
   
    /**
     * Creates a new <code>SVNCopySource</code> object. 
     * 
     * @param pegRevision peg revision where <code>path</code> is valid 
     * @param revision    revision of <code>path</code>
     * @param path        working copy path
     */
    public SVNCopySource(SVNRevision pegRevision, SVNRevision revision, File path) {
        myPegRevision = pegRevision;
        myRevision = revision;
        myPath = path.getAbsoluteFile();
    }

    /**
     * Creates a new <code>SVNCopySource</code> object.
     * 
     * @param pegRevision peg revision where <code>url</code> is valid 
     * @param revision    revision of <code>url</code>
     * @param url         repository url
     */
    public SVNCopySource(SVNRevision pegRevision, SVNRevision revision, SVNURL url) {
        myPegRevision = pegRevision;
        myRevision = revision;
        myURL = url;
    }

    /**
     * Returns the working copy path.
     * @return working copy path; <span class="javakeyword">null</span> if it's a url source.
     */
    public File getFile() {
        return myPath;
    }
    
    /**
     * Returns the peg revision of the source.
     * @return peg revision
     */
    public SVNRevision getPegRevision() {
        return myPegRevision;
    }
    
    /**
     * Returns the revision of the source. 
     * @return  source revision
     */
    public SVNRevision getRevision() {
        return myRevision;
    }
    
    /**
     * Returns the repository url of the source.
     * 
     * @return repository url; <span class="javakeyword">null</span> if it's a local source.  
     */
    public SVNURL getURL() {
        return myURL;
    }
    
    /**
     * Tells if this copy source is url.
     * 
     * @return <span class="javakeyword">true</span> if {@link #getURL()} returns non-<span class="javakeyword">null</span>;
     *         otherwise <span class="javakeyword">false</span> ({@link #getFile() returns non-<span class="javakeyword">null</span>})  
     */
    public boolean isURL() {
        return myURL != null;
    }

    /**
     * Returns the name of this copy source.
     * @return copy source name
     */
    public String getName() {
        if (isURL()) {
            return SVNPathUtil.tail(myURL.getPath());
        } 
        return myPath.getName();
    }
    
    /**
     * Sets whether to expand this copy source to its contents or not. 
     * 
     * @param copyContents   <span class="javakeyword">true</span> to expand; otherwise 
     *                       <span class="javakeyword">false</span>  
     * @see                  #isCopyContents()
     */
    public void setCopyContents(boolean copyContents) {
        myIsCopyContents = copyContents;
    }
    
    /**
     * Tells whether the contents of this copy source should be copied rather than the copy source itself.
     * This is relevant only for directory copy sources. If a user {@link #setCopyContents(boolean) specifies} 
     * to copy contents of a file he will get an {@link org.tmatesoft.svn.core.SVNException}. So, if this copy source represents a 
     * directory and if this method returns <span class="javakeyword">true</span>, children of this copy source 
     * directory will be copied to the target instead of the copy source.    
     * 
     * @return  <span class="javakeyword">true</span> to expand copy source to children
     */
    public boolean isCopyContents() {
        return myIsCopyContents;
    }
}
