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
package org.exist.versioning.svn.wc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.DefaultSVNOptions;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNReturnValueCallback;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.util.SVNLogType;

import de.regnis.q.sequence.line.diff.QDiffGenerator;
import de.regnis.q.sequence.line.diff.QDiffGeneratorFactory;
import de.regnis.q.sequence.line.diff.QDiffManager;
import de.regnis.q.sequence.line.diff.QDiffUniGenerator;

/**
 * <b>DefaultSVNDiffGenerator</b> is a default implementation of 
 * <b>ISVNDiffGenerator</b>.
 * <p>
 * By default, if there's no any specified implementation of the diff generator's
 * interface, SVNKit uses this default implementation. To set a custom
 * diff driver use {@link SVNDiffClient#setDiffGenerator(ISVNDiffGenerator) setDiffGenerator()}.
 * 
 * @version 1.3
 * @since   1.2
 * @author  TMate Software Ltd.
 */
public class DefaultSVNDiffGenerator implements ISVNDiffGenerator {

    protected static final byte[] PROPERTIES_SEPARATOR = "___________________________________________________________________".getBytes();
    protected static final byte[] HEADER_SEPARATOR = "===================================================================".getBytes();
    protected static final String WC_REVISION_LABEL = "(working copy)";
    protected static final InputStream EMPTY_FILE_IS = SVNFileUtil.DUMMY_IN;

    private boolean myIsForcedBinaryDiff;
    private String myAnchorPath1;
    private String myAnchorPath2;

    private ISVNOptions myOptions;    
    private String myEncoding;
    private byte[] myEOL;
    private boolean myIsDiffDeleted;
    private boolean myIsDiffAdded;
    private boolean myIsDiffCopied;
    private File myBasePath;
    private boolean myIsDiffUnversioned;
    private SVNDiffOptions myDiffOptions;
    private Collection myRawDiffOptions;
    private String myDiffCommand;
    private boolean myIsUseAbsolutePaths;
    
    /**
     * Constructs a <b>DefaultSVNDiffGenerator</b>.
     *
     */
    public DefaultSVNDiffGenerator() {
        myIsDiffDeleted = true;
        myAnchorPath1 = "";
        myAnchorPath2 = "";
    }

    /**
     * Initializes this generator with old and new diff anchor paths. 
     * 
     * @param anchorPath1  an old path/URL
     * @param anchorPath2  a new path/URL
     */
    public void init(String anchorPath1, String anchorPath2) {
        myAnchorPath1 = anchorPath1.replace(File.separatorChar, '/');
        myAnchorPath2 = anchorPath2.replace(File.separatorChar, '/');
    }
    
    /**
     * Sets diff options containing diff rules.
     * 
     * @param options diff options
     */
    public void setDiffOptions(SVNDiffOptions options) {
        myDiffOptions = options;
    }

    /**
     * Sets a collection of raw (<code>String</code>) diff options.  
     * 
     * @param options raw options 
     */
    public void setRawDiffOptions(Collection options) {
        myRawDiffOptions = options;
    }

    /**
     * Sets global run-time options. 
     * 
     * @param options options implementation 
     */
    public void setOptions(ISVNOptions options){
        myOptions = options;
    }
    
    /**
     * Sets an external diff program for producing the difference between files.
     * 
     * @param command external diff program 
     */
    public void setExternalDiffCommand(String command) {
        myDiffCommand = command;
    }
    
    /**
     * Sets the base path that must be stripped from the front of the paths of compared files.
     * If <code>basePath</code> is not <span class="javakeyword">null</span> but is not a parent path of 
     * the target, this will lead to an error during diff. 
     * 
     * <p/>
     * Note: <code>basePath</code> doesn't affect the path index generated by external diff programs.
     * 
     * @param basePath common parent path to strip off the displayed paths 
     */
    public void setBasePath(File basePath) {
        myBasePath = basePath;
    }
    
    /**
     * Controls whether error is reported on failure to compute relative display path, 
     * or absolute path is used instead.
     * 
     * @param fallback true to make generator use absolute path when relative path could not
     *                 be computed.
     */
    public void setFallbackToAbsolutePath(boolean fallback) {
        myIsUseAbsolutePaths = fallback;
    }

    /**
     * Enables or disables diffing deleted files.
     * 
     * @param isDiffDeleted
     */
    public void setDiffDeleted(boolean isDiffDeleted) {
        myIsDiffDeleted = isDiffDeleted;
    }

