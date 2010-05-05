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
package org.exist.versioning.svn.wc.xml;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;


/**
 * This is an implementation of the <b>ISVNStatusHandler</b> interface 
 * that writes XML formatted status information to a specified 
 * <b>ContentHandler</b>. 
 *  
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNXMLDirEntryHandler extends AbstractXMLHandler implements ISVNDirEntryHandler, Comparator {
    /**
     * <code>'expires'</code> tag.
     */
    public static final String EXPIRES_TAG = "expires";
    
    /**
     * <code>'created'</code> tag.
     */
    public static final String CREATED_TAG = "created";

    /**
     * <code>'comment'</code> tag.
     */
    public static final String COMMENT_TAG = "comment";
    
    /**
     * <code>'owner'</code> tag.
     */
    public static final String OWNER_TAG = "owner";

    /**
     * <code>'tag'</code> tag.
     */
    public static final String TOKEN_TAG = "token";

    /**
     * <code>'lock'</code> tag.
     */
    public static final String LOCK_TAG = "lock";

    /**
     * <code>'path'</code> attribute.
     */
    public static final String PATH_ATTR = "path";

    /**
     * <code>'revision'</code> attribute.
     */
    public static final String REVISION_ATTR = "revision";
    
    /**
     * <code>'lists'</code> tag.
     */
    public static final String LISTS_TAG = "lists";

    /**
     * <code>'list'</code> tag.
     */
    public static final String LIST_TAG = "list";

    /**
     * <code>'entry'</code> tag.
     */
    public static final String ENTRY_TAG = "entry";

    /**
     * <code>'name'</code> tag.
     */
    public static final String NAME_TAG = "name";

    /**
     * <code>'size'</code> tag.
     */
    public static final String SIZE_TAG = "size";

    /**
     * <code>'commit'</code> tag.
     */
    public static final String COMMIT_TAG = "commit";

    /**
     * <code>'date'</code> tag.
     */
    public static final String DATE_TAG = "date";

    /**
     * <code>'author'</code> tag.
     */
    public static final String AUTHOR_TAG = "author";

    private Collection myDirEntries;
    
    /**
     * Creates a new handler.
     * 
     * @param saxHandler a <b>ContentHandler</b> to form 
     *                   an XML tree
     */
    public SVNXMLDirEntryHandler(ContentHandler saxHandler) {
        this(saxHandler, null);
    }    

    /**
     * Creates a new handler.
     * 
     * @param saxHandler a <b>ContentHandler</b> to form 
     *                   an XML tree
     * @param log        a debug logger
     */
    public SVNXMLDirEntryHandler(ContentHandler saxHandler, ISVNDebugLog log) {
        super(saxHandler, log);
    }    
    
    /**
     * Begins an XML tree with the target path for which the 
     * status is run. 
     * 
     * @param path a WC target path or URL
     */
    public void startTarget(String path) {
        myDirEntries = new TreeSet(this);
        try {
            addAttribute(PATH_ATTR, path == null || path.length() == 9 ? "." : path);
            openTag(LIST_TAG);
        } catch (SAXException e) {
            getDebugLog().logSevere(SVNLogType.DEFAULT, e);
        }
    }

    /**
     * Handles a next dir <code>entry</code>.
     * 
     * @param  entry        dir entry 
     * @throws SVNException 
     */
    public void handleDirEntry(SVNDirEntry entry) throws SVNException {
        myDirEntries.add(entry);
    }
    
    /**
     * Closes the formatted XML output. 
     *
     */
    public void endTarget() {
        try {
            for (Iterator ents = myDirEntries.iterator(); ents.hasNext();) {
                SVNDirEntry entry = (SVNDirEntry) ents.next();
                sendToHandler(entry);
            }
            myDirEntries = null;
            closeTag(LIST_TAG);
        } catch (SAXException e) {
            getDebugLog().logSevere(SVNLogType.DEFAULT, e);
        }
    }
    
    private void sendToHandler(SVNDirEntry entry) throws SAXException {
        if ("".equals(entry.getRelativePath())) {
            if (entry.getKind() == SVNNodeKind.DIR) {
                return;
            }
        }
        openTag(ENTRY_TAG);
        addTag(NAME_TAG, entry.getRelativePath());
        if (entry.getKind() == SVNNodeKind.FILE) {
            addTag(SIZE_TAG, entry.getSize() + "");
        }
        
        addAttribute(REVISION_ATTR, entry.getRevision() + "");
        openTag(COMMIT_TAG);
        addTag(AUTHOR_TAG, entry.getAuthor());
        addTag(DATE_TAG, SVNDate.formatDate(entry.getDate()));
        closeTag(COMMIT_TAG);
        SVNLock lock = entry.getLock();
        if (lock != null) {
            openTag(LOCK_TAG);
            addTag(TOKEN_TAG, lock.getID());
            addTag(OWNER_TAG, lock.getOwner());
            addTag(COMMENT_TAG, lock.getComment());
            addTag(CREATED_TAG, SVNDate.formatDate(lock.getCreationDate()));
            if (lock.getExpirationDate() != null && lock.getExpirationDate().getTime() > 0) {
                addTag(EXPIRES_TAG, SVNDate.formatDate(lock.getExpirationDate()));
            }
            closeTag(LOCK_TAG);
        }
        closeTag(ENTRY_TAG);
    }
    
    protected String getHeaderName() {
        return LISTS_TAG;
    }
    
    /**
     * Compares two objects.
     * 
     * @param  o1 the first object to compare
     * @param  o2 the second object to compare
     * @return    0 if objects are equal; -1 if <code>o1</code> is 
     *            <span class="javakeyword">null</span> or if both 
     *            <code>o1</code> and <code>o2</code> are <b>SVNDirEntry</b> 
     *            objects and the relative path of the first object is 
     *            lexicographically less than that of the second one; 1 otherwise 
     */
    public int compare(Object o1, Object o2) {
        if (o1 == o2) {
            return 0;
        }
        SVNDirEntry e1 = (SVNDirEntry) o1;
        SVNDirEntry e2 = (SVNDirEntry) o2;
        if (e1 == null) {
            return -1;
        } else if (e2 == null) {
            return 1;
        }
        return SVNPathUtil.PATH_COMPARATOR.compare(e1.getRelativePath(), e2.getRelativePath());
    }
}
