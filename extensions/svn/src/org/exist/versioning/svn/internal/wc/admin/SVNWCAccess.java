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
package org.exist.versioning.svn.internal.wc.admin;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.DefaultSVNOptions;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNFileType;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.SVNMergeCallback;
import org.exist.versioning.svn.internal.wc.SVNMergeCallback15;
import org.exist.versioning.svn.internal.wc.SVNMergeDriver;
import org.exist.versioning.svn.internal.wc.SVNUpdateEditor;
import org.exist.versioning.svn.internal.wc.SVNUpdateEditor15;
import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.exist.versioning.svn.wc.ISVNOptions;
import org.exist.versioning.svn.wc.SVNEvent;
import org.exist.versioning.svn.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNFileFetcher;
import org.tmatesoft.svn.core.internal.wc.ISVNUpdateEditor;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNWCAccess implements ISVNEventHandler {
    
    public static final int INFINITE_DEPTH = -1;
    
    private ISVNEventHandler myEventHandler;
    private ISVNOptions myOptions;
    private Map myAdminAreas;
    private Map myCleanupHandlers;

    private File myAnchor;

    public static SVNWCAccess newInstance(ISVNEventHandler eventHandler) {
        return new SVNWCAccess(eventHandler);
    }
    
    private SVNWCAccess(ISVNEventHandler handler) {
        myEventHandler = handler;
    }
    
    public void setEventHandler(ISVNEventHandler handler) {
        myEventHandler = handler;
    }
    
    public ISVNEventHandler getEventHandler() {
        return myEventHandler;
    }
    
    public void checkCancelled() throws SVNCancelException {
        if (myEventHandler != null) {
            myEventHandler.checkCancelled();
        }
    }

    public void handleEvent(SVNEvent event) throws SVNException {
        handleEvent(event, ISVNEventHandler.UNKNOWN);
    }
    
    public void registerCleanupHandler(SVNAdminArea area, ISVNCleanupHandler handler) {
        if (area == null || handler == null) {
            return;
        }
        if (myCleanupHandlers == null) {
            myCleanupHandlers = new SVNHashMap();
        }
        myCleanupHandlers.put(area, handler);
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        if (myEventHandler != null) {
            try {
                myEventHandler.handleEvent(event, progress);
            } catch (SVNException e) {
                throw e;
            } catch (Throwable th) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Error while dispatching event: {0}", th.getMessage());
                SVNErrorManager.error(err, th, SVNLogType.WC);
            }
        }
    }

    public void setOptions(ISVNOptions options) {
        myOptions = options;
    }

    public ISVNOptions getOptions() {
        if (myOptions == null) {
            myOptions = new DefaultSVNOptions();
        }
        return myOptions;
    }
    
    public void setAnchor(File anchor) {
        myAnchor = anchor;
    }
    
    public File getAnchor() {
        return myAnchor;
    }

    public SVNAdminAreaInfo openAnchor(File path, boolean writeLock, int depth) throws SVNException {
        File parent = path.getParentFile();
        if (parent == null || "..".equals(path.getName())) {
            SVNAdminArea anchor = open(path, writeLock, depth);
            return new SVNAdminAreaInfo(this, anchor, anchor, "");
        }

        String name = path.getName();
        SVNAdminArea parentArea = null;
        SVNAdminArea targetArea = null; 
        SVNException parentError = null;
        
        try {
            parentArea = open(parent, writeLock, false, 0);
        } catch (SVNException svne) {
            if (writeLock && svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LOCKED) {
                try {
                    parentArea = open(parent, false, false, 0);
                } catch (SVNException svne2) {
                    throw svne;
                }
                parentError = svne;
            } else if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                throw svne;
            }
        }
        
        try {
            targetArea = open(path, writeLock, false, depth);
        } catch (SVNException svne) {
            if (parentArea == null || svne.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                try {
                    close();
                } catch (SVNException svne2) {
                    //
                }
                throw svne;
            }
        }
        
        if (parentArea != null && targetArea != null) {
            SVNEntry parentEntry = null;
            SVNEntry targetEntry = null;
            SVNEntry targetInParent = null;
            try {
                targetInParent = parentArea.getEntry(name, false);
                targetEntry = targetArea.getEntry(targetArea.getThisDirName(), false);
                parentEntry = parentArea.getEntry(parentArea.getThisDirName(), false);
            } catch (SVNException svne) {
                try {
                    close();
                } catch (SVNException svne2) {
                    //
                }
                throw svne;
            }
            
            SVNURL parentURL = parentEntry != null ? parentEntry.getSVNURL() : null;
            SVNURL targetURL = targetEntry != null ? targetEntry.getSVNURL() : null;
            String encodedName = SVNEncodingUtil.uriEncode(name);
            if (targetInParent == null || (parentURL != null && targetURL != null && 
                    (!parentURL.equals(targetURL.removePathTail()) || !encodedName.equals(SVNPathUtil.tail(targetURL.getURIEncodedPath()))))) {
                if (myAdminAreas != null) {
                    myAdminAreas.remove(parent);
                }
                try {
                    doClose(parentArea, false);
                } catch (SVNException svne) {
                    try {
                        close();
                    } catch (SVNException svne2) {
                        //
                    }
                    throw svne;
                }
                parentArea = null;
            }
        }
        
        if (parentArea != null) {
            if (parentError != null && targetArea != null) {
                if (parentError.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LOCKED) {
                    // try to work without 'anchor'
                    try {
                        doClose(parentArea, false);
                    } catch (SVNException svne) {
                        try {
                            close();
                        } catch (SVNException svne2) {
                            //
                        }
                        throw svne;
                    }
                    parentArea = null;
                } else {
                    try {
                        close();
                    } catch (SVNException svne) {
                        //
                    }
                    throw parentError;
                }
            }
        }

        if (targetArea == null) {
            SVNEntry targetEntry = null;
            try {
                targetEntry = parentArea.getEntry(name, false); 
            } catch (SVNException svne) {
                try {
                    close();
                } catch (SVNException svne2) {
                    //
                }
                throw svne;
            }
            if (targetEntry != null && targetEntry.isDirectory()) {
                if (myAdminAreas != null) {
                    myAdminAreas.put(path, null);
                }
            }
        }
        SVNAdminArea anchor = parentArea != null ? parentArea : targetArea;
        SVNAdminArea target = targetArea != null ? targetArea : parentArea;
        return new SVNAdminAreaInfo(this, anchor, target, parentArea == null ? "" : name);
    }

    public SVNAdminArea open(File path, boolean writeLock, int depth) throws SVNException {
        return open(path, writeLock, false, depth);
    }
    
    public SVNAdminArea open(File path, boolean writeLock, boolean stealLock, int depth) throws SVNException {
        return open(path, writeLock, stealLock, true, depth, Level.FINE);
    }

    public SVNAdminArea open(File path, boolean writeLock, boolean stealLock, boolean upgradeFormat, int depth, Level logLevel) throws SVNException {
        Map tmp = new SVNHashMap();
        SVNAdminArea area;
        try {
            area = doOpen(path, writeLock, stealLock, upgradeFormat, depth, tmp, logLevel);
        } finally {
            for(Iterator paths = tmp.keySet().iterator(); paths.hasNext();) {
                Object childPath = paths.next();
                SVNAdminArea childArea = (SVNAdminArea) tmp.get(childPath);
                myAdminAreas.put(childPath, childArea);
            }
        }
        return area;
    }

	public SVNAdminArea probeOpen(File path, boolean writeLock, int depth) throws SVNException {
		return probeOpen(path, writeLock, depth, Level.FINE);
	}

    public SVNAdminArea probeOpen(File path, boolean writeLock, int depth, Level logLevel) throws SVNException {
        File dir = probe(path, logLevel);
        if (dir == null) {
            // we tried to open root which is not wc.
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
            SVNErrorManager.error(err, logLevel, SVNLogType.WC);
        }
        if (!path.equals(dir)) {
            depth = 0;
        }
        SVNAdminArea adminArea = null;
        try {
            adminArea = open(dir, writeLock, false, true, depth, logLevel);
        } catch (SVNException svne) {
            SVNFileType childKind = SVNFileType.getType(path);
            SVNErrorCode errCode = svne.getErrorMessage().getErrorCode(); 
            if (!path.equals(dir) && childKind == SVNFileType.DIRECTORY && errCode == SVNErrorCode.WC_NOT_DIRECTORY) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
                SVNErrorManager.error(err, logLevel, SVNLogType.WC);
            } else {
                throw svne;
            }
        }
        return adminArea;
    }
    
    public SVNAdminArea probeTry(File path, boolean writeLock, int depth) throws SVNException {
        SVNAdminArea adminArea = null;
        try {
            adminArea = probeRetrieve(path);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                try {
                    adminArea = probeOpen(path, writeLock, depth);
                } catch (SVNException svne2) {
                    if (svne2.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                        throw svne2; 
                    }
                }
            } else {
                throw svne;
            }
        }
        return adminArea;
    }
    
    public void close() throws SVNException {
        if (myAdminAreas != null) {
            doClose(myAdminAreas, false);
            myAdminAreas.clear();
        }
        myCleanupHandlers = null;
    }
    
    public void closeAdminArea(File path) throws SVNException {
        if (myAdminAreas != null) {
            SVNAdminArea area = (SVNAdminArea) myAdminAreas.get(path);
            if (area != null) {
                doClose(area, false);
                myAdminAreas.remove(path);
            }
        }
    }
    
    private SVNAdminArea doOpen(File path, boolean writeLock, boolean stealLock, boolean upgradeFormat, int depth, Map tmp, Level logLevel) throws SVNException {
        // no support for 'under consturction here' - it will go to adminAreaFactory.
        tmp = tmp == null ? new SVNHashMap() : tmp; 
        if (myAdminAreas != null) {
            SVNAdminArea existing = (SVNAdminArea) myAdminAreas.get(path);
            if (myAdminAreas.containsKey(path) && existing != null) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' locked", path);
                SVNErrorManager.error(error, SVNLogType.WC);
            }
        } else {
            myAdminAreas = new SVNHashMap();
        }
        
        SVNAdminArea area = SVNAdminAreaFactory.open(path, logLevel);
        area.setWCAccess(this);

        if (writeLock) {
            area.lock(stealLock);
            if (upgradeFormat) {
                area = SVNAdminAreaFactory.upgrade(area);
            }
        }
        tmp.put(path, area);
        
        if (depth != 0) {
            if (depth > 0) {
                depth--;
            }
            for(Iterator entries = area.entries(false); entries.hasNext();) {
                try {
                    checkCancelled(); 
                } catch (SVNCancelException e) {
                    doClose(tmp, false);
                    throw e;
                }
                
                SVNEntry entry = (SVNEntry) entries.next();
                if (entry.getKind() != SVNNodeKind.DIR  || area.getThisDirName().equals(entry.getName())) {
                    continue;
                }
                if (entry.getDepth() == SVNDepth.EXCLUDE) {
                    continue;
                }
                File childPath = new Resource(path, entry.getName());
                try {
                    // this method will put created area into our map.
                    doOpen(childPath, writeLock, stealLock, upgradeFormat, depth, tmp, logLevel);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                        doClose(tmp, false);
                        throw e;
                    }
                    // only for missing!
                    tmp.put(childPath, null);
                }
                
                SVNAdminArea childArea = (SVNAdminArea) tmp.get(childPath);                
                if (childArea != null) {
                    SVNEntry childRootEntry = childArea.getEntry(childArea.getThisDirName(), false);
                    SVNEntry thisRootEntry = area.getEntry(childArea.getThisDirName(), false);
                    
                    String childRoot = childRootEntry.getRepositoryRoot();
                    String expectedRoot = thisRootEntry.getRepositoryRoot();
                    
                    if (childRoot != null && !childRoot.equals(expectedRoot)) {
                        Map toClose = new SVNHashMap();
                        toClose.put(childPath, childArea);
                        String childPathAbs = childPath.getAbsolutePath().replace(File.separatorChar, '/');
                        for (Iterator paths = tmp.keySet().iterator(); paths.hasNext();) {
                            File p = (File) paths.next();
                            String pAbs = p.getAbsolutePath().replace(File.separatorChar, '/');
                            if (SVNPathUtil.isAncestor(childPathAbs, pAbs)) {
                                toClose.put(p, tmp.get(p));
                                paths.remove();
                            }
                        }
                        tmp.put(childPath, null);
                        doClose(toClose, false);
                    }
                }
            }
        }
        return area;
    }
    
    private void doClose(Map adminAreas, boolean preserveLocks) throws SVNException {
        Set closedAreas = new SVNHashSet();
        while(!adminAreas.isEmpty()) {
            Map copy = new SVNHashMap(adminAreas);
            try {
                for (Iterator paths = copy.keySet().iterator(); paths.hasNext();) {
                    File path = (File) paths.next();
                    SVNAdminArea adminArea = (SVNAdminArea) copy.get(path);
                    if (adminArea == null) {
                        closedAreas.add(path);
                        continue;
                    }
                    doClose(adminArea, preserveLocks);
                    closedAreas.add(path);
                }
            } finally {
                for (Iterator paths = closedAreas.iterator(); paths.hasNext();) {
                    File path = (File) paths.next();
                    adminAreas.remove(path);
                }
            }
        }
    }

    private void doClose(SVNAdminArea adminArea, boolean preserveLocks) throws SVNException {
        if (adminArea == null) {
            return;
        }
        if (myCleanupHandlers != null) {
            ISVNCleanupHandler handler = (ISVNCleanupHandler) myCleanupHandlers.remove(adminArea);
            if (handler != null) {
                handler.cleanup(adminArea);
            }
        }
        if (!preserveLocks && adminArea.isLocked()) {
            adminArea.unlock();
        }
    }

    public SVNAdminArea probeRetrieve(File path) throws SVNException {
        File dir = probe(path, Level.FINE);
        if (dir == null) {
            // we tried to open root which is not wc.
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return retrieve(dir);
    }
    
    public boolean isMissing(File path) {
        if (myAdminAreas != null) {
            return myAdminAreas.containsKey(path) && myAdminAreas.get(path) == null;
        }
        return false;
    }

    public boolean isLocked(File path) throws SVNException {
        File lockFile = new Resource(path, SVNFileUtil.getAdminDirectoryName());
        lockFile = new Resource(lockFile, "lock");
        if (SVNFileType.getType(lockFile) == SVNFileType.FILE) {
            return true;
        } else if (SVNFileType.getType(lockFile) == SVNFileType.NONE) {
            return false;
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, 
                "Lock file ''{0}'' is not a regular file", lockFile);
        SVNErrorManager.error(err, SVNLogType.WC);
        return false;
    }
    
    public boolean isWCRoot(File path) throws SVNException {
        SVNEntry entry = getEntry(path, false);
        File parent = path.getParentFile(); 
        if (parent == null && entry != null) {
            return true;
        }
        SVNAdminArea parentArea = getAdminArea(parent);
        SVNWCAccess tmpAccess = null;
        SVNWCAccess access = this;
        try {
            if (parentArea == null) {
                tmpAccess = new SVNWCAccess(null);
                try {
                    parentArea = tmpAccess.probeOpen(parent, false, 0, Level.FINEST);
                } catch (SVNException svne) {
                    return true;
                }
                access = tmpAccess;
            }
            
            SVNEntry parentEntry = access.getEntry(parent, false);
            if (parentEntry == null || !parentEntry.isThisDir()) {
                return true;
            }
            
            if (parentEntry.getURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                        "''{0}'' has no ancestry information", parent);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            
            // what about switched paths?
            /*
            if (entry != null && entry.getURL() != null) {
                if (!entry.getURL().equals(SVNPathUtil.append(parentEntry.getURL(), SVNEncodingUtil.uriEncode(path.getName())))) {
                    return true;
                }
            }*/
            entry = parentArea.getEntry(path.getName(), false);
            if (entry == null) {
                return true;
            }
        } finally {
            if (tmpAccess != null) {
                try {
                    tmpAccess.close();
                } catch (SVNException svne) {
                    //
                }
            }
        }
        return false;
    }

    public SVNTreeConflictDescription getTreeConflict(File path) throws SVNException {
        File parent = path.getParentFile();
        if (parent == null) {
            return null;
        }
        boolean closeParentArea = false;
        SVNAdminArea parentArea = null;
        try {
            parentArea = retrieve(parent);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                e = null;
                try {
                    parentArea = open(parent, false, 0);
                    closeParentArea = true;
                } catch (SVNException internal) {
                    if (internal.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                        return null;
                    }
                    e = internal;
                }
            }
            if (e != null) {
                throw e;
            }
        }
        SVNTreeConflictDescription treeConflict = parentArea.getTreeConflict(path.getName());
        if (closeParentArea) {
            closeAdminArea(parent);
        }
        return treeConflict;
    }

    public boolean hasTreeConflict(File path) throws SVNException {
        SVNTreeConflictDescription treeConflict = getTreeConflict(path);
        return treeConflict != null;
    }
    
    public SVNEntry getEntry(File path, boolean showHidden) throws SVNException {
        SVNAdminArea adminArea = getAdminArea(path);
        String entryName = null;
        if (adminArea == null) {
            adminArea = getAdminArea(path.getParentFile());
            entryName = path.getName();
        } else {
            entryName = adminArea.getThisDirName();
        }
        
        if (adminArea != null) {
            return adminArea.getEntry(entryName, showHidden);
        }
        return null;
    }
    
    public SVNEntry getVersionedEntry(File path, boolean showHidden) throws SVNException {
        SVNEntry entry = getEntry(path, showHidden);
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, 
                    "''{0}'' is not under version control", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return entry;

    }
    
    public void setRepositoryRoot(File path, SVNURL reposRoot) throws SVNException {
        SVNEntry entry = getEntry(path, false);
        if (entry == null) {
            return;
        }
        SVNAdminArea adminArea = null;
        String name = null;
        if (entry.isFile()) {
            adminArea = getAdminArea(path.getParentFile());
            name = path.getName();
        } else {
            adminArea = getAdminArea(path);
            name = adminArea != null ? adminArea.getThisDirName() : null;
        }
        
        if (adminArea == null) {
            return;
        }
        if (adminArea.tweakEntry(name, null, reposRoot.toString(), -1, false)) {
            adminArea.saveEntries(false);
        }
    }
    
    public SVNAdminArea[] getAdminAreas() {
        if (myAdminAreas != null) {
            return (SVNAdminArea[]) myAdminAreas.values().toArray(new SVNAdminArea[myAdminAreas.size()]);
        }
        return new SVNAdminArea[0];
    }

    /**
     * Ugrades SVNAdminArea associated with the path and cached in this SVNWCAccess instance.
     * Updates caches if upgrade was done.
     *
     * @param  path                           path associated with already retrieved and locked SVNAdminArea
     * @return                                newly created SVNAdminArea object if upgrade was done or already cached SVNAdminArea instance otherwise.
     * @throws SVNException
     */
    public SVNAdminArea upgrade(File path) throws SVNException {
        SVNAdminArea upgradedArea = null;
        if (myAdminAreas != null) {
            SVNAdminArea area = (SVNAdminArea) myAdminAreas.get(path);
            if (area != null) {
                ISVNCleanupHandler cleanupHandler = null;
                if (myCleanupHandlers != null) {
                    cleanupHandler = (ISVNCleanupHandler) myCleanupHandlers.get(area);
                }
                upgradedArea = SVNAdminAreaFactory.upgrade(area);
                if (upgradedArea != area) {
                    myAdminAreas.put(path, upgradedArea);
                    if (cleanupHandler != null) {
                        myCleanupHandlers.remove(area);
                        myCleanupHandlers.put(upgradedArea, cleanupHandler);
                    }
                }
            }
        }
        return upgradedArea;
    }
    
    public SVNAdminArea retrieve(File path) throws SVNException {
        SVNAdminArea adminArea = getAdminArea(path);
        if (adminArea == null) {
            SVNEntry subEntry = null;
            try {
                SVNAdminArea dirAdminArea = getAdminArea(path.getParentFile());
                if (dirAdminArea != null) {
                    subEntry = dirAdminArea.getEntry(path.getName(), true);
                }
            } catch (SVNException svne) {
                subEntry = null;
            }
            SVNFileType type = SVNFileType.getType(path);
            if (subEntry != null) {
                if (subEntry.getKind() == SVNNodeKind.DIR && type == SVNFileType.FILE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Expected ''{0}'' to be a directory but found a file", path);
                    SVNErrorManager.error(err, SVNLogType.WC);
                } else if (subEntry.getKind() == SVNNodeKind.FILE && type == SVNFileType.DIRECTORY) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Expected ''{0}'' to be a file but found a directory", path);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
            File adminDir = new Resource(path, SVNFileUtil.getAdminDirectoryName());
            SVNFileType wcType = SVNFileType.getType(adminDir);
            
            if (type == SVNFileType.NONE) {
                SVNErrorMessage childErr = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "Directory ''{0}'' is missing", path);
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Directory ''{0}'' is missing", path);
                err.setChildErrorMessage(childErr);
                SVNErrorManager.error(err, SVNLogType.WC);
            } else if (type == SVNFileType.DIRECTORY && wcType == SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Directory ''{0}'' containing working copy admin area is missing", adminDir);
                SVNErrorManager.error(err, SVNLogType.WC);
            } else if (type == SVNFileType.DIRECTORY && wcType == SVNFileType.DIRECTORY) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Unable to lock ''{0}''", path);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Working copy ''{0}'' is not locked", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return adminArea;
    }

    //analogous to retrieve_internal
    public SVNAdminArea getAdminArea(File path) {
        //internal retrieve
        SVNAdminArea adminArea = null; 
        if (myAdminAreas != null) {
            adminArea = (SVNAdminArea) myAdminAreas.get(path);
        }
        return adminArea;
    }

    public void walkEntries(File path, ISVNEntryHandler handler, boolean showHidden, SVNDepth depth) throws SVNException {
        walkEntries(path, handler, showHidden, false, depth);
    }
    
    public void walkEntries(File path, ISVNEntryHandler handler, boolean showHidden, boolean includeTC, SVNDepth depth) throws SVNException {
        // wrap handler into tc handler
        if (includeTC) {
            handler = new TCEntryHandler(path, this, handler, depth);
        }
        SVNEntry entry = getEntry(path, showHidden);
        if (entry == null) {
            if (includeTC) {
                SVNTreeConflictDescription tc = getTreeConflict(path);
                if (tc != null) {
                    handler.handleEntry(path, null);
                    return;
                }
            }
            handler.handleError(path, SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, 
                    "''{0}'' is not under version control", path));
            return;
        }
        
        if (entry.isFile()) {
            try {
                handler.handleEntry(path, entry);
            } catch (SVNException svne) {
                handler.handleError(path, svne.getErrorMessage());
            }
        } else if (entry.isDirectory()) {
            SVNAdminArea adminArea = entry.getAdminArea();
            try {
                adminArea.walkThisDirectory(handler, includeTC ? true : showHidden, depth);
            } catch (SVNException svne) {
                handler.handleError(path, svne.getErrorMessage());
            }
        } else {
           handler.handleError(path, SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, 
                   "''{0}'' has an unrecognized node kind", path));
        }
    }
    
    private static boolean ourNeverDescendIntoSymlinks = Boolean.getBoolean("svnkit.symlinks.neverDescend");

    private File probe(File path, Level logLevel) throws SVNException {
        int wcFormat = -1;
        SVNFileType type = SVNFileType.getType(path);
        boolean eligible = type == SVNFileType.DIRECTORY;
        // only treat as directories those, that are not versioned in parent wc.
        if (eligible) {
            wcFormat = SVNAdminAreaFactory.checkWC(path, true, logLevel);
        } else if (type == SVNFileType.SYMLINK && path.isDirectory()) {
            // either wc root which is a link or link within wc.
            // check for being root.
            eligible = !ourNeverDescendIntoSymlinks && isWCRoot(path);
            if (eligible) {
                wcFormat = SVNAdminAreaFactory.checkWC(path, true, logLevel);
            }
        } else {
            wcFormat = 0;
        }
        
        //non wc
        if (!eligible || wcFormat == 0) {
            if ("..".equals(path.getName()) || ".".equals(path.getName())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_BAD_PATH, "Path ''{0}'' ends in ''{1}'', which is unsupported for this operation", new Object[]{path, path.getName()});
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            path = path.getParentFile();
        } 
        return path;
    }

    public static boolean matchesChangeList(Collection changeLists, SVNEntry entry) {
        return changeLists == null || changeLists.isEmpty() || (entry != null && entry.getChangelistName() != null && changeLists.contains(entry.getChangelistName()));
    }

    private int getMaxFormatVersion() {
        int maxVersion = -1;
        for (Iterator iterator = myAdminAreas.values().iterator(); iterator.hasNext();) {
            SVNAdminArea adminArea = (SVNAdminArea) iterator.next();
            if (adminArea != null && adminArea.getFormatVersion() > maxVersion) {
                maxVersion = adminArea.getFormatVersion();
            }
        }
        return maxVersion;
    }

    public ISVNUpdateEditor createUpdateEditor(SVNAdminAreaInfo info, String switchURL,
            boolean allowUnversionedObstructions, boolean depthIsSticky, SVNDepth depth,
            String[] preservedExtensions, ISVNFileFetcher fileFetcher, boolean lockOnDemand) throws SVNException {
        int maxVersion = getMaxFormatVersion();
        if (0 < maxVersion && maxVersion < SVNAdminArea16.WC_FORMAT) {
            return SVNUpdateEditor15.createUpdateEditor(info, switchURL, allowUnversionedObstructions, depthIsSticky, depth, preservedExtensions, fileFetcher, lockOnDemand);
        } 
        return SVNUpdateEditor.createUpdateEditor(info, switchURL, allowUnversionedObstructions, depthIsSticky, depth, preservedExtensions, fileFetcher, lockOnDemand);
    }

    public SVNMergeCallback createMergeCallback(SVNMergeDriver mergeDriver, SVNAdminArea adminArea, SVNURL url,
            SVNDiffOptions mergeOptions, Map conflictedPaths, boolean force, boolean dryRun) {
        int maxVersion = getMaxFormatVersion();
        if (maxVersion < SVNAdminAreaFactory.WC_FORMAT_16) {
            return new SVNMergeCallback15(adminArea, url, force, dryRun,
                    mergeOptions, conflictedPaths, mergeDriver);
        } 
        return new SVNMergeCallback(adminArea, url, force, dryRun, mergeOptions, conflictedPaths, mergeDriver);
    }
    
    private static class TCEntryHandler implements ISVNEntryHandler {
        
        private ISVNEntryHandler myDelegate;
        private SVNDepth myDepth;
        private File myTargetPath;
        private SVNWCAccess myWCAccess;

        public TCEntryHandler(File target, SVNWCAccess wcAccess, ISVNEntryHandler delegate, SVNDepth depth) {
            myDelegate = delegate;
            myDepth = depth;
            myTargetPath = target;
            myWCAccess = wcAccess;
        }

        public void handleEntry(File path, SVNEntry entry) throws SVNException {
            myDelegate.handleEntry(path, entry);
            if (entry == null || !entry.isDirectory() || entry.isHidden()) {
                return;
            }
            boolean checkChildren = false;
            if (myDepth == SVNDepth.IMMEDIATES || myDepth == SVNDepth.FILES) {
                checkChildren = path.equals(myTargetPath);
            } else if (myDepth == SVNDepth.INFINITY || myDepth == SVNDepth.EXCLUDE || myDepth == SVNDepth.UNKNOWN) {
                checkChildren = true;
            } else {
                return;
            }
            if (!checkChildren) {
                return;
            }
            Map tcs = entry.getTreeConflicts();
            for(Iterator paths = tcs.keySet().iterator(); paths.hasNext();) {
                File p = (File) paths.next();
                SVNTreeConflictDescription tc = (SVNTreeConflictDescription) tcs.get(p);
                if (tc.getNodeKind() == SVNNodeKind.DIR && myDepth == SVNDepth.FILES) {
                    continue;
                }
                SVNEntry conflictEntry = myWCAccess.getEntry(p, true);
                if (conflictEntry == null || conflictEntry.isDeleted()) {
                    myDelegate.handleEntry(p, null);
                }
            }
        }

        public void handleError(File path, SVNErrorMessage error) throws SVNException {
            if (error != null && error.getErrorCode() == SVNErrorCode.UNVERSIONED_RESOURCE) {
                SVNTreeConflictDescription tc = myWCAccess.getTreeConflict(path);
                if (tc != null) {
                    myDelegate.handleEntry(path, null);
                    return;
                }
            }
            myDelegate.handleError(path, error);
        }
    }
}