    /**
     * Tells whether deleted files must be diffed also.
     * 
     * @return <span class="javakeyword">true</span> if deleted files must be diffed also  
     */
    public boolean isDiffDeleted() {
        return myIsDiffDeleted;
    }

    /**
     * Enables or disables diffing added files.
     * 
     * @param isDiffAdded
     */
    public void setDiffAdded(boolean isDiffAdded) {
        myIsDiffAdded = isDiffAdded;
    }

    /**
     * Tells whether added files must be diffed also.
     * 
     * @return <span class="javakeyword">true</span> if added files must be diffed also  
     */
    public boolean isDiffAdded() {
        return myIsDiffAdded;
    }

    /**
     * Enables or disables copied files diffing.
     * 
     * @param isDiffCopied 
     */
    public void setDiffCopied(boolean isDiffCopied) {
        myIsDiffCopied = isDiffCopied;
    }

    /**
     * Tells whether deleted files must be diffed also.
     * 
     * @return <span class="javakeyword">true</span> if copied files must be diffed also  
     */
    public boolean isDiffCopied() {
        return myIsDiffCopied;
    }

    /**
     * Gets the diff options that are used by this generator. 
     * Creates a new one if none was used before.
     * 
     * @return diff options
     */
    public SVNDiffOptions getDiffOptions() {
        if (myDiffOptions == null) {
            myDiffOptions = new SVNDiffOptions();
        }
        return myDiffOptions;
    }

    protected String getDisplayPath(String path) throws SVNException {
        if (myBasePath == null) {
            return path;
        }
        if (path == null) {
            path = "";
        }
        if (SVNPathUtil.isURL(path)) {
            return path;
        }
        // treat as file path.
        String basePath = myBasePath.getAbsolutePath().replace(File.separatorChar, '/');
        path = new Resource(path).getAbsolutePath().replace(File.separatorChar, '/');
        if (path.equals(basePath)) {
            return ".";
        }
        String relativePath = SVNPathUtil.getPathAsChild(basePath, path);
        if (relativePath == null) {
            if (myIsUseAbsolutePaths) {
                return path;
            }
            createBadRelativePathError(path);
        }
        if (relativePath.startsWith("./")) {
            relativePath = relativePath.substring("./".length());
        }
        return relativePath;
    }

    /**
     * Sets whether binary files diff must be forced or not.
     * 
     * @param forced whether to force binary diff or not 
     */
    public void setForcedBinaryDiff(boolean forced) {
        myIsForcedBinaryDiff = forced;
    }

    /**
     * Tells if this generator forced binary files diff.
     * 
     * @return <span class="javakeyword">true</span> if forces; otherwise <span class="javakeyword">false</span> 
     */
    public boolean isForcedBinaryDiff() {
        return myIsForcedBinaryDiff;
    }

    /**
     * Produces properties difference and writes it to <code>result</code>.
     * 
     * @param  path 
     * @param  baseProps 
     * @param  diff 
     * @param  result 
     * @throws SVNException  in the following cases:
     *                       <ul>
     *                       <li/>exception with {@link SVNErrorCode#IO_ERROR} error code - if an I\O error occurred
     *                       </ul> 
     */
    public void displayPropDiff(String path, SVNProperties baseProps, SVNProperties diff, OutputStream result) throws SVNException {
        baseProps = baseProps != null ? baseProps : new SVNProperties();
        diff = diff != null ? diff : new SVNProperties();
        for (Iterator changedPropNames = diff.nameSet().iterator(); changedPropNames.hasNext();) {
            String name = (String) changedPropNames.next();
            SVNPropertyValue originalValue = baseProps.getSVNPropertyValue(name);
            SVNPropertyValue newValue = diff.getSVNPropertyValue(name);
            if ((originalValue != null && originalValue.equals(newValue)) || (originalValue == null && newValue == null)) {
                changedPropNames.remove();
            }
        }
        if (diff.isEmpty()) {
            return;
        }
        path = getDisplayPath(path);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        diff = new SVNProperties(diff);
        try {
            bos.write(getEOL());
            bos.write(("Property changes on: " + (useLocalFileSeparatorChar() ? path.replace('/', File.separatorChar) : path)).getBytes(getEncoding()));
            bos.write(getEOL());
            bos.write(PROPERTIES_SEPARATOR);
            bos.write(getEOL());
            for (Iterator changedPropNames = diff.nameSet().iterator(); changedPropNames.hasNext();) {
                String name = (String) changedPropNames.next();
                SVNPropertyValue originalValue = baseProps != null ? baseProps.getSVNPropertyValue(name) : null;
                SVNPropertyValue newValue = diff.getSVNPropertyValue(name);
                String headerFormat = null;
                
                if (originalValue == null) {
                    headerFormat = "Added: ";
                } else if (newValue == null) {
                    headerFormat = "Deleted: ";
                } else {
                    headerFormat = "Modified: ";
                }
                
                bos.write((headerFormat + name).getBytes(getEncoding()));
                bos.write(getEOL());
                if (SVNProperty.MERGE_INFO.equals(name)) {
                    displayMergeInfoDiff(bos, originalValue == null ? null : originalValue.getString(), newValue == null ? null : newValue.getString());
                    continue;
                }
                if (originalValue != null) {
                    bos.write("   - ".getBytes(getEncoding()));
                    bos.write(getPropertyAsBytes(originalValue, getEncoding()));
                    bos.write(getEOL());
                }
                if (newValue != null) {
                    bos.write("   + ".getBytes(getEncoding()));
                    bos.write(getPropertyAsBytes(newValue, getEncoding()));
                    bos.write(getEOL());
                } 
            }
            bos.write(getEOL());
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
        } finally {
            try {
                bos.close();
                bos.writeTo(result);
            } catch (IOException e) {
            }
        }
    }

