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

import java.io.File;

import org.exist.util.io.Resource;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
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
public class SVNXMLStatusHandler extends AbstractXMLHandler implements ISVNStatusHandler {
    
    private static final String AGAINST_TAG = "against";
    private static final String TARGET_TAG = "target";

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
     * <code>'token'</code> tag.
     */
    public static final String TOKEN_TAG = "token";

    /**
     * <code>'date'</code> tag.
     */
    public static final String DATE_TAG = "date";

    /**
     * <code>'author'</code> tag.
     */
    public static final String AUTHOR_TAG = "author";

    /**
     * <code>'repos-status'</code> tag.
     */
    public static final String REMOTE_STATUS_TAG = "repos-status";

    /**
     * <code>'lock'</code> tag.
     */
    public static final String LOCK_TAG = "lock";

    /**
     * <code>'commit'</code> tag.
     */
    public static final String COMMIT_TAG = "commit";

    /**
     * <code>'wc-status'</code> tag.
     */
    public static final String WC_STATUS_TAG = "wc-status";

    /**
     * <code>'entry'</code> tag.
     */
    public static final String ENTRY_TAG = "entry";

    /**
     * <code>'status'</code> tag.
     */
    public static final String STATUS_TAG = "status";

    /**
     * <code>'revision'</code> attribute.
     */
    public static final String REVISION_ATTR = "revision";

    /**
     * <code>'switched'</code> attribute.
     */
    public static final String SWITCHED_ATTR = "switched";

    /**
     * <code>'copied'</code> attribute.
     */
    public static final String COPIED_ATTR = "copied";

    /**
     * <code>'wc-locked'</code> attribute.
     */
    public static final String WC_LOCKED_ATTR = "wc-locked";

    /**
     * <code>'props'</code> attribute.
     */
    public static final String PROPS_ATTR = "props";

    /**
     * <code>'item'</code> attribute.
     */
    public static final String ITEM_ATTR = "item";

    /**
     * <code>'path'</code> attribute.
     */
    public static final String PATH_ATTR = "path";

    private static final String TRUE = "true";

    private File myTargetPath;
    
    /**
     * Creates a new status handler.
     * 
     * @param saxHandler a <b>ContentHandler</b> to form 
     *                   an XML tree
     */
    public SVNXMLStatusHandler(ContentHandler saxHandler) {
        this(saxHandler, null);
    }    

    /**
     * Creates a new status handler.
     * 
     * @param saxHandler a <b>ContentHandler</b> to form 
     *                   an XML tree
     * @param log        a debug logger
     */
    public SVNXMLStatusHandler(ContentHandler saxHandler, ISVNDebugLog log) {
        super(saxHandler, log);
    }    
    
    /**
     * Begins an XML tree with the target path for which the 
     * status is run. 
     * 
     * @param path a WC target path
     */
    public void startTarget(File path) {
        try {
            myTargetPath = path;
            addAttribute(PATH_ATTR, path.getPath());
            openTag(TARGET_TAG);
        } catch (SAXException e) {
            getDebugLog().logSevere(SVNLogType.DEFAULT, e);
        }
    }

