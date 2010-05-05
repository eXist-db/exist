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

import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;


/**
 * This log handler implementation writes xml formatted information 
 * about the log entries it's passed to a specified <b>ContentHandler</b>. 
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNXMLLogHandler extends AbstractXMLHandler implements ISVNLogEntryHandler {
    /**
     * <code>'copyfrom-rev'</code> attribute.
     */
    public static final String COPYFROM_REV_ATTR = "copyfrom-rev";

    /**
     * <code>'copyfrom-path'</code> attribute.
     */
    public static final String COPYFROM_PATH_ATTR = "copyfrom-path";

    /**
     * <code>'action'</code> attribute.
     */
    public static final String ACTION_ATTR = "action";

    /**
     * <code>'revision'</code> attribute.
     */
    public static final String REVISION_ATTR = "revision";

    /**
     * <code>'msg'</code> tag.
     */
    public static final String MSG_TAG = "msg";

    /**
     * <code>'path'</code> tag.
     */
    public static final String PATH_TAG = "path";

    /**
     * <code>'paths'</code> tag.
     */
    public static final String PATHS_TAG = "paths";
    
    /**
     * <code>'date'</code> tag.
     */
    public static final String DATE_TAG = "date";

    /**
     * <code>'author'</code> tag.
     */
    public static final String AUTHOR_TAG = "author";

    /**
     * <code>'logentry'</code> tag.
     */
    public static final String LOGENTRY_TAG = "logentry";

    /**
     * <code>'log'</code> tag.
     */
    public static final String LOG_TAG = "log";
    
    private boolean myIsOmitLogMessage;
    private LinkedList myMergeStack;

    /**
     * Creates a new log handler.
     * 
     * @param contentHandler a <b>ContentHandler</b> to form 
     *                       an XML tree
     */
    public SVNXMLLogHandler(ContentHandler contentHandler) {
        this(contentHandler, null);
    }

    /**
     * Creates a new log handler.
     * 
     * @param contentHandler a <b>ContentHandler</b> to form 
     *                       an XML tree
     * @param log            a debug logger
     */
    public SVNXMLLogHandler(ContentHandler contentHandler, ISVNDebugLog log) {
        super(contentHandler, log);
    }
    /**
     * Returns the header name specific for a log handler.
     * 
     * @return {@link #LOG_TAG} string
     */
    public String getHeaderName() {
        return LOG_TAG;
    }

    /**
     * Handles a next log entry producing corresponding xml.
     * 
     * @param  logEntry       log entry 
     * @throws SVNException 
     */
    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
        try {
            sendToHandler(logEntry);
        } catch (SAXException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.XML_MALFORMED, e.getLocalizedMessage());
            SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
        }
    }
    
    /**
     * Sets whether log messages must be omitted or not.
     * 
     * @param omitLogMessage  <span class="javakeyword">true</span> to omit; 
     *                        otherwise <span class="javakeyword">false</span> 
     */
    public void setOmitLogMessage(boolean omitLogMessage) {
        myIsOmitLogMessage = omitLogMessage;
    }
    
    private void sendToHandler(SVNLogEntry logEntry) throws SAXException {
        if (logEntry.getRevision() == 0 && logEntry.getMessage() == null) {
            return;
        }
        addAttribute(REVISION_ATTR, logEntry.getRevision() + "");
        openTag(LOGENTRY_TAG);
        if (logEntry.getAuthor() != null) {
            addTag(AUTHOR_TAG, logEntry.getAuthor());
        }
        if (logEntry.getDate() != null && logEntry.getDate().getTime() != 0) {
            addTag(DATE_TAG, SVNDate.formatDate(logEntry.getDate()));
        }
        if (logEntry.getChangedPaths() != null && !logEntry.getChangedPaths().isEmpty()) {
            openTag(PATHS_TAG);
            for (Iterator paths = logEntry.getChangedPaths().keySet().iterator(); paths.hasNext();) {
                String key = (String) paths.next();
                SVNLogEntryPath path = (SVNLogEntryPath) logEntry.getChangedPaths().get(key);
                addAttribute(ACTION_ATTR, path.getType() + "");
                if (path.getCopyPath() != null) {
                    addAttribute(COPYFROM_PATH_ATTR, path.getCopyPath());
                    addAttribute(COPYFROM_REV_ATTR, path.getCopyRevision() + "");
                } 
                addTag(PATH_TAG, path.getPath());
            }
            closeTag(PATHS_TAG);
        }
        
        if (!myIsOmitLogMessage) {
            String message = logEntry.getMessage();
            message = message == null ? "" : message;
            addTag(MSG_TAG, message);
        }
        
        if (myMergeStack != null && !myMergeStack.isEmpty()) {
            MergeFrame frame = (MergeFrame) myMergeStack.getLast();
            frame.myNumberOfChildrenRemaining--;
        }
        
        //TODO: FIXME
        if (logEntry.hasChildren()) {
            MergeFrame frame = new MergeFrame();
            //frame.myNumberOfChildrenRemaining = logEntry.getNumberOfChildren();
            if (myMergeStack == null) {
                myMergeStack = new LinkedList();
            }
            myMergeStack.addLast(frame);
        } else {
            while(myMergeStack != null && !myMergeStack.isEmpty()) {
                MergeFrame frame = (MergeFrame) myMergeStack.getLast();
                if (frame.myNumberOfChildrenRemaining == 0) {
                    closeTag(LOGENTRY_TAG);
                    myMergeStack.removeLast();
                } else {
                    break;
                }
            }
            closeTag(LOGENTRY_TAG);
        }
    }
    
    private class MergeFrame {
        private long myNumberOfChildrenRemaining;
    }

}
