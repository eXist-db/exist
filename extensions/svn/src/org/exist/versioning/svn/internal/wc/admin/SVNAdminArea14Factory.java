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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNFileType;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminArea14Factory extends SVNAdminAreaFactory {

    public static final int WC_FORMAT = SVNAdminAreaFactory.WC_FORMAT_14;
    
    protected void doCreateVersionedDirectory(File path, String url, String rootURL, String uuid, long revNumber, SVNDepth depth) throws SVNException {
        SVNAdminArea adminArea = new SVNAdminArea14(path); 
        adminArea.createVersionedDirectory(path, url, rootURL, uuid, revNumber, true, depth);
    }

    protected SVNAdminArea doOpen(File path, int version) throws SVNException {
        if (version != getSupportedVersion()) {
            return null;
        }
        return new SVNAdminArea14(path);
    }

    protected SVNAdminArea doChangeWCFormat(SVNAdminArea adminArea) throws SVNException {
        if (adminArea == null || adminArea.getClass() == SVNAdminArea14.class) {
            return adminArea;
        }
        SVNAdminArea14 newestAdminArea = new SVNAdminArea14(adminArea.getRoot());
        newestAdminArea.setLocked(true);
        return newestAdminArea.formatWC(adminArea);
    }

    public int getSupportedVersion() {
        return WC_FORMAT;
    }

    protected int doCheckWC(File path, Level logLevel) throws SVNException {
        File adminDir = new Resource(path, SVNFileUtil.getAdminDirectoryName());
        File entriesFile = new Resource(adminDir, "entries");
        if (!entriesFile.exists()) {
        	return 0;
//            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {entriesFile, "This resource does not exist."});
//            throw new SVNException(err);
        }
        	
        int formatVersion = -1;

        BufferedReader reader = null;
        String line = null;
    
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(entriesFile, logLevel, SVNLogType.WC), "UTF-8"));
            line = reader.readLine();
        } catch (FileNotFoundException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {entriesFile, e.getLocalizedMessage()});
            throw new SVNException(err);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {entriesFile, e.getLocalizedMessage()});
            SVNErrorManager.error(err, e, SVNLogType.WC);
        } catch (SVNException svne) {
            SVNFileType type = SVNFileType.getType(path);
            if (type != SVNFileType.DIRECTORY || !entriesFile.exists()) { 
                if (type == SVNFileType.NONE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "''{0}'' does not exist", path);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                return 0;
            }
            throw svne;
        } finally {
            SVNFileUtil.closeFile(reader);
        }

        if (line == null || line.length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF, "Reading ''{0}''", entriesFile);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        try {
            formatVersion = Integer.parseInt(line.trim());
        } catch (NumberFormatException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, "First line of ''{0}'' contains non-digit", entriesFile);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return formatVersion;
    }
    
    protected int getVersion(File path) throws SVNException {
        File adminDir = new Resource(path, SVNFileUtil.getAdminDirectoryName());
        File entriesFile = new Resource(adminDir, "entries");
        int formatVersion = -1;

        BufferedReader reader = null;
        String line = null;
    
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(entriesFile, Level.FINEST, SVNLogType.WC), "UTF-8"));
            line = reader.readLine();
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {entriesFile, e.getLocalizedMessage()});
            SVNErrorManager.error(err, e, SVNLogType.WC);
        } catch (SVNException svne) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
            err.setChildErrorMessage(svne.getErrorMessage());
            SVNErrorManager.error(err, svne, Level.FINEST, SVNLogType.WC);
        } finally {
            SVNFileUtil.closeFile(reader);
        }
        
        if (line == null || line.length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF, "Reading ''{0}''", entriesFile);
            SVNErrorMessage err1 = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
            err1.setChildErrorMessage(err);
            SVNErrorManager.error(err1, Level.FINEST, SVNLogType.WC);
        }
        
        try {
            formatVersion = Integer.parseInt(line.trim());
        } catch (NumberFormatException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, "First line of ''{0}'' contains non-digit", entriesFile);
            SVNErrorMessage err1 = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
            err1.setChildErrorMessage(err);
            SVNErrorManager.error(err1, Level.FINEST, SVNLogType.WC);
        }
        return formatVersion;
    }
}
