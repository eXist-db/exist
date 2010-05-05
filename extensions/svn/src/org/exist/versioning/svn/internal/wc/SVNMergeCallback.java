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
import java.util.Iterator;
import java.util.Map;

import org.exist.versioning.svn.internal.wc.SVNMergeDriver.MergeSource;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.exist.versioning.svn.wc.SVNConflictAction;
import org.exist.versioning.svn.wc.SVNConflictReason;
import org.exist.versioning.svn.wc.SVNEvent;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.exist.versioning.svn.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNMergeCallback extends AbstractDiffCallback {

    protected boolean myIsDryRun;
    protected SVNURL myURL;

    protected boolean myIsAddNecessitatedMerge;
    protected String myAddedPath = null;
    protected boolean myIsForce;
    protected SVNDiffOptions myDiffOptions;
    protected Map myConflictedPaths;
    protected SVNMergeDriver myMergeDriver;
    
    public SVNMergeCallback(SVNAdminArea adminArea, SVNURL url, boolean force, boolean dryRun, 
                            SVNDiffOptions options, Map conflictedPathsGetter, SVNMergeDriver mergeDriver) {
        super(adminArea);
        myURL = url;
        myIsDryRun = dryRun;
        myIsForce = force;
        myDiffOptions = options;
        myConflictedPaths = conflictedPathsGetter;
        myMergeDriver = mergeDriver;
    }

    public File createTempDirectory() throws SVNException {
        return SVNFileUtil.createTempDirectory("merge");
    }

    public boolean isDiffUnversioned() {
        return false;
    }
    
    public boolean isDiffCopiedAsAdded() {
        return false;
    }

    public Map getConflictedPaths() {
        return myConflictedPaths;
    }

    public SVNStatusType propertiesChanged(String path, SVNProperties originalProperties, SVNProperties diff, boolean[] isTreeConflicted) throws SVNException {
        setIsConflicted(isTreeConflicted, false);
        SVNStatusType obstructedStatus = getStatusForObstructedOrMissing(path);
        if (obstructedStatus != SVNStatusType.INAPPLICABLE) {
            return obstructedStatus;
        }
        
        SVNProperties regularProps = new SVNProperties();
        categorizeProperties(diff, regularProps, null, null);
        if (regularProps.isEmpty()) {
            return SVNStatusType.UNKNOWN;
        }
        try {
            File file = getFile(path);
            SVNWCAccess wcAccess = getWCAccess(); 
            if (wcAccess.getAdminArea(file) == null) {
                wcAccess.probeTry(file, true, SVNWCAccess.INFINITE_DEPTH);
            }
            
            MergeSource mergeSource = myMergeDriver.getCurrentMergeSource();
            if (mergeSource.getRevision1() < mergeSource.getRevision2()) {
                SVNProperties filteredProps = myMergeDriver.filterSelfReferentialMergeInfo(regularProps, file); 
                if (filteredProps != null) {
                    regularProps = filteredProps; 
                }
            }
            
            SVNStatusType status = SVNPropertiesManager.mergeProperties(getWCAccess(), file, originalProperties, regularProps, 
                    false, myIsDryRun);
            if (!myIsDryRun) {
                for (Iterator propsIter = regularProps.nameSet().iterator(); propsIter.hasNext();) {
                    String propName = (String) propsIter.next();
                    SVNPropertyValue propValue = regularProps.getSVNPropertyValue(propName);
                    if (SVNProperty.MERGE_INFO.equals(propName)) {
                        SVNPropertyValue mergeInfoProp = originalProperties.getSVNPropertyValue(SVNProperty.MERGE_INFO);
                        if (mergeInfoProp == null && propValue != null) {
                            myMergeDriver.addPathWithNewMergeInfo(file);
                        } else if (mergeInfoProp != null && propValue == null) {
                            myMergeDriver.addPathWithDeletedMergeInfo(file);
                        }
                    }
                }
            }
            
            return status;
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.UNVERSIONED_RESOURCE || 
                    e.getErrorMessage().getErrorCode() == SVNErrorCode.ENTRY_NOT_FOUND) {
                setIsConflicted(isTreeConflicted, true);
                return SVNStatusType.MISSING;
            }
            throw e;
        }
    }

    public SVNStatusType directoryAdded(String path, long revision, boolean[] isTreeConflicted) throws SVNException {
        setIsConflicted(isTreeConflicted, false);
        File mergedFile = getFile(path);
        SVNAdminArea dir = retrieve(mergedFile.getParentFile(), true);
        if (dir == null) {
            if (myIsDryRun && myAddedPath != null && SVNPathUtil.isAncestor(myAddedPath, path)) {
                return SVNStatusType.CHANGED;
            } 
            return SVNStatusType.MISSING;
        }
        
        SVNURL copyFromURL = null;
        long copyFromRevision = SVNRepository.INVALID_REVISION;
        if (myMergeDriver.myIsSameRepository) {
            copyFromURL = myURL.appendPath(path, false);
            copyFromRevision = revision;
            // TODO protocol
        }

        SVNFileType fileType = SVNFileType.getType(mergedFile);
        SVNStatusType obstructedStatus = getStatusForObstructedOrMissing(path);
        if (obstructedStatus == SVNStatusType.MISSING || 
                (obstructedStatus == SVNStatusType.OBSTRUCTED && (fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK))) {
            return obstructedStatus;
        }
        
        if (fileType == SVNFileType.NONE) {
            if (myIsDryRun) {
                myAddedPath = path;
            } else {
                if (!mergedFile.mkdirs()) {
                    if (SVNFileType.getType(mergedFile) != SVNFileType.DIRECTORY) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot create directory ''{0}''", mergedFile);
                        SVNErrorManager.error(err, SVNLogType.DEFAULT);
                    }
                }
                ISVNEventHandler oldEventHandler = dir.getWCAccess().getEventHandler();
                dir.getWCAccess().setEventHandler(null);                
                SVNWCManager.add(mergedFile, dir, copyFromURL, copyFromRevision, null);
                dir.getWCAccess().setEventHandler(oldEventHandler);
            }
            return SVNStatusType.CHANGED;
        } else if (fileType == SVNFileType.DIRECTORY) {
            SVNEntry entry = getWCAccess().getEntry(mergedFile, false);
            if (entry == null || entry.isScheduledForDeletion()) {
                if (!myIsDryRun) {
                    ISVNEventHandler oldEventHandler = dir.getWCAccess().getEventHandler();
                    dir.getWCAccess().setEventHandler(null);                
                    SVNWCManager.add(mergedFile, dir, copyFromURL, copyFromRevision, null);
                    dir.getWCAccess().setEventHandler(oldEventHandler);
                }
                if (myIsDryRun) {
                    myAddedPath = path;
                }
                return SVNStatusType.CHANGED;
            }  
                
            if (myIsDryRun && isPathDeleted(path)) {
                return SVNStatusType.CHANGED;
            }
            
            myMergeDriver.recordTreeConflictOnAdd(mergedFile, dir, SVNNodeKind.DIR, SVNConflictAction.ADD, SVNConflictReason.ADDED);
            setIsConflicted(isTreeConflicted, true);
            
            return SVNStatusType.OBSTRUCTED;
        } else if (fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK) {
            if (myIsDryRun) {
                myAddedPath = null;
            }
            SVNEntry entry = getWCAccess().getEntry(mergedFile, false);
            if (entry != null && myIsDryRun && isPathDeleted(path)) {
                return SVNStatusType.CHANGED;
            } 
            
            myMergeDriver.recordTreeConflictOnAdd(mergedFile, dir, SVNNodeKind.DIR, SVNConflictAction.ADD, SVNConflictReason.OBSTRUCTED);
            setIsConflicted(isTreeConflicted, true);
            return SVNStatusType.OBSTRUCTED;
        }
        if (myIsDryRun) {
            myAddedPath = null;
        }
        return SVNStatusType.UNKNOWN;
    }

    public SVNStatusType directoryDeleted(final String path, boolean[] isTreeConflicted) throws SVNException {
        setIsConflicted(isTreeConflicted, false);
        final File mergedFile = getFile(path);
        final SVNAdminArea dir = retrieve(mergedFile.getParentFile(), true);
        if (dir == null) {
            return SVNStatusType.MISSING;
        }
        
        SVNEntry entry = getWCAccess().getEntry(mergedFile, true); 
        SVNStatusType obstructedStatus = getStatusForObstructedOrMissing(path);
        if (obstructedStatus != SVNStatusType.INAPPLICABLE) {
            return obstructedStatus;
        }
        
        SVNFileType fileType = SVNFileType.getType(mergedFile);
        if (fileType == SVNFileType.DIRECTORY) {
            if (entry != null && !entry.isScheduledForDeletion()) {
                final ISVNEventHandler oldEventHandler = getWCAccess().getEventHandler();            
                ISVNEventHandler handler = new ISVNEventHandler() {
                    public void checkCancelled() throws SVNCancelException {
                        oldEventHandler.checkCancelled();
                    }
                    public void handleEvent(SVNEvent event, double progress) throws SVNException {
                    }
                };
                getWCAccess().setEventHandler(handler);
                try {
                    delete(mergedFile, myIsForce, myIsDryRun, false);
                } catch (SVNException e) {
                    myMergeDriver.recordTreeConflict(mergedFile, dir, SVNNodeKind.DIR, SVNConflictAction.DELETE, SVNConflictReason.EDITED);
                    setIsConflicted(isTreeConflicted, true);
                    return SVNStatusType.CONFLICTED;
                } finally {
                    getWCAccess().setEventHandler(oldEventHandler);
                }
                return SVNStatusType.CHANGED;
            }
            myMergeDriver.recordTreeConflict(mergedFile, dir, SVNNodeKind.DIR, SVNConflictAction.DELETE, SVNConflictReason.DELETED);
            setIsConflicted(isTreeConflicted, true);
        } else if (fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK) {
            return SVNStatusType.OBSTRUCTED;
        } else if (fileType == SVNFileType.NONE) {
            myMergeDriver.recordTreeConflict(mergedFile, dir, SVNNodeKind.DIR, SVNConflictAction.DELETE, SVNConflictReason.DELETED);
            setIsConflicted(isTreeConflicted, true);
            return SVNStatusType.MISSING;
        }
        
        return SVNStatusType.UNKNOWN;
    }

    public void directoryOpened(String path, long revision, boolean[] isTreeConflicted) throws SVNException {
        setIsConflicted(isTreeConflicted, false);
        File mergedFile = getFile(path);
        SVNAdminArea dir = retrieve(mergedFile.getParentFile(), myIsDryRun);
        if (dir == null) {
            return;
        }
        
        SVNEntry entry = getWCAccess().getEntry(mergedFile, true);
        SVNFileType type = SVNFileType.getType(mergedFile);
        if (entry == null || entry.isScheduledForDeletion()) {
            myMergeDriver.recordTreeConflict(mergedFile, dir, SVNNodeKind.DIR, SVNConflictAction.EDIT, SVNConflictReason.DELETED);
            setIsConflicted(isTreeConflicted, true);
        } else if (entry.isDirectory() && type == SVNFileType.NONE) {
            myMergeDriver.recordTreeConflict(mergedFile, dir, SVNNodeKind.DIR, SVNConflictAction.EDIT, SVNConflictReason.MISSING);
            setIsConflicted(isTreeConflicted, true);
        } else if (entry.isDirectory() && type != SVNFileType.DIRECTORY) {
            myMergeDriver.recordTreeConflict(mergedFile, dir, SVNNodeKind.DIR, SVNConflictAction.EDIT, SVNConflictReason.OBSTRUCTED);
            setIsConflicted(isTreeConflicted, true);
        } else if (type != SVNFileType.DIRECTORY) {
            // entry of different kind as well, directory has been deleted.
            myMergeDriver.recordTreeConflict(mergedFile, dir, SVNNodeKind.DIR, SVNConflictAction.EDIT, SVNConflictReason.DELETED);
            setIsConflicted(isTreeConflicted, true);
        }
    }

    public SVNStatusType[] fileChanged(String path, File file1, File file2, long revision1, long revision2, String mimeType1, 
            String mimeType2, SVNProperties originalProperties, SVNProperties diff, boolean[] isTreeConflicted) throws SVNException {
        setIsConflicted(isTreeConflicted, false);
        boolean needsMerge = true;
        File mergedFile = getFile(path);
        SVNAdminArea dir = retrieve(mergedFile.getParentFile(), myIsDryRun);
        if (dir == null) {
            return new SVNStatusType[] {SVNStatusType.MISSING, SVNStatusType.MISSING};
        }
        
        SVNStatusType obstructedStatus = getStatusForObstructedOrMissing(path);
        if (obstructedStatus != SVNStatusType.INAPPLICABLE) {
            return new SVNStatusType[] { obstructedStatus, SVNStatusType.UNCHANGED };
        }
        
        SVNStatusType[] result = new SVNStatusType[] {SVNStatusType.UNCHANGED, SVNStatusType.UNCHANGED};
        SVNEntry entry = getWCAccess().getEntry(mergedFile, false);
        SVNFileType fileType = null;
        if (entry != null) {
            fileType = SVNFileType.getType(mergedFile);
        }
        
        if (entry == null || (fileType != SVNFileType.FILE && fileType != SVNFileType.SYMLINK)) {
            myMergeDriver.recordTreeConflict(mergedFile, dir, SVNNodeKind.FILE, SVNConflictAction.EDIT, SVNConflictReason.MISSING);
            setIsConflicted(isTreeConflicted, true);
            return new SVNStatusType[] {SVNStatusType.MISSING, SVNStatusType.MISSING};
        }
        
        if (diff != null && !diff.isEmpty()) {
            boolean[] isTreeConflicted2 = { false };
            result[1] = propertiesChanged(path, originalProperties, diff, isTreeConflicted2);
            if (isTreeConflicted2[0]) {
                setIsConflicted(isTreeConflicted, true);
                return result;
            }
        } 
        
        String name = mergedFile.getName();
        if (file1 != null) {
            boolean textModified = dir.hasTextModifications(name, false);
            if (!textModified && 
                    (SVNProperty.isBinaryMimeType(mimeType1) || SVNProperty.isBinaryMimeType(mimeType2))) {
                boolean same = SVNFileUtil.compareFiles(!myIsAddNecessitatedMerge ? file1 : file2, mergedFile, null);
                if (same) {
                    if (!myIsDryRun && !myIsAddNecessitatedMerge) {
                        SVNFileUtil.rename(file2, mergedFile);
                    }
                    result[0] = SVNStatusType.CHANGED;
                    needsMerge = false;
                }
            }
            
            if (needsMerge) {
                String localLabel = ".working";
                String baseLabel = ".merge-left.r" + revision1;
                String latestLabel = ".merge-right.r" + revision2;
                SVNStatusType mergeResult = dir.mergeText(name, file1, file2, null, localLabel, 
                        baseLabel, latestLabel, diff, myIsDryRun, myDiffOptions, null);

                dir.runLogs();
                if (mergeResult == SVNStatusType.CONFLICTED || mergeResult == SVNStatusType.CONFLICTED_UNRESOLVED) {
                    result[0] = mergeResult;
                } else if (textModified && mergeResult != SVNStatusType.UNCHANGED) {
                    result[0] = SVNStatusType.MERGED;
                } else if (mergeResult == SVNStatusType.MERGED) {
                    result[0] = SVNStatusType.CHANGED;
                } else if (mergeResult != SVNStatusType.MISSING) {
                    result[0] = SVNStatusType.UNCHANGED;
                }

                if (mergeResult == SVNStatusType.CONFLICTED) {
                    if (myConflictedPaths == null) {
                        myConflictedPaths = new SVNHashMap();
                    }
                    myConflictedPaths.put(path, path);
                }
            }
        } 
        return result;
    }

    public SVNStatusType[] fileAdded(String path, File file1, File file2, long revision1, long revision2, 
            String mimeType1, String mimeType2, SVNProperties originalProperties, SVNProperties diff, boolean[] isTreeConflicted) throws SVNException {
        setIsConflicted(isTreeConflicted, false);
        SVNStatusType[] result = new SVNStatusType[] {null, SVNStatusType.UNKNOWN};
        SVNProperties newProps = new SVNProperties(originalProperties);
        for (Iterator propChangesIter = diff.nameSet().iterator(); propChangesIter.hasNext();) {
            String propName = (String) propChangesIter.next();
            if (SVNProperty.isWorkingCopyProperty(propName)) {
                continue;
            }
            if (!myMergeDriver.isSameRepository() && !SVNProperty.isRegularProperty(propName)) {
                continue;
            }
            if (!myMergeDriver.isSameRepository() && SVNProperty.MERGE_INFO.equals(propName)) {
                continue;
            }
            SVNPropertyValue propValue = diff.getSVNPropertyValue(propName);
            newProps.put(propName, propValue);
        }
        File mergedFile = getFile(path);
        SVNAdminArea dir = retrieve(mergedFile.getParentFile(), true);
        if (dir == null) {
            if (myIsDryRun && myAddedPath != null && SVNPathUtil.isAncestor(myAddedPath, path)) {
                result[0] = SVNStatusType.CHANGED;
                if (!newProps.isEmpty()) {
                    result[1] = SVNStatusType.CHANGED;
                }
            } else {
                result[0] = SVNStatusType.MISSING;
            }
            return result;
        }
        
        SVNStatusType obstructedStatus = getStatusForObstructedOrMissing(path);
        if (obstructedStatus != SVNStatusType.INAPPLICABLE) {
            return new SVNStatusType[] { obstructedStatus, SVNStatusType.UNCHANGED };
        }
        
        SVNFileType fileType = SVNFileType.getType(mergedFile);
        if (fileType == SVNFileType.NONE) {
            if (!myIsDryRun) {
                String copyFromURL = null;
                long copyFromRevision = SVNRepository.INVALID_REVISION;
                if (myMergeDriver.myIsSameRepository) {
                    String targePath = myMergeDriver.myTarget.getAbsolutePath();
                    String minePath = mergedFile.getAbsolutePath();
                    String relativePath = SVNPathUtil.getRelativePath(targePath, minePath);

                    copyFromURL = myURL.appendPath(relativePath, false).toString();    
                    copyFromRevision = revision2;
                    // TODO compare protocols with dir one.
                }
                
                SVNTreeConflictDescription existingConflict = getWCAccess().getTreeConflict(mergedFile);
                if (existingConflict != null) {
                    myMergeDriver.recordTreeConflictOnAdd(mergedFile, getAdminArea(), SVNNodeKind.FILE, SVNConflictAction.ADD, SVNConflictReason.ADDED);
                    setIsConflicted(isTreeConflicted, true);
                } else {
                    SVNWCManager.addRepositoryFile(dir, mergedFile.getName(), null, file2, newProps, null, 
                            copyFromURL, copyFromRevision);
                }
            }
            result[0] = SVNStatusType.CHANGED;
            if (!newProps.isEmpty()) {
                result[1] = SVNStatusType.CHANGED;
            }
        } else if (fileType == SVNFileType.DIRECTORY) {
            myMergeDriver.recordTreeConflictOnAdd(mergedFile, dir, SVNNodeKind.FILE, SVNConflictAction.ADD, SVNConflictReason.OBSTRUCTED);
            setIsConflicted(isTreeConflicted, true);
            if (myIsDryRun && isPathDeleted(path)) {
                result[0] = SVNStatusType.CHANGED;
            } else { 
                result[0] = SVNStatusType.OBSTRUCTED;
            }
        } else if (fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK) {
            if (myIsDryRun && isPathDeleted(path)) {
                result[0] = SVNStatusType.CHANGED;
            } else {
                myMergeDriver.recordTreeConflictOnAdd(mergedFile, dir, SVNNodeKind.FILE, SVNConflictAction.ADD, SVNConflictReason.ADDED);
                setIsConflicted(isTreeConflicted, true);
            }
        }
        return result;
    }

    public SVNStatusType fileDeleted(String path, File file1, File file2, String mimeType1, String mimeType2, 
            SVNProperties originalProperties, boolean[] isTreeConflicted) throws SVNException {
        setIsConflicted(isTreeConflicted, false);
        File mergedFile = getFile(path);
        SVNAdminArea dir = retrieve(mergedFile.getParentFile(), true);
        if (dir == null) {
            return SVNStatusType.MISSING;
        }
        
        SVNStatusType obstructedStatus = getStatusForObstructedOrMissing(path);
        if (obstructedStatus != SVNStatusType.INAPPLICABLE) {
            return obstructedStatus;
        }
        
        SVNFileType fileType = SVNFileType.getType(mergedFile);
        if (fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK) {
            if (areFilesTheSame(file1, originalProperties, mergedFile, dir) || myMergeDriver.myIsForce || myMergeDriver.myIsRecordOnly) {
                ISVNEventHandler oldEventHandler = getWCAccess().getEventHandler();
                getWCAccess().setEventHandler(null);
                try {
                    delete(mergedFile, true, myIsDryRun, false);
                } catch (SVNException e) {
                    return SVNStatusType.OBSTRUCTED;
                } finally {
                    getWCAccess().setEventHandler(oldEventHandler);
                }
                return SVNStatusType.CHANGED;
            } 
            
            myMergeDriver.recordTreeConflict(mergedFile, dir, SVNNodeKind.FILE, SVNConflictAction.DELETE, SVNConflictReason.EDITED);
            setIsConflicted(isTreeConflicted, true);
            return SVNStatusType.OBSTRUCTED; 
        } else if (fileType == SVNFileType.DIRECTORY) {
            myMergeDriver.recordTreeConflict(mergedFile, dir, SVNNodeKind.FILE, SVNConflictAction.DELETE, SVNConflictReason.OBSTRUCTED);
            setIsConflicted(isTreeConflicted, true);
            return SVNStatusType.OBSTRUCTED;
        } else if (fileType == SVNFileType.NONE) {
            myMergeDriver.recordTreeConflict(mergedFile, dir, SVNNodeKind.FILE, SVNConflictAction.DELETE, SVNConflictReason.DELETED);
            setIsConflicted(isTreeConflicted, true);
            return SVNStatusType.MISSING;
        }
        return SVNStatusType.UNKNOWN;
    }

    public SVNStatusType[] directoryClosed(String path, boolean[] isTreeConflicted) throws SVNException {
        setIsConflicted(isTreeConflicted, false);
        return new SVNStatusType[] { SVNStatusType.UNKNOWN, SVNStatusType.UNKNOWN };
    }

    protected File getFile(String path) {
        return getAdminArea().getFile(path);
    }
    
    protected SVNAdminArea retrieve(File path, boolean lenient) throws SVNException {
        if (getAdminArea() == null) {
            return null;
        }
        try {
            return getAdminArea().getWCAccess().retrieve(path);
        } catch (SVNException e) {
            if (lenient) {
                return null;
            }
            throw e;
        }
    }
    
    protected void delete(File path, boolean force, boolean dryRun, boolean keepLocal) throws SVNException {
        if (!force && !keepLocal) {
            SVNWCManager.canDelete(path, getWCAccess().getOptions(), getWCAccess());
        }
        SVNAdminArea root = getWCAccess().retrieve(path.getParentFile()); 
        if (!dryRun) {
            SVNWCManager.delete(getWCAccess(), root, path, !keepLocal, false);
        }
    }

    protected boolean areFilesTheSame(File older, SVNProperties originalProps, File mine, SVNAdminArea adminArea) throws SVNException {
        SVNProperties workingProps = adminArea.getProperties(mine.getName()).asMap();
        if (arePropsTheSame(originalProps, workingProps)) {
            return !adminArea.hasVersionedFileTextChanges(mine, older, true);
        }
        return false;
    }

    private SVNStatusType getStatusForObstructedOrMissing(String path) {
        File file = getFile(path);
        SVNEntry entry = null;
        try {
            entry = getWCAccess().getEntry(file, true);
        } catch (SVNException svne) {
            //
        }
        
        if (entry != null && entry.isAbsent()) {
            return SVNStatusType.MISSING;
        }

        SVNNodeKind expectedKind = getWorkingNodeKind(entry, path);
        SVNNodeKind diskKind = getDiskKind(path);
        if (entry != null && entry.isDirectory() && entry.isScheduledForDeletion() && diskKind == SVNNodeKind.DIR) {
            expectedKind = SVNNodeKind.DIR;
        }
        if (expectedKind == diskKind) {
            return SVNStatusType.INAPPLICABLE;
        } else if (diskKind == SVNNodeKind.NONE) {
            return SVNStatusType.MISSING;
        }
        return SVNStatusType.OBSTRUCTED;
    }
    
    private SVNNodeKind getWorkingNodeKind(SVNEntry entry, String path) {
        if (entry == null || entry.isScheduledForDeletion() || (myIsDryRun && isPathDeleted(path)) || 
                (entry.isDeleted() && !entry.isScheduledForAddition())) {
            return SVNNodeKind.NONE;
        }
        return entry.getKind();
    }
    
    private SVNNodeKind getDiskKind(String path) {
        File file = getFile(path);
        SVNFileType type = null;
        type = SVNFileType.getType(file);
        if (type == SVNFileType.UNKNOWN) {
            return SVNNodeKind.UNKNOWN;
        }
        if (myIsDryRun && isPathDeleted(path)) {
            return SVNNodeKind.NONE;
        }
        return SVNFileType.getNodeKind(type);
    }
    
    private boolean arePropsTheSame(SVNProperties props1, SVNProperties props2) {
        SVNProperties propsDiff = props2.compareTo(props1);
        SVNProperties regularPropsDiff = new SVNProperties();
        categorizeProperties(propsDiff, regularPropsDiff, null, null);
        for (Iterator propNamesIter = regularPropsDiff.nameSet().iterator(); propNamesIter.hasNext();) {
            String propName = (String) propNamesIter.next();
            if (!SVNProperty.MERGE_INFO.equals(propName)) {
                return false;
            }
        }
        return true;
    }

}
