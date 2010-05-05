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

import java.io.File;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.exist.versioning.svn.internal.wc.AbstractDiffCallback;
import org.exist.versioning.svn.internal.wc.SVNAmbientDepthFilterEditor;
import org.exist.versioning.svn.internal.wc.SVNCancellableEditor;
import org.exist.versioning.svn.internal.wc.SVNDiffCallback;
import org.exist.versioning.svn.internal.wc.SVNDiffEditor;
import org.exist.versioning.svn.internal.wc.SVNDiffStatusEditor;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNEventFactory;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.SVNMergeDriver;
import org.exist.versioning.svn.internal.wc.SVNRemoteDiffEditor;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminAreaInfo;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNReporter;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNDiffStatusHandler;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * The <b>SVNDiffClient</b> class provides methods allowing to get differences
 * between versioned items ('diff' operation) as well as ones intended for 
 * merging file contents.
 * 
 * <p>
 * Here's a list of the <b>SVNDiffClient</b>'s methods 
 * matched against corresponing commands of the SVN command line 
 * client:
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="40%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>SVNKit</b></td>
 * <td><b>Subversion</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doDiff()</td><td>'svn diff'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doDiffStatus()</td><td>'svn diff --summarize'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doMerge()</td><td>'svn merge'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetLogXXXMergeInfo()</td><td>'svn mergeinfo'</td>
 * </tr>
 * </table>
 * 
 * @version 1.3
 * @since   1.2
 * @author  TMate Software Ltd.
 */
public class SVNDiffClient extends SVNMergeDriver {

    private ISVNDiffGenerator myDiffGenerator;
    private SVNDiffOptions myDiffOptions;

