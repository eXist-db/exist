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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.SVNAdminUtil;
import org.exist.versioning.svn.internal.wc.SVNCancellableOutputStream;
import org.exist.versioning.svn.internal.wc.SVNCommitUtil;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNEventFactory;
import org.exist.versioning.svn.internal.wc.SVNExternal;
import org.exist.versioning.svn.internal.wc.SVNFileListUtil;
import org.exist.versioning.svn.internal.wc.SVNFileType;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.SVNPropertiesManager;
import org.exist.versioning.svn.internal.wc.SVNStatusEditor;
import org.exist.versioning.svn.internal.wc.SVNTreeConflictUtil;
import org.exist.versioning.svn.internal.wc.SVNWCManager;
import org.exist.versioning.svn.internal.wc.admin.ISVNEntryHandler;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminAreaFactory;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminAreaInfo;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNLog;
import org.exist.versioning.svn.internal.wc.admin.SVNTranslator;
import org.exist.versioning.svn.internal.wc.admin.SVNVersionedProperties;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.IOExceptionWrapper;
import org.tmatesoft.svn.core.internal.wc.ISVNFileContentFetcher;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNAddParameters;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * The <b>SVNWCClient</b> class combines a number of version control
 * operations mainly intended for local work with Working Copy items. This class
 * includes those operations that are destined only for local work on a
 * Working Copy as well as those that are moreover able to access  a repository.
 * <p/>
 * <p/>
 * Here's a list of the <b>SVNWCClient</b>'s methods
 * matched against corresponing commands of the SVN command line
 * client:
 * <p/>
 * <table cellpadding="3" cellspacing="1" border="0" width="70%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>SVNKit</b></td>
 * <td><b>Subversion</b></td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doAdd()</td><td>'svn add'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetFileContents()</td><td>'svn cat'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doDelete()</td><td>'svn delete'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doCleanup()</td><td>'svn cleanup'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doInfo()</td><td>'svn info'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doLock()</td><td>'svn lock'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doUnlock()</td><td>'svn unlock'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>
 * doSetProperty()
 * </td>
 * <td>
 * 'svn propset PROPNAME PROPVAL PATH'<br />
 * 'svn propdel PROPNAME PATH'<br />
 * 'svn propedit PROPNAME PATH'
 * </td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doSetRevisionProperty()</td>
 * <td>
 * 'svn propset PROPNAME --revprop -r REV PROPVAL [URL]'<br />
 * 'svn propdel PROPNAME --revprop -r REV [URL]'<br />
 * 'svn propedit PROPNAME --revprop -r REV [URL]'
 * </td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>
 * doGetProperty()
 * </td>
 * <td>
 * 'svn propget PROPNAME PATH'<br />
 * 'svn proplist PATH'
 * </td>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetRevisionProperty()</td>
 * <td>
 * 'svn propget PROPNAME --revprop -r REV [URL]'<br />
 * 'svn proplist --revprop -r REV [URL]'
 * </td>
 * </tr>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doResolve()</td><td>'svn resolved'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doRevert()</td><td>'svn revert'</td>
 * </tr>
 * </table>
 *
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNWCClient extends SVNBasicClient {

    /**
     * Default implementation of {@link ISVNAddParameters} which
     * <code>onInconsistentEOLs(File file)</code> always returns the 
     * {@link ISVNAddParameters#REPORT_ERROR} action.
     * 
     * @since 1.2
     */
    public static ISVNAddParameters DEFAULT_ADD_PARAMETERS = new ISVNAddParameters() {
        public Action onInconsistentEOLs(File file) {
            return ISVNAddParameters.REPORT_ERROR;
        }
    };

    private ISVNAddParameters myAddParameters;
    private ISVNCommitHandler myCommitHandler;

    private boolean myIsRevertMissingDirectories;

    /**
     * Constructs and initializes an <b>SVNWCClient</b> object
     * with the specified run-time configuration and authentication
     * drivers.
     * <p/>
     * <p/>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNWCClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
     * <p/>
     * <p/>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNWCClient</b> will be using a default authentication
     * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
     * which uses server-side settings and auth storage from the
     * default SVN's run-time configuration area (or system properties
     * if that area is not found).
     *
     * @param authManager an authentication and network layers driver
     * @param options     a run-time configuration options driver
     */
    public SVNWCClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    /**
     * Constructs and initializes an <b>SVNWCClient</b> object
     * with the specified run-time configuration and repository pool object.
     * <p/>
     * <p/>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNWCClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
     * <p/>
     * <p/>
     * If <code>repositoryPool</code> is <span class="javakeyword">null</span>,
     * then {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory} will be used to create {@link SVNRepository repository access objects}.
     *
     * @param repositoryPool   a repository pool object
     * @param options          a run-time configuration options driver
     */
    public SVNWCClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }

    /**
     * Sets custom add parameters to this client object.
     * 
     * @param addParameters extra parameters for add operations
     * @since 1.2
     */
    public void setAddParameters(ISVNAddParameters addParameters) {
        myAddParameters = addParameters;
    }

    /**
     * Returns the specified commit handler (if set) being in use or a default one
     * (<b>DefaultSVNCommitHandler</b>) if no special
     * implementations of <b>ISVNCommitHandler</b> were
     * previousely provided.
     *
     * @return the commit handler being in use or a default one
     * @see #setCommitHandler(ISVNCommitHandler)
     * @see DefaultSVNCommitHandler
     */
    public ISVNCommitHandler getCommitHandler() {
        if (myCommitHandler == null) {
            myCommitHandler = new DefaultSVNCommitHandler();
        }
        return myCommitHandler;
    }

    /**
     * Sets an implementation of <b>ISVNCommitHandler</b> to
     * the commit handler that will be used during commit operations to handle
     * commit log messages. The handler will receive a clien's log message and items
     * (represented as <b>SVNCommitItem</b> objects) that will be
     * committed. Depending on implementor's aims the initial log message can
     * be modified (or something else) and returned back.
     * <p/>
     * <p/>
     * If using <b>SVNWCClient</b> without specifying any
     * commit handler then a default one will be used - {@link DefaultSVNCommitHandler}.
     *
     * @param handler an implementor's handler that will be used to handle
     *                commit log messages
     * @see #getCommitHandler()
     * @see ISVNCommitHandler
     */
    public void setCommitHandler(ISVNCommitHandler handler) {
        myCommitHandler = handler;
    }

    /**
     * Returns the add parameters object used by this object.
     * If no custom object was specified through a call to {@link #setAddParameters(ISVNAddParameters)} 
     * then {@link #DEFAULT_ADD_PARAMETERS} is returned.
     * 
     * @return add parameters object
     * @since 1.2
     */
    protected ISVNAddParameters getAddParameters() {
        if (myAddParameters == null) {
            return DEFAULT_ADD_PARAMETERS;
        }

        return myAddParameters;
    }
    
    public void setRevertMissingDirectories(boolean revertMissing) {
        myIsRevertMissingDirectories = revertMissing;
    }

    public boolean isRevertMissingDirectories() {
        return myIsRevertMissingDirectories;
    }

    /**
     * Outputs the content of file identified by <code>path</code> and <code>revision</code> 
     * to the stream <code>dst</code>. The actual node revision selected
     * is determined by the path as it exists in <code>pegRevision</code>. If <code>pegRevision</code>
     * is {@link SVNRevision#UNDEFINED}, then it defaults to {@link SVNRevision#WORKING}.
     * 
     * <p/> 
     * If <vode>revision</code> is one of:
     * <ul>
     * <li>{@link SVNRevision#BASE}
     * <li>{@link SVNRevision#WORKING}
     * <li>{@link SVNRevision#COMMITTED}
     * </ul>
     * then the file contents are taken from the working copy file item (no network connection is needed).
     * Otherwise the file item's contents are taken from the repository at a particular revision.
     *
     * @param path           working copy path
     * @param pegRevision    revision in which the file item is first looked up
     * @param revision       target revision
     * @param expandKeywords if <span class="javakeyword">true</span> then
     *                       all keywords presenting in the file and listed in
     *                       the file's {@link org.tmatesoft.svn.core.SVNProperty#KEYWORDS}
     *                       property (if set) will be substituted, otherwise not
     * @param dst            the destination where the file contents will be written to
     * @throws SVNException  if one of the following is true:
     *                       <ul>
     *                       <li><code>path</code> refers to a directory
     *                       <li><code>path</code> does not exist
     *                       <li><code>path</code> is not under version control
     *                       </ul>
     * @see #doGetFileContents(SVNURL,SVNRevision,SVNRevision,boolean,OutputStream)
     */
    public void doGetFileContents(File path, SVNRevision pegRevision, SVNRevision revision, 
            boolean expandKeywords, OutputStream dst) throws SVNException {
        if (dst == null) {
            return;
        }
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.BASE;
        } else if (revision == SVNRevision.COMMITTED) {
            revision = SVNRevision.BASE;
        }
        if ((!pegRevision.isValid() || pegRevision == SVNRevision.BASE || pegRevision == SVNRevision.WORKING) &&
                (!revision.isValid() || revision == SVNRevision.BASE || revision == SVNRevision.WORKING)) {
            doGetLocalFileContents(path, dst, revision, expandKeywords);
        } else {
            SVNRepository repos = createRepository(null, path, null, pegRevision, revision, null);
            checkCancelled();
            long revNumber = getRevisionNumber(revision, repos, path);
            SVNNodeKind kind = repos.checkPath("", revNumber);
            if (kind == SVNNodeKind.DIR) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_IS_DIRECTORY, 
                        "URL ''{0}'' refers to a directory", repos.getLocation());
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            checkCancelled();
            if (!expandKeywords) {
                repos.getFile("", revNumber, null, new SVNCancellableOutputStream(dst, this));
            } else {
                SVNProperties properties = new SVNProperties();
                repos.getFile("", revNumber, properties, null);
                checkCancelled();

                String keywords = properties.getStringValue(SVNProperty.KEYWORDS);
                String eol = properties.getStringValue(SVNProperty.EOL_STYLE);
                String charset = SVNTranslator.getCharset(properties.getStringValue(SVNProperty.CHARSET), path.getPath(), getOptions());
                if (keywords != null || eol != null || charset != null) {
                    String cmtRev = properties.getStringValue(SVNProperty.COMMITTED_REVISION);
                    String cmtDate = properties.getStringValue(SVNProperty.COMMITTED_DATE);
                    String author = properties.getStringValue(SVNProperty.LAST_AUTHOR);
                    Map keywordsMap = SVNTranslator.computeKeywords(keywords, expandKeywords ? repos.getLocation().toString() : null, author, cmtDate, cmtRev, getOptions());
                    OutputStream translatingStream = SVNTranslator.getTranslatingOutputStream(dst, charset, SVNTranslator.getEOL(eol, getOptions()), false, keywordsMap, expandKeywords);
                    repos.getFile("", revNumber, null, new SVNCancellableOutputStream(translatingStream, getEventDispatcher()));
                    try {
                        translatingStream.close();
                    } catch (IOExceptionWrapper ioew) {
                        throw ioew.getOriginalException();
                    } catch (IOException e) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()), SVNLogType.WC);
                    }
                } else {
                    repos.getFile("", revNumber, null, new SVNCancellableOutputStream(dst, getEventDispatcher()));
                }
            }
            try {
                dst.flush();
            } catch (IOException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()), SVNLogType.WC);
            }
        }
    }

    /**
     * Outputs the content of file identified by <code>url</code> and <code>revision</code> 
     * to the stream <code>dst</code>. The actual node revision selected
     * is determined by the path as it exists in <code>pegRevision</code>. If <code>pegRevision</code>
     * is {@link SVNRevision#UNDEFINED}, then it defaults to {@link SVNRevision#HEAD}.
     * 
     * @param url            a file item's repository location
     * @param pegRevision    a revision in which the file item is first looked up
     * @param revision       a target revision
     * @param expandKeywords if <span class="javakeyword">true</span> then
     *                       all keywords presenting in the file and listed in
     *                       the file's {@link org.tmatesoft.svn.core.SVNProperty#KEYWORDS}
     *                       property (if set) will be substituted, otherwise not
     * @param dst            the destination where the file contents will be written to
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>url</code> refers to a directory
     *                      <li>it's impossible to create temporary files
     *                      ({@link java.io.File#createTempFile(java.lang.String,java.lang.String) createTempFile()}
     *                      fails) necessary for file translating
     *                      </ul>
     * @see #doGetFileContents(File,SVNRevision,SVNRevision,boolean,OutputStream)
     */
    public void doGetFileContents(SVNURL url, SVNRevision pegRevision, SVNRevision revision, 
            boolean expandKeywords, OutputStream dst) throws SVNException {
        revision = revision == null || !revision.isValid() ? SVNRevision.HEAD : revision;
        // now get contents from URL.
        SVNRepository repos = createRepository(url, null, null, pegRevision, revision, null);
        checkCancelled();
        long revNumber = getRevisionNumber(revision, repos, null);
        checkCancelled();
        SVNNodeKind nodeKind = repos.checkPath("", revNumber);
        checkCancelled();
        if (nodeKind == SVNNodeKind.DIR) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_IS_DIRECTORY, "URL ''{0}'' refers to a directory", url);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        checkCancelled();
        if (!expandKeywords) {
            repos.getFile("", revNumber, null, new SVNCancellableOutputStream(dst, this));
        } else {
            SVNProperties properties = new SVNProperties();
            repos.getFile("", revNumber, properties, null);
            checkCancelled();
            String charset = SVNTranslator.getCharset(properties.getStringValue(SVNProperty.CHARSET), repos.getLocation().toDecodedString(), getOptions());
            String keywords = properties.getStringValue(SVNProperty.KEYWORDS);
            String eol = properties.getStringValue(SVNProperty.EOL_STYLE);
            if (charset != null || keywords != null || eol != null) {
                String cmtRev = properties.getStringValue(SVNProperty.COMMITTED_REVISION);
                String cmtDate = properties.getStringValue(SVNProperty.COMMITTED_DATE);
                String author = properties.getStringValue(SVNProperty.LAST_AUTHOR);
                Map keywordsMap = SVNTranslator.computeKeywords(keywords, expandKeywords ? repos.getLocation().toString() : null, author, cmtDate, cmtRev, getOptions());
                OutputStream translatingStream = SVNTranslator.getTranslatingOutputStream(dst, charset, SVNTranslator.getEOL(eol, getOptions()), false, keywordsMap, expandKeywords);
                repos.getFile("", revNumber, null, new SVNCancellableOutputStream(translatingStream, getEventDispatcher()));
                try {
                    translatingStream.close();
                } catch (IOExceptionWrapper ioew) {
                    throw ioew.getOriginalException();
                } catch (IOException e) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()), SVNLogType.WC);
                }
            } else {
                repos.getFile("", revNumber, null, new SVNCancellableOutputStream(dst, getEventDispatcher()));
            }
        }
        try {
            dst.flush();
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()), SVNLogType.WC);
        }
    }

    /**
     * Cleans up a working copy.
     * This method is equivalent to a call to <code>doCleanup(path, false)</code>.
     *  
     * @param  path         a WC path to start a cleanup from
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>path</code> does not exist
     *                      <li><code>path</code>'s parent directory
     *                      is not under version control
     *                      </ul>
     * @see                 #doCleanup(File, boolean)
     */
    public void doCleanup(File path) throws SVNException {
        doCleanup(path, false);
    }

    /**
     * Recursively cleans up the working copy, removing locks and resuming
     * unfinished operations.
     * 
     * <p/>
     * If you ever get a "working copy locked" error, use this method
     * to remove stale locks and get your working copy into a usable
     * state again.
     * 
     * <p>
     * This method operates only on working copies and does not open any network connection.
     * 
     * @param  path                 a WC path to start a cleanup from
     * @param  deleteWCProperties   if <span class="javakeyword">true</span>, removes DAV specific 
     *                              <span class="javastring">"svn:wc:"</span> properties from the working copy 
     * @throws SVNException         if one of the following is true:
     *                              <ul>
     *                              <li><code>path</code> does not exist
     *                              <li><code>path</code>'s parent directory
     *                              is not under version control
     *                              </ul>
     */
    public void doCleanup(File path, boolean deleteWCProperties) throws SVNException {
        SVNFileType fType = SVNFileType.getType(path);
        if (fType == SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "''{0}'' does not exist", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (fType == SVNFileType.FILE) {
            path = path.getParentFile();
        } else if (fType == SVNFileType.SYMLINK) {
            path = SVNFileUtil.resolveSymlink(path);
            if (SVNFileType.getType(path) == SVNFileType.FILE) {
                path = path.getParentFile();
            }
        }
        
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminArea adminArea = wcAccess.open(path, true, true, 0);
            adminArea.cleanup();
            if (deleteWCProperties) {
                SVNPropertiesManager.deleteWCProperties(adminArea, null, true);
            }
        } catch (SVNException e) {
            if (e instanceof SVNCancelException) {
                throw e;
            } else if (!SVNAdminArea.isSafeCleanup()) {
                throw e;
            }
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "CLEANUP FAILED for " + path);
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, e);
        } finally {
            wcAccess.close();
            sleepForTimeStamp();
        }
    }

    /**
     * Sets <code>propName</code> to <code>propValue</code> on <code>path</code>.
     * A <code>propValue</code> of <span class="javakeyword">null</span> will delete 
     * the property.
     * 
     * <p/>
     * If <code>depth</code> is {@link org.tmatesoft.svn.core.SVNDepth#EMPTY}, set the property on <code>path</code>
     * only; if {@link SVNDepth#FILES}, set it on <code>path</code> and its file
     * children (if any); if {@link SVNDepth#IMMEDIATES}, on <code>path</code> and all
     * of its immediate children (both files and directories); if
     * {@link SVNDepth#INFINITY}, on <code>path</code> and everything beneath it.
     * 
     * <p/>
     * If <code>propName</code> is an svn-controlled property (i.e. prefixed with
     * <span class="javastring">"svn:"</span>), then the caller is responsible for ensuring that
     * the value uses LF line-endings.
     * 
     * <p/>
     * If <code>skipChecks</code> is <span class="javakeyword">true</span>, this method does no validity 
     * checking.  But if <code>skipChecks</code> is <span class="javakeyword">false</span>, 
     * and <code>propName</code> is not a valid property for <code>path</code>, it throws an exception, 
     * either with an error code {@link org.tmatesoft.svn.core.SVNErrorCode#ILLEGAL_TARGET} 
     * (if the property is not appropriate for <code>path</code>), or with 
     * {@link org.tmatesoft.svn.core.SVNErrorCode#BAD_MIME_TYPE} (if <code>propName</code> is 
     * <span class="javastring">"svn:mime-type"</span>, but <code>propVal</code> is not a valid mime-type).
     * 
     * <p/>
     * <code>changeLists</code> is a collection of <code>String</code> changelist
     * names, used as a restrictive filter on items whose properties are
     * set; that is, don't set properties on any item unless it's a member
     * of one of those changelists.  If <code>changelists</code> is empty (or
     * <span class="javakeyword">null</span>), no changelist filtering occurs.
     * 
     * <p>
     * This method operates only on working copies and does not open any network connection.
     * 
     * @param path          working copy path
     * @param propName      property name
     * @param propValue     property value
     * @param skipChecks    <span class="javakeyword">true</span> to
     *                      force the operation to run without validity checking 
     * @param depth         working copy tree depth to process   
     * @param handler       a caller's property handler
     * @param changeLists   changelist names
     * @throws SVNException <ul>
     *                      <li><code>path</code> does not exist
     *                      <li>exception with {@link SVNErrorCode#CLIENT_PROPERTY_NAME} error code - 
     *                      if <code>propName</code> is a revision property name or not a valid property name or 
     *                      not a regular property name (one starting with 
     *                      a <span class="javastring">"svn:entry"</span> or 
     *                      <span class="javastring">"svn:wc"</span> prefix)
     *                      </ul>
     * @see   #doSetProperty(SVNURL, String, SVNPropertyValue, SVNRevision, String, SVNProperties, boolean, ISVNPropertyHandler)
     * @since 1.2, SVN 1.5
     */
    public void doSetProperty(File path, String propName, SVNPropertyValue propValue, boolean skipChecks, 
            SVNDepth depth, ISVNPropertyHandler handler, Collection changeLists) throws SVNException {
        depth = depth == null ? SVNDepth.UNKNOWN : depth;
        int admLockLevel = SVNWCAccess.INFINITE_DEPTH;
        if (depth == SVNDepth.EMPTY || depth == SVNDepth.FILES) {
            admLockLevel = 0;
        }

        if (propValue != null && !SVNPropertiesManager.isValidPropertyName(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "Bad property name ''{0}''", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (SVNRevisionProperty.isRevisionProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "Revision property ''{0}'' not allowed in this context", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (SVNProperty.isWorkingCopyProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "''{0}'' is a wcprop, thus not accessible to clients", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (SVNProperty.isEntryProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "Property ''{0}'' is an entry property", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        SVNWCAccess wcAccess = createWCAccess();
        try {
            wcAccess.probeOpen(path, true, admLockLevel);
            SVNEntry entry = wcAccess.getVersionedEntry(path, false);
            if (SVNDepth.FILES.compareTo(depth) <= 0 && entry.isDirectory()) {
                PropSetHandler entryHandler = new PropSetHandler(skipChecks, propName, propValue, handler, 
                        changeLists);
                wcAccess.walkEntries(path, entryHandler, false, depth);
            } else if (SVNWCAccess.matchesChangeList(changeLists, entry)) {
                boolean modified = SVNPropertiesManager.setProperty(wcAccess, path, propName, propValue, skipChecks);
                if (modified && handler != null) {
                    handler.handleProperty(path, new SVNPropertyData(propName, propValue, getOptions()));
                }
            }
        } finally {
            wcAccess.close();
        }
    }

    /**
     * Crawls the working copy at <code>path</code> and calls {@link ISVNPropertyValueProvider#providePropertyValues(java.io.File, org.tmatesoft.svn.core.SVNProperties)}
     * to get properties to be change on each path being traversed
     *
     * <p/>
     * If <code>depth</code> is {@link org.tmatesoft.svn.core.SVNDepth#EMPTY}, change the properties on <code>path</code>
     * only; if {@link SVNDepth#FILES}, change the properties on <code>path</code> and its file
     * children (if any); if {@link SVNDepth#IMMEDIATES}, on <code>path</code> and all
     * of its immediate children (both files and directories); if
     * {@link SVNDepth#INFINITY}, on <code>path</code> and everything beneath it.
     *
     * <p/>
     * If <code>skipChecks</code> is <span class="javakeyword">true</span>, this method does no validity
     * checking of changed properties.  But if <code>skipChecks</code> is <span class="javakeyword">false</span>,
     * and changed property name is not a valid property for <code>path</code>, it throws an exception,
     * either with an error code {@link org.tmatesoft.svn.core.SVNErrorCode#ILLEGAL_TARGET}
     * (if the property is not appropriate for <code>path</code>), or with
     * {@link org.tmatesoft.svn.core.SVNErrorCode#BAD_MIME_TYPE} (if changed propery name is
     * <span class="javastring">"svn:mime-type"</span>, but changed property value is not a valid mime-type).
     *
     * <p/>
     * <code>changeLists</code> is a collection of <code>String</code> changelist
     * names, used as a restrictive filter on items whose properties are
     * set; that is, don't set properties on any item unless it's a member
     * of one of those changelists.  If <code>changelists</code> is empty (or
     * <span class="javakeyword">null</span>), no changelist filtering occurs.
     *
     * <p>
     * This method operates only on working copies and does not open any network connection.
     *
     * @param path                         working copy path
     * @param propertyValueProvider        changed properties provider
     * @param skipChecks                   <span class="javakeyword">true</span> to
     *                                     force the operation to run without validity checking
     * @param depth                        working copy tree depth to process
     * @param handler                      a caller's property handler
     * @param changeLists                  changelist names
     * @throws SVNException                <ul>
     *                                     <li><code>path</code> does not exist
     *                                     <li>exception with {@link SVNErrorCode#CLIENT_PROPERTY_NAME} error code -
     *                                     if changed property name is a revision property name or not a valid property name or
     *                                     not a regular property name (one starting with
     *                                     a <span class="javastring">"svn:entry"</span> or
     *                                     <span class="javastring">"svn:wc"</span> prefix)
     *                                     </ul>
     * @see   #doSetProperty(java.io.File, String, org.tmatesoft.svn.core.SVNPropertyValue, boolean, org.tmatesoft.svn.core.SVNDepth, ISVNPropertyHandler, java.util.Collection) 
     * @since 1.2, SVN 1.5
     */
    public void doSetProperty(File path, ISVNPropertyValueProvider propertyValueProvider, boolean skipChecks,
            SVNDepth depth, ISVNPropertyHandler handler, Collection changeLists) throws SVNException {
        depth = depth == null ? SVNDepth.UNKNOWN : depth;
        int admLockLevel = SVNWCAccess.INFINITE_DEPTH;
        if (depth == SVNDepth.EMPTY || depth == SVNDepth.FILES) {
            admLockLevel = 0;
        }

        SVNWCAccess wcAccess = createWCAccess();
        try {
            wcAccess.probeOpen(path, true, admLockLevel);
            SVNEntry entry = wcAccess.getVersionedEntry(path, false);
            if (SVNDepth.FILES.compareTo(depth) <= 0 && entry.isDirectory()) {
                PropSetHandlerExt entryHandler = new PropSetHandlerExt(skipChecks, propertyValueProvider, handler, changeLists);
                wcAccess.walkEntries(path, entryHandler, false, depth);
            } else if (SVNWCAccess.matchesChangeList(changeLists, entry)) {
                SVNAdminArea adminArea = entry.getAdminArea();
                setLocalProperties(path, entry, adminArea, skipChecks, propertyValueProvider, handler);
            }
        } finally {
            wcAccess.close();
        }
    }

    /**
     * Sets <code>propName</code> to <code>propValue</code> on <code>path</code>.
     * A <code>propValue</code> of <span class="javakeyword">null</span> will delete 
     * the property.
     * 
     * <p/>
     * <code>baseRevision</code> must not be null; in this case, the property will only be set
     * if it has not changed since <code>baseRevision</code>.
     * 
     * <p/>
     * The {@link ISVNAuthenticationManager authentication manager} and 
     * {@link ISVNCommitHandler commit handler}, either provided by a caller or default ones,
     * will be used to immediately attempt to commit the property change in the
     * repository.
     * 
     * <p/>
     * If <code>propName</code> is an svn-controlled property (i.e. prefixed with
     * <span class="javastring">"svn:"</span>), then the caller is responsible for ensuring that
     * the value uses LF line-endings.
     * 
     * <p/>
     * If <code>skipChecks</code> is <span class="javakeyword">true</span>, this method does no validity 
     * checking.  But if <code>skipChecks</code> is <span class="javakeyword">false</span>, 
     * and <code>propName</code> is not a valid property for <code>path</code>, it throws an exception, 
     * either with an error code {@link org.tmatesoft.svn.core.SVNErrorCode#ILLEGAL_TARGET} 
     * (if the property is not appropriate for <code>path</code>), or with 
     * {@link org.tmatesoft.svn.core.SVNErrorCode#BAD_MIME_TYPE} (if <code>propName</code> is 
     * <span class="javastring">"svn:mime-type"</span>, but <code>propVal</code> is not a valid mime-type).
     * 
     * <p/>
     * If non-<span class="javakeyword">null</span>, <code>revisionProperties</code> is an 
     * {@link SVNProperties} object holding additional, custom revision properties 
     * (<code>String</code> names mapped to <code>String</code> values) to be set on the new revision in
     * the event that this is a committing operation. This table cannot contain any standard Subversion 
     * properties.
     * 
     * @param url                  versioned item url
     * @param propName             property name
     * @param propValue            property value
     * @param baseRevision         revision to change properties against          
     * @param commitMessage        commit log message
     * @param revisionProperties   custom revision properties to set
     * @param skipChecks           <span class="javakeyword">true</span> to
     *                             force the operation to run without validity checking 
     * @param handler              a caller's property handler
     * @return                     commit information if the commit succeeds
     * @throws SVNException        <ul>
     *                             <li><code>url</code> does not exist in <code>baseRevision</code>
     *                             <li>exception with {@link SVNErrorCode#CLIENT_PROPERTY_NAME} error code - 
     *                             if <code>propName</code> is a revision property name or not a valid property name or 
     *                             not a regular property name (one starting with 
     *                             an <span class="javastring">"svn:entry"</span> or 
     *                             <span class="javastring">"svn:wc"</span> prefix)
     *                             <li>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - 
     *                             if <code>propName</code> is either equal to {@link SVNProperty#EOL_STYLE} or 
     *                             {@link SVNProperty#KEYWORDS} or {@link SVNProperty#CHARSET}
     *                             </ul>
     * @see                        #doSetProperty(File, String, SVNPropertyValue, boolean, SVNDepth, ISVNPropertyHandler, Collection)
     * @since                      1.2, SVN 1.5
     */
    public SVNCommitInfo doSetProperty(SVNURL url, String propName, SVNPropertyValue propValue,
            SVNRevision baseRevision, String commitMessage, SVNProperties revisionProperties,
            boolean skipChecks, ISVNPropertyHandler handler) throws SVNException {
        if (propValue != null && !SVNPropertiesManager.isValidPropertyName(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "Bad property name ''{0}''", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (SVNRevisionProperty.isRevisionProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "Revision property ''{0}'' not allowed in this context", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (SVNProperty.isWorkingCopyProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "''{0}'' is a wcprop, thus not accessible to clients", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (SVNProperty.isEntryProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "Property ''{0}'' is an entry property", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        final SVNRepository repos = createRepository(url, null, null, true);
        long revNumber = SVNRepository.INVALID_REVISION;
        try {
            revNumber = getRevisionNumber(baseRevision, repos, null);
        } catch (SVNException svne) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION,
                    "Setting property on non-local target ''{0}'' needs a base revision", url);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (SVNProperty.EOL_STYLE.equals(propName) || SVNProperty.KEYWORDS.equals(propName) || SVNProperty.CHARSET.equals(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                    "Setting property ''{0}'' on non-local target ''{1}'' is not supported",
                    new Object[]{propName, url});
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        SVNNodeKind kind = repos.checkPath("", revNumber);
        if (kind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, 
                    "Path ''{0}'' does not exist in revision {1}", 
                    new Object[]{ url.getPath(), new Long(revNumber) });
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (propValue != null && SVNProperty.isSVNProperty(propName)) {
            final long baseRev = revNumber;
            
            propValue = SVNPropertiesManager.validatePropertyValue(url.toString(), kind, propName, propValue, 
                    skipChecks, getOptions(), new ISVNFileContentFetcher() {

                Boolean isBinary = null;

                public void fetchFileContent(OutputStream os) throws SVNException {
                    SVNProperties props = new SVNProperties();
                    repos.getFile("", baseRev, props, os);
                    setBinary(props);
                }

                public boolean fileIsBinary() throws SVNException {
                    if (isBinary == null) {
                        SVNProperties props = new SVNProperties();
                        repos.getFile("", baseRev, props, null);
                        setBinary(props);
                    }
                    return isBinary.booleanValue();
                }

                private void setBinary(SVNProperties props) {
                    String mimeType = props.getStringValue(SVNProperty.MIME_TYPE);
                    isBinary = Boolean.valueOf(SVNProperty.isBinaryMimeType(mimeType));
                }

		public SVNPropertyValue getProperty(String propertyName) throws SVNException {
		    SVNProperties props = new SVNProperties();
		    repos.getFile("", baseRev, props, null);
		    return SVNPropertyValue.create(props.getStringValue(propertyName));
		}
            });
        }

        Collection commitItems = new ArrayList(2);
        SVNCommitItem commitItem = new SVNCommitItem(null, url, null,
                kind, SVNRevision.create(revNumber), SVNRevision.UNDEFINED,
                false, false, true, false, false, false);
        commitItems.add(commitItem);
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, (SVNCommitItem[]) commitItems.toArray(new SVNCommitItem[commitItems.size()]));
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
        }
        
        commitMessage = SVNCommitUtil.validateCommitMessage(commitMessage);
        SVNPropertiesManager.validateRevisionProperties(revisionProperties);
        
        SVNCommitInfo commitInfo = null;
        ISVNEditor commitEditor = repos.getCommitEditor(commitMessage, null, true, revisionProperties, null);
        try {
            commitEditor.openRoot(revNumber);
            if (kind == SVNNodeKind.FILE) {
                commitEditor.openFile("", revNumber);
                commitEditor.changeFileProperty("", propName, propValue);
                commitEditor.closeFile("", null);
            } else {
                commitEditor.changeDirProperty(propName, propValue);
            }
            commitEditor.closeDir();
            commitInfo = commitEditor.closeEdit();
        } catch (SVNException svne) {
            commitEditor.abortEdit();
            throw svne;
        }
        if (handler != null) {
            handler.handleProperty(url, new SVNPropertyData(propName, propValue, getOptions()));
        }
        return commitInfo;
    }

    /**
     * Set <code>propName</code> to <code>propValue</code> on revision 
     * <code>revision</code> in the repository represented by <code>path</code>.
     * 
     * <p/>
     * This method simply obtains a url given a working path and calls 
     * {@link #doSetRevisionProperty(SVNURL, SVNRevision, String, SVNPropertyValue, boolean, ISVNPropertyHandler)}
     * passing this url and the rest parameters.
     * 
     * @param path            working copy path
     * @param revision        revision which properties are to be modified
     * @param propName        property name
     * @param propValue       property value
     * @param force           if <span class="javakeyword">true</span> allows newlines in the author property
     * @param handler         caller's property handler
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>exception with {@link SVNErrorCode#CLIENT_PROPERTY_NAME} error code - if 
     *                        <code>propName</code> is invalid
     *                        <li>exceptions thrown by {@link #doSetRevisionProperty(SVNURL, SVNRevision, String, SVNPropertyValue, boolean, ISVNPropertyHandler)}
     *                        </ul>
     */
    public void doSetRevisionProperty(File path, SVNRevision revision, String propName, 
            SVNPropertyValue propValue, boolean force, ISVNPropertyHandler handler) throws SVNException {
        if (propValue != null && !SVNPropertiesManager.isValidPropertyName(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "Bad property name ''{0}''", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNURL url = getURL(path);
        doSetRevisionProperty(url, revision, propName, propValue, force, handler);
    }

    /**
     * Set <code>propName</code> to <code>propValue</code> on revision 
     * <code>revision</code> in the repository represented by <code>path</code>.
     * 
     * A <code>propValue</code> of <span class="javakeyword">null</span> will delete the
     * property. The {@link ISVNAuthenticationManager authentication manager}, either provided by a caller 
     * or a default one, will be used for authentication.
     * 
     * <p/>
     * If <code>propName</code> is an svn-controlled property (i.e. prefixed with
     * <span class="javastring">"svn:"</span>), then the caller is responsible for ensuring that
     * the value is UTF8-encoded and uses LF line-endings.
     * 
     * <p/>
     * Although this routine accepts a working copy path it doesn't affect the working copy at all;  
     * it's a pure network operation that changes an *unversioned* property attached to a
     * revision. This can be used to tweak log messages, dates, authors, and the like. 
     * Be careful: it's a lossy operation.
     * 
     * <p>
     * Also note that unless the administrator creates a pre-revprop-change hook in the repository, 
     * this feature will fail.
     *
     * @param url             repository URL
     * @param revision        revision which properties are to be modified
     * @param propName        property name
     * @param propValue       property value
     * @param force           if <span class="javakeyword">true</span> allows newlines in the author property
     * @param handler         caller's property handler
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>the operation can not be performed without forcing
     *                        <li><code>propName</code> is either invalid or not a regular property name 
     *                        (one starting with an <span class="javastring">"svn:entry"</span> or 
     *                        <span class="javastring">"svn:wc"</span> prefix)
     *                        </ul>
     * @see #doSetRevisionProperty(File, SVNRevision, String, SVNPropertyValue, boolean, ISVNPropertyHandler)
     */
    public void doSetRevisionProperty(SVNURL url, SVNRevision revision, String propName, 
            SVNPropertyValue propValue, boolean force, ISVNPropertyHandler handler) throws SVNException {
        if (propValue != null && !SVNPropertiesManager.isValidPropertyName(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "Bad property name ''{0}''", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (!force && SVNRevisionProperty.AUTHOR.equals(propName) && propValue != null && propValue.isString() && propValue.getString().indexOf('\n') >= 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_REVISION_AUTHOR_CONTAINS_NEWLINE, "Value will not be set unless forced");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (SVNProperty.isWorkingCopyProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, 
                    "''{0}'' is a wcprop, thus not accessible to clients", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (SVNProperty.isEntryProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "Property ''{0}'' is an entry property", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }         
        SVNRepository repos = createRepository(url, null, null, SVNRevision.UNDEFINED, revision, null);
        long revNumber = getRevisionNumber(revision, repos, null);
        repos.setRevisionPropertyValue(revNumber, propName, propValue);
        if (handler != null) {
            handler.handleProperty(revNumber, new SVNPropertyData(propName, propValue, getOptions()));
        }
    }

    /**
     * Gets the value of the property <code>propName</code> for <code>path</code>. 
     * This method simply creates an implementation of {@link ISVNPropertyHandler} which stores
     * the value only for <code>path</code> which is then used in the following call to 
     * <code>doGetProperty(path, propName, pegRevision, revision, SVNDepth.EMPTY, handler, null)</code>.
     *
     * @param path          a WC item's path
     * @param propName      an item's property name; if it's
     *                      <span class="javakeyword">null</span> then
     *                      all the item's properties will be retrieved
     *                      but only the first of them returned
     * @param pegRevision   a revision in which the item is first looked up
     * @param revision      a target revision;
     * @return              the item's property
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>propName</code> starts
     *                      with the {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                      svn:wc:} prefix
     *                      <li><code>path</code> is not under version control
     *                      </ul>
     * @see #doGetProperty(File, String, SVNRevision, SVNRevision, SVNDepth, ISVNPropertyHandler, Collection)
     */
    public SVNPropertyData doGetProperty(final File path, String propName, SVNRevision pegRevision, 
            SVNRevision revision) throws SVNException {
        final SVNPropertyData[] data = new SVNPropertyData[1];
        doGetProperty(path, propName, pegRevision, revision, SVNDepth.EMPTY, new ISVNPropertyHandler() {
            public void handleProperty(File file, SVNPropertyData property) {
                if (data[0] == null && path.equals(file)) {
                    data[0] = property;
                }
            }

            public void handleProperty(SVNURL url, SVNPropertyData property) {
            }

            public void handleProperty(long revision, SVNPropertyData property) {
            }
        }, null);
        return data[0];
    }

    /**
     * Gets the value of the property <code>propName</code> for <code>url</code>. 
     * This method simply creates an implementation of {@link ISVNPropertyHandler} which stores
     * the value only for <code>path</code> which is then used in the following call to 
     * <code>doGetProperty(url, propName, pegRevision, revision, SVNDepth.EMPTY, handler)</code>.
     *
     * @param url           an item's repository location
     * @param propName      an item's property name; if it's
     *                      <span class="javakeyword">null</span> then
     *                      all the item's properties will be retrieved
     *                      but only the first of them returned
     * @param pegRevision   a revision in which the item is first looked up
     * @param revision      a target revision;
     * @return              the item's property
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>propName</code> starts
     *                      with the {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                      svn:wc:} prefix
     *                      <li><code>path</code> is not under version control
     *                      </ul>
     * @see #doGetProperty(SVNURL, String, SVNRevision, SVNRevision, SVNDepth, ISVNPropertyHandler)
     */
    public SVNPropertyData doGetProperty(final SVNURL url, String propName, SVNRevision pegRevision, 
            SVNRevision revision) throws SVNException {
        final SVNPropertyData[] data = new SVNPropertyData[1];
        doGetProperty(url, propName, pegRevision, revision, SVNDepth.EMPTY, new ISVNPropertyHandler() {
            public void handleProperty(File file, SVNPropertyData property) {
            }

            public void handleProperty(long revision, SVNPropertyData property) {
            }

            public void handleProperty(SVNURL location, SVNPropertyData property) throws SVNException {
                if (data[0] == null && url.toString().equals(location.toString())) {
                    data[0] = property;
                }
            }
        });
        return data[0];
    }

    /**
     * Gets an item's versioned property and passes it to a provided property
     * handler. It's possible to get either a local property (from a Working
     * Copy) or a remote one (located in a repository).
     * If <vode>revision</code> is one of:
     * <ul>
     * <li>{@link SVNRevision#BASE BASE}
     * <li>{@link SVNRevision#WORKING WORKING}
     * <li>{@link SVNRevision#COMMITTED COMMITTED}
     * </ul>
     * then the result is a WC item's property. Otherwise the
     * property is taken from a repository (using the item's URL).
     *
     * @param path          a WC item's path
     * @param propName      an item's property name; if it's
     *                      <span class="javakeyword">null</span> then
     *                      all the item's properties will be retrieved
     *                      and passed to <code>handler</code> for
     *                      processing
     * @param pegRevision   a revision in which the item is first looked up
     * @param revision      a target revision;
     * @param recursive     <span class="javakeyword">true</span> to
     *                      descend recursively
     * @param handler       a caller's property handler
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>propName</code> starts
     *                      with the {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                      svn:wc:} prefix
     *                      <li><code>path</code> is not under version control
     *                      </ul>
     * @deprecated use      {@link #doGetProperty(File, String, SVNRevision, SVNRevision, SVNDepth, ISVNPropertyHandler, Collection)} instead
     */
    public void doGetProperty(File path, String propName, SVNRevision pegRevision, SVNRevision revision, 
            boolean recursive, ISVNPropertyHandler handler) throws SVNException {
        doGetProperty(path, propName, pegRevision, revision, SVNDepth.getInfinityOrEmptyDepth(recursive), 
                handler, null);
    }

    /**
     * Invokes <code>handler</code> on paths covered by <code>depth</code> starting with 
     * the specified <code>path</code>.
     * 
     * <p/>
     * If both <vode>revision</code> and <code>pegRevision</code> are ones of:
     * <ul>
     * <li>{@link SVNRevision#BASE BASE}
     * <li>{@link SVNRevision#WORKING WORKING}
     * <li>{@link SVNRevision#COMMITTED COMMITTED}
     * <li>{@link SVNRevision#UNDEFINED}
     * </ul>
     * then this method gets properties from the working copy without connecting to the repository. 
     * Otherwise properties are taken from the repository (using the item's URL).
     *
     * <p/>
     * The actual node revision selected is determined by the path as it exists in <code>pegRevision</code>.
     * If <code>pegRevision</code> is {@link SVNRevision#UNDEFINED}, then it defaults to 
     * {@link SVNRevision#WORKING}.
     * 
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#EMPTY}, fetch the property from <code>path</code> only; 
     * if {@link SVNDepth#FILES}, fetch from <code>path</code> and its file children (if any); 
     * if {@link SVNDepth#IMMEDIATES}, from <code>path</code> and all of its immediate children (both files and 
     * directories); if {@link SVNDepth#INFINITY}, from <code>path</code> and everything beneath it.
     * 
     * <p/>
     * <code>changeLists</code> is a collection of <code>String</tt> changelist
     * names, used as a restrictive filter on items whose properties are
     * set; that is, don't set properties on any item unless it's a member
     * of one of those changelists.  If <code>changeLists</code> is empty (or
     * <span class="javakeyword">null</span>), no changelist filtering occurs.
     *
     * @param path          a WC item's path
     * @param propName      an item's property name; if it's
     *                      <span class="javakeyword">null</span> then
     *                      all the item's properties will be retrieved
     *                      and passed to <code>handler</code> for
     *                      processing
     * @param pegRevision   a revision in which the item is first looked up
     * @param revision      a target revision
     * @param depth         tree depth 
     * @param handler       a caller's property handler
     * @param changeLists   collection of changelist names
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>propName</code> starts
     *                      with the {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX} prefix
     *                      <li><code>path</code> is not under version control
     *                      </ul>
     * @since               1.2, SVN 1.5
     */
    public void doGetProperty(File path, String propName, SVNRevision pegRevision, SVNRevision revision, 
            SVNDepth depth, ISVNPropertyHandler handler, Collection changeLists) throws SVNException {
        if (SVNProperty.isWorkingCopyProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, 
                    "''{0}'' is a wcprop, thus not accessible to clients", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (SVNProperty.isEntryProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "Property ''{0}'' is an entry property", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }         
        if (depth == null || depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.EMPTY;
        }

        if ((revision != SVNRevision.WORKING && revision != SVNRevision.BASE && revision != SVNRevision.COMMITTED && 
                revision != SVNRevision.UNDEFINED) || (pegRevision != SVNRevision.WORKING && 
                        pegRevision != SVNRevision.BASE && pegRevision != SVNRevision.COMMITTED && 
                        pegRevision != SVNRevision.UNDEFINED)) {
            long[] revNum = { SVNRepository.INVALID_REVISION };
            SVNRepository repository = createRepository(null, path, null, pegRevision, revision, revNum);
            revision = SVNRevision.create(revNum[0]);
            doGetRemoteProperty(repository.getLocation(), "", repository, propName, revision, depth, handler);
        } else {
            SVNWCAccess wcAccess = createWCAccess();
            try {
                int admDepth = getLevelsToLockFromDepth(depth);
                SVNAdminArea area = wcAccess.probeOpen(path, false, admDepth);
                SVNEntry entry = wcAccess.getVersionedEntry(path, false);
                boolean base = revision == SVNRevision.BASE || revision == SVNRevision.COMMITTED;
                doGetLocalProperty(entry, area, propName, base, handler, depth, changeLists);
            } finally {
                wcAccess.close();
            }
        }
    }

    /**
     * Gets an item's versioned property from a repository and passes it to
     * a provided property handler. This method is useful when having no
     * Working Copy at all.
     *
     * @param url         an item's repository location
     * @param propName    an item's property name; if it's
     *                    <span class="javakeyword">null</span> then
     *                    all the item's properties will be retrieved
     *                    and passed to <code>handler</code> for
     *                    processing
     * @param pegRevision a revision in which the item is first looked up
     * @param revision    a target revision
     * @param recursive   <span class="javakeyword">true</span> to
     *                    descend recursively
     * @param handler     a caller's property handler
     * @throws SVNException if <code>propName</code> starts
     *                      with the {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                      svn:wc:} prefix
     * @deprecated use {@link #doGetProperty(SVNURL, String, SVNRevision, SVNRevision, SVNDepth, ISVNPropertyHandler)} instead
     */
    public void doGetProperty(SVNURL url, String propName, SVNRevision pegRevision, SVNRevision revision, 
            boolean recursive, ISVNPropertyHandler handler) throws SVNException {
        doGetProperty(url, propName, pegRevision, revision, SVNDepth.getInfinityOrEmptyDepth(recursive), handler);
    }

    /**
     * Invokes <code>handler</code> on paths covered by <code>depth</code> starting with 
     * the specified <code>path</code>.
     * 
     * <p/>
     * If <code></code> is {@link SVNRevision#UNDEFINED} then get
     * properties from the repository head. Else get the properties as of <code>revision</code>. 
     * The actual node revision selected is determined by the path as it exists in <code>pegRevision</code>.
     * If <code>pegRevision</code> is {@link SVNRevision#UNDEFINED}, then
     * it defaults to {@link SVNRevision#HEAD}.
     * 
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#EMPTY}, fetch the property from <code>path</code> only; 
     * if {@link SVNDepth#FILES}, fetch from <code>path</code> and its file children (if any); 
     * if {@link SVNDepth#IMMEDIATES}, from <code>path</code> and all of its immediate children (both files and 
     * directories); if {@link SVNDepth#INFINITY}, from <code>path</code> and everything beneath it.
     * 
     * @param url           versioned item url
     * @param propName      an item's property name; if it's
     *                      <span class="javakeyword">null</span> then
     *                      all the item's properties will be retrieved
     *                      and passed to <code>handler</code> for
     *                      processing
     * @param pegRevision   a revision in which the item is first looked up
     * @param revision      a target revision
     * @param depth         tree depth 
     * @param handler       a caller's property handler
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>propName</code> starts
     *                      with the {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX} prefix
     *                      <li><code>path</code> is not under version control
     *                      </ul>
     * @since               1.2, SVN 1.5
     */
    public void doGetProperty(SVNURL url, String propName, SVNRevision pegRevision, SVNRevision revision, 
            SVNDepth depth, ISVNPropertyHandler handler) throws SVNException {
        if (SVNProperty.isWorkingCopyProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, 
                    "''{0}'' is a wcprop, thus not accessible to clients", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (SVNProperty.isEntryProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "Property ''{0}'' is an entry property", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }         
        long[] pegRev = new long[]{-1};
        SVNRepository repos = createRepository(url, null, null, pegRevision, revision, pegRev);
        revision = pegRev[0] < 0 ? revision : SVNRevision.create(pegRev[0]);
        doGetRemoteProperty(url, "", repos, propName, revision, depth, handler);
    }

    /**
     * Gets an unversioned revision property from a repository (getting
     * a repository URL from a Working Copy) and passes it to a provided
     * property handler.
     *
     * @param path     a local Working Copy item which repository
     *                 location is used to connect to a repository
     * @param propName a revision property name; if this parameter
     *                 is <span class="javakeyword">null</span> then
     *                 all the revision properties will be retrieved
     *                 and passed to <code>handler</code> for
     *                 processing
     * @param revision a revision which property is to be retrieved
     * @param handler  a caller's property handler
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>revision</code> is invalid
     *                      <li><code>propName</code> starts with the
     *                      {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                      svn:wc:} prefix
     *                      </ul>
     * @see #doGetRevisionProperty(SVNURL,String,SVNRevision,ISVNPropertyHandler)
     */
    public void doGetRevisionProperty(File path, String propName, SVNRevision revision, ISVNPropertyHandler handler) throws SVNException {
        if (SVNProperty.isWorkingCopyProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, 
                    "''{0}'' is a wcprop, thus not accessible to clients", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (SVNProperty.isEntryProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "Property ''{0}'' is an entry property", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }         
        if (!revision.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Valid revision have to be specified to fetch revision property");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        SVNRepository repository = createRepository(null, path, null, SVNRevision.UNDEFINED, revision, null);
        long revisionNumber = getRevisionNumber(revision, repository, path);
        doGetRevisionProperty(repository, propName, revisionNumber, handler);
    }

    /**
     * Gets an unversioned revision property from a repository and passes
     * it to a provided property handler.
     *
     * @param url           a URL pointing to a repository location
     *                      which revision property is to be got
     * @param propName      a revision property name; if this parameter
     *                      is <span class="javakeyword">null</span> then
     *                      all the revision properties will be retrieved
     *                      and passed to <code>handler</code> for
     *                      processing
     * @param revision      a revision which property is to be retrieved
     * @param handler       a caller's property handler
     * @return              actual revision number to which <code>revision</code> is resolved
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>revision</code> is invalid
     *                      <li><code>propName</code> starts with the
     *                      {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                      svn:wc:} prefix
     *                      </ul>
     * @see #doGetRevisionProperty(File,String,SVNRevision,ISVNPropertyHandler)
     */
    public long doGetRevisionProperty(SVNURL url, String propName, SVNRevision revision, 
            ISVNPropertyHandler handler) throws SVNException {
        if (SVNProperty.isWorkingCopyProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, 
                    "''{0}'' is a wcprop, thus not accessible to clients", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (SVNProperty.isEntryProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "Property ''{0}'' is an entry property", propName);
            SVNErrorManager.error(err, SVNLogType.WC);
        }         
        
        if (!revision.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                    "Valid revision have to be specified to fetch revision property");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        SVNRepository repos = createRepository(url, null, null, true);
        long revNumber = getRevisionNumber(revision, repos, null);
        doGetRevisionProperty(repos, propName, revNumber, handler);
        return revNumber;
    }

    /**
     * Schedules a Working Copy item for deletion.
     * This method is equivalent to <code>doDelete(path, force, true, dryRun)</code>.
     * 
     * @param path          a WC item to be deleted
     * @param force         <span class="javakeyword">true</span> to
     *                      force the operation to run
     * @param dryRun        <span class="javakeyword">true</span> only to
     *                      try the delete operation without actual deleting
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>path</code> is not under version control
     *                      <li>can not delete <code>path</code> without forcing
     *                      </ul>
     * @see #doDelete(File,boolean,boolean,boolean)
     */
    public void doDelete(File path, boolean force, boolean dryRun) throws SVNException {
        doDelete(path, force, true, dryRun);
    }

    /**
     * Schedules a Working Copy item for deletion. This method allows to
     * choose - whether file item(s) are to be deleted from the filesystem or
     * not. Another version of the {@link #doDelete(File,boolean,boolean) doDelete()}
     * method is similar to the corresponding SVN client's command - <code>'svn delete'</code>
     * as it always deletes files from the filesystem.
     * 
     * <p/>
     * This method deletes only local working copy paths without connecting to the repository.
     *
     * @param path        a WC item to be deleted
     * @param force       <span class="javakeyword">true</span> to
     *                    force the operation to run
     * @param deleteFiles if <span class="javakeyword">true</span> then
     *                    files will be scheduled for deletion as well as
     *                    deleted from the filesystem, otherwise files will
     *                    be only scheduled for addition and still be present
     *                    in the filesystem
     * @param dryRun      <span class="javakeyword">true</span> only to
     *                    try the delete operation without actual deleting
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>path</code> is not under version control
     *                      <li>can not delete <code>path</code> without forcing
     *                      </ul>
     */
    public void doDelete(File path, boolean force, boolean deleteFiles, boolean dryRun) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        path = path.getAbsoluteFile();
        try {
            if (!force && deleteFiles) {
                SVNWCManager.canDelete(path, getOptions(), this);
            }
            SVNAdminArea root = wcAccess.open(path.getParentFile(), true, 0);
            if (!dryRun) {
                SVNWCManager.delete(wcAccess, root, path, deleteFiles, true);
            }
        } finally {
            wcAccess.close();
        }
    }

    /**
     * Schedules an unversioned item for addition to a repository thus
     * putting it under version control.
     * <p/>
     * <p/>
     * To create and add to version control a new directory, set <code>mkdir</code>
     * to <span class="javakeyword">true</span>.
     * <p/>
     * <p/>
     * Calling this method is equivalent to
     * <code>doAdd(path, force, mkdir, climbUnversionedParents, recursive, false)</code>.
     *
     * @param path                    a path to be put under version
     *                                control (will be added to a repository
     *                                in next commit)
     * @param force                   when <span class="javakeyword">true</span> forces the operation
     *                                to run on already versioned files or directories without reporting
     *                                error. When ran recursively, all unversioned files and directories
     *                                in a tree will be scheduled for addition.
     * @param mkdir                   if <span class="javakeyword">true</span> -
     *                                creates a new directory and schedules it for
     *                                addition
     * @param climbUnversionedParents if <span class="javakeyword">true</span> and
     *                                <code>path</code> is located in an unversioned
     *                                parent directory then the parent will be automatically
     *                                scheduled for addition, too
     * @param recursive               <span class="javakeyword">true</span> to
     *                                descend recursively (relevant for directories)
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>path</code> doesn't belong
     *                      to a Working Copy
     *                      <li><code>path</code> doesn't exist and
     *                      <code>mkdir</code> is <span class="javakeyword">false</span>
     *                      <li><code>path</code> is the root directory of the Working Copy
     * @deprecated use {@link #doAdd(File, boolean, boolean, boolean, SVNDepth, boolean, boolean)} instead
     */
    public void doAdd(File path, boolean force, boolean mkdir, boolean climbUnversionedParents, 
            boolean recursive) throws SVNException {
        SVNDepth depth = SVNDepth.getInfinityOrEmptyDepth(recursive);
        doAdd(path, force, mkdir, climbUnversionedParents, depth, false, false, 
                climbUnversionedParents);
    }

    /**
     * Schedules an unversioned item for addition to a repository thus
     * putting it under version control.
     * <p/>
     * <p/>
     * To create and add to version control a new directory, set <code>mkdir</code>
     * to <span class="javakeyword">true</span>.
     *
     * @param path                    a path to be put under version
     *                                control (will be added to a repository
     *                                in next commit)
     * @param force                   when <span class="javakeyword">true</span> forces the operation
     *                                to run on already versioned files or directories without reporting
     *                                error. When ran recursively, all unversioned files and directories
     *                                in a tree will be scheduled for addition.
     * @param mkdir                   if <span class="javakeyword">true</span> -
     *                                creates a new directory and schedules it for
     *                                addition
     * @param climbUnversionedParents if <span class="javakeyword">true</span> and
     *                                <code>path</code> is located in an unversioned
     *                                parent directory then the parent will be automatically
     *                                scheduled for addition, too
     * @param recursive               <span class="javakeyword">true</span> to
     *                                descend recursively (relevant for directories)
     * @param includeIgnored          controls whether ignored items must be also added
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>path</code> doesn't belong
     *                      to a Working Copy
     *                      <li><code>path</code> doesn't exist and
     *                      <code>mkdir</code> is <span class="javakeyword">false</span>
     *                      <li><code>path</code> is the root directory of the Working Copy
     *                      </ul>
     * @since 1.1
     * @deprecated use {@link #doAdd(File, boolean, boolean, boolean, SVNDepth, boolean, boolean)} instead
     */
    public void doAdd(File path, boolean force, boolean mkdir, boolean climbUnversionedParents, 
            boolean recursive, boolean includeIgnored) throws SVNException {
        SVNDepth depth = SVNDepth.getInfinityOrEmptyDepth(recursive);
        doAdd(path, force, mkdir, climbUnversionedParents, depth, false,
                includeIgnored, climbUnversionedParents);
    }

    /**
     * Schedules a working copy <code>path</code> for addition to the repository.
     *
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#EMPTY}, adds just <code>path</code> and nothing
     * below it. If {@link SVNDepth#FILES}, adds <code>path</code> and any file
     * children of <code>path</code>. If {@link SVNDepth#IMMEDIATES}, adds <code>path</code>, any
     * file children, and any immediate subdirectories (but nothing
     * underneath those subdirectories). If {@link SVNDepth#INFINITY}, adds
     * <code>path</code> and everything under it fully recursively.
     *
     * <p/>
     * <code>path</code>'s parent must be under revision control already (unless
     * <code>makeParents</code> is <span class="javakeyword">true</span>), but <code>path</code> is not.  
     * 
     * <p/>
     * If <code>force</code> is set, <code>path</code> is a directory, <code>depth</code> is 
     * {@link SVNDepth#INFINITY}, then schedules for addition unversioned files and directories
     * scattered deep within a versioned tree.
     *
     * <p/>
     * If <code>includeIgnored</code> is <span class="javakeyword">false</span>, doesn't add files or 
     * directories that match ignore patterns.
     *
     * <p/>
     * If <code>makeParents</code> is <span class="javakeyword">true</span>, recurse up <code>path</code>'s 
     * directory and look for a versioned directory. If found, add all intermediate paths between it
     * and <code>path</code>. 
     *
     * <p/>
     * Important: this is a *scheduling* operation.  No changes will happen to the repository until a commit 
     * occurs. This scheduling can be removed with a call to {@link #doRevert(File[], SVNDepth, Collection)}.
     * 
     * @param path                      working copy path
     * @param force                     if <span class="javakeyword">true</span>, this method does not throw exceptions 
     *                                  on already-versioned items 
     * @param mkdir                     if <span class="javakeyword">true</span>, create a directory also at <code>path</code>
     * @param climbUnversionedParents   not used; make use of <code>makeParents</code> instead
     * @param depth                     tree depth
     * @param includeIgnored            if <span class="javakeyword">true</span>, does not apply ignore patterns 
     *                                  to paths being added
     * @param makeParents               if <span class="javakeyword">true</span>, climb upper and schedule also
     *                                  all unversioned paths in the way
     * @throws SVNException             <ul>
     *                                  <li/>exception with {@link SVNErrorCode#ENTRY_EXISTS} error code -  
     *                                  if <code>force</code> is not set and <code>path</code> is already 
     *                                  under version
     *                                  <li/>exception with {@link SVNErrorCode#CLIENT_NO_VERSIONED_PARENT} 
     *                                  error code - if <code>makeParents</code> is 
     *                                  <span class="javakeyword">true</span> but no unversioned paths stepping 
     *                                  upper from <code>path</code> are found 
     * @since 1.2, SVN 1.5
     */
    public void doAdd(File path, boolean force, boolean mkdir, boolean climbUnversionedParents, 
            SVNDepth depth, boolean includeIgnored, boolean makeParents) throws SVNException {
        doAdd(path, force, mkdir, climbUnversionedParents, depth, false, includeIgnored, makeParents);
    }

    /**
     * Schedules working copy <code>paths</code> for addition to the repository.
     *
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#EMPTY}, adds just <code>paths</code> and nothing
     * below it. If {@link SVNDepth#FILES}, adds <code>paths</code> and any file
     * children of <code>paths</code>. If {@link SVNDepth#IMMEDIATES}, adds <code>paths</code>, any
     * file children, and any immediate subdirectories (but nothing
     * underneath those subdirectories). If {@link SVNDepth#INFINITY}, adds
     * <code>paths</code> and everything under it fully recursively.
     *
     * <p/>
     * <code>paths</code>' parent must be under revision control already (unless
     * <code>makeParents</code> is <span class="javakeyword">true</span>), but <code>paths</code> are not.  
     * 
     * <p/>
     * If <code>force</code> is set, path is a directory, <code>depth</code> is 
     * {@link SVNDepth#INFINITY}, then schedules for addition unversioned files and directories
     * scattered deep within a versioned tree.
     *
     * <p/>
     * If <code>includeIgnored</code> is <span class="javakeyword">false</span>, doesn't add files or 
     * directories that match ignore patterns.
     *
     * <p/>
     * If <code>makeParents</code> is <span class="javakeyword">true</span>, recurse up path's 
     * directory and look for a versioned directory. If found, add all intermediate paths between it
     * and the path. 
     *
     * <p/>
     * Important: this is a *scheduling* operation. No changes will happen to the repository until a commit 
     * occurs. This scheduling can be removed with a call to {@link #doRevert(File[], SVNDepth, Collection)}.
     * 
     * @param paths                     working copy paths to add  
     * @param force                     if <span class="javakeyword">true</span>, this method does not throw exceptions 
     *                                  on already-versioned items 
     * @param mkdir                     if <span class="javakeyword">true</span>, create a directory also at <code>path</code>
     * @param climbUnversionedParents   not used; make use of <code>makeParents</code> instead
     * @param depth                     tree depth
     * @param depthIsSticky             if depth should be recorded to the working copy
     * @param includeIgnored            if <span class="javakeyword">true</span>, does not apply ignore patterns 
     *                                  to paths being added
     * @param makeParents               if <span class="javakeyword">true</span>, climb upper and schedule also
     *                                  all unversioned paths in the way
     * @throws SVNException             <ul>
     *                                  <li/>exception with {@link SVNErrorCode#ENTRY_EXISTS} error code -  
     *                                  if <code>force</code> is not set and a path is already 
     *                                  under version
     *                                  <li/>exception with {@link SVNErrorCode#CLIENT_NO_VERSIONED_PARENT} 
     *                                  error code - if <code>makeParents</code> is 
     *                                  <span class="javakeyword">true</span> but no unversioned paths stepping 
     *                                  upper from a path are found 
     * @since 1.3
     */
    public void doAdd(File[] paths, boolean force, boolean mkdir, boolean climbUnversionedParents, 
            SVNDepth depth, boolean depthIsSticky, boolean includeIgnored, boolean makeParents) throws SVNException {
        setEventPathPrefix("");
        try {
            if (paths != null) {
                for (int i = 0; i < paths.length; i++) {
                    doAdd(paths[i], force, mkdir, climbUnversionedParents,depth, depthIsSticky, includeIgnored, makeParents);
                }
            }
        } finally {
            setEventPathPrefix(null);
        }
    }
    
    /**
     * Schedules a working copy <code>path</code> for addition to the repository.
     *
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#EMPTY}, adds just <code>path</code> and nothing
     * below it. If {@link SVNDepth#FILES}, adds <code>path</code> and any file
     * children of <code>path</code>. If {@link SVNDepth#IMMEDIATES}, adds <code>path</code>, any
     * file children, and any immediate subdirectories (but nothing
     * underneath those subdirectories). If {@link SVNDepth#INFINITY}, adds
     * <code>path</code> and everything under it fully recursively.
     *
     * <p/>
     * <code>path</code>'s parent must be under revision control already (unless
     * <code>makeParents</code> is <span class="javakeyword">true</span>), but <code>path</code> is not.  
     * 
     * <p/>
     * If <code>force</code> is set, <code>path</code> is a directory, <code>depth</code> is 
     * {@link SVNDepth#INFINITY}, then schedules for addition unversioned files and directories
     * scattered deep within a versioned tree.
     *
     * <p/>
     * If <code>includeIgnored</code> is <span class="javakeyword">false</span>, doesn't add files or 
     * directories that match ignore patterns.
     *
     * <p/>
     * If <code>makeParents</code> is <span class="javakeyword">true</span>, recurse up <code>path</code>'s 
     * directory and look for a versioned directory. If found, add all intermediate paths between it
     * and <code>path</code>. 
     *
     * <p/>
     * Important: this is a *scheduling* operation.  No changes will happen to the repository until a commit 
     * occurs. This scheduling can be removed with a call to {@link #doRevert(File[], SVNDepth, Collection)}.
     * 
     * @param path                      working copy path
     * @param force                     if <span class="javakeyword">true</span>, this method does not throw exceptions on already-versioned items 
     * @param mkdir                     if <span class="javakeyword">true</span>, create a directory also at <code>path</code>
     * @param climbUnversionedParents   not used; make use of <code>makeParents</code> instead
     * @param depth                     tree depth
     * @param depthIsSticky             if depth should be recorded to the working copy
     * @param includeIgnored            if <span class="javakeyword">true</span>, does not apply ignore patterns 
     *                                  to paths being added
     * @param makeParents               if <span class="javakeyword">true</span>, climb upper and schedule also
     *                                  all unversioned paths in the way
     * @throws SVNException             <ul>
     *                                  <li/>exception with {@link SVNErrorCode#ENTRY_EXISTS} error code -  
     *                                  if <code>force</code> is not set and <code>path</code> is already 
     *                                  under version
     *                                  <li/>exception with {@link SVNErrorCode#CLIENT_NO_VERSIONED_PARENT} 
     *                                  error code - if <code>makeParents</code> is 
     *                                  <span class="javakeyword">true</span> but no unversioned paths stepping 
     *                                  upper from <code>path</code> are found 
     * @since 1.3
     */
    public void doAdd(File path, boolean force, boolean mkdir, boolean climbUnversionedParents, 
            SVNDepth depth, boolean depthIsSticky, boolean includeIgnored, boolean makeParents) throws SVNException {
        depth = depth == null ? SVNDepth.UNKNOWN : depth;
        path = path.getAbsoluteFile();
        if (!mkdir && makeParents && path.getParentFile() != null) {
            SVNWCAccess wcAccess = createWCAccess();
            try {
                addParentDirectories(wcAccess, path.getParentFile());
            } finally {
                wcAccess.close();
            }
        }
        
        SVNFileType kind = SVNFileType.getType(path);
        if (force && mkdir && kind == SVNFileType.DIRECTORY) {
            // directory is already there.
            doAdd(path, force, false, true, SVNDepth.EMPTY, depthIsSticky, true, makeParents);
            return;
        } else if (mkdir) {
            // attempt to create dir
            File parent = path;
            File firstCreated = path;
            while (parent != null && SVNFileType.getType(parent) == SVNFileType.NONE) {
                if (!parent.equals(path) && !makeParents) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                            "Cannot create directoy ''{0}'' with non-existent parents", path);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                firstCreated = parent;
                parent = parent.getParentFile();
            }
            boolean created = path.mkdirs();
            if (!created) {
                // delete created dirs.
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                        "Cannot create new directory ''{0}''", path);
                while (parent == null ? path != null : !path.equals(parent)) {
                    SVNFileUtil.deleteAll(path, true);
                    path = path.getParentFile();
                }
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            try {
                doAdd(firstCreated, false, false, climbUnversionedParents, depth, depthIsSticky, true, makeParents);
            } catch (SVNException e) {
                SVNFileUtil.deleteAll(firstCreated, true);
                throw e;
            }
            return;
        }
        
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminArea dir = null;
            SVNFileType fileType = SVNFileType.getType(path);
            if (fileType == SVNFileType.DIRECTORY) {
                dir = wcAccess.open(SVNWCUtil.isVersionedDirectory(path.getParentFile()) ? path.getParentFile() : path, true, 0);
            } else {
                // files and symlink goes here.
                dir = wcAccess.open(path.getParentFile(), true, 0);
            }
            if (fileType == SVNFileType.DIRECTORY && depth.compareTo(SVNDepth.FILES) >= 0) {
                File wcRoot = SVNWCUtil.getWorkingCopyRoot(dir.getRoot(), true);
                addDirectory(wcRoot, path, dir, force, includeIgnored, depth, depthIsSticky);
            } else if (fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK) {
                addFile(path, fileType, dir);
            } else {
                SVNWCManager.add(path, dir, null, SVNRevision.UNDEFINED, depthIsSticky ? depth : null);
            }
        } catch (SVNException e) {
            if (!(force && e.getErrorMessage().getErrorCode() == SVNErrorCode.ENTRY_EXISTS)) {
                throw e;
            }
        } finally {
            wcAccess.close();
        }
    }

    /**
     * Schedules <code>path</code> as being replaced.
     * This method does not perform any deletion\addition in the filesysem nor does it require a connection to 
     * the repository. It just marks the current <code>path</code> item as being replaced.  
     * 
     * @param  path working copy path to mark as
     * @throws SVNException
     * @since 1.2 
     */
    public void doMarkReplaced(File path) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        path = path.getAbsoluteFile();
        try {
            SVNAdminAreaInfo areaInfo = wcAccess.openAnchor(path, true, SVNWCAccess.INFINITE_DEPTH);
            SVNAdminArea anchor = areaInfo.getAnchor();
            if (path.equals(anchor.getRoot().getAbsoluteFile())) {
                SVNWCManager.markTree(anchor, SVNProperty.SCHEDULE_REPLACE, false, false, SVNWCManager.SCHEDULE);
            } else {
                SVNEntry entry = anchor.getEntry(path.getName(), false);
                SVNWCManager.markEntry(anchor, entry, SVNProperty.SCHEDULE_REPLACE, false, false, SVNWCManager.SCHEDULE);
            }
            anchor.saveEntries(false);
        } finally {
            wcAccess.close();
        }
    }

    /**
     * Reverts all local changes made to a Working Copy item(s) thus
     * bringing it to a 'pristine' state.
     *
     * @param path      a WC path to perform a revert on
     * @param recursive <span class="javakeyword">true</span> to
     *                  descend recursively (relevant for directories)
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>path</code> is not under version control
     *                      <li>when trying to revert an addition of a directory
     *                      from within the directory itself
     *                      </ul>
     * @see #doRevert(File[],boolean)
     * @deprecated use {@link #doRevert(File[], SVNDepth, Collection)}
     */
    public void doRevert(File path, boolean recursive) throws SVNException {
        doRevert(new File[] { path }, SVNDepth.getInfinityOrEmptyDepth(recursive), null);
    }

    /**
     * Reverts all local changes made to a Working Copy item(s) thus
     * bringing it to a 'pristine' state.
     *
     * @param paths     a WC paths to perform a revert on
     * @param recursive <span class="javakeyword">true</span> to
     *                  descend recursively (relevant for directories)
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>path</code> is not under version control
     *                      <li>when trying to revert an addition of a directory
     *                      from within the directory itself
     *                      </ul>
     *                      <p/>
     *                      Exception will not be thrown if there are multiple paths passed.
     *                      Instead caller should process events received by <code>ISVNEventHandler</code>
     *                      instance to get information on whether certain path was reverted or not.
     * @deprecated use {@link #doRevert(File[], SVNDepth, Collection)} instead
     */
    public void doRevert(File[] paths, boolean recursive) throws SVNException {
        doRevert(paths, recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY, null);
    }

    /**
     * Restores the pristine version of working copy <code>paths</code>,
     * effectively undoing any local mods. For each path in <code>paths</code>,
     * reverts it if it is a file. Else if it is a directory, reverts
     * according to <code>depth</code>:
     * 
     * <p/>
     * If </code>depth</code> is {@link SVNDepth#EMPTY}, reverts just the properties on
     * the directory; else if {@link SVNDepth#FILES}, reverts the properties
     * and any files immediately under the directory; else if
     * {@link SVNDepth#IMMEDIATES}, reverts all of the preceding plus
     * properties on immediate subdirectories; else if {@link SVNDepth#INFINITY},
     * reverts path and everything under it fully recursively.
     *
     * <p/>
     * <code>changeLists</code> is a collection of <code>String</code> changelist
     * names, used as a restrictive filter on items reverted; that is,
     * doesn't revert any item unless it's a member of one of those
     * changelists.  If <code>changeLists</code> is empty (or <span class="javakeyword">null</span>),
     * no changelist filtering occurs.
     * 
     * <p/>
     * If an item specified for reversion is not under version control,
     * then does not fail with an exception, just invokes {@link ISVNEventHandler} 
     * using notification code {@link SVNEventAction#SKIP}.
     * 
     * @param paths           working copy paths to revert
     * @param depth           tree depth
     * @param changeLists     collection with changelist names
     * @throws SVNException 
     * @since 1.2, SVN 1.5
     */
    public void doRevert(File[] paths, SVNDepth depth, Collection changeLists) throws SVNException {
        boolean reverted = false;
        try {
            for (int i = 0; i < paths.length; i++) {
                File path = paths[i];
                path = path.getAbsoluteFile();
                SVNWCAccess wcAccess = createWCAccess();
                try {
                    int admLockLevel = getLevelsToLockFromDepth(depth);
                    SVNAdminAreaInfo info = wcAccess.openAnchor(path, true, admLockLevel);
                    SVNEntry entry = wcAccess.getEntry(path, false);
                    if (entry != null && entry.isDirectory() && entry.isScheduledForAddition()) {
                        if (depth != SVNDepth.INFINITY) {
                            getDebugLog().logFine(SVNLogType.WC, 
                                    "Forcing revert on path '" + path + "' to recurse");
                            depth = SVNDepth.INFINITY;
                            wcAccess.close();
                            info = wcAccess.openAnchor(path, true, SVNWCAccess.INFINITE_DEPTH);
                        }
                    }

                    boolean useCommitTimes = getOptions().isUseCommitTimes();
                    reverted |= doRevert(path, info.getAnchor(), depth, useCommitTimes, changeLists);
                } catch (SVNException e) {
                    reverted |= true;
                    SVNErrorCode code = e.getErrorMessage().getErrorCode();
                    if (code == SVNErrorCode.ENTRY_NOT_FOUND || code == SVNErrorCode.UNVERSIONED_RESOURCE) {
                        SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, SVNEventAction.SKIP, SVNEventAction.REVERT, null, null);
                        dispatchEvent(event);
                        continue;
                    } 
                    throw e;
                } finally {
                    wcAccess.close();
                }
            }
        } finally {
            if (reverted) {
                sleepForTimeStamp();
            }
        }
    }

    /**
     * Resolves a 'conflicted' state on a Working Copy item.
     *
     * @param path      a WC item to be resolved
     * @param recursive <span class="javakeyword">true</span> to
     *                  descend recursively (relevant for directories) - this
     *                  will resolve the entire tree
     * @throws SVNException if <code>path</code> is not under version control
     * @deprecated use {@link #doResolve(File, SVNDepth, SVNConflictChoice)} instead
     */
    public void doResolve(File path, boolean recursive) throws SVNException {
        doResolve(path, SVNDepth.fromRecurse(recursive), SVNConflictChoice.MERGED);
    }

    /**
     * Performs automatic conflict resolution on a working copy <code>path</code>.
     * 
     * <p/> 
     * If <code>depth</code> is {@link SVNDepth#EMPTY}, acts only on <code>path</code>; if
     * {@link SVNDepth#FILES}, resolves <code>path</code> and its conflicted file
     * children (if any); if {@link SVNDepth#IMMEDIATES}, resolves <code>path</code> and
     * all its immediate conflicted children (both files and directories,
     * if any); if {@link SVNDepth#INFINITY}, resolves <code>path</code> and every
     * conflicted file or directory anywhere beneath it.
     * 
     * <p/>
     * If <code>conflictChoice</code> is {@link SVNConflictChoice#BASE}, resolves the
     * conflict with the old file contents; if {@link SVNConflictChoice#MINE_FULL}, uses the original 
     * working contents; if {@link SVNConflictChoice#THEIRS_FULL}, the new contents; and if
     * {@link SVNConflictChoice#MERGED}, doesn't change the contents at all, just removes the conflict status, 
     * which is the pre-1.2 (pre-SVN 1.5) behavior.
     *
     * <p/>
     * {@link SVNConflictChoice#THEIRS_CONFLICT} and {@link SVNConflictChoice#MINE_CONFLICT} are not legal for 
     * binary files or properties.
     *
     * <p/>
     * If <code>path</code> is not in a state of conflict to begin with, does nothing. If 
     * <code>path</code>'s conflict state is removed and caller's {@link ISVNEntryHandler} is not 
     * <span class="javakeyword">null</span>, then an {@link SVNEventAction#RESOLVED} event is 
     * dispatched to the handler.
     * 
     * <p/>
     * This is equivalent to calling <code>doResolve(path, depth, true, true, conflictChoice)</code>.
     * @param path               working copy path
     * @param depth              tree depth
     * @param conflictChoice     choice object for making decision while resolving
     * @throws SVNException  
     * @since 1.2, SVN 1.5
     */
    public void doResolve(File path, SVNDepth depth, SVNConflictChoice conflictChoice) throws SVNException {
        doResolve(path, depth, true, true, conflictChoice);
    }

    /**
     * Performs automatic conflict resolution on a working copy <code>path</code>.
     * 
     * <p/> 
     * If <code>depth</code> is {@link SVNDepth#EMPTY}, acts only on <code>path</code>; if
     * {@link SVNDepth#FILES}, resolves <code>path</code> and its conflicted file
     * children (if any); if {@link SVNDepth#IMMEDIATES}, resolves <code>path</code> and
     * all its immediate conflicted children (both files and directories,
     * if any); if {@link SVNDepth#INFINITY}, resolves <code>path</code> and every
     * conflicted file or directory anywhere beneath it.
     * 
     * <p/>
     * If <code>conflictChoice</code> is {@link SVNConflictChoice#BASE}, resolves the
     * conflict with the old file contents; if {@link SVNConflictChoice#MINE_FULL}, uses the original 
     * working contents; if {@link SVNConflictChoice#THEIRS_FULL}, the new contents; and if
     * {@link SVNConflictChoice#MERGED}, doesn't change the contents at all, just removes the conflict status, 
     * which is the pre-1.2 (pre-SVN 1.5) behavior.
     *
     * <p/>
     * {@link SVNConflictChoice#THEIRS_CONFLICT} and {@link SVNConflictChoice#MINE_CONFLICT} are not legal for 
     * binary files or properties.
     *
     * <p/>
     * If <code>path</code> is not in a state of conflict to begin with, does nothing. If 
     * <code>path</code>'s conflict state is removed and caller's {@link ISVNEntryHandler} is not 
     * <span class="javakeyword">null</span>, then an {@link SVNEventAction#RESOLVED} event is 
     * dispatched to the handler.
     * 
     * @param path               working copy path
     * @param depth              tree depth
     * @param resolveContents    resolve content conflict
     * @param resolveProperties  resolve property conflict
     * @param conflictChoice     choice object for making decision while resolving
     * @throws SVNException  
     * @since 1.2, SVN 1.5
     */
    public void doResolve(File path, SVNDepth depth, final boolean resolveContents, final boolean resolveProperties, 
            SVNConflictChoice conflictChoice) throws SVNException {
        doResolve(path, depth, resolveContents, resolveProperties, true, conflictChoice);
    }

    /**
     * Performs automatic conflict resolution on a working copy <code>path</code>.
     * 
     * <p/> 
     * If <code>depth</code> is {@link SVNDepth#EMPTY}, acts only on <code>path</code>; if
     * {@link SVNDepth#FILES}, resolves <code>path</code> and its conflicted file
     * children (if any); if {@link SVNDepth#IMMEDIATES}, resolves <code>path</code> and
     * all its immediate conflicted children (both files and directories,
     * if any); if {@link SVNDepth#INFINITY}, resolves <code>path</code> and every
     * conflicted file or directory anywhere beneath it.
     * 
     * <p/>
     * If <code>conflictChoice</code> is {@link SVNConflictChoice#BASE}, resolves the
     * conflict with the old file contents; if {@link SVNConflictChoice#MINE_FULL}, uses the original 
     * working contents; if {@link SVNConflictChoice#THEIRS_FULL}, the new contents; and if
     * {@link SVNConflictChoice#MERGED}, doesn't change the contents at all, just removes the conflict status, 
     * which is the pre-1.2 (pre-SVN 1.5) behavior.
     *
     * <p/>
     * {@link SVNConflictChoice#THEIRS_CONFLICT} and {@link SVNConflictChoice#MINE_CONFLICT} are not legal for 
     * binary files or properties.
     *
     * <p/>
     * If <code>path</code> is not in a state of conflict to begin with, does nothing. If 
     * <code>path</code>'s conflict state is removed and caller's {@link ISVNEntryHandler} is not 
     * <span class="javakeyword">null</span>, then an {@link SVNEventAction#RESOLVED} event is 
     * dispatched to the handler.
     * 
     * @param path               working copy path
     * @param depth              tree depth
     * @param resolveContents    resolve content conflict
     * @param resolveProperties  resolve property conflict
     * @param resolveTree n      resolve any tree conlicts
     * @param conflictChoice     choice object for making decision while resolving
     * @throws SVNException  
     * @since 1.3, SVN 1.6
     */
    public void doResolve(File path, SVNDepth depth, final boolean resolveContents, final boolean resolveProperties, 
            final boolean resolveTree, SVNConflictChoice conflictChoice) throws SVNException {
        final SVNConflictChoice choice = conflictChoice == null ? SVNConflictChoice.MERGED : conflictChoice;
        path = path.getAbsoluteFile();
        final SVNWCAccess wcAccess = createWCAccess();
        int admLockLevel = SVNWCAccess.INFINITE_DEPTH;
        if (depth == SVNDepth.EMPTY || depth == SVNDepth.FILES) {
            admLockLevel = 0;
        }

        try {
            wcAccess.probeOpen(path, true, admLockLevel);
            if (!wcAccess.isWCRoot(path)) {
                // not a wc root, open from parent
                wcAccess.close();
                if (admLockLevel >= 0) {
                    admLockLevel++;
                }
                wcAccess.probeOpen(path.getParentFile(), true, admLockLevel);
            }
            ISVNEntryHandler resolveEntryHandler = new ISVNEntryHandler() {
                public void handleEntry(File path, SVNEntry entry) throws SVNException {
                    if (entry != null && entry.isDirectory() && !"".equals(entry.getName())) {
                        return;
                    }
                    SVNNodeKind kind = SVNNodeKind.UNKNOWN;
                    long revision = -1;
                    boolean wcRoot = false;
                    boolean resolved = false;
                    if (entry != null && entry.isDirectory()) {
                        wcRoot = wcAccess.isWCRoot(path);
                    }
                    if (resolveTree && !wcRoot) {                        
                        File parentDir = path.getParentFile();
                        SVNAdminArea parentArea = wcAccess.probeRetrieve(parentDir);
                        SVNTreeConflictDescription tc = parentArea.getTreeConflict(path.getName());
                        if (tc != null) {
                            if (choice != SVNConflictChoice.MERGED) {
                                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CONFLICT_RESOLVER_FAILURE, 
                                        "Tree conflicts can only be resolved to ''working'' state; ''{0}'' not resolved", path);
                                SVNErrorManager.error(err, SVNLogType.WC);
                            }
                            parentArea.deleteTreeConflict(path.getName());
                            kind = tc.getNodeKind();
                            resolved = true;
                        }
                    }
                    if (entry != null && (resolveContents || resolveProperties)) {
                        kind = entry.getKind();
                        revision = entry.getRevision();
                        File conflictDir = entry.isDirectory() ? path : path.getParentFile();
                        SVNAdminArea conflictArea = wcAccess.retrieve(conflictDir);
                        resolved |= conflictArea.markResolved(entry.getName(), resolveContents, resolveProperties, choice);
                    }
                    if (resolved) {
                        SVNEvent event = SVNEventFactory.createSVNEvent(path, kind, null, 
                                revision, SVNEventAction.RESOLVED, null, null, null);
                        dispatchEvent(event);
                    }
                }

                public void handleError(File path, SVNErrorMessage error) throws SVNException {
                    SVNErrorManager.error(error, SVNLogType.WC);
                }
            };

            if (depth == SVNDepth.EMPTY) {
                SVNEntry entry = wcAccess.getEntry(path, false);
                if (entry != null) {
                    resolveEntryHandler.handleEntry(path, entry);
                } else {
                    SVNTreeConflictDescription tc = wcAccess.getTreeConflict(path);
                    if (tc != null) {
                        resolveEntryHandler.handleEntry(path, null);
                    } else {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, 
                                "''{0}'' is not under version control", path);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                }                
            } else {
                wcAccess.walkEntries(path, resolveEntryHandler, false, true, depth);
            }
        } finally {
            wcAccess.close();
        }
    }

    /**
     * Locks file items in a Working Copy as well as in a repository so that
     * no other user can commit changes to them.
     *
     * @param paths       an array of local WC file paths that should be locked
     * @param stealLock   if <span class="javakeyword">true</span> then all existing
     *                    locks on the specified <code>paths</code> will be "stolen"
     * @param lockMessage an optional lock comment
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li>a path to be locked is not under version control
     *                      <li>can not obtain a URL of a local path to lock it in
     *                      the repository - there's no such entry
     *                      <li><code>paths</code> to be locked belong to different repositories
     *                      </ul>
     * @see #doLock(SVNURL[],boolean,String)
     */
    public void doLock(File[] paths, boolean stealLock, String lockMessage) throws SVNException {
        if (paths == null || paths.length == 0) {
            return;
        }
        
        final Map entriesMap = new SVNHashMap();
        Map pathsRevisionsMap = new SVNHashMap();
        final SVNWCAccess wcAccess = createWCAccess();
        try {
            final SVNURL topURL = collectLockInfo(wcAccess, paths, entriesMap, pathsRevisionsMap, true, stealLock);
            SVNRepository repository = createRepository(topURL, paths[0], wcAccess, true);
            final SVNURL rootURL = repository.getRepositoryRoot(true);

            repository.lock(pathsRevisionsMap, lockMessage, stealLock, new ISVNLockHandler() {
                public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                    SVNURL fullURL = rootURL.appendPath(path, false);
                    LockInfo lockInfo = (LockInfo) entriesMap.get(fullURL);
                    SVNAdminArea dir = wcAccess.probeRetrieve(lockInfo.myFile);
                    if (error == null) {
                        SVNEntry entry = wcAccess.getVersionedEntry(lockInfo.myFile, false);
                        entry.setLockToken(lock.getID());
                        entry.setLockComment(lock.getComment());
                        entry.setLockOwner(lock.getOwner());
                        entry.setLockCreationDate(SVNDate.formatDate(lock.getCreationDate()));
                        // get properties and values.
                        SVNVersionedProperties props = dir.getProperties(entry.getName());

                        if (props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null) {
                            SVNFileUtil.setReadonly(dir.getFile(entry.getName()), false);
                        }
                        SVNFileUtil.setExecutable(dir.getFile(entry.getName()), props.getPropertyValue(SVNProperty.EXECUTABLE) != null);
                        dir.saveEntries(false);
                        handleEvent(SVNEventFactory.createLockEvent(dir.getFile(entry.getName()), SVNEventAction.LOCKED, lock, null),
                                ISVNEventHandler.UNKNOWN);
                    } else {
                        handleEvent(SVNEventFactory.createLockEvent(dir.getFile(lockInfo.myFile.getName()), SVNEventAction.LOCK_FAILED, lock, error),
                                ISVNEventHandler.UNKNOWN);
                    }
                }

                public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) {
                }
            });
        } finally {
            wcAccess.close();
        }
    }

    /**
     * Locks file items in a repository so that no other user can commit
     * changes to them.
     *
     * @param urls        an array of URLs to be locked
     * @param stealLock   if <span class="javakeyword">true</span> then all existing
     *                    locks on the specified <code>urls</code> will be "stolen"
     * @param lockMessage an optional lock comment
     * @throws SVNException
     * @see #doLock(File[],boolean,String)
     */
    public void doLock(SVNURL[] urls, boolean stealLock, String lockMessage) throws SVNException {
        Collection paths = new SVNHashSet();
        SVNURL topURL = SVNURLUtil.condenceURLs(urls, paths, false);
        if (paths.isEmpty()) {
            paths.add("");
        }
        Map pathsToRevisions = new SVNHashMap();
        for (Iterator p = paths.iterator(); p.hasNext();) {
            String path = (String) p.next();
            path = SVNEncodingUtil.uriDecode(path);
            pathsToRevisions.put(path, null);
        }
        checkCancelled();
        SVNRepository repository = createRepository(topURL, null, null, true);
        repository.lock(pathsToRevisions, lockMessage, stealLock, new ISVNLockHandler() {
            public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                if (error != null) {
                    handleEvent(SVNEventFactory.createLockEvent(new Resource(path), SVNEventAction.LOCK_FAILED, lock, error), ISVNEventHandler.UNKNOWN);
                } else {
                    handleEvent(SVNEventFactory.createLockEvent(new Resource(path), SVNEventAction.LOCKED, lock, null), ISVNEventHandler.UNKNOWN);
                }
            }

            public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
            }

        });
    }

    /**
     * Unlocks file items in a Working Copy as well as in a repository.
     *
     * @param paths     an array of local WC file paths that should be unlocked
     * @param breakLock if <span class="javakeyword">true</span> and there are locks
     *                  that belong to different users then those locks will be also
     *                  unlocked - that is "broken"
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li>a path is not under version control
     *                      <li>can not obtain a URL of a local path to unlock it in
     *                      the repository - there's no such entry
     *                      <li>if a path is not locked in the Working Copy
     *                      and <code>breakLock</code> is <span class="javakeyword">false</span>
     *                      <li><code>paths</code> to be unlocked belong to different repositories
     *                      </ul>
     * @see #doUnlock(SVNURL[],boolean)
     */
    public void doUnlock(File[] paths, boolean breakLock) throws SVNException {
        if (paths == null || paths.length == 0) {
            return;
        }
        final Map entriesMap = new SVNHashMap();
        Map pathsTokensMap = new SVNHashMap();
        final SVNWCAccess wcAccess = createWCAccess();
        try {
            final SVNURL topURL = collectLockInfo(wcAccess, paths, entriesMap, pathsTokensMap, false, breakLock);
            checkCancelled();
            SVNRepository repository = createRepository(topURL, paths[0], wcAccess, true);
            final SVNURL rootURL = repository.getRepositoryRoot(true);
            repository.unlock(pathsTokensMap, breakLock, new ISVNLockHandler() {
                public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                }

                public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                    SVNURL fullURL = rootURL.appendPath(path, false);
                    LockInfo lockInfo = (LockInfo) entriesMap.get(fullURL);
                    SVNEventAction action = null;
                    SVNAdminArea dir = wcAccess.probeRetrieve(lockInfo.myFile);
                    if (error == null || (error != null && error.getErrorCode() != SVNErrorCode.FS_LOCK_OWNER_MISMATCH)) {
                        SVNEntry entry = wcAccess.getVersionedEntry(lockInfo.myFile, false);
                        entry.setLockToken(null);
                        entry.setLockComment(null);
                        entry.setLockOwner(null);
                        entry.setLockCreationDate(null);

                        SVNVersionedProperties props = dir.getProperties(entry.getName());

                        if (props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null) {
                            SVNFileUtil.setReadonly(dir.getFile(entry.getName()), true);
                        }
                        dir.saveEntries(false);
                        action = SVNEventAction.UNLOCKED;
                    }
                    if (error != null) {
                        action = SVNEventAction.UNLOCK_FAILED;
                    }
                    if (action != null) {
                        handleEvent(SVNEventFactory.createLockEvent(dir.getFile(lockInfo.myFile.getName()), action, lock, error), ISVNEventHandler.UNKNOWN);
                    }
                }
            });
        } finally {
            wcAccess.close();
        }
    }

    /**
     * Unlocks file items in a repository.
     *
     * @param urls      an array of URLs that should be unlocked
     * @param breakLock if <span class="javakeyword">true</span> and there are locks
     *                  that belong to different users then those locks will be also
     *                  unlocked - that is "broken"
     * @throws SVNException
     * @see #doUnlock(File[],boolean)
     */
    public void doUnlock(SVNURL[] urls, boolean breakLock) throws SVNException {
        Collection paths = new SVNHashSet();
        SVNURL topURL = SVNURLUtil.condenceURLs(urls, paths, false);
        if (paths.isEmpty()) {
            paths.add("");
        }
        Map pathsToTokens = new SVNHashMap();
        for (Iterator p = paths.iterator(); p.hasNext();) {
            String path = (String) p.next();
            path = SVNEncodingUtil.uriDecode(path);
            pathsToTokens.put(path, null);
        }

        checkCancelled();
        SVNRepository repository = createRepository(topURL, null, null, true);
        if (!breakLock) {
            pathsToTokens = fetchLockTokens(repository, pathsToTokens);
        }
        repository.unlock(pathsToTokens, breakLock, new ISVNLockHandler() {
            public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
            }

            public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                if (error != null) {
                    handleEvent(SVNEventFactory.createLockEvent(new Resource(path), SVNEventAction.UNLOCK_FAILED, null, error), ISVNEventHandler.UNKNOWN);
                } else {
                    handleEvent(SVNEventFactory.createLockEvent(new Resource(path), SVNEventAction.UNLOCKED, null, null), ISVNEventHandler.UNKNOWN);
                }
            }
        });
    }

    /**
     * Collects information about Working Copy item(s) and passes it to an
     * info handler.
     * <p/>
     * <p/>
     * If <code>revision</code> is valid and not local,
     * then information will be collected on remote items (that is taken from
     * a repository). Otherwise information is gathered on local items not
     * accessing a repository.
     *
     * @param path      a WC item on which info should be obtained
     * @param revision  a target revision
     * @param recursive <span class="javakeyword">true</span> to
     *                  descend recursively (relevant for directories)
     * @param handler   a caller's info handler
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>path</code> is not under version control
     *                      <li>can not obtain a URL corresponding to <code>path</code> to
     *                      get its information from the repository - there's no such entry
     *                      <li>if a remote info: <code>path</code> is an item that does not exist in
     *                      the specified <code>revision</code>
     *                      </ul>
     * @deprecated use {@link #doInfo(File, SVNRevision, SVNRevision, SVNDepth, Collection, ISVNInfoHandler)} instead
     */
    public void doInfo(File path, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException {
        doInfo(path, SVNRevision.UNDEFINED, revision, SVNDepth.getInfinityOrEmptyDepth(recursive), null, handler);        
    }

    /**
     * Collects information about Working Copy item(s) and passes it to an
     * info handler.
     * <p/>
     * <p/>
     * If <code>revision</code> & <code>pegRevision</code> are valid and not
     * local, then information will be collected
     * on remote items (that is taken from a repository). Otherwise information
     * is gathered on local items not accessing a repository.
     *
     * @param path        a WC item on which info should be obtained
     * @param pegRevision a revision in which <code>path</code> is first
     *                    looked up
     * @param revision    a target revision
     * @param recursive   <span class="javakeyword">true</span> to
     *                    descend recursively (relevant for directories)
     * @param handler     a caller's info handler
     * @throws SVNException if one of the following is true:
     *                      <ul>
     *                      <li><code>path</code> is not under version control
     *                      <li>can not obtain a URL corresponding to <code>path</code> to
     *                      get its information from the repository - there's no such entry
     *                      <li>if a remote info: <code>path</code> is an item that does not exist in
     *                      the specified <code>revision</code>
     *                      </ul>
     * @deprecated use {@link #doInfo(File, SVNRevision, SVNRevision, SVNDepth, Collection, ISVNInfoHandler)} instead
     */
    public void doInfo(File path, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException {
        doInfo(path, pegRevision, revision, SVNDepth.getInfinityOrEmptyDepth(recursive), null, handler);
    }

    /**
     * Invokes <code>handler</code> to return information
     * about <code>path</code> in <code>revision</code>. The information returned is
     * system-generated metadata, not the sort of "property" metadata
     * created by users. See {@link SVNInfo}.
     * 
     * <p/>
     * If both revision arguments are either <span class="javakeyword">null</span> or 
     * {@link SVNRevision#isLocal() local}, or {@link SVNRevision#isValid() invalid}, then information 
     * will be pulled solely from the working copy; no network connections will be
     * made.
     * 
     * <p/>
     * Otherwise, information will be pulled from a repository. The
     * actual node revision selected is determined by the <code>path</code>
     * as it exists in <code>pegRevision</code>. If <code>pegRevision</code> is 
     * {@link SVNRevision#UNDEFINED}, then it defaults to {@link SVNRevision#WORKING}.
     *
     * <p/>
     * If <code>path</code> is a file, just invokes <code>handler</code> on it. If it
     * is a directory, then descends according to <code>depth</code>.  If <code>depth</code> is
     * {@link SVNDepth#EMPTY}, invokes <code>handler</code> on <code>path</code> and
     * nothing else; if {@link SVNDepth#FILES}, on <code>path</code> and its
     * immediate file children; if {@link SVNDepth#IMMEDIATES}, the preceding
     * plus on each immediate subdirectory; if {@link SVNDepth#INFINITY}, then
     * recurses fully, invoking <code>handler</code> on <code>path</code> and
     * everything beneath it.
     * 
     * <p/>
     * <code>changeLists</code> is a collection of <code>String</code> changelist
     * names, used as a restrictive filter on items whose info is
     * reported; that is, doesn't report info about any item unless
     * it's a member of one of those changelists.  If <code>changeLists</code> is
     * empty (or <span class="javakeyword">null</span>), no changelist filtering occurs.
     * 
     * @param path           a WC item on which info should be obtained
     * @param pegRevision    a revision in which <code>path</code> is first
     *                       looked up
     * @param revision       a target revision
     * @param depth          tree depth
     * @param changeLists    collection changelist names
     * @param handler        caller's info handler
     * @throws SVNException 
     * @since 1.2, SVN 1.5
     */
    public void doInfo(File path, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth, 
            Collection changeLists, ISVNInfoHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }
        
        boolean local = (revision == null || !revision.isValid() || revision.isLocal()) &&
                (pegRevision == null || !pegRevision.isValid() || pegRevision.isLocal());

        if (!local) {
            SVNWCAccess wcAccess = createWCAccess();
            SVNRevision wcRevision = null;
            SVNURL url = null;
            try {
                wcAccess.probeOpen(path, false, 0);
                SVNEntry entry = wcAccess.getVersionedEntry(path, false);
                url = entry.getSVNURL();
                if (url == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                            "''{0}'' has no URL", path);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                wcRevision = SVNRevision.create(entry.getRevision());
            } finally {
                wcAccess.close();
            }
            doInfo(url, pegRevision == null || !pegRevision.isValid() || pegRevision.isLocal() ? 
                    wcRevision : pegRevision, revision, depth, handler);
            return;
        }
        Collection changelistsSet = null;
        if (changeLists != null) {
            changelistsSet = new SVNHashSet();
            for (Iterator changeListsIter = changeLists.iterator(); changeListsIter.hasNext();) {
                String changeList = (String) changeListsIter.next();
                changelistsSet.add(changeList);
            }
        }
        crawlEntries(path, depth, changelistsSet, handler);
    }

    /**
     * Collects information about item(s) in a repository and passes it to
     * an info handler.
     *
     * @param url         a URL of an item which information is to be
     *                    obtained and processed
     * @param pegRevision a revision in which the item is first looked up
     * @param revision    a target revision
     * @param recursive   <span class="javakeyword">true</span> to
     *                    descend recursively (relevant for directories)
     * @param handler     a caller's info handler
     * @throws SVNException if <code>url</code> is an item that does not exist in
     *                      the specified <code>revision</code>
     * @deprecated use {@link #doInfo(SVNURL, SVNRevision, SVNRevision, SVNDepth, ISVNInfoHandler)} instead 
     */
    public void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException {
        doInfo(url, pegRevision, revision, SVNDepth.getInfinityOrEmptyDepth(recursive), handler);
    }

    /**
     * Invokes <code>handler</code> to return information
     * about <code>url</code> in <code>revision</code>. The information returned is
     * system-generated metadata, not the sort of "property" metadata
     * created by users. See {@link SVNInfo}.
     * 
     * <p/>
     * If <code>revision</code> argument is either <span class="javakeyword">null</span> or 
     * {@link SVNRevision#isValid() invalid}, it defaults to {@link SVNRevision#HEAD}.
     * If <code>revision</code> is {@link SVNRevision#PREVIOUS} (or some other kind that requires
     * a local path), an error will be returned, because the desired
     * revision cannot be determined.
     * If <code>pegRevision</code> argument is either <span class="javakeyword">null</span> or 
     * {@link SVNRevision#isValid() invalid}, it defaults to <code>revision</code>.
     * 
     * <p/>
     * Information will be pulled from the repository. The actual node revision selected is determined by 
     * the <code>url</code> as it exists in <code>pegRevision</code>. If <code>pegRevision</code> is 
     * {@link SVNRevision#UNDEFINED}, then it defaults to {@link SVNRevision#WORKING}.
     *
     * <p/>
     * If <code>url</code> is a file, just invokes <code>handler</code> on it. If it
     * is a directory, then descends according to <code>depth</code>. If <code>depth</code> is
     * {@link SVNDepth#EMPTY}, invokes <code>handler</code> on <code>url</code> and
     * nothing else; if {@link SVNDepth#FILES}, on <code>url</code> and its
     * immediate file children; if {@link SVNDepth#IMMEDIATES}, the preceding
     * plus on each immediate subdirectory; if {@link SVNDepth#INFINITY}, then
     * recurses fully, invoking <code>handler</code> on <code>url</code> and
     * everything beneath it.
     * 
     * @param url            versioned item url
     * @param pegRevision    revision in which <code>path</code> is first
     *                       looked up
     * @param revision       target revision
     * @param depth          tree depth
     * @param handler        caller's info handler
     * @throws SVNException 
     * @since 1.2, SVN 1.5
     */
    public void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth, 
            ISVNInfoHandler handler) throws SVNException {
        depth = depth == null ? SVNDepth.UNKNOWN : depth;
        long[] revNum = { SVNRepository.INVALID_REVISION };
        SVNRepository repos = createRepository(url, null, null, pegRevision, revision, revNum);
        
        url = repos.getLocation();
        SVNDirEntry rootEntry = null;
        SVNURL reposRoot = repos.getRepositoryRoot(true);
        String reposUUID = repos.getRepositoryUUID(true);
        String baseName = SVNPathUtil.tail(url.getPath());

        try {
            rootEntry = repos.info("", revNum[0]);
        } catch (SVNException e) {
            if (e.getErrorMessage() != null && 
                    e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_IMPLEMENTED) {
                // for svnserve older then 1.2.0
                if (url.equals(reposRoot)) {
                    if (depth.compareTo(SVNDepth.EMPTY) > 0) {
                        SVNLock[] locks = null;
                        if (pegRevision == SVNRevision.HEAD) {
                            try {
                                locks = repos.getLocks("");
                            } catch (SVNException svne) {
                                SVNErrorCode code = svne.getErrorMessage().getErrorCode();
                                if (code == SVNErrorCode.RA_NOT_IMPLEMENTED || 
                                        code == SVNErrorCode.UNSUPPORTED_FEATURE) {
                                    locks = new SVNLock[0];
                                } else {
                                    throw svne;
                                }
                            }
                        } else {
                            locks = new SVNLock[0];
                        }

                        locks = locks == null ? new SVNLock[0] : locks;
                        Map locksMap = new SVNHashMap();
                        for (int i = 0; i < locks.length; i++) {
                            SVNLock lock = locks[i];
                            locksMap.put(lock.getPath(), lock);
                        }

                        pushDirInfo(repos, SVNRevision.create(revNum[0]), "", repos.getRepositoryRoot(true), 
                                reposUUID, url, locksMap, depth, handler);
                        return;
                    }
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                            "Server does not support retrieving information about the repository root");
                    SVNErrorManager.error(err, SVNLogType.WC);
                } 
                
                SVNNodeKind urlKind = repos.checkPath("", revNum[0]);
                if (urlKind == SVNNodeKind.NONE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, 
                            "URL ''{0}'' non-existent in revision {1}", 
                            new Object[]{ url, new Long(revNum[0]) });
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                
                SVNRepository parentRepos = createRepository(url.removePathTail(), null, null, false);
                Collection dirEntries = parentRepos.getDir("", revNum[0], null, SVNDirEntry.DIRENT_KIND | 
                        SVNDirEntry.DIRENT_CREATED_REVISION | SVNDirEntry.DIRENT_TIME | 
                        SVNDirEntry.DIRENT_LAST_AUTHOR, ( Collection )null);
                
                for (Iterator ents = dirEntries.iterator(); ents.hasNext();) {
                    SVNDirEntry dirEntry = (SVNDirEntry) ents.next();
                    // dir entry name may differ from 'name', due to renames...
                    if (baseName.equals(dirEntry.getName())) {
                        rootEntry = dirEntry;
                        break;
                    }
                }
                if (rootEntry == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, 
                            "URL ''{0}'' non-existent in revision {1}", 
                            new Object[]{ url, new Long(revNum[0]) });
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            } else {
                throw e;
            }
        }
        
        if (rootEntry == null || rootEntry.getKind() == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, 
                    "URL ''{0}'' non-existent in revision ''{1}''",
                    new Object[]{ url, new Long(revNum[0]) });
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        SVNLock lock = null;
        if (rootEntry.getKind() == SVNNodeKind.FILE) {
            try {
                SVNRepositoryLocation[] locations = getLocations(url, null, null, SVNRevision.create(revNum[0]), SVNRevision.HEAD, SVNRevision.UNDEFINED);
                if (locations != null && locations.length > 0) {
                    SVNURL headURL = locations[0].getURL();
                    if (headURL.equals(url)) {
                        // get lock for this item (@headURL).
                        try {
                            lock = repos.getLock("");
                        } catch (SVNException e) {
                            if (!(e.getErrorMessage() != null && 
                                    e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_IMPLEMENTED)) {
                                throw e;
                            }
                        }
                    }
                }
            } catch (SVNException e) {
                SVNErrorCode code = e.getErrorMessage().getErrorCode();
                if (code != SVNErrorCode.FS_NOT_FOUND && code != SVNErrorCode.CLIENT_UNRELATED_RESOURCES) {
                    throw e;
                }
            }
        }

        SVNInfo info = SVNInfo.createInfo(baseName, reposRoot, reposUUID, url, SVNRevision.create(revNum[0]), rootEntry, lock);
        handler.handleInfo(info);
        
        if (depth.compareTo(SVNDepth.EMPTY) > 0 && rootEntry.getKind() == SVNNodeKind.DIR) {
            SVNLock[] locks = null;
            if (pegRevision == SVNRevision.HEAD) {
                try {
                    locks = repos.getLocks("");
                } catch (SVNException svne) {
                    SVNErrorCode code = svne.getErrorMessage().getErrorCode();
                    if (code == SVNErrorCode.RA_NOT_IMPLEMENTED || 
                            code == SVNErrorCode.UNSUPPORTED_FEATURE) {
                        locks = new SVNLock[0];
                    } else {
                        throw svne;
                    }
                }
            } else {
                locks = new SVNLock[0];
            }

            locks = locks == null ? new SVNLock[0] : locks;
            Map locksMap = new SVNHashMap();
            for (int i = 0; i < locks.length; i++) {
                lock = locks[i];
                locksMap.put(lock.getPath(), lock);
            }

            pushDirInfo(repos, SVNRevision.create(revNum[0]), "", repos.getRepositoryRoot(true), 
                    reposUUID, url, locksMap, depth, handler);
        }
    }

    /**
     * Returns the current Working Copy min- and max- revisions as well as
     * changes and switch status within a single string.
     * 
     * <p/>
     * This method is the same as <code>doGetWorkingCopyID(path, trailURL, false)</code>.
     *
     * @param path     a local path
     * @param trailURL optional: if not <span class="javakeyword">null</span>
     *                 specifies the name of the item that should be met
     *                 in the URL corresponding to the repository location
     *                 of the <code>path</code>; if that URL ends with something
     *                 different than this optional parameter - the Working
     *                 Copy will be considered "switched"
     * @return         brief info on the Working Copy or the string
     *                 "exported" if <code>path</code> is a clean directory
     * @throws SVNException if <code>path</code> is neither versioned nor
     *                      even exported
     * @see #doGetWorkingCopyID(File, String, boolean)
     */
    public String doGetWorkingCopyID(final File path, String trailURL) throws SVNException {
        return doGetWorkingCopyID(path, trailURL, false);
    }

    /**
     * Returns the current Working Copy min- and max- revisions as well as
     * changes and switch status within a single string.
     * <p/>
     * <p/>
     * A return string has a form of <code>"minR[:maxR][M][S]"</code> where:
     * <ul>
     * <li><code>minR</code> - is the smallest revision number met in the
     * Working Copy
     * <li><code>maxR</code> - is the biggest revision number met in the
     * Working Copy; appears only if there are different revision in the
     * Working Copy
     * <li><code>M</code> - appears only if there're local edits to the
     * Working Copy - that means 'Modified'
     * <li><code>S</code> - appears only if the Working Copy is switched
     * against a different URL
     * </ul>
     * If <code>path</code> is a directory - this method recursively descends
     * into the Working Copy, collects and processes local information.
     * 
     * <p/>
     * This method operates on local working copies only without accessing a repository.
     * 
     * @param path          a local path
     * @param trailURL      optional: if not <span class="javakeyword">null</span>
     *                      specifies the name of the item that should be met
     *                      in the URL corresponding to the repository location
     *                      of the <code>path</code>; if that URL ends with something
     *                      different than this optional parameter - the Working
     *                      Copy will be considered "switched"
     * @param committed     if <span class="javakeyword">true</span> committed (last chaned) 
     *                      revisions instead of working copy ones are reported
     * @return              brief info on the Working Copy or the string
     *                      "exported" if <code>path</code> is a clean directory
     * @throws SVNException if <code>path</code> is neither versioned nor
     *                      even exported
     * @since  1.2
     */
    public String doGetWorkingCopyID(final File path, String trailURL, final boolean committed) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        try {
            wcAccess.open(path, false, 0);
        } catch (SVNException e) {
            SVNFileType pathType = SVNFileType.getType(path);
            if (pathType == SVNFileType.DIRECTORY) {
                return "exported";
            } else if (pathType == SVNFileType.NONE) {
                throw e;
            }
            return "'" + path + "' is not versioned and not exported";
        } finally {
            wcAccess.close();
        }
        SVNStatusClient statusClient = new SVNStatusClient((ISVNAuthenticationManager) null, getOptions());
        statusClient.setIgnoreExternals(true);
        final long[] maxRevision = new long[1];
        final long[] minRevision = new long[]{-1};
        final boolean[] switched = new boolean[3];
        final String[] wcURL = new String[1];
        statusClient.doStatus(path, SVNRevision.WORKING, SVNDepth.INFINITY, false, true, false, false, new ISVNStatusHandler() {
            public void handleStatus(SVNStatus status) {
                if (status.getEntryProperties() == null || status.getEntryProperties().isEmpty()) {
                    return;
                }
                if (status.getContentsStatus() != SVNStatusType.STATUS_ADDED) {
                    SVNRevision revision = committed ? status.getCommittedRevision() : status.getRevision();
                    if (revision != null) {
                        if (minRevision[0] < 0 || minRevision[0] > revision.getNumber()) {
                            minRevision[0] = revision.getNumber();
                        }
                        maxRevision[0] = Math.max(maxRevision[0], revision.getNumber());
                    }
                }
                switched[0] |= status.isSwitched();
                switched[1] |= status.getContentsStatus() != SVNStatusType.STATUS_NORMAL;
                switched[1] |= status.getPropertiesStatus() != SVNStatusType.STATUS_NORMAL &&
                        status.getPropertiesStatus() != SVNStatusType.STATUS_NONE;
                switched[2] |= status.getEntry() != null && status.getEntry().getDepth() != SVNDepth.INFINITY;
                if (wcURL[0] == null && status.getFile() != null && status.getFile().equals(path) && status.getURL() != null) {
                    wcURL[0] = status.getURL().toString();
                }
            }
        }, null);
        if (!switched[0] && trailURL != null) {
            if (wcURL[0] == null) {
                switched[0] = true;
            } else {
                switched[0] = !wcURL[0].endsWith(trailURL);
            }
        }
        StringBuffer id = new StringBuffer();
        id.append(minRevision[0]);
        if (minRevision[0] != maxRevision[0]) {
            id.append(":").append(maxRevision[0]);
        }
        if (switched[1]) {
            id.append("M");
        }
        if (switched[0]) {
            id.append("S");
        }
        if (switched[2]) {
            id.append("P");
        }
        return id.toString();
    }

    /**
     * Collects and returns information on a single Working Copy item.
     * 
     * <p/>
     * This method is the same as 
     * <code>doInfo(path, SVNRevision.UNDEFINED, revision, SVNDepth.EMPTY, null, handler)</code> where
     * <code>handler</code> just stores {@link SVNInfo} for the <code>path</code> and then returns it to
     * the caller.
     *
     * @param  path          a WC item on which info should be obtained
     * @param  revision      a target revision
     * @return               collected info
     * @throws SVNException  if one of the following is true:
     *                       <ul>
     *                       <li><code>path</code> is not under version control
     *                       <li>can not obtain a URL corresponding to <code>path</code> to
     *                       get its information from the repository - there's no such entry
     *                       <li>if a remote info: <code>path</code> is an item that does not exist in
     *                       the specified <code>revision</code>
     *                       </ul>
     * @see    #doInfo(File, SVNRevision, SVNRevision, SVNDepth, Collection, ISVNInfoHandler)                       
     */
    public SVNInfo doInfo(File path, SVNRevision revision) throws SVNException {
        final SVNInfo[] result = new SVNInfo[1];
        doInfo(path, SVNRevision.UNDEFINED, revision, SVNDepth.EMPTY, null, new ISVNInfoHandler() {
            public void handleInfo(SVNInfo info) {
                if (result[0] == null) {
                    result[0] = info;
                }
            }
        });
        return result[0];
    }

    /**
     * Collects and returns information on a single item in a repository.
     * 
     * <p/>
     * This method is the same as <code>doInfo(url, pegRevision, revision, SVNDepth.EMPTY, handler)</code> 
     * where <code>handler</code> just stores {@link SVNInfo} for the <code>url</code>.
     * 
     * @param  url            a URL of an item which information is to be
     *                        obtained
     * @param  pegRevision    a revision in which the item is first looked up
     * @param  revision       a target revision
     * @return                collected info
     * @throws SVNException   if <code>url</code> is an item that does not exist in
     *                        the specified <code>revision</code>
     * @see    #doInfo(SVNURL, SVNRevision, SVNRevision, SVNDepth, ISVNInfoHandler)
     */
    public SVNInfo doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision) throws SVNException {
        final SVNInfo[] result = new SVNInfo[1];
        doInfo(url, pegRevision, revision, SVNDepth.EMPTY, new ISVNInfoHandler() {
            public void handleInfo(SVNInfo info) {
                if (result[0] == null) {
                    result[0] = info;
                }
            }
        });
        return result[0];
    }

    /**
     * Recursively removes all DAV-specific <span class="javakeyword">"svn:wc:"</span> properties
     * from the <code>directory</code> and beneath. 
     * 
     * <p>
     * This method does not connect to a repository, it's a local operation only. Nor does it change any user's 
     * versioned data. Changes are made only in administrative version control files.
     *  
     * @param  directory     working copy path
     * @throws SVNException
     * @since  1.2
     */
    public void doCleanupWCProperties(File directory) throws SVNException {
        SVNWCAccess wcAccess = SVNWCAccess.newInstance(this);
        try {
            SVNAdminArea dir = wcAccess.open(directory, true, true, -1);
            if (dir != null) {
                SVNPropertiesManager.deleteWCProperties(dir, null, true);
            }
        } finally {
            wcAccess.close();
        }
    }
    
    /**
     * Changes working copy format. This method may be used to upgrade\downgrade working copy formats.
     * 
     * <p>
     * If externals are not {@link SVNBasicClient#isIgnoreExternals() ignored} then external working copies 
     * are also converted to the new working copy <code>format</code>.
     * 
     * <p>
     * This method does not connect to a repository, it's a local operation only. Nor does it change any user's 
     * versioned data. Changes are made only in administrative version control files.
     * 
     * @param  directory    working copy directory
     * @param  format       format to set, supported formats are: 9 (SVN 1.5), 8 (SVN 1.4) and 4 (SVN 1.2)
     * @throws SVNException 
     * @since  1.2
     */
    public void doSetWCFormat(File directory, int format) throws SVNException {
        SVNAdminAreaInfo info = null;
        SVNWCAccess wcAccess = SVNWCAccess.newInstance(this);
        try {
            info = wcAccess.openAnchor(directory, false, -1);
            setWCFormat(info, info.getTarget(), format);
        } finally {
            wcAccess.close();
        }
        
        if (!isIgnoreExternals() && info != null) {
            Collection processedDirs = new SVNHashSet();
            Map externals = info.getOldExternals();
            // update both old and new externals.
            for (Iterator paths = externals.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                String value = (String) externals.get(path);
                if (value == null) {
                    continue;
                }

                SVNExternal[] externalDefs = SVNExternal.parseExternals("", value);
                for (int i = 0; i < externalDefs.length; i++) {
                    String externalPath = externalDefs[i].getPath();
                    File externalDir = new Resource(info.getAnchor().getRoot(), SVNPathUtil.append(path, externalPath));
                    if (processedDirs.add(externalDir)) {
                        //if externalDir is an empty unversioned dir SVNFileType won't help us to avoid 
                    	//getting in an infinite loop
                    	try {
                        	wcAccess.open(externalDir, false, 0);
                        } catch (SVNException svne) {
                        	if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                        		continue;
                        	}
                        	throw svne;
                        } finally {
                        	wcAccess.close();
                        }

                        try {
                        	doSetWCFormat(externalDir, format);
                        } catch (SVNException e) {
                        	if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                        		continue;
                        	}
                        	throw e;
                        }
                    }
                }
            }
            
            externals = info.getNewExternals();
            for (Iterator paths = externals.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                String value = (String) externals.get(path);
                SVNExternal[] externalDefs = SVNExternal.parseExternals("", value);
                for (int i = 0; i < externalDefs.length; i++) {
                    String externalPath = externalDefs[i].getPath();
                    File externalDir = new Resource(info.getAnchor().getRoot(), SVNPathUtil.append(path, externalPath));
                    if (processedDirs.add(externalDir)) {
                        //if externalDir is an empty unversioned dir SVNFileType won't help us to avoid 
                        //getting in an infinite loop
                        try {
                            wcAccess.open(externalDir, false, 0);
                        } catch (SVNException svne) {
                            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                                continue;
                            }
                            throw svne;
                        } finally {
                            wcAccess.close();
                        }

                    	try {
                    		doSetWCFormat(externalDir, format);
                    	} catch (SVNException e) {
                    		if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                    			continue;
                    		}
                    		throw e;
                    	}
                    }
                }
            }
        }
    }
    
    /**
     * This method is deprecated. 
     * 
     * @param path           a WC item which properties are to be
     *                       modified
     * @param propName       a property name
     * @param propValue      a property value
     * @param force          <span class="javakeyword">true</span> to
     *                       force the operation to run
     * @param recursive      <span class="javakeyword">true</span> to
     *                       descend recursively
     * @param handler        a caller's property handler
     * @throws SVNException  if one of the following is true:
     *                       <ul>
     *                       <li><code>propName</code> is a revision
     *                       property
     *                       <li><code>propName</code> starts
     *                       with the {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX
     *                       svn:wc:} prefix
     *                       </ul>
     * @deprecated use {@link #doSetProperty(File, String, SVNPropertyValue, boolean, SVNDepth, ISVNPropertyHandler, Collection)} 
     *             instead
     */
    public void doSetProperty(File path, String propName, SVNPropertyValue propValue, boolean force, 
            boolean recursive, ISVNPropertyHandler handler) throws SVNException {
        doSetProperty(path, propName, propValue, force, SVNDepth.getInfinityOrEmptyDepth(recursive), handler, 
                null);
    }
    
    private SVNAdminArea addParentDirectories(SVNWCAccess wcAccess, File path) throws SVNException {
        try {
            return wcAccess.open(path, true, 0);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                if (path.getParentFile() == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NO_VERSIONED_PARENT);
                    SVNErrorManager.error(err, SVNLogType.WC);
                } else if (SVNFileUtil.getAdminDirectoryName().equals(path.getName())) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RESERVED_FILENAME_SPECIFIED, 
                                "''{0}'' ends in a reserved name", path);
                        SVNErrorManager.error(err, SVNLogType.WC);
                } else {
                    File parentPath = path.getParentFile();
                    SVNAdminArea parentDir = addParentDirectories(wcAccess, parentPath);
                    SVNWCManager.add(path, parentDir, null, SVNRevision.UNDEFINED, SVNDepth.INFINITY);
                    return wcAccess.getAdminArea(path);
                }
            }
            throw e;
        }
        
    }

    private void doGetRevisionProperty(SVNRepository repos, String propName, long revNumber, ISVNPropertyHandler handler) throws SVNException {
        if (propName != null) {
            SVNPropertyValue value = repos.getRevisionPropertyValue(revNumber, propName);
            if (value != null) {
                handler.handleProperty(revNumber, new SVNPropertyData(propName, value, getOptions()));
            }
        } else {
            SVNProperties props = new SVNProperties();
            repos.getRevisionProperties(revNumber, props);
            for (Iterator names = props.nameSet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                SVNPropertyValue value = props.getSVNPropertyValue(name);
                handler.handleProperty(revNumber, new SVNPropertyData(name, value, getOptions()));
            }
        }
    }

    private void addDirectory(File wcRoot, File path, SVNAdminArea parentDir, boolean force, boolean noIgnore, SVNDepth depth, boolean setDepth) throws SVNException {
        checkCancelled();
        try {
            SVNWCManager.add(path, parentDir, null, SVNRevision.UNDEFINED, setDepth ? SVNDepth.INFINITY : null);
        } catch (SVNException e) {
            if (!(force && e.getErrorMessage().getErrorCode() == SVNErrorCode.ENTRY_EXISTS)) {
                throw e;
            }
        }
        SVNWCAccess access = parentDir.getWCAccess();
        SVNAdminArea dir = access.retrieve(path);
        Collection ignores = Collections.EMPTY_SET;
        if (!noIgnore) {
            ignores = SVNStatusEditor.getIgnorePatterns(dir, SVNStatusEditor.getGlobalIgnores(getOptions()));
        }
        String relativePath = SVNPathUtil.getRelativePath(wcRoot.getAbsolutePath().replace(File.separatorChar, '/'), dir.getRoot().getAbsolutePath().replace(File.separatorChar, '/'));
        relativePath = relativePath != null ? "/" + relativePath : null;

        File[] children = SVNFileListUtil.listFiles(dir.getRoot());
        for (int i = 0; children != null && i < children.length; i++) {
            checkCancelled();
            if (SVNFileUtil.getAdminDirectoryName().equals(children[i].getName())) {
                continue;
            }
            if (!noIgnore) {
                String rootRelativePath = relativePath != null ? SVNPathUtil.append(relativePath, children[i].getName()): null;
                if (SVNStatusEditor.isIgnored(ignores, children[i], rootRelativePath)) {
                    continue;
                }
            }
            SVNFileType childType = SVNFileType.getType(children[i]);
            if (childType == SVNFileType.DIRECTORY && depth.compareTo(SVNDepth.IMMEDIATES) >= 0) {
                SVNDepth depthBelowHere = depth;
                if (depth == SVNDepth.IMMEDIATES) {
                    depthBelowHere = SVNDepth.EMPTY;
                }
                addDirectory(wcRoot, children[i], dir, force, noIgnore, depthBelowHere, setDepth);
            } else if (childType != SVNFileType.UNKNOWN && childType != SVNFileType.DIRECTORY && 
                    depth.compareTo(SVNDepth.FILES) >= 0) {
                try {
                    addFile(children[i], childType, dir);
                } catch (SVNException e) {
                    if (force && e.getErrorMessage().getErrorCode() == SVNErrorCode.ENTRY_EXISTS) {
                        continue;
                    }
                    throw e;
                }
            }
        }
    }

    private void addFile(File path, SVNFileType type, SVNAdminArea dir) throws SVNException {
        ISVNEventHandler handler = dir.getWCAccess().getEventHandler();
        dir.getWCAccess().setEventHandler(null);
        SVNWCManager.add(path, dir, null, SVNRevision.UNDEFINED, null);
        dir.getWCAccess().setEventHandler(handler);

        String mimeType = null;
        if (type == SVNFileType.SYMLINK) {
            SVNPropertiesManager.setProperty(dir.getWCAccess(), path, SVNProperty.SPECIAL,
                    SVNProperty.getValueOfBooleanProperty(SVNProperty.SPECIAL), false);
        } else {
            Map props = SVNPropertiesManager.computeAutoProperties(getOptions(), path, null);
            for (Iterator names = props.keySet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                String propValue = (String) props.get(propName);
                try {
                    SVNPropertiesManager.setProperty(dir.getWCAccess(), path, propName, SVNPropertyValue.create(propValue), false);
                } catch (SVNException e) {
                    if (SVNProperty.EOL_STYLE.equals(propName) &&
                            e.getErrorMessage().getErrorCode() == SVNErrorCode.ILLEGAL_TARGET &&
                            e.getErrorMessage().getMessage().indexOf("newlines") >= 0) {
                        ISVNAddParameters.Action action = getAddParameters().onInconsistentEOLs(path);
                        if (action == ISVNAddParameters.REPORT_ERROR) {
                            ISVNEventHandler eventHandler = getEventDispatcher();
                            try {
                                setEventHandler(null);
                                doRevert(path, dir, SVNDepth.EMPTY, false, null);
                            } catch (SVNException svne) {
                            } finally {
                                setEventHandler(eventHandler);
                            }

                            throw e;
                        } else if (action == ISVNAddParameters.ADD_AS_IS) {
                            SVNPropertiesManager.setProperty(dir.getWCAccess(), path, propName, null, false);
                        } else if (action == ISVNAddParameters.ADD_AS_BINARY) {
                            SVNPropertiesManager.setProperty(dir.getWCAccess(), path, propName, null, false);
                            mimeType = SVNFileUtil.BINARY_MIME_TYPE;
                        }
                    } else {
                        ISVNEventHandler eventHandler = getEventDispatcher();
                        try {
                            setEventHandler(null);
                            doRevert(path, dir, SVNDepth.EMPTY, false, null);
                        } catch (SVNException svne) {
                        } finally {
                            setEventHandler(eventHandler);
                        }

                        throw e;
                    }
                }
            }
            if (mimeType != null) {
                SVNPropertiesManager.setProperty(dir.getWCAccess(), path, SVNProperty.MIME_TYPE, SVNPropertyValue.create(mimeType), false);
            } else {
                mimeType = (String) props.get(SVNProperty.MIME_TYPE);
            }
        }
        SVNEvent event = SVNEventFactory.createSVNEvent(dir.getFile(path.getName()), SVNNodeKind.FILE, mimeType, SVNRepository.INVALID_REVISION, SVNEventAction.ADD, null, null, null);
        dispatchEvent(event);
    }

    private SVNURL collectLockInfo(SVNWCAccess wcAccess, File[] files, Map lockInfo, Map lockPaths, boolean lock, boolean stealLock) throws SVNException {
        String[] paths = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            paths[i] = files[i].getAbsolutePath();
            paths[i] = paths[i].replace(File.separatorChar, '/');
        }
        Collection condencedPaths = new ArrayList();
        String commonParentPath = SVNPathUtil.condencePaths(paths, condencedPaths, false);
        if (condencedPaths.isEmpty()) {
            condencedPaths.add(SVNPathUtil.tail(commonParentPath));
            commonParentPath = SVNPathUtil.removeTail(commonParentPath);
        }
        if (commonParentPath == null || "".equals(commonParentPath)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "No common parent found, unable to operate on dijoint arguments");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        paths = (String[]) condencedPaths.toArray(new String[condencedPaths.size()]);
        int depth = 0;
        for (int i = 0; i < paths.length; i++) {
            int segments = SVNPathUtil.getSegmentsCount(paths[i]);
            if (depth < segments) {
                depth = segments;
            }
        }
        wcAccess.probeOpen(new Resource(commonParentPath).getAbsoluteFile(), true, depth);
        for (int i = 0; i < paths.length; i++) {
            File file = new Resource(commonParentPath, paths[i]);
            SVNEntry entry = wcAccess.getVersionedEntry(file, false);
            if (entry.getURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", file);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (lock) {
                SVNRevision revision = stealLock ? SVNRevision.UNDEFINED : SVNRevision.create(entry.getRevision());
                lockInfo.put(entry.getSVNURL(), new LockInfo(file, revision));
            } else {
                if (!stealLock && entry.getLockToken() == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MISSING_LOCK_TOKEN, "''{0}'' is not locked in this working copy", file);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }                
                lockInfo.put(entry.getSVNURL(), new LockInfo(file, stealLock ? null : entry.getLockToken()));
            }
        }
        checkCancelled();
        SVNURL[] urls = (SVNURL[]) lockInfo.keySet().toArray(new SVNURL[lockInfo.size()]);
        Collection urlPaths = new SVNHashSet();
        final SVNURL topURL = SVNURLUtil.condenceURLs(urls, urlPaths, false);
        if (urlPaths.isEmpty()) {
            urlPaths.add("");
        }
        if (topURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Unable to lock/unlock across multiple repositories");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        // prepare Map for SVNRepository (decoded path : revision/lock token).
        for (Iterator encodedPaths = urlPaths.iterator(); encodedPaths.hasNext();) {
            String encodedPath = (String) encodedPaths.next();
            // get LockInfo for it.
            SVNURL fullURL = topURL.appendPath(encodedPath, true);
            LockInfo info = (LockInfo) lockInfo.get(fullURL);
            encodedPath = SVNEncodingUtil.uriDecode(encodedPath);
            if (lock) {
                if (info.myRevision == SVNRevision.UNDEFINED) {
                    lockPaths.put(encodedPath, null);
                } else {
                    lockPaths.put(encodedPath, new Long(info.myRevision.getNumber()));
                }
            } else {
                lockPaths.put(encodedPath, info.myToken);
            }
        }
        return topURL;
    }

    private void crawlEntries(File path, SVNDepth depth, final Collection changeLists, 
            final ISVNInfoHandler handler) throws SVNException {
        final SVNWCAccess wcAccess = createWCAccess();
        int admLockLevel = getLevelsToLockFromDepth(depth);
        try {
            wcAccess.probeOpen(path, false, admLockLevel);
            wcAccess.walkEntries(path, new ISVNEntryHandler() {
                public void handleEntry(File path, SVNEntry entry) throws SVNException {
                    if (entry.isDirectory() && !entry.isThisDir()) {
                        return;
                    }                    
                    if (SVNWCAccess.matchesChangeList(changeLists, entry)) {
                        reportEntry(path, entry, handler);
                    }
                }
                
                public void handleError(File path, SVNErrorMessage error) throws SVNException {
                    if (error != null && error.getErrorCode() == SVNErrorCode.UNVERSIONED_RESOURCE) {
                        SVNAdminArea dir = wcAccess.probeTry(path.getParentFile(), false, 0);
                        SVNTreeConflictDescription tc = dir.getTreeConflict(path.getName());
                        if (tc != null) {
                            SVNInfo info = SVNInfo.createInfo(path, tc);
                            handler.handleInfo(info);
                            return;
                        }
                    }
                    SVNErrorManager.error(error, SVNLogType.WC);
                }
            }, false, depth);
        } finally {
            wcAccess.close();
        }

    }
    
    private void reportEntry(File path, SVNEntry entry, ISVNInfoHandler handler) throws SVNException {
        if (entry.isDirectory() && !"".equals(entry.getName())) {
            return;
        }
        handler.handleInfo(SVNInfo.createInfo(path, entry));
    }

    private boolean doRevert(File path, SVNAdminArea parent, SVNDepth depth, boolean useCommitTimes, 
            Collection changeLists) throws SVNException {
        checkCancelled();
        SVNWCAccess wcAccess = parent.getWCAccess();
        SVNAdminArea dir = wcAccess.probeRetrieve(path);
        SVNEntry entry = wcAccess.getEntry(path, false);
        SVNTreeConflictDescription treeConflict = wcAccess.getTreeConflict(path);
        if (entry == null && treeConflict == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "Cannot revert unversioned item ''{0}''", 
                    path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        if (entry != null && entry.getKind() == SVNNodeKind.DIR) {
            SVNFileType fileType = SVNFileType.getType(path);
            if (fileType != SVNFileType.DIRECTORY && !entry.isScheduledForAddition()) {
                if (isRevertMissingDirectories() && entry.getSchedule() != null && !entry.isThisDir()) {
                    // missing directory scheduled for deletion in parent.
                    boolean reverted = revert(parent, entry.getName(), entry, useCommitTimes);
                    if (reverted) {
                        SVNEvent event = SVNEventFactory.createSVNEvent(dir.getFile(entry.getName()), entry.getKind(), null, entry.getRevision(), 
                            SVNEventAction.REVERT, null, null, null);
                        dispatchEvent(event);
                    }
                    return reverted;
                }
                SVNEvent event = SVNEventFactory.createSVNEvent(dir.getFile(entry.getName()), entry.getKind(), null, entry.getRevision(), SVNEventAction.FAILED_REVERT, null, null, null);
                dispatchEvent(event);
                return false;
            }
        }

        if (entry != null && entry.getKind() != SVNNodeKind.DIR && entry.getKind() != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                    "Cannot revert ''{0}'': unsupported entry node kind", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        SVNFileType fileType = SVNFileType.getType(path);
        if (fileType == SVNFileType.UNKNOWN) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                    "Cannot revert ''{0}'': unsupported node kind in working copy", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        boolean reverted = false;
        if (SVNWCAccess.matchesChangeList(changeLists, entry)) {
            if (treeConflict != null) {
                parent.deleteTreeConflict(path.getName());
                reverted = true;
            }
            
            if (entry != null) {
                if (entry.isScheduledForAddition()) {
                    boolean wasDeleted = false;
                    if (entry.getKind() == SVNNodeKind.FILE) {
                        wasDeleted = entry.isDeleted();
                        parent.removeFromRevisionControl(path.getName(), false, false);
                    } else if (entry.getKind() == SVNNodeKind.DIR) {
                        SVNEntry entryInParent = parent.getEntry(path.getName(), true);
                        if (entryInParent != null) {
                            wasDeleted = entryInParent.isDeleted();
                        }
                        if (fileType == SVNFileType.NONE || wcAccess.isMissing(path)) {
                            parent.deleteEntry(path.getName());
                            parent.saveEntries(false);
                        } else {
                            dir.removeFromRevisionControl("", false, false);
                        }
                    }

                    reverted = true;
                    depth = SVNDepth.EMPTY;
                    if (wasDeleted) {
                        Map attributes = new SVNHashMap();
                        attributes.put(SVNProperty.KIND, entry.getKind().toString());
                        attributes.put(SVNProperty.DELETED, Boolean.TRUE.toString());
                        parent.modifyEntry(path.getName(), attributes, true, false);
                    }
                } else if (entry.getSchedule() == null || entry.isScheduledForDeletion() || entry.isScheduledForReplacement()) {
                    if (entry.getKind() == SVNNodeKind.FILE) {
                        reverted = revert(parent, entry.getName(), entry, useCommitTimes);
                    } else if (entry.getKind() == SVNNodeKind.DIR) {
                        reverted = revert(dir, dir.getThisDirName(), entry, useCommitTimes);
                        if (reverted && parent != dir) {
                            SVNEntry entryInParent = parent.getEntry(path.getName(), false);
                            revert(parent, path.getName(), entryInParent, useCommitTimes);
                        }
                        if (entry.isScheduledForReplacement()) {
                            depth = SVNDepth.INFINITY;
                        }
                    }
                }
            }
            if (reverted) {
                SVNEvent event = null;
                if (entry != null) {
                    event = SVNEventFactory.createSVNEvent(dir.getFile(entry.getName()), entry.getKind(), null, entry.getRevision(), 
                            SVNEventAction.REVERT, null, null, null);
                } else {
                    event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, 
                            SVNEventAction.REVERT, null, null, null);
                }
                dispatchEvent(event);
            }
        }
        
        
        if (entry != null && entry.getKind() == SVNNodeKind.DIR && depth.compareTo(SVNDepth.EMPTY) > 0) {
            SVNDepth depthBelowHere = depth;
            if (depth == SVNDepth.FILES || depth == SVNDepth.IMMEDIATES) {
                depthBelowHere = SVNDepth.EMPTY;
            }
            for (Iterator entries = dir.entries(false); entries.hasNext();) {
                SVNEntry childEntry = (SVNEntry) entries.next();
                if (dir.getThisDirName().equals(childEntry.getName())) {
                    continue;
                }
                if (depth == SVNDepth.FILES && !childEntry.isFile()) {
                    continue;
                }
                File childPath = new Resource(path, childEntry.getName());
                reverted |= doRevert(childPath, dir, depthBelowHere, useCommitTimes, changeLists);
            }
            
            Map conflicts = SVNTreeConflictUtil.readTreeConflicts(path, entry.getTreeConflictData());
            for (Iterator conflictsIter = conflicts.keySet().iterator(); conflictsIter.hasNext();) {
                File conflictedPath = (File) conflictsIter.next();
                if (dir.getEntry(conflictedPath.getName(), false) == null) {
                    reverted |= doRevert(conflictedPath, dir, SVNDepth.EMPTY, useCommitTimes, changeLists);
                }
            }
        }
        return reverted;
    }

    private boolean revert(SVNAdminArea dir, String name, SVNEntry entry, boolean useCommitTime) throws SVNException {
        SVNLog log = dir.getLog();
        boolean reverted = false;
        SVNVersionedProperties baseProperties = null;
        SVNProperties command = new SVNProperties();
        boolean revertBase = false;

        if (entry.isScheduledForReplacement()) {
            revertBase = true;
            baseProperties = dir.getRevertProperties(name);
            String propRevertPath = SVNAdminUtil.getPropRevertPath(name, entry.getKind(), false);
            command.put(SVNLog.NAME_ATTR, propRevertPath);
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
            reverted = true;
        }
        boolean reinstallWorkingFile = false;
        if (baseProperties == null) {
            if (dir.hasPropModifications(name)) {
                baseProperties = dir.getBaseProperties(name);
                SVNVersionedProperties propDiff = dir.getProperties(name).compareTo(baseProperties);
                Collection propNames = propDiff.getPropertyNames(null);
                reinstallWorkingFile = propNames.contains(SVNProperty.EXECUTABLE) ||
                        propNames.contains(SVNProperty.KEYWORDS) ||
                        propNames.contains(SVNProperty.EOL_STYLE) ||
                        propNames.contains(SVNProperty.CHARSET) ||
                        propNames.contains(SVNProperty.SPECIAL) ||
                        propNames.contains(SVNProperty.NEEDS_LOCK);
            }
        }
        if (baseProperties != null) {
            // save base props both to base and working. 
            SVNProperties newProperties = baseProperties.asMap();
            SVNVersionedProperties originalBaseProperties = dir.getBaseProperties(name);
            SVNVersionedProperties workProperties = dir.getProperties(name);
            if (revertBase) {
                originalBaseProperties.removeAll();
            }
            workProperties.removeAll();
            for (Iterator names = newProperties.nameSet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                if (revertBase) {
                    originalBaseProperties.setPropertyValue(propName, newProperties.getSVNPropertyValue(propName));
                }
                workProperties.setPropertyValue(propName, newProperties.getSVNPropertyValue(propName));
            }
            dir.saveVersionedProperties(log, false);
            reverted = true;
        }
        SVNProperties newEntryProperties = new SVNProperties();
        if (entry.getKind() == SVNNodeKind.FILE) {
            String basePath = SVNAdminUtil.getTextBasePath(name, false);
            String revertBasePath = SVNAdminUtil.getTextRevertPath(name, false);
            
            if (!reinstallWorkingFile) {
                SVNFileType fileType = SVNFileType.getType(dir.getFile(name));
                if (fileType == SVNFileType.NONE) {
                    reinstallWorkingFile = true;
                }
            }
            if (dir.getFile(revertBasePath).isFile()) {
                reinstallWorkingFile = true;
            } else {
                if (!dir.getFile(basePath).isFile()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Error restoring text for ''{0}''", dir.getFile(name));
                    SVNErrorManager.error(err, SVNLogType.WC);
                } 
                revertBasePath = null;
            }
            if (revertBasePath != null) {
                command.put(SVNLog.NAME_ATTR, revertBasePath);
                command.put(SVNLog.DEST_ATTR, name);
                log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                command.clear();
                command.put(SVNLog.NAME_ATTR, revertBasePath);
                command.put(SVNLog.DEST_ATTR, basePath);
                log.addCommand(SVNLog.MOVE, command, false);
                reverted = true;
            } else {
                if (!reinstallWorkingFile) {
                    reinstallWorkingFile = dir.hasTextModifications(name, false, false, false);
                }
                if (reinstallWorkingFile) {
                    command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getTextBasePath(name, false));
                    command.put(SVNLog.DEST_ATTR, name);
                    log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                    command.clear();
                    if (useCommitTime && entry.getCommittedDate() != null) {
                        command.put(SVNLog.NAME_ATTR, name);
                        command.put(SVNLog.TIMESTAMP_ATTR, entry.getCommittedDate());
                        log.addCommand(SVNLog.SET_TIMESTAMP, command, false);
                        command.clear();
                    } else {
                        command.put(SVNLog.NAME_ATTR, name);
                        command.put(SVNLog.TIMESTAMP_ATTR, SVNDate.formatDate(new Date(System.currentTimeMillis())));
                        log.addCommand(SVNLog.SET_TIMESTAMP, command, false);
                        command.clear();
                    }
                    command.put(SVNLog.NAME_ATTR, name);
                    command.put(SVNProperty.shortPropertyName(SVNProperty.TEXT_TIME), SVNLog.WC_TIMESTAMP);
                    log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
                    command.clear();
                    command.put(SVNLog.NAME_ATTR, name);
                    command.put(SVNProperty.shortPropertyName(SVNProperty.WORKING_SIZE), SVNLog.WC_WORKING_SIZE);
                    log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
                    command.clear();
                }
                reverted |= reinstallWorkingFile;
            }
        }
        if (entry.getConflictNew() != null) {
            command.put(SVNLog.NAME_ATTR, entry.getConflictNew());
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
            newEntryProperties.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_NEW), (String) null);
            if (!reverted) {
                reverted |= dir.getFile(entry.getConflictNew()).exists();
            }
        }
        if (entry.getConflictOld() != null) {
            command.put(SVNLog.NAME_ATTR, entry.getConflictOld());
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
            newEntryProperties.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_OLD), (String) null);
            if (!reverted) {
                reverted |= dir.getFile(entry.getConflictOld()).exists();
            }
        }
        if (entry.getConflictWorking() != null) {
            command.put(SVNLog.NAME_ATTR, entry.getConflictWorking());
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
            newEntryProperties.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_WRK), (String) null);
            if (!reverted) {
                reverted |= dir.getFile(entry.getConflictWorking()).exists();
            }
        }
        if (entry.getPropRejectFile() != null) {
            command.put(SVNLog.NAME_ATTR, entry.getPropRejectFile());
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
            newEntryProperties.put(SVNProperty.shortPropertyName(SVNProperty.PROP_REJECT_FILE), (String) null);
            if (!reverted) {
                reverted |= dir.getFile(entry.getPropRejectFile()).exists();
            }
        }
        if (entry.isScheduledForReplacement()) {
            newEntryProperties.put(SVNProperty.shortPropertyName(SVNProperty.COPIED), SVNProperty.toString(false));
            newEntryProperties.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_URL), (String) null);
            newEntryProperties.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_REVISION),
                    SVNProperty.toString(SVNRepository.INVALID_REVISION));
            if (entry.isFile() && entry.getCopyFromURL() != null) {
                String basePath = SVNAdminUtil.getTextRevertPath(name, false);
                File baseFile = dir.getFile(basePath);
                String digest = SVNFileUtil.computeChecksum(baseFile);
                newEntryProperties.put(SVNProperty.shortPropertyName(SVNProperty.CHECKSUM), digest);
            }
        }

        if (entry.getSchedule() != null) {
            newEntryProperties.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE), (String) null);
            reverted = true;
        }
        if (!newEntryProperties.isEmpty()) {
            newEntryProperties.put(SVNLog.NAME_ATTR, name);
            log.addCommand(SVNLog.MODIFY_ENTRY, newEntryProperties, false);
        }
        log.save();
        dir.runLogs();
        return reverted;
    }

    private void setWCFormat(SVNAdminAreaInfo info, SVNAdminArea area, int format) throws SVNException {
        if (!isIgnoreExternals()) {
            SVNVersionedProperties props = area.getProperties(area.getThisDirName());
            SVNVersionedProperties baseProps = area.getBaseProperties(area.getThisDirName());
            SVNPropertyValue property = props.getPropertyValue(SVNProperty.EXTERNALS);
            SVNPropertyValue baseProperty = baseProps.getPropertyValue(SVNProperty.EXTERNALS);
            if (property != null || baseProperty != null) {
                String areaPath = area.getRelativePath(info.getAnchor());
                info.addExternal(areaPath, property != null ? property.getString() : null, baseProperty != null ? baseProperty.getString() : null);
            }
        }
        // re-open this area for writing now!
        area.getWCAccess().closeAdminArea(area.getRoot());
        area = area.getWCAccess().open(area.getRoot(), true, false, false, 0, Level.FINE);
        SVNAdminArea newArea = SVNAdminAreaFactory.changeWCFormat(area, format);
        
        for(Iterator entries = newArea.entries(false); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (entry.isThisDir() || entry.isFile()) {
                continue;
            }
            File childDir = new Resource(newArea.getRoot(), entry.getName());
            SVNAdminArea childArea = newArea.getWCAccess().getAdminArea(childDir);
            if (childArea != null) {
                setWCFormat(info, childArea, format);
            }
        }
    }

    private void pushDirInfo(SVNRepository repos, SVNRevision rev, String path, SVNURL root, 
            String uuid, SVNURL url, Map locks, SVNDepth depth, ISVNInfoHandler handler) throws SVNException {
        Collection children = repos.getDir(path, rev.getNumber(), null, 
                SVNDirEntry.DIRENT_KIND | SVNDirEntry.DIRENT_CREATED_REVISION | SVNDirEntry.DIRENT_TIME | 
                SVNDirEntry.DIRENT_LAST_AUTHOR, new ArrayList());

        for (Iterator ents = children.iterator(); ents.hasNext();) {
            checkCancelled();
            SVNDirEntry child = (SVNDirEntry) ents.next();
            SVNURL childURL = url.appendPath(child.getName(), false);
            String childPath = SVNPathUtil.append(path, child.getName());
            String displayPath = repos.getFullPath(childPath);
            displayPath = displayPath.substring(repos.getLocation().getPath().length());
            if (displayPath.startsWith("/")) {
                displayPath = displayPath.substring(1);
            }
            if ("".equals(displayPath)) {
                displayPath = path;
            }
            SVNLock lock = (SVNLock) locks.get(path);
            SVNInfo info = SVNInfo.createInfo(displayPath, root, uuid, url, rev, child, lock);
            if (depth.compareTo(SVNDepth.IMMEDIATES) >= 0 || (depth == SVNDepth.FILES && 
                    child.getKind() == SVNNodeKind.FILE)) {
                handler.handleInfo(info);    
            }
            if (depth == SVNDepth.INFINITY && child.getKind() == SVNNodeKind.DIR) {
                pushDirInfo(repos, rev, SVNPathUtil.append(path, child.getName()), root, uuid, childURL, 
                        locks, depth, handler);
            }
        }
    }

    private void doGetRemoteProperty(SVNURL url, String path, SVNRepository repos, String propName, 
            SVNRevision rev, SVNDepth depth, ISVNPropertyHandler handler) throws SVNException {
        checkCancelled();
        long revNumber = getRevisionNumber(rev, repos, null);
        SVNNodeKind kind = repos.checkPath(path, revNumber);
        SVNProperties props = new SVNProperties();
        if (kind == SVNNodeKind.DIR) {
            Collection children = repos.getDir(path, revNumber, props, SVNDirEntry.DIRENT_KIND,
                    SVNDepth.FILES.compareTo(depth) <= 0 ? new ArrayList() : null);
            if (propName != null) {
                SVNPropertyValue value = props.getSVNPropertyValue(propName);
                if (value != null) {
                    handler.handleProperty(url, new SVNPropertyData(propName, value, getOptions()));
                }
            } else {
                for (Iterator names = props.nameSet().iterator(); names.hasNext();) {
                    String name = (String) names.next();
                    if (name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)
                            || name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                        continue;
                    }
                    SVNPropertyValue value = props.getSVNPropertyValue(name);
                    handler.handleProperty(url, new SVNPropertyData(name, value, getOptions()));
                }
            }
            if (SVNDepth.FILES.compareTo(depth) <= 0) {
                checkCancelled();
                for (Iterator entries = children.iterator(); entries.hasNext();) {
                    SVNDirEntry child = (SVNDirEntry) entries.next();
                    SVNURL childURL = url.appendPath(child.getName(), false);
                    String childPath = "".equals(path) ? child.getName() : SVNPathUtil.append(path, child.getName());
                    SVNDepth depthBelowHere = depth;
                    if (child.getKind() == SVNNodeKind.DIR && depth == SVNDepth.FILES) {
                        continue;
                    }
                    if (depth == SVNDepth.FILES || depth == SVNDepth.IMMEDIATES) {
                        depthBelowHere = SVNDepth.EMPTY;
                    }
                    doGetRemoteProperty(childURL, childPath, repos, propName, rev, depthBelowHere, handler);
                }
            }
        } else if (kind == SVNNodeKind.FILE) {
            repos.getFile(path, revNumber, props, null);
            if (propName != null) {
                SVNPropertyValue value = props.getSVNPropertyValue(propName);
                if (value != null) {
                    handler.handleProperty(url, new SVNPropertyData(propName, value, getOptions()));
                }
            } else {
                for (Iterator names = props.nameSet().iterator(); names.hasNext();) {
                    String name = (String) names.next();
                    if (name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)
                            || name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                        continue;
                    }
                    SVNPropertyValue value = props.getSVNPropertyValue(name);
                    handler.handleProperty(url, new SVNPropertyData(name, value, getOptions()));
                }
            }
        } else if (kind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, 
                    "''{0}'' does not exist in revision {1}", new Object[] { path, String.valueOf(revNumber) });
            SVNErrorManager.error(err, SVNLogType.WC);
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, 
                    "Unknown node kind for ''{0}''", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }

    private void doGetLocalProperty(SVNEntry entry, SVNAdminArea area, String propName, boolean base, ISVNPropertyHandler handler, SVNDepth depth, Collection changeLists) throws SVNException {
        if (depth == null || depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.EMPTY;
        }
        
        File target = area.getFile(entry.getName());
        SVNWCAccess wcAccess = area.getWCAccess();
        ISVNEntryHandler propGetHandler = new PropFetchHandler(changeLists, propName, handler, base);
        if (SVNDepth.FILES.compareTo(depth) <= 0 && entry.isDirectory()) {
            wcAccess.walkEntries(target, propGetHandler, false, depth);
        } else if (SVNWCAccess.matchesChangeList(changeLists, entry)) {
            if (propName == null) {//proplist hack for compatibility with subvsersion
                SVNVersionedProperties properties = base ? area.getBaseProperties(entry.getName()) : area.getProperties(entry.getName());
                if (propName != null) {
                    SVNPropertyValue propValue = properties.getPropertyValue(propName);
                    if (propValue != null) {
                        handler.handleProperty(target, new SVNPropertyData(propName, propValue, getOptions()));
                    }
                } else {
                    SVNProperties allProps = properties.asMap();
                    for (Iterator names = allProps.nameSet().iterator(); names.hasNext();) {
                        String name = (String) names.next();
                        SVNPropertyValue val = allProps.getSVNPropertyValue(name);
                        handler.handleProperty(area.getFile(entry.getName()), new SVNPropertyData(name, val, getOptions()));
                    }
                }
            } else {
                propGetHandler.handleEntry(target, entry);
            }
        }
    }

    private void setLocalProperties(File path, SVNEntry entry, SVNAdminArea adminArea, boolean force,
             ISVNPropertyValueProvider propertyValueProvider, ISVNPropertyHandler handler) throws SVNException {
        SVNVersionedProperties entryProperties = adminArea.getProperties(entry.getName());
        SVNProperties properties = entryProperties.asMap();
        SVNProperties unmodifiableProperties = SVNProperties.unmodifiableProperties(properties);
        SVNProperties changedProperties = propertyValueProvider.providePropertyValues(path, unmodifiableProperties);
        SVNProperties propDiff = properties.compareTo(changedProperties);

        for (Iterator iterator = propDiff.nameSet().iterator(); iterator.hasNext();) {
            String propName = (String) iterator.next();
            SVNPropertyValue propValue = propDiff.getSVNPropertyValue(propName);

            if (propValue != null && !SVNPropertiesManager.isValidPropertyName(propName)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                        "Bad property name ''{0}''", propName);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            if (SVNRevisionProperty.isRevisionProperty(propName)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                        "Revision property ''{0}'' not allowed in this context", propName);
                SVNErrorManager.error(err, SVNLogType.WC);
            } else if (SVNProperty.isWorkingCopyProperty(propName)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                        "''{0}'' is a wcprop, thus not accessible to clients", propName);
                SVNErrorManager.error(err, SVNLogType.WC);
            } else if (SVNProperty.isEntryProperty(propName)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                        "Property ''{0}'' is an entry property", propName);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            try {
                boolean modified = SVNPropertiesManager.setProperty(adminArea.getWCAccess(), path, propName,
                        propValue, force);
                if (modified && handler != null) {
                    handler.handleProperty(path, new SVNPropertyData(propName, propValue, getOptions()));
                }
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.ILLEGAL_TARGET) {
                    throw svne;
                }
            }
        }
    }

    private Map fetchLockTokens(SVNRepository repository, Map pathsTokensMap) throws SVNException {
        Map tokens = new SVNHashMap();
        for (Iterator paths = pathsTokensMap.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNLock lock = repository.getLock(path);
            if (lock == null || lock.getID() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MISSING_LOCK_TOKEN, "''{0}'' is not locked", path);
                SVNErrorManager.error(err, SVNLogType.WC);
                continue;
            }
            tokens.put(path, lock.getID());
        }
        return tokens;
    }

    private void doGetLocalFileContents(File path, OutputStream dst, SVNRevision revision, boolean expandKeywords) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        InputStream input = null;
        boolean hasMods = false;
        SVNVersionedProperties properties = null;

        try {
            SVNAdminArea area = wcAccess.open(path.getParentFile(), false, 0);
            SVNEntry entry = wcAccess.getVersionedEntry(path, false);
            if (entry.getKind() != SVNNodeKind.FILE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' refers to a directory", path);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            String name = path.getName();
            if (revision != SVNRevision.WORKING) {
                // get base version and base props.
                input = area.getBaseFileForReading(name, false);
                properties = area.getBaseProperties(name);
            } else {
                // get working version and working props.
                input = SVNFileUtil.openFileForReading(area.getFile(path.getName()), SVNLogType.WC);
                hasMods = area.hasPropModifications(name) || area.hasTextModifications(name, true);
                properties = area.getProperties(name);
            }
            String charsetProp = properties.getStringPropertyValue(SVNProperty.CHARSET);
            String eolStyle = properties.getStringPropertyValue(SVNProperty.EOL_STYLE);
            String keywords = properties.getStringPropertyValue(SVNProperty.KEYWORDS);
            boolean special = properties.getPropertyValue(SVNProperty.SPECIAL) != null;
            byte[] eols = null;
            Map keywordsMap = null;
            String time = null;
            String charset = SVNTranslator.getCharset(charsetProp, path.getPath(), getOptions());
            eols = SVNTranslator.getEOL(eolStyle, getOptions());
            if (hasMods && !special) {
                time = SVNDate.formatDate(new Date(path.lastModified()));
            } else {
                time = entry.getCommittedDate();
            }
            if (keywords != null) {
                String url = entry.getURL();
                String author = hasMods ? "(local)" : entry.getAuthor();
                String rev = hasMods ? entry.getCommittedRevision() + "M" : entry.getCommittedRevision() + "";
                keywordsMap = SVNTranslator.computeKeywords(keywords, expandKeywords ? url : null, author, time, rev, getOptions());
            }
            OutputStream translatingStream = charset != null || eols != null || keywordsMap != null ? SVNTranslator.getTranslatingOutputStream(dst, charset, eols, false, keywordsMap, expandKeywords) : dst;
            try {
                SVNTranslator.copy(input, new SVNCancellableOutputStream(translatingStream, getEventDispatcher()));
                if (translatingStream != dst) {
                    SVNFileUtil.closeFile(translatingStream);
                }
                dst.flush();
            } catch (IOExceptionWrapper ioew) {
                throw ioew.getOriginalException();
            } catch (IOException e) {
                if (e instanceof SVNCancellableOutputStream.IOCancelException) {
                    SVNErrorManager.cancel(e.getMessage(), SVNLogType.NETWORK);
                }
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()), SVNLogType.WC);
            }
        } finally {
            SVNFileUtil.closeFile(input);
            wcAccess.close();
        }
    }

    private static class LockInfo {

        public LockInfo(File file, SVNRevision rev) {
            myFile = file;
            myRevision = rev;
        }

        public LockInfo(File file, String token) {
            myFile = file;
            myToken = token;
        }

        private File myFile;
        private SVNRevision myRevision;
        private String myToken;
    }

    private class PropSetHandler implements ISVNEntryHandler {
        private boolean myIsForce;
        private String myPropName;
        private SVNPropertyValue myPropValue;
        private ISVNPropertyHandler myPropHandler;
        private Collection myChangeLists;
        
        public PropSetHandler(boolean isForce, String propName, SVNPropertyValue propValue, 
                ISVNPropertyHandler handler, Collection changeLists) {
            myIsForce = isForce;
            myPropName = propName;
            myPropValue = propValue;
            myPropHandler = handler;
            myChangeLists = changeLists;
        }

        public void handleEntry(File path, SVNEntry entry) throws SVNException {
            SVNAdminArea adminArea = entry.getAdminArea();
            if (entry.isDirectory() && !adminArea.getThisDirName().equals(entry.getName())) {
                return;
            }

            if (entry.isScheduledForDeletion()) {
                return;
            }

            if (!SVNWCAccess.matchesChangeList(myChangeLists, entry)) {
                return;
            }

            try {
                boolean modified = SVNPropertiesManager.setProperty(adminArea.getWCAccess(), path, myPropName,
                        myPropValue, myIsForce);
                if (modified && myPropHandler != null) {
                    myPropHandler.handleProperty(path, new SVNPropertyData(myPropName, myPropValue, getOptions()));
                }
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.ILLEGAL_TARGET) {
                    throw svne;
                }
            }
        }

        public void handleError(File path, SVNErrorMessage error) throws SVNException {
            SVNErrorManager.error(error, SVNLogType.WC);
        }
    }

    private class PropSetHandlerExt implements ISVNEntryHandler {
        private boolean myIsForce;
        private ISVNPropertyValueProvider myPropValueProvider;
        private ISVNPropertyHandler myPropHandler;
        private Collection myChangeLists;

        public PropSetHandlerExt(boolean isForce, ISVNPropertyValueProvider propertyValueProvider,
                ISVNPropertyHandler handler, Collection changeLists) {
            myIsForce = isForce;
            myPropValueProvider = propertyValueProvider;
            myPropHandler = handler;
            myChangeLists = changeLists;
        }

        public void handleEntry(File path, SVNEntry entry) throws SVNException {
            SVNAdminArea adminArea = entry.getAdminArea();
            if (entry.isDirectory() && !adminArea.getThisDirName().equals(entry.getName())) {
                return;
            }

            if (entry.isScheduledForDeletion()) {
                return;
            }

            if (!SVNWCAccess.matchesChangeList(myChangeLists, entry)) {
                return;
            }

            setLocalProperties(path, entry, adminArea, myIsForce, myPropValueProvider, myPropHandler);
        }

        public void handleError(File path, SVNErrorMessage error) throws SVNException {
            SVNErrorManager.error(error, SVNLogType.WC);
        }
    }
    
    private class PropFetchHandler implements ISVNEntryHandler {
        private Collection myChangeLists;
        private boolean myIsPristine;
        private String myPropName;
        private ISVNPropertyHandler myPropHandler;
        
        public PropFetchHandler(Collection changeLists, String propName, ISVNPropertyHandler handler, boolean pristine) {
            myChangeLists = changeLists;
            myIsPristine = pristine;
            myPropName = propName;
            myPropHandler = handler;
        }
        
        public void handleEntry(File path, SVNEntry entry) throws SVNException {
            SVNAdminArea adminArea = entry.getAdminArea();
            
            if (entry.isDirectory() && !entry.isThisDir()) {
                return;
            }
            if ((myIsPristine && entry.isScheduledForAddition()) || (!myIsPristine && entry.isScheduledForDeletion())) {
                return;
            }
            if (!SVNWCAccess.matchesChangeList(myChangeLists, entry)) {
                return;
            }
            
            SVNVersionedProperties properties = myIsPristine ? adminArea.getBaseProperties(entry.getName()) : adminArea.getProperties(entry.getName());
            if (myPropName != null) {
                SVNPropertyValue propValue = properties.getPropertyValue(myPropName);
                if (propValue != null) {
                    myPropHandler.handleProperty(path, new SVNPropertyData(myPropName, propValue, getOptions()));
                }
            } else {
                SVNProperties allProps = properties.asMap();
                for (Iterator names = allProps.nameSet().iterator(); names.hasNext();) {
                    String name = (String) names.next();
                    SVNPropertyValue val = allProps.getSVNPropertyValue(name);
                    myPropHandler.handleProperty(path, new SVNPropertyData(name, val, getOptions()));
                }
            }
        }
        
        public void handleError(File path, SVNErrorMessage error) throws SVNException {
            SVNErrorManager.error(error, SVNLogType.WC);
        }
    }
}
