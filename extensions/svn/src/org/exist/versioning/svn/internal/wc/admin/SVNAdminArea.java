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
package org.exist.versioning.svn.internal.wc.admin;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.DefaultSVNMerger;
import org.exist.versioning.svn.internal.wc.SVNAdminUtil;
import org.exist.versioning.svn.internal.wc.SVNDiffConflictChoiceStyle;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNFileType;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.SVNPropertiesManager;
import org.exist.versioning.svn.internal.wc.SVNWCProperties;
import org.exist.versioning.svn.wc.ISVNMerger;
import org.exist.versioning.svn.wc.ISVNMergerFactory;
import org.exist.versioning.svn.wc.SVNConflictChoice;
import org.exist.versioning.svn.wc.SVNMergeFileSet;
import org.exist.versioning.svn.wc.SVNMergeResult;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.exist.versioning.svn.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.ISVNCommitParameters;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class SVNAdminArea {

    protected static final String ADM_KILLME = "KILLME";
    private static volatile boolean ourIsCleanupSafe;

    protected Map myBaseProperties;
    protected Map myProperties;
    protected Map myWCProperties;
    protected Map myEntries;
    protected boolean myWasLocked;

    private ISVNCommitParameters myCommitParameters;
    private Map myRevertProperties;
    private File myDirectory;
    private SVNWCAccess myWCAccess;
    private File myAdminRoot;
    private int myWCFormatVersion;
    
    public static synchronized void setSafeCleanup(boolean safe) {
        ourIsCleanupSafe = safe;
    }

    public static synchronized boolean isSafeCleanup() {
        return ourIsCleanupSafe;
    }

    public abstract boolean isLocked() throws SVNException;

    public abstract boolean isVersioned();

    protected abstract boolean isEntryPropertyApplicable(String name);

    public abstract boolean lock(boolean stealLock) throws SVNException;

    public abstract boolean unlock() throws SVNException;

    public abstract SVNVersionedProperties getBaseProperties(String name) throws SVNException;

    public abstract SVNVersionedProperties getRevertProperties(String name) throws SVNException;

    public abstract SVNVersionedProperties getWCProperties(String name) throws SVNException;

    public abstract SVNVersionedProperties getProperties(String name) throws SVNException;

    public abstract void saveVersionedProperties(SVNLog log, boolean close) throws SVNException;

    public abstract void installProperties(String name, SVNProperties baseProps, SVNProperties workingProps, 
            SVNLog log, boolean writeBaseProps, boolean close) throws SVNException;

    public abstract void saveWCProperties(boolean close) throws SVNException;

    public abstract void saveEntries(boolean close) throws SVNException;

    public abstract String getThisDirName();

    public abstract boolean hasPropModifications(String entryName) throws SVNException;

    public abstract boolean hasProperties(String entryName) throws SVNException;

    public abstract SVNAdminArea createVersionedDirectory(File dir, String url, String rootURL, String uuid, long revNumber, boolean createMyself, SVNDepth depth) throws SVNException;

    public abstract void postCommit(String fileName, long revisionNumber, boolean implicit, boolean rerun, SVNErrorCode errorCode) throws SVNException;

    public abstract void handleKillMe() throws SVNException;

    public abstract boolean hasTreeConflict(String name) throws SVNException;

    public abstract SVNTreeConflictDescription getTreeConflict(String name) throws SVNException;

    public abstract void addTreeConflict(SVNTreeConflictDescription conflict) throws SVNException;

    public abstract SVNTreeConflictDescription deleteTreeConflict(String name) throws SVNException;

    public abstract void setFileExternalLocation(String name, SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNURL reposRootURL) throws SVNException;
    
    public abstract int getFormatVersion();

    public void updateURL(String rootURL, boolean recursive) throws SVNException {
        SVNWCAccess wcAccess = getWCAccess();
        for (Iterator ents = entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (!getThisDirName().equals(entry.getName()) && entry.isDirectory() && recursive) {
                SVNAdminArea childDir = wcAccess.retrieve(getFile(entry.getName()));
                if (childDir != null) {
                    String childURL = SVNPathUtil.append(rootURL, SVNEncodingUtil.uriEncode(entry.getName()));
                    childDir.updateURL(childURL, recursive);
                }
                continue;
            }
            entry.setURL(getThisDirName().equals(entry.getName()) ? rootURL : SVNPathUtil.append(
                    rootURL, SVNEncodingUtil.uriEncode(entry.getName())));
        }
        saveEntries(false);
    }

    public boolean hasTextModifications(String name, boolean forceComparision) throws SVNException {
        return hasTextModifications(name, forceComparision, true, false);
    }

    public boolean hasTextModifications(String name, boolean forceComparison, boolean compareTextBase, boolean compareChecksum) throws SVNException {
        File textFile = getFile(name);
        SVNFileType fileType = SVNFileType.getType(textFile);
        if (!(fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK)) {
            return false;
        }

        SVNEntry entry = null;
        if (!forceComparison) {
            boolean compare = false;
            try {
                entry = getEntry(name, false);
            } catch (SVNException svne) {
                compare = true;
            }

            if (!compare && entry == null) {
                compare = true;
            }

            if (!compare && isEntryPropertyApplicable(SVNProperty.WORKING_SIZE)) {
                if (entry.getWorkingSize() != SVNProperty.WORKING_SIZE_UNKNOWN &&
                    textFile.length() != entry.getWorkingSize()) {
                    compare = true;
                }
            }

            if (!compare) {
                String textTime = entry.getTextTime();
                if (textTime == null) {
                    compare = true;
                } else {
                    long textTimeAsLong = SVNFileUtil.roundTimeStamp(SVNDate.parseDateAsMilliseconds(textTime));
                    long tstamp = SVNFileUtil.roundTimeStamp(textFile.lastModified());
                    if (textTimeAsLong != tstamp ) {
                        compare = true;
                    }
                }
            }

            if (!compare) {
                return false;
            }
        }

        File baseFile = getBaseFile(name, false);
        if (!baseFile.isFile()) {
            return true;
        }

        boolean differs = compareAndVerify(textFile, baseFile, compareTextBase, compareChecksum);
        if (!differs && isLocked()) {
            Map attributes = new SVNHashMap();
            attributes.put(SVNProperty.WORKING_SIZE, Long.toString(textFile.length()));
            attributes.put(SVNProperty.TEXT_TIME, SVNDate.formatDate(new Date(textFile.lastModified())));
            modifyEntry(name, attributes, true, false);
        }
        return differs;
    }

    public boolean hasVersionedFileTextChanges(File file, File baseFile, boolean compareTextBase) throws SVNException {
        return compareAndVerify(file, baseFile, compareTextBase, false);
    }

    public String getRelativePath(SVNAdminArea anchor) {
        String absoluteAnchor = anchor.getRoot().getAbsolutePath();
        String ownAbsolutePath = getRoot().getAbsolutePath();
        String relativePath = ownAbsolutePath.substring(absoluteAnchor.length());

        relativePath = relativePath.replace(File.separatorChar, '/');
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        if (relativePath.endsWith("/")) {
            relativePath = relativePath.substring(0, relativePath.length() - 1);
        }
        return relativePath;
    }

    public boolean tweakEntry(String name, String newURL, String reposRoot, long newRevision, boolean remove) throws SVNException {
        boolean rewrite = false;
        SVNEntry entry = getEntry(name, true);
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "No such entry: ''{0}''", name);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (newURL != null && (entry.getURL() == null || !newURL.equals(entry.getURL()))) {
            rewrite = true;
            entry.setURL(newURL);
        }

        if (reposRoot != null && (entry.getRepositoryRootURL() == null || !reposRoot.equals(entry.getRepositoryRoot()))
                && entry.getURL() != null && SVNPathUtil.isAncestor(reposRoot, entry.getURL())) {
            boolean setReposRoot = true;
            if (getThisDirName().equals(entry.getName())) {
                for (Iterator entries = entries(true); entries.hasNext();) {
                    SVNEntry childEntry = (SVNEntry) entries.next();
                    if (childEntry.getRepositoryRoot() == null && childEntry.getURL() != null &&
                            !SVNPathUtil.isAncestor(reposRoot, entry.getURL())) {
                        setReposRoot = false;
                        break;
                    }
                }
            }
            if (setReposRoot) {
                rewrite = true;
                entry.setRepositoryRoot(reposRoot);
            }
        }

        if (newRevision >= 0 &&
                !entry.isScheduledForAddition() &&
                !entry.isScheduledForReplacement() &&
                !entry.isCopied() &&
                entry.getRevision() != newRevision) {
            rewrite = true;
            entry.setRevision(newRevision);
        }

        if (remove && (entry.isDeleted() || (entry.isAbsent() && entry.getRevision() != newRevision))) {
            deleteEntry(name);
            rewrite = true;
        }
        return rewrite;
    }

    public boolean isKillMe() {
        return getAdminFile(ADM_KILLME).isFile();
    }

    public boolean markResolved(String name, boolean text, boolean props, SVNConflictChoice conflictChoice) throws SVNException {
        SVNEntry entry = getEntry(name, true);
        if (entry == null) {
            return false;
        }

        String autoResolveSource = null;
        File autoResolveSourceFile = null;
        boolean removeSource = false;
        if (conflictChoice == SVNConflictChoice.BASE) {
            autoResolveSource = entry.getConflictOld();
        } else if (conflictChoice == SVNConflictChoice.MINE_FULL) {
            autoResolveSource = entry.getConflictWorking();
        } else if (conflictChoice == SVNConflictChoice.THEIRS_FULL) {
            autoResolveSource = entry.getConflictNew();
        } else if (conflictChoice == SVNConflictChoice.THEIRS_CONFLICT || conflictChoice == SVNConflictChoice.MINE_CONFLICT) {
            if (entry.getConflictOld() != null && entry.getConflictNew() != null && entry.getConflictWorking() != null) {
                String conflictOld = entry.getConflictOld();
                String conflictNew = entry.getConflictNew();
                String conflictWorking = entry.getConflictWorking();
                
                ISVNMergerFactory factory = myWCAccess.getOptions().getMergerFactory();
                
                File conflictOldFile = SVNPathUtil.isAbsolute(conflictOld) ? new Resource(conflictOld) : getFile(conflictOld);
                File conflictNewFile = SVNPathUtil.isAbsolute(conflictNew) ? new Resource(conflictNew) : getFile(conflictNew);
                File conflictWorkingFile = SVNPathUtil.isAbsolute(conflictWorking) ? new Resource(conflictWorking) : getFile(conflictWorking);
                    
                byte[] conflictStart = ("<<<<<<< " + conflictWorking).getBytes();
                byte[] conflictEnd = (">>>>>>> " + conflictNew).getBytes();
                byte[] separator = ("=======").getBytes();

                ISVNMerger merger = factory.createMerger(conflictStart, separator, conflictEnd);
                SVNDiffConflictChoiceStyle style = conflictChoice == SVNConflictChoice.THEIRS_CONFLICT ? SVNDiffConflictChoiceStyle.CHOOSE_LATEST : 
                    SVNDiffConflictChoiceStyle.CHOOSE_MODIFIED;
                if (merger instanceof DefaultSVNMerger) {
                    DefaultSVNMerger defaultMerger = (DefaultSVNMerger) merger;
                    defaultMerger.setDiffConflictStyle(style);
                }
                
                autoResolveSourceFile = SVNAdminUtil.createTmpFile(this);

                SVNMergeFileSet mergeFileSet = new SVNMergeFileSet(this, null, conflictOldFile, conflictWorkingFile, name, conflictNewFile, 
                        autoResolveSourceFile, null, null);

                String localLabel = ".working";
                String baseLabel = ".old";
                String latestLabel = ".new";
                
                mergeFileSet.setMergeLabels(baseLabel, localLabel, latestLabel);
                merger.mergeText(mergeFileSet, false, null);
                mergeFileSet.dispose();
                removeSource = true;
            }
        } else if (conflictChoice != SVNConflictChoice.MERGED) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, "Invalid 'conflict_result' argument");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        
        if (autoResolveSource != null) {
            autoResolveSourceFile = getFile(autoResolveSource);
        }
        
        if (autoResolveSourceFile != null) {
            SVNFileUtil.copyFile(autoResolveSourceFile, getFile(name), false);
            if (removeSource) {
                SVNFileUtil.deleteFile(autoResolveSourceFile);
            }
        }

        if (!text && !props) {
            return false;
        }

        boolean filesDeleted = false;
        boolean updateEntry = false;
        if (text && entry.getConflictOld() != null) {
            File file = getFile(entry.getConflictOld());
            filesDeleted |= file.isFile();
            updateEntry = true;
            SVNFileUtil.deleteFile(file);
        }
        if (text && entry.getConflictNew() != null) {
            File file = getFile(entry.getConflictNew());
            filesDeleted |= file.isFile();
            updateEntry = true;
            SVNFileUtil.deleteFile(file);
        }
        if (text && entry.getConflictWorking() != null) {
            File file = getFile(entry.getConflictWorking());
            filesDeleted |= file.isFile();
            updateEntry = true;
            SVNFileUtil.deleteFile(file);
        }
        if (props && entry.getPropRejectFile() != null) {
            File file = getFile(entry.getPropRejectFile());
            filesDeleted |= file.isFile();
            updateEntry = true;
            SVNFileUtil.deleteFile(file);
        }
        if (updateEntry) {
            if (text) {
                entry.setConflictOld(null);
                entry.setConflictNew(null);
                entry.setConflictWorking(null);
            }
            if (props) {
                entry.setPropRejectFile(null);
            }
            saveEntries(false);
        }
        return filesDeleted;
    }

    public void restoreFile(String name) throws SVNException {
        SVNVersionedProperties props = getProperties(name);
        SVNEntry entry = getEntry(name, true);
        boolean special = props.getPropertyValue(SVNProperty.SPECIAL) != null;

        File src = getBaseFile(name, false);
        File dst = getFile(name);
        SVNTranslator.translate(this, name, SVNFileUtil.getBasePath(src), SVNFileUtil.getBasePath(dst), true);

        boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;
        boolean needsLock = props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null;
        if (needsLock) {
            SVNFileUtil.setReadonly(dst, entry.getLockToken() == null);
        }
        if (executable) {
            SVNFileUtil.setExecutable(dst, true);
        }

        markResolved(name, true, false, SVNConflictChoice.MERGED);

        long tstamp;
        if (myWCAccess.getOptions().isUseCommitTimes() && !special) {
            entry.setTextTime(entry.getCommittedDate());
            tstamp = SVNDate.parseDate(entry.getCommittedDate()).getTime();
            dst.setLastModified(tstamp);
        } else {
            tstamp = System.currentTimeMillis();
            dst.setLastModified(tstamp);
            entry.setTextTime(SVNDate.formatDate(new Date(tstamp)));
        }
        saveEntries(false);
    }

    public SVNStatusType mergeProperties(String name, SVNProperties serverBaseProps, SVNProperties propDiff, 
    		String localLabel, String latestLabel, boolean baseMerge, boolean dryRun, SVNLog log) throws SVNException {
        SVNVersionedProperties working = getProperties(name);
        SVNVersionedProperties base = getBaseProperties(name);
        return mergeProperties(name, serverBaseProps, base.asMap(), working.asMap(), propDiff, localLabel, 
                latestLabel, baseMerge, dryRun, log);
    }

    public SVNStatusType mergeProperties(String name, SVNProperties serverBaseProps, SVNProperties baseProps, 
            SVNProperties workingProps, SVNProperties propDiff, String localLabel, String latestLabel, 
            boolean baseMerge, boolean dryRun, SVNLog log) throws SVNException {
        localLabel = localLabel == null ? "(modified)" : localLabel;
        latestLabel = latestLabel == null ? "(latest)" : latestLabel;

        byte[] conflictStart = ("<<<<<<< " + localLabel).getBytes();
        byte[] conflictEnd = (">>>>>>> " + latestLabel).getBytes();
        byte[] separator = ("=======").getBytes();
        
        ISVNMergerFactory factory = myWCAccess.getOptions().getMergerFactory();
        ISVNMerger merger = factory.createMerger(conflictStart, separator, conflictEnd);
        propDiff = propDiff == null ? new SVNProperties() : propDiff;

        SVNMergeResult result = merger.mergeProperties(name, workingProps, baseProps, serverBaseProps, 
                propDiff, this, log, baseMerge, dryRun);

        return result.getMergeStatus();
    }

    public SVNStatusType mergeText(String localPath, File base, File latest, File copyFromText, String localLabel,
            String baseLabel, String latestLabel, SVNProperties propChanges, boolean dryRun, SVNDiffOptions options, 
            SVNLog log) throws SVNException {
        SVNEntry entry = getEntry(localPath, false);
        if (entry == null && copyFromText == null) {
            return SVNStatusType.MISSING;
        }

        boolean saveLog = log == null;
        log = log == null ? getLog() : log;

        SVNVersionedProperties props = getProperties(localPath);
        String mimeType = null;
        if (propChanges != null && propChanges.containsName(SVNProperty.MIME_TYPE)) {
            mimeType = propChanges.getStringValue(SVNProperty.MIME_TYPE);
        } else if (copyFromText == null) {
            mimeType = props.getStringPropertyValue(SVNProperty.MIME_TYPE);
        }

        localLabel = localLabel == null ? ".working" : localLabel;
        baseLabel = baseLabel == null ? ".old" : baseLabel;
        latestLabel = latestLabel == null ? ".new" : latestLabel;

        byte[] conflictStart = ("<<<<<<< " + localLabel).getBytes();
        byte[] conflictEnd = (">>>>>>> " + latestLabel).getBytes();
        byte[] separator = ("=======").getBytes();
        ISVNMergerFactory factory = myWCAccess.getOptions().getMergerFactory();
        ISVNMerger merger = factory.createMerger(conflictStart, separator, conflictEnd);

        String workingText = localPath;
        if (copyFromText != null) {
            String copyFromTextPath = copyFromText.getAbsolutePath().replace(File.separatorChar, '/');
            String thisPath = getRoot().getAbsolutePath().replace(File.separatorChar, '/');
            workingText = copyFromTextPath.substring(thisPath.length());
            if (workingText.startsWith("/")) {
                workingText = workingText.substring(1);
            }
        }
        
        File tmpTarget = SVNTranslator.detranslateWorkingCopy(this, workingText, propChanges, false);
        base = SVNTranslator.maybeUpdateTargetEOLs(this, base, propChanges);
        File resultFile = SVNAdminUtil.createTmpFile(this);

        SVNMergeFileSet mergeFileSet = new SVNMergeFileSet(this, log, base, tmpTarget, localPath, latest, 
                resultFile, copyFromText, mimeType);

        mergeFileSet.setMergeLabels(baseLabel, localLabel, latestLabel);

	    SVNMergeResult mergeResult;
	    try {
	        mergeResult = merger.mergeText(mergeFileSet, dryRun, options);
	    }
	    finally {
		    if (dryRun) {
		        SVNFileUtil.deleteFile(resultFile);
		    }
	    }
        mergeFileSet.dispose();

        if (saveLog) {
            log.save();
        }

        return mergeResult.getMergeStatus();
    }

    public InputStream getBaseFileForReading(String name, boolean tmp) throws SVNException {
        String path = tmp ? "tmp/" : "";
        path += "text-base/" + name + ".svn-base";
        File baseFile = getAdminFile(path);
        return SVNFileUtil.openFileForReading(baseFile, SVNLogType.WC);
    }

    public OutputStream getBaseFileForWriting(String name) throws SVNException {
        final String fileName = name;
        final File tmpFile = getBaseFile(name, true);
        try {
            final OutputStream os = SVNFileUtil.openFileForWriting(tmpFile);
            return new OutputStream() {
                private String myName = fileName;
                private File myTmpFile = tmpFile;

                public void write(int b) throws IOException {
                    os.write(b);
                }

                public void write(byte[] b) throws IOException {
                    os.write(b);
                }

                public void write(byte[] b, int off, int len) throws IOException {
                    os.write(b, off, len);
                }

                public void close() throws IOException {
                    os.close();
                    File baseFile = getBaseFile(myName, false);
                    try {
                        SVNFileUtil.rename(myTmpFile, baseFile);
                    } catch (SVNException e) {
                        throw new IOException(e.getMessage());
                    }
                    SVNFileUtil.setReadonly(baseFile, true);
                }
            };
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Your .svn/tmp directory may be missing or corrupt; run 'svn cleanup' and try again");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return null;
    }

    public String getPropertyTime(String name) {
        String path = getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
        File file = getAdminFile(path);
        return SVNDate.formatDate(new Date(file.lastModified()));
    }

    public SVNLog getLog() {
        int index = 0;
        Resource logFile = null;
        Resource tmpFile = null;
        while (true) {
            logFile = getAdminFile("log" + (index == 0 ? "" : "." + index));
            if (logFile.exists()) {
                index++;
                continue;
            }
            tmpFile = getAdminFile("tmp/log" + (index == 0 ? "" : "." + index));
            return new SVNLogImpl(logFile, tmpFile, this);
        }
    }

    public void runLogs() throws SVNException {
        runLogs(false);
    }

    public void runLogs(boolean rerun) throws SVNException {
        SVNLogRunner runner = new SVNLogRunner(rerun);
        int index = 0;
        SVNLog log = null;
        runner.logStarted(this);
        try {
        	Resource logFile = null;
            while (true) {
                if (getWCAccess() != null) {
                    getWCAccess().checkCancelled();
                }
                logFile = getAdminFile("log" + (index == 0 ? "" : "." + index));
                log = new SVNLogImpl(logFile, null, this);
                if (log.exists()) {
                    log.run(runner);
                    markLogProcessed(logFile);
                    index++;
                    continue;
                }
                break;
            }
        } catch (Throwable e) {
            runner.logFailed(this);
            if (e instanceof SVNException) {
                throw (SVNException) e;
            } else if (e instanceof Error) {
                throw (Error) e;
            }
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.UNKNOWN), e);
        }
        runner.logCompleted(this);
        // delete all logs, there shoudn't be left unprocessed.
        File[] logsFiles = getAdminDirectory().listFiles();
        if (logsFiles != null) {
            for (int i = 0; i < logsFiles.length; i++) {
                if (logsFiles[i].getName().startsWith("log") && logsFiles[i].isFile()) {
                    SVNFileUtil.deleteFile(logsFiles[i]);
                }
            }
        }
    }

    public void removeFromRevisionControl(String name, boolean deleteWorkingFiles, boolean reportInstantError) throws SVNException {
        getWCAccess().checkCancelled();
        boolean isFile = !getThisDirName().equals(name);
        boolean leftSomething = false;
        SVNEntry entry = getVersionedEntry(name, true);
        if (isFile) {
            File path = getFile(name);
            boolean wcSpecial = getProperties(name).getPropertyValue(SVNProperty.SPECIAL) != null;
            boolean localSpecial = !SVNFileUtil.symlinksSupported() ? false : SVNFileType.getType(path) == SVNFileType.SYMLINK;
            boolean textModified = false;
            if (wcSpecial || !localSpecial) {
                textModified = hasTextModifications(name, false);
                if (reportInstantError && textModified) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LEFT_LOCAL_MOD, "File ''{0}'' has local modifications", path);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
            SVNPropertiesManager.deleteWCProperties(this, name, false);
            deleteEntry(name);
            saveEntries(false);

            SVNFileUtil.deleteFile(getFile(SVNAdminUtil.getTextBasePath(name, false)));
            SVNFileUtil.deleteFile(getFile(SVNAdminUtil.getPropBasePath(name, entry.getKind(), false)));
            SVNFileUtil.deleteFile(getFile(SVNAdminUtil.getPropPath(name, entry.getKind(), false)));
            if (deleteWorkingFiles) {
                if (textModified || (!wcSpecial && localSpecial)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LEFT_LOCAL_MOD);
                    SVNErrorManager.error(err, SVNLogType.WC);
                } else if (myCommitParameters == null || myCommitParameters.onFileDeletion(path)) {
                    SVNFileUtil.deleteFile(path);
                }
            }
        } else {
            SVNEntry dirEntry = getEntry(getThisDirName(), false);
            dirEntry.setIncomplete(true);
            saveEntries(false);
            SVNPropertiesManager.deleteWCProperties(this, getThisDirName(), false);
            for(Iterator entries = entries(false); entries.hasNext();) {
                SVNEntry nextEntry = (SVNEntry) entries.next();
                String entryName = getThisDirName().equals(nextEntry.getName()) ? null : nextEntry.getName();
                if (nextEntry.isFile()) {
                    try {
                        removeFromRevisionControl(entryName, deleteWorkingFiles, reportInstantError);
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LEFT_LOCAL_MOD) {
                            if (reportInstantError) {
                                throw e;
                            }
                            leftSomething = true;
                        } else {
                            throw e;
                        }
                    }
                } else if (entryName != null && nextEntry.isDirectory()) {
                    File entryPath = getFile(entryName);
                    if (getWCAccess().isMissing(entryPath) || nextEntry.getDepth() == SVNDepth.EXCLUDE) {
                        deleteEntry(entryName);
                    } else {
                        try {
                            SVNAdminArea entryArea = getWCAccess().retrieve(entryPath);
                            entryArea.removeFromRevisionControl(getThisDirName(), deleteWorkingFiles, reportInstantError);
                        } catch (SVNException e) {
                            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LEFT_LOCAL_MOD) {
                                if (reportInstantError) {
                                    throw e;
                                }
                                leftSomething = true;
                            } else {
                                throw e;
                            }
                        }
                    }
                }
            }
            if (!getWCAccess().isWCRoot(getRoot())) {
                SVNEntry dirEntryInParent = getWCAccess().retrieve(getRoot().getParentFile()).getEntry(getRoot().getName(), false);
                if (dirEntryInParent.getDepth() != SVNDepth.EXCLUDE) {
                    getWCAccess().retrieve(getRoot().getParentFile()).deleteEntry(getRoot().getName());
                    getWCAccess().retrieve(getRoot().getParentFile()).saveEntries(false);
                }
            }
            destroyAdminArea();
            if (deleteWorkingFiles && !leftSomething) {
                if ((myCommitParameters == null || myCommitParameters.onDirectoryDeletion(getRoot()))
                        && !getRoot().delete()) {
                    // shouldn't throw exception when directory was intentionally left non-empty.
                    leftSomething = true;
                }
            }

        }
        if (leftSomething && myCommitParameters == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LEFT_LOCAL_MOD);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }

    public void extendLockToTree() throws SVNException {
        final boolean writeLock = isLocked();
        ISVNEntryHandler entryHandler = new ISVNEntryHandler() {
            public void handleEntry(File path, SVNEntry entry) throws SVNException {
                if (entry.isDirectory() && !entry.getName().equals(getThisDirName())) {
                    try {
                        SVNAdminArea area = getWCAccess().probeTry(path, isLocked(), SVNWCAccess.INFINITE_DEPTH);
                        if (writeLock && area != null && !area.isLocked()) {
                            area.lock(false);
                        }
                    } catch (SVNException svne) {
                        if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.WC_LOCKED) {
                            throw svne;
                        }
                    }
                }
            }
          
            public void handleError(File path, SVNErrorMessage error) throws SVNException {
                SVNErrorManager.error(error, SVNLogType.WC);
            }
        };
        
        getWCAccess().walkEntries(getRoot(), entryHandler, false, SVNDepth.INFINITY);
    }
    
    public void foldScheduling(String name, Map attributes, boolean force) throws SVNException {
        if (!attributes.containsKey(SVNProperty.SCHEDULE) || force) {
            return;
        }
        String schedule = (String) attributes.get(SVNProperty.SCHEDULE);
        schedule = "".equals(schedule) ? null : schedule;

        SVNEntry entry = getEntry(name, true);
        if (entry == null) {
            if (SVNProperty.SCHEDULE_ADD.equals(schedule)) {
                return;
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, "''{0}'' is not under version control", name);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        SVNEntry thisDirEntry = getEntry(getThisDirName(), true);
        if (!getThisDirName().equals(entry.getName()) && thisDirEntry.isScheduledForDeletion()) {
            if (SVNProperty.SCHEDULE_ADD.equals(schedule)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, "Can''t add ''{0}'' to deleted directory; try undeleting its parent directory first", name);
                SVNErrorManager.error(err, SVNLogType.WC);
            } else if (SVNProperty.SCHEDULE_REPLACE.equals(schedule)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, "Can''t replace ''{0}'' in deleted directory; try undeleting its parent directory first", name);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }

        if (entry.isAbsent() && SVNProperty.SCHEDULE_ADD.equals(schedule)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, "''{0}'' is marked as absent, so it cannot be scheduled for addition", name);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (SVNProperty.SCHEDULE_ADD.equals(entry.getSchedule())) {
            if (SVNProperty.SCHEDULE_DELETE.equals(schedule)) {
                if (!entry.isDeleted()) {
                    deleteEntry(name);
                } else {
                    attributes.put(SVNProperty.SCHEDULE, null);
                }
            } else {
                attributes.remove(SVNProperty.SCHEDULE);
            }
        } else if (SVNProperty.SCHEDULE_DELETE.equals(entry.getSchedule())) {
            if (SVNProperty.SCHEDULE_DELETE.equals(schedule)) {
                attributes.remove(SVNProperty.SCHEDULE);
            } else if (SVNProperty.SCHEDULE_ADD.equals(schedule)) {
                attributes.put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_REPLACE);
            }
        } else if (SVNProperty.SCHEDULE_REPLACE.equals(entry.getSchedule())) {
            if (SVNProperty.SCHEDULE_DELETE.equals(schedule)) {
                attributes.put(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_DELETE);
            } else if (SVNProperty.SCHEDULE_ADD.equals(schedule) || SVNProperty.SCHEDULE_REPLACE.equals(schedule)) {
                attributes.remove(SVNProperty.SCHEDULE);
            }
        } else {
            if (SVNProperty.SCHEDULE_ADD.equals(schedule) && !entry.isDeleted()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_SCHEDULE_CONFLICT, "Entry ''{0}'' is already under version control", name);
                SVNErrorManager.error(err, SVNLogType.WC);
            } else if (schedule == null) {
                attributes.remove(SVNProperty.SCHEDULE);
            }
        }
    }

    public SVNEntry modifyEntry(String name, Map attributes, boolean save, boolean force) throws SVNException {
        if (name == null) {
            name = getThisDirName();
        }

        boolean deleted = false;
        if (attributes.containsKey(SVNProperty.SCHEDULE)) {
            SVNEntry entryBefore = getEntry(name, true);
            foldScheduling(name, attributes, force);
            SVNEntry entryAfter = getEntry(name, true);
            if (entryBefore != null && entryAfter == null) {
                deleted = true;
            }
        }

        SVNEntry entry = null;
        if (!deleted) {
            entry = getEntry(name, true);
            if (entry == null) {
                entry = addEntry(name);
            }

            Map entryAttrs = entry.asMap();
            for (Iterator atts = attributes.keySet().iterator(); atts.hasNext();) {
                String attName = (String) atts.next();
                if (!isEntryPropertyApplicable(attName)) {
                    atts.remove();
                    continue;                                        
                }
                
                Object value = attributes.get(attName);
                if (value instanceof String) {
                    String strValue = (String) value;
                    if (SVNProperty.CACHABLE_PROPS.equals(attName) || SVNProperty.PRESENT_PROPS.equals(attName)) {
                        String[] propsArray = SVNAdminArea.fromString(strValue, " ");
                        entryAttrs.put(attName, propsArray);
                        continue;
                    }
                }

                if (value != null) {
                    entryAttrs.put(attName, value);
                } else {
                    entryAttrs.remove(attName);
                }
            }

            if (!entry.isDirectory()) {
                SVNEntry rootEntry = getEntry(getThisDirName(), true);
                if (rootEntry != null) {
                    if (!SVNRevision.isValidRevisionNumber(entry.getRevision())) {
                        entry.setRevision(rootEntry.getRevision());
                    }
                    if (entry.getURL() == null) {
                        entry.setURL(SVNPathUtil.append(rootEntry.getURL(), SVNEncodingUtil.uriEncode(name)));
                    }
                    if (entry.getRepositoryRoot() == null) {
                        entry.setRepositoryRoot(rootEntry.getRepositoryRoot());
                    }
                    if (entry.getUUID() == null && !entry.isScheduledForAddition() && !entry.isScheduledForReplacement()) {
                        entry.setUUID(rootEntry.getUUID());
                    }
                    if (isEntryPropertyApplicable(SVNProperty.CACHABLE_PROPS)) {
                        if (entry.getCachableProperties() == null) {
                            entry.setCachableProperties(rootEntry.getCachableProperties());
                        }
                    }
                }
            }

            if (attributes.containsKey(SVNProperty.SCHEDULE)) {
                if (entry.isScheduledForDeletion()) {
                    entry.setCopied(false);
                    entry.setCopyFromRevision(-1);
                    entry.setCopyFromURL(null);
                } else {
                    entry.setKeepLocal(false);
                }
            }
        }

        if (save) {
            saveEntries(false);
        }
        return entry;
    }

    public void deleteEntry(String name) throws SVNException {
        Map entries = loadEntries();
        if (entries != null) {
            entries.remove(name);
        }
    }

    public SVNEntry getEntry(String name, boolean hidden) throws SVNException {
        Map entries = loadEntries();
        if (entries != null && entries.containsKey(name)) {
            SVNEntry entry = (SVNEntry)entries.get(name);
            if (!hidden && entry.isHidden()) {
                return null;
            }
            return entry;
        }
        return null;
    }

    public SVNEntry getVersionedEntry(String name, boolean hidden) throws SVNException {
        SVNEntry entry = getEntry(name, hidden);
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", getFile(name));
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return entry;
    }

    public SVNEntry addEntry(String name) throws SVNException {
        Map entries = loadEntries();
        if (entries == null) {
            myEntries = new SVNHashMap();
            entries = myEntries;
        }

        SVNEntry entry = entries.containsKey(name) ? (SVNEntry) entries.get(name) : new SVNEntry(new SVNHashMap(), this, name);
        entries.put(name, entry);
        return entry;
    }

    public Iterator entries(boolean hidden) throws SVNException {
        Map entries = loadEntries();
        if (entries == null) {
            return Collections.EMPTY_LIST.iterator();
        }
        List copy = new ArrayList(entries.values());
        if (!hidden) {
            for (Iterator iterator = copy.iterator(); iterator.hasNext();) {
                SVNEntry entry = (SVNEntry) iterator.next();
                if (entry.isHidden()) {
                    iterator.remove();
                }
            }
        }
        return copy.iterator();
    }

    public Map getEntries() throws SVNException {
        return loadEntries();
    }

    public void cleanup() throws SVNException {
        getWCAccess().checkCancelled();
        for(Iterator entries = entries(false); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (entry.getKind() == SVNNodeKind.DIR && !getThisDirName().equals(entry.getName())) {
                File childDir = getFile(entry.getName());
                if(childDir.isDirectory()) {
                    try {
                        SVNAdminArea child = getWCAccess().open(childDir, true, true, 0);
                        child.cleanup();
                    } catch (SVNException e) {
                        if (e instanceof SVNCancelException) {
                            throw e;
                        }
                        if (isSafeCleanup()) {
                            continue;
                        }
                        throw e;
                    }
                }
            } else {
                hasPropModifications(entry.getName());
                if (entry.getKind() == SVNNodeKind.FILE) {
                    hasTextModifications(entry.getName(), false);
                }
            }
        }
        if (isKillMe()) {
            removeFromRevisionControl(getThisDirName(), true, false);
        } else {
            runLogs(true);
        }
        SVNFileUtil.deleteAll(getAdminFile("tmp"), false);
    }


    public boolean hasTextConflict(String name) throws SVNException {
        SVNEntry entry = getEntry(name, false);
        if (entry == null || entry.getKind() != SVNNodeKind.FILE) {
            return false;
        }
        boolean conflicted = false;
        if (entry.getConflictNew() != null) {
            conflicted = SVNFileType.getType(getFile(entry.getConflictNew())) == SVNFileType.FILE;
        }
        if (!conflicted && entry.getConflictWorking() != null) {
            conflicted = SVNFileType.getType(getFile(entry.getConflictWorking())) == SVNFileType.FILE;
        }
        if (!conflicted && entry.getConflictOld() != null) {
            conflicted = SVNFileType.getType(getFile(entry.getConflictOld())) == SVNFileType.FILE;
        }
        return conflicted;
    }

    public boolean hasPropConflict(String name) throws SVNException {
        SVNEntry entry = getEntry(name, false);
        if (entry != null && entry.getPropRejectFile() != null) {
            return SVNFileType.getType(getFile(entry.getPropRejectFile())) == SVNFileType.FILE;
        }
        return false;
    }

    public File getRoot() {
        return myDirectory;
    }

    public File getAdminTempDirectory() {
        return getAdminFile("tmp");
    }

    public File getAdminDirectory() {
        return myAdminRoot;
    }

    public Resource getAdminFile(String name) {
        return new Resource(getAdminDirectory(), name);
    }

    public File getFile(String name) {
        if (name == null) {
            return null;
        }
        return new Resource(getRoot(), name);
    }

    public SVNWCAccess getWCAccess() {
        return myWCAccess;
    }

    public void setWCAccess(SVNWCAccess wcAccess) {
        myWCAccess = wcAccess;
    }

    public void closeVersionedProperties() {
        myProperties = null;
        myBaseProperties = null;
    }

    public void closeWCProperties() {
        myWCProperties = null;
    }

    public void closeEntries() {
        myEntries = null;
    }

    public File getBaseFile(String name, boolean tmp) {
        String path = tmp ? "tmp/" : "";
        path += "text-base/" + name + ".svn-base";
        return getAdminFile(path);
    }

    public int getWorkingCopyFormatVersion() {
        return myWCFormatVersion;
    }
    
    public void setWorkingCopyFormatVersion(int wcFormatVersion) {
        myWCFormatVersion = wcFormatVersion;
    }
    
    protected abstract void writeEntries(Writer writer) throws IOException, SVNException;

    protected abstract Map fetchEntries() throws SVNException;

    protected abstract boolean readExtraOptions(BufferedReader reader, Map entryAttrs) throws SVNException, IOException;

    protected abstract int writeExtraOptions(Writer writer, String entryName, Map entryAttrs, int emptyFields) throws SVNException, IOException;

    protected SVNAdminArea(File dir){
        myDirectory = dir;
        myAdminRoot = new Resource(dir, SVNFileUtil.getAdminDirectoryName());
    }

    protected File getBasePropertiesFile(String name, boolean tmp) {
        String path = !tmp ? "" : "tmp/";
        path += getThisDirName().equals(name) ? "dir-prop-base" : "prop-base/" + name + ".svn-base";
        File propertiesFile = getAdminFile(path);
        return propertiesFile;
    }

    protected File getRevertPropertiesFile(String name, boolean tmp) {
        String path = !tmp ? "" : "tmp/";
        path += getThisDirName().equals(name) ? "dir-prop-revert" : "prop-base/" + name + ".svn-revert";
        File propertiesFile = getAdminFile(path);
        return propertiesFile;
    }

    public File getPropertiesFile(String name, boolean tmp) {
        String path = !tmp ? "" : "tmp/";
        path += getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
        File propertiesFile = getAdminFile(path);
        return propertiesFile;
    }

    protected Map loadEntries() throws SVNException {
        if (myEntries != null) {
            return myEntries;
        }
        myEntries = fetchEntries();
        if (myEntries != null) {
            resolveDefaults(myEntries);
        }
        return myEntries;
    }

    protected Map getBasePropertiesStorage(boolean create) {
        if (myBaseProperties == null && create) {
            myBaseProperties = new SVNHashMap();
        }
        return myBaseProperties;
    }

    protected Map getRevertPropertiesStorage(boolean create) {
        if (myRevertProperties == null && create) {
            myRevertProperties = new SVNHashMap();
        }
        return myRevertProperties;
    }

    protected Map getPropertiesStorage(boolean create) {
        if (myProperties == null && create) {
            myProperties = new SVNHashMap();
        }
        return myProperties;
    }

    protected Map getWCPropertiesStorage(boolean create) {
        if (myWCProperties == null && create) {
            myWCProperties = new SVNHashMap();
        }
        return myWCProperties;
    }

    public static String asString(String[] array, String delimiter) {
        String str = null;
        if (array != null) {
            str = "";
            for (int i = 0; i < array.length; i++) {
                str += array[i];
                if (i < array.length - 1) {
                    str += delimiter;
                }
            }
        }
        return str;
    }

    public static String[] fromString(String str, String delimiter) {
        if (str == null) {
            return new String[0];
        }
        LinkedList list = new LinkedList();
        int startInd = 0;
        int ind = -1;
        while ((ind = str.indexOf(delimiter, startInd)) != -1) {
            list.add(str.substring(startInd, ind));
            startInd = ind;
            while (startInd < str.length() && str.charAt(startInd) == ' '){
                startInd++;
            }
        }
        if (startInd < str.length()) {
            list.add(str.substring(startInd));
        }
        return (String[])list.toArray(new String[list.size()]);
    }

    public void commit(String target, SVNCommitInfo info, SVNProperties wcPropChanges,
            boolean removeLock, boolean recursive, boolean removeChangelist,
            Collection explicitCommitPaths, ISVNCommitParameters params) throws SVNException {
        SVNAdminArea anchor = getWCAccess().retrieve(getWCAccess().getAnchor());
        String path = getRelativePath(anchor);
        path = getThisDirName().equals(target) ? path : SVNPathUtil.append(path, target);
        if (!explicitCommitPaths.contains(path)) {
            // if this item is explicitly copied -> skip it.
            SVNEntry entry = getEntry(target, true);
            if (entry != null && entry.getCopyFromURL() != null) {
                return;
            }
        }

        SVNLog log = getLog();
        String checksum = null;
        if (!getThisDirName().equals(target)) {
            log.logRemoveRevertFile(target, this, true);
            log.logRemoveRevertFile(target, this, false);

            File baseFile = getBaseFile(target, true);
            SVNFileType baseType = SVNFileType.getType(baseFile);
            if (baseType == SVNFileType.NONE) {
                baseFile = getBaseFile(target, false);
                baseType = SVNFileType.getType(baseFile);
            }
            if (baseType == SVNFileType.FILE) {
                checksum = SVNFileUtil.computeChecksum(baseFile);
            }
            recursive = false;
        }

        SVNProperties command = new SVNProperties();
        if (info != null) {
            command.put(SVNLog.NAME_ATTR, target);
            command.put(SVNProperty.shortPropertyName(SVNProperty.COMMITTED_REVISION), Long.toString(info.getNewRevision()));
            command.put(SVNProperty.shortPropertyName(SVNProperty.COMMITTED_DATE), SVNDate.formatDate(info.getDate()));
            command.put(SVNProperty.shortPropertyName(SVNProperty.LAST_AUTHOR), info.getAuthor());
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
        }
        if (checksum != null) {
            command.put(SVNLog.NAME_ATTR, target);
            command.put(SVNProperty.shortPropertyName(SVNProperty.CHECKSUM), checksum);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
        }
        if (removeLock) {
            command.put(SVNLog.NAME_ATTR, target);
            log.addCommand(SVNLog.DELETE_LOCK, command, false);
            command.clear();
        }
        if (removeChangelist) {
            command.put(SVNLog.NAME_ATTR, target);
            log.addCommand(SVNLog.DELETE_CHANGELIST, command, false);
            command.clear();
        }
        command.put(SVNLog.NAME_ATTR, target);
        command.put(SVNLog.REVISION_ATTR, info == null ? null : Long.toString(info.getNewRevision()));
        if (!explicitCommitPaths.contains(path)) {
            command.put("implicit", "true");
        }
        log.addCommand(SVNLog.COMMIT, command, false);
        command.clear();
        if (wcPropChanges != null && !wcPropChanges.isEmpty()) {
            for (Iterator propNames = wcPropChanges.nameSet().iterator(); propNames.hasNext();) {
                String propName = (String) propNames.next();
                SVNPropertyValue propValue = wcPropChanges.getSVNPropertyValue(propName);
                command.put(SVNLog.NAME_ATTR, target);
                command.put(SVNLog.PROPERTY_NAME_ATTR, propName);
                command.put(SVNLog.PROPERTY_VALUE_ATTR, propValue);
                log.addCommand(SVNLog.MODIFY_WC_PROPERTY, command, false);
                command.clear();
            }
        }
        log.save();
        runLogs();

        if (recursive) {
            for (Iterator ents = entries(true); ents.hasNext();) {
                SVNEntry entry = (SVNEntry) ents.next();
                if (entry.isThisDir()) {
                    continue;
                }
                if (entry.getDepth() == SVNDepth.EXCLUDE) {
                    continue;
                }
                if (entry.getKind() == SVNNodeKind.DIR) {
                    File childPath = getFile(entry.getName());
                    SVNAdminArea childDir = getWCAccess().retrieve(childPath);
                    if (childDir != null) {
                        childDir.commit(getThisDirName(), info, null, removeLock, true, removeChangelist, explicitCommitPaths, params);
                    }
                } else {
                    if (entry.isScheduledForDeletion()) {
                        SVNEntry parentEntry = getEntry(getThisDirName(), true);
                        if (parentEntry.isScheduledForReplacement()) {
                            continue;
                        }
                    }
                    commit(entry.getName(), info, null, removeLock, false, removeChangelist, explicitCommitPaths, params);
                }
            }
        }
    }

    public void walkThisDirectory(ISVNEntryHandler handler, boolean showHidden, SVNDepth depth) throws SVNException {
        File thisDir = getRoot();

        SVNEntry thisEntry = getEntry(getThisDirName(), showHidden);
        if (thisEntry == null) {
            handler.handleError(thisDir, SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND,
                    "Directory ''{0}'' has no THIS_DIR entry", thisDir));
            return;
        }

        try {
            handler.handleEntry(thisDir, thisEntry);
        } catch (SVNException svne) {
            handler.handleError(thisDir, svne.getErrorMessage());
        }

        if (depth == SVNDepth.EMPTY) {
            return;
        }

        for (Iterator entries = entries(showHidden); entries.hasNext();) {
            getWCAccess().checkCancelled();

            SVNEntry entry = (SVNEntry) entries.next();
            if (getThisDirName().equals(entry.getName())) {
                continue;
            }

            File childPath = getFile(entry.getName());
            if (entry.isFile() || depth.compareTo(SVNDepth.IMMEDIATES) >= 0) {
                try {
                    handler.handleEntry(childPath, entry);
                } catch (SVNException svne) {
                    handler.handleError(childPath, svne.getErrorMessage());
                }
            }
            if (entry.isDirectory() && !entry.isHidden() && depth.compareTo(SVNDepth.IMMEDIATES) >= 0) {
                SVNAdminArea childArea = null;
                SVNDepth depthBelowHere = depth;
                if (depth == SVNDepth.IMMEDIATES) {
                    depthBelowHere = SVNDepth.EMPTY;
                }
                try {
                    childArea = getWCAccess().retrieve(childPath);
                } catch (SVNException svne) {
                    handler.handleError(childPath, svne.getErrorMessage());
                }
                if (childArea != null) {
                    childArea.walkThisDirectory(handler, showHidden, depthBelowHere);
                }
            }
        }
    }

    public void setCommitParameters(ISVNCommitParameters commitParameters) {
        myCommitParameters = commitParameters;
    }

    protected void setLocked(boolean locked) {
        myWasLocked = locked;
    }

    private void destroyAdminArea() throws SVNException {
        if (!isLocked()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Write-lock stolen in ''{0}''", getRoot());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNFileUtil.deleteAll(getAdminDirectory(), getWCAccess());
        getWCAccess().closeAdminArea(getRoot());
    }

    private static void markLogProcessed(File logFile) throws SVNException {
        SVNFileUtil.setReadonly(logFile, false);
        OutputStream os = null;
        try {
            os = SVNFileUtil.openFileForWriting(logFile);
        } finally {
            if (os != null) {
                SVNFileUtil.closeFile(os);
            }
        }
    }

    private boolean compareAndVerify(File text, File baseFile, boolean compareTextBase, boolean checksum) throws SVNException {
        String charsetProp = getProperties(text.getName()).getStringPropertyValue(SVNProperty.CHARSET);
        String charset = SVNTranslator.getCharset(charsetProp, text.getPath(), getWCAccess().getOptions());
        String eolStyle = getProperties(text.getName()).getStringPropertyValue(SVNProperty.EOL_STYLE);
        String keywords = getProperties(text.getName()).getStringPropertyValue(SVNProperty.KEYWORDS);
        boolean special = getProperties(text.getName()).getStringPropertyValue(SVNProperty.SPECIAL) != null && SVNFileUtil.symlinksSupported();

        if (special) {
            compareTextBase = true;
        }

        boolean needsTranslation = charset != null || eolStyle != null || keywords != null || special;
        SVNChecksumInputStream checksumStream = null;
        SVNEntry entry = null;

        if (checksum || needsTranslation) {
            InputStream baseStream = null;
            InputStream textStream = null;
            entry = getVersionedEntry(text.getName(), true);
            try {
                baseStream = SVNFileUtil.openFileForReading(baseFile, SVNLogType.WC);
                textStream = special ? null : SVNFileUtil.openFileForReading(text, SVNLogType.WC);
                if (checksum) {
                    if (entry.getChecksum() != null) {
                        checksumStream = new SVNChecksumInputStream(baseStream, SVNChecksumInputStream.MD5_ALGORITHM);
                        baseStream = checksumStream;
                    }
                }
                if (compareTextBase && needsTranslation) {
                    if (!special) {
                        Map keywordsMap = SVNTranslator.computeKeywords(keywords, null, entry.getAuthor(), entry.getCommittedDate(), entry.getRevision() + "", getWCAccess().getOptions());
                        byte[] eols = SVNTranslator.getBaseEOL(eolStyle);
                        textStream = SVNTranslator.getTranslatingInputStream(textStream, charset, eols, true, keywordsMap, false);
                    } else {
                        String linkPath = SVNFileUtil.getSymlinkName(text);
                        if (linkPath == null) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot detranslate symbolic link ''{0}''; file does not exist or not a symbolic link", text);
                            SVNErrorManager.error(err, SVNLogType.DEFAULT);
                        }
                        String symlinkContents = "link " + linkPath;
                        textStream = new ByteArrayInputStream(symlinkContents.getBytes());
                    }
                } else if (needsTranslation) {
                    Map keywordsMap = SVNTranslator.computeKeywords(keywords, entry.getURL(), entry.getAuthor(), entry.getCommittedDate(), entry.getRevision() + "", getWCAccess().getOptions());
                    byte[] eols = SVNTranslator.getEOL(eolStyle, getWCAccess().getOptions());
                    baseStream = SVNTranslator.getTranslatingInputStream(baseStream, charset, eols, false, keywordsMap, true);
                }
                byte[] buffer1 = new byte[8192];
                byte[] buffer2 = new byte[8192];
                try {
                    while(true) {
                        int r1 = SVNFileUtil.readIntoBuffer(baseStream, buffer1, 0, buffer1.length);
                        int r2 = SVNFileUtil.readIntoBuffer(textStream, buffer2, 0, buffer2.length);
                        r1 = r1 == -1 ? 0 : r1;
                        r2 = r2 == -1 ? 0 : r2;
                        if (r1 != r2) {
                            return true;
                        } else if (r1 == 0) {
                            return false;
                        }
                        for(int i = 0; i < r1; i++) {
                            if (buffer1[i] != buffer2[i]) {
                                return true;
                            }
                        }
                    }
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            } finally {
                SVNFileUtil.closeFile(baseStream);
                SVNFileUtil.closeFile(textStream);
            }
        } else {
            return !SVNFileUtil.compareFiles(text, baseFile, null);
        }
        if (entry != null && checksumStream != null)  {
            if (!entry.getChecksum().equals(checksumStream.getDigest())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch indicates corrupt text base: ''{0}''\n" +
                        "   expected: {1}\n" +
                        "     actual: {2}\n", new Object[] {baseFile, entry.getChecksum(), checksumStream.getDigest()});
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        return false;
    }

    private static void resolveDefaults(Map entries) throws SVNException {
        SVNEntry defaultEntry = (SVNEntry) entries.get("");
        if (defaultEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Missing default entry");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (defaultEntry.getRevision() < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_REVISION, "Default entry has no revision number");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (defaultEntry.getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Default entry is missing no URL");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        for(Iterator names = entries.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            SVNEntry entry = (SVNEntry) entries.get(name);
            if (entry == null || entry == defaultEntry || entry.isDirectory()) {
                continue;
            } else if (entry.isFile()) {
                if (entry.getRevision() < 0) {
                    entry.setRevision(defaultEntry.getRevision());
                }
                if (entry.getURL() == null) {
                    entry.setURL(SVNPathUtil.append(defaultEntry.getURL(), SVNEncodingUtil.uriEncode(entry.getName())));
                }
                if (entry.getUUID() == null && !(entry.isScheduledForAddition() || entry.isScheduledForReplacement())) {
                    entry.setUUID(defaultEntry.getUUID());
                }
                if (entry.getCachableProperties() == null) {
                    entry.setCachableProperties(defaultEntry.getCachableProperties());
                }
            }
        }
    }

    protected abstract SVNVersionedProperties formatBaseProperties(SVNProperties srcProperties);

    protected abstract SVNVersionedProperties formatProperties(SVNEntry entry, SVNProperties srcProperties);

    protected void createFormatFile(File formatFile, boolean createMyself) throws SVNException {
        OutputStream os = null;
        try {
            formatFile = createMyself ? getAdminFile("format") : formatFile;
            os = SVNFileUtil.openFileForWriting(formatFile);
            os.write(String.valueOf(getFormatVersion()).getBytes("UTF-8"));
            os.write('\n');
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e, SVNLogType.WC);
        } finally {
            SVNFileUtil.closeFile(os);
        }
    }

    public SVNAdminArea formatWC(SVNAdminArea adminArea) throws SVNException {
        File logFile = adminArea.getAdminFile("log");
        SVNFileType type = SVNFileType.getType(logFile);
        if (type == SVNFileType.FILE) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, 
                    "Changing working copy format failed: found a log file at '" + logFile + "'");
            return adminArea;
        }

        SVNLog log = getLog();
        SVNProperties command = new SVNProperties();
        command.put(SVNLog.FORMAT_ATTR, String.valueOf(getFormatVersion()));
        log.addCommand(SVNLog.UPGRADE_FORMAT, command, false);
        command.clear();

        setWCAccess(adminArea.getWCAccess());
        myEntries = new SVNHashMap();
        Map basePropsCache = getBasePropertiesStorage(true);
        Map propsCache = getPropertiesStorage(true);

        for (Iterator entries = adminArea.entries(true); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            SVNEntry newEntry = new SVNEntry(new SVNHashMap(entry.asMap()), this, entry.getName());
            myEntries.put(entry.getName(), newEntry);

            if (entry.getKind() != SVNNodeKind.FILE && !adminArea.getThisDirName().equals(entry.getName())) {
                continue;
            }

            SVNVersionedProperties srcBaseProps = adminArea.getBaseProperties(entry.getName());
            SVNVersionedProperties dstBaseProps = formatBaseProperties(srcBaseProps.asMap());
            basePropsCache.put(entry.getName(), dstBaseProps);
            dstBaseProps.setModified(true);

            SVNVersionedProperties srcProps = adminArea.getProperties(entry.getName());
            SVNVersionedProperties dstProps = formatProperties(entry, srcProps.asMap());
            propsCache.put(entry.getName(), dstProps);
            dstProps.setModified(true);

            handleCharsetProperty(adminArea, log, newEntry, dstBaseProps);
            handlePropTime(log, newEntry);

            SVNVersionedProperties wcProps = adminArea.getWCProperties(entry.getName());
            if (wcProps != null) {
                log.logChangedWCProperties(entry.getName(), wcProps.asMap());                                
            }
        }
        saveVersionedProperties(log, true);
        log.save();

        if (getFormatVersion() != SVNXMLAdminAreaFactory.WC_FORMAT) {
            SVNFileUtil.deleteFile(getAdminFile("README.txt"));
            SVNFileUtil.deleteFile(getAdminFile("empty-file"));
            SVNFileUtil.deleteAll(getAdminFile("wcprops"), true);
            SVNFileUtil.deleteAll(getAdminFile("tmp/wcprops"), true);
            SVNFileUtil.deleteAll(getAdminFile("dir-wcprops"), true);
            SVNFileUtil.deleteAll(getAdminFile("all-wcprops"), true);
        } else {
            getAdminFile("wcprops").mkdir();
            getAdminFile("tmp/wcprops").mkdir();
            SVNFileUtil.createEmptyFile(getAdminFile("empty-file"));
            SVNAdminUtil.createReadmeFile(getAdminDirectory());
        }

        runLogs();
        return this;
    }

    private void handleCharsetProperty(SVNAdminArea adminArea, SVNLog log, SVNEntry entry, SVNVersionedProperties baseProps) throws SVNException {
        SVNProperties command = new SVNProperties();
        SVNPropertyValue charsetProp = baseProps.getPropertyValue(SVNProperty.CHARSET);
        String currentCharset = charsetProp == null ? null : charsetProp.getString();
        currentCharset = SVNTranslator.getCharset(currentCharset, getAdminFile(entry.getName()).toString(), getWCAccess().getOptions());
        if (currentCharset != null && !SVNProperty.isUTF8(currentCharset)) {
            File detranslatedFile = SVNAdminUtil.createTmpFile(this, "detranslated", ".tmp", true);
            String detranslatedPath = SVNPathUtil.getRelativePath(getRoot().getAbsolutePath(), detranslatedFile.getAbsolutePath());
            File tmpCharsetPropFile = SVNAdminUtil.createTmpFile(this, "props", ".tmp", true);
            String tmpCharsetPropPath = SVNPathUtil.getRelativePath(getRoot().getAbsolutePath(), tmpCharsetPropFile.getAbsolutePath());

            if (getFormatVersion() == SVNAdminArea15Factory.WC_FORMAT) {
                baseProps.setPropertyValue(SVNProperty.CHARSET, SVNPropertyValue.create("UTF-8"));
                SVNWCProperties propFile = new SVNWCProperties(tmpCharsetPropFile, tmpCharsetPropPath);
                propFile.setProperties(baseProps.asMap());
                baseProps.setPropertyValue(SVNProperty.CHARSET, charsetProp);

                File tmpBaseFile = SVNAdminUtil.createTmpFile(this, entry.getName(), ".tmp", true);
                String tmpBasePath = SVNPathUtil.getRelativePath(getRoot().getAbsolutePath(), tmpBaseFile.getAbsolutePath());
                command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getPropBasePath(entry.getName(), SVNNodeKind.FILE, false));
                command.put(SVNLog.DEST_ATTR, tmpBasePath);
                log.addCommand(SVNLog.COPY, command, false);
                command.clear();

                command.put(SVNLog.NAME_ATTR, tmpCharsetPropPath);
                command.put(SVNLog.DEST_ATTR, SVNAdminUtil.getPropBasePath(entry.getName(), SVNNodeKind.FILE, false));
                log.addCommand(SVNLog.MOVE, command, false);
                command.clear();

                command.put(SVNLog.NAME_ATTR, entry.getName());
                command.put(SVNLog.DEST_ATTR, detranslatedPath);
                log.addCommand(SVNLog.COPY_AND_DETRANSLATE, command, false);
                command.clear();

                command.put(SVNLog.DEST_ATTR, SVNAdminUtil.getPropBasePath(entry.getName(), SVNNodeKind.FILE, false));
                command.put(SVNLog.NAME_ATTR, tmpBasePath);
                log.addCommand(SVNLog.MOVE, command, false);
                command.clear();

                command.put(SVNLog.NAME_ATTR, detranslatedPath);
                command.put(SVNLog.DEST_ATTR, entry.getName());
                log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                command.clear();

                command.put(SVNLog.NAME_ATTR, detranslatedPath);
                log.addCommand(SVNLog.DELETE, command, false);
                command.clear();
            } else if (adminArea.getFormatVersion() == SVNAdminArea15Factory.WC_FORMAT) {
                command.put(SVNLog.NAME_ATTR, entry.getName());
                command.put(SVNLog.DEST_ATTR, detranslatedPath);
                log.addCommand(SVNLog.COPY_AND_DETRANSLATE, command, false);
                command.clear();

                baseProps.setPropertyValue(SVNProperty.CHARSET, SVNPropertyValue.create("UTF-8"));
                SVNWCProperties propFile = new SVNWCProperties(tmpCharsetPropFile, tmpCharsetPropPath);
                propFile.setProperties(baseProps.asMap());
                baseProps.setPropertyValue(SVNProperty.CHARSET, charsetProp);

                command.put(SVNLog.NAME_ATTR, tmpCharsetPropPath);
                command.put(SVNLog.DEST_ATTR, SVNAdminUtil.getPropBasePath(entry.getName(), SVNNodeKind.FILE, false));
                log.addCommand(SVNLog.MOVE, command, false);
                command.clear();

                command.put(SVNLog.NAME_ATTR, detranslatedPath);
                command.put(SVNLog.DEST_ATTR, entry.getName());
                log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                command.clear();

                command.put(SVNLog.NAME_ATTR, detranslatedPath);
                log.addCommand(SVNLog.DELETE, command, false);
                command.clear();
            }
        }
    }

    private void handlePropTime(SVNLog log, SVNEntry entry) throws SVNException {
        if (getFormatVersion() == SVNXMLAdminAreaFactory.WC_FORMAT) {
            return;
        }
        SVNProperties command = new SVNProperties();
        command.put(SVNLog.NAME_ATTR, entry.getName());
        command.put(SVNProperty.shortPropertyName(SVNProperty.PROP_TIME), SVNDate.formatDate(new Date(0), true));
        log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
    }

    public void postUpgradeFormat(int format) throws SVNException {
        if (format == getFormatVersion()) {
            createFormatFile(null, true);
            return;
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN,
                "Unexpected format number:\n" +
                "   expected: {0}\n" +
                "     actual: {1}",
                new Object[] { new Integer(getFormatVersion()), new Integer(format) });
        SVNErrorManager.error(err, SVNLogType.WC);
    }
}
