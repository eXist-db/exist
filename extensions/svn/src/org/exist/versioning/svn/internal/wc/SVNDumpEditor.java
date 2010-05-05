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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCombiner;
import org.tmatesoft.svn.core.internal.io.fs.CountingOutputStream;
import org.tmatesoft.svn.core.internal.io.fs.FSEntry;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryUtil;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNChecksumInputStream;
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNDumpEditor implements ISVNEditor {

    private FSRoot myRoot;
    private FSFS myFSFS; 
    private long myTargetRevision;
    private long myOldestDumpedRevision;
    private String myRootPath;
    private OutputStream myDumpStream;
    private boolean myUseDeltas;
    private boolean myIsVerify;
    private DirectoryInfo myCurrentDirInfo;
    private SVNDeltaCombiner myDeltaCombiner;
    private SVNDeltaGenerator myDeltaGenerator;
    
    public SVNDumpEditor(FSFS fsfs, FSRoot root, long toRevision, long oldestDumpedRevision, String rootPath, OutputStream dumpStream, 
            boolean useDeltas, boolean isVerify) {
        myRoot = root;
        myFSFS = fsfs;
        myTargetRevision = toRevision;
        myOldestDumpedRevision = oldestDumpedRevision;
        myRootPath = rootPath;
        myDumpStream = dumpStream;
        myUseDeltas = useDeltas;
        myIsVerify = isVerify;
    }
    
    public void reset(FSFS fsfs, FSRoot root, long toRevision, long oldestDumpedRevision, String rootPath, OutputStream dumpStream, 
            boolean useDeltas, boolean isVerify) {
        myRoot = root;
        myFSFS = fsfs;
        myTargetRevision = toRevision;
        myOldestDumpedRevision = oldestDumpedRevision;
        myRootPath = rootPath;
        myDumpStream = dumpStream;
        myUseDeltas = useDeltas;
        myIsVerify = isVerify;
        myCurrentDirInfo = null;
    }
    
    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        DirectoryInfo parent = myCurrentDirInfo;
        myCurrentDirInfo = createDirectoryInfo(path, copyFromPath, copyFromRevision, parent);
        boolean isDeleted = parent.myDeletedEntries.containsKey(path);
        boolean isCopy = copyFromPath != null && SVNRevision.isValidRevisionNumber(copyFromRevision);
        dumpNode(path, SVNNodeKind.DIR, isDeleted ? SVNAdminHelper.NODE_ACTION_REPLACE : SVNAdminHelper.NODE_ACTION_ADD, isCopy, isCopy ? copyFromPath : null, isCopy ? copyFromRevision : -1);
        if (isDeleted) {
            parent.myDeletedEntries.remove(path);
        }
        myCurrentDirInfo.myIsWrittenOut = true;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        boolean isCopy = copyFromPath != null && SVNRevision.isValidRevisionNumber(copyFromRevision);
        boolean isDeleted = myCurrentDirInfo.myDeletedEntries.containsKey(path);
        dumpNode(path, SVNNodeKind.FILE, isDeleted ? SVNAdminHelper.NODE_ACTION_REPLACE : SVNAdminHelper.NODE_ACTION_ADD, isCopy, isCopy ? copyFromPath : null, isCopy ? copyFromRevision : -1);
        if (isDeleted) {
            myCurrentDirInfo.myDeletedEntries.remove(path);
        }
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        if (!myCurrentDirInfo.myIsWrittenOut) {
            dumpNode(myCurrentDirInfo.myFullPath, SVNNodeKind.DIR, SVNAdminHelper.NODE_ACTION_CHANGE, false, 
                    myCurrentDirInfo.myComparePath, myCurrentDirInfo.myCompareRevision);
            myCurrentDirInfo.myIsWrittenOut = true;
        }
    }

    public void changeFileProperty(String path, String name, SVNPropertyValue value) throws SVNException {
    }

    public void closeDir() throws SVNException {
        if (myIsVerify) {
            FSRevisionNode node = myRoot.getRevisionNode(myCurrentDirInfo.myFullPath);
            Map entries = node.getDirEntries(myFSFS);
            for (Iterator entriesIter = entries.keySet().iterator(); entriesIter.hasNext();) {
                String entryName = (String) entriesIter.next();
                String entryPath = SVNPathUtil.append(myCurrentDirInfo.myFullPath, entryName);
                SVNNodeKind kind = myRoot.checkNodeKind(entryPath);
                FSRevisionNode entryNode = myRoot.getRevisionNode(entryPath);
                if (kind == SVNNodeKind.DIR) {
                    entryNode.getDirEntries(myFSFS);
                } else if (kind == SVNNodeKind.FILE) {
                    entryNode.getFileLength();
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, 
                            "Unexpected node kind {0} for ''{1}''", new Object[] { kind, entryPath });
                    SVNErrorManager.error(err, SVNLogType.FSFS);
                }
            }
        }
        
        for (Iterator entries = myCurrentDirInfo.myDeletedEntries.keySet().iterator(); entries.hasNext();) {
            String path = (String) entries.next();
            dumpNode(path, SVNNodeKind.UNKNOWN, SVNAdminHelper.NODE_ACTION_DELETE, false, null, -1);
        }
        myCurrentDirInfo = myCurrentDirInfo.myParentInfo;
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        myCurrentDirInfo.myDeletedEntries.put(path, path);
    }

    public void openDir(String path, long revision) throws SVNException {
        DirectoryInfo parent = myCurrentDirInfo;
        String cmpPath = null;
        long cmpRev = -1;
        if (parent != null && parent.myComparePath != null && SVNRevision.isValidRevisionNumber(parent.myCompareRevision)) {
            cmpPath = SVNPathUtil.append(parent.myComparePath, SVNPathUtil.tail(path));
            cmpRev = parent.myCompareRevision;
        }
        myCurrentDirInfo = createDirectoryInfo(path, cmpPath, cmpRev, parent);
    }

    public void openFile(String path, long revision) throws SVNException {
        String cmpPath = null;
        long cmpRev = -1;
        if (myCurrentDirInfo != null && myCurrentDirInfo.myComparePath != null && SVNRevision.isValidRevisionNumber(myCurrentDirInfo.myCompareRevision)) {
            cmpPath = SVNPathUtil.append(myCurrentDirInfo.myComparePath, SVNPathUtil.tail(path));
            cmpRev = myCurrentDirInfo.myCompareRevision;
        }
        dumpNode(path, SVNNodeKind.FILE, SVNAdminHelper.NODE_ACTION_CHANGE, false, cmpPath, cmpRev);
        
    }

    public void openRoot(long revision) throws SVNException {
        myCurrentDirInfo = createDirectoryInfo(null, null, -1, null);
    }

    public void targetRevision(long revision) throws SVNException {
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
    }

    private void dumpNode(String path, SVNNodeKind kind, int nodeAction, boolean isCopy, String cmpPath, long cmpRev) throws SVNException {
        File tmpFile = null;
        try {
            writeDumpData(SVNAdminHelper.DUMPFILE_NODE_PATH + ": " + (path.startsWith("/") ? path.substring(1) : path) + "\n");
            
            if (kind == SVNNodeKind.FILE) {
                writeDumpData(SVNAdminHelper.DUMPFILE_NODE_KIND + ": file\n");
            } else if (kind == SVNNodeKind.DIR) {
                writeDumpData(SVNAdminHelper.DUMPFILE_NODE_KIND + ": dir\n");
            }
            
            if (cmpPath != null) {
                cmpPath = cmpPath.startsWith("/") ? cmpPath.substring(1) : cmpPath; 
            }

            String comparePath = path;
            long compareRevision = myTargetRevision - 1;
            if (cmpPath != null && SVNRevision.isValidRevisionNumber(cmpRev)) {
                comparePath = cmpPath;
                compareRevision = cmpRev;
            }
            comparePath = SVNPathUtil.canonicalizePath(comparePath);
            comparePath = SVNPathUtil.getAbsolutePath(comparePath);
            
            FSRevisionRoot compareRoot = null;
            boolean mustDumpProps = false;
            boolean mustDumpText = false;
            String canonicalPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.canonicalizePath(path));
            switch(nodeAction) {
                case SVNAdminHelper.NODE_ACTION_CHANGE:
                    writeDumpData(SVNAdminHelper.DUMPFILE_NODE_ACTION + ": change\n");
                    compareRoot = myFSFS.createRevisionRoot(compareRevision);
                    mustDumpProps = FSRepositoryUtil.arePropertiesChanged(compareRoot, comparePath, myRoot, canonicalPath); 
                    if (kind == SVNNodeKind.FILE) {
                        mustDumpText = FSRepositoryUtil.areFileContentsChanged(compareRoot, comparePath, myRoot, canonicalPath);
                    }
                    break;
                case SVNAdminHelper.NODE_ACTION_REPLACE:
                    if (!isCopy) {
                        writeDumpData(SVNAdminHelper.DUMPFILE_NODE_ACTION + ": replace\n");
                        if (kind == SVNNodeKind.FILE) {
                            mustDumpText = true;
                        }
                        mustDumpProps = true;
                    } else {
                        writeDumpData(SVNAdminHelper.DUMPFILE_NODE_ACTION + ": delete\n\n");
                        dumpNode(path, kind, SVNAdminHelper.NODE_ACTION_ADD, isCopy, comparePath, compareRevision);
                        mustDumpText = false;
                        mustDumpProps = false;
                    }
                    break;
                case SVNAdminHelper.NODE_ACTION_DELETE:
                    writeDumpData(SVNAdminHelper.DUMPFILE_NODE_ACTION + ": delete\n");
                    mustDumpText = false;
                    mustDumpProps = false;
                    break;
                case SVNAdminHelper.NODE_ACTION_ADD:
                    writeDumpData(SVNAdminHelper.DUMPFILE_NODE_ACTION + ": add\n");
                    if (!isCopy) {
                        if (kind == SVNNodeKind.FILE) {
                            mustDumpText = true;
                        }
                        mustDumpProps = true;
                    } else {
                        if (!myIsVerify && cmpRev < myOldestDumpedRevision) {
                            SVNDebugLog.getDefaultLog().logFine(SVNLogType.FSFS, 
                                    "WARNING: Referencing data in revision " + cmpRev + 
                                    ", which is older than the oldest\nWARNING: dumped revision (" + 
                                    myOldestDumpedRevision + 
                                    ").  Loading this dump into an empty repository\nWARNING: will fail.\n");
                        }
 
                        writeDumpData(SVNAdminHelper.DUMPFILE_NODE_COPYFROM_REVISION + ": " + cmpRev + "\n");
                        writeDumpData(SVNAdminHelper.DUMPFILE_NODE_COPYFROM_PATH + ": " + cmpPath + "\n");
                        compareRoot = myFSFS.createRevisionRoot(compareRevision);
                        mustDumpProps = FSRepositoryUtil.arePropertiesChanged(compareRoot, comparePath, myRoot, 
                                canonicalPath);
                        if (kind == SVNNodeKind.FILE) {
                            mustDumpText = FSRepositoryUtil.areFileContentsChanged(compareRoot, comparePath, 
                                    myRoot, canonicalPath);
                            FSRevisionNode revNode = compareRoot.getRevisionNode(comparePath);
                            String checkSum = revNode.getFileMD5Checksum();
                            if (checkSum != null && checkSum.length() > 0) {
                                writeDumpData(SVNAdminHelper.DUMPFILE_TEXT_COPY_SOURCE_MD5 + ": " + checkSum + "\n");
                            }
                            
                            checkSum = revNode.getFileSHA1Checksum();
                            if (checkSum != null && checkSum.length() > 0) {
                                writeDumpData(SVNAdminHelper.DUMPFILE_TEXT_COPY_SOURCE_SHA1 + ": " + checkSum + "\n");
                            }
                        }
                    }
                    break;
            }
            
            if (!mustDumpProps && !mustDumpText) {
                writeDumpData("\n\n");
                return;
            }
            
            long contentLength = 0;
            String propContents = null;
            if (mustDumpProps) {
                FSRevisionNode node = myRoot.getRevisionNode(canonicalPath);
                SVNProperties props = node.getProperties(myFSFS);
                SVNProperties oldProps = null;
                if (myUseDeltas && compareRoot != null) {
                    FSRevisionNode cmpNode = compareRoot.getRevisionNode(comparePath);
                    oldProps = cmpNode.getProperties(myFSFS);
                    writeDumpData(SVNAdminHelper.DUMPFILE_PROP_DELTA + ": true\n");
                }

                ByteArrayOutputStream encodedProps = new ByteArrayOutputStream();
                SVNAdminHelper.writeProperties(props, oldProps, encodedProps);
                propContents = new String(encodedProps.toByteArray(), "UTF-8");
                contentLength += propContents.length();
                writeDumpData(SVNAdminHelper.DUMPFILE_PROP_CONTENT_LENGTH + ": " + propContents.length() + "\n");
            }
            if (mustDumpText && kind == SVNNodeKind.FILE) {
                long txtLength = 0; 
                FSRevisionNode node = myRoot.getRevisionNode(canonicalPath);

                if (myUseDeltas) {
                    tmpFile = SVNFileUtil.createTempFile("dump", ".tmp");
                    
                    InputStream sourceStream = null;
                    InputStream targetStream = null;
                    OutputStream tmpStream = null; 
                    
                    SVNDeltaCombiner deltaCombiner = getDeltaCombiner();
                    SVNDeltaGenerator deltaGenerator = getDeltaGenerator();
                    try {
                        if (compareRoot != null && comparePath != null) {
                            sourceStream = compareRoot.getFileStreamForPath(deltaCombiner, comparePath);
                        } else {
                            sourceStream = SVNFileUtil.DUMMY_IN;
                        }
                        targetStream = myRoot.getFileStreamForPath(deltaCombiner, canonicalPath);
                        tmpStream = SVNFileUtil.openFileForWriting(tmpFile);
                        final CountingOutputStream countingStream = new CountingOutputStream(tmpStream, 0);  
                        ISVNDeltaConsumer consumer = new ISVNDeltaConsumer() {
                            private boolean isHeaderWritten = false;
                            
                            public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
                                try {
                                    if (diffWindow != null) {
                                        diffWindow.writeTo(countingStream, !isHeaderWritten, false);
                                    } else {
                                        SVNDiffWindow.EMPTY.writeTo(countingStream, !isHeaderWritten, false);
                                    }
                                } catch (IOException ioe) {
                                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                                    SVNErrorManager.error(err, ioe, SVNLogType.FSFS);
                                }

                                isHeaderWritten = true;
                                return SVNFileUtil.DUMMY_OUT;
                            }

                            public void applyTextDelta(String path, String baseChecksum) throws SVNException {
                            }
                            
                            public void textDeltaEnd(String path) throws SVNException {
                            }
                        };
                        
                        deltaGenerator.sendDelta(null, sourceStream, 0, targetStream, consumer, false);
                        txtLength = countingStream.getPosition();
                        
                        if (compareRoot != null) {
                            FSRevisionNode revNode = compareRoot.getRevisionNode(comparePath);
                            String hexDigest = revNode.getFileMD5Checksum();
                            if (hexDigest != null && hexDigest.length() > 0) {
                                writeDumpData(SVNAdminHelper.DUMPFILE_TEXT_DELTA_BASE_MD5 + ": " + hexDigest + "\n");
                            }
                            
                            hexDigest = revNode.getFileSHA1Checksum();
                            if (hexDigest == null) {
                                hexDigest = computeSHA1Checksum(compareRoot, comparePath);
                            }
                            if (hexDigest != null && hexDigest.length() > 0) {
                                writeDumpData(SVNAdminHelper.DUMPFILE_TEXT_DELTA_BASE_SHA1 + ": " + hexDigest + "\n");
                            }
                        }
                    } finally {
                        SVNFileUtil.closeFile(sourceStream);
                        SVNFileUtil.closeFile(targetStream);
                        SVNFileUtil.closeFile(tmpStream);
                    }
                    writeDumpData(SVNAdminHelper.DUMPFILE_TEXT_DELTA + ": true\n");
                } else {
                    txtLength = node.getFileLength(); 
                }
                
                contentLength += txtLength;
                writeDumpData(SVNAdminHelper.DUMPFILE_TEXT_CONTENT_LENGTH + ": " + txtLength + "\n");
                
                String checksum = node.getFileMD5Checksum();
                if (checksum != null && checksum.length() > 0) {
                    writeDumpData(SVNAdminHelper.DUMPFILE_TEXT_CONTENT_MD5 + ": " + checksum + "\n");
                }
                
                checksum = node.getFileSHA1Checksum();
                if (checksum == null) {
                    checksum = computeSHA1Checksum(myRoot, canonicalPath);
                }
                if (checksum != null && checksum.length() > 0) {
                    writeDumpData(SVNAdminHelper.DUMPFILE_TEXT_CONTENT_SHA1 + ": " + checksum + "\n");
                }
            }
            
            writeDumpData(SVNAdminHelper.DUMPFILE_CONTENT_LENGTH + ": " + contentLength + "\n\n");
            
            if (mustDumpProps) {
                writeDumpData(propContents);
            }
            
            if (mustDumpText && kind == SVNNodeKind.FILE) {
                InputStream source = null;
                try {
                    if (tmpFile != null) {
                        source = SVNFileUtil.openFileForReading(tmpFile, SVNLogType.WC);
                    } else {
                        source = myRoot.getFileStreamForPath(getDeltaCombiner(), canonicalPath);
                    }
                    //TODO: provide canceller?
                    FSRepositoryUtil.copy(source, myDumpStream, null);
                } finally {
                    SVNFileUtil.closeFile(source);
                }
            }
            writeDumpData("\n\n");
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe, SVNLogType.FSFS);
        } finally {
            SVNFileUtil.deleteFile(tmpFile);
        }
    }
    
    private String computeSHA1Checksum(FSRoot revision, String filePath) throws SVNException {
        InputStream is = revision.getFileStreamForPath(getDeltaCombiner(), filePath);
        SVNChecksumInputStream checksum = null;
        try {
            checksum = new SVNChecksumInputStream(is, "SHA1");
        } finally {
            SVNFileUtil.closeFile(checksum);
        }        
        return checksum != null ? checksum.getDigest() : null;
    }
    
    private SVNDeltaGenerator getDeltaGenerator() {
        if (myDeltaGenerator == null) {
            myDeltaGenerator = new SVNDeltaGenerator();
        }
        return myDeltaGenerator;
    }

    private SVNDeltaCombiner getDeltaCombiner() {
        if (myDeltaCombiner == null) {
            myDeltaCombiner = new SVNDeltaCombiner();
        } else {
            myDeltaCombiner.reset();
        }
        return myDeltaCombiner;
    }

    private DirectoryInfo createDirectoryInfo(String path, String copyFromPath, long copyFromRev, DirectoryInfo parent) {
        String fullPath = null;
        if (parent != null) {
            fullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(myRootPath, path));
        } else {
            fullPath = myRootPath;
        }
        
        String cmpPath = null;
        if (copyFromPath != null) {
            cmpPath = copyFromPath.startsWith("/") ? copyFromPath.substring(1) : copyFromPath; 
        }
        
        return new DirectoryInfo(fullPath, cmpPath, copyFromRev, parent);
    }

    private void writeDumpData(String data) throws IOException {
        myDumpStream.write(data.getBytes("UTF-8"));
    }

    private class DirectoryInfo {
        String myFullPath;
        String myComparePath;
        long myCompareRevision;
        boolean myIsWrittenOut;
        Map myDeletedEntries;
        DirectoryInfo myParentInfo;
        
        public DirectoryInfo(String path, String cmpPath, long cmpRev, DirectoryInfo parent) {
            myFullPath = path;
            myParentInfo = parent;
            myComparePath = cmpPath;
            myCompareRevision = cmpRev;
            myDeletedEntries = new SVNHashMap();
            myIsWrittenOut = false;
        }
    }
}
