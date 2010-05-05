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
import java.io.OutputStream;
import java.util.LinkedList;

import org.exist.versioning.svn.internal.wc.admin.SVNAdminAreaInfo;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAmbientDepthFilterEditor implements ISVNEditor {

    private ISVNEditor myDelegate;
    private SVNWCAccess myWCAccess;
    private File myAnchor;
    private String myTarget;
    private DirBaton myCurrentDirBaton;
    private FileBaton myCurrentFileBaton;
    private LinkedList myDirs;


    public static ISVNEditor wrap(ISVNEditor editor, SVNAdminAreaInfo info, boolean depthIsSticky) {
        if (!depthIsSticky) {
            return new SVNAmbientDepthFilterEditor(editor, info.getWCAccess(), info.getAnchor().getRoot(), info.getTargetName());
        }
        return editor;
    }

    private SVNAmbientDepthFilterEditor(ISVNEditor delegate, SVNWCAccess wcAccess, File anchor, String target) {
        myDelegate = delegate;
        myWCAccess = wcAccess;
        myAnchor = anchor;
        myTarget = target;
        myDirs = new LinkedList();
    }

    public void abortEdit() throws SVNException {
        myDelegate.abortEdit();
    }

    public void absentDir(String path) throws SVNException {
        if (myCurrentDirBaton.myIsAmbientlyExcluded) {
            return;
        }
        myDelegate.absentDir(path);
    }

    public void absentFile(String path) throws SVNException {
        if (myCurrentDirBaton.myIsAmbientlyExcluded) {
            return;
        }
        myDelegate.absentFile(path);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        DirBaton parentBaton = myCurrentDirBaton;
        myCurrentDirBaton = makeDirBaton(path, parentBaton);
        if (myCurrentDirBaton.myIsAmbientlyExcluded) {
            return;
        }
        
        if (path.equals(myTarget)) {
            myCurrentDirBaton.myAmbientDepth = SVNDepth.INFINITY;
        } else if (parentBaton.myAmbientDepth == SVNDepth.IMMEDIATES) {
            myCurrentDirBaton.myAmbientDepth = SVNDepth.EMPTY;
        } else {
            myCurrentDirBaton.myAmbientDepth = SVNDepth.INFINITY;
        }
        
        myDelegate.addDir(path, copyFromPath, copyFromRevision);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentFileBaton = makeFileBaton(myCurrentDirBaton, path);
        if (myCurrentFileBaton.myIsAmbientlyExcluded) {
            return;
        }
        myDelegate.addFile(path, copyFromPath, copyFromRevision);
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        if (myCurrentDirBaton.myIsAmbientlyExcluded) {
            return;
        }
        myDelegate.changeDirProperty(name, value);
    }

    public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        if (myCurrentFileBaton.myIsAmbientlyExcluded) {
            return;
        }
        myDelegate.changeFileProperty(path, propertyName, propertyValue);
    }

    public void closeDir() throws SVNException {
        DirBaton closedDir = (SVNAmbientDepthFilterEditor.DirBaton) myDirs.removeLast();
        if (myDirs.isEmpty()) {
            myCurrentDirBaton = null;
        } else {
            myCurrentDirBaton = (SVNAmbientDepthFilterEditor.DirBaton) myDirs.getLast();
        }
        if (closedDir.myIsAmbientlyExcluded) {
            return;
        }
        myDelegate.closeDir();
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return myDelegate.closeEdit();
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        if (myCurrentFileBaton.myIsAmbientlyExcluded) {
            return;
        }
        myDelegate.closeFile(path, textChecksum);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        if (myCurrentDirBaton.myIsAmbientlyExcluded) {
            return;
        }
        
        if (myCurrentDirBaton.myAmbientDepth.compareTo(SVNDepth.IMMEDIATES) < 0) {
            File fullPath = new File(myAnchor, path);
            SVNEntry entry = myWCAccess.getEntry(fullPath, false);
            if (entry == null) {
                return;
            }
        }
        
        myDelegate.deleteEntry(path, revision);
    }

    public void openDir(String path, long revision) throws SVNException {
        DirBaton parentBaton = myCurrentDirBaton;
        myCurrentDirBaton = makeDirBaton(path, parentBaton);
        
        if (myCurrentDirBaton.myIsAmbientlyExcluded) {
            return;
        }
        
        myDelegate.openDir(path, revision);
        SVNEntry entry = myWCAccess.getEntry(myCurrentDirBaton.myPath, false);
        if (entry != null) {
            myCurrentDirBaton.myAmbientDepth = entry.getDepth();
        }
    }

    public void openFile(String path, long revision) throws SVNException {
        myCurrentFileBaton = makeFileBaton(myCurrentDirBaton, path);
        if (myCurrentFileBaton.myIsAmbientlyExcluded) {
            return;
        }
        myDelegate.openFile(path, revision);
    }

    public void openRoot(long revision) throws SVNException {
        myCurrentDirBaton = makeDirBaton(null, null);
        if (myCurrentDirBaton.myIsAmbientlyExcluded) {
            return;
        }
        
        if (myTarget == null || "".equals(myTarget)) {
            SVNEntry entry = myWCAccess.getEntry(myCurrentDirBaton.myPath, false);
            if (entry != null) {
                myCurrentDirBaton.myAmbientDepth = entry.getDepth();
            }
        }
        
        myDelegate.openRoot(revision);
    }

    public void targetRevision(long revision) throws SVNException {
        myDelegate.targetRevision(revision);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        if (myCurrentFileBaton.myIsAmbientlyExcluded) {
            return;
        }
        myDelegate.applyTextDelta(path, baseChecksum);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        if (myCurrentFileBaton.myIsAmbientlyExcluded) {
            return SVNFileUtil.DUMMY_OUT;
        }
        return myDelegate.textDeltaChunk(path, diffWindow);
    }

    public void textDeltaEnd(String path) throws SVNException {
        if (myCurrentFileBaton.myIsAmbientlyExcluded) {
            return;
        }
        myDelegate.textDeltaEnd(path);
    }

    private FileBaton makeFileBaton(DirBaton parentBaton, String path) throws SVNException {
        if (path == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                    "aborting in SVNAmbientDepthFilterEditor.makeFileBation(): path == null");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        
        FileBaton fileBaton = new FileBaton();
        if (parentBaton.myIsAmbientlyExcluded) {
            fileBaton.myIsAmbientlyExcluded = true;
            return fileBaton;
        }
        
        if (parentBaton.myAmbientDepth == SVNDepth.EMPTY) {
            SVNEntry entry = myWCAccess.getEntry(new File(myAnchor, path), false);
            if (entry == null) {
                fileBaton.myIsAmbientlyExcluded = true;
            }
        }
        return fileBaton;
    }
    
    private DirBaton makeDirBaton(String path, DirBaton parentBaton) throws SVNException {
        if (parentBaton != null && path == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                    "aborting in SVNAmbientDepthFilterEditor.makeDirBation(): parentBaton != null" +
                    " while path == null");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        
        if (parentBaton != null && parentBaton.myIsAmbientlyExcluded) {
            myDirs.addLast(parentBaton);
            return parentBaton;
        }
        
        DirBaton dirBaton = new DirBaton();
        myDirs.addLast(dirBaton);
        dirBaton.myPath = myAnchor;
        if (path != null) {
            dirBaton.myPath = new File(myAnchor, path);
        }
        
        if (parentBaton != null && (parentBaton.myAmbientDepth == SVNDepth.EMPTY || 
                parentBaton.myAmbientDepth == SVNDepth.FILES)) {
            SVNEntry entry = myWCAccess.getEntry(dirBaton.myPath, false);
            if (entry == null) {
                dirBaton.myIsAmbientlyExcluded = true;
                return dirBaton;
            }
        }
        
        dirBaton.myAmbientDepth = SVNDepth.UNKNOWN;
        return dirBaton;
    }
    
    private class DirBaton {
        boolean myIsAmbientlyExcluded;
        SVNDepth myAmbientDepth;
        File myPath;
    }
    
    private class FileBaton {
        boolean myIsAmbientlyExcluded;
    }
}
