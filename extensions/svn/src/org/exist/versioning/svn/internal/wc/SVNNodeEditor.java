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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCombiner;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryUtil;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionRoot;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.ISVNChangeEntryHandler;
import org.tmatesoft.svn.core.wc.admin.ISVNChangedDirectoriesHandler;
import org.tmatesoft.svn.core.wc.admin.ISVNGNUDiffGenerator;
import org.tmatesoft.svn.core.wc.admin.SVNChangeEntry;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNNodeEditor implements ISVNEditor {

    private static final char TYPE_REPLACED = 'R';
    
    private Node myCurrentNode;
    private Node myRootNode;
    private FSRoot myBaseRoot;
    private FSFS myFSFS;
    private Map myFiles;
    private ISVNEventHandler myCancelHandler;
    private File myTempDirectory;
    
    public SVNNodeEditor(FSFS fsfs, FSRoot baseRoot, ISVNEventHandler handler) {
        myBaseRoot = baseRoot;
        myFSFS = fsfs;
        myCancelHandler = handler;
        myFiles = new SVNHashMap();
    }

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentNode = addOrOpen(path, SVNChangeEntry.TYPE_ADDED, SVNNodeKind.DIR, myCurrentNode, copyFromPath, copyFromRevision);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        Node node = addOrOpen(path, SVNChangeEntry.TYPE_ADDED, SVNNodeKind.FILE, myCurrentNode, copyFromPath, copyFromRevision);
        myFiles.put(path, node);
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        myCurrentNode.myHasPropModifications = true;
    }

    public void changeFileProperty(String path, String name, SVNPropertyValue value) throws SVNException {
        Node fileNode = (Node) myFiles.get(path);
        fileNode.myHasPropModifications = true;
    }

    public void closeDir() throws SVNException {
        myCurrentNode = myCurrentNode.myParent;
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        myFiles.remove(path);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        String name = SVNPathUtil.tail(path);
        Node node = null;

        if (myCurrentNode != null && myCurrentNode.myChildren != null) {
            for (Iterator children = myCurrentNode.myChildren.iterator(); children.hasNext();) {
                Node child = (Node) children.next();
                if (child.myName.equals(name)) {
                    node = child;
                    break;
                }
            }
        }

        if (node == null) {
            if (myCurrentNode != null) {
                node = new Node();
                node.myName = name;
                node.myParent = myCurrentNode;

                if (myCurrentNode.myChildren == null) {
                    myCurrentNode.myChildren = new LinkedList();
                }
                myCurrentNode.myChildren.add(node);
            }
        }

        node.myAction = SVNChangeEntry.TYPE_DELETED;
        SVNLocationEntry baseLocation = findRealBaseLocation(node);
        FSRoot baseRoot = null;
        if (!SVNRevision.isValidRevisionNumber(baseLocation.getRevision())) {
            baseRoot = myBaseRoot;
        } else {
            baseRoot = myFSFS.createRevisionRoot(baseLocation.getRevision()); 
        }
        
        SVNNodeKind kind = baseRoot.checkNodeKind(baseLocation.getPath());
        if (kind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "''{0}'' not found in filesystem", path);
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        
        node.myKind = kind;
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentNode = addOrOpen(path, TYPE_REPLACED, SVNNodeKind.DIR, myCurrentNode, null, -1);
    }

    public void openFile(String path, long revision) throws SVNException {
        Node node = addOrOpen(path, TYPE_REPLACED, SVNNodeKind.FILE, myCurrentNode, null, -1);
        myFiles.put(path, node);
    }

    public void openRoot(long revision) throws SVNException {
        myRootNode = myCurrentNode = new Node();
        myCurrentNode.myName = "";
        myCurrentNode.myParent = null;
        myCurrentNode.myKind = SVNNodeKind.DIR;
        myCurrentNode.myAction = TYPE_REPLACED;
    }

    public void targetRevision(long revision) throws SVNException {
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        Node fileNode = (Node) myFiles.get(path);
        fileNode.myHasTextModifications = true;
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
    }

    public void diff(FSRoot root, long baseRevision, ISVNGNUDiffGenerator generator, OutputStream os) throws SVNException {
        if (myRootNode != null) {
            FSRevisionRoot baseRoot = root.getOwner().createRevisionRoot(baseRevision);
            try {
                diffImpl(root, baseRoot, "/", "/", myRootNode, generator, os);
            } finally {
                cleanup();
            }
        }
    }
    
    public void traverseTree(boolean includeCopyInfo, ISVNChangeEntryHandler handler) throws SVNException {
        if (myRootNode != null) {
            traverseChangedTreeImpl(myRootNode, "/", includeCopyInfo, handler);
        }
    }
    
    public void traverseChangedDirs(ISVNChangedDirectoriesHandler handler) throws SVNException {
        if (myRootNode != null) {
            traverseChangedDirsImpl(myRootNode, "/", handler);
        }
    }

    private void diffImpl(FSRoot root, FSRevisionRoot baseRoot, String path, String basePath, Node node, ISVNGNUDiffGenerator generator, OutputStream os) throws SVNException {
        if (myCancelHandler != null) {
            myCancelHandler.checkCancelled();
        }
        
        DefaultSVNGNUDiffGenerator defaultGenerator = null;
        if (generator instanceof DefaultSVNGNUDiffGenerator) {
            defaultGenerator = (DefaultSVNGNUDiffGenerator) generator;
            defaultGenerator.setHeaderWritten(false);
            defaultGenerator.setDiffWritten(false);
        }
        
        boolean isCopy = false;
        boolean printedHeader = false;
        if (SVNRevision.isValidRevisionNumber(node.myCopyFromRevision) && node.myCopyFromPath != null) {
            basePath = node.myCopyFromPath;
            generator.displayHeader(ISVNGNUDiffGenerator.COPIED, path, basePath, node.myCopyFromRevision, os);
            baseRoot = myFSFS.createRevisionRoot(node.myCopyFromRevision);
            isCopy = true;
            printedHeader = true;
        }
        
        boolean doDiff = false;
        boolean isOriginalEmpty = false;
        DiffItem originalFile = null;
        DiffItem newFile = null;
        if (node.myKind == SVNNodeKind.FILE) {
            if (node.myAction == TYPE_REPLACED && node.myHasTextModifications) {
                doDiff = true;
                originalFile = prepareTmpFile(baseRoot, basePath, generator);
                newFile = prepareTmpFile(root, path, generator);
            } else if (generator.isDiffCopied() && node.myAction == SVNChangeEntry.TYPE_ADDED && isCopy) {
                if (node.myHasTextModifications) {
                    doDiff = true;
                    originalFile = prepareTmpFile(baseRoot, basePath, generator);
                    newFile = prepareTmpFile(root, path, generator);
                }
            } else if (generator.isDiffAdded() && node.myAction == SVNChangeEntry.TYPE_ADDED) {
                doDiff = true;
                isOriginalEmpty = true;
                originalFile = prepareTmpFile(null, basePath, generator);
                newFile = prepareTmpFile(root, path, generator);
            } else if (generator.isDiffDeleted() && node.myAction == SVNChangeEntry.TYPE_DELETED) {
                doDiff = true;
                originalFile = prepareTmpFile(null, basePath, generator);
                newFile = prepareTmpFile(null, path, generator);
            }
            
            if (!printedHeader && (node.myAction != TYPE_REPLACED || node.myHasTextModifications)) {
                if (node.myAction == SVNChangeEntry.TYPE_ADDED) {
                    generator.displayHeader(ISVNGNUDiffGenerator.ADDED, path, null, -1, os);
                } else if (node.myAction == SVNChangeEntry.TYPE_DELETED) {
                    generator.displayHeader(ISVNGNUDiffGenerator.DELETED, path, null, -1, os);
                } else if (node.myAction == TYPE_REPLACED) {
                    generator.displayHeader(ISVNGNUDiffGenerator.MODIFIED, path, null, -1, os);
                }
                printedHeader = true;
            }
        }
        
        if (doDiff) {
            if (defaultGenerator != null) {
                if (isOriginalEmpty) {
                    defaultGenerator.setOriginalFile(null, path);
                } else {
                    defaultGenerator.setOriginalFile(baseRoot, basePath);
                }
                defaultGenerator.setNewFile(root, path);
            }
            String rev1 = isOriginalEmpty ? "(rev 0)" : "(rev " + baseRoot.getRevision() + ")";
            String rev2 = null;
            if (root instanceof FSRevisionRoot) {
                FSRevisionRoot revRoot = (FSRevisionRoot) root;
                rev2 = "(rev " + revRoot.getRevision() + ")";
            } else {
                FSTransactionRoot txnRoot = (FSTransactionRoot) root;
                rev2 = "(txn " + txnRoot.getTxnID() + ")";
            }
            generator.displayFileDiff(path, originalFile.myTmpFile, newFile.myTmpFile, rev1, rev2, originalFile.myMimeType, newFile.myMimeType, os);
            boolean hasDiff = defaultGenerator != null ? defaultGenerator.isDiffWritten() : true;
            if (!hasDiff && !node.myHasPropModifications &&
                    (node.myAction == SVNChangeEntry.TYPE_ADDED && generator.isDiffAdded()  ||
                     node.myAction == SVNChangeEntry.TYPE_DELETED && generator.isDiffDeleted())) {
                int kind = node.myAction == SVNChangeEntry.TYPE_ADDED ? ISVNGNUDiffGenerator.ADDED : ISVNGNUDiffGenerator.DELETED;
                defaultGenerator.setHeaderWritten(false);
                defaultGenerator.displayHeader(kind, path, null, -1, os);
                defaultGenerator.printHeader(os);
            }
        } else if (printedHeader) {
            generator.displayHeader(ISVNGNUDiffGenerator.NO_DIFF, path, null, -1, os);
        }
        
        if (node.myHasPropModifications && node.myAction != SVNChangeEntry.TYPE_DELETED) {
            FSRevisionNode localNode = root.getRevisionNode(path);
            SVNProperties props = localNode.getProperties(root.getOwner());
            SVNProperties baseProps = null;
            if (node.myAction != SVNChangeEntry.TYPE_ADDED) {
                FSRevisionNode baseNode = baseRoot.getRevisionNode(basePath);
                baseProps = baseNode.getProperties(baseRoot.getOwner());
            }
            SVNProperties propsDiff = FSRepositoryUtil.getPropsDiffs(baseProps, props);
            if (propsDiff.size() > 0) {
                String displayPath = path.startsWith("/") ? path.substring(1) : path;
                generator.displayPropDiff(displayPath, baseProps, propsDiff, os);
            }
        }
        
        if (node.myChildren == null || node.myChildren.size() == 0) {
            return;
        }
        
        for (Iterator children = node.myChildren.iterator(); children.hasNext();) {
            Node childNode = (Node) children.next();
            String childPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(path, childNode.myName));
            String childBasePath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(basePath, childNode.myName));
            diffImpl(root, baseRoot, childPath, childBasePath, childNode, generator, os);
        }
    }

    private DiffItem prepareTmpFile(FSRoot root, String path, ISVNDiffGenerator generator) throws SVNException {
        String mimeType = null; 
        if (root != null) {
            FSRevisionNode node = root.getRevisionNode(path);
            SVNProperties nodeProps = node.getProperties(root.getOwner());
            mimeType = nodeProps.getStringValue(SVNProperty.MIME_TYPE);
            if (SVNProperty.isBinaryMimeType(mimeType) && !generator.isForcedBinaryDiff()) {
                return new DiffItem(mimeType, null);
            }
        }
        
        File tmpFile = createTempFile(generator);
        if (root != null) {
            InputStream contents = null;
            OutputStream tmpOS = null;
            try {
                contents = root.getFileStreamForPath(new SVNDeltaCombiner(), path);
                tmpOS = SVNFileUtil.openFileForWriting(tmpFile);
                FSRepositoryUtil.copy(contents, tmpOS, myCancelHandler);
            } finally {
                SVNFileUtil.closeFile(contents);
                SVNFileUtil.closeFile(tmpOS);
            }
        }
        return new DiffItem(mimeType, tmpFile);
    }
    
    private File createTempFile(ISVNDiffGenerator generator) throws SVNException {
        File tmpFile = null;
        try {
            return File.createTempFile("diff.", ".tmp", getTempDirectory(generator));
        } catch (IOException e) {
            SVNFileUtil.deleteFile(tmpFile);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        return null;
    }
    
    private File getTempDirectory(ISVNDiffGenerator generator) throws SVNException {
        if (myTempDirectory == null || !myTempDirectory.exists()) {
            myTempDirectory = generator.createTempDirectory();
        }
        return myTempDirectory;
    }

    private void cleanup() {
        if (myTempDirectory != null) {
            SVNFileUtil.deleteAll(myTempDirectory, true);
        }
    }

    private void traverseChangedDirsImpl(Node node, String path, ISVNChangedDirectoriesHandler handler) throws SVNException {
        if (myCancelHandler != null) {
            myCancelHandler.checkCancelled();
        }
        
        if (node == null || node.myKind != SVNNodeKind.DIR) {
            return;
        }

        boolean proceed = node.myHasPropModifications;
        if (!proceed && node.myChildren != null) {
            for (Iterator children = node.myChildren.iterator(); children.hasNext() && !proceed;) {
                Node child = (Node) children.next();
                if (child.myKind == SVNNodeKind.FILE || child.myHasTextModifications || child.myAction == SVNChangeEntry.TYPE_ADDED || child.myAction == SVNChangeEntry.TYPE_DELETED) {
                    proceed = true;
                }
            }
        }
        
        if (proceed && handler != null) {
            handler.handleDir(path);
        }
        
        if (node.myChildren == null || node.myChildren.size() == 0) {
            return;
        }
        
        for (Iterator children = node.myChildren.iterator(); children.hasNext();) {
            Node childNode = (Node) children.next();
            String fullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(path, childNode.myName));
            traverseChangedDirsImpl(childNode, fullPath, handler);
        }
    }
    
    private void traverseChangedTreeImpl(Node node, String path, boolean includeCopyInfo, ISVNChangeEntryHandler handler) throws SVNException {
        if (myCancelHandler != null) {
            myCancelHandler.checkCancelled();
        }
        
        if (node == null) {
            return;
        }
        
        SVNChangeEntry changeEntry = null;
        if (node.myAction == SVNChangeEntry.TYPE_ADDED) {
            String copyFromPath = includeCopyInfo ? node.myCopyFromPath : null;
            long copyFromRevision = includeCopyInfo ? node.myCopyFromRevision : -1;
            changeEntry = new SVNChangeEntry(path, node.myKind, node.myAction, copyFromPath, copyFromRevision, false, false);
        } else if (node.myAction == SVNChangeEntry.TYPE_DELETED) {
            changeEntry = new SVNChangeEntry(path, node.myKind, node.myAction, null, -1, false, false);
        } else if (node.myAction == TYPE_REPLACED) {
            if (node.myHasPropModifications || node.myHasTextModifications) {
                changeEntry = new SVNChangeEntry(path, node.myKind, SVNChangeEntry.TYPE_UPDATED, null, -1, node.myHasTextModifications, node.myHasPropModifications);
            }
        }
        
        if (changeEntry != null && handler != null) {
            handler.handleEntry(changeEntry);
        }
        
        if (node.myChildren == null || node.myChildren.size() == 0) {
            return;
        }
        
        for (Iterator children = node.myChildren.iterator(); children.hasNext();) {
            Node childNode = (Node) children.next();
            String fullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(path, childNode.myName));
            traverseChangedTreeImpl(childNode, fullPath, includeCopyInfo, handler);
        }
    }
    
    private SVNLocationEntry findRealBaseLocation(Node node) throws SVNException {
        if (node.myAction == SVNChangeEntry.TYPE_ADDED && node.myCopyFromPath != null && SVNRevision.isValidRevisionNumber(node.myCopyFromRevision)) {
            return new SVNLocationEntry(node.myCopyFromRevision, node.myCopyFromPath);
        }
        
        if (node.myParent != null) {
            SVNLocationEntry location = findRealBaseLocation(node.myParent);
            return new SVNLocationEntry(location.getRevision(), SVNPathUtil.getAbsolutePath(SVNPathUtil.append(location.getPath(), node.myName)));
        }

        return new SVNLocationEntry(-1, "/");
    }

    private Node addOrOpen(String path, char action, SVNNodeKind kind, Node parent, String copyFromPath, long copyFromRevision) {
        if (parent.myChildren == null) {
            parent.myChildren = new LinkedList();
        }

        Node node = new Node();
        node.myName = SVNPathUtil.tail(path);
        node.myAction = action;
        node.myKind = kind;
        node.myCopyFromPath = copyFromPath;
        node.myCopyFromRevision = copyFromRevision;
        node.myParent = parent;
        parent.myChildren.add(node);
        return node;
    }

    private class Node {

        SVNNodeKind myKind;
        char myAction;
        boolean myHasTextModifications;
        boolean myHasPropModifications;
        String myName;
        long myCopyFromRevision;
        String myCopyFromPath;
        Node myParent;
        LinkedList myChildren;
    }
    
    private class DiffItem {
        String myMimeType;
        File myTmpFile;
        
        public DiffItem(String mimeType, File tmpFile) {
            myMimeType = mimeType;
            myTmpFile = tmpFile; 
        }
    }
}
