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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNVersionedProperties;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.exist.versioning.svn.wc.SVNCommitItem;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNCommitMediator implements ISVNWorkspaceMediator {

    private Collection myTmpFiles;
    private Map myWCPropsMap;
    private Map myCommitItems;

    public SVNCommitMediator(Map commitItems) {
        myTmpFiles = new ArrayList();
        myWCPropsMap = new SVNHashMap();
        myCommitItems = commitItems;
    }
 
    public SVNProperties getWCProperties(SVNCommitItem item) {
        return (SVNProperties) myWCPropsMap.get(item);
    }

    public Collection getTmpFiles() {
        return myTmpFiles;
    }

    public SVNPropertyValue getWorkspaceProperty(String path, String name) throws SVNException {
        SVNCommitItem item = (SVNCommitItem) myCommitItems.get(path);
        if (item == null) {
            return null;
        }
        SVNAdminArea dir;
        String target;
        SVNWCAccess wcAccess = item.getWCAccess();
        if (item.getKind() == SVNNodeKind.DIR) {
            dir = wcAccess.retrieve(item.getFile());
            target = "";
        } else {
            dir = wcAccess.retrieve(item.getFile().getParentFile());
            target = SVNPathUtil.tail(item.getPath());
        }
        SVNVersionedProperties wcProps = dir.getWCProperties(target);
        if (wcProps != null) {
            return wcProps.getPropertyValue(name);
        }
        return null;    
    }

    public void setWorkspaceProperty(String path, String name, SVNPropertyValue value)
            throws SVNException {
        if (name == null) {
            return;
        }
        SVNCommitItem item = (SVNCommitItem) myCommitItems.get(path);
        if (!myWCPropsMap.containsKey(item)) {
            myWCPropsMap.put(item, new SVNProperties());
        }

        ((SVNProperties) myWCPropsMap.get(item)).put(name, value);
    }
}
