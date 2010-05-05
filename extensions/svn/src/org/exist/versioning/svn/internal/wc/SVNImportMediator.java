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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNImportMediator implements ISVNWorkspaceMediator {

    public SVNImportMediator() {
    }

    public SVNPropertyValue getWorkspaceProperty(String path, String name) throws SVNException {
        return null;
    }

    public void setWorkspaceProperty(String path, String name, SVNPropertyValue value) throws SVNException {
    }
}
