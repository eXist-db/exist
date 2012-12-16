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
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.exist.versioning.svn.wc.SVNEvent;
import org.exist.versioning.svn.wc.SVNEventAction;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNReusableEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNRemoteDiffEditor implements ISVNReusableEditor {

    protected SVNRepository myRepos;
    protected long myRevision1;
    protected long myRevision2;
    protected File myTarget;
    protected SVNAdminArea myAdminArea;
    protected boolean myIsDryRun;

    protected SVNDeltaProcessor myDeltaProcessor;
    protected ISVNEventHandler myEventHandler;
    protected ISVNEventHandler myCancelHandler;
    protected AbstractDiffCallback myDiffCallback;

    protected SVNDirectoryInfo myCurrentDirectory;
    protected SVNFileInfo myCurrentFile;
    protected File myTempDirectory;
    protected Collection myTempFiles;
    protected Map myDeletedPaths;
    private boolean myIsUseGlobalTmp;

    public SVNRemoteDiffEditor(SVNAdminArea adminArea, File target, AbstractDiffCallback callback, SVNRepository repos, 
            long revision1, long revision2, boolean dryRun, ISVNEventHandler handler, ISVNEventHandler cancelHandler) {
        myAdminArea = adminArea;
        myTarget = target;
        myDiffCallback = callback;
        myRepos = repos;
        myRevision1 = revision1;
        myRevision2 = revision2;
        myEventHandler = handler;
        myCancelHandler = cancelHandler;
        myDeltaProcessor = new SVNDeltaProcessor();
        myIsDryRun = dryRun;
        myDeletedPaths = new SVNHashMap();
    }

    public void reset(long revision1, long revision2) {
        myRevision1 = revision1;
        myRevision2 = revision2;
    }

    public void targetRevision(long revision) throws SVNException {
        myRevision2 = revision;
    }

    public void openRoot(long revision) throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(null, "", false);
        myCurrentDirectory.loadFromRepository(revision);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        if (myCurrentDirectory.myIsSkip || myCurrentDirectory.myIsTreeConflicted) {
            return;
        }
        
        SVNNodeKind nodeKind = myRepos.checkPath(path, myRevision1);
        SVNAdminArea dir = retrieve(myCurrentDirectory.myWCFile, true);

        deleteEntry(path, nodeKind, dir);
    }

    protected void deleteEntry(String path, SVNNodeKind nodeKind, SVNAdminArea dir) throws SVNException {
        SVNStatusType type = SVNStatusType.INAPPLICABLE;
        SVNEventAction action = SVNEventAction.SKIP;
        SVNEventAction expectedAction = SVNEventAction.UPDATE_DELETE;
        boolean[] isTreeConflicted = { false };
        
        if (myAdminArea == null || dir != null) {
            if (nodeKind == SVNNodeKind.FILE) {
                SVNFileInfo file = new SVNFileInfo(path, false);
                file.loadFromRepository(myRevision1);
                String baseType = file.myBaseProperties.getStringValue(SVNProperty.MIME_TYPE);
                type = getDiffCallback().fileDeleted(path, file.myBaseFile, null, baseType, null, file.myBaseProperties, isTreeConflicted);
            } else if (nodeKind == SVNNodeKind.DIR) {
                type = getDiffCallback().directoryDeleted(path, isTreeConflicted);
            }
            if (type != SVNStatusType.MISSING && type != SVNStatusType.OBSTRUCTED && !isTreeConflicted[0]) {
                action = SVNEventAction.UPDATE_DELETE;
                if (myIsDryRun) {
                    getDiffCallback().addDeletedPath(path);
                }
            }
        }
        
        action = isTreeConflicted[0] ? SVNEventAction.TREE_CONFLICT : action;
        addDeletedPath(path, nodeKind, type, action, expectedAction, isTreeConflicted[0]);
    }

    protected void addDeletedPath(String path, SVNNodeKind nodeKind, SVNStatusType type, SVNEventAction action, SVNEventAction expectedAction, 
            boolean isTreeConflicted) {
        if (myEventHandler != null) {
            File deletedFile = new Resource(myTarget, path);
            KindActionState kas = new KindActionState();
            kas.myAction = action;
            kas.myKind = nodeKind;
            kas.myStatus = type;
            kas.myExpectedAction = expectedAction;
            kas.myIsTreeConflicted = isTreeConflicted;
            myDeletedPaths.put(deletedFile, kas);
        }
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision)  throws SVNException {
        SVNDirectoryInfo parentDir = myCurrentDirectory;
        
        myCurrentDirectory = new SVNDirectoryInfo(myCurrentDirectory, path, true);
        myCurrentDirectory.myBaseProperties = new SVNProperties();

        if (parentDir.myIsSkip || parentDir.myIsTreeConflicted) {
            myCurrentDirectory.myIsSkip = true;
            return;
        }

        boolean[] isTreeConflicted = { false };
        SVNStatusType type = getDiffCallback().directoryAdded(path, myRevision2, isTreeConflicted);
        myCurrentDirectory.myIsTreeConflicted = isTreeConflicted[0];

        if (myEventHandler != null) {
            SVNEventAction action = SVNEventAction.UPDATE_ADD;
            SVNNodeKind kind = SVNNodeKind.DIR;
            
            KindActionState kas = (KindActionState) myDeletedPaths.get(myCurrentDirectory.myWCFile);
        	if (kas != null) {
        	    kind = kas.myKind;
        	    type = kas.myStatus;
                myDeletedPaths.remove(myCurrentDirectory.myWCFile);

        	}
        	
        	if (myCurrentDirectory.myIsTreeConflicted) {
        	    action = SVNEventAction.TREE_CONFLICT;
        	} else if (kas != null) {
        	    if (kas.myAction == SVNEventAction.UPDATE_DELETE) {
        	        action = SVNEventAction.UPDATE_REPLACE;
        	    } else {
        	        action = kas.myAction;
        	    }
        	} else if (type == SVNStatusType.MISSING || type == SVNStatusType.OBSTRUCTED) {
        	    action = SVNEventAction.SKIP;
        	} 

        	SVNEvent event = SVNEventFactory.createSVNEvent(myCurrentDirectory.myWCFile, kind,
        	        null, SVNRepository.INVALID_REVISION, type, type, null, action, SVNEventAction.UPDATE_ADD, null, null);
        	myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
    }

    public void openDir(String path, long revision) throws SVNException {
        SVNDirectoryInfo parentDir = myCurrentDirectory;
        myCurrentDirectory = new SVNDirectoryInfo(myCurrentDirectory, path, false);
        if (parentDir.myIsSkip || parentDir.myIsTreeConflicted) {
            myCurrentDirectory.myIsSkip = true;
            return;
        }
        
        myCurrentDirectory.loadFromRepository(revision);
        boolean[] isTreeConflicted = { false };
        getDiffCallback().directoryOpened(path, revision, isTreeConflicted);
        myCurrentDirectory.myIsTreeConflicted = isTreeConflicted[0];
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        if (myCurrentDirectory.myIsSkip) {
            return;
        }
        myCurrentDirectory.myPropertyDiff.put(name, value);
    }

    public void closeDir() throws SVNException {
        if (myCurrentDirectory.myIsSkip) {
            myCurrentDirectory = myCurrentDirectory.myParent;
            return;
        }
        
        SVNStatusType type = SVNStatusType.UNKNOWN;
        SVNEventAction expectedAction = SVNEventAction.UPDATE_UPDATE;
        SVNEventAction action = expectedAction;

        if (myIsDryRun) {
            getDiffCallback().clearDeletedPaths();
        }

        SVNAdminArea dir = null;
        if (!myCurrentDirectory.myPropertyDiff.isEmpty()) {
            try {
                dir = retrieve(myCurrentDirectory.myWCFile, myIsDryRun);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                    if (!myCurrentDirectory.myIsAdded && myEventHandler != null) {
                        action = myCurrentDirectory.myIsTreeConflicted ? SVNEventAction.TREE_CONFLICT : SVNEventAction.SKIP;
                        SVNEvent event = SVNEventFactory.createSVNEvent(myCurrentDirectory.myWCFile,
                        		SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNStatusType.MISSING,
                        		SVNStatusType.MISSING, null, action, expectedAction, null, null);
                        myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
                    }
                    myCurrentDirectory = myCurrentDirectory.myParent;
                    return;
                }
                throw e;
            }
            if (!myIsDryRun || dir != null) {
                boolean[] isTreeConflicted = { false };
                type = getDiffCallback().propertiesChanged(myCurrentDirectory.myRepositoryPath, myCurrentDirectory.myBaseProperties, 
                        myCurrentDirectory.myPropertyDiff, isTreeConflicted);
                if (isTreeConflicted[0]) {
                    myCurrentDirectory.myIsTreeConflicted = true;
                }
            }
        }

        getDiffCallback().directoryClosed(myCurrentDirectory.myRepositoryPath, null);
        
        if (type == SVNStatusType.UNKNOWN) {
            action = SVNEventAction.UPDATE_NONE;
        }

        if (!myCurrentDirectory.myIsAdded && myEventHandler != null) {
            for (Iterator deletedPathsIter = myDeletedPaths.keySet().iterator(); deletedPathsIter.hasNext();) {
            	File deletedPath = (File) deletedPathsIter.next();
            	KindActionState kas = (KindActionState) myDeletedPaths.get(deletedPath);
                SVNEvent event = SVNEventFactory.createSVNEvent(deletedPath, kas.myKind, null,
                		SVNRepository.INVALID_REVISION, kas.myStatus, kas.myStatus, SVNStatusType.INAPPLICABLE,
                		kas.myAction, kas.myExpectedAction, null, null);
                myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
                deletedPathsIter.remove();
            }

            action = myCurrentDirectory.myIsTreeConflicted ? SVNEventAction.TREE_CONFLICT : action;
            SVNEvent event = SVNEventFactory.createSVNEvent(myCurrentDirectory.myWCFile,
            		SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNStatusType.INAPPLICABLE, type,
            		SVNStatusType.INAPPLICABLE, action, expectedAction, null, null);
            myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
        myCurrentDirectory = myCurrentDirectory.myParent;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentFile = createFileInfo(path, true);
        if (myCurrentDirectory.myIsSkip || myCurrentDirectory.myIsTreeConflicted) {
            myCurrentFile.myIsSkip = true;
            return;
        }
        
        myCurrentFile.myBaseProperties = new SVNProperties();
        myCurrentFile.myBaseFile = SVNFileUtil.createUniqueFile(getTempDirectory(), ".diff", ".tmp", myIsUseGlobalTmp);
    }

    public void openFile(String path, long revision) throws SVNException {
        myCurrentFile = createFileInfo(path, false);
        if (myCurrentDirectory.myIsSkip || myCurrentDirectory.myIsTreeConflicted) {
            myCurrentFile.myIsSkip = true;
            return;
        }
        myCurrentFile.loadFromRepository(revision);
    }

    public void changeFileProperty(String commitPath, String name, SVNPropertyValue value) throws SVNException {
        if (myCurrentFile.myIsSkip) {
            return;
        }
        myCurrentFile.myPropertyDiff.put(name, value);
    }

    public void applyTextDelta(String commitPath, String baseChecksum) throws SVNException {
        if (myCurrentFile.myIsSkip) {
            return;
        }
        
        SVNAdminArea dir = null;
        try {
            dir = retrieveParent(myCurrentFile.myWCFile, true);
        } catch (SVNException e) {
            dir = null;
        }
        myCurrentFile.myFile = createTempFile(dir, SVNPathUtil.tail(commitPath));
        myDeltaProcessor.applyTextDelta(myCurrentFile.myBaseFile, myCurrentFile.myFile, false);
    }

    public OutputStream textDeltaChunk(String commitPath, SVNDiffWindow diffWindow) throws SVNException {
        if (myCurrentFile.myIsSkip) {
            return SVNFileUtil.DUMMY_OUT;
        }
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }

    public void textDeltaEnd(String commitPath) throws SVNException {
        if (myCurrentFile.myIsSkip) {
            return;
        }
        myDeltaProcessor.textDeltaEnd();
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
        if (myCurrentFile.myIsSkip) {
            return;
        }
        
        boolean[] isTreeConflicted = { false };
        closeFile(commitPath, myCurrentFile.myIsAdded, myCurrentFile.myWCFile, myCurrentFile.myFile,
                myCurrentFile.myPropertyDiff, myCurrentFile.myBaseProperties, myCurrentFile.myBaseFile, isTreeConflicted);
        myCurrentFile.myIsTreeConflicted = isTreeConflicted[0];
    }

    protected void closeFile(String commitPath, boolean added, File wcFile, File file, SVNProperties propertyDiff, SVNProperties baseProperties, 
            File baseFile, boolean[] isTreeConflicted) throws SVNException {
        SVNEventAction expectedAction = added ? SVNEventAction.UPDATE_ADD : SVNEventAction.UPDATE_UPDATE;
        SVNStatusType[] type = {SVNStatusType.UNKNOWN, SVNStatusType.UNKNOWN};
        try {
            retrieveParent(wcFile, myIsDryRun);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                if (myEventHandler != null) {
                    SVNEvent event = SVNEventFactory.createSVNEvent(wcFile,
                            SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNStatusType.MISSING,
                            SVNStatusType.UNKNOWN, null, SVNEventAction.SKIP, expectedAction, null, null);
                    myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
                }
                return;
            }
            throw e;
        }
        
        if (file != null || !propertyDiff.isEmpty()) {
            String baseMimeType = baseProperties.getStringValue(SVNProperty.MIME_TYPE);
            String mimeType = propertyDiff.getStringValue(SVNProperty.MIME_TYPE);
            if (added) {
                type = getDiffCallback().fileAdded(commitPath,
                        file != null ? baseFile : null, file,
                        0, myRevision2, baseMimeType, mimeType,
                        baseProperties, propertyDiff, isTreeConflicted);
            } else {
                type = getDiffCallback().fileChanged(commitPath,
                        file != null ? baseFile : null, file,
                        myRevision1, myRevision2, baseMimeType, mimeType,
                        baseProperties, propertyDiff, isTreeConflicted);
            }
        }
        
        if (myEventHandler != null) {
            SVNNodeKind kind = SVNNodeKind.FILE;
            SVNEventAction action;
            KindActionState kas = (KindActionState) myDeletedPaths.get(wcFile);
            if (kas != null) {
                myDeletedPaths.remove(wcFile);
                kind = kas.myKind;
                type[0] = type[1] = kas.myStatus;
            }

            if (isTreeConflicted != null && isTreeConflicted.length > 0 && isTreeConflicted[0]) {
                action = SVNEventAction.TREE_CONFLICT;
            } else if (kas != null) {
                if (kas.myAction == SVNEventAction.UPDATE_DELETE && added) {
                    action = SVNEventAction.UPDATE_REPLACE;
                } else {
                    action = kas.myAction;
                }
            } else if (type[0] == SVNStatusType.MISSING || type[0] == SVNStatusType.OBSTRUCTED) {
                action = SVNEventAction.SKIP;
            } else if (added) {
                action = SVNEventAction.UPDATE_ADD;
            } else {
                action = SVNEventAction.UPDATE_UPDATE;
            }

            SVNEvent event = SVNEventFactory.createSVNEvent(wcFile, kind, null, SVNRepository.INVALID_REVISION, type[0], type[1], 
                    null, action, expectedAction, null, null);
            myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
        if (myCurrentDirectory.myWCFile != null) {
            File dir = new Resource(myCurrentDirectory.myWCFile, SVNPathUtil.tail(path));
            SVNEvent event = SVNEventFactory.createSVNEvent(dir, SVNNodeKind.DIR,
                    null, SVNRepository.INVALID_REVISION, SVNStatusType.MISSING, SVNStatusType.MISSING, SVNStatusType.MISSING, SVNEventAction.SKIP, SVNEventAction.SKIP,
                    null, null);
            myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
    }

    public void absentFile(String path) throws SVNException {
        if (myCurrentDirectory.myWCFile != null) {
            File file = new Resource(myCurrentDirectory.myWCFile, SVNPathUtil.tail(path));
            SVNEvent event = SVNEventFactory.createSVNEvent(file, SVNNodeKind.FILE,
                    null, SVNRepository.INVALID_REVISION, SVNStatusType.MISSING, SVNStatusType.MISSING, SVNStatusType.MISSING, SVNEventAction.SKIP, SVNEventAction.SKIP,
                    null, null);
            myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
    }

    public void cleanup() throws SVNException {
        if (myTempDirectory != null) {
            SVNFileUtil.deleteAll(myTempDirectory, true);
            myTempDirectory = null;
        }
        if (myTempFiles != null) {
            for(Iterator files = myTempFiles.iterator(); files.hasNext();) {
                SVNFileUtil.deleteFile((File) files.next());
            }
        }
    }

    protected SVNAdminArea retrieve(File path, boolean lenient) throws SVNException {
        if (myAdminArea == null) {
            return null;
        }
        try {
            return myAdminArea.getWCAccess().retrieve(path);
        } catch (SVNException e) {
            if (lenient) {
                return null;
            }
            throw e;
        }
    }

    protected SVNAdminArea retrieveParent(File path, boolean lenient) throws SVNException {
        if (myAdminArea == null) {
            return null;
        }
        return retrieve(path.getParentFile(), lenient);
    }

    protected AbstractDiffCallback getDiffCallback() {
        return myDiffCallback;
    }

    protected File getTempDirectory() throws SVNException {
        if (myTempDirectory == null) {
            myTempDirectory = getDiffCallback().createTempDirectory();
        }
        return myTempDirectory;
    }

    protected File createTempFile(SVNAdminArea dir, String name) throws SVNException {
        if (dir != null && dir.isLocked()) {
            File tmpFile = dir.getBaseFile(name, true);
            if (myTempFiles == null) {
                myTempFiles = new SVNHashSet();
            }
            myTempFiles.add(tmpFile);
            return tmpFile;
        }
        return SVNFileUtil.createUniqueFile(getTempDirectory(), ".diff", ".tmp", myIsUseGlobalTmp);

    }

    protected SVNFileInfo createFileInfo(String path, boolean added) {
        return new SVNFileInfo(path, added);
    }

    protected void setIsConflicted(boolean[] isConflictedResult, boolean isConflicted) {
        if (isConflictedResult != null && isConflictedResult.length > 0) {
            isConflictedResult[0] = isConflicted;
        }
    }

    public void setUseGlobalTmp(boolean global) {
        myIsUseGlobalTmp = global;
    }

    protected class SVNDirectoryInfo {

        public SVNDirectoryInfo(SVNDirectoryInfo parent, String path, boolean added) {
            myParent = parent;
            myRepositoryPath = path;
            myWCFile = myTarget != null ? new Resource(myTarget, path) : null;
            myIsAdded = added;
            myPropertyDiff = new SVNProperties();
        }

        public void loadFromRepository(long baseRevision) throws SVNException {
            myBaseProperties = new SVNProperties();
            myRepos.getDir(myRepositoryPath, baseRevision, myBaseProperties, (ISVNDirEntryHandler) null);
        }

        protected boolean myIsAdded;
        protected boolean myIsSkip;
        protected boolean myIsTreeConflicted;
        protected String myRepositoryPath;
        protected File myWCFile;

        protected SVNProperties myBaseProperties;
        protected SVNProperties myPropertyDiff;

        protected SVNDirectoryInfo myParent;
    }

    protected class SVNFileInfo {

        public SVNFileInfo(String path, boolean added) {
            myRepositoryPath = path;
            myIsAdded = added;
            myWCFile = myTarget != null ? new Resource(myTarget, path) : null;
            myPropertyDiff = new SVNProperties();
        }

        public void loadFromRepository(long revision) throws SVNException {
            myBaseFile = SVNFileUtil.createUniqueFile(getTempDirectory(), ".diff", ".tmp", myIsUseGlobalTmp);
            OutputStream os = null;
            myBaseProperties = new SVNProperties();
            try {
                os = SVNFileUtil.openFileForWriting(myBaseFile);
                myRepos.getFile(myRepositoryPath, revision, myBaseProperties, new SVNCancellableOutputStream(os, myCancelHandler));
            } finally {
                SVNFileUtil.closeFile(os);
            }
        }

        protected String myRepositoryPath;
        protected File myWCFile;
        protected boolean myIsAdded;
        protected boolean myIsSkip;
        protected boolean myIsTreeConflicted;
        
        protected File myFile;
        protected File myBaseFile;
        protected SVNProperties myBaseProperties;
        protected SVNProperties myPropertyDiff;
    }

    protected class KindActionState {
    	protected SVNNodeKind myKind;
    	protected SVNEventAction myAction;
    	protected SVNEventAction myExpectedAction;
    	protected SVNStatusType myStatus;
    	protected boolean myIsTreeConflicted;
    }
}
