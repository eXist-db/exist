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
import java.util.Date;
import java.util.Map;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * The <b>SVNStatus</b> class is used to provide detailed status information for
 * a Working Copy item as a result of a status operation invoked by a 
 * doStatus() method of <b>SVNStatusClient</b>. <b>SVNStatus</b> objects are
 * generated for each 'interesting' local item and depending on the doStatus() method 
 * in use either passed for notification to an <b>ISVNStatusHandler</b> 
 * implementation or such an object is just returned by the method as a 
 * status info for a single item. 
 * 
 * <p>
 * Within the status handler implementation a developer decides how to interpret status 
 * information. For some purposes this way may be more flexible in comparison 
 * with calling doStatus() that returns an <b>SVNStatus</b> per one local item.
 * However the latter one may be useful when needing to find out the status of 
 * the concrete item.  
 * 
 * <p>
 * 
 * There are two approaches how to process <b>SVNStatus</b> objects:<br />
 * 1. Implementing an <b>ISVNStatusHandler</b>:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.ISVNStatusHandler;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNStatus;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNStatusType;
 * ...
 * 
 * <span class="javakeyword">public class</span> MyCustomStatusHandler <span class="javakeyword">implements</span> ISVNStatusHandler {
 *     <span class="javakeyword">public void</span> handleStatus(SVNStatus status) {
 *         <span class="javacomment">//parse the item's contents status</span>
 *         <span class="javakeyword">if</span>(status.getContentsStatus() == SVNStatusType.STATUS_MODIFIED) {
 *             ...
 *         } <span class="javakeyword">else if</span>(status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED) {
 *             ...        
 *         }
 *         ...
 *         <span class="javacomment">//parse properties status</span>
 *         <span class="javakeyword">if</span>(status.getPropertiesStatus() == SVNStatusType.STATUS_MODIFIED) {
 *             ...
 *         }
 *         ...
 *     }
 * }</pre><br />
 * ...and providing a status handler implementation to an <b>SVNStatusClient</b>'s 
 * doStatus() method:
 * <pre class="javacode">
 * ...
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNStatusClient;
 * ...
 * 
 * SVNStatusClient statusClient;
 * ...
 * 
 * statusClient.doStatus(...., <span class="javakeyword">new</span> MyCustomStatusHandler());
 * ...</pre><br />
 * 2. Or process an <b>SVNStatus</b> like this:
 * <pre class="javacode">
 * ...
 * SVNStatus status = statusClient.doStatus(<span class="javakeyword">new</span> File(myPath), <span class="javakeyword">false</span>);
 * <span class="javacomment">//parsing status info here</span>
 * ...</pre>
 * </p> 
 * <p>
 * <b>SVNStatus</b>'s methods which names start with <code>getRemote</code> are relevant
 * for remote status invocations - that is when a doStatus() method of <b>SVNStatusClient</b>
 * is called with the flag <code>remote</code> set to <span class="javakeyword">true</span>.
 *  
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     ISVNStatusHandler
 * @see     SVNStatusType
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNStatus {
    
    private SVNURL myURL;
    private File myFile;
    private SVNNodeKind myKind;
    private SVNRevision myRevision;
    private SVNRevision myCommittedRevision;
    private Date myCommittedDate;
    private String myAuthor;
    private SVNStatusType myContentsStatus;
    private SVNStatusType myPropertiesStatus;
    private SVNStatusType myRemoteContentsStatus;
    private SVNStatusType myRemotePropertiesStatus;
    private boolean myIsLocked;
    private boolean myIsCopied;
    private boolean myIsSwitched;
    private boolean myIsFileExternal;
    private File myConflictNewFile;
    private File myConflictOldFile;
    private File myConflictWrkFile;
    private File myPropRejectFile;
    private String myCopyFromURL;
    private SVNRevision myCopyFromRevision;
    private SVNLock myRemoteLock;
    private SVNLock myLocalLock;
    private Map myEntryProperties;
    private SVNRevision myRemoteRevision;
    private SVNURL myRemoteURL;
    private SVNNodeKind myRemoteKind;
    private String myRemoteAuthor;
    private Date myRemoteDate;
    private Date myLocalContentsDate;
    private Date myLocalPropertiesDate;
    private SVNEntry myEntry;
    private String myChangelistName;
    private int myWorkingCopyFormat;
    private SVNTreeConflictDescription myTreeConflict;
    
    /**
     * Constructs an <b>SVNStatus</b> object filling it with status information
     * details.  
     * 
     * <p>
     * Used by SVNKit internals to construct and initialize an 
     * <b>SVNStatus</b> object. It's not intended for users (from an API 
     * point of view).
     * 
     * @param url                      item's repository location 
     * @param file                     item's path in a File representation
     * @param kind                     item's node kind
     * @param revision                 item's working revision
     * @param committedRevision        item's last changed revision
     * @param committedDate            item's last changed date
     * @param author                   item's last commit author 
     * @param contentsStatus           local status of item's contents
     * @param propertiesStatus         local status of item's properties
     * @param remoteContentsStatus     status of item's contents against a repository
     * @param remotePropertiesStatus   status of item's properties against a repository
     * @param isLocked                 if the item is locked by the driver (not a user lock)
     * @param isCopied                 if the item is added with history 
     * @param isSwitched               if the item is switched to a different URL
     * @param isFileExternal           tells if the item is an external file
     * @param conflictNewFile          temp file with latest changes from the repository
     * @param conflictOldFile          temp file just as the conflicting one was at the BASE revision
     * @param conflictWrkFile          temp file with all user's current local modifications 
     * @param projRejectFile           temp file describing properties conflicts
     * @param copyFromURL              url of the item's ancestor from which the item was copied 
     * @param copyFromRevision         item's ancestor revision from which the item was copied
     * @param remoteLock               item's lock in the repository
     * @param localLock                item's local lock
     * @param entryProperties          item's SVN specific '&lt;entry' properties
     * @param changelistName           changelist name which the item belongs to
     * @param wcFormatVersion          working copy format number         
     * @param treeConflict             tree conflict description
     * @since 1.3
     */
    public SVNStatus(SVNURL url, File file, SVNNodeKind kind,
            SVNRevision revision, SVNRevision committedRevision,
            Date committedDate, String author, SVNStatusType contentsStatus,
            SVNStatusType propertiesStatus, SVNStatusType remoteContentsStatus,
            SVNStatusType remotePropertiesStatus, boolean isLocked,
            boolean isCopied, boolean isSwitched, boolean isFileExternal, File conflictNewFile,
            File conflictOldFile, File conflictWrkFile, File projRejectFile,
            String copyFromURL, SVNRevision copyFromRevision,
            SVNLock remoteLock, SVNLock localLock, Map entryProperties,
            String changelistName, int wcFormatVersion, SVNTreeConflictDescription treeConflict) {
        myURL = url;
        myFile = file;
        myKind = kind == null ? SVNNodeKind.NONE : kind;
        myRevision = revision == null ? SVNRevision.UNDEFINED : revision;
        myCommittedRevision = committedRevision == null ? SVNRevision.UNDEFINED
                : committedRevision;
        myCommittedDate = committedDate;
        myAuthor = author;
        myContentsStatus = contentsStatus == null ? SVNStatusType.STATUS_NONE
                : contentsStatus;
        myPropertiesStatus = propertiesStatus == null ? SVNStatusType.STATUS_NONE
                : propertiesStatus;
        myRemoteContentsStatus = remoteContentsStatus == null ? SVNStatusType.STATUS_NONE
                : remoteContentsStatus;
        myRemotePropertiesStatus = remotePropertiesStatus == null ? SVNStatusType.STATUS_NONE
                : remotePropertiesStatus;
        myIsLocked = isLocked;
        myIsCopied = isCopied;
        myIsSwitched = isSwitched;
        myIsFileExternal = isFileExternal;
        myConflictNewFile = conflictNewFile;
        myConflictOldFile = conflictOldFile;
        myConflictWrkFile = conflictWrkFile;
        myCopyFromURL = copyFromURL;
        myCopyFromRevision = copyFromRevision == null ? SVNRevision.UNDEFINED
                : copyFromRevision;
        myRemoteLock = remoteLock;
        myLocalLock = localLock;
        myPropRejectFile = projRejectFile;
        myEntryProperties = entryProperties;
        myChangelistName = changelistName;
        myWorkingCopyFormat = wcFormatVersion;
        myTreeConflict = treeConflict;
    }
    
    /**
     * Gets the item's repository location. URL is taken from the  
     * {@link org.tmatesoft.svn.core.SVNProperty#URL} property.
     * 
     * @return  the item's URL represented as an <b>SVNURL</b> object
     */
    public SVNURL getURL() {
        return myURL;
    }
    
    /**
     * Gets the item's latest repository location. 
     * For example, the item could have been moved in the repository,
     * but {@link SVNStatus#getURL() getURL()} returns the item's 
     * URL as it's defined in a URL entry property. Applicable
     * for a remote status invocation.
     * 
     * @return  the item's URL as it's real repository location 
     */
    public SVNURL getRemoteURL() {
        return myRemoteURL;
    }

    /**
     * Gets the item's path in the filesystem.
     * 
     * @return a File representation of the item's path
     */
    public File getFile() {
        return myFile;
    }
    
    /**
     * Gets the item's node kind characterizing it as an entry. 
     * 
     * @return the item's node kind (whether it's a file, directory, etc.)
     */
    public SVNNodeKind getKind() {
        return myKind;
    }
    
    /**
     * Gets the item's current working revision.
     *  
     * @return the item's working revision
     */
    public SVNRevision getRevision() {
        return myRevision;
    }
    
    /**
     * Gets the revision when the item was last changed (committed).
     * 
     * @return the last committed revision
     */
    public SVNRevision getCommittedRevision() {
        return myCommittedRevision;
    }
    
    /**
     * Gets the timestamp when the item was last changed (committed).
     * 
     * @return the last committed date 
     */
    public Date getCommittedDate() {
        return myCommittedDate;
    }
    
    /**
     * Gets the author who last changed the item.
     * 
     * @return the item's last commit author
     */
    public String getAuthor() {
        return myAuthor;
    }

    /**
     * Gets the Working Copy local item's contents status type.
     * 
     * @return the local contents status type
     */
    public SVNStatusType getContentsStatus() {
        return myContentsStatus;
    }
    
    /**
     * Gets the Working Copy local item's properties status type.
     * 
     * @return the local properties status type
     */
    public SVNStatusType getPropertiesStatus() {
        return myPropertiesStatus;
    }
    
    /**
     * Gets the Working Copy item's contents status type against the
     * repository - that is comparing the item's BASE revision and the 
     * latest one in the repository when the item was changed. 
     * Applicable for a remote status invocation.
     *
     * <p>
     * If the remote contents status type != {@link SVNStatusType#STATUS_NONE} 
     * the local file may be out of date.  
     * 
     * @return the remote contents status type
     */
    public SVNStatusType getRemoteContentsStatus() {
        return myRemoteContentsStatus;
    }
    
    /**
     * Gets the Working Copy item's properties status type against the 
     * repository - that is comparing the item's BASE revision and the 
     * latest one in the repository when the item was changed. Applicable 
     * for a remote status invocation.
     * 
     * <p>
     * If the remote properties status type != {@link SVNStatusType#STATUS_NONE} 
     * the local file may be out of date.  
     * 
     * @return the remote properties status type
     */
    public SVNStatusType getRemotePropertiesStatus() {
        return myRemotePropertiesStatus;
    }
    
    /**
     * Finds out if the item is locked (not a user lock but a driver's 
     * one when during an operation a Working Copy is locked in <i>.svn</i> 
     * administrative areas to prevent from other operations interrupting 
     * until the running one finishes).  
     * <p>
     * To clean up a Working Copy use {@link SVNWCClient#doCleanup(File) doCleanup()}.
     *  
     * @return <span class="javakeyword">true</span> if locked, otherwise
     *         <span class="javakeyword">false</span> 
     */
    public boolean isLocked() {
        return myIsLocked;
    }
    
    /**
     * Finds out if the item is added with history.
     * 
     * @return <span class="javakeyword">true</span> if the item
     *         is added with history, otherwise <span class="javakeyword">false</span>
     */
    public boolean isCopied() {
        return myIsCopied;
    }
    
    /**
     * Finds out whether the item is switched to a different
     * repository location.
     *  
     * @return <span class="javakeyword">true</span> if switched, otherwise
     *         <span class="javakeyword">false</span>
     */
    public boolean isSwitched() {
        return myIsSwitched;
    }
    
    /**
     * Tells if this is an externals file or not.
     * 
     * @return <span class="javakeyword">true</span> if is a file external, 
     *         otherwise <span class="javakeyword">false</span>
     * @since  1.3
     */
    public boolean isFileExternal() {
        return myIsFileExternal;
    }

    /**
     * Gets the temporary file that contains all latest changes from the 
     * repository which led to a conflict with local changes. This file is
     * at the HEAD revision.
     * 
     * @return  an autogenerated temporary file just as it is in the latest 
     *          revision in the repository 
     */
    public File getConflictNewFile() {
        return myConflictNewFile;
    }
    
    /**
     * Gets the temporary BASE revision file of that working file that is
     * currently in conflict with changes received from the repository. This
     * file does not contain the latest user's modifications, only 'pristine'
     * contents.  
     * 
     * @return an autogenerated temporary file just as the conflicting file was
     *         before any modifications to it
     */
    public File getConflictOldFile() {
        return myConflictOldFile;
    }
    
    /**
     * Gets the temporary <i>'.mine'</i> file with all current local changes to the 
     * original file. That is if the file item is in conflict with changes that 
     * came during an update this temporary file is created to get the snapshot
     * of the user's file with only the user's local modifications and nothing 
     * more.  
     * 
     * @return an autogenerated temporary file with only the user's modifications 
     */
    public File getConflictWrkFile() {
        return myConflictWrkFile;
    }
    
    /**
     * Gets the <i>'.prej'</i> file containing details on properties conflicts.
     * If the item's properties are in conflict with those that came
     * during an update this file will contain a conflict description. 
     * 
     * @return  the properties conflicts file
     */
    public File getPropRejectFile() {
        return myPropRejectFile;
    }
    
    /**
     * Gets the URL (repository location) of the ancestor from which the
     * item was copied. That is when the item is added with history.
     * 
     * @return the item ancestor's URL
     */
    public String getCopyFromURL() {
        return myCopyFromURL;
    }
    
    /**
     * Gets the revision of the item's ancestor
     * from which the item was copied (the item is added
     * with history). 
     * 
     * @return the ancestor's revision 
     */
    public SVNRevision getCopyFromRevision() {
        return myCopyFromRevision;
    }
    
    /**
     * Gets the file item's repository lock - 
     * applicable for a remote status invocation.
     * 
     * @return file item's repository lock
     */
    public SVNLock getRemoteLock() {
        return myRemoteLock;
    }
    
    /**
     * Gets the file item's local lock.
     * 
     * @return file item's local lock
     */
    public SVNLock getLocalLock() {
        return myLocalLock;
    }
    
    /**
     * Gets the item's SVN specific <i>'&lt;entry'</i> properties.
     * These properties' names start with 
     * {@link org.tmatesoft.svn.core.SVNProperty#SVN_ENTRY_PREFIX}.
     * 
     * @return a Map which keys are names of SVN entry properties mapped
     *         against their values (both strings)
     */
    public Map getEntryProperties() {
        return myEntryProperties;
    }
    
    /**
     * Gets the item's last committed repository revision. Relevant for a 
     * remote status invocation. 
     * 
     * @return the latest repository revision when the item was changed; 
     *         <span class="javakeyword">null</span> if there are no incoming
     *         changes for this file or directory. 
     */
    public SVNRevision getRemoteRevision() {
        return myRemoteRevision;
    }
    
    /**
     * Returns the kind of the item got from the repository. Relevant for a 
     * remote status invocation. 
     *  
     * @return a remote item kind
     */
    public SVNNodeKind getRemoteKind() {
        return myRemoteKind;
    }

    /**
     * Gets the item's last changed date. Relevant for a 
     * remote status invocation. 
     * 
     * @return a repository last changed date
     */
    public Date getRemoteDate() {
        return myRemoteDate;
    }
    
    /**
     * Gets the item's last changed author. Relevant for a 
     * remote status invocation. 
     * 
     * @return a last commit author 
     */
    public String getRemoteAuthor() {
        return myRemoteAuthor;
    }
    
    /**
     * Returns the last modified local time of the file item. 
     * Irrelevant for directories (for directories returns <code>Date(0)</code>).
     * 
     * @return last modified time of the file
     */
    public Date getWorkingContentsDate() {
        if (myLocalContentsDate == null) {
            if (getFile() != null && getKind() == SVNNodeKind.FILE) {
                myLocalContentsDate = new Date(getFile().lastModified());
            } else {
                myLocalContentsDate = new Date(0);
            }
        }
        return myLocalContentsDate;
    }
    
    /**
     * Returns the last modified local time of file or directory 
     * properties. 
     * 
     * @return last modified time of the item properties
     */
    public Date getWorkingPropertiesDate() {
        if (myLocalPropertiesDate == null) {
            File propFile = null;
            if (getFile() != null && getKind() == SVNNodeKind.DIR) {
                propFile = new Resource(getFile().getAbsoluteFile().getParentFile(), SVNFileUtil.getAdminDirectoryName());
                propFile = new Resource(propFile, "dir-props");
            } else if (getFile() != null && getKind() == SVNNodeKind.FILE) {
                propFile = new Resource(getFile().getAbsoluteFile().getParentFile(), SVNFileUtil.getAdminDirectoryName());
                propFile = new Resource(propFile, "props/" + getFile().getName() + ".svn-work");
            }
            myLocalPropertiesDate = propFile != null ? new Date(propFile.lastModified()) : new Date(0);
        }
        return myLocalPropertiesDate;
    }
    
    /**
     * Marks the item as an external. This method is used by SVNKit internals
     * and not intended for users (from an API point of view).
     *
     */
    public void markExternal() {
        myContentsStatus = SVNStatusType.STATUS_EXTERNAL;
    }
    
    /**
     * Sets the item's remote status. Used by SVNKit internals and not
     * intended for users (from an API point of view).
     * 
     * @param contents item's contents status type against the repository 
     * @param props    item's properties status type against the repository
     * @param lock     item's lock in the repository
     * @param kind     item's node kind
     */
    public void setRemoteStatus(SVNStatusType contents, SVNStatusType props, SVNLock lock, SVNNodeKind kind) {
        if (contents == SVNStatusType.STATUS_ADDED && myRemoteContentsStatus == SVNStatusType.STATUS_DELETED) {
            contents = SVNStatusType.STATUS_REPLACED;
        }
        myRemoteContentsStatus = contents != null ? contents : myRemoteContentsStatus;
        myRemotePropertiesStatus = props != null ? props : myRemotePropertiesStatus;
        if (lock != null) {
            myRemoteLock = lock;
        }
        if (kind != null) {
            myRemoteKind = kind;
        }
    }
    
    /**
     * Sets the item's remote status. Used by SVNKit internals and not
     * intended for users (from an API point of view).
     * 
     * @param url      item's repository URL
     * @param contents item's contents status type against the repository 
     * @param props    item's properties status type against the repository
     * @param lock     item's lock in the repository
     * @param kind     item's node kind
     * @param revision item's latest revision when it was last committed
     * @param date     last item's committed date 
     * @param author   last item's committed author
     */
    public void setRemoteStatus(SVNURL url, SVNStatusType contents, SVNStatusType props, SVNLock lock, SVNNodeKind kind, SVNRevision revision,
            Date date, String author) {
        setRemoteStatus(contents, props, lock, kind);
        myRemoteURL = url;
        myRemoteRevision = revision == null ? SVNRevision.UNDEFINED : revision;
        myRemoteDate = date;
        myRemoteAuthor = author;
        myRemoteKind = kind;
    }
    
    /**
     * Sets the item's contents status type. Used by SVNKit internals and not
     * intended for users (from an API point of view).
     * 
     * @param statusType status type of the item's contents
     */
    public void setContentsStatus(SVNStatusType statusType) {
        myContentsStatus = statusType;
    }
    
    /**
     * Sets a WC entry for which this object is generated.
     * Used in internals.
     * 
     * @param entry  a WC entry
     */
    public void setEntry(SVNEntry entry) {
        myEntry = entry;
    }
    
    /**
     * Returns a WC entry for which this object is generated.
     * 
     * @return a WC entry (if set)
     */
    public SVNEntry getEntry() {
        return myEntry;
    }

    /**
     * Returns the name of the changelist which the working copy item, denoted by this object,
     * belongs to.   
     * 
     * @return  changelist name  
     * @since   1.2
     */
    public String getChangelistName() {
        return myChangelistName;
    }

    /**
     * Returns a tree conflict description.
     * 
     * @return tree conflict description; <code>null</code> if 
     *         no conflict description exists on this item
     * @since  1.3
     */
    public SVNTreeConflictDescription getTreeConflict() {
        return myTreeConflict;
    }

    /**
     * Returns the working copy format number for the admin directory 
     * which the statused item is versioned under.
     * 
     * <p/>
     * If this status object is a result of a remote status operation, the method will return 
     * <code>-1</code>. 
     * 
     * @return working copy format number; <code>-1</code> for remote status
     * @since  1.2
     */
    public int getWorkingCopyFormat() {
        return myWorkingCopyFormat;
    }
}
