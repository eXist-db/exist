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

import java.io.File;

import org.exist.util.io.Resource;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNPath {
    
    private boolean myHasPegRevision;
    private String myTarget;
    private SVNRevision myPegRevision = SVNRevision.UNDEFINED;
    private File myFile;

    public SVNPath(String target) throws SVNException {
        this(target, false);
    }

    public SVNPath(String target, boolean hasPegRevision) throws SVNException {
        myTarget = target;
        myHasPegRevision = hasPegRevision;
        if (myHasPegRevision) {
            parsePegRevision(true);
        } else {
            parsePegRevision(false);
        }
        myTarget = SVNPathUtil.canonicalizePath(myTarget);
        assertControlChars(isURL() ? SVNEncodingUtil.uriDecode(myTarget) : myTarget);
    }
    
    public String getTarget() {
        return myTarget;
    }

    public boolean isURL() {
        return SVNPathUtil.isURL(myTarget);
    }
    
    public boolean isFile() { 
        return !isURL();
    }
    
    public File getFile() {
        if (myFile != null) {
            return myFile;
        }
        if (isFile()) {            
            return new Resource(myTarget).getAbsoluteFile();
        }
        return null;
    }
    
    public SVNURL getURL() throws SVNException {
        if (isURL()) {
            return SVNURL.parseURIEncoded(myTarget);
        }
        return null;
    }
    
    public SVNRevision getPegRevision() {
        return myPegRevision;
    }

    private void parsePegRevision(boolean use) throws SVNException {
        int index = myTarget.lastIndexOf('@');
        if (index > 0) {
            String revStr = myTarget.substring(index + 1);
            if (revStr.indexOf('/') >= 0) {
                return;
            }
            if (revStr.length() == 0) {
                if (use) {
                    myPegRevision = isURL() ? SVNRevision.HEAD : SVNRevision.BASE;
                }
                myTarget = myTarget.substring(0, index);
                return;
            }
            if (isURL() && revStr.length() > 6 && 
                    revStr.toLowerCase().startsWith("%7b") && revStr.toLowerCase().endsWith("%7d")) {
                revStr = SVNEncodingUtil.uriDecode(revStr);
            }
            SVNRevision revision = SVNRevision.parse(revStr);
            if (revision != SVNRevision.UNDEFINED) {
                if (use) {
                    myPegRevision = revision;
                }
                myTarget = myTarget.substring(0, index);
                return;
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Syntax error parsing revision ''{0}''", myTarget.substring(index + 1));
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        } else if (index == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_FILENAME, 
                    "''{0}'' is just a peg revision. May be try ''{0}@'' instead?", myTarget);
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
    }

    protected static void assertControlChars(String path) throws SVNException {
        if (path != null) {
            for (int i = 0; i < path.length(); i++) {
                char ch = path.charAt(i);
                String code = Integer.toHexString(ch);
                if (code.length() < 2) {
                    code = "0" + code;
                }
                if (SVNEncodingUtil.isASCIIControlChar(ch)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_PATH_SYNTAX, "Invalid control character '0x" + code + "' in path '" + path + "'");
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                }
            }
        }
        return;
    }
}
