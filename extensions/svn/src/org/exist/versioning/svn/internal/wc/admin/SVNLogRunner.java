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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.SVNTreeConflictUtil;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.exist.versioning.svn.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNLogRunner {
    private boolean myIsEntriesChanged;
    private boolean myIsWCPropertiesChanged;
    private Map myTreeConflicts;
    private boolean myHasAddedTreeConflicts;

    private int myLogCount;
    private boolean myIsRerun;
    
    public SVNLogRunner(boolean rerun) {
        myIsRerun = rerun;
    }

    private Map getTreeConflicts() {
        if (myTreeConflicts == null) {
            myTreeConflicts = new SVNHashMap();
        }
        return myTreeConflicts;
    }

    public void runCommand(SVNAdminArea adminArea, String name, SVNProperties attributes, int count) throws SVNException {
        SVNException error = null;
        String fileName = attributes.getStringValue(SVNLog.NAME_ATTR);
        if (SVNLog.DELETE_ENTRY.equals(name)) {
            File path = adminArea.getFile(fileName);
            SVNAdminArea dir = adminArea.getWCAccess().probeRetrieve(path);
            SVNEntry entry = dir.getWCAccess().getEntry(path, false);
            if (entry == null) {
                return;
            }
            try {
                if (entry.isDirectory()) {
                    try {
                        SVNAdminArea childDir = dir.getWCAccess().retrieve(path);
                        // it should be null when there is no dir already.
                        if (childDir != null) {
                            childDir.extendLockToTree();
                            childDir.removeFromRevisionControl(childDir.getThisDirName(), true, false);
                        } else {
                            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED), SVNLogType.DEFAULT);
                        }
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                            if (!entry.isScheduledForAddition()) {
                                adminArea.deleteEntry(fileName);
                                adminArea.saveEntries(false);
                            }
                        } else {
                            throw e;
                        }
                    }
                } else if (entry.isFile()) {
                    adminArea.removeFromRevisionControl(fileName, true, false);
                }
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_LEFT_LOCAL_MOD) {
                    error = e;
                }
            }
        } else if (SVNLog.MODIFY_ENTRY.equals(name)) {
            try {
                Map entryAttrs = new SVNHashMap();
                for (Iterator attrtibutesIter = attributes.nameSet().iterator(); attrtibutesIter.hasNext();) {
                    String attrName = (String) attrtibutesIter.next();
                    if ("".equals(attrName) || SVNLog.NAME_ATTR.equals(attrName) || SVNLog.FORCE_ATTR.equals(attrName)) {
                        continue;
                    }
                    
                    String value = attributes.getStringValue(attrName); 
                    attrName = SVNProperty.SVN_ENTRY_PREFIX + attrName;
                    entryAttrs.put(attrName, value);
                }
                
                if (entryAttrs.containsKey(SVNProperty.TEXT_TIME)) {
                    String value = (String) entryAttrs.get(SVNProperty.TEXT_TIME); 
                    if (SVNLog.WC_TIMESTAMP.equals(value)) {
                        File file = adminArea.getFile(fileName);
                        value = SVNDate.formatDate(new Date(file.lastModified()));
                        entryAttrs.put(SVNProperty.TEXT_TIME, value);
                    }
                }

                if (entryAttrs.containsKey(SVNProperty.PROP_TIME)) {
                    String value = (String) entryAttrs.get(SVNProperty.PROP_TIME); 
                    if (SVNLog.WC_TIMESTAMP.equals(value)) {
                        SVNEntry entry = adminArea.getEntry(fileName, false);
                        if (entry == null) {
                            return;
                        }
                        value = adminArea.getPropertyTime(fileName); 
                        entryAttrs.put(SVNProperty.PROP_TIME, value);
                    }                
                }

                if (entryAttrs.containsKey(SVNProperty.WORKING_SIZE)) {
                    String workingSize = (String) entryAttrs.get(SVNProperty.WORKING_SIZE);
                    if (SVNLog.WC_WORKING_SIZE.equals(workingSize)) {
                        SVNEntry entry = adminArea.getEntry(fileName, false);
                        if (entry == null) {
                            return;
                        }
                        File file = adminArea.getFile(fileName);
                        if (!file.exists()) {
                            entryAttrs.put(SVNProperty.WORKING_SIZE, "0");
                        } else {
                            try {
                                entryAttrs.put(SVNProperty.WORKING_SIZE, Long.toString(file.length()));
                            } catch (SecurityException se) {
                                SVNErrorCode code = count <= 1 ? SVNErrorCode.WC_BAD_ADM_LOG_START : SVNErrorCode.WC_BAD_ADM_LOG;
                                SVNErrorMessage err = SVNErrorMessage.create(code, "Error getting file size on ''{0}''", file);
                                SVNErrorManager.error(err, se, SVNLogType.WC);
                            }
                        }
                    }
                }
                
                boolean force = false;
                if (attributes.containsName(SVNLog.FORCE_ATTR)) {
                    String forceAttr = attributes.getStringValue(SVNLog.FORCE_ATTR);
                    force = SVNProperty.booleanValue(forceAttr);
                }
                
                if (myIsRerun && adminArea.getEntry(fileName, true) == null) {
                    // skip modification without an error.
                } else {
                    try {
                        
                        adminArea.modifyEntry(fileName, entryAttrs, false, force);
                    } catch (SVNException svne) {
                        SVNErrorCode code = count <= 1 ? SVNErrorCode.WC_BAD_ADM_LOG_START : SVNErrorCode.WC_BAD_ADM_LOG;
                        SVNErrorMessage err = SVNErrorMessage.create(code, "Error modifying entry for ''{0}''", fileName);
                        SVNErrorManager.error(err, svne, SVNLogType.WC);
                    }
                    setEntriesChanged(true);
                }
            } catch (SVNException svne) {
                error = svne;
            }
        } else if (SVNLog.MODIFY_WC_PROPERTY.equals(name)) {
            try {
                SVNVersionedProperties wcprops = adminArea.getWCProperties(fileName);
                if (wcprops != null) {
                    String propName = attributes.getStringValue(SVNLog.PROPERTY_NAME_ATTR);
                    SVNPropertyValue propValue = attributes.getSVNPropertyValue(SVNLog.PROPERTY_VALUE_ATTR);
                    wcprops.setPropertyValue(propName, propValue);
                    setWCPropertiesChanged(true);
                }
            } catch (SVNException svne) {
                error = svne;
            }
        } else if (SVNLog.DELETE_LOCK.equals(name)) {
            try {
                SVNEntry entry = adminArea.getEntry(fileName, true);
                if (entry != null) {
                    entry.setLockToken(null);
                    entry.setLockOwner(null);
                    entry.setLockCreationDate(null);
                    entry.setLockComment(null);
                    setEntriesChanged(true);
                }
            } catch (SVNException svne) {
                SVNErrorCode code = count <= 1 ? SVNErrorCode.WC_BAD_ADM_LOG_START : SVNErrorCode.WC_BAD_ADM_LOG;
                SVNErrorMessage err = SVNErrorMessage.create(code, "Error removing lock from entry for ''{0}''", fileName);
                error = new SVNException(err, svne);
            }
        } else if (SVNLog.DELETE_CHANGELIST.equals(name)) {
            try {
                Map entryAttrs = new SVNHashMap();
                entryAttrs.put(SVNProperty.CHANGELIST, null);
                adminArea.modifyEntry(fileName, entryAttrs, false, false);
                setEntriesChanged(true);
            } catch (SVNException svne) {
                SVNErrorCode code = count <= 1 ? SVNErrorCode.WC_BAD_ADM_LOG_START : SVNErrorCode.WC_BAD_ADM_LOG;
                SVNErrorMessage err = SVNErrorMessage.create(code, 
                        "Error removing changelist from entry ''{0}''", fileName);
                error = new SVNException(err, svne);
            }
        } else if (SVNLog.DELETE.equals(name)) {
            File file = adminArea.getFile(fileName);
            SVNFileUtil.deleteFile(file);
        } else if (SVNLog.READONLY.equals(name)) {
            File file = adminArea.getFile(fileName);
            SVNFileUtil.setReadonly(file, true);
        } else if (SVNLog.MOVE.equals(name)) {
            File src = adminArea.getFile(fileName);
            File dst = adminArea.getFile(attributes.getStringValue(SVNLog.DEST_ATTR));
            try {
                SVNFileUtil.rename(src, dst);
            } catch (SVNException svne) {
                if (!myIsRerun || src.exists()) {
                    error = new SVNException(svne.getErrorMessage().wrap("Can't move source to dest"), svne);
                }
            }
        } else if (SVNLog.APPEND.equals(name)) {
            File src = adminArea.getFile(fileName);
            File dst = adminArea.getFile(attributes.getStringValue(SVNLog.DEST_ATTR));
            OutputStream os = null;
            InputStream is = null;
            try {
                os = SVNFileUtil.openFileForWriting(dst, true);
                is = SVNFileUtil.openFileForReading(src, SVNLogType.WC);
                while (true) {
                    int r = is.read();
                    if (r < 0) {
                        break;
                    }
                    os.write(r);
                }
            } catch (IOException e) {
                if (!myIsRerun || !(e instanceof FileNotFoundException)) { 
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot write to ''{0}'': {1}", new Object[] {dst, e.getLocalizedMessage()});
                    error = new SVNException(err, e);
                } 
            } catch (SVNException svne) {
                if (!myIsRerun || src.exists()) {
                    error = svne;
                }                
            } finally {
                SVNFileUtil.closeFile(os);
                SVNFileUtil.closeFile(is);
            }
        } else if (SVNLog.SET_TIMESTAMP.equals(name)) {
            File file = adminArea.getFile(fileName);
            String timestamp = attributes.getStringValue(SVNLog.TIMESTAMP_ATTR);
            try {
                if (timestamp == null) {
                    SVNErrorCode code = count <= 1 ? SVNErrorCode.WC_BAD_ADM_LOG_START : SVNErrorCode.WC_BAD_ADM_LOG;
                    SVNErrorMessage err = SVNErrorMessage.create(code, "Missing 'timestamp' attribute in ''{0}''", adminArea.getRoot());
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                Date time = SVNDate.parseDate(timestamp);
                //TODO: what about special files (do not set for them).
                if (!file.setLastModified(time.getTime())) {
                    if (!file.canWrite() && file.isFile()) {
                        SVNFileUtil.setReadonly(file, false);
                        file.setLastModified(time.getTime());
                        SVNFileUtil.setReadonly(file, true);
                    }
                }
            } catch (SVNException svne) {
                error = svne;
            }
        } else if (SVNLog.UPGRADE_FORMAT.equals(name)) {
            String format = attributes.getStringValue(SVNLog.FORMAT_ATTR);
            SVNErrorCode code = count <= 1 ? SVNErrorCode.WC_BAD_ADM_LOG_START : SVNErrorCode.WC_BAD_ADM_LOG;
            try {
                if (format == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(code, "Invalid 'format' attribute");
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                int number = -1;
                try {
                    number = Integer.parseInt(format);
                } catch (NumberFormatException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(code, "Invalid 'format' attribute");
                    SVNErrorManager.error(err, e, SVNLogType.WC);
                }
                if (number == 0) {
                    SVNErrorMessage err = SVNErrorMessage.create(code, "Invalid 'format' attribute");
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
               
                adminArea.postUpgradeFormat(number);
                setEntriesChanged(true);
            } catch (SVNException svne) {
                error = svne;
            }
        } else if (SVNLog.MAYBE_READONLY.equals(name)) {
            File file = adminArea.getFile(fileName);
            try {
                SVNEntry entry = adminArea.getEntry(fileName, false);
                if (entry != null) {
                    adminArea.closeVersionedProperties();
                    SVNVersionedProperties props = adminArea.getProperties(fileName);
                    String needsLock = props.getStringPropertyValue(SVNProperty.NEEDS_LOCK);
                    if (entry.getLockToken() == null && needsLock != null) {
                        SVNFileUtil.setReadonly(file, true);
                    }
                }
            } catch (SVNException svne) {
                error = svne;
            }
        } else if (SVNLog.MAYBE_EXECUTABLE.equals(name)) {
            adminArea.closeVersionedProperties();
            SVNVersionedProperties props = adminArea.getProperties(fileName);
            boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;
            if (executable) {
                SVNFileUtil.setExecutable(adminArea.getFile(fileName), true);
            }
        } else if (SVNLog.COPY_AND_TRANSLATE.equals(name)) {
            String dstName = attributes.getStringValue(SVNLog.DEST_ATTR);
            String versionedName = attributes.getStringValue(SVNLog.ATTR2);
            if (versionedName == null) {
                versionedName = dstName;
            }
            File src = adminArea.getFile(fileName);
            File dst = adminArea.getFile(dstName);

            //when performing a merge from a log runner we may have just set 
            //new properties (log command that copies a new base prop file), 
            //but probably we've got a non empty props cache which is no more 
            //valid, so clean it up.
            adminArea.closeVersionedProperties();
            try {
                try {
                    SVNTranslator.translate(adminArea, versionedName, src, dst, null, true);
                } catch (SVNException svne) {
                    if (!myIsRerun || src.exists()) {
                        throw svne;
                    }
                }

                
                // get properties for this entry.
                SVNVersionedProperties props = adminArea.getProperties(dstName);
                boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;
    
                if (executable) {
                    SVNFileUtil.setExecutable(dst, true);
                }
                SVNEntry entry = adminArea.getEntry(dstName, false);
                if (entry != null && entry.getLockToken() == null && props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null) {
                    SVNFileUtil.setReadonly(dst, true);
                }
            } catch (SVNException svne) {
                error = svne;
            }
        } else if (SVNLog.COPY_AND_DETRANSLATE.equals(name)) {
            String dstName = attributes.getStringValue(SVNLog.DEST_ATTR);
            String versionedName = attributes.getStringValue(SVNLog.ATTR2);
            adminArea.closeVersionedProperties();
            try {
                SVNTranslator.translate(adminArea, versionedName != null ? versionedName : fileName, fileName, dstName, false);
            } catch (SVNException svne) {
                error = svne;
            }
        } else if (SVNLog.COPY.equals(name)) {
            File src = adminArea.getFile(fileName);
            File dst = adminArea.getFile(attributes.getStringValue(SVNLog.DEST_ATTR));
            try {
                SVNFileUtil.copy(src, dst, true, false);
            } catch (SVNException svne) {
                error = svne;
            }
        } else if (SVNLog.ADD_TREE_CONFLICT.equals(name)) {
            File dirPath = adminArea.getRoot();
            String conflictData = attributes.getStringValue(SVNLog.DATA_ATTR);
            Map newConflicts = SVNTreeConflictUtil.readTreeConflicts(dirPath, conflictData);
            Object[] conflictArray = newConflicts.values().toArray();
            SVNTreeConflictDescription newConflict = (SVNTreeConflictDescription) conflictArray[0];
            if (!getTreeConflicts().containsKey(newConflict.getPath())) {
                getTreeConflicts().put(newConflict.getPath(), newConflict);
                setTreeConflictsAdded(true);
            }
        } else if (SVNLog.MERGE.equals(name)) {
            File target = adminArea.getFile(fileName);
            try {
                SVNErrorCode code = count <= 1 ? SVNErrorCode.WC_BAD_ADM_LOG_START : SVNErrorCode.WC_BAD_ADM_LOG;
                String leftPath = attributes.getStringValue(SVNLog.ATTR1);
                if (leftPath == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(code, "Missing 'left' attribute in ''{0}''", adminArea.getRoot());
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                String rightPath = attributes.getStringValue(SVNLog.ATTR2);
                if (rightPath == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(code, "Missing 'right' attribute in ''{0}''", adminArea.getRoot());
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                String leftLabel = attributes.getStringValue(SVNLog.ATTR3);
                leftLabel = leftLabel == null ? ".old" : leftLabel;
                String rightLabel = attributes.getStringValue(SVNLog.ATTR4);
                rightLabel = rightLabel == null ? ".new" : rightLabel;
                String targetLabel = attributes.getStringValue(SVNLog.ATTR5);
                targetLabel = targetLabel == null ? ".working" : targetLabel;
    
                //when performing a merge from a log runner we may have just set 
                //new properties (log command that copies a new base prop file), 
                //but probably we've got a non empty props cache which is no more 
                //valid, so clean it up.
                adminArea.closeVersionedProperties();
                SVNVersionedProperties props = adminArea.getProperties(fileName);
                SVNEntry entry = adminArea.getEntry(fileName, true);
    
                SVNStatusType mergeResult = adminArea.mergeText(fileName, adminArea.getFile(leftPath),
                        adminArea.getFile(rightPath), null, targetLabel, leftLabel, rightLabel, null, false, 
                        null, null);
    
                if (props.getPropertyValue(SVNProperty.EXECUTABLE) != null) {
                    SVNFileUtil.setExecutable(target, true);
                }
                if (props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null
                        && entry.getLockToken() == null) {
                    SVNFileUtil.setReadonly(target, true);
                }
                setEntriesChanged(mergeResult == SVNStatusType.CONFLICTED || 
                        mergeResult == SVNStatusType.CONFLICTED_UNRESOLVED);
            } catch (SVNException svne) {
                error = svne;
                if (myIsRerun && (svne.getCause() instanceof FileNotFoundException)) {
                    error = null;
                }
                    
            }
        } else if (SVNLog.COMMIT.equals(name)) {
            try {
                SVNErrorCode code = count <= 1 ? SVNErrorCode.WC_BAD_ADM_LOG_START : SVNErrorCode.WC_BAD_ADM_LOG;
                if (attributes.getStringValue(SVNLog.REVISION_ATTR) == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(code, "Missing revision attribute for ''{0}''", fileName);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                
                SVNEntry entry = adminArea.getEntry(fileName, true);
                if (myIsRerun && (entry == null || (entry.isScheduledForDeletion() && entry.isDeleted()))) {
                    // skip without an error
                } else {
                    if (entry == null || (!adminArea.getThisDirName().equals(fileName) && entry.getKind() != SVNNodeKind.FILE)) {
                        SVNErrorMessage err = SVNErrorMessage.create(code, "Log command for directory ''{0}'' is mislocated", adminArea.getRoot()); 
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                    boolean implicit = attributes.getStringValue("implicit") != null && entry.isCopied();
                    setEntriesChanged(true);
                    long revisionNumber = -1;
                    try {
                        revisionNumber = Long.parseLong(attributes.getStringValue(SVNLog.REVISION_ATTR));
                    } catch (NumberFormatException nfe) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.WC_BAD_ADM_LOG, nfe), SVNLogType.WC);
                    }
                    adminArea.postCommit(fileName, revisionNumber, implicit, myIsRerun, code);
                }
            } catch (SVNException svne) {
                error = svne;
            }
        } else {
            SVNErrorCode code = count <= 1 ? SVNErrorCode.WC_BAD_ADM_LOG_START : SVNErrorCode.WC_BAD_ADM_LOG;
            SVNErrorMessage err = SVNErrorMessage.create(code, "Unrecognized logfile element ''{0}'' in ''{1}''", new Object[]{name, adminArea.getRoot()});
            SVNErrorManager.error(err.wrap("In directory ''{0}''", adminArea.getRoot()), SVNLogType.WC);
        }

        myLogCount = count;
        
        if (error != null) {
            SVNErrorCode code = count <= 1 ? SVNErrorCode.WC_BAD_ADM_LOG_START : SVNErrorCode.WC_BAD_ADM_LOG;
            SVNErrorMessage err = SVNErrorMessage.create(code, "Error processing command ''{0}'' in ''{1}''", new Object[]{name, adminArea.getRoot()});
            SVNErrorManager.error(err, error, SVNLogType.WC);
        }
    }

    private void setTreeConflictsAdded(boolean added) {
        myHasAddedTreeConflicts |= added;
    }

    private void setEntriesChanged(boolean modified) {
        myIsEntriesChanged |= modified;
    }
    
    private void setWCPropertiesChanged(boolean modified) {
        myIsWCPropertiesChanged |= modified;
    }

    private void saveTreeConflicts(SVNAdminArea adminArea) throws SVNException {
        Map attributes = new SVNHashMap();
        String conflictData = SVNTreeConflictUtil.getTreeConflictData(getTreeConflicts());
        attributes.put(SVNProperty.TREE_CONFLICT_DATA, conflictData);
        try {
            adminArea.modifyEntry(adminArea.getThisDirName(), attributes, false, false);
        } catch (SVNException e) {
            SVNErrorCode errorCode = myLogCount <= 1 ? SVNErrorCode.WC_BAD_ADM_LOG_START : SVNErrorCode.WC_BAD_ADM_LOG;
            SVNErrorMessage error = SVNErrorMessage.create(errorCode, "Error recording tree conflicts in ''{0}''", adminArea.getRoot());
            SVNErrorManager.error(error, e, SVNLogType.WC);
        }
        myIsEntriesChanged = true;
    }

    public void logStarted(SVNAdminArea adminArea) throws SVNException {
        SVNEntry dirEntry = adminArea.getEntry(adminArea.getThisDirName(), false);
        Map currentConflicts = dirEntry.getTreeConflicts();
        if (currentConflicts != null) {
            getTreeConflicts().putAll(currentConflicts);
        }
        myHasAddedTreeConflicts = false;
        myLogCount = 0;
    }

    public void logFailed(SVNAdminArea adminArea) throws SVNException {
        if (myHasAddedTreeConflicts) {
            saveTreeConflicts(adminArea);
        }
        if (myIsWCPropertiesChanged) {
            adminArea.saveWCProperties(true);
        } else {
            adminArea.closeWCProperties();
        }
        if (myIsEntriesChanged) {
            adminArea.saveEntries(false);
        } else {
            adminArea.closeEntries();
        }
        myLogCount = 0;
    }

    public void logCompleted(SVNAdminArea adminArea) throws SVNException {
        if (myHasAddedTreeConflicts) {
            saveTreeConflicts(adminArea);            
        }
        if (myIsWCPropertiesChanged) {
            adminArea.saveWCProperties(true);
        } 
        if (myIsEntriesChanged) {
            adminArea.saveEntries(false);
        }

        adminArea.handleKillMe();
        myIsEntriesChanged = false;
        myIsWCPropertiesChanged = false;
        myHasAddedTreeConflicts = false;
        myLogCount = 0;
    }

}
