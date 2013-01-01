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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminAreaFactory;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminAreaInfo;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNLog;
import org.exist.versioning.svn.internal.wc.admin.SVNVersionedProperties;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.exist.versioning.svn.wc.ISVNOptions;
import org.exist.versioning.svn.wc.ISVNStatusHandler;
import org.exist.versioning.svn.wc.SVNEvent;
import org.exist.versioning.svn.wc.SVNEventAction;
import org.exist.versioning.svn.wc.SVNStatus;
import org.exist.versioning.svn.wc.SVNStatusClient;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class SVNWCManager {

    public static void add(File path, SVNAdminArea parentDir, SVNURL copyFromURL, SVNRevision copyFromRev, SVNDepth depth) throws SVNException {
        add(path, parentDir, copyFromURL, copyFromRev.getNumber(), depth);
    }

    public static void add(File path, SVNAdminArea parentDir, SVNURL copyFromURL, long copyFromRev, SVNDepth depth) throws SVNException {
        SVNWCAccess wcAccess = parentDir.getWCAccess();
        SVNFileType fileType = SVNFileType.getType(path);
        if (fileType == SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "''{0}'' not found", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (fileType == SVNFileType.UNKNOWN) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "Unsupported node kind for path ''{0}''", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNAdminArea dir;
        if (fileType == SVNFileType.SYMLINK) {
            dir = wcAccess.probeTry(path.getParentFile(), true, copyFromURL != null ? SVNWCAccess.INFINITE_DEPTH : 0);
        } else {
            dir = wcAccess.probeTry(path, true, copyFromURL != null ? SVNWCAccess.INFINITE_DEPTH : 0);
        }
        SVNEntry entry = null;
        if (dir != null) {
            entry = wcAccess.getEntry(path, true);
        }
        boolean replace = false;
        SVNNodeKind kind = SVNFileType.getNodeKind(fileType);
        if (entry != null) {
            if ((copyFromURL == null && !entry.isScheduledForDeletion() && !entry.isDeleted()) || entry.getDepth() == SVNDepth.EXCLUDE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "''{0}'' is already under version control", path);
                SVNErrorManager.error(err, SVNLogType.WC);
            } else if (entry.getKind() != kind) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NODE_KIND_CHANGE,
                        "Can''t replace ''{0}'' with a node of a different type; the deletion must be committed and the parent updated before adding ''{0}''", path);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            replace = entry.isScheduledForDeletion();
        }
        SVNEntry parentEntry = wcAccess.getEntry(path.getParentFile(), false);
        if (parentEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND,
                    "Can''t find parent directory''s entry while trying to add ''{0}''", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (parentEntry.isScheduledForDeletion()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT,
                    "Can''t add ''{0}'' to a parent directory scheduled for deletion", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        Map command = new SVNHashMap();
        String name = path.getName();
        if (copyFromURL != null) {
            if (parentEntry.getRepositoryRoot() != null && !SVNPathUtil.isAncestor(parentEntry.getRepositoryRoot(), copyFromURL.toString())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                        "The URL ''{0}'' has a different repository root than its parent", copyFromURL);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            command.put(SVNProperty.COPYFROM_URL, copyFromURL.toString());
            command.put(SVNProperty.COPYFROM_REVISION, SVNProperty.toString(copyFromRev));
            command.put(SVNProperty.COPIED, Boolean.TRUE.toString());
        }
        if (replace) {
            command.put(SVNProperty.CHECKSUM, null);
            command.put(SVNProperty.HAS_PROPS, Boolean.FALSE.toString());
            command.put(SVNProperty.HAS_PROP_MODS, Boolean.FALSE.toString());
        }
        command.put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_ADD);
        command.put(SVNProperty.KIND, SVNFileType.getNodeKind(fileType).toString());
        if (!(replace || copyFromURL != null)) {
            command.put(SVNProperty.REVISION, "0");
        }
        parentDir.modifyEntry(name, command, true, false);

        if (entry != null && copyFromURL == null) {
            String propPath = SVNAdminUtil.getPropPath(name, entry.getKind(), false);
            File propFile = dir.getFile(propPath);
            SVNFileUtil.deleteFile(propFile);
        }
        if (replace) {
            SVNProperties props = new SVNProperties();
            if (entry.getKind() == SVNNodeKind.FILE) {
                SVNLog log = parentDir.getLog();
                props.put(SVNLog.NAME_ATTR, SVNAdminUtil.getTextBasePath(entry.getName(), false));
                props.put(SVNLog.DEST_ATTR, SVNAdminUtil.getTextRevertPath(entry.getName(), false));
                log.addCommand(SVNLog.MOVE, props, false);
                log.save();
                parentDir.runLogs();
            }
            createRevertProperties(wcAccess, path, true);
        }
        if (kind == SVNNodeKind.DIR) {
            if (copyFromURL == null) {
                SVNEntry pEntry = wcAccess.getEntry(path.getParentFile(), false);
                SVNURL newURL = pEntry.getSVNURL().appendPath(name, false);
                SVNURL rootURL = pEntry.getRepositoryRootURL();
                String uuid = pEntry.getUUID();
                ensureAdminAreaExists(path, newURL.toString(), rootURL != null ? rootURL.toString() : null, uuid, 0, depth == null ? SVNDepth.INFINITY : depth);
            } else {
                SVNURL rootURL = parentEntry.getRepositoryRootURL();
                ensureAdminAreaExists(path, copyFromURL.toString(), rootURL != null ? rootURL.toString() : null, parentEntry.getUUID(), 
                        copyFromRev, depth == null ? SVNDepth.INFINITY : depth);
            }
            if (entry == null || entry.isDeleted()) {
                dir = wcAccess.open(path, true, copyFromURL != null ? SVNWCAccess.INFINITE_DEPTH : 0);
            }
            command.put(SVNProperty.INCOMPLETE, null);
            command.put(SVNProperty.SCHEDULE, replace ? SVNProperty.SCHEDULE_REPLACE : SVNProperty.SCHEDULE_ADD);
            dir.modifyEntry(dir.getThisDirName(), command, true, true);
            if (copyFromURL != null) {
                SVNURL newURL = parentEntry.getSVNURL().appendPath(name, false);
                updateCleanup(path, wcAccess, newURL.toString(), parentEntry.getRepositoryRoot(), -1, false, null, SVNDepth.INFINITY, false);
                markTree(dir, null, true, false, COPIED | KEEP_LOCAL);
                SVNPropertiesManager.deleteWCProperties(dir, null, true);
            }
        }
        SVNEvent event = SVNEventFactory.createSVNEvent(parentDir.getFile(name), kind, null, 0, SVNEventAction.ADD, null, null, null);
        parentDir.getWCAccess().handleEvent(event);
    }

    public static final int SCHEDULE = 1;
    public static final int COPIED = 2;
    public static final int KEEP_LOCAL = 4;

    // this method is not applicable for "this dir" entry, use markTree() for that case
    public static void markEntry(SVNAdminArea dir, SVNEntry entry, String schedule, boolean copied, boolean keepLocal, int flags) throws SVNException {
        if (dir.getThisDirName().equals(entry.getName())) {
            return;
        }
        Map attributes = new SVNHashMap();
        File path = dir.getFile(entry.getName());
        if (entry.getKind() == SVNNodeKind.DIR) {
            SVNAdminArea childDir = dir.getWCAccess().retrieve(path);
            markTree(childDir, schedule, copied, keepLocal, flags);
        }

        if ((flags & SCHEDULE) != 0) {
            attributes.put(SVNProperty.SCHEDULE, schedule);
        }

        if ((flags & COPIED) != 0) {
            attributes.put(SVNProperty.COPIED, copied ? Boolean.TRUE.toString() : null);
        }

        dir.modifyEntry(entry.getName(), attributes, true, false);

        if (copied) {
            SVNPropertiesManager.deleteWCProperties(dir, entry.getName(), false);
        }

        if (SVNProperty.SCHEDULE_DELETE.equals(schedule)) {
            SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.UNKNOWN, null, 0, SVNEventAction.DELETE, null, null, null);
            dir.getWCAccess().handleEvent(event);
        }
    }

    public static void markTree(SVNAdminArea dir, String schedule, boolean copied, boolean keepLocal, int flags) throws SVNException {
        for (Iterator entries = dir.entries(false); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            markEntry(dir, entry, schedule, copied, keepLocal, flags);
        }

        Map attributes = new SVNHashMap();
        SVNEntry dirEntry = dir.getEntry(dir.getThisDirName(), false);
        if (!(dirEntry.isScheduledForAddition() && SVNProperty.SCHEDULE_DELETE.equals(schedule))) {
            if ((flags & SCHEDULE) != 0) {
                attributes.put(SVNProperty.SCHEDULE, schedule);
            }
            if ((flags & COPIED) != 0) {
                attributes.put(SVNProperty.COPIED, copied ? Boolean.TRUE.toString() : null);
            }
        }

        if ((flags & KEEP_LOCAL) != 0 && keepLocal) {
            attributes.put(SVNProperty.KEEP_LOCAL, SVNProperty.toString(true));
        }

        if (attributes.size() > 0) {
            dir.modifyEntry(dir.getThisDirName(), attributes, true, false);
            attributes.clear();
        }

        dir.saveEntries(false);
    }

    public static void markTreeCancellable(SVNAdminArea dir, String schedule, boolean copied, boolean keepLocal, int flags) throws SVNException {
        Map attributes = new SVNHashMap();
        Map recurseMap = new SVNHashMap();
        for (Iterator entries = dir.entries(false); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (dir.getThisDirName().equals(entry.getName())) {
                continue;
            }
            File path = dir.getFile(entry.getName());
            if (entry.getKind() == SVNNodeKind.DIR) {
                SVNAdminArea childDir = dir.getWCAccess().retrieve(path);
                // leave for recursion, do not set anything on 'dir' entry.
                recurseMap.put(entry.getName(), childDir);
                continue;
            }
            if ((flags & SCHEDULE) != 0) {
                attributes.put(SVNProperty.SCHEDULE, schedule);
            }
            if ((flags & COPIED) != 0) {
                attributes.put(SVNProperty.COPIED, copied ? Boolean.TRUE.toString() : null);
            }
            dir.modifyEntry(entry.getName(), attributes, true, false);
            attributes.clear();
            if (SVNProperty.SCHEDULE_DELETE.equals(schedule)) {
                SVNEvent event = SVNEventFactory.createSVNEvent(dir.getFile(entry.getName()), SVNNodeKind.UNKNOWN, null, 0, SVNEventAction.DELETE, null, null, null);
                dir.getWCAccess().handleEvent(event);
            }
        }
        SVNEntry dirEntry = dir.getEntry(dir.getThisDirName(), false);
        if (!(dirEntry.isScheduledForAddition() && SVNProperty.SCHEDULE_DELETE.equals(schedule))) {
            if ((flags & SCHEDULE) != 0) {
                attributes.put(SVNProperty.SCHEDULE, schedule);
            }
            if ((flags & COPIED) != 0) {
                attributes.put(SVNProperty.COPIED, copied ? Boolean.TRUE.toString() : null);
            }
            if (keepLocal) {
                attributes.put(SVNProperty.KEEP_LOCAL, SVNProperty.toString(true));
            }

            dir.modifyEntry(dir.getThisDirName(), attributes, true, false);
            attributes.clear();
        }
        dir.saveEntries(false);
        // could check for cancellation - entries file saved.
        dir.getWCAccess().checkCancelled();

        // recurse.
        for (Iterator dirs = recurseMap.keySet().iterator(); dirs.hasNext();) {
            String entryName = (String) dirs.next();
            SVNAdminArea childDir = (SVNAdminArea) recurseMap.get(entryName);
            // update 'dir' entry, save entries file again, then enter recursion.
            if ((flags & SCHEDULE) != 0) {
                attributes.put(SVNProperty.SCHEDULE, schedule);
            }
            if ((flags & COPIED) != 0) {
                attributes.put(SVNProperty.COPIED, copied ? Boolean.TRUE.toString() : null);
            }
            dir.modifyEntry(entryName, attributes, true, false);
            attributes.clear();
            if (SVNProperty.SCHEDULE_DELETE.equals(schedule)) {
                SVNEvent event = SVNEventFactory.createSVNEvent(dir.getFile(entryName), SVNNodeKind.UNKNOWN, null, 0, SVNEventAction.DELETE, null, null, null);
                dir.getWCAccess().handleEvent(event);
            }
            dir.saveEntries(false);
            markTree(childDir, schedule, copied, keepLocal, flags);
        }
    }

    public static void updateCleanup(File path, SVNWCAccess wcAccess, String baseURL, String rootURL,
                                     long newRevision, boolean removeMissingDirs, Collection excludePaths, SVNDepth depth, boolean skipUnlocked) throws SVNException {
        SVNEntry entry = wcAccess.getEntry(path, true);
        if (entry == null) {
            return;
        }

        excludePaths = excludePaths == null ? Collections.EMPTY_LIST : excludePaths;
        if (entry.isFile() || (entry.isDirectory() && (entry.isAbsent() || entry.isDeleted() || entry.getDepth() == SVNDepth.EXCLUDE))) {
            if (excludePaths.contains(path)) {
                return;
            }
            SVNAdminArea dir = wcAccess.retrieve(path.getParentFile());
            if (skipUnlocked && !dir.isLocked()) {
                return;
            }
            if (dir.tweakEntry(path.getName(), baseURL, rootURL, newRevision, false)) {
                dir.saveEntries(false);
            }
        } else if (entry.isDirectory()) {
            SVNAdminArea dir = wcAccess.retrieve(path);
            if (skipUnlocked && !dir.isLocked()) {
                return;
            }
            tweakEntries(dir, baseURL, rootURL, newRevision, removeMissingDirs, excludePaths, depth, skipUnlocked);
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Unrecognized node kind: ''{0}''", path);
            SVNErrorManager.error(err, SVNLogType.WC);

        }
    }

    private static void tweakEntries(SVNAdminArea dir, String baseURL, String rootURL, long newRevision, boolean removeMissingDirs, Collection excludePaths, SVNDepth depth, boolean skipUnlocked) throws SVNException {
        if (excludePaths.contains(dir.getRoot())) {
            return;
        }
        boolean write = false;
        write = dir.tweakEntry(dir.getThisDirName(), baseURL, rootURL, newRevision, false);
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }
        if (depth.compareTo(SVNDepth.EMPTY) > 0) {
            for (Iterator entries = dir.entries(true); entries.hasNext();) {
                SVNEntry entry = (SVNEntry) entries.next();
                if (dir.getThisDirName().equals(entry.getName())) {
                    continue;
                }

                File childFile = dir.getFile(entry.getName());
                boolean isExcluded = excludePaths.contains(childFile);

                String childURL = null;
                if (baseURL != null) {
                    childURL = SVNPathUtil.append(baseURL, SVNEncodingUtil.uriEncode(entry.getName()));
                }

                if (entry.isFile() || (entry.isAbsent() || entry.isDeleted() || entry.getDepth() == SVNDepth.EXCLUDE)) {
                    if (!isExcluded) {
                        write |= dir.tweakEntry(entry.getName(), childURL, rootURL, newRevision, true);
                    }
                } else if (entry.isDirectory() && (depth == SVNDepth.INFINITY ||
                        depth == SVNDepth.IMMEDIATES)) {
                    SVNDepth depthBelowHere = depth == SVNDepth.IMMEDIATES ? SVNDepth.EMPTY :
                            depth;

                    File path = dir.getFile(entry.getName());
                    if (removeMissingDirs && dir.getWCAccess().isMissing(path)) {
                        if (!entry.isScheduledForAddition() && !isExcluded) {
                            dir.deleteEntry(entry.getName());
                            dir.getWCAccess().handleEvent(SVNEventFactory.createSVNEvent(dir.getFile(entry.getName()), entry.getKind(), null, entry.getRevision(), SVNEventAction.UPDATE_DELETE, null, null, null));
                        }
                    } else {
                        SVNAdminArea childDir = dir.getWCAccess().retrieve(path);
                        if (skipUnlocked && !childDir.isLocked()) {
                            continue;
                        }
                        tweakEntries(childDir, childURL, rootURL, newRevision, removeMissingDirs, excludePaths, depthBelowHere, skipUnlocked);
                    }
                }
            }
        }
        if (write) {
            dir.saveEntries(false);
        }
    }

    public static boolean ensureAdminAreaExists(File path, String url, String rootURL, String uuid, long revision, SVNDepth depth) throws SVNException {
        SVNFileType fileType = SVNFileType.getType(path);
        if (fileType != SVNFileType.DIRECTORY && fileType != SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "''{0}'' is not a directory", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (fileType == SVNFileType.NONE) {
            SVNAdminAreaFactory.createVersionedDirectory(path, url, rootURL, uuid, revision, depth);
            return true;
        }
        SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
        try {
            wcAccess.open(path, false, 0);
            SVNEntry entry = wcAccess.getVersionedEntry(path, false);
            if (!entry.isScheduledForDeletion()) {
                if (entry.getRevision() != revision) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Revision {0} doesn''t match existing revision {1} in ''{2}''",
                            new Object[]{new Long(revision), new Long(entry.getRevision()), path});
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                if (!entry.getURL().equals(url)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "URL {0} doesn''t match existing URL {1} in ''{2}''",
                            new Object[]{url, entry.getURL(), path});
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                SVNAdminAreaFactory.createVersionedDirectory(path, url, rootURL, uuid, revision, depth);
                return true;
            }
            throw e;
        } finally {
            wcAccess.close();
        }
        return false;
    }

    public static void canDelete(File path, ISVNOptions options, final ISVNEventHandler eventHandler) throws SVNException {
        SVNStatusClient statusClient = new SVNStatusClient((ISVNAuthenticationManager) null, options);
        if (eventHandler != null) {
            statusClient.setEventHandler(new ISVNEventHandler() {
                public void checkCancelled() throws SVNCancelException {
                    eventHandler.checkCancelled();
                }

                public void handleEvent(SVNEvent event, double progress) throws SVNException {
                }
            });
        }
        statusClient.doStatus(path, SVNRevision.UNDEFINED, SVNDepth.INFINITY, false, false, false, false, new ISVNStatusHandler() {
            public void handleStatus(SVNStatus status) throws SVNException {
                if (status.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "''{0}'' is in the way of the resource actually under version control", status.getFile());
                    SVNErrorManager.error(err, SVNLogType.WC);
                } else if (status.getEntry() == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", status.getFile());
                    SVNErrorManager.error(err, SVNLogType.WC);
                } else if ((status.getContentsStatus() != SVNStatusType.STATUS_NORMAL &&
                        status.getContentsStatus() != SVNStatusType.STATUS_DELETED &&
                        status.getContentsStatus() != SVNStatusType.STATUS_MISSING) ||
                        (status.getPropertiesStatus() != SVNStatusType.STATUS_NONE &&
                                status.getPropertiesStatus() != SVNStatusType.STATUS_NORMAL)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MODIFIED, "''{0}'' has local modifications", status.getFile());
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        }, null);
    }

    public static void delete(SVNWCAccess wcAccess, SVNAdminArea root, File path, boolean deleteFiles, boolean cancellable) throws SVNException {
        SVNAdminArea dir = wcAccess.probeTry(path, true, SVNWCAccess.INFINITE_DEPTH);
        SVNEntry entry = null;
        if (dir != null) {
            entry = wcAccess.getEntry(path, false);
        } else {
            SVNWCManager.doDeleteUnversionedFiles(wcAccess, path, deleteFiles);
            return;
        }
        
        if (entry == null) {
            SVNWCManager.doDeleteUnversionedFiles(wcAccess, path, deleteFiles);
            return;
        }
        
        if (entry.getExternalFilePath() != null) {
            // check if there is an external property.
            String externalProperty = dir.getProperties(dir.getThisDirName()).getStringPropertyValue(SVNProperty.EXTERNALS);
            String name = entry.getName();
            if (externalProperty != null) {
                SVNExternal[] externals = SVNExternal.parseExternals("", externalProperty);
                for (int i = 0; i < externals.length; i++) {
                    if (name.equals(externals[i].getPath())) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CANNOT_DELETE_FILE_EXTERNAL, 
                                "Cannot remove the file external at ''{0}''; please propedit or propdel the svn:externals description that created it", 
                                path);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                }
            }
            dir.removeFromRevisionControl(name, deleteFiles, false);
            SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.UNKNOWN, null, 0, SVNEventAction.DELETE, null, null, null);
            wcAccess.handleEvent(event);
            return;
        }
        
        String schedule = entry.getSchedule();
        SVNNodeKind kind = entry.getKind();
        boolean copied = entry.isCopied();
        boolean deleted = false;
        String name = path.getName();

        if (kind == SVNNodeKind.DIR) {
            SVNAdminArea parent = wcAccess.retrieve(path.getParentFile());
            SVNEntry entryInParent = parent.getEntry(name, true);
            deleted = entryInParent != null ? entryInParent.isDeleted() : false;
            if (!deleted && SVNProperty.SCHEDULE_ADD.equals(schedule)) {
                if (dir != root) {
                    dir.removeFromRevisionControl("", false, false);
                } else {
                    parent.deleteEntry(name);
                    parent.saveEntries(false);
                }
            } else {
                if (dir != root) {
                    if (cancellable) {
                        markTreeCancellable(dir, SVNProperty.SCHEDULE_DELETE, false, !deleteFiles, SCHEDULE);
                    } else {
                        markTree(dir, SVNProperty.SCHEDULE_DELETE, false, !deleteFiles, SCHEDULE | KEEP_LOCAL);
                    }
                }
            }
        }
        if (!(kind == SVNNodeKind.DIR && SVNProperty.SCHEDULE_ADD.equals(schedule) && !deleted)) {
            SVNLog log = root.getLog();

            SVNProperties command = new SVNProperties();
            command.put(SVNLog.NAME_ATTR, name);
            command.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE), SVNProperty.SCHEDULE_DELETE);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
            if (SVNProperty.SCHEDULE_REPLACE.equals(schedule) && copied) {
                if (kind != SVNNodeKind.DIR) {
                    command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getTextRevertPath(name, false));
                    command.put(SVNLog.DEST_ATTR, SVNAdminUtil.getTextBasePath(name, false));
                    log.addCommand(SVNLog.MOVE, command, false);
                    command.clear();
                }
                command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getPropRevertPath(name, kind, false));
                command.put(SVNLog.DEST_ATTR, SVNAdminUtil.getPropBasePath(name, kind, false));
                log.addCommand(SVNLog.MOVE, command, false);
                command.clear();
            }
            if (SVNProperty.SCHEDULE_ADD.equals(schedule)) {
                command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getPropPath(name, kind, false));
                log.addCommand(SVNLog.DELETE, command, false);
                command.clear();
                command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getPropBasePath(name, kind, false));
                log.addCommand(SVNLog.DELETE, command, false);
                command.clear();
                command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getTextBasePath(name, false));
                log.addCommand(SVNLog.DELETE, command, false);
                command.clear();
            }
            log.save();
            root.runLogs();
        }
        SVNEvent event = SVNEventFactory.createSVNEvent(root.getFile(name), SVNNodeKind.UNKNOWN, null, 0, SVNEventAction.DELETE, null, null, null);
        wcAccess.handleEvent(event);
        if (SVNProperty.SCHEDULE_ADD.equals(schedule)) {
            SVNWCManager.doDeleteUnversionedFiles(wcAccess, path, deleteFiles);
        } else {
            SVNWCManager.doEraseFromWC(path, root, kind, deleteFiles);
        }
    }

    public static void doDeleteUnversionedFiles(SVNWCAccess wcAccess, File path, boolean deleteFiles) throws SVNException {
        wcAccess.checkCancelled();
        SVNFileType fileType = SVNFileType.getType(path);
        if (fileType == SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_FILENAME, "''{0}'' does not exist", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (fileType != SVNFileType.FILE && fileType != SVNFileType.DIRECTORY && fileType != SVNFileType.SYMLINK) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                    "Unsupported node kind for path ''{0}''", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (deleteFiles) {
            SVNFileUtil.deleteAll(path, true, wcAccess.getEventHandler());
        }
    }

    public static void doEraseFromWC(File path, SVNAdminArea dir, SVNNodeKind kind, boolean deleteFiles) throws SVNException {
        SVNFileType type = SVNFileType.getType(path);
        if (type == SVNFileType.NONE) {
            return;
        }
        dir.getWCAccess().checkCancelled();
        if (kind == SVNNodeKind.FILE) {
            if (deleteFiles) {
                SVNFileUtil.deleteFile(path);
            }
        } else if (kind == SVNNodeKind.DIR) {
            SVNAdminArea childDir = null;
            try {
                childDir = dir.getWCAccess().retrieve(path);
            } catch (SVNException svne) {
                if (!path.exists()) {
                    return;
                }
                throw svne;
            }

            Collection versioned = new SVNHashSet();
            for (Iterator entries = childDir.entries(false); entries.hasNext();) {
                SVNEntry entry = (SVNEntry) entries.next();
                versioned.add(entry.getName());
                if (childDir.getThisDirName().equals(entry.getName())) {
                    continue;
                }
                File childPath = childDir.getFile(entry.getName());
                doEraseFromWC(childPath, childDir, entry.getKind(), deleteFiles);
            }
            File[] children = SVNFileListUtil.listFiles(path);
            for (int i = 0; children != null && i < children.length; i++) {
                if (SVNFileUtil.getAdminDirectoryName().equals(children[i].getName())) {
                    continue;
                }
                if (versioned.contains(children[i].getName())) {
                    continue;
                }
                doDeleteUnversionedFiles(dir.getWCAccess(), children[i], deleteFiles);
            }
        }
    }

    public static void addRepositoryFile(SVNAdminArea dir, String fileName, File text, File textBase, SVNProperties baseProperties, SVNProperties properties, String copyFromURL, long copyFromRev) throws SVNException {
        SVNEntry parentEntry = dir.getVersionedEntry(dir.getThisDirName(), false);
        String newURL = SVNPathUtil.append(parentEntry.getURL(), SVNEncodingUtil.uriEncode(fileName));
        if (copyFromURL != null && parentEntry.getRepositoryRoot() != null && !SVNPathUtil.isAncestor(parentEntry.getRepositoryRoot(), copyFromURL)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Copyfrom-url ''{0}'' has different repository root than ''{1}''", new Object[]{copyFromURL, parentEntry.getRepositoryRoot()});
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        SVNEntry dstEntry = dir.getEntry(fileName, false);
        SVNLog log = dir.getLog();
        SVNProperties command = new SVNProperties();
        if (dstEntry != null && dstEntry.isScheduledForDeletion()) {
            String revertTextPath = SVNAdminUtil.getTextRevertPath(fileName, false);
            String baseTextPath = SVNAdminUtil.getTextBasePath(fileName, false);
            String revertPropsPath = SVNAdminUtil.getPropRevertPath(fileName, SVNNodeKind.FILE, false);
            String basePropsPath = SVNAdminUtil.getPropBasePath(fileName, SVNNodeKind.FILE, false);

            command.put(SVNLog.NAME_ATTR, baseTextPath);
            command.put(SVNLog.DEST_ATTR, revertTextPath);
            log.addCommand(SVNLog.MOVE, command, false);
            command.clear();

            if (dir.getFile(basePropsPath).isFile()) {
                command.put(SVNLog.NAME_ATTR, basePropsPath);
                command.put(SVNLog.DEST_ATTR, revertPropsPath);
                log.addCommand(SVNLog.MOVE, command, false);
                command.clear();
            } else {
                String emptyPropPath = SVNAdminUtil.getPropBasePath(fileName, SVNNodeKind.FILE, false);
                SVNWCProperties.setProperties(new SVNProperties(), null, dir.getFile(emptyPropPath),
                        SVNWCProperties.SVN_HASH_TERMINATOR);
                command.put(SVNLog.NAME_ATTR, emptyPropPath);
                command.put(SVNLog.DEST_ATTR, revertPropsPath);
                log.addCommand(SVNLog.MOVE, command, false);
                command.clear();
            }
        }

        SVNProperties entryAttrs = new SVNProperties();
        entryAttrs.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE), SVNProperty.SCHEDULE_ADD);
        if (copyFromURL != null) {
            entryAttrs.put(SVNProperty.shortPropertyName(SVNProperty.COPIED), SVNProperty.toString(true));
            entryAttrs.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_URL), copyFromURL);
            entryAttrs.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_REVISION), SVNProperty.toString(copyFromRev));
        }
        log.logChangedEntryProperties(fileName, entryAttrs);
        entryAttrs.clear();

        log.logTweakEntry(fileName, newURL, dstEntry != null ? dstEntry.getRevision() : parentEntry.getRevision());

        SVNWCManager.addProperties(dir, fileName, baseProperties, true, log);
        SVNWCManager.addProperties(dir, fileName, properties, false, log);

        File tmpTextBase = dir.getBaseFile(fileName, true);
        if (!tmpTextBase.equals(textBase) && textBase != null) {
            SVNFileUtil.rename(textBase, tmpTextBase);
        }
        if (text != null) {
            File tmpFile = SVNFileUtil.createUniqueFile(dir.getRoot(), fileName, ".tmp", false);
            SVNFileUtil.rename(text, tmpFile);
            if (baseProperties != null && baseProperties.containsName(SVNProperty.SPECIAL)) {
                command.put(SVNLog.NAME_ATTR, tmpFile.getName());
                command.put(SVNLog.DEST_ATTR, fileName);
                command.put(SVNLog.ATTR1, "true");
                log.addCommand(SVNLog.COPY, command, false);
                command.clear();
                command.put(SVNLog.NAME_ATTR, tmpFile.getName());
                log.addCommand(SVNLog.DELETE, command, false);
                command.clear();
            } else {
                command.put(SVNLog.NAME_ATTR, tmpFile.getName());
                command.put(SVNLog.DEST_ATTR, fileName);
                log.addCommand(SVNLog.MOVE, command, false);
                command.clear();
            }
        } else {
            command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getTextBasePath(fileName, true));
            command.put(SVNLog.DEST_ATTR, fileName);
            log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
            command.clear();
            command.put(SVNProperty.shortPropertyName(SVNProperty.TEXT_TIME), SVNLog.WC_TIMESTAMP);
            command.put(SVNProperty.shortPropertyName(SVNProperty.WORKING_SIZE), SVNLog.WC_WORKING_SIZE);
            log.logChangedEntryProperties(fileName, command);
            command.clear();
        }

        command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getTextBasePath(fileName, true));
        command.put(SVNLog.DEST_ATTR, SVNAdminUtil.getTextBasePath(fileName, false));
        log.addCommand(SVNLog.MOVE, command, false);
        command.clear();

        command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getTextBasePath(fileName, false));
        log.addCommand(SVNLog.READONLY, command, false);
        command.clear();

        String checksum = SVNFileUtil.computeChecksum(dir.getBaseFile(fileName, true));
        entryAttrs.put(SVNProperty.shortPropertyName(SVNProperty.CHECKSUM), checksum);
        log.logChangedEntryProperties(fileName, entryAttrs);
        entryAttrs.clear();

        log.save();
        dir.runLogs();
    }

    public static void addProperties(SVNAdminArea dir, String fileName, SVNProperties properties, boolean base, SVNLog log) throws SVNException {
        if (properties == null || properties.isEmpty()) {
            return;
        }
        SVNProperties regularProps = new SVNProperties();
        SVNProperties entryProps = new SVNProperties();
        SVNProperties wcProps = new SVNProperties();

        for (Iterator names = properties.nameSet().iterator(); names.hasNext();) {
            String propName = (String) names.next();
            SVNPropertyValue propValue = properties.getSVNPropertyValue(propName);
            if (SVNProperty.isEntryProperty(propName)) {
                entryProps.put(SVNProperty.shortPropertyName(propName), propValue);
            } else if (SVNProperty.isWorkingCopyProperty(propName)) {
                wcProps.put(propName, propValue);
            } else {
                regularProps.put(propName, propValue);
            }
        }
        SVNVersionedProperties props = base ? dir.getBaseProperties(fileName) : dir.getProperties(fileName);
        props.removeAll();
        for (Iterator propNames = regularProps.nameSet().iterator(); propNames.hasNext();) {
            String propName = (String) propNames.next();
            SVNPropertyValue propValue = regularProps.getSVNPropertyValue(propName);
            props.setPropertyValue(propName, propValue);
        }
        dir.saveVersionedProperties(log, false);
        log.logChangedEntryProperties(fileName, entryProps);
        log.logChangedWCProperties(fileName, wcProps);
    }

    public static boolean isEntrySwitched(File path, SVNEntry entry) throws SVNException {
        path = new Resource(SVNPathUtil.validateFilePath(path.getAbsolutePath())).getAbsoluteFile();
        File parent = path.getParentFile();
        if (parent == null) {
            return false;
        }
        
        SVNWCAccess access = SVNWCAccess.newInstance(null);
        SVNAdminArea parentAdminArea = null;
        SVNEntry parentEntry = null;
        try {
            parentAdminArea = access.open(parent, false, 0);
            parentEntry = parentAdminArea.getVersionedEntry(parentAdminArea.getThisDirName(), false);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                return false;
            } 
            throw svne;
        } finally {
            access.close();
        }
        
        SVNURL parentSVNURL = parentEntry.getSVNURL();
        SVNURL entrySVNURL = entry.getSVNURL(); 
        if (parentSVNURL == null || entrySVNURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                    "Cannot find a URL for ''{0}''", parentSVNURL == null ? parent : path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        SVNURL expectedSVNURL = parentSVNURL.appendPath(path.getName(), false);
        return !entrySVNURL.equals(expectedSVNURL);
    }
    
    public static void crop(SVNAdminAreaInfo info, SVNDepth depth) throws SVNException {
        if (depth == SVNDepth.INFINITY) {
            return;
        }
        if (!(depth.compareTo(SVNDepth.EXCLUDE) >= 0 && depth.compareTo(SVNDepth.INFINITY) < 0)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Can only crop a working copy with a restrictive depth");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        // get target entry
        SVNWCAccess wcAccess = info.getWCAccess();
        File fullPath = info.getAnchor().getRoot();
        if (!"".equals(info.getTargetName())) {
            fullPath = new Resource(fullPath, info.getTargetName());
        }
        SVNEntry targetEntry = wcAccess.getEntry(fullPath, false);
        if (targetEntry == null || !targetEntry.isDirectory()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Can only crop directories");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (targetEntry.isScheduledForDeletion()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot crop ''{0}'': it is going to be removed from repository." +
            		" Try commit instead", fullPath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (depth == SVNDepth.EXCLUDE) {
            if (fullPath.getParentFile() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot exclude root directory");
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            SVNAdminArea parentDir = wcAccess.getAdminArea(fullPath.getParentFile());
            SVNEntry parentEntry = null;
            if (parentDir == null) {
                try { 
                    parentDir = wcAccess.probeOpen(fullPath.getParentFile(), false, 0);
                } catch (SVNException e) {
                }
            }
            if (parentDir != null) {
                parentEntry = wcAccess.getEntry(fullPath.getParentFile(), false);
            }
            if (parentEntry != null) {
                SVNURL expectedURL = parentEntry.getSVNURL().appendPath(fullPath.getName(), false);
                if (!expectedURL.equals(targetEntry.getSVNURL())) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot crop ''{0}'': it is a switched path", fullPath);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
            boolean inRepos = !((targetEntry.isScheduledForAddition() || targetEntry.isScheduledForReplacement()) && !targetEntry.isCopied());
            if (parentEntry != null && inRepos && parentEntry.getDepth().compareTo(SVNDepth.FILES) > 0) {
                SVNEntry entryInParent = parentDir.getEntry(fullPath.getName(), false);
                entryInParent.setDepth(SVNDepth.EXCLUDE);
                parentDir.saveEntries(true);
            }
            // remove dir.
            SVNAdminArea dir = wcAccess.retrieve(fullPath);
            try {
                dir.removeFromRevisionControl(dir.getThisDirName(), true, false);
            } catch (SVNException svne) {                
                handleLeftLocalModificationsError(svne);
            }
            SVNEvent event = SVNEventFactory.createSVNEvent(fullPath, SVNNodeKind.DIR, null, 
                    SVNRepository.INVALID_REVISION, SVNEventAction.UPDATE_DELETE, null, null, null);
            wcAccess.handleEvent(event);
            return;
        }
        // crop children.
        cropChildren(wcAccess, fullPath, depth);
    }
    
    public static String getActualTarget(File file) throws SVNException {
        SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
        try {
            wcAccess.probeOpen(file, false, 0);
            boolean isWCRoot = wcAccess.isWCRoot(file);
            SVNEntry entry = wcAccess.getEntry(file, false);
            SVNNodeKind kind = entry != null? entry.getKind() : SVNNodeKind.FILE;
            if (kind == SVNNodeKind.FILE || !isWCRoot) {
                return file.getName();
            }
        } finally {
            wcAccess.close();
        }
        return "";
    }
    
    public static void createRevertProperties(SVNWCAccess access, File path, /*SVNAdminArea area, SVNLog log, String entryName,*/ boolean removeBase) throws SVNException {
        SVNEntry entry = access.getVersionedEntry(path, false);
        String revertPropPath = SVNAdminUtil.getPropRevertPath(entry.getName(), entry.getKind(), false);
        String basePropPath = SVNAdminUtil.getPropBasePath(entry.getName(), entry.getKind(), false);
        
        SVNAdminArea area = entry.getAdminArea();
        SVNLog log = area.getLog();
        
        File basePropFile = area.getFile(basePropPath);
        if (basePropFile.isFile()) {
            SVNProperties command = new SVNProperties();
            command.put(SVNLog.NAME_ATTR, basePropPath);
            command.put(SVNLog.DEST_ATTR, revertPropPath);
            if (removeBase) {
                log.addCommand(SVNLog.MOVE, command, false);
            } else {
                log.addCommand(SVNLog.COPY, command, false);
            }
        } else {
            // create empty props file and move it to revert props.
            String tmpPath = SVNAdminUtil.getPropRevertPath(entry.getName(), entry.getKind(), true);
            File tmpFile = area.getFile(tmpPath);
            SVNWCProperties.setProperties(new SVNProperties(), tmpFile, null, SVNWCProperties.SVN_HASH_TERMINATOR);
            SVNProperties command = new SVNProperties();
            command.put(SVNLog.NAME_ATTR, tmpPath);
            command.put(SVNLog.DEST_ATTR, revertPropPath);
            log.addCommand(SVNLog.MOVE, command, false);
        }
        log.save();
        area.runLogs();
    }

    private static void cropChildren(SVNWCAccess wcAccess, File path, SVNDepth depth) throws SVNException {
        SVNAdminArea dir = wcAccess.retrieve(path);
        SVNEntry dotEntry = dir.getEntry(dir.getThisDirName(), false);
        if (dotEntry.getDepth().compareTo(depth) > 0) {
            dotEntry.setDepth(depth);
            dir.saveEntries(false);
        }
        for(Iterator ents = dir.entries(true); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (entry.isThisDir()) {
                continue;
            }
            File entryPath = new Resource(path, entry.getName());
            if (entry.isFile()) {
                if (depth == SVNDepth.EMPTY) {
                    try {
                        dir.removeFromRevisionControl(entry.getName(), true, false);
                    } catch (SVNException e) {
                        handleLeftLocalModificationsError(e);
                    }
                } else {
                    continue;
                }
            } else if (entry.isDirectory()) {
                if (entry.getDepth() == SVNDepth.EXCLUDE) {
                    if (depth.compareTo(SVNDepth.IMMEDIATES) < 0) {
                        dir.deleteEntry(entry.getName());
                        dir.saveEntries(false);
                    }
                    continue;
                } else if (depth.compareTo(SVNDepth.IMMEDIATES) < 0) {
                    SVNAdminArea childDir = wcAccess.retrieve(entryPath);
                    try {
                        childDir.removeFromRevisionControl(childDir.getThisDirName(), true, false);
                    } catch (SVNException e) {
                        handleLeftLocalModificationsError(e);
                    }
                } else {
                    cropChildren(wcAccess, entryPath, SVNDepth.EMPTY);
                    continue;
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Unknown entry kind for ''{0}''", entryPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            SVNEvent event = SVNEventFactory.createSVNEvent(entryPath, entry.getKind(), null, 
                    SVNRepository.INVALID_REVISION, SVNEventAction.UPDATE_DELETE, null, null, null);
            wcAccess.handleEvent(event);
        }
    }

    private static void handleLeftLocalModificationsError(SVNException originalError) throws SVNException {
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

}
