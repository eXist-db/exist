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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNVersionedProperties;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.exist.versioning.svn.wc.SVNCommitItem;
import org.exist.versioning.svn.wc.SVNEvent;
import org.exist.versioning.svn.wc.SVNStatus;
import org.exist.versioning.svn.wc.SVNStatusClient;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.exist.versioning.svn.wc.SVNTreeConflictDescription;
import org.exist.versioning.svn.wc.SVNWCUtil;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.wc.ISVNCommitParameters;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNCommitUtil {
    
    public static final Comparator FILE_COMPARATOR = new Comparator() {
        public int compare(Object o1, Object o2) {
            if (o1 == null) {
                return -1;
            } else if (o2 == null) {
                return 1;
            } else if (o1 == o2) {
                return 0;
            }
            
            File f1 = (File) o1;
            File f2 = (File) o2;
            return f1.getPath().compareTo(f2.getPath());
        }
    };

    public static void driveCommitEditor(ISVNCommitPathHandler handler, Collection paths, ISVNEditor editor, long revision) throws SVNException {
        if (paths == null || paths.isEmpty() || handler == null || editor == null) {
            return;
        }
        
        String[] pathsArray = (String[]) paths.toArray(new String[paths.size()]);
        Arrays.sort(pathsArray, SVNPathUtil.PATH_COMPARATOR);
        int index = 0;
        int depth = 0;
        String lastPath = null;
        
        if ("".equals(pathsArray[index])) {
            handler.handleCommitPath("", editor);
            lastPath = pathsArray[index];
            index++;
        } else {
            editor.openRoot(revision);
        }
        
        depth++;
        
        for (; index < pathsArray.length; index++) {
            String commitPath = pathsArray[index];
//            if (!SVNPathUtil.isCanonical(commitPath)) {
//                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
//                        "Assertion failed in  SVNCommitUtil.driveCommitEditor(): path ''{0}'' is not canonical", 
//                        commitPath);
//                SVNErrorManager.error(err, SVNLogType.DEFAULT);
//            }
            
            String commonAncestor = lastPath == null || "".equals(lastPath) ? "" : SVNPathUtil.getCommonPathAncestor(commitPath, lastPath);
            if (lastPath != null) {
                
                while (!lastPath.equals(commonAncestor)) {
                    editor.closeDir();
                    depth--;
                    if (lastPath.lastIndexOf('/') >= 0) {
                        lastPath = lastPath.substring(0, lastPath.lastIndexOf('/'));
                    } else {
                        lastPath = "";
                    }
                }
            }
            String relativeCommitPath = commitPath.substring(commonAncestor.length());
            if (relativeCommitPath.startsWith("/")) {
                relativeCommitPath = relativeCommitPath.substring(1);
            }

            for (StringTokenizer tokens = new StringTokenizer(
                    relativeCommitPath, "/"); tokens.hasMoreTokens();) {
                String token = tokens.nextToken();
                commonAncestor = "".equals(commonAncestor) ? token : commonAncestor + "/" + token;
                if (!commonAncestor.equals(commitPath)) {
                    editor.openDir(commonAncestor, revision);
                    depth++;
                } else {
                    break;
                }
            }
            boolean closeDir = handler.handleCommitPath(commitPath, editor);
            if (closeDir) {
                lastPath = commitPath;
                depth++;
            } else {
                if (index + 1 < pathsArray.length) {
                    lastPath = SVNPathUtil.removeTail(commitPath);
                } else {
                    lastPath = commitPath;
                }
            }
        }
        
        while (depth > 0) {
            editor.closeDir();
            depth--;
        }
        
    }

    public static SVNWCAccess createCommitWCAccess(File[] paths, SVNDepth depth, boolean force, 
                                                   Collection relativePaths, final SVNStatusClient statusClient) throws SVNException {
        String[] validatedPaths = new String[paths.length];
        for (int i = 0; i < paths.length; i++) {
            statusClient.checkCancelled();
            File file = paths[i];
            validatedPaths[i] = file.getAbsolutePath().replace(File.separatorChar, '/');
        }
        String rootPath = SVNPathUtil.condencePaths(validatedPaths, relativePaths, depth == SVNDepth.INFINITY);
        
        if (rootPath == null) {
            return null;
        }

        boolean lockAll = false;
        if (depth == SVNDepth.FILES || depth == SVNDepth.IMMEDIATES) {
            for (Iterator relPathsIter = relativePaths.iterator(); relPathsIter.hasNext();) {
                String relPath = (String) relPathsIter.next();
                if ("".equals(relPath)) {
                    lockAll = true;
                    break;
                }
            }
        }

        File baseDir = new Resource(rootPath).getAbsoluteFile();
        rootPath = baseDir.getAbsolutePath().replace(File.separatorChar, '/');
        Collection dirsToLock = new SVNHashSet(); // relative paths to lock.
        Collection dirsToLockRecursively = new SVNHashSet(); 
        if (relativePaths.isEmpty()) {
            statusClient.checkCancelled();
            String target = SVNWCManager.getActualTarget(baseDir);
            if (!"".equals(target)) {
                // we will have to lock target as well, not only base dir.
                SVNFileType targetType = SVNFileType.getType(new Resource(rootPath));
                relativePaths.add(target);
                if (targetType == SVNFileType.DIRECTORY) {
                    // lock recursively if forced and copied...
                    if (depth == SVNDepth.INFINITY || depth == SVNDepth.IMMEDIATES || 
                        (force && isRecursiveCommitForced(baseDir))) {
                        // dir is copied, include children
                        dirsToLockRecursively.add(target);
                    } else {
                        dirsToLock.add(target);
                    }  
                }
                baseDir = baseDir.getParentFile();
            } else {
                lockAll = true;
            }
        } else if (!lockAll) {
            baseDir = adjustRelativePaths(baseDir, relativePaths);
            // there are multiple paths.
            for (Iterator targets = relativePaths.iterator(); targets.hasNext();) {
                statusClient.checkCancelled();
                String targetPath = (String) targets.next();
                File targetFile = new Resource(baseDir, targetPath);
                SVNFileType targetKind = SVNFileType.getType(targetFile);
                if (targetKind == SVNFileType.DIRECTORY) {
                    if (depth == SVNDepth.INFINITY || depth == SVNDepth.IMMEDIATES || 
                        (force && isRecursiveCommitForced(targetFile))) {
                        dirsToLockRecursively.add(targetPath);
                    } else if (!targetFile.equals(baseDir)){
                        dirsToLock.add(targetPath);
                    }
                }
                if (!targetFile.equals(baseDir)) {
                    targetFile = targetFile.getParentFile();
                    targetPath = SVNPathUtil.removeTail(targetPath);
                    while (targetFile != null && !targetFile.equals(baseDir) && !dirsToLock.contains(targetPath)) {
                        dirsToLock.add(targetPath);
                        targetPath = SVNPathUtil.removeTail(targetPath);
                        targetFile = targetFile.getParentFile();
                    }
                }
            }
        }
        
        SVNWCAccess baseAccess = SVNWCAccess.newInstance(new ISVNEventHandler() {
            public void handleEvent(SVNEvent event, double progress) throws SVNException {
            }
            public void checkCancelled() throws SVNCancelException {
                statusClient.checkCancelled();
            }
        });
        
        baseAccess.setOptions(statusClient.getOptions());
        try {
            baseAccess.open(baseDir, true, lockAll ? SVNWCAccess.INFINITE_DEPTH : 0);
            statusClient.checkCancelled();
            dirsToLock = new ArrayList(dirsToLock);
            dirsToLockRecursively = new ArrayList(dirsToLockRecursively);
            Collections.sort((List) dirsToLock, SVNPathUtil.PATH_COMPARATOR);
            Collections.sort((List) dirsToLockRecursively, SVNPathUtil.PATH_COMPARATOR);
            if (!lockAll) {
                List uniqueDirsToLockRecursively = new ArrayList();
                uniqueDirsToLockRecursively.addAll(dirsToLockRecursively);
                Map processedPaths = new SVNHashMap();
                for(Iterator ps = uniqueDirsToLockRecursively.iterator(); ps.hasNext();) {
                    String pathToLock = (String) ps.next();
                    if (processedPaths.containsKey(pathToLock)) {
                        //remove any duplicates
                        ps.remove();
                        continue;
                    }
                    processedPaths.put(pathToLock, pathToLock);
                    
                    for(Iterator existing = dirsToLockRecursively.iterator(); existing.hasNext();) {
                        String existingPath = (String) existing.next();
                        if (pathToLock.startsWith(existingPath + "/")) {
                            // child of other path
                            ps.remove();
                            break;
                        }
                    }
                    
                }
                
                Collections.sort(uniqueDirsToLockRecursively, SVNPathUtil.PATH_COMPARATOR);
                dirsToLockRecursively = uniqueDirsToLockRecursively;
                removeRedundantPaths(dirsToLockRecursively, dirsToLock);
                for (Iterator nonRecusivePaths = dirsToLock.iterator(); nonRecusivePaths.hasNext();) {
                    statusClient.checkCancelled();
                    String path = (String) nonRecusivePaths.next();
                    File pathFile = new Resource(baseDir, path);
                    baseAccess.open(pathFile, true, 0);
                }
                for (Iterator recusivePaths = dirsToLockRecursively.iterator(); recusivePaths.hasNext();) {
                    statusClient.checkCancelled();
                    String path = (String) recusivePaths.next();
                    File pathFile = new Resource(baseDir, path);
                    baseAccess.open(pathFile, true, SVNWCAccess.INFINITE_DEPTH);
                }
            }
            
            for(int i = 0; i < paths.length; i++) {
                statusClient.checkCancelled();
                File path = paths[i].getAbsoluteFile();
                path = path.getAbsoluteFile();
                try {
                    baseAccess.probeRetrieve(path);
                } catch (SVNException e) {
                    SVNErrorMessage err = e.getErrorMessage().wrap("Are all the targets part of the same working copy?");
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                if (depth != SVNDepth.INFINITY && !force) {
                    if (SVNFileType.getType(path) == SVNFileType.DIRECTORY) {
                        // TODO replace with direct SVNStatusEditor call.
                        SVNStatus status = statusClient.doStatus(path, false);
                        if (status != null && (status.getContentsStatus() == SVNStatusType.STATUS_DELETED || status.getContentsStatus() == SVNStatusType.STATUS_REPLACED)) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot non-recursively commit a directory deletion");
                            SVNErrorManager.error(err, SVNLogType.WC);
                        }
                    }
                }
            }
            
            // if commit is non-recursive and forced, remove those child dirs 
            // that were not explicitly added but are explicitly copied. ufff.
            if (depth != SVNDepth.INFINITY && force) {
                SVNAdminArea[] lockedDirs = baseAccess.getAdminAreas();
                for (int i = 0; i < lockedDirs.length; i++) {
                    statusClient.checkCancelled();
                    SVNAdminArea dir = lockedDirs[i];
                    if (dir == null) {
                        // could be null for missing, but known dir.
                        continue;
                    }
                    SVNEntry rootEntry = baseAccess.getEntry(dir.getRoot(), true);
                    if (rootEntry.getCopyFromURL() != null) {
                        File dirRoot = dir.getRoot();
                        boolean keep = false;
                        for (int j = 0; j < paths.length; j++) {
                            if (dirRoot.equals(paths[j])) {
                                keep = true;
                                break;
                            }
                        }
                        if (!keep) {
                            baseAccess.closeAdminArea(dir.getRoot());
                        }
                    }
                }
            }
        } catch (SVNException e) {
            baseAccess.close();
            throw e;
        }
        baseAccess.setAnchor(baseDir);
        return baseAccess;
    }

    public static SVNWCAccess[] createCommitWCAccess2(File[] paths, SVNDepth depth, boolean force, 
                                                      Map relativePathsMap, SVNStatusClient statusClient) throws SVNException {
        Map rootsMap = new SVNHashMap(); // wc root file -> paths to be committed (paths).
        Map localRootsCache = new SVNHashMap();
        for (int i = 0; i < paths.length; i++) {
            statusClient.checkCancelled();
            File path = paths[i];
            File rootPath = path;
            if (rootPath.isFile()) {
                rootPath = rootPath.getParentFile();
            }
            File wcRoot = localRootsCache.containsKey(rootPath) ? (File) localRootsCache.get(rootPath) : SVNWCUtil.getWorkingCopyRoot(rootPath, true);
            localRootsCache.put(path, wcRoot);
            if (!rootsMap.containsKey(wcRoot)) {
                rootsMap.put(wcRoot, new ArrayList());
            }
            Collection wcPaths = (Collection) rootsMap.get(wcRoot);
            wcPaths.add(path);
        }
        Collection result = new ArrayList();
        try {
            for (Iterator roots = rootsMap.keySet().iterator(); roots.hasNext();) {
                statusClient.checkCancelled();
                File root = (File) roots.next();
                Collection filesList = (Collection) rootsMap.get(root);
                File[] filesArray = (File[]) filesList.toArray(new Resource[filesList.size()]);
                Collection relativePaths = new ArrayList();
                SVNWCAccess wcAccess = createCommitWCAccess(filesArray, depth, force, relativePaths, statusClient);
                relativePathsMap.put(wcAccess, relativePaths);
                result.add(wcAccess);
            }
        } catch (SVNException e) {
            for (Iterator wcAccesses = result.iterator(); wcAccesses.hasNext();) {
                SVNWCAccess wcAccess = (SVNWCAccess) wcAccesses.next();
                wcAccess.close();
            }
            throw e;
        }
        return (SVNWCAccess[]) result.toArray(new SVNWCAccess[result.size()]);
    }

    public static SVNCommitItem[] harvestCommitables(SVNWCAccess baseAccess, Collection paths, Map lockTokens, 
            boolean justLocked, SVNDepth depth, boolean force, Collection changelists, 
            ISVNCommitParameters params) throws SVNException {
        Map commitables = new TreeMap(FILE_COMPARATOR);
        Collection danglers = new SVNHashSet();
        Iterator targets = paths.iterator();
        
        boolean isRecursionForced = false;

        do {
            String target = targets.hasNext() ? (String) targets.next() : "";
            baseAccess.checkCancelled();
            // get entry for target
            File targetFile = new Resource(baseAccess.getAnchor(), target);
            String parentPath = SVNPathUtil.removeTail(target);
            SVNAdminArea dir = baseAccess.probeRetrieve(targetFile);
            SVNEntry entry = null;
            try {
                entry = baseAccess.getVersionedEntry(targetFile, false);
            } catch (SVNException e) {
                if (e.getErrorMessage() != null &&
                        e.getErrorMessage().getErrorCode() == SVNErrorCode.ENTRY_NOT_FOUND) {
                    SVNTreeConflictDescription tc = baseAccess.getTreeConflict(targetFile);
                    if (tc != null) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, "Aborting commit: ''{0}'' remains in conflict", targetFile);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }                    
                }
                throw e;                
            }
            String url = null;
            if (entry.getURL() == null) {
                // it could be missing directory.
                if (!entry.isThisDir() && entry.getName() != null && 
                        entry.isDirectory() && !(entry.isScheduledForAddition() || entry.isScheduledForReplacement()) && SVNFileType.getType(targetFile) == SVNFileType.NONE) {
                    File parentDir = targetFile.getParentFile();
                    if (parentDir != null) {
                        SVNEntry parentEntry = baseAccess.getEntry(parentDir, false);
                        if (parentEntry != null) {
                            url = SVNPathUtil.append(parentEntry.getURL(), SVNEncodingUtil.uriEncode(entry.getName()));
                        }
                    }
                } 
                if (url == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Entry for ''{0}'' has no URL", targetFile);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            } else {
                url = entry.getURL();
            }
            SVNEntry parentEntry = null;
            if (entry.isScheduledForAddition() || entry.isScheduledForReplacement()) {
                // get parent (for file or dir-> get ""), otherwise open parent
                // dir and get "".
                try {
                    baseAccess.retrieve(targetFile.getParentFile());
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                        baseAccess.open(targetFile.getParentFile(), true, 0);
                    } else {
                        throw e;
                    }
                }
                parentEntry = baseAccess.getEntry(targetFile.getParentFile(), false);
                if (parentEntry == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, 
                            "''{0}'' is scheduled for addition within unversioned parent", targetFile);
                    SVNErrorManager.error(err, SVNLogType.WC);
                } else if (parentEntry.isScheduledForAddition() || parentEntry.isScheduledForReplacement()) {
                    danglers.add(targetFile.getParentFile());
                }
            }
            SVNDepth forcedDepth = depth;
            if (entry.isCopied() && entry.getSchedule() == null) {
                // if commit is forced => we could collect this entry, assuming
                // that its parent is already included into commit
                // it will be later removed from commit anyway.
                if (!force) {
                    SVNErrorMessage err =  SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, 
                            "Entry for ''{0}''"
                                    + " is marked as 'copied' but is not itself scheduled\n"
                                    + "for addition.  Perhaps you're committing a target that is\n"
                                    + "inside an unversioned (or not-yet-versioned) directory?", targetFile);
                    SVNErrorManager.error(err, SVNLogType.WC);
                } else {
                    // just do not process this item as in case of recursive
                    // commit.
                    continue;
                }
            } else if (entry.isCopied() && entry.isScheduledForAddition()) {
                if (force) {
                    isRecursionForced = depth != SVNDepth.INFINITY;
                    forcedDepth = SVNDepth.INFINITY;
                }
            } else if (entry.isScheduledForDeletion() && force && depth != SVNDepth.INFINITY) {
                // if parent is also deleted -> skip this entry
                File parentFile = targetFile.getParentFile();
                parentEntry = baseAccess.getEntry(parentFile, false);
                if (parentEntry == null) {
                    try {
                        baseAccess.retrieve(parentFile);
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                            baseAccess.open(parentFile, true, 0);
                        } else {
                            throw e;
                        }
                    }
                    parentEntry = baseAccess.getEntry(parentFile, false);
                }
                if (parentEntry != null && parentEntry.isScheduledForDeletion() && paths.contains(parentPath)) {
                    continue;
                }
                // this recursion is not considered as "forced", all children should be 
                // deleted anyway.
                forcedDepth = SVNDepth.INFINITY;
            }
            
            // check ancestors for tc.
            File ancestorPath = dir.getRoot();
            
            SVNWCAccess localAccess = SVNWCAccess.newInstance(null);
            localAccess.open(ancestorPath, false, 0);
            try {
                while (true) {
                    boolean isRoot = localAccess.isWCRoot(ancestorPath);
                    if (isRoot) {
                        break;
                    }
                    File pPath = ancestorPath.getParentFile();
                    localAccess.open(pPath, false, 0);
                    if (localAccess.hasTreeConflict(ancestorPath)) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, 
                                "Aborting commit: ''{0}'' remains in tree-conflict", ancestorPath);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                    ancestorPath = pPath;
                }
            } finally {
                localAccess.close();
            }
