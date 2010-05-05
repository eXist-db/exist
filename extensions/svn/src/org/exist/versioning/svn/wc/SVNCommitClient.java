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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.exist.versioning.svn.internal.wc.SVNCommitMediator;
import org.exist.versioning.svn.internal.wc.SVNCommitUtil;
import org.exist.versioning.svn.internal.wc.SVNCommitter;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNEventFactory;
import org.exist.versioning.svn.internal.wc.SVNFileListUtil;
import org.exist.versioning.svn.internal.wc.SVNFileType;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.SVNImportMediator;
import org.exist.versioning.svn.internal.wc.SVNPropertiesManager;
import org.exist.versioning.svn.internal.wc.SVNStatusEditor;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNTranslator;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.SVNCancelException;
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
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.wc.ISVNCommitParameters;
import org.tmatesoft.svn.core.wc.ISVNFileFilter;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * The <b>SVNCommitClient</b> class provides methods to perform operations that relate to 
 * committing changes to an SVN repository. These operations are similar to 
 * respective commands of the native SVN command line client 
 * and include ones which operate on working copy items as well as ones
 * that operate only on a repository.
 * 
 * <p>
 * Here's a list of the <b>SVNCommitClient</b>'s commit-related methods 
 * matched against corresponing commands of the SVN command line 
 * client:
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="40%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>SVNKit</b></td>
 * <td><b>Subversion</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doCommit()</td><td>'svn commit'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doImport()</td><td>'svn import'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doDelete()</td><td>'svn delete URL'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doMkDir()</td><td>'svn mkdir URL'</td>
 * </tr>
 * </table>
 *   
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNCommitClient extends SVNBasicClient {

    private ISVNCommitHandler myCommitHandler;
    private ISVNCommitParameters myCommitParameters;
    
    /**
     * Constructs and initializes an <b>SVNCommitClient</b> object
     * with the specified run-time configuration and authentication 
     * drivers.
     * 
     * <p>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNCommitClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the 
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
     * 
     * <p>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNCommitClient</b> will be using a default authentication
     * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
     * which uses server-side settings and auth storage from the 
     * default SVN's run-time configuration area (or system properties
     * if that area is not found).
     * 
     * @param authManager an authentication and network layers driver
     * @param options     a run-time configuration options driver     
     */
    public SVNCommitClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    /**
     * Constructs and initializes an <b>SVNCommitClient</b> object
     * with the specified run-time configuration and repository pool object.
     * 
     * <p/>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNCommitClient</b> will be using a default run-time
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
    public SVNCommitClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }

    /**
     * @param handler
     * @deprecated use {@link #setCommitHandler(ISVNCommitHandler)} instead
     */
    public void setCommitHander(ISVNCommitHandler handler) {
        myCommitHandler = handler;
    }
    
    /**
     * Sets an implementation of <b>ISVNCommitHandler</b> to 
     * the commit handler that will be used during commit operations to handle 
     * commit log messages. The handler will receive a clien's log message and items 
     * (represented as <b>SVNCommitItem</b> objects) that will be 
     * committed. Depending on implementor's aims the initial log message can
     * be modified (or something else) and returned back. 
     * 
     * <p>
     * If using <b>SVNCommitClient</b> without specifying any
     * commit handler then a default one will be used - {@link DefaultSVNCommitHandler}.
     * 
     * @param handler				an implementor's handler that will be used to handle 
     * 								commit log messages
     * @see	  #getCommitHandler()
     * @see	  ISVNCommitHandler
     */
    public void setCommitHandler(ISVNCommitHandler handler) {
        myCommitHandler = handler;
    }
    
    /**
     * Returns the specified commit handler (if set) being in use or a default one 
     * (<b>DefaultSVNCommitHandler</b>) if no special 
     * implementations of <b>ISVNCommitHandler</b> were 
     * previously provided.
     *   
     * @return	the commit handler being in use or a default one
     * @see	    #setCommitHander(ISVNCommitHandler)
     * @see		ISVNCommitHandler
     * @see		DefaultSVNCommitHandler 
     */
    public ISVNCommitHandler getCommitHandler() {
        if (myCommitHandler == null) {
            myCommitHandler = new DefaultSVNCommitHandler();
        }
        return myCommitHandler;
    }
    
    /**
     * Sets commit parameters to use.
     * 
     * <p>
     * When no parameters are set {@link DefaultSVNCommitParameters default} 
     * ones are used. 
     * 
     * @param parameters commit parameters
     * @see              #getCommitParameters()
     */
    public void setCommitParameters(ISVNCommitParameters parameters) {
        myCommitParameters = parameters;
    }
    
    /**
     * Returns commit parameters. 
     * 
     * <p>
     * If no user parameters were previously specified, once creates and 
     * returns {@link DefaultSVNCommitParameters default} ones. 
     * 
     * @return commit parameters
     * @see    #setCommitParameters(ISVNCommitParameters)
     */
    public ISVNCommitParameters getCommitParameters() {
        if (myCommitParameters == null) {
            myCommitParameters = new DefaultSVNCommitParameters();
        }
        return myCommitParameters;
    }
    
    /**
     * Committs removing specified URL-paths from the repository.
     * This call is equivalent to <code>doDelete(urls, commitMessage, null)</code>. 
     *   
     * @param  urls				an array containing URL-strings that represent
     * 							repository locations to be removed
     * @param  commitMessage	a string to be a commit log message
     * @return					information on a new revision as the result
     * 							of the commit
     * @throws SVNException     if one of the following is true:
     *                          <ul>
     *                          <li>a URL does not exist
     *                          <li>probably some of URLs refer to different
     *                          repositories
     *                          </ul>
     * @see                     #doDelete(SVNURL[], String, SVNProperties)
     */
    public SVNCommitInfo doDelete(SVNURL[] urls, String commitMessage)
            throws SVNException {
        return doDelete(urls, commitMessage, null);
    }
    
    /** 
     * Deletes items from a repository.
     * 
     * <p/>
     * If non-<span class="javakeyword">null</span>, <code>revisionProperties</code> holds additional, custom 
     * revision properties (<code>String</code> names mapped to {@link SVNPropertyValue} values) to be set on 
     * the new revision. This table cannot contain any standard Subversion properties.
     *
     * <p/>
     * {@link #getCommitHandler() Commit handler} will be asked for a commit log message.
     *
     * <p/>
     * If the caller's {@link ISVNEventHandler event handler} is not <span class="javakeyword">null</span> and 
     * if the commit succeeds, the handler will be called with {@link SVNEventAction#COMMIT_COMPLETED} event 
     * action.
     * 
     * @param  urls                  repository urls to delete 
     * @param  commitMessage         commit log message
     * @param  revisionProperties    custom revision properties 
     * @return                       information about the new committed revision    
     * @throws SVNException          in the following cases:
     *                               <ul>
     *                               <li/>exception with {@link SVNErrorCode#RA_ILLEGAL_URL} error code - if 
     *                               cannot compute common root url for <code>urls</code>
     *                               <li/>exception with {@link SVNErrorCode#FS_NOT_FOUND} error code - if 
     *                               some of <code>urls</code> does not exist
     *                               </ul>
     * @since                        1.2.0, SVN 1.5.0
     */
    public SVNCommitInfo doDelete(SVNURL[] urls, String commitMessage, SVNProperties revisionProperties)
            throws SVNException {
        if (urls == null || urls.length == 0) {
            return SVNCommitInfo.NULL;
        }
        List paths = new ArrayList();
        SVNURL rootURL = SVNURLUtil.condenceURLs(urls, paths, true);
        if (rootURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, 
                    "Can not compute common root URL for specified URLs");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        if (paths.isEmpty()) {
            // there is just root.
            paths.add(SVNPathUtil.tail(rootURL.getURIEncodedPath()));
            rootURL = rootURL.removePathTail();
        }
        SVNCommitItem[] commitItems = new SVNCommitItem[paths.size()];
        for (int i = 0; i < commitItems.length; i++) {
            String path = (String) paths.get(i);
            commitItems[i] = new SVNCommitItem(null, rootURL.appendPath(path, true),
                    null, SVNNodeKind.NONE, SVNRevision.UNDEFINED, SVNRevision.UNDEFINED, 
                    false, true, false, false, false, false);
        }
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, commitItems);
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
        }

        List decodedPaths = new ArrayList();
        for (Iterator commitPaths = paths.iterator(); commitPaths.hasNext();) {
            String path = (String) commitPaths.next();
            decodedPaths.add(SVNEncodingUtil.uriDecode(path));
        }
        paths = decodedPaths;
        SVNRepository repos = createRepository(rootURL, null, null, true);
        for (Iterator commitPath = paths.iterator(); commitPath.hasNext();) {
            String path = (String) commitPath.next();
            SVNNodeKind kind = repos.checkPath(path, -1);
            if (kind == SVNNodeKind.NONE) {
                SVNURL url = rootURL.appendPath(path, false);
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, 
                        "URL ''{0}'' does not exist", url);
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
        }
        commitMessage = SVNCommitUtil.validateCommitMessage(commitMessage);
        SVNPropertiesManager.validateRevisionProperties(revisionProperties);
        ISVNEditor commitEditor = repos.getCommitEditor(commitMessage, null, false, revisionProperties, null);
        ISVNCommitPathHandler deleter = new ISVNCommitPathHandler() {
            public boolean handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
                commitEditor.deleteEntry(commitPath, -1);
                return false;
            }
        };
        SVNCommitInfo info;
        try {
            SVNCommitUtil.driveCommitEditor(deleter, paths, commitEditor, -1);
            info = commitEditor.closeEdit();
        } catch (SVNException e) {
            try {
                commitEditor.abortEdit();
            } catch (SVNException inner) {
                //
            }
            throw e;
        }
        if (info != null && info.getNewRevision() >= 0) { 
            dispatchEvent(SVNEventFactory.createSVNEvent(null, SVNNodeKind.NONE, null, info.getNewRevision(), SVNEventAction.COMMIT_COMPLETED, null, null, null), ISVNEventHandler.UNKNOWN);
        }
        return info != null ? info : SVNCommitInfo.NULL;
    }
    
    /**
     * Committs a creation of a new directory/directories in the repository.
     * 
     * @param  urls				an array containing URL-strings that represent
     * 							new repository locations to be created
     * @param  commitMessage	a string to be a commit log message
     * @return					information on a new revision as the result
     * 							of the commit
     * @throws SVNException     if some of URLs refer to different
     *                          repositories
     */
    public SVNCommitInfo doMkDir(SVNURL[] urls, String commitMessage) throws SVNException {
        return doMkDir(urls, commitMessage, null, false);
    }
    
    /** 
     * Creates directory(ies) in a repository.
     * 
     * <p/>
     * If <code>makeParents</code> is <span class="javakeyword">true</span>, creates any non-existent parent 
     * directories also.
     * 
     * <p/>
     * If non-<span class="javakeyword">null</span>, <code>revisionProperties</code> holds additional,
     * custom revision properties (<code>String</code> names mapped to {@link SVNPropertyValue} values) to be 
     * set on the new revision. This table cannot contain any standard Subversion properties.
     *
     * <p/>
     * {@link #getCommitHandler() Commit handler} will be asked for a commit log message.
     *
     * <p/>
     * If the caller's {@link ISVNEventHandler event handler} is not <span class="javakeyword">null</span> and 
     * if the commit succeeds, the handler will be called with {@link SVNEventAction#COMMIT_COMPLETED} event 
     * action.
     * 
     * @param  urls                  repository locations to create 
     * @param  commitMessage         commit log message
     * @param  revisionProperties    custom revision properties
     * @param  makeParents           if <span class="javakeyword">true</span>, creates all non-existent 
     *                               parent directories
     * @return                       information about the new committed revision
     * @throws SVNException          in the following cases:
     *                               <ul>
     *                               <li/>exception with {@link SVNErrorCode#RA_ILLEGAL_URL} error code - if 
     *                               cannot compute common root url for <code>urls</code>
     *                               <li/>exception with {@link SVNErrorCode#FS_NOT_FOUND} error code - if 
     *                               some of <code>urls</code> does not exist
     *                               </ul>
     * @since                        1.2.0, SVN 1.5.0
     */
    public SVNCommitInfo doMkDir(SVNURL[] urls, String commitMessage, SVNProperties revisionProperties, 
            boolean makeParents) throws SVNException {
        if (makeParents) {
            List allURLs = new LinkedList();
            for (int i = 0; i < urls.length; i++) {
                SVNURL url = urls[i];
                addURLParents(allURLs, url);
            }
            urls = (SVNURL[]) allURLs.toArray(new SVNURL[allURLs.size()]);
        }
        
        if (urls == null || urls.length == 0) {
            return SVNCommitInfo.NULL;
        }
        Collection paths = new SVNHashSet();
        SVNURL rootURL = SVNURLUtil.condenceURLs(urls, paths, false);
        if (rootURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, 
                    "Can not compute common root URL for specified URLs");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        if (paths.isEmpty()) {
            paths.add(SVNPathUtil.tail(rootURL.getURIEncodedPath()));
            rootURL = rootURL.removePathTail();
        }
        
        if (paths.contains("")) {
            List convertedPaths = new ArrayList();
            String tail = SVNPathUtil.tail(rootURL.getURIEncodedPath());
            rootURL = rootURL.removePathTail();
            for (Iterator commitPaths = paths.iterator(); commitPaths.hasNext();) {
                String path = (String) commitPaths.next();
                if ("".equals(path)) {
                    convertedPaths.add(tail);
                } else {
                    convertedPaths.add(SVNPathUtil.append(tail, path));
                }
            }
            paths = convertedPaths;
        }
        List sortedPaths = new ArrayList(paths);
        Collections.sort(sortedPaths, SVNPathUtil.PATH_COMPARATOR);
        
        SVNCommitItem[] commitItems = new SVNCommitItem[sortedPaths.size()];
        for (int i = 0; i < commitItems.length; i++) {
            String path = (String) sortedPaths.get(i);
            commitItems[i] = new SVNCommitItem(null, rootURL.appendPath(path, true),
                    null, SVNNodeKind.DIR, SVNRevision.UNDEFINED, SVNRevision.UNDEFINED,
                    true, false, false, false, false, false);
        }
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, commitItems);
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
        }

        List decodedPaths = new ArrayList();
        for (Iterator commitPaths = sortedPaths.iterator(); commitPaths.hasNext();) {
            String path = (String) commitPaths.next();
            decodedPaths.add(SVNEncodingUtil.uriDecode(path));
        }
        paths = decodedPaths;
        SVNRepository repos = createRepository(rootURL, null, null, true);
        commitMessage = SVNCommitUtil.validateCommitMessage(commitMessage);
        SVNPropertiesManager.validateRevisionProperties(revisionProperties);
        ISVNEditor commitEditor = repos.getCommitEditor(commitMessage, null, false, revisionProperties, null);
        ISVNCommitPathHandler creater = new ISVNCommitPathHandler() {
            public boolean handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
                SVNPathUtil.checkPathIsValid(commitPath);
                commitEditor.addDir(commitPath, null, -1);
                return true;
            }
        };
        SVNCommitInfo info;
        try {
            SVNCommitUtil.driveCommitEditor(creater, paths, commitEditor, -1);
            info = commitEditor.closeEdit();
        } catch (SVNException e) {
            try {
                commitEditor.abortEdit();
            } catch (SVNException inner) {
                //
            }
            throw e;
        }
        if (info != null && info.getNewRevision() >= 0) { 
            dispatchEvent(SVNEventFactory.createSVNEvent(null, SVNNodeKind.NONE, null, info.getNewRevision(), 
                    SVNEventAction.COMMIT_COMPLETED, null, null, null), ISVNEventHandler.UNKNOWN);
        }
        return info != null ? info : SVNCommitInfo.NULL;
    }
    
    /**
     * Committs an addition of a local unversioned file or directory into 
     * the repository. 
     * 
     * <p/>
     * This method is identical to <code>doImport(path, dstURL, commitMessage, null, true, false, SVNDepth.fromRecurse(recursive))</code>. 
     * 
     * @param  path				a local unversioned file or directory to be imported
     * 							into the repository
     * @param  dstURL			a URL-string that represents a repository location
     * 							where the <code>path</code> will be imported 			
     * @param  commitMessage	a string to be a commit log message
     * @param  recursive		this flag is relevant only when the <code>path</code> is 
     * 							a directory: if <span class="javakeyword">true</span> then the entire directory
     * 							tree will be imported including all child directories, otherwise 
     * 							only items located in the directory itself
     * @return					information on a new revision as the result
     * 							of the commit
     * @throws SVNException     if one of the following is true:
     *                          <ul>
     *                          <li><code>dstURL</code> is invalid
     *                          <li>the path denoted by <code>dstURL</code> already
     *                          exists
     *                          <li><code>path</code> contains a reserved name - <i>'.svn'</i>
     *                          </ul>
     * @deprecated              use {@link #doImport(File, SVNURL, String, SVNProperties, boolean, boolean, SVNDepth)}
     *                          instead                          
     */
    public SVNCommitInfo doImport(File path, SVNURL dstURL, String commitMessage, boolean recursive) throws SVNException {
        return doImport(path, dstURL, commitMessage, null, true, false, SVNDepth.fromRecurse(recursive));
    }

    /**
     * Committs an addition of a local unversioned file or directory into 
     * the repository. 
     * 
     * <p/>
     * This method is identical to <code>doImport(path, dstURL, commitMessage, null, useGlobalIgnores, false, SVNDepth.fromRecurse(recursive))</code>.
     * 
     * @param  path             a local unversioned file or directory to be imported
     *                          into the repository
     * @param  dstURL           a URL-string that represents a repository location
     *                          where the <code>path</code> will be imported            
     * @param  commitMessage    a string to be a commit log message
     * @param  useGlobalIgnores if <span class="javakeyword">true</span> 
     *                          then those paths that match global ignore patterns controlled 
     *                          by a config options driver (see {@link org.tmatesoft.svn.core.wc.ISVNOptions#getIgnorePatterns()}) 
     *                          will not be imported, otherwise global ignore patterns are not  
     *                          used
     * @param  recursive        this flag is relevant only when the <code>path</code> is 
     *                          a directory: if <span class="javakeyword">true</span> then the entire directory
     *                          tree will be imported including all child directories, otherwise 
     *                          only items located in the directory itself
     * @return                  information on a new revision as the result
     *                          of the commit
     * @throws SVNException     if one of the following is true:
     *                          <ul>
     *                          <li><code>dstURL</code> is invalid
     *                          <li>the path denoted by <code>dstURL</code> already
     *                          exists
     *                          <li><code>path</code> contains a reserved name - <i>'.svn'</i>
     *                          </ul>
     * @deprecated              use {@link #doImport(File, SVNURL, String, SVNProperties, boolean, boolean, SVNDepth)}
     *                          instead                          
     */
    public SVNCommitInfo doImport(File path, SVNURL dstURL, String commitMessage, boolean useGlobalIgnores, boolean recursive) throws SVNException {
        return doImport(path, dstURL, commitMessage, null, useGlobalIgnores, false, SVNDepth.fromRecurse(recursive));
    }

    /** 
     * Imports file or directory <code>path</code> into repository directory <code>dstURL</code> at
     * HEAD revision. If some components of <code>dstURL</code> do not exist, then creates parent directories 
     * as necessary.
     * 
     * <p/>
     * If <code>path</code> is a directory, the contents of that directory are imported directly into the 
     * directory identified by <code>dstURL</code>. Note that the directory <code>path</code> itself is not 
     * imported -- that is, the base name of <code>path<code> is not part of the import.
     *
     * <p/>
     * If <code>path</code> is a file, then the parent of <code>dstURL</code> is the directory
     * receiving the import. The base name of <code>dstURL</code> is the filename in the repository. 
     * In this case if <code>dstURL</code> already exists, throws {@link SVNException}.
     *
     * <p/>
     * If the caller's {@link ISVNEventHandler event handler} is not <span class="javakeyword">null</span> it 
     * will be called as the import progresses with {@link SVNEventAction#COMMIT_ADDED} action. If the commit 
     * succeeds, the handler will be called with {@link SVNEventAction#COMMIT_COMPLETED} event 
     * action.
     * 
     * <p/>
     * If non-<span class="javakeyword">null</span>, <code>revisionProperties</code> holds additional, custom 
     * revision properties (<code>String</code> names mapped to {@link SVNPropertyValue} values) to be set on the new revision.
     * This table cannot contain any standard Subversion properties.
     *
     * <p/>
     * {@link #getCommitHandler() Commit handler} will be asked for a commit log message.
     * 
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#EMPTY}, imports just <code>path</code> and nothing below it. If 
     * {@link SVNDepth#FILES}, imports <code>path</code> and any file children of <code>path</code>. If 
     * {@link SVNDepth#IMMEDIATES}, imports <code>path</code>, any file children, and any immediate 
     * subdirectories (but nothing underneath those subdirectories). If {@link SVNDepth#INFINITY}, imports
     * <code>path</code> and everything under it fully recursively.
     * 
     * <p/>
     * If <code>useGlobalIgnores</code> is <span class="javakeyword">false</span>, doesn't add files or 
     * directories that match ignore patterns.
     * 
     * <p/>
     * If <code>ignoreUnknownNodeTypes</code> is <span class="javakeyword">false</span>, ignores files of which 
     * the node type is unknown, such as device files and pipes.
     * 
     * @param  path                     path to import
     * @param  dstURL                   import destination url 
     * @param  commitMessage            commit log message
     * @param  revisionProperties       custom revision properties
     * @param  useGlobalIgnores         whether matching against global ignore patterns should take place
     * @param  ignoreUnknownNodeTypes   whether to ignore files of unknown node types or not 
     * @param  depth                    tree depth to process
     * @return                          information about the new committed revision
     * @throws SVNException             in the following cases:
     *                                  <ul>
     *                                  <li/>exception with {@link SVNErrorCode#ENTRY_NOT_FOUND} error code - 
     *                                  if <code>path</code> does not exist
     *                                  <li/>exception with {@link SVNErrorCode#ENTRY_EXISTS} error code -
     *                                  if <code>dstURL</code> already exists and <code>path</code> is a file
     *                                  <li/>exception with {@link SVNErrorCode#CL_ADM_DIR_RESERVED} error code -
     *                                  if trying to import an item with a reserved SVN name (like 
     *                                  <code>'.svn'</code> or <code>'_svn'</code>) 
     *                                  </ul> 
     * @since                           1.2.0, New in SVN 1.5.0
     */
    public SVNCommitInfo doImport(File path, SVNURL dstURL, String commitMessage, 
            SVNProperties revisionProperties, boolean useGlobalIgnores, boolean ignoreUnknownNodeTypes,
            SVNDepth depth) throws SVNException {
        // first find dstURL root.
        SVNRepository repos = null;
        SVNFileType srcKind = SVNFileType.getType(path);
        if (srcKind == SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, 
                    "Path ''{0}'' does not exist", path);            
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        List newPaths = new ArrayList();
        SVNURL rootURL = dstURL;
        repos = createRepository(rootURL, null, null, true);
        SVNURL reposRoot = repos.getRepositoryRoot(true);
        while (!reposRoot.equals(rootURL)) {
            if (repos.checkPath("", -1) == SVNNodeKind.NONE) {
                newPaths.add(SVNPathUtil.tail(rootURL.getPath()));
                rootURL = rootURL.removePathTail();
                repos = createRepository(rootURL, null, null, true);
            } else {
                break;
            }
        }
        if (newPaths.isEmpty() && (srcKind == SVNFileType.FILE || srcKind == SVNFileType.SYMLINK)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, 
                    "Path ''{0}'' already exists", dstURL);            
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (newPaths.contains(SVNFileUtil.getAdminDirectoryName())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ADM_DIR_RESERVED, 
                    "''{0}'' is a reserved name and cannot be imported", SVNFileUtil.getAdminDirectoryName());            
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNCommitItem[] items = new SVNCommitItem[1];
        items[0] = new SVNCommitItem(path, dstURL, null, srcKind == SVNFileType.DIRECTORY ? SVNNodeKind.DIR : 
                        SVNNodeKind.FILE, SVNRevision.UNDEFINED, SVNRevision.UNDEFINED,  
                        true, false, false, false, false, false);
        items[0].setPath(path.getName());
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, items);
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
        }
        commitMessage = SVNCommitUtil.validateCommitMessage(commitMessage);
        SVNPropertiesManager.validateRevisionProperties(revisionProperties);
        ISVNEditor commitEditor = repos.getCommitEditor(commitMessage, null, false, revisionProperties, 
                new SVNImportMediator());
        String filePath = "";
        if (srcKind != SVNFileType.DIRECTORY) {
            filePath = (String) newPaths.remove(0);
            for (int i = 0; i < newPaths.size(); i++) {
                String newDir = (String) newPaths.get(i);
                filePath = newDir + "/" + filePath;
            }
        }
        Collection ignores = useGlobalIgnores ? SVNStatusEditor.getGlobalIgnores(getOptions()) : null;
        checkCancelled();
        boolean changed = false;
        SVNCommitInfo info = null;
        try {
            commitEditor.openRoot(-1);
            String newDirPath = null;
            for (int i = newPaths.size() - 1; i >= 0; i--) {
                newDirPath = newDirPath == null ? (String) newPaths.get(i) : SVNPathUtil.append(newDirPath, (String) newPaths.get(i));
                commitEditor.addDir(newDirPath, null, -1);
            }
            changed = newPaths.size() > 0;
            SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
            if (srcKind == SVNFileType.DIRECTORY) {
                changed |= importDir(deltaGenerator, path, newDirPath, useGlobalIgnores, 
                        ignoreUnknownNodeTypes, depth, commitEditor);
            } else if (srcKind == SVNFileType.FILE || srcKind == SVNFileType.SYMLINK) {
                if (!useGlobalIgnores || !SVNStatusEditor.isIgnored(ignores, path, "/" + path.getName())) {
                    changed |= importFile(deltaGenerator, path, srcKind, filePath, commitEditor);
                }
            } else if (srcKind == SVNFileType.NONE || srcKind == SVNFileType.UNKNOWN) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, 
                        "''{0}'' does not exist", path);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            
            if (!changed) {
                try {
                    commitEditor.abortEdit();
                } catch (SVNException e) {}
                return SVNCommitInfo.NULL;
            }
            for (int i = 0; i < newPaths.size(); i++) {
                commitEditor.closeDir();
            }
            info = commitEditor.closeEdit();
        } finally {
            if (!changed || info == null) {
                try {
                    commitEditor.abortEdit();
                } catch (SVNException e) {
                    //
                }
            }
        }
        if (info != null && info.getNewRevision() >= 0) { 
            dispatchEvent(SVNEventFactory.createSVNEvent(null, SVNNodeKind.NONE, null, info.getNewRevision(), 
                    SVNEventAction.COMMIT_COMPLETED, null, null, null), ISVNEventHandler.UNKNOWN);
        }
        return info != null ? info : SVNCommitInfo.NULL;
    }
    
    /**
     * Committs local changes to the repository. 
     * 
     * <p/>
     * This method is identical to <code>doCommit(paths, keepLocks, commitMessage, null, null, false, force, SVNDepth.fromRecurse(recursive))</code>.
     * 
     * @param  paths			an array of local items which should be traversed
     * 							to commit changes they have to the repository  
     * @param  keepLocks		if <span class="javakeyword">true</span> and there are local items that 
     * 							were locked then the commit will left them locked,
     * 							otherwise the items will be unlocked after the commit
     * 							succeeds  
     * @param  commitMessage	a string to be a commit log message
     * @param  force			<span class="javakeyword">true</span> to force a non-recursive commit; if
     * 							<code>recursive</code> is set to <span class="javakeyword">true</span> the <code>force</code>
     * 							flag is ignored
     * @param  recursive		relevant only for directory items: if <span class="javakeyword">true</span> then 
     * 							the entire directory tree will be committed including all child directories, 
     * 							otherwise only items located in the directory itself
     * @return					information on a new revision as the result
     * 							of the commit
     * @throws SVNException
     * @deprecated              use {@link #doCommit(File[], boolean, String, SVNProperties, String[], boolean, boolean, SVNDepth)} 
     *                          instead
     */
    public SVNCommitInfo doCommit(File[] paths, boolean keepLocks, String commitMessage, boolean force, boolean recursive) throws SVNException {
        return doCommit(paths, keepLocks, commitMessage, null, null, false, force, SVNDepth.getInfinityOrEmptyDepth(recursive));
    }
    
    /**
     * Commits files or directories into repository.
     * 
     * <p/>
     * <code>paths</code> need not be canonicalized nor condensed; this method will take care of
     * that. If <code>targets has zero elements, then do nothing and return
     * immediately without error.
     * 
     * <p/>
     * If non-<span class="javakeyword">null</span>, <code>revisionProperties</code> holds additional,
     * custom revision properties (<code>String</code> names mapped to {@link SVNPropertyValue} values) to be 
     * set on the new revision. This table cannot contain any standard Subversion properties.
     *
     * <p/>
     * If the caller's {@link ISVNEventHandler event handler} is not <span class="javakeyword">null</span> it 
     * will be called as the commit progresses with any of the following actions: 
     * {@link SVNEventAction#COMMIT_MODIFIED}, {@link SVNEventAction#COMMIT_ADDED}, 
     * {@link SVNEventAction#COMMIT_DELETED}, {@link SVNEventAction#COMMIT_REPLACED}. If the commit 
     * succeeds, the handler will be called with {@link SVNEventAction#COMMIT_COMPLETED} event 
     * action.
     * 
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, commits all changes to and below named targets. If 
     * <code>depth</code> is {@link SVNDepth#EMPTY}, commits only named targets (that is, only property changes 
     * on named directory targets, and property and content changes for named file targets). If <code>depth</code> 
     * is {@link SVNDepth#FILES}, behaves as above for named file targets, and for named directory targets, 
     * commits property changes on a named directory and all changes to files directly inside that directory. 
     * If {@link SVNDepth#IMMEDIATES}, behaves as for {@link SVNDepth#FILES}, and for subdirectories of any 
     * named directory target commits as though for {@link SVNDepth#EMPTY}.
     * 
     * <p/>
     * Unlocks paths in the repository, unless <code>keepLocks</code> is <span class="javakeyword">true</span>.
     *
     * <p/>
     * <code>changelists</code> is an array of <code>String</code> changelist names, used as a restrictive 
     * filter on items that are committed; that is, doesn't commit anything unless it's a member of one of those
     * changelists. After the commit completes successfully, removes changelist associations from the targets, 
     * unless <code>keepChangelist</code> is set. If <code>changelists</code> is empty (or altogether 
     * <span class="javakeyword">null</span>), no changelist filtering occurs.
     * 
     * <p/>
     * If no exception is thrown and {@link SVNCommitInfo#getNewRevision()} is invalid (<code>&lt;0</code>), 
     * then the commit was a no-op; nothing needed to be committed.
     * 
     * @param  paths                paths to commit 
     * @param  keepLocks            whether to unlock or not files in the repository
     * @param  commitMessage        commit log message
     * @param  revisionProperties   custom revision properties
     * @param  changelists          changelist names array 
     * @param  keepChangelist       whether to remove <code>changelists</code> or not
     * @param  force                <span class="javakeyword">true</span> to force a non-recursive commit; if
     *                              <code>depth</code> is {@link SVNDepth#INFINITY} the <code>force</code>
     *                              flag is ignored
     * @param  depth                tree depth to process
     * @return                      information about the new committed revision
     * @throws SVNException 
     * @since                       1.2.0, New in Subversion 1.5.0
     */
    public SVNCommitInfo doCommit(File[] paths, boolean keepLocks, String commitMessage, SVNProperties revisionProperties, 
            String[] changelists, boolean keepChangelist, boolean force, SVNDepth depth) throws SVNException {
        SVNCommitPacket packet = doCollectCommitItems(paths, keepLocks, force, depth, changelists);
        try {
            packet = packet.removeSkippedItems();
            return doCommit(packet, keepLocks, keepChangelist, commitMessage, revisionProperties);
        } finally {
            if (packet != null) {
                packet.dispose();
            }
        }
    }
    
    /**
     * Committs local changes made to the Working Copy items to the repository. 
     * 
     * <p>
     * This method is identical to <code>doCommit(commitPacket, keepLocks, false, commitMessage, null)</code>.
     * 
     * <p>
     * <code>commitPacket</code> contains commit items ({@link SVNCommitItem}) 
     * which represent local Working Copy items that were changed and are to be committed. 
     * Commit items are gathered into a single {@link SVNCommitPacket}
     * by invoking {@link #doCollectCommitItems(File[], boolean, boolean, boolean) doCollectCommitItems()}. 
     * 
     * @param  commitPacket		a single object that contains items to be committed
     * @param  keepLocks		if <span class="javakeyword">true</span> and there are local items that 
     * 							were locked then the commit will left them locked,
     * 							otherwise the items will be unlocked after the commit
     * 							succeeds
     * @param  commitMessage	a string to be a commit log message
     * @return					information on a new revision as the result
     * 							of the commit
     * @throws SVNException     
     * @see	   SVNCommitItem
     */
    public SVNCommitInfo doCommit(SVNCommitPacket commitPacket, boolean keepLocks, String commitMessage) throws SVNException {
        return doCommit(commitPacket, keepLocks, false, commitMessage, null);
    }
    
    /**
     * Commits files or directories into repository.
     * 
     * <p/>
     * This method is identical to {@link #doCommit(File[], boolean, String, SVNProperties, String[], boolean, boolean, SVNDepth)}
     * except for it receives a commit packet instead of paths array. The aforementioned method collects commit 
     * items into a commit packet given working copy paths. This one accepts already collected commit items 
     * provided in <code>commitPacket</code>.  
     * 
     * <p/>
     * <code>commitPacket</code> contains commit items ({@link SVNCommitItem}) 
     * which represent local Working Copy items that are to be committed. 
     * Commit items are gathered in a single {@link SVNCommitPacket} by invoking 
     * either {@link #doCollectCommitItems(File[], boolean, boolean, SVNDepth, String[])} or 
     * {@link #doCollectCommitItems(File[], boolean, boolean, SVNDepth, boolean, String[])}. 
     * 
     * <p/>
     * For more details on parameters, please, refer to {@link #doCommit(File[], boolean, String, SVNProperties, String[], boolean, boolean, SVNDepth)}.
     * 
     * @param  commitPacket        a single object that contains items to be committed
     * @param  keepLocks           if <span class="javakeyword">true</span> and there are local items that 
     *                             were locked then the commit will left them locked,
     *                             otherwise the items will be unlocked after the commit
     *                             succeeds
     * @param  keepChangelist      whether to remove changelists or not
     * @param  commitMessage       commit log message
     * @param  revisionProperties  custom revision properties
     * @return                     information about the new committed revision
     * @throws SVNException 
     * @since                      1.2.0, SVN 1.5.0
     */
    public SVNCommitInfo doCommit(SVNCommitPacket commitPacket, boolean keepLocks, boolean keepChangelist, 
            String commitMessage, SVNProperties revisionProperties) throws SVNException {
        SVNCommitInfo[] info = doCommit(new SVNCommitPacket[] {commitPacket}, keepLocks, keepChangelist, commitMessage, revisionProperties);
        if (info != null && info.length > 0) {
            if (info[0].getErrorMessage() != null && info[0].getErrorMessage().getErrorCode() != SVNErrorCode.REPOS_POST_COMMIT_HOOK_FAILED) {
                SVNErrorManager.error(info[0].getErrorMessage(), SVNLogType.DEFAULT);
            }
            return info[0];
        } 
        return SVNCommitInfo.NULL;
    }
    
    /**
     * Committs local changes, made to the Working Copy items, to the repository. 
     * 
     * <p>
     * <code>commitPackets</code> is an array of packets that contain commit items (<b>SVNCommitItem</b>) 
     * which represent local Working Copy items that were changed and are to be committed. 
     * Commit items are gathered in a single <b>SVNCommitPacket</b>
     * by invoking {@link #doCollectCommitItems(File[], boolean, boolean, boolean) doCollectCommitItems()}. 
     * 
     * <p>
     * This allows to commit separate trees of Working Copies "belonging" to different
     * repositories. One packet per one repository. If repositories are different (it means more than
     * one commit will be done), <code>commitMessage</code> may be replaced by a commit handler
     * to be a specific one for each commit.
     * 
     * <p>
     * This method is identical to <code>doCommit(commitPackets, keepLocks, false, commitMessage, null)</code>.
     * 
     * @param  commitPackets    logically grouped items to be committed
     * @param  keepLocks        if <span class="javakeyword">true</span> and there are local items that 
     *                          were locked then the commit will left them locked,
     *                          otherwise the items will be unlocked after the commit
     *                          succeeds
     * @param  commitMessage    a string to be a commit log message
     * @return                  committed information
     * @throws SVNException
     */
    public SVNCommitInfo[] doCommit(SVNCommitPacket[] commitPackets, boolean keepLocks, String commitMessage) throws SVNException {
        return doCommit(commitPackets, keepLocks, false, commitMessage, null);
    }
    
    /**
     * Commits files or directories into repository.
     * 
     * <p>
     * <code>commitPackets</code> is an array of packets that contain commit items ({@link SVNCommitItem}) 
     * which represent local Working Copy items that were changed and are to be committed. 
     * Commit items are gathered in a single {@link SVNCommitPacket}
     * by invoking {@link #doCollectCommitItems(File[], boolean, boolean, SVNDepth, String[])} or 
     * {@link #doCollectCommitItems(File[], boolean, boolean, SVNDepth, boolean, String[])}. 
     * 
     * <p>
     * This allows to commit items from separate Working Copies checked out from the same or different 
     * repositories. For each commit packet {@link #getCommitHandler() commit handler} is invoked to 
     * produce a commit message given the one <code>commitMessage</code> passed to this method.
     * Each commit packet is committed in a separate transaction.
     * 
     * <p/>
     * For more details on parameters, please, refer to {@link #doCommit(File[], boolean, String, SVNProperties, String[], boolean, boolean, SVNDepth)}.
     * 
     * @param  commitPackets       commit packets containing commit commit items per one commit
     * @param  keepLocks           if <span class="javakeyword">true</span> and there are local items that 
     *                             were locked then the commit will left them locked, otherwise the items will 
     *                             be unlocked by the commit
     * @param  keepChangelist      whether to remove changelists or not
     * @param  commitMessage       a string to be a commit log message
     * @param  revisionProperties  custom revision properties
     * @return                     information about the new committed revisions 
     * @throws SVNException 
     * @since                      1.2.0, SVN 1.5.0
     */
    public SVNCommitInfo[] doCommit(SVNCommitPacket[] commitPackets, boolean keepLocks, boolean keepChangelist, 
            String commitMessage, SVNProperties revisionProperties) throws SVNException {
        if (commitPackets == null || commitPackets.length == 0) {
            return new SVNCommitInfo[0];
        }

        Collection tmpFiles = null;
        SVNCommitInfo info = null;
        ISVNEditor commitEditor = null;

        Collection infos = new ArrayList();
        boolean needsSleepForTimeStamp = false;
        for (int p = 0; p < commitPackets.length; p++) {
            SVNCommitPacket commitPacket = commitPackets[p].removeSkippedItems();
            if (commitPacket.getCommitItems().length == 0) {
                continue;
            }
            try {
                commitMessage = getCommitHandler().getCommitMessage(commitMessage, commitPacket.getCommitItems());                
                if (commitMessage == null) {
                    infos.add(SVNCommitInfo.NULL);
                    continue;
                }
                commitMessage = SVNCommitUtil.validateCommitMessage(commitMessage);
                Map commitables = new TreeMap();
                SVNURL baseURL = SVNCommitUtil.translateCommitables(commitPacket.getCommitItems(), commitables);
                Map lockTokens = SVNCommitUtil.translateLockTokens(commitPacket.getLockTokens(), baseURL.toString());
                //TODO: we should pass wcAccess and path to check uuids
                SVNCommitItem firstItem = commitPacket.getCommitItems()[0];
                SVNRepository repository = createRepository(baseURL, firstItem.getFile(), 
                        firstItem.getWCAccess(), true);
                SVNCommitMediator mediator = new SVNCommitMediator(commitables);
                
                tmpFiles = mediator.getTmpFiles();
                String repositoryRoot = repository.getRepositoryRoot(true).getPath();
                SVNPropertiesManager.validateRevisionProperties(revisionProperties);
                commitEditor = repository.getCommitEditor(commitMessage, lockTokens, keepLocks, revisionProperties, mediator);
                // commit.
                // set event handler for each wc access.
                for (int i = 0; i < commitPacket.getCommitItems().length; i++) {
                    commitPacket.getCommitItems()[i].getWCAccess().setEventHandler(getEventDispatcher());
                }
                info = SVNCommitter.commit(mediator.getTmpFiles(), commitables, repositoryRoot, commitEditor);
                // update wc.
                Collection processedItems = new SVNHashSet();
                Collection explicitCommitPaths = new SVNHashSet();
                for (Iterator urls = commitables.keySet().iterator(); urls.hasNext();) {
                    String url = (String) urls.next();
                    SVNCommitItem item = (SVNCommitItem) commitables.get(url);
                    explicitCommitPaths.add(item.getPath());
                }
                for (Iterator urls = commitables.keySet().iterator(); urls.hasNext();) {
                    String url = (String) urls.next();
                    SVNCommitItem item = (SVNCommitItem) commitables.get(url);
                    SVNWCAccess wcAccess = item.getWCAccess();
                    String path = item.getPath();
                    SVNAdminArea dir = null;
                    String target = null;

                    try {
                        if (item.getKind() == SVNNodeKind.DIR) {
                            target = "";
                            dir = wcAccess.retrieve(item.getFile());
                        } else {
                            target = SVNPathUtil.tail(path);
                            dir = wcAccess.retrieve(item.getFile().getParentFile());
                        }
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                            dir = null;
                        }
                    }
                    if (dir == null) {
                        if (hasProcessedParents(processedItems, path)) {
                            processedItems.add(path);
                            continue;
                        }
                        if (item.isDeleted() && item.getKind() == SVNNodeKind.DIR) {
                            File parentPath = "".equals(path) ? null : item.getFile().getParentFile();
                            String nameInParent = "".equals(path) ? null : SVNPathUtil.tail(path);
                            if (parentPath != null) {
                                SVNAdminArea parentDir = wcAccess.retrieve(parentPath);
                                if (parentDir != null) {
                                    SVNEntry entryInParent = parentDir.getEntry(nameInParent, true);
                                    if (entryInParent != null) {
                                        Map attributes = new SVNHashMap();
                                        attributes.put(SVNProperty.SCHEDULE, null);
                                        attributes.put(SVNProperty.DELETED, Boolean.TRUE.toString());
                                        parentDir.modifyEntry(nameInParent, attributes, true, true);
                                    }
                                }
                            }
                            processedItems.add(path);
                            continue;
                        }
                    }
                    SVNEntry entry = dir.getEntry(target, true);
                    if (entry == null && hasProcessedParents(processedItems, path)) {
                        processedItems.add(path);
                        continue;
                    }
                    boolean recurse = false;
                    if (item.isAdded() && item.getCopyFromURL() != null && item.getKind() == SVNNodeKind.DIR) {
                        recurse = true;
                    }
                    boolean removeLock = !keepLocks && item.isLocked();
                    // update entry in dir.
                    SVNProperties wcPropChanges = mediator.getWCProperties(item);
                    dir.commit(target, info, wcPropChanges, removeLock, recurse, !keepChangelist, explicitCommitPaths, getCommitParameters());
                    processedItems.add(path);
                } 
                needsSleepForTimeStamp = true;
                // commit completed, include revision number.
                dispatchEvent(SVNEventFactory.createSVNEvent(null, SVNNodeKind.NONE, null, info.getNewRevision(), SVNEventAction.COMMIT_COMPLETED, null, null, null), ISVNEventHandler.UNKNOWN);
            } catch (SVNException e) {
                if (e instanceof SVNCancelException) {
                    throw e;
                }
                SVNErrorMessage err = e.getErrorMessage().wrap("Commit failed (details follow):");
                infos.add(new SVNCommitInfo(-1, null, null, err));
                dispatchEvent(SVNEventFactory.createErrorEvent(err, SVNEventAction.COMMIT_COMPLETED), ISVNEventHandler.UNKNOWN);
                continue;
            } finally {
                if (info == null && commitEditor != null) {
                    try {
                        commitEditor.abortEdit();
                    } catch (SVNException e) {
                        //
                    }
                }
                if (tmpFiles != null) {
                    for (Iterator files = tmpFiles.iterator(); files.hasNext();) {
                        File file = (File) files.next();
                        file.delete();
                    }
                }
                if (commitPacket != null) {
                    commitPacket.dispose();
                }
            }
            infos.add(info != null ? info : SVNCommitInfo.NULL);
        }
        if (needsSleepForTimeStamp) {
            sleepForTimeStamp();
        }
        return (SVNCommitInfo[]) infos.toArray(new SVNCommitInfo[infos.size()]);
    }
    
    /**
     * Collects commit items (containing detailed information on each Working Copy item
     * that was changed and need to be committed to the repository) into a single 
     * {@link SVNCommitPacket}. 
     * 
     * <p/>
     * This method is equivalent to <code>doCollectCommitItems(paths, keepLocks, force, SVNDepth.fromRecurse(recursive), null)</code>.
     * 
     * @param  paths			an array of local items which should be traversed
     * 							to collect information on every changed item (one 
     * 							<b>SVNCommitItem</b> per each
     * 							modified local item)
     * @param  keepLocks		if <span class="javakeyword">true</span> and there are local items that 
     * 							were locked then these items will be left locked after
     * 							traversing all of them, otherwise the items will be unlocked
     * @param  force			forces collecting commit items for a non-recursive commit  
     * @param  recursive		relevant only for directory items: if <span class="javakeyword">true</span> then 
     * 							the entire directory tree will be traversed including all child 
     * 							directories, otherwise only items located in the directory itself
     * 							will be processed
     * @return					an <b>SVNCommitPacket</b> containing
     * 							all Working Copy items having local modifications and represented as 
     * 							<b>SVNCommitItem</b> objects; if no modified
     * 							items were found then 
     * 							{@link SVNCommitPacket#EMPTY} is returned
     * @throws SVNException
     * @deprecated              use {@link #doCollectCommitItems(File[], boolean, boolean, SVNDepth, String[])}
     *                          instead
     */
    public SVNCommitPacket doCollectCommitItems(File[] paths, boolean keepLocks, boolean force, boolean recursive) throws SVNException {
        SVNDepth depth = recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY;
        return doCollectCommitItems(paths, keepLocks, force, depth, null);
    }

    /**
     * Collects commit items (containing detailed information on each Working Copy item
     * that contains changes and need to be committed to the repository) into a single 
     * {@link SVNCommitPacket}. Further this commit packet can be passed to
     * {@link #doCommit(SVNCommitPacket, boolean, boolean, String, SVNProperties)}.
     * 
     * <p/>
     * For more details on parameters, please, refer to {@link #doCommit(File[], boolean, String, SVNProperties, String[], boolean, boolean, SVNDepth)}. 
     * 
     * @param  paths            an array of local items which should be traversed
     *                          to collect information on every changed item (one 
     *                          <b>SVNCommitItem</b> per each
     *                          modified local item)
     * @param  keepLocks        if <span class="javakeyword">true</span> and there are local items that 
     *                          were locked then these items will be left locked after
     *                          traversing all of them, otherwise the items will be unlocked
     * @param  force            forces collecting commit items for a non-recursive commit  
     * @param  depth            tree depth to process
     * @param  changelists      changelist names array 
     * @return                  commit packet containing commit items                 
     * @throws SVNException 
     * @since                   1.2.0
     */
    public SVNCommitPacket doCollectCommitItems(File[] paths, boolean keepLocks, boolean force, SVNDepth depth, 
            String[] changelists) throws SVNException {
        depth = depth == null ? SVNDepth.UNKNOWN : depth;
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }

        if (paths == null || paths.length == 0) {
            return SVNCommitPacket.EMPTY;
        }
        Collection targets = new ArrayList();
        SVNStatusClient statusClient = new SVNStatusClient(getRepositoryPool(), getOptions());
        statusClient.setEventHandler(new ISVNEventHandler() {
            public void handleEvent(SVNEvent event, double progress) throws SVNException {
            }
            public void checkCancelled() throws SVNCancelException {
                SVNCommitClient.this.checkCancelled();
            }
        });
        SVNWCAccess wcAccess = SVNCommitUtil.createCommitWCAccess(paths, depth, force, targets, statusClient);
        SVNAdminArea[] areas = wcAccess.getAdminAreas();
        for (int i = 0; areas != null && i < areas.length; i++) {
            if (areas[i] != null) {
                areas[i].setCommitParameters(getCommitParameters());
            }
        }
        try {
            Map lockTokens = new SVNHashMap();
            checkCancelled();
            Collection changelistsSet = changelists != null ? new SVNHashSet() : null;
            if (changelists != null) {
                for (int j = 0; j < changelists.length; j++) {
                    changelistsSet.add(changelists[j]);
                }
            }
            
            SVNCommitItem[] commitItems = SVNCommitUtil.harvestCommitables(wcAccess, targets, lockTokens, 
                    !keepLocks, depth, force, changelistsSet, getCommitParameters());
            
            boolean hasModifications = false;
            checkCancelled();
            for (int i = 0; commitItems != null && i < commitItems.length; i++) {
                SVNCommitItem commitItem = commitItems[i];
                if (commitItem.isAdded() || commitItem.isDeleted()
                        || commitItem.isContentsModified()
                        || commitItem.isPropertiesModified()
                        || commitItem.isCopied()) {
                    hasModifications = true;
                    break;
                }
            }
            if (!hasModifications) {
                wcAccess.close();
                return SVNCommitPacket.EMPTY;
            }
            return new SVNCommitPacket(wcAccess, commitItems, lockTokens);
        } catch (SVNException e) {
            wcAccess.close();
            if (e instanceof SVNCancelException) {
                throw e;
            }
            SVNErrorMessage nestedErr = e.getErrorMessage();
            SVNErrorMessage err = SVNErrorMessage.create(nestedErr.getErrorCode(), 
                    "Commit failed (details follow):");
            SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
            return null;
        }
    }
    
    /**
     * Collects commit items (containing detailed information on each Working Copy item
     * that was changed and need to be committed to the repository) into different 
     * <b>SVNCommitPacket</b>s. 
     * 
     * <p/>
     * This method is identical to <code>doCollectCommitItems(paths, keepLocks, force, SVNDepth.fromRecurse(recursive), combinePackets, null)</code>.
     * 
     * @param  paths            an array of local items which should be traversed
     *                          to collect information on every changed item (one 
     *                          <b>SVNCommitItem</b> per each
     *                          modified local item)
     * @param  keepLocks        if <span class="javakeyword">true</span> and there are local items that 
     *                          were locked then these items will be left locked after
     *                          traversing all of them, otherwise the items will be unlocked
     * @param  force            forces collecting commit items for a non-recursive commit  
     * @param  recursive        relevant only for directory items: if <span class="javakeyword">true</span> then 
     *                          the entire directory tree will be traversed including all child 
     *                          directories, otherwise only items located in the directory itself
     *                          will be processed
     * @param combinePackets    if <span class="javakeyword">true</span> then collected commit
     *                          packets will be joined into a single one, so that to be committed
     *                          in a single transaction
     * @return                  an array of commit packets
     * @throws SVNException
     * @deprecated              use {@link #doCollectCommitItems(File[], boolean, boolean, SVNDepth, boolean, String[])}
     *                          instead 
     */
    public SVNCommitPacket[] doCollectCommitItems(File[] paths, boolean keepLocks, boolean force, 
            boolean recursive, boolean combinePackets) throws SVNException {
        SVNDepth depth = recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY;
        return doCollectCommitItems(paths, keepLocks, force, depth, combinePackets, null);
    }
    
    /**
     * Collects commit items (containing detailed information on each Working Copy item that was changed and 
     * need to be committed to the repository) into different 
     * <code>SVNCommitPacket</code>s. This method may be considered as an advanced version of the 
     * {@link #doCollectCommitItems(File[], boolean, boolean, SVNDepth, String[])} method. Its main difference 
     * from the aforementioned method is that it provides an ability to collect commit items from different 
     * working copies checked out from the same repository and combine them into a single commit packet. 
     * This is attained via setting <code>combinePackets</code> into <span class="javakeyword">true</span>. 
     * However even if <code>combinePackets</code> is set, combining may only occur if (besides that the paths
     * must be from the same repository) URLs of <code>paths</code> are formed of identical components, that is 
     * protocol name, host name, port number (if any) must match for all paths. Otherwise combining will not 
     * occur.   
     * 
     * <p/>
     * Combined items will be committed in a single transaction.
     * 
     * <p/>
     * For details on other parameters, please, refer to 
     * {@link #doCommit(File[], boolean, String, SVNProperties, String[], boolean, boolean, SVNDepth)}.
     * 
     * @param  paths            an array of local items which should be traversed
     *                          to collect information on every changed item (one 
     *                          <b>SVNCommitItem</b> per each
     *                          modified local item)
     * @param  keepLocks        if <span class="javakeyword">true</span> and there are local items that 
     *                          were locked then these items will be left locked after
     *                          traversing all of them, otherwise the items will be unlocked
     * @param  force            forces collecting commit items for a non-recursive commit  
     * @param  depth            tree depth to process
     * @param  combinePackets   whether combining commit packets into a single commit packet is allowed or not   
     * @param  changelists      changelist names array
     * @return                  array of commit packets
     * @throws SVNException     in the following cases:
     *                          <ul>
     *                          <li/>exception with {@link SVNErrorCode#ENTRY_MISSING_URL} error code - if 
     *                          working copy root of either path has no url
     *                          </ul>     
     * @since                   1.2.0 
     */
    public SVNCommitPacket[] doCollectCommitItems(File[] paths, boolean keepLocks, boolean force, SVNDepth depth, 
            boolean combinePackets, String[] changelists) throws SVNException {
        
        depth = depth == null ? SVNDepth.UNKNOWN : depth;
        
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }
        
        if (paths == null || paths.length == 0) {
            return new SVNCommitPacket[0];
        }
        Collection packets = new ArrayList();
        Map targets = new SVNHashMap();
        SVNStatusClient statusClient = new SVNStatusClient(getRepositoryPool(), getOptions());
        statusClient.setEventHandler(new ISVNEventHandler() {
            public void handleEvent(SVNEvent event, double progress) throws SVNException {
            }
            public void checkCancelled() throws SVNCancelException {
                SVNCommitClient.this.checkCancelled();
            }
        });
        
        SVNWCAccess[] wcAccesses = SVNCommitUtil.createCommitWCAccess2(paths, depth, force, targets, statusClient);

        for (int i = 0; i < wcAccesses.length; i++) {
            SVNWCAccess wcAccess = wcAccesses[i];
            SVNAdminArea[] areas = wcAccess.getAdminAreas();
            for (int j = 0; areas != null && j < areas.length; j++) {
                if (areas[j] != null) {
                    areas[j].setCommitParameters(getCommitParameters());
                }
            }
            Collection targetPaths = (Collection) targets.get(wcAccess);
            try {
                checkCancelled();
                Map lockTokens = new SVNHashMap();
                Collection changelistsSet = changelists != null ? new SVNHashSet() : null;
                if (changelists != null) {
                    for (int j = 0; j < changelists.length; j++) {
                        changelistsSet.add(changelists[j]);
                    }
                }
                SVNCommitItem[] commitItems = SVNCommitUtil.harvestCommitables(wcAccess, targetPaths, lockTokens, 
                        !keepLocks, depth, force, changelistsSet, getCommitParameters());
                checkCancelled();
                boolean hasModifications = false;
                for (int j = 0; commitItems != null && j < commitItems.length; j++) {
                    SVNCommitItem commitItem = commitItems[j];
                    if (commitItem.isAdded() || commitItem.isDeleted() || commitItem.isContentsModified() || 
                            commitItem.isPropertiesModified() || commitItem.isCopied()) {
                        hasModifications = true;
                        break;
                    }
                }
                if (!hasModifications) {
                    wcAccess.close();
                    continue;
                }
                packets.add(new SVNCommitPacket(wcAccess, commitItems, lockTokens));
            } catch (SVNException e) {
                for (int j = 0; j < wcAccesses.length; j++) {
                    wcAccesses[j].close();
                }
                if (e instanceof SVNCancelException) {
                    throw e;
                }
                SVNErrorMessage nestedErr = e.getErrorMessage();
                SVNErrorMessage err = SVNErrorMessage.create(nestedErr.getErrorCode(), 
                        "Commit failed (details follow):");
                SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
            }
        }
        SVNCommitPacket[] packetsArray = (SVNCommitPacket[]) packets.toArray(new SVNCommitPacket[packets.size()]);
        if (!combinePackets) {
            return packetsArray;
        }
        Map repoUUIDs = new SVNHashMap();
        Map locktokensMap = new SVNHashMap();
        try {
            // get wc root for each packet and uuid for each root.
            // group items by uuid.
            for (int i = 0; i < packetsArray.length; i++) {
                checkCancelled();
                SVNCommitPacket packet = packetsArray[i];
                File wcRoot = SVNWCUtil.getWorkingCopyRoot(packet.getCommitItems()[0].getWCAccess().getAnchor(), true);
                SVNWCAccess rootWCAccess = createWCAccess();
                String uuid = null;
                SVNURL url = null;
                try {
                    SVNAdminArea rootDir = rootWCAccess.open(wcRoot, false, 0);
                    uuid = rootDir.getEntry(rootDir.getThisDirName(), false).getUUID();
                    url = rootDir.getEntry(rootDir.getThisDirName(), false).getSVNURL();
                } finally {
                    rootWCAccess.close();
                }
                checkCancelled();
                if (uuid == null) {
                    if (url != null) {
                        SVNRepository repos = createRepository(url, wcRoot, rootWCAccess, true);
                        uuid = repos.getRepositoryUUID(true);
                    } else {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                                "''{0}'' has no URL", wcRoot);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                }
                // also use protocol, host and port as a key, not only uuid.
                uuid += url.getProtocol() + ":" + url.getHost() + ":" + url.getPort() + ":" + url.getUserInfo();
                if (!repoUUIDs.containsKey(uuid)) {
                    repoUUIDs.put(uuid, new ArrayList());
                    locktokensMap.put(uuid, new SVNHashMap());
                }
                Collection items = (Collection) repoUUIDs.get(uuid);
                Map lockTokens = (Map) locktokensMap.get(uuid);
                for (int j = 0; j < packet.getCommitItems().length; j++) {
                    items.add(packet.getCommitItems()[j]);
                }
                if (packet.getLockTokens() != null) {
                    lockTokens.putAll(packet.getLockTokens());
                }
                checkCancelled();
            }
            packetsArray = new SVNCommitPacket[repoUUIDs.size()];
            int index = 0;
            for (Iterator roots = repoUUIDs.keySet().iterator(); roots.hasNext();) {
                checkCancelled();
                String uuid = (String) roots.next();
                Collection items = (Collection) repoUUIDs.get(uuid);
                Map lockTokens = (Map) locktokensMap.get(uuid);
                SVNCommitItem[] itemsArray = (SVNCommitItem[]) items.toArray(new SVNCommitItem[items.size()]);
                packetsArray[index++] = new SVNCommitPacket(null, itemsArray, lockTokens);
            }
        } catch (SVNException e) {
            for (int j = 0; j < wcAccesses.length; j++) {
                wcAccesses[j].close();
            }
            if (e instanceof SVNCancelException) {
                throw e;
            }            
            SVNErrorMessage nestedErr = e.getErrorMessage();
            SVNErrorMessage err = SVNErrorMessage.create(nestedErr.getErrorCode(), 
                    "Commit failed (details follow):");
            SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
        }
        return packetsArray;        
    }

    private void addURLParents(List targets, SVNURL url) throws SVNException {
        SVNURL parentURL = url.removePathTail();
        SVNRepository repos = createRepository(parentURL, null, null, true);
        SVNNodeKind kind = repos.checkPath("", SVNRepository.INVALID_REVISION);
        if (kind == SVNNodeKind.NONE) {
            addURLParents(targets, parentURL);
        }
        targets.add(url);
    }

    private boolean importDir(SVNDeltaGenerator deltaGenerator, File dir, String importPath, 
            boolean useGlobalIgnores, boolean ignoreUnknownNodeTypes, SVNDepth depth, ISVNEditor editor) throws SVNException {
        checkCancelled();
        File[] children = SVNFileListUtil.listFiles(dir);
        boolean changed = false;
        ISVNFileFilter filter = getCommitHandler() instanceof ISVNFileFilter ? (ISVNFileFilter) getCommitHandler() : null;
        Collection ignores = useGlobalIgnores ? SVNStatusEditor.getGlobalIgnores(getOptions()) : null;
        for (int i = 0; children != null && i < children.length; i++) {
            File file = children[i];
            if (SVNFileUtil.getAdminDirectoryName().equals(file.getName())) {
                SVNEvent skippedEvent = SVNEventFactory.createSVNEvent(file, SVNNodeKind.NONE, null, SVNRepository.INVALID_REVISION, SVNEventAction.SKIP, SVNEventAction.COMMIT_ADDED, null, null);
                handleEvent(skippedEvent, ISVNEventHandler.UNKNOWN);
                continue;
            }
            if (filter != null && !filter.accept(file)) {
                continue;
            }
            String path = importPath == null ? file.getName() : SVNPathUtil.append(importPath, file.getName());
            if (useGlobalIgnores && SVNStatusEditor.isIgnored(ignores, file, "/" + path)) {
                continue;
            }
            SVNFileType fileType = SVNFileType.getType(file);
            if (fileType == SVNFileType.DIRECTORY && depth.compareTo(SVNDepth.IMMEDIATES) >= 0) {
                editor.addDir(path, null, -1);
                changed |= true;
                SVNEvent event = SVNEventFactory.createSVNEvent(file, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.COMMIT_ADDED, null, null, null);
                handleEvent(event, ISVNEventHandler.UNKNOWN);
                SVNDepth depthBelowHere = depth;
                if (depth == SVNDepth.IMMEDIATES) {
                    depthBelowHere = SVNDepth.EMPTY;
                }
                importDir(deltaGenerator, file, path, useGlobalIgnores, ignoreUnknownNodeTypes, 
                        depthBelowHere, editor);
                editor.closeDir();
            } else if ((fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK) && 
                    depth.compareTo(SVNDepth.FILES) >= 0) {
                changed |= importFile(deltaGenerator, file, fileType, path, editor);
            } else if (fileType != SVNFileType.DIRECTORY && fileType != SVNFileType.FILE) {
                if (ignoreUnknownNodeTypes) {
                    SVNEvent skippedEvent = SVNEventFactory.createSVNEvent(file, SVNNodeKind.NONE, null, SVNRepository.INVALID_REVISION, SVNEventAction.SKIP, SVNEventAction.COMMIT_ADDED, null, null);
                    handleEvent(skippedEvent, ISVNEventHandler.UNKNOWN);
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, 
                            "Unknown or unversionable type for ''{0}''", file);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }

        }
        return changed;
    }

    private boolean importFile(SVNDeltaGenerator deltaGenerator, File file, SVNFileType fileType, String filePath, ISVNEditor editor) throws SVNException {
        if (fileType == null || fileType == SVNFileType.UNKNOWN) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "unknown or unversionable type for ''{0}''", file);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        editor.addFile(filePath, null, -1);
        String mimeType = null;
        Map autoProperties = new SVNHashMap();
        if (fileType != SVNFileType.SYMLINK) {
            autoProperties = SVNPropertiesManager.computeAutoProperties(getOptions(), file, autoProperties);
        } else {
            autoProperties.put(SVNProperty.SPECIAL, "*");
        }
        for (Iterator names = autoProperties.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            String value = (String) autoProperties.get(name);
            if (SVNProperty.EOL_STYLE.equals(name) && value != null) {
                if (SVNProperty.isBinaryMimeType((String) autoProperties.get(SVNProperty.MIME_TYPE))) {
                    continue;
                } else if (!SVNTranslator.checkNewLines(file)) {
                    continue;
                } 
            }
            if (SVNProperty.CHARSET.equals(name) && value != null) {
                if (SVNProperty.isBinaryMimeType((String) autoProperties.get(SVNProperty.MIME_TYPE))) {
                    continue;
                }
                try {
                    SVNTranslator.getCharset(value, filePath, getOptions());
                } catch (SVNException e) {
                    continue;
                }
            }
            editor.changeFileProperty(filePath, name, SVNPropertyValue.create(value));
        }
        // send "adding"
        SVNEvent addedEvent = SVNEventFactory.createSVNEvent(file, SVNNodeKind.FILE, mimeType, SVNRepository.INVALID_REVISION, SVNEventAction.COMMIT_ADDED, null, null, null);
        handleEvent(addedEvent, ISVNEventHandler.UNKNOWN);
        // translate and send file.
        String charset = SVNTranslator.getCharset((String) autoProperties.get(SVNProperty.CHARSET), file.getPath(), getOptions());
        String eolStyle = (String) autoProperties.get(SVNProperty.EOL_STYLE);
        String keywords = (String) autoProperties.get(SVNProperty.KEYWORDS);
        boolean special = autoProperties.get(SVNProperty.SPECIAL) != null;
        File tmpFile = null;
        if (charset != null || eolStyle != null || keywords != null || special) {
            byte[] eolBytes = SVNTranslator.getBaseEOL(eolStyle);
            Map keywordsMap = keywords != null ? SVNTranslator.computeKeywords(keywords, null, null, null, null, getOptions()) : null;
            tmpFile = SVNFileUtil.createTempFile("import", ".tmp");
            SVNTranslator.translate(file, tmpFile, charset, eolBytes, keywordsMap, special, false);
        }
        File importedFile = tmpFile != null ? tmpFile : file;
        InputStream is = null;
        String checksum = null;
        try {
            is = SVNFileUtil.openFileForReading(importedFile, SVNLogType.WC);
            editor.applyTextDelta(filePath, null);
            checksum = deltaGenerator.sendDelta(filePath, is, editor, true);
        } finally {
            SVNFileUtil.closeFile(is);
            SVNFileUtil.deleteFile(tmpFile);
        }
        editor.closeFile(filePath, checksum);
        return true;
    }

    private static boolean hasProcessedParents(Collection paths, String path) throws SVNException {
        path = SVNPathUtil.removeTail(path);
        if (paths.contains(path)) {
            return true;
        }
        if ("".equals(path)) {
            return false;
        }
        return hasProcessedParents(paths, path);
    }
    
    static String validateCommitMessage(String message) {
        if (message == null) {
            return message;
        }
        message = message.replaceAll("\r\n", "\n");
        message = message.replace('\r', '\n');
        return message;
    }

}