    /**
     * Constructs and initializes an <b>SVNDiffClient</b> object
     * with the specified run-time configuration and authentication 
     * drivers.
     * 
     * <p/>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNDiffClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the 
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
     * 
     * <p/>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNDiffClient</b> will be using a default authentication
     * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
     * which uses server-side settings and auth storage from the 
     * default SVN's run-time configuration area (or system properties
     * if that area is not found).
     * 
     * @param authManager an authentication and network layers driver
     * @param options     a run-time configuration options driver     
     */
    public SVNDiffClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    /**
     * Constructs and initializes an <b>SVNDiffClient</b> object
     * with the specified run-time configuration and repository pool object.
     * 
     * <p/>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNDiffClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
     * 
     * <p/>
     * If <code>repositoryPool</code> is <span class="javakeyword">null</span>,
     * then {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory} will be used to create 
     * {@link SVNRepository repository access objects}.
     *
     * @param repositoryPool   a repository pool object
     * @param options          a run-time configuration options driver
     */
    public SVNDiffClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }
    
    /**
     * Sets the specified diff driver for this object to use for
     * generating and writing file differences to an otput stream.
     * 
     * <p>
     * If no specific diff driver was set in this way, a default one
     * will be used (see {@link DefaultSVNDiffGenerator}). 
     * 
     * @param diffGenerator a diff driver
     * @see   #getDiffGenerator()
     */
    public void setDiffGenerator(ISVNDiffGenerator diffGenerator) {
        myDiffGenerator = diffGenerator;
    }
    
    /**
     * Returns the diff driver being in use.
     *  
     * <p>
     * If no specific diff driver was previously provided, a default one
     * will be returned (see {@link DefaultSVNDiffGenerator}). 
     * 
     * @return the diff driver being in use
     * @see    #setDiffGenerator(ISVNDiffGenerator)
     */
    public ISVNDiffGenerator getDiffGenerator() {
        if (myDiffGenerator == null) {
            DefaultSVNDiffGenerator defaultDiffGenerator = new DefaultSVNDiffGenerator();
            defaultDiffGenerator.setOptions(getOptions());
            myDiffGenerator = defaultDiffGenerator;
        }
        return myDiffGenerator;
    }
    
    /**
     * Sets diff options for this client to use in merge operations.
     * 
     * @param diffOptions diff options object
     */
    public void setMergeOptions(SVNDiffOptions diffOptions) {
        myDiffOptions = diffOptions;
    }

    /**
     * Gets the diff options that are used in merge operations by this client. 
     * If none was provided by the user, one created as <code>new SVNDiffOptions()</code> will be returned
     * and used further.
     * 
     * @return diff options
     */
    public SVNDiffOptions getMergeOptions() {
        if (myDiffOptions == null) {
            myDiffOptions = new SVNDiffOptions();
        }
        return myDiffOptions;
    }

    /**
     * Generates the differences for the specified URL taken from the two 
     * specified revisions and writes the result to the provided output
     * stream.
     * 
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn diff -r N:M URL'</code> command.
     * 
     * @param  url            a repository location
     * @param  pegRevision    a revision in which <code>url</code> is first looked up
     * @param  rN             an old revision                          
     * @param  rM             a new revision
     * @param  recursive      <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>rN</code>, <code>rM</code> and
     *                        <code>pegRevision</code> is invalid
     *                        <li>at least one of <code>rN</code> and <code>rM</code> is
     *                        a local revision (see {@link SVNRevision#isLocal()})
     *                        <li><code>url</code> was not found in <code>rN</code>
     *                        <li><code>url</code> was not found in <code>rM</code>
     *                        </ul>
     * @deprecated use {@link #doDiff(SVNURL, SVNRevision, SVNRevision, SVNRevision, SVNDepth, boolean, OutputStream)} 
     *             instead
     */
    public void doDiff(SVNURL url, SVNRevision pegRevision, SVNRevision rN, SVNRevision rM, boolean recursive, 
            boolean useAncestry, OutputStream result) throws SVNException {
        doDiff(url, pegRevision, rN, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, result);
    }

    /**
     * Produces diff output which describes the delta between <code>url</code> in peg revision 
     * <code>pegRevision</code>, as it changed between <code>rN</code> and <code>rM</code>. 
     * 
     * <p/>
     * If <code>pegRevision is {@link SVNRevision#isValid() invalid}, behaves identically to 
     * {@link #doDiff(SVNURL, SVNRevision, SVNURL, SVNRevision, SVNDepth, boolean, OutputStream)}, 
     * using <code>url</code> for both of that function's <code>url1</code> and <code>url2</code> arguments.
     * 
     * <p/>
     * All other options are handled identically to 
     * {@link #doDiff(SVNURL, SVNRevision, SVNURL, SVNRevision, SVNDepth, boolean, OutputStream)}.
     *
     * @param  url            a repository location
     * @param  pegRevision    a revision in which <code>url</code> is first looked up
     * @param  rN             an old revision                          
     * @param  rM             a new revision
     * @param  depth          tree depth to process
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} error code -
     *                        if either of <code>rN</code> and <code>rM</code> is either 
     *                        {@link SVNRevision#isValid() invalid} or {@link SVNRevision#isLocal() local} 
     *                        <li>exception with {@link SVNErrorCode#FS_NOT_FOUND} error code - 
     *                        <code>url</code> can not be found in either <code>rN</code> or <code>rM</code>
     *                        </ul>
     * @since                 1.2, SVN 1.5
     */
    public void doDiff(SVNURL url, SVNRevision pegRevision, SVNRevision rN, SVNRevision rM, SVNDepth depth, 
            boolean useAncestry, OutputStream result) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                    "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        if (rN.isLocal() || rM.isLocal()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                    "Both rN and rM revisions must be non-local for a pegged diff of an URL");            
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        getDiffGenerator().init(url.toString(), url.toString());
        doDiffURLURL(url, null, rN, url, null, rM, pegRevision, depth, useAncestry, result);
    }
    
    /**
     * Generates the differences for the specified path taken from the two 
     * specified revisions and writes the result to the provided output
     * stream.
     * 
     * <p>
     * If <code>rM</code> is a local revision (see {@link SVNRevision#isLocal()}),
     * then the Working Copy <code>path</code> is compared with the corresponding 
     * repository file at revision <code>rN</code> (that is similar to the SVN command 
     * line client's <code>'svn diff -r N path'</code> command). 
     * 
     * <p>
     * Otherwise if both <code>rN</code> and <code>rM</code> are non-local, then 
     * the repository location of <code>path</code> is compared for these 
     * revisions (<code>'svn diff -r N:M URL'</code>).
     * 
     * @param  path           a Working Copy path
     * @param  pegRevision    a revision in which the repository location of <code>path</code> 
     *                        is first looked up
     * @param  rN             an old revision                          
     * @param  rM             a new revision (or a local one)
     * @param  recursive      <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>rN</code>, <code>rM</code> and
     *                        <code>pegRevision</code> is invalid
     *                        <li>both <code>rN</code> and <code>rM</code> are 
     *                        local revisions
     *                        <li><code>path</code> was not found in <code>rN</code>
     *                        <li><code>path</code> was not found in <code>rM</code>
     *                        </ul>
     * @deprecated            use {@link #doDiff(File, SVNRevision, SVNRevision, SVNRevision, SVNDepth, boolean, OutputStream, Collection)}
     *                        instead 
     */
    public void doDiff(File path, SVNRevision pegRevision, SVNRevision rN, SVNRevision rM, boolean recursive, 
            boolean useAncestry, OutputStream result) throws SVNException {
        doDiff(path, pegRevision, rN, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, result, null);
    }
    
    /**
     * Iterates over the passed in <code>paths</code> calling 
     * {@link #doDiff(File, SVNRevision, SVNRevision, SVNRevision, SVNDepth, boolean, OutputStream, Collection)} 
     * for each one in the array.
     * 
     * @param  paths         array of working copy paths 
     * @param  rN            an old revision                          
     * @param  rM            a new revision
     * @param  pegRevision   a revision in which the repository location of <code>paths</code> 
     *                       is first looked up
     * @param  depth         tree depth to process
     * @param  useAncestry   if <span class="javakeyword">true</span> then
     *                       the paths ancestry will be noticed while calculating differences,
     *                       otherwise not
     * @param  result        the target {@link java.io.OutputStream} where
     *                       the differences will be written to
     * @param  changeLists   collection with changelist names
     * @throws SVNException
     * @since                1.2, SVN 1.5
     */
    public void doDiff(File[] paths, SVNRevision rN, SVNRevision rM, SVNRevision pegRevision, SVNDepth depth, 
            boolean useAncestry, OutputStream result, Collection changeLists) throws SVNException {
        if (paths == null) {
            return;
        }
        
        for (int i = 0; i < paths.length; i++) {
            File path = paths[i];
            try {
                doDiff(path, pegRevision, rN, rM, depth, useAncestry, result, changeLists);
            } catch (SVNException svne) {
                dispatchEvent(SVNEventFactory.createErrorEvent(svne.getErrorMessage(), null));
            }
        }
    }
    
    /**
     * Produces diff output which describes the delta between <code>path</code> in peg revision 
     * <code>pegRevision</code>, as it changed between <code>rN</code> and <code>rM</code>. 
     * 
     * <p/>
     * If <code>rM</code> is neither {@link SVNRevision#BASE}, nor {@link SVNRevision#WORKING}, nor 
     * {@link SVNRevision#COMMITTED}, and if, on the contrary, <code>rN</code> is one of the aforementioned 
     * revisions, then a wc-against-url diff is performed; if <code>rN</code> also is not one of those 
     * revision constants, then a url-against-url diff is performed. Otherwise it's a url-against-wc diff. 
     * 
     * <p/>
     * If <code>pegRevision is {@link SVNRevision#isValid() invalid}, behaves identically to 
     * {@link #doDiff(File, SVNRevision, File, SVNRevision, SVNDepth, boolean, OutputStream, Collection)}, 
     * using <code>path</code> for both of that function's <code>path1</code> and <code>path2</code> arguments.
     * 
     * <p/>
     * All other options are handled identically to 
     * {@link #doDiff(File, SVNRevision, File, SVNRevision, SVNDepth, boolean, OutputStream, Collection)}.
     *
     * @param  path           a Working Copy path
     * @param  pegRevision    a revision in which the repository location of <code>path</code> 
     *                        is first looked up
     * @param  rN             an old revision                          
     * @param  rM             a new revision
     * @param  depth          tree depth to process
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @param  changeLists    collection with changelist names
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} error code -
     *                        if either of <code>rN</code> and <code>rM</code> is 
     *                        {@link SVNRevision#isValid() invalid}; if both <code>rN</code> and 
     *                        <code>rM</code> are either {@link SVNRevision#WORKING} or {@link SVNRevision#BASE}
     *                        <li>exception with {@link SVNErrorCode#FS_NOT_FOUND} error code - 
     *                        <code>path</code> can not be found in either <code>rN</code> or <code>rM</code>
     *                        </ul>
     * @since                 1.2, SVN 1.5
     */
    public void doDiff(File path, SVNRevision pegRevision, SVNRevision rN, SVNRevision rM, SVNDepth depth, 
            boolean useAncestry, OutputStream result, Collection changeLists) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                    "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        boolean rNisLocal = rN == SVNRevision.BASE || rN == SVNRevision.WORKING;
        boolean rMisLocal = rM == SVNRevision.BASE || rM == SVNRevision.WORKING;
        if (rNisLocal && rMisLocal) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                    "At least one revision must be non-local for a pegged diff");            
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        path = path.getAbsoluteFile();
        getDiffGenerator().init(path.getAbsolutePath(), path.getAbsolutePath());
        if (!(rM == SVNRevision.BASE || rM == SVNRevision.WORKING || rM == SVNRevision.COMMITTED)) {
            if ((rN == SVNRevision.BASE || rN == SVNRevision.WORKING || rN == SVNRevision.COMMITTED)) {
                doDiffURLWC(path, rM, pegRevision, path, rN, true, depth, useAncestry, result, changeLists);
            } else {
                doDiffURLURL(null, path, rN, null, path, rM, pegRevision, depth, useAncestry, result);
            }
        } else {
            // head, prev,date,number will go here.
            doDiffURLWC(path, rN, pegRevision, path, rM, false, depth, useAncestry, result, changeLists);
        }
    }
    
    /**
     * Generates the differences for the specified URLs taken from the two 
     * specified revisions and writes the result to the provided output
     * stream.
     * 
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn diff -r N:M URL1 URL2'</code> command.
     * 
     * @param  url1           the first URL to be compared
     * @param  rN             a revision of <code>url1</code>
     * @param  url2           the second URL to be compared
     * @param  rM             a revision of <code>url2</code>
     * @param  recursive      <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>rN</code> and <code>rM</code> is
     *                        invalid
     *                        <li><code>url1</code> was not found in <code>rN</code>
     *                        <li><code>url2</code> was not found in <code>rM</code>
     *                        </ul>
     * @deprecated use {@link #doDiff(SVNURL, SVNRevision, SVNURL, SVNRevision, SVNDepth, boolean, OutputStream)}
     *             instead
     */
    public void doDiff(SVNURL url1, SVNRevision rN, SVNURL url2, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        doDiff(url1, rN, url2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, result);
    }
    
    /**
     * Produces diff output which describes the delta between <code>url1</code>/<code>rN</code> and 
     * <code>url2</code>/<code>rM</code>. Writes the output of the diff to <code>result</code>.
     * 
     * <p/>
     * If this client object uses {@link DefaultSVNDiffGenerator} and there was a 
     * non-<span class="javakeyword">null</span> {@link DefaultSVNDiffGenerator#setBasePath(File) base path} 
     * provided to it, the original path and modified path will have this base path stripped from the front 
     * of the respective paths. If the base path is not <span class="javakeyword">null</span> but 
     * is not a parent path of the target, an exception with the {@link SVNErrorCode#BAD_RELATIVE_PATH} error 
     * code is thrown. 
     * 
     * <p/>
     * <code>url1</code> and <code>url2</code> must both represent the same node kind -- that
     * is, if <code>url1</code> is a directory, <code>url2</code> must also be, and if <code>url1</code>
     * is a file, <code>url2</code> must also be.
     *
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, diffs fully recursively. Else if it is 
     * {@link SVNDepth#IMMEDIATES}, diffs the named paths and their file children (if any), and diffs 
     * properties of subdirectories, but does not descend further into the subdirectories. Else if 
     * {@link SVNDepth#FILES}, behaves as if for {@link SVNDepth#IMMEDIATES} except doesn't diff properties 
     * of subdirectories. If {@link SVNDepth#EMPTY}, diffs exactly the named paths but nothing underneath them.
     * 
     * <p/>
     * <code>useAncestry</code> controls whether or not items being diffed will be checked for relatedness 
     * first. Unrelated items are typically transmitted to the editor as a deletion of one thing
     * and the addition of another, but if this flag is <span class="javakeyword">true</span>, unrelated
     * items will be diffed as if they were related.
     * 
     * <p/>
     * If {@link ISVNDiffGenerator#isDiffDeleted()} returns <span class="javakeyword">true</span>, then no diff 
     * output will be generated on deleted files.
     * 
     * <p/>
     * Generated headers are encoded using {@link ISVNDiffGenerator#getEncoding()}.
     *
     * <p/>
     * Diffs output will not be generated for binary files, unless 
     * {@link ISVNDiffGenerator#isForcedBinaryDiff()} is <span class="javakeyword">true</span>, in which case 
     * diffs will be shown regardless of the content types.
     *
     * <p/>
     * If this client object uses {@link DefaultSVNDiffGenerator} then a caller can set 
     * {@link SVNDiffOptions} to it which will be used to pass additional options to the diff processes 
     * invoked to compare files.
     * 
     * @param  url1           the first URL to be compared
     * @param  rN             a revision of <code>url1</code>
     * @param  url2           the second URL to be compared
     *                        against <code>path1</code>
     * @param  rM             a revision of <code>url2</code>
     * @param  depth          tree depth to process
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @throws SVNException   in the following cases:
     *                        <ul>
     *                        <li/>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} error code - if 
     *                        either <code>rN</code> or <code>rM</code> is {@link SVNRevision#isValid() invalid}
     *                        </ul> 
     * @since  1.2, SVN 1.5
     */
    public void doDiff(SVNURL url1, SVNRevision rN, SVNURL url2, SVNRevision rM, SVNDepth depth, 
            boolean useAncestry, OutputStream result) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                    "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        getDiffGenerator().init(url1.toString(), url2.toString());
        doDiffURLURL(url1, null, rN, url2, null, rM, SVNRevision.UNDEFINED, depth, useAncestry, result);
    }
    
    /**
     * Generates the differences comparing the specified URL in a certain 
     * revision against either the specified Working Copy path or its repository 
     * location URL in the specified revision, and writes the result to the provided output
     * stream.
     * 
     * <p>
     * If <code>rN</code> is not a local revision (see {@link SVNRevision#isLocal()}),
     * then its repository location URL as it is in the revision represented by 
     * <code>rN</code> is taken for comparison with <code>url2</code>.
     * 
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn diff -r N:M PATH URL'</code> command.
     * 
     * @param  path1          a WC path  
     * @param  rN             a revision of <code>path1</code>
     * @param  url2           a repository location URL that is to be compared 
     *                        against <code>path1</code> (or its repository location)
     * @param  rM             a revision of <code>url2</code> 
     * @param  recursive      <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>rN</code> and <code>rM</code> is
     *                        invalid
     *                        <li><code>path1</code> is not under version control
     *                        <li><code>path1</code> has no URL
     *                        <li><code>url2</code> was not found in <code>rM</code>
     *                        <li>the repository location of <code>path1</code> was 
     *                        not found in <code>rN</code>
     *                        </ul>
     * @deprecated            use {@link #doDiff(File, SVNRevision, SVNURL, SVNRevision, SVNDepth, boolean, OutputStream, Collection)}
     *                        instead
     */
    public void doDiff(File path1, SVNRevision rN, SVNURL url2, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        doDiff(path1, rN, url2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, result, null);
    }

    /**
     * Produces diff output which describes the delta between <code>path1</code>/<code>rN</code> and 
     * <code>url2</code>/<code>rM</code>. Writes the output of the diff to <code>result</code>.
     * 
     * <p/>
     * If this client object uses {@link DefaultSVNDiffGenerator} and there was a 
     * non-<span class="javakeyword">null</span> {@link DefaultSVNDiffGenerator#setBasePath(File) base path} 
     * provided to it, the original path and modified path will have this base path stripped from the front 
     * of the respective paths. If the base path is not <span class="javakeyword">null</span> but 
     * is not a parent path of the target, an exception with the {@link SVNErrorCode#BAD_RELATIVE_PATH} error 
     * code is thrown. 
     * 
     * <p/>
     * <code>path1</code> and <code>url2</code> must both represent the same node kind -- that
     * is, if <code>path1</code> is a directory, <code>url2</code> must also be, and if <code>path1</code>
     * is a file, <code>url2</code> must also be.
     *
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, diffs fully recursively. Else if it is 
     * {@link SVNDepth#IMMEDIATES}, diffs the named paths and their file children (if any), and diffs 
     * properties of subdirectories, but does not descend further into the subdirectories. Else if 
     * {@link SVNDepth#FILES}, behaves as if for {@link SVNDepth#IMMEDIATES} except doesn't diff properties 
     * of subdirectories. If {@link SVNDepth#EMPTY}, diffs exactly the named paths but nothing underneath them.
     * 
     * <p/>
     * <code>useAncestry</code> controls whether or not items being diffed will be checked for relatedness 
     * first. Unrelated items are typically transmitted to the editor as a deletion of one thing
     * and the addition of another, but if this flag is <span class="javakeyword">true</span>, unrelated
     * items will be diffed as if they were related.
     * 
     * <p/>
     * If {@link ISVNDiffGenerator#isDiffDeleted()} returns <span class="javakeyword">true</span>, then no diff 
     * output will be generated on deleted files.
     * 
     * <p/>
     * Generated headers are encoded using {@link ISVNDiffGenerator#getEncoding()}.
     *
     * <p/>
     * Diffs output will not be generated for binary files, unless 
     * {@link ISVNDiffGenerator#isForcedBinaryDiff()} is <span class="javakeyword">true</span>, in which case 
     * diffs will be shown regardless of the content types.
     *
     * <p/>
     * If this client object uses {@link DefaultSVNDiffGenerator} then a caller can set 
     * {@link SVNDiffOptions} to it which will be used to pass additional options to the diff processes 
     * invoked to compare files.
     * 
     * <p/>
     * <code>changeLists</code> is a collection of <code>String</code> changelist names, used as a restrictive 
     * filter on items whose differences are reported; that is, doesn't generate diffs about any item unless
     * it's a member of one of those changelists. If <code>changeLists</code> is empty (or 
     * <span class="javakeyword">null</span>), no changelist filtering occurs.
     *
     * <p/>
     * Note: changelist filtering only applies to diffs in which at least one side of the diff represents 
     * working copy data.
     * 
     * <p/>
     * If both <code>rN</code> is either {@link SVNRevision#WORKING} or {@link SVNRevision#BASE}, then it will 
     * be a wc-against-url; otherwise, a url-against-url diff. 
     * 
     * @param  path1          a WC path
     * @param  rN             a revision of <code>path1</code>
     * @param  url2           a repository location URL that is to be compared 
     *                        against <code>path1</code> (or its repository location)
     * @param  rM             a revision of <code>url2</code>
     * @param  depth          tree depth to process
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @param  changeLists    collection with changelist names    
     * @throws SVNException   in the following cases:
     *                        <ul>
     *                        <li/>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} error code - if 
     *                        either <code>rN</code> or <code>rM</code> is {@link SVNRevision#isValid() invalid}
     *                        </ul> 
     * @since  1.2, SVN 1.5
     */
    public void doDiff(File path1, SVNRevision rN, SVNURL url2, SVNRevision rM, SVNDepth depth, 
            boolean useAncestry, OutputStream result, Collection changeLists) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        getDiffGenerator().init(path1.getAbsolutePath(), url2.toString());
        if (rN == SVNRevision.BASE || rN == SVNRevision.WORKING) {
            doDiffURLWC(url2, rM, SVNRevision.UNDEFINED, path1, rN, true, depth, useAncestry, result, changeLists);
        } else {
            doDiffURLURL(null, path1, rN, url2, null, rM, SVNRevision.UNDEFINED, depth, useAncestry, result);
        }
    }
    
    /**
     * Generates the differences comparing either the specified Working Copy path or 
     * its repository location URL in the specified revision against the specified URL 
     * in a certain revision, and writes the result to the provided output stream.
     * 
     * <p>
     * If <code>rM</code> is not a local revision (see {@link SVNRevision#isLocal()}),
     * then its repository location URL as it is in the revision represented by 
     * <code>rM</code> is taken for comparison with <code>url1</code>.
     * 
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn diff -r N:M URL PATH'</code> command.
     * 
     * @param  url1           a repository location URL 
     * @param  rN             a revision of <code>url1</code>
     * @param  path2          a WC path that is to be compared 
     *                        against <code>url1</code>
     * @param  rM             a revision of <code>path2</code>
     * @param  recursive      <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>rN</code> and <code>rM</code> is
     *                        invalid
     *                        <li><code>path2</code> is not under version control
     *                        <li><code>path2</code> has no URL
     *                        <li><code>url1</code> was not found in <code>rN</code>
     *                        <li>the repository location of <code>path2</code> was 
     *                        not found in <code>rM</code>
     *                        </ul>
     * @deprecated            use {@link #doDiff(SVNURL, SVNRevision, File, SVNRevision, SVNDepth, boolean, OutputStream, Collection)}
     *                        instead 
     *                       
     */
    public void doDiff(SVNURL url1, SVNRevision rN, File path2, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        doDiff(url1, rN, path2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, result, null);
    }

    /**
     * Produces diff output which describes the delta between <code>url1</code>/<code>rN</code> and 
     * <code>path2</code>/<code>rM</code>. Writes the output of the diff to <code>result</code>.
     * 
     * <p/>
     * If this client object uses {@link DefaultSVNDiffGenerator} and there was a 
     * non-<span class="javakeyword">null</span> {@link DefaultSVNDiffGenerator#setBasePath(File) base path} 
     * provided to it, the original path and modified path will have this base path stripped from the front 
     * of the respective paths. If the base path is not <span class="javakeyword">null</span> but 
     * is not a parent path of the target, an exception with the {@link SVNErrorCode#BAD_RELATIVE_PATH} error 
     * code is thrown. 
     * 
     * <p/>
     * <code>url1</code> and <code>path2</code> must both represent the same node kind -- that
     * is, if <code>url1</code> is a directory, <code>path2</code> must also be, and if <code>url1</code>
     * is a file, <code>path2</code> must also be.
     *
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, diffs fully recursively. Else if it is 
     * {@link SVNDepth#IMMEDIATES}, diffs the named paths and their file children (if any), and diffs 
     * properties of subdirectories, but does not descend further into the subdirectories. Else if 
     * {@link SVNDepth#FILES}, behaves as if for {@link SVNDepth#IMMEDIATES} except doesn't diff properties 
     * of subdirectories. If {@link SVNDepth#EMPTY}, diffs exactly the named paths but nothing underneath them.
     * 
     * <p/>
     * <code>useAncestry</code> controls whether or not items being diffed will be checked for relatedness 
     * first. Unrelated items are typically transmitted to the editor as a deletion of one thing
     * and the addition of another, but if this flag is <span class="javakeyword">true</span>, unrelated
     * items will be diffed as if they were related.
     * 
     * <p/>
     * If {@link ISVNDiffGenerator#isDiffDeleted()} returns <span class="javakeyword">true</span>, then no diff 
     * output will be generated on deleted files.
     * 
     * <p/>
     * Generated headers are encoded using {@link ISVNDiffGenerator#getEncoding()}.
     *
     * <p/>
     * Diffs output will not be generated for binary files, unless 
     * {@link ISVNDiffGenerator#isForcedBinaryDiff()} is <span class="javakeyword">true</span>, in which case 
     * diffs will be shown regardless of the content types.
     *
     * <p/>
     * If this client object uses {@link DefaultSVNDiffGenerator} then a caller can set 
     * {@link SVNDiffOptions} to it which will be used to pass additional options to the diff processes 
     * invoked to compare files.
     * 
     * <p/>
     * <code>changeLists</code> is a collection of <code>String</code> changelist names, used as a restrictive 
     * filter on items whose differences are reported; that is, doesn't generate diffs about any item unless
     * it's a member of one of those changelists. If <code>changeLists</code> is empty (or 
     * <span class="javakeyword">null</span>), no changelist filtering occurs.
     *
     * <p/>
     * Note: changelist filtering only applies to diffs in which at least one side of the diff represents 
     * working copy data.
     * 
     * <p/>
     * If both <code>rM</code> is either {@link SVNRevision#WORKING} or {@link SVNRevision#BASE}, then it will 
     * be a url-against-wc; otherwise, a url-against-url diff. 
     * 
     * @param  url1           a repository location URL 
     * @param  rN             a revision of <code>url1</code>
     * @param  path2          a WC path that is to be compared 
     *                        against <code>url1</code>
     * @param  rM             a revision of <code>path2</code>
     * @param  depth          tree depth to process
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @param  changeLists    collection with changelist names    
     * @throws SVNException   in the following cases:
     *                        <ul>
     *                        <li/>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} error code - if 
     *                        either <code>rN</code> or <code>rM</code> is {@link SVNRevision#isValid() invalid}
     *                        </ul> 
     * @since  1.2, SVN 1.5
     */
    public void doDiff(SVNURL url1, SVNRevision rN, File path2, SVNRevision rM, SVNDepth depth, 
            boolean useAncestry, OutputStream result, Collection changeLists) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                    "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        getDiffGenerator().init(url1.toString(), path2.getAbsolutePath());
        if (rM == SVNRevision.BASE || rM == SVNRevision.WORKING) {
            doDiffURLWC(url1, rN, SVNRevision.UNDEFINED, path2, rM, false, depth, useAncestry, result, changeLists);
        } else {
            doDiffURLURL(url1, null, rN, null, path2, rM, SVNRevision.UNDEFINED, depth, useAncestry, result);
        }
    }
    
    /**
     * Generates the differences comparing either the specified Working Copy paths or 
     * their repository location URLs (any combinations are possible) in the specified 
     * revisions and writes the result to the provided output stream.
     * 
     * <p>
     * If both <code>rN</code> and <code>rM</code> are local revisions (see {@link SVNRevision#isLocal()}),
     * then a Working Copy <code>path2</code> is compared against a Working Copy <code>path1</code>.
     * 
     * <p>
     * If <code>rN</code> is a local revision but <code>rM</code> is not, then
     * the repository location URL of <code>path2</code> as it is in the revision 
     * represented by <code>rM</code> is compared against the Working Copy <code>path1</code>.
     *
     * <p>
     * If <code>rM</code> is a local revision but <code>rN</code> is not, then
     * the Working Copy <code>path2</code> is compared against the repository location 
     * URL of <code>path1</code> as it is in the revision represented by <code>rN</code>.
     * 
     * <p>
     * If both <code>rN</code> and <code>rM</code> are non-local revisions, then the
     * repository location URL of <code>path2</code> in revision <code>rM</code> is 
     * compared against the repository location URL of <code>path1</code> in revision 
     * <code>rN</code>.
     * 
     * @param  path1          a WC path
     * @param  rN             a revision of <code>path1</code>
     * @param  path2          a WC path that is to be compared 
     *                        against <code>path1</code>
     * @param  rM             a revision of <code>path2</code>
     * @param  recursive      <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>rN</code> and <code>rM</code> is
     *                        invalid
     *                        <li><code>path1</code> is not under version control
     *                        <li><code>path1</code> has no URL
     *                        <li><code>path2</code> is not under version control
     *                        <li><code>path2</code> has no URL
     *                        <li>the repository location of <code>path1</code> was 
     *                        not found in <code>rN</code>
     *                        <li>the repository location of <code>path2</code> was 
     *                        not found in <code>rM</code>
     *                        <li>both <code>rN</code> and <code>rM</code> are local,
     *                        but either <code>path1</code> does not equal <code>path2</code>,
     *                        or <code>rN</code> is not {@link SVNRevision#BASE}, or
     *                        <code>rM</code> is not {@link SVNRevision#WORKING} 
     *                        </ul>
     * @deprecated            use {@link #doDiff(File, SVNRevision, File, SVNRevision, SVNDepth, boolean, OutputStream, Collection)} 
     *                        instead
     */
    public void doDiff(File path1, SVNRevision rN, File path2, SVNRevision rM, boolean recursive, boolean useAncestry,
            OutputStream result) throws SVNException {
        doDiff(path1, rN, path2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, result, null);
    }
    
    /**
     * Produces diff output which describes the delta between <code>path1</code>/<code>rN</code> and 
     * <code>path2</code>/<code>rM</code>. Writes the output of the diff to <code>result</code>.
     * 
     * <p/>
     * If this client object uses {@link DefaultSVNDiffGenerator} and there was a 
     * non-<span class="javakeyword">null</span> {@link DefaultSVNDiffGenerator#setBasePath(File) base path} 
     * provided to it, the original path and modified path will have this base path stripped from the front 
     * of the respective paths. If the base path is not <span class="javakeyword">null</span> but 
     * is not a parent path of the target, an exception with the {@link SVNErrorCode#BAD_RELATIVE_PATH} error 
     * code is thrown. 
     * 
     * <p/>
     * <code>path1</code> and <code>path2</code> must both represent the same node kind -- that
     * is, if <code>path1</code> is a directory, <code>path2</code> must also be, and if <code>path1</code>
     * is a file, <code>path2</code> must also be.
     *
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, diffs fully recursively. Else if it is 
     * {@link SVNDepth#IMMEDIATES}, diffs the named paths and their file children (if any), and diffs 
     * properties of subdirectories, but does not descend further into the subdirectories. Else if 
     * {@link SVNDepth#FILES}, behaves as if for {@link SVNDepth#IMMEDIATES} except doesn't diff properties 
     * of subdirectories. If {@link SVNDepth#EMPTY}, diffs exactly the named paths but nothing underneath them.
     * 
     * <p/>
     * <code>useAncestry</code> controls whether or not items being diffed will be checked for relatedness 
     * first. Unrelated items are typically transmitted to the editor as a deletion of one thing
     * and the addition of another, but if this flag is <span class="javakeyword">true</span>, unrelated
     * items will be diffed as if they were related.
     * 
     * <p/>
     * If {@link ISVNDiffGenerator#isDiffDeleted()} returns <span class="javakeyword">true</span>, then no diff 
     * output will be generated on deleted files.
     * 
     * <p/>
     * Generated headers are encoded using {@link ISVNDiffGenerator#getEncoding()}.
     *
     * <p/>
     * Diffs output will not be generated for binary files, unless 
     * {@link ISVNDiffGenerator#isForcedBinaryDiff()} is <span class="javakeyword">true</span>, in which case 
     * diffs will be shown regardless of the content types.
     *
     * <p/>
     * If this client object uses {@link DefaultSVNDiffGenerator} then a caller can set 
     * {@link SVNDiffOptions} to it which will be used to pass additional options to the diff processes 
     * invoked to compare files.
     * 
     * <p/>
     * <code>changeLists</code> is a collection of <code>String</code> changelist names, used as a restrictive 
     * filter on items whose differences are reported; that is, doesn't generate diffs about any item unless
     * it's a member of one of those changelists. If <code>changeLists</code> is empty (or 
     * <span class="javakeyword">null</span>), no changelist filtering occurs.
     *
     * <p/>
     * Note: changelist filtering only applies to diffs in which at least one side of the diff represents 
     * working copy data.
     * 
     * <p/>
     * If both <code>rN</code> and <code>rM</code> are either {@link SVNRevision#WORKING} or 
     * {@link SVNRevision#BASE}, then it will be a wc-against-wc diff operation, in which case no repository 
     * access is needed. If only <code>rN</code> or <code>rM</code> is, then it will be a wc-against-url or 
     * url-against-wc diff correspondingly; if neither - a url-against-url diff. 
     * 
     * @param  path1          a WC path
     * @param  rN             a revision of <code>path1</code>
     * @param  path2          a WC path that is to be compared 
     *                        against <code>path1</code>
     * @param  rM             a revision of <code>path2</code>
     * @param  depth          tree depth to process
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  result         the target {@link java.io.OutputStream} where
     *                        the differences will be written to
     * @param  changeLists    collection with changelist names    
     * @throws SVNException   in the following cases:
     *                        <ul>
     *                        <li/>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} error code - if 
     *                        either <code>rN</code> or <code>rM</code> is {@link SVNRevision#isValid() invalid}
     *                        </ul> 
     * @since  1.2, SVN 1.5
     */
    public void doDiff(File path1, SVNRevision rN, File path2, SVNRevision rM, SVNDepth depth, 
            boolean useAncestry, OutputStream result, Collection changeLists) throws SVNException {
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }

        boolean isPath1Local = rN == SVNRevision.WORKING || rN == SVNRevision.BASE; 
        boolean isPath2Local = rM == SVNRevision.WORKING || rM == SVNRevision.BASE;
        getDiffGenerator().init(path1.getAbsolutePath(), path2.getAbsolutePath());
        if (isPath1Local && isPath2Local) {
            doDiffWCWC(path1, rN, path2, rM, depth, useAncestry, result, changeLists);
        } else if (isPath1Local) {
            doDiffURLWC(path2, rM, SVNRevision.UNDEFINED, path1, rN, true, depth, useAncestry, result, changeLists);
        } else if (isPath2Local) {
            doDiffURLWC(path1, rN, SVNRevision.UNDEFINED, path2, rM, false, depth, useAncestry, result, changeLists);
        } else {
            doDiffURLURL(null, path1, rN, null, path2, rM, SVNRevision.UNDEFINED, depth, useAncestry, result);
        }
    }

    /**
     * Diffs one path against another one providing short status-like change information to the provided
     * handler. This method functionality is equivalent to the 'svn diff --summarize' command.
     * 
     * @param  path1             the path of a left-hand item to diff
     * @param  rN                a revision of <code>path1</code>
     * @param  path2             the path of a right-hand item to diff
     * @param  rM                a revision of <code>path2</code>
     * @param  recursive         controls whether operation must recurse or not 
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  handler           a diff status handler
     * @throws SVNException
     * @since                    1.1, new in Subversion 1.4
     * @deprecated               use {@link #doDiffStatus(File, SVNRevision, File, SVNRevision, SVNDepth, boolean, ISVNDiffStatusHandler)} 
     *                           instead
     */
    public void doDiffStatus(File path1, SVNRevision rN, File path2, SVNRevision rM, boolean recursive, 
            boolean useAncestry, ISVNDiffStatusHandler handler) throws SVNException {
        doDiffStatus(path1, rN, path2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, handler);
    }

    /**
     * Produces a diff summary which lists the changed items between <code>path</code> in peg revision 
     * <code>pegRevision</code>, as it changed between <code>rN</code> and <code>rM</code>. 
     *
     * <p/>
     * If <code>pegRevision</code> is {@link SVNRevision#isValid() invalid}, behaves identically to 
     * {@link #doDiffStatus(File, SVNRevision, File, SVNRevision, SVNDepth, boolean, ISVNDiffStatusHandler)}, 
     * using <code>path</code> for both of that method's <code>path1</code> and <code>path2</code> argments.
     * 
     * <p/>
     * The method may report false positives if <code>useAncestry</code> is 
     * <span class="javakeyword">false</span>, as described in the documentation for 
     * {@link #doDiffStatus(File, SVNRevision, File, SVNRevision, SVNDepth, boolean, ISVNDiffStatusHandler)}.
     * 
     * <p/>
     * Calls <code>handler</code> for each difference with an {@link SVNDiffStatus} object describing the 
     * difference.
     * 
     * <p/>
     * See {@link #doDiff(File, SVNRevision, SVNRevision, SVNRevision, SVNDepth, boolean, OutputStream, Collection)} 
     * for a description of the other parameters.
     *
     * @param  path            working copy path
     * @param  rN              left-hand revision
     * @param  rM              right-hand revision
     * @param  pegRevision     a revision in which the repository location of <code>path</code> 
     *                         is first looked up
     * @param  depth           tree depth to process
     * @param  useAncestry     if <span class="javakeyword">true</span> then
     *                         the paths ancestry will be noticed while calculating differences,
     *                         otherwise not
     * @param  handler         a diff status handler
     * @throws SVNException   
     * @since                  1.2, SVN 1.5
     */
    public void doDiffStatus(File path, SVNRevision rN, SVNRevision rM, SVNRevision pegRevision, SVNDepth depth, 
            boolean useAncestry, ISVNDiffStatusHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }
        
        if (pegRevision == null) {
            pegRevision = SVNRevision.UNDEFINED;
        }
        
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                    "Not all required revisions are specified");            
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        
        boolean isPath1Local = rN == SVNRevision.WORKING || rN == SVNRevision.BASE; 
        boolean isPath2Local = rM == SVNRevision.WORKING || rM == SVNRevision.BASE;
        if (isPath1Local || isPath2Local) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                    "Summarizing diff can only compare repository to repository");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        } 
        doDiffURLURL(null, path, rN, null, path, rM, pegRevision, depth, useAncestry, handler);        
    }
    
    /**
     * Produces a diff summary which lists the changed items between
     * <code>path1</code>/<code>rN</code> and <code>path2</code>/<code>rM</code> without creating text
     * deltas. 
     * 
     * <p/>
     * The function may report false positives if <code>ignoreAncestry</code> is 
     * <span class="javakeyword">false</span>, since a file might have been modified between two revisions, 
     * but still have the same contents.
     * 
     * <p/>
     * Calls <code>handler</code> for each difference with an {@link SVNDiffStatus} object describing the 
     * difference.
     * 
     * <p/>
     * See {@link #doDiff(File, SVNRevision, File, SVNRevision, SVNDepth, boolean, OutputStream, Collection)} 
     * for a description of the other parameters.
     *
     * @param  path1             the path of a left-hand item to diff
     * @param  rN                a revision of <code>path1</code>
     * @param  path2             the path of a right-hand item to diff
     * @param  rM                a revision of <code>path2</code>
     * @param  depth             tree depth to process 
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  handler           a diff status handler
     * @throws SVNException      in the following cases:
     *                           <ul>
     *                           <li/>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} error code - if 
     *                           either <code>rN</code> or <code>rM</code> is {@link SVNRevision#isValid() invalid}
     *                           <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - if 
     *                           either of <code>rM</code> or </code>rN</code> is either {@link SVNRevision#WORKING}
     *                           or {@link SVNRevision#BASE} 
     *                           </ul> 
     * @since                    1.2, SVN 1.5
     */
    public void doDiffStatus(File path1, SVNRevision rN, File path2, SVNRevision rM, SVNDepth depth, 
            boolean useAncestry, ISVNDiffStatusHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                    "Not all required revisions are specified");            
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }

        boolean isPath1Local = rN == SVNRevision.WORKING || rN == SVNRevision.BASE; 
        boolean isPath2Local = rM == SVNRevision.WORKING || rM == SVNRevision.BASE;
        if (isPath1Local || isPath2Local) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                    "Summarizing diff can only compare repository to repository");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        } 
        doDiffURLURL(null, path1, rN, null, path2, rM, SVNRevision.UNDEFINED, depth, useAncestry, handler);        
    }
    
    /**
     * Diffs a path against a url providing short status-like change information to the provided
     * handler. This method functionality is equivalent to the 'svn diff --summarize' command.
     * 
     * @param  path1             the path of a left-hand item to diff
     * @param  rN                a revision of <code>path1</code>
     * @param  url2              the url of a right-hand item to diff
     * @param  rM                a revision of <code>url2</code>
     * @param  recursive         controls whether operation must recurse or not 
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  handler           a diff status handler
     * @throws SVNException
     * @since                    1.1, new in Subversion 1.4
     * @deprecated               use {@link #doDiffStatus(File, SVNRevision, SVNURL, SVNRevision, SVNDepth, boolean, ISVNDiffStatusHandler)}
     *                           instead
     */
    public void doDiffStatus(File path1, SVNRevision rN, SVNURL url2, SVNRevision rM, boolean recursive, 
            boolean useAncestry, ISVNDiffStatusHandler handler) throws SVNException {
        doDiffStatus(path1, rN, url2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, handler);
    }

    /**
     * Produces a diff summary which lists the changed items between
     * <code>path1</code>/<code>rN</code> and <code>url2</code>/<code>rM</code> without creating text
     * deltas. 
     * 
     * <p/>
     * The function may report false positives if <code>ignoreAncestry</code> is 
     * <span class="javakeyword">false</span>, since a file might have been modified between two revisions, 
     * but still have the same contents.
     * 
     * <p/>
     * Calls <code>handler</code> for each difference with an {@link SVNDiffStatus} object describing the 
     * difference.
     * 
     * <p/>
     * See {@link #doDiff(File, SVNRevision, SVNURL, SVNRevision, SVNDepth, boolean, OutputStream, Collection)} 
     * for a description of the other parameters.
     *
     * @param  path1             the path of a left-hand item to diff
     * @param  rN                a revision of <code>path1</code>
     * @param  url2              repository url as a right-hand item
     * @param  rM                a revision of <code>url2</code>
     * @param  depth             tree depth to process 
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  handler           a diff status handler
     * @throws SVNException      in the following cases:
     *                           <ul>
     *                           <li/>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} error code - if 
     *                           either <code>rN</code> or <code>rM</code> is {@link SVNRevision#isValid() invalid}
     *                           <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - if 
     *                           either of <code>rM</code> or </code>rN</code> is either {@link SVNRevision#WORKING}
     *                           or {@link SVNRevision#BASE} 
     *                           </ul> 
     * @since                    1.2, SVN 1.5
     */
    public void doDiffStatus(File path1, SVNRevision rN, SVNURL url2, SVNRevision rM, SVNDepth depth, 
            boolean useAncestry, ISVNDiffStatusHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Not all required revisions are specified");            
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        if (rN == SVNRevision.BASE || rN == SVNRevision.WORKING) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Summarizing diff can only compare repository to repository");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        } else {
            doDiffURLURL(null, path1, rN, url2, null, rM, SVNRevision.UNDEFINED, depth, useAncestry, handler);
        }
    }

    /**
     * Diffs a url against a path providing short status-like change information to the provided
     * handler. This method functionality is equivalent to the 'svn diff --summarize' command.
     * 
     * @param  url1              the url of a left-hand item to diff
     * @param  rN                a revision of <code>url1</code>
     * @param  path2             the path of a right-hand item to diff
     * @param  rM                a revision of <code>path2</code>
     * @param  recursive         controls whether operation must recurse or not 
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  handler           a diff status handler
     * @throws SVNException
     * @since                    1.1, new in Subversion 1.4
     * @deprecated               use {@link #doDiffStatus(SVNURL, SVNRevision, File, SVNRevision, SVNDepth, boolean, ISVNDiffStatusHandler)}
     *                           instead
     */
    public void doDiffStatus(SVNURL url1, SVNRevision rN, File path2, SVNRevision rM, boolean recursive, 
            boolean useAncestry, ISVNDiffStatusHandler handler) throws SVNException {
        doDiffStatus(url1, rN, path2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, handler);
    }
    
    /**
     * Produces a diff summary which lists the changed items between
     * <code>url1</code>/<code>rN</code> and <code>path2</code>/<code>rM</code> without creating text
     * deltas. 
     * 
     * <p/>
     * The function may report false positives if <code>ignoreAncestry</code> is 
     * <span class="javakeyword">false</span>, since a file might have been modified between two revisions, 
     * but still have the same contents.
     * 
     * <p/>
     * Calls <code>handler</code> for each difference with an {@link SVNDiffStatus} object describing the 
     * difference.
     * 
     * <p/>
     * See {@link #doDiff(SVNURL, SVNRevision, File, SVNRevision, SVNDepth, boolean, OutputStream, Collection)} 
     * for a description of the other parameters.
     *
     * @param  url1              repository url as a left-hand item
     * @param  rN                a revision of <code>url1</code>
     * @param  path2             the path of a right-hand item to diff
     * @param  rM                a revision of <code>path2</code>
     * @param  depth             tree depth to process 
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  handler           a diff status handler
     * @throws SVNException      in the following cases:
     *                           <ul>
     *                           <li/>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} error code - if 
     *                           either <code>rN</code> or <code>rM</code> is {@link SVNRevision#isValid() invalid}
     *                           <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - if 
     *                           either of <code>rM</code> or </code>rN</code> is either {@link SVNRevision#WORKING}
     *                           or {@link SVNRevision#BASE} 
     *                           </ul> 
     * @since                    1.2, SVN 1.5
     */
    public void doDiffStatus(SVNURL url1, SVNRevision rN, File path2, SVNRevision rM, SVNDepth depth, 
            boolean useAncestry, ISVNDiffStatusHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }   
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        if (rM == SVNRevision.BASE || rM == SVNRevision.WORKING) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Summarizing diff can only compare repository to repository");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        } else {
            doDiffURLURL(url1, null, rN, null, path2, rM, SVNRevision.UNDEFINED, depth, useAncestry, handler);
        }
    }

    /**
     * Diffs one url against another one providing short status-like change information to the provided
     * handler. This method functionality is equivalent to the 'svn diff --summarize' command.
     * 
     * @param  url1              the url of a left-hand item to diff
     * @param  rN                a revision of <code>url1</code>
     * @param  url2              the url of a right-hand item to diff
     * @param  rM                a revision of <code>url2</code>
     * @param  recursive         controls whether operation must recurse or not 
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  handler           a diff status handler
     * @throws SVNException
     * @since                    1.1, new in Subversion 1.4
     * @deprecated               use {@link #doDiffStatus(SVNURL, SVNRevision, SVNURL, SVNRevision, SVNDepth, boolean, ISVNDiffStatusHandler)} 
     *                           instead
     */
    public void doDiffStatus(SVNURL url1, SVNRevision rN, SVNURL url2, SVNRevision rM, boolean recursive, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {
        doDiffStatus(url1, rN, url2, rM, SVNDepth.getInfinityOrFilesDepth(recursive), useAncestry, handler);
    }

    /**
     * Produces a diff summary which lists the changed items between <code>url</code> in peg revision 
     * <code>pegRevision</code>, as it changed between <code>rN</code> and <code>rM</code>. 
     *
     * <p/>
     * If <code>pegRevision</code> is {@link SVNRevision#isValid() invalid}, behaves identically to 
     * {@link #doDiffStatus(SVNURL, SVNRevision, SVNURL, SVNRevision, SVNDepth, boolean, ISVNDiffStatusHandler)}, 
     * using <code>url</code> for both of that method's <code>url1</code> and <code>url2</code> argments.
     * 
     * <p/>
     * The method may report false positives if <code>useAncestry</code> is 
     * <span class="javakeyword">false</span>, as described in the documentation for 
     * {@link #doDiffStatus(SVNURL, SVNRevision, SVNURL, SVNRevision, SVNDepth, boolean, ISVNDiffStatusHandler)}.
     * 
     * <p/> 
     * Calls <code>handler</code> for each difference with an {@link SVNDiffStatus} object describing the 
     * difference.
     * 
     * <p/>
     * See {@link #doDiff(SVNURL, SVNRevision, SVNRevision, SVNRevision, SVNDepth, boolean, OutputStream)} 
     * for a description of the other parameters.
     *
     * @param  url             repository url
     * @param  rN              left-hand revision
     * @param  rM              right-hand revision
     * @param  pegRevision     a revision in which the repository location of <code>path</code> 
     *                         is first looked up
     * @param  depth           tree depth to process
     * @param  useAncestry     if <span class="javakeyword">true</span> then
     *                         the paths ancestry will be noticed while calculating differences,
     *                         otherwise not
     * @param  handler         a diff status handler
     * @throws SVNException   
     * @since                  1.2, SVN 1.5
     */
    public void doDiffStatus(SVNURL url, SVNRevision rN, SVNRevision rM, SVNRevision pegRevision, SVNDepth depth, boolean useAncestry,
            ISVNDiffStatusHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }

        if (pegRevision == null) {
            pegRevision = SVNRevision.UNDEFINED;
        }
        
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        doDiffURLURL(url, null, rN, url, null, rM, pegRevision, depth, useAncestry, handler);
    }

    /**
     * Produces a diff summary which lists the changed items between
     * <code>url1</code>/<code>rN</code> and <code>url2</code>/<code>rM</code> without creating text
     * deltas. 
     * 
     * <p/>
     * The function may report false positives if <code>ignoreAncestry</code> is 
     * <span class="javakeyword">false</span>, since a file might have been modified between two revisions, 
     * but still have the same contents.
     * 
     * <p/>
     * Calls <code>handler</code> for each difference with an {@link SVNDiffStatus} object describing the 
     * difference.
     * 
     * <p/>
     * See {@link #doDiff(SVNURL, SVNRevision, SVNURL, SVNRevision, SVNDepth, boolean, OutputStream)} 
     * for a description of the other parameters.
     * 
     * @param  url1              the url of a left-hand item to diff
     * @param  rN                a revision of <code>url1</code>
     * @param  url2              the url of a right-hand item to diff
     * @param  rM                a revision of <code>url2</code>
     * @param  depth             tree depth to process
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  handler           a diff status handler
     * @throws SVNException
     * @since                    1.2, SVN 1.5
     */
    public void doDiffStatus(SVNURL url1, SVNRevision rN, SVNURL url2, SVNRevision rM, SVNDepth depth, 
            boolean useAncestry, ISVNDiffStatusHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }
        if (!rN.isValid() || !rM.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Both rN and rM revisions should be specified");            
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        doDiffURLURL(url1, null, rN, url2, null, rM, SVNRevision.UNDEFINED, depth, useAncestry, handler);
    }
    
    /**
     * Applies the differences between two sources (using Working Copy paths to 
     * get corresponding URLs of the sources) to a Working Copy path.
     *
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn merge sourceWCPATH1@rev1 sourceWCPATH2@rev2 WCPATH'</code> command.
     * 
     * <p>
     * If you need only to try merging your file(s) without actual merging, you
     * should set <code>dryRun</code> to <span class="javakeyword">true</span>.
     * Your event handler will be dispatched status type information on the target 
     * path(s). If a path can be successfully merged, the status type will be
     * {@link SVNStatusType#MERGED} for that path.  
     * 
     * @param  path1          the first source path
     * @param  revision1      a revision of <code>path1</code>
     * @param  path2          the second source path which URL is to be compared
     *                        against the URL of <code>path1</code>
     * @param  revision2      a revision of <code>path2</code>
     * @param  dstPath        the target path to which the result should
     *                        be applied 
     * @param  recursive     <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>revision1</code> and <code>revision2</code> is
     *                        invalid
     *                        <li><code>path1</code> has no URL
     *                        <li><code>path2</code> has no URL
     *                        <li>the repository location of <code>path1</code> was 
     *                        not found in <code>revision1</code>
     *                        <li>the repository location of <code>path2</code> was 
     *                        not found in <code>revision2</code>
     *                        <li><code>dstPath</code> is not under version control
     *                        </ul>
     * @deprecated            use {@link #doMerge(File, SVNRevision, File, SVNRevision, File, SVNDepth, boolean, boolean, boolean, boolean)}
     *                        instead
     */
    public void doMerge(File path1, SVNRevision revision1, File path2, SVNRevision revision2, File dstPath, 
            boolean recursive, boolean useAncestry, boolean force, boolean dryRun) throws SVNException {
        doMerge(path1, revision1, path2, revision2, dstPath, SVNDepth.getInfinityOrFilesDepth(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    /** 
     * Merges changes from <code>path1</code>/<code>revision1</code> to <code>path2</code>/<code>revision2</code> 
     * into the working-copy path <code>dstPath</code>.
     * 
     * <p/>
     * <code>path1</code> and <code>path2</code> must both represent the same node kind - that
     * is, if <code>path1</code> is a directory, <code>path2</code> must also be, and if <code>path1</code>
     * is a file, <code>path2</code> must also be.
     * 
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, merges fully recursively. Else if 
     * {@link SVNDepth#IMMEDIATES}, merges changes at most to files that are immediate children of 
     * <code>dstPath</code> and to directory properties of <code>dstPath</code> and its immediate subdirectory 
     * children. Else if {@link SVNDepth#FILES}, merges at most to immediate file children of 
     * <code>dstPath</code> and to <code>dstPath</code> itself. Else if {@link SVNDepth#EMPTY}, applies changes 
     * only to <code>dstPath</code> (i.e., directory property changes only).
     *
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#UNKNOWN}, uses the depth of <code>dstPath</code>.
     *
     * <p/>
     * Uses <code>useAncestry</code> to control whether or not items being diffed will be checked for 
     * relatedness first. Unrelated items are typically transmitted to the editor as a deletion of one thing
     * and the addition of another, but if this flag is <span class="javakeyword">true</span>, unrelated
     * items will be diffed as if they were related.
     *
     * <p/>
     * If <code>force</code> is not set and the merge involves deleting locally modified or unversioned items 
     * the operation will fail. If <code>force</code> is set such items will be deleted.
     * 
     * <p/>
     * {@link #getMergeOptions() merge options} is used to pass arguments to the merge processes (internal or 
     * external). 
     * 
     * <p/>
     * If the caller's {@link ISVNEventHandler} is not <span class="javakeyword">null</span>, then it will be 
     * called once for each merged target.
     * 
     * <p>
     * If <code>recordOnly</code> is <span class="javakeyword">true</span>, the merge isn't actually performed, 
     * but the mergeinfo for the revisions which would've been merged is recorded in the working copy (and must 
     * be subsequently committed back to the repository).
     * 
     * <p/>
     * If <code>dryRun</code> is <span class="javakeyword">true</span>, the merge is carried out, and full 
     * notification feedback is provided, but the working copy is not modified.
     *
     * <p/>
     * Note: this method requires repository access. 
     * 
     * @param  path1             left-hand working copy path
     * @param  revision1         revision of <code>path1</code>
     * @param  path2             right-hand working copy path
     * @param  revision2         revision of <code>path2</code>
     * @param  dstPath           target working copy path
     * @param  depth             tree depth to process
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  force             <span class="javakeyword">true</span> to
     *                           force the operation to run
     * @param  dryRun            if <span class="javakeyword">true</span> then runs merge without any file 
     *                           changes 
     * @param  recordOnly        if <span class="javakeyword">true</span>, records only the rusult of merge - 
     *                           mergeinfo data
     * @throws SVNException      in the following cases:
     *                           <ul>
     *                           <li/>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} error code - 
     *                           if either <code>revision1</code> or <code>revision2</code> is 
     *                           {@link SVNRevision#isValid() invalid}
     *                           <li/>exception with {@link SVNErrorCode#ENTRY_MISSING_URL} error code - 
     *                           if failed to retrieve url of either <code>path1</code> or <code>path2</code>
     *                           </ul>
     * @since                    1.2, SVN 1.5
     */
    public void doMerge(File path1, SVNRevision revision1, File path2, SVNRevision revision2, File dstPath, 
            SVNDepth depth, boolean useAncestry, boolean force, boolean dryRun, boolean recordOnly) throws SVNException {
        path1 = path1.getAbsoluteFile();
        path2 = path2.getAbsoluteFile();
        dstPath = dstPath.getAbsoluteFile();
        /*
         * Same as 2. merge sourceWCPATH1@N sourceWCPATH2@M [WCPATH]
         * or      3. merge -r N:M SOURCE[@REV] [WCPATH]
         * where SOURCE is a path and path1 and path2 are the same.
         */
        SVNURL url1 = getURL(path1);
        if (url1 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", 
                    path1);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNURL url2 = getURL(path2);
        if (url2 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", 
                    path2);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        runMerge(url1, revision1, url2, revision2, dstPath, depth, dryRun, force, !useAncestry, recordOnly);
    }
    
    /**
     * Applies the differences between two sources (a source URL against the 
     * repository location URL of a source Working Copy path) to a Working Copy 
     * path.
     * 
     * <p>
     * If you need only to try merging your file(s) without actual merging, you
     * should set <code>dryRun</code> to <span class="javakeyword">true</span>.
     * Your event handler will be dispatched status type information on the target 
     * path(s). If a path can be successfully merged, the status type will be
     * {@link SVNStatusType#MERGED} for that path.  
     * 
     * @param  path1          the first source - a WC path 
     * @param  revision1      a revision of <code>path1</code>
     * @param  url2           the second source - a URL that is to be compared 
     *                        against the URL of <code>path1</code>
     * @param  revision2      a revision of <code>url2</code>
     * @param  dstPath        the target path to which the result should
     *                        be applied
     * @param  recursive     <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>revision1</code> and <code>revision2</code> is
     *                        invalid
     *                        <li><code>path1</code> has no URL
     *                        <li>the repository location of <code>path1</code> was 
     *                        not found in <code>revision1</code>
     *                        <li><code>url2</code> was not found in 
     *                        <code>revision2</code>
     *                        <li><code>dstPath</code> is not under version control
     *                        </ul>
     * @deprecated            use {@link #doMerge(File, SVNRevision, SVNURL, SVNRevision, File, SVNDepth, boolean, boolean, boolean, boolean)}
     *                        instead
     */
    public void doMerge(File path1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, File dstPath, boolean recursive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        doMerge(path1, revision1, url2, revision2, dstPath, SVNDepth.getInfinityOrFilesDepth(recursive), 
                useAncestry, force, dryRun, false);
    }

    /** 
     * Merges changes from <code>path1</code>/<code>revision1</code> to <code>url2</code>/<code>revision2</code> 
     * into the working-copy path <code>dstPath</code>.
     * 
     * <p/>
     * <code>path1</code> and <code>url2</code> must both represent the same node kind - that
     * is, if <code>path1</code> is a directory, <code>url2</code> must also be, and if <code>path1</code>
     * is a file, <code>url2</code> must also be.
     * 
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, merges fully recursively. Else if 
     * {@link SVNDepth#IMMEDIATES}, merges changes at most to files that are immediate children of 
     * <code>dstPath</code> and to directory properties of <code>dstPath</code> and its immediate subdirectory 
     * children. Else if {@link SVNDepth#FILES}, merges at most to immediate file children of 
     * <code>dstPath</code> and to <code>dstPath</code> itself. Else if {@link SVNDepth#EMPTY}, applies changes 
     * only to <code>dstPath</code> (i.e., directory property changes only).
     *
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#UNKNOWN}, uses the depth of <code>dstPath</code>.
     *
     * <p/>
     * Uses <code>useAncestry</code> to control whether or not items being diffed will be checked for 
     * relatedness first. Unrelated items are typically transmitted to the editor as a deletion of one thing
     * and the addition of another, but if this flag is <span class="javakeyword">true</span>, unrelated
     * items will be diffed as if they were related.
     *
     * <p/>
     * If <code>force</code> is not set and the merge involves deleting locally modified or unversioned items 
     * the operation will fail. If <code>force</code> is set such items will be deleted.
     * 
     * <p/>
     * {@link #getMergeOptions() merge options} is used to pass arguments to the merge processes (internal or 
     * external). 
     * 
     * <p/>
     * If the caller's {@link ISVNEventHandler} is not <span class="javakeyword">null</span>, then it will be 
     * called once for each merged target.
     * 
     * <p>
     * If <code>recordOnly</code> is <span class="javakeyword">true</span>, the merge isn't actually performed, 
     * but the mergeinfo for the revisions which would've been merged is recorded in the working copy (and must 
     * be subsequently committed back to the repository).
     * 
     * <p/>
     * If <code>dryRun</code> is <span class="javakeyword">true</span>, the merge is carried out, and full 
     * notification feedback is provided, but the working copy is not modified.
     *
     * <p/>
     * Note: this method requires repository access. 
     * 
     * @param  path1             left-hand item - working copy path
     * @param  revision1         revision of <code>path1</code>
     * @param  url2              right-hand item - repository url
     * @param  revision2         revision of <code>url2</code>
     * @param  dstPath           target working copy path
     * @param  depth             tree depth to process
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  force             <span class="javakeyword">true</span> to
     *                           force the operation to run
     * @param  dryRun            if <span class="javakeyword">true</span> then runs merge without any file 
     *                           changes 
     * @param  recordOnly        if <span class="javakeyword">true</span>, records only the rusult of merge - 
     *                           mergeinfo data
     * @throws SVNException      in the following cases:
     *                           <ul>
     *                           <li/>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} error code - 
     *                           if either <code>revision1</code> or <code>revision2</code> is 
     *                           {@link SVNRevision#isValid() invalid}
     *                           <li/>exception with {@link SVNErrorCode#ENTRY_MISSING_URL} error code - 
     *                           if failed to retrieve the repository url of <code>path1</code>
     *                           </ul>
     * @since                    1.2, SVN 1.5
     */
    public void doMerge(File path1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, File dstPath, 
            SVNDepth depth, boolean useAncestry, boolean force, boolean dryRun, boolean recordOnly) throws SVNException {
        path1 = path1.getAbsoluteFile();
        dstPath = dstPath.getAbsoluteFile();
        SVNURL url1 = getURL(path1);
        if (url1 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path1);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        runMerge(url1, revision1, url2, revision2, dstPath, depth, dryRun, force, !useAncestry, 
                 recordOnly);
    }
    
    /**
     * Applies the differences between two sources (the repository location URL of 
     * a source Working Copy against a source URL) to a Working Copy path.
     * 
     * <p>
     * If you need only to try merging your file(s) without actual merging, you
     * should set <code>dryRun</code> to <span class="javakeyword">true</span>.
     * Your event handler will be dispatched status type information on the target 
     * path(s). If a path can be successfully merged, the status type will be
     * {@link SVNStatusType#MERGED} for that path.  
     * 
     * @param  url1           the first source - a URL
     * @param  revision1      a revision of <code>url1</code>
     * @param  path2          the second source - a WC path that is to be compared 
     *                        against <code>url1</code>
     * @param  revision2      a revision of <code>path2</code>
     * @param  dstPath        the target path to which the result should
     *                        be applied
     * @param  recursive     <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>revision1</code> and <code>revision2</code> is
     *                        invalid
     *                        <li><code>path2</code> has no URL
     *                        <li><code>url1</code> was not found in 
     *                        <code>revision1</code>
     *                        <li>the repository location of <code>path2</code> was 
     *                        not found in <code>revision2</code>
     *                        <li><code>dstPath</code> is not under version control
     *                        </ul>
     * @deprecated            use {@link #doMerge(SVNURL, SVNRevision, File, SVNRevision, File, SVNDepth, boolean, boolean, boolean, boolean)}
     *                        instead
     */
    public void doMerge(SVNURL url1, SVNRevision revision1, File path2, SVNRevision revision2, File dstPath, boolean recursive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        doMerge(url1, revision1, path2, revision2, dstPath, SVNDepth.getInfinityOrFilesDepth(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    /** 
     * Merges changes from <code>url1</code>/<code>revision1</code> to <code>path2</code>/<code>revision2</code> 
     * into the working-copy path <code>dstPath</code>.
     * 
     * <p/>
     * <code>url1</code> and <code>path2</code> must both represent the same node kind - that
     * is, if <code>url1</code> is a directory, <code>path2</code> must also be, and if <code>url1</code>
     * is a file, <code>path2</code> must also be.
     * 
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, merges fully recursively. Else if 
     * {@link SVNDepth#IMMEDIATES}, merges changes at most to files that are immediate children of 
     * <code>dstPath</code> and to directory properties of <code>dstPath</code> and its immediate subdirectory 
     * children. Else if {@link SVNDepth#FILES}, merges at most to immediate file children of 
     * <code>dstPath</code> and to <code>dstPath</code> itself. Else if {@link SVNDepth#EMPTY}, applies changes 
     * only to <code>dstPath</code> (i.e., directory property changes only).
     *
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#UNKNOWN}, uses the depth of <code>dstPath</code>.
     *
     * <p/>
     * Uses <code>useAncestry</code> to control whether or not items being diffed will be checked for 
     * relatedness first. Unrelated items are typically transmitted to the editor as a deletion of one thing
     * and the addition of another, but if this flag is <span class="javakeyword">true</span>, unrelated
     * items will be diffed as if they were related.
     *
     * <p/>
     * If <code>force</code> is not set and the merge involves deleting locally modified or unversioned items 
     * the operation will fail. If <code>force</code> is set such items will be deleted.
     * 
     * <p/>
     * {@link #getMergeOptions() merge options} is used to pass arguments to the merge processes (internal or 
     * external). 
     * 
     * <p/>
     * If the caller's {@link ISVNEventHandler} is not <span class="javakeyword">null</span>, then it will be 
     * called once for each merged target.
     * 
     * <p>
     * If <code>recordOnly</code> is <span class="javakeyword">true</span>, the merge isn't actually performed, 
     * but the mergeinfo for the revisions which would've been merged is recorded in the working copy (and must 
     * be subsequently committed back to the repository).
     * 
     * <p/>
     * If <code>dryRun</code> is <span class="javakeyword">true</span>, the merge is carried out, and full 
     * notification feedback is provided, but the working copy is not modified.
     *
     * <p/>
     * Note: this method requires repository access. 
     * 
     * @param  url1              left-hand item - repository url
     * @param  revision1         revision of <code>url1</code>
     * @param  path2             right-hand item - working copy path
     * @param  revision2         revision of <code>path2</code>
     * @param  dstPath           target working copy path
     * @param  depth             tree depth to process
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  force             <span class="javakeyword">true</span> to
     *                           force the operation to run
     * @param  dryRun            if <span class="javakeyword">true</span> then runs merge without any file 
     *                           changes 
     * @param  recordOnly        if <span class="javakeyword">true</span>, records only the rusult of merge - 
     *                           mergeinfo data
     * @throws SVNException      in the following cases:
     *                           <ul>
     *                           <li/>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} error code - 
     *                           if either <code>revision1</code> or <code>revision2</code> is 
     *                           {@link SVNRevision#isValid() invalid}
     *                           <li/>exception with {@link SVNErrorCode#ENTRY_MISSING_URL} error code - 
     *                           if failed to retrieve the repository url of <code>path2</code>
     *                           </ul>
     * @since                    1.2, SVN 1.5
     */
    public void doMerge(SVNURL url1, SVNRevision revision1, File path2, SVNRevision revision2, File dstPath, 
            SVNDepth depth, boolean useAncestry, boolean force, boolean dryRun, boolean recordOnly) throws SVNException {
        path2 = path2.getAbsoluteFile();
        dstPath = dstPath.getAbsoluteFile();
        SVNURL url2 = getURL(path2);
        if (url2 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path2);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        runMerge(url1, revision1, url2, revision2, dstPath, depth, dryRun, force, !useAncestry, 
                 recordOnly);
    }
    
    /**
     * Applies the differences between two sources (one source URL against another 
     * source URL) to a Working Copy path.
     *
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn merge sourceURL1@rev1 sourceURL2@rev2 WCPATH'</code> command.
     * 
     * <p>
     * If you need only to try merging your file(s) without actual merging, you
     * should set <code>dryRun</code> to <span class="javakeyword">true</span>.
     * Your event handler will be dispatched status type information on the target 
     * path(s). If a path can be successfully merged, the status type will be
     * {@link SVNStatusType#MERGED} for that path.  
     * 
     * @param  url1           the first source URL
     * @param  revision1      a revision of <code>url1</code>
     * @param  url2           the second source URL that is to be compared against 
     *                        <code>url1</code>
     * @param  revision2      a revision of <code>url2</code>
     * @param  dstPath        the target path to which the result should
     *                        be applied
     * @param  recursive     <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>revision1</code> and <code>revision2</code> is
     *                        invalid
     *                        <li><code>url1</code> was not found in 
     *                        <code>revision1</code>
     *                        <li><code>url2</code> was not found in 
     *                        <code>revision2</code>
     *                        <li><code>dstPath</code> is not under version control
     *                        </ul>
     * @deprecated            use {@link #doMerge(SVNURL, SVNRevision, SVNURL, SVNRevision, File, SVNDepth, boolean, boolean, boolean, boolean)}
     *                        instead                        
     */
    public void doMerge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, File dstPath, boolean recursive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        doMerge(url1, revision1, url2, revision2, dstPath, SVNDepth.getInfinityOrFilesDepth(recursive), 
                useAncestry, force, dryRun, false);
    }

    /** 
     * Merges changes from <code>url1</code>/<code>revision1</code> to <code>url2</code>/<code>revision2</code> 
     * into the working-copy path <code>dstPath</code>.
     * 
     * <p/>
     * <code>url1</code> and <code>url2</code> must both represent the same node kind - that
     * is, if <code>url1</code> is a directory, <code>url2</code> must also be, and if <code>url1</code>
     * is a file, <code>url2</code> must also be.
     * 
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, merges fully recursively. Else if 
     * {@link SVNDepth#IMMEDIATES}, merges changes at most to files that are immediate children of 
     * <code>dstPath</code> and to directory properties of <code>dstPath</code> and its immediate subdirectory 
     * children. Else if {@link SVNDepth#FILES}, merges at most to immediate file children of 
     * <code>dstPath</code> and to <code>dstPath</code> itself. Else if {@link SVNDepth#EMPTY}, applies changes 
     * only to <code>dstPath</code> (i.e., directory property changes only).
     *
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#UNKNOWN}, uses the depth of <code>dstPath</code>.
     *
     * <p/>
     * Uses <code>useAncestry</code> to control whether or not items being diffed will be checked for 
     * relatedness first. Unrelated items are typically transmitted to the editor as a deletion of one thing
     * and the addition of another, but if this flag is <span class="javakeyword">true</span>, unrelated
     * items will be diffed as if they were related.
     *
     * <p/>
     * If <code>force</code> is not set and the merge involves deleting locally modified or unversioned items 
     * the operation will fail. If <code>force</code> is set such items will be deleted.
     * 
     * <p/>
     * {@link #getMergeOptions() merge options} is used to pass arguments to the merge processes (internal or 
     * external). 
     * 
     * <p/>
     * If the caller's {@link ISVNEventHandler} is not <span class="javakeyword">null</span>, then it will be 
     * called once for each merged target.
     * 
     * <p/>
     * If <code>recordOnly</code> is <span class="javakeyword">true</span>, the merge isn't actually performed, 
     * but the mergeinfo for the revisions which would've been merged is recorded in the working copy (and must 
     * be subsequently committed back to the repository).
     * 
     * <p/>
     * If <code>dryRun</code> is <span class="javakeyword">true</span>, the merge is carried out, and full 
     * notification feedback is provided, but the working copy is not modified.
     *
     * <p/>
     * Note: this method requires repository access. 
     * 
     * @param  url1              left-hand repository url
     * @param  revision1         revision of <code>url1</code>
     * @param  url2              right-hand repository url
     * @param  revision2         revision of <code>url2</code>
     * @param  dstPath           target working copy path
     * @param  depth             tree depth to process
     * @param  useAncestry       if <span class="javakeyword">true</span> then
     *                           the paths ancestry will be noticed while calculating differences,
     *                           otherwise not
     * @param  force             <span class="javakeyword">true</span> to
     *                           force the operation to run
     * @param  dryRun            if <span class="javakeyword">true</span> then runs merge without any file 
     *                           changes 
     * @param  recordOnly        if <span class="javakeyword">true</span>, records only the rusult of merge - 
     *                           mergeinfo data
     * @throws SVNException      in the following cases:
     *                           <ul>
     *                           <li/>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} error code - 
     *                           if either <code>revision1</code> or <code>revision2</code> is 
     *                           {@link SVNRevision#isValid() invalid}
     *                           </ul>
     * @since                    1.2, SVN 1.5
     */
    public void doMerge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, File dstPath, 
            SVNDepth depth, boolean useAncestry, boolean force, boolean dryRun, boolean recordOnly) throws SVNException {
         runMerge(url1, revision1, url2, revision2, dstPath, depth, dryRun, force, !useAncestry, recordOnly);
    }
    
    /**
     * Applies the differences between two sources (a source URL in a particular
     * revision against the same source URL in another particular revision) to a 
     * Working Copy path.
     * 
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn merge -r rev1:rev2 URL@pegRev WCPATH'</code> command.
     * 
     * <p>
     * If you need only to try merging your file(s) without actual merging, you
     * should set <code>dryRun</code> to <span class="javakeyword">true</span>.
     * Your event handler will be dispatched status type information on the target 
     * path(s). If a path can be successfully merged, the status type will be
     * {@link SVNStatusType#MERGED} for that path.  
     * 
     * @param  url1           a source URL
     * @param  pegRevision    a revision in which code>url1</code> 
     *                        is first looked up
     * @param  revision1      a left-hand revision of <code>url1</code> 
     * @param  revision2      a right-hand revision of <code>url1</code>
     * @param  dstPath        the target path to which the result should
     *                        be applied
     * @param  recursive     <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>revision1</code>, <code>revision2</code> and
     *                        <code>pegRevision</code> is invalid
     *                        <li><code>url1</code> was not found in 
     *                        <code>revision1</code>
     *                        <li><code>url1</code> was not found in 
     *                        <code>revision2</code>
     *                        <li><code>dstPath</code> is not under version control
     *                        </ul>
     * @deprecated            use {@link #doMerge(SVNURL, SVNRevision, Collection, File, SVNDepth, boolean, boolean, boolean, boolean)}
     *                        instead
     */
    public void doMerge(SVNURL url1, SVNRevision pegRevision, SVNRevision revision1, SVNRevision revision2, File dstPath, boolean recursive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        SVNRevisionRange range = new SVNRevisionRange(revision1, revision2);
        List rangesToMerge = new LinkedList();
        rangesToMerge.add(range);
    	doMerge(url1, pegRevision, rangesToMerge, dstPath, SVNDepth.getInfinityOrFilesDepth(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    /**
     * Merges the changes between <code>url1</code> in peg revision <code>pegRevision</code>, as it changed 
     * between the ranges described in <code>rangesToMerge</code>.
     * 
     * <p/>
     * <code>rangesToMerge</code> is a collection of {@link SVNRevisionRange} ranges. These ranges may 
     * describe additive and/or subtractive merge ranges, they may overlap fully or partially, and/or they may 
     * partially or fully negate each other. This rangelist is not required to be sorted.  
     * 
     * <p/>
     * All other options are handled identically to 
     * {@link #doMerge(SVNURL, SVNRevision, SVNURL, SVNRevision, File, SVNDepth, boolean, boolean, boolean, boolean)}.
     *
     * <p/>
     * Note: this method requires repository access.
     * 
     * @param  url1           a source URL
     * @param  pegRevision    a revision in which <code>url1</code> 
     *                        is first looked up
     * @param  rangesToMerge  collection of revision ranges to merge
     * @param  dstPath        target working copy path
     * @param  depth          tree depth to process
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @param  recordOnly
     * @throws SVNException   in the following cases:
     *                        <ul>
     *                        <li/>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} - If any revision in 
     *                        the list of provided ranges is {@link SVNRevision#isValid() invalid}
     *                        </ul>
     * @since                 1.2, SVN 1.5
     */
    public void doMerge(SVNURL url1, SVNRevision pegRevision, Collection rangesToMerge, File dstPath, 
            SVNDepth depth, boolean useAncestry, boolean force, boolean dryRun, boolean recordOnly) throws SVNException {
        if (pegRevision == null || !pegRevision.isValid()) {
            pegRevision = SVNRevision.HEAD;
        }
        runPeggedMerge(url1, null, rangesToMerge, pegRevision, dstPath, depth, dryRun, force, !useAncestry, 
                recordOnly);
    }
    
    /**
     * Applies the differences between two sources (the repository location of
     * a source Working Copy path in a particular revision against the repository
     * location of the same path in another particular revision) to a 
     * Working Copy path.
     * 
     * <p>
     * Corresponds to the SVN command line client's 
     * <code>'svn merge -r rev1:rev2 sourceWCPATH@pegRev WCPATH'</code> command.
     * 
     * <p>
     * If you need only to try merging your file(s) without actual merging, you
     * should set <code>dryRun</code> to <span class="javakeyword">true</span>.
     * Your event handler will be dispatched status type information on the target 
     * path(s). If a path can be successfully merged, the status type will be
     * {@link SVNStatusType#MERGED} for that path.  
     * 
     * @param  path1          a source WC path
     * @param  pegRevision    a revision in which the repository location of 
     *                        <code>path1</code> is first looked up
     * @param  revision1      a left-hand revision of <code>path1</code> 
     * @param  revision2      a right-hand revision of <code>path1</code>
     * @param  dstPath        the target path to which the result should
     *                        be applied
     * @param  recursive     <span class="javakeyword">true</span> to descend 
     *                        recursively
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li>at least one of <code>revision1</code>, <code>revision2</code> and
     *                        <code>pegRevision</code> is invalid
     *                        <li><code>path1</code> has no URL
     *                        <li>the repository location of <code>path1</code> was not found in 
     *                        <code>revision1</code>
     *                        <li>the repository location of <code>path1</code> was not found in 
     *                        <code>revision2</code>
     *                        <li><code>dstPath</code> is not under version control
     *                        </ul>
     * @deprecated            use {@link #doMerge(File, SVNRevision, Collection, File, SVNDepth, boolean, boolean, boolean, boolean)}
     *                        instead
     */
    public void doMerge(File path1, SVNRevision pegRevision, SVNRevision revision1, SVNRevision revision2, File dstPath, boolean recursive, boolean useAncestry, 
            boolean force, boolean dryRun) throws SVNException {
        SVNRevisionRange range = new SVNRevisionRange(revision1, revision2);
        List rangesToMerge = new LinkedList();
        rangesToMerge.add(range);
    	doMerge(path1, pegRevision, rangesToMerge, dstPath, SVNDepth.getInfinityOrFilesDepth(recursive), 
                useAncestry, force, dryRun, false);
    }
    
    /**
     * Merges the changes between <code>path1</code> in peg revision <code>pegRevision</code>, as it changed 
     * between the ranges described in <code>rangesToMerge</code>.
     * 
     * <p/>
     * <code>rangesToMerge</code> is a collection of {@link SVNRevisionRange} ranges. These ranges may 
     * describe additive and/or subtractive merge ranges, they may overlap fully or partially, and/or they may 
     * partially or fully negate each other. This rangelist is not required to be sorted.  
     * 
     * <p/>
     * All other options are handled identically to 
     * {@link #doMerge(File, SVNRevision, File, SVNRevision, File, SVNDepth, boolean, boolean, boolean, boolean)}.
     *
     * <p/>
     * Note: this method requires repository access.
     * 
     * @param  path1          working copy path
     * @param  pegRevision    a revision in which <code>path1</code> 
     *                        is first looked up
     * @param  rangesToMerge  collection of revision ranges to merge
     * @param  dstPath        target working copy path
     * @param  depth          tree depth to process
     * @param  useAncestry    if <span class="javakeyword">true</span> then
     *                        the paths ancestry will be noticed while calculating differences,
     *                        otherwise not
     * @param  force          <span class="javakeyword">true</span> to
     *                        force the operation to run
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @param  recordOnly
     * @throws SVNException   in the following cases:
     *                        <ul>
     *                        <li/>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} - If any revision in 
     *                        the list of provided ranges is {@link SVNRevision#isValid() invalid}
     *                        </ul>
     * @since                 1.2, SVN 1.5
     */
    public void doMerge(File path1, SVNRevision pegRevision, Collection rangesToMerge, File dstPath, SVNDepth depth, 
    		boolean useAncestry, boolean force, boolean dryRun, boolean recordOnly) throws SVNException {
        /*
         * Equivalent of 3. merge -r N:M SOURCE[@REV] [WCPATH]
         * where SOURCE is a wc path.
         */
        if (pegRevision == null || !pegRevision.isValid()) {
            pegRevision = SVNRevision.WORKING;
        }
        runPeggedMerge(null, path1, rangesToMerge, pegRevision, dstPath, depth, dryRun, 
                       force, !useAncestry, recordOnly);
    }

    /**
     * Performs a reintegration merge of <code>srcPath</code> at <code>pegRevision</code> into 
     * <code>dstPath</code>.
     * 
     * <p/>
     * <code>dstPath</code> must be a single-revision, {@link SVNDepth#INFINITY}, pristine, unswitched working 
     * copy -- in other words, it must reflect a single revision tree, the "target". The mergeinfo on 
     * <code>srcPath</code> must reflect that all of the target has been merged into it. 
     * 
     * <p/>
     * This kind of merge should be used for back merging (for example, merging branches back to trunk, 
     * in which case merge is carried out by comparing the latest trunk tree with the latest branch tree; i.e. 
     * the resulting difference is excatly the branch changes which will go back to trunk).
     * 
     * <p/>
     * All other options are handled identically to {@link #doMerge(File, SVNRevision, File, SVNRevision, File, SVNDepth, boolean, boolean, boolean, boolean)}.
     * The depth of the merge is always {@link SVNDepth#INFINITY}.
     * 
     * <p/>
     * If <code>pegRevision</code> is <span class="javakeyword">null</span> or {@link SVNRevision#isValid() invalid}, 
     * then it defaults to {@link SVNRevision#WORKING}. 
     * 
     * <p/>
     * Note: this method requires repository access.
     * 
     * @param  srcPath        working copy path 
     * @param  pegRevision    a revision in which <code>srcPath</code> 
     *                        is first looked up
     * @param  dstPath        target working copy path
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @throws SVNException 
     * @since                 1.2, SVN 1.5
     */
    public void doMergeReIntegrate(File srcPath, SVNRevision pegRevision, File dstPath, 
            boolean dryRun) throws SVNException {
        if (pegRevision == null || !pegRevision.isValid()) {
            pegRevision = SVNRevision.WORKING;
        }
        runMergeReintegrate(null, srcPath, pegRevision, dstPath, dryRun);
    }

    /**
     * Performs a reintegration merge of <code>srcURL</code> at <code>pegRevision</code> into 
     * <code>dstPath</code>.
     * 
     * <p/>
     * <code>dstPath</code> must be a single-revision, {@link SVNDepth#INFINITY}, pristine, unswitched working 
     * copy -- in other words, it must reflect a single revision tree, the "target". The mergeinfo on 
     * <code>srcPath</code> must reflect that all of the target has been merged into it. 
     * 
     * <p/>
     * This kind of merge should be used for back merging (for example, merging branches back to trunk, 
     * in which case merge is carried out by comparing the latest trunk tree with the latest branch tree; i.e. 
     * the resulting difference is excatly the branch changes which will go back to trunk).
     * 
     * <p/>
     * All other options are handled identically to {@link #doMerge(SVNURL, SVNRevision, SVNURL, SVNRevision, File, SVNDepth, boolean, boolean, boolean, boolean)}.
     * The depth of the merge is always {@link SVNDepth#INFINITY}.
     * 
     * <p/>
     * If <code>pegRevision</code> is <span class="javakeyword">null</span> or {@link SVNRevision#isValid() invalid}, 
     * then it defaults to {@link SVNRevision#HEAD}. 
     * 
     * <p/>
     * Note: this method requires repository access.
     * 
     * @param  srcURL         repository url 
     * @param  pegRevision    a revision in which <code>srcURL</code> 
     *                        is first looked up
     * @param  dstPath        target working copy path
     * @param  dryRun         if <span class="javakeyword">true</span> then
     *                        only tries the operation to run (to find out
     *                        if a file can be merged successfully)
     * @throws SVNException 
     * @since                 1.2, SVN 1.5
     */
    public void doMergeReIntegrate(SVNURL srcURL, SVNRevision pegRevision, File dstPath, 
            boolean dryRun) throws SVNException {
        if (pegRevision == null || !pegRevision.isValid()) {
            pegRevision = SVNRevision.HEAD;
        }
        runMergeReintegrate(srcURL, null, pegRevision, dstPath, dryRun);
    }

    /**
     * Drives a log entry <code>handler</code> with the revisions merged from <code>mergeSrcURL</code> (as of 
     * <code>srcPegRevision</code>) into <code>path</code> (as of <code>pegRevision</code>).
     * 
     * <p/>
     * <code>discoverChangedPaths</code> and <code>revisionProperties</code> are the same as for 
     * {@link SVNLogClient#doLog(File[], SVNRevision, SVNRevision, SVNRevision, boolean, boolean, boolean, long, String[], ISVNLogEntryHandler)}. 
     * 
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  path                     working copy path (merge target)                    
     * @param  pegRevision              a revision in which <code>path</code> is first looked up
     * @param  mergeSrcURL              merge source repository url
     * @param  srcPegRevision           a revision in which <code>mergeSrcURL</code> is first looked up
     * @param  discoverChangedPaths     <span class="javakeyword">true</span> to report of all changed paths for 
     *                                  every revision being processed (those paths will be available by calling 
     *                                  {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  revisionProperties       names of revision properties to retrieve     
     * @param  handler                  the caller's log entry handler
     * @throws SVNException             in the following cases: 
     *                                  <ul>
     *                                  <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - 
     *                                  if the server doesn't support retrieval of mergeinfo
     *                                  </ul> 
     * @since                           1.2, SVN 1.5
     */
    public void doGetLogMergedMergeInfo(File path, SVNRevision pegRevision, SVNURL mergeSrcURL, 
            SVNRevision srcPegRevision, boolean discoverChangedPaths, String[] revisionProperties, 
            ISVNLogEntryHandler handler) throws SVNException {
        getLogMergedMergeInfoImpl(path, null, pegRevision, mergeSrcURL, null, srcPegRevision, discoverChangedPaths, 
                revisionProperties, handler);
    }

    /**
     * Drives a log entry <code>handler</code> with the revisions merged from <code>mergeSrcURL</code> (as of 
     * <code>srcPegRevision</code>) into <code>url</code> (as of <code>pegRevision</code>).
     * 
     * <p/>
     * <code>discoverChangedPaths</code> and <code>revisionProperties</code> are the same as for 
     * {@link SVNLogClient#doLog(File[], SVNRevision, SVNRevision, SVNRevision, boolean, boolean, boolean, long, String[], ISVNLogEntryHandler)}. 
     * 
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  url                      repository url (merge target)                    
     * @param  pegRevision              a revision in which <code>url</code> is first looked up
     * @param  mergeSrcURL              merge source repository url
     * @param  srcPegRevision           a revision in which <code>mergeSrcURL</code> is first looked up
     * @param  discoverChangedPaths     <span class="javakeyword">true</span> to report of all changed paths for 
     *                                  every revision being processed (those paths will be available by calling 
     *                                  {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  revisionProperties       names of revision properties to retrieve     
     * @param  handler                  the caller's log entry handler
     * @throws SVNException             in the following cases: 
     *                                  <ul>
     *                                  <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - 
     *                                  if the server doesn't support retrieval of mergeinfo
     *                                  </ul> 
     * @since                           1.2, SVN 1.5
     */
    public void doGetLogMergedMergeInfo(SVNURL url, SVNRevision pegRevision, SVNURL mergeSrcURL, 
            SVNRevision srcPegRevision, boolean discoverChangedPaths, String[] revisionProperties, 
            ISVNLogEntryHandler handler) throws SVNException {
        getLogMergedMergeInfoImpl(null, url, pegRevision, mergeSrcURL, null, srcPegRevision, discoverChangedPaths, 
                revisionProperties, handler);
    }

    /**
     * Drives a log entry <code>handler</code> with the revisions merged from <code>mergeSrcPath</code> (as of 
     * <code>srcPegRevision</code>) into <code>path</code> (as of <code>pegRevision</code>).
     * 
     * <p/>
     * <code>discoverChangedPaths</code> and <code>revisionProperties</code> are the same as for 
     * {@link SVNLogClient#doLog(File[], SVNRevision, SVNRevision, SVNRevision, boolean, boolean, boolean, long, String[], ISVNLogEntryHandler)}. 
     * 
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  path                     working copy path (merge target)                    
     * @param  pegRevision              a revision in which <code>path</code> is first looked up
     * @param  mergeSrcPath             merge source working copy path
     * @param  srcPegRevision           a revision in which <code>mergeSrcPath</code> is first looked up
     * @param  discoverChangedPaths     <span class="javakeyword">true</span> to report of all changed paths for 
     *                                  every revision being processed (those paths will be available by calling 
     *                                  {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  revisionProperties       names of revision properties to retrieve     
     * @param  handler                  the caller's log entry handler
     * @throws SVNException             in the following cases: 
     *                                  <ul>
     *                                  <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - 
     *                                  if the server doesn't support retrieval of mergeinfo
     *                                  </ul> 
     * @since                           1.2, SVN 1.5
     */
    public void doGetLogMergedMergeInfo(File path, SVNRevision pegRevision, File mergeSrcPath, 
            SVNRevision srcPegRevision, boolean discoverChangedPaths, String[] revisionProperties, 
            ISVNLogEntryHandler handler) throws SVNException {
        getLogMergedMergeInfoImpl(path, null, pegRevision, null, mergeSrcPath, srcPegRevision, discoverChangedPaths, 
                revisionProperties, handler);
    }

    /**
     * Drives a log entry <code>handler</code> with the revisions merged from <code>mergeSrcPath</code> (as of 
     * <code>srcPegRevision</code>) into <code>url</code> (as of <code>pegRevision</code>).
     * 
     * <p/>
     * <code>discoverChangedPaths</code> and <code>revisionProperties</code> are the same as for 
     * {@link SVNLogClient#doLog(File[], SVNRevision, SVNRevision, SVNRevision, boolean, boolean, boolean, long, String[], ISVNLogEntryHandler)}. 
     * 
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  url                      repository url (merge target)                    
     * @param  pegRevision              a revision in which <code>url</code> is first looked up
     * @param  mergeSrcPath             merge source working copy path
     * @param  srcPegRevision           a revision in which <code>mergeSrcPath</code> is first looked up
     * @param  discoverChangedPaths     <span class="javakeyword">true</span> to report of all changed paths for 
     *                                  every revision being processed (those paths will be available by calling 
     *                                  {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  revisionProperties       names of revision properties to retrieve     
     * @param  handler                  the caller's log entry handler
     * @throws SVNException             in the following cases: 
     *                                  <ul>
     *                                  <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - 
     *                                  if the server doesn't support retrieval of mergeinfo
     *                                  </ul> 
     * @since                           1.2, SVN 1.5
     */
    public void doGetLogMergedMergeInfo(SVNURL url, SVNRevision pegRevision, File mergeSrcPath, 
            SVNRevision srcPegRevision, boolean discoverChangedPaths, String[] revisionProperties, 
            ISVNLogEntryHandler handler) throws SVNException {
        getLogMergedMergeInfoImpl(null, url, pegRevision, null, mergeSrcPath, srcPegRevision, discoverChangedPaths, 
                revisionProperties, handler);
    }

    /**
     * Drives a log entry <code>handler</code> with the revisions eligible for merge from 
     * <code>mergeSrcURL</code> (as of <code>srcPegRevision</code>) into <code>path</code> (as of 
     * <code>pegRevision</code>).
     * 
     * <p/>
     * <code>discoverChangedPaths</code> and <code>revisionProperties</code> are the same as for 
     * {@link SVNLogClient#doLog(File[], SVNRevision, SVNRevision, SVNRevision, boolean, boolean, boolean, long, String[], ISVNLogEntryHandler)}. 
     *
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  path                     working copy path (merge target)                    
     * @param  pegRevision              a revision in which <code>path</code> is first looked up
     * @param  mergeSrcURL              merge source repository url
     * @param  srcPegRevision           a revision in which <code>mergeSrcURL</code> is first looked up
     * @param  discoverChangedPaths     <span class="javakeyword">true</span> to report of all changed paths for 
     *                                  every revision being processed (those paths will be available by calling 
     *                                  {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  revisionProperties       names of revision properties to retrieve     
     * @param  handler                  the caller's log entry handler
     * @throws SVNException             in the following cases: 
     *                                  <ul>
     *                                  <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - 
     *                                  if the server doesn't support retrieval of mergeinfo
     *                                  </ul> 
     * @since                           1.2, SVN 1.5
     */
    public void doGetLogEligibleMergeInfo(File path, SVNRevision pegRevision, 
            SVNURL mergeSrcURL, SVNRevision srcPegRevision, boolean discoverChangedPaths, 
            String[] revisionProperties, ISVNLogEntryHandler handler) throws SVNException {
        getLogEligibleMergeInfoImpl(path, null, pegRevision, mergeSrcURL, null, srcPegRevision, discoverChangedPaths, 
                revisionProperties, handler);
    }

    /**
     * Drives a log entry <code>handler</code> with the revisions eligible for merge from 
     * <code>mergeSrcURL</code> (as of <code>srcPegRevision</code>) into <code>url</code> (as of 
     * <code>pegRevision</code>).
     * 
     * <p/>
     * <code>discoverChangedPaths</code> and <code>revisionProperties</code> are the same as for 
     * {@link SVNLogClient#doLog(File[], SVNRevision, SVNRevision, SVNRevision, boolean, boolean, boolean, long, String[], ISVNLogEntryHandler)}. 
     *
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  url                      repository url (merge target)                    
     * @param  pegRevision              a revision in which <code>url</code> is first looked up
     * @param  mergeSrcURL              merge source repository url
     * @param  srcPegRevision           a revision in which <code>mergeSrcURL</code> is first looked up
     * @param  discoverChangedPaths     <span class="javakeyword">true</span> to report of all changed paths for 
     *                                  every revision being processed (those paths will be available by calling 
     *                                  {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  revisionProperties       names of revision properties to retrieve     
     * @param  handler                  the caller's log entry handler
     * @throws SVNException             in the following cases: 
     *                                  <ul>
     *                                  <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - 
     *                                  if the server doesn't support retrieval of mergeinfo
     *                                  </ul> 
     * @since                           1.2, SVN 1.5
     */
    public void doGetLogEligibleMergeInfo(SVNURL url, SVNRevision pegRevision, 
            SVNURL mergeSrcURL, SVNRevision srcPegRevision, boolean discoverChangedPaths, 
            String[] revisionProperties, ISVNLogEntryHandler handler) throws SVNException {
        getLogEligibleMergeInfoImpl(null, url, pegRevision, mergeSrcURL, null, srcPegRevision, discoverChangedPaths, 
                revisionProperties, handler);
    }

    /**
     * Drives a log entry <code>handler</code> with the revisions eligible for merge from 
     * <code>mergeSrcPath</code> (as of <code>srcPegRevision</code>) into <code>path</code> (as of 
     * <code>pegRevision</code>).
     * 
     * <p/>
     * <code>discoverChangedPaths</code> and <code>revisionProperties</code> are the same as for 
     * {@link SVNLogClient#doLog(File[], SVNRevision, SVNRevision, SVNRevision, boolean, boolean, boolean, long, String[], ISVNLogEntryHandler)}. 
     *
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  path                     working copy path (merge target)                    
     * @param  pegRevision              a revision in which <code>path</code> is first looked up
     * @param  mergeSrcPath             merge source working copy path
     * @param  srcPegRevision           a revision in which <code>mergeSrcPath</code> is first looked up
     * @param  discoverChangedPaths     <span class="javakeyword">true</span> to report of all changed paths for 
     *                                  every revision being processed (those paths will be available by calling 
     *                                  {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  revisionProperties       names of revision properties to retrieve     
     * @param  handler                  the caller's log entry handler
     * @throws SVNException             in the following cases: 
     *                                  <ul>
     *                                  <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - 
     *                                  if the server doesn't support retrieval of mergeinfo
     *                                  </ul> 
     * @since                           1.2, SVN 1.5
     */
    public void doGetLogEligibleMergeInfo(File path, SVNRevision pegRevision, 
            File mergeSrcPath, SVNRevision srcPegRevision, boolean discoverChangedPaths, 
            String[] revisionProperties, ISVNLogEntryHandler handler) throws SVNException {
        getLogEligibleMergeInfoImpl(path, null, pegRevision, null, mergeSrcPath, srcPegRevision, discoverChangedPaths, 
                revisionProperties, handler);
    }
    
    /**
     * Drives a log entry <code>handler</code> with the revisions eligible for merge from 
     * <code>mergeSrcPath</code> (as of <code>srcPegRevision</code>) into <code>url</code> (as of 
     * <code>pegRevision</code>).
     * 
     * <p/>
     * <code>discoverChangedPaths</code> and <code>revisionProperties</code> are the same as for 
     * {@link SVNLogClient#doLog(File[], SVNRevision, SVNRevision, SVNRevision, boolean, boolean, boolean, long, String[], ISVNLogEntryHandler)}. 
     *
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  url                      repository url (merge target)                    
     * @param  pegRevision              a revision in which <code>url</code> is first looked up
     * @param  mergeSrcPath             merge source working copy path
     * @param  srcPegRevision           a revision in which <code>mergeSrcPath</code> is first looked up
     * @param  discoverChangedPaths     <span class="javakeyword">true</span> to report of all changed paths for 
     *                                  every revision being processed (those paths will be available by calling 
     *                                  {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  revisionProperties       names of revision properties to retrieve     
     * @param  handler                  the caller's log entry handler
     * @throws SVNException             in the following cases: 
     *                                  <ul>
     *                                  <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - 
     *                                  if the server doesn't support retrieval of mergeinfo
     *                                  </ul> 
     * @since                           1.2, SVN 1.5
     */
    public void doGetLogEligibleMergeInfo(SVNURL url, SVNRevision pegRevision, 
            File mergeSrcPath, SVNRevision srcPegRevision, boolean discoverChangedPaths, 
            String[] revisionProperties, ISVNLogEntryHandler handler) throws SVNException {
        getLogEligibleMergeInfoImpl(null, url, pegRevision, null, mergeSrcPath, srcPegRevision, discoverChangedPaths, 
                revisionProperties, handler);
    }

    /**
     * Returns mergeinfo as a <code>Map</code> with merge source URLs (as {@link SVNURL}) mapped to 
     * range lists ({@link SVNMergeRangeList}). Range lists are objects containing arrays of 
     * {@link SVNMergeRange ranges} describing the ranges which have been merged into <code>path</code> as 
     * of <code>pegRevision</code>. If there is no mergeinfo, returns <span class="javakeyword">null</span>. 
     * 
     * <p/>
     * Note: unlike most APIs which deal with mergeinfo, this one returns data where the keys of the map are 
     * absolute repository URLs rather than repository filesystem paths.
     *
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  path           working copy path 
     * @param  pegRevision    a revision in which <code>path</code> is first looked up
     * @return                mergeinfo for <code>path</code>
     * @throws SVNException   in the following cases:
     *                        <ul>
     *                        <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - 
     *                        if the server doesn't support retrieval of mergeinfo (which will never happen 
     *                        for file:// URLs)                        
     *                        </ul>            
     * @since                 1.2, SVN 1.5
     */
    public Map doGetMergedMergeInfo(File path, SVNRevision pegRevision) throws SVNException {
    	SVNURL reposRoot[] = new SVNURL[1];
    	Map mergeInfo = getMergeInfo(path, pegRevision, reposRoot);
    	SVNURL repositoryRoot = reposRoot[0];
    	if (mergeInfo != null) {
    		Map fullPathMergeInfo = new SVNHashMap();
            for (Iterator paths = mergeInfo.keySet().iterator(); paths.hasNext();) {
                String mergeSrcPath = (String) paths.next();
                SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(mergeSrcPath);
                if (mergeSrcPath.startsWith("/")) {
                    mergeSrcPath = mergeSrcPath.substring(1);
                }
                SVNURL url = repositoryRoot.appendPath(mergeSrcPath, false);
                fullPathMergeInfo.put(url, rangeList);
            }
            mergeInfo = fullPathMergeInfo;
    	}
    	return mergeInfo;
    }

    /**
     * Returns mergeinfo as a <code>Map</code> with merge source URLs (as {@link SVNURL}) mapped to 
     * range lists ({@link SVNMergeRangeList}). Range lists are objects containing arrays of 
     * {@link SVNMergeRange ranges} describing the ranges which have been merged into <code>url</code> as 
     * of <code>pegRevision</code>. If there is no mergeinfo, returns <span class="javakeyword">null</span>. 
     * 
     * <p/>
     * Note: unlike most APIs which deal with mergeinfo, this one returns data where the keys of the map are 
     * absolute repository URLs rather than repository filesystem paths.
     *
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  url            repository url 
     * @param  pegRevision    a revision in which <code>url</code> is first looked up
     * @return                mergeinfo for <code>url</code>
     * @throws SVNException   in the following cases:
     *                        <ul>
     *                        <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - 
     *                        if the server doesn't support retrieval of mergeinfo (which will never happen 
     *                        for file:// URLs)                        
     *                        </ul>            
     * @since                 1.2, SVN 1.5
     */
    public Map doGetMergedMergeInfo(SVNURL url, SVNRevision pegRevision) throws SVNException {
    	SVNURL reposRoot[] = new SVNURL[1];
    	Map mergeInfo = getMergeInfo(url, pegRevision, reposRoot);
    	SVNURL repositoryRoot = reposRoot[0];
    	if (mergeInfo != null) {
    		Map fullPathMergeInfo = new SVNHashMap();
            for (Iterator paths = mergeInfo.keySet().iterator(); paths.hasNext();) {
                String mergeSrcPath = (String) paths.next();
                SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(mergeSrcPath);
                if (mergeSrcPath.startsWith("/")) {
                    mergeSrcPath = mergeSrcPath.substring(1);
                }
                SVNURL nextURL = repositoryRoot.appendPath(mergeSrcPath, false);
                fullPathMergeInfo.put(nextURL, rangeList);
            }
            mergeInfo = fullPathMergeInfo;
    	}
    	return mergeInfo;
    }

    /** 
     * Returns a collection of potential merge sources (expressed as full repository {@link SVNURL URLs}) for 
     * <code>path</code> at <code>pegRevision</code>. 
     *
     * @param  path          working copy path 
     * @param  pegRevision   a revision in which <code>path</code> is first looked up
     * @throws SVNException 
     * @return               potential merge sources for <code>path</code> 
     * @since                1.2, SVN 1.5
     */
    public Collection doSuggestMergeSources(File path, SVNRevision pegRevision) throws SVNException {
        LinkedList suggestions = new LinkedList();
        SVNURL reposRoot = getReposRoot(path, null, pegRevision, null, null);
        SVNLocationEntry copyFromInfo = getCopySource(path, null, pegRevision);
        String copyFromPath = copyFromInfo.getPath();
        SVNURL copyFromURL = null;
        if (copyFromPath != null) {
            String relCopyFromPath = copyFromPath.startsWith("/") ? copyFromPath.substring(1) : copyFromPath;
            copyFromURL = reposRoot.appendPath(relCopyFromPath, false);
            suggestions.add(copyFromURL);
        }
        
        Map mergeInfo = doGetMergedMergeInfo(path, pegRevision);
        if (mergeInfo != null) {
            for (Iterator mergeSrcURLs = mergeInfo.keySet().iterator(); mergeSrcURLs.hasNext();) {
                SVNURL mergeSrcURL = (SVNURL) mergeSrcURLs.next();
                if (copyFromURL == null || !copyFromURL.equals(mergeSrcURL)) {
                    suggestions.add(mergeSrcURL);
                }
            }
        }
        return suggestions;
    }

    /** 
     * Returns a collection of potential merge sources (expressed as full repository {@link SVNURL URLs}) for 
     * <code>url</code> at <code>pegRevision</code>. 
     *
     * @param  url           repository url 
     * @param  pegRevision   a revision in which <code>url</code> is first looked up
     * @throws SVNException 
     * @return               potential merge sources for <code>url</code> 
     * @since                1.2, SVN 1.5
     */
    public Collection doSuggestMergeSources(SVNURL url, SVNRevision pegRevision) throws SVNException {
        LinkedList suggestions = new LinkedList();
        SVNURL reposRoot = getReposRoot(null, url, pegRevision, null, null);
        SVNLocationEntry copyFromInfo = getCopySource(null, url, pegRevision);
        String copyFromPath = copyFromInfo.getPath();

        SVNURL copyFromURL = null;
        if (copyFromPath != null) {
            String relCopyFromPath = copyFromPath.startsWith("/") ? copyFromPath.substring(1) : copyFromPath;
            copyFromURL = reposRoot.appendPath(relCopyFromPath, false);
            suggestions.add(copyFromURL);
        }
        
        Map mergeInfo = doGetMergedMergeInfo(url, pegRevision);
        if (mergeInfo != null) {
            for (Iterator mergeSrcURLs = mergeInfo.keySet().iterator(); mergeSrcURLs.hasNext();) {
                SVNURL mergeSrcURL = (SVNURL) mergeSrcURLs.next();
                if (copyFromURL == null || !copyFromURL.equals(mergeSrcURL)) {
                    suggestions.add(mergeSrcURL);
                }
            }
        }
        return suggestions;
    }

    private void doDiffURLWC(SVNURL url1, SVNRevision revision1, SVNRevision pegRevision, File path2, SVNRevision revision2, 
            boolean reverse, SVNDepth depth, boolean useAncestry, OutputStream result, Collection changeLists) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminAreaInfo info = wcAccess.openAnchor(path2, false, SVNDepth.recurseFromDepth(depth) ? SVNWCAccess.INFINITE_DEPTH : 0);
            File anchorPath = info.getAnchor().getRoot();
            String target = "".equals(info.getTargetName()) ? null : info.getTargetName();
            
            SVNEntry anchorEntry = info.getAnchor().getVersionedEntry("", false);
            if (anchorEntry.getURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", anchorPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            SVNURL anchorURL = anchorEntry.getSVNURL();
            if (pegRevision.isValid()) {
                SVNRepositoryLocation[] locations = getLocations(url1, null, null, pegRevision, revision1, SVNRevision.UNDEFINED);
                url1 = locations[0].getURL();
                String anchorPath2 = SVNPathUtil.append(anchorURL.toString(), target == null ? "" : target);
                getDiffGenerator().init(url1.toString(), anchorPath2);
            }
            SVNRepository repository = createRepository(anchorURL, null, null, true);
            long revNumber = getRevisionNumber(revision1, repository, null);
            AbstractDiffCallback callback = new SVNDiffCallback(info.getAnchor(), 
                                                                getDiffGenerator(), 
                                                                reverse ? -1 : revNumber, reverse ? revNumber : -1, result);
            SVNDiffEditor editor = new SVNDiffEditor(wcAccess, info, callback, useAncestry, reverse /* reverse */,
                    revision2 == SVNRevision.BASE  || revision2 == SVNRevision.COMMITTED  /* compare to base */, 
                    depth, changeLists);
            boolean serverSupportsDepth = repository.hasCapability(SVNCapability.DEPTH);
            SVNReporter reporter = new SVNReporter(info, info.getAnchor().getFile(info.getTargetName()), false, 
                    !serverSupportsDepth, depth, false, false, true, getDebugLog());
            
            long pegRevisionNumber = getRevisionNumber(revision2, repository, path2);
            try {
                repository.diff(url1, revNumber, pegRevisionNumber, target, !useAncestry, depth, true, reporter, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
            } finally {
                editor.cleanup();
            }
        } finally {
            wcAccess.close();
        }
    }

    private void doDiffURLWC(File path1, SVNRevision revision1, SVNRevision pegRevision, File path2, 
            SVNRevision revision2, boolean reverse, SVNDepth depth, boolean useAncestry, OutputStream result, 
            Collection changeLists) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        try {
            int admDepth = getAdminDepth(depth);
            SVNAdminAreaInfo info = wcAccess.openAnchor(path2, false, admDepth);
            File anchorPath = info.getAnchor().getRoot();
            String target = "".equals(info.getTargetName()) ? null : info.getTargetName();
            
            SVNEntry anchorEntry = info.getAnchor().getVersionedEntry("", false);
            if (anchorEntry.getURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", anchorPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            SVNURL url1;
            SVNURL anchorURL = anchorEntry.getSVNURL();
            if (pegRevision.isValid()) {
                SVNRepositoryLocation[] locations = getLocations(null, path1, null, pegRevision, revision1, SVNRevision.UNDEFINED);
                url1 = locations[0].getURL();
                String anchorPath2 = SVNPathUtil.append(anchorURL.toString(), target == null ? "" : target);
                if (!reverse) {
                    getDiffGenerator().init(url1.toString(), anchorPath2);
                } else {
                    getDiffGenerator().init(anchorPath2, url1.toString());
                } 
            } else {
                url1 = getURL(path1);
            }
            SVNRepository repository = createRepository(anchorURL, null, null, true);
            long revNumber = getRevisionNumber(revision1, repository, path1);
            AbstractDiffCallback callback = new SVNDiffCallback(info.getAnchor(), 
                                                                getDiffGenerator(), 
                                                                reverse ? -1 : revNumber, reverse ? revNumber : -1, result);
            SVNDiffEditor editor = new SVNDiffEditor(wcAccess, info, callback, useAncestry, reverse /* reverse */, 
                    revision2 == SVNRevision.BASE || revision2 == SVNRevision.COMMITTED /* compare to base */, 
                    depth, changeLists);
            ISVNEditor filterEditor = SVNAmbientDepthFilterEditor.wrap(editor, info, false);
            boolean serverSupportsDepth = repository.hasCapability(SVNCapability.DEPTH);
            SVNReporter reporter = new SVNReporter(info, info.getAnchor().getFile(info.getTargetName()), 
                    false, !serverSupportsDepth, depth, false, false, true, getDebugLog());
            
            // this should be rev2.
            long pegRevisionNumber = getRevisionNumber(revision2, repository, path2);
            try {
                repository.diff(url1, revNumber, pegRevisionNumber, target, !useAncestry, depth, true, reporter, SVNCancellableEditor.newInstance(filterEditor, this, getDebugLog()));
            } finally {
                editor.cleanup();
            }
        } finally {
            wcAccess.close();
        }
    }
    
    private void doDiffWCWC(File path1, SVNRevision revision1, File path2, SVNRevision revision2, 
            SVNDepth depth, boolean useAncestry, OutputStream result, Collection changeLists) throws SVNException {
        if (!path1.equals(path2) || !(revision1 == SVNRevision.BASE && revision2 == SVNRevision.WORKING)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                    "Only diffs between a path's text-base and its working files are supported at this time (-rBASE:WORKING)");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        SVNWCAccess wcAccess = createWCAccess();
        try {
            int admDepth = getAdminDepth(depth);
            SVNAdminAreaInfo info = wcAccess.openAnchor(path1, false, admDepth);
            wcAccess.getVersionedEntry(path1, false);
            long rev = getRevisionNumber(revision1, null, path1);
            AbstractDiffCallback callback = new SVNDiffCallback(info.getAnchor(), getDiffGenerator(), rev, -1, 
                    result);
            SVNDiffEditor editor = new SVNDiffEditor(wcAccess, info, callback, useAncestry, false, false, depth, 
                    changeLists);
            try {
                editor.closeEdit();
            } finally {
                editor.cleanup();
            }
        } finally {
            wcAccess.close();
        }
    }
    
    private void doDiffURLURL(SVNURL url1, File path1, SVNRevision revision1, SVNURL url2, File path2, SVNRevision revision2, SVNRevision pegRevision,
            SVNDepth depth, boolean useAncestry, OutputStream result) throws SVNException {
        File basePath = null;
        if (path1 != null) {
            basePath = path1;
        }
        if (path2 != null) {
            basePath = path2;
        }
        if (pegRevision.isValid()) {
            SVNRepositoryLocation[] locations = getLocations(url2, path2, null, pegRevision, revision1, revision2);
            url1 = locations[0].getURL();
            url2 = locations[1].getURL();
            
            getDiffGenerator().init(url1.toString(), url2.toString());
        } else {
            url1 = url1 == null ? getURL(path1) : url1;
            url2 = url2 == null ? getURL(path2) : url2;
        }
        SVNRepository repository1 = createRepository(url1, null, null, true);
        SVNRepository repository2 = createRepository(url2, null, null, false);

        final long rev1 = getRevisionNumber(revision1, repository1, path1);
        long rev2 = -1;
        String target1 = null;
        SVNNodeKind kind1 = null;
        SVNNodeKind kind2 = null;
        try {
            rev2 = getRevisionNumber(revision2, repository2, path2);            
            kind1 = repository1.checkPath("", rev1);
            kind2 = repository2.checkPath("", rev2);
            if (kind1 == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "''{0}'' was not found in the repository at revision {1}",
                        new Object[] {url1, new Long(rev1)});
                SVNErrorManager.error(err, SVNLogType.WC);
            } else if (kind2 == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "''{0}'' was not found in the repository at revision {1}",
                        new Object[] {url2, new Long(rev2)});
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        } finally {
            repository2.closeSession();
        }
        if (kind1 == SVNNodeKind.FILE || kind2 == SVNNodeKind.FILE) {
            target1 = SVNPathUtil.tail(url1.getPath());
            if (basePath != null) {
                basePath = basePath.getParentFile();
            }
            url1 = SVNURL.parseURIEncoded(SVNPathUtil.removeTail(url1.toString()));
            repository1 = createRepository(url1, null, null, true);
        }
        repository2 = createRepository(url1, null, null, false); 
        SVNRemoteDiffEditor editor = null;
        try {
            SVNDiffCallback callback = new SVNDiffCallback(null, getDiffGenerator(), rev1, rev2, result);
            callback.setBasePath(basePath);
            editor = new SVNRemoteDiffEditor(null, null, callback, repository2, rev1, rev2, false, null, this);
            editor.setUseGlobalTmp(true);
            ISVNReporterBaton reporter = new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    //TODO(sd): dynamic depth here
                    reporter.setPath("", null, rev1, SVNDepth.INFINITY, false);
                    reporter.finishReport();
                }
            };
            repository1.diff(url2, rev2, rev1, target1, !useAncestry, depth, true, reporter, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
        } finally {
            if (editor != null) {
                editor.cleanup();
            }
            repository2.closeSession();
        }
    }

    private void doDiffURLURL(SVNURL url1, File path1, SVNRevision revision1, SVNURL url2, File path2, SVNRevision revision2, SVNRevision pegRevision,
            SVNDepth depth, boolean useAncestry, ISVNDiffStatusHandler handler) throws SVNException {
        File basePath = null;
        if (path1 != null) {
            basePath = path1;
        }
        if (path2 != null) {
            basePath = path2;
        }
        if (pegRevision.isValid()) {
            SVNRepositoryLocation[] locations = getLocations(url2, path2, null, pegRevision, revision1, revision2);
            url1 = locations[0].getURL();
            url2 = locations[1].getURL();
            
            getDiffGenerator().init(url1.toString(), url2.toString());
        } else {
            url1 = url1 == null ? getURL(path1) : url1;
            url2 = url2 == null ? getURL(path2) : url2;
        }
        SVNRepository repository1 = createRepository(url1, null, null, true);
        SVNRepository repository2 = createRepository(url2, null, null, false);
        
        final long rev1 = getRevisionNumber(revision1, repository1, path1);
        long rev2 = -1;
        SVNNodeKind kind1 = null;
        SVNNodeKind kind2 = null;
        String target1 = null;

        try {
            rev2 = getRevisionNumber(revision2, repository2, path2);
            
            kind1 = repository1.checkPath("", rev1);
            kind2 = repository2.checkPath("", rev2);
            if (kind1 == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "''{0}'' was not found in the repository at revision {1}",
                        new Object[] {url1, new Long(rev1)});
                SVNErrorManager.error(err, SVNLogType.WC);
            } else if (kind2 == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "''{0}'' was not found in the repository at revision {1}",
                        new Object[] {url2, new Long(rev2)});
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (kind1 == SVNNodeKind.FILE || kind2 == SVNNodeKind.FILE) {
                target1 = SVNPathUtil.tail(url1.getPath());
                if (basePath != null) {
                    basePath = basePath.getParentFile();
                }
                url1 = SVNURL.parseURIEncoded(SVNPathUtil.removeTail(url1.toString()));
                repository1 = createRepository(url1, null, null, true);
            }
        } finally {
            repository2.closeSession();
        }
        repository2 = createRepository(url1, null, null, false); 
        File tmpFile = getDiffGenerator().createTempDirectory();
        try {
            SVNDiffStatusEditor editor = new SVNDiffStatusEditor(basePath, target1, repository2, rev1, handler);
            ISVNReporterBaton reporter = new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    //TODO(sd): dynamic depth here
                    reporter.setPath("", null, rev1, SVNDepth.INFINITY, false);
                    reporter.finishReport();
                }
            };
            repository1.diff(url2, rev2, rev1, target1, !useAncestry, depth, false, reporter, SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
        } finally {
            if (tmpFile != null) {
                SVNFileUtil.deleteAll(tmpFile, true, null);
            }
            repository2.closeSession();
        }
    }
    
    private int getAdminDepth(SVNDepth depth) {
        int admDepth = SVNWCAccess.INFINITE_DEPTH;
        if (depth == SVNDepth.IMMEDIATES) {
            admDepth = 1;
        } else if (depth == SVNDepth.EMPTY || depth == SVNDepth.FILES) {
            admDepth = 0;
        }
        return admDepth;
    }

    private Object[] getLocationFromPathAndRevision(File path, SVNURL url, SVNRevision pegRevision) throws SVNException {
        SVNWCAccess wcAccess = null;
        SVNRepository repos = null;
        SVNAdminArea adminArea = null;
        try {
            if (path != null && (pegRevision == SVNRevision.BASE || pegRevision == SVNRevision.WORKING || 
                    pegRevision == SVNRevision.COMMITTED || pegRevision == SVNRevision.UNDEFINED)) {
                int admLockLevel = getLevelsToLockFromDepth(SVNDepth.EMPTY);
                wcAccess = createWCAccess();
                wcAccess.probeOpen(path, false, admLockLevel);
            }
            long[] rev = { SVNRepository.INVALID_REVISION };
            repos = createRepository(url, path, adminArea, pegRevision, pegRevision, rev);
            return new Object[] { repos.getLocation(), SVNRevision.create(rev[0]) };
        } finally {
            if (wcAccess != null) {
                wcAccess.close();
            }
            if (repos != null) {
                repos.closeSession();
            }
        }
    }
    
    private void getLogMergedMergeInfoImpl(File path, SVNURL url, SVNRevision pegRevision, SVNURL mergeSrcURL, 
            File mergeSrcPath, SVNRevision srcPegRevision, boolean discoverChangedPaths, String[] revisionProperties, 
            ISVNLogEntryHandler handler) throws SVNException {
        SVNURL reposRoot[] = new SVNURL[1];
        
        Object[] location = getLocationFromPathAndRevision(mergeSrcPath, mergeSrcURL, srcPegRevision);
        SVNURL realMergeSrcURL = (SVNURL) location[0];
        SVNRevision realSrcPegRevision = (SVNRevision) location[1];

        Map targetMergeInfo = path != null ? getMergeInfo(path, pegRevision, reposRoot) : 
            getMergeInfo(url, pegRevision, reposRoot);
        if (targetMergeInfo == null) {
            return;
        }
        
        Map srcHistory = getHistoryAsMergeInfo(realMergeSrcURL, null, realSrcPegRevision, SVNRepository.INVALID_REVISION, 
                SVNRepository.INVALID_REVISION, null, null);
        Map mergeInfo = SVNMergeInfoUtil.intersectMergeInfo(targetMergeInfo, srcHistory, false);
        SVNMergeRangeList rangeList = new SVNMergeRangeList(new SVNMergeRange[0]);
        long youngestRev = SVNRepository.INVALID_REVISION;
        String logTarget = null;
        for (Iterator mergeInfoIter = mergeInfo.keySet().iterator(); mergeInfoIter.hasNext();) {
            String mergeSrc = (String) mergeInfoIter.next();
            SVNMergeRangeList list = (SVNMergeRangeList) mergeInfo.get(mergeSrc);
            SVNMergeRange[] listRanges = list.getRanges();
            SVNMergeRange range = listRanges[listRanges.length - 1];
            if (!SVNRevision.isValidRevisionNumber(youngestRev) || range.getEndRevision() > youngestRev) {
                youngestRev = range.getEndRevision();
                logTarget = mergeSrc;
            }
            rangeList = rangeList.merge(list);
        }
        if (rangeList.isEmpty()) {
            return;
        }
        getLogsForMergeInfoRangeList(reposRoot[0], new String [] { logTarget }, rangeList, discoverChangedPaths, 
                revisionProperties, handler);
    }

    private void getLogEligibleMergeInfoImpl(File path, SVNURL url, SVNRevision pegRevision, 
            SVNURL mergeSrcURL, File mergeSrcPath, SVNRevision srcPegRevision, boolean discoverChangedPaths, 
            String[] revisionProperties, ISVNLogEntryHandler handler) throws SVNException {
        SVNURL reposRoot[] = new SVNURL[1];

        Object[] location = getLocationFromPathAndRevision(mergeSrcPath, mergeSrcURL, srcPegRevision);
        SVNURL realMergeSrcURL = (SVNURL) location[0];
        SVNRevision realSrcPegRevision = (SVNRevision) location[1];

        Map mergeInfo = path != null ? getMergeInfo(path, pegRevision, reposRoot) : 
            getMergeInfo(url, pegRevision, reposRoot);
        Map history = getHistoryAsMergeInfo(url, path, pegRevision, SVNRepository.INVALID_REVISION, 
                SVNRepository.INVALID_REVISION, null, null);

        if (mergeInfo == null) {
            mergeInfo = history;
        } else {
            mergeInfo = SVNMergeInfoUtil.mergeMergeInfos(mergeInfo, history);
        }


        SVNRepository repos = null;
        Map sourceHistory = null;
        try {
            repos = createRepository(realMergeSrcURL, null, null, true);
            sourceHistory = getHistoryAsMergeInfo(realMergeSrcURL, null, realSrcPegRevision, 
                    SVNRepository.INVALID_REVISION, SVNRepository.INVALID_REVISION, repos, null);
        } finally {
            repos.closeSession();
        }
        
        Map availableMergeInfo = SVNMergeInfoUtil.removeMergeInfo(mergeInfo, sourceHistory, false);
        SVNMergeRangeList rangeList = new SVNMergeRangeList(new SVNMergeRange[0]);
        long youngestRev = SVNRepository.INVALID_REVISION;
        String logTarget = null;
        for (Iterator availableIter = availableMergeInfo.keySet().iterator(); availableIter.hasNext();) {
            String mergeSrc = (String) availableIter.next();
            SVNMergeRangeList availableRangeList = (SVNMergeRangeList) availableMergeInfo.get(mergeSrc);
            SVNMergeRange[] ranges = availableRangeList.getRanges();
            SVNMergeRange range = ranges[ranges.length - 1];
            if (!SVNRevision.isValidRevisionNumber(youngestRev) || range.getEndRevision() > youngestRev) {
                youngestRev = range.getEndRevision();
                logTarget = mergeSrc;
            }
            rangeList = rangeList.merge(availableRangeList);
        }
        
        if (rangeList.isEmpty()) {
            return;
        }
        
        getLogsForMergeInfoRangeList(reposRoot[0], new String[] { logTarget }, rangeList, discoverChangedPaths, 
                revisionProperties, handler);
    }

}