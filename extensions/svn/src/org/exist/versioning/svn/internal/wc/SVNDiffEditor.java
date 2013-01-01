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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminAreaInfo;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNTranslator;
import org.exist.versioning.svn.internal.wc.admin.SVNVersionedProperties;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.exist.versioning.svn.wc.ISVNOptions;
import org.exist.versioning.svn.wc.SVNWCUtil;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNDiffEditor implements ISVNEditor {

    private SVNWCAccess myWCAccess;
    private boolean myUseAncestry;
    private boolean myIsReverseDiff;
    private boolean myIsCompareToBase;
    private boolean myIsRootOpen;
    private long myTargetRevision;
    private SVNDirectoryInfo myCurrentDirectory;
    private SVNFileInfo myCurrentFile;
    private SVNDeltaProcessor myDeltaProcessor;
    private SVNAdminAreaInfo myAdminInfo;
    private SVNDepth myDepth;
    private File myTempDirectory;
    private AbstractDiffCallback myDiffCallback;
    private Collection myChangeLists;
    private String myWCRootPath;
    
    public SVNDiffEditor(SVNWCAccess wcAccess, SVNAdminAreaInfo info, AbstractDiffCallback callback,
            boolean useAncestry, boolean reverseDiff, boolean compareToBase, SVNDepth depth,
            Collection changeLists) {
        myWCAccess = wcAccess;
        myAdminInfo = info;
        myUseAncestry = useAncestry;
        myIsReverseDiff = reverseDiff;
        myDepth = depth;
        myIsCompareToBase = compareToBase;
        myDiffCallback = callback;
        myChangeLists = changeLists != null ? changeLists : Collections.EMPTY_LIST;
        myDeltaProcessor = new SVNDeltaProcessor();
    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpen = true;
        myCurrentDirectory = createDirInfo(null, "", false, myDepth);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        File fullPath = new Resource(myAdminInfo.getAnchor().getRoot(), path);
        SVNAdminArea dir = myWCAccess.probeRetrieve(fullPath);
        SVNEntry entry = myWCAccess.getEntry(fullPath, false);
        if (entry == null) {
            return;
        }
        String name = SVNPathUtil.tail(path);
        myCurrentDirectory.myComparedEntries.add(name);
        if (!myIsCompareToBase && entry.isScheduledForDeletion()) {
            return;
        }
        if (entry.isFile()) {
            if (myIsReverseDiff) {
                File baseFile = dir.getBaseFile(name, false);
                SVNProperties baseProps = dir.getBaseProperties(name).asMap();
                getDiffCallback().fileDeleted(path, baseFile, null, null, null, baseProps, null);
            } else {
                reportAddedFile(myCurrentDirectory, path, entry);
            }
        } else if (entry.isDirectory()) {
            SVNDirectoryInfo info = createDirInfo(myCurrentDirectory, path, false, SVNDepth.INFINITY);
            reportAddedDir(info);
        }
    }
    
    private void reportAddedDir(SVNDirectoryInfo info) throws SVNException {
        SVNAdminArea dir = retrieve(info.myPath);
        SVNEntry thisDirEntry = dir.getEntry(dir.getThisDirName(), false);
        if (SVNWCAccess.matchesChangeList(myChangeLists, thisDirEntry)) {
            SVNProperties wcProps;
            if (myIsCompareToBase) {
                wcProps = dir.getBaseProperties(dir.getThisDirName()).asMap();
            } else {
                wcProps = dir.getProperties(dir.getThisDirName()).asMap();
            }
            SVNProperties propDiff = computePropsDiff(new SVNProperties(), wcProps);
            if (!propDiff.isEmpty()) {
                getDiffCallback().propertiesChanged(info.myPath, null, propDiff, null);
            }
        }
        for(Iterator entries = dir.entries(false); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (dir.getThisDirName().equals(entry.getName())) {
                continue;
            }
            if (!myIsCompareToBase && entry.isScheduledForDeletion()) {
                continue;
            }
            if (entry.isFile()) {
                reportAddedFile(info, SVNPathUtil.append(info.myPath, entry.getName()), entry);
            } else if (entry.isDirectory()) {
                if (info.myDepth.compareTo(SVNDepth.FILES) > 0 || 
                    info.myDepth == SVNDepth.UNKNOWN) {
                    SVNDepth depthBelowHere = info.myDepth;
                    if (depthBelowHere == SVNDepth.IMMEDIATES) {
                        depthBelowHere = SVNDepth.EMPTY;
                    }
                    SVNDirectoryInfo childInfo = createDirInfo(info, 
                                                               SVNPathUtil.append(info.myPath, entry.getName()), 
                                                               false,
                                                               depthBelowHere);
                    reportAddedDir(childInfo);
                }
            }
        }
    }

    private void reportAddedFile(SVNDirectoryInfo info, String path, SVNEntry entry) throws SVNException {
        if (!SVNWCAccess.matchesChangeList(myChangeLists, entry)) {
            return;
        }
        
        if (entry.isCopied()) {
            if (myIsCompareToBase) {
                return;
            }
            reportModifiedFile(info, entry);
            return;
        }
        SVNAdminArea dir = retrieve(info.myPath);
        String name = SVNPathUtil.tail(path);
        SVNProperties wcProps = null;
        if (myIsCompareToBase) {
            wcProps = dir.getBaseProperties(name).asMap();
        } else {
            wcProps = dir.getProperties(name).asMap();
        }
        String mimeType = wcProps.getStringValue(SVNProperty.MIME_TYPE);
        SVNProperties propDiff = computePropsDiff(new SVNProperties(), wcProps);
        
        File sourceFile;
        if (myIsCompareToBase) {
            sourceFile = dir.getBaseFile(name, false);
        } else {
            sourceFile = detranslateFile(dir, name);
        }
        getDiffCallback().fileAdded(path, null, sourceFile, 0, entry.getRevision(), null, mimeType, null, propDiff, null);
    }
    
    private void reportModifiedFile(SVNDirectoryInfo dirInfo, SVNEntry entry) throws SVNException {
        SVNAdminArea dir = retrieve(dirInfo.myPath);
        if (!SVNWCAccess.matchesChangeList(myChangeLists, entry)) {
            return;
        }
        String schedule = entry.getSchedule();
        String fileName = entry.getName();
        if (!getDiffCallback().isDiffCopiedAsAdded() && entry.isCopied()) {
            schedule = null;
        }
        if (!myUseAncestry && entry.isScheduledForReplacement()) {
            schedule = null;
        }
        SVNProperties propDiff = null;
        SVNProperties baseProps = null;
        File baseFile = dir.getBaseFile(fileName, false);
        if (SVNFileType.getType(baseFile) == SVNFileType.NONE) {
            baseFile = dir.getFile(SVNAdminUtil.getTextRevertPath(fileName, false));
        }
        if (!entry.isScheduledForDeletion()) {
            if (getDiffCallback().isDiffCopiedAsAdded() && entry.isCopied()) {
                propDiff = dir.getProperties(fileName).asMap();
            } else {
                boolean modified = dir.hasPropModifications(fileName);
                if (modified) {
                    baseProps = dir.getBaseProperties(fileName).asMap();
                    propDiff = computePropsDiff(baseProps, dir.getProperties(fileName).asMap());
                } else {
                    propDiff = new SVNProperties();
                }
            }
        } else {
            baseProps = dir.getBaseProperties(fileName).asMap();
        }
        boolean isAdded = schedule != null && entry.isScheduledForAddition();
        String filePath = SVNPathUtil.append(dirInfo.myPath, fileName);
        if (schedule != null && (entry.isScheduledForDeletion() || entry.isScheduledForReplacement())) {
            String mimeType = dir.getBaseProperties(fileName).getStringPropertyValue(SVNProperty.MIME_TYPE);
            getDiffCallback().fileDeleted(filePath, baseFile, null, mimeType, null, dir.getBaseProperties(fileName).asMap(), null);
            isAdded = entry.isScheduledForReplacement();
        }
        if (isAdded) {
            String mimeType = dir.getProperties(fileName).getStringPropertyValue(SVNProperty.MIME_TYPE);
            
            File tmpFile = detranslateFile(dir, fileName);
            SVNProperties originalProperties = null; 
            long revision = entry.getRevision();
            if (entry.isCopied() && getDiffCallback().isDiffCopiedAsAdded()) {
                originalProperties = new SVNProperties();
                revision = 0;
            } else {
                originalProperties = dir.getBaseProperties(fileName).asMap();
            }
            getDiffCallback().fileAdded(filePath, null, tmpFile, 0, revision, mimeType, null, originalProperties, propDiff, null);
        } else if (schedule == null) {
            boolean modified = dir.hasTextModifications(fileName, false);
            File tmpFile = null;
            if (modified) {
                tmpFile = detranslateFile(dir, fileName);
            }
            if (modified || (propDiff != null && !propDiff.isEmpty())) {
                String baseMimeType = dir.getBaseProperties(fileName).getStringPropertyValue(SVNProperty.MIME_TYPE);
                String mimeType = dir.getProperties(fileName).getStringPropertyValue(SVNProperty.MIME_TYPE);

                getDiffCallback().fileChanged(filePath, modified ? baseFile : null, tmpFile, entry.getRevision(), -1, 
                        baseMimeType, mimeType, baseProps, propDiff, null);
            }
        }
        
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        SVNDepth subDirDepth = myCurrentDirectory.myDepth;
        if (subDirDepth == SVNDepth.IMMEDIATES) {
            subDirDepth = SVNDepth.EMPTY;    
        }
        myCurrentDirectory = createDirInfo(myCurrentDirectory, path, true, subDirDepth);
    }

    public void openDir(String path, long revision) throws SVNException {
        SVNDepth subDirDepth = myCurrentDirectory.myDepth;
        if (subDirDepth == SVNDepth.IMMEDIATES) {
            subDirDepth = SVNDepth.EMPTY;    
        }
        myCurrentDirectory = createDirInfo(myCurrentDirectory, path, false, subDirDepth);
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        if (myCurrentDirectory.myPropertyDiff == null) {
            myCurrentDirectory.myPropertyDiff = new SVNProperties();
        }
        myCurrentDirectory.myPropertyDiff.put(name, value);
    }

    public void closeDir() throws SVNException {
        // display dir prop changes.
        SVNProperties diff = myCurrentDirectory.myPropertyDiff;
        if (diff != null && !diff.isEmpty()) {
            // reverse changes
            SVNProperties originalProps = null;
            if (myCurrentDirectory.myIsAdded) {
                originalProps = new SVNProperties();
            } else {
                SVNAdminArea dir = retrieve(myCurrentDirectory.myPath);
                if (dir != null && myIsCompareToBase) {
                    originalProps = dir.getBaseProperties(dir.getThisDirName()).asMap();
                } else {
                    originalProps = dir.getProperties(dir.getThisDirName()).asMap();
                    SVNProperties baseProps = dir.getBaseProperties(dir.getThisDirName()).asMap();
                    SVNProperties reposProps = applyPropChanges(baseProps, myCurrentDirectory.myPropertyDiff);
                    diff = computePropsDiff(originalProps, reposProps);
                    
                }
            }
            if (!myIsReverseDiff) {
                reversePropChanges(originalProps, diff);
            }
            getDiffCallback().propertiesChanged(myCurrentDirectory.myPath, originalProps, diff, null);
            myCurrentDirectory.myComparedEntries.add("");
        }
        if (!myCurrentDirectory.myIsAdded) {
            localDirectoryDiff(myCurrentDirectory);
        }
        String name = SVNPathUtil.tail(myCurrentDirectory.myPath);
        myCurrentDirectory = myCurrentDirectory.myParent;
        if (myCurrentDirectory != null) {
            myCurrentDirectory.myComparedEntries.add(name);
        }
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        String name = SVNPathUtil.tail(path);
        myCurrentFile = createFileInfo(myCurrentDirectory, path, true);
        myCurrentDirectory.myComparedEntries.add(name);
    }

    public void openFile(String path, long revision) throws SVNException {
        String name = SVNPathUtil.tail(path);
        myCurrentFile = createFileInfo(myCurrentDirectory, path, false);
        myCurrentDirectory.myComparedEntries.add(name);
    }

    public void changeFileProperty(String path,String name, SVNPropertyValue value) throws SVNException {
        if (myCurrentFile.myPropertyDiff == null) {
            myCurrentFile.myPropertyDiff = new SVNProperties();
        }
        myCurrentFile.myPropertyDiff.put(name, value);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        SVNEntry entry = myWCAccess.getEntry(myAdminInfo.getAnchor().getFile(myCurrentFile.myPath), false);
        if (entry != null && entry.isCopied()) {
            myCurrentFile.myIsAdded = false;
        }
        if (!myCurrentFile.myIsAdded) {
            SVNAdminArea dir = retrieve(myCurrentDirectory.myPath);
            String fileName = SVNPathUtil.tail(myCurrentFile.myPath);
            myCurrentFile.myBaseFile = dir.getBaseFile(fileName, false);
        } 
        myCurrentFile.myFile = createTempFile();
        myDeltaProcessor.applyTextDelta(myCurrentFile.myBaseFile, myCurrentFile.myFile, false);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }

    public void textDeltaEnd(String path) throws SVNException {
        myDeltaProcessor.textDeltaEnd();
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
        String fileName = SVNPathUtil.tail(myCurrentFile.myPath);
        
        File filePath = myAdminInfo.getAnchor().getFile(myCurrentFile.myPath);
        SVNAdminArea dir = myWCAccess.probeRetrieve(filePath);
        SVNEntry entry = myWCAccess.getEntry(filePath, false);
        SVNProperties baseProperties = null;
        if (myCurrentFile.myIsAdded) {
            baseProperties = new SVNProperties();
        } else {
            baseProperties = dir != null ? dir.getBaseProperties(fileName).asMap() : new SVNProperties();
        }
        SVNProperties reposProperties = applyPropChanges(baseProperties, myCurrentFile.myPropertyDiff);
        String reposMimeType = reposProperties.getStringValue(SVNProperty.MIME_TYPE);
        File reposFile = myCurrentFile.myFile;
        File localFile = null;
        if (reposFile == null) {
            reposFile = dir.getBaseFile(fileName, false);
        }
        if (myCurrentFile.myIsAdded || (!myIsCompareToBase && entry.isScheduledForDeletion())) {
            if (myIsReverseDiff) {
                getDiffCallback().fileAdded(commitPath, null, reposFile, 0, myTargetRevision, null, reposMimeType, null, 
                        myCurrentFile.myPropertyDiff, null);
            } else {
                getDiffCallback().fileDeleted(commitPath, reposFile, null, reposMimeType, null, reposProperties, null);
            }
            return;
        }
        boolean modified = myCurrentFile.myFile != null;
        if (!modified && !myIsCompareToBase) {
            modified = dir.hasTextModifications(fileName, false);
        }
        if (modified) {
            if (myIsCompareToBase) {
                localFile = dir.getBaseFile(fileName, false);
            } else {
                localFile = detranslateFile(dir, fileName);
            }
        } else {
            localFile = null;
            reposFile = null;
        }
        
        SVNProperties originalProps = null;
        if (myIsCompareToBase) {
            originalProps = baseProperties;
        } else {
            originalProps = dir.getProperties(fileName).asMap();
            myCurrentFile.myPropertyDiff = computePropsDiff(originalProps, reposProperties);
        }
        
        if (localFile != null || (myCurrentFile.myPropertyDiff != null && !myCurrentFile.myPropertyDiff.isEmpty())) {
            String originalMimeType = originalProps.getStringValue(SVNProperty.MIME_TYPE);
            if (myCurrentFile.myPropertyDiff != null && !myCurrentFile.myPropertyDiff.isEmpty() && !myIsReverseDiff) {
                reversePropChanges(originalProps, myCurrentFile.myPropertyDiff);
            }
            if (localFile != null || reposFile != null || (myCurrentFile.myPropertyDiff != null && !myCurrentFile.myPropertyDiff.isEmpty())) {
                getDiffCallback().fileChanged(commitPath, 
                        myIsReverseDiff ? localFile : reposFile, 
                        myIsReverseDiff ? reposFile : localFile, 
                        myIsReverseDiff ? -1 : myTargetRevision,
                        myIsReverseDiff ? myTargetRevision : -1,
                        myIsReverseDiff ? originalMimeType : reposMimeType, 
                        myIsReverseDiff ? reposMimeType : originalMimeType,
                        originalProps, myCurrentFile.myPropertyDiff, null);
            }
        }
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (!myIsRootOpen) {
            localDirectoryDiff(createDirInfo(null, "", false, myDepth));
        }
        return null;
    }
    

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void cleanup() {
        if (myTempDirectory != null) {
            SVNFileUtil.deleteAll(myTempDirectory, true);
        }
    }
    
    private SVNProperties applyPropChanges(SVNProperties props, SVNProperties propChanges) {
        SVNProperties result = new SVNProperties(props);
        if (propChanges != null) {
            for(Iterator names = propChanges.nameSet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                SVNPropertyValue value = propChanges.getSVNPropertyValue(name);
                if (value == null) {
                    result.remove(name);
                } else {
                    result.put(name, value);
                }
            }
        }
        return result;
    }

    private void localDirectoryDiff(SVNDirectoryInfo info) throws SVNException {
        if (myIsCompareToBase) {
            return;
        }
        SVNAdminArea dir = retrieve(info.myPath);
        boolean anchor = !"".equals(myAdminInfo.getTargetName()) && dir == myAdminInfo.getAnchor();
        SVNEntry thisDirEntry = dir.getEntry(dir.getThisDirName(), false);
        if (SVNWCAccess.matchesChangeList(myChangeLists, thisDirEntry) && !anchor && 
                !info.myComparedEntries.contains("")) {
            // generate prop diff for dir.
            if (dir.hasPropModifications(dir.getThisDirName())) {
                SVNVersionedProperties baseProps = dir.getBaseProperties(dir.getThisDirName());
                SVNProperties propDiff = baseProps.compareTo(dir.getProperties(dir.getThisDirName())).asMap();
                getDiffCallback().propertiesChanged(info.myPath, baseProps.asMap(), propDiff, null);
            }
        }
        
        if (info.myDepth == SVNDepth.EMPTY && !anchor) {
            return;
        }
        
        Set processedFiles = null;
        if (getDiffCallback().isDiffUnversioned()) {
            processedFiles = new SVNHashSet();
        }
        for (Iterator entries = dir.entries(false); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            
            if (processedFiles != null && !dir.getThisDirName().equals(entry.getName())) {
                processedFiles.add(entry.getName());
            }
            if (anchor && !myAdminInfo.getTargetName().equals(entry.getName())) {
                continue;
            }
            if (dir.getThisDirName().equals(entry.getName())) {
                continue;
            }
            if (info.myComparedEntries.contains(entry.getName())) {
                continue;
            }
            info.myComparedEntries.add(entry.getName());
            if (entry.isFile()) {
                reportModifiedFile(info, entry);
            } else if (entry.isDirectory()) {
                if (anchor || info.myDepth.compareTo(SVNDepth.FILES) > 0 || 
                    info.myDepth == SVNDepth.UNKNOWN) {
                    SVNDepth depthBelowHere = info.myDepth;
                    if (depthBelowHere == SVNDepth.IMMEDIATES) {
                        depthBelowHere = SVNDepth.EMPTY;
                    }
                    SVNDirectoryInfo childInfo = createDirInfo(info, 
                                                               SVNPathUtil.append(info.myPath, entry.getName()), 
                                                               false,
                                                               depthBelowHere);
                    localDirectoryDiff(childInfo);
                }
            }
        }
        if (getDiffCallback().isDiffUnversioned()) {
            String relativePath = dir.getRelativePath(myAdminInfo.getAnchor());
            diffUnversioned(dir.getRoot(), dir, relativePath, anchor, processedFiles);
        }
    }

    private void diffUnversioned(File root, SVNAdminArea dir, String parentRelativePath, boolean anchor, Set processedFiles) throws SVNException {
        File[] allFiles = SVNFileListUtil.listFiles(root);
        for (int i = 0; allFiles != null && i < allFiles.length; i++) {
            File file = allFiles[i];
            if (SVNFileUtil.getAdminDirectoryName().equals(file.getName())) {
                continue;
            }
            if (processedFiles != null && processedFiles.contains(file.getName())) {
                continue;
            }
            if (anchor && !myAdminInfo.getTargetName().equals(file.getName())) {
                continue;
            } else if (dir != null) {// && SVNStatusEditor.isIgnored(, name)dir.isIgnored(file.getName())) {
                Collection globalIgnores = SVNStatusEditor.getGlobalIgnores(myWCAccess.getOptions());
                Collection ignores = SVNStatusEditor.getIgnorePatterns(dir, globalIgnores);
                
                String rootRelativePath = null;
                boolean needToComputeRelativePath = false;
                for (Iterator patterns = ignores.iterator(); patterns.hasNext();) {
                    String pattern = (String) patterns.next();
                    if (pattern.startsWith("/")) {
                        needToComputeRelativePath = true;
                        break;
                    }
                }
                if (needToComputeRelativePath) {
                    if (myWCRootPath == null) {
                        File wcRoot = SVNWCUtil.getWorkingCopyRoot(dir.getRoot(), true);
                        myWCRootPath = wcRoot.getAbsolutePath().replace(File.separatorChar, '/');
                    }
                    if (myWCRootPath != null) {
                        rootRelativePath = file.getAbsolutePath().replace(File.separatorChar, '/');
                        rootRelativePath = SVNPathUtil.getPathAsChild(myWCRootPath, rootRelativePath);
                        if (rootRelativePath != null && !rootRelativePath.startsWith("/")) {
                            rootRelativePath = "/" + rootRelativePath;
                        }
                    }
                }
                if (SVNStatusEditor.isIgnored(ignores, file, rootRelativePath)) {
                    continue;
                }
            }
            // generate patch as for added file.
            SVNFileType fileType = SVNFileType.getType(file);
            if (fileType == SVNFileType.DIRECTORY) {
                diffUnversioned(file, null, SVNPathUtil.append(parentRelativePath, file.getName()), false, null);
            } else if (fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK) {
                String mimeType1 = null;
                String mimeType2 = SVNFileUtil.detectMimeType(file, null);
                String filePath = SVNPathUtil.append(parentRelativePath, file.getName());
                getDiffCallback().fileAdded(filePath, null, file, 0, 0, mimeType1, mimeType2, null, null, null);
            }
        }
    }

    private SVNDirectoryInfo createDirInfo(SVNDirectoryInfo parent, String path, boolean added, 
                                           SVNDepth depth) {
        SVNDirectoryInfo info = new SVNDirectoryInfo();
        info.myParent = parent;
        info.myPath = path;
        info.myIsAdded = added;
        info.myDepth = depth;
        return info;
    }

    private SVNFileInfo createFileInfo(SVNDirectoryInfo parent, String path, boolean added) {
        SVNFileInfo info = new SVNFileInfo();
        info.myPath = path;
        info.myIsAdded = added;
        if (parent.myIsAdded) {
            while(parent.myIsAdded) {
                parent = parent.myParent;
            }
            info.myPath = SVNPathUtil.append(parent.myPath, "fake");
        }
        return info;
    }
    
    private File detranslateFile(SVNAdminArea dir, String name) throws SVNException {
        SVNVersionedProperties properties = dir.getProperties(name);
        String keywords = properties.getStringPropertyValue(SVNProperty.KEYWORDS);
        String eolStyle = properties.getStringPropertyValue(SVNProperty.EOL_STYLE);
        String charsetProp = properties.getStringPropertyValue(SVNProperty.CHARSET);
        ISVNOptions options = dir.getWCAccess().getOptions();
        String charset = SVNTranslator.getCharset(charsetProp, dir.getFile(name).getPath(), options);
        boolean special = properties.getPropertyValue(SVNProperty.SPECIAL) != null;
        if (charset == null && keywords == null && eolStyle == null && (!special || !SVNFileUtil.symlinksSupported())) {
            return dir.getFile(name);
        }
        byte[] eol = SVNTranslator.getEOL(eolStyle, options);
        File tmpFile = createTempFile();
        Map keywordsMap = SVNTranslator.computeKeywords(keywords, null, null, null, null, null);
        SVNTranslator.translate(dir.getFile(name), tmpFile, charset, eol, keywordsMap, special, false);
        return tmpFile;
    }
    
    private File createTempFile() throws SVNException {
        File tmpFile = null;
        try {
            return File.createTempFile("diff.", ".tmp", getTempDirectory());
        } catch (IOException e) {
            SVNFileUtil.deleteFile(tmpFile);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        return null;
    }
    
    private File getTempDirectory() throws SVNException {
        if (myTempDirectory == null) {
            myTempDirectory = getDiffCallback().createTempDirectory();
        }
        return myTempDirectory;
    }
    
    private SVNAdminArea retrieve(String path) throws SVNException {
        File dir = myAdminInfo.getAnchor().getFile(path);
        return myWCAccess.retrieve(dir);
    }
    
    private AbstractDiffCallback getDiffCallback() {
        return myDiffCallback;
    }

    private static class SVNDirectoryInfo {

        private boolean myIsAdded;
        private String myPath;
        private SVNProperties myPropertyDiff;
        private SVNDirectoryInfo myParent;
        private Set myComparedEntries = new SVNHashSet();
        private SVNDepth myDepth;
    }

    private static class SVNFileInfo {

        private boolean myIsAdded;
        private String myPath;
        private File myFile;
        private File myBaseFile;
        private SVNProperties myPropertyDiff;
    }

    private static void reversePropChanges(SVNProperties base, SVNProperties diff) {
        Collection namesList = new ArrayList(diff.nameSet());
        for (Iterator names = namesList.iterator(); names.hasNext();) {
            String name = (String) names.next();
            SVNPropertyValue newValue = diff.getSVNPropertyValue(name);
            SVNPropertyValue oldValue = base.getSVNPropertyValue(name);
            if (oldValue == null && newValue != null) {
                base.put(name, newValue);
                diff.put(name, (SVNPropertyValue) null);
            } else if (oldValue != null && newValue == null) {
                base.put(name, (SVNPropertyValue) null);
                diff.put(name, oldValue);
            } else if (oldValue != null && newValue != null) {
                base.put(name, newValue);
                diff.put(name, oldValue);
            }
        }
    }

    private static SVNProperties computePropsDiff(SVNProperties props1, SVNProperties props2) {
        SVNProperties propsDiff = new SVNProperties();
        for (Iterator names = props2.nameSet().iterator(); names.hasNext();) {
            String newPropName = (String) names.next();
            if (props1.containsName(newPropName)) {
                // changed.
                SVNPropertyValue oldValue = props2.getSVNPropertyValue(newPropName);
                SVNPropertyValue value = props1.getSVNPropertyValue(newPropName);
                if (oldValue != null && !oldValue.equals(value)) {
                    propsDiff.put(newPropName, oldValue);
                } else if (oldValue == null && value != null) {
                    propsDiff.put(newPropName, oldValue);
                }
            } else {
                // added.
                propsDiff.put(newPropName, props2.getSVNPropertyValue(newPropName));
            }
        }
        for (Iterator names = props1.nameSet().iterator(); names.hasNext();) {
            String oldPropName = (String) names.next();
            if (!props2.containsName(oldPropName)) {
                // deleted
                propsDiff.put(oldPropName, (String) null);
            }
        }
        return propsDiff;
    }

}
