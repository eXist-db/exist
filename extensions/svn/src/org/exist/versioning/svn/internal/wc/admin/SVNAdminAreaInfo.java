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
package org.exist.versioning.svn.internal.wc.admin;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminAreaInfo {
    
    private String myTargetName;
    private SVNAdminArea myTarget;
    private SVNAdminArea myAnchor;
    private SVNWCAccess myAccess;
    
    private Map myNewExternals;
    private Map myOldExternals;
    private Map myDepths;
    private Set myIncompleteEntries;
    private boolean myIsEnableIncompleteTrick;

    public SVNAdminAreaInfo(SVNWCAccess access, SVNAdminArea anchor, SVNAdminArea target, String targetName) {
        myAccess = access;
        myAnchor = anchor;
        myTarget = target;
        myTargetName = targetName;
        myIsEnableIncompleteTrick = false;
    }
    
    public SVNAdminArea getAnchor() {
        return myAnchor;
    }
    
    public SVNAdminArea getTarget() {
        return myTarget;
    }

    /**
     * This method has been added to give an ability to replace 
     * read-only areas with write enabled ones. 
     */
    public void setTarget(SVNAdminArea target) {
        myTarget = target;
    }

    /**
     * This method has been added to give an ability to replace 
     * read-only areas with write enabled ones. 
     */
    public void setAnchor(SVNAdminArea anchor) {
        myAnchor = anchor;
    }

    public String getTargetName() {
        return myTargetName;
    }

    public SVNWCAccess getWCAccess() {
        return myAccess;
    }

    public void setWCAccess(SVNWCAccess wcAccess) {
        myAccess = wcAccess;
    }
    
    public void addOldExternal(String path, String oldValue) {
        if (myOldExternals == null) {
            myOldExternals = new SVNHashMap();
        }
        myOldExternals.put(path, oldValue);
    }

    public void addNewExternal(String path, String newValue) {
        if (myNewExternals == null) {
            myNewExternals = new SVNHashMap();
        }
        myNewExternals.put(path, newValue);
    }

    public void addExternal(String path, String oldValue, String newValue) {
        addNewExternal(path, newValue);
        addOldExternal(path, oldValue);
    }
    
    public void addDepth(String path, SVNDepth depth) {
        if (myDepths == null) {
            myDepths = new SVNHashMap();
        }
        myDepths.put(path, depth);
    }
    
    public void removeDepth(String path) {
        if (myDepths != null) {
            myDepths.remove(path);
        }
    }

    public void removeExternal(String path) {
        if (myNewExternals != null) {
            myNewExternals.remove(path);
        }
        if (myOldExternals != null) {
            myOldExternals.remove(path);
        }
    }
    
    public Map getNewExternals() {
        return myNewExternals == null ? Collections.EMPTY_MAP : myNewExternals;
    }

    public Map getOldExternals() {
        return myOldExternals == null ? Collections.EMPTY_MAP : myOldExternals;
    }

    public Map getDepths() {
        return myDepths == null ? Collections.EMPTY_MAP : myDepths;
    }
    
    public void addIncompleteEntry(String path) {
        if (!myIsEnableIncompleteTrick) {
            return;
        }
        if (myIncompleteEntries == null) {
            myIncompleteEntries = new SVNHashSet();
        }
        myIncompleteEntries.add(path);
    }
    
    public boolean isIncomplete(String path) {
        if (!myIsEnableIncompleteTrick) {
            return false;
        }
        return myIncompleteEntries != null && myIncompleteEntries.contains(path);
    }
}
