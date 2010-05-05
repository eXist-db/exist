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
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNMergeRange;

/**
 * @author TMate Software Ltd.
 * @version 1.3
 * @since 1.2
 */
public class SVNEventExt extends SVNEvent {

    private long myProcessedItemsCount;
    private long myTotalItemsCount;

    public SVNEventExt(SVNErrorMessage errorMessage) {
        super(errorMessage, null);
    }

    public SVNEventExt(File file, SVNNodeKind kind, String mimetype, long revision, SVNStatusType cstatus, SVNStatusType pstatus, SVNStatusType lstatus, SVNLock lock, SVNEventAction action, SVNEventAction expected, SVNErrorMessage error, SVNMergeRange range, String changelistName, long processedItemsCount, long totalItemsCount) {
        super(file, kind, mimetype, revision, cstatus, pstatus, lstatus, lock, action, expected, error, range, changelistName);
        myProcessedItemsCount = processedItemsCount;
        myTotalItemsCount = totalItemsCount;
    }

    public long getProcessedItemsCount() {
        return myProcessedItemsCount;
    }

    public long getTotalItemsCount() {
        return myTotalItemsCount;
    }
}
