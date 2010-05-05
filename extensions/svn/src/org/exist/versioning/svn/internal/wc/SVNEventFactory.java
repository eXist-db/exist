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
package org.exist.versioning.svn.internal.wc;

import java.io.File;

import org.exist.versioning.svn.wc.SVNEvent;
import org.exist.versioning.svn.wc.SVNEventAction;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNEventFactory {

    public static SVNEvent createErrorEvent(SVNErrorMessage error, SVNEventAction expectedAction){
        SVNEvent event = new SVNEvent(error, expectedAction);
        return event;
    }

    public static SVNEvent createLockEvent(File file, SVNEventAction action, SVNLock lock, SVNErrorMessage error){
        return new SVNEvent(file, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, null, null, null, lock, action, null, error, null, null);
    }

    public static SVNEvent createSVNEvent(File file, SVNNodeKind kind , String mimetype, long revision, SVNStatusType cstatus, SVNStatusType pstatus,
            SVNStatusType lstatus, SVNEventAction action, SVNEventAction expected, SVNErrorMessage error, SVNMergeRange range, String changelistName){
        return new SVNEvent(file, kind, mimetype, revision, cstatus, pstatus, lstatus, null, action, expected, error, range, changelistName);
    }

    public static SVNEvent createSVNEvent(File file, SVNNodeKind kind , String mimetype, long revision, SVNStatusType cstatus, SVNStatusType pstatus,
            SVNStatusType lstatus, SVNEventAction action, SVNEventAction expected, SVNErrorMessage error, SVNMergeRange range){
        return new SVNEvent(file, kind, mimetype, revision, cstatus, pstatus, lstatus, null, action, expected, error, range, null);
    }

    public static SVNEvent createSVNEvent(File file, SVNNodeKind kind , String mimetype, long revision, SVNEventAction action, SVNEventAction expected, SVNErrorMessage error, SVNMergeRange range){
        return new SVNEvent(file, kind, mimetype, revision, SVNStatusType.INAPPLICABLE, SVNStatusType.INAPPLICABLE, SVNStatusType.LOCK_INAPPLICABLE, null, action, expected, error, range, null);
    }

    public static SVNEvent createSVNEvent(File file, SVNNodeKind kind , String mimetype, long revision, SVNEventAction action, SVNEventAction expected, SVNErrorMessage error, SVNMergeRange range, long processedItemsCount, long totalItemsCount) {
        return new SVNEventExt(file, kind, mimetype, revision, SVNStatusType.INAPPLICABLE, SVNStatusType.INAPPLICABLE, SVNStatusType.LOCK_INAPPLICABLE, null, action, expected, error, range, null, processedItemsCount, totalItemsCount);
    }
}