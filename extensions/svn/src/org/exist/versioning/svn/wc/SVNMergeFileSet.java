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
package org.exist.versioning.svn.wc;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.exist.versioning.svn.internal.wc.SVNAdminUtil;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;


/**
 * The <b>SVNMergeFileSet</b> class holds information about the file that is to be merged.
 * This information includes references to <code>File</code> objects with working, base, repository contents; 
 * file mimeType; labels to append to the file name to produce conflict files in case a merge fails with a 
 * conflict, and so on.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNMergeFileSet {
    
    private String myLocalFilePath;
    private String myBaseFilePath;
    private String myRepositoryFilePath;
    private String myWCFilePath;
    private String myMergeResultFilePath;
    
    private String myMimeType;
    private SVNAdminArea myAdminArea;
    private SVNLog myLog;
    
    private String myLocalLabel;
    private String myBaseLabel;
    private String myRepositoryLabel;

    private File myLocalFile;
    private File myBaseFile;
    private File myRepositoryFile;
    private File myMergeResultFile;
    private File myCopyFromFile;
    
    private Collection myTmpPaths = new ArrayList();

    /**
     * Creates a new <code>SVNMergeFileSet</code> object given the data prepared for 
     * merging a file.
     * 
     * <p/>
     * Note: This is intended for internal use only, not for API users.
     * 
     * @param adminArea     admin area the file is controlled under 
     * @param log           log object
     * @param baseFile      file with pristine contents
     * @param localFile     file with translated working contents
     * @param wcPath        working copy path relative to the location of <code>adminArea</code>
     * @param reposFile     file contents from the repository
     * @param resultFile    file where the resultant merged contents will be written to  
     * @param copyFromFile  contents of the copy source file (if any)  
     * @param mimeType      file mime type       
     */
    public SVNMergeFileSet(SVNAdminArea adminArea, SVNLog log,
            File baseFile, 
            File localFile, 
            String wcPath, 
            File reposFile, 
            File resultFile,
            File copyFromFile,
            String mimeType) {
        myAdminArea = adminArea;
        myLog = log;
        myLocalFile = localFile;
        myBaseFile = baseFile;
        myRepositoryFile = reposFile;
        myWCFilePath = wcPath;
        myMergeResultFile = resultFile;
        myCopyFromFile = copyFromFile;
        myMimeType = mimeType;
        
        if (myBaseFile != null) {
            myBaseFilePath = SVNPathUtil.isAncestor(myAdminArea.getAdminDirectory().getAbsolutePath(), 
                    myBaseFile.getAbsolutePath()) ? SVNFileUtil.getBasePath(myBaseFile) : null;
        }
        if (myLocalFile != null) {
            myLocalFilePath = SVNFileUtil.getBasePath(myLocalFile);
        }
        if (myRepositoryFile != null) {
            myRepositoryFilePath = SVNPathUtil.isAncestor(myAdminArea.getAdminDirectory().getAbsolutePath(), 
                    myRepositoryFile.getAbsolutePath()) ? SVNFileUtil.getBasePath(myRepositoryFile) : null;
        }
        if (myMergeResultFile != null) {
            myMergeResultFilePath = SVNFileUtil.getBasePath(myMergeResultFile);
        }
    }
    
    /**
     * Sets the labels for conflict files.
     * 
     * <p/>
     * If <code>baseLabel</code> is <span class="javakeyword">null</span>, 
     * <span class="javastring">".old"</span> will be set by default. 
     * If <code>localLabel</code> is <span class="javakeyword">null</span>, 
     * <span class="javastring">".working"</span> will be set by default. 
     * If <code>repositoryLabel</code> is <span class="javakeyword">null</span>, 
     * <span class="javastring">".new"</span> will be set by default. 
     * 
     * @param baseLabel          base file label 
     * @param localLabel         working file label
     * @param repositoryLabel    repository file label
     */
    public void setMergeLabels(String baseLabel, String localLabel, String repositoryLabel) {
        myLocalLabel = localLabel == null ? ".working" : localLabel;
        myBaseLabel = baseLabel == null ? ".old" : baseLabel;
        myRepositoryLabel = repositoryLabel == null ? ".new" : repositoryLabel;
    }

    /**
     * Returns the log object.
     * 
     * <p/>
     * Note: This is intended for internal use only, not for API users.
     * 
     * @return wc modification commands logger 
     */
    public SVNLog getLog() {
        return myLog;
    }
    
    /**
     * Returns the base file label.
     * 
     * @return base label string 
     */
    public String getBaseLabel() {
        return myBaseLabel;
    }
    
    /**
     * Returns the local file label.
     * 
     * @return working file label 
     */
    public String getLocalLabel() {
        return myLocalLabel;
    }

    /**
     * Returns the repository file label.
     * 
     * @return label of the repository file version 
     */
    public String getRepositoryLabel() {
        return myRepositoryLabel;
    }
    
    /**
     * Returns the base file path.
     * 
     * <p/>
     * If the {@link #getBaseFile() base file} is located under the 
     * {@link #getAdminArea() admin area}, then the return path will be just a relevant to the admin area path 
     * of the base file. Otherwise (in case the repository file is located not under the admin area) this 
     * method will create a temporary file in the <code>.svn/tmp</code> area of the admin area and copy the 
     * contents of the base file into it; the return path will be again relative to the location of 
     * the admin area.  
     * 
     * @return               path of the file with pristine contents
     * @throws SVNException 
     */
    public String getBasePath() throws SVNException {
        if (myBaseFilePath == null && myBaseFile != null) {
            File tmp = SVNAdminUtil.createTmpFile(myAdminArea);
            SVNFileUtil.copyFile(myBaseFile, tmp, false);
            myBaseFilePath = SVNFileUtil.getBasePath(tmp);
            myTmpPaths.add(myBaseFilePath);
        }
        return myBaseFilePath;
    }
    
    /**
     * Returns the path of the detranslated version of the working copy file.
     * Detranslating of a working copy file takes place in case it's a symlink, or it has keywords or 
     * eol-style properties set on it.
     * 
     * @return path to the file with detranslated working contents; it's relevant to the 
     *         {@link #getAdminArea() admin area} location
     */
    public String getLocalPath() {
        return myLocalFilePath;
    }
    
    /**
     * Returns the path of the working copy file.
     * 
     * @return path of the working copy file; it's relevant to the {@link #getAdminArea() admin area} location
     */
    public String getWCPath() {
        return myWCFilePath;
    }
    
    /**
     * Returns the path to the file containing the contents of the repository version of the file.
     *
     * <p/>
     * If the {@link #getRepositoryFile() repository file} is located under the 
     * {@link #getAdminArea() admin area}, then the return path will be just a relevant to the admin area path 
     * of the repository file. Otherwise (in case the repository file is located not under the admin area) this 
     * method will create a temporary file in the <code>.svn/tmp</code> area of the admin area and copy the 
     * contents of the repository file into it; the return path will be again relative to the location of 
     * the admin area.  
     * 
     * @return                path of the file containing file contents that come from the repository  
     * @throws SVNException 
     */
    public String getRepositoryPath() throws SVNException {
        if (myRepositoryFilePath == null && myRepositoryFile != null) {
            File tmp = SVNAdminUtil.createTmpFile(myAdminArea);
            SVNFileUtil.copyFile(myRepositoryFile, tmp, false);
            myRepositoryFilePath = SVNFileUtil.getBasePath(tmp);
            myTmpPaths.add(myRepositoryFilePath);
        }
        return myRepositoryFilePath;
    }
    
    /**
     * Returns the path of the file where the merged resultant text is written to.
     * 
     * @return path of the result file; it's relevant to the {@link #getAdminArea() admin area} location
     */
    public String getResultPath() {
        return myMergeResultFilePath;
    }
    
    /**
     * Returns the file containing the pristine file contents.
     * @return base file 
     */
    public File getBaseFile() {
        return myBaseFile;
    }
    
    /**
     * Returns the working copy file as it presents in the working copy.
     * @return working copy file 
     */
    public File getWCFile() {
        return myAdminArea.getFile(myWCFilePath);
    }
    
    /**
     * Returns the detranslated working copy file.
     * Detranslating of a working copy file takes place in case it's a symlink, or it has keywords or 
     * eol-style properties set on it.
     * 
     * @return detranslated working copy file 
     */
    public File getLocalFile() {
        return myLocalFile;
    }
    
    /**
     * Returns the repository version of the file. 
     * @return repository file 
     */
    public File getRepositoryFile() {
        return myRepositoryFile;
    }
    
    /**
     * Returns the file where the merged resultant text is written to.
     * @return merge result file
     */
    public File getResultFile() {
        return myMergeResultFile;
    }
    
    /**
     * Tells whether this file is binary or textual.
     * The result will depend on the value of the file {@link #getMimeType() mime type}.
     * 
     * @return <span class="javakeyword">true</span> if binary 
     */
    public boolean isBinary() {
        return SVNProperty.isBinaryMimeType(myMimeType);
    }
    
    /**
     * Returns the mime type of the file.
     * @return file mime type 
     */
    public String getMimeType() {
        return myMimeType;
    }
    
    /**
     * Returns the admin area which controls the file.
     * 
     * <p/>
     * Note: this method is not intended for API users.
     * @return admin area
     */
    public SVNAdminArea getAdminArea() {
        return myAdminArea;
    }
    
    /**
     * Disposes this object.
     * 
     * <p/>
     * Note: this method is not intended for API users.
     * @throws SVNException 
     */
    public void dispose() throws SVNException {
        // add deletion commands to the log file.
        SVNProperties command = new SVNProperties();
        for (Iterator paths = myTmpPaths.iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            command.put(SVNLog.NAME_ATTR, path);
            myLog.addCommand(SVNLog.DELETE, command, false);
            command.clear();
        }
    }
    
    /**
     * Returns the file which is the copy source for the file being merged.
     * @return copy source file 
     */
    public File getCopyFromFile() {
        return myCopyFromFile;
    }

    /**
     * Returns the copy source path.
     * 
     * @return   path of the copy source file; 
     *           it's relevant to the {@link #getAdminArea() admin area} location
     * @since    1.3
     */
    public String getCopyFromPath() {
        String root = myAdminArea.getRoot().getAbsolutePath().replace(File.separatorChar, '/');
        String copyFrom = getCopyFromFile().getAbsolutePath().replace(File.separatorChar, '/');
        String copyFromPath = copyFrom.substring(root.length());
        copyFromPath = copyFromPath.startsWith("/") ? copyFromPath.substring("/".length()) : copyFromPath;
        return copyFromPath;
    }

    public String toString() {
        final StringBuffer buffer = new StringBuffer();
        buffer.append("{Merge File Set: ");
        buffer.append("admin area = ");
        buffer.append(myAdminArea);
        buffer.append("; local file path = ");
        buffer.append(myLocalFilePath);
        buffer.append("; base file path =");
        buffer.append(myBaseFilePath);
        buffer.append("; repository file path = ");
        buffer.append(myRepositoryFilePath);
        buffer.append("; WC file path = ");
        buffer.append(myWCFilePath);
        buffer.append("; merge result path = ");
        buffer.append(myMergeResultFilePath);
        buffer.append("; local file = ");
        buffer.append(myLocalFile);
        buffer.append("; base file = ");
        buffer.append(myBaseFile);
        buffer.append("; repository file = ");
        buffer.append(myRepositoryFile);
        buffer.append("; merge result file = ");
        buffer.append(myMergeResultFile);
        buffer.append('}');
        return buffer.toString();
    }
}