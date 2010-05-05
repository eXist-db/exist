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

import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNDepthFilterEditor implements ISVNEditor {

    private ISVNEditor myDelegate;
    private SVNDepth myRequestedDepth;
    private boolean myHasTarget;
    private NodeBaton myCurrentNodeBaton;
    
    private SVNDepthFilterEditor(SVNDepth depth, ISVNEditor delegate, boolean hasTarget) {
        myRequestedDepth = depth;
        myDelegate = delegate;
        myHasTarget = hasTarget;
    }
    
    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
        if (!myCurrentNodeBaton.myIsFiltered) {
            myDelegate.absentDir(path);
        }
    }

    public void absentFile(String path) throws SVNException {
        if (!myCurrentNodeBaton.myIsFiltered) {
            myDelegate.absentFile(path);
        }
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        NodeBaton childNodeBaton = null;
        if (myCurrentNodeBaton.canEdit(SVNNodeKind.DIR)) {
            childNodeBaton = new NodeBaton(false, myCurrentNodeBaton.myDirDepth + 1, myCurrentNodeBaton);
            myDelegate.addDir(path, copyFromPath, copyFromRevision);
        } else {
            childNodeBaton = new NodeBaton(true, myCurrentNodeBaton.myDirDepth + 1, myCurrentNodeBaton);
        }
        myCurrentNodeBaton = childNodeBaton;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        NodeBaton childNodeBaton = null;
        if (myCurrentNodeBaton.canEdit(SVNNodeKind.FILE)) {
            childNodeBaton = new NodeBaton(false, myCurrentNodeBaton.myDirDepth, myCurrentNodeBaton);
            myDelegate.addFile(path, copyFromPath, copyFromRevision);
        } else {
            childNodeBaton = new NodeBaton(true, myCurrentNodeBaton.myDirDepth, myCurrentNodeBaton);
        }
        myCurrentNodeBaton = childNodeBaton;
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        if (!myCurrentNodeBaton.myIsFiltered) {
            myDelegate.changeDirProperty(name, value);
        }
    }

    public void changeFileProperty(String path, String name, SVNPropertyValue value) throws SVNException {
        if (!myCurrentNodeBaton.myIsFiltered) {
            myDelegate.changeFileProperty(path, name, value);
        }
    }

    public void closeDir() throws SVNException {
        if (!myCurrentNodeBaton.myIsFiltered) {
            myDelegate.closeDir();
        }
        myCurrentNodeBaton = myCurrentNodeBaton.myParentBaton;
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return myDelegate.closeEdit();
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        if (!myCurrentNodeBaton.myIsFiltered) {
            myDelegate.closeFile(path, textChecksum);
        }
        myCurrentNodeBaton = myCurrentNodeBaton.myParentBaton;
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        if (myCurrentNodeBaton.canEdit(SVNNodeKind.FILE)) {
            myDelegate.deleteEntry(path, revision);
        }
    }

    public void openDir(String path, long revision) throws SVNException {
        NodeBaton childNodeBaton = null;
        if (myCurrentNodeBaton.canEdit(SVNNodeKind.DIR)) {
            childNodeBaton = new NodeBaton(false, myCurrentNodeBaton.myDirDepth + 1, myCurrentNodeBaton);
            myDelegate.openDir(path, revision);
        } else {
            childNodeBaton = new NodeBaton(true, myCurrentNodeBaton.myDirDepth + 1, myCurrentNodeBaton);
        }
        myCurrentNodeBaton = childNodeBaton; 
    }

    public void openFile(String path, long revision) throws SVNException {
        NodeBaton childNodeBaton = null;
        if (myCurrentNodeBaton.canEdit(SVNNodeKind.FILE)) {
            childNodeBaton = new NodeBaton(false, myCurrentNodeBaton.myDirDepth, myCurrentNodeBaton);
            myDelegate.openFile(path, revision);
        } else {
            childNodeBaton = new NodeBaton(true, myCurrentNodeBaton.myDirDepth, myCurrentNodeBaton);
        }
        myCurrentNodeBaton = childNodeBaton;
    }

    public void openRoot(long revision) throws SVNException {
        myCurrentNodeBaton = new NodeBaton(false, 1, null);
        myDelegate.openRoot(revision);
    }

    public void targetRevision(long revision) throws SVNException {
        myDelegate.targetRevision(revision);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        if (!myCurrentNodeBaton.myIsFiltered) {
            myDelegate.applyTextDelta(path, baseChecksum);
        }
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        if (!myCurrentNodeBaton.myIsFiltered) {
            return myDelegate.textDeltaChunk(path, diffWindow);
        }
        return SVNFileUtil.DUMMY_OUT;
    }

    public void textDeltaEnd(String path) throws SVNException {
        if (!myCurrentNodeBaton.myIsFiltered) {
            myDelegate.textDeltaEnd(path);
        }
    }

    public static ISVNEditor getDepthFilterEditor(SVNDepth requestedDepth, ISVNEditor delegate, boolean hasTarget) {
        if (requestedDepth == SVNDepth.UNKNOWN || requestedDepth == SVNDepth.INFINITY) {
            return delegate;
        }
        return new SVNDepthFilterEditor(requestedDepth, delegate, hasTarget);
    }
    
    private class NodeBaton {
        boolean myIsFiltered;
        int myDirDepth;
        NodeBaton myParentBaton;
        
        public NodeBaton(boolean isFiltered, int depth, NodeBaton parent) {
            myIsFiltered = isFiltered;
            myDirDepth = depth;
            myParentBaton = parent; 
        }
        
        public boolean canEdit(SVNNodeKind entryKind) throws SVNException {
            if (myIsFiltered) {
                return false;
            }
            
            int effectiveDepth = myDirDepth - (myHasTarget ? 1 : 0);
            if (myRequestedDepth == SVNDepth.EMPTY) {
                return effectiveDepth <= 0;
            } else if (myRequestedDepth == SVNDepth.FILES) {
                return effectiveDepth <= 0 || (entryKind == SVNNodeKind.FILE && effectiveDepth == 1);
            } else if (myRequestedDepth == SVNDepth.IMMEDIATES) {
                return effectiveDepth <= 1;
            } else if (myRequestedDepth == SVNDepth.INFINITY) {
                return true;
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "depth should be a valid constant");
            SVNErrorManager.error(err, SVNLogType.WC);
            return false;
        }
        
    }
}
