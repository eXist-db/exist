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
import java.util.Iterator;

import org.exist.versioning.svn.internal.wc.SVNCopyDriver;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNEventFactory;
import org.exist.versioning.svn.internal.wc.SVNFileType;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNLog;
import org.exist.versioning.svn.internal.wc.admin.SVNVersionedProperties;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * The <b>SVNMoveClient</b> provides an extra client-side functionality over
 * standard (i.e. compatible with the SVN command line client) move 
 * operations. This class helps to overcome the SVN limitations regarding
 * move operations. Using <b>SVNMoveClient</b> you can easily:
 * <ul>
 * <li>move versioned items to other versioned ones  
 * within the same Working Copy, what even allows to replace items 
 * scheduled for deletion, or those that are missing but are still under
 * version control and have a node kind different from the node kind of the 
 * source (!);  
 * <li>move versioned items belonging to one Working Copy to versioned items
 * that belong to absolutely different Working Copy; 
 * <li>move versioned items to unversioned ones;
 * <li>move unversioned items to versioned ones;
 * <li>move unversioned items to unversioned ones;
 * <li>revert any of the kinds of moving listed above;
 * <li>complete a copy/move operation for a file, that is if you have
 * manually copied/moved a versioned file to an unversioned file in a Working
 * copy, you can run a 'virtual' copy/move on these files to copy/move
 * all the necessary administrative version control information.
 * </ul>
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNMoveClient extends SVNCopyDriver {

    private SVNWCClient myWCClient;
    private SVNCopyClient myCopyClient;
    /**
     * Constructs and initializes an <b>SVNMoveClient</b> object
     * with the specified run-time configuration and authentication 
     * drivers.
     * 
     * <p>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNMoveClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the 
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
     * 
     * <p>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNMoveClient</b> will be using a default authentication
     * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
     * which uses server-side settings and auth storage from the 
     * default SVN's run-time configuration area (or system properties
     * if that area is not found).
     * 
     * @param authManager an authentication and network layers driver
     * @param options     a run-time configuration options driver     
     */
    public SVNMoveClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
        myWCClient = new SVNWCClient(authManager, options);
        myCopyClient = new SVNCopyClient(authManager, options);
    }

    /**
     * Constructs and initializes an <b>SVNMoveClient</b> object
     * with the specified run-time configuration and repository pool object.
     * 
     * <p/>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNMoveClient</b> will be using a default run-time
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
    public SVNMoveClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
        myWCClient = new SVNWCClient(repositoryPool, options);
        myCopyClient = new SVNCopyClient(repositoryPool, options);
    }

	public void setEventHandler(ISVNEventHandler dispatcher) {
		super.setEventHandler(dispatcher);
		myWCClient.setEventHandler(dispatcher);
		myCopyClient.setEventHandler(dispatcher);
	}

	public void setDebugLog(ISVNDebugLog log) {
		super.setDebugLog(log);
		myWCClient.setDebugLog(log);
		myCopyClient.setDebugLog(log);
	}

	public void setOptions(ISVNOptions options) {
		super.setOptions(options);
		if (myWCClient != null) {
			myWCClient.setOptions(options);
		}
		if (myCopyClient != null) {
			myCopyClient.setOptions(options);
		}
    }
    
    /**
     * Moves a source item to a destination one. 
     * 
     * <p>
     * <code>dst</code> should not exist. Furher it's considered to be versioned if
     * its parent directory is under version control, otherwise <code>dst</code>
     * is considered to be unversioned.
     * 
     * <p>
     * If both <code>src</code> and <code>dst</code> are unversioned, then simply 
     * moves <code>src</code> to <code>dst</code> in the filesystem.
     *
     * <p>
     * If <code>src</code> is versioned but <code>dst</code> is not, then 
     * exports <code>src</code> to <code>dst</code> in the filesystem and
     * removes <code>src</code> from version control.
     * 
     * <p>
     * If <code>dst</code> is versioned but <code>src</code> is not, then 
     * moves <code>src</code> to <code>dst</code> (even if <code>dst</code>
     * is scheduled for deletion).
     * 
     * <p>
     * If both <code>src</code> and <code>dst</code> are versioned and located
     * within the same Working Copy, then moves <code>src</code> to 
     * <code>dst</code> (even if <code>dst</code> is scheduled for deletion),
     * or tries to replace <code>dst</code> with <code>src</code> if the former
     * is missing and has a node kind different from the node kind of the source.
     * If <code>src</code> is scheduled for addition with history, 
     * <code>dst</code> will be set the same ancestor URL and revision from which
     * the source was copied. If <code>src</code> and <code>dst</code> are located in 
     * different Working Copies, then this method copies <code>src</code> to 
     * <code>dst</code>, tries to put the latter under version control and 
     * finally removes <code>src</code>.
     *  
     * @param  src            a source path
     * @param  dst            a destination path
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li><code>dst</code> already exists
     *                        <li><code>src</code> does not exist
     *                        </ul>
     */ 
    public void doMove(File src, File dst) throws SVNException {
        if (dst.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "File ''{0}'' already exists", dst);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (!src.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Path ''{0}'' does not exist", src);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        // src considered as unversioned when it is not versioned
        boolean srcIsVersioned = isVersionedFile(src);
        // dst is considered as unversioned when its parent is not versioned.
        boolean dstParentIsVersioned = isVersionedFile(dst.getParentFile());

        if (!srcIsVersioned && !dstParentIsVersioned) {
            // world:world
            SVNFileUtil.rename(src, dst);
        } else if (!dstParentIsVersioned) {
            // wc:world
            // 1. export to world
            SVNFileUtil.copy(src, dst, false, false);

            // 2. delete in wc.
            myWCClient.doDelete(src, true, false);
        } else if (!srcIsVersioned) {
            // world:wc (add, if dst is 'deleted' it will be replaced)
            SVNFileUtil.rename(src, dst);
            // do we have to add it if it was unversioned?
            //myWCClient.doAdd(dst, false, false, false, true, false);
        } else {
            // wc:wc.

            SVNWCAccess wcAccess = createWCAccess();
            File srcParent = src.getParentFile();
            File dstParent = dst.getParentFile();
            SVNAdminArea srcParentArea = null;
            SVNAdminArea dstParentArea = null;
            try {
                if (srcParent.equals(dstParent)) {
                    wcAccess.closeAdminArea(srcParent);
                    srcParentArea = dstParentArea = wcAccess.open(srcParent, true, 0);
                } else {
                    srcParentArea = wcAccess.open(srcParent, false, 0);
                    dstParentArea = wcAccess.open(dstParent, true, 0);
                }

                SVNEntry srcEntry = srcParentArea.getVersionedEntry(src.getName(), false);
                SVNEntry dstEntry = dstParentArea.getEntry(dst.getName(), false);

                File srcWCRoot = SVNWCUtil.getWorkingCopyRoot(src, true);
                File dstWCRoot = SVNWCUtil.getWorkingCopyRoot(dst, true);
                boolean sameWC = srcWCRoot != null && srcWCRoot.equals(dstWCRoot);
                
                if (sameWC && dstEntry != null
                        && (dstEntry.isScheduledForDeletion() || dstEntry.getKind() != srcEntry.getKind())) {
                    wcAccess.close();
                    if (srcEntry.getKind() == dstEntry.getKind() && srcEntry.getSchedule() == null && srcEntry.isFile()) {
                        // make normal move to keep history (R+).
                        SVNCopySource source = new SVNCopySource(SVNRevision.UNDEFINED, SVNRevision.WORKING, src);
                        myCopyClient.doCopy(new SVNCopySource[]{source}, dst, true, false, true);
                        return;
                    }
                    // attempt replace.
                    SVNFileUtil.copy(src, dst, false, false);
                    try {
                        myWCClient.doAdd(dst, false, false, false, SVNDepth.INFINITY, false, false);
                    } catch (SVNException e) {
                        // will be thrown on obstruction.
                    }
                    myWCClient.doDelete(src, true, false);
                    return;
                } else if (!sameWC) {
                    SVNEntry dstTmpEntry = dstEntry != null ? dstEntry : dstParentArea.getVersionedEntry(dstParentArea.getThisDirName(), false); 
                    if (srcEntry.getRepositoryRoot() != null && dstTmpEntry.getRepositoryRoot() != null &&
                            srcEntry.getRepositoryRoot().equals(dstTmpEntry.getRepositoryRoot())) {
                        //this is the case when different WCs occur to be from the same repository,
                        //use SVNCopyClient to move between them
                        wcAccess.close();
                        SVNCopySource source = new SVNCopySource(SVNRevision.UNDEFINED, SVNRevision.WORKING, src);
                        myCopyClient.doCopy(new SVNCopySource[] { source }, dst, true, false, true);
                        return;
                    }
                }

                if (dstEntry != null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "There is already a versioned item ''{0}''", dst);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }

                // 2. do manual copy of the file or directory
                SVNFileUtil.copy(src, dst, false, sameWC);

                // 3. update dst dir and dst entry in parent.
                if (!sameWC) {
                    // just add dst (at least try to add, files already there).
                    wcAccess.close();
                    try {
                        myWCClient.doAdd(dst, false, false, false, SVNDepth.INFINITY, false, false);
                    } catch (SVNException e) {
                        // obstruction
                    }
                } else if (srcEntry.isFile()) {
                    
                    if (dstEntry == null) {
                        dstEntry = dstParentArea.addEntry(dst.getName());
                    }

                    String srcURL = srcEntry.getURL();
                    String srcCFURL = srcEntry.getCopyFromURL();
                    long srcRevision = srcEntry.getRevision();
                    long srcCFRevision = srcEntry.getCopyFromRevision();
                    // copy props!
                    SVNVersionedProperties srcProps = srcParentArea.getProperties(src.getName());
                    SVNVersionedProperties dstProps = dstParentArea.getProperties(dst.getName());
                    srcProps.copyTo(dstProps);
                    File srcBaseFile = srcParentArea.getBaseFile(src.getName(), false);
                    File dstBaseFile = dstParentArea.getBaseFile(dst.getName(), false);
                    if (srcBaseFile.isFile()) {
                        SVNFileUtil.copy(srcBaseFile, dstBaseFile, false, false);
                    }
                    
                    if (srcEntry.isScheduledForAddition() && srcEntry.isCopied()) {
                        dstEntry.scheduleForAddition();
                        dstEntry.setCopyFromRevision(srcCFRevision);
                        dstEntry.setCopyFromURL(srcCFURL);
                        dstEntry.setKind(SVNNodeKind.FILE);
                        dstEntry.setRevision(srcRevision);
                        dstEntry.setCopied(true);
                    } else if (!srcEntry.isCopied()
                            && !srcEntry.isScheduledForAddition()) {
                        dstEntry.setCopied(true);
                        dstEntry.scheduleForAddition();
                        dstEntry.setKind(SVNNodeKind.FILE);
                        dstEntry.setCopyFromRevision(srcRevision);
                        dstEntry.setCopyFromURL(srcURL);
                    } else {
                        dstEntry.scheduleForAddition();
                        dstEntry.setKind(SVNNodeKind.FILE);
                        if (!dstEntry.isScheduledForReplacement()) {
                            dstEntry.setRevision(0);
                        }
                    }
                    
                    SVNLog log = dstParentArea.getLog(); 
                    dstParentArea.saveEntries(false);
                    dstParentArea.saveVersionedProperties(log, true);
                    log.save();
                    dstParentArea.runLogs();
                } else if (srcEntry.isDirectory()) {
                    SVNAdminArea srcArea = wcAccess.open(src, false, 0);
                    srcEntry = srcArea.getEntry(srcArea.getThisDirName(), false);
                    if (dstEntry == null) {
                        dstEntry = dstParentArea.addEntry(dst.getName());
                    }
                    SVNAdminArea dstArea = wcAccess.open(dst, true, SVNWCAccess.INFINITE_DEPTH);
                    
                    SVNVersionedProperties srcProps = srcArea.getProperties(srcArea.getThisDirName());
                    SVNVersionedProperties dstProps = dstArea.getProperties(dstArea.getThisDirName());
                    
                    SVNEntry dstParentEntry = dstParentArea.getEntry(dstParentArea.getThisDirName(), false); 
                    String srcURL = srcEntry.getURL();
                    String srcCFURL = srcEntry.getCopyFromURL();
                    String dstURL = dstParentEntry.getURL();
                    String repositoryRootURL = dstParentEntry.getRepositoryRoot();
                    long srcRevision = srcEntry.getRevision();
                    long srcCFRevision = srcEntry.getCopyFromRevision();

                    dstURL = SVNPathUtil.append(dstURL, SVNEncodingUtil.uriEncode(dst.getName()));
                    if (srcEntry.isScheduledForAddition() && srcEntry.isCopied()) {
                        srcProps.copyTo(dstProps);
                        dstEntry.scheduleForAddition();
                        dstEntry.setKind(SVNNodeKind.DIR);
                        dstEntry.setCopied(true);
                        dstEntry.setCopyFromRevision(srcCFRevision);
                        dstEntry.setCopyFromURL(srcCFURL);

                        SVNEntry dstThisEntry = dstArea.getEntry(dstArea.getThisDirName(), false);
                        dstThisEntry.scheduleForAddition();
                        dstThisEntry.setKind(SVNNodeKind.DIR);
                        dstThisEntry.setCopyFromRevision(srcCFRevision);
                        dstThisEntry.setCopyFromURL(srcCFURL);
                        dstThisEntry.setRevision(srcRevision);
                        dstThisEntry.setCopied(true);
                        
                        SVNLog log = dstArea.getLog();
                        dstArea.saveVersionedProperties(log, true);
                        dstParentArea.saveEntries(false);
                        log.save();
                        dstArea.runLogs();
                        
                        // update URL in children.
                        dstArea.updateURL(dstURL, true);
                        dstParentArea.saveEntries(true);
                    } else if (!srcEntry.isCopied() && !srcEntry.isScheduledForAddition()) {
                        // versioned (deleted, replaced, or normal).
                        srcProps.copyTo(dstProps);
                        dstEntry.scheduleForAddition();
                        dstEntry.setKind(SVNNodeKind.DIR);
                        dstEntry.setCopied(true);
                        dstEntry.setCopyFromRevision(srcRevision);
                        dstEntry.setCopyFromURL(srcURL);

                        // update URL, CF-URL and CF-REV in children.
                        SVNEntry dstThisEntry = dstArea.getEntry(dstArea.getThisDirName(), false);
                        dstThisEntry.scheduleForAddition();
                        dstThisEntry.setKind(SVNNodeKind.DIR);
                        dstThisEntry.setCopied(true);
                        dstThisEntry.scheduleForAddition();
                        dstThisEntry.setKind(SVNNodeKind.DIR);
                        dstThisEntry.setCopyFromRevision(srcRevision);
                        dstThisEntry.setCopyFromURL(srcURL);
                        dstThisEntry.setURL(dstURL);
                        dstThisEntry.setRepositoryRoot(repositoryRootURL);

                        SVNLog log = dstArea.getLog();
                        dstArea.saveVersionedProperties(log, true);
                        dstArea.saveEntries(false);
                        log.save();
                        dstArea.runLogs();

                        updateCopiedDirectory(dstArea, dstArea.getThisDirName(), dstURL, repositoryRootURL, null, -1);
                        dstArea.saveEntries(true);
                        dstParentArea.saveEntries(true);

                    } else {
                        // unversioned entry (copied or added)
                        dstParentArea.deleteEntry(dst.getName());
                        dstParentArea.saveEntries(true);
                        SVNFileUtil.deleteAll(dst, this);
                        SVNFileUtil.copy(src, dst, false, false);
                        wcAccess.close();
                        myWCClient.doAdd(dst, false, false, false, SVNDepth.INFINITY, false, false);
                    }
                }
                // now delete src (if it is not the same as dst :))
                try {
                    wcAccess.close();
                    myWCClient.doDelete(src, true, false);
                } catch (SVNException e) {
                    //
                }
            } finally {
                wcAccess.close();
            }
        }
    }

    /**
     * Reverts a previous move operation back. Provided in pair with {@link #doMove(File, File) doMove()} 
     * and used to roll back move operations. In this case <code>src</code> is
     * considered to be the target of the previsous move operation, and <code>dst</code>
     * is regarded to be the source of that same operation which have been moved
     * to <code>src</code> and now is to be restored. 
     * 
     * <p>
     * <code>dst</code> could exist in that case if it has been a WC directory
     * that was scheduled for deletion during the previous move operation. Furher 
     * <code>dst</code> is considered to be versioned if its parent directory is 
     * under version control, otherwise <code>dst</code> is considered to be unversioned.
     * 
     * <p>
     * If both <code>src</code> and <code>dst</code> are unversioned, then simply 
     * moves <code>src</code> back to <code>dst</code> in the filesystem.
     *
     * <p>
     * If <code>src</code> is versioned but <code>dst</code> is not, then 
     * unmoves <code>src</code> to <code>dst</code> in the filesystem and
     * removes <code>src</code> from version control.
     * 
     * <p>
     * If <code>dst</code> is versioned but <code>src</code> is not, then 
     * first tries to make a revert on <code>dst</code> - if it has not been committed
     * yet, it will be simply reverted. However in the case <code>dst</code> has been already removed 
     * from the repository, <code>src</code> will be copied back to <code>dst</code>
     * and scheduled for addition. Then <code>src</code> is removed from the filesystem.
     * 
     * <p>
     * If both <code>src</code> and <code>dst</code> are versioned then the 
     * following situations are possible:
     * <ul>
     * <li>If <code>dst</code> is still scheduled for deletion, then it is
     * reverted back and <code>src</code> is scheduled for deletion.
     * <li>in the case if <code>dst</code> exists but is not scheduled for 
     * deletion, <code>src</code> is cleanly exported to <code>dst</code> and
     * removed from version control.
     * <li>if <code>dst</code> and <code>src</code> are from different repositories
     * (appear to be in different Working Copies), then <code>src</code> is copied
     * to <code>dst</code> (with scheduling <code>dst</code> for addition, but not
     * with history since copying is made in the filesystem only) and removed from
     * version control.
     * <li>if both <code>dst</code> and <code>src</code> are in the same 
     * repository (appear to be located in the same Working Copy) and: 
     *    <ul style="list-style-type: lower-alpha">
     *    <li>if <code>src</code> is scheduled for addition with history, then
     *    copies <code>src</code> to <code>dst</code> specifying the source
     *    ancestor's URL and revision (i.e. the ancestor of the source is the
     *    ancestor of the destination);
     *    <li>if <code>src</code> is already under version control, then
     *    copies <code>src</code> to <code>dst</code> specifying the source
     *    URL and revision as the ancestor (i.e. <code>src</code> itself is the
     *    ancestor of <code>dst</code>);
     *    <li>if <code>src</code> is just scheduled for addition (without history),
     *    then simply copies <code>src</code> to <code>dst</code> (only in the filesystem,
     *    without history) and schedules <code>dst</code> for addition;  
     *    </ul>
     * then <code>src</code> is removed from version control.
     * </ul>
     * 
     * @param  src            a source path
     * @param  dst            a destination path
     * @throws SVNException   if <code>src</code> does not exist
     * 
     */
    // move that considered as move undo.
    public void undoMove(File src, File dst) throws SVNException {
        // dst could exists, if it is deleted directory.
        if (!src.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Path ''{0}'' does not exist", src);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        // src considered as unversioned when it is not versioned
        boolean srcIsVersioned = isVersionedFile(src);
        // dst is considered as unversioned when its parent is not versioned.
        boolean dstParentIsVersioned = isVersionedFile(dst.getParentFile());

        if (!srcIsVersioned && !dstParentIsVersioned) {
            // world:world
            SVNFileUtil.rename(src, dst);
        } else if (!dstParentIsVersioned) {
            // wc:world
            // 1. export to world
            SVNFileUtil.copy(src, dst, false, false);

            // 2. delete in wc.
            myWCClient.doDelete(src, true, false);
        } else if (!srcIsVersioned) {
            // world:wc (add, if dst is 'deleted' it will be replaced)
            SVNFileUtil.rename(src, dst);
            // dst should probably be deleted, in this case - revert it
            SVNWCAccess dstAccess = createWCAccess();
            boolean revert = false;
            try {
                dstAccess.probeOpen(dst, false, 0);
                SVNEntry dstEntry = dstAccess.getEntry(dst, false);
                revert = dstEntry != null && dstEntry.isScheduledForDeletion();
            } catch (SVNException e) {
            } finally {
                dstAccess.close();
            }
            if (revert) {
                myWCClient.doRevert(new File[] { dst }, SVNDepth.INFINITY, null);
            } else {
                // should we do this? there is no old source, may be rename is enough.
//                myWCClient.doAdd(dst, false, false, false, true, false);
            }
        } else {
            // wc:wc.
            SVNWCAccess wcAccess = createWCAccess();
            File srcParent = src.getParentFile();
            File dstParent = dst.getParentFile();
            SVNAdminArea srcParentArea = null;
            SVNAdminArea dstParentArea = null;
            try {
                if (srcParent.equals(dstParent)) {
                    wcAccess.closeAdminArea(srcParent);
                    srcParentArea = dstParentArea = wcAccess.open(srcParent, true, 0);
                } else {
                    srcParentArea = wcAccess.open(srcParent, false, 0);
                    dstParentArea = wcAccess.open(dstParent, true, 0);
                }

                SVNEntry srcEntry = srcParentArea.getEntry(src.getName(), true);
                SVNEntry dstEntry = dstParentArea.getEntry(dst.getName(), true);
                if (dstEntry != null && dstEntry.isScheduledForDeletion()) {
                    wcAccess.close();
                    // clear undo.
                    myWCClient.doRevert(new File[] { dst }, SVNDepth.INFINITY, null);
                    myWCClient.doDelete(src, true, false);
                    return;
                }
                SVNEntry dstParentEntry = wcAccess.getEntry(dstParent, false);

                File srcWCRoot = SVNWCUtil.getWorkingCopyRoot(src, true);
                File dstWCRoot = SVNWCUtil.getWorkingCopyRoot(dst, true);
                boolean sameWC = srcWCRoot != null && srcWCRoot.equals(dstWCRoot);

                SVNFileUtil.copy(src, dst, false, sameWC);

                // obstruction assertion.
                if (dstEntry != null && dstEntry.getKind() != srcEntry.getKind()) {
                    // ops have no sence->target is obstructed, just export src to
                    // dst and delete src.
                    wcAccess.close();
                    myWCClient.doDelete(src, true, false);
                    return;
                }
                if (!sameWC) {
                    // just add dst (at least try to add, files already there).
                    wcAccess.close();
                    try {
                        myWCClient.doAdd(dst, false, false, false, SVNDepth.INFINITY, false, false);
                    } catch (SVNException e) {
                        // obstruction
                    }
                } else if (srcEntry.isFile()) {
                    if (dstEntry == null) {
                        dstEntry = dstParentArea.addEntry(dst.getName());
                    }

                    String srcURL = srcEntry.getURL();
                    String srcCFURL = srcEntry.getCopyFromURL();
                    long srcRevision = srcEntry.getRevision();
                    long srcCFRevision = srcEntry.getCopyFromRevision();

                    if (srcEntry.isScheduledForAddition() && srcEntry.isCopied()) {
                        dstEntry.scheduleForAddition();
                        dstEntry.setCopyFromRevision(srcCFRevision);
                        dstEntry.setCopyFromURL(srcCFURL);
                        dstEntry.setKind(SVNNodeKind.FILE);
                        dstEntry.setRevision(srcRevision);
                        dstEntry.setCopied(true);
                    } else if (!srcEntry.isCopied() && !srcEntry.isScheduledForAddition()) {
                        dstEntry.setCopied(true);
                        dstEntry.scheduleForAddition();
                        dstEntry.setKind(SVNNodeKind.FILE);
                        dstEntry.setCopyFromRevision(srcRevision);
                        dstEntry.setCopyFromURL(srcURL);
                    } else {
                        dstEntry.scheduleForAddition();
                        dstEntry.setKind(SVNNodeKind.FILE);
                        if (!dstEntry.isScheduledForReplacement()) {
                            dstEntry.setRevision(0);
                        }
                    }
                    dstParentArea.saveEntries(false);
                } else if (srcEntry.isDirectory()) {
                    SVNAdminArea srcArea = wcAccess.open(src, false, 0);
                    srcEntry = srcArea.getEntry(srcArea.getThisDirName(), false);
                    if (dstEntry == null) {
                        dstEntry = dstParentArea.addEntry(dst.getName());
                    }

                    String srcURL = srcEntry.getURL();
                    String dstURL = dstParentEntry.getURL();
                    long srcRevision = srcEntry.getRevision();
                    String repositoryRootURL = srcEntry.getRepositoryRoot();

                    dstURL = SVNPathUtil.append(dstURL, SVNEncodingUtil.uriEncode(dst.getName()));
                    
                    SVNAdminArea dstArea = wcAccess.open(dst, true, SVNWCAccess.INFINITE_DEPTH);
                    
                    if (srcEntry.isScheduledForAddition() && srcEntry.isCopied()) {
                        dstEntry.scheduleForAddition();
                        dstEntry.setKind(SVNNodeKind.DIR);
                        dstParentArea.saveEntries(true);
                        // update URL in children.
                        dstArea.updateURL(dstURL, true);
                        dstArea.saveEntries(true);
                    } else if (!srcEntry.isCopied()
                            && !srcEntry.isScheduledForAddition()) {
                        dstEntry.setCopied(true);
                        dstEntry.scheduleForAddition();
                        dstEntry.setKind(SVNNodeKind.DIR);
                        dstEntry.setCopyFromRevision(srcRevision);
                        dstEntry.setCopyFromURL(srcURL);

                        dstParentArea.saveEntries(true);

                        SVNEntry dstThisEntry = dstArea.getEntry(dstArea.getThisDirName(), false);
                        dstThisEntry.setCopied(true);
                        dstThisEntry.scheduleForAddition();
                        dstThisEntry.setKind(SVNNodeKind.DIR);
                        dstThisEntry.setCopyFromRevision(srcRevision);
                        dstThisEntry.setURL(dstURL);
                        dstThisEntry.setCopyFromURL(srcURL);
                        dstThisEntry.setRepositoryRoot(repositoryRootURL);
                        
                        updateCopiedDirectory(dstArea, dstArea.getThisDirName(), dstURL, repositoryRootURL, null, -1);
                        dstArea.saveEntries(true);
                    } else {
                        // replay
                        dstParentArea.deleteEntry(dst.getName());
                        dstParentArea.saveEntries(true);
                        wcAccess.close();
                        SVNFileUtil.deleteAll(dst, this);
                        SVNFileUtil.copy(src, dst, false, false);
                        myWCClient.doAdd(dst, false, false, false, SVNDepth.INFINITY, false, false);
                    }
                }
                // now delete src.
                try {
                    wcAccess.close();
                    myWCClient.doDelete(src, true, false);
                } catch (SVNException e) {
                    //
                }
            } finally {
                wcAccess.close();
            }
        }
    }
    
    /**
     * Copies/moves administrative version control information of a source file 
     * to administrative information of a destination file.
     * For example, if you have manually copied/moved a source file to a target one 
     * (manually means just in the filesystem, not using version control operations) and then
     * would like to turn this copying/moving into a complete version control copy
     * or move operation, use this method that will finish all the work for you - it
     * will copy/move all the necessary administrative information (kept in the source
     * <i>.svn</i> directory) to the target <i>.svn</i> directory. 
     * 
     * <p>
     * In that case when you have your files copied/moved in the filesystem, you
     * can not perform standard (version control) copying/moving - since the target already exists and
     * the source may be already deleted. Use this method to overcome that restriction.  
     * 
     * @param  src           a source file path (was copied/moved to <code>dst</code>)
     * @param  dst           a destination file path
     * @param  move          if <span class="javakeyword">true</span> then
     *                       completes moving <code>src</code> to <code>dst</code>,
     *                       otherwise completes copying <code>src</code> to <code>dst</code>
     * @throws SVNException  if one of the following is true:
     *                       <ul>
     *                       <li><code>move = </code><span class="javakeyword">true</span> and <code>src</code>
     *                       still exists
     *                       <li><code>dst</code> does not exist
     *                       <li><code>dst</code> is a directory 
     *                       <li><code>src</code> is a directory
     *                       <li><code>src</code> is not under version control
     *                       <li><code>dst</code> is already under version control
     *                       <li>if <code>src</code> is copied but not scheduled for
     *                       addition, and SVNKit is not able to locate the copied
     *                       directory root for <code>src</code>
     *                       </ul>
     */
    public void doVirtualCopy(File src, File dst, boolean move) throws SVNException {
        SVNFileType srcType  = SVNFileType.getType(src);
        SVNFileType dstType  = SVNFileType.getType(dst);
        
        String opName = move ? "move" : "copy";
        if (move && srcType != SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "Cannot perform 'virtual' {0}: ''{1}'' still exists", new Object[] {opName, src});
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (dstType == SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Cannot perform 'virtual' {0}: ''{1}'' does not exist", new Object[] {opName, dst});
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (dstType == SVNFileType.DIRECTORY) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot perform 'virtual' {0}: ''{1}'' is a directory", new Object[] {opName, dst});
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (!move && srcType == SVNFileType.DIRECTORY) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot perform 'virtual' {0}: ''{1}'' is a directory", new Object[] {opName, src});
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        SVNURL srcRepoRoot = null;
        SVNURL dstRepoRoot = null;
        boolean versionedDst = false;

        SVNWCAccess dstAccess = createWCAccess();
        try {
            dstAccess.probeOpen(dst, false, 0);
            SVNEntry dstEntry = dstAccess.getEntry(dst, false);
            if (dstEntry != null) {
                if (!dstEntry.isScheduledForAddition() && !dstEntry.isScheduledForReplacement()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_ATTRIBUTE_INVALID, "Cannot perform 'virtual' {0}: ''{1}'' is scheduled neither for addition nor for replacement", new Object[]{opName, dst});
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                versionedDst = true;
                dstRepoRoot = dstEntry.getRepositoryRootURL();
            }
        } finally {
            dstAccess.close();
        }
        
        SVNWCAccess srcAccess = createWCAccess();
        String cfURL = null;
        boolean added = false;
        long cfRevision = -1;
        try {
            srcAccess.probeOpen(src, false, 0);
            SVNEntry srcEntry = srcAccess.getEntry(src, false);
            if (srcEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", src);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            srcRepoRoot = srcEntry.getRepositoryRootURL();

            if (srcEntry.isCopied() && !srcEntry.isScheduledForAddition()) {
                cfURL = getCopyFromURL(src.getParentFile(), SVNEncodingUtil.uriEncode(src.getName()));
                cfRevision = getCopyFromRevision(src.getParentFile());
                if (cfURL == null || cfRevision < 0) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Cannot locate copied directory root for ''{0}''", src);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                added = false;
            } else {
                cfURL = srcEntry.isCopied() ? srcEntry.getCopyFromURL() : srcEntry.getURL();
                cfRevision = srcEntry.isCopied() ? srcEntry.getCopyFromRevision() : srcEntry.getRevision();
                added = srcEntry.isScheduledForAddition() && !srcEntry.isCopied();
            }
        } finally {
            srcAccess.close();
        }
        if (added && !versionedDst) {
            if (move) {
                myWCClient.doDelete(src, true, false);
            }
            myWCClient.doAdd(dst, true, false, false, SVNDepth.EMPTY, false, false);            
            return;
        }

        dstAccess = createWCAccess();
        srcAccess = createWCAccess();
        try {
            SVNAdminArea dstArea = dstAccess.probeOpen(dst, true, 0);
            SVNEntry dstEntry = dstAccess.getEntry(dst, false);
            if (dstEntry != null && !dstEntry.isScheduledForAddition() && !dstEntry.isScheduledForReplacement()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_ATTRIBUTE_INVALID, "Cannot perform 'virtual' {0}: ''{1}'' is scheduled neither for addition nor for replacement", new Object[]{opName, dst});
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (srcRepoRoot != null && dstRepoRoot != null && !dstRepoRoot.equals(srcRepoRoot)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_ATTRIBUTE_INVALID, "Cannot perform 'virtual' {0}: paths belong to different repositories", opName);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            
            SVNAdminArea srcArea = srcAccess.probeOpen(src, false, 0);
            SVNVersionedProperties srcProps = srcArea.getProperties(src.getName());
            SVNVersionedProperties srcBaseProps = srcArea.getBaseProperties(src.getName());
            SVNVersionedProperties dstProps = dstArea.getProperties(dst.getName());
            SVNVersionedProperties dstBaseProps  = dstArea.getBaseProperties(dst.getName());
            dstProps.removeAll();
            dstBaseProps.removeAll();
            srcProps.copyTo(dstProps);
            srcBaseProps.copyTo(dstBaseProps);
            
            dstEntry = dstArea.addEntry(dst.getName());
            dstEntry.setCopyFromURL(cfURL);
            dstEntry.setCopyFromRevision(cfRevision);
            dstEntry.setCopied(true);
            dstEntry.setKind(SVNNodeKind.FILE);

            File baseSrc = srcArea.getBaseFile(src.getName(), false);
            File baseDst = dstArea.getBaseFile(dst.getName(), false);
            SVNFileUtil.copyFile(baseSrc, baseDst, false);

            if (dstEntry.isScheduledForDeletion()) {
                dstEntry.unschedule();
                dstEntry.scheduleForReplacement();                
            } else if (!dstEntry.isScheduledForReplacement()) {
                dstEntry.unschedule();
                dstEntry.scheduleForAddition();
            }

            dstArea.saveEntries(false);
            SVNLog log = dstArea.getLog();
            dstArea.saveVersionedProperties(log, true);
            log.save();
            dstArea.runLogs();

	        SVNEvent event = SVNEventFactory.createSVNEvent(dst, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNEventAction.ADD, null, null, null);
	        dispatchEvent(event);
        } finally {
            srcAccess.close();
            dstAccess.close();
        }
        if (move) {
            myWCClient.doDelete(src, true, false);
        }
    }

    private void updateCopiedDirectory(SVNAdminArea dir, String name, String newURL, String reposRootURL, String copyFromURL, long copyFromRevision) throws SVNException {
        SVNWCAccess wcAccess = dir.getWCAccess();
        SVNEntry entry = dir.getEntry(name, true);
        if (entry != null) {
            entry.setCopied(true);
            if (newURL != null) {
                entry.setURL(newURL);
            }
            entry.setRepositoryRoot(reposRootURL);
            if (entry.isFile()) {
                if (dir.getWCProperties(name) != null) {
                    dir.getWCProperties(name).removeAll();
                    dir.saveWCProperties(false);
                }
                if (copyFromURL != null) {
                    entry.setCopyFromURL(copyFromURL);
                    entry.setCopyFromRevision(copyFromRevision);
                }
            }
            boolean deleted = false;
            if (entry.isDeleted() && newURL != null) {
                // convert to scheduled for deletion.
                deleted = true;
                entry.setDeleted(false);
                entry.scheduleForDeletion();
                if (entry.isDirectory()) {
                    entry.setKind(SVNNodeKind.FILE);
                }
            }
            if (entry.getLockToken() != null && newURL != null) {
                entry.setLockToken(null);
                entry.setLockOwner(null);
                entry.setLockComment(null);
                entry.setLockCreationDate(null);
            }
            if (!dir.getThisDirName().equals(name) && entry.isDirectory() && !deleted) {
                SVNAdminArea childDir = wcAccess.retrieve(dir.getFile(name));
                if (childDir != null) {
                    String childCopyFromURL = copyFromURL == null ? null : SVNPathUtil.append(copyFromURL, SVNEncodingUtil.uriEncode(entry.getName()));
                    updateCopiedDirectory(childDir, childDir.getThisDirName(), newURL, reposRootURL, childCopyFromURL, copyFromRevision);
                }
            } else if (dir.getThisDirName().equals(name)) {
                dir.getWCProperties(dir.getThisDirName()).removeAll();
                dir.saveWCProperties(false);
                if (copyFromURL != null) {
                    entry.setCopyFromURL(copyFromURL);
                    entry.setCopyFromRevision(copyFromRevision);
                }
                for (Iterator ents = dir.entries(true); ents.hasNext();) {
                    SVNEntry childEntry = (SVNEntry) ents.next();
                    if (dir.getThisDirName().equals(childEntry.getName())) {
                        continue;
                    }
                    String childCopyFromURL = copyFromURL == null ? null : SVNPathUtil.append(copyFromURL, SVNEncodingUtil.uriEncode(childEntry.getName()));
                    String newChildURL = newURL == null ? null : SVNPathUtil.append(newURL, SVNEncodingUtil.uriEncode(childEntry.getName()));
                    updateCopiedDirectory(dir, childEntry.getName(), newChildURL, reposRootURL, childCopyFromURL, copyFromRevision);
                }
                dir.saveEntries(false);
            }
        }
    }
    
    private String getCopyFromURL(File path, String urlTail) throws SVNException {
        if (path == null) {
            return null;
        }
        SVNWCAccess wcAccess = createWCAccess();
        try {
            wcAccess.probeOpen(path, false, 0);
        } catch (SVNException e) {
            wcAccess.close();
            return null;
        } 
        // urlTail is either name of an entry
        try {
            SVNEntry entry = wcAccess.getEntry(path, false);
            if (entry == null) {
                return null;
            }
            String cfURL = entry.getCopyFromURL();
            if (cfURL != null) {
                return SVNPathUtil.append(cfURL, urlTail);
            }
            urlTail = SVNPathUtil.append(SVNEncodingUtil.uriEncode(path.getName()), urlTail);
            path = path.getParentFile();
            return getCopyFromURL(path, urlTail);
        } finally {
            wcAccess.close();
        }
    }

    private long getCopyFromRevision(File path) throws SVNException {
        if (path == null) {
            return -1;
        }
        SVNWCAccess wcAccess = createWCAccess();
        try {
            wcAccess.probeOpen(path, false, 0);
        } catch (SVNException e) {
            wcAccess.close();
            return -1;
        } 
        try {
            SVNEntry entry = wcAccess.getEntry(path, false);
            if (entry == null) {
                return -1;
            }
            long rev = entry.getCopyFromRevision();
            if (rev >= 0) {
                return rev;
            }
            path = path.getParentFile();
            return getCopyFromRevision(path);
        } finally {
            wcAccess.close();
        }
    }

    private static boolean isVersionedFile(File file) {
        SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
        try {
            SVNAdminArea area = wcAccess.probeOpen(file, false, 0);
            if (area.getEntry(area.getThisDirName(), false) == null) {
                return false;
            }
            SVNFileType type = SVNFileType.getType(file);
            if (type.isFile() || type == SVNFileType.NONE) {
                // file or missing file
                return area.getEntry(file.getName(), false) != null;
            } else if (type != SVNFileType.NONE && !area.getRoot().equals(file)) {
                // directory, but not anchor. always considered unversioned.
                return false;
            } 
            return true;
        } catch (SVNException e) {
            return false;
        } finally {
            try {
                wcAccess.close();
            } catch (SVNException svne) {
                //
            }
        }
    }

}
