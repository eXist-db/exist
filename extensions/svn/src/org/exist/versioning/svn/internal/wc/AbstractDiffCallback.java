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
import java.util.Iterator;
import java.util.Set;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class AbstractDiffCallback {
    
    private SVNAdminArea myAdminArea;
    private File myBasePath;
    private Set myDeletedPaths;
    
    protected AbstractDiffCallback(SVNAdminArea adminArea) {
        myAdminArea = adminArea;
    }
    
    public void setBasePath(File path) {
        myBasePath = path;
    }
    
    public abstract boolean isDiffUnversioned();

    public abstract boolean isDiffCopiedAsAdded();
    
    public abstract File createTempDirectory() throws SVNException;

    public abstract SVNStatusType propertiesChanged(String path, SVNProperties originalProperties, SVNProperties diff, 
            boolean[] isTreeConflicted) throws SVNException;

    public abstract SVNStatusType[] fileChanged(String path, File file1, File file2, long revision1, long revision2, String mimeType1, 
            String mimeType2, SVNProperties originalProperties, SVNProperties diff, boolean[] isTreeConflicted) throws SVNException;
    
    public abstract SVNStatusType[] fileAdded(String path, File file1, File file2, long revision1, long revision2, String mimeType1, 
            String mimeType2, SVNProperties originalProperties, SVNProperties diff, boolean[] isTreeConflicted) throws SVNException;
    
    public abstract SVNStatusType fileDeleted(String path, File file1, File file2, String mimeType1, String mimeType2, 
            SVNProperties originalProperties, boolean[] isTreeConflicted) throws SVNException;
    
    public abstract SVNStatusType directoryAdded(String path, long revision, boolean[] isTreeConflicted) throws SVNException;

    public abstract SVNStatusType directoryDeleted(String path, boolean[] isTreeConflicted) throws SVNException;
    
    public abstract void directoryOpened(String path, long revision, boolean[] isTreeConflicted) throws SVNException;
    
    public abstract SVNStatusType[] directoryClosed(String path, boolean[] isTreeConflicted) throws SVNException;

    protected String getDisplayPath(String path) {
        if (myAdminArea == null) {
            if (myBasePath != null) {
                return new Resource(myBasePath, path).getAbsolutePath().replace(Resource.separatorChar, '/');
            }
            return path.replace(Resource.separatorChar, '/');
        }
        return myAdminArea.getFile(path).getAbsolutePath().replace(Resource.separatorChar, '/');
    }
    
    protected void categorizeProperties(SVNProperties original, SVNProperties regular, SVNProperties entry, SVNProperties wc) {
        if (original == null) {
            return;
        }
        for(Iterator propNames = original.nameSet().iterator(); propNames.hasNext();) {
            String name = (String) propNames.next();
            if (regular != null && SVNProperty.isRegularProperty(name)) {
                regular.put(name, original.getSVNPropertyValue(name));
            } else if (entry != null && SVNProperty.isEntryProperty(name)) {
                entry.put(name, original.getSVNPropertyValue(name));
            } else if (wc != null && SVNProperty.isWorkingCopyProperty(name)) {
                wc.put(name, original.getSVNPropertyValue(name));
            }
        }
    }
    
    protected SVNAdminArea getAdminArea() {
        return myAdminArea;        
    }
    
    protected SVNWCAccess getWCAccess() {
        return getAdminArea().getWCAccess();
    }
    
    protected void addDeletedPath(String path) {
        if (myDeletedPaths == null) {
            myDeletedPaths = new SVNHashSet();
        }
        myDeletedPaths.add(path);
    }
    
    protected boolean isPathDeleted(String path) {
        return myDeletedPaths != null && myDeletedPaths.contains(path);
    }

    protected void clearDeletedPaths() {
        myDeletedPaths = null;
    }

    protected void setIsConflicted(boolean[] isConflictedResult, boolean isConflicted) {
        if (isConflictedResult != null && isConflictedResult.length > 0) {
            isConflictedResult[0] = isConflicted;
        }
    }

}
