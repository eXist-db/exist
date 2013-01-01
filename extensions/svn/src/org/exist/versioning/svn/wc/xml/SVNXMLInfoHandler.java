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
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNXMLInfoHandler extends AbstractXMLHandler implements ISVNInfoHandler {

    private static final String INFO_TAG = "info";
    private static final String ENTRY_TAG = "entry";
    private static final String REVISION_ATTR = "revision";
    private static final String PATH_ATTR = "path";
    private static final String KIND_ATTR = "kind";
    private static final String URL_TAG = "url";
    private static final String REPOSITORY_TAG = "repository";
    private static final String UUID_TAG = "uuid";
    private static final String ROOT_TAG = "root";
    private static final String WC_INFO_TAG = "wc-info";
    private static final String SCHEDULE_TAG = "schedule";
    private static final String COPY_FROM_URL_TAG = "copy-from-url";
    private static final String COPY_FROM_REVISION_TAG = "copy-from-rev";
    private static final String CHECKSUM_TAG = "checksum";
    private static final String TEXT_TIME_TAG = "text-update";
    private static final String PROP_TIME_TAG = "prop-updated";
    private static final String COMMIT_TAG = "commit";
    private static final String AUTHOR_TAG = "author";
    private static final String DATE_TAG = "date";
    private static final String CONFLICT_TAG = "conflict";
    private static final String OLD_CONFLICT_TAG = "prev-base-file";
    private static final String WRK_CONFLICT_TAG = "prev-wc-file";
    private static final String NEW_CONFLICT_TAG = "cur-base-file";
    private static final String PROP_CONFLICT_TAG = "prop-file";
    private static final String TOKEN_TAG = "token";
    private static final String OWNER_TAG = "owner";
    private static final String COMMENT_TAG = "comment";
    private static final String CREATED_TAG = "created";
    private static final String EXPIRES_TAG = "expires";
    private static final String LOCK_TAG = "lock";
    private static final String DEPTH_TAG = "depth";
    private static final String CHANGELIST_TAG = "changelist";
    
    private File myTargetPath;

    /**
     * Creates a new info handler.
     * 
     * @param contentHandler a <b>ContentHandler</b> to form 
     *                       an XML tree
     */
    public SVNXMLInfoHandler(ContentHandler contentHandler) {
        this(contentHandler, null);
    }

    /**
     * Creates a new info handler.
     * 
     * @param contentHandler a <b>ContentHandler</b> to form 
     *                       an XML tree
     * @param log            a debug logger
     */
    public SVNXMLInfoHandler(ContentHandler contentHandler, ISVNDebugLog log) {
        super(contentHandler, log);
    }
    
    /**
     * Sets the target path what makes all paths be relative to this one. 
     * 
     * @param path target path
     */
    public void setTargetPath(File path) {
        myTargetPath = path;
    }

    protected String getHeaderName() {
        return INFO_TAG;
    }

    /**
     * Handles info producing corresponding xml.
     * 
     * @param  info            info  
     * @throws SVNException 
     */
    public void handleInfo(SVNInfo info) throws SVNException {
        try {
            sendToHandler(info);
        } catch (SAXException e) {
            getDebugLog().logSevere(SVNLogType.DEFAULT, e);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.XML_MALFORMED, e.getMessage());
            SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
        }
    }

    private void sendToHandler(SVNInfo info) throws SAXException {
        addAttribute(KIND_ATTR, info.getKind().toString());
        if (info.getFile() != null) {
            addAttribute(PATH_ATTR, getRelativePath(info.getFile()));
        } else if (info.getPath() != null){
            addAttribute(PATH_ATTR, info.getPath());
        }
        addAttribute(REVISION_ATTR, info.getRevision().toString());
        openTag(ENTRY_TAG);
        if (info.getURL() != null) {
            addTag(URL_TAG, info.getURL().toString());
        }
        SVNURL rootURL = info.getRepositoryRootURL();
        String uuid = info.getRepositoryUUID();
        if (rootURL != null || uuid != null) {
            openTag(REPOSITORY_TAG);
            if (rootURL != null) {
                addTag(ROOT_TAG, rootURL.toString());
            }
            if (uuid != null) {
                addTag(UUID_TAG, uuid);
            }
            closeTag(REPOSITORY_TAG);
        }   
        if (info.getFile() != null) {
            openTag(WC_INFO_TAG);
            String schedule = info.getSchedule();
            if (schedule == null || "".equals(schedule)) {
                schedule = "normal";
            }
            addTag(SCHEDULE_TAG, schedule);
            if (info.getDepth() != null) {
                addTag(DEPTH_TAG, info.getDepth().getName());
            }
            if (info.getCopyFromURL() != null) {
                addTag(COPY_FROM_URL_TAG, info.getCopyFromURL().toString());
            }
            if (info.getCopyFromRevision() != null && info.getCopyFromRevision().isValid()) {
                addTag(COPY_FROM_REVISION_TAG, info.getCopyFromRevision().toString());
            }
            if (info.getTextTime() != null) {
                addTag(TEXT_TIME_TAG, SVNDate.formatDate(info.getTextTime()));
            }
            if (info.getPropTime() != null) {
                addTag(PROP_TIME_TAG, SVNDate.formatDate(info.getPropTime()));
            }
            if (info.getChecksum() != null) {
                addTag(CHECKSUM_TAG, info.getChecksum());
            }
            if (info.getChangelistName() != null) {
                addTag(CHANGELIST_TAG, info.getChangelistName());
            }
            closeTag(WC_INFO_TAG);
        }
        if (info.getAuthor() != null || info.getCommittedRevision().isValid() ||
                info.getCommittedDate() != null) {
            if (info.getCommittedRevision().isValid()) {
                addAttribute(REVISION_ATTR, info.getCommittedRevision().toString());
            }
            openTag(COMMIT_TAG);
            addTag(AUTHOR_TAG, info.getAuthor());
            if (info.getCommittedDate() != null) {
                addTag(DATE_TAG, SVNDate.formatDate(info.getCommittedDate()));
            }
            closeTag(COMMIT_TAG);
        }
        
        if (info.getConflictNewFile() != null || info.getConflictOldFile() != null || info.getConflictWrkFile() != null ||
                info.getPropConflictFile() != null) {
            openTag(CONFLICT_TAG);
            if (info.getConflictOldFile() != null) {
                addTag(OLD_CONFLICT_TAG, info.getConflictOldFile().getName());
            }
            if (info.getConflictWrkFile() != null) {
                addTag(WRK_CONFLICT_TAG, info.getConflictWrkFile().getName());
            }
            if (info.getConflictNewFile() != null) {
                addTag(NEW_CONFLICT_TAG, info.getConflictNewFile().getName());
            }
            if (info.getPropConflictFile() != null) {
                addTag(PROP_CONFLICT_TAG, info.getPropConflictFile().getName());
            }
            closeTag(CONFLICT_TAG);
        }
        
        if (info.getLock() != null) {
            SVNLock lock = info.getLock();
            openTag(LOCK_TAG);
            if (lock.getID() != null) {
                addTag(TOKEN_TAG, lock.getID());
            }
            if (lock.getOwner() != null) {
                addTag(OWNER_TAG, lock.getOwner());
            }
            if (lock.getComment() != null) {
                addTag(COMMENT_TAG, lock.getComment());
            }
            if (lock.getCreationDate() != null) {
                addTag(CREATED_TAG, SVNDate.formatDate(lock.getCreationDate()));
            }
            if (lock.getExpirationDate() != null) {
                addTag(EXPIRES_TAG, SVNDate.formatDate(lock.getExpirationDate()));
            }
            closeTag(LOCK_TAG);
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

}
 