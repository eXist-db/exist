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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNVersionedProperties;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.exist.versioning.svn.wc.ISVNCommitHandler;
import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.exist.versioning.svn.wc.ISVNOptions;
import org.exist.versioning.svn.wc.SVNBasicClient;
import org.exist.versioning.svn.wc.SVNCommitItem;
import org.exist.versioning.svn.wc.SVNCopySource;
import org.exist.versioning.svn.wc.SVNEvent;
import org.exist.versioning.svn.wc.SVNEventAction;
import org.exist.versioning.svn.wc.SVNUpdateClient;
import org.exist.versioning.svn.wc.SVNWCClient;
import org.exist.versioning.svn.wc.SVNWCUtil;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNCommitParameters;
import org.tmatesoft.svn.core.wc.ISVNExternalsHandler;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

public class SVNCopyDriver extends SVNBasicClient {

    private SVNWCAccess myWCAccess;
    private boolean myIsDisableLocalModificationsCopying;
    
    public void setDisableLocalModificationCopying(boolean disable) {
        myIsDisableLocalModificationsCopying = disable;
    }
    
    protected SVNCopyDriver(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNCopyDriver(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }

    protected void setWCAccess(SVNWCAccess access) {
        myWCAccess = access;
    }

    private SVNWCAccess getWCAccess() {
        if (myWCAccess == null) {
            return createWCAccess();
        }
        return myWCAccess;
    }

    private SVNAdminArea probeOpen(SVNWCAccess access, File path, boolean writeLock, int depth) throws SVNException {
        if (access != myWCAccess) {
            return access.probeOpen(path, writeLock, depth);
        }
        return myWCAccess.probeRetrieve(path);
    }

    private SVNAdminArea open(SVNWCAccess access, File path, boolean writeLock, boolean stealLock, int depth) throws SVNException {
        if (access != myWCAccess) {
            return access.open(path, writeLock, stealLock, depth);
        }
        return myWCAccess.retrieve(path);
    }

    private void close(SVNWCAccess access) throws SVNException {
        if (access != myWCAccess) {
            access.close();
        }
    }

    protected SVNCopySource[] expandCopySources(SVNCopySource[] sources) throws SVNException {
        Collection expanded = new ArrayList(sources.length);
        for (int i = 0; i < sources.length; i++) {
            SVNCopySource source = sources[i];
            if (source.isCopyContents() && source.isURL()) {
                // get children at revision.
                SVNRevision pegRevision = source.getPegRevision();
                if (!pegRevision.isValid()) {
                    pegRevision = SVNRevision.HEAD;
                }
                SVNRevision startRevision = source.getRevision();
                if (!startRevision.isValid()) {
                    startRevision = pegRevision;
                }
                SVNRepositoryLocation[] locations = getLocations(source.getURL(), null, null, pegRevision, startRevision, SVNRevision.UNDEFINED);
                SVNRepository repository = createRepository(locations[0].getURL(), null, null, true);
                long revision = locations[0].getRevisionNumber();
                Collection entries = new ArrayList();
                repository.getDir("", revision, null, 0, entries);
                for (Iterator ents = entries.iterator(); ents.hasNext();) {
                    SVNDirEntry entry = (SVNDirEntry) ents.next();
                    // add new copy source.
                    expanded.add(new SVNCopySource(SVNRevision.UNDEFINED, source.getRevision(), entry.getURL()));
                }
            } else {
                expanded.add(source);
            }
        }
        return (SVNCopySource[]) expanded.toArray(new SVNCopySource[expanded.size()]);
    }

    private SVNCommitInfo copyReposToRepos(List copyPairs, boolean makeParents, boolean isMove, String message, SVNProperties revprops, ISVNCommitHandler commitHandler) throws SVNException {
        List pathInfos = new ArrayList();
        Map pathsMap = new SVNHashMap();
        for (int i = 0; i < copyPairs.size(); i++) {
            CopyPathInfo info = new CopyPathInfo();
            pathInfos.add(info);
        }
        String commonURL = ((CopyPair) copyPairs.get(0)).mySource;
        String topDstURL = ((CopyPair) copyPairs.get(0)).myDst;
        for (int i = 1; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            commonURL = SVNPathUtil.getCommonPathAncestor(commonURL, pair.mySource);
        }
        
        if (copyPairs.size() == 1) {
            commonURL = SVNPathUtil.getCommonPathAncestor(commonURL, topDstURL);
        } else {
            commonURL = SVNPathUtil.getCommonPathAncestor(commonURL, SVNPathUtil.removeTail(topDstURL));
        }
        
        try {
            SVNURL.parseURIEncoded(commonURL);
        } catch (SVNException e) {
            commonURL = null;
        }
        if (commonURL == null) {
            SVNURL url1 = SVNURL.parseURIEncoded(((CopyPair) copyPairs.get(0)).mySource);
            SVNURL url2 = SVNURL.parseURIEncoded(((CopyPair) copyPairs.get(0)).myDst);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Source and dest appear not to be in the same repository (src: ''{0}''; dst: ''{1}'')", new Object[] {url1, url2});
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNRepository nonTopRepos = null;
        for (int i = 0; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            CopyPathInfo info = (CopyPathInfo) pathInfos.get(i);
            if (nonTopRepos == null) {
                nonTopRepos = createRepository(SVNURL.parseURIEncoded(pair.mySource), null, null, true);
            }
            if (pair.mySource.equals(pair.myDst)) {
                info.isResurrection = true;
            }
        }
        /*
         * Get list of parents that have to be created. start with first 'dst' parent.
         * This is a list of urls.
         */        
        String rootURL = nonTopRepos.getRepositoryRoot(true).toString();

        List newDirs = new ArrayList();
        SVNURL oldLocation = null;
        if (makeParents) {
            CopyPair pair = (CopyPair) copyPairs.get(0);
            if (!pair.myDst.equals(rootURL)) {
                oldLocation = nonTopRepos.getLocation();
                nonTopRepos.setLocation(SVNURL.parseURIEncoded(pair.myDst).removePathTail(), false);
                SVNNodeKind kind = nonTopRepos.checkPath("", -1);
                while (kind == SVNNodeKind.NONE) {
                    newDirs.add(nonTopRepos.getLocation().toString());
                    nonTopRepos.setLocation(nonTopRepos.getLocation().removePathTail(), false);
                    kind = nonTopRepos.checkPath("", -1);
                }
            }
        } else if (Boolean.getBoolean("svnkit.compatibleHash")) {            
            // XXX: hack for tests to generate error message tests will like.
            // do not check paths above repository root.
            CopyPair pair = (CopyPair) copyPairs.get(0);
            if (!pair.myDst.equals(rootURL)) {
                oldLocation = nonTopRepos.getLocation();
                nonTopRepos.setLocation(SVNURL.parseURIEncoded(pair.myDst).removePathTail(), false);
                SVNNodeKind kind = nonTopRepos.checkPath("", -1);
                if (kind == SVNNodeKind.NONE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "''{0}'' path not found", nonTopRepos.getLocation());
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        }
        if (oldLocation != null) {
            nonTopRepos.setLocation(oldLocation, false);
        }

        /*
         * Check if source is dst child (while dst is not root).
         */
        for (int i = 0; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            CopyPathInfo info = (CopyPathInfo) pathInfos.get(i);
            if (!pair.myDst.equals(rootURL) && SVNPathUtil.getPathAsChild(pair.myDst, pair.mySource) != null) {
                info.isResurrection = true;
            }
        }

        long latestRevision = nonTopRepos.getLatestRevision();

        for (int i = 0; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            CopyPathInfo info = (CopyPathInfo) pathInfos.get(i);
            pair.mySourceRevisionNumber = getRevisionNumber(pair.mySourceRevision, nonTopRepos, null);
            info.mySourceRevisionNumber = pair.mySourceRevisionNumber;

            SVNRepositoryLocation[] locations = getLocations(SVNURL.parseURIEncoded(pair.mySource), null, null /*optimize topRepos*/, pair.mySourcePegRevision, pair.mySourceRevision, SVNRevision.UNDEFINED);
            pair.mySource = locations[0].getURL().toString();
            
            // tests:
            // src is equal to dst
            if (isMove && pair.mySource.equals(pair.myDst)) {
            //if ("".equals(srcRelative) && isMove) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot move URL ''{0}'' into itself", SVNURL.parseURIEncoded(pair.mySource));
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            // src doesn't exist at source revision.
            nonTopRepos.setLocation(SVNURL.parseURIEncoded(pair.mySource), false);
            info.mySourceKind = nonTopRepos.checkPath("", pair.mySourceRevisionNumber);
            if (info.mySourceKind == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND,
                        "Path ''{0}'' does not exist in revision {1}", new Object[] {SVNURL.parseURIEncoded(pair.mySource), new Long(pair.mySourceRevisionNumber)});
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            // dst already exists at HEAD.
            nonTopRepos.setLocation(SVNURL.parseURIEncoded(pair.myDst), false);
            SVNNodeKind dstKind = nonTopRepos.checkPath("", latestRevision);
            if (dstKind != SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS,
                        "Path ''{0}'' already exists", pair.myDst);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            info.mySource = pair.mySource;
            info.myDstPath = pair.myDst;
        }

        List paths = new ArrayList(copyPairs.size() * 2);
        List commitItems = new ArrayList(copyPairs.size() * 2);
        if (makeParents) {
            for (Iterator newDirsIter = newDirs.iterator(); newDirsIter.hasNext();) {
                String itemURL = (String) newDirsIter.next();
                SVNCommitItem item = new SVNCommitItem(null, 
                        SVNURL.parseURIEncoded(itemURL), null, SVNNodeKind.NONE, null, null, true, false, false, false, false, false);
                commitItems.add(item);
            }
        }

        for (Iterator infos = pathInfos.iterator(); infos.hasNext();) {
            CopyPathInfo info = (CopyPathInfo) infos.next();
            SVNURL itemURL = SVNURL.parseURIEncoded(info.myDstPath);
            SVNCommitItem item = new SVNCommitItem(null, itemURL, null, SVNNodeKind.NONE, null, null, true, false, false, false, false, false);
            commitItems.add(item);
            pathsMap.put(info.myDstPath, info);
            if (isMove && !info.isResurrection) {
                itemURL = SVNURL.parseURIEncoded(info.mySource);
                item = new SVNCommitItem(null, itemURL, null, SVNNodeKind.NONE, null, null, false, true, false, false, false, false);
                commitItems.add(item);
                pathsMap.put(info.mySource, info);
            }
        }

        if (makeParents) {
            for (Iterator newDirsIter = newDirs.iterator(); newDirsIter.hasNext();) {
                String dirPath = (String) newDirsIter.next();
                CopyPathInfo info = new CopyPathInfo();
                info.myDstPath = dirPath;
                info.isDirAdded = true;
                paths.add(info.myDstPath);
                pathsMap.put(dirPath, info);
            }
        }

        for (Iterator infos = pathInfos.iterator(); infos.hasNext();) {
            CopyPathInfo info = (CopyPathInfo) infos.next();
            nonTopRepos.setLocation(SVNURL.parseURIEncoded(info.mySource), false);
            Map mergeInfo = calculateTargetMergeInfo(null, null, SVNURL.parseURIEncoded(info.mySource),
                    info.mySourceRevisionNumber, nonTopRepos, false);
            if (mergeInfo != null) {
                info.myMergeInfoProp = SVNMergeInfoUtil.formatMergeInfoToString(mergeInfo, null);
            }
            paths.add(info.myDstPath);
            if (isMove && !info.isResurrection) {
                // this is too.
                paths.add(info.mySource);
            }
        }

        SVNCommitItem[] commitables = (SVNCommitItem[]) commitItems.toArray(new SVNCommitItem[commitItems.size()]);
        message = commitHandler.getCommitMessage(message, commitables);
        if (message == null) {
            return SVNCommitInfo.NULL;
        }
        message = SVNCommitUtil.validateCommitMessage(message);

        revprops = commitHandler.getRevisionProperties(message, commitables, revprops == null ? new SVNProperties() : revprops);
        if (revprops == null) {
            return SVNCommitInfo.NULL;
        }

        SVNPropertiesManager.validateRevisionProperties(revprops);

        SVNURL topURL = SVNURL.parseURIEncoded((String) paths.get(0));
        for(int i = 1; i < paths.size(); i++) {
            String url = (String) paths.get(i);
            topURL = SVNURLUtil.getCommonURLAncestor(topURL, SVNURL.parseURIEncoded(url));
        }
        if (paths.contains(topURL.toString())) {
            topURL = topURL.removePathTail();
        }
        for(int i = 0; i < paths.size(); i++) {
            String url = (String) paths.get(i);
            SVNURL svnURL =  SVNURL.parseURIEncoded(url);
            url = SVNPathUtil.getPathAsChild(topURL.getPath(), svnURL.getPath());
            paths.set(i, url);
            CopyPathInfo info = (CopyPathInfo) pathsMap.remove(svnURL.toString());
            if (info != null) {
                if (info.mySource != null) {
                    info.mySourcePath = getPathRelativeToRoot(null, SVNURL.parseURIEncoded(info.mySource), SVNURL.parseURIEncoded(rootURL), null, null);
                    info.mySourceRelativePath = getPathRelativeToSession(SVNURL.parseURIEncoded(info.mySource), topURL, null);
                }
                pathsMap.put(url, info);
            }
            
        }
        
        nonTopRepos.setLocation(topURL, false);
        ISVNEditor commitEditor = nonTopRepos.getCommitEditor(message, null, true, revprops, null);
        ISVNCommitPathHandler committer = new CopyCommitPathHandler(pathsMap, isMove);

        SVNCommitInfo result = null;
        try {
            SVNCommitUtil.driveCommitEditor(committer, paths, commitEditor, latestRevision);
            result = commitEditor.closeEdit();
        } catch (SVNCancelException cancel) {
            throw cancel;
        } catch (SVNException e) {
            SVNErrorMessage err = e.getErrorMessage().wrap("Commit failed (details follow):");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        } finally {
            if (commitEditor != null && result == null) {
                try {
                    commitEditor.abortEdit();
                } catch (SVNException e) {
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, e);
                }
            }
        }
        if (result != null && result.getNewRevision() >= 0) {
            dispatchEvent(SVNEventFactory.createSVNEvent(null, SVNNodeKind.NONE, null, result.getNewRevision(), SVNEventAction.COMMIT_COMPLETED, null, null, null), ISVNEventHandler.UNKNOWN);
        }
        return result != null ? result : SVNCommitInfo.NULL;
    }

    private String getUUIDFromPath(SVNWCAccess wcAccess, File path) throws SVNException {
        SVNEntry entry = wcAccess.getVersionedEntry(path, true);
        String uuid = null;
        if (entry.getUUID() != null) {
            uuid = entry.getUUID();
        } else if (entry.getURL() != null) {
            SVNRepository repos = createRepository(entry.getSVNURL(), null, null, false);
            try {
                uuid = repos.getRepositoryUUID(true);
            } finally {
                repos.closeSession();
            }
        } else {
            if (wcAccess.isWCRoot(path)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            uuid = getUUIDFromPath(wcAccess, path.getParentFile());
        }
        return uuid;
    }

    private static void postCopyCleanup(SVNAdminArea dir) throws SVNException {
        SVNPropertiesManager.deleteWCProperties(dir, null, false);
        SVNFileUtil.setHidden(dir.getAdminDirectory(), true);
        Map attributes = new SVNHashMap();
        boolean save = false;

        for(Iterator entries = dir.entries(true); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            boolean deleted = entry.isDeleted();
            SVNNodeKind kind = entry.getKind();
            boolean force = false;
            if (entry.getDepth() == SVNDepth.EXCLUDE) {
                continue;
            }

            if (entry.isDeleted()) {
                force = true;
                attributes.put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_DELETE);
                attributes.put(SVNProperty.DELETED, null);
                if (entry.isDirectory()) {
                    attributes.put(SVNProperty.KIND, SVNProperty.KIND_FILE);
                }
            }
            if (entry.getLockToken() != null) {
                force = true;
                attributes.put(SVNProperty.LOCK_TOKEN, null);
                attributes.put(SVNProperty.LOCK_OWNER, null);
                attributes.put(SVNProperty.LOCK_CREATION_DATE, null);
            }
            if (force) {
                dir.modifyEntry(entry.getName(), attributes, false, force);
                save = true;
            }
            if (!deleted && kind == SVNNodeKind.DIR && !dir.getThisDirName().equals(entry.getName())) {
                SVNAdminArea childDir = dir.getWCAccess().retrieve(dir.getFile(entry.getName()));
                postCopyCleanup(childDir);
            }

            attributes.clear();
        }

        if (save) {
            dir.saveEntries(false);
        }
    }

    protected SVNCommitInfo setupCopy(SVNCopySource[] sources, SVNPath dst, boolean isMove, boolean makeParents,
            String message, SVNProperties revprops, ISVNCommitHandler commitHandler, ISVNCommitParameters commitParameters, ISVNExternalsHandler externalsHandler) throws SVNException {
        List pairs = new ArrayList(sources.length);
        for (int i = 0; i < sources.length; i++) {
            SVNCopySource source = sources[i];
            if (source.isURL() &&
                 (source.getPegRevision() == SVNRevision.BASE ||
                  source.getPegRevision() == SVNRevision.COMMITTED ||
                  source.getPegRevision() == SVNRevision.PREVIOUS)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION,
                        "Revision type requires a working copy path, not URL");
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        boolean srcIsURL = sources[0].isURL();
        boolean dstIsURL = dst.isURL();

        if (sources.length > 1) {
            for (int i = 0; i < sources.length; i++) {
                SVNCopySource source = sources[i];
                CopyPair pair = new CopyPair();
                pair.mySource = source.isURL() ? source.getURL().toString() : source.getFile().getAbsolutePath().replace(File.separatorChar, '/');
                pair.setSourceRevisions(source.getPegRevision(), source.getRevision());
                if (SVNPathUtil.isURL(pair.mySource) != srcIsURL) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                            "Cannot mix repository and working copy sources");
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                String baseName = source.getName();
                if (srcIsURL && !dstIsURL) {
                    baseName = SVNEncodingUtil.uriDecode(baseName);
                }
                pair.myDst = dstIsURL ? dst.getURL().appendPath(baseName, true).toString() :
                    new Resource(dst.getFile(), baseName).getAbsolutePath().replace(File.separatorChar, '/');
                pairs.add(pair);
            }
        } else {
            SVNCopySource source = sources[0];
            CopyPair pair = new CopyPair();
            pair.mySource = source.isURL() ? source.getURL().toString() : source.getFile().getAbsolutePath().replace(File.separatorChar, '/');
            pair.setSourceRevisions(source.getPegRevision(), source.getRevision());
            pair.myDst = dstIsURL ? dst.getURL().toString() : dst.getFile().getAbsolutePath().replace(File.separatorChar, '/');
            pairs.add(pair);
        }

        if (!srcIsURL && !dstIsURL) {
            for (Iterator ps = pairs.iterator(); ps.hasNext();) {
                CopyPair pair = (CopyPair) ps.next();
                String srcPath = pair.mySource;
                String dstPath = pair.myDst;
                if (SVNPathUtil.isAncestor(srcPath, dstPath)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                            "Cannot copy path ''{0}'' into its own child ''{1}",
                            new Object[] { srcPath, dstPath });
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        }
        
        if (isMove && !srcIsURL) {
            for (Iterator ps = pairs.iterator(); ps.hasNext();) {
                CopyPair pair = (CopyPair) ps.next();
                SVNWCAccess wcAccess = getWCAccess();
                try {
                    File srcFile = new Resource(pair.mySource);
                    probeOpen(wcAccess, srcFile, false, 0);
                    SVNEntry entry = wcAccess.getVersionedEntry(new Resource(pair.mySource), false);
                    if (entry.getExternalFilePath() != null) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CANNOT_MOVE_FILE_EXTERNAL, 
                                "Cannot move the file external at ''{0}''; please propedit the svn:externals description that created it", 
                                srcFile);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                } finally {
                    close(wcAccess);
                }
            }
        }
        
        if (isMove) {
            if (srcIsURL == dstIsURL) {
                for (Iterator ps = pairs.iterator(); ps.hasNext();) {
                    CopyPair pair = (CopyPair) ps.next();
                    boolean same;
                    Object p;
                    if (!srcIsURL) {
                        File srcPath = new Resource(pair.mySource);
                        File dstPath = new Resource(pair.myDst);
                        same = srcPath.equals(dstPath);
                        p = srcPath;
                    } else {
                        SVNURL srcURL = SVNURL.parseURIEncoded(pair.mySource);
                        SVNURL dstURL = SVNURL.parseURIEncoded(pair.myDst);
                        same = srcURL.getPath().equals(dstURL.getPath());
                        p = srcURL;
                    }
                    if (same) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                                "Cannot move path ''{0}'' into itself", p);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                        "Moves between the working copy and the repository are not supported");
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        } else {
            if (!srcIsURL) {
                boolean needReposRevision = false;
                boolean needReposPegRevision = false;
                for (Iterator ps = pairs.iterator(); ps.hasNext();) {
                    CopyPair pair = (CopyPair) ps.next();
                    if (pair.mySourceRevision != SVNRevision.UNDEFINED &&
                            pair.mySourceRevision != SVNRevision.WORKING) {
                        needReposRevision = true;
                    }
                    if (pair.mySourcePegRevision != SVNRevision.UNDEFINED &&
                            pair.mySourcePegRevision != SVNRevision.WORKING) {
                        needReposPegRevision = true;
                    }
                    if (needReposRevision || needReposPegRevision) {
                        break;
                    }
                }

                if (needReposRevision || needReposPegRevision) {
                    for (Iterator ps = pairs.iterator(); ps.hasNext();) {
                        CopyPair pair = (CopyPair) ps.next();
                        SVNWCAccess wcAccess = getWCAccess();
                        try {
                            probeOpen(wcAccess, new Resource(pair.mySource), false, 0);
                            SVNEntry entry = wcAccess.getEntry(new Resource(pair.mySource), false);
                            SVNURL url = entry.isCopied() ? entry.getCopyFromSVNURL() : entry.getSVNURL();
                            if (url == null) {
                                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL,
                                        "''{0}'' does not have a URL associated with it", new File(pair.mySource));
                                SVNErrorManager.error(err, SVNLogType.WC);
                            }
                            pair.mySource = url.toString();
                            if (!needReposPegRevision || pair.mySourcePegRevision == SVNRevision.BASE) {
                                pair.mySourcePegRevision = entry.isCopied() ? SVNRevision.create(entry.getCopyFromRevision()) : SVNRevision.create(entry.getRevision());
                            }
                            if (pair.mySourceRevision == SVNRevision.BASE) {
                                pair.mySourceRevision = entry.isCopied() ? SVNRevision.create(entry.getCopyFromRevision()) : SVNRevision.create(entry.getRevision());
                            }
                        } finally {
                            close(wcAccess);
                        }
                    }
                    srcIsURL = true;
                }
            }
        }
        if (!srcIsURL && !dstIsURL) {
            copyWCToWC(pairs, isMove, makeParents);
            return SVNCommitInfo.NULL;
        } else if (!srcIsURL && dstIsURL) {
            //wc2url.
            return copyWCToRepos(pairs, makeParents, message, revprops, commitHandler, commitParameters, externalsHandler);
        } else if (srcIsURL && !dstIsURL) {
            // url2wc.
            copyReposToWC(pairs, makeParents);
            return SVNCommitInfo.NULL;
        } else {
            return copyReposToRepos(pairs, makeParents, isMove, message, revprops, commitHandler);
        }
    }

    private SVNCommitInfo copyWCToRepos(List copyPairs, boolean makeParents, String message,
            SVNProperties revprops, ISVNCommitHandler commitHandler, ISVNCommitParameters commitParameters, ISVNExternalsHandler externalsHandler) throws SVNException {
        String topSrc = ((CopyPair) copyPairs.get(0)).mySource;
        for (int i = 1; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            topSrc = SVNPathUtil.getCommonPathAncestor(topSrc, pair.mySource);
        }
        SVNWCAccess wcAccess = createWCAccess();
        SVNCommitInfo info = null;
        ISVNEditor commitEditor = null;
        Collection tmpFiles = null;
        try {
            SVNAdminArea adminArea = wcAccess.probeOpen(new Resource(topSrc), false, SVNWCAccess.INFINITE_DEPTH);
            wcAccess.setAnchor(adminArea.getRoot());

            String topDstURL = ((CopyPair) copyPairs.get(0)).myDst;
            topDstURL = SVNPathUtil.removeTail(topDstURL);
            for (int i = 1; i < copyPairs.size(); i++) {
                CopyPair pair = (CopyPair) copyPairs.get(i);
                topDstURL = SVNPathUtil.getCommonPathAncestor(topDstURL, pair.myDst);
            }

            // should we use also wcAccess here? i do not think so.
            SVNRepository repos = createRepository(SVNURL.parseURIEncoded(topDstURL), adminArea.getRoot(),
                    wcAccess, true);
            List newDirs = new ArrayList();
            if (makeParents) {
                String rootURL = topDstURL;
                SVNNodeKind kind = repos.checkPath("", -1);
                while(kind == SVNNodeKind.NONE) {
                    newDirs.add(rootURL);
                    rootURL = SVNPathUtil.removeTail(rootURL);
                    repos.setLocation(SVNURL.parseURIEncoded(rootURL), false);
                    kind = repos.checkPath("", -1);
                }
                topDstURL = rootURL;
            }

            for (int i = 0; i < copyPairs.size(); i++) {
                CopyPair pair = (CopyPair) copyPairs.get(i);
                SVNEntry entry = wcAccess.getEntry(new Resource(pair.mySource), false);
                pair.mySourceRevisionNumber = entry.getRevision();
                String dstRelativePath = SVNPathUtil.getPathAsChild(topDstURL, pair.myDst);
                dstRelativePath = SVNEncodingUtil.uriDecode(dstRelativePath);
                SVNNodeKind kind = repos.checkPath(dstRelativePath, -1);
                if (kind != SVNNodeKind.NONE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS,
                            "Path ''{0}'' already exists", SVNURL.parseURIEncoded(pair.myDst));
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
            // create commit items list to fetch log messages.
            List commitItems = new ArrayList(copyPairs.size());
            if (makeParents) {
                for (int i = 0; i < newDirs.size(); i++) {
                    String newDirURL = (String) newDirs.get(i);
                    SVNURL url = SVNURL.parseURIEncoded(newDirURL);
                    SVNCommitItem item = new SVNCommitItem(null, url, null, SVNNodeKind.NONE, null, null, true, false, false, false, false, false);
                    commitItems.add(item);
                }
            }
            for (int i = 0; i < copyPairs.size(); i++) {
                CopyPair pair = (CopyPair) copyPairs.get(i);
                SVNURL url = SVNURL.parseURIEncoded(pair.myDst);
                SVNCommitItem item = new SVNCommitItem(null, url, null, SVNNodeKind.NONE, null, null, true, false, false,
                        false, false, false);
                commitItems.add(item);
            }
            SVNCommitItem[] commitables = (SVNCommitItem[]) commitItems.toArray(new SVNCommitItem[commitItems.size()]);
            message = commitHandler.getCommitMessage(message, commitables);
            if (message == null) {
                return SVNCommitInfo.NULL;
            }
            revprops = commitHandler.getRevisionProperties(message, commitables, revprops == null ? new SVNProperties() : revprops);
            if (revprops == null) {
                return SVNCommitInfo.NULL;
            }

            Map allCommitables = new TreeMap(SVNCommitUtil.FILE_COMPARATOR);
            repos.setLocation(repos.getRepositoryRoot(true), false);
            Map pathsToExternalsProps = new SVNHashMap();
            for (int i = 0; i < copyPairs.size(); i++) {
                CopyPair source = (CopyPair) copyPairs.get(i);
                File srcFile = new Resource(source.mySource);
                SVNEntry entry = wcAccess.getVersionedEntry(srcFile, false);
                SVNAdminArea dirArea;
                if (entry.isDirectory()) {
                    dirArea = wcAccess.retrieve(srcFile);
                } else {
                    dirArea = wcAccess.retrieve(srcFile.getParentFile());
                }


                pathsToExternalsProps.clear();

                Map sourceCommittables = new HashMap();
                SVNCommitUtil.harvestCommitables(sourceCommittables, dirArea, srcFile,
                        null, entry, source.myDst, entry.getURL(), true, false, false, null, SVNDepth.INFINITY,
                        false, null, commitParameters, pathsToExternalsProps);
                
                // filter out file externals.
                // path of the source relative to wcAccess anchor.
                String basePath = SVNPathUtil.canonicalizePath(wcAccess.getAnchor().getAbsolutePath());
                String sourcePath = SVNPathUtil.canonicalizePath(srcFile.getAbsolutePath());
                String path = SVNPathUtil.getRelativePath(basePath, sourcePath);
                SVNCommitUtil.filterOutFileExternals(Collections.singletonList(path), sourceCommittables, wcAccess);
                
                allCommitables.putAll(sourceCommittables);

                SVNCommitItem item = (SVNCommitItem) allCommitables.get(srcFile);
                SVNURL srcURL = entry.getSVNURL();

                Map mergeInfo = calculateTargetMergeInfo(srcFile, wcAccess, srcURL,
                        source.mySourceRevisionNumber, repos, false);

                Map wcMergeInfo = SVNPropertiesManager.parseMergeInfo(srcFile, entry, false);
                if (wcMergeInfo != null && mergeInfo != null) {
                    mergeInfo = SVNMergeInfoUtil.mergeMergeInfos(mergeInfo, wcMergeInfo);
                } else if (mergeInfo == null) {
                    mergeInfo = wcMergeInfo;
                }
                if (mergeInfo != null) {
                    String mergeInfoString = SVNMergeInfoUtil.formatMergeInfoToString(mergeInfo, null);
                    setCommitItemProperty(item, SVNProperty.MERGE_INFO, SVNPropertyValue.create(mergeInfoString));
                }

                if (!pathsToExternalsProps.isEmpty()) {
                    LinkedList newExternals = new LinkedList();
                    for (Iterator pathsIter = pathsToExternalsProps.keySet().iterator(); pathsIter.hasNext();) {
                        File localPath = (File) pathsIter.next();
                        String externalsPropString = (String) pathsToExternalsProps.get(localPath);
                        SVNExternal[] externals = SVNExternal.parseExternals(localPath.getAbsolutePath(),
                                externalsPropString);
                        boolean introduceVirtualExternalChange = false;
                        newExternals.clear();
                        for (int k = 0; k < externals.length; k++) {
                            File externalWC = new Resource(localPath, externals[k].getPath());
                            SVNEntry externalEntry = null;
                            SVNRevision externalsWCRevision = SVNRevision.UNDEFINED;

                            try {
                                wcAccess.open(externalWC, false, 0);
                                externalEntry = wcAccess.getEntry(externalWC, false);
                            } catch (SVNException svne) {
                                if (svne instanceof SVNCancelException) {
                                    throw svne;
                                }
                            } finally {
                                wcAccess.closeAdminArea(externalWC);
                            }
                            
                            if (externalEntry == null) {
                                externalEntry = wcAccess.getEntry(externalWC, false);
                            }                            
                            if (externalEntry != null && (externalEntry.isThisDir() || externalEntry.getExternalFilePath() != null)) {
                                externalsWCRevision = SVNRevision.create(externalEntry.getRevision());
                            }
                            
                            SVNEntry ownerEntry = wcAccess.getEntry(localPath, false);
                            SVNURL ownerURL = null;
                            if (ownerEntry != null) {
                                ownerURL = ownerEntry.getSVNURL();
                            }
                            if (ownerURL == null) {
                                // there is no entry for the directory that has external
                                // property or no url in it?
                                continue;
                            }
                            SVNRevision[] revs = externalsHandler.handleExternal(
                                    externalWC,
                                    externals[k].resolveURL(repos.getRepositoryRoot(true), ownerURL),
                                    externals[k].getRevision(),
                                    externals[k].getPegRevision(),
                                    externals[k].getRawValue(),
                                    externalsWCRevision);

                            if (revs != null && revs[0].equals(externals[k].getRevision())) {
                                newExternals.add(externals[k].getRawValue());
                            } else if (revs != null) {
                                SVNExternal newExternal = new SVNExternal(externals[k].getPath(),
                                        externals[k].getUnresolvedUrl(), revs[1],
                                        revs[0], true, externals[k].isPegRevisionExplicit(),
                                        externals[k].isNewFormat());

                                newExternals.add(newExternal.toString());

                                if (!introduceVirtualExternalChange) {
                                    introduceVirtualExternalChange = true;
                                }
                            }
                        }

                        if (introduceVirtualExternalChange) {
                            String newExternalsProp = "";
                            for (Iterator externalsIter = newExternals.iterator(); externalsIter.hasNext();) {
                                String external = (String) externalsIter.next();
                                newExternalsProp += external + '\n';
                            }

                            SVNCommitItem itemWithExternalsChanges = (SVNCommitItem) allCommitables.get(localPath);
                            if (itemWithExternalsChanges != null) {
                                setCommitItemProperty(itemWithExternalsChanges, SVNProperty.EXTERNALS, SVNPropertyValue.create(newExternalsProp));
                            } else {
                                SVNAdminArea childArea = wcAccess.retrieve(localPath);
                                String relativePath = childArea.getRelativePath(dirArea);
                                String itemURL = SVNPathUtil.append(source.myDst,
                                        SVNEncodingUtil.uriEncode(relativePath));
                                itemWithExternalsChanges = new SVNCommitItem(localPath,
                                        SVNURL.parseURIEncoded(itemURL), null, SVNNodeKind.DIR, null, null,
                                        false, false, true, false, false, false);
                                setCommitItemProperty(itemWithExternalsChanges, SVNProperty.EXTERNALS, SVNPropertyValue.create(newExternalsProp));
                                allCommitables.put(localPath, itemWithExternalsChanges);
                            }
                        }
                    }
                }
            }

            commitItems = new ArrayList(allCommitables.values());
            // in case of 'base' commit, remove all 'deletions', mark all other items as non-modified, remove additions and copies from other urls.
            
            if (myIsDisableLocalModificationsCopying) {
                ArrayList harmlessItems = new ArrayList();
                for(int i = 0 ; i < commitItems.size(); i++) {
                    SVNCommitItem item = (SVNCommitItem) commitItems.get(i);
                    if (item.isDeleted()) {
                        // deletion or replacement, skip it.
                        continue;
                    }                
                    if (item.isAdded()) {
                        if (!item.isCopied()) {
                            // this is just new file or directory
                            continue;
                        }
                        SVNURL copyFromURL = item.getCopyFromURL();
                        if (copyFromURL == null) {
                            // also skip.
                            continue;
                        }                    
                        SVNEntry entry = wcAccess.getEntry(item.getFile(), false);
                        if (entry == null) {
                            continue;
                        }
                        SVNURL expectedURL = entry.getSVNURL();
                        if (!copyFromURL.equals(expectedURL)) {
                            // copied from some other location.
                            continue;
                        }
                    }
                    setCommitItemFlags(item, false, false);
                    harmlessItems.add(item);                
                }
                commitItems = harmlessItems;
            }
            
            // add parents to commits hash?
            if (makeParents) {
                for (int i = 0; i < newDirs.size(); i++) {
                    String newDirURL = (String) newDirs.get(i);
                    SVNURL url = SVNURL.parseURIEncoded(newDirURL);
                    SVNCommitItem item = new SVNCommitItem(null, url, null, SVNNodeKind.NONE, null, null, true, false, false, false, false, false);
                    commitItems.add(item);
                }
            }
            commitables = (SVNCommitItem[]) commitItems.toArray(new SVNCommitItem[commitItems.size()]);
            for (int i = 0; i < commitables.length; i++) {
                setCommitItemAccess(commitables[i], wcAccess);
            }
            allCommitables = new TreeMap();
            SVNURL url = SVNCommitUtil.translateCommitables(commitables, allCommitables);

            repos = createRepository(url, null, null, true);

            SVNCommitMediator mediator = new SVNCommitMediator(allCommitables);
            tmpFiles = mediator.getTmpFiles();

            message = SVNCommitUtil.validateCommitMessage(message);

            SVNURL rootURL = repos.getRepositoryRoot(true);
            SVNPropertiesManager.validateRevisionProperties(revprops);
            commitEditor = repos.getCommitEditor(message, null, true, revprops, mediator);
            info = SVNCommitter.commit(tmpFiles, allCommitables, rootURL.getPath(), commitEditor);
            commitEditor = null;

        } catch (SVNCancelException cancel) {
            throw cancel;
        } catch (SVNException e) {
            // wrap error message.
            SVNErrorMessage err = e.getErrorMessage().wrap("Commit failed (details follow):");
            SVNErrorManager.error(err, SVNLogType.WC);
        } finally {
            if (tmpFiles != null) {
                for (Iterator files = tmpFiles.iterator(); files.hasNext();) {
                    File file = (File) files.next();
                    SVNFileUtil.deleteFile(file);
                }
            }
            if (commitEditor != null && info == null) {
                // should we hide this exception?
                try {
                    commitEditor.abortEdit();
                } catch (SVNException e) {
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, e);
                }
            }
            if (wcAccess != null) {
                wcAccess.close();
            }
        }
        if (info != null && info.getNewRevision() >= 0) {
            dispatchEvent(SVNEventFactory.createSVNEvent(null, SVNNodeKind.NONE, null, info.getNewRevision(), SVNEventAction.COMMIT_COMPLETED, null, null, null), ISVNEventHandler.UNKNOWN);
        }
        return info != null ? info : SVNCommitInfo.NULL;
    }

    private void copyReposToWC(List copyPairs, boolean makeParents) throws SVNException {
        for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
            CopyPair pair = (CopyPair) pairs.next();
            SVNRepositoryLocation[] locations = getLocations(SVNURL.parseURIEncoded(pair.mySource), null, null, pair.mySourcePegRevision, pair.mySourceRevision, SVNRevision.UNDEFINED);
            // new
            String actualURL = locations[0].getURL().toString();
            String originalSource = pair.mySource;
            pair.mySource = actualURL;
            pair.myOriginalSource = originalSource;
        }
        // get src and dst ancestors.
        String topDst = ((CopyPair) copyPairs.get(0)).myDst;
        if (copyPairs.size() > 1) {
            topDst = SVNPathUtil.removeTail(topDst);
        }
        String topSrc = ((CopyPair) copyPairs.get(0)).mySource;
        for(int i = 1; i < copyPairs.size(); i++) {
            CopyPair pair = (CopyPair) copyPairs.get(i);
            topSrc = SVNPathUtil.getCommonPathAncestor(topSrc, pair.mySource);
        }
        if (copyPairs.size() == 1) {
            topSrc = SVNPathUtil.removeTail(topSrc);
        }
        SVNRepository topSrcRepos = createRepository(SVNURL.parseURIEncoded(topSrc), null, null, false);
        try {
            for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
                CopyPair pair = (CopyPair) pairs.next();
                pair.mySourceRevisionNumber = getRevisionNumber(pair.mySourceRevision, topSrcRepos, null);
            }
            String reposPath = topSrcRepos.getLocation().toString();
            for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
                CopyPair pair = (CopyPair) pairs.next();
                String relativePath = SVNPathUtil.getPathAsChild(reposPath, pair.mySource);
                relativePath = SVNEncodingUtil.uriDecode(relativePath);
                SVNNodeKind kind = topSrcRepos.checkPath(relativePath, pair.mySourceRevisionNumber);
                if (kind == SVNNodeKind.NONE) {
                    SVNErrorMessage err;
                    if (pair.mySourceRevisionNumber >= 0) {
                        err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path ''{0}'' not found in revision {1}",
                                new Object[] {SVNURL.parseURIEncoded(pair.mySource), new Long(pair.mySourceRevisionNumber)});
                    } else {
                        err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path ''{0}'' not found in head revision",
                                SVNURL.parseURIEncoded(pair.mySource));
                    }
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                pair.mySourceKind = kind;
                SVNFileType dstType = SVNFileType.getType(new Resource(pair.myDst));
                if (dstType != SVNFileType.NONE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "Path ''{0}'' already exists", new Resource(pair.myDst));
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                String dstParent = SVNPathUtil.removeTail(pair.myDst);
                SVNFileType dstParentFileType = SVNFileType.getType(new Resource(dstParent));
                if (makeParents && dstParentFileType == SVNFileType.NONE) {
                    // create parents.
                    addLocalParents(new Resource(dstParent), getEventDispatcher());
                } else if (dstParentFileType != SVNFileType.DIRECTORY) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "Path ''{0}'' is not a directory", dstParent);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
            SVNWCAccess dstAccess = getWCAccess();
            try {
                probeOpen(dstAccess, new Resource(topDst), true, 0);
                for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
                    CopyPair pair = (CopyPair) pairs.next();
                    SVNEntry dstEntry = dstAccess.getEntry(new Resource(pair.myDst), true);
                    if (dstEntry != null) {
                        if (dstEntry.getDepth() == SVNDepth.EXCLUDE || dstEntry.isAbsent()) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS,
                                    "''{0}'' is already under version control", new Resource(pair.myDst));
                            SVNErrorManager.error(err, SVNLogType.WC);
                        }
                        if (!dstEntry.isDirectory() && !dstEntry.isScheduledForDeletion() && !dstEntry.isDeleted()) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE,
                                    "Entry for ''{0}'' exists (though the working file is missing)", new Resource(pair.myDst));
                            SVNErrorManager.error(err, SVNLogType.WC);
                        }
                    }
                }
                String srcUUID = null;
                String dstUUID = null;
                try {
                    srcUUID = topSrcRepos.getRepositoryUUID(true);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.RA_NO_REPOS_UUID) {
                        throw e;
                    }
                }
                String dstParent = topDst;
                if (copyPairs.size() == 1) {
                    dstParent = SVNPathUtil.removeTail(topDst);
                }
                try {
                    dstUUID = getUUIDFromPath(dstAccess, new Resource(dstParent));
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.RA_NO_REPOS_UUID) {
                        throw e;
                    }
                }
                boolean sameRepos = false;
                if (srcUUID != null) {
                    sameRepos = srcUUID.equals(dstUUID);
                }
                for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
                    CopyPair pair = (CopyPair) pairs.next();
                    copyReposToWC(pair, sameRepos, topSrcRepos, dstAccess);
                }
            } finally {
                    close(dstAccess);                    
            }
        } finally {
            topSrcRepos.closeSession();
        }

    }

    private void copyReposToWC(CopyPair pair, boolean sameRepositories, SVNRepository topSrcRepos, SVNWCAccess dstAccess) throws SVNException {
        long srcRevNum = pair.mySourceRevisionNumber;

        if (pair.mySourceKind == SVNNodeKind.DIR) {
            // do checkout
            String srcURL = pair.myOriginalSource;
            SVNURL url = SVNURL.parseURIEncoded(srcURL);
            SVNUpdateClient updateClient = new SVNUpdateClient(getRepositoryPool(), getOptions());
            updateClient.setEventHandler(getEventDispatcher());

            File dstFile = new Resource(pair.myDst);
            SVNRevision srcRevision = pair.mySourceRevision;
            SVNRevision srcPegRevision = pair.mySourcePegRevision;
            updateClient.doCheckout(url, dstFile, srcPegRevision, srcRevision, SVNDepth.INFINITY, false);

            if (sameRepositories) {
                url = SVNURL.parseURIEncoded(pair.mySource);

                SVNAdminArea dstArea = dstAccess.open(dstFile, true, SVNWCAccess.INFINITE_DEPTH);
                SVNEntry dstRootEntry = dstArea.getEntry(dstArea.getThisDirName(), false);
                if (srcRevision == SVNRevision.HEAD) {
                    srcRevNum = dstRootEntry.getRevision();
                }
                SVNAdminArea dir = dstAccess.getAdminArea(dstFile.getParentFile());
                SVNWCManager.add(dstFile, dir, url, srcRevNum, SVNDepth.INFINITY);
                Map srcMergeInfo = calculateTargetMergeInfo(null, null, url, srcRevNum, topSrcRepos, false);
                extendWCMergeInfo(dstFile, dstRootEntry, srcMergeInfo, dstAccess);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Source URL ''{0}'' is from foreign repository; leaving it as a disjoint WC", url);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        } else if (pair.mySourceKind == SVNNodeKind.FILE) {
            String srcURL = pair.mySource;
            SVNURL url = SVNURL.parseURIEncoded(srcURL);

            File dst = new Resource(pair.myDst);
            SVNAdminArea dir = dstAccess.getAdminArea(dst.getParentFile());
            File tmpFile = SVNAdminUtil.createTmpFile(dir);
            String path = getPathRelativeToRoot(null, url, null, null, topSrcRepos);
            SVNProperties props = new SVNProperties();
            OutputStream os = null;
            long revision = -1;
            try {
                os = SVNFileUtil.openFileForWriting(tmpFile);
                revision = topSrcRepos.getFile(path, srcRevNum, props, new SVNCancellableOutputStream(os, this));
            } finally {
                SVNFileUtil.closeFile(os);
            }
            if (srcRevNum < 0) {
                srcRevNum = revision;
            }
            SVNWCManager.addRepositoryFile(dir, dst.getName(), null, tmpFile, props, null,
                    sameRepositories ? pair.mySource : null,
                    sameRepositories ? srcRevNum : -1);

            SVNEntry entry = dstAccess.getEntry(dst, false);
            Map mergeInfo = calculateTargetMergeInfo(null, null, url, srcRevNum, topSrcRepos, false);
            extendWCMergeInfo(dst, entry, mergeInfo, dstAccess);

            SVNEvent event = SVNEventFactory.createSVNEvent(dst, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNEventAction.ADD, null, null, null);
            dstAccess.handleEvent(event);

            sleepForTimeStamp();
        }
    }

    private void copyWCToWC(List copyPairs, boolean isMove, boolean makeParents) throws SVNException {
        for (Iterator pairs = copyPairs.iterator(); pairs.hasNext();) {
            CopyPair pair = (CopyPair) pairs.next();
            File source = new Resource(pair.mySource);
            SVNFileType srcFileType = SVNFileType.getType(source);
            if (srcFileType == SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND,
                        "Path ''{0}'' does not exist", source);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (isMove && SVNWCUtil.isWorkingCopyRoot(source)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                        "Cannot move ''{0}'' as it is the root of the working copy", source);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            SVNFileType dstFileType = SVNFileType.getType(new Resource(pair.myDst));
            if (dstFileType != SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS,
                        "Path ''{0}'' already exists", new Resource(pair.myDst));
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            File dstParent = new Resource(SVNPathUtil.removeTail(pair.myDst));
            pair.myBaseName = SVNPathUtil.tail(pair.myDst);
            SVNFileType dstParentFileType = SVNFileType.getType(dstParent);
            if (makeParents && dstParentFileType == SVNFileType.NONE) {
                // create parents.
                addLocalParents(dstParent, getEventDispatcher());
            } else if (dstParentFileType != SVNFileType.DIRECTORY) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY,
                        "Path ''{0}'' is not a directory", dstParent);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        if (isMove) {
            moveWCToWC(copyPairs);
        } else {
            copyWCToWC(copyPairs);
        }
    }

    protected void copyDisjointWCToWC(File nestedWC) throws SVNException {
        SVNFileType nestedWCType = SVNFileType.getType(nestedWC);
        if (nestedWCType != SVNFileType.DIRECTORY) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                    "This kind of copy can be run on a root of a disjoint wc directory only");
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        nestedWC = new Resource(nestedWC.getAbsolutePath().replace(File.separatorChar, '/'));
        File nestedWCParent = nestedWC.getParentFile();
        if (nestedWCParent == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                    "{0} seems to be not a disjoint wc since it has no parent", nestedWC);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        SVNWCAccess parentWCAccess = createWCAccess();
        SVNWCAccess nestedWCAccess = createWCAccess();
        try {
            SVNAdminArea parentArea = parentWCAccess.open(nestedWCParent, true, 0);

            SVNEntry srcEntryInParent = parentWCAccess.getEntry(nestedWC, false);
            if (srcEntryInParent != null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS,
                        "Entry ''{0}'' already exists in parent directory", nestedWC.getName());
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            SVNAdminArea nestedArea = nestedWCAccess.open(nestedWC, false, SVNWCAccess.INFINITE_DEPTH);

            SVNEntry nestedWCThisEntry = nestedWCAccess.getVersionedEntry(nestedWC, false);
            SVNEntry parentThisEntry = parentWCAccess.getVersionedEntry(nestedWCParent, false);

            // uuids may be identical while it might be absolutely independent repositories.
            // subversion uses repos roots comparison for local copies, and uuids comparison for
            // operations involving ra access. so, I believe we should act similarly here.

            if (nestedWCThisEntry.getRepositoryRoot() != null && parentThisEntry.getRepositoryRoot() != null &&
                    !nestedWCThisEntry.getRepositoryRoot().equals(parentThisEntry.getRepositoryRoot())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE,
                        "Cannot copy to ''{0}'', as it is not from repository ''{1}''; it is from ''{2}''",
                        new Object[] { nestedWCParent, nestedWCThisEntry.getRepositoryRootURL(),
                        parentThisEntry.getRepositoryRootURL() });
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            if (parentThisEntry.isScheduledForDeletion()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE,
                        "Cannot copy to ''{0}'', as it is scheduled for deletion", nestedWCParent);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            if (nestedWCThisEntry.isScheduledForDeletion()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE,
                        "Cannot copy ''{0}'', as it is scheduled for deletion", nestedWC);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            SVNURL nestedWCReposRoot = getReposRoot(nestedWC, null, SVNRevision.WORKING, nestedArea,
                    nestedWCAccess);
            String nestedWCPath = getPathRelativeToRoot(nestedWC, null, nestedWCReposRoot, nestedWCAccess, null);

            SVNURL parentReposRoot = getReposRoot(nestedWCParent, null, SVNRevision.WORKING, parentArea,
                    parentWCAccess);
            String parentPath = getPathRelativeToRoot(nestedWCParent, null, parentReposRoot, parentWCAccess, null);

            if (SVNPathUtil.isAncestor(nestedWCPath, parentPath)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                        "Cannot copy path ''{0}'' into its own child ''{1}",
                        new Object[] { nestedWCPath, parentPath });
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            if ((nestedWCThisEntry.isScheduledForAddition() && !nestedWCThisEntry.isCopied()) ||
                    nestedWCThisEntry.getURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS,
                        "Cannot copy or move ''{0}'': it is not in repository yet; " +
                        "try committing first", nestedWC);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            copyDisjointDir(nestedWC, parentWCAccess, nestedWCParent);
            parentWCAccess.probeTry(nestedWC, true, SVNWCAccess.INFINITE_DEPTH);
        } finally {
            parentWCAccess.close();
            nestedWCAccess.close();
            sleepForTimeStamp();
        }
    }

    private void copyDisjointDir(File nestedWC, SVNWCAccess parentAccess, File nestedWCParent) throws SVNException {
        SVNWCClient wcClient = new SVNWCClient((ISVNAuthenticationManager) null, null);
        wcClient.setEventHandler(getEventDispatcher());
        wcClient.doCleanup(nestedWC);

        SVNWCAccess nestedWCAccess = createWCAccess();
        SVNAdminArea dir;
        String copyFromURL = null;
        long copyFromRevision = -1;
        try {
            dir = nestedWCAccess.open(nestedWC, true, SVNWCAccess.INFINITE_DEPTH);
            SVNEntry nestedWCThisEntry = nestedWCAccess.getVersionedEntry(nestedWC, false);
            postCopyCleanup(dir);
            if (nestedWCThisEntry.isCopied()) {
                if (nestedWCThisEntry.getCopyFromURL() != null) {
                    copyFromURL = nestedWCThisEntry.getCopyFromURL();
                    copyFromRevision = nestedWCThisEntry.getCopyFromRevision();
                }

                Map attributes = new SVNHashMap();
                attributes.put(SVNProperty.URL, copyFromURL);
                dir.modifyEntry(dir.getThisDirName(), attributes, true, false);
            } else {
                copyFromURL = nestedWCThisEntry.getURL();
                copyFromRevision = nestedWCThisEntry.getRevision();
            }
        } finally {
            nestedWCAccess.close();
        }
        SVNWCManager.add(nestedWC, parentAccess.getAdminArea(nestedWCParent),
                SVNURL.parseURIEncoded(copyFromURL), copyFromRevision, SVNDepth.INFINITY);
    }

    private void copyWCToWC(List pairs) throws SVNException {
        // find common ancestor for all dsts.
        String dstParentPath = null;
        for (Iterator ps = pairs.iterator(); ps.hasNext();) {
            CopyPair pair = (CopyPair) ps.next();
            String dstPath = pair.myDst;
            if (dstParentPath == null) {
                dstParentPath = SVNPathUtil.removeTail(pair.myDst);
            }
            dstParentPath = SVNPathUtil.getCommonPathAncestor(dstParentPath, dstPath);
        }
        SVNWCAccess dstAccess = getWCAccess();
        try {
            open(dstAccess, new Resource(dstParentPath), true, false, 0);
            for (Iterator ps = pairs.iterator(); ps.hasNext();) {
                CopyPair pair = (CopyPair) ps.next();
                checkCancelled();
                SVNWCAccess srcAccess = null;
                try {
                    // do real copy.
                    File sourceFile = new Resource(pair.mySource);
                    copyFiles(sourceFile, new Resource(dstParentPath), dstAccess, pair.myBaseName);
                } finally {
                    if (srcAccess != null && srcAccess != dstAccess) {
                        close(srcAccess);
                    }
                }
            }
        } finally {
            close(dstAccess);
            sleepForTimeStamp();
        }
    }

    private void moveWCToWC(List pairs) throws SVNException {
        for (Iterator ps = pairs.iterator(); ps.hasNext();) {
            CopyPair pair = (CopyPair) ps.next();
            checkCancelled();
            File srcParent = new Resource(SVNPathUtil.removeTail(pair.mySource));
            File dstParent = new Resource(SVNPathUtil.removeTail(pair.myDst));
            File sourceFile = new Resource(pair.mySource);
            SVNFileType srcType = SVNFileType.getType(sourceFile);
            SVNWCAccess srcAccess = createWCAccess();
            SVNWCAccess dstAccess = null;
            try {
                srcAccess.open(srcParent, true, srcType == SVNFileType.DIRECTORY ? -1 : 0);
                if (srcParent.equals(dstParent)) {
                    dstAccess = srcAccess;
                } else {
                    String srcParentPath = srcParent.getAbsolutePath().replace(File.separatorChar, '/');
                    srcParentPath = SVNPathUtil.validateFilePath(srcParentPath);
                    String dstParentPath = dstParent.getAbsolutePath().replace(File.separatorChar, '/');
                    dstParentPath = SVNPathUtil.validateFilePath(dstParentPath);
                    if (srcType == SVNFileType.DIRECTORY &&
                            SVNPathUtil.isAncestor(srcParentPath, dstParentPath)) {
                        dstAccess = srcAccess;
                        if (dstAccess.getAdminArea(dstParent) == null) {
                            dstAccess.open(dstParent, true, 0);
                        }
                    } else {
                        dstAccess = createWCAccess();
                        dstAccess.open(dstParent, true, 0);
                    }
                }
                copyFiles(sourceFile, dstParent, dstAccess, pair.myBaseName);
                // delete src.
                SVNWCManager.delete(srcAccess, srcAccess.getAdminArea(srcParent), sourceFile, true, true);
            } finally {
                if (dstAccess != null && dstAccess != srcAccess) {
                    dstAccess.close();
                } 
                srcAccess.close();
            }
        }
        sleepForTimeStamp();
    }

    private void copyFiles(File src, File dstParent, SVNWCAccess dstAccess, String dstName) throws SVNException {
        SVNWCAccess srcAccess = getWCAccess();
        try {
            probeOpen(srcAccess, src, false, -1);
            SVNEntry dstEntry = dstAccess.getVersionedEntry(dstParent, false);
            SVNEntry srcEntry = srcAccess.getVersionedEntry(src, false);

            if (srcEntry.getRepositoryRoot() != null && dstEntry.getRepositoryRoot() != null &&
                    !srcEntry.getRepositoryRoot().equals(dstEntry.getRepositoryRoot())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE,
                        "Cannot copy to ''{0}'', as it is not from repository ''{1}''; it is from ''{2}''",
                        new Object[] {dstParent, srcEntry.getRepositoryRootURL(), dstEntry.getRepositoryRootURL()});
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (dstEntry.isScheduledForDeletion()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SCHEDULE,
                        "Cannot copy to ''{0}'', as it is scheduled for deletion", dstParent);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            SVNFileType srcType = SVNFileType.getType(src);
            if (srcType == SVNFileType.FILE || srcType == SVNFileType.SYMLINK) {
                if (srcEntry.isScheduledForAddition() && !srcEntry.isCopied()) {
                    copyAddedFileAdm(src, srcAccess, dstAccess, dstParent, dstName, true);
                } else {
                    copyFileAdm(src, srcAccess, dstParent, dstAccess, dstName);
                }
            } else if (srcType == SVNFileType.DIRECTORY) {
                if (srcEntry.isScheduledForAddition() && !srcEntry.isCopied()) {
                    copyAddedDirAdm(src, srcAccess, dstParent, dstAccess, dstName, true);
                } else {
                    copyDirAdm(src, srcAccess, dstAccess, dstParent, dstName);
                }
            }
        } finally {
            close(srcAccess);
        }
    }

    private void copyFileAdm(File src, SVNWCAccess srcAccess, File dstParent, SVNWCAccess dstAccess, String dstName) throws SVNException {
        File dst = new Resource(dstParent, dstName);
        SVNFileType dstType = SVNFileType.getType(dst);
        if (dstType != SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "''{0}'' already exists and is in the way", dst);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNEntry dstEntry = dstAccess.getEntry(dst, false);
        if (dstEntry != null && !dstEntry.isScheduledForDeletion()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "There is already a versioned item ''{0}''", dst);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNEntry srcEntry = srcAccess.getVersionedEntry(src, false);
        if ((srcEntry.isScheduledForAddition() && !srcEntry.isCopied()) || srcEntry.getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "Cannot copy or move ''{0}'': it is not in repository yet; " +
            		"try committing first", src);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        String copyFromURL;
        long copyFromRevision;
        SVNAdminArea srcDir = srcAccess.getAdminArea(src.getParentFile());
        if (srcEntry.isCopied()) {
            // get cf info - one of the parents has to keep that.
            SVNLocationEntry location = determineCopyFromInfo(src, srcAccess, srcEntry, dstEntry);
            copyFromURL = location.getPath();
            copyFromRevision = location.getRevision();
        } else {
            copyFromURL = srcEntry.getURL();
            copyFromRevision = srcEntry.getRevision();
        }
        // copy base file.
        File srcBaseFile = new Resource(src.getParentFile(), SVNAdminUtil.getTextBasePath(src.getName(), false));
        File dstBaseFile = new Resource(dstParent, SVNAdminUtil.getTextBasePath(dstName, true));
        SVNFileUtil.copyFile(srcBaseFile, dstBaseFile, false);
        SVNVersionedProperties srcBaseProps = srcDir.getBaseProperties(src.getName());
        SVNVersionedProperties srcWorkingProps = srcDir.getProperties(src.getName());

        // copy wc file.
        SVNAdminArea dstDir = dstAccess.getAdminArea(dstParent);
        File tmpWCFile = SVNAdminUtil.createTmpFile(dstDir);
        if (srcWorkingProps.getPropertyValue(SVNProperty.SPECIAL) != null) {
            // TODO create symlink there?
            SVNFileUtil.copyFile(src, tmpWCFile, false);
        } else {
            SVNFileUtil.copyFile(src, tmpWCFile, false);
        }
        SVNWCManager.addRepositoryFile(dstDir, dstName, tmpWCFile, null, srcBaseProps.asMap(), srcWorkingProps.asMap(), copyFromURL, copyFromRevision);

        SVNEvent event = SVNEventFactory.createSVNEvent(dst, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNEventAction.ADD, null, null, null);
        dstAccess.handleEvent(event);
    }

    private void copyAddedFileAdm(File src, SVNWCAccess srcAccess, SVNWCAccess dstAccess, File dstParent, String dstName, boolean isAdded) throws SVNException {
        File dst = new Resource(dstParent, dstName);
        SVNFileUtil.copyFile(src, dst, false);
        if (isAdded) {
            SVNWCManager.add(dst, dstAccess.getAdminArea(dstParent), null, SVNRepository.INVALID_REVISION, SVNDepth.INFINITY);
            copyProps(src, dst, srcAccess, dstAccess);
        }
    }

    private void copyDirAdm(File src, SVNWCAccess srcAccess, SVNWCAccess dstAccess, File dstParent,
            String dstName) throws SVNException {
        File dst = new Resource(dstParent, dstName);
        SVNEntry srcEntry = srcAccess.getVersionedEntry(src, false);
        if ((srcEntry.isScheduledForAddition() && !srcEntry.isCopied()) || srcEntry.getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS,
                    "Cannot copy or move ''{0}'': it is not in repository yet; " +
                    "try committing first", src);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNFileUtil.copyDirectory(src, dst, true, getEventDispatcher());
        SVNWCClient wcClient = new SVNWCClient((ISVNAuthenticationManager) null, null);
        wcClient.setEventHandler(getEventDispatcher());
        wcClient.doCleanup(dst);

        SVNWCAccess tgtAccess = getWCAccess();
        SVNAdminArea dir;
        String copyFromURL = null;
        long copyFromRevision = -1;
        try {
            dir = open(tgtAccess, dst, true, false, -1);
            postCopyCleanup(dir);
            if (srcEntry.isCopied()) {
                SVNEntry dstEntry = dstAccess.getEntry(dst, false);
                SVNLocationEntry info = determineCopyFromInfo(src, srcAccess, srcEntry, dstEntry);
                copyFromURL = info.getPath();
                copyFromRevision = info.getRevision();

                Map attributes = new SVNHashMap();
                attributes.put(SVNProperty.URL, copyFromURL);
                dir.modifyEntry(dir.getThisDirName(), attributes, true, false);
            } else {
                copyFromURL = srcEntry.getURL();
                copyFromRevision = srcEntry.getRevision();
            }
        } finally {
            close(tgtAccess);
        }
        SVNWCManager.add(dst, dstAccess.getAdminArea(dstParent), SVNURL.parseURIEncoded(copyFromURL), copyFromRevision, SVNDepth.INFINITY);
    }

    private void copyAddedDirAdm(File src, SVNWCAccess srcAccess, File dstParent, SVNWCAccess dstParentAccess, String dstName, boolean isAdded) throws SVNException {
        File dst = new Resource(dstParent, dstName);
        if (!isAdded) {
            SVNFileUtil.copyDirectory(src, dst, true, getEventDispatcher());
        } else {
            checkCancelled();
            dst.mkdirs();

            SVNWCManager.add(dst, dstParentAccess.getAdminArea(dstParent), null, SVNRepository.INVALID_REVISION, SVNDepth.INFINITY);
            copyProps(src, dst, srcAccess, dstParentAccess);
            
            SVNAdminArea srcChildArea = srcAccess.retrieve(src);

            File[] entries = SVNFileListUtil.listFiles(src);

            for (int i = 0; entries != null && i < entries.length; i++) {
                checkCancelled();
                File fsEntry = entries[i];
                String name = fsEntry.getName();
                if (SVNFileUtil.getAdminDirectoryName().equals(name)) {
                    continue;
                }

                SVNEntry entry = srcChildArea.getEntry(name, true);
                if (fsEntry.isDirectory()) {
                    copyAddedDirAdm(fsEntry, srcAccess, dst, dstParentAccess, name, entry != null);
                } else if (fsEntry.isFile()) {
                    copyAddedFileAdm(fsEntry, srcAccess, dstParentAccess, dst, name, entry != null);
                }
            }
        }
    }

    private void copyProps(File src, File dst, SVNWCAccess srcAccess, SVNWCAccess dstAccess) throws SVNException {
        SVNEntry srcEntry = srcAccess.getVersionedEntry(src, false);
        SVNAdminArea srcArea = srcEntry.getAdminArea();
        SVNVersionedProperties srcProps = srcArea.getProperties(srcEntry.getName());
        Collection propNames = srcProps.getPropertyNames(null);
        for (Iterator propNamesIter = propNames.iterator(); propNamesIter.hasNext();) {
            String propName = (String) propNamesIter.next();
            SVNPropertyValue propValue = srcProps.getPropertyValue(propName);
            SVNPropertiesManager.setProperty(dstAccess, dst, propName, propValue, false);
        }
    }
    
    private SVNLocationEntry determineCopyFromInfo(File src, SVNWCAccess srcAccess, SVNEntry srcEntry, SVNEntry dstEntry) throws SVNException {
        String url;
        long rev;
        if (srcEntry.getCopyFromURL() != null) {
            url = srcEntry.getCopyFromURL();
            rev = srcEntry.getCopyFromRevision();
        } else {
            SVNLocationEntry info = getCopyFromInfoFromParent(src, srcAccess);
            url = info.getPath();
            rev = info.getRevision();
        }
        if (dstEntry != null && rev == dstEntry.getRevision() && url.equals(dstEntry.getCopyFromURL())) {
            url = null;
            rev = -1;
        }
        return new SVNLocationEntry(rev, url);
    }

    private SVNLocationEntry getCopyFromInfoFromParent(File file, SVNWCAccess access) throws SVNException {
        File parent = file.getParentFile();
        String rest = file.getName();
        String url = null;
        long rev = -1;
        while (parent != null && url == null) {
            try {
                SVNEntry entry = access.getVersionedEntry(parent, false);
                url = entry.getCopyFromURL();
                rev = entry.getCopyFromRevision();
            } catch (SVNException e) {
                SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
                try {
                    probeOpen(wcAccess, parent, false, -1);
                    SVNEntry entry = wcAccess.getVersionedEntry(parent, false);
                    url = entry.getCopyFromURL();
                    rev = entry.getCopyFromRevision();
                } finally {
                    close(wcAccess);
                }
            }
            if (url != null) {
                url = SVNPathUtil.append(url, SVNEncodingUtil.uriEncode(rest));
            } else {
                rest = SVNPathUtil.append(parent.getName(), rest);
                parent = parent.getParentFile();
            }
        }
        return new SVNLocationEntry(rev, url);
    }


    private void addLocalParents(File path, ISVNEventHandler handler) throws SVNException {
        boolean created = path.mkdirs();
        SVNWCClient wcClient = new SVNWCClient((ISVNAuthenticationManager) null, null);
        try {
            wcClient.setEventHandler(handler);
            wcClient.doAdd(path, false, false, true, SVNDepth.EMPTY, true, true);
        } catch (SVNException e) {
            if (created) {
                SVNFileUtil.deleteAll(path, true);
            }
            throw e;
        }
    }

    private void extendWCMergeInfo(File path, SVNEntry entry, Map mergeInfo, SVNWCAccess access) throws SVNException {
        Map wcMergeInfo = SVNPropertiesManager.parseMergeInfo(path, entry, false);
        if (wcMergeInfo != null && mergeInfo != null) {
            wcMergeInfo = SVNMergeInfoUtil.mergeMergeInfos(wcMergeInfo, mergeInfo);
        } else if (wcMergeInfo == null) {
            wcMergeInfo = mergeInfo;
        }
        SVNPropertiesManager.recordWCMergeInfo(path, wcMergeInfo, access);
    }

    private Map calculateTargetMergeInfo(File srcFile, SVNWCAccess access, SVNURL srcURL, long srcRevision,
            SVNRepository repository, boolean noReposAccess) throws SVNException {
        boolean isLocallyAdded = false;
        SVNEntry entry = null;
        SVNURL url = null;
        if (access != null) {
            entry = access.getVersionedEntry(srcFile, false);
            if (entry.isScheduledForAddition() && !entry.isCopied()) {
                isLocallyAdded = true;
            } else {
                if (entry.getCopyFromURL() != null) {
                    url = entry.getCopyFromSVNURL();
                    srcRevision = entry.getCopyFromRevision();
                } else if (entry.getURL() != null) {
                    url = entry.getSVNURL();
                    srcRevision = entry.getRevision();
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL,
                            "Entry for ''{0}'' has no URL", srcFile);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        } else {
            url = srcURL;
        }

        Map targetMergeInfo = null;
        if (!isLocallyAdded) {
            String mergeInfoPath;
            if (!noReposAccess) {
                SVNRepository repos = repository;
                if (repos == null) {
                    repos = createRepository(url, null, false);
                }
                SVNURL oldLocation = null;
                try {
                    mergeInfoPath = getPathRelativeToSession(url, null, repos);
                    if (mergeInfoPath == null) {
                        oldLocation = repos.getLocation();
                        repos.setLocation(url, false);
                        mergeInfoPath = "";
                    }
                    targetMergeInfo = getReposMergeInfo(repos, mergeInfoPath, srcRevision,
                    		SVNMergeInfoInheritance.INHERITED, true);
                } finally {
                    if (repository == null) {
                        repos.closeSession();
                    } else if (oldLocation != null) {
                        repos.setLocation(oldLocation, false);
                    }
                }
            } else {
                targetMergeInfo = getWCMergeInfo(srcFile, entry, null, SVNMergeInfoInheritance.INHERITED, false,
                        new boolean[1]);
            }
        }
        return targetMergeInfo;
    }

    private static class CopyCommitPathHandler implements ISVNCommitPathHandler {

        private Map myPathInfos;
        private boolean myIsMove;

        public CopyCommitPathHandler(Map pathInfos, boolean isMove) {
            myPathInfos = pathInfos;
            myIsMove = isMove;
        }

        public boolean handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
            CopyPathInfo pathInfo = (CopyPathInfo) myPathInfos.get(commitPath);
            boolean doAdd = false;
            boolean doDelete = false;
            if (pathInfo.isDirAdded) {
                commitEditor.addDir(commitPath, null, SVNRepository.INVALID_REVISION);
                return true;
            }

            if (pathInfo.isResurrection) {
                if (!myIsMove) {
                    doAdd = true;
                }
            } else {
                if (myIsMove) {
                    if (commitPath.equals(pathInfo.mySourceRelativePath)) {
                        doDelete = true;
                    } else {
                        doAdd = true;
                    }
                } else {
                    doAdd = true;
                }
            }
            if (doDelete) {
                commitEditor.deleteEntry(commitPath, -1);
            }
            boolean closeDir = false;
            if (doAdd) {
                SVNPathUtil.checkPathIsValid(commitPath);
                if (pathInfo.mySourceKind == SVNNodeKind.DIR) {
                    commitEditor.addDir(commitPath, pathInfo.mySourcePath, pathInfo.mySourceRevisionNumber);
                    if (pathInfo.myMergeInfoProp != null) {
                        commitEditor.changeDirProperty(SVNProperty.MERGE_INFO, SVNPropertyValue.create(pathInfo.myMergeInfoProp));
                    }
                    closeDir = true;
                } else {
                    commitEditor.addFile(commitPath, pathInfo.mySourcePath, pathInfo.mySourceRevisionNumber);
                    if (pathInfo.myMergeInfoProp != null) {
                        commitEditor.changeFileProperty(commitPath, SVNProperty.MERGE_INFO, SVNPropertyValue.create(pathInfo.myMergeInfoProp));
                    }
                    commitEditor.closeFile(commitPath, null);
                }
            }
            return closeDir;
        }
    }

    private static class CopyPathInfo {
        public String mySourceRelativePath;
        public boolean isDirAdded;
        public boolean isResurrection;

        public SVNNodeKind mySourceKind;

        public String mySource;
        public String mySourcePath;
        public String myDstPath;

        public String myMergeInfoProp;
        public long mySourceRevisionNumber;
    }

    private static class CopyPair {

        public String mySource;
        public String myOriginalSource;

        public SVNNodeKind mySourceKind;

        public SVNRevision mySourceRevision;
        public SVNRevision mySourcePegRevision;
        public long mySourceRevisionNumber;

        public String myBaseName;
        public String myDst;

        public void setSourceRevisions(SVNRevision pegRevision, SVNRevision revision) {
            if (pegRevision == SVNRevision.UNDEFINED) {
                if (SVNPathUtil.isURL(mySource)) {
                    pegRevision = SVNRevision.HEAD;
                } else {
                    pegRevision = SVNRevision.WORKING;
                }
            }
            if (revision == SVNRevision.UNDEFINED) {
                revision = pegRevision;
            }
            mySourceRevision = revision;
            mySourcePegRevision = pegRevision;
        }
    }

}