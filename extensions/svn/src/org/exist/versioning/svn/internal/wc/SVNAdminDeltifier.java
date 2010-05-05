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

import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCombiner;
import org.tmatesoft.svn.core.internal.io.fs.FSEntry;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryUtil;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminDeltifier {

    private FSFS myFSFS;
    private SVNDepth myDepth;
    private boolean myIsIncludeEntryProperties;
    private boolean myIsIgnoreAncestry;
    private boolean myIsSendTextDeltas;
    private ISVNEditor myEditor;
    private SVNDeltaCombiner myDeltaCombiner;
    private SVNDeltaGenerator myDeltaGenerator;
    
    public SVNAdminDeltifier(FSFS fsfs, SVNDepth depth, boolean includeEntryProperties, 
            boolean ignoreAncestry, boolean sendTextDeltas, ISVNEditor editor) {
        myFSFS = fsfs;
        myDepth = depth;
        myIsIncludeEntryProperties = includeEntryProperties;
        myIsIgnoreAncestry = ignoreAncestry;
        myIsSendTextDeltas = sendTextDeltas;
        myEditor = editor;
        myDeltaCombiner = new SVNDeltaCombiner();
        myDeltaGenerator = new SVNDeltaGenerator();
    }
    
    public void setEditor(ISVNEditor editor) {
        myEditor = editor;
    }
    
    public void deltifyDir(FSRevisionRoot srcRoot, String srcParentDir, String srcEntry, 
            FSRevisionRoot tgtRoot, String tgtFullPath) throws SVNException {
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
            myEditor.closeEdit();
            return;
        }

        if (srcEntry == null && (srcKind != SVNNodeKind.DIR || tgtKind != SVNNodeKind.DIR)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_PATH_SYNTAX, 
                    "Invalid editor anchoring; at least one " + 
                    "of the input paths is not a directory " + 
                    "and there was no source entry");
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }

        myEditor.targetRevision(tgtRoot.getRevision());
        long rootRevision = srcRoot.getRevision();
        if (tgtKind == SVNNodeKind.NONE) {
            myEditor.openRoot(rootRevision);
            myEditor.deleteEntry(srcEntry, -1);
            myEditor.closeDir();
            myEditor.closeEdit();
            return;
        }

        if (srcKind == SVNNodeKind.NONE) {
            myEditor.openRoot(rootRevision);
            addFileOrDir(srcRoot, tgtRoot, tgtFullPath, srcEntry, tgtKind);
            myEditor.closeDir();
            myEditor.closeEdit();
            return;
        }

        FSRevisionNode srcNode = srcRoot.getRevisionNode(srcFullPath);
        FSRevisionNode tgtNode = tgtRoot.getRevisionNode(tgtFullPath);
        int distance = srcNode.getId().compareTo(tgtNode.getId());

        if (distance == 0) {
            myEditor.closeEdit();
        } else if (srcEntry != null) {
            if (srcKind != tgtKind || distance == -1) {
                myEditor.openRoot(rootRevision);
                myEditor.deleteEntry(srcEntry, -1);
                addFileOrDir(srcRoot, tgtRoot, tgtFullPath, srcEntry, tgtKind);
            } else {
                myEditor.openRoot(rootRevision);
                replaceFileOrDir(srcRoot, tgtRoot, srcFullPath, tgtFullPath, srcEntry, tgtKind);
            }

            myEditor.closeDir();
            myEditor.closeEdit();
        } else {
            myEditor.openRoot(rootRevision);
            deltifyDirs(srcRoot, tgtRoot, srcFullPath, tgtFullPath, "");
            myEditor.closeDir();
            myEditor.closeEdit();
        }
    }

    private void addFileOrDir(FSRevisionRoot srcRoot, FSRevisionRoot tgtRoot, String tgtPath, 
            String editPath, SVNNodeKind tgtKind) throws SVNException {
        if (tgtKind == SVNNodeKind.DIR) {
            myEditor.addDir(editPath, null, -1);
            deltifyDirs(srcRoot, tgtRoot, null, tgtPath, editPath);
            myEditor.closeDir();
        } else {
            myEditor.addFile(editPath, null, -1);
            deltifyFiles(srcRoot, tgtRoot, null, tgtPath, editPath);
            FSRevisionNode tgtNode = tgtRoot.getRevisionNode(tgtPath);
            myEditor.closeFile(editPath, tgtNode.getFileMD5Checksum());
        }
    }

    private void deltifyDirs(FSRevisionRoot srcRoot, FSRevisionRoot tgtRoot, String srcPath, 
            String tgtPath, String editPath) throws SVNException {
        deltifyProperties(srcRoot, tgtRoot, srcPath, tgtPath, editPath, true);

        FSRevisionNode targetNode = tgtRoot.getRevisionNode(tgtPath);
        Map targetEntries = targetNode.getDirEntries(myFSFS);
        
        Map sourceEntries = null;
        if (srcPath != null) {
            FSRevisionNode sourceNode = srcRoot.getRevisionNode(srcPath);
            sourceEntries = sourceNode.getDirEntries(myFSFS);
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
                
                if (myDepth == SVNDepth.INFINITY || srcKind != SVNNodeKind.DIR) {
                    int distance = srcEntry.getId().compareTo(tgtEntry.getId());
                    if (srcKind != tgtKind || (distance == -1 && !myIsIgnoreAncestry)) {
                        myEditor.deleteEntry(editFullPath, -1);
                        addFileOrDir(srcRoot, tgtRoot, targetFullPath, editFullPath, tgtKind);
                    } else if (distance != 0) {
                        replaceFileOrDir(srcRoot, tgtRoot, sourceFullPath, targetFullPath, 
                                editFullPath, tgtKind);
                    }
                    
                }
                sourceEntries.remove(name);
            } else {
                if (myDepth == SVNDepth.INFINITY || tgtKind != SVNNodeKind.DIR) {
                    addFileOrDir(srcRoot, tgtRoot, targetFullPath, editFullPath, tgtKind);
                }
            }
        }
        
        if (sourceEntries != null) {
            for (Iterator srcEntries = sourceEntries.keySet().iterator(); srcEntries.hasNext();) {
                String name = (String) srcEntries.next();
                FSEntry srcEntry = (FSEntry) sourceEntries.get(name);
                String editFullPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(editPath, srcEntry.getName()));
                if (myDepth == SVNDepth.INFINITY || srcEntry.getType() != SVNNodeKind.DIR) {
                    myEditor.deleteEntry(editFullPath, -1);
                }
            }
        }
    }

    private void replaceFileOrDir(FSRevisionRoot srcRoot, FSRevisionRoot tgtRoot, String srcPath, 
            String tgtPath, String editPath, SVNNodeKind tgtKind) throws SVNException {
        long baseRevision = srcRoot.getRevision();
        if (tgtKind == SVNNodeKind.DIR) {
            myEditor.openDir(editPath, baseRevision);
            deltifyDirs(srcRoot, tgtRoot, srcPath, tgtPath, editPath);
            myEditor.closeDir();
        } else {
            myEditor.openFile(editPath, baseRevision);
            deltifyFiles(srcRoot, tgtRoot, srcPath, tgtPath, editPath);
            FSRevisionNode tgtNode = tgtRoot.getRevisionNode(tgtPath);
            myEditor.closeFile(editPath, tgtNode.getFileMD5Checksum());
        }
    }
    
    private void deltifyFiles(FSRevisionRoot srcRoot, FSRevisionRoot tgtRoot, String srcPath, 
            String tgtPath, String editPath) throws SVNException {
        deltifyProperties(srcRoot, tgtRoot, srcPath, tgtPath, editPath, false);
        
        boolean changed = false;
        if (srcPath != null) {
            if (myIsIgnoreAncestry) {
                changed = FSRepositoryUtil.checkFilesDifferent(srcRoot, srcPath, tgtRoot, tgtPath, 
                        myDeltaCombiner);
            } else {
                changed = FSRepositoryUtil.areFileContentsChanged(srcRoot, srcPath, tgtRoot, tgtPath);    
            }
        }
        
        if (changed) {
            String srcHexDigest = null;
            if (srcPath != null) {
                FSRevisionNode srcNode = srcRoot.getRevisionNode(srcPath);
                srcHexDigest = srcNode.getFileMD5Checksum();
            }
            
            FSRepositoryUtil.sendTextDelta(myEditor, editPath, srcPath, srcHexDigest, srcRoot, 
                    tgtPath, tgtRoot, myIsSendTextDeltas, myDeltaCombiner, myDeltaGenerator, myFSFS);
        }
    }

    private void deltifyProperties(FSRevisionRoot srcRoot, FSRevisionRoot tgtRoot, String srcPath, 
            String tgtPath, String editPath, boolean isDir) throws SVNException {
        if (myIsIncludeEntryProperties) {
            
            FSRevisionNode node = tgtRoot.getRevisionNode(tgtPath);
            long committedRevision = node.getCreatedRevision();
            if (SVNRevision.isValidRevisionNumber(committedRevision)) {
                if (isDir) {
                    myEditor.changeDirProperty(SVNProperty.COMMITTED_REVISION, SVNPropertyValue.create(String.valueOf(committedRevision)));
                } else {
                    myEditor.changeFileProperty(editPath, SVNProperty.COMMITTED_REVISION, SVNPropertyValue.create(String.valueOf(committedRevision)));
                }
                
                SVNProperties revisionProps = myFSFS.getRevisionProperties(committedRevision);
                String committedDateStr = revisionProps.getStringValue(SVNRevisionProperty.DATE);
                if (committedDateStr != null || srcPath != null) {
                    if (isDir) {
                        myEditor.changeDirProperty(SVNProperty.COMMITTED_DATE, SVNPropertyValue.create(committedDateStr));
                    } else {
                        myEditor.changeFileProperty(editPath,SVNProperty.COMMITTED_DATE, SVNPropertyValue.create(committedDateStr));
                    }
                }
                String lastAuthor = revisionProps.getStringValue(SVNRevisionProperty.AUTHOR);
                if (lastAuthor != null || srcPath != null) {
                    if (isDir) {
                        myEditor.changeDirProperty(SVNProperty.LAST_AUTHOR, SVNPropertyValue.create(lastAuthor));
                    } else {
                        myEditor.changeFileProperty(editPath,SVNProperty.LAST_AUTHOR, SVNPropertyValue.create(lastAuthor));
                    }
                }

                String uuid = myFSFS.getUUID();
                if (isDir) {
                    myEditor.changeDirProperty(SVNProperty.UUID, SVNPropertyValue.create(uuid));
                } else {
                    myEditor.changeFileProperty(editPath, SVNProperty.UUID, SVNPropertyValue.create(uuid));
                }
            }
        }
        
        FSRevisionNode targetNode = tgtRoot.getRevisionNode(tgtPath);

        SVNProperties sourceProps = null;
        if (srcPath != null) {
            FSRevisionNode sourceNode = srcRoot.getRevisionNode(srcPath);
            boolean propsChanged = !FSRepositoryUtil.arePropertiesEqual(sourceNode, targetNode);
            if (!propsChanged) {
                return;
            }
            sourceProps = sourceNode.getProperties(myFSFS);
        } else {
            sourceProps = new SVNProperties();
        }

        SVNProperties targetProps = targetNode.getProperties(myFSFS);
        SVNProperties propsDiffs = FSRepositoryUtil.getPropsDiffs(sourceProps, targetProps);
        Object[] names = propsDiffs.nameSet().toArray();
        for (int i = 0; i < names.length; i++) {
            String propName = (String) names[i];
            SVNPropertyValue propValue = propsDiffs.getSVNPropertyValue(propName);
            if (isDir) {
                myEditor.changeDirProperty(propName, propValue);
            } else {
                myEditor.changeFileProperty(editPath, propName, propValue);
            }
        }
    }

    private static void generateNotADirError(String role, String path) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_DIRECTORY, 
                "Invalid {0} directory ''{1}''", new Object[]{role, 
                path != null ? path : "(null)"});
        SVNErrorManager.error(err, SVNLogType.FSFS);
    }

}

