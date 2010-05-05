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
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminAreaInfo;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.exist.versioning.svn.wc.ISVNOptions;
import org.exist.versioning.svn.wc.ISVNStatusHandler;
import org.exist.versioning.svn.wc.SVNStatus;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.SVNRevision;



/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNRemoteStatusEditor extends SVNStatusEditor implements ISVNEditor, ISVNStatusHandler {
    
    private boolean myIsRootOpen;
    private SVNStatus myAnchorStatus;
    
    private DirectoryInfo myDirectoryInfo;
    private FileInfo myFileInfo;

    public SVNRemoteStatusEditor(ISVNOptions options, SVNWCAccess wcAccess, SVNAdminAreaInfo info, boolean noIgnore, boolean reportAll, SVNDepth depth, ISVNStatusHandler handler) throws SVNException {
        super(options, wcAccess, info, noIgnore, reportAll, depth, handler);
        myAnchorStatus = createStatus(info.getAnchor().getRoot());
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpen = true;
        myDirectoryInfo = new DirectoryInfo(null, null);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        File file = getAnchor().getFile(path);
        SVNEntry entry = getWCAccess().getVersionedEntry(file, false);

        File dirPath;
        String name;
        if (entry.getKind() == SVNNodeKind.DIR) {
            dirPath = file;
            name = "";
        } else {
            dirPath = file.getParentFile();
            name = file.getName();
        }
        SVNAdminArea dir = null;
        try {
            dir = getWCAccess().retrieve(dirPath);
        } catch (SVNException e) {
            SVNFileType type = SVNFileType.getType(file);
            if (type == SVNFileType.NONE && e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                return;
            }
            throw e;
        }
        
        if (dir.getEntry(name, false) != null) {
            tweakStatusHash(myDirectoryInfo, null, file, SVNStatusType.STATUS_DELETED, SVNStatusType.STATUS_NONE, null, SVNRevision.create(revision));
            // set entry node kind
            SVNStatus status = (SVNStatus) myDirectoryInfo.myChildrenStatuses.get(file);
            if (status != null) {
                status.setRemoteStatus(null, null, null, dir.getEntry(name, false).getKind());
            }
        }
        if (myDirectoryInfo.myParent != null && !hasTarget()) {
            tweakStatusHash(myDirectoryInfo.myParent, myDirectoryInfo, myDirectoryInfo.myPath, SVNStatusType.STATUS_MODIFIED, SVNStatusType.STATUS_NONE, null, null);
        } else if (!hasTarget() && myDirectoryInfo.myParent == null) {
            myDirectoryInfo.myIsContentsChanged = true;
        }
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myDirectoryInfo = new DirectoryInfo(path, myDirectoryInfo);
        myDirectoryInfo.myIsAdded = true;
        myDirectoryInfo.myParent.myIsContentsChanged = true;
    }

    public void openDir(String path, long revision) throws SVNException {
        myDirectoryInfo = new DirectoryInfo(path, myDirectoryInfo);
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        if (!name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)
                && !name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            myDirectoryInfo.myIsPropertiesChanged = true;
        }
        if (SVNProperty.COMMITTED_REVISION.equals(name) && value != null) {
            try {
                long number = Long.parseLong(value.getString());
                myDirectoryInfo.myRemoteRevision = SVNRevision.create(number);
            } catch (NumberFormatException nfe) {
                myDirectoryInfo.myRemoteRevision = SVNRevision.UNDEFINED;
            }
        } else if (SVNProperty.COMMITTED_DATE.equals(name) && value != null) {
            myDirectoryInfo.myRemoteDate = SVNDate.parseDate(value.getString());
        } else if (SVNProperty.LAST_AUTHOR.equals(name) && value != null) {
            myDirectoryInfo.myRemoteAuthor = value.getString();
        }
    }

    public void closeDir() throws SVNException {
        DirectoryInfo parent = myDirectoryInfo.myParent;
        if (myDirectoryInfo.myIsAdded || myDirectoryInfo.myIsPropertiesChanged || myDirectoryInfo.myIsContentsChanged 
                || (myDirectoryInfo.myRemoteRevision != null && myDirectoryInfo.myRemoteRevision != SVNRevision.UNDEFINED)) {
            SVNStatusType contentsStatus;
            SVNStatusType propertiesStatus;
            if (myDirectoryInfo.myIsAdded) {
                contentsStatus = SVNStatusType.STATUS_ADDED;
                propertiesStatus = myDirectoryInfo.myIsPropertiesChanged ? SVNStatusType.STATUS_ADDED : SVNStatusType.STATUS_NONE;
            } else {
                contentsStatus = myDirectoryInfo.myIsContentsChanged ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NONE;
                propertiesStatus = myDirectoryInfo.myIsPropertiesChanged ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NONE;
            }
            if (parent != null) {
                tweakStatusHash(parent, myDirectoryInfo, myDirectoryInfo.myPath, contentsStatus, propertiesStatus, null, null);
            }
        }
        if (parent != null && myDirectoryInfo.myDepth != SVNDepth.EXCLUDE) {
            boolean wasDeleted = false;
            SVNStatus dirStatus = (SVNStatus) parent.myChildrenStatuses.get(myDirectoryInfo.myPath);
            if (dirStatus != null && 
                    (dirStatus.getRemoteContentsStatus() == SVNStatusType.STATUS_DELETED || 
                    dirStatus.getRemoteContentsStatus() == SVNStatusType.STATUS_REPLACED)) {
                wasDeleted = true;
            }
            handleStatusHash(dirStatus != null ? dirStatus.getEntry() : null, myDirectoryInfo.myChildrenStatuses, wasDeleted, myDirectoryInfo.myDepth);
            if (isSendableStatus(dirStatus)) {
                getDefaultHandler().handleStatus(dirStatus);
            }
            parent.myChildrenStatuses.remove(myDirectoryInfo.myPath);
        } else if (parent == null) {
            if (hasTarget()) {
                File targetPath = getAnchor().getFile(getAdminAreaInfo().getTargetName());
                SVNStatus tgtStatus = (SVNStatus) myDirectoryInfo.myChildrenStatuses.get(targetPath);
                if (tgtStatus != null) {
                    if (tgtStatus.getKind() == SVNNodeKind.DIR) {
                        SVNAdminArea dir = getWCAccess().retrieve(targetPath);
                        getDirStatus(null, dir, null, getDepth(), isReportAll(), isNoIgnore(), null, true, getDefaultHandler());
                    } 
                    if (isSendableStatus(tgtStatus)) {
                        getDefaultHandler().handleStatus(tgtStatus);
                    }
                }
            } else {
                handleStatusHash(myAnchorStatus.getEntry(), myDirectoryInfo.myChildrenStatuses, false, getDepth());
                if (myDirectoryInfo != null && myDirectoryInfo.myParent == null) {
                    tweakAnchorStatus(myDirectoryInfo);
                }
                if (isSendableStatus(myAnchorStatus)) {
                    getDefaultHandler().handleStatus(myAnchorStatus);
                }
            }
        }
        myDirectoryInfo = myDirectoryInfo.myParent;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myFileInfo = new FileInfo(myDirectoryInfo, path, true);
        myDirectoryInfo.myIsContentsChanged = true;
    }

    public void openFile(String path, long revision) throws SVNException {
        myFileInfo = new FileInfo(myDirectoryInfo, path, false);
    }

    public void changeFileProperty(String path, String name, SVNPropertyValue value) throws SVNException {
        if (!name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)
                && !name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            myFileInfo.myIsPropertiesChanged = true;
        }
        if (SVNProperty.COMMITTED_REVISION.equals(name) && value != null) {
            try {
                long number = Long.parseLong(value.getString());
                myFileInfo.myRemoteRevision = SVNRevision.create(number);
            } catch (NumberFormatException nfe) {
                myFileInfo.myRemoteRevision = SVNRevision.UNDEFINED;
            }
        } else if (SVNProperty.COMMITTED_DATE.equals(name) && value != null) {
            myFileInfo.myRemoteDate = SVNDate.parseDate(value.getString());
        } else if (SVNProperty.LAST_AUTHOR.equals(name) && value != null) {
            myFileInfo.myRemoteAuthor = value.getString();
        }
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        myFileInfo.myIsContentsChanged = true;
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        if (!(myFileInfo.myIsAdded || myFileInfo.myIsPropertiesChanged || myFileInfo.myIsContentsChanged)) {
            return;
        }
        SVNStatusType contentsStatus;
        SVNStatusType propertiesStatus;
        SVNLock remoteLock = null;
        
        if (myFileInfo.myIsAdded) {
            contentsStatus = SVNStatusType.STATUS_ADDED;
            propertiesStatus = myFileInfo.myIsPropertiesChanged ? SVNStatusType.STATUS_ADDED : SVNStatusType.STATUS_NONE;
        } else {
            contentsStatus = myFileInfo.myIsContentsChanged ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NONE;
            propertiesStatus = myFileInfo.myIsPropertiesChanged ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NONE;
            remoteLock = getLock(myFileInfo.myURL);
        }
        tweakStatusHash(myFileInfo, myFileInfo.myPath, contentsStatus, propertiesStatus, remoteLock);
        myFileInfo = null;
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (myIsRootOpen) {
            cleanup();
        } else {
            super.closeEdit();
        }
        return new SVNCommitInfo(getTargetRevision(), null, null);
    }

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }
    
    private void handleStatusHash(SVNEntry dirEntry, Map hash, boolean deleted, SVNDepth depth) throws SVNException {
        ISVNStatusHandler handler = deleted ? this : getDefaultHandler();
        for(Iterator paths = hash.keySet().iterator(); paths.hasNext();) {
            File path = (File) paths.next();
            SVNStatus status = (SVNStatus) hash.get(path);
            
            if (status.getContentsStatus() != SVNStatusType.STATUS_OBSTRUCTED &&
                status.getContentsStatus() != SVNStatusType.STATUS_MISSING &&
                status.getEntry() != null && status.getKind() == SVNNodeKind.DIR && 
                (depth == SVNDepth.UNKNOWN || depth == SVNDepth.IMMEDIATES || 
                 depth == SVNDepth.INFINITY)) {
                SVNAdminArea dir = getWCAccess().retrieve(path);
                SVNDepth depthMinusOne = depth;
                if (depthMinusOne == SVNDepth.IMMEDIATES) {
                    depthMinusOne = SVNDepth.EMPTY;
                }
                getDirStatus(dirEntry, dir, null, depthMinusOne, isReportAll(), isNoIgnore(), null, true, handler);
            }
            if (deleted) {
                status.setRemoteStatus(SVNStatusType.STATUS_DELETED, null, null, null);
            }
            if (isSendableStatus(status)) {
                handler.handleStatus(status);
            }
        }
    }
    
    private void tweakStatusHash(FileInfo fileInfo, File path, SVNStatusType text, SVNStatusType props, SVNLock lock) throws SVNException {
        Map hash = fileInfo.myParent.myChildrenStatuses;
        SVNStatus status = (SVNStatus) hash.get(fileInfo.myPath);
        if (status == null) {
            if (text != SVNStatusType.STATUS_ADDED) {
                return;
            }
            status = createStatus(path);
            hash.put(fileInfo.myPath, status);
        }
        if (text == SVNStatusType.STATUS_ADDED && status.getRemoteContentsStatus() == SVNStatusType.STATUS_DELETED) {
            text = SVNStatusType.STATUS_REPLACED;
        }
        status.setRemoteStatus(fileInfo.myURL, text, props, lock, fileInfo.myRemoteKind, fileInfo.myRemoteRevision, fileInfo.myRemoteDate, fileInfo.myRemoteAuthor);
    }

    private void tweakStatusHash(DirectoryInfo dirInfo, DirectoryInfo childDir, File path, SVNStatusType text, SVNStatusType props, SVNLock lock, SVNRevision revision) throws SVNException {
        Map hash = dirInfo.myChildrenStatuses;
        SVNStatus status = (SVNStatus) hash.get(path);
        if (status == null) {
            if (text != SVNStatusType.STATUS_ADDED) {
                return;
            }
            status = createStatus(path);
            hash.put(path, status);
        }
        if (text == SVNStatusType.STATUS_ADDED && status.getRemoteContentsStatus() == SVNStatusType.STATUS_DELETED) {
            text = SVNStatusType.STATUS_REPLACED;
        }
        if (text == SVNStatusType.STATUS_DELETED) {
            if (revision == SVNRevision.UNDEFINED) {
                revision = dirInfo.myRemoteRevision;
            }
            status.setRemoteStatus(dirInfo.myURL, text, props, lock, SVNNodeKind.NONE, revision, null, null);
        } else if (childDir == null) {
            status.setRemoteStatus(dirInfo.myURL, text, props, lock, dirInfo.myRemoteKind, dirInfo.myRemoteRevision, dirInfo.myRemoteDate, dirInfo.myRemoteAuthor);
        } else {
            status.setRemoteStatus(childDir.myURL, text, props, lock, childDir.myRemoteKind, childDir.myRemoteRevision, childDir.myRemoteDate, childDir.myRemoteAuthor);
        }
    }
    
    private void tweakAnchorStatus(DirectoryInfo anchorInfo) {
        if (anchorInfo != null && (anchorInfo.myIsContentsChanged || anchorInfo.myIsPropertiesChanged)) {
            SVNStatusType text = anchorInfo.myIsContentsChanged ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NONE;
            SVNStatusType props = anchorInfo.myIsPropertiesChanged ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NONE;
            myAnchorStatus.setRemoteStatus(myDirectoryInfo.myURL, text, props, null, SVNNodeKind.DIR,
                    myDirectoryInfo.myRemoteRevision, myDirectoryInfo.myRemoteDate, myDirectoryInfo.myRemoteAuthor);
        }
    }
    
    private boolean isSendableStatus(SVNStatus status) {
        if (status == null) {
            return false;
        }
        if (status.getRemoteContentsStatus() != SVNStatusType.STATUS_NONE) {
            return true;
        }
        if (status.getRemotePropertiesStatus() != SVNStatusType.STATUS_NONE) {
            return true;
        }
        if (status.getRemoteLock() != null) {
            return true;
        }
        if (status.getContentsStatus() == SVNStatusType.STATUS_IGNORED && !isNoIgnore()) {
            return false;
        }
        if (isReportAll()) {
            return true;
        }
        if (status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED) {
            return true;
        }
        if (status.getContentsStatus() != SVNStatusType.STATUS_NONE && status.getContentsStatus() != SVNStatusType.STATUS_NORMAL) {
            return true;
        }
        if (status.getPropertiesStatus() != SVNStatusType.STATUS_NONE && status.getPropertiesStatus() != SVNStatusType.STATUS_NORMAL) {
            return true;
        }
        if (status.getTreeConflict() != null) {
            return true;
        }
        return status.isLocked() || status.isSwitched() || 
        status.getLocalLock() != null || status.getChangelistName() != null || 
        status.isFileExternal();
    }
    
    private SVNStatus createStatus(File path) throws SVNException {
        SVNEntry entry = getWCAccess().getEntry(path, false);
        SVNEntry parentEntry = null;
        if (entry != null) {
            SVNAdminArea parentDir = getWCAccess().getAdminArea(path.getParentFile());
            if (parentDir != null) {
                parentEntry = getWCAccess().getEntry(path.getParentFile(), false);
            }
        }
        return assembleStatus(path, entry != null ? getWCAccess().probeRetrieve(path) : null, entry, 
                parentEntry, SVNNodeKind.UNKNOWN, false, true, false);
    }
    
    public void handleStatus(SVNStatus status) throws SVNException {
        status.setContentsStatus(SVNStatusType.STATUS_DELETED);
        getDefaultHandler().handleStatus(status);
    }
    
    private class DirectoryInfo implements ISVNStatusHandler {
        
        public DirectoryInfo(String path, DirectoryInfo parent) throws SVNException {
            myParent = parent;
            if (myParent != null) {
                myPath = getAnchor().getFile(path);
            } else {
                myPath = getAnchor().getRoot();
            }
            myName = path != null ? SVNPathUtil.tail(path) : null;
            myChildrenStatuses = new TreeMap();
            myURL = computeURL();
            myRemoteRevision = SVNRevision.UNDEFINED;
            myRemoteKind = SVNNodeKind.DIR;

            if (myParent != null) {
                if (myParent.myDepth == SVNDepth.IMMEDIATES) {
                    myDepth = SVNDepth.EMPTY;
                } else if (myParent.myDepth == SVNDepth.FILES || myParent.myDepth == SVNDepth.EMPTY) {
                    myDepth = SVNDepth.EXCLUDE;
                } else if (myParent.myDepth == SVNDepth.UNKNOWN) {
                    myDepth = SVNDepth.UNKNOWN;
                } else {
                    myDepth = SVNDepth.INFINITY;
                }
            } else {
                myDepth = getDepth();
            }
            // this dir's status in parent.
            SVNStatus parentStatus = null;
            if (myParent != null) {
                parentStatus = (SVNStatus) myParent.myChildrenStatuses.get(myPath);
            } else {
                parentStatus = myAnchorStatus;
            }
            if (parentStatus != null) {
                SVNStatusType textStatus = parentStatus.getContentsStatus();
                if (textStatus != SVNStatusType.STATUS_UNVERSIONED &&
                        textStatus != SVNStatusType.STATUS_MISSING &&
                        textStatus != SVNStatusType.STATUS_OBSTRUCTED &&
                        textStatus != SVNStatusType.STATUS_EXTERNAL &&
                        textStatus != SVNStatusType.STATUS_IGNORED &&
                        parentStatus.getKind() == SVNNodeKind.DIR && 
                        (myDepth == SVNDepth.UNKNOWN || 
                         myDepth == SVNDepth.FILES || 
                         myDepth == SVNDepth.IMMEDIATES || 
                         myDepth == SVNDepth.INFINITY)) {
                    SVNAdminArea dir = getWCAccess().getAdminArea(myPath);
                    if (dir != null) {
                        getDirStatus(null, dir, null, SVNDepth.IMMEDIATES, true, true, null, true, this);
                        SVNStatus thisDirStatus = (SVNStatus)myChildrenStatuses.get(myPath);
                        if (thisDirStatus != null && thisDirStatus.getEntry() != null && (
                                myDepth == SVNDepth.UNKNOWN || 
                                myDepth.compareTo(parentStatus.getEntry().getDepth()) > 0)) {
                            myDepth = thisDirStatus.getEntry().getDepth();
                        }
                    }
                }
            }       
        }

        private SVNURL computeURL() throws SVNException {
            if (myURL != null) {
                return myURL;
            }
            if (myName == null) {
                return myAnchorStatus.getURL();
            }
            SVNStatus status = (SVNStatus) myParent.myChildrenStatuses.get(myPath);
            if (status != null && status.getEntry() != null && status.getEntry().getSVNURL() != null) {
                return status.getEntry().getSVNURL();
            }
            SVNURL url = myParent.computeURL();
            return url != null ? url.appendPath(myName, false) : null;
        }

        public void handleStatus(SVNStatus status) throws SVNException {
            myChildrenStatuses.put(status.getFile(), status);
        }

        public File myPath;
        public String myName;
        public SVNURL myURL;
        public DirectoryInfo myParent;
        public SVNDepth myDepth;
        public SVNRevision myRemoteRevision;
        public Date myRemoteDate;
        public String myRemoteAuthor;
        public SVNNodeKind myRemoteKind;
        
        public boolean myIsAdded;
        public boolean myIsPropertiesChanged;
        public boolean myIsContentsChanged;
        
        public Map myChildrenStatuses;
    }

    private class FileInfo {

        public FileInfo(DirectoryInfo parent, String path, boolean added) throws SVNException {
            myPath = getAnchor().getFile(path);
            myName = myPath.getName();
            myParent = parent;
            myURL = myParent.computeURL().appendPath(myName, false);
        
            myRemoteRevision = SVNRevision.UNDEFINED;
            myRemoteKind = SVNNodeKind.FILE;
            
            myIsAdded = added;
        }

        public DirectoryInfo myParent;
        public File myPath;
        public String myName;
        public SVNURL myURL;
        
        public boolean myIsAdded;
        public boolean myIsContentsChanged;
        public boolean myIsPropertiesChanged;
        
        public SVNRevision myRemoteRevision;
        public Date myRemoteDate;
        public String myRemoteAuthor;
        public SVNNodeKind myRemoteKind;
    }
}