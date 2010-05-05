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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNLog;
import org.exist.versioning.svn.internal.wc.admin.SVNTranslator;
import org.exist.versioning.svn.wc.ISVNConflictHandler;
import org.exist.versioning.svn.wc.ISVNMerger;
import org.exist.versioning.svn.wc.SVNConflictAction;
import org.exist.versioning.svn.wc.SVNConflictChoice;
import org.exist.versioning.svn.wc.SVNConflictDescription;
import org.exist.versioning.svn.wc.SVNConflictReason;
import org.exist.versioning.svn.wc.SVNConflictResult;
import org.exist.versioning.svn.wc.SVNMergeFileSet;
import org.exist.versioning.svn.wc.SVNMergeResult;
import org.exist.versioning.svn.wc.SVNPropertyConflictDescription;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.exist.versioning.svn.wc.SVNTextConflictDescription;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.util.SVNLogType;

import de.regnis.q.sequence.line.QSequenceLineRAByteData;
import de.regnis.q.sequence.line.QSequenceLineRAData;
import de.regnis.q.sequence.line.QSequenceLineRAFileData;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2.0
 */
public class DefaultSVNMerger extends AbstractSVNMerger implements ISVNMerger {

	private static List STATUS_ORDERING = new LinkedList(); 
	static {
		STATUS_ORDERING.add(SVNStatusType.UNKNOWN);
		STATUS_ORDERING.add(SVNStatusType.UNCHANGED);
		STATUS_ORDERING.add(SVNStatusType.INAPPLICABLE);
		STATUS_ORDERING.add(SVNStatusType.CHANGED);
		STATUS_ORDERING.add(SVNStatusType.MERGED);
		STATUS_ORDERING.add(SVNStatusType.OBSTRUCTED); 
		STATUS_ORDERING.add(SVNStatusType.CONFLICTED);
	}
	
    private ISVNConflictHandler myConflictCallback;
    private SVNDiffConflictChoiceStyle myDiffConflictStyle;
    
    public DefaultSVNMerger(byte[] start, byte[] sep, byte[] end) {
        this(start, sep, end, null);
    }

    public DefaultSVNMerger(byte[] start, byte[] sep, byte[] end, ISVNConflictHandler callback) {
        this(start, sep, end, callback, SVNDiffConflictChoiceStyle.CHOOSE_MODIFIED_LATEST);
    }

    public DefaultSVNMerger(byte[] start, byte[] sep, byte[] end, ISVNConflictHandler callback, SVNDiffConflictChoiceStyle style) {
        super(start, sep, end);
        myConflictCallback = callback;
        myDiffConflictStyle = style;
    }


	public SVNMergeResult mergeProperties(String localPath, SVNProperties workingProperties, 
			SVNProperties baseProperties, SVNProperties serverBaseProps, SVNProperties propDiff, 
			SVNAdminArea adminArea, SVNLog log, boolean baseMerge, boolean dryRun) throws SVNException {
        propDiff = propDiff == null ? new SVNProperties() : propDiff;
        
        if (baseProperties == null) {
            baseProperties = adminArea.getBaseProperties(localPath).asMap();
        }
        
        if (workingProperties == null) {
            workingProperties = adminArea.getProperties(localPath).asMap();
        }
        
        if (serverBaseProps == null) {
            serverBaseProps = baseProperties != null ? new SVNProperties(baseProperties) : new SVNProperties();
        }
        
        boolean isDir = adminArea.getThisDirName().equals(localPath);
        
        List conflicts = new LinkedList();
        List conflict = new LinkedList();
        SVNStatusType status = SVNStatusType.UNCHANGED;

        for (Iterator propEntries = propDiff.nameSet().iterator(); propEntries.hasNext();) {
            String propName = (String) propEntries.next();
            SVNPropertyValue toValue = propDiff.getSVNPropertyValue(propName);
            SVNPropertyValue fromValue = serverBaseProps.getSVNPropertyValue(propName);
            SVNPropertyValue workingValue = workingProperties.getSVNPropertyValue(propName);
            SVNPropertyValue baseValue = baseProperties.getSVNPropertyValue(propName);
            boolean isNormal = SVNProperty.isRegularProperty(propName);
            if (baseMerge) {
                changeProperty(baseProperties, propName, toValue);
            }            

            if (isNormal) {
            	status = getPropMergeStatus(status, SVNStatusType.CHANGED);
            }
            
            SVNStatusType newStatus = null;
            if (fromValue == null) {
                newStatus = applySinglePropertyAdd(localPath, isDir, isNormal ? status : null, 
                		workingProperties, propName, baseValue, toValue, workingValue, adminArea, log, conflict, dryRun);
            } else if (toValue == null) {
            	newStatus = applySinglePropertyDelete(localPath, isDir, isNormal ? status : null, 
            			workingProperties, propName, baseValue, fromValue, workingValue, adminArea, log, conflict, dryRun);
            } else {
            	newStatus = applySinglePropertyChange(localPath, isDir, status, workingProperties, propName, 
            			baseValue, fromValue, toValue, workingValue, adminArea, log, conflict, dryRun);
            }
            if (isNormal) {
            	status = newStatus;
            }
            
            if (!conflict.isEmpty()) {
            	if (isNormal) {
            		status = getPropMergeStatus(status, SVNStatusType.CONFLICTED);
            	}
            	
            	Object conflictDescription = conflict.remove(0);
            	if (dryRun) {
            		continue;
            	}
            	conflicts.add(conflictDescription);
            }
        }
        
        if (dryRun) {
            return SVNMergeResult.createMergeResult(status, null);
        }
        
        log = log == null ? adminArea.getLog() : log;
        adminArea.installProperties(localPath, baseProperties, workingProperties, log, baseMerge, true);

        if (!conflicts.isEmpty()) {
            SVNEntry entry = adminArea.getVersionedEntry(localPath, false);
        	String prejTmpPath = adminArea.getThisDirName().equals(localPath) ? 
            		"tmp/dir_conflicts" : "tmp/props/" + localPath;
            File prejTmpFile = SVNFileUtil.createUniqueFile(adminArea.getAdminDirectory(),  prejTmpPath, ".prej", false);

            prejTmpPath = SVNFileUtil.getBasePath(prejTmpFile);
            String prejPath = entry.getPropRejectFile();

            if (prejPath == null) {
                prejPath = adminArea.getThisDirName().equals(localPath) ? "dir_conflicts" : localPath;
                File prejFile = SVNFileUtil.createUniqueFile(adminArea.getRoot(), prejPath, ".prej", false);
                prejPath = SVNFileUtil.getBasePath(prejFile);
            }
            File file = adminArea.getFile(prejTmpPath);

            OutputStream os = SVNFileUtil.openFileForWriting(file);
            try {
                for (Iterator lines = conflicts.iterator(); lines.hasNext();) {
                    String line = (String) lines.next();
                    os.write(SVNEncodingUtil.fuzzyEscape(line).getBytes("UTF-8"));
                }
                os.write(SVNEncodingUtil.fuzzyEscape("\n").getBytes("UTF-8"));
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot write properties conflict file: {1}", e.getLocalizedMessage());
                SVNErrorManager.error(err, e, SVNLogType.WC);
            } finally {
                SVNFileUtil.closeFile(os);
            }

            SVNProperties command = new SVNProperties();
            command.put(SVNLog.NAME_ATTR, prejTmpPath);
            command.put(SVNLog.DEST_ATTR, prejPath);
            log.addCommand(SVNLog.APPEND, command, false);
            command.clear();

            command.put(SVNLog.NAME_ATTR, prejTmpPath);
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();

            command.put(SVNLog.NAME_ATTR, localPath);
            command.put(SVNProperty.shortPropertyName(SVNProperty.PROP_REJECT_FILE),
                        prejPath);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
        }
        return SVNMergeResult.createMergeResult(status, null);
	}

