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
package org.exist.versioning.svn.wc;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNEventFactory;
import org.exist.versioning.svn.internal.wc.admin.ISVNEntryHandler;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNChangelistHandler;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * The <b>SVNChangelistClient</b> provides API for managing changelists.
 * 
 * <p>
 * Here's a list of the <b>SVNChangelistClient</b>'s methods 
 * matched against corresponing commands of the SVN command line 
 * client:
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="40%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>SVNKit</b></td>
 * <td><b>Subversion</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doAddToChangelist()</td><td>'svn changelist CLNAME TARGET'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doRemoveFromChangelist()</td><td>'svn changelist --remove TARGET'</td>
 * </tr>
 * </table>
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNChangelistClient extends SVNBasicClient {
    /**
     * Constructs and initializes an <b>SVNChangelistClient</b> object
     * with the specified run-time configuration and authentication
     * drivers.
     * 
     * <p/>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNChangelistClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
     * 
     * <p/>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNChangelistClient</b> will be using a default authentication
     * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
     * which uses server-side settings and auth storage from the
     * default SVN's run-time configuration area (or system properties
     * if that area is not found).
     *
     * @param authManager an authentication and network layers driver
     * @param options     a run-time configuration options driver
     */
    public SVNChangelistClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    /**
     * Constructs and initializes an <b>SVNChangelistClient</b> object
     * with the specified run-time configuration and repository pool object.
     * 
     * <p/>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNChangelistClient</b> will be using a default run-time
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
    public SVNChangelistClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }

    /**
     * @param path 
     * @param changeLists 
     * @param depth 
     * @param handler 
     * @throws SVNException 
     * @deprecated            use {@link #doGetChangeLists(File, Collection, SVNDepth, ISVNChangelistHandler)} 
     *                        instead
     */
    public void getChangeLists(File path, final Collection changeLists, SVNDepth depth, 
            final ISVNChangelistHandler handler) throws SVNException {
        doGetChangeLists(path, changeLists, depth, handler);
    }

    /**
     * @param changeLists 
     * @param targets 
     * @param depth 
     * @param handler 
     * @throws SVNException
     * @deprecated           use {@link #doGetChangeListPaths(Collection, Collection, SVNDepth, ISVNChangelistHandler)}
     *                       instead 
     */
    public void getChangeListPaths(Collection changeLists, Collection targets, SVNDepth depth, 
            ISVNChangelistHandler handler) throws SVNException {
        doGetChangeListPaths(changeLists, targets, depth, handler);
    }

    /**
     * @param paths 
     * @param depth 
     * @param changelist 
     * @param changelists 
     * @throws SVNException 
     * @deprecated           use {@link #doAddToChangelist(File[], SVNDepth, String, String[])} instead
     */
    public void addToChangelist(File[] paths, SVNDepth depth, String changelist, String[] changelists) throws SVNException {
        doAddToChangelist(paths, depth, changelist, changelists);
    }

    /**
     * @param paths 
     * @param depth 
     * @param changelists 
     * @throws SVNException 
     * @deprecated           use {@link #doRemoveFromChangelist(File[], SVNDepth, String[])} instead
     */
    public void removeFromChangelist(File[] paths, SVNDepth depth, String[] changelists) throws SVNException {
        doRemoveFromChangelist(paths, depth, changelists);
    }
    
    /**
     * Adds each path in <code>paths</code> (recursing to <code>depth</code> as necessary) to
     * <code>changelist</code>. If a path is already a member of another changelist, then removes it from the 
     * other changelist and adds it to <code>changelist</code>. (For now, a path cannot belong to two 
     * changelists at once.)
     * 
     * <p/>
     * <code>changelists</code> is an array of <code>String</code> changelist names, used as a restrictive 
     * filter on items whose changelist assignments are adjusted; that is, doesn't tweak the changeset of any
     * item unless it's currently a member of one of those changelists. If <code>changelists</code> is empty 
     * (or <span class="javakeyword">null</span>), no changelist filtering occurs.
     *
     * <p/>
     * Note: this metadata is purely a client-side "bookkeeping" convenience, and is entirely managed by the 
     * working copy.
     * 
     * <p/>
     * Note: this method does not require repository access.
     * 
     * @param  paths          working copy paths to add to <code>changelist</code>
     * @param  depth          tree depth to process
     * @param  changelist     name of the changelist to add new paths to 
     * @param  changelists    collection of changelist names as a filter
     * @throws SVNException 
     * @since                  1.2.0, New in SVN 1.5.0
     */
    public void doAddToChangelist(File[] paths, SVNDepth depth, String changelist, String[] changelists) throws SVNException {
        setChangelist(paths, changelist, changelists, depth);
    }

    /**
     * Removes each path in <code>paths</code> (recursing to <code>depth</code> as necessary) from changelists 
     * to which they are currently assigned.
     * 
     * <p/>
     * <code>changelists</code> is an array of <code>String</code> changelist names, used as a restrictive 
     * filter on items whose changelist assignments are removed; that is, doesn't remove from a changeset any
     * item unless it's currently a member of one of those changelists. If <code>changelists</code> is empty 
     * (or <span class="javakeyword">null</span>), all changelist assignments in and under each path in 
     * <code>paths</code> (to <code>depth</code>) will be removed.
     * 
     * <p/>
     * Note: this metadata is purely a client-side "bookkeeping" convenience, and is entirely managed by the 
     * working copy.
     *
     * <p/>
     * Note: this method does not require repository access.
     * 
     * @param paths            paths to remove from any changelists  
     * @param depth            tree depth to process
     * @param changelists      collection of changelist names as a filter
     * @throws SVNException 
     * @since                  1.2.0, New in SVN 1.5.0
     */
    public void doRemoveFromChangelist(File[] paths, SVNDepth depth, String[] changelists) throws SVNException {
        setChangelist(paths, null, changelists, depth);
    }

    /**
     * Gets paths belonging to the specified changelists discovered under the specified targets.
     * 
     * <p/>
     * This method is just like {@link #doGetChangeLists(File, Collection, SVNDepth, ISVNChangelistHandler)} 
     * except for it operates on multiple targets instead of a single one. 
     *
     * <p/>
     * Note: this method does not require repository access.
     * 
     * @param  changeLists   collection of changelist names 
     * @param  targets       working copy paths to operate on 
     * @param  depth         tree depth to process
     * @param  handler       caller's handler to receive path-to-changelist information
     * @throws SVNException 
     */
    public void doGetChangeListPaths(Collection changeLists, Collection targets, SVNDepth depth, 
            ISVNChangelistHandler handler) throws SVNException {
        if (changeLists == null || changeLists.isEmpty()) {
            return;
        }
        
        targets = targets == null ? Collections.EMPTY_LIST : targets;
        for (Iterator targetsIter = targets.iterator(); targetsIter.hasNext();) {
            File target = (File) targetsIter.next();
            doGetChangeLists(target, changeLists, depth, handler);
        }
    }

    /**
     * Gets paths belonging to the specified changelists discovered under the specified path.
     * 
     * <p/>
     * Beginning at <code>path</code>, crawls to <code>depth</code> to discover every path in or under 
     * <code>path<code> which belongs to one of the changelists in <code>changeLists</code> (a collection of 
     * <code>String</code> changelist names).
     * If <code>changeLists</code> is null, discovers paths with any changelist.
     * Calls <code>handler</code> each time a changelist-having path is discovered.
     *
     * <p/> 
     * If there was an event handler provided via {@link #setEventHandler(ISVNEventHandler)}, then its 
     * {@link ISVNEventHandler#checkCancelled()} will be invoked during the recursive walk.
     *
     * <p/>
     * Note: this method does not require repository access.
     * 
     * @param  path            target working copy path            
     * @param  changeLists     collection of changelist names
     * @param  depth           tree depth to process
     * @param  handler         caller's handler to receive path-to-changelist information  
     * @throws SVNException 
     * @since                  1.2.0, New in SVN 1.5.0
     */
    public void doGetChangeLists(File path, final Collection changeLists, SVNDepth depth, 
            final ISVNChangelistHandler handler) throws SVNException {
        path = path.getAbsoluteFile();
        SVNWCAccess wcAccess = createWCAccess();
        try {
            wcAccess.probeOpen(path, false, SVNWCAccess.INFINITE_DEPTH);
            
            ISVNEntryHandler entryHandler = new ISVNEntryHandler() {
                
                public void handleEntry(File path, SVNEntry entry) throws SVNException {
                    if (SVNWCAccess.matchesChangeList(changeLists, entry) && 
                            (entry.isFile() || (entry.isDirectory() && 
                                    entry.getName().equals(entry.getAdminArea().getThisDirName())))) {
                        if (handler != null) {
                            handler.handle(path, entry.getChangelistName());
                        }
                    }
                }
            
                public void handleError(File path, SVNErrorMessage error) throws SVNException {
                    SVNErrorManager.error(error, SVNLogType.WC);
                }
            };
            
            wcAccess.walkEntries(path, entryHandler, false, depth);
        } finally {
            wcAccess.close();
        }
    }

    private void setChangelist(File[] paths, String changelistName, String[] changelists, SVNDepth depth) throws SVNException {
        if ("".equals(changelistName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, "Changelist names must not be empty");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        SVNWCAccess wcAccess = createWCAccess();
        for (int i = 0; i < paths.length; i++) {
            checkCancelled();
            File path = paths[i].getAbsoluteFile();
            Collection changelistsSet = null;
            if (changelists != null && changelists.length > 0) {
                changelistsSet = new SVNHashSet();
                for (int j = 0; j < changelists.length; j++) {
                    changelistsSet.add(changelists[j]);
                }
            } 
            try {
                wcAccess.probeOpen(path, true, -1);
                wcAccess.walkEntries(path, new SVNChangeListWalker(wcAccess, changelistName, changelistsSet), false, depth);
            } finally {
                wcAccess.close();
            }
        }
    }
    
    private class SVNChangeListWalker implements ISVNEntryHandler {
        
        private String myChangelist;
        private Collection myChangelists;
        private SVNWCAccess myWCAccess;

        public SVNChangeListWalker(SVNWCAccess wcAccess, String changelistName, Collection changelists) {
            myChangelist = changelistName;
            myChangelists = changelists;
            myWCAccess = wcAccess;
        }
        
        public void handleEntry(File path, SVNEntry entry) throws SVNException {
            if (!SVNWCAccess.matchesChangeList(myChangelists, entry)) {
                return;
            }
            
            if (!entry.isFile()) {
                if (entry.isThisDir()) {
                    SVNEventAction action = myChangelist != null ? SVNEventAction.CHANGELIST_SET :SVNEventAction.CHANGELIST_CLEAR;
                    SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.SKIP, action, null, null);
                    SVNChangelistClient.this.dispatchEvent(event);
                }
                return;
                
            }
            
            if (entry.getChangelistName() == null && myChangelist == null) {
                return;
            }
            
            if (entry.getChangelistName() != null && entry.getChangelistName().equals(myChangelist)) {
                return;
            }
            
            if (myChangelist != null && entry.getChangelistName() != null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CHANGELIST_MOVE, "Removing ''{0}'' from changelist ''{1}''.", new Object[] {path, entry.getChangelistName()});
                SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNEventAction.CHANGELIST_MOVED, SVNEventAction.CHANGELIST_MOVED, err, null);
                SVNChangelistClient.this.dispatchEvent(event);
            }
            
            Map attributes = new SVNHashMap();
            attributes.put(SVNProperty.CHANGELIST, myChangelist);
            SVNAdminArea area = myWCAccess.retrieve(path.getParentFile());
            entry = area.modifyEntry(entry.getName(), attributes, true, false);

            SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION,
                    null, null, null, myChangelist != null ? SVNEventAction.CHANGELIST_SET :SVNEventAction.CHANGELIST_CLEAR,
                    null, null, null, myChangelist);

            SVNChangelistClient.this.dispatchEvent(event);
        }

        public void handleError(File path, SVNErrorMessage error) throws SVNException {
            SVNErrorManager.error(error, SVNLogType.WC);
        }
    }

}