    /**
     * Handles a next <code>status</code> object producing corresponding xml.
     * 
     * @param  status 
     * @throws SVNException 
     */
    public void handleStatus(SVNStatus status) throws SVNException {
        try {
            sendToHandler(status);
        } catch (SAXException th) {
            getDebugLog().logSevere(SVNLogType.DEFAULT, th);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.XML_MALFORMED, th.getLocalizedMessage());
            SVNErrorManager.error(err, th, SVNLogType.DEFAULT);
        }
    }
    
    /**
     * Closes the formatted XML with the revision against which 
     * the status is run. 
     * 
     * @param revision a revision against which the status is run
     */
    public void endTarget(long revision) {
        try {
            myTargetPath = null;
            if (revision >= 0) {
                addAttribute(REVISION_ATTR, revision + "");
                openTag(AGAINST_TAG);
                closeTag(AGAINST_TAG);
            }
            closeTag(TARGET_TAG);
        } catch (SAXException e) {
            getDebugLog().logSevere(SVNLogType.DEFAULT, e);
        }
    }
    
    private void sendToHandler(SVNStatus status) throws SAXException {
        addAttribute(PATH_ATTR, getRelativePath(status.getFile()));
        openTag(ENTRY_TAG);
        addAttribute(PROPS_ATTR, status.getPropertiesStatus().toString());
        addAttribute(ITEM_ATTR, status.getContentsStatus().toString());
        if (status.isLocked()) {
            addAttribute(WC_LOCKED_ATTR, TRUE);
        }
        if (status.isCopied()) {
            addAttribute(COPIED_ATTR, TRUE);
        }
        if (status.isSwitched()) {
            addAttribute(SWITCHED_ATTR, TRUE);
        }
        if (!status.isCopied() && status.getRevision() != null && status.getRevision().getNumber() >= 0) {
            addAttribute(REVISION_ATTR, status.getRevision().toString());
        }
        openTag(WC_STATUS_TAG);
        if (status.getCommittedRevision() != null && status.getCommittedRevision().getNumber() >= 0) {
            addAttribute(REVISION_ATTR, status.getCommittedRevision().toString());
            openTag(COMMIT_TAG);
            addTag(AUTHOR_TAG, status.getAuthor());
            if (status.getCommittedDate() != null) {
                addTag(DATE_TAG, SVNDate.formatDate(status.getCommittedDate()));
            }
            closeTag(COMMIT_TAG);
        }
        if (status.getLocalLock() != null) {
            openTag(LOCK_TAG);
            addTag(TOKEN_TAG, status.getLocalLock().getID());
            addTag(OWNER_TAG, status.getLocalLock().getOwner());
            addTag(COMMENT_TAG, status.getLocalLock().getComment());
            addTag(CREATED_TAG, SVNDate.formatDate(status.getLocalLock().getCreationDate()));
            closeTag(LOCK_TAG);
        }
        closeTag(WC_STATUS_TAG);
        
        if (status.getRemoteContentsStatus() != SVNStatusType.STATUS_NONE || status.getRemotePropertiesStatus() != SVNStatusType.STATUS_NONE ||
                status.getRemoteLock() != null) {
            addAttribute(PROPS_ATTR, status.getRemotePropertiesStatus().toString());
            addAttribute(ITEM_ATTR, status.getRemoteContentsStatus().toString());
            openTag(REMOTE_STATUS_TAG);
            if (status.getRemoteLock() != null) {
                openTag(LOCK_TAG);
                addTag(TOKEN_TAG, status.getRemoteLock().getID());
                addTag(OWNER_TAG, status.getRemoteLock().getOwner());
                addTag(COMMENT_TAG, status.getRemoteLock().getComment());
                addTag(CREATED_TAG, SVNDate.formatDate(status.getRemoteLock().getCreationDate()));
                if (status.getRemoteLock().getExpirationDate() != null) {
                    addTag(EXPIRES_TAG, SVNDate.formatDate(status.getRemoteLock().getExpirationDate()));
                }
                closeTag(LOCK_TAG);
            }
            closeTag(REMOTE_STATUS_TAG);
        }
        closeTag(ENTRY_TAG);
    }
    
    protected String getRelativePath(File path) {
        String fullPath = path.getAbsoluteFile().getAbsolutePath(); 
        if (myTargetPath == null) {
            return fullPath; 
        }
        StringBuffer relativePath = new StringBuffer();
        // collect path till target is met, then prepend target.
        char pathSeparator = Resource.separatorChar;
        boolean targetMeet = false;
        if (!path.getAbsoluteFile().equals(myTargetPath.getAbsoluteFile())) {
            do {
                if (relativePath.length() > 0) {
                    relativePath.insert(0, pathSeparator);
                }
                relativePath = relativePath.insert(0, path.getName());
                path = path.getParentFile();
                if (path != null) {
                    targetMeet = path.getAbsoluteFile().equals(myTargetPath.getAbsoluteFile());
                }
            } while(path !=null && !targetMeet);
        } else {
            return myTargetPath.getPath();
        }

        if (path != null) {
            if (relativePath.length() > 0) {
                relativePath.insert(0, pathSeparator);
            }
            relativePath = relativePath.insert(0, myTargetPath.getPath());
        } else {
            return fullPath;
        }
        return relativePath.toString();
    }
    
    protected String getTargetPath() {
        return getRelativePath(myTargetPath);        
    }
    
    protected String getHeaderName() {
        return STATUS_TAG;
    }
}
