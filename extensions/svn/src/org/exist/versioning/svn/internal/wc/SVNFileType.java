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
import java.io.IOException;
import java.util.Set;

import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.jna.SVNJNAUtil;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNFileType {

    public static final SVNFileType UNKNOWN = new SVNFileType(0);
    public static final SVNFileType NONE = new SVNFileType(1);
    public static final SVNFileType FILE = new SVNFileType(2);
    public static final SVNFileType SYMLINK = new SVNFileType(3);
    public static final SVNFileType DIRECTORY = new SVNFileType(4);
    
    private static final boolean ourFastSymlinkResoution = !"false".equalsIgnoreCase(System.getProperty("svnkit.fastSymlinkResolution", System.getProperty("javasvn.fastSymlinkResolution")));
    private static final boolean ourCanonPathCacheUsed = !"false".equalsIgnoreCase(System.getProperty("sun.io.useCanonCaches"));
    private static boolean ourDetectSymlinks = !"false".equalsIgnoreCase(System.getProperty("svnkit.symlinks", System.getProperty("javasvn.symlinks", "true")));
    
    private static final Set ADMIN_FILE_PARENTS = new SVNHashSet();
    
    static {
        ADMIN_FILE_PARENTS.add("text-base");
        ADMIN_FILE_PARENTS.add("prop-base");
        ADMIN_FILE_PARENTS.add("props");
        ADMIN_FILE_PARENTS.add("wcprops");
        ADMIN_FILE_PARENTS.add("tmp");
    }

    private int myType;

    private SVNFileType(int type) {
        myType = type;
    }

    public String toString() {
        switch(myType) {
            case 0: return "unknown";
            case 1: return "none";
            case 2: return "file";
            case 3: return "symlink";
            case 4: return "directory";
        }
        return Integer.toString(myType);
    }
    
    
    public static synchronized void setSymlinkSupportEnabled(boolean enabled) {
        ourDetectSymlinks = enabled;
    }
    
    public static synchronized boolean isSymlinkSupportEnabled() {
        return ourDetectSymlinks;
    }

    public static SVNFileType getType(File file) {
        if (file == null)
            return SVNFileType.UNKNOWN;

        if (file.isFile())
            return SVNFileType.FILE;
        else if (file.isDirectory())
            return SVNFileType.DIRECTORY;
        else if (!file.exists())
            return SVNFileType.NONE;
        
        return SVNFileType.UNKNOWN;
    }

    public static boolean equals(SVNFileType type, SVNNodeKind nodeKind) {
        if (nodeKind == SVNNodeKind.DIR) {
            return type == SVNFileType.DIRECTORY;
        } else if (nodeKind == SVNNodeKind.FILE) {
            return type == SVNFileType.FILE || type == SVNFileType.SYMLINK;
        } else if (nodeKind == SVNNodeKind.NONE) {
            return type == SVNFileType.NONE;
        } else if (nodeKind == SVNNodeKind.UNKNOWN) {
            return type == SVNFileType.UNKNOWN;
        }
        return false;
    }
    
    private static boolean isAdminFile(File file) {
        String path = file.getAbsolutePath().replace(File.separatorChar, '/');
        String adminDir = "/" + SVNFileUtil.getAdminDirectoryName();
        return path.lastIndexOf(adminDir + "/") > 0 || path.endsWith(adminDir);
    }
    
    public int getID() {
        return myType;
    }

    public boolean isFile() {
        return this == SVNFileType.FILE || this == SVNFileType.SYMLINK;
    }
    
    public static SVNNodeKind getNodeKind(SVNFileType type) {
        if (type == null || type == SVNFileType.NONE || type == SVNFileType.UNKNOWN) {
            return SVNNodeKind.NONE;
        } else if (type == SVNFileType.DIRECTORY) {
            return SVNNodeKind.DIR;
        }
        return SVNNodeKind.FILE;
    }

}