	public SVNDiffConflictChoiceStyle getDiffConflictStyle() {
        return myDiffConflictStyle;
    }
    
    public void setDiffConflictStyle(SVNDiffConflictChoiceStyle diffConflictStyle) {
        myDiffConflictStyle = diffConflictStyle;
    }

    protected SVNStatusType mergeBinary(File baseFile, File localFile, File repositoryFile, SVNDiffOptions options, File resultFile) throws SVNException {
        return SVNStatusType.CONFLICTED;
    }

    protected SVNStatusType mergeText(File baseFile, File localFile, File latestFile, SVNDiffOptions options, File resultFile) throws SVNException {
        FSMergerBySequence merger = new FSMergerBySequence(getConflictStartMarker(), getConflictSeparatorMarker(), getConflictEndMarker());
        int mergeResult = 0;
        RandomAccessFile localIS = null;
        RandomAccessFile latestIS = null;
        RandomAccessFile baseIS = null;
        OutputStream result = null;
        try {
            result = SVNFileUtil.openFileForWriting(resultFile);
            localIS = new RandomAccessFile(localFile, "r");
            latestIS = new RandomAccessFile(latestFile, "r");
            baseIS = new RandomAccessFile(baseFile, "r");

            QSequenceLineRAData baseData = new QSequenceLineRAFileData(baseIS);
            QSequenceLineRAData localData = new QSequenceLineRAFileData(localIS);
            QSequenceLineRAData latestData = new QSequenceLineRAFileData(latestIS);
            mergeResult = merger.merge(baseData, localData, latestData, options, result, getDiffConflictStyle());
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e, SVNLogType.WC);
        } finally {
            SVNFileUtil.closeFile(result);
            SVNFileUtil.closeFile(localIS);
            SVNFileUtil.closeFile(baseIS);
            SVNFileUtil.closeFile(latestIS);
        }

