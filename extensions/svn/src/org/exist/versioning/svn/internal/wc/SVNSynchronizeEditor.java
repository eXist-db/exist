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

import java.io.OutputStream;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSynchronizeEditor implements ISVNEditor {

    private ISVNEditor myWrappedEditor;
    private boolean myIsRootOpened;
    private long myBaseRevision;
    private SVNCommitInfo myCommitInfo;
    private ISVNLogEntryHandler myHandler;
    private SVNRepository myTargetRepository;
    private int myNormalizedNodePropsCounter;
    private SVNProperties myRevisionProperties;
    
    public SVNSynchronizeEditor(SVNRepository toRepository, ISVNLogEntryHandler handler, long baseRevision, SVNProperties revProps) {
        myTargetRepository = toRepository;
        myIsRootOpened = false;
        myBaseRevision = baseRevision;
        myHandler = handler;
        myNormalizedNodePropsCounter = 0;
        myRevisionProperties = revProps;
    }
    
    public void reset(long baseRevision, SVNProperties revProps) {
        myWrappedEditor = null;
        myCommitInfo = null;
        myIsRootOpened = false;
        myBaseRevision = baseRevision;
        myNormalizedNodePropsCounter = 0;
        myRevisionProperties = revProps;
    }
    
    public void abortEdit() throws SVNException {
        getWrappedEditor().abortEdit();
    }

    private ISVNEditor getWrappedEditor() throws SVNException {
        if (myWrappedEditor == null) {
            myWrappedEditor = myTargetRepository.getCommitEditor(null, null, false, myRevisionProperties, null);
        }
        return myWrappedEditor;
    }
    
    public void absentDir(String path) throws SVNException {
        getWrappedEditor().absentDir(path);
    }

    public void absentFile(String path) throws SVNException {
        getWrappedEditor().absentFile(path);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        getWrappedEditor().addDir(path, copyFromPath, copyFromRevision);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        getWrappedEditor().addFile(path, copyFromPath, copyFromRevision);
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        if (SVNProperty.isRegularProperty(name)) {
            if (SVNPropertiesManager.propNeedsTranslation(name)) {
                String normalizedValue = SVNAdminClient.normalizeString(SVNPropertyValue.getPropertyAsString(value));
                if (normalizedValue != null) {
                    value = SVNPropertyValue.create(normalizedValue);
                    myNormalizedNodePropsCounter++;
                }
            }
            getWrappedEditor().changeDirProperty(name, value);
        }
    }

    public void changeFileProperty(String path, String name, SVNPropertyValue value) throws SVNException {
        if (SVNProperty.isRegularProperty(name)) {
            if (SVNPropertiesManager.propNeedsTranslation(name)) {
                String normalizedVal = SVNAdminClient.normalizeString(SVNPropertyValue.getPropertyAsString(value));
                if (normalizedVal != null) {
                    value = SVNPropertyValue.create(normalizedVal);
                    myNormalizedNodePropsCounter++;
                }
               
            }
            getWrappedEditor().changeFileProperty(path, name, value);
        }
    }

    public void closeDir() throws SVNException {
        getWrappedEditor().closeDir();
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        ISVNEditor wrappedEditor = getWrappedEditor();
        if (!myIsRootOpened) {
            wrappedEditor.openRoot(myBaseRevision);
        }
        myCommitInfo = wrappedEditor.closeEdit();
        if (myHandler != null) {
            SVNLogEntry logEntry = new SVNLogEntry(null, myCommitInfo.getNewRevision(), 
                    myCommitInfo.getAuthor(), myCommitInfo.getDate(), null);
            myHandler.handleLogEntry(logEntry);
        }
        return myCommitInfo;
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        getWrappedEditor().closeFile(path, textChecksum);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        getWrappedEditor().deleteEntry(path, revision);
    }

    public void openDir(String path, long revision) throws SVNException {
        getWrappedEditor().openDir(path, revision);
    }

    public void openFile(String path, long revision) throws SVNException {
        getWrappedEditor().openFile(path, revision);
    }

    public void openRoot(long revision) throws SVNException {
        getWrappedEditor().openRoot(revision);
        myIsRootOpened = true;
    }

    public void targetRevision(long revision) throws SVNException {
        getWrappedEditor().targetRevision(revision);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        getWrappedEditor().applyTextDelta(path, baseChecksum);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return getWrappedEditor().textDeltaChunk(path, diffWindow);
    }

    public void textDeltaEnd(String path) throws SVNException {
        getWrappedEditor().textDeltaEnd(path);
    }

    public SVNCommitInfo getCommitInfo() {
        return myCommitInfo;
    }
    
    public int getNormalizedNodePropsCounter() {
        return myNormalizedNodePropsCounter;
    }
}
