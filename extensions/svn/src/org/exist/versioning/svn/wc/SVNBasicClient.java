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
package org.exist.versioning.svn.wc;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.util.SVNMergeInfoUtil;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.SVNPropertiesManager;
import org.exist.versioning.svn.internal.wc.SVNWCManager;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.ISVNPathListHandler;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * The <b>SVNBasicClient</b> is the base class of all 
 * <b>SVN</b>*<b>Client</b> classes that provides a common interface
 * and realization.
 * 
 * <p>
 * All of <b>SVN</b>*<b>Client</b> classes use inherited methods of
 * <b>SVNBasicClient</b> to access Working Copies metadata, to create 
 * a driver object to access a repository if it's necessary, etc. In addition
 * <b>SVNBasicClient</b> provides some interface methods  - such as those
 * that allow you to set your {@link ISVNEventHandler event handler}, 
 * obtain run-time configuration options, and others. 
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNBasicClient implements ISVNEventHandler {

    private ISVNRepositoryPool myRepositoryPool;
    private ISVNOptions myOptions;
    private ISVNEventHandler myEventDispatcher;
    private List myPathPrefixesStack;
    private boolean myIsIgnoreExternals;
    private boolean myIsLeaveConflictsUnresolved;
    private ISVNDebugLog myDebugLog;
    private ISVNPathListHandler myPathListHandler;

    protected SVNBasicClient(final ISVNAuthenticationManager authManager, ISVNOptions options) {
        this(new DefaultSVNRepositoryPool(authManager == null ? SVNWCUtil.createDefaultAuthenticationManager() : authManager, options, 0, false), options);
    }

    protected SVNBasicClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        myRepositoryPool = repositoryPool;
        setOptions(options);
        myPathPrefixesStack = new LinkedList();
    }
    
    /**
     * Gets run-time configuration options used by this object.
     * 
     * @return the run-time options being in use
     */
    public ISVNOptions getOptions() {
        return myOptions;
    }
    
    /**
     * Sets run-time global configuration options to this object.
     * 
     * @param options  the run-time configuration options 
     */
    public void setOptions(ISVNOptions options) {
        myOptions = options;
        if (myOptions == null) {
            myOptions = SVNWCUtil.createDefaultOptions(true);
        }
    }
    
    /**
     * Sets externals definitions to be ignored or not during
     * operations.
     * 
     * <p>
     * For example, if external definitions are set to be ignored
     * then a checkout operation won't fetch them into a Working Copy.
     * 
     * @param ignore  <span class="javakeyword">true</span> to ignore
     *                externals definitions, <span class="javakeyword">false</span> - 
     *                not to
     * @see           #isIgnoreExternals()
     */
    public void setIgnoreExternals(boolean ignore) {
        myIsIgnoreExternals = ignore;
    }
    
    /**
     * Determines if externals definitions are ignored.
     * 
     * @return <span class="javakeyword">true</span> if ignored,
     *         otherwise <span class="javakeyword">false</span>
     * @see    #setIgnoreExternals(boolean)
     */
    public boolean isIgnoreExternals() {
        return myIsIgnoreExternals;
    }
    /**
     * Sets (or unsets) all conflicted working files to be untouched
     * by update and merge operations.
     * 
     * <p>
     * By default when a file receives changes from the repository 
     * that are in conflict with local edits, an update operation places
     * two sections for each conflicting snatch into the working file 
     * one of which is a user's local edit and the second is the one just 
     * received from the repository. Like this:
     * <pre class="javacode">
     * <<<<<<< .mine
     * user's text
     * =======
     * received text
     * >>>>>>> .r2</pre><br /> 
     * Also the operation creates three temporary files that appear in the 
     * same directory as the working file. Now if you call this method with 
     * <code>leave</code> set to <span class="javakeyword">true</span>,
     * an update will still create temporary files but won't place those two
     * sections into your working file. And this behaviour also concerns
     * merge operations: any merging to a conflicted file will be prevented. 
     * In addition if there is any registered event
     * handler for an <b>SVNDiffClient</b> or <b>SVNUpdateClient</b> 
     * instance then the handler will be dispatched an event with 
     * the status type set to {@link SVNStatusType#CONFLICTED_UNRESOLVED}. 
     * 
     * <p>
     * The default value is <span class="javakeyword">false</span> until
     * a caller explicitly changes it calling this method. 
     * 
     * @param leave  <span class="javakeyword">true</span> to prevent 
     *               conflicted files from merging (all merging operations 
     *               will be skipped), otherwise <span class="javakeyword">false</span>
     * @see          #isLeaveConflictsUnresolved()              
     * @see          SVNUpdateClient
     * @see          SVNDiffClient
     * @see          ISVNEventHandler
     * @deprecated   this method should not be used anymore
     */
    public void setLeaveConflictsUnresolved(boolean leave) {
        myIsLeaveConflictsUnresolved = leave;
    }
    
    /**
     * Determines if conflicted files should be left unresolved
     * preventing from merging their contents during update and merge 
     * operations.
     *  
     * @return     <span class="javakeyword">true</span> if conflicted files
     *             are set to be prevented from merging, <span class="javakeyword">false</span>
     *             if there's no such restriction
     * @see        #setLeaveConflictsUnresolved(boolean)
     * @deprecated this method should not be used anymore
     */
    public boolean isLeaveConflictsUnresolved() {
        return myIsLeaveConflictsUnresolved;
    }
    
    /**
     * Sets an event handler for this object. This event handler
     * will be dispatched {@link SVNEvent} objects to provide 
     * detailed information about actions and progress state 
     * of version control operations performed by <b>do</b>*<b>()</b>
     * methods of <b>SVN</b>*<b>Client</b> classes.
     * 
     * @param dispatcher an event handler
     */
    public void setEventHandler(ISVNEventHandler dispatcher) {
        myEventDispatcher = dispatcher;
    }

    /**
     * Sets a path list handler implementation to this object.
     * @param handler  handler implementation
     * @since          1.2.0
     */
    public void setPathListHandler(ISVNPathListHandler handler) {
        myPathListHandler = handler;
    }
    
    /**
     * Sets a logger to write debug log information to.
     * 
     * @param log a debug logger
     */
    public void setDebugLog(ISVNDebugLog log) {
        myDebugLog = log;
    }
    
    /**
     * Returns the debug logger currently in use.  
     * 
     * <p>
     * If no debug logger has been specified by the time this call occurs, 
     * a default one (returned by <code>org.tmatesoft.svn.util.SVNDebugLog.getDefaultLog()</code>) 
     * will be created and used.
     * 
     * @return a debug logger
     */
    public ISVNDebugLog getDebugLog() {
        if (myDebugLog == null) {
            return SVNDebugLog.getDefaultLog();
        }
        return myDebugLog;
    }
    
    /**
     * Returns the root of the repository. 
     * 
     * <p/>
     * If <code>path</code> is not <span class="javakeyword">null</span> and <code>pegRevision</code> is 
     * either {@link SVNRevision#WORKING} or {@link SVNRevision#BASE}, then attempts to fetch the repository 
     * root from the working copy represented by <code>path</code>. If these conditions are not met or if the 
     * repository root is not recorded in the working copy, then a repository connection is established 
     * and the repository root is fetched from the session. 
     * 
     * <p/>
     * When fetching the repository root from the working copy and if <code>access</code> is 
     * <span class="javakeyword">null</span>, a new working copy access will be created and the working copy 
     * will be opened non-recursively for reading only. 
     * 
     * <p/>
     * All necessary cleanup (session or|and working copy close) will be performed automatically as the routine 
     * finishes. 
     * 
     * @param  path           working copy path
     * @param  url            repository url
     * @param  pegRevision    revision in which the target is valid
     * @param  adminArea      working copy administrative area object
     * @param  access         working copy access object
     * @return                repository root url
     * @throws SVNException 
     * @since                 1.2.0         
     */
    public SVNURL getReposRoot(File path, SVNURL url, SVNRevision pegRevision, SVNAdminArea adminArea, 
            SVNWCAccess access) throws SVNException {
        SVNURL reposRoot = null;
        if (path != null && (pegRevision == SVNRevision.WORKING || pegRevision == SVNRevision.BASE)) {
            if (access == null) {
                access = createWCAccess();
            } 
            
            boolean needCleanUp = false; 
            try {
                if (adminArea == null) {
                    adminArea = access.probeOpen(path, false, 0);
                    needCleanUp = true;
                }
                SVNEntry entry = access.getVersionedEntry(path, false);
                url = getEntryLocation(path, entry, null, SVNRevision.UNDEFINED);
                reposRoot = entry.getRepositoryRootURL();
            } finally {
                if (needCleanUp) {
                    access.closeAdminArea(path);
                }
            }
        }
       
        if (reposRoot == null) {
        	SVNRepository repos = null;
        	try {
            	repos = createRepository(url, path, null, pegRevision, pegRevision, null);
            	reposRoot = repos.getRepositoryRoot(true); 
            } finally {
                if (repos != null) {
                    repos.closeSession();
                }
            }
        }
        
        return reposRoot;
    }
    
    protected void sleepForTimeStamp() {
        if (myPathPrefixesStack == null || myPathPrefixesStack.isEmpty()) {
            SVNFileUtil.sleepForTimestamp();
        }
    }

    protected SVNRepository createRepository(SVNURL url, File path, SVNWCAccess access, boolean mayReuse) throws SVNException {
        String uuid = null;
        if (access != null) {
            SVNEntry entry = access.getEntry(path, false);
            if (entry != null) {
                uuid = entry.getUUID();
            }
        }
        return createRepository(url, uuid, mayReuse);
    }
    
    protected SVNRepository createRepository(SVNURL url, String uuid, boolean mayReuse) throws SVNException {
        SVNRepository repository = null;
        if (myRepositoryPool == null) {
            repository = SVNRepositoryFactory.create(url, null);
        } else {
            repository = myRepositoryPool.createRepository(url, mayReuse);
        }
        
        if (uuid != null) {
            String reposUUID = repository.getRepositoryUUID(true);
            if (!uuid.equals(reposUUID)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_UUID_MISMATCH, 
                        "Repository UUID ''{0}'' doesn''t match expected UUID ''{1}''", 
                        new Object[] { reposUUID, uuid });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        
        repository.setDebugLog(getDebugLog());
        repository.setCanceller(getEventDispatcher());
        return repository;
    }
    
    protected ISVNRepositoryPool getRepositoryPool() {
        return myRepositoryPool;
    }

    protected void dispatchEvent(SVNEvent event) throws SVNException {
        dispatchEvent(event, ISVNEventHandler.UNKNOWN);

    }

    protected void dispatchEvent(SVNEvent event, double progress) throws SVNException {
        if (myEventDispatcher != null) {
            try {
                myEventDispatcher.handleEvent(event, progress);
            } catch (SVNException e) {
                throw e;
            } catch (Throwable th) {
                SVNDebugLog.getDefaultLog().logSevere(SVNLogType.WC, th);
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                        "Error while dispatching event: {0}", new Object[] { th.getMessage() }, 
                        SVNErrorMessage.TYPE_ERROR, th);
                SVNErrorManager.error(err, th, SVNLogType.DEFAULT);
            }
        }
    }
    
    /**
     * Removes or adds a path prefix. This method is not intended for 
     * users (from an API point of view). 
     * 
     * @param prefix a path prefix
     */
    public void setEventPathPrefix(String prefix) {
        if (prefix == null && !myPathPrefixesStack.isEmpty()) {
            myPathPrefixesStack.remove(myPathPrefixesStack.size() - 1);
        } else if (prefix != null) {
            myPathPrefixesStack.add(prefix);
        }
    }

    protected ISVNEventHandler getEventDispatcher() {
        return myEventDispatcher;
    }

    protected SVNWCAccess createWCAccess() {
        return createWCAccess(null);
    }

    protected SVNWCAccess createWCAccess(final String pathPrefix) {
        ISVNEventHandler eventHandler = null;
        if (pathPrefix != null) {
            eventHandler = new ISVNEventHandler() {
                public void handleEvent(SVNEvent event, double progress) throws SVNException {
                    dispatchEvent(event, progress);
                }

                public void checkCancelled() throws SVNCancelException {
                    SVNBasicClient.this.checkCancelled();
                }
            };
        } else {
            eventHandler = this;
        }
        SVNWCAccess access = SVNWCAccess.newInstance(eventHandler);
        access.setOptions(myOptions);
        return access;
    }

    /**
     * Dispatches events to the registered event handler (if any). 
     * 
     * @param event       the current event
     * @param progress    progress state (from 0 to 1)
     * @throws SVNException
     */
    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        dispatchEvent(event, progress);
    }
    
    /**
     * Handles a next working copy path with the {@link ISVNPathListHandler path list handler} 
     * if any was provided to this object through {@link #setPathListHandler(ISVNPathListHandler)}.
     * 
     * <p/>
     * Note: used by <code>SVNKit</code> internals.
     * 
     * @param  path            working copy path 
     * @throws SVNException 
     * @since                  1.2.0
     */
    public void handlePathListItem(File path) throws SVNException {
        if (myPathListHandler != null && path != null) {
            myPathListHandler.handlePathListItem(path);
        }
    }

    
    /**
     * Redirects this call to the registered event handler (if any).
     * 
     * @throws SVNCancelException  if the current operation
     *                             was cancelled
     */
    public void checkCancelled() throws SVNCancelException {
        if (myEventDispatcher != null) {
            myEventDispatcher.checkCancelled();
        }
    }
    
    protected long getRevisionNumber(SVNRevision revision, SVNRepository repository, File path) throws SVNException {
        return getRevisionNumber(revision, null, repository, path);
    }

    protected long getRevisionNumber(SVNRevision revision, long[] latestRevisionNumber, SVNRepository repository, 
            File path) throws SVNException {
        if (repository == null && (revision == SVNRevision.HEAD || revision.getDate() != null)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_RA_ACCESS_REQUIRED);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (revision.getNumber() >= 0) {
            return revision.getNumber();
        } else if (revision.getDate() != null) {
            return repository.getDatedRevision(revision.getDate());
        } else if (revision == SVNRevision.HEAD) {
            if (latestRevisionNumber != null && latestRevisionNumber.length > 0 && SVNRevision.isValidRevisionNumber(latestRevisionNumber[0])) {
                return latestRevisionNumber[0]; 
            }
            long latestRevision = repository.getLatestRevision(); 
            if (latestRevisionNumber != null && latestRevisionNumber.length > 0) {
                latestRevisionNumber[0] = latestRevision;
            }
            return latestRevision;
        } else if (!revision.isValid()) {
            return -1;
        } else if (revision == SVNRevision.COMMITTED || revision == SVNRevision.WORKING || 
                revision == SVNRevision.BASE || revision == SVNRevision.PREVIOUS) {
            if (path == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_VERSIONED_PATH_REQUIRED);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            SVNWCAccess wcAccess = createWCAccess();
            wcAccess.probeOpen(path, false, 0);
            SVNEntry entry = null;
            try {
                entry = wcAccess.getVersionedEntry(path, false);
            } finally {
                wcAccess.close();
            }
            
            if (revision == SVNRevision.WORKING || revision == SVNRevision.BASE) {
                return entry.getRevision();
            }
            if (entry.getCommittedRevision() < 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Path ''{0}'' has no committed revision", path);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            return revision == SVNRevision.PREVIOUS ? entry.getCommittedRevision() - 1 : entry.getCommittedRevision();            
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Unrecognized revision type requested for ''{0}''", path != null ? path : (Object) repository.getLocation());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return -1;
    }

    protected SVNRepository createRepository(SVNURL url, File path, SVNAdminArea area, SVNRevision pegRevision, 
            SVNRevision revision, long[] pegRev) throws SVNException {
        if (url == null) {
            SVNURL pathURL = getURL(path);
            if (pathURL == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                        "''{0}'' has no URL", path);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }

        SVNRevision startRevision = revision;
        SVNRevision[] resolvedRevisions = resolveRevisions(pegRevision, startRevision, url != null, true);
        pegRevision = resolvedRevisions[0];
        startRevision = resolvedRevisions[1];
        SVNRepositoryLocation[] locations = getLocations(url, path, null, pegRevision, startRevision, 
                SVNRevision.UNDEFINED);
        url = locations[0].getURL();
        long actualRevision = locations[0].getRevisionNumber();
        SVNRepository repository = createRepository(url, area != null ? area.getRoot() : null, 
                area != null ? area.getWCAccess() : null, true);
        actualRevision = getRevisionNumber(SVNRevision.create(actualRevision), repository, path);
        if (actualRevision < 0) {
            actualRevision = repository.getLatestRevision();
        }
        if (pegRev != null && pegRev.length > 0) {
            pegRev[0] = actualRevision;
        }
        return repository;
    }

    protected SVNRevision[] resolveRevisions(SVNRevision pegRevision, SVNRevision revision, boolean isURL,
            boolean noticeLocalModifications) {
        if (!pegRevision.isValid()) {
            if (isURL) {
                pegRevision = SVNRevision.HEAD;
            } else {
                if (noticeLocalModifications) {
                    pegRevision = SVNRevision.WORKING;
                } else {
                    pegRevision = SVNRevision.BASE;
                }
            }
        }

        if (!revision.isValid()) {
            revision = pegRevision;
        }
        return new SVNRevision[] { pegRevision, revision };
    }
    
    protected void elideMergeInfo(SVNWCAccess access, File path, SVNEntry entry,
            File wcElisionLimitPath) throws SVNException {
        if (wcElisionLimitPath == null || !wcElisionLimitPath.equals(path)) {
            Map mergeInfo = null;
            Map targetMergeInfo = null;

            boolean[] inherited = new boolean[1];
                
            targetMergeInfo = getWCMergeInfo(path, entry, wcElisionLimitPath, 
                    SVNMergeInfoInheritance.INHERITED, false, inherited);
                
            if (inherited[0] || targetMergeInfo == null) {
                return;
            }
                
            mergeInfo = getWCMergeInfo(path, entry, wcElisionLimitPath, 
                    SVNMergeInfoInheritance.NEAREST_ANCESTOR, false, inherited);
                
            if (mergeInfo == null && wcElisionLimitPath == null) {
                mergeInfo = getWCOrRepositoryMergeInfo(path, entry, SVNMergeInfoInheritance.NEAREST_ANCESTOR, 
                        inherited, true, null);
            }
                
            if (mergeInfo == null && wcElisionLimitPath != null) {
                return;
            }
                
            SVNMergeInfoUtil.elideMergeInfo(mergeInfo, targetMergeInfo, path, null, access);
        }
    }

    /**
     * @param path path relative to the repository location.
     */
    protected Map getReposMergeInfo(SVNRepository repository, String path, long revision, 
    		SVNMergeInfoInheritance inheritance, boolean squelchIncapable) throws SVNException {
    	Map reposMergeInfo = null;
    	try {
    		reposMergeInfo = repository.getMergeInfo(new String[] { path }, revision, inheritance, false);
    	} catch (SVNException svne) {
    		if (!squelchIncapable || svne.getErrorMessage().getErrorCode() != SVNErrorCode.UNSUPPORTED_FEATURE) {
    			throw svne;
    		}
    	}
    	String rootRelativePath = getPathRelativeToRoot(null, repository.getLocation(), repository.getRepositoryRoot(false), null, repository);
    	Map targetMergeInfo = null;
    	if (reposMergeInfo != null) {
    		SVNMergeInfo mergeInfo = (SVNMergeInfo) reposMergeInfo.get(rootRelativePath);
    		if (mergeInfo != null) {
    			targetMergeInfo = mergeInfo.getMergeSourcesToMergeLists();
    		}
    	}
    	return targetMergeInfo;
    }
    
    protected Map getWCOrRepositoryMergeInfo(File path, SVNEntry entry, 
            SVNMergeInfoInheritance inherit, boolean[] indirect, boolean reposOnly, 
            SVNRepository repository) throws SVNException {
        Map mergeInfo = null;
        long targetRev[] = { -1 };
        SVNURL url = getEntryLocation(path, entry, targetRev, SVNRevision.WORKING);
        long revision = targetRev[0];

        if (!reposOnly) {
            mergeInfo = getWCMergeInfo(path, entry, null, inherit, false, indirect);
        }
        
        if (mergeInfo == null) {            
            if (!entry.isScheduledForAddition()) {
                Map fileToProp = SVNPropertiesManager.getWorkingCopyPropertyValues(path, entry, 
                        SVNProperty.MERGE_INFO, SVNDepth.EMPTY, true);
                SVNPropertyValue mergeInfoProp = (SVNPropertyValue) fileToProp.get(path);
                if (mergeInfoProp == null) {
                    SVNURL oldLocation = null;
                    boolean closeRepository = false;
                    Map reposMergeInfo = null;
                    String repositoryPath = null;
                    try {
                        if (repository == null) {
                            repository = createRepository(url, null, null, false);
                            closeRepository = true;
                        }
                        repositoryPath = getPathRelativeToSession(url, null, repository);
                        oldLocation = repository.getLocation();
                        if (repositoryPath == null) {
                            repositoryPath = "";
                            repository.setLocation(url, false);
                        }
                        reposMergeInfo = getReposMergeInfo(repository, repositoryPath, revision, inherit, true);
                    } finally {
                        if (closeRepository) {
                            repository.closeSession();
                        } else if (oldLocation != null) {
                            repository.setLocation(oldLocation, false);
                        }
                    }
                    
                    if (reposMergeInfo != null) {
                        indirect[0] = true;
                        mergeInfo = reposMergeInfo;
                    } 
                }
            }
        }
        return mergeInfo;
    }
    
    /**
     * mergeInfo must not be null!
     */
    protected Map getWCMergeInfo(File path, SVNEntry entry, File limitPath, SVNMergeInfoInheritance inherit, 
            boolean base, boolean[] inherited) throws SVNException {
        String walkPath = "";
        Map wcMergeInfo = null;
        SVNWCAccess wcAccess = createWCAccess();
        Map mergeInfo = null;
        if (limitPath != null) {
        	limitPath = new Resource(SVNPathUtil.validateFilePath(limitPath.getAbsolutePath())).getAbsoluteFile();
        }
        
        try {
            while (true) {
                if (inherit == SVNMergeInfoInheritance.NEAREST_ANCESTOR) {
                    wcMergeInfo = null;
                    inherit = SVNMergeInfoInheritance.INHERITED;
                } else {
                    wcMergeInfo = SVNPropertiesManager.parseMergeInfo(path, entry, base);
                }

                if (SVNWCManager.isEntrySwitched(path, entry)) {
                    break;
                }
    
                path = new Resource(SVNPathUtil.validateFilePath(path.getAbsolutePath())).getAbsoluteFile();
                if (wcMergeInfo == null && inherit != SVNMergeInfoInheritance.EXPLICIT &&
                		path.getParentFile() != null) {
                    
                    if (limitPath != null && limitPath.equals(path)) {
                        break;
                    }
                    
                    walkPath = SVNPathUtil.append(path.getName(), walkPath);
                    path = path.getParentFile();
                    try {
                        wcAccess.open(path, false, 0);
                    } catch (SVNException svne) {
                        if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                            mergeInfo = wcMergeInfo;
                            inherited[0] = false;
                            return mergeInfo;
                        }
                        throw svne;
                    } 
    
                    entry = wcAccess.getEntry(path, false);
                    if (entry != null) {
                        continue;
                    }
                }
                break;
            }
        } finally {
            wcAccess.close();
        }
        
        inherited[0] = false;
        if (walkPath.length() == 0) {
            mergeInfo = wcMergeInfo;
        } else {
            if (wcMergeInfo != null) {
                mergeInfo = SVNMergeInfoUtil.adjustMergeInfoSourcePaths(null, walkPath, wcMergeInfo);
                inherited[0] = true;
            } else {
                mergeInfo = null;
            }
        }
        
        if (inherited[0]) {
            mergeInfo = SVNMergeInfoUtil.getInheritableMergeInfo(mergeInfo, null, 
            		SVNRepository.INVALID_REVISION, SVNRepository.INVALID_REVISION);
            SVNMergeInfoUtil.removeEmptyRangeLists(mergeInfo);
        }
        return mergeInfo;
    }

    protected long getPathLastChangeRevision(String relPath, long revision, SVNRepository repository) throws SVNException {
        final long[] rev = new long[1];
        rev[0] = SVNRepository.INVALID_REVISION;

            repository.log(new String[] { relPath }, 1, revision, false, true, 1, false, null, 
                    new ISVNLogEntryHandler() {
                public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                    rev[0] = logEntry.getRevision();
                }
            });
        return rev[0];
    }
    
    protected String getPathRelativeToRoot(File path, SVNURL url, SVNURL reposRootURL, SVNWCAccess wcAccess,
            SVNRepository repos) throws SVNException {
        if (path != null) {
            boolean cleanUp = false;
            try {
                if (wcAccess == null) {
                    wcAccess = createWCAccess();
                    wcAccess.probeOpen(path, false, 0);
                    cleanUp = true;
                }
                SVNEntry entry = wcAccess.getVersionedEntry(path, false);
                url = getEntryLocation(path, entry, null, SVNRevision.UNDEFINED);
                if (reposRootURL == null) {
                    reposRootURL = entry.getRepositoryRootURL();
                }
            } finally {
                if (cleanUp) {
                    wcAccess.closeAdminArea(path);
                }
            }
        }
        
        if (reposRootURL == null) {
            reposRootURL = repos.getRepositoryRoot(true);
        }
        
        String reposRootPath = reposRootURL.getPath();
        String absPath = url.getPath();
        if (!absPath.startsWith(reposRootPath)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_UNRELATED_RESOURCES, 
                    "URL ''{0}'' is not a child of repository root URL ''{1}''", new Object[] { url, 
                    reposRootURL});
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        absPath = absPath.substring(reposRootPath.length());
        if (!absPath.startsWith("/")) {
            absPath = "/" + absPath;
        }
        return absPath;
    }

    protected String getPathRelativeToSession(SVNURL url, SVNURL sessionURL, SVNRepository repos) throws SVNException {
        if (sessionURL == null) {
            sessionURL = repos.getLocation();
        }
        
        String reposPath = sessionURL.getPath();
        String absPath = url.getPath();
        if (!absPath.startsWith(reposPath + "/") && !absPath.equals(reposPath)) {
            return null;
        }
        absPath = absPath.substring(reposPath.length());
        if (absPath.startsWith("/")) {
            absPath = absPath.substring(1);
        }
        return absPath;
    }
    
    protected SVNRepositoryLocation[] getLocations(SVNURL url, File path, SVNRepository repository, 
    		SVNRevision revision, SVNRevision start, SVNRevision end) throws SVNException {
        if (!revision.isValid() || !start.isValid()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION), SVNLogType.DEFAULT);
        }
        long pegRevisionNumber = -1;
        long startRevisionNumber;
        long endRevisionNumber;
        long youngestRevNumber[] = { SVNRepository.INVALID_REVISION };
        
        if (path != null) {
            SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
            try {
                wcAccess.openAnchor(path, false, 0);
                SVNEntry entry = wcAccess.getVersionedEntry(path, false);
                if (entry.getCopyFromURL() != null && revision == SVNRevision.WORKING) {
                    url = entry.getCopyFromSVNURL();
                    pegRevisionNumber = entry.getCopyFromRevision();
                    if (entry.getURL() == null || !entry.getURL().equals(entry.getCopyFromURL())) {
                    	repository = null;
                    }
                } else if (entry.getURL() != null){
                    url = entry.getSVNURL();
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                    		"''{0}'' has no URL", path);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            } finally {
                wcAccess.close();
            }
        }
        String repoPath = "";
        Map locations = null;
        SVNURL rootURL = null;

        if (repository == null) {
            repository = createRepository(url, null, null, true);
        } else {
            // path relative to repository location.
            repoPath = SVNPathUtil.getPathAsChild(repository.getLocation().toString(), url.toString());
            if (repoPath == null) {
                repoPath = "";
            }
        }

        if (pegRevisionNumber < 0) {
            pegRevisionNumber = getRevisionNumber(revision, youngestRevNumber, repository, path);
        }

        if (revision == start && revision == SVNRevision.HEAD) {
            startRevisionNumber = pegRevisionNumber;
        } else {
            startRevisionNumber = getRevisionNumber(start, youngestRevNumber, repository, path);
        }

        if (!end.isValid()) {
            endRevisionNumber = startRevisionNumber;
        } else {
            endRevisionNumber = getRevisionNumber(end, youngestRevNumber, repository, path);
        }

        if (endRevisionNumber == pegRevisionNumber && startRevisionNumber == pegRevisionNumber) {
            SVNRepositoryLocation[] result = new SVNRepositoryLocation[2];
            result[0] = new SVNRepositoryLocation(url, startRevisionNumber);
            result[1] = new SVNRepositoryLocation(url, endRevisionNumber);
            return result;
        }

        rootURL = repository.getRepositoryRoot(true);
        long[] revisionsRange = startRevisionNumber == endRevisionNumber ? new long[] { startRevisionNumber } : 
            new long[] {startRevisionNumber, endRevisionNumber};

        try {
            locations = repository.getLocations(repoPath, (Map) null, pegRevisionNumber, revisionsRange);
        } catch (SVNException e) {
            if (e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_IMPLEMENTED) {
                locations = getLocations10(repository, pegRevisionNumber, startRevisionNumber, endRevisionNumber);
            } else {
                throw e;
            }
        }

        // try to get locations with 'log' method.
        SVNLocationEntry startPath = (SVNLocationEntry) locations.get(new Long(startRevisionNumber));
        SVNLocationEntry endPath = (SVNLocationEntry) locations.get(new Long(endRevisionNumber));
        
        if (startPath == null) {
            Object source = path != null ? (Object) path : (Object) url;
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_UNRELATED_RESOURCES, "Unable to find repository location for ''{0}'' in revision ''{1}''", new Object[] {source, new Long(startRevisionNumber)});
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (endPath == null) {
            Object source = path != null ? (Object) path : (Object) url;
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_UNRELATED_RESOURCES, "The location for ''{0}'' for revision {1} does not exist in the " +
                    "repository or refers to an unrelated object", new Object[] {source, new Long(endRevisionNumber)});
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        SVNRepositoryLocation[] result = new SVNRepositoryLocation[2];
        SVNURL startURL = SVNURL.parseURIEncoded(SVNPathUtil.append(rootURL.toString(), SVNEncodingUtil.uriEncode(startPath.getPath())));
        result[0] = new SVNRepositoryLocation(startURL, startRevisionNumber);
        if (end.isValid()) {
            SVNURL endURL = SVNURL.parseURIEncoded(SVNPathUtil.append(rootURL.toString(), SVNEncodingUtil.uriEncode(endPath.getPath())));
            result[1] = new SVNRepositoryLocation(endURL, endRevisionNumber);
        }
        return result;
    }
    
    private Map getLocations10(SVNRepository repos, final long pegRevision, final long startRevision, final long endRevision) throws SVNException {
        final String path = repos.getRepositoryPath("");
        final SVNNodeKind kind = repos.checkPath("", pegRevision);
        if (kind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "path ''{0}'' doesn't exist at revision {1}", new Object[] {path, new Long(pegRevision)});
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        long logStart = pegRevision;
        logStart = Math.max(startRevision, logStart);
        logStart = Math.max(endRevision, logStart);
        long logEnd = pegRevision;
        logStart = Math.min(startRevision, logStart);
        logStart = Math.min(endRevision, logStart);
        
        LocationsLogEntryHandler handler = new LocationsLogEntryHandler(path, startRevision, endRevision, pegRevision, kind, getEventDispatcher());
        repos.log(new String[] {""}, logStart, logEnd, true, false, handler);
        
        String pegPath = handler.myPegPath == null ? handler.myCurrentPath : handler.myPegPath;
        String startPath = handler.myStartPath == null ? handler.myCurrentPath : handler.myStartPath;
        String endPath = handler.myEndPath == null ? handler.myCurrentPath : handler.myEndPath;
        
        if (pegPath == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "path ''{0}'' in revision {1} is an unrelated object", new Object[] {path, new Long(logStart)});
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        Map result = new SVNHashMap();
        result.put(new Long(startRevision), new SVNLocationEntry(-1, startPath));
        result.put(new Long(endRevision), new SVNLocationEntry(-1, endPath));
        return result;
    }
    
    private static String getPreviousLogPath(String path, SVNLogEntry logEntry, SVNNodeKind kind) throws SVNException {
        String prevPath = null;
        SVNLogEntryPath logPath = (SVNLogEntryPath) logEntry.getChangedPaths().get(path);
        if (logPath != null) {
            if (logPath.getType() != SVNLogEntryPath.TYPE_ADDED && logPath.getType() != SVNLogEntryPath.TYPE_REPLACED) {
                return logPath.getPath();
            }
            if (logPath.getCopyPath() != null) {
                return logPath.getCopyPath();
            } 
            return null;
        } else if (!logEntry.getChangedPaths().isEmpty()){
            Map sortedMap = new SVNHashMap();
            sortedMap.putAll(logEntry.getChangedPaths());
            List pathsList = new ArrayList(sortedMap.keySet());
            Collections.sort(pathsList, SVNPathUtil.PATH_COMPARATOR);
            Collections.reverse(pathsList);
            for(Iterator paths = pathsList.iterator(); paths.hasNext();) {
                String p = (String) paths.next();
                if (path.startsWith(p + "/")) {
                    SVNLogEntryPath lPath = (SVNLogEntryPath) sortedMap.get(p);
                    if (lPath.getCopyPath() != null) {
                        prevPath = SVNPathUtil.append(lPath.getCopyPath(), path.substring(p.length()));
                        break;
                    }
                }
            }
        }
        if (prevPath == null) {
            if (kind == SVNNodeKind.DIR) {
                prevPath = path;
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_UNRELATED_RESOURCES, "Missing changed-path information for ''{0}'' in revision {1}", new Object[] {path, new Long(logEntry.getRevision())});
                SVNErrorManager.error(err, SVNLogType.WC);
            }            
        }
        return prevPath;
    }
    
    protected SVNURL getURL(File path) throws SVNException {
        return deriveLocation(path, null, null, SVNRevision.UNDEFINED, null, null);
    }
    
    protected SVNURL deriveLocation(File path, SVNURL url, long[] pegRevisionNumber, SVNRevision pegRevision, 
            SVNRepository repos, SVNWCAccess access) throws SVNException {
        if (path != null) {
            SVNEntry entry = null;
            if (access != null) {
                entry = access.getVersionedEntry(path, false);
            } else {
                SVNWCAccess wcAccess = createWCAccess();
                try {
                    wcAccess.probeOpen(path, false, 0);
                    entry = wcAccess.getVersionedEntry(path, false);
                } finally {
                    wcAccess.close();
                }
            }
            url = getEntryLocation(path, entry, pegRevisionNumber, pegRevision);
        }
        
        if (pegRevisionNumber != null && pegRevisionNumber.length > 0 && 
                !SVNRevision.isValidRevisionNumber(pegRevisionNumber[0])) {
            boolean closeRepository = false;
            try {
                if (repos == null) {
                    repos = createRepository(url, null, null, false);
                    closeRepository = true;
                }
                pegRevisionNumber[0] = getRevisionNumber(pegRevision, null, repos, path);
            } finally {
                if (closeRepository) {
                    repos.closeSession();
                }
            }
        }
        return url;
    }
    
    protected SVNURL getEntryLocation(File path, SVNEntry entry, long[] revNum, SVNRevision pegRevision) throws SVNException {
        SVNURL url = null;
        if (entry.getCopyFromURL() != null && pegRevision == SVNRevision.WORKING) {
            url = entry.getCopyFromSVNURL();
            if (revNum != null && revNum.length > 0) {
                revNum[0] = entry.getCopyFromRevision();
            } 
        } else if (entry.getURL() != null) {
            url = entry.getSVNURL();
            if (revNum != null && revNum.length > 0) {
                revNum[0] = entry.getRevision();
            } 
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                    "Entry for ''{0}'' has no URL", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return url;
    }
    
    protected SVNURL ensureSessionURL(SVNRepository repository, SVNURL url) throws SVNException {
    	SVNURL oldURL = repository.getLocation();
    	if (url == null) {
    		url = repository.getRepositoryRoot(true);
    	}
    	if (!url.equals(oldURL)) {
    		repository.setLocation(url, false);
        	return oldURL;
    	}
    	return null;
    }
    
    protected int getLevelsToLockFromDepth(SVNDepth depth) {
        return  depth == SVNDepth.EMPTY || depth == SVNDepth.FILES ? 0 : 
            (depth == SVNDepth.IMMEDIATES ? 1 : SVNWCAccess.INFINITE_DEPTH);  
    }

    protected void setCommitItemAccess(SVNCommitItem item, SVNWCAccess access) {
        item.setWCAccess(access);
    }

    protected void setCommitItemProperty(SVNCommitItem item, String name, SVNPropertyValue value) {
        item.setProperty(name, value);
    }

    protected void setCommitItemFlags(SVNCommitItem item, boolean contentModified, boolean propertiesModified) {
        item.setContentsModified(contentModified);
        item.setPropertiesModified(propertiesModified);
    }
    
    private static final class LocationsLogEntryHandler implements ISVNLogEntryHandler {
        
        private String myCurrentPath = null;
        private String myStartPath = null;
        private String myEndPath = null;
        private String myPegPath = null;

        private long myStartRevision;
        private long myEndRevision;
        private long myPegRevision;
        private SVNNodeKind myKind;
        private ISVNEventHandler myEventHandler;

        private LocationsLogEntryHandler(String path, long startRevision, long endRevision, long pegRevision, SVNNodeKind kind,
                ISVNEventHandler eventHandler) {
            myCurrentPath = path;
            myStartRevision = startRevision;
            myEndRevision = endRevision;
            myPegRevision = pegRevision;
            myEventHandler = eventHandler;
            myKind = kind;
        }

        public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
            if (myEventHandler != null) {
                myEventHandler.checkCancelled();
            }
            if (logEntry.getChangedPaths() == null) {
                return;
            }
            if (myCurrentPath == null) {
                return;
            }
            if (myStartPath == null && logEntry.getRevision() <= myStartRevision) { 
                myStartPath = myCurrentPath;                    
            }
            if (myEndPath == null && logEntry.getRevision() <= myEndRevision) { 
                myEndPath = myCurrentPath;                    
            }
            if (myPegPath == null && logEntry.getRevision() <= myPegRevision) { 
                myPegPath = myCurrentPath;                    
            }
            myCurrentPath = getPreviousLogPath(myCurrentPath, logEntry, myKind);
        }
    }

    protected static class RepositoryReference {

        public RepositoryReference(String url, long rev) {
            URL = url;
            Revision = rev;
        }

        public String URL;

        public long Revision;
    }

    protected static class SVNRepositoryLocation {

        private SVNURL myURL;
        private long myRevision;

        public SVNRepositoryLocation(SVNURL url, long rev) {
            myURL = url;
            myRevision = rev;
        }
        public long getRevisionNumber() {
            return myRevision;
        }
        public SVNURL getURL() {
            return myURL;
        }
    }

}