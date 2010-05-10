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
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaReader;
import org.tmatesoft.svn.core.internal.io.fs.FSCommitter;
import org.tmatesoft.svn.core.internal.io.fs.FSDeltaConsumer;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionRoot;
import org.tmatesoft.svn.core.internal.util.FixedSizeInputStream;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNLoadHandler;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.ISVNAdminEventHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAction;
import org.tmatesoft.svn.core.wc.admin.SVNUUIDAction;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class DefaultLoadHandler implements ISVNLoadHandler {
    
    private FSFS myFSFS;
    private RevisionBaton myCurrentRevisionBaton;
    private NodeBaton myCurrentNodeBaton;
    private boolean myIsUsePreCommitHook;
    private boolean myIsUsePostCommitHook;
    private Map myRevisionsMap;
    private String myParentDir;
    private SVNUUIDAction myUUIDAction;
    private SVNDeltaReader myDeltaReader;
    private SVNDeltaGenerator myDeltaGenerator;
    private ISVNAdminEventHandler myProgressHandler;
    
    public DefaultLoadHandler(boolean usePreCommitHook, boolean usePostCommitHook, SVNUUIDAction uuidAction, 
            String parentDir, ISVNAdminEventHandler progressHandler) {
        myProgressHandler = progressHandler;
        myIsUsePreCommitHook = usePreCommitHook;
        myIsUsePostCommitHook = usePostCommitHook;
        myUUIDAction = uuidAction;
        myParentDir = SVNPathUtil.canonicalizePath(parentDir);
        myRevisionsMap = new SVNHashMap();
    }
    
    public void setFSFS(FSFS fsfs) {
        myFSFS = fsfs;
    }
    
    public void closeRevision() throws SVNException {
        if (myCurrentRevisionBaton != null) {
            myCurrentRevisionBaton.getConsumer().close();
            
            RevisionBaton baton = myCurrentRevisionBaton;
            myCurrentRevisionBaton = null;
            
            if (baton.myRevision <= 0) {
                return;
            }
            
            long oldRevision = baton.myRevision;
            long newRevision = -1;
            try {
                newRevision = baton.getCommitter().commitTxn(myIsUsePreCommitHook, myIsUsePostCommitHook, null, null);
            } catch (SVNException svne) {
                try {
                    FSCommitter.abortTransaction(myFSFS, baton.myTxn.getTxnId());
                } catch (SVNException svne2) {
                    //
                }
                throw svne;
            }
            
            if (baton.myDatestamp == null) {
                myFSFS.setRevisionProperty(baton.myRevision, SVNRevisionProperty.DATE, null);
            }
            File revProps = myFSFS.getRevisionPropertiesFile(baton.myRevision, true);
            if (!revProps.exists()) {
                OutputStream os = SVNFileUtil.openFileForWriting(revProps);
                try {
                    SVNWCProperties.setProperties(new SVNProperties(), os, SVNWCProperties.SVN_HASH_TERMINATOR);
                } finally {
                    SVNFileUtil.closeFile(os);
                }
            }

            myRevisionsMap.put(new Long(oldRevision), new Long(newRevision));
            if (baton.myDatestamp != null) {
                myFSFS.setRevisionProperty(newRevision, SVNRevisionProperty.DATE, baton.myDatestamp);
            }
            
            String message;
            if (newRevision == baton.myRevision) {
                message = "\n------- Committed revision " + newRevision + " >>>";
            } else {
                message = "\n------- Committed new rev " + newRevision + " (loaded from original rev " + baton.myRevision + ") >>>";
            }
            if (myProgressHandler != null) {
                SVNAdminEvent event = new SVNAdminEvent(newRevision, baton.myRevision, SVNAdminEventAction.REVISION_LOADED, message); 
                myProgressHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
            }
        }
    }

    public void openRevision(Map headers) throws SVNException {
        myCurrentRevisionBaton = new RevisionBaton();
        long revision = -1;
        if (headers.containsKey(SVNAdminHelper.DUMPFILE_REVISION_NUMBER)) {
            try {
                revision = Long.parseLong((String) headers.get(SVNAdminHelper.DUMPFILE_REVISION_NUMBER)); 
            } catch (NumberFormatException nfe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Cannot parse revision ({0}) in dump file", headers.get(SVNAdminHelper.DUMPFILE_REVISION_NUMBER));
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }
        }
        
        myCurrentRevisionBaton.myRevision = revision;
        long headRevision = myFSFS.getYoungestRevision();
        myCurrentRevisionBaton.myRevisionOffset = revision - (headRevision + 1);
        
        if (revision > 0) {
            myCurrentRevisionBaton.myTxn = FSTransactionRoot.beginTransaction(headRevision, 0, myFSFS);
            myCurrentRevisionBaton.myTxnRoot = myFSFS.createTransactionRoot(myCurrentRevisionBaton.myTxn);
            String message = "<<< Started new transaction, based on original revision " + revision;
            if (myProgressHandler != null) {
                SVNAdminEvent event = new SVNAdminEvent(revision, SVNAdminEventAction.REVISION_LOAD, message); 
                myProgressHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
            }
        }
    }

    public void openNode(Map headers) throws SVNException {
        if (myCurrentRevisionBaton.myRevision == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Malformed dumpstream: Revision 0 must not contain node records");
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        
        myCurrentNodeBaton = createNodeBaton(headers);
        String message;
        switch (myCurrentNodeBaton.myAction) {
            case SVNAdminHelper.NODE_ACTION_CHANGE:
                message = "     * editing path : " + myCurrentNodeBaton.myPath + " ...";
                if (myProgressHandler != null) {
                    SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.REVISION_LOAD_EDIT_PATH, myCurrentNodeBaton.myPath, message); 
                    myProgressHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                }
                break;
            case SVNAdminHelper.NODE_ACTION_DELETE:
                message = "     * deleting path : " + myCurrentNodeBaton.myPath + " ...";
                if (myProgressHandler != null) {
                    SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.REVISION_LOAD_DELETE_PATH, myCurrentNodeBaton.myPath, message); 
                    myProgressHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                }
                myCurrentRevisionBaton.getCommitter().deleteNode(myCurrentNodeBaton.myPath);
                break;
            case SVNAdminHelper.NODE_ACTION_ADD:
                message = "     * adding path : " + myCurrentNodeBaton.myPath + " ...";
                if (maybeAddWithHistory(myCurrentNodeBaton)) {
                    message += "COPIED...";
                }
                if (myProgressHandler != null) {
                    SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.REVISION_LOAD_ADD_PATH, myCurrentNodeBaton.myPath, message); 
                    myProgressHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                }
                break;
            case SVNAdminHelper.NODE_ACTION_REPLACE:
                message = "     * replacing path : " + myCurrentNodeBaton.myPath + " ...";
                myCurrentRevisionBaton.getCommitter().deleteNode(myCurrentNodeBaton.myPath);
                if (maybeAddWithHistory(myCurrentNodeBaton)) {
                    message += "COPIED...";
                }
                if (myProgressHandler != null) {
                    SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.REVISION_LOAD_REPLACE_PATH, myCurrentNodeBaton.myPath, message); 
                    myProgressHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                }
                break;
            default:
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNRECOGNIZED_DATA, "Unrecognized node-action on node ''{0}''", myCurrentNodeBaton.myPath);
                SVNErrorManager.error(err, SVNLogType.FSFS);
        }
    }

    public void parseUUID(String uuid) throws SVNException {
        if (myUUIDAction == SVNUUIDAction.IGNORE_UUID) {
            return;
        }
        
        if (myUUIDAction != SVNUUIDAction.FORCE_UUID) {
            long latestRevision = myFSFS.getYoungestRevision();
            if (latestRevision != 0) {
                return;
            }
        }

        myFSFS.setUUID(uuid);
    }

    public void closeNode() throws SVNException {
        myCurrentNodeBaton = null;
    }

    public void applyTextDelta() throws SVNException {
        FSDeltaConsumer fsConsumer = myCurrentRevisionBaton.getConsumer();
        fsConsumer.applyTextDelta(myCurrentNodeBaton.myPath, myCurrentNodeBaton.myBaseChecksum);
    }

    public void setFullText() throws SVNException {
        FSDeltaConsumer fsConsumer = myCurrentRevisionBaton.getConsumer();
        fsConsumer.applyText(myCurrentNodeBaton.myPath);
    }

    public void parseTextBlock(InputStream dumpStream, long contentLength, boolean isDelta) throws SVNException {
        FSDeltaConsumer fsConsumer = myCurrentRevisionBaton.getConsumer();

        try {
            if (isDelta) {
                applyTextDelta();
            } else {
                setFullText();
            }
            
            String checksum = null;
            byte[] buffer = null;
            if (contentLength == 0) {
                getDeltaGenerator().sendDelta(myCurrentNodeBaton.myPath, SVNFileUtil.DUMMY_IN, fsConsumer, false);
            } else {
                if (!isDelta) {
                    // 
                    InputStream tgt = new FixedSizeInputStream(dumpStream, contentLength);
                    checksum = getDeltaGenerator().sendDelta(myCurrentNodeBaton.myPath, tgt, fsConsumer, true);
                } else {
                    buffer = new byte[SVNFileUtil.STREAM_CHUNK_SIZE];
                    SVNDeltaReader deltaReader = null;
                    try {
                        while (contentLength > 0) {
                            int numToRead = contentLength > SVNFileUtil.STREAM_CHUNK_SIZE ? SVNFileUtil.STREAM_CHUNK_SIZE : (int) contentLength;
                            int read = 0;
                            while(numToRead > 0) {
                                int numRead = dumpStream.read(buffer, read, numToRead);
                                if (numRead < 0) {
                                    SVNAdminHelper.generateIncompleteDataError();
                                }
                                read += numRead;
                                numToRead -= numRead;
                            }
                            deltaReader = getDeltaReader();
                            deltaReader.nextWindow(buffer, 0, read, myCurrentNodeBaton.myPath, fsConsumer);
                            contentLength -= read;
                        }
                    } catch (IOException ioe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getMessage());
                        SVNErrorManager.error(err, ioe, SVNLogType.FSFS);
                    }
                    if (deltaReader != null) {
                        deltaReader.reset(myCurrentNodeBaton.myPath, fsConsumer);
                    }
                    fsConsumer.textDeltaEnd(myCurrentNodeBaton.myPath);
                    checksum = fsConsumer.getChecksum();
                }
            }
            
            if (checksum != null && myCurrentNodeBaton.myResultChecksum != null) {
                if (!checksum.equals(myCurrentNodeBaton.myResultChecksum)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH, "Checksum mismatch for ''{0}'':\n   expected:  {1}\n     actual:  {2}\n", 
                            new Object[] { myCurrentNodeBaton.myPath, myCurrentNodeBaton.myResultChecksum, checksum });
                    SVNErrorManager.error(err, SVNLogType.FSFS);
                }
            }
        } catch (SVNException svne) {
            fsConsumer.abort();
            throw svne;
        }
    }

    public void removeNodeProperties() throws SVNException {
        FSTransactionRoot txnRoot = myCurrentRevisionBaton.myTxnRoot;
        FSRevisionNode node = txnRoot.getRevisionNode(myCurrentNodeBaton.myPath);
        SVNProperties props = node.getProperties(myFSFS);
        
        for (Iterator propNames = props.nameSet().iterator(); propNames.hasNext();) {
            String propName = (String) propNames.next();
            myCurrentRevisionBaton.getCommitter().changeNodeProperty(myCurrentNodeBaton.myPath, propName, null);
        }
    }

    public void setRevisionProperty(String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        if (myCurrentRevisionBaton.myRevision > 0) {
            myFSFS.setTransactionProperty(myCurrentRevisionBaton.myTxn.getTxnId(), propertyName, propertyValue);
            if (SVNRevisionProperty.DATE.equals(propertyName)) {
                myCurrentRevisionBaton.myDatestamp = propertyValue;
            }
        } else if (myCurrentRevisionBaton.myRevision == 0) {
            long youngestRevision = myFSFS.getYoungestRevision();
            if (youngestRevision == 0) {
                myFSFS.setRevisionProperty(0, propertyName, propertyValue);
            }
        }
    }

    public void setUsePreCommitHook(boolean use) {
        myIsUsePreCommitHook = use;
    }
    
    public void setUsePostCommitHook(boolean use) {
        myIsUsePostCommitHook = use;
    }
    
    public void setParentDir(String parentDir) {
        myParentDir = parentDir;
    }

    public void setUUIDAction(SVNUUIDAction action) {
        myUUIDAction = action;
    }
    
    public void deleteNodeProperty(String propertyName) throws SVNException {
        myCurrentRevisionBaton.getCommitter().changeNodeProperty(myCurrentNodeBaton.myPath, propertyName, null);
    }
    
    public void setNodeProperty(String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        if (SVNProperty.MERGE_INFO.equals(propertyName)) {
            Map mergeInfo = renumberMergeInfoRevisions(propertyValue);
            if (myParentDir != null) {
                mergeInfo = prefixMergeInfoPaths(mergeInfo);
            }
            String mergeInfoString = SVNMergeInfoUtil.formatMergeInfoToString(mergeInfo, null);
            propertyValue = SVNPropertyValue.create(mergeInfoString);
        }
        myCurrentRevisionBaton.getCommitter().changeNodeProperty(myCurrentNodeBaton.myPath, propertyName, 
                propertyValue);
    }

    private SVNDeltaReader getDeltaReader() {
        if (myDeltaReader == null) {
            myDeltaReader = new SVNDeltaReader();
        } 
        return myDeltaReader;
    }

    private SVNDeltaGenerator getDeltaGenerator() {
        if (myDeltaGenerator == null) {
            myDeltaGenerator = new SVNDeltaGenerator();
        }
        return myDeltaGenerator;
    }

    private boolean maybeAddWithHistory(NodeBaton nodeBaton) throws SVNException {
        if (nodeBaton.myCopyFromPath == null) {
            if (nodeBaton.myKind == SVNNodeKind.FILE) {
                myCurrentRevisionBaton.getCommitter().makeFile(nodeBaton.myPath);
            } else if (nodeBaton.myKind == SVNNodeKind.DIR) {
                myCurrentRevisionBaton.getCommitter().makeDir(nodeBaton.myPath);
            }
            return false;
        } 
        long srcRevision = nodeBaton.myCopyFromRevision - myCurrentRevisionBaton.myRevisionOffset;
        Long copyFromRevision = new Long(nodeBaton.myCopyFromRevision);
        
        if (myRevisionsMap.containsKey(copyFromRevision)) {
            Long revision = (Long) myRevisionsMap.get(copyFromRevision);
            srcRevision = revision.longValue();
        }
        
        if (!SVNRevision.isValidRevisionNumber(srcRevision)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "Relative source revision {0} is not available in current repository", new Long(srcRevision));
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        
        FSRevisionRoot copyRoot = myFSFS.createRevisionRoot(srcRevision);
        if (nodeBaton.myCopySourceChecksum != null) {
            FSRevisionNode revNode = copyRoot.getRevisionNode(nodeBaton.myCopyFromPath);
            String hexDigest = revNode.getFileMD5Checksum();
            if (hexDigest != null && !hexDigest.equals(nodeBaton.myCopySourceChecksum)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH, 
                        "Copy source checksum mismatch on copy from ''{0}''@{1}\n" +
                        " to ''{2}'' in rev based on r{3}:\n" +
                        "   expected:  {4}\n" + 
                        "     actual:  {5}\n", new Object[] { nodeBaton.myCopyFromPath, 
                        String.valueOf(srcRevision), nodeBaton.myPath, 
                        String.valueOf(myCurrentRevisionBaton.myRevision), 
                        nodeBaton.myCopySourceChecksum, hexDigest });
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }
        }
        myCurrentRevisionBaton.getCommitter().makeCopy(copyRoot, nodeBaton.myCopyFromPath, nodeBaton.myPath, true);
        
        return true;
    }
    
    private NodeBaton createNodeBaton(Map headers) throws SVNException {
        NodeBaton baton = new NodeBaton();
        baton.myKind = SVNNodeKind.UNKNOWN;
        if (headers.containsKey(SVNAdminHelper.DUMPFILE_NODE_PATH)) {
            String nodePath = (String) headers.get(SVNAdminHelper.DUMPFILE_NODE_PATH); 
            if (myParentDir != null) {
                baton.myPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(myParentDir, nodePath));
            } else {
                baton.myPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.canonicalizePath(nodePath));
            }
        }
        
        if (headers.containsKey(SVNAdminHelper.DUMPFILE_NODE_KIND)) {
            baton.myKind = SVNNodeKind.parseKind((String) headers.get(SVNAdminHelper.DUMPFILE_NODE_KIND));
        }
        
        baton.myAction = SVNAdminHelper.NODE_ACTION_UNKNOWN;
        if (headers.containsKey(SVNAdminHelper.DUMPFILE_NODE_ACTION)) {
            String action = (String) headers.get(SVNAdminHelper.DUMPFILE_NODE_ACTION);
            if ("change".equals(action)) {
                baton.myAction = SVNAdminHelper.NODE_ACTION_CHANGE;
            } else if ("add".equals(action)) {
                baton.myAction = SVNAdminHelper.NODE_ACTION_ADD;
            } else if ("delete".equals(action)) {
                baton.myAction = SVNAdminHelper.NODE_ACTION_DELETE;
            } else if ("replace".equals(action)) {
                baton.myAction = SVNAdminHelper.NODE_ACTION_REPLACE;
            }
        }
        
        baton.myCopyFromRevision = -1;
        if (headers.containsKey(SVNAdminHelper.DUMPFILE_NODE_COPYFROM_REVISION)) {
            try {
                baton.myCopyFromRevision = Long.parseLong((String) headers.get(SVNAdminHelper.DUMPFILE_NODE_COPYFROM_REVISION)); 
            } catch (NumberFormatException nfe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Cannot parse revision ({0}) in dump file", headers.get(SVNAdminHelper.DUMPFILE_NODE_COPYFROM_REVISION));
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }
        }
        
        if (headers.containsKey(SVNAdminHelper.DUMPFILE_NODE_COPYFROM_PATH)) {
            String copyFromPath = (String) headers.get(SVNAdminHelper.DUMPFILE_NODE_COPYFROM_PATH);
            if (myParentDir != null) {
                baton.myCopyFromPath = SVNPathUtil.append(myParentDir, copyFromPath);
            } else {
                baton.myCopyFromPath = SVNPathUtil.canonicalizePath(copyFromPath);
            }
            baton.myCopyFromPath = SVNPathUtil.getAbsolutePath(baton.myCopyFromPath);
        }
        
        if (headers.containsKey(SVNAdminHelper.DUMPFILE_TEXT_CONTENT_MD5)) {
            baton.myResultChecksum = (String) headers.get(SVNAdminHelper.DUMPFILE_TEXT_CONTENT_MD5);
        }        
        
        if (headers.containsKey(SVNAdminHelper.DUMPFILE_TEXT_DELTA_BASE_MD5)) {
            baton.myBaseChecksum = (String) headers.get(SVNAdminHelper.DUMPFILE_TEXT_DELTA_BASE_MD5);
        }
        
        if (headers.containsKey(SVNAdminHelper.DUMPFILE_TEXT_COPY_SOURCE_MD5)) {
            baton.myCopySourceChecksum = (String) headers.get(SVNAdminHelper.DUMPFILE_TEXT_COPY_SOURCE_MD5);
        }
        return baton;
    }
    
    private Map renumberMergeInfoRevisions(SVNPropertyValue mergeInfoProp) throws SVNException {
        String mergeInfoString = SVNPropertyValue.getPropertyAsString(mergeInfoProp);
        Map mergeInfo = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(mergeInfoString), null);
        for (Iterator mergeInfoIter = mergeInfo.keySet().iterator(); mergeInfoIter.hasNext();) {
            String mergeSource = (String) mergeInfoIter.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(mergeSource);
            SVNMergeRange[] ranges = rangeList.getRanges();
            for (int i = 0; i < ranges.length; i++) {
                SVNMergeRange range = ranges[i];
                Long revFromMap = (Long) myRevisionsMap.get(new Long(range.getStartRevision()));
                if (revFromMap != null && SVNRevision.isValidRevisionNumber(revFromMap.longValue())) {
                    range.setStartRevision(revFromMap.longValue());
                }
                revFromMap = (Long) myRevisionsMap.get(new Long(range.getEndRevision()));
                if (revFromMap != null && SVNRevision.isValidRevisionNumber(revFromMap.longValue())) {
                    range.setEndRevision(revFromMap.longValue());
                }
            }
            Arrays.sort(ranges);
        }
        return mergeInfo;
    }
    
    private Map prefixMergeInfoPaths(Map mergeInfo) {
        Map prefixedMergeInfo = new TreeMap();
        for (Iterator mergeInfoIter = mergeInfo.keySet().iterator(); mergeInfoIter.hasNext();) {
            String mergeSource = (String) mergeInfoIter.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(mergeSource);
            mergeSource = mergeSource.startsWith("/") ? mergeSource.substring(1) : mergeSource;
            mergeSource = SVNPathUtil.append(myParentDir, mergeSource);
            if (mergeSource.charAt(0) != '/') {
                mergeSource = '/' + mergeSource;
            } 
            prefixedMergeInfo.put(mergeSource, rangeList);
        }
        return prefixedMergeInfo;
    }
    
    private class RevisionBaton {
        FSTransactionInfo myTxn;
        FSTransactionRoot myTxnRoot;
        long myRevision;
        long myRevisionOffset;
        SVNPropertyValue myDatestamp;
        
        private FSCommitter myCommitter;
        private FSDeltaConsumer myDeltaConsumer;
        
        public FSDeltaConsumer getConsumer() {
            if (myDeltaConsumer == null) {
                myDeltaConsumer = new FSDeltaConsumer("", myTxnRoot, myFSFS, getCommitter(), null, null);
            }
            return myDeltaConsumer;
        }
        
        public FSCommitter getCommitter() {
            if (myCommitter == null) {
                myCommitter = new FSCommitter(myFSFS, myTxnRoot, myTxn, null, null);
            }
            return myCommitter;
        }
    }
    
    private class NodeBaton {
        String myPath;
        SVNNodeKind myKind;
        int myAction;
        String myBaseChecksum;
        String myResultChecksum;
        String myCopySourceChecksum;
        long myCopyFromRevision;
        String myCopyFromPath;
    }
}
