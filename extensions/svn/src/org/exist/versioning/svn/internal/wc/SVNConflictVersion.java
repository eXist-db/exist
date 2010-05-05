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

import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;

/**
 * <b>SVNConflictVersion</b> represents Info about one of the conflicting versions of a node.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.3
 */
public class SVNConflictVersion {

    private final SVNURL myRepositoryRoot;
    private final String myPath;
    private final long myPegRevision;
    private final SVNNodeKind myKind;

    /**
     * Creates a new <code>SVNConflictVersion</code>.
     * 
     * @param repositoryRoot  repository root url
     * @param path            absolute repository path                
     * @param pegRevision     peg revision at which to look up <code>path</code>
     * @param kind            node kind of the <code>path</code>
     * @since 1.3
     */
    public SVNConflictVersion(SVNURL repositoryRoot, String path, long pegRevision, SVNNodeKind kind) {
        myRepositoryRoot = repositoryRoot;
        myPath = path;
        myPegRevision = pegRevision;
        myKind = kind;
    }

    /**
     * Returns the repository root url.
     * 
     * @return repository root url
     * @since  1.3
     */
    public SVNURL getRepositoryRoot() {
        return myRepositoryRoot;
    }

    /**
     * Returns the repository path.
     * @return  absolute repository path
     * @since   1.3
     */
    public String getPath() {
        return myPath;
    }

    /**
     * Returns the peg revision
     * @return  peg revision
     * @since   1.3
     */
    public long getPegRevision() {
        return myPegRevision;
    }

    /**
     * Returns the node kind.
     * @return  node kind of the path
     * @since   1.3
     */
    public SVNNodeKind getKind() {
        return myKind;
    }

    /**
     * Returns a string representation of this object.
     * @return  string representation
     * @sinec   1.3
     */
    public String toString() {
        return "[SVNConflictVersion root = " + getRepositoryRoot() + "; path = " + getPath() + "@" + getPegRevision() + " " + getKind() + "]";
    }
}
