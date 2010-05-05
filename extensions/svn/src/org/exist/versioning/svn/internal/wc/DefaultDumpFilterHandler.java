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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
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
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNLoadHandler;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.ISVNAdminEventHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAction;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class DefaultDumpFilterHandler implements ISVNLoadHandler {

    private boolean myIsDoRenumberRevisions;
    private boolean myIsDoExclude;
    private boolean myIsPreserveRevisionProps;
    private boolean myIsDropEmptyRevisions;
    private boolean myIsSkipMissingMergeSources;
    private long myDroppedRevisionsCount;
    private long myLastLiveRevision;
    private OutputStream myOutputStream;
    private Collection myPrefixes;
    private Map myDroppedNodes;
    private Map myRenumberHistory;
    private RevisionBaton myCurrentRevisionBaton;
    private NodeBaton myCurrentNodeBaton;
    private ISVNAdminEventHandler myEventHandler;
    
    public DefaultDumpFilterHandler(OutputStream os, ISVNAdminEventHandler handler, boolean exclude, 
            boolean renumberRevisions, boolean dropEmptyRevisions, boolean preserveRevisionProperties, 
            Collection prefixes, boolean skipMissingMergeSources) {
        myDroppedRevisionsCount = 0;
        myLastLiveRevision = SVNRepository.INVALID_REVISION;
        myOutputStream = os;
        myEventHandler = handler;
        myIsDoExclude = exclude;
        myIsDoRenumberRevisions = renumberRevisions;
        myIsDropEmptyRevisions = dropEmptyRevisions;
        myIsPreserveRevisionProps = preserveRevisionProperties;
        myIsSkipMissingMergeSources = skipMissingMergeSources;
        myPrefixes = prefixes;
        myDroppedNodes = new SVNHashMap();
        myRenumberHistory = new SVNHashMap();
    }
    
    public void reset(OutputStream os, ISVNAdminEventHandler handler, boolean exclude, boolean renumberRevisions, 
            boolean dropEmptyRevisions, boolean preserveRevisionProperties, Collection prefixes, 
            boolean skipMissingMergeSources) {
        myDroppedRevisionsCount = 0;
        myLastLiveRevision = SVNRepository.INVALID_REVISION;
        myOutputStream = os;
        myEventHandler = handler;
        myIsDoExclude = exclude;
        myIsDoRenumberRevisions = renumberRevisions;
        myIsDropEmptyRevisions = dropEmptyRevisions;
        myIsPreserveRevisionProps = preserveRevisionProperties;
        myIsSkipMissingMergeSources = skipMissingMergeSources;
        myPrefixes = prefixes;
        myDroppedNodes.clear();
        myRenumberHistory.clear();
    }
    
    public void closeNode() throws SVNException {
        if (myCurrentNodeBaton.myIsDoSkip) {
            return;
        }
        if (!myCurrentNodeBaton.myHasWritingBegun) {
            outputNode(myCurrentNodeBaton);
        }
        writeDumpData(myOutputStream, "\n\n");
    }

    public void closeRevision() throws SVNException {
        if (myCurrentRevisionBaton != null && !myCurrentRevisionBaton.myHasWritingBegun) {
            outputRevision(myCurrentRevisionBaton);
        }
    }

    public void openNode(Map headers) throws SVNException {
        myCurrentNodeBaton = new NodeBaton();
        String nodePath = (String) headers.get(SVNAdminHelper.DUMPFILE_NODE_PATH);
        String copyFromPath = (String) headers.get(SVNAdminHelper.DUMPFILE_NODE_COPYFROM_PATH);
        if (!nodePath.startsWith("/")) {
            nodePath = "/" + nodePath;    
        }
        if (copyFromPath != null && !copyFromPath.startsWith("/")) {
            copyFromPath = "/" + copyFromPath;
        }
        
        myCurrentNodeBaton.myIsDoSkip = skipPath(nodePath);
        if (myCurrentNodeBaton.myIsDoSkip) {
            myDroppedNodes.put(nodePath, nodePath);
            myCurrentRevisionBaton.myHadDroppedNodes = true;
        } else {
            long textContentLength = getLongFromHeaders(SVNAdminHelper.DUMPFILE_TEXT_CONTENT_LENGTH, headers);
            if (copyFromPath != null && skipPath(copyFromPath)) {
                SVNNodeKind kind = getNodeKindFromHeaders(SVNAdminHelper.DUMPFILE_NODE_KIND, headers);
                if (textContentLength >= 0 && kind == SVNNodeKind.FILE) {
                    headers.remove(SVNAdminHelper.DUMPFILE_NODE_COPYFROM_PATH);
                    headers.remove(SVNAdminHelper.DUMPFILE_NODE_COPYFROM_REVISION);
                    copyFromPath = null;
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, 
                            "Invalid copy source path ''{0}''", copyFromPath);
                    SVNErrorManager.error(err, SVNLogType.FSFS);
                }
            }
            
            myCurrentNodeBaton.myTextContentLength = textContentLength > 0 ? textContentLength : 0;
            myCurrentRevisionBaton.myHasNodes = true;
            if (!myCurrentRevisionBaton.myHasWritingBegun) {
                outputRevision(myCurrentRevisionBaton);
            }
            
            for (Iterator headersIter = headers.keySet().iterator(); headersIter.hasNext();) {
                String header = (String) headersIter.next();
                if (header.equals(SVNAdminHelper.DUMPFILE_CONTENT_LENGTH) || 
                        header.equals(SVNAdminHelper.DUMPFILE_PROP_CONTENT_LENGTH) ||
                        header.equals(SVNAdminHelper.DUMPFILE_TEXT_CONTENT_LENGTH)) {
                    continue;
                }

                String headerValue = (String) headers.get(header);
                if (myIsDoRenumberRevisions && header.equals(SVNAdminHelper.DUMPFILE_NODE_COPYFROM_REVISION)) {
                    long copyFromOriginalRevision = -1;
                    try {
                        copyFromOriginalRevision = Long.parseLong(headerValue);
                    } catch (NumberFormatException nfe) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, nfe), SVNLogType.FSFS);
                    }
                    RevisionItem reNumberedCopyFromValue = (RevisionItem) myRenumberHistory.get(new Long(copyFromOriginalRevision));
                    if (reNumberedCopyFromValue == null || 
                            !SVNRevision.isValidRevisionNumber(reNumberedCopyFromValue.myRevision)) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, 
                                "No valid copyfrom revision in filtered stream");
                        SVNErrorManager.error(err, SVNLogType.FSFS);
                    }
                    writeDumpData(myOutputStream, SVNAdminHelper.DUMPFILE_NODE_COPYFROM_REVISION + ": " +
                            reNumberedCopyFromValue.myRevision + "\n");
                    continue;
                }
                writeDumpData(myOutputStream, header + ": " + headerValue + "\n");
            }
        }
    }

    public void openRevision(Map headers) throws SVNException {
        RevisionBaton revisionBaton = new RevisionBaton();
        revisionBaton.myProperties = new SVNProperties();
        revisionBaton.myOriginalRevision = getLongFromHeaders(SVNAdminHelper.DUMPFILE_REVISION_NUMBER, headers);
        if (myIsDoRenumberRevisions) {
            revisionBaton.myActualRevision = revisionBaton.myOriginalRevision - myDroppedRevisionsCount; 
        } else {
            revisionBaton.myActualRevision = revisionBaton.myOriginalRevision;
        }
        
        revisionBaton.writeToHeader(SVNAdminHelper.DUMPFILE_REVISION_NUMBER + ": " + 
                revisionBaton.myActualRevision + "\n");
        
        for (Iterator headersIter = headers.keySet().iterator(); headersIter.hasNext();) {
            String header = (String) headersIter.next();
            String headerValue = (String) headers.get(header);
            if (header.equals(SVNAdminHelper.DUMPFILE_CONTENT_LENGTH) || 
                    header.equals(SVNAdminHelper.DUMPFILE_PROP_CONTENT_LENGTH) ||
                    header.equals(SVNAdminHelper.DUMPFILE_REVISION_NUMBER)) {
                continue;
            }
            revisionBaton.writeToHeader(header + ": " + headerValue + "\n");
        }
        
        myCurrentRevisionBaton = revisionBaton;
    }

    public void parseTextBlock(InputStream dumpStream, long contentLength, boolean isDelta) throws SVNException {
        if (isDelta) {
            applyTextDelta();
        } else {
            setFullText();
        }
        
        byte[] buffer = null;
        if (contentLength > 0) {
            buffer = new byte[SVNFileUtil.STREAM_CHUNK_SIZE];
            while (contentLength > 0) {
                int numToRead = contentLength > SVNFileUtil.STREAM_CHUNK_SIZE ? 
                        SVNFileUtil.STREAM_CHUNK_SIZE : (int) contentLength;
                int read = 0;
                while(numToRead > 0) {
                    int numRead = -1;
                    try {
                        numRead = dumpStream.read(buffer, read, numToRead);
                    } catch (IOException ioe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getMessage());
                        SVNErrorManager.error(err, ioe, SVNLogType.FSFS);
                    }

                    if (numRead < 0) {
                        SVNAdminHelper.generateIncompleteDataError();
                    }
                    read += numRead;
                    numToRead -= numRead;
                }
                            
                if (!myCurrentNodeBaton.myIsDoSkip) {
                    try {
                        myOutputStream.write(buffer, 0, read);
                    } catch (IOException ioe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF, 
                                "Unexpected EOF writing contents");
                        SVNErrorManager.error(err, ioe, SVNLogType.FSFS);
                    }
                }

                contentLength -= read;
            }
        }
    }

    public void parseUUID(String uuid) throws SVNException {
        writeDumpData(myOutputStream, SVNAdminHelper.DUMPFILE_UUID + ": " + uuid + "\n\n");
    }

    public void removeNodeProperties() throws SVNException {
        myCurrentNodeBaton.myHasProps = true;
    }

    public void setFullText() throws SVNException {
        if (!myCurrentNodeBaton.myIsDoSkip) {
            myCurrentNodeBaton.myHasText = true;
            if (!myCurrentNodeBaton.myHasWritingBegun) {
                outputNode(myCurrentNodeBaton);
            }
        }
    }

    public void setRevisionProperty(String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        myCurrentRevisionBaton.myHasProps = true;
        if (propertyValue == null) {
            myCurrentRevisionBaton.myProperties.remove(propertyName);
        } else {
            myCurrentRevisionBaton.myProperties.put(propertyName, propertyValue);
        }
    }

    public void setNodeProperty(String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        if (myCurrentNodeBaton.myIsDoSkip) {
            return;
        }
        if (!myCurrentNodeBaton.myHasProps) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                    "Delta property block detected - not supported by svndumpfilter");
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        if (propertyName.equals(SVNProperty.MERGE_INFO)) {
            Map filteredMergeInfo = adjustMergeInfo(propertyValue);
            propertyValue = SVNPropertyValue.create(SVNMergeInfoUtil.formatMergeInfoToString(filteredMergeInfo, null));
        }
        myCurrentNodeBaton.writeProperty(propertyName, propertyValue);
    }

    public void deleteNodeProperty(String propertyName) throws SVNException {
    }

    public void applyTextDelta() throws SVNException {
    }

    public long getDroppedRevisionsCount() {
        return myDroppedRevisionsCount;
    }

    public Map getRenumberHistory() {
        return myRenumberHistory;
    }

    public Map getDroppedNodes() {
        return myDroppedNodes;
    }

    private void outputRevision(RevisionBaton revisionBaton) throws SVNException {
        revisionBaton.myHasWritingBegun = true;
        if (!myIsPreserveRevisionProps && !revisionBaton.myHasNodes && revisionBaton.myHadDroppedNodes &&
                !myIsDropEmptyRevisions) {
            SVNProperties oldProps = revisionBaton.myProperties;
            revisionBaton.myHasProps = true;
            revisionBaton.myProperties = new SVNProperties();
            revisionBaton.myProperties.put(SVNRevisionProperty.DATE, 
                    oldProps.getSVNPropertyValue(SVNRevisionProperty.DATE));
            revisionBaton.myProperties.put(SVNRevisionProperty.LOG, "This is an empty revision for padding.");
        }
        
        ByteArrayOutputStream propsBuffer = new ByteArrayOutputStream();
        if (revisionBaton.myHasProps) {
            for (Iterator propsIter = revisionBaton.myProperties.nameSet().iterator(); propsIter.hasNext();) {
                String propName = (String) propsIter.next();
                SVNPropertyValue propValue = revisionBaton.myProperties.getSVNPropertyValue(propName);
                writeProperty(propsBuffer, propName, propValue);
            }
            writeDumpData(propsBuffer, "PROPS-END\n");
            revisionBaton.writeToHeader(SVNAdminHelper.DUMPFILE_PROP_CONTENT_LENGTH + ": " + propsBuffer.size() + 
                    "\n");
        }
        
        revisionBaton.writeToHeader(SVNAdminHelper.DUMPFILE_CONTENT_LENGTH + ": " + propsBuffer.size() + "\n\n");
        writeDumpData(propsBuffer, "\n");
        
        if (revisionBaton.myHasNodes || !myIsDropEmptyRevisions || !revisionBaton.myHadDroppedNodes) {
            writeDumpData(myOutputStream, revisionBaton.myHeaderBuffer.toByteArray());
            writeDumpData(myOutputStream, propsBuffer.toByteArray());
            
            if (myIsDoRenumberRevisions) {
                myRenumberHistory.put(new Long(revisionBaton.myOriginalRevision), 
                        new RevisionItem(revisionBaton.myActualRevision, false));
                myLastLiveRevision = revisionBaton.myActualRevision;
            }
            
            String message = MessageFormat.format("Revision {0} committed as {1}.", new Object[] { 
                    String.valueOf(revisionBaton.myOriginalRevision), 
                    String.valueOf(revisionBaton.myActualRevision) });
            dispatchEvent(new SVNAdminEvent(revisionBaton.myActualRevision, revisionBaton.myOriginalRevision, 
                    SVNAdminEventAction.DUMP_FILTER_REVISION_COMMITTED, message));
        } else {
            myDroppedRevisionsCount++;
            if (myIsDoRenumberRevisions) {
                myRenumberHistory.put(new Long(revisionBaton.myOriginalRevision), 
                        new RevisionItem(myLastLiveRevision, true));
            }
            
            String message = MessageFormat.format("Revision {0} skipped.", new Object[] { 
                    String.valueOf(revisionBaton.myOriginalRevision) });
            dispatchEvent(new SVNAdminEvent(revisionBaton.myOriginalRevision, 
                    SVNAdminEventAction.DUMP_FILTER_REVISION_SKIPPED, message));
        }
    }
    
    private void outputNode(NodeBaton nodeBaton) throws SVNException {
        nodeBaton.myHasWritingBegun = true;
        if (nodeBaton.myHasProps) {
            nodeBaton.writeToPropertyBuffer("PROPS-END\n");
            nodeBaton.writeToHeader(SVNAdminHelper.DUMPFILE_PROP_CONTENT_LENGTH + ": " + 
                    nodeBaton.myPropertiesBuffer.size() + "\n");
        }
        if (nodeBaton.myHasText) {
            nodeBaton.writeToHeader(SVNAdminHelper.DUMPFILE_TEXT_CONTENT_LENGTH + ": " + 
                    nodeBaton.myTextContentLength + "\n");
        }
        nodeBaton.writeToHeader(SVNAdminHelper.DUMPFILE_CONTENT_LENGTH + ": " + 
                (nodeBaton.myPropertiesBuffer.size() + nodeBaton.myTextContentLength) + "\n\n");
        writeDumpData(myOutputStream, nodeBaton.myHeaderBuffer.toByteArray());
        writeDumpData(myOutputStream, nodeBaton.myPropertiesBuffer.toByteArray());
    }
    
    private void writeProperty(OutputStream out, String propName, SVNPropertyValue propValue) throws SVNException {
        try {
            writeDumpData(out, "K ");
            byte[] propNameBytes = propName.getBytes("UTF-8");
            writeDumpData(out, String.valueOf(propNameBytes.length));
            writeDumpData(out, "\n");
            writeDumpData(out, propNameBytes);
            
            writeDumpData(out, "\n");
            writeDumpData(out, "V ");
            byte[] propValueBytes = SVNPropertyValue.getPropertyAsBytes(propValue);
            writeDumpData(out, String.valueOf(propValueBytes.length));
            writeDumpData(out, "\n");
            writeDumpData(out, propValueBytes);
            writeDumpData(out, "\n");
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e, SVNLogType.FSFS);
        } 
    }
    
    private Map adjustMergeInfo(SVNPropertyValue initialValue) throws SVNException {
        Map finalMergeInfo = new TreeMap();
        Map mergeInfo = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(initialValue.getString()), null);
        for (Iterator mergeInfoIter = mergeInfo.keySet().iterator(); mergeInfoIter.hasNext();) {
            String mergeSource = (String) mergeInfoIter.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(mergeSource);
            if (skipPath(mergeSource)) {
                if (myIsSkipMissingMergeSources) {
                    continue;
                }

                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, 
                        "Missing merge source path ''{0}''; try with --skip-missing-merge-sources", 
                        mergeSource);
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }
            
            if (myIsDoRenumberRevisions) {
                SVNMergeRange[] ranges = rangeList.getRanges();
                for (int i = 0; i < rangeList.getSize(); i++) {
                    SVNMergeRange range = ranges[i];
                    
                    RevisionItem revItemStart = (RevisionItem) myRenumberHistory.get(new Long(range.getStartRevision()));
                    if (revItemStart == null || !SVNRevision.isValidRevisionNumber(revItemStart.myRevision)) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, 
                                "No valid revision range 'start' in filtered stream");
                        SVNErrorManager.error(err, SVNLogType.FSFS);
                    }

                    RevisionItem revItemEnd = (RevisionItem) myRenumberHistory.get(new Long(range.getEndRevision()));
                    if (revItemEnd == null || !SVNRevision.isValidRevisionNumber(revItemEnd.myRevision)) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, 
                                "No valid revision range 'end' in filtered stream");
                        SVNErrorManager.error(err, SVNLogType.FSFS);
                    }
                    
                    range.setStartRevision(revItemStart.myRevision);
                    range.setEndRevision(revItemEnd.myRevision);
                }
                Arrays.sort(ranges);
            }
            finalMergeInfo.put(mergeSource, rangeList);
        }
        return finalMergeInfo;
    }
    
    private SVNNodeKind getNodeKindFromHeaders(String header, Map headers) {
        return SVNNodeKind.parseKind((String) headers.get(header)); 
    }
    
    private long getLongFromHeaders(String header, Map headers) {
        String val = (String) headers.get(header);
        if (val != null) {
            try {
                return Long.parseLong(val);
            } catch (NumberFormatException nfe) {
                //
            }
        }
        return -1;
    }
    
    private void writeDumpData(OutputStream out, String data) throws SVNException {
        try {
            out.write(data.getBytes("UTF-8")); 
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe, SVNLogType.FSFS);
        }
    }

    private void writeDumpData(OutputStream out, byte[] bytes) throws SVNException {
        try {
            out.write(bytes); 
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe, SVNLogType.FSFS);
        }
    }

    private boolean skipPath(String path) {
        for (Iterator prefixesIter = myPrefixes.iterator(); prefixesIter.hasNext();) {
            String prefix = (String) prefixesIter.next();
            if (path.startsWith(prefix)) {
                return myIsDoExclude;
            }
        }
        return !myIsDoExclude;
    }
    
    private void dispatchEvent(SVNAdminEvent event) throws SVNException {
        if (myEventHandler != null) {
            myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
        }
    }

    public class RevisionItem {
        long myRevision;
        boolean myWasDropped;
        
        public RevisionItem(long revision, boolean dropped) {
            myRevision = revision;
            myWasDropped = dropped;
        }
        
        public boolean wasDropped() {
            return myWasDropped;
        }
        
        public long getRevision() {
            return myRevision;
        }
    }

    private class RevisionBaton {
        boolean myHasNodes;
        boolean myHasProps;
        boolean myHadDroppedNodes;
        boolean myHasWritingBegun;
        long myOriginalRevision;
        long myActualRevision;
        SVNProperties myProperties;
        ByteArrayOutputStream myHeaderBuffer;
 
        void writeToHeader(String data) throws SVNException {
            if (myHeaderBuffer == null) {
                myHeaderBuffer = new ByteArrayOutputStream();
            }
            writeDumpData(myHeaderBuffer, data);
        }
    }
    
    private class NodeBaton {
        boolean myIsDoSkip;
        boolean myHasProps;
        boolean myHasText;
        boolean myHasWritingBegun;
        long myTextContentLength;
        ByteArrayOutputStream myPropertiesBuffer;
        ByteArrayOutputStream myHeaderBuffer;
        
        public NodeBaton() {
            myPropertiesBuffer = new ByteArrayOutputStream();
            myHeaderBuffer = new ByteArrayOutputStream();
        }
        
        void writeProperty(String propName, SVNPropertyValue propValue) throws SVNException {
            DefaultDumpFilterHandler.this.writeProperty(myPropertiesBuffer, propName, propValue);
        }
        
        void writeToPropertyBuffer(String data) throws SVNException {
            DefaultDumpFilterHandler.this.writeDumpData(myPropertiesBuffer, data);
        }
        
        void writeToHeader(String data) throws SVNException {
            writeDumpData(myHeaderBuffer, data);
        }

    }

}
