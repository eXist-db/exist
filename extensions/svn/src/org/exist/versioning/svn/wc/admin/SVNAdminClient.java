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
package org.exist.versioning.svn.wc.admin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.exist.versioning.svn.internal.wc.DefaultDumpFilterHandler;
import org.exist.versioning.svn.internal.wc.DefaultLoadHandler;
import org.exist.versioning.svn.internal.wc.SVNAdminDeltifier;
import org.exist.versioning.svn.internal.wc.SVNAdminHelper;
import org.exist.versioning.svn.internal.wc.SVNCancellableEditor;
import org.exist.versioning.svn.internal.wc.SVNDumpEditor;
import org.exist.versioning.svn.internal.wc.SVNDumpStreamParser;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.SVNPropertiesManager;
import org.exist.versioning.svn.internal.wc.admin.SVNTranslator;
import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.exist.versioning.svn.wc.ISVNOptions;
import org.exist.versioning.svn.wc.SVNBasicClient;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSHotCopier;
import org.tmatesoft.svn.core.internal.io.fs.FSPacker;
import org.tmatesoft.svn.core.internal.io.fs.FSRecoverer;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryUtil;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNUUIDGenerator;
import org.tmatesoft.svn.core.internal.wc.ISVNLoadHandler;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.replicator.SVNRepositoryReplicator;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.ISVNAdminEventHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAction;
import org.tmatesoft.svn.core.wc.admin.SVNUUIDAction;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * The <b>SVNAdminClient</b> class provides methods that brings repository-side functionality
 * and repository synchronizing features.
 * 
 * <p>
 * Repository administrative methods are analogues of the corresponding commands of the native 
 * Subversion 'svnadmin' utility, while repository synchronizing methods are the ones for the
 * 'svnsync' utility. 
 * 
 * <p>
 * Here's a list of the <b>SVNAdminClient</b>'s methods 
 * matched against corresponing commands of the Subversion svnsync and svnadmin command-line utilities:
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="40%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>SVNKit</b></td>
 * <td><b>Subversion</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doInitialize()</td><td>'svnsync initialize'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doSynchronize()</td><td>'svnsync synchronize'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doInfo()</td><td>'svnsync info'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doCopyRevisionProperties()</td><td>'svnsync copy-revprops'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doDump()</td><td>'svnadmin dump'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doListTransactions()</td><td>'svnadmin lstxns'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doLoad()</td><td>'svnadmin load'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doRemoveTransactions()</td><td>'svnadmin rmtxns'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doVerify()</td><td>'svnadmin verify'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doPack()</td><td>'svnadmin pack'</td>
 * </tr>
 * </table>
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNAdminClient extends SVNBasicClient {

    private ISVNLogEntryHandler mySyncHandler;
    private DefaultDumpFilterHandler myDumpFilterHandler;
    private ISVNAdminEventHandler myEventHandler;
    private FSHotCopier myHotCopier;
    private SVNDumpStreamParser myDumpStreamParser;
    private SVNDumpEditor myDumpEditor;
    
    private static final int LOCK_RETRY_COUNT = 10;

    /**
     * Creates a new admin client.
     * 
     * @param authManager   an auth manager
     * @param options       an options driver
     */
    public SVNAdminClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    /**
     * Creates a new admin client.
     * 
     * @param repositoryPool a repository pool 
     * @param options        an options driver
     */
    public SVNAdminClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }

    /**
     * Sets a replication handler that will receive a log entry object 
     * per each replayed revision.
     * 
     * <p>
     * Log entries dispatched to the handler may not contain changed paths and 
     * committed log message until this features are implemented in future releases. 
     * 
     * @param handler a replay handler
     */
    public void setReplayHandler(ISVNLogEntryHandler handler) {
        mySyncHandler = handler;
    }

    /**
     * Sets an event handler for this object. 
     * {@link ISVNAdminEventHandler} should be provided to <b>SVNAdminClent</b>
     * via this method also. But it does not mean that you can have two handler set, only 
     * one handler can be used at a time.
     * 
     * @param handler an event handler
     */
    public void setEventHandler(ISVNEventHandler handler) {
        super.setEventHandler(handler);
        if (handler instanceof ISVNAdminEventHandler) {
            myEventHandler = (ISVNAdminEventHandler) handler;
        }
    }

    /**
     * Creates an FSFS-type repository.
     * 
     * This implementation uses {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory#createLocalRepository(File, String, boolean, boolean)}}.
     * <p>
     * If <code>uuid</code> is <span class="javakeyword">null</span> a new uuid will be generated, otherwise 
     * the specified will be used.
     * 
     * <p>
     * If <code>enableRevisionProperties</code> is <span class="javakeyword">true</span>, an empty 
     * pre-revprop-change hook will be placed into the repository /hooks subdir. This enables changes to 
     * revision properties of the newly created repository. 
     * 
     * <p>
     * If <code>force</code> is <span class="javakeyword">true</span> and <code>path</code> already 
     * exists, deletes that path and creates a repository in its place.
     * 
     * @param  path                        a repository root dir path
     * @param  uuid                        a repository uuid
     * @param  enableRevisionProperties    enables/disables changes to revision properties
     * @param  force                       forces operation to run
     * @return                             a local URL (file:///) of a newly created repository
     * @throws SVNException
     * @see                                #doCreateRepository(File, String, boolean, boolean, boolean)
     * @since                              1.1.0 
     */
    public SVNURL doCreateRepository(File path, String uuid, boolean enableRevisionProperties, boolean force) throws SVNException {
        return SVNRepositoryFactory.createLocalRepository(path, uuid, enableRevisionProperties, force);
    }

    /**
     * Creates an FSFS-type repository.
     * 
     * This implementation uses {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory#createLocalRepository(File, String, boolean, boolean)}}.
     * <p>
     * If <code>uuid</code> is <span class="javakeyword">null</span> a new uuid will be generated, otherwise 
     * the specified will be used.
     * 
     * <p>
     * If <code>enableRevisionProperties</code> is <span class="javakeyword">true</span>, an empty 
     * pre-revprop-change hook will be placed into the repository /hooks subdir. This enables changes to 
     * revision properties of the newly created repository. 
     * 
     * <p>
     * If <code>force</code> is <span class="javakeyword">true</span> and <code>path</code> already 
     * exists, deletes that path and creates a repository in its place.
     * 
     * <p>
     * Set <code>pre14Compatible</code> to <span class="javakeyword">true</span> if you want a new repository 
     * to be compatible with pre-1.4 servers.
     * 
     * @param  path                        a repository root dir path
     * @param  uuid                        a repository uuid
     * @param  enableRevisionProperties    enables/disables changes to revision properties
     * @param  force                       forces operation to run
     * @param  pre14Compatible             <span class="javakeyword">true</span> to 
     *                                     create a repository with pre-1.4 format
     * @return                             a local URL (file:///) of a newly created repository
     * @throws SVNException
     * @since                              1.1.1 
     */
    public SVNURL doCreateRepository(File path, String uuid, boolean enableRevisionProperties, boolean force, boolean pre14Compatible) throws SVNException {
        return doCreateRepository(path, uuid, enableRevisionProperties, force, pre14Compatible, false);
    }

    /**
     * Creates an FSFS-type repository.
     * 
     * This implementation uses {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory#createLocalRepository(File, String, boolean, boolean)}}.
     * <p>
     * If <code>uuid</code> is <span class="javakeyword">null</span> a new uuid will be generated, otherwise 
     * the specified will be used.
     * 
     * <p>
     * If <code>enableRevisionProperties</code> is <span class="javakeyword">true</span>, an empty 
     * pre-revprop-change hook will be placed into the repository /hooks subdir. This enables changes to 
     * revision properties of the newly created repository. 
     * 
     * <p>
     * If <code>force</code> is <span class="javakeyword">true</span> and <code>path</code> already 
     * exists, deletes that path and creates a repository in its place.
     * 
     * <p>
     * Set <code>pre14Compatible</code> to <span class="javakeyword">true</span> if you want a new repository 
     * to be compatible with pre-1.4 servers, <code>pre15Compatible</code> - with pre-1.5 servers and 
     * <code>pre16Compatible</code> - with pre-1.6 servers. 
     * 
     * <p>
     * There must be only one option (either <code>pre14Compatible</code> or <code>pre15Compatible</code> or <code>pre16Compatible</code>) 
     * set to <span class="javakeyword">true</span> at a time.
     * 
     * @param  path                        a repository root dir path
     * @param  uuid                        a repository uuid
     * @param  enableRevisionProperties    enables/disables changes to revision properties
     * @param  force                       forces operation to run
     * @param  pre14Compatible             <span class="javakeyword">true</span> to 
     *                                     create a repository with pre-1.4 format
     * @param  pre15Compatible             <span class="javakeyword">true</span> to
     *                                     create a repository with pre-1.5 format
     * @param  pre16Compatible             <span class="javakeyword">true</span> to
     *                                     create a repository with pre-1.6 format
     * @return                             a local URL (file:///) of a newly created repository
     * @throws SVNException
     * @since                              1.3, SVN 1.5.0 
     */
    public SVNURL doCreateRepository(File path, String uuid, boolean enableRevisionProperties, boolean force, 
            boolean pre14Compatible, boolean pre15Compatible, boolean pre16Compatible) throws SVNException {
        return SVNRepositoryFactory.createLocalRepository(path, uuid, enableRevisionProperties, force, pre14Compatible, pre15Compatible, pre16Compatible);
    }

    /**
     * Creates an FSFS-type repository.
     * 
     * This implementation uses {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory#createLocalRepository(File, String, boolean, boolean)}}.
     * <p>
     * If <code>uuid</code> is <span class="javakeyword">null</span> a new uuid will be generated, otherwise 
     * the specified will be used.
     * 
     * <p>
     * If <code>enableRevisionProperties</code> is <span class="javakeyword">true</span>, an empty 
     * pre-revprop-change hook will be placed into the repository /hooks subdir. This enables changes to 
     * revision properties of the newly created repository. 
     * 
     * <p>
     * If <code>force</code> is <span class="javakeyword">true</span> and <code>path</code> already 
     * exists, deletes that path and creates a repository in its place.
     * 
     * <p>
     * Set <code>pre14Compatible</code> to <span class="javakeyword">true</span> if you want a new repository 
     * to be compatible with pre-1.4 servers, <code>pre15Compatible</code> - with pre-1.5 servers.
     * 
     * <p>
     * There must be only one option (either <code>pre14Compatible</code> or <code>pre15Compatible</code>) 
     * set to <span class="javakeyword">true</span> at a time.
     * 
     * @param  path                        a repository root dir path
     * @param  uuid                        a repository uuid
     * @param  enableRevisionProperties    enables/disables changes to revision properties
     * @param  force                       forces operation to run
     * @param  pre14Compatible             <span class="javakeyword">true</span> to 
     *                                     create a repository with pre-1.4 format
     * @param  pre15Compatible             <span class="javakeyword">true</span> to
     *                                     create a repository with pre-1.5 format
     * @return                             a local URL (file:///) of a newly created repository
     * @throws SVNException
     * @since                              1.2, SVN 1.5.0 
     */
    public SVNURL doCreateRepository(File path, String uuid, boolean enableRevisionProperties, boolean force, 
            boolean pre14Compatible, boolean pre15Compatible) throws SVNException {
        return doCreateRepository(path, uuid, enableRevisionProperties, force, pre14Compatible, pre15Compatible, false);
    }

    
    /**
     * Copies revision properties from the source repository starting at <code>startRevision</code> and up to 
     * <code>endRevision</code> to corresponding revisions of the destination repository represented by 
     * <code>toURL</code>.
     * 
     * <p>
     * This method is equivalent to the command 'copy-revprops' of the native Subversion <i>svnsync</i> utility. 
     * Note that the destination repository given as <code>toURL</code> must be synchronized with a source 
     * repository. Please, see {@link #doInitialize(SVNURL, SVNURL)}} how to initialize such a synchronization.  
     * 
     * <p/>
     * If the caller has {@link #setEventHandler(ISVNEventHandler) provided} an event handler, the handler will 
     * receive an {@link SVNAdminEvent} with the {@link SVNAdminEventAction#REVISION_PROPERTIES_COPIED} action 
     * when the properties get copied.
     * 
     * @param  toURL          a url to the destination repository which must be synchronized
     *                        with another repository 
     * @param  startRevision  start revision
     * @param  endRevision    end revision
     * @throws SVNException   in the following cases:
     *                        <ul>
     *                        <li/>exception with {@link SVNErrorCode#IO_ERROR} error code - if any of revisions
     *                        between <code>startRevision</code> and <code>endRevision</code> inclusively was not 
     *                        synchronized yet
     *                        </ul>
     * @since                 1.2.0, new in Subversion 1.5.0
     */
    public void doCopyRevisionProperties(SVNURL toURL, long startRevision, long endRevision) throws SVNException {
        SVNRepository toRepos = null;
        SessionInfo info = null;
        SVNException error = null;
        SVNException error2 = null;
        try {
            toRepos = createRepository(toURL, null, true);
            checkIfRepositoryIsAtRoot(toRepos, toURL);
            lock(toRepos);
            info = openSourceRepository(toRepos);

            if (!SVNRevision.isValidRevisionNumber(startRevision)) {
                startRevision = info.myLastMergedRevision;
            }
            if (!SVNRevision.isValidRevisionNumber(endRevision)) {
                endRevision = info.myLastMergedRevision;
            }
            
            if (startRevision > info.myLastMergedRevision) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                        "Cannot copy revprops for a revision ({0}) that has not been synchronized yet", 
                        String.valueOf(startRevision));
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }

            if (endRevision > info.myLastMergedRevision) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                        "Cannot copy revprops for a revision ({0}) that has not been synchronized yet", 
                        String.valueOf(endRevision));
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }

            int normalizedRevPropsCount = 0;
            long step = startRevision > endRevision ? -1 : 1;
            for (long i = startRevision; i != endRevision + step; i += step) {
                checkCancelled();
                SVNProperties normalizedProps = copyRevisionProperties(info.myRepository, toRepos, i, false);
                normalizedRevPropsCount += normalizedProps.size();
            }
            handleNormalizedProperties(normalizedRevPropsCount, 0);
        } catch (SVNException svne) {
            error = svne;
        } finally {
            try {
                unlock(toRepos);
                if (toRepos != null) {
                    toRepos.closeSession();
                }
                if (info != null && info.myRepository != null) {
                    info.myRepository.closeSession();
                }
            } catch (SVNException svne) {
                error2 = svne;
            }
        }

        if (error != null) {
            throw error;
        } else if (error2 != null) {
            throw error2;
        }
    }

    /**
     * Initializes synchronization between source and target repositories.
     * 
     * <p>
     * This method is equivalent to the command 'initialize' ('init') of the native Subversion <i>svnsync</i> 
     * utility. Initialization places information of a source repository to a destination one (setting special 
     * revision properties in revision 0) as well as copies all revision props from revision 0 of the source 
     * repository to revision 0 of the destination one.   
     * 
     * @param  fromURL         a source repository url
     * @param  toURL           a destination repository url
     * @throws SVNException    in the following cases:
     *                         <ul>
     *                         <li/>exception with {@link SVNErrorCode#IO_ERROR} error code - if 
     *                         either the target repository's last revision is different from <code>0</code>, or
     *                         no {@link SVNRevisionProperty#FROM_URL} property value found in the target 
     *                         repository
     *                         <li/>exception with {@link SVNErrorCode#RA_PARTIAL_REPLAY_NOT_SUPPORTED} error 
     *                         code - if the source repository does not support partial replay
     *                         </ul>
     * @since                  1.1, new in Subversion 1.4
     */
    public void doInitialize(SVNURL fromURL, SVNURL toURL) throws SVNException {
        SVNRepository toRepos = null;
        SVNRepository fromRepos = null;
        SVNException error = null;
        SVNException error2 = null;

        try {
            toRepos = createRepository(toURL, null, true);
            checkIfRepositoryIsAtRoot(toRepos, toURL);
            lock(toRepos);
            long latestRevision = toRepos.getLatestRevision();
            if (latestRevision != 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                        "Cannot initialize a repository with content in it");
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }

            SVNPropertyValue fromURLProp = toRepos.getRevisionPropertyValue(0, SVNRevisionProperty.FROM_URL);
            if (fromURLProp != null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                        "Destination repository is already synchronizing from ''{0}''", fromURLProp);
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }

            fromRepos = createRepository(fromURL, null, false);
            SVNURL rootURL = fromRepos.getRepositoryRoot(true);
            if (SVNPathUtil.getPathAsChild(rootURL.toString(), fromURL.toString()) != null) {
                boolean supportsPartialReplay = false;
                try {
                    supportsPartialReplay = fromRepos.hasCapability(SVNCapability.PARTIAL_REPLAY);
                } catch (SVNException svne) {
                    if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.UNSUPPORTED_FEATURE) {
                        throw svne;
                    } 
                }
                if (!supportsPartialReplay) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_PARTIAL_REPLAY_NOT_SUPPORTED);
                    SVNErrorManager.error(err, SVNLogType.FSFS);
                }
            }
            toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.FROM_URL, 
                    SVNPropertyValue.create(fromURL.toDecodedString()));
            String uuid = fromRepos.getRepositoryUUID(true);
            toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.FROM_UUID, SVNPropertyValue.create(uuid));
            toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION, 
                    SVNPropertyValue.create("0"));
            SVNProperties normalizedProps = copyRevisionProperties(fromRepos, toRepos, 0, false);
            handleNormalizedProperties(normalizedProps.size(), 0);
        } catch (SVNException svne) {
            error = svne;
        } finally {
            try {
                unlock(toRepos);
                if (toRepos != null) {
                    toRepos.closeSession();
                }
                if (fromRepos != null) {
                    fromRepos.closeSession();
                }
            } catch (SVNException svne) {
                error2 = svne;
            }
        }

        if (error != null) {
            throw error;
        } else if (error2 != null) {
            throw error2;
        }
    }

    /**
     * Returns information about the synchronization repository located at <code>toURL</code>.
     * 
     * @param  toURL          destination repository url
     * @return                synchronization information
     * @throws SVNException
     * @since  1.3, SVN 1.6
     */
    public SVNSyncInfo doInfo(SVNURL toURL) throws SVNException {
        SVNRepository toRepos = null;
        try {
            toRepos = createRepository(toURL, null, true);
            checkIfRepositoryIsAtRoot(toRepos, toURL);
            SVNPropertyValue fromURL = toRepos.getRevisionPropertyValue(0, SVNRevisionProperty.FROM_URL);
            if (fromURL == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Repository ''{0}'' is not initialized for synchronization", 
                        toURL);
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }
            
            SVNPropertyValue fromUUID = toRepos.getRevisionPropertyValue(0, SVNRevisionProperty.FROM_UUID);
            SVNPropertyValue lastMergedRevProp = toRepos.getRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION);
            long lastMergedRev = lastMergedRevProp != null ? Long.parseLong(lastMergedRevProp.getString()) : SVNRepository.INVALID_REVISION;
            return new SVNSyncInfo(fromURL.getString(), fromUUID != null ? fromUUID.getString() : null, lastMergedRev);
        } finally {
            if (toRepos != null) {
                toRepos.closeSession();
            }
        }
    }
    
    /**
     * Compacts a repository into a more efficient storage model.  
     * 
     * <p/>
     * Compacting does not occur if there are no full shards. Also compacting does not work 
     * on pre-1.6 repositories.
     * 
     * @param  repositoryRoot  root of the repository to pack
     * @throws SVNException
     * @since  1.3, SVN 1.6
     */
    public void doPack(File repositoryRoot) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot, true);
        try {
            FSPacker packer = new FSPacker(myEventHandler);
            packer.pack(fsfs);
        } finally {
            SVNAdminHelper.closeRepository(fsfs);
        }
            
    }
    
    /**
     * Completely synchronizes two repositories.
     * 
     * <p>
     * This method initializes the destination repository and then copies all revision
     * changes (including revision properties) 
     * from the given source repository to the destination one. First it 
     * tries to use synchronization features similar to the native Subversion 
     * 'svnsync' capabilities. But if a server does not support 
     * <code>replay</code> functionality, SVNKit uses its own repository 
     * replication feature (see {@link org.tmatesoft.svn.core.replicator.SVNRepositoryReplicator}})
     * 
     * @param  fromURL        a url of a repository to copy from     
     * @param  toURL          a destination repository url
     * @throws SVNException
     * @since                 1.1
     */
    public void doCompleteSynchronize(SVNURL fromURL, SVNURL toURL) throws SVNException {
        try {
            doInitialize(fromURL, toURL);
            doSynchronize(toURL);
            return;
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.RA_NOT_IMPLEMENTED) {
                throw svne;
            }
        }

        SVNRepositoryReplicator replicator = SVNRepositoryReplicator.newInstance();
        SVNRepository fromRepos = null;
        SVNRepository toRepos = null;
        try {
            fromRepos = createRepository(fromURL, null, true);
            toRepos = createRepository(toURL, null, false);
            replicator.replicateRepository(fromRepos, toRepos, 1, -1);
        } finally {
            if (fromRepos != null) {
                fromRepos.closeSession();
            }
            if (toRepos != null) {
                toRepos.closeSession();
            }
        }
    }

    /**
     * Synchronizes the repository at the given url.
     * 
     * <p>
     * Synchronization means copying revision changes and revision properties from the source 
     * repository (that the destination one is synchronized with) to the destination one starting at 
     * the last merged revision. This method is equivalent to the command 'synchronize' ('sync') of 
     * the native Subversion <i>svnsync</i> utility. 
     * 
     * @param  toURL          a destination repository url
     * @throws SVNException
     * @since                 1.1, new in Subversion 1.4
     */
    public void doSynchronize(SVNURL toURL) throws SVNException {
        SVNRepository toRepos = null;
        SVNRepository fromRepos = null;
        SVNException error = null;
        SVNException error2 = null;

        try {
            toRepos = createRepository(toURL, null, true);
            checkIfRepositoryIsAtRoot(toRepos, toURL);
            lock(toRepos);
            
            SessionInfo info = openSourceRepository(toRepos);
            fromRepos = info.myRepository;
            long lastMergedRevision = info.myLastMergedRevision;
            SVNPropertyValue currentlyCopying = toRepos.getRevisionPropertyValue(0, 
                    SVNRevisionProperty.CURRENTLY_COPYING);
            long toLatestRevision = toRepos.getLatestRevision();
            int normalizedRevPropsCount = 0;

            if (currentlyCopying != null) {
                long copyingRev = -1;
                try {
                    copyingRev = Long.parseLong(currentlyCopying.getString());
                } catch (NumberFormatException nfe) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, nfe), SVNLogType.WC);
                }
                if (copyingRev < lastMergedRevision || copyingRev > lastMergedRevision + 1 || 
                        (toLatestRevision != lastMergedRevision && toLatestRevision != copyingRev)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                            "Revision being currently copied ({0}), last merged revision ({1}), and destination HEAD ({2}) are inconsistent; have you committed to the destination without using svnsync?",
                            new Object[] { String.valueOf(copyingRev), String.valueOf(lastMergedRevision), 
                            String.valueOf(toLatestRevision) });
                    SVNErrorManager.error(err, SVNLogType.FSFS);
                } else if (copyingRev == toLatestRevision) {
                    if (copyingRev > lastMergedRevision) {
                        SVNProperties normalizedProps = copyRevisionProperties(fromRepos, toRepos, toLatestRevision, true);
                        normalizedRevPropsCount += normalizedProps.size();
                        lastMergedRevision = copyingRev;
                    }
                    toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION, SVNPropertyValue.create(SVNProperty.toString(lastMergedRevision)));
                    toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING, null);
                } 
            } else {
                if (toLatestRevision != lastMergedRevision) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                            "Destination HEAD ({0}) is not the last merged revision ({1}); have you committed to the destination without using svnsync?", 
                            new Object[] { String.valueOf(toLatestRevision), String.valueOf(lastMergedRevision) });
                    SVNErrorManager.error(err, SVNLogType.FSFS);
                }
            }

            long fromLatestRevision = fromRepos.getLatestRevision();
            if (fromLatestRevision < lastMergedRevision) {
                return;
            }

            boolean hasCommitRevPropCapability = toRepos.hasCapability(SVNCapability.COMMIT_REVPROPS);
            checkCancelled();
            
            long startRevision = lastMergedRevision + 1;
            long endRevision = fromLatestRevision;

            SVNReplayHandler replayHandler = new SVNReplayHandler(toRepos, hasCommitRevPropCapability, 
                    mySyncHandler, getDebugLog(), this, this);
            
            fromRepos.replayRange(startRevision, endRevision, 0, true, replayHandler);
            handleNormalizedProperties(normalizedRevPropsCount + replayHandler.getNormalizedRevPropsCount(), 
                    replayHandler.getNormalizedNodePropsCount());
        } catch (SVNException svne) {
            error = svne;
        } finally {
            try {
                unlock(toRepos);
                if (toRepos != null) {
                    toRepos.closeSession();
                }
                if (fromRepos != null) {
                    fromRepos.closeSession();
                }
            } catch (SVNException svne) {
                error2 = svne;
            }
        }

        if (error != null) {
            throw error;
        } else if (error2 != null) {
            throw error2;
        }
    }

    /**
     * Walks through the repository tree found under <code>repositoryRoot</code> and reports all found locks 
     * via calls to the caller's {@link ISVNAdminEventHandler} handler implementation.
     * 
     * <p/>
     * On each locked path found this method dispatches an {@link SVNAdminEventAction#LOCK_LISTED} 
     * {@link SVNAdminEvent event} to the caller's handler providing the 
     * {@link SVNAdminEvent#getLock() lock information}. 
     * 
     * @param  repositoryRoot   repository root location
     * @throws SVNException
     * @since                   1.2.0, SVN 1.5.0  
     */
    public void doListLocks(File repositoryRoot) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot, true);
        try {
            File digestFile = fsfs.getDigestFileFromRepositoryPath("/");
            ISVNLockHandler handler = new ISVNLockHandler() {
                public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                    checkCancelled();
                    if (myEventHandler != null) {
                        SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.LOCK_LISTED, lock, error, null);
                        myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                    }
                    
                }
                public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                }
            };
            fsfs.walkDigestFiles(digestFile, handler, false);
        } finally {
            SVNAdminHelper.closeRepository(fsfs);
        }
    }

    /**
     * Removes locks from the specified <code>paths</code> in the repository found under 
     * <code>repositoryRoot</code>.
     * 
     * <p/>
     * If any path of the <code>paths</code> is not locked, then an {@link SVNAdminEvent event} with the 
     * {@link SVNAdminEventAction#NOT_LOCKED} action is dispatched to the caller's {@link ISVNAdminEventHandler} 
     * handler.
     * 
     * <p/>
     * If, on the contrary, a path is locked, it is unlocked and an event with the 
     * {@link SVNAdminEventAction#UNLOCKED} action is dispatched to the caller's handler providing the 
     * {@link SVNAdminEvent#getLock() lock information}.
     * 
     * <p/>
     * If some error occurs while unlocking, an event with the {@link SVNAdminEventAction#UNLOCK_FAILED} action 
     * is dispatched to the caller's handler providing the {@link SVNAdminEvent#getError() error description}.
     * 
     * @param  repositoryRoot       repository root location 
     * @param  paths                paths to unlock
     * @throws SVNException 
     * @since                       1.2.0, SVN 1.5.0  
     */
    public void doRemoveLocks(File repositoryRoot, String[] paths) throws SVNException {
        if (paths == null) {
            return;
        }
        
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot, true);
        try {
            for (int i = 0; i < paths.length; i++) {
                String path = paths[i];
                if (path == null) {
                    continue;
                }
                checkCancelled();
                
                SVNLock lock = null;
                try {
                    lock = fsfs.getLockHelper(path, false);
                    if (lock == null) {
                        if (myEventHandler != null) {
                            SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.NOT_LOCKED, null, null, "Path '" + path + "' isn't locked.");
                            myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                        }
                        continue;
                    }
                    
                    fsfs.unlockPath(path, lock.getID(), null, true, false);
                    if (myEventHandler != null) {
                        SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.UNLOCKED, lock, null, "Removed lock on '" + path + "'.");
                        myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                    }
                } catch (SVNException svne) {
                    if (myEventHandler != null) {
                        SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.UNLOCK_FAILED, lock, svne.getErrorMessage(), "svnadmin: " + svne.getErrorMessage().getFullMessage());
                        myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                    }
                }
            }
        } finally {
            SVNAdminHelper.closeRepository(fsfs);
        }
    }
    
    /**
     * Lists all uncommitted transactions.
     * On each uncommetted transaction found this method fires an {@link SVNAdminEvent} 
     * with action set to {@link SVNAdminEventAction#TRANSACTION_LISTED} to the registered 
     * {@link ISVNAdminEventHandler} (if any). To register your <b>ISVNAdminEventHandler</b> 
     * pass it to {@link #setEventHandler(ISVNEventHandler)}. For this operation the following 
     * information can be retrieved out of {@link SVNAdminEvent}:
     * <ul>
     * <li>transaction name - use {@link SVNAdminEvent#getTxnName() SVNAdminEvent.getTxnName()} to get it</li>
     * <li>transaction directory - use {@link SVNAdminEvent#getTxnDir() SVNAdminEvent.getTxnDir()} to get it</li>
     * </ul>
     * 
     * @param  repositoryRoot   a repository root directory path
     * @throws SVNException
     * @since                   1.1.1
     */
    public void doListTransactions(File repositoryRoot) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot, true);
        try {
            Map txns = fsfs.listTransactions();
    
            for(Iterator names = txns.keySet().iterator(); names.hasNext();) {
                String txnName = (String) names.next();
                File txnDir = (File) txns.get(txnName);
                if (myEventHandler != null) {
                    SVNAdminEvent event = new SVNAdminEvent(txnName, txnDir, SVNAdminEventAction.TRANSACTION_LISTED);
                    myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                }
            }
        } finally {
            SVNAdminHelper.closeRepository(fsfs);
        }
    }
    
    /**
     * Removes the specified outstanding transactions from a repository.
     * On each transaction removed this method fires an {@link SVNAdminEvent} 
     * with action set to {@link SVNAdminEventAction#TRANSACTION_REMOVED} to the registered 
     * {@link ISVNAdminEventHandler} (if any). To register your <b>ISVNAdminEventHandler</b> 
     * pass it to {@link #setEventHandler(ISVNEventHandler)}. For this operation the following 
     * information can be retrieved out of {@link SVNAdminEvent}:
     * <ul>
     * <li>transaction name - use {@link SVNAdminEvent#getTxnName() SVNAdminEvent.getTxnName()} to get it</li>
     * <li>transaction directory - use {@link SVNAdminEvent#getTxnDir() SVNAdminEvent.getTxnDir()} to get it</li>
     * </ul>
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  transactions     an array with transaction names
     * @throws SVNException
     * @since                   1.1.1
     */
    public void doRemoveTransactions(File repositoryRoot, String[] transactions) throws SVNException {
        if (transactions == null) {
            return;
        }

        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot, true);
        try {
            for (int i = 0; i < transactions.length; i++) {
                String txnName = transactions[i];
                fsfs.openTxn(txnName);
                fsfs.purgeTxn(txnName);
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.FSFS, "Transaction '" + txnName + "' removed.\n");
                if (myEventHandler != null) {
                    SVNAdminEvent event = new SVNAdminEvent(txnName, fsfs.getTransactionDir(txnName), SVNAdminEventAction.TRANSACTION_REMOVED);
                    myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                }
            }
        } finally {
            SVNAdminHelper.closeRepository(fsfs);
        }
    }

    /**
     * Verifies the data stored in the repository. This method uses the dump implementation 
     * (non incremental, beginning with revision 0, ending at the latest one) 
     * passing a dummy output stream to it. This allows to check the integrity of the 
     * repository data. 
     * 
     * <p/>
     * This is identical to <code>doVerify(repositoryRoot, SVNRevision.create(0), SVNRevision.HEAD)</code>.
     * 
     * @param  repositoryRoot   a repository root directory path
     * @throws SVNException     verification failed - a repository may be corrupted
     * @since                   1.1.1
     */
    public void doVerify(File repositoryRoot) throws SVNException {
        doVerify(repositoryRoot, SVNRevision.create(0), SVNRevision.HEAD);
    }

    /**
     * Verifies repository contents found under <code>repositoryRoot</code> starting at <code>startRevision</code>
     * and up to <code>endRevision</code>. This method uses the dump implementation 
     * (non incremental) passing a dummy output stream to it. This allows to check the integrity of the 
     * repository data. 
     * 
     * <p/> 
     * If <code>startRevision</code> is {@link SVNRevision#isValid() invalid}, it defaults to <code>0</code>.
     * If <code>endRevision</code> is {@link SVNRevision#isValid() invalid}, it defaults to the HEAD revision.
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  startRevision    revision to start verification at
     * @param  endRevision      revision to stop verification at
     * @throws SVNException     verification failed - a repository may be corrupted
     * @since                   1.2.0, SVN 1.5.0
     */
    public void doVerify(File repositoryRoot, SVNRevision startRevision, SVNRevision endRevision) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot, true);
        try {
            long youngestRevision = fsfs.getYoungestRevision();
            
            long lowerRev = SVNAdminHelper.getRevisionNumber(startRevision, youngestRevision, fsfs);
            long upperRev = SVNAdminHelper.getRevisionNumber(endRevision, youngestRevision, fsfs);

            if (!SVNRevision.isValidRevisionNumber(upperRev)) {
                upperRev = lowerRev;
            }

            verify(fsfs, lowerRev, upperRev);
        } finally {
            SVNAdminHelper.closeRepository(fsfs);
        }
    }
    
    /**
     * Dumps contents of the repository to the provided output stream in a 
     * 'dumpfile' portable format.
     * 
     * <p>
     * On each revision dumped this method fires an {@link SVNAdminEvent} 
     * with action set to {@link SVNAdminEventAction#REVISION_DUMPED} to the registered 
     * {@link ISVNAdminEventHandler} (if any). To register your <b>ISVNAdminEventHandler</b> 
     * pass it to {@link #setEventHandler(ISVNEventHandler)}. For this operation the following 
     * information can be retrieved out of {@link SVNAdminEvent}:
     * <ul>
     * <li>dumped revision - use {@link SVNAdminEvent#getRevision() SVNAdminEvent.getRevision()} to get it</li>
     * </ul>
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  dumpStream       an output stream to write dumped contents to
     * @param  startRevision    the first revision to start dumping from
     * @param  endRevision      the last revision to end dumping at
     * @param  isIncremental    if <span class="javakeyword">true</span> 
     *                          then the first revision dumped will be a 
     *                          diff against the previous revision; otherwise 
     *                          the first revision is a fulltext. 
     * @param  useDeltas        if <span class="javakeyword">true</span> 
     *                          deltas will be written instead of fulltexts
     * @throws SVNException
     * @since                   1.1.1
     */
    public void doDump(File repositoryRoot, OutputStream dumpStream, SVNRevision startRevision, SVNRevision endRevision, boolean isIncremental, boolean useDeltas) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot, true);
        try {
            long youngestRevision = fsfs.getYoungestRevision();
            
            long lowerR = SVNAdminHelper.getRevisionNumber(startRevision, youngestRevision, fsfs);
            long upperR = SVNAdminHelper.getRevisionNumber(endRevision, youngestRevision, fsfs);
            
            if (!SVNRevision.isValidRevisionNumber(lowerR)) {
                lowerR = 0;
                upperR = youngestRevision;
            } else if (!SVNRevision.isValidRevisionNumber(upperR)) {
                upperR = lowerR; 
            }
            
            if (lowerR > upperR) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "First revision cannot be higher than second");
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }
            
            dump(fsfs, dumpStream, lowerR, upperR, isIncremental, useDeltas);
        } finally {
            SVNAdminHelper.closeRepository(fsfs);
        }
    }
    
    /**
     * Reads the provided dump stream committing new revisions to a repository.
     * 
     * <p>
     * On each revision loaded this method fires an {@link SVNAdminEvent} 
     * with action set to {@link SVNAdminEventAction#REVISION_LOADED} to the registered 
     * {@link ISVNAdminEventHandler} (if any). To register your <b>ISVNAdminEventHandler</b> 
     * pass it to {@link #setEventHandler(ISVNEventHandler)}. For this operation the following 
     * information can be retrieved out of {@link SVNAdminEvent}:
     * <ul>
     * <li>original revision - use {@link SVNAdminEvent#getOriginalRevision() SVNAdminEvent.getOriginalRevision()} to get it</li>
     * <li>new committed revision - use {@link SVNAdminEvent#getRevision() SVNAdminEvent.getRevision()} to get it</li>
     * </ul>
     * 
     * <p>
     * A call to this method is equivalent to 
     * <code>doLoad(repositoryRoot, dumpStream, false, false, SVNUUIDAction.DEFAULT, null)</code>.
     * 
     * @param  repositoryRoot   the root directory path of the repository where 
     *                          new revisions will be committed
     * @param  dumpStream       stream with dumped contents of a repository
     * @throws SVNException
     * @see                     #doLoad(File, InputStream, boolean, boolean, SVNUUIDAction, String)                     
     * @since                   1.1.1
     */
    public void doLoad(File repositoryRoot, InputStream dumpStream) throws SVNException {
        doLoad(repositoryRoot, dumpStream, false, false, SVNUUIDAction.DEFAULT, null);
    }

    /**
     * Reads the provided dump stream committing new revisions to a repository.
     * 
     * <p>
     * On each revision loaded this method fires an {@link SVNAdminEvent} 
     * with action set to {@link SVNAdminEventAction#REVISION_LOADED} to the registered 
     * {@link ISVNAdminEventHandler} (if any). To register your <b>ISVNAdminEventHandler</b> 
     * pass it to {@link #setEventHandler(ISVNEventHandler)}. For this operation the following 
     * information can be retrieved out of {@link SVNAdminEvent}:
     * <ul>
     * <li>original revision - use {@link SVNAdminEvent#getOriginalRevision() SVNAdminEvent.getOriginalRevision()} to get it</li>
     * <li>new committed revision - use {@link SVNAdminEvent#getRevision() SVNAdminEvent.getRevision()} to get it</li>
     * </ul>
     * 
     * @param  repositoryRoot    the root directory path of the repository where 
     *                           new revisions will be committed
     * @param  dumpStream        stream with dumped contents of a repository
     * @param  usePreCommitHook  if <span class="javakeyword">true</span> 
     *                           then calls a pre-commit hook before committing 
     * @param  usePostCommitHook if <span class="javakeyword">true</span> 
     *                           then calls a post-commit hook after committing
     * @param  uuidAction        one of the three possible ways to treat uuids 
     * @param  parentDir         if not <span class="javakeyword">null</span> 
     *                           then loads at this directory in the repository
     * @throws SVNException
     * @since                       1.1.1
     */
    public void doLoad(File repositoryRoot, InputStream dumpStream, boolean usePreCommitHook, 
            boolean usePostCommitHook, SVNUUIDAction uuidAction, String parentDir) throws SVNException {
        CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
        FSFS fsfs = null;
        try {
            fsfs = SVNAdminHelper.openRepository(repositoryRoot, true);
            ISVNLoadHandler handler = createLoadHandler(fsfs, usePreCommitHook, usePostCommitHook, 
                    uuidAction, parentDir);
            SVNDumpStreamParser parser = getDumpStreamParser();
            parser.parseDumpStream(dumpStream, handler, decoder);
        } finally {
            SVNAdminHelper.closeRepository(fsfs);
        }
    }

    /**
     * Recovers the repository found under <code>repositoryRoot</code>.
     * This method can recover only FSFS type repositories and is identical to the <code>'svnadmin recover'</code>
     * command.
     * 
     * <p/>
     * If the caller has {@link #setEventHandler(ISVNEventHandler) provided} an event handler, the handler will 
     * receive an {@link SVNAdminEvent} with the {@link SVNAdminEventAction#RECOVERY_STARTED} action before 
     * the recovery starts.
     * 
     * @param  repositoryRoot    repository root location 
     * @throws SVNException 
     * @since                    1.2.0, SVN 1.5.0
     */
    public void doRecover(File repositoryRoot) throws SVNException {
        FSFS fsfs = null;
        try {
            fsfs = SVNAdminHelper.openRepositoryForRecovery(repositoryRoot);
            
            if (myEventHandler != null) {
                SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.RECOVERY_STARTED);
                myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
            }
            FSRecoverer recoverer = new FSRecoverer(fsfs, this);
            recoverer.runRecovery();
        } finally {
            if (fsfs != null) {
                fsfs.close();
            }
        }
    }
    
    /**
     * Upgrades the repository located at <code>repositoryRoot</code> to the latest supported
     * schema version. This method is identical to the <code>'svnadmin upgrade'</code> command.
     * 
     * <p/>
     * This functionality is provided as a convenience for repository administrators who wish to make use of 
     * new Subversion functionality without having to undertake a potentially costly full repository dump 
     * and load operation. As such, the upgrade performs only the minimum amount of work needed to accomplish 
     * this while still maintaining the integrity of the repository. It does not guarantee the most optimized 
     * repository state as a dump and subsequent load would.
     *
     * <p/>
     * If the caller has {@link #setEventHandler(ISVNEventHandler) provided} an event handler, the handler will 
     * receive an {@link SVNAdminEvent} with the {@link SVNAdminEventAction#UPGRADE} action before 
     * the upgrade starts.
     * 
     * @param  repositoryRoot   repository root location
     * @throws SVNException 
     * @since                   1.2.0, SVN 1.5.0
     */
    public void doUpgrade(File repositoryRoot)throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot, true);
        try {
            if (myEventHandler != null) {
                SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.UPGRADE);
                myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
            }
            
            File reposFormatFile = fsfs.getRepositoryFormatFile();
            int format = fsfs.getReposFormat();
            SVNFileUtil.writeVersionFile(reposFormatFile, format);
            fsfs.upgrade();
            SVNFileUtil.writeVersionFile(reposFormatFile, FSFS.REPOSITORY_FORMAT);
        } finally {
            SVNAdminHelper.closeRepository(fsfs);
        }
    }
    
    /**
     * Resets the repository UUID with the provided <code>uuid</code> for the repository located at 
     * <code>repositoryRoot</code>. This method is identical to the <code>'svnadmin setuuid'</code> command.
     * 
     * <p/>
     * If no <code>uuid</code> is specified, then <code>SVNKit</code> will generate a new one and will use it to 
     * reset the original UUID.
     * 
     * @param  repositoryRoot   repository root location
     * @param  uuid             new UUID to set
     * @throws SVNException     exception with {@link SVNErrorCode#BAD_UUID} error code - if the <code>uuid</code>
     *                          is malformed 
     * @since                   1.2.0, SVN 1.5.0
     */
    public void doSetUUID(File repositoryRoot, String uuid) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot, true);
        try {
            if (uuid == null) {
                uuid = SVNUUIDGenerator.generateUUIDString();
            } else {
                String[] components = uuid.split("-");
                if (components.length != 5) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_UUID, "Malformed UUID ''{0}''", 
                            uuid);
                    SVNErrorManager.error(err, SVNLogType.FSFS);
                }
            }
            fsfs.setUUID(uuid);
        } finally {
            SVNAdminHelper.closeRepository(fsfs);
        }
    }
    
    /**
     * Makes a hot copy of a repository located at <code>srcRepositoryRoot</code> to one located at 
     * <code>newRepositoryRoot</code>. This method is identical to the <code>'svnadmin hotcopy'</code> command.
     * 
     * @param  srcRepositoryRoot   repository to copy data from 
     * @param  newRepositoryRoot   repository to copy data to
     * @throws SVNException 
     * @since                       1.2.0, SVN 1.5.0
     */
    public void doHotCopy(File srcRepositoryRoot, File newRepositoryRoot) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(srcRepositoryRoot, false);
        try {
            FSHotCopier copier = getHotCopier();
            copier.runHotCopy(fsfs, newRepositoryRoot);
        } finally {
            SVNAdminHelper.closeRepository(fsfs);
        }
    }
    
    /**
     * Returns the HEAD revision of the repository located at <code>repositoryRoot</code>.
     * 
     * <p/>
     * Identical to the <code>'svnlook youngest'</code> svn command.
     * 
     * @param  repositoryRoot  repository root location
     * @return                 the last revision
     * @throws SVNException 
     * @since                  1.2.0
     */
    public long getYoungestRevision(File repositoryRoot) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot, true);
        try {
            return fsfs.getYoungestRevision();
        } finally {
            SVNAdminHelper.closeRepository(fsfs);
        }
    }
    
    /**
     * Filters out nodes with or without the given <code>prefixes</code> from <code>dumpStream</code> 
     * to <code>resultDumpStream</code>. This method is similar to the functionality provided by the 
     * <code>'svndumpfilter'</code> utility.
     * 
     * <p/>
     * If <code>exclude</code> is <span class="javakeyword">true</span> then filters out nodes with 
     * <code>prefixes</code>, otherwise nodes without <code>prefixes</code>.
     * 
     * <p/>
     * If the caller has {@link #setEventHandler(ISVNEventHandler) provided} an event handler, the handler will 
     * be called with different actions:
     * <ul>
     * <li/>{@link SVNAdminEventAction#DUMP_FILTER_TOTAL_REVISIONS_DROPPED} - use {@link SVNAdminEvent#getDroppedRevisionsCount()} 
     * to retrieve the total number of dropped revisions.
     * <li/>{@link SVNAdminEventAction#DUMP_FILTER_DROPPED_RENUMBERED_REVISION} - is sent only when 
     * <code>renumberRevisions</code> is <span class="javakeyword">true</span> and informs that an original 
     * revision (which is provided as {@link SVNAdminEvent#getRevision()}) was dropped.
     * <li/>{@link SVNAdminEventAction#DUMP_FILTER_RENUMBERED_REVISION} - is sent only when 
     * <code>renumberRevisions</code> is <span class="javakeyword">true</span> and informs that the original 
     * revision (provided as {@link SVNAdminEvent#getOriginalRevision()}) was renumbered to {@link SVNAdminEvent#getRevision()}.  
     * <li/>{@link SVNAdminEventAction#DUMP_FILTER_DROPPED_NODE} - says that {@link SVNAdminEvent#getPath()} was 
     * dropped.
     * <li/>{@link SVNAdminEventAction#DUMP_FILTER_TOTAL_NODES_DROPPED} - use {@link SVNAdminEvent#getDroppedNodesCount()} 
     * to retrieve the total number of dropped nodes.
     * <li/>{@link SVNAdminEventAction#DUMP_FILTER_REVISION_COMMITTED} - is sent to inform that the original 
     * revision {@link SVNAdminEvent#getOriginalRevision()} resulted in {@link SVNAdminEvent#getRevision()} 
     * in the output.
     * <li/>{@link SVNAdminEventAction#DUMP_FILTER_REVISION_SKIPPED} - is sent to inform that the original 
     * revision {@link SVNAdminEvent#getRevision()} is dropped (skipped).
     * </ul>
     * 
     * @param  dumpStream                   the input repository dump stream
     * @param  resultDumpStream             the resultant (filtered) dump stream
     * @param  exclude                      whether to exclude or include paths with the specified 
     *                                      <code>prefixes</code> 
     * @param  renumberRevisions            if <span class="javakeyword">true</span>, renumbers revisions left
     *                                      after filtering
     * @param  dropEmptyRevisions           if <span class="javakeyword">true</span>, then removes revisions
     *                                      emptied by filtering  
     * @param  preserveRevisionProperties   if <span class="javakeyword">true</span>, then does not filter
     *                                      revision properties
     * @param  prefixes                     prefixes of the path to filter 
     * @param  skipMissingMergeSources      if <span class="javakeyword">true</span>, then skips missig merge 
     *                                      sources
     * @throws SVNException 
     * @since                               1.2.0, SVN 1.5.0
     */
    public void doFilter(InputStream dumpStream, OutputStream resultDumpStream, boolean exclude, 
            boolean renumberRevisions, boolean dropEmptyRevisions, boolean preserveRevisionProperties, 
            Collection prefixes, boolean skipMissingMergeSources) throws SVNException {
        CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
        
        writeDumpData(resultDumpStream, SVNAdminHelper.DUMPFILE_MAGIC_HEADER + ": 2\n\n");
        
        DefaultDumpFilterHandler handler = getDumpFilterHandler(resultDumpStream, exclude, renumberRevisions, 
                dropEmptyRevisions, preserveRevisionProperties, prefixes, skipMissingMergeSources);
        SVNDumpStreamParser parser = getDumpStreamParser();
        parser.parseDumpStream(dumpStream, handler, decoder);

        if (myEventHandler != null) {
            if (handler.getDroppedRevisionsCount() > 0) {
                String message = MessageFormat.format("Dropped {0} revision(s).", new Object[] { 
                        String.valueOf(handler.getDroppedRevisionsCount()) });
                SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.DUMP_FILTER_TOTAL_REVISIONS_DROPPED, 
                        message);
                event.setDroppedRevisionsCount(handler.getDroppedRevisionsCount());
                myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
            }
            
            if (renumberRevisions) {
                Map renumberHistory = handler.getRenumberHistory();
                Long[] reNumberedRevisions = (Long[]) renumberHistory.keySet().toArray(new Long[renumberHistory.size()]);
                Arrays.sort(reNumberedRevisions);
                for (int i = reNumberedRevisions.length; i > 0; i--) {
                    Long revision = reNumberedRevisions[i - 1];
                    DefaultDumpFilterHandler.RevisionItem revItem = (DefaultDumpFilterHandler.RevisionItem) renumberHistory.get(revision);
                    if (revItem.wasDropped()) {
                        String message = MessageFormat.format("{0} => (dropped)", new Object[] { revision.toString() });
                        SVNAdminEvent event = new SVNAdminEvent(revision.longValue(), 
                                SVNAdminEventAction.DUMP_FILTER_DROPPED_RENUMBERED_REVISION, message);
                        myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                    } else {
                        String message = MessageFormat.format("{0} => {1}", new Object[] { revision.toString(), 
                                String.valueOf(revItem.getRevision()) });
                        SVNAdminEvent event = new SVNAdminEvent(revItem.getRevision(), revision.longValue(), 
                                SVNAdminEventAction.DUMP_FILTER_RENUMBERED_REVISION, message);
                        myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                    }
                }
            }
            
            Map droppedNodes = handler.getDroppedNodes();
            if (!droppedNodes.isEmpty()) {
                String message = MessageFormat.format("Dropped {0} node(s)", new Object[] { 
                        String.valueOf(droppedNodes.size()) });
                SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.DUMP_FILTER_TOTAL_NODES_DROPPED, 
                        message);
                event.setDroppedNodesCount(droppedNodes.size());
                myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                String[] paths = (String[]) droppedNodes.keySet().toArray(new String[droppedNodes.size()]);
                Arrays.sort(paths, SVNPathUtil.PATH_COMPARATOR);
                for (int i = 0; i < paths.length; i++) {
                    String path = paths[i];
                    message = "'" + path + "'";
                    event = new SVNAdminEvent(SVNAdminEventAction.DUMP_FILTER_DROPPED_NODE, path, message);
                    myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                }
            }
        }
    }
    
    protected void handlePropertesCopied(boolean foundSyncProps, long revision) throws SVNException {
        if (myEventHandler != null) {
            String message = null;
            if (foundSyncProps) {
                message = MessageFormat.format("Copied properties for revision {0} ({1}* properties skipped).", 
                        new Object[] { String.valueOf(revision), SVNProperty.SVN_SYNC_PREFIX });
            } else {
                message = MessageFormat.format("Copied properties for revision {0}.", new Object[] { 
                        String.valueOf(revision) }); 
            }
            SVNAdminEvent event = new SVNAdminEvent(revision, SVNAdminEventAction.REVISION_PROPERTIES_COPIED,
                    message);
            myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
        }
    }
    
    protected void handleNormalizedProperties(int normalizedRevPropsCount, int normalizedNodePropsCount) throws SVNException {
        if (myEventHandler != null && (normalizedRevPropsCount > 0 || normalizedNodePropsCount > 0)) {
            String message = MessageFormat.format("NOTE: Normalized {0}* properties to LF line endings ({1} rev-props, {2} node-props).", 
                    new Object[] { SVNProperty.SVN_PREFIX, String.valueOf(normalizedRevPropsCount), String.valueOf(normalizedNodePropsCount) });
            SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.NORMALIZED_PROPERTIES, message);
            myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
        }
    }
    
    private FSHotCopier getHotCopier() {
        if (myHotCopier == null) {
            myHotCopier = new FSHotCopier();
        }
        return myHotCopier;
    }
    
    private void verify(FSFS fsfs, long startRev, long endRev) throws SVNException {
        long youngestRev = fsfs.getYoungestRevision();
        if (!SVNRevision.isValidRevisionNumber(startRev)) {
            startRev = 0;
        }
        if (!SVNRevision.isValidRevisionNumber(endRev)) {
            endRev = youngestRev;
        }
        
        if (startRev > endRev) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS, "Start revision {0} is greater than end revision {1}", 
                    new Object[] { String.valueOf(startRev), String.valueOf(endRev) });
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        
        if (endRev > youngestRev) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS, 
                    "End revision {0} is invalid (youngest revision is {1})", new Object[] { String.valueOf(endRev), 
                    String.valueOf(youngestRev) });
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        
        for (long rev = startRev; rev <= endRev; rev++) {
            FSRevisionRoot toRoot = fsfs.createRevisionRoot(rev);
            ISVNEditor editor = getDumpEditor(fsfs, toRoot, rev, startRev, "/", SVNFileUtil.DUMMY_OUT, false, true);
            editor = SVNCancellableEditor.newInstance(editor, getEventDispatcher(), getDebugLog());
            FSRepositoryUtil.replay(fsfs, toRoot, "", SVNRepository.INVALID_REVISION, false, editor);
            fsfs.getRevisionProperties(rev);
            String message = "* Verified revision " + rev + ".";
        
            if (myEventHandler != null) {
                SVNAdminEvent event = new SVNAdminEvent(rev, SVNAdminEventAction.REVISION_DUMPED, message);
                myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
            }
        }        
    }
    
    private void dump(FSFS fsfs, OutputStream dumpStream, long start, long end, boolean isIncremental, boolean useDeltas) throws SVNException {
        boolean isDumping = dumpStream != null && dumpStream != SVNFileUtil.DUMMY_OUT;
        long youngestRevision = fsfs.getYoungestRevision();
        SVNAdminDeltifier deltifier = new SVNAdminDeltifier(fsfs, SVNDepth.INFINITY, 
                false, false, false, null);
            
        if (!SVNRevision.isValidRevisionNumber(start)) {
            start = 0;
        }
        
        if (!SVNRevision.isValidRevisionNumber(end)) {
            end = youngestRevision;
        }
        
        if (dumpStream == null) {
            dumpStream = SVNFileUtil.DUMMY_OUT;
        }
        
        if (start > end) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS, 
                    "Start revision {0} is greater than end revision {1}", new Object[] { String.valueOf(start), 
                    String.valueOf(end) });
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        
        if (end > youngestRevision) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS, 
                    "End revision {0} is invalid (youngest revision is {1})", new Object[] { String.valueOf(end), 
                    String.valueOf(youngestRevision) });
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        
        if (start == 0 && isIncremental) {
            isIncremental = false;
        }
        
        String uuid = fsfs.getUUID();
        int version = SVNAdminHelper.DUMPFILE_FORMAT_VERSION;
        
        if (!useDeltas) {
            //for compatibility with SVN 1.0.x
            version--;
        }
        
        writeDumpData(dumpStream, SVNAdminHelper.DUMPFILE_MAGIC_HEADER + ": " + version + "\n\n");
        writeDumpData(dumpStream, SVNAdminHelper.DUMPFILE_UUID + ": " + uuid + "\n\n");

        for (long i = start; i <= end; i++) {
            long fromRev, toRev;
                
            checkCancelled();

            if (i == start && !isIncremental) {
                if (i == 0) {
                    writeRevisionRecord(dumpStream, fsfs, 0);
                    toRev = 0;
                    String message = (isDumping ? "* Dumped" : "* Verified") + " revision " + toRev + ".";
                    if (myEventHandler != null) {
                        SVNAdminEvent event = new SVNAdminEvent(toRev, SVNAdminEventAction.REVISION_DUMPED, message);
                        myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                    }
                    continue;
                }
                
                fromRev = 0;
                toRev = i;
            } else {
                fromRev = i - 1;
                toRev = i;
            }
            
            writeRevisionRecord(dumpStream, fsfs, toRev);
            boolean useDeltasForRevision = useDeltas && (isIncremental || i != start);
            FSRevisionRoot toRoot = fsfs.createRevisionRoot(toRev);
            ISVNEditor dumpEditor = getDumpEditor(fsfs, toRoot, toRev, start, "/", dumpStream, useDeltasForRevision, false);

            if (i == start && !isIncremental) {
                FSRevisionRoot fromRoot = fsfs.createRevisionRoot(fromRev);
                deltifier.setEditor(dumpEditor);
                deltifier.deltifyDir(fromRoot, "/", "", toRoot, "/");
            } else {
                FSRepositoryUtil.replay(fsfs, toRoot, "", -1, false, dumpEditor);
            }
            String message = (isDumping ? "* Dumped" : "* Verified") + " revision " + toRev + ".";
            if (myEventHandler != null) {
                SVNAdminEvent event = new SVNAdminEvent(toRev, SVNAdminEventAction.REVISION_DUMPED, message);
                myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
            }
        }
    }
    
    private void writeRevisionRecord(OutputStream dumpStream, FSFS fsfs, long revision) throws SVNException {
        SVNProperties revProps = fsfs.getRevisionProperties(revision);
        
        String revisionDate = revProps.getStringValue(SVNRevisionProperty.DATE);
        if (revisionDate != null) {
            SVNDate date = SVNDate.parseDate(revisionDate);
            revProps.put(SVNRevisionProperty.DATE, date.format());
        }
        
        ByteArrayOutputStream encodedProps = new ByteArrayOutputStream();
        SVNAdminHelper.writeProperties(revProps, null, encodedProps);
        
        writeDumpData(dumpStream, SVNAdminHelper.DUMPFILE_REVISION_NUMBER + ": " + revision + "\n");
        String propContents = null;
        try {
            propContents = new String(encodedProps.toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException uee) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, uee.getLocalizedMessage());
            SVNErrorManager.error(err, uee, SVNLogType.FSFS);
        }
        writeDumpData(dumpStream, SVNAdminHelper.DUMPFILE_PROP_CONTENT_LENGTH + ": " + propContents.length() + "\n");
        writeDumpData(dumpStream, SVNAdminHelper.DUMPFILE_CONTENT_LENGTH + ": " + propContents.length() + "\n\n");
        writeDumpData(dumpStream, propContents);
        writeDumpData(dumpStream, "\n");
    }
    
    private void writeDumpData(OutputStream out, String data) throws SVNException {
        try {
            out.write(data.getBytes("UTF-8"));
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe, SVNLogType.FSFS);
        }
    }
    
    private DefaultLoadHandler createLoadHandler(FSFS fsfs, boolean usePreCommitHook, 
            boolean usePostCommitHook, SVNUUIDAction uuidAction, String parentDir) {
        DefaultLoadHandler handler = new DefaultLoadHandler(usePreCommitHook, usePostCommitHook, uuidAction, 
                parentDir, myEventHandler);
        handler.setFSFS(fsfs);
        handler.setUsePreCommitHook(usePreCommitHook);
        handler.setUsePostCommitHook(usePostCommitHook);
        handler.setUUIDAction(uuidAction);
        handler.setParentDir(parentDir);
        return handler;
    }

    private DefaultDumpFilterHandler getDumpFilterHandler(OutputStream os, boolean exclude, 
            boolean renumberRevisions, boolean dropEmptyRevisions, boolean preserveRevisionProperties, 
            Collection prefixes, boolean skipMissingMergeSources) {
        if (myDumpFilterHandler == null) {
            myDumpFilterHandler = new DefaultDumpFilterHandler(os, myEventHandler, exclude, renumberRevisions, 
                    dropEmptyRevisions, preserveRevisionProperties, prefixes, skipMissingMergeSources);
        } else {
            myDumpFilterHandler.reset(os, myEventHandler, exclude, renumberRevisions, dropEmptyRevisions, 
                    preserveRevisionProperties, prefixes, skipMissingMergeSources);
        }
        return myDumpFilterHandler;
    }

    private SVNDumpStreamParser getDumpStreamParser() {
        if (myDumpStreamParser == null) {
            myDumpStreamParser = new SVNDumpStreamParser(this);
        }
        return myDumpStreamParser;
    }
    
    private SVNDumpEditor getDumpEditor(FSFS fsfs, FSRoot root, long toRevision, long oldestDumpedRevision, String rootPath, OutputStream dumpStream, 
            boolean useDeltas, boolean isVerify) {
        if (myDumpEditor == null) {
            myDumpEditor = new SVNDumpEditor(fsfs, root, toRevision, oldestDumpedRevision, rootPath, dumpStream, useDeltas, isVerify);
        } else {
            myDumpEditor.reset(fsfs, root, toRevision, oldestDumpedRevision, rootPath, dumpStream, useDeltas, isVerify);
        }
        return myDumpEditor;
    }

    private SVNProperties copyRevisionProperties(SVNRepository fromRepository, SVNRepository toRepository, 
            long revision, boolean sync) throws SVNException {
        int filteredCount = 0;
        
        SVNProperties existingRevProps = null;
        if (sync) {
            existingRevProps = toRepository.getRevisionProperties(revision, null);
        }
        
        SVNProperties revProps = fromRepository.getRevisionProperties(revision, null);
        SVNProperties normalizedProps = normalizeRevisionProperties(revProps);
        filteredCount += SVNAdminHelper.writeRevisionProperties(toRepository, revision, revProps);
        
        if (sync) {
            SVNAdminHelper.removePropertiesNotInSource(toRepository, revision, revProps, existingRevProps);
        }
        handlePropertesCopied(filteredCount > 0, revision);
        return normalizedProps;
    }

    private SessionInfo openSourceRepository(SVNRepository targetRepos) throws SVNException {
        SVNPropertyValue fromURL = targetRepos.getRevisionPropertyValue(0, SVNRevisionProperty.FROM_URL);
        SVNPropertyValue fromUUID = targetRepos.getRevisionPropertyValue(0, SVNRevisionProperty.FROM_UUID);
        SVNPropertyValue lastMergedRev = targetRepos.getRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION);

        if (fromURL == null || fromUUID == null || lastMergedRev == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Destination repository has not been initialized");
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }

        SVNURL srcURL = SVNURL.parseURIDecoded(fromURL.getString());
        SVNRepository srcRepos = createRepository(srcURL, fromUUID.getString(), false);
        try {
            return new SessionInfo(srcRepos, Long.parseLong(lastMergedRev.getString()));
        } catch (NumberFormatException nfe) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, nfe), SVNLogType.FSFS);
        }
        return null;
    }

    private void checkIfRepositoryIsAtRoot(SVNRepository repos, SVNURL url) throws SVNException {
        SVNURL reposRoot = repos.getRepositoryRoot(true);
        if (!reposRoot.equals(url)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Session is rooted at ''{0}'' but the repos root is ''{1}''", new SVNURL[] {
                    url, reposRoot
            });
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
    }

    private void lock(SVNRepository repos) throws SVNException {
        String hostName = null;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can't get local hostname");
            SVNErrorManager.error(err, e, SVNLogType.FSFS);
        }

        if (hostName.length() > 256) {
            hostName = hostName.substring(0, 256);
        }

        String lockToken = hostName + ":" + SVNUUIDGenerator.formatUUID(SVNUUIDGenerator.generateUUID());
        int i = 0;
        SVNErrorMessage childError = null;
        for (i = 0; i < LOCK_RETRY_COUNT; i++) {
            checkCancelled();
            SVNPropertyValue reposLockToken = repos.getRevisionPropertyValue(0, SVNRevisionProperty.LOCK);
            if (reposLockToken != null) {
                if (lockToken.equals(reposLockToken.getString())) {
                    return;
                }
                childError = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                        "Failed to get lock on destination repos, currently held by ''{0}''", reposLockToken.getString());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    //
                }
            } else if (i < LOCK_RETRY_COUNT - 1) {
                repos.setRevisionPropertyValue(0, SVNRevisionProperty.LOCK, SVNPropertyValue.create(lockToken));
            }
        }

        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                "Couldn''t get lock on destination repos after {0} attempts", String.valueOf(i));
        if (childError != null) {
            err.setChildErrorMessage(childError);
        }
        SVNErrorManager.error(err, SVNLogType.FSFS);
    }

    private void unlock(SVNRepository repos) throws SVNException {
        repos.setRevisionPropertyValue(0, SVNRevisionProperty.LOCK, null);
    }

    private class SessionInfo {

        SVNRepository myRepository;
        long myLastMergedRevision;

        public SessionInfo(SVNRepository repos, long lastMergedRev) {
            myRepository = repos;
            myLastMergedRevision = lastMergedRev;
        }
    }
    
    public static SVNProperties normalizeRevisionProperties(SVNProperties revProps) throws SVNException {
        SVNProperties normalizedProps = new SVNProperties();
        for (Iterator propNamesIter = revProps.nameSet().iterator(); propNamesIter.hasNext();) {
            String propName = (String) propNamesIter.next();
            if (SVNPropertiesManager.propNeedsTranslation(propName)) {
                SVNPropertyValue value = revProps.getSVNPropertyValue(propName); 
                String normalizedValue = normalizeString(SVNPropertyValue.getPropertyAsString(value));
                if (normalizedValue != null) {
                    normalizedProps.put(propName, SVNPropertyValue.create(normalizedValue));
                }
            }
        }
        revProps.putAll(normalizedProps);
        return normalizedProps;
    }

    public static String normalizeString(String string) throws SVNException {
        if (string != null && string.indexOf(SVNProperty.EOL_CR_BYTES[0]) != -1) {
            return SVNTranslator.transalteString(string, SVNProperty.EOL_LF_BYTES, null, true, false);
        }
        return null;
    }

}
