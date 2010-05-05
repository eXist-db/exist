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
package org.exist.versioning.svn.wc.xml;

import java.io.File;
import java.util.Date;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;


/**
 * This is an implementation of the <b>ISVNAnnotateHandler</b> interface 
 * that writes XML formatted annotation information to a specified 
 * <b>ContentHandler</b>. 
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNXMLAnnotateHandler extends AbstractXMLHandler implements ISVNAnnotateHandler {

    /**
     * <code>'path'</code> attribute.
     */
    public static final String PATH_ATTR = "path";
    /**
     * <code>'revision'</code> attribute.
     */
    public static final String REVISION_ATTR = "revision";

    /**
     * <code>'date'</code> tag.
     */
    public static final String DATE_TAG = "date";

    /**
     * <code>'author'</code> tag.
     */
    public static final String AUTHOR_TAG = "author";

    /**
     * <code>'commit'</code> tag.
     */
    public static final String COMMIT_TAG = "commit";

    /**
     * <code>'entry'</code> tag.
     */
    public static final String ENTRY_TAG = "entry";

    /**
     * <code>'line-number'</code> tag.
     */
    public static final String LINE_NUMBER_TAG = "line-number";

    /**
     * <code>'target'</code> tag.
     */
    public static final String TARGET_TAG = "target";

    /**
     * <code>'blame'</code> tag.
     */
    public static final String BLAME_TAG = "blame";

    /**
     * <code>'merged'</code> tag.
     */
    public static final String MERGED_TAG = "merged";
    
    private long myLineNumber;
    private boolean myIsUseMergeHistory;
    
    /**
     * Creates a new annotation handler.
     * 
     * @param contentHandler a <b>ContentHandler</b> to form 
     *                       an XML tree
     */
    public SVNXMLAnnotateHandler(ContentHandler contentHandler) {
        this(contentHandler, null);
    }

    /**
     * Creates a new annotation handler.
     * 
     * @param contentHandler a <b>ContentHandler</b> to form 
     *                       an XML tree
     * @param log            a debug logger
     */
    public SVNXMLAnnotateHandler(ContentHandler contentHandler, ISVNDebugLog log) {
        this(contentHandler, log, false);
    }

    /**
     * Creates a new annotation handler.
     * 
     * @param contentHandler     a <b>ContentHandler</b> to form 
     *                           an XML tree
     * @param log                a debug logger
     * @param isUseMergeHistory  whether merge history should be taken into account or not
     */
    public SVNXMLAnnotateHandler(ContentHandler contentHandler, ISVNDebugLog log, boolean isUseMergeHistory) {
        super(contentHandler, log);
        myIsUseMergeHistory = isUseMergeHistory;
    }

    protected String getHeaderName() {
        return BLAME_TAG;
    }
    
    /**
     * Begins an XML tree with the target path/URL for which 
     * annotating is run.
     *  
     * @param pathOrURL a target file WC path or URL 
     */
    public void startTarget(String pathOrURL) {
        myLineNumber = 1;
        try {
            addAttribute(PATH_ATTR, pathOrURL);
            openTag(TARGET_TAG);
        } catch (SAXException e) {
            getDebugLog().logSevere(SVNLogType.DEFAULT, e);
        }
    }
    
    /**
     * Closes the formatted XML output. 
     *
     */
    public void endTarget() {
        myLineNumber = 1;
        try {
            closeTag(TARGET_TAG);
        } catch (SAXException e) {
            getDebugLog().logSevere(SVNLogType.DEFAULT, e);
        }
    }

    /**
     * Handles line annotation producing corresponding xml tags.
     * 
     * @param date 
     * @param revision 
     * @param author 
     * @param line 
     * @throws SVNException 
     */
    public void handleLine(Date date, long revision, String author, String line) throws SVNException {
        try {
            addAttribute(LINE_NUMBER_TAG, myLineNumber + "");
            openTag(ENTRY_TAG);
            if (revision >= 0) {
                addAttribute(REVISION_ATTR, revision + "");
                openTag(COMMIT_TAG);
                addTag(AUTHOR_TAG, author);
                addTag(DATE_TAG, SVNDate.formatDate(date));
                closeTag(COMMIT_TAG);
            }
            closeTag(ENTRY_TAG);
        } catch (SAXException e) {
            getDebugLog().logSevere(SVNLogType.DEFAULT, e);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.XML_MALFORMED, e.getLocalizedMessage());
            SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
        } finally {
            myLineNumber++;
        }
    }

    /**
     * Handles line annotation producing corresponding xml tags.
     * 
     * @param date 
     * @param revision 
     * @param author 
     * @param line 
     * @param mergedDate 
     * @param mergedRevision 
     * @param mergedAuthor 
     * @param mergedPath 
     * @param lineNumber 
     * @throws SVNException 
     */
    public void handleLine(Date date, long revision, String author, String line, 
                           Date mergedDate, long mergedRevision, String mergedAuthor, 
                           String mergedPath, int lineNumber) throws SVNException {
        try {
            addAttribute(LINE_NUMBER_TAG, ++lineNumber + "");
            openTag(ENTRY_TAG);
            if (revision >= 0) {
                addAttribute(REVISION_ATTR, revision + "");
                openTag(COMMIT_TAG);
                addTag(AUTHOR_TAG, author);
                addTag(DATE_TAG, SVNDate.formatDate(date));
                closeTag(COMMIT_TAG);
            }
            if (myIsUseMergeHistory && mergedRevision >= 0) {
                addAttribute(PATH_ATTR, mergedPath);
                openTag(MERGED_TAG);
                addAttribute(REVISION_ATTR, mergedRevision + "");
                openTag(COMMIT_TAG);
                addTag(AUTHOR_TAG, mergedAuthor);
                addTag(DATE_TAG, SVNDate.formatDate(mergedDate));
                closeTag(COMMIT_TAG);
                closeTag(MERGED_TAG);
            }
            closeTag(ENTRY_TAG);
        } catch (SAXException e) {
            getDebugLog().logSevere(SVNLogType.DEFAULT, e);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.XML_MALFORMED, e.getLocalizedMessage());
            SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
        } 
    }

    /**
     * Just returns <span class="javakeyword">false</span>.
     * @param date 
     * @param revision 
     * @param author 
     * @param contents 
     * @return               <span class="javakeyword">false</span>
     * @throws SVNException 
     */
    public boolean handleRevision(Date date, long revision, String author, File contents) throws SVNException {
        return false;
    }

    /**
     * Does nothing.
     */
    public void handleEOF() {
    }
}