        SVNStatusType status = SVNStatusType.UNCHANGED;
        if (mergeResult == FSMergerBySequence.CONFLICTED) {
            status = SVNStatusType.CONFLICTED;
        } else if (mergeResult == FSMergerBySequence.MERGED) {
            status = SVNStatusType.MERGED;
        }
        return status;
    }

	protected SVNMergeResult processMergedFiles(SVNMergeFileSet files, SVNMergeResult mergeResult) throws SVNException {
	    DefaultSVNMergerAction mergeAction = getMergeAction(files, mergeResult);

	    if (mergeAction == DefaultSVNMergerAction.MARK_CONFLICTED) {
	        mergeResult = handleMarkConflicted(files);
	    } else if (mergeAction == DefaultSVNMergerAction.CHOOSE_BASE) {
	        mergeResult = handleChooseBase(files);
	    } else if (mergeAction == DefaultSVNMergerAction.CHOOSE_REPOSITORY) {
	        mergeResult = handleChooseRepository(files);
	    } else if (mergeAction == DefaultSVNMergerAction.CHOOSE_WORKING) {
	        mergeResult = handleChooseWorking(files);
	    } else if (mergeAction == DefaultSVNMergerAction.CHOOSE_MERGED_FILE) {
	        mergeResult = handleChooseMerged(files, mergeResult);
	    } else if (mergeAction == DefaultSVNMergerAction.MARK_RESOLVED) {
	        mergeResult = handleMarkResolved(files, mergeResult);
	    } else if (mergeAction == DefaultSVNMergerAction.CHOOSE_REPOSITORY_CONFLICTED) {
	        mergeResult = handleChooseConflicted(false, files);
	    } else if (mergeAction == DefaultSVNMergerAction.CHOOSE_WORKING_CONFLICTED) {
	        mergeResult = handleChooseConflicted(true, files);
	    }

	    postMergeCleanup(files);
	    return mergeResult;
	}

	protected DefaultSVNMergerAction getMergeAction(SVNMergeFileSet files, SVNMergeResult mergeResult) throws SVNException {
	    if (mergeResult.getMergeStatus() == SVNStatusType.CONFLICTED) {
	        if (myConflictCallback != null) {
                SVNConflictDescription descr = new SVNTextConflictDescription(files, SVNNodeKind.FILE, 
                        SVNConflictAction.EDIT, SVNConflictReason.EDITED);
                
                SVNConflictResult result = myConflictCallback.handleConflict(descr);
                if (result == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CONFLICT_RESOLVER_FAILURE,
                            "Conflict callback violated API: returned no results.");
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                }
                
                SVNConflictChoice choice = result.getConflictChoice();
                if (choice == SVNConflictChoice.BASE) {
                    return DefaultSVNMergerAction.CHOOSE_BASE;                        
                } else if (choice == SVNConflictChoice.MERGED) {
                    return DefaultSVNMergerAction.CHOOSE_MERGED_FILE;                        
                } else if (choice == SVNConflictChoice.MINE_FULL) {
                    return DefaultSVNMergerAction.CHOOSE_WORKING;                        
                } else if (choice == SVNConflictChoice.THEIRS_FULL) {
                    return DefaultSVNMergerAction.CHOOSE_REPOSITORY;                        
                } else if (choice == SVNConflictChoice.MINE_CONFLICT) {
                    return DefaultSVNMergerAction.CHOOSE_WORKING_CONFLICTED;
                } else if (choice == SVNConflictChoice.THEIRS_CONFLICT) {
                    return DefaultSVNMergerAction.CHOOSE_REPOSITORY_CONFLICTED;
                }
	        }
	        return DefaultSVNMergerAction.MARK_CONFLICTED;
	    }
	    return DefaultSVNMergerAction.CHOOSE_MERGED_FILE;
    }
    
    protected SVNMergeResult handleChooseBase(SVNMergeFileSet files) throws SVNException {
        SVNLog log = files.getLog();
        if (log != null) {
            SVNProperties command = new SVNProperties();
            command.put(SVNLog.NAME_ATTR, files.getBasePath());
            command.put(SVNLog.DEST_ATTR, files.getWCPath());
            log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
            command.clear();
        }
        return SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);
    }
    
    protected SVNMergeResult handleChooseRepository(SVNMergeFileSet files) throws SVNException {
        SVNLog log = files.getLog();
        if (log != null) {
            SVNProperties command = new SVNProperties();
            command.put(SVNLog.NAME_ATTR, files.getRepositoryPath());
            command.put(SVNLog.DEST_ATTR, files.getWCPath());
            log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
            command.clear();
        }

        return SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);
    }

    protected SVNMergeResult handleChooseConflicted(boolean chooseMine, SVNMergeFileSet files) throws SVNException {
        File tmpFile = SVNAdminUtil.createTmpFile(files.getAdminArea());
        String separator = new String(getConflictSeparatorMarker());
        String mineMarker = new String(getConflictStartMarker());
        String theirsMarker = new String(getConflictEndMarker());
        OutputStream tmpOS = null;
        BufferedReader reader = null;
        boolean skip = false;
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(files.getResultFile())));
            tmpOS = SVNFileUtil.openFileForWriting(tmpFile);
            String line = null;
            
            while ((line = reader.readLine()) != null) {
                if (mineMarker.equals(line)) {
                    skip = chooseMine ? false : true;
                    continue;
                } else if (separator.equals(line)) {
                    skip = chooseMine ? true : false;
                    continue;
                } else if (theirsMarker.equals(line)) {
                    skip = false;
                    continue;
                } else if (line.endsWith(mineMarker)) {
                    int ind = line.indexOf(mineMarker);
                    line = line.substring(0, ind);
                    tmpOS.write(line.getBytes());
                    tmpOS.write('\n');
                    
                    skip = chooseMine ? false : true;
                    continue;
                } else if (line.endsWith(separator)) {
                    if (chooseMine) {
                        int ind = line.indexOf(separator);
                        line = line.substring(0, ind);
                        tmpOS.write(line.getBytes());
                        tmpOS.write('\n');
                    }

                    skip = chooseMine ? true : false;
                    continue;
                } else if (line.endsWith(theirsMarker)) {
                    if (!chooseMine) {
                        int ind = line.indexOf(theirsMarker);
                        line = line.substring(0, ind);
                        tmpOS.write(line.getBytes());
                        tmpOS.write('\n');
                    }
                    
                    skip = false;
                    continue;
                }
                if (!skip) {
                    tmpOS.write(line.getBytes());
                    tmpOS.write('\n');
                }
            }
        } catch (IOException ioe) {
            String conflictedPart = chooseMine ? "mine-conflict" : "theirs-conflict";
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Error occured while resolving to " + conflictedPart + ": {0}", ioe.getMessage());
            SVNErrorManager.error(err, ioe, SVNLogType.WC);
        } finally {
            SVNFileUtil.closeFile(tmpOS);
            SVNFileUtil.closeFile(reader);
        }

        SVNLog log = files.getLog();
        if (log != null) {
            SVNProperties command = new SVNProperties();
            String tmpBasePath = SVNFileUtil.getBasePath(tmpFile);
            command.put(SVNLog.NAME_ATTR, tmpBasePath);
            command.put(SVNLog.DEST_ATTR, files.getWCPath());
            log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
            command.clear();

            command.put(SVNLog.NAME_ATTR, tmpBasePath);
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
        }

        return SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);
    }

    protected SVNMergeResult handleChooseWorking(SVNMergeFileSet files) throws SVNException {
        if (files == null) {
            SVNErrorManager.cancel("", SVNLogType.WC);
        }
        return SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);        
    }

    protected SVNMergeResult handleMarkConflicted(SVNMergeFileSet files) throws SVNException {
        if (files.isBinary()) {
            return handleMarkBinaryConflicted(files);
        }
        return handleMarkTextConflicted(files);                

    }

    protected SVNMergeResult handleMarkBinaryConflicted(SVNMergeFileSet files) throws SVNException {
        SVNProperties command = new SVNProperties();
        File root = files.getAdminArea().getRoot();
        SVNLog log = files.getLog();

        File oldFile = SVNFileUtil.createUniqueFile(root, files.getWCPath(), files.getBaseLabel(), false);
        File newFile = SVNFileUtil.createUniqueFile(root, files.getWCPath(), files.getRepositoryLabel(), false);
        SVNFileUtil.copyFile(files.getBaseFile(), oldFile, false);
        SVNFileUtil.copyFile(files.getRepositoryFile(), newFile, false);
        
        
        if (!files.getLocalPath().equals(files.getWCPath())) {
            File mineFile = SVNFileUtil.createUniqueFile(root, files.getWCPath(), files.getLocalLabel(), false);
            String minePath = SVNFileUtil.getBasePath(mineFile);
            if (log != null) {
                command.put(SVNLog.NAME_ATTR, files.getLocalPath());
                command.put(SVNLog.DEST_ATTR, minePath);
                log.addCommand(SVNLog.MOVE, command, false);
                command.clear();
            }
            command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_WRK), minePath);
        } else {
            command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_WRK), "");
        }

        String newPath = SVNFileUtil.getBasePath(newFile);
        String oldPath = SVNFileUtil.getBasePath(oldFile);
        
        makeBinaryConflictEntry(files, newPath, oldPath);

        return SVNMergeResult.createMergeResult(SVNStatusType.CONFLICTED, null);
    }
    
    protected void makeBinaryConflictEntry(SVNMergeFileSet files, String newFilePath, String oldFilePath) throws SVNException {
        SVNProperties command = new SVNProperties();
        SVNLog log = files.getLog();
        if (log != null) {
            command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_NEW), newFilePath);
            command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_OLD), oldFilePath);
            log.logChangedEntryProperties(files.getWCPath(), command);
            command.clear();
        }
        files.getAdminArea().saveEntries(false);
    }

    protected SVNMergeResult handleMarkTextConflicted(SVNMergeFileSet files) throws SVNException {
        SVNProperties command = new SVNProperties();
        File root = files.getAdminArea().getRoot();
        SVNLog log = files.getLog();
        
        if (files.getCopyFromFile() != null) {
            String copyFromPath = files.getCopyFromPath();
            String detranslatedPath = files.getWCPath();
            if (log != null) {
                command.put(SVNLog.NAME_ATTR, copyFromPath);
                command.put(SVNLog.DEST_ATTR, detranslatedPath);
                log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                command.clear();
            }
        }

        File mineFile = SVNFileUtil.createUniqueFile(root, files.getWCPath(), files.getLocalLabel(), false);
        File oldFile = SVNFileUtil.createUniqueFile(root, files.getWCPath(), files.getBaseLabel(), false);
        File newFile = SVNFileUtil.createUniqueFile(root, files.getWCPath(), files.getRepositoryLabel(), false);
        
        String newPath = SVNFileUtil.getBasePath(newFile);
        String oldPath = SVNFileUtil.getBasePath(oldFile);
        String minePath = SVNFileUtil.getBasePath(mineFile);
        
        String basePath = files.getBasePath();
        String latestPath = files.getRepositoryPath();
        File tmpTargetCopy = SVNTranslator.getTranslatedFile(files.getAdminArea(), files.getWCPath(), files.getWCFile(), 
                false, false, false, true);
        String tmpTargetCopyPath = SVNFileUtil.getBasePath(tmpTargetCopy);

        if (log != null) {
            command.put(SVNLog.NAME_ATTR, basePath);
            command.put(SVNLog.DEST_ATTR, oldPath);
            command.put(SVNLog.ATTR2, files.getWCPath());
            log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
            command.clear();

            command.put(SVNLog.NAME_ATTR, latestPath);
            command.put(SVNLog.DEST_ATTR, newPath);
            command.put(SVNLog.ATTR2, files.getWCPath());
            log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
            command.clear();

            command.put(SVNLog.NAME_ATTR, tmpTargetCopyPath);
            command.put(SVNLog.DEST_ATTR, minePath);
            command.put(SVNLog.ATTR2, files.getWCPath());
            log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
            command.clear();

            if (!tmpTargetCopy.equals(files.getLocalFile())) {
                command.put(SVNLog.NAME_ATTR, tmpTargetCopyPath);
                log.addCommand(SVNLog.DELETE, command, false);
                command.clear();
            }

            command.put(SVNLog.NAME_ATTR, files.getResultPath());
            command.put(SVNLog.DEST_ATTR, files.getWCPath());
            command.put(SVNLog.ATTR2, files.getWCPath());
            log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
            command.clear();
        }

        makeTextConflictEntry(files, minePath, newPath, oldPath);
        
        return SVNMergeResult.createMergeResult(SVNStatusType.CONFLICTED, null);
    }

    protected void makeTextConflictEntry(SVNMergeFileSet files, String mineFilePath, String newFilePath, String oldFilePath) throws SVNException {
        SVNLog log = files.getLog();
        if (log != null) {
            SVNProperties command = new SVNProperties();
            command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_WRK), mineFilePath);
            command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_NEW), newFilePath);
            command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_OLD), oldFilePath);
            log.logChangedEntryProperties(files.getWCPath(), command);
            command.clear();
        }
    }
    
    protected SVNMergeResult handleChooseMerged(SVNMergeFileSet files, SVNMergeResult mergeResult) throws SVNException {
        SVNProperties command = new SVNProperties();
        SVNLog log = files.getLog();
        if (mergeResult.getMergeStatus() != SVNStatusType.CONFLICTED) {
            // do normal merge.
            if (mergeResult.getMergeStatus() != SVNStatusType.UNCHANGED) {
                if (log != null) {
                    command.put(SVNLog.NAME_ATTR, files.getResultPath());
                    command.put(SVNLog.DEST_ATTR, files.getWCPath());
                    log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                    command.clear();
                }
            }
            return mergeResult;
        } else if (files.isBinary()) {
            // this action is not applicable for binary conflited files.
            return handleMarkConflicted(files);
        } else {
            if (log != null) {
                // for text file we could use merged version in case of conflict.
                command.put(SVNLog.NAME_ATTR, files.getResultPath());
                command.put(SVNLog.DEST_ATTR, files.getWCPath());
                log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                command.clear();
            }
            return SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);
        }
    }

    protected SVNMergeResult handleMarkResolved(SVNMergeFileSet files, SVNMergeResult mergeResult) throws SVNException {
        if (!files.isBinary()) {
            // same as choose merged.
            return handleChooseMerged(files, mergeResult);
        }
        // same as choose working.
        return handleChooseWorking(files);
    }

    protected void postMergeCleanup(SVNMergeFileSet files) throws SVNException {
        SVNProperties command = new SVNProperties();
        SVNLog log = files.getLog();

        if (!files.getLocalPath().equals(files.getWCPath())) {
            if (log != null) {
                command.put(SVNLog.NAME_ATTR, files.getLocalPath());
                log.addCommand(SVNLog.DELETE, command, false);
                command.clear();
            }
        }
        
        if (log != null) {
            command.put(SVNLog.NAME_ATTR, files.getWCPath());
            log.addCommand(SVNLog.MAYBE_EXECUTABLE, command, false);
            command.clear();

            command.put(SVNLog.NAME_ATTR, files.getWCPath());
            log.addCommand(SVNLog.MAYBE_READONLY, command, false);
            command.clear();

            command.put(SVNLog.NAME_ATTR, files.getResultPath());
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
        }
    }

    private SVNStatusType applySinglePropertyAdd(String localPath, boolean isDir, SVNStatusType status, 
    		SVNProperties workingProps, String propName, SVNPropertyValue baseValue, 
    		SVNPropertyValue newValue, SVNPropertyValue workingValue, SVNAdminArea adminArea, 
    		SVNLog log,	Collection conflicts, boolean dryRun) throws SVNException {
        boolean gotConflict = false;
    	
        if (workingValue != null) {
            if (workingValue.equals(newValue)) {
                status = getPropMergeStatus(status, SVNStatusType.MERGED);
                changeProperty(workingProps, propName, newValue);
            } else {
                if (SVNProperty.MERGE_INFO.equals(propName)) {
                    newValue = SVNPropertyValue.create(
                            SVNMergeInfoUtil.combineMergeInfoProperties(workingValue.getString(),
                    				newValue.getString()));
                    changeProperty(workingProps, propName, newValue);
                    status = getPropMergeStatus(status, SVNStatusType.MERGED);
                } else {
                    gotConflict = maybeGeneratePropConflict(localPath, propName, workingProps, null, newValue, 
                    		baseValue, workingValue,  adminArea, log, isDir, dryRun);
                    if (gotConflict) {
                        conflicts.add(MessageFormat.format("Trying to add new property ''{0}'' with value ''{1}'',\n" +
                                "but property already exists with value ''{2}''.",
                                new Object[] { propName, SVNPropertyValue.getPropertyAsString(newValue),
                                SVNPropertyValue.getPropertyAsString(workingValue) }));
                    	
                    }
                }
            }
    	} else if (baseValue != null) {
    		gotConflict = maybeGeneratePropConflict(localPath, propName, workingProps, null, newValue, baseValue, 
    				null, adminArea, log, isDir, dryRun);
    		if (gotConflict) {
                conflicts.add(MessageFormat.format("Trying to create property ''{0}'' with value ''{1}'',\n" +
                        "but it has been locally deleted.",
                        new Object[] { propName, SVNPropertyValue.getPropertyAsString(newValue) }));
    		}
    	} else {
    		changeProperty(workingProps, propName, newValue);
    	}
        return status;
    }

    private void changeProperty(SVNProperties properties, String propName, SVNPropertyValue propValue) {
        if (propValue == null) {
            properties.remove(propName);
        } else {
            properties.put(propName, propValue);
        }
    }
    
    private SVNStatusType applySinglePropertyChange(String localPath, boolean isDir, SVNStatusType status, 
            SVNProperties workingProps, String propName, SVNPropertyValue baseValue, 
            SVNPropertyValue oldValue, SVNPropertyValue newValue, SVNPropertyValue workingValue, 
            SVNAdminArea adminArea, SVNLog log, Collection conflicts, boolean dryRun) throws SVNException {
        if (SVNProperty.MERGE_INFO.equals(propName)) {
            return applySingleMergeInfoPropertyChange(localPath, isDir, status, workingProps, propName, baseValue, oldValue, 
                    newValue, workingValue, adminArea, log, conflicts, dryRun);
        } 
        return applySingleGenericPropertyChange(localPath, isDir, status, workingProps, propName, baseValue, oldValue, newValue, 
                workingValue, adminArea, log, conflicts, dryRun);
    }

    private SVNStatusType applySingleMergeInfoPropertyChange(String localPath, boolean isDir, SVNStatusType status, 
            SVNProperties workingProps, String propName, SVNPropertyValue baseValue, 
            SVNPropertyValue oldValue, SVNPropertyValue newValue, SVNPropertyValue workingValue, 
            SVNAdminArea adminArea, SVNLog log, Collection conflicts, boolean dryRun) throws SVNException {
        boolean gotConflict = false;
        
        if ((workingValue != null && baseValue == null) || 
                (workingValue == null && baseValue != null) ||
                (workingValue != null && baseValue != null && !workingValue.equals(baseValue))) {
            if (workingValue != null) {
                if (workingValue.equals(newValue)) {
                    status = getPropMergeStatus(status, SVNStatusType.MERGED);
                } else {
                    newValue = SVNPropertyValue.create(SVNMergeInfoUtil.combineForkedMergeInfoProperties(oldValue.getString(),
                            workingValue.getString(), newValue.getString()));
                    changeProperty(workingProps, propName, newValue);
                    status = getPropMergeStatus(status, SVNStatusType.MERGED);
                }
            } else {
                gotConflict = maybeGeneratePropConflict(localPath, propName, workingProps, oldValue, newValue, 
                        baseValue, workingValue, adminArea, log, isDir, dryRun);
                if (gotConflict) {
                    conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                            "but it has been locally deleted.", new Object[] { propName, SVNPropertyValue.getPropertyAsString(oldValue), 
                            SVNPropertyValue.getPropertyAsString(newValue) }));
                }
            }
        } else if (workingValue == null) {
            Map addedMergeInfo = new TreeMap();
            SVNMergeInfoUtil.diffMergeInfoProperties(null, addedMergeInfo, oldValue.getString(), null, newValue.getString(), null);
            newValue = SVNPropertyValue.create(SVNMergeInfoUtil.formatMergeInfoToString(addedMergeInfo, null));
            changeProperty(workingProps, propName, newValue);
        } else {
            if (baseValue.equals(oldValue)) {
                changeProperty(workingProps, propName, newValue);
            } else {
                newValue = SVNPropertyValue.create(SVNMergeInfoUtil.combineForkedMergeInfoProperties(oldValue.getString(),
                        workingValue.getString(), newValue.getString()));
               changeProperty(workingProps, propName, newValue);
               status = getPropMergeStatus(status, SVNStatusType.MERGED);
            }
        }
        return status;
    }

    private SVNStatusType applySingleGenericPropertyChange(String localPath, boolean isDir, SVNStatusType status, 
    		SVNProperties workingProps, String propName, SVNPropertyValue baseValue, 
    		SVNPropertyValue oldValue, SVNPropertyValue newValue, SVNPropertyValue workingValue, 
    		SVNAdminArea adminArea, SVNLog log, Collection conflicts, boolean dryRun) throws SVNException {
    	boolean gotConflict = false;
    	if ((workingValue == null && oldValue == null) || (workingValue != null && oldValue != null && 
    	        workingValue.equals(oldValue))) {
            changeProperty(workingProps, propName, newValue);
    	} else {
            gotConflict = maybeGeneratePropConflict(localPath, propName, workingProps, oldValue, 
                    newValue, baseValue, workingValue, adminArea, log, isDir, dryRun);
            if (gotConflict) {
                if (workingValue != null && baseValue != null && workingValue.equals(baseValue)) {
                    conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                            "but property already exists with value ''{3}''.",
                            new Object[] { propName, SVNPropertyValue.getPropertyAsString(oldValue),
                            SVNPropertyValue.getPropertyAsString(newValue),
                            SVNPropertyValue.getPropertyAsString(workingValue) }));
                } else if (workingValue != null && baseValue != null) {
                    conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                            "but the property has been locally changed from ''{3}'' to ''{4}''.", new Object[] { propName, 
                            SVNPropertyValue.getPropertyAsString(oldValue), SVNPropertyValue.getPropertyAsString(newValue), 
                            SVNPropertyValue.getPropertyAsString(baseValue), SVNPropertyValue.getPropertyAsString(workingValue) }));
                } else if (workingValue != null) {
                    conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                            "but property has been locally added with value ''{3}''.", new Object[] { propName, 
                            SVNPropertyValue.getPropertyAsString(oldValue), SVNPropertyValue.getPropertyAsString(newValue), 
                            SVNPropertyValue.getPropertyAsString(workingValue) }));
                } else if (baseValue != null) {
                    conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                            "but it has been locally deleted.", new Object[] { propName, 
                            SVNPropertyValue.getPropertyAsString(oldValue), SVNPropertyValue.getPropertyAsString(newValue) }));
                } else {
                    conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                            "but the property does not exist.", new Object[] { propName, SVNPropertyValue.getPropertyAsString(oldValue),
                            SVNPropertyValue.getPropertyAsString(newValue) }));
                }
            }

    	}
    	return status;
    }
    
    private SVNStatusType applySinglePropertyDelete(String localPath, boolean isDir, SVNStatusType status, 
    		SVNProperties workingProps, String propName, SVNPropertyValue baseValue, 
    		SVNPropertyValue oldValue, SVNPropertyValue workingValue, SVNAdminArea adminArea, 
    		SVNLog log,	Collection conflicts, boolean dryRun) throws SVNException {
    	boolean gotConflict = false;

    	if (baseValue == null) {
            changeProperty(workingProps, propName, (SVNPropertyValue) null); 
    		if (oldValue != null) {
    			status = getPropMergeStatus(status, SVNStatusType.MERGED);
    		}
    	} else if (baseValue.equals(oldValue)) {
    		if (workingValue != null) {
    			if (workingValue.equals(oldValue)) {
    				changeProperty(workingProps, propName, (SVNPropertyValue) null);
    			} else {
    				gotConflict = maybeGeneratePropConflict(localPath, propName, workingProps, oldValue, null, 
    						baseValue, workingValue, adminArea, log, isDir, dryRun);
    				if (gotConflict) {
                        conflicts.add(MessageFormat.format("Trying to delete property ''{0}'' with value ''{1}''\n " +
                        		"but it has been modified from ''{2}'' to ''{3}''.",
                        		new Object[] { propName, SVNPropertyValue.getPropertyAsString(oldValue),
                        		SVNPropertyValue.getPropertyAsString(baseValue),
                        		SVNPropertyValue.getPropertyAsString(workingValue) }));
    				}
    			}
    		} else {
    			status = getPropMergeStatus(status, SVNStatusType.MERGED);
    		}
    	} else {
    		gotConflict = maybeGeneratePropConflict(localPath, propName, workingProps, oldValue, null, 
    				baseValue, workingValue, adminArea, log, isDir, dryRun);
    		if (gotConflict) {
                conflicts.add(MessageFormat.format("Trying to delete property ''{0}'' with value ''{1}''\n " +
                        "but the local value is ''{2}''.",
                        new Object[] { propName, SVNPropertyValue.getPropertyAsString(baseValue),
                                SVNPropertyValue.getPropertyAsString(workingValue) }));
    		}
    	}
    	return status;
    }
    
    private static SVNStatusType getPropMergeStatus(SVNStatusType status, SVNStatusType newStatus) {
    	if (status == null) {
    		return null;
    	}
    	
    	int statusInd = STATUS_ORDERING.indexOf(status);
    	int newStatusInd = STATUS_ORDERING.indexOf(newStatus);
    	if (newStatusInd <= statusInd) {
    		return status;
    	}
    	return newStatus;
    	
    }

    private boolean maybeGeneratePropConflict(String localPath, String propName, SVNProperties workingProps, 
            SVNPropertyValue oldValue, SVNPropertyValue newValue, SVNPropertyValue baseValue, 
            SVNPropertyValue workingValue, SVNAdminArea adminArea, SVNLog log, boolean isDir, boolean dryRun) throws SVNException {
        boolean conflictRemains = true;
        if (myConflictCallback == null || dryRun) {
            return conflictRemains;
        }

        File path = adminArea.getFile(localPath);
        File workingFile = null;
        File newFile = null;
        File baseFile = null;
        File mergedFile = null;
        try {
            if (workingValue != null) {
                workingFile = SVNFileUtil.createUniqueFile(path.getParentFile(), path.getName(), ".tmp", false);
                OutputStream os = SVNFileUtil.openFileForWriting(workingFile);
                try {
                    os.write(SVNPropertyValue.getPropertyAsBytes(workingValue));
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR,
                            "Cannot write a working property value file: {1}", e.getLocalizedMessage());
                    SVNErrorManager.error(err, e, SVNLogType.WC);
                } finally {
                    SVNFileUtil.closeFile(os);
                }
            }

            if (newValue != null) {
                newFile = SVNFileUtil.createUniqueFile(path.getParentFile(), path.getName(), ".tmp", false);
                OutputStream os = SVNFileUtil.openFileForWriting(newFile);
                try {
                    os.write(SVNPropertyValue.getPropertyAsBytes(newValue));
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR,
                            "Cannot write a new property value file: {1}", e.getLocalizedMessage());
                    SVNErrorManager.error(err, e, SVNLogType.WC);
                } finally {
                    SVNFileUtil.closeFile(os);
                }
            }

            if ((baseValue != null && oldValue == null) ||
                    (baseValue == null && oldValue != null)) {
                SVNPropertyValue theValue = baseValue != null ? baseValue : oldValue;
                baseFile = SVNFileUtil.createUniqueFile(path.getParentFile(), path.getName(), ".tmp", false);
                OutputStream os = SVNFileUtil.openFileForWriting(baseFile);
                try {
                    os.write(SVNPropertyValue.getPropertyAsBytes(theValue));
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR,
                            "Cannot write a base property value file: {1}", e.getLocalizedMessage());
                    SVNErrorManager.error(err, e, SVNLogType.WC);
                } finally {
                    SVNFileUtil.closeFile(os);
                }
            } else if (baseValue != null && oldValue != null) {
                SVNPropertyValue theValue = baseValue;
                if (!baseValue.equals(oldValue)) {
                    if (workingValue != null && baseValue.equals(workingValue)) {
                        theValue = oldValue;
                    }
                }
                baseFile = SVNFileUtil.createUniqueFile(path.getParentFile(), path.getName(), ".tmp", false);
                OutputStream os = SVNFileUtil.openFileForWriting(baseFile);
                try {
                    os.write(SVNPropertyValue.getPropertyAsBytes(theValue));
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR,
                            "Cannot write a base property value file: {1}", e.getLocalizedMessage());
                    SVNErrorManager.error(err, e, SVNLogType.WC);
                } finally {
                    SVNFileUtil.closeFile(os);
                }

                if (workingValue != null && newValue != null) {
                    FSMergerBySequence merger = new FSMergerBySequence(getConflictStartMarker(),
                            getConflictSeparatorMarker(), getConflictEndMarker());
                    OutputStream result = null;
                    try {
                        mergedFile = SVNFileUtil.createUniqueFile(path.getParentFile(), path.getName(), ".tmp", false);
                        result = SVNFileUtil.openFileForWriting(mergedFile);

                        QSequenceLineRAData baseData = new QSequenceLineRAByteData(SVNPropertyValue.getPropertyAsBytes(theValue));
                        QSequenceLineRAData localData = new QSequenceLineRAByteData(SVNPropertyValue.getPropertyAsBytes(workingValue));
                        QSequenceLineRAData latestData = new QSequenceLineRAByteData(SVNPropertyValue.getPropertyAsBytes(newValue));
                        merger.merge(baseData, localData, latestData, null, result, null);
                    } catch (IOException e) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                        SVNErrorManager.error(err, e, SVNLogType.WC);
                    } finally {
                        SVNFileUtil.closeFile(result);
                    }
                }
            }

            String mimeType = null;
            if (!isDir && workingProps != null) {
                mimeType = workingProps.getStringValue(SVNProperty.MIME_TYPE);
            }
            SVNMergeFileSet fileSet = new SVNMergeFileSet(adminArea, log, baseFile, workingFile, localPath, 
                    newFile, mergedFile, null, mimeType);

            SVNConflictAction action = SVNConflictAction.EDIT;
            if (oldValue == null && newValue != null) {
                action = SVNConflictAction.ADD;
            } else if (oldValue != null && newValue == null) {
                action = SVNConflictAction.DELETE;
            }

            SVNConflictReason reason = SVNConflictReason.EDITED;
            if (baseValue != null && workingValue == null) {
                reason = SVNConflictReason.DELETED;
            } else if (baseValue == null && workingValue != null) {
                reason = SVNConflictReason.OBSTRUCTED;
            }
            SVNConflictDescription description = new SVNPropertyConflictDescription(fileSet,
                    isDir ? SVNNodeKind.DIR : SVNNodeKind.FILE, propName, action, reason);
            SVNConflictResult result = myConflictCallback.handleConflict(description);
            if (result == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CONFLICT_RESOLVER_FAILURE,
                        "Conflict callback violated API: returned no results.");
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
            SVNConflictChoice choice = result.getConflictChoice();
            if (choice == SVNConflictChoice.MINE_FULL) {
                conflictRemains = false;
            } else if (choice == SVNConflictChoice.THEIRS_FULL) {
                changeProperty(workingProps, propName, newValue);
                conflictRemains = false;
            } else if (choice == SVNConflictChoice.BASE) {
                changeProperty(workingProps, propName, baseValue);
                conflictRemains = false;
            } else if (choice == SVNConflictChoice.MERGED) {
                if (mergedFile == null && result.getMergedFile() == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CONFLICT_RESOLVER_FAILURE,
                            "Conflict callback violated API: returned no merged file.");
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                }

                String mergedString = SVNFileUtil.readFile(mergedFile != null ? mergedFile : result.getMergedFile());
                changeProperty(workingProps, propName, SVNPropertyValue.create(mergedString));
                conflictRemains = false;
            }
            return conflictRemains;
        } finally {
            SVNFileUtil.deleteFile(workingFile);
            SVNFileUtil.deleteFile(newFile);
            SVNFileUtil.deleteFile(baseFile);
            SVNFileUtil.deleteFile(mergedFile);
        }
    }
}
