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
import java.util.Map;

import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * The <b>SVNCommitItem</b> represents a versioned item that is  
 * to be committed to a repository. 
 * 
 * <p>
 * Used to wrap information about a versioned item into a single 
 * object. A commit item can represent either a Working Copy item 
 * (speaking of committing local changes in WC files and directories) 
 * or one that is located in a repository (for example, when deleting 
 * a file/directory right from a repository). 
 * 
 * <p>
 * When you call <b>SVNCommitClient</b>'s {@link SVNCommitClient#doCollectCommitItems(File[], boolean, boolean, org.tmatesoft.svn.core.SVNDepth, boolean, String[]) doCollectCommitItems()}
 * this methods processes the specified paths and collects information
 * on items to be committed in <b>SVNCommitItem</b> objects which are
 * packed into a single <b>SVNCommitPacket</b> object. This object is 
 * returned by the method to the caller.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     SVNCommitPacket
 */
public class SVNCommitItem {

    private SVNRevision myRevision;
    private File myFile;
    private SVNURL myURL;
    private SVNURL myCopyFromURL;
    private SVNNodeKind myKind;
    private boolean myIsAdded;
    private boolean myIsDeleted;
    private boolean myIsPropertiesModified;
    private boolean myIsContentsModified;
    private boolean myIsCopied;
    private boolean myIsLocked;
    private String myPath;
    private SVNWCAccess myWCAccess;
    private SVNRevision myCopyFromRevision;
    private Map myOutgoingProperties;
    
    /**
     * Constructs and initializes an <b>SVNCommitItem</b> object.
     * 
     * @param file                  a WC item's location
     * @param URL                   the item's repository location
     * @param copyFromURL           the repository location of the item's ancestor
     *                              (if the item was or to be copied)
     * @param kind                  the item's node kind
     * @param revision              the item's revision
     * @param copyFromRevision      the revision of the item's ancestor 
     *                              it's copied from
     * @param isAdded               <span class="javakeyword">true</span> if the 
     *                              item is to be added to version control, otherwise
     *                              <span class="javakeyword">false</span>
     * @param isDeleted             <span class="javakeyword">true</span> if the 
     *                              item is to be deleted from version control, otherwise
     *                              <span class="javakeyword">false</span> 
     * @param isPropertiesModified  <span class="javakeyword">true</span> if the 
     *                              item's properties have local changes, otherwise
     *                              <span class="javakeyword">false</span>
     * @param isContentsModified    <span class="javakeyword">true</span> if the 
     *                              item's contents (file contents or directory entries) 
     *                              have local changes, otherwise 
     *                              <span class="javakeyword">false</span>
     * @param isCopied              <span class="javakeyword">true</span> if the 
     *                              item is to be added to version control with history, 
     *                              otherwise <span class="javakeyword">false</span>
     * @param locked                <span class="javakeyword">true</span> if the 
     *                              item is to be locked, otherwise
     *                              <span class="javakeyword">false</span>
     */
    public SVNCommitItem(File file, SVNURL URL, SVNURL copyFromURL,
            SVNNodeKind kind, SVNRevision revision, SVNRevision copyFromRevision,
            boolean isAdded, boolean isDeleted, boolean isPropertiesModified,
            boolean isContentsModified, boolean isCopied, boolean locked) {
        myRevision = revision == null ? SVNRevision.UNDEFINED : revision;
        myCopyFromRevision = copyFromRevision == null ? SVNRevision.UNDEFINED : copyFromRevision;
        myFile = file;
        myURL = URL;
        myCopyFromURL = copyFromURL;
        myKind = kind;
        myIsAdded = isAdded;
        myIsDeleted = isDeleted;
        myIsPropertiesModified = isPropertiesModified;
        myIsContentsModified = isContentsModified;
        myIsCopied = isCopied;
        myIsLocked = locked;
    }
    
    /**
     * Gets the revision of the versioned item . For a WC item it is 
     * the current working revision. 
     * 
     * @return the revision of the item to be committed
     */
    public SVNRevision getRevision() {
        return myRevision;
    }