//            String relativePath = entry.getKind() == SVNNodeKind.DIR ? target : SVNPathUtil.removeTail(target);
            harvestCommitables(commitables, dir, targetFile, parentEntry, entry, url, null, false, false, 
                    justLocked, lockTokens, forcedDepth, isRecursionForced, changelists, params, null);
        } while (targets.hasNext());

        for (Iterator ds = danglers.iterator(); ds.hasNext();) {
            baseAccess.checkCancelled();
            File file = (File) ds.next();
            if (!commitables.containsKey(file)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, 
                        "''{0}'' is not under version control\n"
                        + "and is not part of the commit, \n"
                        + "yet its child is part of the commit", file);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        
        // filter out file externals that were not explicitly specified.
        filterOutFileExternals(paths, commitables, baseAccess);
        
        if (isRecursionForced) {
            // if commit is non-recursive and forced and there are elements included into commit 
            // that not only 'copied' but also has local mods (modified or deleted), remove those items?
            // or not?
            for (Iterator items = commitables.values().iterator(); items.hasNext();) {
                baseAccess.checkCancelled();
                SVNCommitItem item = (SVNCommitItem) items.next();
                if (item.isDeleted()) {
                    // to detect deleted copied items.
                    File file = item.getFile();
                    if (item.getKind() == SVNNodeKind.DIR) {
                        if (!file.exists()) {
                            continue;
                        } 
                    } else {
                        String name = SVNPathUtil.tail(item.getPath());
                        SVNAdminArea dir = baseAccess.retrieve(item.getFile().getParentFile());
                        if (!dir.getBaseFile(name, false).exists()) {
                            continue;
                        }
                    }
                }
                if (item.isContentsModified() || item.isDeleted() || item.isPropertiesModified()) {
                    // if item was not explicitly included into commit, then just make it 'added'
                    // but do not remove that are marked as 'deleted'
                    String itemPath = item.getPath();
                    if (!paths.contains(itemPath)) {
                        items.remove(); 
                    }
                }
            }
        }
        return (SVNCommitItem[]) commitables.values().toArray(new SVNCommitItem[commitables.values().size()]);
    }
    
    public static void filterOutFileExternals(Collection explicitPaths, Map commitables, SVNWCAccess baseAccess) throws SVNException {
        for (Iterator items = commitables.values().iterator(); items.hasNext();) {
            baseAccess.checkCancelled();
            SVNCommitItem item = (SVNCommitItem) items.next();
            SVNEntry entry = baseAccess.getEntry(item.getFile(), false);
            if (entry != null && entry.isFile() && entry.getExternalFilePath() != null) {
                if (!explicitPaths.contains(item.getPath())) {
                    items.remove();
                }
            }
        }

    }

    public static SVNURL translateCommitables(SVNCommitItem[] items, Map decodedPaths) throws SVNException {
        Map itemsMap = new SVNHashMap();
        for (int i = 0; i < items.length; i++) {
            SVNCommitItem item = items[i];
            if (itemsMap.containsKey(item.getURL())) {
                SVNCommitItem oldItem = (SVNCommitItem) itemsMap.get(item.getURL());
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_DUPLICATE_COMMIT_URL, 
                        "Cannot commit both ''{0}'' and ''{1}'' as they refer to the same URL",
                        new Object[] {item.getFile(), oldItem.getFile()});
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            itemsMap.put(item.getURL(), item);
        }

        Iterator urls = itemsMap.keySet().iterator();
        SVNURL baseURL = (SVNURL) urls.next();
        while (urls.hasNext()) {
            SVNURL url = (SVNURL) urls.next();
            baseURL = SVNURLUtil.getCommonURLAncestor(baseURL, url);
        }
        if (itemsMap.containsKey(baseURL)) {
            SVNCommitItem root = (SVNCommitItem) itemsMap.get(baseURL);
            if (root.getKind() != SVNNodeKind.DIR) {
                baseURL = baseURL.removePathTail();
            } else if (root.getKind() == SVNNodeKind.DIR
                    && (root.isAdded() || root.isDeleted() || root.isCopied() || root.isLocked())) {
                baseURL = baseURL.removePathTail();
            }
        }
        if (baseURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, 
                    "Cannot compute base URL for commit operation");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        for (Iterator iterator = itemsMap.keySet().iterator(); iterator.hasNext();) {
            SVNURL url = (SVNURL) iterator.next();
            SVNCommitItem item = (SVNCommitItem) itemsMap.get(url);
            String realPath = url.equals(baseURL) ? "" : SVNPathUtil.getRelativePath(baseURL.getPath(), url.getPath());
            decodedPaths.put(realPath, item);
        }
        return baseURL;
    }

    public static Map translateLockTokens(Map lockTokens, String baseURL) {
        Map translatedLocks = new TreeMap();
        for (Iterator urls = lockTokens.keySet().iterator(); urls.hasNext();) {
            String url = (String) urls.next();
            String token = (String) lockTokens.get(url);
            url = url.equals(baseURL) ? "" : url.substring(baseURL.length() + 1);
            translatedLocks.put(SVNEncodingUtil.uriDecode(url), token);
        }
        lockTokens.clear();
        lockTokens.putAll(translatedLocks);
        return lockTokens;
    }

    public static void harvestCommitables(Map commitables, SVNAdminArea dir, File path, SVNEntry parentEntry, 
            SVNEntry entry, String url, String copyFromURL, boolean copyMode, boolean addsOnly, 
            boolean justLocked, Map lockTokens, SVNDepth depth, boolean forcedRecursion, 
            Collection changelists, ISVNCommitParameters params, Map pathsToExternalsProperties) throws SVNException {
        if (commitables.containsKey(path)) {
            return;
        }
        if (dir != null && dir.getWCAccess() != null) {
            dir.getWCAccess().checkCancelled();
        }
        long cfRevision = entry.getCopyFromRevision();
        String cfURL = null;
        if (entry.getKind() != SVNNodeKind.DIR
                && entry.getKind() != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Unknown entry kind for ''{0}''", path);                    
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNFileType fileType = SVNFileType.getType(path);
        if (fileType == SVNFileType.UNKNOWN) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Unknown entry kind for ''{0}''", path);                    
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        String specialPropertyValue = dir.getProperties(entry.getName()).getStringPropertyValue(SVNProperty.SPECIAL);
        boolean specialFile = fileType == SVNFileType.SYMLINK;
        if (SVNFileType.isSymlinkSupportEnabled()) {
            if (((specialPropertyValue == null && specialFile) || (SVNFileUtil.symlinksSupported() && specialPropertyValue != null && !specialFile))
                    && fileType != SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "Entry ''{0}'' has unexpectedly changed special status", path);                    
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        
        boolean propConflicts;
        boolean textConflicts = false;
        boolean treeConflicts = dir.hasTreeConflict(entry.getName());
        
        SVNAdminArea entries = null;
        if (entry.getKind() == SVNNodeKind.DIR) {
            SVNAdminArea childDir = null;
            try {
                childDir = dir.getWCAccess().retrieve(dir.getFile(entry.getName()));
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                    childDir = null;
                } else {
                    throw e;
                }
            }
            if (childDir != null && childDir.entries(true) != null) {
                entries = childDir;
                if (entries.getEntry("", false) != null) {
                    entry = entries.getEntry("", false);
                    dir = childDir;
                }
            } 
            propConflicts = dir.hasPropConflict(entry.getName());

            Map tcs = entry.getTreeConflicts();
            for (Iterator keys = tcs.keySet().iterator(); keys.hasNext();) {
                File entryPath = (File) keys.next();
                SVNTreeConflictDescription tc = (SVNTreeConflictDescription) tcs.get(entryPath);
                if (tc.getNodeKind() == SVNNodeKind.DIR && depth == SVNDepth.FILES) {
                    continue;
                }
                SVNEntry conflictingEntry = null;
                if (tc.getNodeKind() == SVNNodeKind.DIR) {
                    // get dir admin area and root entry
                    SVNAdminArea childConflictingDir = dir.getWCAccess().getAdminArea(entryPath);
                    if (childConflictingDir != null) {
                        conflictingEntry = childConflictingDir.getEntry("", true);
                    }
                    conflictingEntry = childDir.getEntry(entryPath.getName(), true);
                } else {
                    conflictingEntry = dir.getEntry(entryPath.getName(), true);
                }
                if (changelists == null || changelists.isEmpty() || 
                        (conflictingEntry != null && SVNWCAccess.matchesChangeList(changelists, conflictingEntry))) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, 
                            "Aborting commit: ''{0}'' remains in conflict", path);                    
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        } else {
            propConflicts = dir.hasPropConflict(entry.getName());
            textConflicts = dir.hasTextConflict(entry.getName());
        }
        
        if (propConflicts || textConflicts || treeConflicts) {
            if (SVNWCAccess.matchesChangeList(changelists, entry)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, 
                        "Aborting commit: ''{0}'' remains in conflict", path);                    
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        if (entry.getURL() != null && !copyMode) {
            url = entry.getURL();
        }
        boolean commitDeletion = !addsOnly
                && ((entry.isDeleted() && entry.getSchedule() == null) || entry.isScheduledForDeletion() || entry.isScheduledForReplacement());
        if (!addsOnly && !commitDeletion && fileType == SVNFileType.NONE && params != null) {
            ISVNCommitParameters.Action action = 
                entry.getKind() == SVNNodeKind.DIR ? params.onMissingDirectory(path) : params.onMissingFile(path);
            if (action == ISVNCommitParameters.ERROR) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Working copy file ''{0}'' is missing", path);
                SVNErrorManager.error(err, SVNLogType.WC);
            } else if (action == ISVNCommitParameters.DELETE) {
                commitDeletion = true;
                entry.scheduleForDeletion();
                dir.saveEntries(false);
            }
        }
        boolean commitAddition = false;
        boolean commitCopy = false;
        if (entry.isScheduledForAddition() || entry.isScheduledForReplacement()) {
            commitAddition = true;
            if (entry.getCopyFromURL() != null) {
                cfURL = entry.getCopyFromURL();
                addsOnly = false;
                commitCopy = true;
            } else {
                addsOnly = true;
            }
        }
        if ((entry.isCopied() || copyMode) && !entry.isDeleted() && entry.getSchedule() == null) {
            long parentRevision = entry.getRevision() - 1;
            boolean switched = false;
            if (entry != null && parentEntry != null) {
                switched = !entry.getURL().equals(SVNPathUtil.append(parentEntry.getURL(), 
                        SVNEncodingUtil.uriEncode(path.getName())));
            }
            if (!switched && !dir.getWCAccess().isWCRoot(path)) {
                if (parentEntry != null) {
                    parentRevision = parentEntry.getRevision();
                }
            } else if (!copyMode) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, 
                        "Did not expect ''{0}'' to be a working copy root", path);                    
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (parentRevision != entry.getRevision()) {
                commitAddition = true;
                commitCopy = true;
                addsOnly = false;
                cfRevision = entry.getRevision();
                if (copyMode) {
                    cfURL = entry.getURL();
                } else if (copyFromURL != null) {
                    cfURL = copyFromURL;
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, 
                            "Commit item ''{0}'' has copy flag but no copyfrom URL", path);                    
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        }
        boolean textModified = false;
        boolean propsModified = false;
        boolean commitLock;

        if (commitAddition) {
            SVNFileType addedFileType = SVNFileType.getType(path);
            if (addedFileType == SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, 
                        "''{0}'' is scheduled for addition, but is missing", path);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            SVNVersionedProperties props = dir.getProperties(entry.getName());
            SVNVersionedProperties baseProps = dir.getBaseProperties(entry.getName());            
            SVNProperties propDiff = null;
            if (entry.isScheduledForReplacement()) {
                propDiff = props.asMap();
            } else {
                propDiff = baseProps.compareTo(props).asMap();
            }
            boolean eolChanged = textModified = propDiff != null && propDiff.containsName(SVNProperty.EOL_STYLE);
            boolean charsetChanged = propDiff != null && propDiff.containsName(SVNProperty.CHARSET);
            textModified = eolChanged || charsetChanged;
            if (entry.getKind() == SVNNodeKind.FILE) {
                if (commitCopy) {
                    textModified = propDiff != null && (propDiff.containsName(SVNProperty.EOL_STYLE) || propDiff.containsName(SVNProperty.CHARSET));
                    if (!textModified) {
                        textModified = dir.hasTextModifications(entry.getName(), eolChanged);
                    }
                } else {
                    textModified = true;
                }
            }
            propsModified = propDiff != null && !propDiff.isEmpty();
        } else if (!commitDeletion) {
            SVNVersionedProperties props = dir.getProperties(entry.getName());
            SVNVersionedProperties baseProps = dir.getBaseProperties(entry.getName());
            SVNProperties propDiff = baseProps.compareTo(props).asMap();
            boolean forceComparison = textModified = propDiff != null && (propDiff.containsName(SVNProperty.EOL_STYLE) || propDiff.containsName(SVNProperty.CHARSET));
            propsModified = propDiff != null && !propDiff.isEmpty();
            if (entry.getKind() == SVNNodeKind.FILE) {
                textModified = dir.hasTextModifications(entry.getName(),  forceComparison);
            }
        }

        commitLock = entry.getLockToken() != null && (justLocked || textModified || propsModified
                        || commitDeletion || commitAddition || commitCopy);

        if (commitAddition || commitDeletion || textModified || propsModified
                || commitCopy || commitLock) {
            if (SVNWCAccess.matchesChangeList(changelists, entry)) {
                SVNCommitItem item = new SVNCommitItem(path, 
                        SVNURL.parseURIEncoded(url), cfURL != null ? SVNURL.parseURIEncoded(cfURL) : null, entry.getKind(), 
                        SVNRevision.create(entry.getRevision()), SVNRevision.create(cfRevision), 
                        commitAddition, commitDeletion, propsModified, textModified, commitCopy,
                        commitLock);
                String itemPath = dir.getRelativePath(dir.getWCAccess().retrieve(dir.getWCAccess().getAnchor()));
                if ("".equals(itemPath)) {
                    itemPath += entry.getName();
                } else if (!"".equals(entry.getName())) {
                    itemPath += "/" + entry.getName();
                }
                item.setPath(itemPath);
                commitables.put(path, item);
                if (lockTokens != null && entry.getLockToken() != null) {
                    lockTokens.put(url, entry.getLockToken());
                }
            }
        }

        //collect externals properties
        if (pathsToExternalsProperties != null && SVNWCAccess.matchesChangeList(changelists, entry)) {
            SVNVersionedProperties props = dir.getProperties(entry.getName());
            String externalsProperty = props.getStringPropertyValue(SVNProperty.EXTERNALS);
            if (externalsProperty != null) {
                pathsToExternalsProperties.put(dir.getFile(entry.getName()), externalsProperty);
            }
        }

        if (entries != null && SVNDepth.EMPTY.compareTo(depth) < 0 && (commitAddition || !commitDeletion)) {
            // recurse.
            for (Iterator ents = entries.entries(copyMode); ents.hasNext();) {
                if (dir != null && dir.getWCAccess() != null) {
                    dir.getWCAccess().checkCancelled();
                }
                SVNEntry currentEntry = (SVNEntry) ents.next();
                if (currentEntry.isThisDir()) {
                    continue;
                }
                // if recursion is forced and entry is explicitly copied, skip it.
                if (forcedRecursion && currentEntry.isCopied() && currentEntry.getCopyFromURL() != null) {
                    continue;
                }
                if (currentEntry.getDepth() == SVNDepth.EXCLUDE) {
                    continue;
                }
                if (entry.isScheduledForReplacement() && currentEntry.isScheduledForDeletion()) {
                    continue;
                }
                String currentCFURL = cfURL != null ? cfURL : copyFromURL;
                if (currentCFURL != null) {
                    currentCFURL = SVNPathUtil.append(currentCFURL, SVNEncodingUtil.uriEncode(currentEntry.getName()));
                }
                String currentURL = currentEntry.getURL();
                if (copyMode || currentEntry.getURL() == null) {
                    currentURL = SVNPathUtil.append(url, SVNEncodingUtil.uriEncode(currentEntry.getName()));
                }
                File currentFile = dir.getFile(currentEntry.getName());
                SVNAdminArea childDir;
                if (currentEntry.getKind() == SVNNodeKind.DIR) {
                    if (SVNDepth.FILES.compareTo(depth) >= 0) {
                        continue;
                    } 
                    
                    try {
                        childDir = dir.getWCAccess().retrieve(dir.getFile(currentEntry.getName()));
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                            childDir = null;
                        } else {
                            throw e;
                        }
                    }
                    
                    if (childDir == null) {
                        SVNFileType currentType = SVNFileType.getType(currentFile);
                        if (currentType == SVNFileType.NONE && currentEntry.isScheduledForDeletion()) {
                            if (SVNWCAccess.matchesChangeList(changelists, entry)) {
                                SVNCommitItem item = new SVNCommitItem(currentFile,
                                        SVNURL.parseURIEncoded(currentURL), null, currentEntry.getKind(),
                                        SVNRevision.UNDEFINED, SVNRevision.UNDEFINED, false, true, false,
                                        false, false, false);
                                String dirPath = dir.getRelativePath(dir.getWCAccess().retrieve(dir.getWCAccess().getAnchor()));
                                item.setPath(SVNPathUtil.append(dirPath, currentEntry.getName()));
                                commitables.put(currentFile, item);
                                continue;
                            }                            
                        } else if (currentType != SVNFileType.NONE) {
                            // directory is not missing, but obstructed, 
                            // or no special params are specified.
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Working copy ''{0}'' is missing or not locked", currentFile);
                            SVNErrorManager.error(err, SVNLogType.WC);
                        } else { 
                            ISVNCommitParameters.Action action = 
                                params != null ? params.onMissingDirectory(dir.getFile(currentEntry.getName())) : ISVNCommitParameters.ERROR;
                            if (action == ISVNCommitParameters.DELETE) {
                                SVNCommitItem item = new SVNCommitItem(currentFile,
                                        SVNURL.parseURIEncoded(currentURL), null, currentEntry.getKind(),
                                        SVNRevision.UNDEFINED, SVNRevision.UNDEFINED, false, true, false,
                                        false, false, false);
                                String dirPath = dir.getRelativePath(dir.getWCAccess().retrieve(dir.getWCAccess().getAnchor()));
                                item.setPath(SVNPathUtil.append(dirPath, currentEntry.getName()));
                                commitables.put(currentFile, item);
                                currentEntry.scheduleForDeletion();
                                entries.saveEntries(false);
                                continue;
                            } else if (action == ISVNCommitParameters.ERROR) {
                                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Working copy ''{0}'' is missing or not locked", currentFile);
                                SVNErrorManager.error(err, SVNLogType.WC);
                            } else {
                                continue;
                            }
                        }
                    }
                }
                
                SVNDepth depthBelowHere = depth;
                if (depth == SVNDepth.FILES || depth == SVNDepth.IMMEDIATES) {
                    depthBelowHere = SVNDepth.EMPTY;
                }

                harvestCommitables(commitables, dir, currentFile, entry, currentEntry, currentURL, 
                        currentCFURL, copyMode, addsOnly, justLocked, lockTokens, depthBelowHere, 
                        forcedRecursion, changelists, params, pathsToExternalsProperties);

            }
        }
        
        if (lockTokens != null && entry.getKind() == SVNNodeKind.DIR && commitDeletion) {
            // harvest lock tokens for deleted items.
            collectLocks(dir, lockTokens);
        }
    }
    
    private static void collectLocks(SVNAdminArea adminArea, Map lockTokens) throws SVNException {
        for (Iterator ents = adminArea.entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (entry.getURL() != null && entry.getLockToken() != null) {
                lockTokens.put(entry.getURL(), entry.getLockToken());
            }
            if (!adminArea.getThisDirName().equals(entry.getName()) && entry.isDirectory()) {
                
                SVNAdminArea child;
                try {
                    child = adminArea.getWCAccess().retrieve(adminArea.getFile(entry.getName()));
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                        child = null;
                    } else {
                        throw e;
                    }
                }
                if (child != null) {
                    collectLocks(child, lockTokens);
                }
            }
        }
        adminArea.closeEntries();
    }

    private static void removeRedundantPaths(Collection dirsToLockRecursively, Collection dirsToLock) {
        Map processedDirs = new SVNHashMap();
        for (Iterator paths = dirsToLock.iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            //check for path dublicates and remove them if any 
            if (processedDirs.containsKey(path)) {
                paths.remove();
                continue;
            }
            processedDirs.put(path, path);
            
            if (dirsToLockRecursively.contains(path)) {
                paths.remove();
            } else {
                for (Iterator recPaths = dirsToLockRecursively.iterator(); recPaths.hasNext();) {
                    String existingPath = (String) recPaths.next();
                    if (path.startsWith(existingPath + "/")) {
                        paths.remove();
                        break;
                    }
                }
            }
        }
    }

    private static File adjustRelativePaths(File rootFile, Collection relativePaths) throws SVNException {
        if (relativePaths.contains("")) {
            String targetName = SVNWCManager.getActualTarget(rootFile);
            if (!"".equals(targetName) && rootFile.getParentFile() != null) {
                // there is a versioned parent.
                rootFile = rootFile.getParentFile();
                List result = new ArrayList();
                for (Iterator paths = relativePaths.iterator(); paths.hasNext();) {
                    String path = (String) paths.next();
                    path = "".equals(path) ? targetName : SVNPathUtil.append(targetName, path);
                    if (!result.contains(path)) {
                        result.add(path);
                    }
                }
                relativePaths.clear();
                Collections.sort(result);
                relativePaths.addAll(result);
            }
        }
        return rootFile;
    }

    private static boolean isRecursiveCommitForced(File directory) throws SVNException {
        SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
        try {
            wcAccess.open(directory, false, 0);
            SVNEntry targetEntry = wcAccess.getEntry(directory, false);
            if (targetEntry != null) {
                return targetEntry.isCopied() || targetEntry.isScheduledForDeletion() || targetEntry.isScheduledForReplacement();
            }
        } finally {
            wcAccess.close();
        }
        return false;
    }

    public static String validateCommitMessage(String message) {
        if (message == null) {
            return message;
        }
        message = message.replaceAll("\r\n", "\n");
        message = message.replace('\r', '\n');
        return message;
    }
}
