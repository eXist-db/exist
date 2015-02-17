/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.exist.versioning.svn.internal.wc;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;

import org.exist.versioning.svn.internal.wc.admin.ISVNEntryHandler;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNVersionedProperties;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.exist.versioning.svn.wc.ISVNOptions;
import org.exist.versioning.svn.wc.SVNBasicClient;
import org.exist.versioning.svn.wc.SVNConflictAction;
import org.exist.versioning.svn.wc.SVNConflictReason;
import org.exist.versioning.svn.wc.SVNDiffClient;
import org.exist.versioning.svn.wc.SVNEvent;
import org.exist.versioning.svn.wc.SVNEventAction;
import org.exist.versioning.svn.wc.SVNLogClient;
import org.exist.versioning.svn.wc.SVNOperation;
import org.exist.versioning.svn.wc.SVNStatus;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.exist.versioning.svn.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNReusableEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNLocationSegment;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class SVNMergeDriver extends SVNBasicClient implements ISVNMergeStrategy {

    protected boolean myAreSourcesAncestral;
    protected boolean myIsSameRepository;
    protected boolean myIsDryRun;
    protected boolean myIsRecordOnly;
    protected boolean myIsForce;
    protected boolean myIsTargetMissingChild;
    protected boolean myHasExistingMergeInfo;
    protected boolean myIsTargetHasDummyMergeRange;
    protected boolean myIsIgnoreAncestry;
    protected boolean myIsSingleFileMerge;
    protected boolean myIsMergeInfoCapable;
    protected boolean myIsReIntegrateMerge;
    protected boolean myIsAddNecessitatedMerge;
    protected int myOperativeNotificationsNumber;
    protected int myNotificationsNumber;
    protected int myCurrentAncestorIndex;
    protected Map myConflictedPaths;
    protected Map myDryRunDeletions;
    protected SVNURL myURL;
    protected File myTarget;
    private List myMergedPaths;
    private List mySkippedPaths;
    private List myChildrenWithMergeInfo;
    private List myAddedPaths;
    protected SVNWCAccess myWCAccess;
    protected SVNRepository myRepository1;
    protected SVNRepository myRepository2;
    private SVNLogClient myLogClient;
    private List myPathsWithNewMergeInfo;
    private LinkedList myPathsWithDeletedMergeInfo;
    private MergeSource myCurrentMergeSource;
    private SVNMergeRangeList myImplicitSrcGap;
    
    public SVNMergeDriver(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNMergeDriver(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }
    
    public abstract SVNDiffOptions getMergeOptions();

    /**
     * @param  path 
     * @param  pegRevision 
     * @param  mergeSrcURL 
     * @param  srcPegRevision 
     * @param  discoverChangedPaths 
     * @param  revisionProperties 
     * @param  handler 
     * @throws SVNException 
     * @deprecated                    use {@link org.tmatesoft.svn.core.wc.SVNDiffClient#doGetLogMergedMergeInfo(File, SVNRevision, SVNURL, SVNRevision, boolean, String[], ISVNLogEntryHandler)}
     *                                instead
     */
    public void getLogMergedMergeInfo(File path, SVNRevision pegRevision, SVNURL mergeSrcURL, 
            SVNRevision srcPegRevision, boolean discoverChangedPaths, String[] revisionProperties, 
            ISVNLogEntryHandler handler) throws SVNException {
        if (this instanceof SVNDiffClient) {
            SVNDiffClient diffClient = (SVNDiffClient) this;
            diffClient.doGetLogMergedMergeInfo(path, pegRevision, mergeSrcURL, srcPegRevision, 
                    discoverChangedPaths, revisionProperties, handler);

        }
    }

    /**
     * @param  url 
     * @param  pegRevision 
     * @param  mergeSrcURL 
     * @param  srcPegRevision 
     * @param  discoverChangedPaths 
     * @param  revisionProperties 
     * @param  handler 
     * @throws SVNException 
     * @deprecated                   use {@link org.tmatesoft.svn.core.wc.SVNDiffClient#doGetLogMergedMergeInfo(SVNURL, SVNRevision, SVNURL, SVNRevision, boolean, String[], ISVNLogEntryHandler)}
     *                               instead
     */
    public void getLogMergedMergeInfo(SVNURL url, SVNRevision pegRevision, SVNURL mergeSrcURL, 
            SVNRevision srcPegRevision, boolean discoverChangedPaths, String[] revisionProperties, 
            ISVNLogEntryHandler handler) throws SVNException {
        if (this instanceof SVNDiffClient) {
            SVNDiffClient diffClient = (SVNDiffClient) this;
            diffClient.doGetLogMergedMergeInfo(url, pegRevision, mergeSrcURL, srcPegRevision, 
                    discoverChangedPaths, revisionProperties, handler);
        }
    }

    /**
     * @param  path 
     * @param  pegRevision 
     * @param  mergeSrcPath 
     * @param  srcPegRevision 
     * @param  discoverChangedPaths 
     * @param  revisionProperties 
     * @param  handler 
     * @throws SVNException 
     * @deprecated                    use {@link org.tmatesoft.svn.core.wc.SVNDiffClient#doGetLogMergedMergeInfo(File, SVNRevision, File, SVNRevision, boolean, String[], ISVNLogEntryHandler)}
     *                                instead
     */
    public void getLogMergedMergeInfo(File path, SVNRevision pegRevision, File mergeSrcPath, 
            SVNRevision srcPegRevision, boolean discoverChangedPaths, 
            String[] revisionProperties, ISVNLogEntryHandler handler) throws SVNException {
        if (this instanceof SVNDiffClient) {
            SVNDiffClient diffClient = (SVNDiffClient) this;
            diffClient.doGetLogMergedMergeInfo(path, pegRevision, mergeSrcPath, srcPegRevision, 
                    discoverChangedPaths, revisionProperties, handler);
        }
    }

    /**
     * @param  url 
     * @param  pegRevision 
     * @param  mergeSrcPath 
     * @param  srcPegRevision 
     * @param  discoverChangedPaths 
     * @param  revisionProperties 
     * @param  handler 
     * @throws SVNException 
     * @deprecated                   use {@link org.tmatesoft.svn.core.wc.SVNDiffClient#doGetLogMergedMergeInfo(SVNURL, SVNRevision, File, SVNRevision, boolean, String[], ISVNLogEntryHandler)}
     *                               instead
     */
    public void getLogMergedMergeInfo(SVNURL url, SVNRevision pegRevision, File mergeSrcPath, 
            SVNRevision srcPegRevision, boolean discoverChangedPaths, String[] revisionProperties, 
            ISVNLogEntryHandler handler) throws SVNException {
        if (this instanceof SVNDiffClient) {
            SVNDiffClient diffClient = (SVNDiffClient) this;
            diffClient.doGetLogMergedMergeInfo(url, pegRevision, mergeSrcPath, srcPegRevision, 
                    discoverChangedPaths, revisionProperties, handler);
        }
    }

    /**
     * @param  path 
     * @param  pegRevision 
     * @param  mergeSrcURL 
     * @param  srcPegRevision 
     * @param  discoverChangedPaths 
     * @param  revisionProperties 
     * @param  handler 
     * @throws SVNException
     * @deprecated                    use {@link org.tmatesoft.svn.core.wc.SVNDiffClient#doGetLogEligibleMergeInfo(File, SVNRevision, SVNURL, SVNRevision, boolean, String[], ISVNLogEntryHandler)}
     *                                instead  
     */
    public void getLogEligibleMergeInfo(File path, SVNRevision pegRevision, 
            SVNURL mergeSrcURL, SVNRevision srcPegRevision, boolean discoverChangedPaths, 
            String[] revisionProperties, ISVNLogEntryHandler handler) throws SVNException {
        if (this instanceof SVNDiffClient) {
            SVNDiffClient diffClient = (SVNDiffClient) this;
            diffClient.doGetLogEligibleMergeInfo(path, pegRevision, mergeSrcURL, srcPegRevision, discoverChangedPaths, revisionProperties, handler);
        }
    }
    
    /**
     * @param  url 
     * @param  pegRevision 
     * @param  mergeSrcURL 
     * @param  srcPegRevision 
     * @param  discoverChangedPaths 
     * @param  revisionProperties 
     * @param  handler 
     * @throws SVNException 
     * @deprecated                    use {@link org.tmatesoft.svn.core.wc.SVNDiffClient#doGetLogEligibleMergeInfo(SVNURL, SVNRevision, SVNURL, SVNRevision, boolean, String[], ISVNLogEntryHandler)}
     *                                instead
     */
    public void getLogEligibleMergeInfo(SVNURL url, SVNRevision pegRevision, 
            SVNURL mergeSrcURL, SVNRevision srcPegRevision, boolean discoverChangedPaths, 
            String[] revisionProperties, ISVNLogEntryHandler handler) throws SVNException {
        if (this instanceof SVNDiffClient) {
            SVNDiffClient diffClient = (SVNDiffClient) this;
            diffClient.doGetLogEligibleMergeInfo(url, pegRevision, mergeSrcURL, srcPegRevision, 
                    discoverChangedPaths, revisionProperties, handler);
        }
    }

    /**
     * @param  path 
     * @param  pegRevision 
     * @param  mergeSrcPath 
     * @param  srcPegRevision 
     * @param  discoverChangedPaths 
     * @param  revisionProperties 
     * @param  handler 
     * @throws SVNException 
     * @deprecated                    use {@link org.tmatesoft.svn.core.wc.SVNDiffClient#doGetLogEligibleMergeInfo(File, SVNRevision, File, SVNRevision, boolean, String[], ISVNLogEntryHandler)}
     *                                instead
     */
    public void getLogEligibleMergeInfo(File path, SVNRevision pegRevision, 
            File mergeSrcPath, SVNRevision srcPegRevision, boolean discoverChangedPaths, 
            String[] revisionProperties, ISVNLogEntryHandler handler) throws SVNException {
        if (this instanceof SVNDiffClient) {
            SVNDiffClient diffClient = (SVNDiffClient) this;
            diffClient.doGetLogEligibleMergeInfo(path, pegRevision, mergeSrcPath, srcPegRevision, 
                    discoverChangedPaths, revisionProperties, handler);
        }
    }

    /**
     * @param  url 
     * @param  pegRevision 
     * @param  mergeSrcPath 
     * @param  srcPegRevision 
     * @param  discoverChangedPaths 
     * @param  revisionProperties 
     * @param  handler 
     * @throws SVNException 
     * @deprecated                     use {@link org.tmatesoft.svn.core.wc.SVNDiffClient#doGetLogEligibleMergeInfo(SVNURL, SVNRevision, File, SVNRevision, boolean, String[], ISVNLogEntryHandler)}
     *                                 instead
     */
    public void getLogEligibleMergeInfo(SVNURL url, SVNRevision pegRevision, 
            File mergeSrcPath, SVNRevision srcPegRevision, boolean discoverChangedPaths, 
            String[] revisionProperties, ISVNLogEntryHandler handler) throws SVNException {
        if (this instanceof SVNDiffClient) {
            SVNDiffClient diffClient = (SVNDiffClient) this;
            diffClient.doGetLogEligibleMergeInfo(url, pegRevision, mergeSrcPath, srcPegRevision, 
                    discoverChangedPaths, revisionProperties, handler);
        }
    }

    /**
     * @param  path 
     * @param  pegRevision 
     * @return                mergeinfo
     * @throws SVNException 
     * @deprecated            use {@link org.tmatesoft.svn.core.wc.SVNDiffClient#doGetMergedMergeInfo(File, SVNRevision)}
     *                        instead
     */
    public Map getMergedMergeInfo(File path, SVNRevision pegRevision) throws SVNException {
        if (this instanceof SVNDiffClient) {
            SVNDiffClient diffClient = (SVNDiffClient) this;
            return diffClient.doGetMergedMergeInfo(path, pegRevision);
        }
        return null;
    }

    /**
     * @param  url 
     * @param  pegRevision 
     * @return                   mergeinfo
     * @throws SVNException 
     * @deprecated               use {@link org.tmatesoft.svn.core.wc.SVNDiffClient#doGetMergedMergeInfo(SVNURL, SVNRevision)}
     *                           instead
     */
    public Map getMergedMergeInfo(SVNURL url, SVNRevision pegRevision) throws SVNException {
        if (this instanceof SVNDiffClient) {
            SVNDiffClient diffClient = (SVNDiffClient) this;
            return diffClient.doGetMergedMergeInfo(url, pegRevision);
        }
        return null;
    }
    
    /**
     * @param  path 
     * @param  pegRevision 
     * @return                mergeinfo
     * @throws SVNException 
     * @deprecate             use {@link org.tmatesoft.svn.core.wc.SVNDiffClient#doSuggestMergeSources(File, SVNRevision)}
     *                        instead
     */
    public Collection suggestMergeSources(File path, SVNRevision pegRevision) throws SVNException {
        if (this instanceof SVNDiffClient) {
            SVNDiffClient diffClient = (SVNDiffClient) this;
            return diffClient.doSuggestMergeSources(path, pegRevision);
        }
        return null;
    }

    /**
     * @param  url 
     * @param  pegRevision 
     * @return                mergeinfo
     * @throws SVNException 
     * @deprecated            use {@link org.tmatesoft.svn.core.wc.SVNDiffClient#doSuggestMergeSources(SVNURL, SVNRevision)}
     *                        instead
     */
    public Collection suggestMergeSources(SVNURL url, SVNRevision pegRevision) throws SVNException {
        if (this instanceof SVNDiffClient) {
            SVNDiffClient diffClient = (SVNDiffClient) this;
            return diffClient.doSuggestMergeSources(url, pegRevision);
        }
        return null;
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        boolean isOperativeNotification = false;
        if (isOperativeNotification(event)) {
            myOperativeNotificationsNumber++;
            isOperativeNotification = true;
        }

        if (myAreSourcesAncestral) {
            myNotificationsNumber++;
            if (!myIsSingleFileMerge && isOperativeNotification) {
                Object childrenWithMergeInfoArray[] = null;
                if (myChildrenWithMergeInfo != null) {
                    childrenWithMergeInfoArray = myChildrenWithMergeInfo.toArray();
                }
                int newNearestAncestorIndex = findNearestAncestor(childrenWithMergeInfoArray, 
                        event.getAction() != SVNEventAction.UPDATE_DELETE, event.getFile());
                if (newNearestAncestorIndex != myCurrentAncestorIndex) {
                    MergePath child = (MergePath) childrenWithMergeInfoArray[newNearestAncestorIndex];
                    myCurrentAncestorIndex = newNearestAncestorIndex;
                    if (!child.myIsAbsent && !child.myRemainingRanges.isEmpty() &&
                            !(newNearestAncestorIndex == 0 && myIsTargetHasDummyMergeRange)) {
                        SVNMergeRange ranges[] = child.myRemainingRanges.getRanges();
                        SVNEvent mergeBeginEvent = SVNEventFactory.createSVNEvent(child.myPath, 
                                SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, 
                                myIsSameRepository ? SVNEventAction.MERGE_BEGIN : SVNEventAction.FOREIGN_MERGE_BEGIN, null, null, ranges[0]);
                        super.handleEvent(mergeBeginEvent, ISVNEventHandler.UNKNOWN);
                    }
                }
            }
            
            if (event.getContentsStatus() == SVNStatusType.MERGED ||
                    event.getContentsStatus() == SVNStatusType.CHANGED ||
                    event.getPropertiesStatus() == SVNStatusType.MERGED ||
                    event.getPropertiesStatus() == SVNStatusType.CHANGED ||
                    event.getAction() == SVNEventAction.UPDATE_ADD) {
                File mergedPath = event.getFile();
                if (myMergedPaths == null) {
                    myMergedPaths = new LinkedList();
                }
                myMergedPaths.add(mergedPath);
            }
            
            if (event.getAction() == SVNEventAction.SKIP) {
                File skippedPath = event.getFile();
                if (mySkippedPaths == null) {
                    mySkippedPaths = new LinkedList();
                }
                mySkippedPaths.add(skippedPath);
            } else if (event.getAction() == SVNEventAction.UPDATE_ADD) {
                boolean isRootOfAddedSubTree = false;
                File addedPath = event.getFile();
                if (myAddedPaths == null) {
                    isRootOfAddedSubTree = true;
                    myAddedPaths = new LinkedList();
                } else {
                    File addedPathParent = addedPath.getParentFile();
                    isRootOfAddedSubTree = !myAddedPaths.contains(addedPathParent);
                }
                if (isRootOfAddedSubTree) {
                    myAddedPaths.add(addedPath);
                }
            }
        } else if (!myIsSingleFileMerge && myOperativeNotificationsNumber == 1 && isOperativeNotification) {
            SVNEvent mergeBeginEvent = SVNEventFactory.createSVNEvent(myTarget, 
                    SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, 
                    myIsSameRepository ? SVNEventAction.MERGE_BEGIN : SVNEventAction.FOREIGN_MERGE_BEGIN, null, null, null);
            super.handleEvent(mergeBeginEvent, ISVNEventHandler.UNKNOWN);
        }
        
        super.handleEvent(event, progress);
    }

    public void checkCancelled() throws SVNCancelException {
        super.checkCancelled();
    }

    protected SVNLocationEntry getCopySource(File path, SVNURL url, SVNRevision revision) throws SVNException {
        long[] pegRev = { SVNRepository.INVALID_REVISION };
        SVNRepository repos = createRepository(url, path, null, revision, revision, pegRev);
        SVNLocationEntry copyFromEntry = null;
        String targetPath = getPathRelativeToRoot(path, url, null, null, repos);
        CopyFromReceiver receiver = new CopyFromReceiver(targetPath); 
            try {
                repos.log(new String[] { "" }, pegRev[0], 1, true, true, 0, false, new String[0], receiver);
                copyFromEntry = receiver.getCopyFromLocation();
            } catch (SVNException e) {
                SVNErrorCode errCode = e.getErrorMessage().getErrorCode();
                if (errCode == SVNErrorCode.FS_NOT_FOUND || errCode == SVNErrorCode.RA_DAV_REQUEST_FAILED) {
                    return new SVNLocationEntry(SVNRepository.INVALID_REVISION, null);
                }
                throw e;
            }

        return copyFromEntry == null ? new SVNLocationEntry(SVNRepository.INVALID_REVISION, null) 
                                     : copyFromEntry;
    }

    protected void getLogsForMergeInfoRangeList(SVNURL reposRootURL, String[] paths, SVNMergeRangeList rangeList, 
            boolean discoverChangedPaths, String[] revProps, ISVNLogEntryHandler handler) throws SVNException {
        if (rangeList.isEmpty()) {
            return;
        }
        
        SVNMergeRange[] listRanges = rangeList.getRanges();
        Arrays.sort(listRanges);
        
        SVNMergeRange youngestRange = listRanges[listRanges.length - 1];
        SVNRevision youngestRev = SVNRevision.create(youngestRange.getEndRevision());
        SVNMergeRange oldestRange = listRanges[0];
        SVNRevision oldestRev = SVNRevision.create(oldestRange.getStartRevision()); 
            
        LogHandlerFilter filterHandler = new LogHandlerFilter(handler, rangeList);
        SVNLogClient logClient = getLogClient();
        logClient.doLog(reposRootURL, paths, youngestRev, oldestRev, youngestRev, false, discoverChangedPaths, 
                false, 0, revProps, filterHandler);
        checkCancelled();
    }
    
    protected Map getMergeInfo(File path, SVNRevision pegRevision, SVNURL repositoryRoot[]) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminArea adminArea = wcAccess.probeOpen(path, false, 0);
            SVNEntry entry = wcAccess.getVersionedEntry(path, false);
            long revNum[] = { SVNRepository.INVALID_REVISION };
            SVNURL url = getEntryLocation(path, entry, revNum, SVNRevision.WORKING);
            SVNRepository repository = null;
            try {
                repository = createRepository(url, null, null, false);
                repository.assertServerIsMergeInfoCapable(path.toString());
            } finally {
                repository.closeSession();
            }
            
            SVNURL reposRoot = getReposRoot(path, null, pegRevision, adminArea, wcAccess);
            if (repositoryRoot != null && repositoryRoot.length > 0) {
                repositoryRoot[0] = reposRoot;
            }
            
            boolean[] indirect = { false };
            return getWCOrRepositoryMergeInfo(path, entry, SVNMergeInfoInheritance.INHERITED, indirect, false, 
                    null);
        } finally {
            wcAccess.close();
        }
    }

    protected Map getMergeInfo(SVNURL url, SVNRevision pegRevision, SVNURL repositoryRoot[]) throws SVNException {
        SVNRepository repository = null;        
        try {
            repository = createRepository(url, null, null, true); 
            long revisionNum = getRevisionNumber(pegRevision, repository, null);
            SVNURL reposRoot = repository.getRepositoryRoot(true);
            if (repositoryRoot != null && repositoryRoot.length > 0) {
                repositoryRoot[0] = reposRoot;
            }
            String relPath = getPathRelativeToSession(url, null, repository);
            if (relPath == null) {
                repository.setLocation(url, false);
                relPath = "";
            }
            return getReposMergeInfo(repository, relPath, revisionNum, 
                    SVNMergeInfoInheritance.INHERITED, false);
        } finally {
            if (repository != null) {
                repository.closeSession();
            }
        }
    }
    
    protected void runPeggedMerge(SVNURL srcURL, File srcPath, Collection rangesToMerge, 
    		SVNRevision pegRevision, File targetWCPath, SVNDepth depth, boolean dryRun, 
            boolean force, boolean ignoreAncestry, boolean recordOnly) throws SVNException {
        if (rangesToMerge == null || rangesToMerge.isEmpty()) {
        	return;
        }
        
    	myWCAccess = createWCAccess();
        targetWCPath = targetWCPath.getAbsoluteFile();
        try {
            SVNAdminArea adminArea = myWCAccess.probeOpen(targetWCPath, !dryRun, SVNWCAccess.INFINITE_DEPTH);
            SVNEntry targetEntry = myWCAccess.getVersionedEntry(targetWCPath, false);
            SVNURL url = srcURL == null ? getURL(srcPath) : srcURL;
            if (url == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                		"''{0}'' has no URL", srcPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        
            SVNURL wcReposRoot = getReposRoot(targetWCPath, null, SVNRevision.WORKING, adminArea, myWCAccess);
            List mergeSources = null;
            SVNRepository repository = null;
            SVNURL sourceReposRoot = null;
            try {
            	repository = createRepository(url, null, null, true);
            	sourceReposRoot = repository.getRepositoryRoot(true);
            	mergeSources = normalizeMergeSources(srcPath, url, sourceReposRoot, pegRevision, rangesToMerge, 
            			repository);
            } finally {
            	repository.closeSession();
            }
            
            doMerge(mergeSources, targetWCPath, targetEntry, adminArea, true, true, 
            		wcReposRoot.equals(sourceReposRoot), ignoreAncestry, force, dryRun, recordOnly, false, depth);
            
        } finally {
            myWCAccess.close();
        }
    }

    protected void runMerge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, 
            File targetWCPath, SVNDepth depth, boolean dryRun, boolean force, 
            boolean ignoreAncestry, boolean recordOnly) throws SVNException {
        
        if (!revision1.isValid() || !revision2.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                    "Not all required revisions are specified");            
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        
        SVNRepository repository1 = null;
        SVNRepository repository2 = null;
        myWCAccess = createWCAccess();
        targetWCPath = targetWCPath.getAbsoluteFile();
        try {
            SVNAdminArea adminArea = myWCAccess.probeOpen(targetWCPath, !dryRun, SVNWCAccess.INFINITE_DEPTH);

            SVNEntry entry = myWCAccess.getVersionedEntry(targetWCPath, false);
            SVNURL wcReposRoot = getReposRoot(targetWCPath, null, SVNRevision.WORKING, adminArea, myWCAccess);
                
            long[] latestRev = new long[1];
            latestRev[0] = SVNRepository.INVALID_REVISION;

            repository1 = createRepository(url1, null, null, false);
            SVNURL sourceReposRoot = repository1.getRepositoryRoot(true); 
            long rev1 = getRevisionNumber(revision1, latestRev, repository1, null); 

            repository2 = createRepository(url2, null, null, false);
            long rev2 = getRevisionNumber(revision2, latestRev, repository2, null); 
            
            boolean sameRepos = sourceReposRoot.equals(wcReposRoot);
            String youngestCommonPath = null;
            long youngestCommonRevision = SVNRepository.INVALID_REVISION;
            if (!ignoreAncestry) {
            	SVNLocationEntry youngestLocation = getYoungestCommonAncestor(null, url1, rev1, null, url2, 
            			rev2);
            	youngestCommonPath = youngestLocation.getPath();
            	youngestCommonRevision = youngestLocation.getRevision();
            }

            boolean related = false;
            boolean ancestral = false;
            List mergeSources = null;
            if (youngestCommonPath != null && SVNRevision.isValidRevisionNumber(youngestCommonRevision)) {
            	SVNRevisionRange range = null;
            	List ranges = new LinkedList();
            	related = true;
            	SVNURL youngestCommonURL = sourceReposRoot.appendPath(youngestCommonPath, false);

            	if (youngestCommonURL.equals(url2) && youngestCommonRevision == rev2) {
            		ancestral = true;
            		SVNRevision sRev = SVNRevision.create(rev1);
            		SVNRevision eRev = SVNRevision.create(youngestCommonRevision);
            		range = new SVNRevisionRange(sRev, eRev);
            		ranges.add(range);
            		mergeSources = normalizeMergeSources(null, url1, sourceReposRoot, sRev, 
            				ranges, repository1);
            	} else if (youngestCommonURL.equals(url1) && youngestCommonRevision == rev1) {
            		ancestral = true;
            		SVNRevision sRev = SVNRevision.create(youngestCommonRevision);
            		SVNRevision eRev = SVNRevision.create(rev2);
            		range = new SVNRevisionRange(sRev, eRev);
            		ranges.add(range);
            		mergeSources = normalizeMergeSources(null, url2, sourceReposRoot, eRev, 
            				ranges, repository2);
            	} else {
            		mergeCousinsAndSupplementMergeInfo(targetWCPath, entry, adminArea, repository1, url1, 
            				rev1, url2, rev2, youngestCommonRevision, sourceReposRoot, wcReposRoot, depth, 
            				ignoreAncestry, force, recordOnly, dryRun);
            		return;
            	}
            } else {
                MergeSource mergeSrc = new MergeSource();
                mergeSrc.myURL1 = url1;
                mergeSrc.myURL2 = url2;
                mergeSrc.myRevision1 = rev1;
                mergeSrc.myRevision2 = rev2;
                mergeSources = new LinkedList();
                mergeSources.add(mergeSrc);
            }

    		repository1.closeSession();
    		repository2.closeSession();
            
    		doMerge(mergeSources, targetWCPath, entry, adminArea, ancestral, related, sameRepos, 
    				ignoreAncestry, force, dryRun, recordOnly, false, depth);
        } finally {
        	if (repository1 != null) {
        		repository1.closeSession();
        	}
        	if (repository2 != null) {
        		repository2.closeSession();
        	}
        	try {
                myWCAccess.close();
            } catch (SVNException svne) {
                //
            }
         }
    }
    
    protected void runMergeReintegrate(SVNURL srcURL, File srcPath, SVNRevision pegRevision, 
            File targetWCPath, boolean dryRun) throws SVNException {
        myWCAccess = createWCAccess();
        targetWCPath = targetWCPath.getAbsoluteFile();
        try {
            SVNAdminArea adminArea = myWCAccess.probeOpen(targetWCPath, !dryRun, SVNWCAccess.INFINITE_DEPTH);
            SVNEntry targetEntry = myWCAccess.getVersionedEntry(targetWCPath, false);
            SVNURL url2 = srcURL == null ? getURL(srcPath) : srcURL;
            if (url2 == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                        "''{0}'' has no URL", srcPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        
            SVNURL wcReposRoot = getReposRoot(targetWCPath, null, SVNRevision.WORKING, adminArea, myWCAccess);
            SVNRepository repository = null;
            SVNURL sourceReposRoot = null;
            try {
                repository = createRepository(wcReposRoot, null, null, true);
                sourceReposRoot = repository.getRepositoryRoot(true);
                if (!wcReposRoot.equals(sourceReposRoot)) {
                    Object source = srcPath;
                    if (source == null) {
                        source = srcURL;
                    }
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_UNRELATED_RESOURCES, 
                            "''{0}'' must be from the same repository as ''{1}''", new Object[] { source,  
                            targetWCPath });
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                
                ensureWCReflectsRepositorySubTree(targetWCPath);
                long[] rev1 = { targetEntry.getRevision() };
                String sourceReposRelPath = getPathRelativeToRoot(null, url2, null, null, repository);
                String targetReposRelPath = getPathRelativeToRoot(targetWCPath, null, wcReposRoot, null, 
                        repository);
                
                SubTreeMergeInfoHandler handler = new SubTreeMergeInfoHandler(targetWCPath, wcReposRoot); 
                myWCAccess.walkEntries(targetWCPath, handler, true, SVNDepth.INFINITY);
                
                long rev2 = getRevisionNumber(pegRevision, repository, srcPath);
                SVNURL[] url1 = { null };
                
                Map unmergedToSourceMergeInfoCatalog = calculateLeftHandSide(url1, rev1, targetReposRelPath, 
                        handler.getSubTreesWithMergeInfoPaths(), rev1[0], sourceReposRelPath, sourceReposRoot, rev2, repository);
                
                SVNLocationEntry youngestCommonAncestor = getYoungestCommonAncestor(null, url2, rev2, null, url1[0], 
                        rev1[0]);
                
                String youngestAncestorPath = youngestCommonAncestor.getPath();
                long youngestAncestorRevision = youngestCommonAncestor.getRevision();
                if (!(youngestAncestorPath != null && SVNRevision.isValidRevisionNumber(youngestAncestorRevision))) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE, 
                            "''{0}@{1}'' must be ancestrally related to ''{2}@{3}''", 
                            new Object[] { url1[0], new Long(rev1[0]), url2, new Long(rev2)});
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                
                if (rev1[0] > youngestAncestorRevision) {
                    try {
                        ensureAllMissingRangesArePhantoms(repository, unmergedToSourceMergeInfoCatalog);
                    } catch (SVNException svne) {
                        if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.CLIENT_NOT_READY_TO_MERGE) {
                            String srcMergeInfoCatalogString = SVNMergeInfoUtil.formatMergeInfoCatalogToString(unmergedToSourceMergeInfoCatalog, 
                                    "  ", "    Missing ranges: ");
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE, 
                                    "Reintegrate can only be used if revisions {0} through {1} were " + 
                                    "previously merged from {2} to the reintegrate source, but this is not the case:\n{3}", new Object[] { 
                                    String.valueOf(youngestAncestorRevision + 1), String.valueOf(rev2), targetEntry.getURL(), 
                                    srcMergeInfoCatalogString });
                            SVNErrorManager.error(err, SVNLogType.WC);
                        }
                        throw svne;
                    }
                }

                mergeCousinsAndSupplementMergeInfo(targetWCPath, targetEntry, adminArea, repository, url1[0], 
                        rev1[0], url2, rev2, youngestAncestorRevision, sourceReposRoot, wcReposRoot, 
                        SVNDepth.INFINITY, false, false, false, dryRun);

            } finally {
                repository.closeSession();
            }
            
        } finally {
            myWCAccess.close();
        }
    }

    protected void doMerge(List mergeSources, File target, SVNEntry targetEntry, SVNAdminArea adminArea, 
            boolean sourcesAncestral, boolean sourcesRelated, boolean sameRepository, boolean ignoreAncestry, 
            boolean force, boolean dryRun, boolean recordOnly, boolean reintegrateMerge, SVNDepth depth) throws SVNException {
        
        if (recordOnly) {
            if (!sourcesAncestral) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS,
                        "Use of two URLs is not compatible with mergeinfo modification"); 
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
            if (!sameRepository) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS,
                        "Merge from foreign repository is not compatible with mergeinfo modification");
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (dryRun) {
                return;
            }
        }
        
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }
        
        myIsForce = force;
        myIsDryRun = dryRun;
        myIsRecordOnly = recordOnly;
        myIsIgnoreAncestry = ignoreAncestry;
        myIsSameRepository = sameRepository;
        myIsMergeInfoCapable = false;
        myAreSourcesAncestral = sourcesAncestral;
        myIsTargetMissingChild = false;
        myIsSingleFileMerge = false;
        myTarget = target;
        myNotificationsNumber = 0;
        myOperativeNotificationsNumber = 0;
        myCurrentAncestorIndex = -1;
        myMergedPaths = null;
        mySkippedPaths = null;
        myAddedPaths = null;
        myChildrenWithMergeInfo = null;
        myPathsWithNewMergeInfo = null;
        myHasExistingMergeInfo = false;
        myIsReIntegrateMerge = reintegrateMerge;
        myImplicitSrcGap = null;
        
        boolean checkedMergeInfoCapability = false;
        for (int i = 0; i < mergeSources.size(); i++) {
            MergeSource mergeSource = (MergeSource) mergeSources.get(i);
            SVNURL url1 = mergeSource.myURL1;
            SVNURL url2 = mergeSource.myURL2;
            long revision1 = mergeSource.myRevision1;
            long revision2 = mergeSource.myRevision2;
            if (revision1 == revision2 && mergeSource.myURL1.equals(mergeSource.myURL2)) {
                continue;
            }
            
            try {
                myRepository1 = ensureRepository(myRepository1, url1);
                myRepository2 = ensureRepository(myRepository2, url2);
                myIsTargetHasDummyMergeRange = false;
                myURL = url2;
                myConflictedPaths = null;
                myDryRunDeletions = dryRun ? new SVNHashMap() : null;
                myIsAddNecessitatedMerge = false;
                myCurrentMergeSource = mergeSource;
                
                if (!checkedMergeInfoCapability) {
                	myIsMergeInfoCapable = myRepository1.hasCapability(SVNCapability.MERGE_INFO);
                	checkedMergeInfoCapability = true;
                }
                
                if (targetEntry.isFile()) {
                    doFileMerge(url1, revision1, url2, revision2, target, adminArea, sourcesRelated);
                } else if (targetEntry.isDirectory()) {
                    doDirectoryMerge(url1, revision1, url2, revision2, targetEntry, adminArea, depth);
                }
                
                if (!dryRun) {
                    elideMergeInfo(myWCAccess, target, targetEntry, null);
                }
                SVNEvent mergeCompletedEvent = SVNEventFactory.createSVNEvent(target, SVNNodeKind.NONE, null, SVNRepository.INVALID_REVISION, 
                        SVNStatusType.INAPPLICABLE, SVNStatusType.INAPPLICABLE, SVNStatusType.LOCK_INAPPLICABLE, SVNEventAction.MERGE_COMPLETE, 
                        null, null, null); 
                super.handleEvent(mergeCompletedEvent, ISVNEventHandler.UNKNOWN);
            } finally {
                if (myRepository1 != null) {
                    myRepository1.closeSession();
                }
                if (myRepository2 != null) {
                    myRepository2.closeSession();
                }
            }
        }
    }
    
    protected void addPathWithNewMergeInfo(File path) {
        if (myPathsWithNewMergeInfo == null) {
            myPathsWithNewMergeInfo = new LinkedList();
        }
        myPathsWithNewMergeInfo.add(path);
    }

    protected void addPathWithDeletedMergeInfo(File path) {
        if (myPathsWithDeletedMergeInfo == null) {
            myPathsWithDeletedMergeInfo = new LinkedList();
        }
        myPathsWithDeletedMergeInfo.add(path);
    }
    
    protected SVNRepository ensureRepository(SVNRepository repository, SVNURL url) throws SVNException {
        if (repository != null) {
            try {
                ensureSessionURL(repository, url);
                return repository;
            } catch (SVNException e) {
                //
            }
            repository = null;
        }
        if (repository == null) {
            repository = createRepository(url, null, null, false);
        }
        return repository; 
    }

    public Object[] calculateRemainingRangeList(File targetFile, SVNEntry entry, SVNURL sourceRoot, boolean[] indirect,
                                 SVNURL url1, long revision1, SVNURL url2, long revision2,
                                 SVNMergeRange range) throws SVNException {
        SVNMergeRangeList remainingRangeList = null;
        Map targetMergeInfo = null;
        Map implicitMergeInfo = null;
        boolean honorMergeInfo = isHonorMergeInfo();
        if (honorMergeInfo) {
            MergePath mergeTarget = new MergePath();

            myRepository1.setLocation(entry.getSVNURL(), false);
            Map[] fullMergeInfo = getFullMergeInfo(entry, indirect, SVNMergeInfoInheritance.INHERITED,
                    myRepository1, targetFile, Math.max(revision1, revision2), Math.min(revision1, revision2));
            targetMergeInfo = fullMergeInfo[0];
            implicitMergeInfo = fullMergeInfo[1];
            myRepository1.setLocation(url1, false);

            if (!myIsRecordOnly) {
                calculateRemainingRanges(null, mergeTarget, sourceRoot, url1, revision1, url2, revision2,
                        targetMergeInfo, implicitMergeInfo, myImplicitSrcGap, false, entry, myRepository1);
                remainingRangeList = mergeTarget.myRemainingRanges;
            }
        }

        if (!honorMergeInfo || myIsRecordOnly) {
            remainingRangeList = new SVNMergeRangeList(range);
        }
        return new Object[]{remainingRangeList, targetMergeInfo, implicitMergeInfo};
    }
    
    protected void findGapsInMergeSourceHistory(long[] gap, String mergeSrcCanonPath, SVNURL url1, long rev1, SVNURL url2, long rev2, SVNRepository repos) throws SVNException {
        long youngRev = Math.max(rev1, rev2);
        long oldRev = Math.min(rev1, rev2);
        SVNURL url = rev2 < rev1 ? url1 : url2;
        gap[0] = gap[1] = -1;
        SVNRevision pegRevision = SVNRevision.create(youngRev);
        
        SVNURL oldURL = null;
        if (repos != null) {
            oldURL = ensureSessionURL(repos, url);            
        }
        Map implicitSrcMergeInfo = null;
        try {
           implicitSrcMergeInfo = getHistoryAsMergeInfo(url, null, pegRevision, youngRev, oldRev, repos, null);
        } finally {
            if (repos != null && oldURL != null) {
                repos.setLocation(oldURL, false);
            }
        }
        SVNMergeRangeList rangelist = (SVNMergeRangeList) implicitSrcMergeInfo.get(mergeSrcCanonPath);
        if (rangelist != null) {
            if (rangelist.getSize() > 1) {
                gap[0] = Math.min(rev1, rev2);
                gap[1] = rangelist.getRanges()[rangelist.getSize() - 1].getStartRevision();
            } else if (implicitSrcMergeInfo.size() > 1) {
                SVNMergeRangeList implicitMergeRangeList = new SVNMergeRangeList(new SVNMergeRange[0]);
                SVNMergeRangeList requestedMergeRangeList = new SVNMergeRangeList(Math.min(rev1, rev2), Math.max(rev1, rev2), true);
                for(Iterator paths = implicitSrcMergeInfo.keySet().iterator(); paths.hasNext();) {
                    String path = (String) paths.next();
                    rangelist = (SVNMergeRangeList) implicitSrcMergeInfo.get(path);
                    implicitMergeRangeList = implicitMergeRangeList != null ? implicitMergeRangeList.merge(rangelist) : rangelist;
                }
                SVNMergeRangeList gapRangeList = requestedMergeRangeList.diff(implicitMergeRangeList, false);
                if (gapRangeList.getSize() > 0) {
                    gap[0] = gapRangeList.getRanges()[0].getStartRevision();
                    gap[1] = gapRangeList.getRanges()[0].getEndRevision();
                }
            }
        }
    }

    protected void doFileMerge(SVNURL url1, long revision1, SVNURL url2, long revision2,
            File targetWCPath, SVNAdminArea adminArea, boolean sourcesRelated) throws SVNException {
        boolean isRollBack = revision1 > revision2;
        SVNURL primaryURL = isRollBack ? url1 : url2;
        boolean honorMergeInfo = isHonorMergeInfo();
        boolean recordMergeInfo = isRecordMergeInfo();
        myIsSingleFileMerge = true;
        boolean[] indirect = { false };
        boolean[] isTreeConflict = { false };
        Map targetMergeInfo;
        Map implicitMergeInfo;
        SVNURL sourceRootURL = null;
        String mergeInfoPath = null;
        SVNMergeRange conflictedRange = null;
        
        myWCAccess.probeTry(targetWCPath, true, SVNWCAccess.INFINITE_DEPTH);
        SVNEntry entry = myWCAccess.getVersionedEntry(targetWCPath, false);

        if (honorMergeInfo) {
            sourceRootURL = myRepository1.getRepositoryRoot(true);
            mergeInfoPath = getPathRelativeToRoot(null, primaryURL, sourceRootURL, null, null);            
        }
        SVNMergeRange range = new SVNMergeRange(revision1, revision2, true);

        Object[] mergeInfoBundle = calculateRemainingRangeList(targetWCPath, entry, sourceRootURL, indirect,
                url1, revision1, url2, revision2, range);
        SVNMergeRangeList remainingRangeList = (SVNMergeRangeList) mergeInfoBundle[0];
        targetMergeInfo = (Map) mergeInfoBundle[1];
        implicitMergeInfo = (Map) mergeInfoBundle[1];

        SVNMergeRange[] remainingRanges = remainingRangeList.getRanges();
        AbstractDiffCallback callback = getMergeCallback(adminArea);

        String targetName = targetWCPath.getName();
        if (!myIsRecordOnly) {
            SVNMergeRangeList rangeListToMerge = remainingRangeList;
            if (myAreSourcesAncestral && remainingRangeList.getSize() > 1) {
                SVNURL oldSessionURL = ensureSessionURL(myRepository1, primaryURL);
                rangeListToMerge = removeNoOpMergeRanges(myRepository1, remainingRangeList);
                if (oldSessionURL != null) {
                    myRepository1.setLocation(oldSessionURL, false);
                }
            }
            
            SVNMergeRange[] rangesToMerge = rangeListToMerge.getRanges();
            
            for (int i = 0; i < rangesToMerge.length; i++) {
                SVNMergeRange nextRange = rangesToMerge[i];
                boolean headerSent = false;
                SVNEvent event = SVNEventFactory.createSVNEvent(targetWCPath, SVNNodeKind.UNKNOWN, null, 
                        SVNRepository.INVALID_REVISION, myIsSameRepository ? SVNEventAction.MERGE_BEGIN : SVNEventAction.FOREIGN_MERGE_BEGIN, null, null, 
                        myAreSourcesAncestral ? nextRange : null);

                SVNProperties props1 = new SVNProperties();
                SVNProperties props2 = new SVNProperties();
                File f1 = null;
                File f2 = null;

                String mimeType2;
                String mimeType1;
                SVNStatusType[] mergeResult;
                
                SVNRepository repos1 = myRepository1;
                SVNRepository repos2 = myRepository2;
                if (honorMergeInfo && !url1.equals(url2)) {
                    if (!isRollBack && nextRange.getStartRevision() != revision1) {
                        repos1 = repos2;
                    } else if (isRollBack && nextRange.getEndRevision() != revision2) {
                        repos2 = repos1;
                    }
                }

                try {
                    f1 = loadFile(repos1, nextRange.getStartRevision(), props1, adminArea);
                    f2 = loadFile(repos2, nextRange.getEndRevision(), props2, adminArea);

                    mimeType1 = props1.getStringValue(SVNProperty.MIME_TYPE);
                    mimeType2 = props2.getStringValue(SVNProperty.MIME_TYPE);
                    props1 = filterProperties(props1, true, false, false);
                    props2 = filterProperties(props2, true, false, false);

                    SVNProperties propsDiff = computePropsDiff(props1, props2);
                    
                    if (!(myIsIgnoreAncestry || sourcesRelated)) {
                        SVNStatusType cstatus = callback.fileDeleted(targetName, f1, f2, mimeType1, 
                                mimeType2, props1, isTreeConflict);
                        headerSent = notifySingleFileMerge(targetWCPath, isTreeConflict[0] ? SVNEventAction.TREE_CONFLICT : 
                            SVNEventAction.UPDATE_DELETE, cstatus, SVNStatusType.UNKNOWN, event, headerSent);
                        mergeResult = callback.fileAdded(targetName, f1, f2, nextRange.getStartRevision(), 
                                                         nextRange.getEndRevision(), mimeType1, mimeType2, 
                                                         props1, propsDiff, isTreeConflict);
                        headerSent = notifySingleFileMerge(targetWCPath, isTreeConflict[0] ? SVNEventAction.TREE_CONFLICT : 
                            SVNEventAction.UPDATE_ADD, mergeResult[0], mergeResult[1], event, headerSent);
                    } else {
                        mergeResult = callback.fileChanged(targetName, f1, f2, nextRange.getStartRevision(), 
                                                           nextRange.getEndRevision(), mimeType1, 
                                                           mimeType2, props1, propsDiff, isTreeConflict);
                        headerSent = notifySingleFileMerge(targetWCPath, isTreeConflict[0] ? SVNEventAction.TREE_CONFLICT : 
                            SVNEventAction.UPDATE_UPDATE, mergeResult[0], mergeResult[1], event, headerSent);
                    }
                } finally {
                    SVNFileUtil.deleteAll(f1, null);
                    SVNFileUtil.deleteAll(f2, null);
                }
                
                if (i < rangesToMerge.length - 1 && myConflictedPaths != null && !myConflictedPaths.isEmpty()) {
                    conflictedRange = nextRange;
                    break;
                }
            }
        }
        
        if (recordMergeInfo && remainingRanges.length > 0) {
            SVNMergeRangeList filteredRangeList = filterNaturalHistoryFromMergeInfo(mergeInfoPath, 
                    implicitMergeInfo, range);
            if (!filteredRangeList.isEmpty() && (mySkippedPaths == null || mySkippedPaths.isEmpty())) {
                if (indirect[0]) {
                    SVNPropertiesManager.recordWCMergeInfo(targetWCPath, targetMergeInfo, myWCAccess);
                }
                Map merges = new TreeMap();
                merges.put(targetWCPath, filteredRangeList);
                updateWCMergeInfo(targetWCPath, mergeInfoPath, entry, merges, isRollBack);
            }
        }

        sleepForTimeStamp();

        if (conflictedRange != null) {
            SVNErrorMessage error = makeMergeConflictError(targetWCPath, conflictedRange);
            SVNErrorManager.error(error, SVNLogType.WC);
        }
    }

    protected void doMergeInfoUnawareDirectoryMerge(SVNURL url1, long revision1, SVNURL url2, long revision2,
            SVNAdminArea adminArea, SVNDepth depth) throws SVNException {
        File targetWCPath = adminArea.getRoot();
        boolean isRollBack = revision1 > revision2;

        MergePath item = new MergePath(targetWCPath);
        SVNMergeRange itemRange = new SVNMergeRange(revision1, revision2, true);
        item.myRemainingRanges = new SVNMergeRangeList(itemRange);
        myChildrenWithMergeInfo.add(item);

        driveMergeReportEditor(targetWCPath, url1, revision1, url2, revision2, null, isRollBack, 
                depth, adminArea, getMergeCallback(adminArea), null);
        
    }
    
    protected void recordMergeInfoForDirectoryMerge(SVNEntry targetEntry, SVNMergeRange range, String mergeInfoPath, SVNDepth depth) throws SVNException {
        boolean isRollBack = range.getStartRevision() > range.getEndRevision();
        removeAbsentChildren(myTarget, myChildrenWithMergeInfo);
        for (int i = 0; i < myChildrenWithMergeInfo.size(); i++) {
            MergePath child = (MergePath) myChildrenWithMergeInfo.get(i);
            if (child == null || child.myIsAbsent) {
                continue;
            }
            
            String childReposPath = null;
            if (child.myPath.equals(myTarget)) {
                childReposPath = ""; 
            } else {
                childReposPath = SVNPathUtil.getRelativePath(myTarget.getAbsolutePath(), 
                        child.myPath.getAbsolutePath());
            }
            
            SVNEntry childEntry = myWCAccess.getVersionedEntry(child.myPath, false);
            String childMergeSourcePath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(mergeInfoPath, 
                    childReposPath));
            
            SVNMergeRangeList childMergeRangeList = filterNaturalHistoryFromMergeInfo(childMergeSourcePath, 
                    child.myImplicitMergeInfo, range);
            if (childMergeRangeList.isEmpty()) {
                continue;
            } 
            if (i == 0) {
                recordSkips(mergeInfoPath, targetEntry, isRollBack);
            }
            calculateMergeInheritance(childMergeRangeList, childEntry, i == 0, child.myHasMissingChildren, depth);
            if (child.myIsIndirectMergeInfo) {
                SVNPropertiesManager.recordWCMergeInfo(child.myPath, child.myPreMergeMergeInfo, 
                        myWCAccess);
            }
            if (myImplicitSrcGap != null) {
                if (isRollBack) {
                    childMergeRangeList.reverse();
                }
                childMergeRangeList = childMergeRangeList.diff(myImplicitSrcGap, false);
                if (isRollBack) {
                    childMergeRangeList.reverse();
                }
            }
            Map childMerges = new TreeMap();
            childMerges.put(child.myPath, childMergeRangeList);
            updateWCMergeInfo(child.myPath, childMergeSourcePath, childEntry, childMerges, isRollBack);

            if (i > 0) {
                boolean isInSwitchedSubTree = false;
                if (child.myIsSwitched) {
                    isInSwitchedSubTree = true;
                } else if (i > 1) {
                    for (int j = i - 1; j > 0; j--) {
                        MergePath parent = (MergePath) myChildrenWithMergeInfo.get(j);
                        if (parent != null && parent.myIsSwitched && 
                                SVNPathUtil.isAncestor(parent.myPath.getAbsolutePath().replace(File.separatorChar, '/'), 
                                child.myPath.getAbsolutePath().replace(File.separatorChar, '/'))) {
                            isInSwitchedSubTree = true;
                            break;
                        }
                    }
                }
                
                elideMergeInfo(myWCAccess, child.myPath, childEntry, isInSwitchedSubTree ? null : myTarget);
            }
        }
    }

    protected void recordMergeInfoForAddedSubtrees(SVNMergeRange range, String mergeInfoPath, SVNDepth depth) throws SVNException {
        if (myAddedPaths != null) {
            for (Iterator addedPathsIter = myAddedPaths.iterator(); addedPathsIter.hasNext();) {
                File addedPath = (File) addedPathsIter.next();
                SVNPropertyValue addedPathParentPropValue = SVNPropertiesManager.getProperty(myWCAccess, 
                        addedPath.getParentFile(), SVNProperty.MERGE_INFO);
                String addedPathParentPropValueStr = addedPathParentPropValue != null ? 
                        addedPathParentPropValue.getString() : null;
                if (addedPathParentPropValueStr != null && 
                        addedPathParentPropValueStr.indexOf(SVNMergeRangeList.MERGE_INFO_NONINHERITABLE_STRING) != -1) {
                    SVNEntry entry = myWCAccess.getVersionedEntry(addedPath, false);
                    Map mergeMergeInfo = new TreeMap();
                    MergePath targetMergePath = (MergePath) myChildrenWithMergeInfo.get(0);
                    
                    SVNMergeRange rng = range.dup();
                    if (entry.isFile()) {
                        rng.setInheritable(true);
                    } else {
                        rng.setInheritable(!(depth == SVNDepth.INFINITY || depth == SVNDepth.IMMEDIATES));
                    }

                    String addedPathStr = SVNPathUtil.validateFilePath(addedPath.getAbsolutePath());
                    String targetMergePathStr = SVNPathUtil.validateFilePath(targetMergePath.myPath.getAbsolutePath());
                    String commonAncestorPath = SVNPathUtil.getCommonPathAncestor(addedPathStr, targetMergePathStr);
                    String relativeAddedPath = SVNPathUtil.getRelativePath(commonAncestorPath, addedPathStr);
                    if (relativeAddedPath.startsWith("/")) {
                        relativeAddedPath = relativeAddedPath.substring(1);
                    }
                    
                    SVNMergeRangeList rangeList = new SVNMergeRangeList(rng);
                    mergeMergeInfo.put(SVNPathUtil.getAbsolutePath(SVNPathUtil.append(mergeInfoPath, 
                            relativeAddedPath)), rangeList);
                    boolean[] inherited = { false };
                    Map addedPathMergeInfo = getWCMergeInfo(addedPath, entry, null, 
                            SVNMergeInfoInheritance.EXPLICIT, false, inherited);
                    if (addedPathMergeInfo != null) {
                        mergeMergeInfo = SVNMergeInfoUtil.mergeMergeInfos(mergeMergeInfo, addedPathMergeInfo);
                    }
                    SVNPropertiesManager.recordWCMergeInfo(addedPath, mergeMergeInfo, myWCAccess);
                }
            }
        }
    }
    
    protected void doDirectoryMerge(SVNURL url1, long revision1, SVNURL url2, long revision2,
    		SVNEntry parentEntry, SVNAdminArea adminArea, SVNDepth depth) throws SVNException {        
    	boolean isRollBack = revision1 > revision2;
    	SVNURL primaryURL = isRollBack ? url1 : url2;
    	boolean honorMergeInfo = isHonorMergeInfo();
    	boolean recordMergeInfo = isRecordMergeInfo();
    	boolean sameURLs = url1.equals(url2);

    	SVNMergeCallback mergeCallback = getMergeCallback(adminArea);
    	
    	myChildrenWithMergeInfo = new LinkedList();
    	if (!honorMergeInfo) {
    	    doMergeInfoUnawareDirectoryMerge(url1, revision1, url2, revision2, adminArea, depth);
    	    return;
    	}
    	
    	SVNRepository repository = isRollBack ? myRepository1 : myRepository2;
    	SVNURL sourceRootURL = repository.getRepositoryRoot(true);
    	String mergeInfoPath = getPathRelativeToRoot(null, primaryURL, sourceRootURL, null, null);
    	myChildrenWithMergeInfo = getMergeInfoPaths(myChildrenWithMergeInfo, mergeInfoPath, parentEntry, 
    			sourceRootURL, revision1, revision2, honorMergeInfo, repository, depth);

    	MergePath targetMergePath = (MergePath) myChildrenWithMergeInfo.get(0);
        myIsTargetMissingChild = targetMergePath.myHasMissingChildren;
        
        populateRemainingRanges(myChildrenWithMergeInfo, sourceRootURL, url1, revision1, url2, revision2, honorMergeInfo, repository, mergeInfoPath);
        
        SVNMergeRange range = new SVNMergeRange(revision1, revision2, true);
        ISVNReusableEditor editor = null;
        SVNErrorMessage err = null;
        if (honorMergeInfo && !myIsRecordOnly) {
        	long startRev = getMostInclusiveStartRevision(myChildrenWithMergeInfo, isRollBack);
        	if (SVNRevision.isValidRevisionNumber(startRev)) {
        		range.setStartRevision(startRev);
                long endRev = getMostInclusiveEndRevision(myChildrenWithMergeInfo, isRollBack);
                while (SVNRevision.isValidRevisionNumber(endRev)) {
                    SVNURL realURL1 = url1;
                    SVNURL realURL2 = url2;
                    SVNURL oldURL1 = null;
                    SVNURL oldURL2 = null;
                    long nextEndRev = SVNRepository.INVALID_REVISION;
                    
                    sliceRemainingRanges(myChildrenWithMergeInfo, isRollBack, endRev);
                    myCurrentAncestorIndex = -1;
                    if (!sameURLs) {
                        if (isRollBack && endRev != revision2) {
                            realURL2 = url1;
                            oldURL2 = ensureSessionURL(myRepository2, realURL2);
                        }
                        if (!isRollBack && startRev != revision1) {
                            realURL1 = url2;
                            oldURL1 = ensureSessionURL(myRepository1, realURL1);
                        }
                    }
                    
                    try {
                        editor = driveMergeReportEditor(myTarget, realURL1, startRev, realURL2, endRev, 
                                myChildrenWithMergeInfo, isRollBack, depth, adminArea, mergeCallback, editor);
                    } finally {
                        if (oldURL1 != null) {
                            myRepository1.setLocation(oldURL1, false);
                        }
                        if (oldURL2 != null) {
                            myRepository2.setLocation(oldURL2, false);
                        }
                    }
                    
                    processChildrenWithNewMergeInfo();
                    processChildrenWithDeletedMergeInfo();
                    
                    removeFirstRangeFromRemainingRanges(endRev, myChildrenWithMergeInfo);
                    nextEndRev = getMostInclusiveEndRevision(myChildrenWithMergeInfo, isRollBack);
                    if (SVNRevision.isValidRevisionNumber(nextEndRev) && myConflictedPaths != null && 
                            !myConflictedPaths.isEmpty() && !myIsDryRun) {
                        SVNMergeRange conflictedRange = new SVNMergeRange(startRev, endRev, false);
                        err = makeMergeConflictError(myTarget, conflictedRange);
                        range.setEndRevision(endRev);
                        break;
                    }
                    startRev = getMostInclusiveStartRevision(myChildrenWithMergeInfo, isRollBack);
                    endRev = nextEndRev;
                }
        	}
        } else {
        	if (!myIsRecordOnly) {
                myCurrentAncestorIndex = -1;
                editor = driveMergeReportEditor(myTarget, url1, revision1, url2, revision2, null, isRollBack, 
                        depth, adminArea, mergeCallback, editor);
        	}
        }

        if (recordMergeInfo) {
            recordMergeInfoForDirectoryMerge(parentEntry, range, mergeInfoPath, depth);
            recordMergeInfoForAddedSubtrees(range, mergeInfoPath, depth);
        }
        
        if (err != null) {
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }

    protected SVNProperties filterSelfReferentialMergeInfo(SVNProperties props, File path) throws SVNException {
        boolean honorMergeInfo = isHonorMergeInfo();
        if (!honorMergeInfo && myIsSameRepository && !myIsReIntegrateMerge) {
            return null;
        }
        SVNEntry targetEntry = myWCAccess.getVersionedEntry(path, false);
        if (myIsSameRepository && (targetEntry.isScheduledForAddition() || targetEntry.isScheduledForReplacement())) {
            return null;
        }
        SVNProperties adjustedProperties = new SVNProperties();
        for (Iterator propNamesIter = props.nameSet().iterator(); propNamesIter.hasNext();) {
            String propName = (String) propNamesIter.next();
            SVNPropertyValue propValue = props.getSVNPropertyValue(propName);
            if (SVNProperty.MERGE_INFO.equals(propName) && !myIsSameRepository) { 
                // skip mergeinfo from foreign repository
            } else if (!SVNProperty.MERGE_INFO.equals(propName) || propValue == null ||
                    "".equals(propValue.getString())) {
                adjustedProperties.put(propName, propValue);
            } else {
                SVNURL mergeSourceRootURL = myRepository2.getRepositoryRoot(true);
                SVNURL targetURL = getURL(path);
                SVNURL oldURL = ensureSessionURL(myRepository2, targetURL);
                
                Map filteredYoungerMergeInfo = null;
                Map mergeInfo = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(propValue.getString()), null);
                
                Map[] mergeInfoArr = { mergeInfo };
                Map youngerMergeInfo = splitMergeInfoOnRevision(mergeInfoArr, targetEntry.getRevision());
                mergeInfo = mergeInfoArr[0];
                
                if (youngerMergeInfo != null) {
                    for (Iterator youngerMergeInfoIter = youngerMergeInfo.keySet().iterator(); youngerMergeInfoIter.hasNext();) {
                        String sourcePath = (String) youngerMergeInfoIter.next();
                        SVNMergeRangeList rangeList = (SVNMergeRangeList) youngerMergeInfo.get(sourcePath);
                        SVNMergeRange ranges[] = rangeList.getRanges();
                        LinkedList adjustedRanges = new LinkedList();
                        SVNURL mergeSourceURL = mergeSourceRootURL.appendPath(sourcePath, false);
                        for (int i = 0; i < ranges.length; i++) {
                            SVNMergeRange range = ranges[i];
                            SVNRepositoryLocation[] locations = null;
                            try {
                                locations = getLocations(targetURL, null, myRepository2, 
                                        SVNRevision.create(targetEntry.getRevision()), 
                                        SVNRevision.create(range.getStartRevision() + 1), SVNRevision.UNDEFINED);
                                SVNURL startURL = locations[0].getURL();
                                if (!mergeSourceURL.equals(startURL)) {
                                    adjustedRanges.add(range);
                                }
                            } catch (SVNException svne) {
                                SVNErrorCode code = svne.getErrorMessage().getErrorCode();
                                if (code == SVNErrorCode.CLIENT_UNRELATED_RESOURCES || 
                                        code == SVNErrorCode.RA_DAV_PATH_NOT_FOUND ||
                                        code == SVNErrorCode.FS_NOT_FOUND ||
                                        code == SVNErrorCode.FS_NO_SUCH_REVISION) {
                                    adjustedRanges.add(range);
                                } else {
                                    throw svne;
                                }
                            }
                        }

                        if (!adjustedRanges.isEmpty()) {
                            if (filteredYoungerMergeInfo == null) {
                                filteredYoungerMergeInfo = new TreeMap();
                            }
                            SVNMergeRangeList adjustedRangeList = SVNMergeRangeList.fromCollection(adjustedRanges); 
                            filteredYoungerMergeInfo.put(sourcePath, adjustedRangeList);
                        }
                    }
                }
                
                Map filteredMergeInfo = null;
                if (mergeInfo != null && !mergeInfo.isEmpty()) {
                    Map implicitMergeInfo = getHistoryAsMergeInfo(null, path, SVNRevision.create(targetEntry.getRevision()), 
                            targetEntry.getRevision(), SVNRepository.INVALID_REVISION, myRepository2, myWCAccess);
                    
                    filteredMergeInfo = SVNMergeInfoUtil.removeMergeInfo(implicitMergeInfo, mergeInfo);
                }
                
                if (oldURL != null) {
                    myRepository2.setLocation(oldURL, false);
                }
                
                if (filteredMergeInfo != null && filteredYoungerMergeInfo != null) {
                    filteredMergeInfo = SVNMergeInfoUtil.mergeMergeInfos(filteredMergeInfo, filteredYoungerMergeInfo);
                    
                } else if (filteredYoungerMergeInfo != null) {
                    filteredMergeInfo = filteredYoungerMergeInfo;
                }

                if (filteredMergeInfo != null && !filteredMergeInfo.isEmpty()) {
                    String filteredMergeInfoStr = SVNMergeInfoUtil.formatMergeInfoToString(filteredMergeInfo, null);
                    adjustedProperties.put(SVNProperty.MERGE_INFO, filteredMergeInfoStr);
                }
            }
        }
        return adjustedProperties;
    }

    protected SVNLogClient getLogClient() {
        if (myLogClient == null) {
            myLogClient = new SVNLogClient(getRepositoryPool(), getOptions());
        }
        return myLogClient;
    }
    
    protected void recordTreeConflict(File victim, SVNAdminArea adminArea, SVNNodeKind kind, SVNConflictAction action, 
            SVNConflictReason reason) throws SVNException {
        if (myIsRecordOnly || myIsDryRun) {
            return;
        }
        
        SVNTreeConflictDescription conflict = makeTreeConflict(victim, kind, action, reason);
        adminArea.addTreeConflict(conflict);
    }

    protected void recordTreeConflictOnAdd(File victim, SVNAdminArea adminArea, SVNNodeKind kind, SVNConflictAction action, 
            SVNConflictReason reason) throws SVNException {
        if (myIsRecordOnly || myIsDryRun) {
            return;
        }
        
        SVNTreeConflictDescription conflict = makeTreeConflict(victim, kind, action, reason);
        SVNTreeConflictDescription existingConflict = myWCAccess.getTreeConflict(conflict.getPath());
        
        if (existingConflict != null && existingConflict.getConflictAction() == SVNConflictAction.DELETE && conflict.getConflictAction() == SVNConflictAction.ADD) {
            adminArea.deleteTreeConflict(conflict.getPath().getName());
            conflict = new SVNTreeConflictDescription(conflict.getPath(), conflict.getNodeKind(), SVNConflictAction.DELETE, existingConflict.getConflictReason(), 
                    conflict.getOperation(), existingConflict.getSourceLeftVersion(), conflict.getSourceRightVersion());
        }
        adminArea.addTreeConflict(conflict);
    }
        
    protected SVNTreeConflictDescription makeTreeConflict(File victim, SVNNodeKind kind, SVNConflictAction action, SVNConflictReason reason) throws SVNException {
        SVNURL srcReposRoot = myRepository1.getRepositoryRoot(true);
        String child = SVNPathUtil.getRelativePath(myTarget.getAbsolutePath(), victim.getAbsolutePath());
        SVNURL leftURL = null;
        SVNURL rightURL = null;
        if (child != null) {
            leftURL = myCurrentMergeSource.myURL1.appendPath(child, false);
            rightURL = myCurrentMergeSource.myURL2.appendPath(child, false);
        } else {
            leftURL = myCurrentMergeSource.myURL1;
            rightURL = myCurrentMergeSource.myURL2;
        }
        
        SVNConflictVersion leftConflictVersion = new SVNConflictVersion(srcReposRoot, SVNURLUtil.getRelativeURL(srcReposRoot, leftURL, false),
                myCurrentMergeSource.myRevision1, kind);
        SVNConflictVersion rightConflictVersion = new SVNConflictVersion(srcReposRoot, SVNURLUtil.getRelativeURL(srcReposRoot, rightURL, false),
                myCurrentMergeSource.myRevision2, kind);
        SVNTreeConflictDescription conflictDescription  = new SVNTreeConflictDescription(victim, kind, action, reason, SVNOperation.MERGE, 
                leftConflictVersion, rightConflictVersion);
        return conflictDescription; 
        
    }
    
    private SVNMergeCallback getMergeCallback(SVNAdminArea adminArea) {
        return myWCAccess.createMergeCallback(this, adminArea, myURL, getMergeOptions(), myConflictedPaths, myIsForce, myIsDryRun);
    }

    private void processChildrenWithNewMergeInfo() throws SVNException {
        if (myPathsWithNewMergeInfo != null && !myIsDryRun) {
            for (Iterator pathsIter = myPathsWithNewMergeInfo.iterator(); pathsIter.hasNext();) {
                File pathWithNewMergeInfo = (File) pathsIter.next();
                SVNEntry pathEntry = myWCAccess.getVersionedEntry(pathWithNewMergeInfo, false);
                boolean[] indirect = { false };
                Map pathExplicitMergeInfo = getWCMergeInfo(pathWithNewMergeInfo, pathEntry, null, SVNMergeInfoInheritance.EXPLICIT, false, 
                        indirect);
                
                SVNURL oldURL = null;
                if (pathExplicitMergeInfo != null) {
                    oldURL = ensureSessionURL(myRepository2, pathEntry.getSVNURL());
                    Map pathInheritedMergeInfo = getWCOrRepositoryMergeInfo(pathWithNewMergeInfo, pathEntry, 
                            SVNMergeInfoInheritance.NEAREST_ANCESTOR, indirect, false, myRepository2);
                    
                    if (pathInheritedMergeInfo != null) {
                        pathExplicitMergeInfo = SVNMergeInfoUtil.mergeMergeInfos(pathExplicitMergeInfo, pathInheritedMergeInfo);
                        SVNPropertiesManager.recordWCMergeInfo(pathWithNewMergeInfo, pathExplicitMergeInfo, myWCAccess);
                    }
                
                    MergePath newChild = new MergePath(pathWithNewMergeInfo);
                    if (!myChildrenWithMergeInfo.contains(newChild)) {
                        int parentIndex = findNearestAncestor(myChildrenWithMergeInfo.toArray(), false, pathWithNewMergeInfo);
                        MergePath parent = (MergePath) myChildrenWithMergeInfo.get(parentIndex);
                        newChild.myRemainingRanges = parent.myRemainingRanges.dup();
                        myChildrenWithMergeInfo.add(newChild);
                        Collections.sort(myChildrenWithMergeInfo);
                    }
                }
                
                if (oldURL != null) {
                    myRepository2.setLocation(oldURL, false);
                }
            }
        }
    }
    
    private void processChildrenWithDeletedMergeInfo() {
        if (myPathsWithDeletedMergeInfo != null && !myIsDryRun) {
            Iterator children = myChildrenWithMergeInfo.iterator();
            children.next(); // skip first.
            while(children.hasNext()) {
                MergePath path = (MergePath) children.next();
                if (path != null && myPathsWithDeletedMergeInfo.contains(path.myPath)) {
                    children.remove();             
                }
            }
        }
    }
    
    private Map splitMergeInfoOnRevision(Map[] mergeInfo, long revision) {
        Map youngerMergeInfo = null;
        for (Iterator mergeInfoIter = mergeInfo[0].keySet().iterator(); mergeInfoIter.hasNext();) {
            String mergeSourcePath = (String) mergeInfoIter.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo[0].get(mergeSourcePath);
            SVNMergeRange[] ranges = rangeList.getRanges();
            for (int i = 0; i < ranges.length; i++) {
                SVNMergeRange range = ranges[i];
                if (range.getEndRevision() < revision) {
                    continue;
                } 

                LinkedList youngerRanges = new LinkedList();
                for (int j = i; j < ranges.length; j++) {
                    SVNMergeRange youngerRange = ranges[j].dup();
                    if (j == i && youngerRange.getStartRevision() < revision) {
                        youngerRange.setStartRevision(revision);
                        range.setEndRevision(revision);
                    }
                    youngerRanges.add(youngerRange);    
                }
                    
                if (youngerMergeInfo == null) {
                    youngerMergeInfo = new TreeMap();
                }
                
                youngerMergeInfo.put(mergeSourcePath, SVNMergeRangeList.fromCollection(youngerRanges));
                mergeInfo[0] = SVNMergeInfoUtil.removeMergeInfo(youngerMergeInfo, mergeInfo[0]);
                break;
            }
        }
        return youngerMergeInfo;
    }

    private void ensureWCReflectsRepositorySubTree(File targetWCPath) throws SVNException {
    	SVNRevisionStatus wcStatus = SVNStatusUtil.getRevisionStatus(targetWCPath, null, false, getEventDispatcher());
    	if (wcStatus.isSwitched()) {
    	    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE,
    	    "Cannot reintegrate into a working copy with a switched subtree");
    	    SVNErrorManager.error(err, SVNLogType.WC);
    	}

    	if (wcStatus.isSparseCheckout()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE,
            "Cannot reintegrate into a working copy not entirely at infinite depth");
            SVNErrorManager.error(err, SVNLogType.WC);
    	}
    	
    	if (wcStatus.isModified()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE,
            "Cannot reintegrate into a working copy that has local modifications");
            SVNErrorManager.error(err, SVNLogType.WC);
    	}
    	
    	if (!SVNRevision.isValidRevisionNumber(wcStatus.getMinRevision()) || 
    	        !SVNRevision.isValidRevisionNumber(wcStatus.getMaxRevision())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE,
            "Cannot determine revision of working copy");
            SVNErrorManager.error(err, SVNLogType.WC);
    	}
    	
    	if (wcStatus.getMinRevision() != wcStatus.getMaxRevision()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE,
            "Cannot reintegrate into mixed-revision working copy; try updating first");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
    	}
    }
    
    private void ensureAllMissingRangesArePhantoms(SVNRepository repository, Map unmergedHistory) throws SVNException {
        for (Iterator unmergedHistoryIter = unmergedHistory.keySet().iterator(); unmergedHistoryIter.hasNext();) {
            String catalogPath = (String) unmergedHistoryIter.next();
            Map historyAsMergeInfo = (Map) unmergedHistory.get(catalogPath);
            for (Iterator pathsIter = historyAsMergeInfo.keySet().iterator(); pathsIter.hasNext();) {
                String path = (String) pathsIter.next();
                SVNMergeRangeList rangeList = (SVNMergeRangeList) historyAsMergeInfo.get(path);
                SVNMergeRange[] ranges = rangeList.getRanges();
                for (int i = 0; i < ranges.length; i++) {
                    SVNMergeRange mergeRange = ranges[i];
                    if (mergeRange.getStartRevision() >= mergeRange.getEndRevision()) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "range start >= end");
                        SVNErrorManager.error(err, SVNLogType.DEFAULT);
                    }
                    
                    SVNDirEntry dirEntry = repository.info(path, mergeRange.getEndRevision());
                    if (mergeRangeContainsRevision(mergeRange, dirEntry.getRevision())) {
                        SVNURL fullURL = repository.getLocation();
                        if (path.startsWith("/")) {
                            path = path.substring(1);
                        }
                        fullURL = fullURL.appendPath(path, false);
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE, 
                        "At least one revision (r{0}) not yet merged from ''{1}''", 
                        new Object[] { new Long(dirEntry.getRevision()), fullURL });
                        SVNErrorManager.error(err, SVNLogType.DEFAULT);
                    }
                }
            }
        }
    }
    
    private Map findUnmergedMergeInfo(boolean[] neverSynched, long[] youngestMergedRev, long[] ycAncestorRev, Map srcCatalog, Map targetSegments, 
            String sourceReposPath, String targetReposPath, long targetRev, long srcRev, SVNRepository repos) throws SVNException {
        neverSynched[0] = true;
        youngestMergedRev[0] = SVNRepository.INVALID_REVISION;
        Map newCatalog = new TreeMap();
        for (Iterator targetSegmentsIter = targetSegments.keySet().iterator(); targetSegmentsIter.hasNext();) {
            String path = (String) targetSegmentsIter.next();
            Collection segments = (Collection) targetSegments.get(path);

            String srcPath = path.substring(targetReposPath.length());
            if (srcPath.startsWith("/")) {
                srcPath = srcPath.substring(1);
            }

            srcPath = SVNPathUtil.append(sourceReposPath, srcPath);
            if (!srcPath.startsWith("/")) {
                srcPath = "/" + srcPath;
            }
            
            Map targetHistoryAsMergeInfo = getMergeInfoFromSegments(segments);
            targetHistoryAsMergeInfo = SVNMergeInfoUtil.filterMergeInfoByRanges(targetHistoryAsMergeInfo, srcRev, ycAncestorRev[0]);
            SVNMergeInfo sourceMergeInfoObject = (SVNMergeInfo) srcCatalog.get(srcPath);
            
            Map srcMergeInfo = sourceMergeInfoObject != null ? sourceMergeInfoObject.getMergeSourcesToMergeLists() : null;
            if (srcMergeInfo != null) {
                srcCatalog.remove(srcPath);
                Map explicitSrcTgtHistoryIntersection = SVNMergeInfoUtil.intersectMergeInfo(srcMergeInfo, targetHistoryAsMergeInfo);
                if (!explicitSrcTgtHistoryIntersection.isEmpty()) {
                    neverSynched[0] = false;
                    long[] endPoints = SVNMergeInfoUtil.getRangeEndPoints(explicitSrcTgtHistoryIntersection);
                    long youngRev = endPoints[0];

                    if (!SVNRevision.isValidRevisionNumber(youngestMergedRev[0]) || (youngRev > youngestMergedRev[0])) {
                        youngestMergedRev[0] = youngRev;
                    }
                }
            } else {
                SVNNodeKind kind = repos.checkPath(srcPath, srcRev);
                if (kind == SVNNodeKind.NONE) {
                    continue;
                }
                
                Map subTreeCatalog = repos.getMergeInfo(new String[] { srcPath }, srcRev, SVNMergeInfoInheritance.INHERITED, false);
                if (subTreeCatalog != null) {
                    SVNMergeInfo sourceMergeInfo = (SVNMergeInfo) subTreeCatalog.get(srcPath);
                    srcMergeInfo = sourceMergeInfo != null ? sourceMergeInfo.getMergeSourcesToMergeLists() : null;
                }
                if (srcMergeInfo == null) {
                    srcMergeInfo = new TreeMap();
                }
            }
            
            segments = repos.getLocationSegments(srcPath, srcRev, srcRev, SVNRepository.INVALID_REVISION);
            Map srcHistoryAsMergeInfo = getMergeInfoFromSegments(segments);
            srcMergeInfo = SVNMergeInfoUtil.mergeMergeInfos(srcMergeInfo, srcHistoryAsMergeInfo);
            Map commonMergeInfo = SVNMergeInfoUtil.intersectMergeInfo(srcMergeInfo, targetHistoryAsMergeInfo);
            Map filteredMergeInfo = SVNMergeInfoUtil.removeMergeInfo(commonMergeInfo, targetHistoryAsMergeInfo);
            newCatalog.put(srcPath, filteredMergeInfo);
        }
        
        if (!srcCatalog.isEmpty()) {
            for (Iterator srcCatalogIter = srcCatalog.keySet().iterator(); srcCatalogIter.hasNext();) {
                String srcPath = (String) srcCatalogIter.next();
                SVNMergeInfo sourceMergeInfoObject = (SVNMergeInfo) srcCatalog.get(srcPath);
                Map srcMergeInfo = sourceMergeInfoObject.getMergeSourcesToMergeLists();
                String targetPath = srcPath.substring(sourceReposPath.length());
                if (targetPath.startsWith("/")) {
                    targetPath = targetPath.substring(1);
                }
                targetPath = SVNPathUtil.append(targetReposPath, targetPath);
                Collection segments = null;
                boolean tgtPathExists = true;
                try {
                    segments = repos.getLocationSegments(targetPath, targetRev, targetRev, SVNRepository.INVALID_REVISION);
                } catch (SVNException svne) {
                    SVNErrorMessage err = svne.getErrorMessage();
                    SVNErrorCode errCode = err.getErrorCode();
                    if (errCode != SVNErrorCode.FS_NOT_FOUND && errCode != SVNErrorCode.RA_DAV_REQUEST_FAILED) {
                        throw svne;
                    }
                    tgtPathExists = false;
                }
                
                if (tgtPathExists) {
                    Map targetHistoryAsMergeInfo = getMergeInfoFromSegments(segments);
                    Map explicitSrcTgtHistoryIntersection = SVNMergeInfoUtil.intersectMergeInfo(srcMergeInfo, targetHistoryAsMergeInfo);
                    if (!explicitSrcTgtHistoryIntersection.isEmpty()) {
                        neverSynched[0] = false;
                        long[] endPoints = SVNMergeInfoUtil.getRangeEndPoints(explicitSrcTgtHistoryIntersection);
                        long youngRev = endPoints[0];
                        
                        if (!SVNRevision.isValidRevisionNumber(youngestMergedRev[0]) || (youngRev > youngestMergedRev[0])) {
                            youngestMergedRev[0] = youngRev;
                        }
                    }
                    
                    segments = repos.getLocationSegments(srcPath, targetRev, targetRev, SVNRepository.INVALID_REVISION);
                    Map srcHistoryAsMergeInfo = getMergeInfoFromSegments(segments);
                    srcMergeInfo = SVNMergeInfoUtil.mergeMergeInfos(srcMergeInfo, srcHistoryAsMergeInfo);
                    Map commonMergeInfo = SVNMergeInfoUtil.intersectMergeInfo(srcMergeInfo, targetHistoryAsMergeInfo);
                    Map filteredMergeInfo = SVNMergeInfoUtil.removeMergeInfo(commonMergeInfo, targetHistoryAsMergeInfo);
                    if (!filteredMergeInfo.isEmpty()) {
                        newCatalog.put(srcPath, filteredMergeInfo);
                    }
                }
            }
        }

        if (SVNRevision.isValidRevisionNumber(youngestMergedRev[0])) {
            newCatalog = SVNMergeInfoUtil.filterCatalogByRanges(newCatalog, youngestMergedRev[0], 0);
        }
        return newCatalog;
    }
    
    private Map calculateLeftHandSide(SVNURL[] leftURL, long[] leftRev, String targetReposRelPath, 
            Collection subTreesWithMergeInfo, long targetRev, String sourceReposRelPath, SVNURL sourceReposRoot, long sourceRev, 
            SVNRepository repository) throws SVNException {
        Map segmentsMap = new TreeMap();
        for (Iterator subTreesPathIter = subTreesWithMergeInfo.iterator(); subTreesPathIter.hasNext();) {
            String path = (String) subTreesPathIter.next();
            Collection segments = repository.getLocationSegments(path, targetRev, targetRev, 
                    SVNRepository.INVALID_REVISION);
            segmentsMap.put(path, segments);
        }

        Map mergeInfoCatalog = repository.getMergeInfo(new String[] { sourceReposRelPath }, sourceRev, 
                SVNMergeInfoInheritance.INHERITED, true);
        if (mergeInfoCatalog == null) {
            mergeInfoCatalog = Collections.EMPTY_MAP;
        }
        
        boolean[] neverSynched = new boolean[1];
        long[] youngestMergedRev = new long[1];
        SVNURL sourceURL = sourceReposRoot.appendPath(sourceReposRelPath.startsWith("/") ? 
                sourceReposRelPath.substring(1) : sourceReposRelPath, false);
        SVNURL targetURL = sourceReposRoot.appendPath(targetReposRelPath.startsWith("/") ? 
                targetReposRelPath.substring(1) : targetReposRelPath, false);
        SVNLocationEntry youngestLocation = getYoungestCommonAncestor(null, sourceURL, sourceRev, null, 
                targetURL, targetRev);
        String youngestCommonAncestorPath = youngestLocation.getPath();
        leftRev[0] = youngestLocation.getRevision();
        if (!(youngestCommonAncestorPath != null && SVNRevision.isValidRevisionNumber(leftRev[0]))) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE, 
                    "''{0}@{1}'' must be ancestrally related to ''{2}@{3}''", 
                    new Object[] { sourceURL, new Long(sourceRev), targetURL, new Long(targetRev) });
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        Map unmergedCatalog = findUnmergedMergeInfo(neverSynched, youngestMergedRev, leftRev, mergeInfoCatalog, segmentsMap, sourceReposRelPath, 
                targetReposRelPath, targetRev, sourceRev, repository); 
        unmergedCatalog = SVNMergeInfoUtil.elideMergeInfoCatalog(unmergedCatalog);
        
        if (neverSynched[0]) {
            leftURL[0] = sourceReposRoot.appendPath(youngestCommonAncestorPath.startsWith("/") ? 
                    youngestCommonAncestorPath.substring(1) : youngestCommonAncestorPath, false);
        } else {
            leftRev[0] = youngestMergedRev[0];
            leftURL[0] = deriveLocation(null, sourceReposRoot.appendPath(targetReposRelPath, false), null, 
                    SVNRevision.create(youngestMergedRev[0]), repository, null);
        }
        return unmergedCatalog;
    }

    private boolean mergeRangeContainsRevision(SVNMergeRange range, long rev) throws SVNException {
        if (!SVNRevision.isValidRevisionNumber(range.getStartRevision())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "invalid start range revision");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        if (!SVNRevision.isValidRevisionNumber(range.getEndRevision())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "invalid end range revision");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        if (range.getStartRevision() == range.getEndRevision()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                    "start range revision is equal to end range revision");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        if (range.getStartRevision() < range.getEndRevision()) {
            return rev > range.getStartRevision() && rev <= range.getEndRevision();
        }
        return rev > range.getEndRevision() && rev <= range.getStartRevision();
    }
    
    private void mergeCousinsAndSupplementMergeInfo(File targetWCPath, SVNEntry entry, 
    		SVNAdminArea adminArea, SVNRepository repository, SVNURL url1, long rev1, SVNURL url2, 
    		long rev2, long youngestCommonRev, SVNURL sourceReposRoot, SVNURL wcReposRoot, SVNDepth depth, 
    		boolean ignoreAncestry,	boolean force, boolean recordOnly, boolean dryRun) throws SVNException {
		SVNURL oldURL = repository.getLocation();
		List addSources = null;
		List removeSources = null;
		try {
			SVNRevision sRev = SVNRevision.create(rev1);
			SVNRevision eRev = SVNRevision.create(youngestCommonRev);
			SVNRevisionRange range = new SVNRevisionRange(sRev, eRev);
	    	List ranges = new LinkedList();
			ranges.add(range);
			repository.setLocation(url1, false);
			removeSources = normalizeMergeSources(null, url1, sourceReposRoot, sRev, ranges, repository);
			sRev = eRev;
			eRev = SVNRevision.create(rev2);
			range = new SVNRevisionRange(sRev, eRev);
			ranges.clear();
			ranges.add(range);
			repository.setLocation(url2, false);
			addSources = normalizeMergeSources(null, url2, sourceReposRoot, eRev, 
					ranges, repository);
		} finally {
			repository.setLocation(oldURL, false);
		}
		
		boolean sameRepos = sourceReposRoot.equals(wcReposRoot);  
		if (!recordOnly) {
			MergeSource fauxSource = new MergeSource();
			fauxSource.myURL1 = url1;
			fauxSource.myURL2 = url2;
			fauxSource.myRevision1 = rev1;
			fauxSource.myRevision2 = rev2;
			List fauxSources = new LinkedList();
			fauxSources.add(fauxSource);
			doMerge(fauxSources, targetWCPath, entry, adminArea, false, true, 
					sourceReposRoot.equals(wcReposRoot), ignoreAncestry, force, dryRun, 
					false, true, depth);
		} else if (!sameRepos) {
		    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
		            "Merge from foreign repository is not compatible with mergeinfo modification");
		    SVNErrorManager.error(err, SVNLogType.DEFAULT);
		}
		
		if (sameRepos) {
		    doMerge(addSources, targetWCPath, entry, adminArea, true, true, sameRepos, 
	                ignoreAncestry, force, dryRun, true, true, depth);
	        doMerge(removeSources, targetWCPath, entry, adminArea, true, true, sameRepos, 
	                ignoreAncestry, force, dryRun, true, true, depth);
		}
    }

    public boolean isSameRepository() {
        return myIsSameRepository;
    }
    
    protected boolean isHonorMergeInfo() {
    	return myIsMergeInfoCapable && myAreSourcesAncestral && myIsSameRepository && !myIsIgnoreAncestry;
    }

    public boolean isRecordMergeInfo() {
    	return myIsMergeInfoCapable && myAreSourcesAncestral && myIsSameRepository && !myIsIgnoreAncestry && !myIsDryRun;
    }
    
    protected MergeSource getCurrentMergeSource() {
        return myCurrentMergeSource;
    }
    
    private List normalizeMergeSources(File source, SVNURL sourceURL, SVNURL sourceRootURL, 
    		SVNRevision pegRevision, Collection rangesToMerge, SVNRepository repository) throws SVNException {
    	long youngestRevision[] = { SVNRepository.INVALID_REVISION };
    	long pegRevNum = getRevisionNumber(pegRevision, youngestRevision, repository, source);
    	if (!SVNRevision.isValidRevisionNumber(pegRevNum)) {
    		SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION);
    		SVNErrorManager.error(err, SVNLogType.DEFAULT);
    	}
    	
    	List mergeRanges = new ArrayList(rangesToMerge.size());
    	for (Iterator rangesIter = rangesToMerge.iterator(); rangesIter.hasNext();) {
			SVNRevisionRange revRange = (SVNRevisionRange) rangesIter.next();
			SVNRevision rangeStart = revRange.getStartRevision();
			SVNRevision rangeEnd = revRange.getEndRevision();
			
			if (!rangeStart.isValid() || !rangeEnd.isValid()) {
				SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
						"Not all required revisions are specified");
				SVNErrorManager.error(err, SVNLogType.DEFAULT);
			}
			
			long rangeStartRev = getRevisionNumber(rangeStart, youngestRevision, repository, source); 
			long rangeEndRev = getRevisionNumber(rangeEnd, youngestRevision, repository, source);
			if (rangeStartRev != rangeEndRev) {
				SVNMergeRange range = new SVNMergeRange(rangeStartRev, rangeEndRev, true);
				mergeRanges.add(range);
			}
    	}
    	
    	SVNMergeRangeList mergeRangesList = SVNMergeRangeList.fromCollection(mergeRanges);
    	mergeRanges = mergeRangesList.getRangesAsList();
    	if (mergeRanges.isEmpty()) {
    		return mergeRanges;
    	}
    	
    	long oldestRequestedRev = SVNRepository.INVALID_REVISION;
    	long youngestRequestedRev = SVNRepository.INVALID_REVISION;
    	for (Iterator rangesIter = mergeRanges.iterator(); rangesIter.hasNext();) {
			SVNMergeRange range = (SVNMergeRange) rangesIter.next();
			long minRev = Math.min(range.getStartRevision(), range.getEndRevision());
			long maxRev = Math.max(range.getStartRevision(), range.getEndRevision());
			
			if (!SVNRevision.isValidRevisionNumber(oldestRequestedRev) || minRev < oldestRequestedRev) {
				oldestRequestedRev = minRev;
			}
			if (!SVNRevision.isValidRevisionNumber(youngestRequestedRev) || maxRev > youngestRequestedRev) {
				youngestRequestedRev = maxRev;
			}
		}
    	
    	if (pegRevNum < youngestRequestedRev) {
            getLocations(sourceURL, null, repository, SVNRevision.create(pegRevNum), 
                    SVNRevision.create(youngestRequestedRev), SVNRevision.UNDEFINED);
            pegRevNum = youngestRequestedRev;
    	}

    	LinkedList segments = (LinkedList) repository.getLocationSegments("", pegRevNum, youngestRequestedRev, 
    			oldestRequestedRev);

		long trimRevision =  SVNRepository.INVALID_REVISION;
		if (!segments.isEmpty()) {
		    SVNLocationSegment segment = (SVNLocationSegment) segments.get(0);
		    if (segment.getStartRevision() != oldestRequestedRev) {
		        trimRevision = segment.getStartRevision();
		    } else if (segment.getPath() == null) {
		        if (segments.size() > 1) {
		            SVNLocationSegment segment2 = (SVNLocationSegment) segments.get(1);
		            SVNURL segmentURL = sourceRootURL.appendPath(segment2.getPath(), false);
		            SVNLocationEntry copyFromLocation = getCopySource(null, segmentURL, 
		                    SVNRevision.create(segment2.getStartRevision()));
		            String copyFromPath = copyFromLocation.getPath();
		            long copyFromRevision = copyFromLocation.getRevision();
		            if (copyFromPath != null && SVNRevision.isValidRevisionNumber(copyFromRevision)) {
		                SVNLocationSegment newSegment = new SVNLocationSegment(copyFromRevision, 
		                        copyFromRevision, copyFromPath);
		                segment.setStartRevision(copyFromRevision + 1);
		                segments.addFirst(newSegment);
		            }
		        }
		    }
		}

		SVNLocationSegment[] segmentsArray = (SVNLocationSegment[]) segments.toArray(new SVNLocationSegment[segments.size()]);
		List resultMergeSources = new LinkedList();
    	for (Iterator rangesIter = mergeRanges.iterator(); rangesIter.hasNext();) {
			SVNMergeRange range = (SVNMergeRange) rangesIter.next();
			if (SVNRevision.isValidRevisionNumber(trimRevision)) {
			    if (Math.max(range.getStartRevision(), range.getEndRevision()) < trimRevision) {
			        continue;
			    }
			    if (range.getStartRevision() < trimRevision) {
			        range.setStartRevision(trimRevision);
			    }
			    if (range.getEndRevision() < trimRevision) {
			        range.setEndRevision(trimRevision);
			    }
			}
			List mergeSources = combineRangeWithSegments(range, segmentsArray, sourceRootURL);
			resultMergeSources.addAll(mergeSources);
    	}
    	return resultMergeSources;
    }
    
    private List combineRangeWithSegments(SVNMergeRange range, SVNLocationSegment[] segments, 
    		SVNURL sourceRootURL) throws SVNException {
    	long minRev = Math.min(range.getStartRevision(), range.getEndRevision()) + 1;
    	long maxRev = Math.max(range.getStartRevision(), range.getEndRevision());
    	boolean subtractive = range.getStartRevision() > range.getEndRevision();
    	List mergeSources = new LinkedList();
    	for (int i = 0; i < segments.length; i++) {
			SVNLocationSegment segment = segments[i];
			if (segment.getEndRevision() < minRev || segment.getStartRevision() > maxRev || 
					segment.getPath() == null) {
				continue;
			}
			
			String path1 = null;
			long rev1 = Math.max(segment.getStartRevision(), minRev) - 1;
			if (minRev <= segment.getStartRevision()) {
				if (i > 0) {
					path1 = segments[i - 1].getPath();
				}
				if (path1 == null && i > 1) {
					path1 = segments[i - 2].getPath();
					rev1 = segments[i - 2].getEndRevision();
				}
			} else {
				path1 = segment.getPath();
			}
			
			if (path1 == null || segment.getPath() == null) {
				continue;
			}
			
			MergeSource mergeSource = new MergeSource();
			mergeSource.myURL1 = sourceRootURL.appendPath(path1, false);
			mergeSource.myURL2 = sourceRootURL.appendPath(segment.getPath(), false);
			mergeSource.myRevision1 = rev1;
			mergeSource.myRevision2 = Math.min(segment.getEndRevision(), maxRev);
			if (subtractive) {
				long tmpRev = mergeSource.myRevision1;
				SVNURL tmpURL = mergeSource.myURL1;
				mergeSource.myRevision1 = mergeSource.myRevision2;
				mergeSource.myURL1 = mergeSource.myURL2;
				mergeSource.myRevision2 = tmpRev;
				mergeSource.myURL2 = tmpURL;
			}
			mergeSources.add(mergeSource);
    	}
    	
    	if (subtractive && !mergeSources.isEmpty()) {
    		Collections.sort(mergeSources, new Comparator() {
				public int compare(Object o1, Object o2) {
					MergeSource source1 = (MergeSource) o1;
					MergeSource source2 = (MergeSource) o2;
					long src1Rev1 = source1.myRevision1;
					long src2Rev1 = source2.myRevision1;
					if (src1Rev1 == src2Rev1) {
						return 0;
					}
					return src1Rev1 < src2Rev1 ? 1 : -1;
				}
    		});
    	}
    	return mergeSources;
    }

    private SVNLocationEntry getYoungestCommonAncestor(File path1, SVNURL url1, long revision1, 
    		File path2, SVNURL url2, long revision2) throws SVNException {
    	Map history1 = getHistoryAsMergeInfo(url1, path1, SVNRevision.create(revision1), 
    			SVNRepository.INVALID_REVISION, SVNRepository.INVALID_REVISION, null, null);
    	Map history2 = getHistoryAsMergeInfo(url2, path2, SVNRevision.create(revision2), 
    			SVNRepository.INVALID_REVISION,	SVNRepository.INVALID_REVISION, null, null);
    	
    	long youngestCommonRevision = SVNRepository.INVALID_REVISION;
    	String youngestCommonPath = null;
    	for (Iterator historyIter = history1.entrySet().iterator(); historyIter.hasNext();) {
    		Map.Entry historyEntry = (Map.Entry) historyIter.next();
    		String path = (String) historyEntry.getKey();
    		SVNMergeRangeList ranges1 = (SVNMergeRangeList) historyEntry.getValue();
    		SVNMergeRangeList ranges2 = (SVNMergeRangeList) history2.get(path);
    		if (ranges2 != null) {
    			SVNMergeRangeList commonList = ranges2.intersect(ranges1, true);
    			if (!commonList.isEmpty()) {
    				SVNMergeRange commonRanges[] = commonList.getRanges();
    				SVNMergeRange youngestCommonRange = commonRanges[commonRanges.length - 1];
    				if (!SVNRevision.isValidRevisionNumber(youngestCommonRevision) || 
    						youngestCommonRange.getEndRevision() > youngestCommonRevision) {
    					youngestCommonRevision = youngestCommonRange.getEndRevision();
    					youngestCommonPath = path;
    				}
    			}
    		}
    	}
    	return new SVNLocationEntry(youngestCommonRevision, youngestCommonPath);
    }

    protected Map[] getFullMergeInfo(SVNEntry entry, boolean[] indirect, SVNMergeInfoInheritance inherit,
            SVNRepository repos, File target, long start, long end) throws SVNException {
        Map[] result = new Map[2];
        if (!SVNRevision.isValidRevisionNumber(start) || !SVNRevision.isValidRevisionNumber(end) || 
                start <= end) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                    "ASSERTION FAILED in SVNMergeDriver.getFullMergeInfo()");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        
        //get recorded merge info
        result[0] = getWCOrRepositoryMergeInfo(target, entry, inherit, indirect, false, repos);
        long[] targetRev = new long[1];
        targetRev[0] = SVNRepository.INVALID_REVISION;
        SVNURL url = deriveLocation(target, null, targetRev, SVNRevision.WORKING, repos, myWCAccess);
        if (targetRev[0] <= end) {
            result[1] = new TreeMap();//implicit merge info
            return result;
        }

        Map implicitMergeInfo = calculateImplicitMergeInfo(repos, url, targetRev, start, end);
        if (implicitMergeInfo != null) {
            result[1] = implicitMergeInfo;
        }
        return result;
    }

    public Map calculateImplicitMergeInfo(SVNRepository repos, SVNURL url, long[] targetRev, long start, long end) throws SVNException {
        Map implicitMergeInfo = null;
        boolean closeSession = false;
        SVNURL sessionURL = null;
        try {
            if (repos != null) {
                sessionURL = ensureSessionURL(repos, url);
            } else {
                repos = createRepository(url, null, null, false);
                closeSession = true;
            }
            
            if (targetRev[0] < start) {
                getLocations(url, null, repos, SVNRevision.create(targetRev[0]), 
                        SVNRevision.create(start), SVNRevision.UNDEFINED);
                targetRev[0] = start;
            }
            implicitMergeInfo = getHistoryAsMergeInfo(url, null, SVNRevision.create(targetRev[0]), start, end, repos, null);
            if (sessionURL != null) {
                repos.setLocation(sessionURL, false);
            }
        } finally {
            if (closeSession) {
                repos.closeSession();
            }
        }
        return implicitMergeInfo;
    }
    
    private int findNearestAncestor(Object[] childrenWithMergeInfoArray, boolean pathIsOwnAncestor, File path) {
        if (childrenWithMergeInfoArray == null) {
            return 0;
        }

        int ancestorIndex = 0;
        for (int i = 0; i < childrenWithMergeInfoArray.length; i++) {
            MergePath child = (MergePath) childrenWithMergeInfoArray[i];
            String childPath = child.myPath.getAbsolutePath().replace(File.separatorChar, '/');
            String pathStr = path.getAbsolutePath().replace(File.separatorChar, '/');
            if (SVNPathUtil.isAncestor(childPath, pathStr) && (!childPath.equals(pathStr) || pathIsOwnAncestor)) {
                ancestorIndex = i;
            }
        }
        return ancestorIndex;
    }
    
    protected Map getHistoryAsMergeInfo(SVNURL url, File path, SVNRevision pegRevision, long rangeYoungest, 
            long rangeOldest, SVNRepository repos, SVNWCAccess access) throws SVNException {
        long[] pegRevNum = new long[1];
        pegRevNum[0] = SVNRepository.INVALID_REVISION;
        url = deriveLocation(path, url, pegRevNum, pegRevision, repos, access);
        
        boolean closeSession = false;
        try {
            if (repos == null) {
                repos = createRepository(url, null, null, false);
                closeSession = true;
            }
            if (!SVNRevision.isValidRevisionNumber(rangeYoungest)) {
                rangeYoungest = pegRevNum[0];
            }
            if (!SVNRevision.isValidRevisionNumber(rangeOldest)) {
                rangeOldest = 0;
            }
            
            Collection segments = repos.getLocationSegments("", pegRevNum[0], rangeYoungest, rangeOldest);
            return getMergeInfoFromSegments(segments);
        } finally {
            if (closeSession) {
                repos.closeSession();
            }
        }
    }

    private Map getMergeInfoFromSegments(Collection segments) {
        Map mergeInfo = new TreeMap();
        for (Iterator segmentsIter = segments.iterator(); segmentsIter.hasNext();) {
            SVNLocationSegment segment = (SVNLocationSegment) segmentsIter.next();
            if (segment.getPath() == null) {
                continue;
            }
            String sourcePath = segment.getPath();
            Collection pathRanges = (Collection) mergeInfo.get(sourcePath);
            if (pathRanges == null) {
                pathRanges = new LinkedList();
                mergeInfo.put(sourcePath, pathRanges);
            }
            SVNMergeRange range = new SVNMergeRange(Math.max(segment.getStartRevision() - 1, 0), 
                    segment.getEndRevision(), true);
            pathRanges.add(range);
        }
        Map result = new TreeMap();
        for (Iterator paths = mergeInfo.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            Collection pathRanges = (Collection) mergeInfo.get(path);
            result.put(path, SVNMergeRangeList.fromCollection(pathRanges));
        }
        return result;
    }
    
    private void removeAbsentChildren(File targetWCPath, List childrenWithMergeInfo) {
        for (Iterator children = childrenWithMergeInfo.iterator(); children.hasNext();) {
            MergePath child = (MergePath) children.next();
            String topDir = targetWCPath.getAbsolutePath().replace(File.separatorChar, '/');
            String childPath = child.myPath.getAbsolutePath().replace(File.separatorChar, '/');
            if (child != null && (child.myIsAbsent || child.myIsScheduledForDeletion) && 
            		SVNPathUtil.isAncestor(topDir, childPath)) {
                if (mySkippedPaths != null) {
                    mySkippedPaths.remove(child.myPath);
                }
                children.remove();
            }
        }
    }
    
    private void removeFirstRangeFromRemainingRanges(long endRevision, List childrenWithMergeInfo) {
        for (Iterator children = childrenWithMergeInfo.iterator(); children.hasNext();) {
            MergePath child = (MergePath) children.next();
            if (child == null || child.myIsAbsent) {
                continue;
            }
            if (!child.myRemainingRanges.isEmpty()) {
                SVNMergeRange[] originalRemainingRanges = child.myRemainingRanges.getRanges();
                SVNMergeRange firstRange = originalRemainingRanges[0]; 
                if (firstRange.getEndRevision() == endRevision) {
                    SVNMergeRange[] remainingRanges = new SVNMergeRange[originalRemainingRanges.length - 1];
                    System.arraycopy(originalRemainingRanges, 1, remainingRanges, 0, 
                            originalRemainingRanges.length - 1);
                    child.myRemainingRanges = new SVNMergeRangeList(remainingRanges);
                }
            }
        }
    }
    
    private SVNMergeRangeList removeNoOpMergeRanges(SVNRepository repository, SVNMergeRangeList ranges) throws SVNException {
        long oldestRev = SVNRepository.INVALID_REVISION;
        long youngestRev = SVNRepository.INVALID_REVISION;
        
        SVNMergeRange[] mergeRanges = ranges.getRanges();
        for (int i = 0; i < ranges.getSize(); i++) {
            SVNMergeRange range = mergeRanges[i];
            long maxRev = Math.max(range.getStartRevision(), range.getEndRevision());
            long minRev = Math.min(range.getStartRevision(), range.getEndRevision());
            if (!SVNRevision.isValidRevisionNumber(youngestRev) || maxRev > youngestRev) {
                youngestRev = maxRev;
            }
            if (!SVNRevision.isValidRevisionNumber(oldestRev) || minRev < oldestRev) {
                oldestRev = minRev;
            }
        }
        
        final List changedRevs = new LinkedList();
        repository.log(new String[] { "" }, youngestRev, oldestRev, false, false, 0, false, new String[0], 
                new ISVNLogEntryHandler() {
            public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                changedRevs.add(new Long(logEntry.getRevision()));
            }
        });
        
        long youngestChangedRevision = SVNRepository.INVALID_REVISION;
        long oldestChangedRevision = SVNRepository.INVALID_REVISION;
        if (changedRevs.size() > 0) {
            youngestChangedRevision = ((Long) changedRevs.get(0)).longValue();
            oldestChangedRevision = ((Long) changedRevs.get(changedRevs.size() - 1)).longValue();
        }
        
        List operativeRanges = new LinkedList();
        for (int i = 0; i < ranges.getSize(); i++) {
            SVNMergeRange range = mergeRanges[i];
            long rangeMinRev = Math.min(range.getStartRevision(), range.getEndRevision()) + 1;
            long rangeMaxRev = Math.max(range.getStartRevision(), range.getEndRevision());
            if (rangeMinRev > youngestChangedRevision || rangeMaxRev < oldestChangedRevision) {
                continue;
            }
            for (Iterator changedRevsIter = changedRevs.iterator(); changedRevsIter.hasNext();) {
                long changedRev = ((Long) changedRevsIter.next()).longValue();
                if (changedRev >= rangeMinRev && changedRev <= rangeMaxRev) {
                    operativeRanges.add(range);
                    break;
                }
            }
        }
        return SVNMergeRangeList.fromCollection(operativeRanges);
    }

    private SVNMergeRangeList filterNaturalHistoryFromMergeInfo(String srcPath, Map implicitMergeInfo, 
            SVNMergeRange requestedRange) {
        SVNMergeRangeList requestedRangeList = new SVNMergeRangeList(requestedRange.dup());
        SVNMergeRangeList filteredRangeList = null;
        if (implicitMergeInfo != null && requestedRange.getStartRevision() < requestedRange.getEndRevision()) {
            SVNMergeRangeList impliedRangeList = (SVNMergeRangeList) implicitMergeInfo.get(srcPath);
            if (impliedRangeList != null) {
                filteredRangeList = requestedRangeList.diff(impliedRangeList, false);
            }
        }
        if (filteredRangeList == null) {
            filteredRangeList = requestedRangeList;
        }
        return filteredRangeList;
    }
    
    private void sliceRemainingRanges(List childrenWithMergeInfo, boolean isRollBack, long endRevision) {
        for (Iterator children = childrenWithMergeInfo.iterator(); children.hasNext();) {
            MergePath child = (MergePath) children.next();
            if (child == null || child.myIsAbsent) {
                continue;
            }
            
            if (!child.myRemainingRanges.isEmpty()) {
                SVNMergeRange[] originalRemainingRanges = child.myRemainingRanges.getRanges();
                SVNMergeRange range = originalRemainingRanges[0];
                if ((isRollBack && range.getStartRevision() > endRevision && 
                		range.getEndRevision() < endRevision) ||
                		(!isRollBack && range.getStartRevision() < endRevision && 
                				range.getEndRevision() > endRevision)) {
                    SVNMergeRange splitRange1 = new SVNMergeRange(range.getStartRevision(), endRevision, 
                            range.isInheritable());
                    SVNMergeRange splitRange2 = new SVNMergeRange(endRevision, range.getEndRevision(), 
                            range.isInheritable());
                    SVNMergeRange[] remainingRanges = new SVNMergeRange[originalRemainingRanges.length + 1];
                    remainingRanges[0] = splitRange1;
                    remainingRanges[1] = splitRange2;
                    System.arraycopy(originalRemainingRanges, 1, remainingRanges, 2, 
                    		originalRemainingRanges.length - 1);
                    child.myRemainingRanges = new SVNMergeRangeList(remainingRanges);
                }
            }
        }
    }
    
    private long getMostInclusiveEndRevision(List childrenWithMergeInfo, boolean isRollBack) {
    	long endRev = SVNRepository.INVALID_REVISION;
    	for (int i = 0; i < childrenWithMergeInfo.size(); i++) {
    		MergePath child = (MergePath) childrenWithMergeInfo.get(i);
    		if (child == null || child.myIsAbsent) {
    			continue;
    		}
    		if (child.myRemainingRanges.getSize() > 0) {
        		SVNMergeRange ranges[] = child.myRemainingRanges.getRanges();
        		SVNMergeRange range = ranges[0];
        		if (!SVNRevision.isValidRevisionNumber(endRev) || 
        				(isRollBack && range.getEndRevision() > endRev) ||
        				(!isRollBack && range.getEndRevision() < endRev)) {
        			endRev = range.getEndRevision();
        		}
    		}
    	}
    	return endRev;
    }

    private long getMostInclusiveStartRevision(List childrenWithMergeInfo, boolean isRollBack) {
    	long startRev = SVNRepository.INVALID_REVISION;
    	for (int i = 0; i < childrenWithMergeInfo.size(); i++) {
    		MergePath child = (MergePath) childrenWithMergeInfo.get(i);
    		if (child == null || child.myIsAbsent) {
    			continue;
    		}
    		if (child.myRemainingRanges.isEmpty()) {
    			continue;
    		}
    		SVNMergeRange ranges[] = child.myRemainingRanges.getRanges();
    		SVNMergeRange range = ranges[0];
    		if (i == 0 && range.getStartRevision() == range.getEndRevision()) {
    			continue;
    		}
    		if (!SVNRevision.isValidRevisionNumber(startRev) || 
    				(isRollBack && range.getStartRevision() > startRev) ||
    				(!isRollBack && range.getStartRevision() < startRev)) {
    			startRev = range.getStartRevision();
    		}
    	}
    	return startRev;
    }
    
    private void populateRemainingRanges(List childrenWithMergeInfo, SVNURL sourceRootURL, 
    		SVNURL url1, long revision1, SVNURL url2, long revision2, 
    		boolean honorMergeInfo,	SVNRepository repository, String parentMergeSrcCanonPath) throws SVNException {

    	if (!honorMergeInfo || myIsRecordOnly) {
        	for (ListIterator childrenIter = childrenWithMergeInfo.listIterator(); childrenIter.hasNext();) {
                MergePath child = (MergePath) childrenIter.next();
                SVNMergeRange range = new SVNMergeRange(revision1, revision2, true);
                child.myRemainingRanges = new SVNMergeRangeList(range);
        	}    		
        	return;
    	}
    	long[] gap = new long[2];
    	findGapsInMergeSourceHistory(gap, parentMergeSrcCanonPath, url1, revision1, url2, revision2, repository);
    	if (gap[0] >= 0 && gap[1] >= 0) {
    	    myImplicitSrcGap = new SVNMergeRangeList(gap[0], gap[1], true);
    	}
    	int index = 0;
    	for (ListIterator childrenIter = childrenWithMergeInfo.listIterator(); childrenIter.hasNext();) {
            MergePath child = (MergePath) childrenIter.next();
            if (child == null || child.myIsAbsent) {
                index++;
                continue;
            }
            
            String childRelativePath = null;
            if (myTarget.equals(child.myPath)) {
                childRelativePath = "";
            } else {
                childRelativePath = SVNPathUtil.getRelativePath(myTarget.getAbsolutePath(),
                               child.myPath.getAbsolutePath());
            }
            MergePath parent = null;
            SVNURL childURL1 = url1.appendPath(childRelativePath, false);
            SVNURL childURL2 = url2.appendPath(childRelativePath, false);
            SVNEntry childEntry = myWCAccess.getVersionedEntry(child.myPath, false);
            
            boolean indirect[] = { false };
            Map mergeInfo[] = getFullMergeInfo(childEntry, indirect, SVNMergeInfoInheritance.INHERITED, 
            		repository, child.myPath, Math.max(revision1, revision2), Math.min(revision1, revision2));
        
            child.myPreMergeMergeInfo = mergeInfo[0];
            child.myImplicitMergeInfo = mergeInfo[1];
            child.myIsIndirectMergeInfo = indirect[0];

            if (index > 0) {
                Object[] childrenWithMergeInfoArray = childrenWithMergeInfo.toArray();
                int parentIndex = findNearestAncestor(childrenWithMergeInfoArray, false, child.myPath);
                if (parentIndex >= 0 && parentIndex < childrenWithMergeInfoArray.length) {
                    parent = (MergePath) childrenWithMergeInfoArray[parentIndex];
                }                
            }
            calculateRemainingRanges(parent, child, sourceRootURL, childURL1, revision1, 
            		childURL2, revision2, child.myPreMergeMergeInfo, child.myImplicitMergeInfo, myImplicitSrcGap, 
            		index > 0, childEntry, repository);
            if (child.myRemainingRanges.getSize() > 0  && myImplicitSrcGap != null) {
                long start, end;
                boolean properSubset = false;
                boolean equals = false;
                boolean overlapsOrAdjoins = false;
                
                if (revision1 > revision2) {
                    child.myRemainingRanges.reverse();
                }
                for(int j = 0; j < child.myRemainingRanges.getSize(); j++) {
                    start = child.myRemainingRanges.getRanges()[j].getStartRevision();
                    end = child.myRemainingRanges.getRanges()[j].getEndRevision();
                    
                    if ((start <= gap[0] && gap[1] < end) || (start < gap[0] && gap[1] <= end)) {
                        properSubset = true;
                        break;
                    } else if (gap[0] == start && gap[1] == end) {
                        equals = true;
                        break;
                    } else if (gap[0] <= end && start <= gap[1]) {
                        overlapsOrAdjoins = true;
                        break;
                    }
                }
                if (!properSubset) {
                    if (overlapsOrAdjoins) {
                        child.myRemainingRanges = child.myRemainingRanges.merge(myImplicitSrcGap);
                    } else if (equals) {
                        child.myRemainingRanges = child.myRemainingRanges.diff(myImplicitSrcGap, false);
                    }
                }
                if (revision1 > revision2) {
                    child.myRemainingRanges.reverse();
                }
            }
            index++;
    	}
        
    	if (childrenWithMergeInfo.size() > 1) {
    		MergePath child = (MergePath) childrenWithMergeInfo.get(0);
    		if (child.myRemainingRanges.isEmpty()) {
    			SVNMergeRange dummyRange = new SVNMergeRange(revision2, revision2, true);
    			child.myRemainingRanges = new SVNMergeRangeList(dummyRange);
                myIsTargetHasDummyMergeRange = true;
    		}
    	}
    }
    
    public ISVNReusableEditor driveMergeReportEditor(File targetWCPath, SVNURL url1, long revision1,
    		SVNURL url2, final long revision2, final List childrenWithMergeInfo, final boolean isRollBack, 
    		SVNDepth depth, SVNAdminArea adminArea, SVNMergeCallback mergeCallback, 
            ISVNReusableEditor editor) throws SVNException {
        final boolean honorMergeInfo = isHonorMergeInfo();
        long defaultStart = revision1;
        long targetStart = revision1;
        
        if (honorMergeInfo) {
            if (myIsTargetHasDummyMergeRange) {
                targetStart = revision2;
            } else if (childrenWithMergeInfo != null && !childrenWithMergeInfo.isEmpty()) {
                MergePath targetMergePath = (MergePath) childrenWithMergeInfo.get(0);
                SVNMergeRangeList remainingRanges = targetMergePath.myRemainingRanges; 
                if (remainingRanges != null && !remainingRanges.isEmpty()) {
                    SVNMergeRange[] ranges = remainingRanges.getRanges();
                    SVNMergeRange range = ranges[0];
                    if ((!isRollBack && range.getStartRevision() > revision2) ||
                            (isRollBack && range.getStartRevision() < revision2)) {
                        targetStart = revision2;
                    } else {
                        targetStart = range.getStartRevision();
                    }
                }
            }
        }

        editor = getMergeReportEditor(defaultStart, revision2, adminArea, depth, mergeCallback, editor);

        SVNURL oldURL = ensureSessionURL(myRepository2, url1);
        try {
            final SVNDepth reportDepth = depth;
            final long reportStart = targetStart;
            final String targetPath = targetWCPath.getAbsolutePath().replace(File.separatorChar, '/');

            SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "repository1 location: " + myRepository1.getLocation());
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "repository2 location: " + myRepository2.getLocation());

            myRepository1.diff(url2, revision2, revision2, null, myIsIgnoreAncestry, depth, true,
                    new ISVNReporterBaton() {
                        public void report(ISVNReporter reporter) throws SVNException {
                            
                            reporter.setPath("", null, reportStart, reportDepth, false);

                            if (honorMergeInfo && childrenWithMergeInfo != null) {
                            	for (int i = 1; i < childrenWithMergeInfo.size(); i++) {
                                   MergePath childMergePath = (MergePath) childrenWithMergeInfo.get(i);
                                   MergePath parent = null;
                                   if (childMergePath == null || childMergePath.myIsAbsent) {
                                       continue;
                                   }
                                   Object[] childrenWithMergeInfoArray = childrenWithMergeInfo.toArray();
                                   int parentIndex = findNearestAncestor(childrenWithMergeInfoArray, false, childMergePath.myPath);
                                   if (parentIndex >= 0 && parentIndex < childrenWithMergeInfoArray.length) {
                                       parent = (MergePath) childrenWithMergeInfoArray[parentIndex];
                                   }
                                   String parentPath = parent.myPath.getAbsolutePath().replace(File.separatorChar, '/');
                                   boolean nearestParentIsTarget = parentPath.equals(targetPath);
                                   
                                   SVNMergeRange range = null;
                                   if (childMergePath.myRemainingRanges != null && 
                                           !childMergePath.myRemainingRanges.isEmpty()) {
                                       SVNMergeRangeList remainingRangesList = childMergePath.myRemainingRanges; 
                                       SVNMergeRange[] remainingRanges = remainingRangesList.getRanges();
                                       range = remainingRanges[0];
                                       
                                       if ((!isRollBack && range.getStartRevision() > revision2) ||
                                               (isRollBack && range.getStartRevision() < revision2)) {
                                           continue;
                                       } else if (parent.myRemainingRanges != null && !parent.myRemainingRanges.isEmpty()) {
                                           SVNMergeRange parentRange = parent.myRemainingRanges.getRanges()[0];
                                           SVNMergeRange childRange = childMergePath.myRemainingRanges.getRanges()[0];
                                           if (parentRange.getStartRevision() == childRange.getStartRevision()) {
                                               continue;
                                           }
                                       }
                                   } else {
                                       if ((parent.myRemainingRanges == null || parent.myRemainingRanges.isEmpty())
                                               || (nearestParentIsTarget && myIsTargetHasDummyMergeRange)) {
                                           continue;
                                       }
                                   }
                                     
                                   String childPath = childMergePath.myPath.getAbsolutePath();
                                   childPath = childPath.replace(File.separatorChar, '/');
                                   String relChildPath = childPath.substring(targetPath.length());
                                   if (relChildPath.startsWith("/")) {
                                       relChildPath = relChildPath.substring(1);
                                   }
                                   
                                   if (childMergePath.myRemainingRanges == null || 
                                           childMergePath.myRemainingRanges.isEmpty() ||
                                           (isRollBack && range.getStartRevision() < revision2) ||
                                           (!isRollBack && range.getStartRevision() > revision2)) {
                                       reporter.setPath(relChildPath, null, revision2, reportDepth, false);
                                   } else {
                                       reporter.setPath(relChildPath, null, range.getStartRevision(), 
                                               reportDepth, false);
                                   }
                                }
                            }
                            reporter.finishReport();
                        }
                    }, 
                    SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
        } finally {
        	if (oldURL != null) {
        		myRepository2.setLocation(oldURL, false);
        	}
            editor.cleanup();
        }
        
        sleepForTimeStamp();
        if (myConflictedPaths == null) {
            myConflictedPaths = mergeCallback.getConflictedPaths();
        }
             
        return editor;
    }

    public ISVNReusableEditor getMergeReportEditor(long defaultStart, long revision, SVNAdminArea adminArea, SVNDepth depth, 
            AbstractDiffCallback mergeCallback, ISVNReusableEditor editor) throws SVNException {
        if (editor == null) {
            editor = new SVNRemoteDiffEditor(adminArea, adminArea.getRoot(), mergeCallback, myRepository2,
                    defaultStart, revision, myIsDryRun, this, this);
        } else {
            editor.reset(defaultStart, revision);
        }
        return editor;
    }

    private SVNErrorMessage makeMergeConflictError(File targetPath, SVNMergeRange range) {
        SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, 
                "One or more conflicts were produced while merging r{0}:{1} into\n" + 
                "''{2}'' --\n" +
                "resolve all conflicts and rerun the merge to apply the remaining\n" + 
                "unmerged revisions", new Object[] { Long.toString(range.getStartRevision()), 
                Long.toString(range.getEndRevision()), targetPath} );
        return error;
    }
    
    private List getMergeInfoPaths(final List children, final String mergeSrcPath, 
    		SVNEntry entry, final SVNURL sourceRootURL, final long revision1, 
    		final long revision2, boolean honorMergeInfo, final SVNRepository repository, final SVNDepth depth) throws SVNException {
    	final List childrenWithMergeInfo = children == null ? new LinkedList() : children;
        ISVNEntryHandler handler = getMergeInfoEntryHandler(mergeSrcPath, sourceRootURL, revision1, revision2, repository, depth, 
                childrenWithMergeInfo);

        if (entry.isFile()) {
            handler.handleEntry(myTarget, entry);
        } else {
            myWCAccess.walkEntries(myTarget, handler, true, honorMergeInfo ? depth : SVNDepth.EMPTY);
        }
        
        if (honorMergeInfo && SVNDepth.EMPTY.compareTo(depth) < 0) {
            Collections.sort(childrenWithMergeInfo);
            for (int i = 0; i < childrenWithMergeInfo.size(); i++) {
                MergePath child = (MergePath) childrenWithMergeInfo.get(i);
                
                if (child.myHasNonInheritableMergeInfo) {
                    SVNAdminArea childArea = myWCAccess.probeTry(child.myPath, true, SVNWCAccess.INFINITE_DEPTH);
                    
                    for (Iterator entries = childArea.entries(false); entries.hasNext();) {
                        SVNEntry childEntry = (SVNEntry) entries.next();
                        if (childArea.getThisDirName().equals(childEntry.getName())) {
                            continue;
                        }
                        
                        File childPath = childArea.getFile(childEntry.getName()); 
                        MergePath childOfNonInheritable = new MergePath(childPath);
                        if (!childrenWithMergeInfo.contains(childOfNonInheritable)) {
                            if (depth == SVNDepth.FILES) {
                                SVNEntry childEntry2 = myWCAccess.getEntry(childPath, false);
                                if (childEntry2 == null || !childEntry2.isFile()) {
                                    continue;
                                }
                            }
                            childrenWithMergeInfo.add(childOfNonInheritable);
                            //TODO: optimize these repeating sorts
                            Collections.sort(childrenWithMergeInfo);
                            if (!myIsDryRun && myIsSameRepository) {
                                SVNEntry childOfNonInheritableEntry = myWCAccess.getVersionedEntry(childOfNonInheritable.myPath, false);
                                
                                Map mergeInfo = getWCMergeInfo(childOfNonInheritable.myPath, childOfNonInheritableEntry, myTarget, 
                                        SVNMergeInfoInheritance.NEAREST_ANCESTOR, false, new boolean[1]);
                                SVNPropertiesManager.recordWCMergeInfo(childPath, mergeInfo, myWCAccess);
                            }
                        }
                    }
                }
                
                if (child.myIsAbsent || (child.myIsSwitched && !myTarget.equals(child.myPath))) {
                    File parentPath = child.myPath.getParentFile();
                    int parentInd = childrenWithMergeInfo.indexOf(new MergePath(parentPath));
                    MergePath parent = parentInd != -1 ? (MergePath) childrenWithMergeInfo.get(parentInd) : null;
                    if (parent != null) {
                        parent.myHasMissingChildren = true; 
                    } else {
                        parent = new MergePath(parentPath);
                        parent.myHasMissingChildren = true;
                        childrenWithMergeInfo.add(parent);
                        //TODO: optimize these repeating sorts
                        Collections.sort(childrenWithMergeInfo);
                        i++;
                    }
                    
                    SVNAdminArea parentArea = myWCAccess.probeTry(parentPath, true, 
                            SVNWCAccess.INFINITE_DEPTH);
                    for (Iterator siblings = parentArea.entries(false); siblings.hasNext();) {
                        SVNEntry siblingEntry = (SVNEntry) siblings.next();
                        if (parentArea.getThisDirName().equals(siblingEntry.getName())) {
                            continue;
                        }
                        
                        File siblingPath = parentArea.getFile(siblingEntry.getName());
                        MergePath siblingOfMissing = new MergePath(siblingPath);
                        if (!childrenWithMergeInfo.contains(siblingOfMissing)) {
                            if (depth == SVNDepth.FILES) {
                                SVNEntry childEntry = myWCAccess.getEntry(siblingPath, false);
                                if (childEntry == null || !childEntry.isFile()) {
                                    continue;
                                }
                            }
                            childrenWithMergeInfo.add(siblingOfMissing);
                            //TODO: optimize these repeating sorts
                            Collections.sort(childrenWithMergeInfo);
                        }
                    }
                }
            }
        }
        
        return childrenWithMergeInfo;
    }

    public ISVNEntryHandler getMergeInfoEntryHandler(String mergeSrcPath, SVNURL sourceRootURL, long revision1, long revision2, SVNRepository repository, SVNDepth depth, List childrenWithMergeInfo) {
        return new MergeInfoFetcher(mergeSrcPath, sourceRootURL, revision1, revision2, repository, depth, childrenWithMergeInfo);
    }

    private boolean notifySingleFileMerge(File targetWCPath, SVNEventAction action, 
            SVNStatusType cstate, SVNStatusType pstate, SVNEvent headerEvent, 
            boolean isHeaderSent) throws SVNException {
        action = cstate == SVNStatusType.MISSING ? SVNEventAction.SKIP : action;
        SVNEvent event = SVNEventFactory.createSVNEvent(targetWCPath, SVNNodeKind.FILE, null, 
                SVNRepository.INVALID_REVISION, cstate, pstate, SVNStatusType.LOCK_INAPPLICABLE, action, 
                null, null, null);
        if (isOperativeNotification(event) && headerEvent != null && !isHeaderSent) {
            handleEvent(headerEvent, ISVNEventHandler.UNKNOWN);
            isHeaderSent = true;
        }
        this.handleEvent(event, ISVNEventHandler.UNKNOWN);
        return isHeaderSent;
    }

    private boolean isOperativeNotification(SVNEvent event) {
        return event.getContentsStatus() == SVNStatusType.CONFLICTED || 
                event.getContentsStatus() == SVNStatusType.MERGED ||
                event.getContentsStatus() == SVNStatusType.CHANGED ||
                event.getPropertiesStatus() == SVNStatusType.CONFLICTED ||
                event.getPropertiesStatus() == SVNStatusType.MERGED ||
                event.getPropertiesStatus() == SVNStatusType.CHANGED ||
                event.getAction() == SVNEventAction.UPDATE_ADD ||
                event.getAction() == SVNEventAction.TREE_CONFLICT;
    }
    
    private void calculateMergeInheritance(SVNMergeRangeList rangeList, SVNEntry entry, boolean wcPathIsMergeTarget, boolean wcPathHasMissingChild, SVNDepth depth) {
        if (entry.isFile()) {
            rangeList.setInheritable(true);
        } else if (entry.isDirectory()) {
            if (wcPathIsMergeTarget) {
                if (wcPathHasMissingChild || depth == SVNDepth.FILES || depth == SVNDepth.EMPTY) {
                    rangeList.setInheritable(false);
                } else {
                    rangeList.setInheritable(true);
                }
            } else {
                if (wcPathHasMissingChild || depth == SVNDepth.IMMEDIATES) {
                    rangeList.setInheritable(false);
                } else {
                    rangeList.setInheritable(true);
                }
            }
        }
    }
    
    private void recordSkips(String mergeInfoPath, SVNEntry targetEntry, boolean isRollback) throws SVNException {
        int numberOfSkippedPaths = mySkippedPaths != null ? mySkippedPaths.size() : 0;
            
        if (numberOfSkippedPaths > 0) {
            Map merges = new TreeMap();
            for (Iterator skippedPaths = mySkippedPaths.iterator(); skippedPaths.hasNext();) {
                File skippedPath = (File) skippedPaths.next();
                SVNStatus status = SVNStatusUtil.getStatus(skippedPath, myWCAccess);
                if (status.getContentsStatus() == SVNStatusType.STATUS_NONE || 
                        status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED) {
                    continue;
                }
                merges.put(skippedPath, new SVNMergeRangeList(new SVNMergeRange[0]));
            }
            updateWCMergeInfo(myTarget, mergeInfoPath, targetEntry, merges, isRollback);
        }
    }
    
    private void updateWCMergeInfo(File targetPath, String parentReposPath, SVNEntry entry, Map merges, boolean isRollBack) throws SVNException {
        
        for (Iterator mergesEntries = merges.entrySet().iterator(); mergesEntries.hasNext();) {
            
            Map.Entry pathToRangeList = (Map.Entry) mergesEntries.next();
            File path = (File) pathToRangeList.getKey();
            SVNMergeRangeList ranges = (SVNMergeRangeList) pathToRangeList.getValue();
            Map mergeInfo = null;
            try {
                // TODO this is a hack: assert that path is not missing if it is directory.
                SVNEntry pathEntry = entry.getAdminArea().getWCAccess().getEntry(path, false);
                if (pathEntry != null && pathEntry.isDirectory()) {
                    pathEntry.getAdminArea().getWCAccess().retrieve(path);
                }
            	mergeInfo = SVNPropertiesManager.parseMergeInfo(path, entry, false);	
            } catch (SVNException svne) {
            	if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
            		continue;
            	}
            	throw svne;
            }
            
            if (mergeInfo == null && ranges.isEmpty()) {
                mergeInfo = getWCMergeInfo(path, entry, null, 
                        SVNMergeInfoInheritance.NEAREST_ANCESTOR, true, new boolean[1]);
            }
            
            if (mergeInfo == null) {
                mergeInfo = new TreeMap();
            }
            
            String parent = targetPath.getAbsolutePath();
            parent = parent.replace(File.separatorChar, '/');
            String child = path.getAbsolutePath();
            child = child.replace(File.separatorChar, '/');
            String reposPath = null;
            if (parent.length() < child.length()) {
                String childRelPath = child.substring(parent.length());
                if (childRelPath.startsWith("/")) {
                    childRelPath = childRelPath.substring(1);
                }
                reposPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(parentReposPath, childRelPath));
            } else {
                reposPath = parentReposPath;
            }
            
            SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(reposPath);
            if (rangeList == null) {
                rangeList = new SVNMergeRangeList(new SVNMergeRange[0]);
            }
            
            if (isRollBack) {
                ranges = ranges.dup();
                ranges = ranges.reverse();
                rangeList = rangeList.diff(ranges, false);
            } else {
                rangeList = rangeList.merge(ranges);
            }
            
            mergeInfo.put(reposPath, rangeList);
            //TODO: I do not understand this:) how mergeInfo can be ever empty here????
            if (isRollBack && mergeInfo.isEmpty()) {
                mergeInfo = null;
            }
            
            SVNMergeInfoUtil.removeEmptyRangeLists(mergeInfo);
            
            try {
                SVNPropertiesManager.recordWCMergeInfo(path, mergeInfo, myWCAccess);
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.ENTRY_NOT_FOUND) {
                    throw svne;
                }
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, svne);
            }
        }
    }

    public void calculateRemainingRanges(MergePath parent, MergePath child, SVNURL sourceRootURL, SVNURL url1, long revision1, 
            SVNURL url2, long revision2, Map targetMergeInfo, Map implicitMergeInfo, SVNMergeRangeList implicitSrcGap, 
            boolean isSubtree, SVNEntry entry, SVNRepository repository) throws SVNException {
        SVNURL primaryURL = revision1 < revision2 ? url2 : url1;
        Map adjustedTargetMergeInfo = null;

        String mergeInfoPath = getPathRelativeToRoot(null, primaryURL, sourceRootURL, null, repository);
        if (implicitSrcGap != null && child.myPreMergeMergeInfo != null) {
            SVNMergeRangeList explicitMergeInfoGapRanges = (SVNMergeRangeList) child.myPreMergeMergeInfo.get(mergeInfoPath);
            if (explicitMergeInfoGapRanges != null) {
                Map gapMergeInfo = new TreeMap();
                gapMergeInfo.put(mergeInfoPath, implicitSrcGap);
                adjustedTargetMergeInfo = SVNMergeInfoUtil.removeMergeInfo(gapMergeInfo, targetMergeInfo, false);
            }
        } else {
            adjustedTargetMergeInfo = targetMergeInfo;
        }
        filterMergedRevisions(child, mergeInfoPath, 
                adjustedTargetMergeInfo, implicitMergeInfo, revision1, revision2); 
        
        if (isSubtree) {
            SVNMergeRangeList[] rangeListDiff = SVNMergeInfoUtil.diffMergeRangeLists(child.myRemainingRanges, parent.myRemainingRanges, true);
            SVNMergeRangeList deletedRangeList = rangeListDiff[0];
            SVNMergeRangeList addedRangeList = rangeListDiff[1];
            if (!deletedRangeList.isEmpty() || !addedRangeList.isEmpty()) {
                adjustDeletedSubTreeRanges(child, parent, revision1, revision2, primaryURL, repository);
            }
        }

        if ((child.myRemainingRanges == null || child.myRemainingRanges.isEmpty()) &&
                (revision2 < revision1) &&
                (entry.getRevision() <= revision2)) {
            SVNRepositoryLocation[] locations = null;
            try {
                locations = getLocations(url1, null, repository, SVNRevision.create(revision1), 
                        SVNRevision.create(entry.getRevision()), SVNRevision.UNDEFINED);
                SVNURL startURL = locations[0].getURL();
                if (startURL.equals(entry.getSVNURL())) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE, 
                            "Cannot reverse-merge a range from a path's own future history; try updating first");
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                }
            } catch (SVNException svne) {
                SVNErrorCode code = svne.getErrorMessage().getErrorCode();
                if (!(code == SVNErrorCode.FS_NOT_FOUND || code == SVNErrorCode.RA_DAV_PATH_NOT_FOUND || 
                        code == SVNErrorCode.CLIENT_UNRELATED_RESOURCES)) {
                    throw svne;
                }
            }
        }
    }
    
    private void adjustDeletedSubTreeRanges(MergePath child, MergePath parent, long revision1, long revision2, 
            SVNURL primaryURL, SVNRepository repository) throws SVNException {
        if (parent.myRemainingRanges == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                    "Assertions failed: parent must already have non-null remaining ranges set");
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        String relativePath = getPathRelativeToRoot(null, primaryURL, repository.getLocation(), null, repository);
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        
        boolean isRollback = revision2 < revision1;
        long pegRev = isRollback ? revision1 : revision2;
        long youngerRev = pegRev;
        long olderRev = isRollback ? revision2 : revision1;
        
        List locationSegments = null;
        try {
            locationSegments = repository.getLocationSegments(relativePath, pegRev, youngerRev, olderRev);
        } catch (SVNException e) {
            SVNErrorCode errCode = e.getErrorMessage().getErrorCode();
            if (errCode == SVNErrorCode.FS_NOT_FOUND || errCode == SVNErrorCode.RA_DAV_REQUEST_FAILED) {
                SVNNodeKind kind = repository.checkPath(relativePath, olderRev);
                if (kind == SVNNodeKind.NONE) {
                    child.myRemainingRanges = parent.myRemainingRanges.dup();
                } else {
                    long primaryURLDeletedRevision = repository.getDeletedRevision(relativePath, olderRev, youngerRev);
                    if (!SVNRevision.isValidRevisionNumber(primaryURLDeletedRevision)) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failed: deleted revision must exist");
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                    if (isRollback) {
                        child.myRemainingRanges = child.myRemainingRanges.reverse();
                        parent.myRemainingRanges = parent.myRemainingRanges.reverse();
                    }
                    
                    SVNMergeRangeList existingRangeList = new SVNMergeRangeList(new SVNMergeRange(olderRev, primaryURLDeletedRevision - 1, true));
                    child.myRemainingRanges = child.myRemainingRanges.intersect(existingRangeList, false);
                    
                    SVNMergeRangeList deletedRangeList = new SVNMergeRangeList(new SVNMergeRange(primaryURLDeletedRevision - 1, pegRev, true));
                    deletedRangeList = parent.myRemainingRanges.intersect(deletedRangeList, false);
                    child.myRemainingRanges = child.myRemainingRanges.merge(deletedRangeList);
                    
                    if (isRollback) {
                        child.myRemainingRanges = child.myRemainingRanges.reverse();
                        parent.myRemainingRanges = parent.myRemainingRanges.reverse();
                    }
                }
            } else {
                throw e;            
            }
        }
        
        if (locationSegments != null && !locationSegments.isEmpty()) {
            SVNLocationSegment segment = (SVNLocationSegment) locationSegments.get(locationSegments.size() - 1);
            if (segment.getStartRevision() == olderRev) {
                return;
            }
            if (isRollback) {
                child.myRemainingRanges = child.myRemainingRanges.reverse();
                parent.myRemainingRanges = parent.myRemainingRanges.reverse();
            }
            
            SVNMergeRangeList existingRangeList = new SVNMergeRangeList(new SVNMergeRange(segment.getStartRevision(), pegRev, true));
            child.myRemainingRanges = child.myRemainingRanges.intersect(existingRangeList, false);
            SVNMergeRangeList nonExistentRangeList = new SVNMergeRangeList(new SVNMergeRange(olderRev, segment.getStartRevision(), true));
            nonExistentRangeList = parent.myRemainingRanges.intersect(nonExistentRangeList, false);
            child.myRemainingRanges = child.myRemainingRanges.merge(nonExistentRangeList);

            if (isRollback) {
                child.myRemainingRanges = child.myRemainingRanges.reverse();
                parent.myRemainingRanges = parent.myRemainingRanges.reverse();
            }
        }
    }

    private void filterMergedRevisions(MergePath child, String mergeInfoPath, Map targetMergeInfo, Map implicitMergeInfo,
            long rev1, long rev2) throws SVNException {
        Map mergeInfo = implicitMergeInfo;
        SVNMergeRangeList targetRangeList = null;
        
        if (rev1 > rev2) {
            if (targetMergeInfo != null) {
                mergeInfo = SVNMergeInfoUtil.dupMergeInfo(implicitMergeInfo, null);
                SVNMergeInfoUtil.mergeMergeInfos(mergeInfo, targetMergeInfo);
            }
            targetRangeList = (SVNMergeRangeList) mergeInfo.get(mergeInfoPath);
            if (targetRangeList != null) {
                SVNMergeRangeList requestedMergeRangeList = new SVNMergeRangeList(new SVNMergeRange(rev1, rev2, true));
                requestedMergeRangeList = requestedMergeRangeList.reverse();
                child.myRemainingRanges = targetRangeList.intersect(requestedMergeRangeList, false);
                child.myRemainingRanges = child.myRemainingRanges.reverse();
            } else {
                child.myRemainingRanges = new SVNMergeRangeList(new SVNMergeRange[0]);
            }
        } else {
            if (getOptions().isAllowAllForwardMergesFromSelf()) {
                if (targetMergeInfo != null) {
                    targetRangeList = (SVNMergeRangeList) targetMergeInfo.get(mergeInfoPath);
                }
            } else {
                if (targetMergeInfo != null) {
                    mergeInfo = SVNMergeInfoUtil.dupMergeInfo(implicitMergeInfo, null);
                    mergeInfo = SVNMergeInfoUtil.mergeMergeInfos(mergeInfo, targetMergeInfo);
                }
                targetRangeList = (SVNMergeRangeList) mergeInfo.get(mergeInfoPath);
            }
            if (targetRangeList != null) {
                SVNMergeRangeList requestedRangeList = new SVNMergeRangeList(new SVNMergeRange(rev1, rev2, true));
                child.myRemainingRanges = requestedRangeList.diff(targetRangeList, false);
            } else {
                child.myRemainingRanges = new SVNMergeRangeList(new SVNMergeRange(rev1, rev2, true));
            }
        }
    }

    private File loadFile(SVNRepository repository, long revision, 
                          SVNProperties properties, SVNAdminArea adminArea) throws SVNException {
        File tmpDir = adminArea.getAdminTempDirectory();
        File result = SVNFileUtil.createUniqueFile(tmpDir, ".merge", ".tmp", false);
        
        OutputStream os = null;
        try {
            os = SVNFileUtil.openFileForWriting(result); 
            repository.getFile("", revision, properties, 
                               new SVNCancellableOutputStream(os, this));
        } finally {
            SVNFileUtil.closeFile(os);
        }
        return result;
    }

    private static SVNProperties computePropsDiff(SVNProperties props1, SVNProperties props2) {
        SVNProperties propsDiff = new SVNProperties();
        for (Iterator names = props2.nameSet().iterator(); names.hasNext();) {
            String newPropName = (String) names.next();
            if (props1.containsName(newPropName)) {
                // changed.
                SVNPropertyValue oldValue = props2.getSVNPropertyValue(newPropName);
                if (!oldValue.equals(props1.getSVNPropertyValue(newPropName))) {
                    propsDiff.put(newPropName, props2.getSVNPropertyValue(newPropName));
                }
            } else {
                // added.
                propsDiff.put(newPropName, props2.getSVNPropertyValue(newPropName));
            }
        }
        for (Iterator names = props1.nameSet().iterator(); names.hasNext();) {
            String oldPropName = (String) names.next();
            if (!props2.containsName(oldPropName)) {
                // deleted
                propsDiff.put(oldPropName, (SVNPropertyValue) null);
            }
        }
        return propsDiff;
    }

    private static SVNProperties filterProperties(SVNProperties props1, boolean leftRegular,
            boolean leftEntry, boolean leftWC) {
        SVNProperties result = new SVNProperties();
        for (Iterator names = props1.nameSet().iterator(); names.hasNext();) {
            String propName = (String) names.next();
            if (!leftEntry && propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                continue;
            }
            if (!leftWC && propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                continue;
            }
            if (!leftRegular
                    && !(propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX) || propName
                            .startsWith(SVNProperty.SVN_WC_PREFIX))) {
                continue;
            }
            result.put(propName, props1.getSVNPropertyValue(propName));
        }
        return result;
    }

    protected class MergeSource {
        private SVNURL myURL1;
        private long myRevision1;
        private SVNURL myURL2;
        private long myRevision2;

        public SVNURL getURL1() {
            return myURL1;
        }

        public SVNURL getURL2() {
            return myURL2;
        }
        
        public long getRevision1() {
            return myRevision1;
        }
        
        public long getRevision2() {
            return myRevision2;
        }
    }
    
    protected static class MergeAction {
        public static final MergeAction MERGE = new MergeAction();
        public static final MergeAction ROLL_BACK = new MergeAction();
        public static final MergeAction NO_OP = new MergeAction();
    }
    
    protected class MergePath implements Comparable {
        protected File myPath;
        protected boolean myHasMissingChildren;
        protected boolean myIsSwitched;
        protected boolean myHasNonInheritableMergeInfo;
        protected boolean myIsAbsent;
        protected boolean myIsIndirectMergeInfo;
        protected boolean myIsScheduledForDeletion;
        public SVNMergeRangeList myRemainingRanges;
        protected Map myPreMergeMergeInfo;
        protected Map myImplicitMergeInfo;
        
        public MergePath() {
        }

        public MergePath(File path) {
            myPath = path;
        }
        
        public int compareTo(Object obj) {
            if (obj == null || obj.getClass() != MergePath.class) {
                return -1;
            }
            MergePath mergePath = (MergePath) obj; 
            if (this == mergePath) {
                return 0;
            }
            return myPath.compareTo(mergePath.myPath);
        }
        
        public boolean equals(Object obj) {
            return compareTo(obj) == 0;
        }
        
        public String toString() {
            return myPath.toString();
        }
    }
    
    private class LogHandlerFilter implements ISVNLogEntryHandler {
        ISVNLogEntryHandler myRealHandler;
        SVNMergeRangeList myRangeList;
        
        public LogHandlerFilter(ISVNLogEntryHandler handler, SVNMergeRangeList rangeList) {
            myRealHandler = handler;
            myRangeList = rangeList;
        }
        
        public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
            checkCancelled();
            SVNMergeRange range = new SVNMergeRange(logEntry.getRevision() - 1, logEntry.getRevision(), true);
            SVNMergeRangeList thisRangeList = new SVNMergeRangeList(range);
            SVNMergeRangeList intersection = thisRangeList.intersect(myRangeList, true);
            if (intersection == null || intersection.isEmpty()) {
                return;
            }
            
            if (intersection.getSize() != 1) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                        "assertion failure in SVNMergeDriver.LogHandlerFilter.handleLogEntry: intersection list " +
                        "size is {0}", new Integer(intersection.getSize()));
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
            if (myRealHandler != null) {
                myRealHandler.handleLogEntry(logEntry);
            }
        }
    }

    private static class CopyFromReceiver implements ISVNLogEntryHandler {
        private String myTargetPath;
        private SVNLocationEntry myCopyFromLocation;
        
        public CopyFromReceiver(String targetPath) {
            myTargetPath = targetPath;
        }
        
        public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
            if (myCopyFromLocation != null) {
                return;
            }
            
            Map changedPaths = logEntry.getChangedPaths();
            if (changedPaths != null && !changedPaths.isEmpty()) {
                TreeMap sortedChangedPaths = new TreeMap(Collections.reverseOrder());
                sortedChangedPaths.putAll(changedPaths);
                for (Iterator changedPathsIter = sortedChangedPaths.keySet().iterator(); changedPathsIter.hasNext();) {
                    String changedPath = (String) changedPathsIter.next();
                    SVNLogEntryPath logEntryPath = (SVNLogEntryPath) sortedChangedPaths.get(changedPath);
                    if (logEntryPath.getCopyPath() != null && 
                        SVNRevision.isValidRevisionNumber(logEntryPath.getCopyRevision()) && 
                        SVNPathUtil.isAncestor(changedPath, myTargetPath)) {
                        String copyFromPath = null;
                        if (changedPath.equals(myTargetPath)) {
                            copyFromPath = logEntryPath.getCopyPath();
                        } else {
                            String relPath = myTargetPath.substring(changedPath.length());
                            if (relPath.startsWith("/")) {
                                relPath = relPath.substring(1);
                            }
                            copyFromPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(logEntryPath.getCopyPath(), relPath));
                        }
                        myCopyFromLocation = new SVNLocationEntry(logEntryPath.getCopyRevision(), copyFromPath);
                        break;
                    }
                }
            }
        } 

        public SVNLocationEntry getCopyFromLocation() {
            return myCopyFromLocation;
        }
    }

    protected class SubTreeMergeInfoHandler implements ISVNEntryHandler {
        private File myTargetPath;
        private SVNURL myTargetReposRoot;
        private Collection mySubTreesWithMergeInfoPaths;
        
        public SubTreeMergeInfoHandler(File targetPath, SVNURL targetReposRoot) {
            mySubTreesWithMergeInfoPaths = new LinkedList();
            myTargetPath = targetPath;
            myTargetReposRoot = targetReposRoot;
        }

        public void handleEntry(File path, SVNEntry entry) throws SVNException {
            SVNAdminArea adminArea = entry.getAdminArea();
            if (entry.getKind() == SVNNodeKind.DIR && !adminArea.getThisDirName().equals(entry.getName()) && !entry.isAbsent()) {
                return;
            }
            
            SVNVersionedProperties props = adminArea.getProperties(entry.getName());
            String mergeInfoProp = props.getStringPropertyValue(SVNProperty.MERGE_INFO);
            if (mergeInfoProp != null || path.equals(myTargetPath)) {
                String storedPath = getPathRelativeToRoot(path, null, myTargetReposRoot, adminArea.getWCAccess(), null);
                mySubTreesWithMergeInfoPaths.add(storedPath);
            }
        }

        public void handleError(File path, SVNErrorMessage error) throws SVNException {
            while (error.hasChildErrorMessage()) {
                error = error.getChildErrorMessage();
            }
            if (error.getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND ||
                    error.getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                return;
            }
            SVNErrorManager.error(error, SVNLogType.DEFAULT);
        }
        
        public Collection getSubTreesWithMergeInfoPaths() {
            return mySubTreesWithMergeInfoPaths;
        }
    }
    
    protected class MergeInfoFetcher implements ISVNEntryHandler {

        private String myMergeSrcPath;
        private SVNURL mySourceRootURL;
        private long myRevision1;
        private long myRevision2;
        private SVNRepository myRepository;
        private SVNDepth myDepth;
        private List myChildrenWithMergeInfo;

        protected MergeInfoFetcher(String mergeSrcPath, SVNURL sourceRootURL, long revision1, long revision2, SVNRepository repository, SVNDepth depth, List childrenWithMergeInfo) {
            myMergeSrcPath = mergeSrcPath;
            mySourceRootURL = sourceRootURL;
            myRevision1 = revision1;
            myRevision2 = revision2;
            myRepository = repository;
            myDepth = depth;
            myChildrenWithMergeInfo = childrenWithMergeInfo;
        }

        public MergeInfoFetcher() {
        }

        public void handleEntry(File path, SVNEntry entry) throws SVNException {
            File target = myTarget;
            SVNAdminArea adminArea = entry.getAdminArea();
            if (entry.isDirectory() && !adminArea.getThisDirName().equals(entry.getName()) &&
                    !entry.isAbsent()) {
                return;
            }

            if (entry.isDeleted()) {
                return;
            }

            boolean isSwitched = false;
            boolean hasMergeInfoFromMergeSrc = false;
            boolean pathIsMergeTarget = target.equals(path);
            String mergeInfoProp = null;
            if (!entry.isAbsent() && !entry.isScheduledForDeletion()) {
                SVNVersionedProperties props = adminArea.getProperties(entry.getName());
                mergeInfoProp = props.getStringPropertyValue(SVNProperty.MERGE_INFO);
                if (mergeInfoProp != null && !pathIsMergeTarget) {
                    String relToTargetPath = SVNPathUtil.getRelativePath(target.getAbsolutePath(),
                            path.getAbsolutePath());
                    String mergeSrcChildPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(myMergeSrcPath,
                            relToTargetPath));
                    Map mergeInfo = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(mergeInfoProp),
                            null);
                    if (mergeInfoProp == null || mergeInfoProp.length() == 0 || mergeInfo.containsKey(mergeSrcChildPath)) {
                        hasMergeInfoFromMergeSrc = true;
                    } else {
                        SVNURL mergeInfoURL = mySourceRootURL.appendPath(mergeSrcChildPath, false);
                        SVNRevision pegRevision = SVNRevision.create(myRevision1 < myRevision2 ?
                                myRevision2 : myRevision1);
                        SVNErrorCode code = null;
                        SVNURL originalURL = null;
                        try {
                            originalURL = ensureSessionURL(myRepository, mergeInfoURL);
                            getLocations(mergeInfoURL, null, myRepository, pegRevision,
                                    SVNRevision.create(myRevision1), SVNRevision.create(myRevision2));
                            hasMergeInfoFromMergeSrc = true;
                        } catch (SVNException svne) {
                            code = svne.getErrorMessage().getErrorCode();
                            if (code != SVNErrorCode.FS_NOT_FOUND &&
                                    code != SVNErrorCode.RA_DAV_PATH_NOT_FOUND &&
                                    code != SVNErrorCode.CLIENT_UNRELATED_RESOURCES) {
                                throw svne;
                            }
                        } finally {
                            if (originalURL != null) {
                                myRepository.setLocation(originalURL, false);
                            }
                        }
                    }
                }

                isSwitched = SVNWCManager.isEntrySwitched(path, entry);
            }

            File parent = path.getParentFile();
            if (pathIsMergeTarget || hasMergeInfoFromMergeSrc ||
                    entry.isScheduledForDeletion() ||
                    isSwitched ||
                    entry.getDepth() == SVNDepth.EMPTY ||
                    entry.getDepth() == SVNDepth.FILES ||
                    entry.isAbsent() ||
                    (myDepth == SVNDepth.IMMEDIATES && entry.isDirectory() && parent != null && 
                            !parent.equals(path) && parent.equals(target)) ||
                    (myDepth == SVNDepth.FILES && entry.isFile() && parent != null && parent.equals(target))) {

                boolean hasMissingChild = entry.getDepth() == SVNDepth.EMPTY || entry.getDepth() == SVNDepth.FILES ||
                        (myDepth == SVNDepth.IMMEDIATES && entry.isDirectory() && parent != null && parent.equals(target));

                boolean hasNonInheritable = false;
                if (mergeInfoProp != null && mergeInfoProp.indexOf(SVNMergeRangeList.MERGE_INFO_NONINHERITABLE_STRING) != -1) {
                    hasNonInheritable = true;
                }

                if (!hasNonInheritable && (entry.getDepth() == SVNDepth.EMPTY ||
                        entry.getDepth() == SVNDepth.FILES)) {
                    hasNonInheritable = true;
                }

                MergePath child = new MergePath();
                child.myPath = path;
                child.myHasMissingChildren = hasMissingChild;
                child.myIsSwitched = isSwitched;
                child.myIsAbsent = entry.isAbsent();
                child.myIsScheduledForDeletion = entry.isScheduledForDeletion();
                child.myHasNonInheritableMergeInfo = hasNonInheritable;
                myChildrenWithMergeInfo.add(child);
            }
        }

        public void handleError(File path, SVNErrorMessage error) throws SVNException {
            while (error.hasChildErrorMessage()) {
                error = error.getChildErrorMessage();
            }
            if (error.getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND ||
                    error.getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                return;
            }
            SVNErrorManager.error(error, SVNLogType.DEFAULT);
        }
    }
}
