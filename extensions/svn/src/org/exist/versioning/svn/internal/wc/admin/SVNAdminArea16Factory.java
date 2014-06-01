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
package org.exist.versioning.svn.internal.wc.admin;

import java.io.File;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminArea16Factory extends SVNAdminArea14Factory {

    public static final int WC_FORMAT = SVNAdminAreaFactory.WC_FORMAT_16;

    protected void doCreateVersionedDirectory(File path, String url, String rootURL, String uuid, long revNumber, SVNDepth depth) throws SVNException {
        SVNAdminArea adminArea = new SVNAdminArea16(path); 
        adminArea.createVersionedDirectory(path, url, rootURL, uuid, revNumber, true, depth);
    }

    protected SVNAdminArea doOpen(File path, int version) throws SVNException {
        if (version != getSupportedVersion()) {
            return null;
        }
        return new SVNAdminArea16(path);
    }

    protected SVNAdminArea doChangeWCFormat(SVNAdminArea adminArea) throws SVNException {
        if (adminArea == null || adminArea.getClass() == SVNAdminArea16.class) {
            return adminArea;
        }
        SVNAdminArea16 newAdminArea = new SVNAdminArea16(adminArea.getRoot());
        newAdminArea.setLocked(true);
        return newAdminArea.formatWC(adminArea);
    }

    public int getSupportedVersion() {
        return WC_FORMAT;
    }

}
