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
import java.io.OutputStream;
import java.util.Map;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.admin.SVNTranslator;
import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.exist.versioning.svn.wc.ISVNOptions;
import org.exist.versioning.svn.wc.SVNEventAction;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNExportEditor implements ISVNEditor {

    private File myRoot;
    private boolean myIsForce;
    private String myEOLStyle;
    private File myCurrentDirectory;
    private File myCurrentFile;
    private File myCurrentTmpFile;
    private String myCurrentPath;
    private Map myExternals;
    private SVNProperties myFileProperties;
    private ISVNEventHandler myEventDispatcher;
    private String myURL;
    private ISVNOptions myOptions;
    
    private SVNDeltaProcessor myDeltaProcessor;
    private boolean myIsExpandKeywords;

    public SVNExportEditor(ISVNEventHandler eventDispatcher, String url,
            File dstPath, boolean force, String eolStyle, boolean expandKeywords, ISVNOptions options) {
        myRoot = dstPath;
        myIsForce = force;
        myEOLStyle = eolStyle;
        myExternals = new SVNHashMap();
        myEventDispatcher = eventDispatcher;
        myURL = url;
        myDeltaProcessor = new SVNDeltaProcessor();
        myOptions = options;
        myIsExpandKeywords = expandKeywords;
    }

    public Map getCollectedExternals() {
        return myExternals;
    }

    public void openRoot(long revision) throws SVNException {
        // create root if missing or delete (if force).
        addDir("", null, -1);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentDirectory = new Resource(myRoot, path);
        myCurrentPath = path;

        SVNFileType dirType = SVNFileType.getType(myCurrentDirectory);
        if (dirType == SVNFileType.FILE || dirType == SVNFileType.SYMLINK) {
            // export is obstructed.
            if (!myIsForce) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' exists and is not a directory", myCurrentDirectory);
                SVNErrorManager.error(err, SVNLogType.WC);
            } else {
                SVNFileUtil.deleteAll(myCurrentDirectory, myEventDispatcher);
            }
        } else if (dirType == SVNFileType.DIRECTORY && !myIsForce) {
            if (!"".equals(path)) {
                SVNErrorMessage err =  SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "''{0}'' already exists", myCurrentDirectory);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            File[] children = SVNFileListUtil.listFiles(myCurrentDirectory);
            if (children != null && children.length > 0) {
                SVNErrorMessage err =  SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "''{0}'' already exists", myCurrentDirectory);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        } else if (dirType == SVNFileType.NONE) {        
            if (!myCurrentDirectory.mkdirs()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "Cannot create directory ''{0}''", myCurrentDirectory);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        myEventDispatcher.handleEvent(SVNEventFactory.createSVNEvent(myCurrentDirectory, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.UPDATE_ADD, null, null, null), ISVNEventHandler.UNKNOWN);
    }

    public void changeDirProperty(String name, SVNPropertyValue value)
            throws SVNException {
        if (SVNProperty.EXTERNALS.equals(name) && value != null) {
            myExternals.put(myCurrentPath, value.getString());
        }
    }

    public void closeDir() throws SVNException {
        myCurrentDirectory = myCurrentDirectory.getParentFile();
        myCurrentPath = SVNPathUtil.removeTail(myCurrentPath);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        File file = new Resource(myRoot, path);

        if (!myIsForce && file.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "File ''{0}'' already exists", file);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        myCurrentFile = file;
        myFileProperties = new SVNProperties();
        myChecksum = null;
    }

    public void changeFileProperty(String commitPath, String name, SVNPropertyValue value)
            throws SVNException {
        myFileProperties.put(name, value);
    }

    public void applyTextDelta(String commitPath, String baseChecksum) throws SVNException {
        String name = SVNPathUtil.tail(commitPath);
        myCurrentTmpFile = SVNFileUtil.createUniqueFile(myCurrentDirectory, name, ".tmp", false);
        myDeltaProcessor.applyTextDelta((File)null, myCurrentTmpFile, true);
    }

    public OutputStream textDeltaChunk(String commitPath, SVNDiffWindow diffWindow) throws SVNException {
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }
    
    private String myChecksum;

    public void textDeltaEnd(String commitPath) throws SVNException {
        myChecksum = myDeltaProcessor.textDeltaEnd();
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
        if (textChecksum == null) {
            textChecksum = myFileProperties.getStringValue(SVNProperty.CHECKSUM);
        }
        if (myIsForce) {
            myCurrentFile.delete();
        }
        String realChecksum = myChecksum != null ? myChecksum : SVNFileUtil.computeChecksum(myCurrentTmpFile);
        myChecksum = null;
        if (textChecksum != null && !textChecksum.equals(realChecksum)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH, "Checksum mismatch for ''{0}''; expected: ''{1}'', actual: ''{2}''",
                    new Object[] {myCurrentFile, textChecksum, realChecksum}); 
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        // retranslate.
        if (!myIsExpandKeywords) {
            myFileProperties.put(SVNProperty.MIME_TYPE, "application/octet-stream");
        }
        try {
            String date = myFileProperties.getStringValue(SVNProperty.COMMITTED_DATE);
            boolean special = myFileProperties.getStringValue(SVNProperty.SPECIAL) != null;
            boolean binary = SVNProperty.isBinaryMimeType(myFileProperties.getStringValue(SVNProperty.MIME_TYPE));
            String keywords = myFileProperties.getStringValue(SVNProperty.KEYWORDS);
            Map keywordsMap = null;
            if (keywords != null) {
                String url = SVNPathUtil.append(myURL, SVNEncodingUtil.uriEncode(myCurrentPath));
                url = SVNPathUtil.append(url, SVNEncodingUtil.uriEncode(myCurrentFile.getName()));
                String author = myFileProperties.getStringValue(SVNProperty.LAST_AUTHOR);
                String revStr = myFileProperties.getStringValue(SVNProperty.COMMITTED_REVISION);
                keywordsMap = SVNTranslator.computeKeywords(keywords, url, author, date, revStr, myOptions);
            }
            String charset = SVNTranslator.getCharset(myFileProperties.getStringValue(SVNProperty.CHARSET), myCurrentFile.getPath(), myOptions);
            byte[] eolBytes = null;
            if (SVNProperty.EOL_STYLE_NATIVE.equals(myFileProperties.getStringValue(SVNProperty.EOL_STYLE))) {
                eolBytes = SVNTranslator.getEOL(myEOLStyle != null ? myEOLStyle : myFileProperties.getStringValue(SVNProperty.EOL_STYLE), myOptions);
            } else if (myFileProperties.containsName(SVNProperty.EOL_STYLE)) {
                eolBytes = SVNTranslator.getEOL(myFileProperties.getStringValue(SVNProperty.EOL_STYLE), myOptions);
            }
            if (binary) {
                // no translation unless 'special'.
                charset = null;
                eolBytes = null;
                keywordsMap = null;
            }
            if (charset != null || eolBytes != null || (keywordsMap != null && !keywordsMap.isEmpty()) || special) {
                SVNTranslator.translate(myCurrentTmpFile, myCurrentFile, charset, eolBytes, keywordsMap, special, true);
            } else {
                SVNFileUtil.rename(myCurrentTmpFile, myCurrentFile);
            }
            boolean executable = myFileProperties.getStringValue(SVNProperty.EXECUTABLE) != null;
            if (executable) {
                SVNFileUtil.setExecutable(myCurrentFile, true);
            }
            if (!special && date != null) {
                myCurrentFile.setLastModified(SVNDate.parseDate(date).getTime());
            }
            myEventDispatcher.handleEvent(SVNEventFactory.createSVNEvent(myCurrentFile, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNEventAction.UPDATE_ADD, null, null, null), ISVNEventHandler.UNKNOWN);
        } finally {
            myCurrentTmpFile.delete();
        }
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void targetRevision(long revision) throws SVNException {
    }

    public void deleteEntry(String path, long revision) throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void openDir(String path, long revision) throws SVNException {
    }

    public void openFile(String path, long revision) throws SVNException {
    }

    public void abortEdit() throws SVNException {
    }
}