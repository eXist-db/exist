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
import java.util.LinkedList;
import java.util.Map;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.admin.ISVNCleanupHandler;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminAreaInfo;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNLog;
import org.exist.versioning.svn.internal.wc.admin.SVNVersionedProperties;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.exist.versioning.svn.wc.SVNEvent;
import org.exist.versioning.svn.wc.SVNEventAction;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNFileFetcher;
import org.tmatesoft.svn.core.internal.wc.ISVNUpdateEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNUpdateEditor15 implements ISVNUpdateEditor, ISVNCleanupHandler {

    private String mySwitchURL;
    private String myTarget;
    private String myTargetURL;
    private String myRootURL;
    private SVNAdminAreaInfo myAdminInfo;
    private SVNDirectoryInfo myCurrentDirectory;
    private SVNFileInfo myCurrentFile;
    private long myTargetRevision;
    private boolean myIsRootOpen;
    private boolean myIsTargetDeleted;
    private boolean myIsUnversionedObstructionsAllowed;
    private boolean myIsDepthSticky;
    //File objects
    private Collection mySkippedPaths;
    private SVNWCAccess myWCAccess; 
    private SVNDeltaProcessor myDeltaProcessor;
    private SVNDepth myRequestedDepth;
    private String[] myExtensionPatterns;
    private ISVNFileFetcher myFileFetcher;
    
    private boolean myIsLockOnDemand;

    private SVNUpdateEditor15(SVNAdminAreaInfo info, String switchURL, boolean allowUnversionedObstructions,
            boolean depthIsSticky, SVNDepth depth, String[] preservedExtensions, String targetURL, 
            String rootURL, ISVNFileFetcher fileFetcher, boolean lockOnDemand) {
        myAdminInfo = info;
        myWCAccess = info.getWCAccess();
        myIsUnversionedObstructionsAllowed = allowUnversionedObstructions;
        myTarget = info.getTargetName();
        mySwitchURL = switchURL;
        myTargetRevision = -1;
        myRequestedDepth = depth;
        myIsDepthSticky = depthIsSticky;
        myDeltaProcessor = new SVNDeltaProcessor();
        myExtensionPatterns = preservedExtensions;
        myFileFetcher = fileFetcher;
        myTargetURL = targetURL;
        myRootURL = rootURL;
        myIsLockOnDemand = lockOnDemand;
        
        if (myTarget != null) {
            myTargetURL = SVNPathUtil.append(myTargetURL, SVNEncodingUtil.uriEncode(myTarget));
        }
        if ("".equals(myTarget)) {
            myTarget = null;
        }
    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }

    public long getTargetRevision() {
        return myTargetRevision;
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpen = true;
        myCurrentDirectory = createDirectoryInfo(null, "", false);
        myWCAccess.registerCleanupHandler(myCurrentDirectory.getAdminArea(), myCurrentDirectory);
        if (myTarget == null) {
            SVNAdminArea adminArea = myCurrentDirectory.getAdminArea();
            SVNEntry entry = adminArea.getEntry(adminArea.getThisDirName(), false);
            if (entry != null) {
                myCurrentDirectory.myAmbientDepth = entry.getDepth();
                myCurrentDirectory.myPreviousRevision = entry.getRevision();
            }
            Map attributes = new SVNHashMap();
            attributes.put(SVNProperty.REVISION, Long.toString(myTargetRevision));
            attributes.put(SVNProperty.URL, myCurrentDirectory.URL);
            attributes.put(SVNProperty.INCOMPLETE, Boolean.TRUE.toString());
            if (myRootURL != null && SVNPathUtil.isAncestor(myRootURL, myCurrentDirectory.URL)) {
                attributes.put(SVNProperty.REPOS, myRootURL);
            }
            adminArea.modifyEntry(adminArea.getThisDirName(), attributes, true, false);
            
            if (mySwitchURL != null) {
                clearWCProperty(myCurrentDirectory.getAdminArea(), null);
            }
        }  else if (mySwitchURL != null) {
            if (myAdminInfo.getTarget() == myAdminInfo.getAnchor()) {
                clearWCProperty(myAdminInfo.getTarget(), myTarget);
            } else {
                clearWCProperty(myAdminInfo.getTarget(), null);
            }
        }
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        checkIfPathIsUnderRoot(path);
        String name = SVNPathUtil.tail(path);
        SVNAdminArea parentArea = myCurrentDirectory.getAdminArea();
        SVNEntry entry = parentArea.getEntry(name, true);
        if (entry == null) {
            return;
        }

        SVNLog log = myCurrentDirectory.getLog();
        SVNProperties attributes = new SVNProperties();

        attributes.put(SVNLog.NAME_ATTR, name);
        log.addCommand(SVNLog.DELETE_ENTRY, attributes, false);
        SVNNodeKind kind = entry.getKind();
        long previousRevision = entry.getRevision();
        boolean isDeleted = entry.isDeleted();
	      SVNURL url = entry.getSVNURL();
        if (path.equals(myTarget)) {
            attributes.put(SVNLog.NAME_ATTR, name);
            attributes.put(SVNProperty.shortPropertyName(SVNProperty.KIND), kind == SVNNodeKind.DIR ? SVNProperty.KIND_DIR : SVNProperty.KIND_FILE);
            attributes.put(SVNProperty.shortPropertyName(SVNProperty.REVISION), Long.toString(myTargetRevision));
            attributes.put(SVNProperty.shortPropertyName(SVNProperty.DELETED), Boolean.TRUE.toString());
            log.addCommand(SVNLog.MODIFY_ENTRY, attributes, false);
            myIsTargetDeleted = true;
        }
        try {
            myCurrentDirectory.flushLog(); 
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Error writing log file for ''{0}''", myCurrentDirectory.getPath());
            SVNErrorManager.error(err, svne, SVNLogType.WC);
        }
        if (myIsLockOnDemand && kind == SVNNodeKind.DIR) {
            SVNAdminArea childArea = myWCAccess.getAdminArea(parentArea.getFile(name));
            if (childArea != null && !childArea.isLocked()) {
                childArea.lock(false);
            }
        }
        if (mySwitchURL != null && kind == SVNNodeKind.DIR) {
            SVNAdminArea childArea = myWCAccess.retrieve(parentArea.getFile(name));
            try {
                childArea.removeFromRevisionControl(childArea.getThisDirName(), true, true);
            } catch (SVNException svne) {
                handleLeftLocalModificationsError(svne);
            }
        }
        try {
            myCurrentDirectory.runLogs();
        } catch (SVNException svne) {
            handleLeftLocalModificationsError(svne);
        }

        if (isDeleted) {
            // entry was deleted, but it was already deleted, no need to make a
            // notification.
            return;
        }
        SVNEvent event = SVNEventFactory.createSVNEvent(parentArea.getFile(name), kind, null, 
                SVNRepository.INVALID_REVISION, SVNEventAction.UPDATE_DELETE, null, null, null);
        event.setPreviousRevision(previousRevision);
        event.setPreviousURL(url);
        myWCAccess.handleEvent(event);
    }

    private void handleLeftLocalModificationsError(SVNException originalError) throws SVNException {
        SVNException error = null;
        for (error = originalError; error != null;) {
            if (error.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LEFT_LOCAL_MOD) {
                break;
            }
            error = (error.getCause() instanceof SVNException) ? (SVNException) error.getCause() : null; 
        }
        if (error != null) {
            return;
        }
        throw originalError;
    }
    
    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        SVNAdminArea parentArea = myCurrentDirectory.getAdminArea();
        SVNDirectoryInfo parentDirectory = myCurrentDirectory;
        myCurrentDirectory = createDirectoryInfo(myCurrentDirectory, path, true);
        myCurrentDirectory.myPreviousRevision = -1;

        if (path.equals(myTarget)) {
            myCurrentDirectory.myAmbientDepth = myRequestedDepth == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : myRequestedDepth;
        } else if (myRequestedDepth == SVNDepth.IMMEDIATES || (myRequestedDepth == SVNDepth.UNKNOWN && 
                parentDirectory.myAmbientDepth == SVNDepth.IMMEDIATES)) {
            myCurrentDirectory.myAmbientDepth = SVNDepth.EMPTY;
        } else {
            myCurrentDirectory.myAmbientDepth = SVNDepth.INFINITY;
        }
        
        parentDirectory.flushLog();
        checkIfPathIsUnderRoot(path);
        String name = SVNPathUtil.tail(path);
        File childDir = parentArea.getFile(name);
        SVNFileType kind = SVNFileType.getType(childDir);
        if (kind == SVNFileType.FILE || kind == SVNFileType.UNKNOWN) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                    "Failed to add directory ''{0}'': a non-directory object of the same name already exists", 
                    path);
            SVNErrorManager.error(err, SVNLogType.WC);
        } 

        if (kind == SVNFileType.DIRECTORY) {
            SVNAdminArea adminArea = null;
            try {
                adminArea = SVNWCAccess.newInstance(null).open(childDir, false, 0);
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                    throw svne;
                }
                if (myIsUnversionedObstructionsAllowed) {
                    myCurrentDirectory.isExisted = true;
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                            "Failed to add directory ''{0}'': an unversioned directory of the same name already exists", 
                            myCurrentDirectory.getPath());
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }

            if (adminArea != null) {
                SVNEntry entry = adminArea.getEntry(adminArea.getThisDirName(), false);
                if (entry != null && entry.isScheduledForAddition() && !entry.isCopied()) {
                    myCurrentDirectory.isAddExisted = true;
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                            "Failed to add directory ''{0}'': a versioned directory of the same name already exists", 
                            myCurrentDirectory.getPath());
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        }
        
        if (SVNFileUtil.getAdminDirectoryName().equals(name)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                    "Failed to add directory ''{0}'':  object of the same name as the administrative directory", 
                    path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (copyFromPath != null || SVNRevision.isValidRevisionNumber(copyFromRevision)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                    "Failed to add directory ''{0}'': copyfrom arguments not yet supported", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        SVNEntry entry = parentArea.getEntry(name, false);
        Map attributes = new SVNHashMap();
        attributes.put(SVNProperty.KIND, SVNProperty.KIND_DIR);
        attributes.put(SVNProperty.ABSENT, null);
        attributes.put(SVNProperty.DELETED, null);
        boolean force = false;
        if (myCurrentDirectory.isAddExisted) {
            attributes.put(SVNProperty.SCHEDULE, null);
            force = true;
        }
        entry = parentArea.modifyEntry(name, attributes, true, force);
        
        if (myCurrentDirectory.isAddExisted) {
            attributes.put(SVNProperty.REVISION, Long.toString(myTargetRevision));
            if (mySwitchURL != null) {
                attributes.put(SVNProperty.URL, SVNPathUtil.append(mySwitchURL, name));
            }
            SVNAdminArea adminArea = myCurrentDirectory.getAdminArea();
            adminArea.modifyEntry(adminArea.getThisDirName(), attributes, true, true);
        }
        
        String rootURL = null;
        if (SVNPathUtil.isAncestor(myRootURL, myCurrentDirectory.URL)) {
            rootURL = myRootURL;
        }
        if (myWCAccess.getAdminArea(childDir) != null) {
            myWCAccess.closeAdminArea(childDir);
        }
        
        if (SVNWCManager.ensureAdminAreaExists(childDir, myCurrentDirectory.URL, rootURL, null, 
                myTargetRevision, myCurrentDirectory.myAmbientDepth)) {
            // hack : remove created lock file.
            SVNFileUtil.deleteFile(new Resource(childDir, SVNFileUtil.getAdminDirectoryName() + "/lock"));
        }
        SVNAdminArea childArea = myWCAccess.open(childDir, true, 0);
        myWCAccess.registerCleanupHandler(childArea, myCurrentDirectory);
        if (!myCurrentDirectory.isAddExisted) {
            SVNEvent event = SVNEventFactory.createSVNEvent(parentArea.getFile(entry.getName()), 
                    SVNNodeKind.DIR, null, myTargetRevision, myCurrentDirectory.isExisted ? 
                            SVNEventAction.UPDATE_EXISTS : SVNEventAction.UPDATE_ADD, null, null, null);
            event.setPreviousRevision(myCurrentDirectory.myPreviousRevision);
            event.setPreviousURL(entry.getSVNURL());
            event.setURL(myCurrentDirectory.URL != null ? SVNURL.parseURIEncoded(myCurrentDirectory.URL) : null);
            myWCAccess.handleEvent(event);
        }
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory.flushLog();
        checkIfPathIsUnderRoot(path);
        myCurrentDirectory = createDirectoryInfo(myCurrentDirectory, path, false);
        SVNAdminArea adminArea = myCurrentDirectory.getAdminArea(); 
        myWCAccess.registerCleanupHandler(adminArea, myCurrentDirectory);
        SVNEntry entry = adminArea.getEntry(adminArea.getThisDirName(), true);        
        if (entry != null) {
            myCurrentDirectory.myPreviousRevision = entry.getRevision();
            myCurrentDirectory.myAmbientDepth = entry.getDepth();
            boolean hasPropConflicts = adminArea.hasPropConflict(adminArea.getThisDirName());
            if (hasPropConflicts) {
                myCurrentDirectory.isSkipped = true;
                Collection skippedPaths = getSkippedPaths();
                skippedPaths.add(adminArea.getRoot());
                SVNEvent event = SVNEventFactory.createSVNEvent(adminArea.getRoot(), SVNNodeKind.DIR, null, 
                        myTargetRevision, SVNStatusType.INAPPLICABLE, SVNStatusType.CONFLICTED, 
                        SVNStatusType.LOCK_INAPPLICABLE, SVNEventAction.SKIP, SVNEventAction.UPDATE_UPDATE, 
                        null, null);
                event.setPreviousRevision(entry.getRevision());
	            event.setURL(entry.getSVNURL());
                myWCAccess.handleEvent(event);
                return;
            }
        } else {
            myCurrentDirectory.myPreviousRevision = -1;
        }
        
        Map attributes = new SVNHashMap();
        attributes.put(SVNProperty.REVISION, Long.toString(myTargetRevision));
        attributes.put(SVNProperty.URL, myCurrentDirectory.URL);
        attributes.put(SVNProperty.INCOMPLETE, Boolean.TRUE.toString());
        
        if (myRootURL != null && SVNPathUtil.isAncestor(myRootURL, myCurrentDirectory.URL)) {
            attributes.put(SVNProperty.REPOS, myRootURL);
        }
        entry = adminArea.modifyEntry(adminArea.getThisDirName(), attributes, true, false);

        if (mySwitchURL != null) {
            clearWCProperty(myCurrentDirectory.getAdminArea(), null);
        }
    }

    private Collection getSkippedPaths() {
        if (mySkippedPaths == null) {
            mySkippedPaths = new LinkedList();
        }
        return mySkippedPaths;
    }

    public void absentDir(String path) throws SVNException {
        absentEntry(path, SVNNodeKind.DIR);
    }

    public void absentFile(String path) throws SVNException {
        absentEntry(path, SVNNodeKind.FILE);
    }

    private void absentEntry(String path, SVNNodeKind kind) throws SVNException {
        String name = SVNPathUtil.tail(path);
        SVNAdminArea adminArea = myCurrentDirectory.getAdminArea();
        SVNEntry entry = adminArea.getEntry(name, false);
        if (entry != null && entry.isScheduledForAddition()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                    "Failed to mark ''{0}'' absent: item of the same name is already scheduled for addition", 
                    path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        Map attributes = new SVNHashMap();
        attributes.put(SVNProperty.REVISION, Long.toString(myTargetRevision));
        attributes.put(SVNProperty.KIND, kind.toString());
        attributes.put(SVNProperty.DELETED, null);
        attributes.put(SVNProperty.ABSENT, Boolean.TRUE.toString());
        entry = adminArea.modifyEntry(name, attributes, true, false);
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        if (!myCurrentDirectory.isSkipped) {
            myCurrentDirectory.propertyChanged(name, value);
        }
    }

    private void clearWCProperty(SVNAdminArea adminArea, String target) throws SVNException {
        if (adminArea == null) {
            return;
        }
        for (Iterator ents = adminArea.entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (target != null) {
                if (entry.isFile() && target.equals(entry.getName())) {
                    SVNVersionedProperties props = adminArea.getWCProperties(entry.getName());
                    props.setPropertyValue(SVNProperty.WC_URL, null);
                    adminArea.saveWCProperties(false);
                }
                continue;
            }
            if (entry.isFile() || adminArea.getThisDirName().equals(entry.getName())) {
                SVNVersionedProperties props = adminArea.getWCProperties(entry.getName());
                props.setPropertyValue(SVNProperty.WC_URL, null);
                adminArea.saveWCProperties(false);
            } else {
                SVNAdminArea childArea = myAdminInfo.getWCAccess().getAdminArea(adminArea.getFile(entry.getName()));
                clearWCProperty(childArea, null);
            }
        }
    }

    public void closeDir() throws SVNException {
        SVNProperties modifiedWCProps = myCurrentDirectory.getChangedWCProperties();
        SVNProperties modifiedEntryProps = myCurrentDirectory.getChangedEntryProperties();
        SVNProperties modifiedProps = myCurrentDirectory.getChangedProperties();

        SVNStatusType propStatus = SVNStatusType.UNKNOWN;
        SVNAdminArea adminArea = myCurrentDirectory.getAdminArea();
        
        if (myAdminInfo.isIncomplete(myCurrentDirectory.getPath())) {
            // delete all props.
            SVNVersionedProperties oldBaseProps = adminArea.getBaseProperties(adminArea.getThisDirName());
            SVNProperties baseMap = oldBaseProps.asMap();
            if (modifiedProps == null) {
                modifiedProps = new SVNProperties();
            }
            for(Iterator names = baseMap.nameSet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                if (!modifiedProps.containsName(name)) {
                    modifiedProps.put(name, SVNPropertyValue.create(null));
                }
            }
        }
        
        if (modifiedWCProps != null || modifiedEntryProps != null || modifiedProps != null) {
            SVNLog log = myCurrentDirectory.getLog();
            if (modifiedProps != null && !modifiedProps.isEmpty()) {
                if (modifiedProps.containsName(SVNProperty.EXTERNALS)) {
                    String oldExternal = adminArea.getProperties(adminArea.getThisDirName()).getStringPropertyValue(SVNProperty.EXTERNALS);
                    String newExternal = modifiedProps.getStringValue(SVNProperty.EXTERNALS);
                    String path = myCurrentDirectory.getPath();
                    if (oldExternal == null && newExternal != null) {
                        myAdminInfo.addExternal(path, oldExternal, newExternal);
                        myAdminInfo.addDepth(path, myCurrentDirectory.myAmbientDepth);
                    } else if (oldExternal != null && newExternal == null) {
                        myAdminInfo.addExternal(path, oldExternal, newExternal);
                        myAdminInfo.addDepth(path, myCurrentDirectory.myAmbientDepth);
                    } else if (oldExternal != null && !oldExternal.equals(newExternal)) {
                        myAdminInfo.addExternal(path, oldExternal, newExternal);
                        myAdminInfo.addDepth(path, myCurrentDirectory.myAmbientDepth);
                    }
                }
                SVNVersionedProperties oldBaseProps = adminArea.getBaseProperties(adminArea.getThisDirName());
                try {
                    propStatus = adminArea.mergeProperties(adminArea.getThisDirName(), oldBaseProps.asMap(), 
                    		modifiedProps, null, null, true, false, log);
                } catch (SVNException svne) {
                    SVNErrorMessage err = svne.getErrorMessage().wrap("Couldn't do property merge");
                    SVNErrorManager.error(err, svne, SVNLogType.WC);
                }
            }
            log.logChangedEntryProperties(adminArea.getThisDirName(), modifiedEntryProps);
            log.logChangedWCProperties(adminArea.getThisDirName(), modifiedWCProps);
        }

        myCurrentDirectory.flushLog();
        myCurrentDirectory.runLogs();
        maybeBumpDirInfo(myCurrentDirectory);
        if (!myCurrentDirectory.isSkipped && (myCurrentDirectory.isAddExisted || !myCurrentDirectory.IsAdded)) {
            if (!(adminArea == myAdminInfo.getAnchor() && !"".equals(myAdminInfo.getTargetName()))) {
                // skip event for anchor when there is a target.
                SVNEventAction action = myCurrentDirectory.isAddExisted || myCurrentDirectory.isExisted ? SVNEventAction.UPDATE_EXISTS : SVNEventAction.UPDATE_UPDATE;
                if (propStatus == SVNStatusType.UNKNOWN && action != SVNEventAction.UPDATE_EXISTS) {
                    action = SVNEventAction.UPDATE_NONE;
                }
                SVNEvent event = SVNEventFactory.createSVNEvent(adminArea.getRoot(), SVNNodeKind.DIR, null, myTargetRevision, SVNStatusType.UNKNOWN, propStatus, null, action, null, null, null);
                event.setPreviousRevision(myCurrentDirectory.myPreviousRevision);
	            event.setURL(myCurrentDirectory.URL != null ? SVNURL.parseURIEncoded(myCurrentDirectory.URL) : null);
                myWCAccess.handleEvent(event);
            }
        }
        myCurrentDirectory = myCurrentDirectory.Parent;
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (myTarget != null && myWCAccess.isMissing(myAdminInfo.getAnchor().getFile(myTarget))) {
            myCurrentDirectory = createDirectoryInfo(null, "", false);
            myWCAccess.registerCleanupHandler(myCurrentDirectory.getAdminArea(), myCurrentDirectory);
            deleteEntry(myTarget, myTargetRevision);
        }

        if (!myIsRootOpen) {
            if (myCurrentDirectory == null) {
                myCurrentDirectory = createDirectoryInfo(null, "", false);
            }
            completeDirectory(myCurrentDirectory);
        }
        if (!myIsTargetDeleted) {
            File targetFile = myTarget != null ? myAdminInfo.getAnchor().getFile(myTarget) : myAdminInfo.getAnchor().getRoot(); 
            SVNWCManager.updateCleanup(targetFile, myWCAccess, mySwitchURL, myRootURL, myTargetRevision, true, mySkippedPaths, myRequestedDepth, myIsLockOnDemand);
        }
        return null;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentFile = addFile(myCurrentDirectory, path, copyFromPath, copyFromRevision);
    }

    public void openFile(String path, long revision) throws SVNException {
        myCurrentFile = openFile(path, myCurrentDirectory);
    }

    public void changeFileProperty(String commitPath, String name, SVNPropertyValue value) throws SVNException {
        changeFileProperty(name, value, myCurrentFile);
    }

    public void applyTextDelta(String commitPath, String baseChecksum) throws SVNException {
        if (myCurrentFile.isSkipped) {
            return;
        }
        myCurrentFile.receivedTextDelta = true;
        
        SVNAdminArea adminArea = myCurrentFile.getAdminArea();
        SVNEntry entry = adminArea.getEntry(myCurrentFile.Name, false);
        boolean replaced = entry != null && entry.isScheduledForReplacement();
        boolean useRevertBase = replaced && entry.getCopyFromURL() != null;

        if (useRevertBase) {
            myCurrentFile.baseFile = adminArea.getFile(SVNAdminUtil.getTextRevertPath(myCurrentFile.Name, false));
            myCurrentFile.newBaseFile = adminArea.getFile(SVNAdminUtil.getTextRevertPath(myCurrentFile.Name, true));
        } else {
            myCurrentFile.baseFile = adminArea.getBaseFile(myCurrentFile.Name, false);
            myCurrentFile.newBaseFile = adminArea.getBaseFile(myCurrentFile.Name, true);
        }
        
        if (entry != null && entry.getChecksum() != null) {
            String realChecksum = SVNFileUtil.computeChecksum(myCurrentFile.baseFile);
            if (baseChecksum != null) {
                if (!baseChecksum.equals(realChecksum)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, 
                            "Checksum mismatch for ''{0}''; expected: ''{1}'', actual: ''{2}''",
                            new Object[] {myCurrentFile.baseFile, baseChecksum, realChecksum}); 
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
            
            String realChecksumSafe = realChecksum == null ? "" : realChecksum;
            String entryChecksumSafe = entry.getChecksum() == null ? "" : entry.getChecksum();
            if (!replaced && !realChecksumSafe.equals(entryChecksumSafe)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, 
                        "Checksum mismatch for ''{0}''; recorded: ''{1}'', actual: ''{2}''",
                        new Object[] {myCurrentFile.baseFile, entry.getChecksum(), realChecksum}); 
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        
        File baseSrcFile = null;
        if (!myCurrentFile.IsAdded) {
            baseSrcFile = myCurrentFile.baseFile;
        } else {
            if (myCurrentFile.copiedBaseText != null) {
                baseSrcFile = myCurrentFile.copiedBaseText;
            }
        }
        File baseTmpFile = myCurrentFile.newBaseFile;
        myDeltaProcessor.applyTextDelta(baseSrcFile, baseTmpFile, true);
    }

    public OutputStream textDeltaChunk(String commitPath, SVNDiffWindow diffWindow) throws SVNException {
        if (!myCurrentFile.isSkipped) {
            try {
                myDeltaProcessor.textDeltaChunk(diffWindow);
            } catch (SVNException svne) {
                myDeltaProcessor.textDeltaEnd();
                SVNFileUtil.deleteFile(myCurrentFile.newBaseFile);
                myCurrentFile.newBaseFile = null;
                throw svne;
            }
        }
        return SVNFileUtil.DUMMY_OUT;
    }

    public void textDeltaEnd(String commitPath) throws SVNException {
        if (!myCurrentFile.isSkipped) {
            myCurrentFile.Checksum = myDeltaProcessor.textDeltaEnd();
        }
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
        closeFile(textChecksum, myCurrentFile, myCurrentDirectory);
        myCurrentFile = null;
    }

    public void abortEdit() throws SVNException {
    }

    private void checkIfPathIsUnderRoot(String path) throws SVNException {
        if (path != null) { 
            String testPath = path.replace(File.separatorChar, '/');
            int ind = -1;
            
            while (testPath.length() > 0 && (ind = testPath.indexOf("..")) != -1) {
                if (ind == 0 || testPath.charAt(ind - 1) == '/') {
                    int i;
                    for (i = ind + 2; i < testPath.length(); i++) {
                        if (testPath.charAt(i) == '.') {
                            continue;
                        } else if (testPath.charAt(i) == '/') {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                                    "Path ''{0}'' is not in the working copy", path);
                            SVNErrorManager.error(err, SVNLogType.WC);
                        } else {
                            break;
                        }
                    }
                    if (i == testPath.length()) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                                "Path ''{0}'' is not in the working copy", path);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                    testPath = testPath.substring(i);
                } else {
                    testPath = testPath.substring(ind + 2);
                }
            }
        }
    }
    
    private void maybeBumpDirInfo(SVNDirectoryInfo dirInfo) throws SVNException {
        while (dirInfo != null) {
            dirInfo.RefCount--;
            if (dirInfo.RefCount > 0) {
                return;
            }
            if (!dirInfo.isSkipped) {
                completeDirectory(dirInfo);
            }
            dirInfo = dirInfo.Parent;
        }        
    }
    
    private void completeDirectory(SVNDirectoryInfo dirInfo) throws SVNException {
        if (dirInfo.Parent == null && myTarget != null) {
            return;
        }

        SVNAdminArea adminArea = dirInfo.getAdminArea();
        SVNEntry entry = adminArea.getEntry(adminArea.getThisDirName(), true); 
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, 
                    "No ''.'' entry found in ''{0}''", adminArea.getRoot());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
                
        entry.setIncomplete(false);
        File target = myAdminInfo.getTarget().getRoot();

        if (myIsDepthSticky && (myRequestedDepth == SVNDepth.INFINITY || (adminArea.getRoot().equals(target) && 
                myRequestedDepth.compareTo(entry.getDepth()) > 0))) {
            entry.setDepth(myRequestedDepth);
            myAdminInfo.addDepth(dirInfo.getPath(), myRequestedDepth);
        }

        for (Iterator ents = adminArea.entries(true); ents.hasNext();) {
            SVNEntry currentEntry = (SVNEntry) ents.next();
            if (currentEntry.isDeleted()) {
                if (!currentEntry.isScheduledForAddition()) {
                    adminArea.deleteEntry(currentEntry.getName());
                } else {
                    Map attributes = new SVNHashMap();
                    attributes.put(SVNProperty.DELETED, null);
                    adminArea.modifyEntry(currentEntry.getName(), attributes, false, false);
                }
            } else if (currentEntry.isAbsent() && currentEntry.getRevision() != myTargetRevision) {
                adminArea.deleteEntry(currentEntry.getName());
            } else if (currentEntry.getKind() == SVNNodeKind.DIR) {
                if (myWCAccess.isMissing(adminArea.getFile(currentEntry.getName())) && !currentEntry.isAbsent() && 
                        !currentEntry.isScheduledForAddition()) {
                    adminArea.deleteEntry(currentEntry.getName());
                    myWCAccess.handleEvent(SVNEventFactory.createSVNEvent(adminArea.getFile(currentEntry.getName()), 
                            currentEntry.getKind(), null, currentEntry.getRevision(), SVNEventAction.UPDATE_DELETE, 
                            null, null, null));
                }
            }
        }
        adminArea.saveEntries(true);
    }

    private SVNFileInfo addFile(SVNDirectoryInfo parent, String path, String copyFromPath, 
            long copyFromRevision) throws SVNException {
        if (copyFromPath != null || SVNRevision.isValidRevisionNumber(copyFromRevision)) {
            if (copyFromPath == null || !SVNRevision.isValidRevisionNumber(copyFromRevision)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OP_ON_CWD, 
                        "Bad copyfrom arguments received.");
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
            return addFileWithHistory(parent, path, copyFromPath, copyFromRevision);
        }

        checkIfPathIsUnderRoot(path);

        SVNFileInfo info = createFileInfo(parent, path, true);
        SVNAdminArea adminArea = parent.getAdminArea();
        SVNFileType kind = SVNFileType.getType(adminArea.getFile(info.Name));
        SVNEntry entry = adminArea.getEntry(info.Name, true);
        
        if (kind != SVNFileType.NONE) {
            if (myIsUnversionedObstructionsAllowed || (entry != null && entry.isScheduledForAddition())) {
                if (entry != null && entry.isCopied()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                            "Failed to add file ''{0}'': a file of the same name is already scheduled for " +
                            "addition with history", path);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                if (kind != SVNFileType.FILE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                            "Failed to add file ''{0}'': a non-file object of the same name already exists", 
                            path);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                if (entry != null) {
                    info.isAddExisted = true;
                } else {
                    info.isExisted = true;
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, 
                        "Failed to add file ''{0}'': object of the same name already exists", path);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }

        return info;
    }

    private SVNFileInfo addFileWithHistory(SVNDirectoryInfo parent, String path, 
            String copyFromPath, long copyFromRevision) throws SVNException {
        SVNFileInfo info = addFile(parent, path, null, SVNRepository.INVALID_REVISION);
        info.addedWithHistory = true;
        
        SVNAdminArea adminArea = parent.getAdminArea();
        SVNEntry pathEntry = adminArea.getEntry(adminArea.getThisDirName(), false);
        SVNEntry srcEntry = null;
        try {
            srcEntry = locateCopyFrom(copyFromPath, copyFromRevision, adminArea.getRoot(), pathEntry);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.WC_COPYFROM_PATH_NOT_FOUND) {
                throw svne;
            }
        }

        info.copiedBaseText = SVNAdminUtil.createTmpFile(adminArea);
        
        SVNProperties baseProperties = null;
        SVNProperties workingProperties = null;
        if (srcEntry != null) {
            SVNAdminArea srcArea = srcEntry.getAdminArea();
            String srcTextBasePath = null;
            if (srcEntry.isScheduledForReplacement() && srcEntry.getCopyFromURL() != null) {
                srcTextBasePath = SVNAdminUtil.getTextRevertPath(srcEntry.getName(), false);
                baseProperties = srcArea.getRevertProperties(srcEntry.getName()).asMap();
                workingProperties = baseProperties;
            } else {
                srcTextBasePath = SVNAdminUtil.getTextBasePath(srcEntry.getName(), false);
                baseProperties = srcArea.getBaseProperties(srcEntry.getName()).asMap();
                workingProperties = srcArea.getProperties(srcEntry.getName()).asMap();
            }
            
            SVNFileUtil.copyFile(srcArea.getFile(srcTextBasePath), info.copiedBaseText, true);
            
            if (srcArea.hasTextModifications(srcEntry.getName(), false, true, false)) {
                info.copiedWorkingText = SVNAdminUtil.createTmpFile(adminArea);
                SVNFileUtil.copyFile(srcArea.getFile(srcEntry.getName()), info.copiedWorkingText, true);
            }
        } else {
            if (myFileFetcher == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_OP_ON_CWD, 
                        "No fetch_func supplied to update_editor.");
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
        
            baseProperties = new SVNProperties();
            OutputStream baseTextOS = null;
            try {
                baseTextOS = SVNFileUtil.openFileForWriting(info.copiedBaseText);
                myFileFetcher.fetchFile(copyFromPath, copyFromRevision, baseTextOS, baseProperties);
            } finally {
                SVNFileUtil.closeFile(baseTextOS);
            }
            workingProperties = baseProperties;
        }

        info.copiedBaseProperties = baseProperties.getRegularProperties();
        info.copiedWorkingProperties = workingProperties.getRegularProperties();
        return info;
    }

    private SVNEntry locateCopyFrom(String copyFromPath, long copyFromRevision, File dstDir, 
            SVNEntry dstEntry) throws SVNException {
        if (dstEntry.getRepositoryRoot() == null || dstEntry.getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_COPYFROM_PATH_NOT_FOUND, 
                    "Destination directory of add-with-history is missing a URL");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        dstDir = new Resource(SVNPathUtil.validateFilePath(dstDir.getAbsolutePath())).getAbsoluteFile();
        String dstReposPath = SVNPathUtil.getPathAsChild(dstEntry.getRepositoryRootURL().toDecodedString(), 
                dstEntry.getSVNURL().toDecodedString());
        if (dstReposPath == null) {
            if (dstEntry.getURL().equals(dstEntry.getRepositoryRoot())) {
                dstReposPath = "";
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_COPYFROM_PATH_NOT_FOUND, 
                        "Destination URLs are broken");
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        
        dstReposPath = "/" + dstReposPath;
        dstReposPath = SVNPathUtil.canonicalizePath(dstReposPath);
        String copyFromParent = SVNPathUtil.removeTail(copyFromPath);
        String ancestorPath = SVNPathUtil.getCommonPathAncestor(dstReposPath, copyFromParent);
        if ("".equals(ancestorPath)) {
            return null;
        }
        
        int levelsUP = SVNPathUtil.getSegmentsCount(dstReposPath) - SVNPathUtil.getSegmentsCount(ancestorPath);
        File currentWD = dstDir;
        for (int i = 0; i < levelsUP && currentWD != null; i++) {
            currentWD = currentWD.getParentFile();
        }
        if (currentWD == null) {
            return null;
        }
        SVNFileType kind = SVNFileType.getType(currentWD);
        if (kind != SVNFileType.DIRECTORY) {
            return null;
        }
        
        SVNWCAccess ancestorAccess = SVNWCAccess.newInstance(null);
        SVNAdminArea ancestorArea = null;
        try {
            ancestorArea = ancestorAccess.open(currentWD, false, 0);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                ancestorAccess.close();
                return null;
            }
            throw svne;
        }
        
        SVNEntry ancestorEntry = ancestorArea.getEntry(ancestorArea.getThisDirName(), false);
        if (dstEntry.getUUID() != null && ancestorEntry.getUUID() != null && 
                !dstEntry.getUUID().equals(ancestorEntry.getUUID())) {
            ancestorAccess.close();
            return null;
        }
        
        SVNURL ancestorURL = dstEntry.getRepositoryRootURL().appendPath(ancestorPath, false);
        if (!ancestorURL.equals(ancestorEntry.getSVNURL())) {
            ancestorAccess.close();
            return null;
        }
        
        String extraComponents = SVNPathUtil.getPathAsChild(ancestorPath, copyFromPath);
        currentWD = new Resource(currentWD, extraComponents);
        File currentWDParent = currentWD.getParentFile();
        
        kind = SVNFileType.getType(currentWD);
        if (kind != SVNFileType.FILE) {
            ancestorAccess.close();
            return null;
        }
        
        try {
            ancestorAccess.close();
            ancestorArea = ancestorAccess.open(currentWDParent, false, 0);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                ancestorAccess.close();
                return null;
            }
            throw svne;
        }
        
        SVNEntry fileEntry = ancestorArea.getEntry(currentWD.getName(), false);
        if (fileEntry == null) {
            ancestorAccess.close();
            return null;
        }
        
        if (fileEntry.getUUID() != null && dstEntry.getUUID() != null && 
                !fileEntry.getUUID().equals(dstEntry.getUUID())) {
            ancestorAccess.close();
            return null;
        }
        
        SVNURL fileURL = fileEntry.getRepositoryRootURL().appendPath(copyFromPath, false);
        if (!fileURL.equals(fileEntry.getSVNURL())) {
            ancestorAccess.close();
            return null;
        }
        
        if (!SVNRevision.isValidRevisionNumber(fileEntry.getCommittedRevision()) || 
                !SVNRevision.isValidRevisionNumber(fileEntry.getRevision())) {
            ancestorAccess.close();
            return null;
        }
        
        if (!(fileEntry.getCommittedRevision() <= copyFromRevision 
                && copyFromRevision <= fileEntry.getRevision())) {
            ancestorAccess.close();
            return null;
        }
        
        return fileEntry;
    }
    
    private void changeFileProperty(String name, SVNPropertyValue value, SVNFileInfo fileInfo) {
        if (!fileInfo.isSkipped) {
            fileInfo.propertyChanged(name, value);
            if (myWCAccess.getOptions().isUseCommitTimes() && SVNProperty.COMMITTED_DATE.equals(name)) {
                fileInfo.CommitTime = value.getString();
                if (fileInfo.CommitTime != null) {
                    fileInfo.CommitTime = fileInfo.CommitTime.trim();
                }
            }
        }
    }

    private SVNFileInfo openFile(String path, SVNDirectoryInfo parent) throws SVNException {
        checkIfPathIsUnderRoot(path);
        SVNFileInfo info = createFileInfo(parent, path, false);
        SVNAdminArea adminArea = parent.getAdminArea();
        SVNEntry entry = adminArea.getEntry(info.Name, true);

        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, 
                    "File ''{0}'' in directory ''{1}'' is not a versioned resource", 
                    new Object[] {info.Name, adminArea.getRoot()});
            SVNErrorManager.error(err, SVNLogType.WC);
        }
            
        boolean hasTextConflicts = adminArea.hasTextConflict(info.Name);
        boolean hasPropConflicts = adminArea.hasPropConflict(info.Name);
        if (hasTextConflicts || hasPropConflicts) {
            info.isSkipped = true;
            Collection skippedPaths = getSkippedPaths();
            File file = new Resource(myAdminInfo.getAnchor().getRoot(), path);
            skippedPaths.add(file);
            SVNEvent event = SVNEventFactory.createSVNEvent(adminArea.getFile(info.Name), SVNNodeKind.FILE, 
                    null, myTargetRevision, hasTextConflicts ? SVNStatusType.CONFLICTED : SVNStatusType.UNKNOWN,
                    hasPropConflicts ? SVNStatusType.CONFLICTED : SVNStatusType.UNKNOWN,
                    SVNStatusType.LOCK_INAPPLICABLE, SVNEventAction.SKIP, SVNEventAction.UPDATE_UPDATE, null, null);
            event.setPreviousRevision(entry.getRevision());
	        event.setURL(entry.getSVNURL());
            myWCAccess.handleEvent(event);
        }
        return info;
    }
    
    private void closeFile(String textChecksum, SVNFileInfo fileInfo, SVNDirectoryInfo dirInfo) throws SVNException {
        if (fileInfo.isSkipped) {
            maybeBumpDirInfo(dirInfo);
            return;
        }

        if (fileInfo.addedWithHistory && !fileInfo.receivedTextDelta) {
            if (fileInfo.baseFile != null || fileInfo.newBaseFile != null || fileInfo.copiedBaseText == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "assertion failure in " +
                        "SVNUpdateEditor.closeFile(): fileInfo.baseFile = {0}, fileInfo.newBaseFile = {1}, " +
                        "fileInfo.copiedBaseText = {2}", new Object[] { fileInfo.baseFile, fileInfo.newBaseFile, 
                        fileInfo.copiedBaseText });
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
            SVNAdminArea adminArea = fileInfo.getAdminArea();
            SVNEntry entry = adminArea.getEntry(fileInfo.Name, false);
            boolean replaced = entry != null && entry.isScheduledForReplacement();
            boolean useRevertBase = replaced && entry.getCopyFromURL() != null;
            if (useRevertBase) {
                fileInfo.baseFile = adminArea.getFile(SVNAdminUtil.getTextRevertPath(fileInfo.Name, false));
                fileInfo.newBaseFile = adminArea.getFile(SVNAdminUtil.getTextRevertPath(fileInfo.Name, true));
            } else {
                fileInfo.baseFile = adminArea.getBaseFile(fileInfo.Name, false);
                fileInfo.newBaseFile = adminArea.getBaseFile(fileInfo.Name, true);
            }
            SVNFileUtil.copyFile(fileInfo.copiedBaseText, fileInfo.newBaseFile, true);
            fileInfo.Checksum = SVNFileUtil.computeChecksum(fileInfo.newBaseFile);
        }
        
        // check checksum.
        String checksum = null;
        boolean isTextUpdated = fileInfo.newBaseFile != null;
        if (textChecksum != null && isTextUpdated) {            
            if (fileInfo.Checksum != null && !textChecksum.equals(fileInfo.Checksum)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH, 
                        "Checksum mismatch for ''{0}''; expected: ''{1}'', actual: ''{2}''",
                        new Object[] {fileInfo.getPath(), textChecksum, fileInfo.Checksum}); 
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            checksum = textChecksum;
        }
        
        SVNAdminArea adminArea = fileInfo.getAdminArea();
        SVNLog log = dirInfo.getLog();
        String name = fileInfo.Name;
        SVNEntry fileEntry = adminArea.getEntry(name, false);
        if (fileEntry == null && !fileInfo.IsAdded) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, 
                    "''{0}'' is not under version control", fileInfo.getPath());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        long previousRevision = fileEntry != null ? fileEntry.getRevision() : -1;
	    SVNURL previousURL = fileEntry != null ? fileEntry.getSVNURL() : null;

        // merge props.
        SVNProperties modifiedWCProps = fileInfo.getChangedWCProperties();
        SVNProperties modifiedEntryProps = fileInfo.getChangedEntryProperties();
        SVNProperties modifiedProps = fileInfo.getChangedProperties();
        String commitTime = fileInfo.CommitTime;

        SVNProperties command = new SVNProperties();

        SVNStatusType textStatus = SVNStatusType.UNCHANGED;
        SVNStatusType lockStatus = SVNStatusType.LOCK_UNCHANGED;
        
        if (myAdminInfo.isIncomplete(fileInfo.getPath()) && fileEntry != null) {
            // delete all props.
            SVNVersionedProperties oldBaseProps = adminArea.getBaseProperties(fileEntry.getName());
            SVNProperties baseMap = oldBaseProps.asMap();
            if (modifiedProps == null) {
                modifiedProps = new SVNProperties();
            }
            for(Iterator names = baseMap.nameSet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                if (!modifiedProps.containsName(propName)) {
                    modifiedProps.put(propName, SVNPropertyValue.create(null));
                }
            }
        }


        boolean magicPropsChanged = false;
        if (modifiedProps != null && !modifiedProps.isEmpty()) {
            magicPropsChanged = modifiedProps.containsName(SVNProperty.EXECUTABLE) ||
            modifiedProps.containsName(SVNProperty.NEEDS_LOCK) ||
            modifiedProps.containsName(SVNProperty.KEYWORDS) ||
            modifiedProps.containsName(SVNProperty.EOL_STYLE) ||
            modifiedProps.containsName(SVNProperty.CHARSET) ||
            modifiedProps.containsName(SVNProperty.SPECIAL);
        }
        
        SVNStatusType propStatus = adminArea.mergeProperties(name, null, fileInfo.copiedBaseProperties, 
                fileInfo.copiedWorkingProperties, modifiedProps, null, null, true, false, log);
        if (modifiedEntryProps != null) {
            lockStatus = log.logChangedEntryProperties(name, modifiedEntryProps);
        }
        if (modifiedWCProps != null) {
            log.logChangedWCProperties(name, modifiedWCProps);
        }

        boolean isLocallyModified = false;
        if (fileInfo.copiedWorkingText != null) {
            isLocallyModified = true;
        } else if (!fileInfo.isExisted) { 
            isLocallyModified = adminArea.hasTextModifications(name, false, false, false);
        } else if (isTextUpdated) {
            isLocallyModified = adminArea.hasVersionedFileTextChanges(adminArea.getFile(name), 
                    fileInfo.newBaseFile, false);
        }
        
        boolean isReplaced = fileEntry != null && fileEntry.isScheduledForReplacement();
        
        SVNProperties logAttributes = new SVNProperties();
        if (fileInfo.isAddExisted) {
            logAttributes.put(SVNLog.FORCE_ATTR, "true");
            logAttributes.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE), "");
        } 
        
        log.logTweakEntry(name, fileInfo.URL, myTargetRevision);
        
        String absDirPath = adminArea.getRoot().getAbsolutePath().replace(File.separatorChar, '/');
        String basePath = null;
        if (fileInfo.baseFile != null) {
            String absBasePath = fileInfo.baseFile.getAbsolutePath().replace(File.separatorChar, '/');
            basePath = absBasePath.substring(absDirPath.length());
            if (basePath.startsWith("/")) {
                basePath = basePath.substring(1);
            }
        }
        
        String tmpBasePath = null;
        if (fileInfo.newBaseFile != null) {
            String absTmpBasePath = fileInfo.newBaseFile.getAbsolutePath().replace(File.separatorChar, '/');
            tmpBasePath = absTmpBasePath.substring(absDirPath.length());
            if (tmpBasePath.startsWith("/")) {
                tmpBasePath = tmpBasePath.substring(1);
            }
        }

        SVNStatusType mergeOutcome = SVNStatusType.UNCHANGED;
        File workingFile = adminArea.getFile(name);
        boolean deletedCopiedBaseText = false;
        if (tmpBasePath != null) {
            textStatus = SVNStatusType.CHANGED;
            // there is a text to replace the working copy with.
            if (!isLocallyModified && !isReplaced) {
                if (fileEntry == null || !fileEntry.isScheduledForDeletion()) {
                    command.put(SVNLog.NAME_ATTR, tmpBasePath);
                    command.put(SVNLog.DEST_ATTR, name);
                    log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                    command.clear();
                }
            } else {
                SVNFileType kind = SVNFileType.getType(workingFile);
                if (kind == SVNFileType.NONE && !fileInfo.addedWithHistory) {
                    command.put(SVNLog.NAME_ATTR, tmpBasePath);
                    command.put(SVNLog.DEST_ATTR, name);
                    log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                    command.clear();
                } else if (!fileInfo.isExisted) {
                    File mergeLeftFile = fileInfo.baseFile;
                    String pathExt = null;
                    if (myExtensionPatterns != null && myExtensionPatterns.length > 0) {
                        int dotInd = name.lastIndexOf('.'); 
                        if (dotInd != -1 && dotInd != 0 && dotInd != name.length() - 1) {
                            pathExt = name.substring(dotInd + 1);
                        }
                        if (pathExt != null && !"".equals(pathExt)) {
                            boolean matches = false;
                            for (int i = 0; i < myExtensionPatterns.length; i++) {
                                String extPattern = myExtensionPatterns[i];
                                matches = DefaultSVNOptions.matches(extPattern, pathExt);    
                                if (matches) {
                                    break;
                                }
                            }
                            if (!matches) {
                                pathExt = null;
                            }
                        }
                    }

                    boolean deleteLeftMergeFile = false;
                    boolean deleteCopiedBaseText = false;
                    if (fileInfo.isAddExisted && !isReplaced) {
                        deleteLeftMergeFile = true;
                        mergeLeftFile = SVNAdminUtil.createTmpFile(adminArea);
                    } else if (fileInfo.copiedBaseText != null) {
                        deleteLeftMergeFile = deleteCopiedBaseText = true;
                        mergeLeftFile = fileInfo.copiedBaseText;
                    } 

                    String absMergeLeftFilePath = mergeLeftFile.getAbsolutePath().replace(File.separatorChar, '/');
                    String mergeLeftFilePath = absMergeLeftFilePath.substring(absDirPath.length());
                    if (mergeLeftFilePath.startsWith("/")) {
                        mergeLeftFilePath = mergeLeftFilePath.substring(1);
                    }

                    String leftLabel = null;
                    if (fileInfo.addedWithHistory) {
                        leftLabel = ".copied" + (pathExt != null ? "." + pathExt : "");
                    } else {
                        leftLabel = ".r" + fileEntry.getRevision() + (pathExt != null ? "." + pathExt : ""); 
                    }
                    
                    String rightLabel = ".r" + myTargetRevision + (pathExt != null ? "." + pathExt : "");
                    String mineLabel = ".mine" + (pathExt != null ? "." + pathExt : "");
                    // do test merge.
                    mergeOutcome = adminArea.mergeText(name, mergeLeftFile, adminArea.getFile(tmpBasePath), 
                            fileInfo.copiedWorkingText, mineLabel, leftLabel, rightLabel, modifiedProps, false, 
                            null, log);
                    if (mergeOutcome == SVNStatusType.UNCHANGED) {
                        textStatus = SVNStatusType.MERGED;
                    }

                    if (deleteLeftMergeFile) {
                        command.put(SVNLog.NAME_ATTR, mergeLeftFilePath);
                        log.addCommand(SVNLog.DELETE, command, false);
                        command.clear();
                        if (deleteCopiedBaseText) {
                            deletedCopiedBaseText = true;
                        }
                    }
                    
                    if (fileInfo.copiedWorkingText != null) {
                        String absCopiedWorkingTextPath = fileInfo.copiedWorkingText.getAbsolutePath().replace(File.separatorChar, '/');
                        String copiedWorkingTextPath = absCopiedWorkingTextPath.substring(absDirPath.length());
                        if (copiedWorkingTextPath.startsWith("/")) {
                            copiedWorkingTextPath = copiedWorkingTextPath.substring(1);
                        }
                        command.put(SVNLog.NAME_ATTR, copiedWorkingTextPath);
                        log.addCommand(SVNLog.DELETE, command, false);
                        command.clear();
                    }
                }
            }
        } else {
            if (magicPropsChanged && (workingFile.exists() || SVNFileType.getType(workingFile) == SVNFileType.SYMLINK)) {
                // only props were changed, but we have to retranslate file.
                // only if wc file exists (may be locally deleted), otherwise no
                // need to retranslate...
                String tmpPath = SVNAdminUtil.getTextBasePath(name, true);
                command.put(SVNLog.NAME_ATTR, name);
                command.put(SVNLog.DEST_ATTR, tmpPath);
                log.addCommand(SVNLog.COPY_AND_DETRANSLATE, command, false);
                command.clear();
                command.put(SVNLog.NAME_ATTR, tmpPath);
                command.put(SVNLog.DEST_ATTR, name);
                log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                    command.clear();
                }
            if (lockStatus == SVNStatusType.LOCK_UNLOCKED) {
                command.put(SVNLog.NAME_ATTR, name);
                log.addCommand(SVNLog.MAYBE_READONLY, command, false);
                command.clear();
            }
        }
        
        if (tmpBasePath != null) {
            command.put(SVNLog.NAME_ATTR, tmpBasePath);
            command.put(SVNLog.DEST_ATTR, basePath);
            log.addCommand(SVNLog.MOVE, command, false);
            command.clear();
            command.put(SVNLog.NAME_ATTR, basePath);
            log.addCommand(SVNLog.READONLY, command, false);
            command.clear();
            if (!isReplaced) {
                logAttributes.put(SVNProperty.shortPropertyName(SVNProperty.CHECKSUM), checksum);
            }
        }

        if (logAttributes.size() > 0) {
            logAttributes.put(SVNLog.NAME_ATTR, name);
            log.addCommand(SVNLog.MODIFY_ENTRY, logAttributes, false);
        }
        
        if (!isLocallyModified && (fileInfo.IsAdded || fileEntry.getSchedule() == null)) {
            if (commitTime != null && !fileInfo.isExisted) {
                command.put(SVNLog.NAME_ATTR, name);
                command.put(SVNLog.TIMESTAMP_ATTR, commitTime);
                log.addCommand(SVNLog.SET_TIMESTAMP, command, false);
                command.clear();
            }
            
            if (tmpBasePath != null || magicPropsChanged) {
                command.put(SVNLog.NAME_ATTR, name);
                command.put(SVNProperty.shortPropertyName(SVNProperty.TEXT_TIME), SVNLog.WC_TIMESTAMP);
                log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
                command.clear();
            }

            command.put(SVNLog.NAME_ATTR, name);
            command.put(SVNProperty.shortPropertyName(SVNProperty.WORKING_SIZE), SVNLog.WC_WORKING_SIZE);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
        }

        if (fileInfo.copiedBaseText != null && !deletedCopiedBaseText) {
            String absCopiedBaseTextPath = fileInfo.copiedBaseText.getAbsolutePath().replace(File.separatorChar, '/');
            String copiedBaseTextPath = absCopiedBaseTextPath.substring(absDirPath.length());
            if (copiedBaseTextPath.startsWith("/")) {
                copiedBaseTextPath = copiedBaseTextPath.substring(1);
            }
            command.put(SVNLog.NAME_ATTR, copiedBaseTextPath);
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
        }
        
        // bump.
        maybeBumpDirInfo(dirInfo);

        if (mergeOutcome == SVNStatusType.CONFLICTED_UNRESOLVED) {
            textStatus = SVNStatusType.CONFLICTED_UNRESOLVED;
        } else if (mergeOutcome == SVNStatusType.CONFLICTED) {
            textStatus = SVNStatusType.CONFLICTED;
        } else if (fileInfo.newBaseFile != null) {
            if (isLocallyModified) {
                textStatus = SVNStatusType.MERGED;
            } else {
                textStatus = SVNStatusType.CHANGED;
            }
        }

        // notify.
        if ((textStatus != SVNStatusType.UNCHANGED || 
                propStatus != SVNStatusType.UNCHANGED || 
                lockStatus != SVNStatusType.LOCK_UNCHANGED)) {
            SVNEventAction action = SVNEventAction.UPDATE_UPDATE;
            if (fileInfo.isExisted || fileInfo.isAddExisted) {
                if (textStatus != SVNStatusType.CONFLICTED_UNRESOLVED && textStatus != SVNStatusType.CONFLICTED) {
                    action = SVNEventAction.UPDATE_EXISTS;
                } 
            } else if (fileInfo.IsAdded) {
                action = SVNEventAction.UPDATE_ADD;
            }
            SVNEvent event = SVNEventFactory.createSVNEvent(adminArea.getFile(fileInfo.Name), SVNNodeKind.FILE,  null, myTargetRevision, textStatus, propStatus, lockStatus, action, null, null, null);
            event.setPreviousRevision(previousRevision);
	        event.setPreviousURL(previousURL);
	        event.setURL(fileInfo.URL != null ? SVNURL.parseURIEncoded(fileInfo.URL) : null);
            myWCAccess.handleEvent(event);
        }
    }

    private SVNFileInfo createFileInfo(SVNDirectoryInfo parent, String path, boolean added) throws SVNException {
        SVNFileInfo info = new SVNFileInfo(parent, path);
        info.IsAdded = added;
        info.Name = SVNPathUtil.tail(path);
        info.isExisted = false;
        info.isAddExisted = false;
        info.isSkipped = false;
        info.baseFile = null;
        info.newBaseFile = null;

        SVNAdminArea adminArea = parent.getAdminArea();
        SVNEntry entry = adminArea.getEntry(info.Name, true);

        if (mySwitchURL != null || entry == null) {
            info.URL = SVNPathUtil.append(parent.URL, SVNEncodingUtil.uriEncode(info.Name));
        } else {
            info.URL = entry.getURL();
        }
        parent.RefCount++;
        return info;
    }

    private SVNDirectoryInfo createDirectoryInfo(SVNDirectoryInfo parent, String path, 
            boolean added) {
        SVNDirectoryInfo info = new SVNDirectoryInfo(path);
        info.Parent = parent;
        info.IsAdded = added;
        String name = path != null ? SVNPathUtil.tail(path) : "";

        if (mySwitchURL == null) {
            SVNAdminArea area = null;
            SVNEntry dirEntry = null;
            
            File areaPath = new Resource(myAdminInfo.getAnchor().getRoot(), info.getPath());            
            try {
                area = myWCAccess.getAdminArea(areaPath);
                if (area != null) {
                    // could be missing.
                    dirEntry = area.getEntry(area.getThisDirName(), false);
                }
            } catch (SVNException svne) {
                //
            }
            
            if (area != null && dirEntry != null) {
                info.URL = dirEntry.getURL();
            }
            if (info.URL == null && parent != null) {
                info.URL = SVNPathUtil.append(parent.URL, SVNEncodingUtil.uriEncode(name));
            } else if (info.URL == null && parent == null) {
                info.URL = myTargetURL;
            }
        } else {
            if (parent == null) {
                info.URL = myTarget == null ? mySwitchURL : SVNPathUtil.removeTail(mySwitchURL);
            } else {
                if (myTarget != null && parent.Parent == null) {
                    info.URL = mySwitchURL;
                } else {
                    info.URL = SVNPathUtil.append(parent.URL, SVNEncodingUtil.uriEncode(name));
                }
            }
        }
        info.RefCount = 1;
        info.isSkipped = false;
        if (info.Parent != null) {
            info.Parent.RefCount++;
        }
        info.isExisted = false;
        info.isAddExisted = false;
        info.log = null;
        info.myAmbientDepth = SVNDepth.UNKNOWN;
        
        return info;
    }

    public static SVNUpdateEditor15 createUpdateEditor(SVNAdminAreaInfo info, String switchURL,
            boolean allowUnversionedObstructions, boolean depthIsSticky, SVNDepth depth, 
            String[] preservedExtensions, ISVNFileFetcher fileFetcher, boolean lockOnDemand) throws SVNException {
        if (depth == SVNDepth.UNKNOWN) {
            depthIsSticky = false;
        }

        SVNEntry entry = info.getAnchor().getEntry(info.getAnchor().getThisDirName(), false);
        if (switchURL != null && entry != null && entry.getRepositoryRoot() != null) {
            if (!SVNPathUtil.isAncestor(entry.getRepositoryRoot(), switchURL)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SWITCH, 
                        "''{0}''\nis not the same repository as\n''{1}''",
                        new Object[] { switchURL, entry.getRepositoryRoot() });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }

        SVNUpdateEditor15 editor =
            new SVNUpdateEditor15(info, switchURL, allowUnversionedObstructions, depthIsSticky, depth, preservedExtensions,
                    entry != null ? entry.getURL() : null, entry != null ? entry.getRepositoryRoot() : null, fileFetcher, lockOnDemand);
        info.getTarget().closeEntries();

        return editor;
    }
    
    private class SVNEntryInfo {

        public String URL;
        public boolean IsAdded;
        public boolean isExisted;
        public boolean isAddExisted;
        public SVNDirectoryInfo Parent;
        public boolean isSkipped;
        public long myPreviousRevision;

        private String myPath;
        private SVNProperties myChangedProperties;
        private SVNProperties myChangedEntryProperties;
        private SVNProperties myChangedWCProperties;

        protected SVNEntryInfo(String path) {
            myPath = path;
        }

        protected String getPath() {
            return myPath;
        }

        public void propertyChanged(String name, SVNPropertyValue value) {
            if (name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                myChangedEntryProperties = myChangedEntryProperties == null ? new SVNProperties() : myChangedEntryProperties;
                // trim value of svn:entry property
                if (value != null) {
	                String strValue = value.getString();
	                if (strValue != null) {
	                    strValue = strValue.trim();
	                    value = SVNPropertyValue.create(strValue);
	                }
                }
                myChangedEntryProperties.put(name.substring(SVNProperty.SVN_ENTRY_PREFIX.length()), value);
            } else if (name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                myChangedWCProperties = myChangedWCProperties == null ? new SVNProperties() : myChangedWCProperties;
                myChangedWCProperties.put(name, value);
            } else {
                myChangedProperties = myChangedProperties == null ? new SVNProperties() : myChangedProperties;
                myChangedProperties.put(name, value);
            }
        }

        public SVNProperties getChangedWCProperties() {
            return myChangedWCProperties;
        }

        public SVNProperties getChangedEntryProperties() {
            return myChangedEntryProperties;
        }

        public SVNProperties getChangedProperties() {
            return myChangedProperties;
        }
    }

    private class SVNFileInfo extends SVNEntryInfo {
        public String Name;
        public String CommitTime;
        public String Checksum;
        public File baseFile;
        public File newBaseFile;
        public boolean addedWithHistory;
        public boolean receivedTextDelta;
        private SVNProperties copiedBaseProperties;
        private SVNProperties copiedWorkingProperties;
        private File copiedBaseText;
        private File copiedWorkingText;
        
        public SVNFileInfo(SVNDirectoryInfo parent, String path) {
            super(path);
            this.Parent = parent;
        }

        public SVNAdminArea getAdminArea() throws SVNException {
            return Parent.getAdminArea();
        }
    }

    private class SVNDirectoryInfo extends SVNEntryInfo implements ISVNCleanupHandler {

        public int RefCount;
        private SVNLog log;
        public int LogCount;
        public SVNDepth myAmbientDepth;

        public SVNDirectoryInfo(String path) {
            super(path);
        }

        public SVNAdminArea getAdminArea() throws SVNException {
            String path = getPath();
            File file = new Resource(myAdminInfo.getAnchor().getRoot(), path);
            SVNAdminArea area = myAdminInfo.getWCAccess().retrieve(file);
            if (myIsLockOnDemand && area != null && !area.isLocked()) {
                area.lock(false);
                area = myAdminInfo.getWCAccess().upgrade(file);
            }
            return area;
        }

        public SVNLog getLog() throws SVNException {
            if (log == null) {
                log = getAdminArea().getLog();    
                LogCount++;
            }
            return log;
        }

        public void flushLog() throws SVNException {
            if (log != null) {
                log.save();
                log = null;
        }
        }

        public void runLogs() throws SVNException {
            LogCount = 0;
            getAdminArea().runLogs();
        }

        public void cleanup(SVNAdminArea area) throws SVNException {
            if (area != null && LogCount > 0) {
                LogCount = 0;
                area.runLogs();
            }
        }
    }

    public void cleanup(SVNAdminArea area) throws SVNException {
        area.runLogs();
    }
}
