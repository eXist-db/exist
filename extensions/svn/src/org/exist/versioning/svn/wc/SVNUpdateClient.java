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
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.SVNAmbientDepthFilterEditor;
import org.exist.versioning.svn.internal.wc.SVNCancellableEditor;
import org.exist.versioning.svn.internal.wc.SVNCancellableOutputStream;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNEventFactory;
import org.exist.versioning.svn.internal.wc.SVNExportEditor;
import org.exist.versioning.svn.internal.wc.SVNExternal;
import org.exist.versioning.svn.internal.wc.SVNFileType;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.SVNPropertiesManager;
import org.exist.versioning.svn.internal.wc.SVNWCManager;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea16;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminAreaFactory;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminAreaInfo;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNReporter;
import org.exist.versioning.svn.internal.wc.admin.SVNTranslator;
import org.exist.versioning.svn.internal.wc.admin.SVNVersionedProperties;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
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
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNFileFetcher;
import org.tmatesoft.svn.core.internal.wc.ISVNUpdateEditor;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNExternalsHandler;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * This class provides methods which allow to check out, update, switch and relocate a
 * Working Copy as well as export an unversioned directory or file from a repository.
 * 
 * <p>
 * Here's a list of the <b>SVNUpdateClient</b>'s methods 
 * matched against corresponing commands of the SVN command line 
 * client:
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="40%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>SVNKit</b></td>
 * <td><b>Subversion</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doCheckout()</td><td>'svn checkout'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doUpdate()</td><td>'svn update'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doSwitch()</td><td>'svn switch'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doRelocate()</td><td>'svn switch --relocate oldURL newURL'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doExport()</td><td>'svn export'</td>
 * </tr>
 * </table>
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNUpdateClient extends SVNBasicClient {

    private ISVNExternalsHandler myExternalsHandler;
    private boolean myIsUpdateLocksOnDemand;
    private boolean myIsExportExpandsKeywords;

    /**
     * Constructs and initializes an <b>SVNUpdateClient</b> object
     * with the specified run-time configuration and authentication 
     * drivers.
     * 
     * <p>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNUpdateClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the 
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
     * 
     * <p>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNUpdateClient</b> will be using a default authentication
     * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
     * which uses server-side settings and auth storage from the 
     * default SVN's run-time configuration area (or system properties
     * if that area is not found).
     * 
     * @param authManager an authentication and network layers driver
     * @param options     a run-time configuration options driver     
     */
    public SVNUpdateClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
        myIsExportExpandsKeywords = true;
    }

    /**
     * Constructs and initializes an <b>SVNUpdateClient</b> object
     * with the specified run-time configuration and authentication 
     * drivers.
     * 
     * <p/>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNUpdateClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the 
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
     * 
     * <p/> 
     * If <code>repositoryPool</code> is <span class="javakeyword">null</span>,
     * then {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory} will be used to create {@link SVNRepository repository access objects}.
     * 
     * @param repositoryPool   a repository pool object
     * @param options          a run-time configuration options driver     
     */
    public SVNUpdateClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
        myIsExportExpandsKeywords = true;
    }
    
    /**
     * Sets an externals handler to be used by this client object.
     * 
     * @param externalsHandler user's implementation of {@link ISVNExternalsHandler}
     * @see   #getExternalsHandler()
     * @since 1.2
     */
    public void setExternalsHandler(ISVNExternalsHandler externalsHandler) {
        myExternalsHandler = externalsHandler;
    }

    /**
     * Returns an externals handler used by this update client.
     * 
     * <p/>
     * If no user's handler is provided then {@link ISVNExternalsHandler#DEFAULT} is returned and 
     * used by this client object by default.
     * 
     * <p/>
     * For more information what externals handlers are for, please, refer to {@link ISVNExternalsHandler}. 
     * 
     * @return externals handler being in use
     * @see #setExternalsHandler(ISVNExternalsHandler)
     * @since 1.2 
     */
    public ISVNExternalsHandler getExternalsHandler() {
        if (myExternalsHandler == null) {
            myExternalsHandler = ISVNExternalsHandler.DEFAULT;
        }
        return myExternalsHandler;
    }
    
    /**
     * Brings the Working Copy item up-to-date with repository changes at the specified
     * revision.
     * 
     * <p>
     * As a revision <b>SVNRevision</b>'s pre-defined constant fields can be used. For example,
     * to update the Working Copy to the latest revision of the repository use 
     * {@link SVNRevision#HEAD HEAD}.
     * 
     * @param  file			the Working copy item to be updated
     * @param  revision		the desired revision against which the item will be updated 
     * @param  recursive	if <span class="javakeyword">true</span> and <code>file</code> is
     * 						a directory then the entire tree will be updated, otherwise if 
     * 						<span class="javakeyword">false</span> - only items located immediately
     * 						in the directory itself
     * @return				the revision number to which <code>file</code> was updated to
     * @throws SVNException
     * @deprecated use {@link #doUpdate(File, SVNRevision, SVNDepth, boolean, boolean)} instead 
     */
    public long doUpdate(File file, SVNRevision revision, boolean recursive) throws SVNException {
        return doUpdate(file, revision, SVNDepth.fromRecurse(recursive), false, false);
    }
    
    /**
     * @param file 
     * @param revision 
     * @param recursive 
     * @param force 
     * @return               actual revision number  
     * @throws SVNException 
     * @deprecated use {@link #doUpdate(File, SVNRevision, SVNDepth, boolean, boolean)} instead
     */
    public long doUpdate(File file, SVNRevision revision, boolean recursive, boolean force) throws SVNException {
        return doUpdate(file, revision, SVNDepth.fromRecurse(recursive), force, false);
    }    
    
    /**
     * Updates working trees <code>paths</code> to <code>revision</code>. 
     * Unversioned paths that are direct children of a versioned path will cause an update that 
     * attempts to add that path, other unversioned paths are skipped.
     * 
     * <p/>
     * <code>revision</code> must represent a valid revision number ({@link SVNRevision#getNumber()} >= 0),
     * or date ({@link SVNRevision#getDate()} != <span class="javakeyword">true</span>), or be equal to 
     * {@link SVNRevision#HEAD}. If <code>revision</code> does not meet these requirements, an exception with 
     * the error code {@link SVNErrorCode#CLIENT_BAD_REVISION} is thrown.
     * 
     * <p/>
     * The paths in <code>paths</code> can be from multiple working copies from multiple
     * repositories, but even if they all come from the same repository there
     * is no guarantee that revision represented by {@link SVNRevision#HEAD}
     * will remain the same as each path is updated.
     * 
     * <p/>
     * If externals are {@link #isIgnoreExternals() ignored}, doesn't process externals definitions
     * as part of this operation.
     * 
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, updates fully recursively.
     * Else if it is {@link SVNDepth#IMMEDIATES} or {@link SVNDepth#FILES}, updates
     * each target and its file entries, but not its subdirectories. Else if {@link SVNDepth#EMPTY}, 
     * updates exactly each target, nonrecursively (essentially, updates the target's properties).
     *
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#UNKNOWN}, takes the working depth from
     * <code>paths</code> and then behaves as described above.
     * 
     * <p/>
     * If <code>depthIsSticky</code> is set and <code>depth</code> is not {@link SVNDepth#UNKNOWN}, 
     * then in addition to updating <code>paths</code>, also sets
     * their sticky ambient depth value to <code>depth</codes>.
     * 
     * <p/>
     * If <code>allowUnversionedObstructions</code> is <span class="javakeyword">true</span> then the update 
     * tolerates existing unversioned items that obstruct added paths. Only obstructions of the same type 
     * (file or dir) as the added item are tolerated. The text of obstructing files is left as-is, effectively
     * treating it as a user modification after the update. Working properties of obstructing items are set 
     * equal to the base properties. If <code>allowUnversionedObstructions</code> is 
     * <span class="javakeyword">false</span> then the update will abort if there are any unversioned 
     * obstructing items.
     *
     * <p/>
     * If the caller's {@link ISVNEventHandler} is non-<span class="javakeyword">null</span>, it is invoked for 
     * each item handled by the update, and also for files restored from text-base. Also 
     * {@link ISVNEventHandler#checkCancelled()} will be used at various places during the update to check 
     * whether the caller wants to stop the update.
     * 
     * <p/>
     * Before updating a next path from <code>paths</code> this method calls {@link #handlePathListItem(File)} 
     * passing the path to it.
     * 
     * <p/>
     * This operation requires repository access (in case the repository is not on the same machine, network
     * connection is established).
     * 
     * @param  paths                           working copy paths
     * @param  revision                        revision to update to
     * @param  depth                           tree depth to update
     * @param  allowUnversionedObstructions    flag that allows tollerating unversioned items 
     *                                         during update
     * @param  depthIsSticky                   flag that controls whether the requested depth 
     *                                         should be written to the working copy
     * @return                                 an array of <code>long</code> revisions with each 
     *                                         element set to the revision to which <code>revision</code> was resolved
     * @throws SVNException 
     * @since 1.2, SVN 1.5
     */
    public long[] doUpdate(File[] paths, SVNRevision revision, SVNDepth depth, boolean allowUnversionedObstructions, 
            boolean depthIsSticky) throws SVNException {
        if (paths == null) {
            return new long[0];
        }
        Collection revisions = new LinkedList();
        for (int i = 0; i < paths.length; i++) {
            checkCancelled();
            File path = paths[i];
            try {
                setEventPathPrefix("");
                handlePathListItem(path);
                long rev = doUpdate(path, revision, depth, allowUnversionedObstructions, depthIsSticky);
                revisions.add(new Long(rev));
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                    SVNEvent skipEvent = SVNEventFactory.createSVNEvent(path, SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, SVNEventAction.SKIP, SVNEventAction.UPDATE_COMPLETED, null, null);
                    dispatchEvent(skipEvent);
                    revisions.add(new Long(-1));
                    continue;
                }
                throw svne;
            } finally {
                setEventPathPrefix(null);
            }
        }
        sleepForTimeStamp();
        long[] result = new long[revisions.size()];
        int i = 0;
        for (Iterator revs = revisions.iterator(); revs.hasNext();) {
            Long value = (Long) revs.next();
            result[i++] = value.longValue();
        }
        return result;
    }

    /**
     * Updates working copy <code></code> to <code>revision</code>. 
     * Unversioned paths that are direct children of a versioned path will cause an update that 
     * attempts to add that path, other unversioned paths are skipped.
     * 
     * <p/>
     * <code>revision</code> must represent a valid revision number ({@link SVNRevision#getNumber()} >= 0),
     * or date ({@link SVNRevision#getDate()} != <span class="javakeyword">true</span>), or be equal to 
     * {@link SVNRevision#HEAD}. If <code>revision</code> does not meet these requirements, an exception with 
     * the error code {@link SVNErrorCode#CLIENT_BAD_REVISION} is thrown.
     * 
     * <p/>
     * If externals are {@link #isIgnoreExternals() ignored}, doesn't process externals definitions
     * as part of this operation.
     * 
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, updates fully recursively.
     * Else if it is {@link SVNDepth#IMMEDIATES} or {@link SVNDepth#FILES}, updates
     * <code>path</code> and its file entries, but not its subdirectories. Else if {@link SVNDepth#EMPTY}, 
     * updates exactly <code>path</code>, nonrecursively (essentially, updates the target's properties).
     *
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#UNKNOWN}, takes the working depth from
     * <code>path</code> and then behaves as described above.
     * 
     * <p/>
     * If <code>depthIsSticky</code> is set and <code>depth</code> is not {@link SVNDepth#UNKNOWN}, 
     * then in addition to updating <code>path</code>, also sets its sticky ambient depth value to 
     * <code>depth</codes>.
     * 
     * <p/>
     * If <code>allowUnversionedObstructions</code> is <span class="javakeyword">true</span> then the update 
     * tolerates existing unversioned items that obstruct added paths. Only obstructions of the same type 
     * (file or dir) as the added item are tolerated. The text of obstructing files is left as-is, effectively
     * treating it as a user modification after the update. Working properties of obstructing items are set 
     * equal to the base properties. If <code>allowUnversionedObstructions</code> is 
     * <span class="javakeyword">false</span> then the update will abort if there are any unversioned 
     * obstructing items.
     *
     * <p/>
     * If the caller's {@link ISVNEventHandler} is non-<span class="javakeyword">null</span>, it is invoked for 
     * each item handled by the update, and also for files restored from text-base. Also 
     * {@link ISVNEventHandler#checkCancelled()} will be used at various places during the update to check 
     * whether the caller wants to stop the update.
     * 
     * <p/>
     * This operation requires repository access (in case the repository is not on the same machine, network
     * connection is established).
     * 
     * @param  path                           working copy path
     * @param  revision                       revision to update to
     * @param  depth                          tree depth to update
     * @param  allowUnversionedObstructions   flag that allows tollerating unversioned items 
     *                                        during update
     * @param  depthIsSticky                  flag that controls whether the requested depth 
     *                                        should be written to the working copy
     * @return                                revision to which <code>revision</code> was resolved
     * @throws SVNException 
     * @since 1.2, SVN 1.5
     */
    public long doUpdate(File path, SVNRevision revision, SVNDepth depth, boolean allowUnversionedObstructions, boolean depthIsSticky) throws SVNException {
        return update(path, revision, depth, allowUnversionedObstructions, depthIsSticky, true);
    }

    private long doSwitchImpl(SVNWCAccess wcAccess, File path, SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth, 
            boolean allowUnversionedObstructions, boolean depthIsSticky) throws SVNException {
        if (depth == SVNDepth.UNKNOWN) {
            depthIsSticky = false;
        }
        
        if (depthIsSticky && depth == SVNDepth.EXCLUDE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot both exclude and switch a path");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        boolean closeAccess = wcAccess == null;
        
        try {
            SVNAdminAreaInfo info = null;
            if (wcAccess != null) {
                SVNWCAccess tmpAccess = null;
                try {
                    tmpAccess = createWCAccess();
                    info = tmpAccess.openAnchor(path, false, SVNWCAccess.INFINITE_DEPTH);
                } finally {
                    tmpAccess.close();
                }
                
                SVNAdminArea anchor = info.getAnchor();
                SVNAdminArea target = info.getTarget();
                anchor = wcAccess.retrieve(anchor.getRoot());
                target = wcAccess.retrieve(target.getRoot());
                info.setAnchor(anchor);
                info.setTarget(target);
                info.setWCAccess(wcAccess);
            } else {
                wcAccess = createWCAccess();    
                info = wcAccess.openAnchor(path, true, SVNWCAccess.INFINITE_DEPTH);
            }

            final SVNReporter reporter = new SVNReporter(info, path, true, false, depth, false, false, !depthIsSticky, getDebugLog());
            SVNAdminArea anchorArea = info.getAnchor();
            SVNEntry entry = anchorArea.getVersionedEntry(anchorArea.getThisDirName(), false);
            SVNURL sourceURL = entry.getSVNURL();
            if (sourceURL == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Directory ''{0}'' has no URL", anchorArea.getRoot());
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            long[] revs = new long[1];
            // should fail on missing repository.
            SVNRepository repository = createRepository(url, null, anchorArea, pegRevision, revision, revs);
            long revNumber = revs[0];
            url = repository.getLocation();
            // root of the switched repos.
            SVNURL sourceRoot = repository.getRepositoryRoot(true);
            if (!SVNPathUtil.isAncestor(sourceRoot.toString(), sourceURL.toString())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SWITCH, "''{0}''\nis not the same repository as\n''{1}''",
                        new Object[] {url.toString(), sourceRoot.toString()});
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (depthIsSticky && depth.compareTo(SVNDepth.INFINITY) < 0) {
                SVNEntry targetEntry = anchorArea.getEntry(info.getTargetName(), true);
                if (targetEntry != null && targetEntry.isDirectory()) {
                    SVNWCManager.crop(info, depth);
                }
            }

            // reparent to the sourceURL
            repository.setLocation(sourceURL, false);
            String[] preservedExts = getOptions().getPreservedConflictFileExtensions();
            ISVNUpdateEditor editor = wcAccess.createUpdateEditor(info, url.toString(), 
                    allowUnversionedObstructions, depthIsSticky, depth, preservedExts, null, false);

            ISVNEditor filterEditor = SVNAmbientDepthFilterEditor.wrap(editor, info, depthIsSticky);
            
            String target = "".equals(info.getTargetName()) ? null : info.getTargetName();
            repository.update(url, revNumber, target, depth, reporter, SVNCancellableEditor.newInstance(filterEditor, this, getDebugLog()));

            long targetRevision = editor.getTargetRevision();
            if (targetRevision >= 0 && !isIgnoreExternals() && depth.isRecursive()) {
                url = target == null ? url : url.removePathTail();
                handleExternals(wcAccess, info.getAnchor().getRoot(), info.getOldExternals(), info.getNewExternals(), 
                        info.getDepths(), url, sourceRoot, depth, false, true);
            }
            
            dispatchEvent(SVNEventFactory.createSVNEvent(info.getTarget().getRoot(), SVNNodeKind.NONE, null, 
                    targetRevision, SVNEventAction.UPDATE_COMPLETED, null, null, null, reporter.getReportedFilesCount(), reporter.getTotalFilesCount()));
            return targetRevision;
        } finally {
            if (closeAccess) {
                wcAccess.close();
            }
            sleepForTimeStamp();
        }
    }

    private long update(File path, SVNRevision revision, SVNDepth depth, boolean allowUnversionedObstructions, boolean depthIsSticky, boolean sendCopyFrom) throws SVNException {
        depth = depth == null ? SVNDepth.UNKNOWN : depth;
        if (depth == SVNDepth.UNKNOWN) {
            depthIsSticky = false;
        }
        
        path = path.getAbsoluteFile();
        SVNWCAccess wcAccess = createWCAccess();
        SVNAdminAreaInfo adminInfo = null;
        int admOpenDepth = depthIsSticky ? -1 : getLevelsToLockFromDepth(depth);
        try {
            if (isUpdateLocksOnDemand()) {
                wcAccess.openAnchor(path, true, 0);
                wcAccess.close();
            }

            adminInfo = wcAccess.openAnchor(path, !isUpdateLocksOnDemand(), admOpenDepth);
            SVNAdminArea anchorArea = adminInfo.getAnchor();

            SVNEntry entry = anchorArea.getEntry(anchorArea.getThisDirName(), false);
            SVNURL url = entry.getSVNURL();
            if (url == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                        "Entry ''{0}'' has no URL", anchorArea.getRoot());
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            
            if (depthIsSticky && depth.compareTo(SVNDepth.INFINITY) < 0) {
                SVNEntry targetEntry = anchorArea.getEntry(adminInfo.getTargetName(), true);
                if (targetEntry != null && targetEntry.isDirectory()) {
                    SVNWCManager.crop(adminInfo, depth);
                    if (depth == SVNDepth.EXCLUDE) {
                        return -1;
                    }
                }
            }
  
            String[] preservedExts = getOptions().getPreservedConflictFileExtensions();
            
            SVNRepository repos = createRepository(url, anchorArea.getRoot(), wcAccess, true);
            boolean serverSupportsDepth = repos.hasCapability(SVNCapability.DEPTH);
            final SVNReporter reporter = new SVNReporter(adminInfo, path, true, !serverSupportsDepth, 
                    depth, isUpdateLocksOnDemand(), false, !depthIsSticky, getDebugLog());
            
            String target = "".equals(adminInfo.getTargetName()) ? null : adminInfo.getTargetName();
            long revNumber = getRevisionNumber(revision, repos, path);
            final SVNURL reposRoot = repos.getRepositoryRoot(true);
            wcAccess.setRepositoryRoot(path, reposRoot);
            
            final SVNRepository[] repos2 = new SVNRepository[1];
            ISVNFileFetcher fileFetcher = new ISVNFileFetcher() {
                public long fetchFile(String path, long revision, OutputStream os, SVNProperties properties) throws SVNException {
                    SVNURL url = reposRoot.appendPath(SVNPathUtil.removeTail(path), false);
                    if (repos2[0] == null) {
                        repos2[0] = createRepository(url, null, null, false);
                    } else {
                        repos2[0].setLocation(url, false);
                    }
                    return repos2[0].getFile(SVNPathUtil.tail(path), revision, properties, os);
                }
            };
            
            ISVNUpdateEditor editor = wcAccess.createUpdateEditor(adminInfo, null, allowUnversionedObstructions, 
                    depthIsSticky, depth, preservedExts, fileFetcher, isUpdateLocksOnDemand());

            ISVNEditor filterEditor = SVNAmbientDepthFilterEditor.wrap(editor, adminInfo, depthIsSticky);

            try {
                repos.update(revNumber, target, depth, sendCopyFrom, reporter, SVNCancellableEditor.newInstance(filterEditor, this, getDebugLog()));
            } finally {
                if (repos2[0] != null) {
                    repos2[0].closeSession();
                }
            }

            long targetRevision = editor.getTargetRevision();
            
            if (targetRevision >= 0) {
                if ((depth == SVNDepth.INFINITY || depth == SVNDepth.UNKNOWN) && !isIgnoreExternals()) {
                    handleExternals(wcAccess, adminInfo.getAnchor().getRoot(), 
                            adminInfo.getOldExternals(), adminInfo.getNewExternals(), adminInfo.getDepths(), url, reposRoot, depth, false, true);
                }
                dispatchEvent(SVNEventFactory.createSVNEvent(adminInfo.getTarget().getRoot(), 
                        SVNNodeKind.NONE, null, targetRevision, SVNEventAction.UPDATE_COMPLETED, null, null, 
                        null, reporter.getReportedFilesCount(), reporter.getTotalFilesCount()));
            }
            return targetRevision;
        } finally {
            wcAccess.close();
            sleepForTimeStamp();
        }
    }
    
    /**
     * Sets whether working copies should be locked on demand or not during an update process.
     * 
     * <p>
     * For additional description, please, refer to {@link #isUpdateLocksOnDemand()}.
     * 
     * @param locksOnDemand <span class="javakeyword">true</span> to make update lock a working copy tree on 
     *                      demand only (for those subdirectories only which will be changed by update)
     */
    public void setUpdateLocksOnDemand(boolean locksOnDemand) {
        myIsUpdateLocksOnDemand = locksOnDemand;
    }
    
    /**
     * Says whether the entire working copy should be locked while updating or not.
     * 
     * <p/>
     * If this method returns <span class="javakeyword">false</span>, then the working copy will be 
     * closed for all paths involved in the update. Otherwise only those working copy subdirectories 
     * will be locked, which will be either changed by the update or which contain deleted files
     * that should be restored during the update; all other versioned subdirectories than won't be 
     * touched by the update will remain opened for read only access without locking. 
     * 
     * <p/>
     * Locking working copies on demand is intended to improve update performance for large working 
     * copies because even a no-op update on a huge working copy always locks the entire tree by default.
     * And locking a working copy tree means opening special lock files for privileged access for all 
     * subdirectories involved. This makes an update process work slower. Locking wc on demand 
     * feature suggests such a workaround to enhance update performance.
     * 
     * @return  <span class="javakeyword">true</span> when locking wc on demand
     */
    public boolean isUpdateLocksOnDemand() {
        return myIsUpdateLocksOnDemand;
    }

    /**
     * Updates the Working Copy item to mirror a new URL. 
     * 
     * <p>
     * As a revision <b>SVNRevision</b>'s pre-defined constant fields can be used. For example,
     * to update the Working Copy to the latest revision of the repository use 
     * {@link SVNRevision#HEAD HEAD}.
     * 
     * <p>
     * Calling this method is equivalent to 
     * <code>doSwitch(file, url, SVNRevision.UNDEFINED, revision, recursive)</code>.
     * 
     * @param  file			the Working copy item to be switched
     * @param  url			the repository location as a target against which the item will 
     * 						be switched
     * @param  revision		the desired revision of the repository target   
     * @param  recursive	if <span class="javakeyword">true</span> and <code>file</code> is
     * 						a directory then the entire tree will be updated, otherwise if 
     * 						<span class="javakeyword">false</span> - only items located immediately
     * 						in the directory itself
     * @return				the revision number to which <code>file</code> was updated to
     * @throws SVNException
     * @deprecated use {@link #doSwitch(File, SVNURL, SVNRevision, SVNRevision, SVNDepth, boolean, boolean)} instead
     */
    public long doSwitch(File file, SVNURL url, SVNRevision revision, boolean recursive) throws SVNException {
        return doSwitch(file, url, SVNRevision.UNDEFINED, revision, SVNDepth.getInfinityOrFilesDepth(recursive), 
                false, false);
        
    }

    /**
     * Updates the Working Copy item to mirror a new URL. 
     * 
     * <p>
     * As a revision <b>SVNRevision</b>'s pre-defined constant fields can be used. For example,
     * to update the Working Copy to the latest revision of the repository use 
     * {@link SVNRevision#HEAD HEAD}.
     * 
     * @param  file         the Working copy item to be switched
     * @param  url          the repository location as a target against which the item will 
     *                      be switched
     * @param  pegRevision  a revision in which <code>file</code> is first looked up
     *                      in the repository
     * @param  revision     the desired revision of the repository target   
     * @param  recursive    if <span class="javakeyword">true</span> and <code>file</code> is
     *                      a directory then the entire tree will be updated, otherwise if 
     *                      <span class="javakeyword">false</span> - only items located immediately
     *                      in the directory itself
     * @return              the revision number to which <code>file</code> was updated to
     * @throws SVNException
     * @deprecated use {@link #doSwitch(File, SVNURL, SVNRevision, SVNRevision, SVNDepth, boolean, boolean)} instead
     */
    public long doSwitch(File file, SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive) throws SVNException {
        return doSwitch(file, url, pegRevision, revision, SVNDepth.getInfinityOrFilesDepth(recursive), false, 
                false);
    }
    
    /**
     * @param file 
     * @param url 
     * @param pegRevision 
     * @param revision 
     * @param recursive 
     * @param force 
     * @return               actual revision number
     * @throws SVNException 
     * @deprecated use {@link #doSwitch(File, SVNURL, SVNRevision, SVNRevision, SVNDepth, boolean, boolean)} instead 
     */
    public long doSwitch(File file, SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, boolean force) throws SVNException {
        return doSwitch(file, url, pegRevision, revision, SVNDepth.getInfinityOrFilesDepth(recursive), force, false);
    }    
    
    /**
     * Switches working tree <code>path</code> to <code>url</code>\<code>pegRevision</code> at 
     * <code>revision</code>. 
     * 
     * <p/>
     * Summary of purpose: this is normally used to switch a working
     * directory over to another line of development, such as a branch or
     * a tag.  Switching an existing working directory is more efficient
     * than checking out <code>url</code> from scratch.
     *
     * <p/>
     * <code>revision</code> must represent a valid revision number ({@link SVNRevision#getNumber()} >= 0),
     * or date ({@link SVNRevision#getDate()} != <span class="javakeyword">true</span>), or be equal to 
     * {@link SVNRevision#HEAD}. If <code>revision</code> does not meet these requirements, an exception with 
     * the error code {@link SVNErrorCode#CLIENT_BAD_REVISION} is thrown.
     * 
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, switches fully recursively.
     * Else if it is {@link SVNDepth#IMMEDIATES}, switches <code>path</code> and its file
     * children (if any), and switches subdirectories but does not update
     * them.  Else if {@link SVNDepth#FILES}, switches just file children,
     * ignoring subdirectories completely. Else if {@link SVNDepth#EMPTY},
     * switches just <code>path</code> and touches nothing underneath it.
     *
     * <p/>
     * If <code>depthIsSticky</code> is set and <code>depth</code> is not 
     * {@link SVNDepth#UNKNOWN}, then in addition to switching <code>path</code>, also sets
     * its sticky ambient depth value to <code>depth</code>.
     * 
     * <p/>
     * If externals are {@link #isIgnoreExternals() ignored}, doesn't process externals definitions
     * as part of this operation.
     *
     * <p/>
     * If <code>allowUnversionedObstructions</code> is <span class="javakeyword">true</span> then the switch 
     * tolerates existing unversioned items that obstruct added paths. Only
     * obstructions of the same type (file or dir) as the added item are
     * tolerated. The text of obstructing files is left as-is, effectively
     * treating it as a user modification after the switch. Working
     * properties of obstructing items are set equal to the base properties.
     * If <code>allowUnversionedObstructions</code> is <span class="javakeyword">false</span> then the switch 
     * will abort if there are any unversioned obstructing items.
     * 
     * <p/>
     * If the caller's {@link ISVNEventHandler} is non-<span class="javakeyword">null</span>, it is invoked for 
     * paths affected by the switch, and also for files restored from text-base. Also 
     * {@link ISVNEventHandler#checkCancelled()} will be used at various places during the switch to check 
     * whether the caller wants to stop the switch.
     * 
     * <p/>
     * This operation requires repository access (in case the repository is not on the same machine, network
     * connection is established).
     * 
     * @param  path                           the Working copy item to be switched
     * @param  url                            the repository location as a target against which the item will 
     *                                        be switched
     * @param  pegRevision                    a revision in which <code>path</code> is first looked up
     *                                        in the repository
     * @param  revision                       the desired revision of the repository target   
     * @param  depth                          tree depth to update
     * @param  allowUnversionedObstructions   flag that allows tollerating unversioned items 
     *                                        during update
     * @param  depthIsSticky                  flag that controls whether the requested depth 
     *                                        should be written into the working copy
     * @return                                value of the revision to which the working copy was actually switched
     * @throws SVNException 
     * @since  1.2, SVN 1.5
     */
    public long doSwitch(File path, SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth, 
            boolean allowUnversionedObstructions, boolean depthIsSticky) throws SVNException {
        return doSwitchImpl(null, path, url, pegRevision, revision, depth, allowUnversionedObstructions, depthIsSticky);
    }
    
    /**
     * Checks out a Working Copy from a repository.
     * 
     * <p>
     * If the destination path (<code>dstPath</code>) is <span class="javakeyword">null</span>
     * then the last component of <code>url</code> is used for the local directory name.
     * 
     * <p>
     * As a revision <b>SVNRevision</b>'s pre-defined constant fields can be used. For example,
     * to check out a Working Copy at the latest revision of the repository use 
     * {@link SVNRevision#HEAD HEAD}.
     * 
     * @param  url			a repository location from where a Working Copy will be checked out		
     * @param  dstPath		the local path where the Working Copy will be placed
     * @param  pegRevision	the revision at which <code>url</code> will be firstly seen
     * 						in the repository to make sure it's the one that is needed
     * @param  revision		the desired revision of the Working Copy to be checked out
     * @param  recursive	if <span class="javakeyword">true</span> and <code>url</code> is
     * 						a directory then the entire tree will be checked out, otherwise if 
     * 						<span class="javakeyword">false</span> - only items located immediately
     * 						in the directory itself
     * @return				the revision number of the Working Copy
     * @throws SVNException <code>url</code> refers to a file, not a directory; <code>dstPath</code>
     * 						already exists but it is a file, not a directory; <code>dstPath</code> already
     * 						exists and is a versioned directory but has a different URL (repository location
     * 						against which the directory is controlled)
     * @deprecated use {@link #doCheckout(SVNURL, File, SVNRevision, SVNRevision, SVNDepth, boolean)} instead  
     */
    public long doCheckout(SVNURL url, File dstPath, SVNRevision pegRevision, SVNRevision revision, boolean recursive) throws SVNException {
        return doCheckout(url, dstPath, pegRevision, revision, SVNDepth.fromRecurse(recursive), false);
    }

    /**
     * @param url 
     * @param dstPath 
     * @param pegRevision 
     * @param revision 
     * @param recursive 
     * @param force 
     * @return               actual revision number 
     * @throws SVNException 
     * @deprecated use {@link #doCheckout(SVNURL, File, SVNRevision, SVNRevision, SVNDepth, boolean)} instead
     */
    public long doCheckout(SVNURL url, File dstPath, SVNRevision pegRevision, SVNRevision revision, boolean recursive, boolean force) throws SVNException {
        return doCheckout(url, dstPath, pegRevision, revision, SVNDepth.fromRecurse(recursive), force);
    }
    
    /**
     * Checks out a working copy of <code>url</code> at <code>revision</code>, looked up at 
     * <code>pegRevision</code>, using <code>dstPath</code> as the root directory of the newly
     * checked out working copy. 
     * 
     * <p/>
     * If <code>pegRevision</code> is {@link SVNRevision#UNDEFINED}, then it
     * defaults to {@link SVNRevision#HEAD}.
     * 
     * <p/>
     * <code>revision</code> must represent a valid revision number ({@link SVNRevision#getNumber()} >= 0),
     * or date ({@link SVNRevision#getDate()} != <span class="javakeyword">true</span>), or be equal to 
     * {@link SVNRevision#HEAD}. If <code>revision</code> does not meet these requirements, an exception with 
     * the error code {@link SVNErrorCode#CLIENT_BAD_REVISION} is thrown.
     * 
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, checks out fully recursively.
     * Else if it is {@link SVNDepth#IMMEDIATES}, checks out <code>url</code> and its
     * immediate entries (subdirectories will be present, but will be at
     * depth {@link SVNDepth#EMPTY} themselves); else {@link SVNDepth#FILES},
     * checks out <code>url</code> and its file entries, but no subdirectories; else
     * if {@link SVNDepth#EMPTY}, checks out <code>url</code> as an empty directory at
     * that depth, with no entries present.
     * 
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#UNKNOWN}, then behave as if for
     * {@link SVNDepth#INFINITY}, except in the case of resuming a previous
     * checkout of <code>dstPath</code> (i.e., updating), in which case uses the depth
     * of the existing working copy.
     *
     * <p/>
     * If externals are {@link #isIgnoreExternals() ignored}, doesn't process externals definitions
     * as part of this operation.
     *
     * <p/>
     * If <code>allowUnversionedObstructions</code> is <span class="javakeyword">true</span> then the checkout 
     * tolerates existing unversioned items that obstruct added paths from <code>url</code>. Only
     * obstructions of the same type (file or dir) as the added item are tolerated.  The text of obstructing 
     * files is left as-is, effectively treating it as a user modification after the checkout. Working
     * properties of obstructing items are set equal to the base properties. If 
     * <code>allowUnversionedObstructions</code> is <span class="javakeyword">false</span> then the checkout 
     * will abort if there are any unversioned obstructing items.
     * 
     * <p/>
     * If the caller's {@link ISVNEventHandler} is non-<span class="javakeyword">null</span>, it is invoked 
     * as the checkout processes. Also {@link ISVNEventHandler#checkCancelled()} will be used at various places 
     * during the checkout to check whether the caller wants to stop the checkout.
     * 
     * <p/>
     * This operation requires repository access (in case the repository is not on the same machine, network
     * connection is established).
     *
     * @param url                           a repository location from where a Working Copy will be checked out     
     * @param dstPath                       the local path where the Working Copy will be placed
     * @param pegRevision                   the revision at which <code>url</code> will be firstly seen
     *                                      in the repository to make sure it's the one that is needed
     * @param revision                      the desired revision of the Working Copy to be checked out
     * @param depth                         tree depth
     * @param allowUnversionedObstructions  flag that allows tollerating unversioned items 
     *                                      during 
     * @return                              value of the revision actually checked out from the repository
     * @throws SVNException                 <ul>
     *                                      <li/>{@link SVNErrorCode#UNSUPPORTED_FEATURE} - if <code>url</code> refers to a 
     *                                      file rather than a directory
     *                                      <li/>{@link SVNErrorCode#RA_ILLEGAL_URL} - if <code>url</code> does not exist  
     *                                      </ul>    
     * @since 1.2, SVN 1.5
     */
    public long doCheckout(SVNURL url, File dstPath, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth, 
            boolean allowUnversionedObstructions) throws SVNException {
        if (dstPath == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_FILENAME, "Checkout destination path can not be NULL");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        pegRevision = pegRevision == null ? SVNRevision.UNDEFINED : pegRevision;
        
        if (!revision.isValid() && pegRevision.isValid()) {
            revision = pegRevision;
        }
        
        if (!revision.isValid()) {
            revision = SVNRevision.HEAD;
        }
        
        SVNRepository repos = createRepository(url, null, null, pegRevision, revision, null);
        url = repos.getLocation();
        long revNumber = getRevisionNumber(revision, repos, null);
        SVNNodeKind targetNodeKind = repos.checkPath("", revNumber);
        if (targetNodeKind == SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "URL ''{0}'' refers to a file, not a directory", url);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (targetNodeKind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL ''{0}'' doesn''t exist", url);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        String uuid = repos.getRepositoryUUID(true);
        SVNURL repositoryRoot = repos.getRepositoryRoot(true);

        long result = -1;
        depth = depth == null ? SVNDepth.UNKNOWN : depth;
        SVNWCAccess wcAccess = createWCAccess();
        SVNFileType kind = SVNFileType.getType(dstPath);
        if (kind == SVNFileType.NONE) {
            depth = depth == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : depth;
            SVNAdminAreaFactory.createVersionedDirectory(dstPath, url, repositoryRoot, uuid, revNumber, depth);
            result = update(dstPath, revision, depth, allowUnversionedObstructions, true, false);
        } else if (kind == SVNFileType.DIRECTORY) {
            int formatVersion = SVNAdminAreaFactory.checkWC(dstPath, true);
            if (formatVersion != 0) {
                SVNAdminArea adminArea = wcAccess.open(dstPath, false, 0);
                SVNEntry rootEntry = adminArea.getEntry(adminArea.getThisDirName(), false);
                wcAccess.closeAdminArea(dstPath);
                if (rootEntry.getSVNURL() != null && url.equals(rootEntry.getSVNURL())) {
                    result = update(dstPath, revision, depth, allowUnversionedObstructions, true, false);
                } else {
                    String message = "''{0}'' is already a working copy for a different URL";
                    if (rootEntry.isIncomplete()) {
                        message += "; perform update to complete it";
                    }
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, message, dstPath);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            } else {
                depth = depth == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : depth;
                SVNAdminAreaFactory.createVersionedDirectory(dstPath, url, repositoryRoot, uuid, revNumber, depth);
                result = update(dstPath, revision, depth, allowUnversionedObstructions, true, false);
            }
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NODE_KIND_CHANGE, "''{0}'' already exists and is not a directory", dstPath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return result;
    }
    
    /**
     * Exports a clean directory or single file from a repository.
     * 
     * <p>
     * If <code>eolStyle</code> is not <span class="javakeyword">null</span> then it should denote
     * a specific End-Of-Line marker for the files to be exported. Significant values for 
     * <code>eolStyle</code> are:
     * <ul>
     * <li>"CRLF" (Carriage Return Line Feed) - this causes files to contain '\r\n' line ending sequences 
     * for EOL markers, regardless of the operating system in use (for instance, this EOL marker is used by 
     * software on the Windows platform).
     * <li>"LF" (Line Feed) - this causes files to contain '\n' line ending sequences 
     * for EOL markers, regardless of the operating system in use (for instance, this EOL marker is used by 
     * software on the Unix platform). 
     * <li>"CR" (Carriage Return) - this causes files to contain '\r' line ending sequences 
     * for EOL markers, regardless of the operating system in use (for instance, this EOL marker was used by 
     * software on older Macintosh platforms).
     * <li>"native" - this causes files to contain the EOL markers that are native to the operating system 
     * on which SVNKit is run.
     * </ul>
     * 
     * @param  url				a repository location from where the unversioned directory/file  will
     * 							be exported
     * @param  dstPath			the local path where the repository items will be exported to 			
     * @param  pegRevision		the revision at which <code>url</code> will be firstly seen
     * 							in the repository to make sure it's the one that is needed
     * @param  revision			the desired revision of the directory/file to be exported
     * @param  eolStyle			a string that denotes a specific End-Of-Line charecter;  
     * @param  force			<span class="javakeyword">true</span> to fore the operation even
     * 							if there are local files with the same names as those in the repository
     * 							(local ones will be replaced) 
     * @param  recursive		if <span class="javakeyword">true</span> and <code>url</code> is
     * 							a directory then the entire tree will be exported, otherwise if 
     * 							<span class="javakeyword">false</span> - only items located immediately
     * 							in the directory itself
     * @return					the revision number of the exported directory/file 
     * @throws SVNException
     * @deprecated use {@link #doExport(SVNURL, File, SVNRevision, SVNRevision, String, boolean, SVNDepth)}
     */
    public long doExport(SVNURL url, File dstPath, SVNRevision pegRevision, SVNRevision revision, String eolStyle, 
            boolean force, boolean recursive) throws SVNException {
        return doExport(url, dstPath, pegRevision, revision, eolStyle, force, SVNDepth.fromRecurse(recursive));
    }
    
    /**
     * Exports the contents of a subversion repository into a 'clean' directory (meaning a
     * directory with no administrative directories). 
     * 
     * <p/>
     * <code>pegRevision</code> is the revision where the path is first looked up. 
     * If <code>pegRevision</code> is {@link SVNRevision#UNDEFINED}, 
     * then it defaults to {@link SVNRevision#HEAD}.
     * 
     * <p/>
     * If externals are {@link #isIgnoreExternals() ignored}, doesn't process externals definitions
     * as part of this operation.
     * 
     * <p/>
     * <code>eolStyle</code> allows you to override the standard eol marker on the platform
     * you are running on. Can be either "LF", "CR" or "CRLF" or <span class="javakeyword">null</span>.  
     * If <span class="javakeyword">null</span> will use the standard eol marker. Any other value will cause 
     * an exception with the error code {@link SVNErrorCode#IO_UNKNOWN_EOL} error to be returned.
     * 
     * <p>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, exports fully recursively.
     * Else if it is {@link SVNDepth#IMMEDIATES}, exports <code>url</code> and its immediate
     * children (if any), but with subdirectories empty and at
     * {@link SVNDepth#EMPTY}. Else if {@link SVNDepth#FILES}, exports <code>url</code> and
     * its immediate file children (if any) only.  If <code>depth</code> is {@link SVNDepth#EMPTY}, 
     * then exports exactly <code>url</code> and none of its children.
     * 
     * @param url             repository url to export from
     * @param dstPath         path to export to
     * @param pegRevision     the revision at which <code>url</code> will be firstly seen
     *                        in the repository to make sure it's the one that is needed
     * @param revision        the desired revision of the directory/file to be exported
     * @param eolStyle        a string that denotes a specific End-Of-Line charecter  
     * @param overwrite       if <span class="javakeyword">true</span>, will cause the export to overwrite 
     *                        files or directories
     * @param depth           tree depth
     * @return                value of the revision actually exported
     * @throws SVNException
     * @since  1.2, SVN 1.5
     */
    public long doExport(SVNURL url, File dstPath, SVNRevision pegRevision, SVNRevision revision, String eolStyle, 
            boolean overwrite, SVNDepth depth) throws SVNException {
        long[] revNum = { SVNRepository.INVALID_REVISION }; 
        SVNRepository repository = createRepository(url, null, null, pegRevision, revision, revNum);
        long exportedRevision = doRemoteExport(repository, revNum[0], dstPath, eolStyle, overwrite, depth);
        dispatchEvent(SVNEventFactory.createSVNEvent(null, SVNNodeKind.NONE, null, exportedRevision, 
                SVNEventAction.UPDATE_COMPLETED, null, null, null));
        return exportedRevision;
    }

    /**
     * Exports a clean directory or single file from eihter a source Working Copy or
     * a repository.
     * 
     * <p>
     * How this method works:
     * <ul>
     * <li> If <code>revision</code> is different from {@link SVNRevision#BASE BASE}, 
     * {@link SVNRevision#WORKING WORKING}, {@link SVNRevision#COMMITTED COMMITTED}, 
     * {@link SVNRevision#UNDEFINED UNDEFINED} - then the repository origin of <code>srcPath</code>
     * will be exported (what is done by "remote" {@link #doExport(SVNURL, File, SVNRevision, SVNRevision, String, boolean, boolean)
     * doExport()}).
     * <li> In other cases a clean unversioned copy of <code>srcPath</code> - either a directory or a single file -
     * is exported to <code>dstPath</code>. 
     * </ul>
     * 
     * <p>
     * If <code>eolStyle</code> is not <span class="javakeyword">null</span> then it should denote
     * a specific End-Of-Line marker for the files to be exported. Significant values for 
     * <code>eolStyle</code> are:
     * <ul>
     * <li>"CRLF" (Carriage Return Line Feed) - this causes files to contain '\r\n' line ending sequences 
     * for EOL markers, regardless of the operating system in use (for instance, this EOL marker is used by 
     * software on the Windows platform).
     * <li>"LF" (Line Feed) - this causes files to contain '\n' line ending sequences 
     * for EOL markers, regardless of the operating system in use (for instance, this EOL marker is used by 
     * software on the Unix platform). 
     * <li>"CR" (Carriage Return) - this causes files to contain '\r' line ending sequences 
     * for EOL markers, regardless of the operating system in use (for instance, this EOL marker was used by 
     * software on older Macintosh platforms).
     * <li>"native" - this causes files to contain the EOL markers that are native to the operating system 
     * on which SVNKit is run.
     * </ul>
     * 
     * @param  srcPath			a repository location from where the unversioned directory/file  will
     * 							be exported
     * @param  dstPath			the local path where the repository items will be exported to 			
     * @param  pegRevision		the revision at which <code>url</code> will be firstly seen
     * 							in the repository to make sure it's the one that is needed
     * @param  revision			the desired revision of the directory/file to be exported
     * @param  eolStyle			a string that denotes a specific End-Of-Line charecter;  
     * @param  force			<span class="javakeyword">true</span> to fore the operation even
     * 							if there are local files with the same names as those in the repository
     * 							(local ones will be replaced) 
     * @param  recursive		if <span class="javakeyword">true</span> and <code>url</code> is
     * 							a directory then the entire tree will be exported, otherwise if 
     * 							<span class="javakeyword">false</span> - only items located immediately
     * 							in the directory itself
     * @return					the revision number of the exported directory/file 
     * @throws SVNException
     * @deprecated use {@link #doExport(File, File, SVNRevision, SVNRevision, String, boolean, SVNDepth)}
     */
    public long doExport(File srcPath, final File dstPath, SVNRevision pegRevision, SVNRevision revision, 
            String eolStyle, final boolean force, boolean recursive) throws SVNException {
        return doExport(srcPath, dstPath, pegRevision, revision, eolStyle, force, SVNDepth.fromRecurse(recursive));
    }

    /**
     * Exports the contents of either a subversion repository or a
     * subversion working copy into a 'clean' directory (meaning a 
     * directory with no administrative directories).
     * 
     * <p/>
     * <code>pegRevision</code> is the revision where the path is first looked up
     * when exporting from a repository. If <code>pegRevision</code> is {@link SVNRevision#UNDEFINED}, 
     * then it defaults to {@link SVNRevision#WORKING}.
     * 
     * <p/>
     * If <code>revision</code> is one of:
     * <ul>
     * <li/>{@link SVNRevision#BASE}
     * <li/>{@link SVNRevision#WORKING}
     * <li/>{@link SVNRevision#COMMITTED}
     * <li/>{@link SVNRevision#UNDEFINED}
     * </ul> 
     * then local export is performed. Otherwise exporting from the repository.
     * 
     * <p/>
     * If externals are {@link #isIgnoreExternals() ignored}, doesn't process externals definitions
     * as part of this operation.
     * 
     * <p/>
     * <code>eolStyle</code> allows you to override the standard eol marker on the platform
     * you are running on. Can be either "LF", "CR" or "CRLF" or <span class="javakeyword">null</span>.  
     * If <span class="javakeyword">null</span> will use the standard eol marker. Any other value will cause 
     * an exception with the error code {@link SVNErrorCode#IO_UNKNOWN_EOL} error to be returned.
     * 
     * <p>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, exports fully recursively.
     * Else if it is {@link SVNDepth#IMMEDIATES}, exports <code>srcPath</code> and its immediate
     * children (if any), but with subdirectories empty and at
     * {@link SVNDepth#EMPTY}. Else if {@link SVNDepth#FILES}, exports <code>srcPath</code> and
     * its immediate file children (if any) only.  If <code>depth</code> is {@link SVNDepth#EMPTY}, 
     * then exports exactly <code>srcPath</code> and none of its children.
     * 
     * @param srcPath         working copy path
     * @param dstPath         path to export to
     * @param pegRevision     the revision at which <code>url</code> will be firstly seen
     *                        in the repository to make sure it's the one that is needed
     * @param revision        the desired revision of the directory/file to be exported; used only
     *                        when exporting from a repository
     * @param eolStyle        a string that denotes a specific End-Of-Line charecter  
     * @param overwrite       if <span class="javakeyword">true</span>, will cause the export to overwrite 
     *                        files or directories
     * @param depth           tree depth
     * @return                value of the revision actually exported
     * @throws SVNException
     * @since  1.2, SVN 1.5
     */
    public long doExport(File srcPath, final File dstPath, SVNRevision pegRevision, SVNRevision revision, 
            String eolStyle, final boolean overwrite, SVNDepth depth) throws SVNException {
        long exportedRevision = -1;
        if (revision != SVNRevision.BASE && revision != SVNRevision.WORKING && revision != SVNRevision.COMMITTED && revision != SVNRevision.UNDEFINED) {
            SVNRepository repository = createRepository(null, srcPath, null, pegRevision, revision, null);
            long revisionNumber = getRevisionNumber(revision, repository, srcPath);
            exportedRevision = doRemoteExport(repository, revisionNumber, dstPath, eolStyle, overwrite, depth); 
        } else {
            if (revision == SVNRevision.UNDEFINED) {
                revision = SVNRevision.WORKING;
            }
            copyVersionedDir(srcPath, dstPath, revision, eolStyle, overwrite, depth);
        }
        dispatchEvent(SVNEventFactory.createSVNEvent(null, SVNNodeKind.NONE, null, exportedRevision, SVNEventAction.UPDATE_COMPLETED, null, null, null));
        return exportedRevision;
    }
    
    /**
     * Substitutes the beginning part of a Working Copy's URL with a new one.
     * 
     * <p> 
     * When a repository root location or a URL schema is changed the old URL of the 
     * Working Copy which starts with <code>oldURL</code> should be substituted for a
     * new URL beginning - <code>newURL</code>.
     * 
     * @param  dst				a Working Copy item's path 
     * @param  oldURL			the old beginning part of the repository's URL that should
     * 							be overwritten  
     * @param  newURL			a new beginning part for the repository location that
     * 							will overwrite <code>oldURL</code> 
     * @param  recursive		if <span class="javakeyword">true</span> and <code>dst</code> is
     * 							a directory then the entire tree will be relocated, otherwise if 
     * 							<span class="javakeyword">false</span> - only <code>dst</code> itself
     * @throws SVNException
     */
    public void doRelocate(File dst, SVNURL oldURL, SVNURL newURL, boolean recursive) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminArea adminArea = wcAccess.probeOpen(dst, true, recursive ? SVNWCAccess.INFINITE_DEPTH : 0);
            String name = dst.equals(adminArea.getRoot()) ? adminArea.getThisDirName() : dst.getName();
            String from = oldURL.toString();
            String to = newURL.toString();
            if (from.endsWith("/")) {
                from = from.substring(0, from.length() - 1);
            } 
            if (to.endsWith("/")) {
                to = to.substring(0, to.length() - 1);
            } 
            doRelocate(adminArea, name, from, to, recursive, new SVNHashMap());
        } finally {
            wcAccess.close();
        }
    }

    /**
     * Canonicalizes all urls in the specified Working Copy.
     * 
     * @param dst               a WC path     
     * @param omitDefaultPort   if <span class="javakeyword">true</span> then removes all
     *                          port numbers from urls which equal to default ones, otherwise
     *                          does not
     * @param recursive         recurses an operation
     * @throws SVNException
     */
    public void doCanonicalizeURLs(File dst, boolean omitDefaultPort, boolean recursive) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminAreaInfo adminAreaInfo = wcAccess.openAnchor(dst, true, recursive ? SVNWCAccess.INFINITE_DEPTH : 0);
            SVNAdminArea target = adminAreaInfo.getTarget();
            SVNEntry entry = wcAccess.getEntry(dst, false);
            String name = target.getThisDirName();
            if (entry != null && entry.isFile()) {
                name = entry.getName();
            }
            doCanonicalizeURLs(adminAreaInfo, target, name, omitDefaultPort, recursive);
            if (recursive && !isIgnoreExternals()) {
                for(Iterator externals = adminAreaInfo.getNewExternals().keySet().iterator(); externals.hasNext();) {
                    String path = (String) externals.next();
                    String external = (String) adminAreaInfo.getNewExternals().get(path);
                    SVNExternal[] exts = SVNExternal.parseExternals(path, external);
                    File owner = new Resource(adminAreaInfo.getAnchor().getRoot(), path);
                    for (int i = 0; i < exts.length; i++) {
                        File externalFile = new Resource(owner, exts[i].getPath()); 
                        try {
                            doCanonicalizeURLs(externalFile, omitDefaultPort, true);
                        } catch (SVNCancelException e) {
                            throw e;
                        } catch (SVNException e) {
                            getDebugLog().logFine(SVNLogType.WC, e);
                        }
                    }
                }
            }
        } finally {
            wcAccess.close();
        }
    }
    
    /**
     * Sets whether keywords must be expanded during an export operation.
     * 
     * @param expand <span class="javakeyword">true</span> to expand;
     *               otherwise <span class="javakeyword">false</span>
     * @since 1.3
     */
    public void setExportExpandsKeywords(boolean expand) {
        myIsExportExpandsKeywords = expand;
    }

    /**
     * Says whether keywords expansion during export operations is turned on or not.
     * @return <span class="javakeyword">true</span> if expanding keywords;
     *         <span class="javakeyword">false</span> otherwise
     * @since  1.3
     */
    public boolean isExportExpandsKeywords() {
        return myIsExportExpandsKeywords;
    }

    private void copyVersionedDir(File from, File to, SVNRevision revision, String eolStyle, boolean force, SVNDepth depth) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        SVNAdminArea adminArea = wcAccess.probeOpen(from, false, 0);
        
        SVNEntry entry = null;
        try {
            entry = wcAccess.getVersionedEntry(from, false);
        } catch (SVNException svne) {
            wcAccess.close();
            throw svne;
        }
        
        if (revision == SVNRevision.WORKING && entry.isScheduledForDeletion()) {
            return;
        }
        if (revision != SVNRevision.WORKING && entry.isScheduledForAddition()) {
            return;
        }
        if (entry.isDirectory()) {
            // create dir
            boolean dirCreated = to.mkdirs();
            if (!to.exists() || to.isFile()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot create directory ''{0}''", to);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (!dirCreated && to.isDirectory() && !force) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "''{0}'' already exists and will not be owerwritten unless forced", to);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            // read entries
            for (Iterator ents = adminArea.entries(false); ents.hasNext();) {
                SVNEntry childEntry = (SVNEntry) ents.next();
                if (childEntry.isDirectory()) {
                    if (adminArea.getThisDirName().equals(childEntry.getName())) {
                        continue;
                    } else if (depth == SVNDepth.INFINITY) {
                        File childTo = new Resource(to, childEntry.getName());
                        File childFrom = new Resource(from, childEntry.getName());
                        copyVersionedDir(childFrom, childTo, revision, eolStyle, force, depth);
                    }
                } else if (childEntry.isFile()) {
                    File childTo = new Resource(to, childEntry.getName());
                    copyVersionedFile(childTo, adminArea, childEntry.getName(), revision, eolStyle);
                }
            }
            if (!isIgnoreExternals() && depth == SVNDepth.INFINITY && entry.getDepth() == SVNDepth.INFINITY) {
                SVNVersionedProperties properties = adminArea.getProperties(adminArea.getThisDirName());
                String externalsValue = properties.getStringPropertyValue(SVNProperty.EXTERNALS);
                if (externalsValue != null) {
                    SVNExternal[] externals = SVNExternal.parseExternals(adminArea.getRoot().getAbsolutePath(), externalsValue);
                    for (int i = 0; i < externals.length; i++) {
                        SVNExternal info = externals[i];
                        File srcPath = new Resource(adminArea.getRoot(), info.getPath());
                        File dstPath = new Resource(to, info.getPath());
                        if (SVNPathUtil.getSegmentsCount(info.getPath()) > 1) {
                            if (!dstPath.getParentFile().exists() && !dstPath.getParentFile().mkdirs()) {
                                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CLIENT_IS_DIRECTORY, "Could not create directory ''{0}''", dstPath.getParentFile()), SVNLogType.WC);
                            }
                        }
                        copyVersionedDir(srcPath, dstPath, revision, eolStyle, force, SVNDepth.INFINITY);
                    }
                }
            }
        } else if (entry.isFile()) {
            copyVersionedFile(to, adminArea, entry.getName(), revision, eolStyle);
        }
        
        wcAccess.close();
    }

    private void copyVersionedFile(File dstPath, SVNAdminArea adminArea, String fileName, SVNRevision revision, String eol) throws SVNException {
        SVNEntry entry = adminArea.getEntry(fileName, false);
        if (revision == SVNRevision.WORKING && entry.isScheduledForDeletion()) {
            return;
        }
        if (revision != SVNRevision.WORKING && entry.isScheduledForAddition()) {
            return;
        }
        boolean modified = false;
        SVNVersionedProperties props = null;
        long timestamp;
        if (revision != SVNRevision.WORKING) {
            props = adminArea.getBaseProperties(fileName);
        } else {
            props = adminArea.getProperties(fileName);
            modified = adminArea.hasTextModifications(fileName, false);
        }
        boolean special = props.getPropertyValue(SVNProperty.SPECIAL) != null;
        boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;
        String keywords = props.getStringPropertyValue(SVNProperty.KEYWORDS);
        String charsetProp = props.getStringPropertyValue(SVNProperty.CHARSET);
        String charset = SVNTranslator.getCharset(charsetProp, adminArea.getFile(fileName).getPath(), getOptions());
        byte[] eols = eol != null ? SVNTranslator.getEOL(eol, getOptions()) : null;
        if (eols == null) {
            eol = props.getStringPropertyValue(SVNProperty.EOL_STYLE);
            eols = SVNTranslator.getEOL(eol, getOptions());
        }
        if (modified && !special) {
            timestamp = adminArea.getFile(fileName).lastModified();
        } else {
            timestamp = SVNDate.parseDateAsMilliseconds(entry.getCommittedDate());
        }
        Map keywordsMap = null;
        if (keywords != null) {
            String rev = Long.toString(entry.getCommittedRevision());
            String author;
            if (modified) {
                author = "(local)";
                rev += "M";
            } else {
                author = entry.getAuthor();                
            }
            keywordsMap = SVNTranslator.computeKeywords(keywords, entry.getURL(), author, entry.getCommittedDate(), rev, getOptions());            
        }
        File srcFile = revision == SVNRevision.WORKING ? adminArea.getFile(fileName) : adminArea.getBaseFile(fileName, false);
        SVNFileType fileType = SVNFileType.getType(srcFile);
        if (fileType == SVNFileType.SYMLINK && revision == SVNRevision.WORKING) {
            // base will be translated OK, but working not.
            File tmpBaseFile = adminArea.getBaseFile(fileName, true);
            try {
                SVNTranslator.translate(srcFile, tmpBaseFile, charset, eols, keywordsMap, special, false);
                SVNTranslator.translate(tmpBaseFile, dstPath, charset, eols, keywordsMap, special, true);
            } finally {
                tmpBaseFile.delete();
            }
        } else {
            SVNTranslator.translate(srcFile, dstPath, charset, eols, keywordsMap, special, true);
        }
        if (executable) {
            SVNFileUtil.setExecutable(dstPath, true);
        }
        if (!special && timestamp > 0) {
            dstPath.setLastModified(timestamp);
        }
    }

    private long doRemoteExport(SVNRepository repository, final long revNumber, File dstPath, String eolStyle, boolean force, SVNDepth depth) throws SVNException {
        SVNNodeKind dstKind = repository.checkPath("", revNumber);
        if (dstKind == SVNNodeKind.DIR) {
            SVNExportEditor editor = new SVNExportEditor(this, repository.getLocation().toString(), dstPath,  force, eolStyle, isExportExpandsKeywords(), getOptions());
            repository.update(revNumber, null, depth, false, new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, revNumber, SVNDepth.INFINITY, true);
                    reporter.finishReport();
                }
            }, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
            // nothing may be created.
            SVNFileType fileType = SVNFileType.getType(dstPath);
            if (fileType == SVNFileType.NONE) {
                editor.openRoot(revNumber);
            }
            if (!isIgnoreExternals() && depth == SVNDepth.INFINITY) {
                Map externals = editor.getCollectedExternals();
                handleExternals(null, dstPath, Collections.EMPTY_MAP, externals, Collections.EMPTY_MAP, repository.getLocation(), repository.getRepositoryRoot(true), 
                        depth, true, true);
            }
        } else if (dstKind == SVNNodeKind.FILE) {
            String url = repository.getLocation().toString();
            if (dstPath.isDirectory()) {
                dstPath = new Resource(dstPath, SVNEncodingUtil.uriDecode(SVNPathUtil.tail(url)));
            }
            if (dstPath.exists()) {
                if (!force) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Path ''{0}'' already exists", dstPath);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            } else {
                dstPath.getParentFile().mkdirs();
            }
            SVNProperties properties = new SVNProperties();
            OutputStream os = null;
            File tmpFile = SVNFileUtil.createUniqueFile(dstPath.getParentFile(), ".export", ".tmp", false);
            try {
                os = SVNFileUtil.openFileForWriting(tmpFile);
                try {
                    repository.getFile("", revNumber, properties, new SVNCancellableOutputStream(os, this));
                } finally {
                    SVNFileUtil.closeFile(os);
                }
                if (force && dstPath.exists()) {
                    SVNFileUtil.deleteAll(dstPath, this);
                }
                if (!isExportExpandsKeywords()) {
                    properties.put(SVNProperty.MIME_TYPE, "application/octet-stream");
                }
                boolean binary = SVNProperty.isBinaryMimeType(properties.getStringValue(SVNProperty.MIME_TYPE));
                String charset = SVNTranslator.getCharset(properties.getStringValue(SVNProperty.CHARSET), url, getOptions());
                Map keywords = SVNTranslator.computeKeywords(properties.getStringValue(SVNProperty.KEYWORDS), url,
                        properties.getStringValue(SVNProperty.LAST_AUTHOR),
                        properties.getStringValue(SVNProperty.COMMITTED_DATE),
                        properties.getStringValue(SVNProperty.COMMITTED_REVISION), getOptions());
                byte[] eols = null;
                if (SVNProperty.EOL_STYLE_NATIVE.equals(properties.getStringValue(SVNProperty.EOL_STYLE))) {
                    eols = SVNTranslator.getEOL(eolStyle != null ? eolStyle : properties.getStringValue(SVNProperty.EOL_STYLE), getOptions());
                } else if (properties.containsName(SVNProperty.EOL_STYLE)) {
                    eols = SVNTranslator.getEOL(properties.getStringValue(SVNProperty.EOL_STYLE), getOptions());
                }
                if (binary) {
                    charset = null;
                    eols = null;
                    keywords = null;
                }
                SVNTranslator.translate(tmpFile, dstPath, charset, eols, keywords, properties.getStringValue(SVNProperty.SPECIAL) != null, true);
            } finally {
                SVNFileUtil.deleteFile(tmpFile);
            }
            if (properties.getStringValue(SVNProperty.EXECUTABLE) != null) {
                SVNFileUtil.setExecutable(dstPath, true);
            }
            dispatchEvent(SVNEventFactory.createSVNEvent(dstPath, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNEventAction.UPDATE_ADD, null, null, null));
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL ''{0}'' doesn't exist", repository.getLocation());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return revNumber;
    }

    private void doCanonicalizeURLs(SVNAdminAreaInfo adminAreaInfo, SVNAdminArea adminArea, String name, boolean omitDefaultPort, boolean recursive) throws SVNException {
        boolean save = false;
        checkCancelled();
        if (!adminArea.getThisDirName().equals(name)) {
            SVNEntry entry = adminArea.getEntry(name, true);
            save = canonicalizeEntry(entry, omitDefaultPort);
            adminArea.getWCProperties(name).setPropertyValue(SVNProperty.WC_URL, null);
            if (save) {
                adminArea.saveEntries(false);
            }
            return;
        }
        if (!isIgnoreExternals()) {
            SVNPropertyValue externalsValue = adminArea.getProperties(adminArea.getThisDirName()).getPropertyValue(SVNProperty.EXTERNALS);
            if (externalsValue != null) {
                String ownerPath = adminArea.getRelativePath(adminAreaInfo.getAnchor());
                String externals = externalsValue == null ? null : externalsValue.getString();
                adminAreaInfo.addExternal(ownerPath, externals, externals);
                if (externalsValue != null) {
                    externalsValue = SVNPropertyValue.create(canonicalizeExtenrals(externals, omitDefaultPort));
                    adminArea.getProperties(adminArea.getThisDirName()).setPropertyValue(SVNProperty.EXTERNALS, externalsValue);
                }
            }
        }
        
        SVNEntry rootEntry = adminArea.getEntry(adminArea.getThisDirName(), true);
        save = canonicalizeEntry(rootEntry, omitDefaultPort);
        adminArea.getWCProperties(adminArea.getThisDirName()).setPropertyValue(SVNProperty.WC_URL, null);
        // now all child entries that doesn't has repos/url has new values.
        for(Iterator ents = adminArea.entries(true); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (adminArea.getThisDirName().equals(entry.getName())) {
                continue;
            }
            checkCancelled();
            if (recursive && entry.isDirectory() && 
                    (entry.isScheduledForAddition() || !entry.isDeleted()) &&
                    !entry.isAbsent()) {
                SVNAdminArea childArea = adminArea.getWCAccess().retrieve(adminArea.getFile(entry.getName()));
                if (childArea != null) {
                    doCanonicalizeURLs(adminAreaInfo, childArea, "", omitDefaultPort, recursive);
                }
            }
            save |= canonicalizeEntry(entry, omitDefaultPort);
            SVNVersionedProperties properties = adminArea.getWCProperties(entry.getName());
            if (properties != null) {
                properties.setPropertyValue(SVNProperty.WC_URL, null);
            }
        }
        if (save) {
            adminArea.saveEntries(true);
        }
    }
    
    private static String canonicalizeExtenrals(String externals, boolean omitDefaultPort) throws SVNException {
        if (externals == null) {
            return null;
        }
        StringBuffer canonicalized = new StringBuffer();
        for(StringTokenizer lines = new StringTokenizer(externals, "\r\n", true); lines.hasMoreTokens();) {
            String line = lines.nextToken();
            if (line.trim().length() == 0 || line.trim().startsWith("#") 
                    || line.indexOf('\r') >= 0 || line.indexOf('\n') >= 0) {
                canonicalized.append(line);
                continue;
            }
            String[] tokens = line.split("[ \t]");
            int index = tokens.length - 1;
            SVNURL url = null;
            if (index >= 1) {
                try {
                    url = SVNURL.parseURIEncoded(tokens[index]);
                } catch (SVNException e) {
                    url = null;
                }
            } 
            SVNURL canonicalURL = canonicalizeURL(url, omitDefaultPort);
            if (canonicalURL == null) {
                canonicalized.append(line);
            } else {
                canonicalized.append(tokens[0]);
                canonicalized.append(' ');
                if (index == 2) {
                    canonicalized.append(tokens[1]);
                    canonicalized.append(' ');
                }
                canonicalized.append(canonicalURL.toString());
            }
        }
        return canonicalized.toString();
    }
    
    private static boolean canonicalizeEntry(SVNEntry entry, boolean omitDefaultPort) throws SVNException {
        boolean updated = false;
        SVNURL root = canonicalizeURL(entry.getRepositoryRootURL(), omitDefaultPort);
        if (root != null) {
            updated |= entry.setRepositoryRootURL(root);            
        }
        SVNURL url = canonicalizeURL(entry.getSVNURL(), omitDefaultPort);
        if (url != null) {
            updated |= entry.setURL(url.toString());
        }
        SVNURL copyFrom = canonicalizeURL(entry.getCopyFromSVNURL(), omitDefaultPort);
        if (copyFrom != null) {
            updated |= entry.setCopyFromURL(copyFrom.toString());
        }
        return updated;
    }
    
    private static SVNURL canonicalizeURL(SVNURL url, boolean omitDefaultPort) throws SVNException {
        if (url == null || url.getPort() <= 0) {
            // no url or file url.
            return null;
        }
        int defaultPort = SVNURL.getDefaultPortNumber(url.getProtocol());
        if (defaultPort <= 0) {
            // file or svn+ext URL.
            return null;
        }
        if (omitDefaultPort) {
            // remove port if it is same as default.
            if (url.hasPort() && url.getPort() == defaultPort) {
                return SVNURL.create(url.getProtocol(), url.getUserInfo(), url.getHost(), -1, url.getPath(), false);
            }
        } else if (!url.hasPort()) {
            // set port if there is no port set.
            return SVNURL.create(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), false);
        }
        return null;
    }

    private void handleExternals(SVNWCAccess wcAccess, File root, Map oldExternals, Map newExternals, Map depths, SVNURL fromURL, SVNURL rootURL, 
            SVNDepth requestedDepth, boolean isExport, boolean updateUnchanged) throws SVNException {
        Set diff = new SVNHashSet();
        if (oldExternals != null) {
            diff.addAll(oldExternals.keySet());
        } 
        if (newExternals != null) {
            diff.addAll(newExternals.keySet());
        }
        // now we have diff.
        for (Iterator diffPaths = diff.iterator(); diffPaths.hasNext();) {
            String diffPath = (String) diffPaths.next();
            SVNDepth ambientDepth = depths == Collections.EMPTY_MAP ? SVNDepth.INFINITY : (SVNDepth) depths.get(diffPath);
            if (ambientDepth == null) {
                // TODO convert diffpath to full path.
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Traversal of ''{0}'' found no ambient depth", diffPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (!ambientDepth.isRecursive() || !requestedDepth.isRecursive()) {
                // skip externals - either folder depth is not recursive,
                // or requested depth is not recursive.
                continue;
            }

            String oldValue = (String) oldExternals.get(diffPath);
            String newValue = (String) newExternals.get(diffPath);

            // TODO convert diffpath to full path.
            SVNExternal[] previous = oldValue != null ? SVNExternal.parseExternals(diffPath, oldValue) : null;
            SVNExternal[] current = newValue != null ? SVNExternal.parseExternals(diffPath, newValue) : null;
            Map oldParsedExternals = new LinkedHashMap();
            Map newParsedExternals = new LinkedHashMap();
            // put to another hashes.
            for (int i = 0; current != null && i < current.length; i++) {
                newParsedExternals.put(current[i].getPath(), current[i]);
            }
            for (int i = 0; previous != null && i < previous.length; i++) {
                oldParsedExternals.put(previous[i].getPath(), previous[i]);
            }
            // finally handle changes.
            ExternalDiff externalDiff = new ExternalDiff();
            externalDiff.isExport = isExport;
            externalDiff.isUpdateUnchanged = updateUnchanged;
            externalDiff.rootURL = rootURL;

            for (Iterator paths = oldParsedExternals.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                externalDiff.oldExternal = (SVNExternal) oldParsedExternals.get(path);
                externalDiff.newExternal = (SVNExternal) newParsedExternals.get(path);
                externalDiff.owner = new Resource(root, diffPath);
                if (!isExport) {
                    externalDiff.ownerURL = getOwnerURL(externalDiff.owner);
                } 
                if (externalDiff.ownerURL == null) {
                    externalDiff.ownerURL = fromURL.appendPath(diffPath, false);
                }                    
                handleExternalItemChange(wcAccess, externalDiff.oldExternal.getPath(), externalDiff);
            }
            for (Iterator paths = newParsedExternals.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                if (!oldParsedExternals.containsKey(path)) {
                    externalDiff.oldExternal = null;
                    externalDiff.newExternal = (SVNExternal) newParsedExternals.get(path);
                    externalDiff.owner = new Resource(root, diffPath);
                    if (!isExport) {
                        externalDiff.ownerURL = getOwnerURL(externalDiff.owner);
                    } 
                    if (externalDiff.ownerURL == null) {
                        externalDiff.ownerURL = fromURL.appendPath(diffPath, false);
                    }                    
                    handleExternalItemChange(wcAccess, externalDiff.newExternal.getPath(), externalDiff);
                }
            }
        }
    }
    
    private SVNURL getOwnerURL(File root) {
        if (root != null && SVNFileType.getType(root) == SVNFileType.DIRECTORY) {
            SVNWCAccess access = createWCAccess();
            try {
                access.open(root, false, 0);
                SVNEntry entry = access.getVersionedEntry(root, false);
                if (entry != null) {
                    return entry.getSVNURL();
                }
            } catch (SVNException e) {
                e.printStackTrace();
            } finally {
                if (access != null) {
                    try {
                        access.close();
                    } catch (SVNException e) {
                    }
                }
            }
        }
        return null;
    }
    
    
    private void handleExternalItemChange(SVNWCAccess access, String targetDir, ExternalDiff externalDiff) throws SVNException {
        try {
            handleExternalChange(access, targetDir, externalDiff);
        } catch (SVNException svne) {
            File target = new Resource(externalDiff.owner, targetDir);
            SVNEvent event = SVNEventFactory.createSVNEvent(target, SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, 
                    SVNEventAction.FAILED_EXTERNAL, SVNEventAction.UPDATE_EXTERNAL, svne.getErrorMessage(), null);
            dispatchEvent(event);
        }
    }
    /**
     * oldURL is null when externals is added: 
     * jsvn ps svn:externals "path URL" .
     * jsvn up .
     * 
     * 
     * newURL is null when external is deleted:
     * jsvn pd svn:externals .
     * jsvn up .
     * 
     * Also newURL or oldURL could be null, when external property is added or 
     * removed by update itself (someone else has changed it). For instance, 
     * oldURL is always null during checkout or export operation.
     */
    private void handleExternalChange(SVNWCAccess access, String targetDir, ExternalDiff externalDiff) throws SVNException {
        File target = new Resource(externalDiff.owner, targetDir);
        SVNURL oldURL = null;
        SVNURL newURL = null;
        String externalDefinition = null;
        if (externalDiff.oldExternal != null && !externalDiff.isExport) {
            oldURL = externalDiff.oldExternal.resolveURL(externalDiff.rootURL, externalDiff.ownerURL);
            externalDefinition = externalDiff.oldExternal.getRawValue(); 
        }
        SVNRevision externalRevision = SVNRevision.UNDEFINED;
        SVNRevision externalPegRevision = SVNRevision.UNDEFINED;
        if (externalDiff.newExternal != null) {
            newURL = externalDiff.newExternal.resolveURL(externalDiff.rootURL, externalDiff.ownerURL);
            externalRevision = externalDiff.newExternal.getRevision();
            externalPegRevision = externalDiff.newExternal.getPegRevision();
            externalDefinition = externalDiff.newExternal.getRawValue();
        }
        if (oldURL == null && newURL == null) {
            return;
        }

        SVNRevision[] revs = getExternalsHandler().handleExternal(target, newURL, externalRevision, 
                externalPegRevision, externalDefinition, SVNRevision.UNDEFINED);
        if (revs == null) {
            SVNEvent event = SVNEventFactory.createSVNEvent(target, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, 
                    SVNEventAction.SKIP, SVNEventAction.UPDATE_EXTERNAL, null, null);
            dispatchEvent(event);
            return;
        }
        
        externalRevision = revs.length > 0 && revs[0] != null ? revs[0] : externalRevision;
        externalPegRevision = revs.length > 1 && revs[1] != null ? revs[1] : externalPegRevision;
        
        SVNRepository repository = null;
        SVNNodeKind kind = null;
        SVNURL reposRootURL = null;
        if (newURL != null) {
            long[] rev = { SVNRepository.INVALID_REVISION };
            repository = createRepository(newURL, null, null, externalPegRevision, externalRevision, rev);
            reposRootURL = repository.getRepositoryRoot(true);
            kind = repository.checkPath("", rev[0]);
            if (kind == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL ''{0}'' at revision {1} doesn''t exist", 
                        new Object[] { repository.getLocation(), String.valueOf(rev[0]) });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            
            if (kind != SVNNodeKind.DIR && kind != SVNNodeKind.FILE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL ''{0}'' at revision {1} is not a file or a directory",
                        new Object[] { repository.getLocation(), String.valueOf(rev[0]) });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        
        try {
            setEventPathPrefix("path");
            if (oldURL == null) {
                if (kind == SVNNodeKind.DIR) {
                    target.mkdirs();
                    dispatchEvent(SVNEventFactory.createSVNEvent(target, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, 
                            SVNEventAction.UPDATE_EXTERNAL, null, null, null));
                    if (externalDiff.isExport) {
                        doExport(newURL, target, externalPegRevision, externalRevision, null, true, SVNDepth.INFINITY); 
                    } else {
                        doCheckout(newURL, target, externalPegRevision, externalRevision, SVNDepth.INFINITY, false);
                    }
                } else if (kind == SVNNodeKind.FILE) {
                    dispatchEvent(SVNEventFactory.createSVNEvent(target, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, 
                            SVNEventAction.UPDATE_EXTERNAL, null, null, null));

                    if (externalDiff.isExport) {
                        boolean ignoreExternals = isIgnoreExternals();
                        setIgnoreExternals(true);
                        doExport(newURL, target, externalPegRevision, externalRevision, null, false, SVNDepth.INFINITY);
                        setIgnoreExternals(ignoreExternals);
                    } else {
                        switchFileExternal(access, target, newURL, externalPegRevision, externalRevision, reposRootURL);
                    }
                }
            } else if (newURL == null) {
                SVNWCAccess wcAccess = createWCAccess();
                SVNAdminArea area = wcAccess.open(target, true, SVNWCAccess.INFINITE_DEPTH);
                SVNException error = null;
                try {
                    area.removeFromRevisionControl(area.getThisDirName(), true, false);
                } catch (SVNException svne) {
                    error = svne;
                }
                if (error == null || error.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LEFT_LOCAL_MOD) {
                    try {
                        wcAccess.close();
                    } catch (SVNException svne) {
                        error = error == null ? svne : error;
                    }
                }
                if (error != null && error.getErrorMessage().getErrorCode() != SVNErrorCode.WC_LEFT_LOCAL_MOD) {
                    throw error;
                }
            } else if (externalDiff.isUpdateUnchanged || !externalDiff.compareExternals(oldURL, newURL)) {
                if (kind == SVNNodeKind.DIR) {
                    SVNFileType fileType = SVNFileType.getType(target);
                    boolean empty = false;
                    if (fileType == SVNFileType.DIRECTORY) {
                        File[] children = target.listFiles();
                        if (children != null && children.length == 0) {
                            empty = true;
                        }
                    }
                    
                    if (fileType == SVNFileType.DIRECTORY && !empty) {
                        dispatchEvent(SVNEventFactory.createSVNEvent(target, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.UPDATE_EXTERNAL, null, null, null));
                        SVNWCAccess wcAccess = createWCAccess();
                        SVNAdminArea area = wcAccess.open(target, true, 0);
                        SVNEntry entry = area.getEntry(area.getThisDirName(), false);
                        wcAccess.close();
                        String url = entry.getURL();
        
                        if (entry != null && entry.getURL() != null) {
                            if (newURL.toString().equals(url)) {
                                doUpdate(target, externalRevision, SVNDepth.UNKNOWN, true, false);
                                return;
                            } else if (entry.getRepositoryRoot() != null) {
                                if (!SVNPathUtil.isAncestor(entry.getRepositoryRoot(), newURL.toString())) {
                                    SVNRepository repos = createRepository(newURL, null, null, true);
                                    SVNURL reposRoot = repos.getRepositoryRoot(true);
                                    try {
                                        doRelocate(target, entry.getSVNURL(), reposRoot, true);
                                    } catch (SVNException svne) {
                                        if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_INVALID_RELOCATION || 
                                                svne.getErrorMessage().getErrorCode() == SVNErrorCode.CLIENT_INVALID_RELOCATION) {
                                            deleteExternal(target);
                                            target.mkdirs();
                                            doCheckout(newURL, target, externalPegRevision, externalRevision, SVNDepth.INFINITY, false);
                                            return;
                                        } 
                                        throw svne;
                                    }
                                }
                                doSwitch(target, newURL, externalPegRevision, 
                                        externalRevision, SVNDepth.INFINITY, false, true);
                                return;
                            }
                        }
                        deleteExternal(target);
                        target.mkdirs();
                        dispatchEvent(SVNEventFactory.createSVNEvent(target, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.UPDATE_EXTERNAL, null, null, null));
                        doCheckout(newURL, target, externalPegRevision, externalRevision, SVNDepth.INFINITY, false);
                        return;
                    } 
                    if (fileType != SVNFileType.DIRECTORY) {
                        target.mkdirs();
                    }
                    dispatchEvent(SVNEventFactory.createSVNEvent(target, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.UPDATE_EXTERNAL, null, null, null));
                    doCheckout(newURL, target, externalPegRevision, externalRevision, SVNDepth.INFINITY, true);
                } else {
                    dispatchEvent(SVNEventFactory.createSVNEvent(target, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, 
                            SVNEventAction.UPDATE_EXTERNAL, null, null, null));
                    switchFileExternal(access, target, newURL, externalPegRevision, externalRevision, reposRootURL);
                }
            }
        } catch (SVNCancelException cancel) {
            throw cancel;
        } catch (SVNException e) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, e); 
            SVNEvent event = SVNEventFactory.createSVNEvent(target, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.SKIP, SVNEventAction.UPDATE_EXTERNAL, e.getErrorMessage(), null);
            dispatchEvent(event);
        } finally {
            setEventPathPrefix(null);
        }
    }

    private void switchFileExternal(SVNWCAccess wcAccess, File path, SVNURL url, SVNRevision pegRevision, SVNRevision revision, 
            SVNURL reposRootURL) throws SVNException {
        String target = SVNWCManager.getActualTarget(path);
        File anchor = "".equals(target) ? path : path.getParentFile();
        
        boolean closeTarget = false;
        boolean revertFile = false;
        boolean removeFromRevisionControl = false;
        boolean unlinkFile = false;
        boolean cleanUp = false;
        boolean ignoreExternals = isIgnoreExternals();
        SVNAdminArea targetArea = null;
        try {
            try {
                targetArea = wcAccess.retrieve(anchor);
            } catch (SVNException svne) {
                SVNErrorMessage err = svne.getErrorMessage();
                if (err.getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                    SVNWCAccess targetAccess = SVNWCAccess.newInstance(null);
                    targetArea = targetAccess.open(anchor, true, 1);
                    closeTarget = true;
                    SVNURL dstWCReposRootURL = getReposRoot(anchor, null, SVNRevision.BASE, targetArea, targetAccess);
                    if (!reposRootURL.equals(dstWCReposRootURL)) {
                        SVNErrorMessage err1 = SVNErrorMessage.create(SVNErrorCode.RA_REPOS_ROOT_URL_MISMATCH, 
                                "Cannot insert a file external from ''{0}'' into a working copy from a different repository rooted at ''{1}''", 
                                new Object[] { url, dstWCReposRootURL });
                        SVNErrorManager.error(err1, SVNLogType.WC);
                    }
                } else {
                    throw svne;
                }
            }
            
            if (targetArea.getFormatVersion() < SVNAdminArea16.WC_FORMAT) {
                dispatchEvent(SVNEventFactory.createSVNEvent(path, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, 
                        SVNEventAction.SKIP, SVNEventAction.UPDATE_EXTERNAL, null, null));
                return;
            }

            SVNEntry entry = targetArea.getEntry(target, false);
            if (entry != null) {
                if (entry.getExternalFilePath() == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_FILE_EXTERNAL_OVERWRITE_VERSIONED, 
                            "The file external from ''{0}'' cannot overwrite the existing versioned item at ''{1}''", 
                            new Object[] { url, path });
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            } else {
                targetArea.getVersionedEntry(targetArea.getThisDirName(), false);
                boolean hasPropConflicts = targetArea.hasPropConflict(targetArea.getThisDirName());
                boolean hasTreeConflicts = targetArea.hasTreeConflict(targetArea.getThisDirName());
                if (hasPropConflicts || hasTreeConflicts) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, 
                            "The file external from ''{0}'' cannot be written to ''{1}'' while ''{2}'' remains in conflict", 
                            new Object[] { url, path, anchor });
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                
                if (!path.exists()) {
                    SVNFileUtil.createEmptyFile(path);
                    unlinkFile = true;
                }

                
                ISVNEventHandler eventHandler = targetArea.getWCAccess().getEventHandler(); 
                try {
                    targetArea.getWCAccess().setEventHandler(null);
                    SVNWCManager.add(path, targetArea, null, SVNRepository.INVALID_REVISION, SVNDepth.INFINITY);
                } catch (SVNException svne) {
                    cleanUp = true;
                    throw svne;
                } finally {
                    if (eventHandler != null) {
                        targetArea.getWCAccess().setEventHandler(eventHandler);
                    }
                }
                
                revertFile = true;
                
                try {
                    targetArea.setFileExternalLocation(target, url, pegRevision, revision, reposRootURL);
                } catch (SVNException svne) {
                    cleanUp = true;
                    throw svne;
                }
            }
            
            setIgnoreExternals(true);
            try {
                doSwitchImpl(targetArea.getWCAccess(), path, url, pegRevision, revision, SVNDepth.EMPTY, false, false);
            } catch (SVNException svne) {
                cleanUp = true;
                throw svne;
            }
            
            if (unlinkFile) {
                revertFile = false;
                removeFromRevisionControl = true;
            }
        } catch (SVNException svne) {
            if (cleanUp) {
                if (revertFile) {
                    SVNWCClient wcClient = new SVNWCClient(getRepositoryPool(), getOptions());
                    try {
                        wcClient.doRevert(new File[] { path }, SVNDepth.EMPTY, null);
                    } catch (SVNException svne2) {
                        //ignore
                    }
                }
                if (removeFromRevisionControl) {
                    try {
                        targetArea.removeFromRevisionControl(target, true, false);
                    } catch (SVNException svne2) {
                        //ignore
                    }
                }
                if (unlinkFile) {
                    try {
                        SVNFileUtil.deleteFile(path);
                    } catch (SVNException svne2) {
                        //ignore
                    }
                }
            }
            throw svne;
        } finally {
            setIgnoreExternals(ignoreExternals);
            if (closeTarget) {
                targetArea.getWCAccess().close();
            }
        }
    }
    
    private void deleteExternal(File external) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        SVNAdminArea adminArea = wcAccess.open(external, true, SVNWCAccess.INFINITE_DEPTH);
        SVNException error = null;
        try {
            adminArea.removeFromRevisionControl(adminArea.getThisDirName(), true, false);
        } catch (SVNException svne) {
            getDebugLog().logFine(SVNLogType.WC, svne);
            error = svne;
        }
        
        if (error == null || error.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LEFT_LOCAL_MOD) {
            wcAccess.close();
        }
        
        if (error != null && error.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LEFT_LOCAL_MOD) {
            external.getParentFile().mkdirs();
            File newLocation = SVNFileUtil.createUniqueFile(external.getParentFile(), external.getName(), ".OLD", false);
            SVNFileUtil.rename(external, newLocation);
        } else if (error != null) {
            throw error;
        }
    }

    private Map validateRelocateTargetURL(SVNURL targetURL, String expectedUUID, Map validatedURLs, boolean isRoot) throws SVNException {
        if (validatedURLs == null) {
            return null;
        }

        for(Iterator targetURLs = validatedURLs.keySet().iterator(); targetURLs.hasNext();) {
            SVNURL validatedURL = (SVNURL) targetURLs.next();
            if (targetURL.toString().startsWith(validatedURL.toString())) {
                if (isRoot && !targetURL.equals(validatedURL)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_RELOCATION, "''{0}'' is not the root of the repository", targetURL);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                String validatedUUID = (String) validatedURLs.get(validatedURL);
                if (expectedUUID != null && !expectedUUID.equals(validatedUUID)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_RELOCATION, "The repository at ''{0}'' has uuid ''{1}'', but the WC has ''{2}''",
                            new Object[] { validatedURL, validatedUUID, expectedUUID });
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                return validatedURLs;
            }
        }
        SVNRepository repos = createRepository(targetURL, null, null, false);
        try {
            SVNURL actualRoot = repos.getRepositoryRoot(true);
            if (isRoot && !targetURL.equals(actualRoot)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_RELOCATION, "''{0}'' is not the root of the repository", targetURL);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
    
            String actualUUID = repos.getRepositoryUUID(true);
            if (expectedUUID != null && !expectedUUID.equals(actualUUID)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_RELOCATION, "The repository at ''{0}'' has uuid ''{1}'', but the WC has ''{2}''",
                        new Object[] { targetURL, actualUUID, expectedUUID });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            validatedURLs.put(targetURL, actualUUID);
        } finally {
            repos.closeSession();
        }
        return validatedURLs;
    }
    
    private Map relocateEntry(SVNEntry entry, String from, String to, Map validatedURLs) throws SVNException {
        if (entry.getRepositoryRoot() != null) {
            // that is what i do not understand :)
            String repos = entry.getRepositoryRoot();
            if (from.length() > repos.length()) {
                String fromPath = from.substring(repos.length());
                if (!to.endsWith(fromPath)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_RELOCATION, "Relocate can only change the repository part of an URL");
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                from = repos;
                to = to.substring(0, to.length() - fromPath.length());
            }
            if (repos.startsWith(from)) {
                entry.setRepositoryRoot(to + repos.substring(from.length()));
                validatedURLs = validateRelocateTargetURL(entry.getRepositoryRootURL(), entry.getUUID(), validatedURLs, true);
            }
        }
        if (entry.getURL() != null && entry.getURL().startsWith(from)) {
            entry.setURL(to + entry.getURL().substring(from.length()));
            if (entry.getUUID() != null && validatedURLs != null) {
                validatedURLs = validateRelocateTargetURL(entry.getSVNURL(), entry.getUUID(), validatedURLs, false);
            }
        }
        if (entry.getCopyFromURL() != null && entry.getCopyFromURL().startsWith(from)) {
            entry.setCopyFromURL(to + entry.getCopyFromURL().substring(from.length()));
            if (entry.getUUID() != null && validatedURLs != null) {
                validatedURLs = validateRelocateTargetURL(entry.getCopyFromSVNURL(), entry.getUUID(), validatedURLs, false);
            }
        }
        return validatedURLs;
    }
    
    private Map doRelocate(SVNAdminArea adminArea, String name, String from, String to, boolean recursive, Map validatedURLs) throws SVNException {
        SVNEntry entry = adminArea.getEntry(name, true);
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        if (entry.isFile()) {
            relocateEntry(entry, from, to, validatedURLs);
            SVNPropertiesManager.deleteWCProperties(adminArea, name, false);
            adminArea.saveEntries(false);
            return validatedURLs;
        }
        
        validatedURLs = relocateEntry(entry, from, to, validatedURLs);
        SVNWCAccess wcAccess = adminArea.getWCAccess();
        for (Iterator entries = adminArea.entries(true); entries.hasNext();) {
            SVNEntry childEntry = (SVNEntry) entries.next();
            if (adminArea.getThisDirName().equals(childEntry.getName())) {
                continue;
            }
            if (recursive && childEntry.isDirectory() && (childEntry.isScheduledForAddition() || !childEntry.isDeleted()) && 
                    !childEntry.isAbsent() && childEntry.getDepth() != SVNDepth.EXCLUDE) {
                File childDir = adminArea.getFile(childEntry.getName());
                if (wcAccess.isMissing(childDir)) {
                    continue;
                }
                SVNAdminArea childArea = wcAccess.retrieve(childDir);
                validatedURLs = doRelocate(childArea, childArea.getThisDirName(), from, to, recursive, validatedURLs);
            }
            validatedURLs = relocateEntry(childEntry, from, to, validatedURLs);
            SVNPropertiesManager.deleteWCProperties(adminArea, childEntry.getName(), false);
        }
        SVNPropertiesManager.deleteWCProperties(adminArea, "", false);
        adminArea.saveEntries(false);
        return validatedURLs;
    }
    
    private static class ExternalDiff {
        
        public SVNExternal oldExternal;
        public SVNExternal newExternal;
        
        public File owner;
        public SVNURL ownerURL;
        
        public SVNURL rootURL;
        
        public boolean isExport;
        public boolean isUpdateUnchanged;
        
        public boolean compareExternals(SVNURL oldURL, SVNURL newURL) {
            return oldURL.equals(newURL) && 
                oldExternal.getRevision().equals(newExternal.getRevision()) &&
                oldExternal.getPegRevision().equals(newExternal.getPegRevision());
        }
    }
}
