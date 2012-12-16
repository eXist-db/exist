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
package org.exist.versioning.svn.internal.wc;

import java.io.File;
import java.io.OutputStream;

import org.exist.util.io.Resource;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNDiffStatusHandler;
import org.tmatesoft.svn.core.wc.SVNDiffStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;



/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNDiffStatusEditor implements ISVNEditor {
    
    private SVNSummarize myCurrentDirSummarize;
    private SVNSummarize myCurrentFileSummarize;

    private SVNRepository myRepository;
    private ISVNDiffStatusHandler myHandler;
    private long myRevision;
    private SVNURL myRootURL;
    private File myAnchor;
    private String myTarget;
    
    public SVNDiffStatusEditor(File anchor, String target, SVNRepository repos, long revision, ISVNDiffStatusHandler handler) {
        myRepository = repos;
        myHandler = handler;
        myRevision = revision;
        myRootURL = repos.getLocation();
        myAnchor = anchor;
        myTarget = target;
    }

    public void openRoot(long revision) throws SVNException {
        myCurrentDirSummarize = new SVNSummarize(null, "", SVNNodeKind.DIR);
    }

    public void targetRevision(long revision) throws SVNException {
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        SVNNodeKind kind = myRepository.checkPath(path, myRevision);
        String statusPath = getStatusPath(path);
        SVNDiffStatus status = new SVNDiffStatus(myAnchor != null ? new Resource(myAnchor, path) : null, myRootURL.appendPath(path, false), statusPath, SVNStatusType.STATUS_DELETED, false, kind);
        myHandler.handleDiffStatus(status);
    }

    private String getStatusPath(String path) {
        String statusPath = path;
        if (myTarget != null && (path.equals(myTarget) || path.startsWith(myTarget + "/"))) {
            statusPath = SVNPathUtil.removeHead(path);
        }
        if (statusPath.startsWith("/")) {
            statusPath = statusPath.substring(1);
        }
        return statusPath;
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirSummarize = new SVNSummarize(myCurrentDirSummarize, path, SVNNodeKind.DIR);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentDirSummarize = new SVNSummarize(myCurrentDirSummarize, path, SVNNodeKind.DIR);
        myCurrentDirSummarize.myType = SVNStatusType.STATUS_ADDED;
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        if (SVNProperty.isRegularProperty(name)) {
            myCurrentDirSummarize.myPropChanged = true;
        }
    }

    public void closeDir() throws SVNException {
        myHandler.handleDiffStatus(myCurrentDirSummarize.toStatus());
        myCurrentDirSummarize = myCurrentDirSummarize.myParent;
    }

    public void openFile(String path, long revision) throws SVNException {
        myCurrentFileSummarize = new SVNSummarize(myCurrentDirSummarize, path, SVNNodeKind.FILE);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentFileSummarize = new SVNSummarize(myCurrentDirSummarize, path, SVNNodeKind.FILE);
        myCurrentFileSummarize.myType = SVNStatusType.STATUS_ADDED;
    }

    public void changeFileProperty(String path, String name, SVNPropertyValue value) throws SVNException {
        if (SVNProperty.isRegularProperty(name)) {
            myCurrentFileSummarize.myPropChanged = true;
        }
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        if (myCurrentFileSummarize.myType != SVNStatusType.STATUS_ADDED) {
            myCurrentFileSummarize.myType = SVNStatusType.STATUS_MODIFIED;
        }
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        myHandler.handleDiffStatus(myCurrentFileSummarize.toStatus());
        myCurrentFileSummarize = null;
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (myCurrentDirSummarize != null) {
            myHandler.handleDiffStatus(myCurrentDirSummarize.toStatus());
        }
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    private class SVNSummarize {
        
        public SVNSummarize(SVNSummarize parent, String path, SVNNodeKind kind) {
            myKind = kind;
            myType = SVNStatusType.STATUS_NONE;
            myParent = parent;
            myFile = myAnchor != null ? new Resource(myAnchor, path) : null;
            myPath = getStatusPath(path);
        }
        
        public SVNDiffStatus toStatus() throws SVNException {
            return new SVNDiffStatus(myFile, myRootURL.appendPath(myPath, false), myPath, myType, myPropChanged, myKind);
        }
        
        private File myFile;
        private String myPath;
        private SVNNodeKind myKind;
        private SVNStatusType myType;
        private boolean myPropChanged; 
        private SVNSummarize myParent;
    }

}