    private byte[] getPropertyAsBytes(SVNPropertyValue value, String encoding){
        if (value == null){
            return null;            
        }
        if (value.isString()){
            try {
                return value.getString().getBytes(encoding);
            } catch (UnsupportedEncodingException e) {
                return value.getString().getBytes();
            }
        } 
        return value.getBytes();
    }

    protected File getBasePath() {
        return myBasePath;
    }

    /**
     * Writes the difference between <code>file1</code> and <code>file2</code> as they are seen in 
     * <code>rev1</code> and <code>rev2</code> to <code>result</code>.
     * 
     * @param  path 
     * @param  file1 
     * @param  file2 
     * @param  rev1 
     * @param  rev2 
     * @param  mimeType1 
     * @param  mimeType2 
     * @param  result 
     * @throws SVNException  in the following cases:
     *                       <ul>
     *                       <li/>exception with {@link SVNErrorCode#EXTERNAL_PROGRAM} error code - if an external diff program 
     *                       exited with an error code value different from <code>0</code> and <code>1</code>
     *                       <li/>exception with {@link SVNErrorCode#IO_ERROR} error code - if an I\O error occurred
     *                       </ul> 
     */
    public void displayFileDiff(String path, File file1, File file2,
            String rev1, String rev2, String mimeType1, String mimeType2, OutputStream result) throws SVNException {
        path = getDisplayPath(path);
        // if anchor1 is the same as anchor2 just use path.        
        // if anchor1 differs from anchor2 =>
        // condence anchors (get common root and remainings).
        int i = 0;
        for(; i < myAnchorPath1.length() && i < myAnchorPath2.length() &&
            myAnchorPath1.charAt(i) == myAnchorPath2.charAt(i); i++) {}
        if (i < myAnchorPath1.length() || i < myAnchorPath2.length()) {
            if (i == myAnchorPath1.length()) {
                i = myAnchorPath1.length() - 1;
            }
            for(; i > 0 && myAnchorPath1.charAt(i) != '/'; i--) {}
        }
        String p1 = myAnchorPath1.substring(i) ;
        String p2 = myAnchorPath2.substring(i);
        
        if (p1.length() == 0) {
            p1 = path;
        } else if (p1.charAt(0) == '/') {
            p1 = path + "\t(..." + p1 + ")";
        } else {
            p1 = path + "\t(.../" + p1 + ")";
        }
        if (p2.length() == 0) {
            p2 = path;
        } else if (p2.charAt(0) == '/') {
            p2 = path + "\t(..." + p2 + ")";
        } else {
            p2 = path + "\t(.../" + p2 + ")";
        }
        
        String label1 = getLabel(p1, rev1);
        String label2 = getLabel(p2, rev2);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            if (displayHeader(bos, path, file2 == null)) {
                bos.close();
                bos.writeTo(result);
                return;
            }
            if (isHeaderForced(file1, file2)) {
                bos.writeTo(result);
                bos.reset();
            }
        } catch (IOException e) {
            try {
                bos.close();
                bos.writeTo(result);
            } catch (IOException inner) {
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
        }
        
        if (!isForcedBinaryDiff() && (SVNProperty.isBinaryMimeType(mimeType1) || SVNProperty.isBinaryMimeType(mimeType2))) {
            try {
                displayBinary(bos, mimeType1, mimeType2);
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
            } finally {
                try {
                    bos.close();
                    bos.writeTo(result);
                } catch (IOException e) {
                }
            }
            return;
        }
        
        if (file1 == file2 && file1 == null) {
            try {
                bos.close();
                bos.writeTo(result);
            } catch (IOException e) {
            }
            return;
        }

        final String diffCommand = getExternalDiffCommand();
        if (diffCommand != null) {
            try {
                bos.close();
                bos.writeTo(result);
            } catch (IOException e) {
            }

            Collection args = new LinkedList();
            File diffCommandFile = new Resource(diffCommand);
            args.add(diffCommandFile.getAbsolutePath().replace(File.separatorChar, '/'));
            
            if (myRawDiffOptions != null) {
                args.addAll(myRawDiffOptions);
            } else {
                Collection diffOptions = getDiffOptions().toOptionsCollection();
                args.addAll(diffOptions);
                args.add("-u");
            }
            
            if (label1 != null) {
                args.add("-L");
                args.add(label1);
            }
            
            if (label2 != null) {
                args.add("-L");
                args.add(label2);
            }
            boolean tmpFile1 = false;
            boolean tmpFile2 = false;
            if (file1 == null) {
                file1 = SVNFileUtil.createTempFile("svn.", ".tmp");
                tmpFile1 = true;
            }
            if (file2 == null) {
                file2 = SVNFileUtil.createTempFile("svn.", ".tmp");
                tmpFile2 = true;
            }
                
            String currentDir = new File("").getAbsolutePath().replace(File.separatorChar, '/');
            String file1Path = file1.getAbsolutePath().replace(File.separatorChar, '/');
            String file2Path = file2.getAbsolutePath().replace(File.separatorChar, '/'); 
            
            if (file1Path.startsWith(currentDir)) {
                file1Path = file1Path.substring(currentDir.length());
                file1Path = file1Path.startsWith("/") ? file1Path.substring(1) : file1Path;
            }
            
            if (file2Path.startsWith(currentDir)) {
                file2Path = file2Path.substring(currentDir.length());
                file2Path = file2Path.startsWith("/") ? file2Path.substring(1) : file2Path;
            }

            args.add(file1Path);
            args.add(file2Path);
            
            try {
                final Writer writer = new OutputStreamWriter(result, getEncoding());

                SVNFileUtil.execCommand((String[]) args.toArray(new String[args.size()]), true, 
                        new ISVNReturnValueCallback() {
                    
                    public void handleReturnValue(int returnValue) throws SVNException {
                        if (returnValue != 0 && returnValue != 1) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.EXTERNAL_PROGRAM, 
                                    "''{0}'' returned {1}", new Object[] { diffCommand, String.valueOf(returnValue) });
                            SVNErrorManager.error(err, SVNLogType.DEFAULT);
                        }
                    }

                    public void handleChar(char ch) throws SVNException {
                        try {
                            writer.write(ch);
                        } catch (IOException ioe) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getMessage());
                            SVNErrorManager.error(err, ioe, SVNLogType.DEFAULT);
                        }
                    }

                    public boolean isHandleProgramOutput() {
                        return true;
                    }
                });

                writer.flush();
            } catch (IOException ioe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getMessage());
                SVNErrorManager.error(err, ioe, SVNLogType.DEFAULT);
            } finally {
                try {
                    if (tmpFile1) {
                        SVNFileUtil.deleteFile(file1);                    
                    }
                    if (tmpFile2) {
                        SVNFileUtil.deleteFile(file2);                    
                    }
                } catch (SVNException e) {
                    // skip
                }
            }
            return;
        }

        // put header fields.
        try {
            displayHeaderFields(bos, label1, label2);
        } catch (IOException e) {
            try {
                bos.close();
                bos.writeTo(result);
            } catch (IOException inner) {
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
        }

        String header;
        try {
            bos.close();
            header = bos.toString();            
        } catch (IOException inner) {
            header = "";
        }

        RandomAccessFile is1 = null;
        RandomAccessFile is2 = null;
        try {
            is1 = file1 == null ? null : SVNFileUtil.openRAFileForReading(file1);
            is2 = file2 == null ? null : SVNFileUtil.openRAFileForReading(file2);

            QDiffUniGenerator.setup();
            Map properties = new SVNHashMap();
            
            properties.put(QDiffGeneratorFactory.IGNORE_EOL_PROPERTY, Boolean.valueOf(getDiffOptions().isIgnoreEOLStyle()));
            if (getDiffOptions().isIgnoreAllWhitespace()) {
                properties.put(QDiffGeneratorFactory.IGNORE_SPACE_PROPERTY, QDiffGeneratorFactory.IGNORE_ALL_SPACE);
            } else if (getDiffOptions().isIgnoreAmountOfWhitespace()) {
                properties.put(QDiffGeneratorFactory.IGNORE_SPACE_PROPERTY, QDiffGeneratorFactory.IGNORE_SPACE_CHANGE);
            }
            QDiffGenerator generator = new QDiffUniGenerator(properties, header);
            Writer writer = new OutputStreamWriter(result, getEncoding());
            QDiffManager.generateTextDiff(is1, is2, getEncoding(), writer, generator);
            writer.flush();
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
            SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
        } finally {
            SVNFileUtil.closeFile(is1);
            SVNFileUtil.closeFile(is2);
        }
    }

    /**
     * Sets the encoding to use for diff output.
     * @param encoding  charset name 
     */
    public void setEncoding(String encoding) {
        myEncoding = encoding;
    }

    /**
     * Returns the encoding used for diff output.
     * 
     * @return charset name 
     */
    public String getEncoding() {
        if (hasEncoding()) {
            return myEncoding;
        }
        return getOptions().getNativeCharset();
    }

    /**
     * Says whether this generator is using any special (non-native) 
     * charset for outputting diffs.
     * 
     * @return <span class="javakeyword">true</span> if yes;
     *         otherwise <span class="javakeyword">false</span> 
     */
    public boolean hasEncoding() {
        return myEncoding != null;
    }

    /**
     * Sets the EOL bytes to use in diff output.
     * 
     * @param eol EOL bytes 
     */
    public void setEOL(byte[] eol){
        myEOL = eol;
    }

    /**
     * Returns the EOL marker bytes being in use.
     * If no EOL bytes were provided, uses {@link ISVNOptions#getNativeEOL() native EOL} fetched from 
     * the options.
     * 
     * @return EOL bytes 
     */
    public byte[] getEOL(){
        if (myEOL == null){
            myEOL = getOptions().getNativeEOL();            
        }
        return myEOL;
    }

    /**
     * Creates a temporary directory for diff files.
     * 
     * @return                returns the temp directory 
     * @throws SVNException 
     */
    public File createTempDirectory() throws SVNException {
        return SVNFileUtil.createTempDirectory("diff");
    }

    /**
     * Says if unversioned files are also diffed or ignored.
     * 
     * <p>
     * By default unversioned files are ignored. 
     * 
     * @return <span class="javakeyword">true</span> if diffed, 
     *         <span class="javakeyword">false</span> if ignored  
     * @see    #setDiffUnversioned(boolean)
     * 
     */

    public boolean isDiffUnversioned() {
        return myIsDiffUnversioned;
    }

    /**
     * Includes or not unversioned files into diff processing. 
     * 
     * <p>
     * If a diff operation is invoked on  a versioned directory and 
     * <code>diffUnversioned</code> is <span class="javakeyword">true</span> 
     * then all unversioned files that may be met in the directory will 
     * be processed as added. Otherwise if <code>diffUnversioned</code> 
     * is <span class="javakeyword">false</span> such files are ignored. 
     * 
     * <p>
     * By default unversioned files are ignored.
     * 
     * @param diffUnversioned controls whether to diff unversioned files 
     *                        or not 
     * @see                   #isDiffUnversioned()
     */
    public void setDiffUnversioned(boolean diffUnversioned) {
        myIsDiffUnversioned = diffUnversioned;
    }

    /**
     * Does nothing.
     * 
     * @param  path           a directory path
     * @param  rev1           the first diff revision
     * @param  rev2           the second diff revision
     * @throws SVNException   
     */
    public void displayDeletedDirectory(String path, String rev1, String rev2) throws SVNException {
        // not implemented.
    }

    /**
     * Does nothing.
     * 
     * @param  path           a directory path
     * @param  rev1           the first diff revision
     * @param  rev2           the second diff revision
     * @throws SVNException
     */
    public void displayAddedDirectory(String path, String rev1, String rev2) throws SVNException {
        // not implemented.
    }

    protected String getExternalDiffCommand() {
        if (myDiffCommand != null) {
            return myDiffCommand;
        }
        if (myOptions instanceof DefaultSVNOptions) {
            return ((DefaultSVNOptions) myOptions).getDiffCommand();
        }
        return null;
    }

    protected ISVNOptions getOptions(){
        if (myOptions == null){
            myOptions = new DefaultSVNOptions();
        }
        return myOptions;
    }

    protected void displayBinary(OutputStream os, String mimeType1, String mimeType2) throws IOException {
        os.write("Cannot display: file marked as a binary type.".getBytes(getEncoding()));
        os.write(getEOL());
        if (SVNProperty.isBinaryMimeType(mimeType1)
                && !SVNProperty.isBinaryMimeType(mimeType2)) {
            os.write("svn:mime-type = ".getBytes(getEncoding()));
            os.write(mimeType1.getBytes(getEncoding()));
            os.write(getEOL());
        } else if (!SVNProperty.isBinaryMimeType(mimeType1)
                && SVNProperty.isBinaryMimeType(mimeType2)) {
            os.write("svn:mime-type = ".getBytes(getEncoding()));
            os.write(mimeType2.getBytes(getEncoding()));
            os.write(getEOL());
        } else if (SVNProperty.isBinaryMimeType(mimeType1)
                && SVNProperty.isBinaryMimeType(mimeType2)) {
            if (mimeType1.equals(mimeType2)) {
                os.write("svn:mime-type = ".getBytes(getEncoding()));
                os.write(mimeType2.getBytes(getEncoding()));
                os.write(getEOL());
            } else {
                os.write("svn:mime-type = (".getBytes(getEncoding()));
                os.write(mimeType1.getBytes(getEncoding()));
                os.write(", ".getBytes(getEncoding()));
                os.write(mimeType2.getBytes(getEncoding()));
                os.write(")".getBytes(getEncoding()));
                os.write(getEOL());
            }
        }
    }
    
    protected boolean displayHeader(OutputStream os, String path, boolean deleted) throws IOException {
        if (deleted && !isDiffDeleted()) {
            os.write("Index: ".getBytes(getEncoding()));
            os.write(path.getBytes(getEncoding()));
            os.write(" (deleted)".getBytes(getEncoding()));
            os.write(getEOL());
            os.write(HEADER_SEPARATOR);
            os.write(getEOL());
            return true;
        }
        os.write("Index: ".getBytes(getEncoding()));
        os.write(path.getBytes(getEncoding()));
        os.write(getEOL());
        os.write(HEADER_SEPARATOR);
        os.write(getEOL());
        return false;
    }
    
    protected void displayHeaderFields(OutputStream os, String label1, String label2) throws IOException {
        os.write("--- ".getBytes(getEncoding()));
        os.write(label1.getBytes(getEncoding()));
        os.write(getEOL());
        os.write("+++ ".getBytes(getEncoding()));
        os.write(label2.getBytes(getEncoding()));
        os.write(getEOL());
    }
    
    protected boolean isHeaderForced(File file1, File file2) {
        return (file1 == null && file2 != null);
    }
    
    protected boolean useLocalFileSeparatorChar() {
        return true;
    }
    
    protected String getLabel(String path, String revToken) {
        revToken = revToken == null ? WC_REVISION_LABEL : revToken;
        return path + "\t" + revToken;
    }
    
    private void displayMergeInfoDiff(ByteArrayOutputStream baos, String oldValue, String newValue) throws SVNException, IOException {
        Map oldMergeInfo = null;
        Map newMergeInfo = null;
        if (oldValue != null) {
            oldMergeInfo = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(oldValue), null);
        }
        if (newValue != null) {
            newMergeInfo = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(newValue), null);
        }
        
        Map deleted = new TreeMap();
        Map added = new TreeMap();
        SVNMergeInfoUtil.diffMergeInfo(deleted, added, oldMergeInfo, newMergeInfo, true);

        for (Iterator paths = deleted.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) deleted.get(path);
            baos.write(("   Reverse-merged " + path + ":r").getBytes(getEncoding())); 
            baos.write(rangeList.toString().getBytes(getEncoding()));
            baos.write(getEOL());
        }
        
        for (Iterator paths = added.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) added.get(path);
            baos.write(("   Merged " + path + ":r").getBytes(getEncoding())); 
            baos.write(rangeList.toString().getBytes(getEncoding()));
            baos.write(getEOL());
        }
    }
    
    private void createBadRelativePathError(String path) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_RELATIVE_PATH, 
                "Path ''{0}'' must be an immediate child of the directory ''{1}''", 
                new Object[] { path, myBasePath });
        SVNErrorManager.error(err, SVNLogType.DEFAULT);
    }
}
