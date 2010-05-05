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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;

/**
 * The <b>SVNCommitPacket</b> is a storage for <b>SVNCommitItem</b>
 * objects which represent information on versioned items intended
 * for being committed to a repository.
 * 
 * <p>
 * Used by {@link SVNCommitClient} to collect and hold information on paths that are to be committed.
 * Each <code>SVNCommitPacket</code> is committed in a single transaction.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     SVNCommitItem
 */
public class SVNCommitPacket {
    /**
     * This constant denotes an empty commit items storage (contains no
     * {@link SVNCommitItem} objects).
     */
    public static final SVNCommitPacket EMPTY = new SVNCommitPacket(null, new SVNCommitItem[0], null);

    private SVNCommitItem[] myCommitItems;
    private Map myLockTokens;
    private boolean[] myIsSkipped;
    private boolean myIsDisposed;

    SVNCommitPacket(SVNWCAccess wcAccess, SVNCommitItem[] items, Map lockTokens) {
        myCommitItems = items;
        myLockTokens = lockTokens;
        myIsSkipped = new boolean[items == null ? 0 : items.length];
        myIsDisposed = false;

        if (wcAccess != null) {
            for (int i = 0; i < items.length; i++) {
                if (items[i].getWCAccess() == null) {
                    items[i].setWCAccess(wcAccess);
                }
            }
        }
    }
    
    /**
     * Gets an array of <b>SVNCommitItem</b> objects stored in this
     * object.
     * 
     * @return an array of <b>SVNCommitItem</b> objects containing
     *         info of versioned items to be committed
     */
    public SVNCommitItem[] getCommitItems() {
        return myCommitItems;
    }
    
    /**
     * Sets or unsets a versioned item to be skipped - 
     * whether or not it should be committed. 
     * 
     * 
     * @param item      an item that should be marked skipped
     * @param skipped   if <span class="javakeyword">true</span> the item is
     *                  set to be skipped (a commit operation should skip 
     *                  the item), otherwise - unskipped if it was
     *                  previously marked skipped
     * @see             #isCommitItemSkipped(SVNCommitItem)
     *                  
     */
    public void setCommitItemSkipped(SVNCommitItem item, boolean skipped) {
        int index = getItemIndex(item);
        if (index >= 0 && index < myIsSkipped.length) {
            myIsSkipped[index] = skipped;
        }
    }
    
    /**
     * Determines if an item intended for a commit is set to 
     * be skipped - that is not to be committed.
     * 
     * @param  item  an item to check
     * @return       <span class="javakeyword">true</span> if the item
     *               is set to be skipped, otherwise <span class="javakeyword">false</span>
     * @see          #setCommitItemSkipped(SVNCommitItem, boolean)   
     */
    public boolean isCommitItemSkipped(SVNCommitItem item) {
        int index = getItemIndex(item);
        if (index >= 0 && index < myIsSkipped.length) {
            return myIsSkipped[index];
        }
        return true;
    }
    
    /**
     * Determines if this object is disposed.
     * 
     * @return <span class="javakeyword">true</span> if disposed
     *         otherwise <span class="javakeyword">false</span>
     */
    public boolean isDisposed() {
        return myIsDisposed;
    }
    
    /**
     * Disposes the current object.
     * 
     * @throws SVNException
     */
    public void dispose() throws SVNException {
        try {
            for (int i = 0; i < myCommitItems.length; i++) {
                if (myCommitItems[i] != null && myCommitItems[i].getWCAccess() != null) {
                    myCommitItems[i].getWCAccess().close();
                }
            }
        } finally { 
            myIsDisposed = true;
        }
    }
    
    private int getItemIndex(SVNCommitItem item) {
        for (int i = 0; myCommitItems != null && i < myCommitItems.length; i++) {
            SVNCommitItem commitItem = myCommitItems[i];
            if (commitItem == item) {
                return i;
            }
        }
        return -1;
    }

    Map getLockTokens() {
        return myLockTokens;
    }

    SVNCommitPacket removeSkippedItems() {
        if (this == EMPTY) {
            return EMPTY;
        }
        Collection items = new ArrayList();
        Map lockTokens = myLockTokens == null ? null : new SVNHashMap(myLockTokens);
        for (int i = 0; myCommitItems != null && i < myCommitItems.length; i++) {
            SVNCommitItem commitItem = myCommitItems[i];
            if (!myIsSkipped[i]) {
                items.add(commitItem);
            } else if (lockTokens != null) {
                lockTokens.remove(commitItem.getURL().toString());
            }
        }
        SVNCommitItem[] filteredItems = (SVNCommitItem[]) items.toArray(new SVNCommitItem[items.size()]);
        return new SVNCommitPacket(null, filteredItems, lockTokens);
    }
    
    /**
     * Gives a string representation of this object.
     * 
     * @return a string representing this object.
     */
    public String toString() {
        if (EMPTY == this) {
            return "[EMPTY]";
        }
        StringBuffer result = new StringBuffer();
        result.append("SVNCommitPacket: ");
        for (int i = 0; i < myCommitItems.length; i++) {
            SVNCommitItem commitItem = myCommitItems[i];
            result.append("\n");
            if (commitItem.isAdded()) {
                result.append("A");
            } else if (commitItem.isDeleted()) {
                result.append("D");
            } else if (commitItem.isContentsModified()) {
                result.append("M");
            } else {
                result.append("_");
            }
            if (commitItem.isPropertiesModified()) {
                result.append("M");
            } else {
                result.append(" ");
            }
            result.append(" ");
            if (commitItem.getPath() != null) {
                result.append(commitItem.getPath());
                result.append(" ");
            }
            result.append(commitItem.getFile().getAbsolutePath());
            result.append("\n");
            result.append(commitItem.getRevision());
            result.append(" ");
            result.append(commitItem.getURL());
            if (commitItem.isCopied()) {
                result.append("\n");
                result.append("+");
                result.append(commitItem.getCopyFromURL());
            }
            if (commitItem.isLocked()) {
                result.append("\n");
                result.append("LOCKED");
            }
        }
        return result.toString();
    }
}
