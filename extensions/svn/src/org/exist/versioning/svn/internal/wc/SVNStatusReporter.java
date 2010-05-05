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

import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNStatusReporter implements ISVNReporterBaton, ISVNReporter {

    private ISVNReporter myReporter;
    private ISVNReporterBaton myBaton;
    private SVNURL myRepositoryLocation;
    private SVNRepository myRepository;
    private SVNURL myRepositoryRoot;
    private Map myLocks;

    private SVNStatusEditor myEditor;

    public SVNStatusReporter(SVNRepository repos, ISVNReporterBaton baton, SVNStatusEditor editor) {
        myBaton = baton;
        myRepository = repos;
        myRepositoryLocation = repos.getLocation();
        myEditor = editor;
        myLocks = new SVNHashMap();
    }

    public SVNLock getLock(SVNURL url) {
        // get decoded path
        if (myRepositoryRoot == null || myLocks.isEmpty() || url == null) {
            return null;
        }
        String urlString = url.getPath();
        String root = myRepositoryRoot.getPath();
        String path;
        if (urlString.equals(root)) {
            path = "/";
        } else {
            path = urlString.substring(root.length());
        }
        return (SVNLock) myLocks.get(path);
    }

    public void report(ISVNReporter reporter) throws SVNException {
        myReporter = reporter;
        myBaton.report(this);
    }

    public void setPath(String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
        setPath(path, lockToken, revision, SVNDepth.INFINITY, startEmpty);
    }

    public void deletePath(String path) throws SVNException {
        myReporter.deletePath(path);
    }

    public void linkPath(SVNURL url, String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
        linkPath(url, path, lockToken, revision, SVNDepth.INFINITY, startEmpty);
    }

    public void finishReport() throws SVNException {
        // collect locks
        SVNLock[] locks = null;
        try {
            myRepositoryRoot = myRepository.getRepositoryRoot(true);
            locks = myRepository.getLocks("");
        } catch (SVNException e) {
            if (!(e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_IMPLEMENTED)) {
                throw e;
            }
        } finally {
            myRepository.closeSession();
        }
        if (locks != null) {
            for (int i = 0; i < locks.length; i++) {
                SVNLock lock = locks[i];
                myLocks.put(lock.getPath(), lock);
            }
        }
        myEditor.setRepositoryInfo(myRepositoryRoot, myLocks);
        myReporter.finishReport();
    }

    public void abortReport() throws SVNException {
        myReporter.abortReport();
    }

    public void linkPath(SVNURL url, String path, String lockToken, long revision, SVNDepth depth, boolean startEmpty) throws SVNException {
        SVNURL rootURL = SVNURLUtil.getCommonURLAncestor(url, myRepositoryLocation);
        if (rootURL == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, 
                    "Can not determine common ancestor of ''{0}'' and ''{1}'';\nprobably these entries belong to different repositories.", 
                    new Object[] {url, myRepositoryLocation});
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (SVNPathUtil.getPathAsChild(rootURL.getPath(), myRepositoryLocation.getPath()) != null) {
            myRepositoryLocation = rootURL;
        }
        myReporter.linkPath(url, path, lockToken, revision, depth, startEmpty);
    }

    public void setPath(String path, String lockToken, long revision, SVNDepth depth, boolean startEmpty) throws SVNException {
        myReporter.setPath(path, lockToken, revision, depth, startEmpty);
    }
}
