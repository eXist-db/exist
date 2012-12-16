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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNChecksumInputStream;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNTranslator;
import org.exist.versioning.svn.internal.wc.admin.SVNVersionedProperties;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.exist.versioning.svn.wc.SVNCommitItem;
import org.exist.versioning.svn.wc.SVNEvent;
import org.exist.versioning.svn.wc.SVNEventAction;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNCommitter implements ISVNCommitPathHandler {

    private Map myCommitItems;
    private Map myModifiedFiles;
    private Collection myTmpFiles;
    private String myRepositoryRoot;
    private SVNDeltaGenerator myDeltaGenerator;

    public SVNCommitter(Map commitItems, String reposRoot, Collection tmpFiles) {
        myCommitItems = commitItems;
        myModifiedFiles = new TreeMap();
        myTmpFiles = tmpFiles;
        myRepositoryRoot = reposRoot;
    }

    public boolean handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException {
        SVNCommitItem item = (SVNCommitItem) myCommitItems.get(commitPath);
        SVNWCAccess wcAccess = item.getWCAccess();
        wcAccess.checkCancelled();
        if (item.isCopied()) {
            if (item.getCopyFromURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Commit item ''{0}'' has copy flag but no copyfrom URL", item.getFile());                    
                SVNErrorManager.error(err, SVNLogType.WC);
            } else if (item.getRevision().getNumber() < 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Commit item ''{0}'' has copy flag but an invalid revision", item.getFile());                    
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        SVNEvent event = null;
        boolean closeDir = false;

        File file = null;
        if (item.getFile() != null) {
            file = item.getFile();
        } else if (item.getPath() != null) {
            file = new Resource(wcAccess.getAnchor(), item.getPath());
        }

        long rev = item.getRevision().getNumber();
        if (item.isAdded() && item.isDeleted()) {
            event = SVNEventFactory.createSVNEvent(file, item.getKind(), null, SVNRepository.INVALID_REVISION, SVNEventAction.COMMIT_REPLACED, null, null, null);
	        event.setPreviousRevision(rev);
        } else if (item.isAdded()) {
            String mimeType = null;
            if (item.getKind() == SVNNodeKind.FILE && file != null) {
                SVNAdminArea dir = item.getWCAccess().retrieve(file.getParentFile());
                mimeType = dir.getProperties(file.getName()).getStringPropertyValue(SVNProperty.MIME_TYPE);
            }
            event = SVNEventFactory.createSVNEvent(file, item.getKind(), mimeType, SVNRepository.INVALID_REVISION, SVNEventAction.COMMIT_ADDED, null, null, null);
	        event.setPreviousRevision(item.getCopyFromRevision() != null ? item.getCopyFromRevision().getNumber() : -1);
	        event.setPreviousURL(item.getCopyFromURL());
        } else if (item.isDeleted()) {
            event = SVNEventFactory.createSVNEvent(file, item.getKind(), null, SVNRepository.INVALID_REVISION, SVNEventAction.COMMIT_DELETED, null, null, null);
	        event.setPreviousRevision(rev);
        } else if (item.isContentsModified() || item.isPropertiesModified()) {
            event = SVNEventFactory.createSVNEvent(file, item.getKind(), null, SVNRepository.INVALID_REVISION, SVNEventAction.COMMIT_MODIFIED, null, null, null);
	        event.setPreviousRevision(rev);
        }
        if (event != null) {
	        event.setURL(item.getURL());
            wcAccess.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
        long cfRev = item.getCopyFromRevision().getNumber();//item.getCopyFromURL() != null ? rev : -1;
        if (item.isDeleted()) {
            try {
                commitEditor.deleteEntry(commitPath, rev);
            } catch (SVNException e) {
                fixError(commitPath, e, SVNNodeKind.FILE);
            }
        }
        
        boolean fileOpen = false;
        Map outgoingProperties = item.getOutgoingProperties();
        if (item.isAdded()) {
            String copyFromPath = getCopyFromPath(item.getCopyFromURL());
            if (item.getKind() == SVNNodeKind.FILE) {
                commitEditor.addFile(commitPath, copyFromPath, cfRev);
                fileOpen = true;
            } else {
                commitEditor.addDir(commitPath, copyFromPath, cfRev);
                closeDir = true;
            }
            
            if (outgoingProperties != null) {
                for (Iterator propsIter = outgoingProperties.keySet().iterator(); propsIter.hasNext();) {
                    String propName = (String) propsIter.next();
                    SVNPropertyValue propValue = (SVNPropertyValue) outgoingProperties.get(propName);
                    if (item.getKind() == SVNNodeKind.FILE) {
                        commitEditor.changeFileProperty(commitPath, propName, propValue);
                    } else {
                        commitEditor.changeDirProperty(propName, propValue);
                    }
                }
                outgoingProperties = null;
            }
        }
        
        if (item.isPropertiesModified() || (outgoingProperties != null && !outgoingProperties.isEmpty())) {
            if (item.getKind() == SVNNodeKind.FILE) {
                if (!fileOpen) {
                    try {
                        commitEditor.openFile(commitPath, rev);
                    } catch (SVNException e) {
                        fixError(commitPath, e, SVNNodeKind.FILE);
                    }
                }
                fileOpen = true;
            } else if (!item.isAdded()) {
                // do not open dir twice.
                try {
                    if ("".equals(commitPath)) {
                        commitEditor.openRoot(rev);
                    } else {
                        commitEditor.openDir(commitPath, rev);
                    }
                } catch (SVNException svne) {
                    fixError(commitPath, svne, SVNNodeKind.DIR);
                }
                closeDir = true;
            }

            if (item.isPropertiesModified()) {
                try {
                    sendPropertiesDelta(commitPath, item, commitEditor);
                } catch (SVNException e) {
                    fixError(commitPath, e, item.getKind());
                }
            }
            
            if (outgoingProperties != null) {
                for (Iterator propsIter = outgoingProperties.keySet().iterator(); propsIter.hasNext();) {
                    String propName = (String) propsIter.next();
                    SVNPropertyValue propValue = (SVNPropertyValue) outgoingProperties.get(propName);
                    if (item.getKind() == SVNNodeKind.FILE) {
                        commitEditor.changeFileProperty(commitPath, propName, propValue);
                    } else {
                        commitEditor.changeDirProperty(propName, propValue);
                    }
                }
            }
        }
        
        if (item.isContentsModified() && item.getKind() == SVNNodeKind.FILE) {
            if (!fileOpen) {
                try {
                    commitEditor.openFile(commitPath, rev);
                } catch (SVNException e) {
                    fixError(commitPath, e, SVNNodeKind.FILE);
                }
            }
            myModifiedFiles.put(commitPath, item);
        } else if (fileOpen) {
            commitEditor.closeFile(commitPath, null);
        }
        return closeDir;
    }

    public void sendTextDeltas(ISVNEditor editor) throws SVNException {
        for (Iterator paths = myModifiedFiles.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNCommitItem item = (SVNCommitItem) myModifiedFiles.get(path);
            SVNWCAccess wcAccess = item.getWCAccess();
            wcAccess.checkCancelled();

            SVNEvent event = SVNEventFactory.createSVNEvent(new Resource(wcAccess.getAnchor(), item.getPath()),SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNEventAction.COMMIT_DELTA_SENT, null, null, null);
            wcAccess.handleEvent(event, ISVNEventHandler.UNKNOWN);

            SVNAdminArea dir = wcAccess.retrieve(item.getFile().getParentFile());
            String name = SVNPathUtil.tail(item.getPath());
            SVNEntry entry = dir.getEntry(name, false);

            File tmpFile = dir.getBaseFile(name, true);
            myTmpFiles.add(tmpFile);

            String expectedChecksum = null;
            String checksum = null;
            String newChecksum = null;

            SVNChecksumInputStream baseChecksummedIS = null;
            InputStream sourceIS = null;
            InputStream targetIS = null;
            OutputStream tmpBaseStream = null;
            File baseFile = dir.getBaseFile(name, false);
            SVNErrorMessage error = null;
            boolean useChecksummedStream = false;
            boolean openSrcStream = false;
            if (!item.isAdded()) {
                openSrcStream = true;
                expectedChecksum = entry.getChecksum();
                if (expectedChecksum != null) {
                    useChecksummedStream = true;
                } else {
                    expectedChecksum = SVNFileUtil.computeChecksum(baseFile);
                }
            } else {
                sourceIS = SVNFileUtil.DUMMY_IN;
            }
  
            editor.applyTextDelta(path, expectedChecksum);
            if (myDeltaGenerator == null) {
                myDeltaGenerator = new SVNDeltaGenerator();
            }

            try {
                sourceIS = openSrcStream ? SVNFileUtil.openFileForReading(baseFile, SVNLogType.WC) : sourceIS;
                if (useChecksummedStream) {
                    baseChecksummedIS = new SVNChecksumInputStream(sourceIS, SVNChecksumInputStream.MD5_ALGORITHM);
                    sourceIS = baseChecksummedIS;
                }
                    
                targetIS = SVNTranslator.getTranslatedStream(dir, name, true, false);
                tmpBaseStream = SVNFileUtil.openFileForWriting(tmpFile);
                CopyingStream localStream = new CopyingStream(tmpBaseStream, targetIS);
                newChecksum = myDeltaGenerator.sendDelta(path, sourceIS, 0, localStream, editor, true);
            } catch (SVNException svne) {
                error = svne.getErrorMessage().wrap("While preparing ''{0}'' for commit", dir.getFile(name));
            } finally {
                SVNFileUtil.closeFile(sourceIS);
                SVNFileUtil.closeFile(targetIS);
                SVNFileUtil.closeFile(tmpBaseStream);
            }

            if (baseChecksummedIS != null) {
                checksum = baseChecksummedIS.getDigest();
            }
            
            if (expectedChecksum != null && checksum != null && !expectedChecksum.equals(checksum)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, 
                        "Checksum mismatch for ''{0}''; expected: ''{1}'', actual: ''{2}''",
                        new Object[] { dir.getFile(name), expectedChecksum, checksum }); 
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            if (error != null) {
                SVNErrorManager.error(error, SVNLogType.WC);
            }
            
            editor.closeFile(path, newChecksum);
        }
    }

    private void sendPropertiesDelta(String commitPath, SVNCommitItem item, ISVNEditor editor) throws SVNException {
        SVNAdminArea dir;
        String name;
        SVNWCAccess wcAccess = item.getWCAccess();
        if (item.getKind() == SVNNodeKind.DIR) {
            dir = wcAccess.retrieve(item.getFile());
            name = "";
        } else {
            dir = wcAccess.retrieve(item.getFile().getParentFile());
            name = SVNPathUtil.tail(item.getPath());
        }
        
        if (!dir.hasPropModifications(name)) {
            return;
        }
        SVNEntry entry = dir.getEntry(name, false);
        boolean replaced = false;
        if (entry != null) {
            replaced = entry.isScheduledForReplacement();
        }
        SVNVersionedProperties props = dir.getProperties(name);
        SVNVersionedProperties baseProps = replaced ? null : dir.getBaseProperties(name);
        SVNProperties diff = replaced ? props.asMap() : baseProps.compareTo(props).asMap();
        if (diff != null && !diff.isEmpty()) {
            File tmpPropsFile = dir.getPropertiesFile(name, true);
            SVNWCProperties tmpProps = new SVNWCProperties(tmpPropsFile, null);
            for(Iterator propNames = props.getPropertyNames(null).iterator(); propNames.hasNext();) {
                String propName = (String) propNames.next();
                SVNPropertyValue propValue = props.getPropertyValue(propName);
                tmpProps.setPropertyValue(propName, propValue);
            }
            if (!tmpPropsFile.exists()) {
                // create empty tmp (!) file just to make sure it will be used on post-commit.
                SVNFileUtil.createEmptyFile(tmpPropsFile);
            }
            myTmpFiles.add(tmpPropsFile);

            for (Iterator names = diff.nameSet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                SVNPropertyValue value = diff.getSVNPropertyValue(propName);
                if (item.getKind() == SVNNodeKind.FILE) {
                    editor.changeFileProperty(commitPath, propName, value);
                } else {
                    editor.changeDirProperty(propName, value);
                }
            }
        }
    }

    private String getCopyFromPath(SVNURL url) {
        if (url == null) {
            return null;
        }
        String path = url.getPath();
        if (myRepositoryRoot.equals(path)) {
            return "/";
        }
        return path.substring(myRepositoryRoot.length());
    }
    
    private void fixError(String path, SVNException e, SVNNodeKind kind) throws SVNException {
        SVNErrorMessage err = e.getErrorMessage();
        if (err.getErrorCode() == SVNErrorCode.FS_NOT_FOUND || err.getErrorCode() == SVNErrorCode.RA_DAV_PATH_NOT_FOUND) {
            err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, kind == SVNNodeKind.DIR ? 
                    "Directory ''{0}'' is out of date" : "File ''{0}'' is out of date", path);
            throw new SVNException(err);
        }
        throw e;
    }

    public static SVNCommitInfo commit(Collection tmpFiles, Map commitItems, String repositoryRoot, ISVNEditor commitEditor) throws SVNException {
        SVNCommitter committer = new SVNCommitter(commitItems, repositoryRoot, tmpFiles);
        SVNCommitUtil.driveCommitEditor(committer, commitItems.keySet(), commitEditor, -1);
        committer.sendTextDeltas(commitEditor);
        return commitEditor.closeEdit();
    }

    private class CopyingStream extends InputStream {
        private InputStream myInput;
        private OutputStream myOutput;
        
        public CopyingStream(OutputStream os, InputStream is) {
            myInput = is;
            myOutput = os;
        }
        
        public int read() throws IOException {
            int r = myInput.read();
            if (r != -1) {
                myOutput.write(r);
            }
            return r;
        }
        
        public int read(byte[] b) throws IOException {
            int r = myInput.read(b);
            if (r != -1) {
                myOutput.write(b, 0, r);
            }
            return r;
        }
        
        public int read(byte[] b, int off, int len) throws IOException {
            int r = myInput.read(b, off, len);
            if (r != -1) {
                myOutput.write(b, off, r);
            }
            return r;
        }
    }
}
