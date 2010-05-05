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
package org.exist.versioning.svn;

import java.io.File;
import java.util.Date;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class Annotations {
    
    public static void main (String[] args) {
        //1. first initialize the DAV protocol
        DAVRepositoryFactory.setup();

        try {
            //we will annotate a publicly available file
            SVNURL fileURL = SVNURL.parseURIEncoded("https://svn.svnkit.com/repos/svnkit/trunk/changelog.txt");

            //SVNLogClient is the class with which you can perform annotations 
            SVNLogClient logClient = SVNClientManager.newInstance().getLogClient();
            boolean ignoreMimeType = false;
            boolean includeMergedRevisions = false;
            
            logClient.doAnnotate(fileURL, SVNRevision.UNDEFINED, SVNRevision.create(1), SVNRevision.HEAD, 
                    ignoreMimeType /*not ignoring mime type*/, includeMergedRevisions /*not including merged revisions */, 
                    new AnnotationHandler(includeMergedRevisions, false/*use a short form of inline information*/, 
                            logClient.getOptions()), null);
        } catch (SVNException svne) {
            System.out.println(svne.getMessage());
            System.exit(1);
        }
    }

    private static class AnnotationHandler implements ISVNAnnotateHandler {
        private boolean myIsUseMergeHistory;
        private boolean myIsVerbose;
        private ISVNOptions myOptions;
        
        public AnnotationHandler(boolean useMergeHistory, boolean verbose, ISVNOptions options) {
            myIsUseMergeHistory = useMergeHistory;
            myIsVerbose = verbose;
            myOptions = options;
        }
        
        /**
         * Deprecated.
         */
        public void handleLine(Date date, long revision, String author, String line) throws SVNException {
            handleLine(date, revision, author, line, null, -1, null, null, 0);
        }

        /**
         * Formats per line information and prints it out to the console.
         */
        public void handleLine(Date date, long revision, String author, String line, Date mergedDate, 
                long mergedRevision, String mergedAuthor, String mergedPath, int lineNumber) throws SVNException {
            
            String mergedStr = "";
            if(myIsUseMergeHistory) {
                if (revision != mergedRevision) {
                    mergedStr = "G ";
                } else {
                    mergedStr = "  ";
                }

                date = mergedDate;
                revision = mergedRevision;
                author = mergedAuthor;
            } 
               
            String revStr = revision >= 0 ? SVNFormatUtil.formatString(Long.toString(revision), 6, false) : "     -";
            String authorStr = author != null ? SVNFormatUtil.formatString(author, 10, false) : "         -";
            if (myIsVerbose) {
                String dateStr = "                                           -"; 
                if (date != null) {
                    dateStr = SVNDate.formatHumanDate(date, myOptions);
                }

                System.out.print(mergedStr + revStr + " " + authorStr + " " + dateStr + " ");
                if (myIsUseMergeHistory && mergedPath != null) {
                    String pathStr = SVNFormatUtil.formatString(mergedPath, 14, true);
                    System.out.print(pathStr + " ");
                }
                System.out.println(line);
            } else {
                System.out.println(mergedStr + revStr + " " + authorStr + " " + line);
            }
        }

        public boolean handleRevision(Date date, long revision, String author, File contents) throws SVNException {
            /* We do not want our file to be annotated for each revision of the range, but only for the last 
             * revision of it, so we return false  
             */
            return false;
        }

        public void handleEOF() {
        }
        
    }
}
