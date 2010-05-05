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

import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNVersionedProperties;
import org.exist.versioning.svn.wc.ISVNMerger;
import org.exist.versioning.svn.wc.SVNMergeFileSet;
import org.exist.versioning.svn.wc.SVNMergeResult;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class AbstractSVNMerger implements ISVNMerger {
    
    private byte[] myStart;
    private byte[] mySeparator;
    private byte[] myEnd;

    protected AbstractSVNMerger(byte[] start, byte[] sep, byte[] end) {
        myStart = start;
        mySeparator = sep;
        myEnd = end;
    }
    
    protected byte[] getConflictSeparatorMarker() {
        return mySeparator;
    }
    
    protected byte[] getConflictStartMarker() {
        return myStart;
    }

    protected byte[] getConflictEndMarker() {
        return myEnd;
    }

    public SVNMergeResult mergeText(SVNMergeFileSet files, boolean dryRun, SVNDiffOptions options) throws SVNException {
        SVNStatusType status;
        if (files.isBinary()) {
            status = mergeBinary(files.getBaseFile(), files.getLocalFile(), files.getRepositoryFile(), options, files.getResultFile());
        } else {
            status = mergeText(files.getBaseFile(), files.getLocalFile(), files.getRepositoryFile(), options, files.getResultFile());
        }

        if (!files.isBinary() && status != SVNStatusType.CONFLICTED) {
            if (files.getCopyFromFile() != null) {
                status = SVNStatusType.MERGED;
            } else {
                SVNAdminArea adminArea = files.getAdminArea();
                SVNVersionedProperties props = adminArea.getProperties(files.getWCPath());
                boolean isSpecial = props.getPropertyValue(SVNProperty.SPECIAL) != null;
                 // compare merge result with 'wcFile' (in case of text and no conflict).
                boolean isSameContents = SVNFileUtil.compareFiles(isSpecial ? files.getLocalFile() : 
                    files.getWCFile(), files.getResultFile(), null);
                status = isSameContents ? SVNStatusType.UNCHANGED : status;
            }
        }
	    final SVNMergeResult result = SVNMergeResult.createMergeResult(status, null);
	    if (dryRun) {
		    return result;
	    }
        return processMergedFiles(files, result);
    }

    protected abstract SVNMergeResult processMergedFiles(SVNMergeFileSet files, SVNMergeResult mergeResult) throws SVNException;

    protected abstract SVNStatusType mergeText(File baseFile, File localFile, File repositoryFile, SVNDiffOptions options, File resultFile) throws SVNException;

    protected abstract SVNStatusType mergeBinary(File baseFile, File localFile, File repositoryFile, SVNDiffOptions options, File resultFile) throws SVNException;
}
