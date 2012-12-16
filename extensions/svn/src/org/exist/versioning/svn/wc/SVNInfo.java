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

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * The <b>SVNInfo</b> class is a wrapper for versioned item's (located either
 * in a Working Copy or a repository) information details. When running an 
 * info operation invoking a doInfo() method of the <b>SVNWCClient</b> class
 * all collected item information data is packed inside an <b>SVNInfo</b> object
 * and depending on the exact doInfo() method being in use is either dispatched to
 * an implementation of <b>ISVNInfoHandler</b> or just returned by the method (per
 * single item info operation).
 * 
 * <p>
 * There are two approaches how to process <b>SVNInfo</b> objects:<br />
 * 1. Implementing an <b>ISVNInfoHandler</b>:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.ISVNInfoHandler;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNInfo;
 * ...
 * 
 * <span class="javakeyword">public class</span> MyCustomInfoHandler <span class="javakeyword">implements</span> ISVNInfoHandler {
 *     <span class="javakeyword">public void</span> handleInfo(SVNInfo info) {
 *         <span class="javacomment">//parsing info here</span> 
 *         ...
 *     }
 * }</pre><br />
 * ...and providing an info handler implementation to an <b>SVNWCClient</b>'s 
 * doInfo() method:
 * <pre class="javacode">
 * ...
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNWCClient;
 * ...
 * 
 * SVNWCClient wcClient;
 * ...
 * 
 * wcClient.doInfo(...., <span class="javakeyword">new</span> MyCustomInfoHandler());
 * ...</pre><br />
 * 2. Or process an <b>SVNInfo</b> like this:
 * <pre class="javacode">
 * ...
 * SVNInfo info = wcClient.doInfo(<span class="javakeyword">new</span> File(myPath), SVNRevision.WORKING);
 * <span class="javacomment">//parsing info here</span>
 * ...</pre>
 * </p> 
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     ISVNInfoHandler
 * @see     SVNWCClient
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNInfo {

    private File myFile;
    private String myPath;
    private SVNURL myURL;
    private SVNRevision myRevision;
    private SVNNodeKind myKind;
    private SVNURL myRepositoryRootURL;
    private String myRepositoryUUID;
    private SVNRevision myCommittedRevision;
    private Date myCommittedDate;
    private String myAuthor;
    private SVNLock myLock;
    private boolean myIsRemote;
    private String mySchedule;
    private SVNURL myCopyFromURL;
    private SVNRevision myCopyFromRevision;
    private Date myTextTime;
    private Date myPropTime;
    private String myChecksum;
    private File myConflictOldFile;
    private File myConflictNewFile;
    private File myConflictWrkFile;
    private File myPropConflictFile;
    private SVNDepth myDepth;
    private String myChangelistName;
    private long myWorkingSize;
    private long myRepositorySize;
    private SVNTreeConflictDescription myTreeConflict;
    
    static SVNInfo createInfo(File file, SVNEntry entry) throws SVNException {
        if (entry == null) {
            return null;
        }
        SVNLock lock = null;
        if (entry.getLockToken() != null) {
            lock = new SVNLock(null, entry.getLockToken(),
                    entry.getLockOwner(), entry.getLockComment(), SVNDate.parseDate(entry.getLockCreationDate()), null);
        }
        SVNTreeConflictDescription tc = null;
        if (entry.getAdminArea() != null && entry.getAdminArea().getWCAccess() != null) {
        	tc = entry.getAdminArea().getWCAccess().getTreeConflict(file); 
        }        
        return new SVNInfo(file, entry.getSVNURL(), entry.getRepositoryRootURL(), 
                entry.getRevision(), entry.getKind(), entry.getUUID(), entry.getCommittedRevision(),
                entry.getCommittedDate(), entry.getAuthor(), entry.getSchedule(), 
                entry.getCopyFromSVNURL(), entry.getCopyFromRevision(), entry.getTextTime(), entry.getPropTime(), entry.getChecksum(), 
                entry.getConflictOld(), entry.getConflictNew(), entry.getConflictWorking(), entry.getPropRejectFile(), 
                lock, entry.getDepth(), entry.getChangelistName(), entry.getWorkingSize(), tc);
    }

    static SVNInfo createInfo(File file, SVNTreeConflictDescription tc) {
        return new SVNInfo(file, null, null, 
                -1, SVNNodeKind.NONE, null, -1,
                null, null, null, 
                null, -1, null, null, null, 
                null, null, null, null, 
                null, SVNDepth.UNKNOWN, null, -1, tc);
    }

    static SVNInfo createInfo(String path, SVNURL reposRootURL, String uuid,
            SVNURL url, SVNRevision revision, SVNDirEntry dirEntry, SVNLock lock) {
        if (dirEntry == null) {
            return null;
        }
        return new SVNInfo(path, url, revision, dirEntry.getKind(), uuid,
                reposRootURL, dirEntry.getRevision(), dirEntry.getDate(),
                dirEntry.getAuthor(), lock, SVNDepth.UNKNOWN, dirEntry.getSize());
    }

    protected SVNInfo(File file, SVNURL url, SVNURL rootURL, long revision, SVNNodeKind kind,
            String uuid, long committedRevision, String committedDate,
            String author, String schedule, SVNURL copyFromURL,
            long copyFromRevision, String textTime, String propTime,
            String checksum, String conflictOld, String conflictNew,
            String conflictWorking, String propRejectFile, SVNLock lock, 
            SVNDepth depth, String changelistName, long wcSize, SVNTreeConflictDescription treeConflict) {
        myFile = file;
        myURL = url;
        myRevision = SVNRevision.create(revision);
        myKind = kind;
        myRepositoryUUID = uuid;
        myRepositoryRootURL = rootURL;

        myCommittedRevision = SVNRevision.create(committedRevision);
        myCommittedDate = committedDate != null ? SVNDate
                .parseDate(committedDate) : null;
        myAuthor = author;

        mySchedule = schedule;
        myChecksum = checksum;
        myTextTime = textTime != null ? SVNDate.parseDate(textTime) : null;
        myPropTime = propTime != null ? SVNDate.parseDate(propTime) : null;

        myCopyFromURL = copyFromURL;
        myCopyFromRevision = SVNRevision.create(copyFromRevision);

        myLock = lock;
        myChangelistName = changelistName;
        myTreeConflict = treeConflict;
        
        if (file != null) {
            if (conflictOld != null) {
                myConflictOldFile = new Resource(file.getParentFile(), conflictOld);
            }
            if (conflictNew != null) {
                myConflictNewFile = new Resource(file.getParentFile(), conflictNew);
            }
            if (conflictWorking != null) {
                myConflictWrkFile = new Resource(file.getParentFile(),
                        conflictWorking);
            }
            if (propRejectFile != null) {
                myPropConflictFile = new Resource(file.getParentFile(),
                        propRejectFile);
            }
        }

        myIsRemote = false;
        myDepth = depth;
        myWorkingSize = wcSize;
        myRepositorySize = -1;
    }

    protected SVNInfo(String path, SVNURL url, SVNRevision revision,
            SVNNodeKind kind, String uuid, SVNURL reposRootURL,
            long comittedRevision, Date date, String author, SVNLock lock, 
            SVNDepth depth, long size) {
        myIsRemote = true;
        myURL = url;
        myRevision = revision;
        myKind = kind;
        myRepositoryRootURL = reposRootURL;
        myRepositoryUUID = uuid;

        myCommittedDate = date;
        myCommittedRevision = SVNRevision.create(comittedRevision);
        myAuthor = author;

        myLock = lock;
        myPath = path;
        myDepth = depth;
        myRepositorySize = size;
        myWorkingSize = -1;
    }
    
    /**
     * Gets the item's last commit author. This is the value of the item's 
     * {@link org.tmatesoft.svn.core.SVNProperty#LAST_AUTHOR} property.
     * 
     * @return the author who last changed (committed) the item 
     */
    public String getAuthor() {
        return myAuthor;
    }
    
    /**
     * Gets the file item's checksum. This is the value of the file item's
     * {@link org.tmatesoft.svn.core.SVNProperty#CHECKSUM} property. 
     * 
     * @return the file item's checksum
     */
    public String getChecksum() {
        return myChecksum;
    }
    
    /**
     * Gets the item's last commit date. This is the value of the item's
     * {@link org.tmatesoft.svn.core.SVNProperty#COMMITTED_DATE}
     * property. 
     * 
     * @return the item's last commit date
     */
    public Date getCommittedDate() {
        return myCommittedDate;
    }
    
    /**
     * Gets the item's last committed revision. This is the value of the item's
     * {@link org.tmatesoft.svn.core.SVNProperty#COMMITTED_REVISION} property. 
     * 
     * @return the item's last committed revision.
     */
    public SVNRevision getCommittedRevision() {
        return myCommittedRevision;
    }
    
    /**
     * Gets the temporary file that contains all latest changes from the 
     * repository which led to a conflict with local changes. This file is
     * at the HEAD revision.
     * 
     * <p>
     * Taken from the item's {@link org.tmatesoft.svn.core.SVNProperty#CONFLICT_NEW}
     * property.
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
     * <p>
     * Taken from the item's {@link org.tmatesoft.svn.core.SVNProperty#CONFLICT_OLD}
     * property.
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
     * <p>
     * Taken from the item's {@link org.tmatesoft.svn.core.SVNProperty#CONFLICT_WRK}
     * property.
     * 
     * @return an autogenerated temporary file with only the user's modifications 
     */
    public File getConflictWrkFile() {
        return myConflictWrkFile;
    }
    
    /**
     * Returns a tree conflict description on the item represented by 
     * this object.
     * 
     * @return a tree conflict description object or <code>null</code>
     *         if no tree conflict exists on this item
     * @since  1.3
     */
    public SVNTreeConflictDescription getTreeConflict() {
    	return myTreeConflict;
    }

    /**
     * Gets the revision of the item's ancestor from which the item was 
     * copied.
     * 
     * @return the ancestor's revision (taken from the 
     *         {@link org.tmatesoft.svn.core.SVNProperty#COPYFROM_REVISION} property)
     */
    public SVNRevision getCopyFromRevision() {
        return myCopyFromRevision;
    }

    /**
     * Gets the URL (repository location) of the ancestor from which the
     * item was copied.
     * 
     * @return the item ancestor's URL (taken from the 
     *         {@link org.tmatesoft.svn.core.SVNProperty#COPYFROM_URL} property)
     */
    public SVNURL getCopyFromURL() {
        return myCopyFromURL;
    }
    
    /**
     * Gets the item's local path. Applicable for local info operation 
     * invocations, however if an info operation is invoked for remote 
     * items, use {@link #getPath()} instead. 
     * 
     * @return  the item's local path
     */
    public File getFile() {
        return myFile;
    }
    
    /**
     * Finds out whether the item for which this <b>SVNInfo</b> is generated
     * is local (located in a user's Working Copy) or remote (located in a 
     * repository). It depends on the type of an info operation to perform - 
     * that is on an {@link SVNWCClient}'s doInfo() method to use. Also
     * applicability of some methods of the <b>SVNInfo</b> class depends
     * on the item's location that can be determined calling this method.   
     *  
     * @return <span class="javakeyword">true</span> if the item is located
     *         in a repository, otherwise <span class="javakeyword">false</span>
     *         and the item is in a Working Copy
     */
    public boolean isRemote() {
        return myIsRemote;
    }
    
    /**
     * Gets the item's node kind. Used to find out whether the item is
     * a file, directory, etc. 
     * 
     * @return  the item's node kind
     */
    public SVNNodeKind getKind() {
        return myKind;
    }
    
    /**
     * Gets the file item's lock. Used to get lock information - lock 
     * token, comment, etc. 
     * 
     * @return the file item's lock.
     */
    public SVNLock getLock() {
        return myLock;
    }
    
    /**
     * Gets the item's path (relative to the repository root). Applicable for 
     * remote info operation invocations, however if an info operation is 
     * invoked for Working Copy items, use {@link #getFile()} instead. 
     * 
     * @return  the item's path in the repository
     */
    public String getPath() {
        return myPath;
    }
    
    /**
     * Gets the <i>'.prej'</i> file containing details on properties conflicts.
     * If the item's properties are in conflict with those that came
     * during an update this file will contain a conflict description. 
     * This is the value of the item's {@link org.tmatesoft.svn.core.SVNProperty#PROP_REJECT_FILE}
     * property.
     * 
     * @return  the properties conflicts file
     */
    public File getPropConflictFile() {
        return myPropConflictFile;
    }
    
    /**
     * Gets the value of the item's
     * {@link org.tmatesoft.svn.core.SVNProperty#PROP_TIME} property.
     * It corresponds to the last time when properties were committed.
     * 
     * @return the value of the item's prop-time property  
     */
    public Date getPropTime() {
        return myPropTime;
    }
    
    /**
     * Gets the repository root url (where the repository itself
     * is installed). Applicable only for remote info operation invocations 
     * (for items in a repository).
     * 
     * @return the repository's root URL
     */
    public SVNURL getRepositoryRootURL() {
        return myRepositoryRootURL;
    }
    
    /**
     * Gets the repository Universal Unique IDentifier (UUID). This is the
     * value of the {@link org.tmatesoft.svn.core.SVNProperty#UUID} 
     * property.
     * 
     * @return the repository UUID
     */
    public String getRepositoryUUID() {
        return myRepositoryUUID;
    }
    
    /**
     * Gets the item's revision.
     * 
     * @return the item's revision
     */
    public SVNRevision getRevision() {
        return myRevision;
    }
    
    /**
     * Gets the item's schedule status. Schedule status is inapplicable
     * when running a remote info operation (for items in a repository).
     * If it's a local info operation and the return value is 
     * <span class="javakeyword">null</span> then it corresponds to the
     * SVN's <i>'normal'</i> schedule status. 
     * 
     * @return the item's schedule status
     */
    public String getSchedule() {
        return mySchedule;
    }
    
    /**
     * Gets the value of the item's {@link org.tmatesoft.svn.core.SVNProperty#TEXT_TIME}
     * property. It corresponds to the last commit time. 
     * 
     * @return the value of the item's text-time property
     */
    public Date getTextTime() {
        return myTextTime;
    }
    
    /**
     * Gets the item's URL - its repository location.
     * 
     * @return the item's URL
     */
    public SVNURL getURL() {
        return myURL;
    }

    /**
     * Gets the item's depth. 
     * 
     * @return  depth value  
     * @since   1.2.0, SVN 1.5.0
     */
    public SVNDepth getDepth() {
        return myDepth;
    }

    /**
     * Gets the name of the changelist the item belongs to.
     * 
     * @return  changelist name 
     * @since   1.2.0, SVN 1.5.0
     */
    public String getChangelistName() {
        return myChangelistName;
    }

    /**
     * Returns the size of the working copy file.
     * Relevant for file items only.
     * 
     * @return  working file size in bytes
     * @since   1.2.0, SVN 1.5.0
     */
    public long getWorkingSize() {
        return myWorkingSize;
    }

    /**
     * Returns the size of the file in the repository.
     * Relevant for file items only and in case of a remote operation (i.e. info fetched just from 
     * the working copy will always return -1 in this method).
     * 
     * @return  repository file size in bytes
     * @since   1.2.0, SVN 1.5.0
     */
    public long getRepositorySize() {
        return myRepositorySize;
    }

}
