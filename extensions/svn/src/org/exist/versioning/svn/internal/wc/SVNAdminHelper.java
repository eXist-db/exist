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

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.io.fs.FSEntry;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryUtil;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminHelper {
    
    public static int writeRevisionProperties(SVNRepository toRepository, long revision, SVNProperties revProps) throws SVNException {
        int filteredCount = 0;
        for (Iterator propNamesIter = revProps.nameSet().iterator(); propNamesIter.hasNext();) {
            String propName = (String) propNamesIter.next();
            SVNPropertyValue propValue = revProps.getSVNPropertyValue(propName);
            if (propName.startsWith(SVNProperty.SVN_SYNC_PREFIX)) {
                filteredCount++;
            } else {
                toRepository.setRevisionPropertyValue(revision, propName, propValue);
            }
        }
        return filteredCount;
    }

    public static void removePropertiesNotInSource(SVNRepository repository, long revision, 
            SVNProperties sourceProps, SVNProperties targetProps) throws SVNException {
        for (Iterator propNamesIter = targetProps.nameSet().iterator(); propNamesIter.hasNext();) {
            String propName = (String) propNamesIter.next();
            if (sourceProps.getSVNPropertyValue(propName) == null) {
                repository.setRevisionPropertyValue(revision, propName, null);
            }
        }
    }

    public static FSFS openRepository(File reposRootPath, boolean openFS) throws SVNException {
        FSFS fsfs = new FSFS(reposRootPath);
        if (openFS) {
            fsfs.open();
        } else {
            fsfs.openRoot();
            fsfs.getFSType();
        }
        return fsfs;
    }

    public static void closeRepository(FSFS fsfs) {
        if (fsfs != null) {
            try {
                fsfs.close();
            } catch (SVNException e) {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.FSFS, e);
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.FSFS, e.getMessage());
            }
        }
    }

    public static FSFS openRepositoryForRecovery(File reposRootPath) throws SVNException {
        FSFS fsfs = new FSFS(reposRootPath);
        fsfs.openForRecovery();
        return fsfs;
    }

    public static long getRevisionNumber(SVNRevision revision, long youngestRevision, FSFS fsfs) throws SVNException {
        long revNumber = -1;
        if (revision.getNumber() >= 0) {
            revNumber = revision.getNumber();
        } else if (revision == SVNRevision.HEAD) {
            revNumber = youngestRevision;
        } else if (revision.getDate() != null) {
            revNumber = fsfs.getDatedRevision(revision.getDate());
        } else if (revision != SVNRevision.UNDEFINED) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Invalid revision specifier");
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        
        if (revNumber > youngestRevision) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Revisions must not be greater than the youngest revision ({0})", new Long(youngestRevision));
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        return revNumber;
    }
    
    public static void writeProperties(SVNProperties props, SVNProperties oldProps, OutputStream dumpStream) throws SVNException {
        LinkedList propNames = new LinkedList();
        for(Iterator names = props.nameSet().iterator(); names.hasNext();) {
            String propName = (String) names.next();
            if (SVNRevisionProperty.LOG.equals(propName)) {
                  propNames.addFirst(propName);
            } else if (SVNRevisionProperty.AUTHOR.equals(propName)) {
                if (propNames.contains(SVNRevisionProperty.LOG)) {
                    int ind = propNames.indexOf(SVNRevisionProperty.LOG);
                    propNames.add(ind + 1, propName);
                } else {
                    propNames.addFirst(propName);
                }
            } else {
                propNames.addLast(propName);
            }
        }
        
        for(Iterator names = propNames.iterator(); names.hasNext();) {
            String propName = (String) names.next();
            SVNPropertyValue propValue = props.getSVNPropertyValue(propName);
            if (oldProps != null) {
                SVNPropertyValue oldValue = oldProps.getSVNPropertyValue(propName);
                if (oldValue != null && oldValue.equals(propValue)) {
                    continue;
                }
            }
            
            SVNWCProperties.appendProperty(propName, propValue, dumpStream);
        }
        
        if (oldProps != null) {
            for(Iterator names = oldProps.nameSet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                if (props.containsName(propName)) {
                    continue;
                }
                SVNWCProperties.appendPropertyDeleted(propName, dumpStream);
            }            
        }
        
        try {
            byte[] terminator = "PROPS-END\n".getBytes("UTF-8");
            dumpStream.write(terminator);
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe, SVNLogType.FSFS);
        }
    }
    
    public static void deltifyDir(FSFS fsfs, FSRevisionRoot srcRoot, String srcParentDir, 
                                  String srcEntry, FSRevisionRoot tgtRoot, String tgtFullPath, 
                                  ISVNEditor editor) throws SVNException {
        if (srcParentDir == null) {
            generateNotADirError("source parent", srcParentDir);
        }
        
        if (tgtFullPath == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_PATH_SYNTAX, 
                                                         "Invalid target path");
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        
        String srcFullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(srcParentDir, srcEntry));
        SVNNodeKind tgtKind = tgtRoot.checkNodeKind(tgtFullPath); 
        SVNNodeKind srcKind = srcRoot.checkNodeKind(srcFullPath);
        
        if (tgtKind == SVNNodeKind.NONE && srcKind == SVNNodeKind.NONE) {
            editor.closeEdit();
            return;
        }
        
        if (srcEntry == null && (srcKind != SVNNodeKind.DIR || tgtKind != SVNNodeKind.DIR)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_PATH_SYNTAX, "Invalid editor anchoring; at least one of the input paths is not a directory and there was no source entry");
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        
        editor.targetRevision(tgtRoot.getRevision());
        long rootRevision = srcRoot.getRevision();
        if (tgtKind == SVNNodeKind.NONE) {
            editor.openRoot(rootRevision);
            editor.deleteEntry(srcEntry, -1);
            editor.closeDir();
            editor.closeEdit();
            return;
        }
        if (srcKind == SVNNodeKind.NONE) {
            editor.openRoot(rootRevision);
            addFileOrDir(fsfs, editor, srcRoot, tgtRoot, tgtFullPath, srcEntry, tgtKind);
            editor.closeDir();
            editor.closeEdit();
            return;
        }

        FSRevisionNode srcNode = srcRoot.getRevisionNode(srcFullPath);
        FSRevisionNode tgtNode = tgtRoot.getRevisionNode(tgtFullPath);
        int distance = srcNode.getId().compareTo(tgtNode.getId());
        if (distance == 0) {
            editor.closeEdit();
            return;
        } else if (srcEntry != null) {
            if (srcKind != tgtKind || distance == -1) {
                editor.openRoot(rootRevision);
                editor.deleteEntry(srcEntry, -1);
                addFileOrDir(fsfs, editor, srcRoot, tgtRoot, tgtFullPath, srcEntry, tgtKind);
            } else {
                editor.openRoot(rootRevision);
                replaceFileOrDir(fsfs, editor, srcRoot, tgtRoot, srcFullPath, tgtFullPath, srcEntry, tgtKind);
            }
            editor.closeDir();
            editor.closeEdit();
        } else {
            editor.openRoot(rootRevision);
            deltifyDirs(fsfs, editor, srcRoot, tgtRoot, srcFullPath, tgtFullPath, "");
            editor.closeDir();
            editor.closeEdit();
        }
    }
    
    public static void generateIncompleteDataError() throws SVNException {
        SVNDebugLog.getDefaultLog().logFine(SVNLogType.FSFS, new Exception());
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, 
                "Premature end of content data in dumpstream");
        SVNErrorManager.error(err, SVNLogType.FSFS);
    }

    public static void generateStreamMalformedError() throws SVNException {
        SVNDebugLog.getDefaultLog().logFine(SVNLogType.FSFS, new Exception());
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Dumpstream data appears to be malformed");
        SVNErrorManager.error(err, SVNLogType.FSFS);
    }

    public static int readKeyOrValue(InputStream dumpStream, byte[] buffer, int len) throws SVNException, IOException {
        int read = 0;
        while(len - read > 0) {
            int r = dumpStream.read(buffer, read, len - read);
            if (r < 0) {
                break;
            }
            read += r;
        }        
        if (read != len) {
            SVNAdminHelper.generateIncompleteDataError();
        }
        if (buffer[len - 1] != '\n') {
            SVNAdminHelper.generateStreamMalformedError();
        }
        return read - 1;
    }

    private static void addFileOrDir(FSFS fsfs, ISVNEditor editor, FSRevisionRoot srcRoot, 
            FSRevisionRoot tgtRoot, String tgtPath, String editPath, SVNNodeKind tgtKind) throws SVNException {
        if (tgtKind == SVNNodeKind.DIR) {
            editor.addDir(editPath, null, -1);
            deltifyDirs(fsfs, editor, srcRoot, tgtRoot, null, tgtPath, editPath);
            editor.closeDir();
        } else {
            editor.addFile(editPath, null, -1);
            deltifyFiles(fsfs, editor, srcRoot, tgtRoot, null, tgtPath, editPath);
            FSRevisionNode tgtNode = tgtRoot.getRevisionNode(tgtPath);
            editor.closeFile(editPath, tgtNode.getFileMD5Checksum());
        }
    }

    private static void replaceFileOrDir(FSFS fsfs, ISVNEditor editor, FSRevisionRoot srcRoot, FSRevisionRoot tgtRoot, String srcPath, String tgtPath, String editPath, SVNNodeKind tgtKind) throws SVNException {
        long baseRevision = srcRoot.getRevision();
        if (tgtKind == SVNNodeKind.DIR) {
            editor.openDir(editPath, baseRevision);
            deltifyDirs(fsfs, editor, srcRoot, tgtRoot, srcPath, tgtPath, editPath);
            editor.closeDir();
        } else {
            editor.openFile(editPath, baseRevision);
            deltifyFiles(fsfs, editor, srcRoot, tgtRoot, srcPath, tgtPath, editPath);
            FSRevisionNode tgtNode = tgtRoot.getRevisionNode(tgtPath);
            editor.closeFile(editPath, tgtNode.getFileMD5Checksum());
        }
    }
    
    private static void deltifyFiles(FSFS fsfs, ISVNEditor editor, FSRevisionRoot srcRoot, FSRevisionRoot tgtRoot, String srcPath, String tgtPath, String editPath) throws SVNException {
        deltifyProperties(fsfs, editor, srcRoot, tgtRoot, srcPath, tgtPath, editPath, false);
        
        boolean changed = false;
        if (srcPath != null) {
            changed = FSRepositoryUtil.areFileContentsChanged(srcRoot, srcPath, tgtRoot, tgtPath);
        }
        
        if (changed) {
            String srcHexDigest = null;
            if (srcPath != null) {
                FSRevisionNode srcNode = srcRoot.getRevisionNode(srcPath);
                srcHexDigest = srcNode.getFileMD5Checksum();
            }
            editor.applyTextDelta(editPath, srcHexDigest);
            editor.textDeltaChunk(editPath, SVNDiffWindow.EMPTY);
        }
    }
    
    private static void deltifyDirs(FSFS fsfs, ISVNEditor editor, FSRevisionRoot srcRoot, FSRevisionRoot tgtRoot, String srcPath, String tgtPath, String editPath) throws SVNException {
        deltifyProperties(fsfs, editor, srcRoot, tgtRoot, srcPath, tgtPath, editPath, true);

        FSRevisionNode targetNode = tgtRoot.getRevisionNode(tgtPath);
        Map targetEntries = targetNode.getDirEntries(fsfs);
        
        Map sourceEntries = null;
        if (srcPath != null) {
            FSRevisionNode sourceNode = srcRoot.getRevisionNode(srcPath);
            sourceEntries = sourceNode.getDirEntries(fsfs);
        }

        for (Iterator tgtEntries = targetEntries.keySet().iterator(); tgtEntries.hasNext();) {
            String name = (String) tgtEntries.next();
            FSEntry tgtEntry = (FSEntry) targetEntries.get(name);
            
            SVNNodeKind tgtKind = tgtEntry.getType();
            String targetFullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(tgtPath, tgtEntry.getName()));
            String editFullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(editPath, tgtEntry.getName()));
            
            if (sourceEntries != null && sourceEntries.containsKey(name)) {
                FSEntry srcEntry = (FSEntry) sourceEntries.get(name);
                String sourceFullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(srcPath, tgtEntry.getName()));
                SVNNodeKind srcKind = srcEntry.getType();
                
                int distance = srcEntry.getId().compareTo(tgtEntry.getId());
                if (srcKind != tgtKind || distance == -1) {
                    editor.deleteEntry(editFullPath, -1);
                    addFileOrDir(fsfs, editor, srcRoot, tgtRoot, targetFullPath, editFullPath, tgtKind);
                } else if (distance != 0) {
                    replaceFileOrDir(fsfs, editor, srcRoot, tgtRoot, sourceFullPath, targetFullPath, editFullPath, tgtKind);
                }
                sourceEntries.remove(name);
            } else {
                addFileOrDir(fsfs, editor, srcRoot, tgtRoot, targetFullPath, editFullPath, tgtKind);
            }
        }
        
        if (sourceEntries != null) {
            for (Iterator srcEntries = sourceEntries.keySet().iterator(); srcEntries.hasNext();) {
                String name = (String) srcEntries.next();
                FSEntry entry = (FSEntry) sourceEntries.get(name);
                String editFullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(editPath, entry.getName()));
                editor.deleteEntry(editFullPath, -1);
            }
        }
    }

    private static void deltifyProperties(FSFS fsfs, ISVNEditor editor, FSRevisionRoot srcRoot, FSRevisionRoot tgtRoot, String srcPath, String tgtPath, String editPath, boolean isDir) throws SVNException {
        FSRevisionNode targetNode = tgtRoot.getRevisionNode(tgtPath);

        SVNProperties sourceProps = null;
        if (srcPath != null) {
            FSRevisionNode sourceNode = srcRoot.getRevisionNode(srcPath);
            boolean propsChanged = !FSRepositoryUtil.arePropertiesEqual(sourceNode, targetNode);
            if (!propsChanged) {
                return;
            }
            sourceProps = sourceNode.getProperties(fsfs);
        } else {
            sourceProps = new SVNProperties();
        }

        SVNProperties targetProps = targetNode.getProperties(fsfs);
        SVNProperties propsDiffs = FSRepositoryUtil.getPropsDiffs(sourceProps, targetProps);
        Object[] names = propsDiffs.nameSet().toArray();
        for (int i = 0; i < names.length; i++) {
            String propName = (String) names[i];
            SVNPropertyValue propValue = propsDiffs.getSVNPropertyValue(propName);
            if (isDir) {
                editor.changeDirProperty(propName, propValue);
            } else {
                editor.changeFileProperty(editPath, propName, propValue);
            }
        }
    }
    
    private static void generateNotADirError(String role, String path) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_DIRECTORY, "Invalid {0} directory ''{1}''", new Object[]{role, path != null ? path : "(null)"});
        SVNErrorManager.error(err, SVNLogType.FSFS);
    }
    
    public static final String DUMPFILE_MAGIC_HEADER               = "SVN-fs-dump-format-version";
    public static final String DUMPFILE_CONTENT_LENGTH             = "Content-length";
    public static final String DUMPFILE_NODE_ACTION                = "Node-action";
    public static final String DUMPFILE_NODE_COPYFROM_PATH         = "Node-copyfrom-path";
    public static final String DUMPFILE_NODE_COPYFROM_REVISION     = "Node-copyfrom-rev";
    public static final String DUMPFILE_NODE_KIND                  = "Node-kind";
    public static final String DUMPFILE_NODE_PATH                  = "Node-path";
    public static final String DUMPFILE_PROP_CONTENT_LENGTH        = "Prop-content-length";
    public static final String DUMPFILE_PROP_DELTA                 = "Prop-delta";
    public static final String DUMPFILE_REVISION_NUMBER            = "Revision-number";
    public static final String DUMPFILE_TEXT_CONTENT_LENGTH        = "Text-content-length";
    public static final String DUMPFILE_TEXT_DELTA                 = "Text-delta";
    public static final String DUMPFILE_UUID                       = "UUID";
    public static final String DUMPFILE_TEXT_CONTENT_MD5           = "Text-content-md5";
    public static final String DUMPFILE_TEXT_CONTENT_SHA1          = "Text-content-sha1";
    public static final String DUMPFILE_TEXT_COPY_SOURCE_MD5       = "Text-copy-source-md5";
    public static final String DUMPFILE_TEXT_COPY_SOURCE_SHA1      = "Text-copy-source-sha1"; 
    public static final String DUMPFILE_TEXT_DELTA_BASE_MD5        = "Text-delta-base-md5";
    public static final String DUMPFILE_TEXT_DELTA_BASE_SHA1       = "Text-delta-base-sha1";
    public static final int DUMPFILE_FORMAT_VERSION                = 3;

    public static final int NODE_ACTION_ADD     = 1;
    public static final int NODE_ACTION_CHANGE  = 0;
    public static final int NODE_ACTION_DELETE  = 2;
    public static final int NODE_ACTION_REPLACE = 3;
    public static final int NODE_ACTION_UNKNOWN = -1;

}
