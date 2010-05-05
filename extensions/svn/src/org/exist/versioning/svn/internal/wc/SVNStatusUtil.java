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
import java.util.Map;

import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminAreaInfo;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.exist.versioning.svn.wc.ISVNStatusHandler;
import org.exist.versioning.svn.wc.SVNStatus;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.exist.versioning.svn.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNStatusUtil {

	public static SVNRevisionStatus getRevisionStatus(final File wcPath, String trailURL, 
	        final boolean committed, ISVNEventHandler eventHandler) throws SVNException {
		
	    SVNWCAccess wcAccess = null; 
	        
	    try {    
    	    wcAccess = SVNWCAccess.newInstance(eventHandler);
    		SVNAdminAreaInfo anchor = wcAccess.openAnchor(wcPath, false, SVNWCAccess.INFINITE_DEPTH);
    		
    		final long[] minRev = { SVNRepository.INVALID_REVISION };
            final long[] maxRev = { SVNRepository.INVALID_REVISION };
    		
    		final boolean[] isSwitched = { false, false, false }; 
    		final boolean[] isModified = { false };
    		final boolean[] isSparseCheckOut = { false };
    		final SVNURL[] wcURL = { null };
    		
    		SVNStatusEditor editor = new SVNStatusEditor(null, wcAccess, anchor, false, true, 
                    SVNDepth.INFINITY, new ISVNStatusHandler() {
                public void handleStatus(SVNStatus status) throws SVNException {
                    SVNEntry entry = status.getEntry();
                    if (entry == null) {
                        return;
                    }
                    
                    if (status.getContentsStatus() != SVNStatusType.STATUS_ADDED) {
                        long itemRev = committed ? entry.getCommittedRevision() : entry.getRevision();
                        if (!SVNRevision.isValidRevisionNumber(minRev[0]) || itemRev < minRev[0]) {
                            minRev[0] = itemRev;
                        }
                        if (!SVNRevision.isValidRevisionNumber(maxRev[0]) || itemRev > maxRev[0]) {
                            maxRev[0] = itemRev;
                        }
                    }
                    
                    isSwitched[0] |= status.isSwitched();
                    isModified[0] |= status.getContentsStatus() != SVNStatusType.STATUS_NORMAL; 
                    isModified[0] |= status.getPropertiesStatus() != SVNStatusType.STATUS_NORMAL &&
                    status.getPropertiesStatus() != SVNStatusType.STATUS_NONE;                           
                    isSparseCheckOut[0] |= entry.getDepth() != SVNDepth.INFINITY;
                    
                    if (wcPath != null && wcURL[0] == null && wcPath.equals(status.getFile())) {
                        wcURL[0] = entry.getSVNURL();
                    }
                }
            });
    
    		editor.closeEdit();
    		if (!isSwitched[0] && trailURL != null) {
    		    if (wcURL[0] == null) {
    		        isSwitched[0] = true;
    		    } else {
    		        String wcURLStr = wcURL[0].toDecodedString();
    		        if (trailURL.length() > wcURLStr.length() || !wcURLStr.endsWith(trailURL)) {
    		            isSwitched[0] = true;
    		        }
    		    }
    		}
            return new SVNRevisionStatus(minRev[0], maxRev[0], isSwitched[0], isModified[0], isSparseCheckOut[0]);
	    } finally {
	        wcAccess.close();
	    }
	}
	
	public static SVNStatus getStatus(File path, SVNWCAccess wcAccess) throws SVNException {
		SVNEntry entry = null;
		SVNEntry parentEntry = null;
		SVNAdminArea adminArea = null;
		if (wcAccess != null) {
			entry = wcAccess.getEntry(path, false);
			adminArea = entry != null ? entry.getAdminArea() : null; 
		}
		
		File parentPath = path.getParentFile();
		if (entry != null && parentPath != null) {
			SVNAdminArea parentArea = wcAccess.retrieve(parentPath);
			if (parentArea != null) {
				parentEntry = wcAccess.getEntry(parentPath, false);
			}
		}
		return assembleStatus(path, adminArea, entry, parentEntry, SVNNodeKind.UNKNOWN, false, true, false, 
				null, null, wcAccess);
	}
	
    public static SVNStatus assembleStatus(File file, SVNAdminArea dir, SVNEntry entry, SVNEntry parentEntry, 
    		SVNNodeKind fileKind, boolean special, boolean reportAll, boolean isIgnored, Map repositoryLocks, 
    		SVNURL reposRoot, SVNWCAccess wcAccess) throws SVNException {
        boolean hasProps = false;
        boolean isTextModified = false;
        boolean isPropsModified = false;
        boolean isLocked = false;
        boolean isSwitched = false;
        boolean isSpecial = false;
        boolean isFileExternal = false;
        
        SVNStatusType textStatus = SVNStatusType.STATUS_NORMAL;
        SVNStatusType propStatus = SVNStatusType.STATUS_NONE;
        
        SVNLock repositoryLock = null;
        
        if (repositoryLocks != null) {
            SVNURL url = null;
            if (entry != null && entry.getSVNURL() != null) {
                url = entry.getSVNURL();
            } else if (parentEntry != null && parentEntry.getSVNURL() != null) {
                url = parentEntry.getSVNURL().appendPath(file.getName(), false);
            }
            if (url != null) {
                repositoryLock = getLock(repositoryLocks, url, reposRoot);
            }
        }
        
        if (fileKind == SVNNodeKind.UNKNOWN || fileKind == null) {
            SVNFileType fileType = SVNFileType.getType(file);
            fileKind = SVNFileType.getNodeKind(fileType);
            special = !SVNFileUtil.symlinksSupported() ? false : fileType == SVNFileType.SYMLINK;
        }
        
        SVNTreeConflictDescription treeConflict = wcAccess.getTreeConflict(file);
        if (entry == null) {
            SVNStatus status = new SVNStatus(null, file, SVNNodeKind.UNKNOWN,
                    SVNRevision.UNDEFINED, SVNRevision.UNDEFINED, null, null, SVNStatusType.STATUS_NONE, 
                    SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE, false,
                    false, false, false, null, null, null, null, null, SVNRevision.UNDEFINED, repositoryLock, null, 
                    null, null, -1, treeConflict);
            status.setRemoteStatus(SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE, repositoryLock, SVNNodeKind.NONE);
            SVNStatusType text = SVNStatusType.STATUS_NONE;
            SVNFileType fileType = SVNFileType.getType(file);
            if (fileType != SVNFileType.NONE) {
                text = isIgnored ? SVNStatusType.STATUS_IGNORED : SVNStatusType.STATUS_UNVERSIONED;
            }
            if (fileType == SVNFileType.NONE && treeConflict != null) {
                text = SVNStatusType.STATUS_MISSING;
            }
            status.setContentsStatus(text);
            return status;
        }
        if (entry.getKind() == SVNNodeKind.DIR) {
            if (fileKind == SVNNodeKind.DIR) {
                if (wcAccess.isMissing(file)) {
                    textStatus = SVNStatusType.STATUS_OBSTRUCTED;
                }
            } else if (fileKind != SVNNodeKind.NONE) {
                textStatus = SVNStatusType.STATUS_OBSTRUCTED;
            }
        }
        
        if (entry.getExternalFilePath() != null) {
            isFileExternal = true;
        } else if (entry.getSVNURL() != null && parentEntry != null && parentEntry.getSVNURL() != null) {
            String urlName = SVNPathUtil.tail(entry.getSVNURL().getURIEncodedPath());
            if (!SVNEncodingUtil.uriEncode(file.getName()).equals(urlName)) {
                isSwitched = true;
            }
            if (!isSwitched && !entry.getSVNURL().removePathTail().equals(parentEntry.getSVNURL())) {
                isSwitched = true;
            }
        }
        if (textStatus != SVNStatusType.STATUS_OBSTRUCTED) {
            String name = entry.getName();
            if (dir != null && dir.hasProperties(name)) {
                propStatus = SVNStatusType.STATUS_NORMAL;
                hasProps = true;
            }
            isPropsModified = dir != null && dir.hasPropModifications(name);
            if (hasProps) {
                isSpecial = dir != null && dir.getProperties(name).getPropertyValue(SVNProperty.SPECIAL) != null;
            }
            if (entry.getKind() == SVNNodeKind.FILE && special == isSpecial) {
                isTextModified = dir != null && dir.hasTextModifications(name, false);
            }
            if (isTextModified) {
                textStatus = SVNStatusType.STATUS_MODIFIED;
            }
            if (isPropsModified) {
                propStatus = SVNStatusType.STATUS_MODIFIED;
            }
            if (entry.getPropRejectFile() != null || 
                    entry.getConflictOld() != null || entry.getConflictNew() != null || entry.getConflictWorking() != null) {
                if (dir != null && dir.hasTextConflict(name)) {
                    textStatus = SVNStatusType.STATUS_CONFLICTED;
                }
                if (dir != null && dir.hasPropConflict(name)) {
                    propStatus = SVNStatusType.STATUS_CONFLICTED;
                }
            }
            if (entry.isScheduledForAddition() && textStatus != SVNStatusType.STATUS_CONFLICTED) {
                textStatus = SVNStatusType.STATUS_ADDED;
                propStatus = SVNStatusType.STATUS_NONE;
            } else if (entry.isScheduledForReplacement() && textStatus != SVNStatusType.STATUS_CONFLICTED) {
                textStatus = SVNStatusType.STATUS_REPLACED;
                propStatus = SVNStatusType.STATUS_NONE;
            } else if (entry.isScheduledForDeletion() && textStatus != SVNStatusType.STATUS_CONFLICTED) {
                textStatus = SVNStatusType.STATUS_DELETED;
                propStatus = SVNStatusType.STATUS_NONE;
            }
            if (entry.isIncomplete() && textStatus != SVNStatusType.STATUS_DELETED && textStatus != SVNStatusType.STATUS_ADDED) { 
                textStatus = SVNStatusType.STATUS_INCOMPLETE;
            } else if (fileKind == SVNNodeKind.NONE) {
                if (textStatus != SVNStatusType.STATUS_DELETED) {
                    textStatus = SVNStatusType.STATUS_MISSING;
                }
            } else if (fileKind != entry.getKind()) {
                textStatus = SVNStatusType.STATUS_OBSTRUCTED;
            } else if ((!isSpecial && special) || (isSpecial && !special)) {
                textStatus = SVNStatusType.STATUS_OBSTRUCTED;
            }
            if (fileKind == SVNNodeKind.DIR && entry.getKind() == SVNNodeKind.DIR) {
                isLocked = wcAccess.isLocked(file);
            }
        }
        if (!reportAll) {
            if ((textStatus == SVNStatusType.STATUS_NONE || textStatus == SVNStatusType.STATUS_NORMAL) &&
                (propStatus == SVNStatusType.STATUS_NONE || propStatus == SVNStatusType.STATUS_NORMAL) &&
                !isLocked && !isSwitched && entry.getLockToken() == null && repositoryLock == null && 
                entry.getChangelistName() == null && !isFileExternal && treeConflict == null) {
                return null;
            }
        }
        SVNLock localLock = null;
        if (entry.getLockToken() != null) {
            localLock = new SVNLock(null, entry.getLockToken(), entry.getLockOwner(), entry.getLockComment(),
                    SVNDate.parseDate(entry.getLockCreationDate()), null);
        }
        File conflictNew = dir != null ? dir.getFile(entry.getConflictNew()) : null;
        File conflictOld = dir != null ? dir.getFile(entry.getConflictOld()) : null;
        File conflictWrk = dir != null ? dir.getFile(entry.getConflictWorking()) : null;
        File conflictProp = dir != null ? dir.getFile(entry.getPropRejectFile()) : null;
        int wcFormatNumber = dir != null ? dir.getWorkingCopyFormatVersion() : -1;
        
        SVNStatus status = new SVNStatus(entry.getSVNURL(), file, entry.getKind(),
                SVNRevision.create(entry.getRevision()), SVNRevision.create(entry.getCommittedRevision()),
                SVNDate.parseDate(entry.getCommittedDate()), entry.getAuthor(),
                textStatus,  propStatus, SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE, 
                isLocked, entry.isCopied(), isSwitched, isFileExternal, conflictNew, conflictOld, conflictWrk, conflictProp, 
                entry.getCopyFromURL(), SVNRevision.create(entry.getCopyFromRevision()),
                repositoryLock, localLock, entry.asMap(), entry.getChangelistName(), wcFormatNumber, treeConflict);
        status.setEntry(entry);
        return status;
    }

    public static SVNLock getLock(Map repositoryLocks, SVNURL url, SVNURL reposRoot) {
        // get decoded path
        if (reposRoot == null || repositoryLocks == null || repositoryLocks.isEmpty() || url == null) {
            return null;
        }
        String urlString = url.getPath();
        String root = reposRoot.getPath();
        String path;
        if (urlString.equals(root)) {
            path = "/";
        } else {
            path = urlString.substring(root.length());
        }
        return (SVNLock) repositoryLocks.get(path);
    }

}
