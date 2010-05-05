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
import java.text.DateFormat;
import java.util.Map;

import org.tmatesoft.svn.core.io.ISVNTunnelProvider;

/**
 * The <b>ISVNOptions</b> interface should be implemented to manage
 * global run-time configuration options. 
 * 
 * <p>
 * Like the Subversion client library SVNKit uses configuration options
 * during runtime. <b>ISVNOptions</b> is intended for managing those
 * options which are similar to ones you can meet in the <i>config</i> file 
 * located in the default Subversion configuration area - on <i>Windows</i> platforms
 * it's usually located in the <i>'Documents and Settings\UserName\Subversion'</i> 
 * (or simply <i>'%APPDATA%\Subversion'</i>) directory, on <i>Unix</i>-like platforms - in 
 * <i>'~/.subversion'</i>. <b>ISVNOptions</b> is not intended for managing those
 * options that can be met in the <i>servers</i> file (located in the same directory
 * as <i>config</i>) - options for network layers are managed by interfaces and classes
 * of the <B><A HREF="../auth/package-summary.html">org.tmatesoft.svn.core.auth</A></B> package. 
 * 
 * <p>
 * Every <b>SVN</b>*<b>Client</b>'s public constructor receives an <b>ISVNOptions</b> 
 * as a driver of the run-time configuration options. <b>SVNClientManager</b> also has
 * got several <b>newInstance()</b> methods that receive an options driver. Thus it's simpe 
 * to implement a specific options driver to <b>ISVNOptions</b> and use it instead of a default one.
 * However if you are not interested in customizing the run-time configuration area
 * you can use a default driver which uses config info from the default SVN configuration area (see
 * above).
 * 
 * <p>
 * Use {@link SVNWCUtil} to get a default options driver, like this:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.ISVNOptions;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNClientManager;
 * ...
 *     <span class="javacomment">//here the only one boolean parameter - <i>readonly</i> - enables</span>
 *     <span class="javacomment">//or disables writing to the config file: if true (like in this snippet) -</span>
 *     <span class="javacomment">//SVNKit can only read options from the config file but not write</span>
 *     ISVNOptions options = SVNWCUtil.createDefaultOptions(<span class="javakeyword">true</span>);
 *     SVNClientManager clientManager = SVNClientManager.newInstance(options, <span class="javastring">"name"</span>, <span class="javastring">"password"</span>);
 *     ...</pre>
 * <p> 
 * If you would like to have the default configuration area in a place different 
 * from the SVN default one, you should provide a preferred path to the config 
 * directory like this:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.ISVNOptions;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNClientManager;
 * ...
 *     File defaultConfigDir = <span class="javakeyword">new</span> File(<span class="javastring">"way/to/your/config/dir"</span>); 
 *     ISVNOptions options = SVNWCUtil.createDefaultOptions(defaultConfigDir, <span class="javakeyword">true</span>);
 *     SVNClientManager clientManager = SVNClientManager.newInstance(options, <span class="javastring">"name"</span>, <span class="javastring">"password"</span>);
 *     ...</pre><br />
 * In this case in the specified directory SVNKit will create necessary configuration files (in particular <i>config</i> and <i>servers</i>) which
 * are absolutely identical to those <u>default</u> ones (without any user's edits) located in the SVN config area.
 * 
 * <p>
 * Read also this <a href="http://svnbook.red-bean.com/nightly/en/svn-book.html#svn.advanced">Subversion book chapter</a> on runtime configuration area.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     SVNWCUtil
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public interface ISVNOptions extends ISVNTunnelProvider {
    
    /**
     * Determines if the commit-times option is enabled.  
     * 
     * <p>
     * The commit-times option makes checkout/update/switch/revert operations put
     * last-committed timestamps on every file they touch. 
     * 
     * <p>
     * This option corresponds to
     * the <i>'use-commit-times'</i> option that can be found in the 
     * SVN's <i>config</i> file under the <i>[miscellany]</i> section.
     * 
     * @return <span class="javakeyword">true</span> if commit-times
     *         are enabled, otherwise <span class="javakeyword">false</span>
     */
    public boolean isUseCommitTimes();

    /**
     * Returns all the global ignore patterns.
     * 
     * <p>
     * The global ignore patterns describe the names of 
     * files and directories that SVNKit should ignore during status, add and 
     * import operations. Similar to the 
     * <i>'global-ignores'</i> option that can be found in the SVN's <i>config</i> 
     * file under the <i>[miscellany]</i> section.
     * 
     * @return an array of patterns (that usually contain wildcards)
     *         that specify file and directory names to be ignored until
     *         they are versioned
     */
    public String[] getIgnorePatterns();

    /**
     * Collects and puts into a {@link java.util.Map} all 
     * autoproperties specified for the file name pattern matched by the 
     * target file name. 
     * 
     * <p>
     * If <code>fileName</code> matches any known file name pattern then
     * all properties set for that pattern will be collected and
     * placed into <code>target</code>. 
     * 
     * <p>
     * For one file name pattern there can be several autoproperties set,
     * delimited by ";".  
     * 
     * @param file      a target file
     * @param target    a {@link java.util.Map} that will receive
     *                  autoproperties
     * @return          <code>target</code> itself
     */
    public Map applyAutoProperties(File file, Map target);
    
    /**
     * Returns a factory object which is responsible for creating 
     * merger drivers. 
     * 
     * @return a factory that produces merger drivers
     *         for merge operations
     */
    public ISVNMergerFactory getMergerFactory();
    
    /**
     * Returns the date format used to format datestamps.
     * @return date format
     */
    public DateFormat getKeywordDateFormat();
    
    /**
     * Returns an array of path extensions which the user wants to preserve when conflict files are made. 
     * 
     * <p/>
     * If the extension of a conflicted path does not match any of the returned by this method or if this method
     * returns <span class="javakeyword">null</span>, the extension (if any) of the conflicted file will be 
     * cut off.     
     * 
     * @return  array of preserved file extensions   
     * @since   1.2.0, new in Subversion 1.5.0 
     */
    public String[] getPreservedConflictFileExtensions();

    /**
     * Says to a merge driver whether to allow all forward merges or not.
     * 
     * <p/> 
     * If this returns <span class="javakeyword">true</span>, we allow all forward-merges not already found in 
     * recorded mergeinfo, and thus we destroy the ability to, say, merge the whole of a branch to the trunk 
     * while automatically ignoring the revisions common to both.
     * 
     * <p/>
     * If this returns <span class="javakeyword">false</span>, we allow only forward-merges not found in either 
     * recorded mergeinfo or implicit mergeinfo (natural history), then the previous scenario works great, but 
     * we can't reverse-merge a previous change made to our line of history and then remake it (because the 
     * reverse-merge will leave no mergeinfo trace, and the remake-it attempt will still find the original 
     * change in natural mergeinfo. 
     * 
     * @return  <span class="javakeyword">true</span> to allow all forward-merges; otherwise <span class="javakeyword">false</span>
     * @since   1.2.0  
     */
    public boolean isAllowAllForwardMergesFromSelf();

    /**
     * Returns the native EOL marker bytes.
     * @return native EOL bytes
     * @since         1.2.0
     */
    public byte[] getNativeEOL();

    /**
     * Returns the native charset name. See also {@link org.tmatesoft.svn.core.SVNProperty#NATIVE}.
     * @return  native charset name
     * @since   1.2.0       
     */
    public String getNativeCharset();

    /**
     * Returns a hash holding file extensions to MIME types mappings. Extensions must not include 
     * the leading dot in their names. 
     * 
     * @return      map which keys are <code>String</code> file extensions, and values are <code>String</code>
     *              MIME types      
     * @since       1.2.0, New in Subversion 1.5.0
     */
    public Map getFileExtensionsToMimeTypes();
}
