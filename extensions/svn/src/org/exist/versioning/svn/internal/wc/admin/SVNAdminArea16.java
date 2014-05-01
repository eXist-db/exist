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
package org.exist.versioning.svn.internal.wc.admin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.SVNTreeConflictUtil;
import org.exist.versioning.svn.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminArea16 extends SVNAdminArea15 {

    public static final int WC_FORMAT = SVNAdminArea16Factory.WC_FORMAT;

    public SVNAdminArea16(File dir) {
        super(dir);
    }

    public boolean hasTreeConflict(String victimName) throws SVNException {
        return getTreeConflict(victimName) != null;
    }
    
    public SVNTreeConflictDescription getTreeConflict(String victimName) throws SVNException {
        SVNEntry dirEntry = getEntry(getThisDirName(), false);
        if (dirEntry == null) {
            return null;
        }
        Map conflicts = dirEntry.getTreeConflicts();
        return (SVNTreeConflictDescription) conflicts.get(getFile(victimName));
    }

    public void addTreeConflict(SVNTreeConflictDescription conflict) throws SVNException {
        SVNTreeConflictDescription existingDescription = getTreeConflict(conflict.getPath().getName());
        if (existingDescription != null) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Attempt to add tree conflict that already exists at ''{0}''", 
                    conflict.getPath());
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        Map conflicts = new SVNHashMap();
        conflicts.put(conflict.getPath(), conflict);
        String conflictData = SVNTreeConflictUtil.getTreeConflictData(conflicts);
        SVNProperties command = new SVNProperties();
        command.put(SVNLog.NAME_ATTR, getThisDirName());
        command.put(SVNLog.DATA_ATTR, conflictData);

        SVNLog log = getLog();
        log.addCommand(SVNLog.ADD_TREE_CONFLICT, command, false);
        log.save();
        runLogs();
    }

    public SVNTreeConflictDescription deleteTreeConflict(String victimName) throws SVNException {
        SVNEntry dirEntry = getEntry(getThisDirName(), false);
        Map conflicts = dirEntry.getTreeConflicts();
        File victimPath = getFile(victimName);
        if (conflicts.containsKey(victimPath)) {
            SVNTreeConflictDescription conflict = (SVNTreeConflictDescription) conflicts.remove(victimPath);
            String conflictData = SVNTreeConflictUtil.getTreeConflictData(conflicts);
            Map attributes = new SVNHashMap();
            attributes.put(SVNProperty.TREE_CONFLICT_DATA, conflictData);
            modifyEntry(getThisDirName(), attributes, true, false);
            return conflict;
        }
        return null;
    }
   
    public void setFileExternalLocation(String name, SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNURL reposRootURL) throws SVNException {
        Map attributes = new HashMap();
        if (url != null) {
            String strURL = url.toDecodedString();
            String reposRootStrURL = reposRootURL.toDecodedString();
            String path = strURL.substring(reposRootStrURL.length());
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            attributes.put(SVNProperty.FILE_EXTERNAL_PEG_REVISION, pegRevision);
            attributes.put(SVNProperty.FILE_EXTERNAL_REVISION, revision);
            attributes.put(SVNProperty.FILE_EXTERNAL_PATH, path);
        } else {
            attributes.put(SVNProperty.FILE_EXTERNAL_PEG_REVISION, SVNRevision.UNDEFINED);
            attributes.put(SVNProperty.FILE_EXTERNAL_REVISION, SVNRevision.UNDEFINED);
            attributes.put(SVNProperty.FILE_EXTERNAL_PATH, null);
        }
        modifyEntry(name, attributes, true, false);
    }

    public int getFormatVersion() {
        return SVNAdminArea16Factory.WC_FORMAT;
    }

    public void postUpgradeFormat(int format) throws SVNException {
        super.postUpgradeFormat(format);
        try {
            SVNFileUtil.deleteFile(getAdminFile("format"));
        } catch (SVNException svne) {
        }
    }

    protected void createFormatFile(File formatFile, boolean createMyself) throws SVNException {
        //does nothing since the working copy format v10
    }

    protected boolean readExtraOptions(BufferedReader reader, Map entryAttrs) throws SVNException, IOException {
        if (super.readExtraOptions(reader, entryAttrs)) {
            return true;
        }
        
        String line = reader.readLine();
        if (isEntryFinished(line)) {
            return true;
        }
        String treeConflictData = parseString(line);
        if (treeConflictData != null) {
            entryAttrs.put(SVNProperty.TREE_CONFLICT_DATA, treeConflictData);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return true;
        }
        String fileExternalData = parseString(line);
        if (fileExternalData != null) {
            unserializeExternalFileData(entryAttrs, fileExternalData);
        }
        
        return false;
    }

    protected int writeExtraOptions(Writer writer, String entryName, Map entryAttrs, int emptyFields) throws SVNException, IOException {
        emptyFields = super.writeExtraOptions(writer, entryName, entryAttrs, emptyFields);
        String treeConflictData = (String) entryAttrs.get(SVNProperty.TREE_CONFLICT_DATA); 
        if (writeString(writer, treeConflictData, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String serializedFileExternalData = serializeExternalFileData(entryAttrs);
        if (writeString(writer, serializedFileExternalData, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        return emptyFields;
    }

    private String serializeExternalFileData(Map entryAttrs) throws SVNException {
        String representation = null;
        String path = (String) entryAttrs.get(SVNProperty.FILE_EXTERNAL_PATH);
        SVNRevision revision = (SVNRevision) entryAttrs.get(SVNProperty.FILE_EXTERNAL_REVISION);
        SVNRevision pegRevision = (SVNRevision) entryAttrs.get(SVNProperty.FILE_EXTERNAL_PEG_REVISION);
        if (path != null) {
            String revStr = asString(revision, path);
            String pegRevStr = asString(pegRevision, path);
            representation = pegRevStr + ":" + revStr + ":" + path;
        }
        return representation;
    }
    
    private String asString(SVNRevision revision, String path) throws SVNException {
        if (revision == SVNRevision.HEAD || 
                SVNRevision.isValidRevisionNumber(revision.getNumber())) {
            return revision.toString();
        }
        
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, "Illegal file external revision kind {0} for path ''{1}''", 
        
                new Object[] { revision.toString(), path });
        SVNErrorManager.error(err, SVNLogType.WC);
        return null;
    }
    
    private void unserializeExternalFileData(Map entryAttrs, String rawExternalFileData) throws SVNException {
        SVNRevision pegRevision = SVNRevision.UNDEFINED;
        SVNRevision revision = SVNRevision.UNDEFINED;
        String path = null;
        if (rawExternalFileData != null) {
            StringBuffer buffer = new StringBuffer(rawExternalFileData);
            pegRevision = parseRevision(buffer);
            revision = parseRevision(buffer);
            path = buffer.toString();
        }
        entryAttrs.put(SVNProperty.FILE_EXTERNAL_PATH, path);
        entryAttrs.put(SVNProperty.FILE_EXTERNAL_REVISION, revision);
        entryAttrs.put(SVNProperty.FILE_EXTERNAL_PEG_REVISION, pegRevision);
    }
    
    private SVNRevision parseRevision(StringBuffer str) throws SVNException {
        int ind = str.indexOf(":"); 
        if ( ind == -1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
                    "Found an unexpected \\0 in the file external ''{0}''", str);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        SVNRevision revision = null;
        String subStr = str.substring(0, ind);
        if (subStr.equals(SVNRevision.HEAD.getName())) {
            revision = SVNRevision.HEAD;
        } else {
            revision = SVNRevision.parse(subStr);
        }
        str = str.delete(0, ind + 1);
        return revision;
    }

    protected boolean isEntryPropertyApplicable(String propName) {
        return propName != null;
    }
}