    /**
     * Gets the revision of the versioned item's ancestor
     * from which the item was copied. 
     * 
     * @return the revision the item was copied from
     */
    public SVNRevision getCopyFromRevision() {
        return myCopyFromRevision;
    }
    
    /**
     * Gets the location of the Working Copy item. 
     * 
     * @return the item's local path
     */
    public File getFile() {
        return myFile;
    }
    
    /**
     * Gets the versioned item's repository location.
     * 
     * @return the item's URL pointing to its repository location
     */
    public SVNURL getURL() {
        return myURL;
    }
    
    /**
     * Gets the repository location of the versioned item's ancestor
     * from which the item was copied. 
     *  
     * @return the URL of the copy source in an {@link org.tmatesoft.svn.core.SVNURL}
     *         representation
     */
    public SVNURL getCopyFromURL() {
        return myCopyFromURL;
    }
    
    /**
     * Gets the node kind of the versioned item.
     * 
     * @return the item's node kind
     */
    public SVNNodeKind getKind() {
        return myKind;
    }
    
    /**
     * Determines if the item is to be added to version control.
     *  
     * @return <span class="javakeyword">true</span> if added, 
     *         otherwise <span class="javakeyword">false</span>
     */
    public boolean isAdded() {
        return myIsAdded;
    }
    
    /**
     * Determines if the item is to be deleted from version control.
     * 
     * @return <span class="javakeyword">true</span> if deleted, 
     *         otherwise <span class="javakeyword">false</span>
     */
    public boolean isDeleted() {
        return myIsDeleted;
    }
    
    /**
     * Determines if the Working Copy item has local edits
     * to properties.
     * 
     * @return <span class="javakeyword">true</span> if the properties 
     *         have local changes, otherwise <span class="javakeyword">false</span>
     */
    public boolean isPropertiesModified() {
        return myIsPropertiesModified;
    }

    /**
     * Determines if the Working Copy item has local edits
     * to its contents. If the item is a file - that is the file contents,
     * a directory - the directory contents (meaning entries). 
     * 
     * @return <span class="javakeyword">true</span> if the contents 
     *         have local changes, otherwise <span class="javakeyword">false</span>
     */
    public boolean isContentsModified() {
        return myIsContentsModified;
    }
    
    /**
     * Determines if the item is to be added to version control with 
     * history.
     * 
     * @return <span class="javakeyword">true</span> if added with
     *         history (copied in other words), otherwise 
     *         <span class="javakeyword">false</span>
     */
    public boolean isCopied() {
        return myIsCopied;
    }
    
    /**
     * Determines whether the item needs to be locked.
     * 
     * @return <span class="javakeyword">true</span> if locked, 
     *         otherwise <span class="javakeyword">false</span>
     */
    public boolean isLocked() {
        return myIsLocked;
    }
    
    /**
     * Gets the item's relevant path. The path is relevant to
     * the Working Copy root.
     * 
     * @return the item's relevant path
     */
    // TODO get rid of this. always use getURL or getFile instead.
    public String getPath() {
        return myPath;
    }
    
    /**
     * Sets the item's relevant path.
     * 
     * @param path the item's path relevant to the Working Copy root
     */
    public void setPath(String path) {
        myPath = path;
    }
    
    /**
     * This method is not intended for users (from an API point of view).
     * @return wc access object 
     */
    public SVNWCAccess getWCAccess() {
        return myWCAccess;
    }

    /**
     * Returns properties to commit.
     * @return properties to commit 
     */
    public Map getOutgoingProperties() {
        return myOutgoingProperties;
    }
    
    void setWCAccess(SVNWCAccess wcAccess) {
        myWCAccess = wcAccess;
    }
    
    void setProperty(String propertyName, SVNPropertyValue propertyValue) {
        myIsPropertiesModified = true;
        Map props = getProperties();
        props.put(propertyName, propertyValue);
    }
    
    private Map getProperties() {
        if (myOutgoingProperties == null) {
            myOutgoingProperties = new SVNHashMap();
        }
        return myOutgoingProperties;
    }

    void setContentsModified(boolean modified) {
        myIsContentsModified = modified;
    }

    void setPropertiesModified(boolean modified) {
        myIsPropertiesModified = modified;
    }
}
