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

import org.exist.versioning.svn.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.io.fs.CountingOutputStream;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionRoot;
import org.tmatesoft.svn.core.wc.admin.ISVNGNUDiffGenerator;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class DefaultSVNGNUDiffGenerator extends DefaultSVNDiffGenerator implements ISVNGNUDiffGenerator {

    private String myHeader;
    private boolean myIsHeaderWritten;
    private FSRoot myOriginalRoot;
    private String myOriginalPath;
    private FSRoot myNewRoot;
    private String myNewPath;
    private boolean myIsDiffWritten;

    public void displayHeader(int type, String path, String copyFromPath, long copyFromRevision, OutputStream result) throws SVNException {
        switch (type) {
            case ADDED:
                if (!myIsHeaderWritten) {
                    path = path.startsWith("/") ? path.substring(1) : path;
                    myHeader = "Added: " + path;
                }
                break;
            case DELETED:
                if (!myIsHeaderWritten) {
                    path = path.startsWith("/") ? path.substring(1) : path;
                    myHeader = "Deleted: " + path;
                }
                break;
            case MODIFIED:
                if (!myIsHeaderWritten) {
                    path = path.startsWith("/") ? path.substring(1) : path;
                    myHeader = "Modified: " + path;
                }
                break;
            case COPIED:
                if (!myIsHeaderWritten) {
                    path = path.startsWith("/") ? path.substring(1) : path;
                    copyFromPath = copyFromPath.startsWith("/") ? copyFromPath.substring(1) : copyFromPath;
                    myHeader = "Copied: " + path + " (from rev " + copyFromRevision + ", " + copyFromPath + ")";
                }
                break;
            case NO_DIFF:
                try {
                    result.write(getEOL());
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                    SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
                }
                break;
        }
    }

    public void displayFileDiff(String path, File file1, File file2,
            String rev1, String rev2, String mimeType1, String mimeType2, OutputStream result) throws SVNException {
        CountingOutputStream counitngStream = new CountingOutputStream(result, 0);
        super.displayFileDiff(path, file1, file2, rev1, rev2, mimeType1, mimeType2, counitngStream);
        if (counitngStream.getPosition() > 0) {
            try {
                result.write(getEOL());
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
            }
        }
        setDiffWritten(counitngStream.getPosition() > 0);
    }

    public void setHeaderWritten(boolean written) {
        myIsHeaderWritten = written;
    }
    
    protected boolean displayHeader(OutputStream os, String path, boolean deleted) throws IOException {
        if (myHeader != null) {
            os.write(myHeader.getBytes(getEncoding())); 
            os.write(getEOL());
            myHeader = null;
            myIsHeaderWritten = true;
        } else if (!myIsHeaderWritten) {
            path = path.startsWith("/") ? path.substring(1) : path;
            String header = "Index: " + path;
            os.write(header.getBytes(getEncoding())); 
            os.write(getEOL());
            myIsHeaderWritten = true;
        }
        os.write(HEADER_SEPARATOR);
        os.write(getEOL());
        return false;
    }
    
    protected void displayBinary(OutputStream os, String mimeType1, String mimeType2) throws IOException {
        os.write("(Binary files differ)".getBytes(getEncoding()));
        os.write(getEOL());
    }

    protected void displayHeaderFields(OutputStream os, String label1, String label2) throws IOException {
        os.write("--- ".getBytes(getEncoding()));
        String originalLabel = null;
        String newLabel = null;
        try {
            originalLabel = generateLabel(myOriginalRoot, myOriginalPath); 
            newLabel = generateLabel(myNewRoot, myNewPath);
        } catch (SVNException svne) {
            throw new IOException(svne.getLocalizedMessage());
        }
        os.write(originalLabel.getBytes(getEncoding()));
        os.write(getEOL());
        os.write("+++ ".getBytes(getEncoding()));
        os.write(newLabel.getBytes(getEncoding()));
        os.write(getEOL());
    }

    protected void setOriginalFile(FSRoot originalRoot, String originalPath) {
        myOriginalRoot = originalRoot;
        myOriginalPath = originalPath;
    }

    protected void setNewFile(FSRoot newRoot, String newPath) {
        myNewRoot = newRoot;
        myNewPath = newPath;
    }

    private String generateLabel(FSRoot root, String path) throws SVNException {
        String date = null;
        String txnName = null;
        long rev = 0;
        if (root != null) {
            FSFS fsfs = root.getOwner();
            SVNProperties props = null;
            if (root instanceof FSRevisionRoot) {
                FSRevisionRoot revisionRoot = (FSRevisionRoot) root;
                rev = revisionRoot.getRevision();
                props = fsfs.getRevisionProperties(rev);
            } else {
                FSTransactionRoot txnRoot = (FSTransactionRoot) root;
                txnName = txnRoot.getTxnID();
                props = fsfs.getTransactionProperties(txnName);
            }
            date = props.getStringValue(SVNRevisionProperty.DATE);
        } 
        
        String dateString = null;
        if (date != null) {
            int tInd = date.indexOf('T');
            dateString = date.substring(0, tInd) + " " + date.substring(tInd + 1, tInd + 9) + " UTC";
            
        } else {
            dateString = "                       ";
        }
        
        if (txnName != null) {
            return path + '\t' + dateString + " (txn " + txnName + ")";
        }
        return path + '\t' + dateString + " (rev " + rev + ")";
    }
    
    protected boolean useLocalFileSeparatorChar() {
        return false;
    }

    public void setDiffWritten(boolean b) {
        myIsDiffWritten = b;
    }
    
    public boolean isDiffWritten() {
        return myIsDiffWritten;
    }
    
    public void printHeader(OutputStream os) throws SVNException {
        if (myHeader != null) {
            try {
                displayHeader(os, null, false);
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
            }
        }
    }

}
