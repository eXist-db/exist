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
import java.lang.reflect.Method;
import java.util.logging.Level;

import org.exist.EXistException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.DefaultSVNAuthenticationManager;
import org.exist.versioning.svn.internal.wc.DefaultSVNOptions;
import org.exist.versioning.svn.internal.wc.SVNExternal;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNVersionedProperties;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.exist.xmldb.XmldbURI;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;

/**
 * The <b>SVNWCUtil</b> is a utility class providing some common methods used
 * by Working Copy API classes for such purposes as creating default run-time
 * configuration and authentication drivers and some others.
 * 
 * 
 * @version 1.3
 * @author TMate Software Ltd., Peter Skoog
 * @since  1.2
 * @see ISVNOptions
 * @see <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNWCUtil {

    private static final String ECLIPSE_AUTH_MANAGER_CLASSNAME = "org.tmatesoft.svn.core.internal.wc.EclipseSVNAuthenticationManager";
    private static Boolean ourIsEclipse;

    /**
     * Gets the location of the default SVN's run-time configuration area on the
     * current machine. The result path depends on the platform on which SVNKit
     * is running:
     * <ul>
     * <li>on <i>Windows</i> this path usually looks like <i>'Documents and
     * Settings\UserName\Subversion'</i> or simply <i>'%APPDATA%\Subversion'</i>.
     * <li>on a <i>Unix</i>-like platform - <i>'~/.subversion'</i>.
     * </ul>
     * 
     * @return a {@link java.io.File} representation of the default SVN's
     *         run-time configuration area location
     */
    public static File getDefaultConfigurationDirectory() {
    	try {
			Subject subject = BrokerPool.getInstance().getSubject();
			XmldbURI home = null;//subject.getHome();
			if (home != null)
				return new Resource(home.append(".subversion"));
		} catch (EXistException e) {
			//XXX: log?
		}
        return new Resource("/system/etc/subversion");
    }

    /**
     * Creates a default authentication manager that uses the default SVN's
     * <i>servers</i> configuration and authentication storage. Whether the
     * default auth storage is used or not depends on the 'store-auth-creds'</i>
     * option that can be found in the SVN's <i>config</i> file under the
     * <i>[auth]</i> section.
     * 
     * @return a default implementation of the credentials and servers
     *         configuration driver interface
     * @see #getDefaultConfigurationDirectory()
     */
    public static ISVNAuthenticationManager createDefaultAuthenticationManager() {
        return createDefaultAuthenticationManager(getDefaultConfigurationDirectory(), null, null);
    }

    /**
     * Creates a default authentication manager that uses the <i>servers</i>
     * configuration and authentication storage located in the provided
     * directory. The authentication storage is enabled.
     * 
     * @param configDir
     *            a new location of the run-time configuration area
     * @return a default implementation of the credentials and servers
     *         configuration driver interface
     */
    public static ISVNAuthenticationManager createDefaultAuthenticationManager(File configDir) {
        return createDefaultAuthenticationManager(configDir, null, null, true);
    }

    /**
     * Creates a default authentication manager that uses the default SVN's
     * <i>servers</i> configuration and provided user's credentials. Whether
     * the default auth storage is used or not depends on the 'store-auth-creds'</i>
     * option that can be found in the SVN's <i>config</i> file under the
     * <i>[auth]</i> section.
     * 
     * @param userName
     *            a user's name
     * @param password
     *            a user's password
     * @return a default implementation of the credentials and servers
     *         configuration driver interface
     */
    public static ISVNAuthenticationManager createDefaultAuthenticationManager(String userName, String password) {
        return createDefaultAuthenticationManager(null, userName, password);
    }

    /**
     * Creates a default authentication manager that uses the provided
     * configuration directory and user's credentials. Whether the default auth
     * storage is used or not depends on the 'store-auth-creds'</i> option that
     * is looked up in the <i>config</i> file under the <i>[auth]</i> section.
     * Files <i>config</i> and <i>servers</i> will be created (if they still
     * don't exist) in the specified directory (they are the same as those ones
     * you can find in the default SVN's run-time configuration area).
     * 
     * @param configDir
     *            a new location of the run-time configuration area
     * @param userName
     *            a user's name
     * @param password
     *            a user's password
     * @return a default implementation of the credentials and servers
     *         configuration driver interface
     */
    public static ISVNAuthenticationManager createDefaultAuthenticationManager(File configDir, String userName, String password) {
        DefaultSVNOptions options = createDefaultOptions(configDir, true);
        boolean store = options.isAuthStorageEnabled();
        return createDefaultAuthenticationManager(configDir, userName, password, store);
    }

    /**
     * Creates a default authentication manager that uses the provided
     * configuration directory and user's credentials. The
     * <code>storeAuth</code> parameter affects on using the auth storage.
     * 
     * 
     * @param configDir
     *            a new location of the run-time configuration area
     * @param userName
     *            a user's name
     * @param password
     *            a user's password
     * @param storeAuth
     *            if <span class="javakeyword">true</span> then the auth
     *            storage is enabled, otherwise disabled
     * @return a default implementation of the credentials and servers
     *         configuration driver interface
     */
    public static ISVNAuthenticationManager createDefaultAuthenticationManager(File configDir, String userName, String password, boolean storeAuth) {
        return createDefaultAuthenticationManager(configDir, userName, password, null, null, storeAuth);
    }

    /**
     * Creates a default authentication manager that uses the provided
     * configuration directory and user's credentials. The
     * <code>storeAuth</code> parameter affects on using the auth storage.
     * 
     * 
     * @param configDir
     *            a new location of the run-time configuration area
     * @param userName
     *            a user's name
     * @param password
     *            a user's password
     * @param privateKey
     *            a private key file for SSH session
     * @param passphrase
     *            a passphrase that goes with the key file
     * @param storeAuth
     *            if <span class="javakeyword">true</span> then the auth
     *            storage is enabled, otherwise disabled
     * @return a default implementation of the credentials and servers
     *         configuration driver interface
     */
    public static ISVNAuthenticationManager createDefaultAuthenticationManager(File configDir, String userName, String password, File privateKey, String passphrase, boolean storeAuth) {
        return new DefaultSVNAuthenticationManager(configDir, storeAuth, userName, password, privateKey, passphrase);
    }
    
    /**
     * Creates a default run-time configuration options driver that uses the
     * provided configuration directory.
     * 
     * <p>
     * If <code>dir</code> is not <span class="javakeyword">null</span> then
     * all necessary config files (in particular <i>config</i> and <i>servers</i>)
     * will be created in this directory if they still don't exist. Those files
     * are the same as those ones you can find in the default SVN's run-time
     * configuration area.
     * 
     * @param dir
     *            a new location of the run-time configuration area
     * @param readonly
     *            if <span class="javakeyword">true</span> then run-time
     *            configuration options are available only for reading, if <span
     *            class="javakeyword">false</span> then those options are
     *            available for both reading and writing
     * @return a default implementation of the run-time configuration options
     *         driver interface
     */
    public static DefaultSVNOptions createDefaultOptions(File dir, boolean readonly) {
        return new DefaultSVNOptions(dir, readonly);
    }

    /**
     * Creates a default run-time configuration options driver that uses the
     * default SVN's run-time configuration area.
     * 
     * @param readonly
     *            if <span class="javakeyword">true</span> then run-time
     *            configuration options are available only for reading, if <span
     *            class="javakeyword">false</span> then those options are
     *            available for both reading and writing
     * @return a default implementation of the run-time configuration options
     *         driver interface
     * @see #getDefaultConfigurationDirectory()
     */
    public static DefaultSVNOptions createDefaultOptions(boolean readonly) {
        return new DefaultSVNOptions(null, readonly);
    }

    /**
     * Determines if a directory is under version control.
     * 
     * @param dir
     *            a directory to check
     * @return <span class="javakeyword">true</span> if versioned, otherwise
     *         <span class="javakeyword">false</span>
     */
    public static boolean isVersionedDirectory(File dir) {
        SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
        try {
	        wcAccess.open(dir, false, false, false, 0, Level.FINEST);
        } catch (SVNException e) {
            return false;
        } finally {
            try {
                wcAccess.close();
            } catch (SVNException e) {
                //
            }
        }
        return true;
    }

    /**
     * Determines if a directory is the root of the Working Copy.
     * 
     * @param versionedDir
     *            a versioned directory to check
     * @return <span class="javakeyword">true</span> if
     *         <code>versionedDir</code> is versioned and the WC root (or the
     *         root of externals if <code>considerExternalAsRoot</code> is
     *         <span class="javakeyword">true</span>), otherwise <span
     *         class="javakeyword">false</span>
     * @throws SVNException
     * @since 1.1
     */
    public static boolean isWorkingCopyRoot(final File versionedDir) throws SVNException {
        SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
        try {
	        wcAccess.open(versionedDir, false, false, false, 0, Level.FINEST);
	        return wcAccess.isWCRoot(versionedDir);
        } catch (SVNException e) {
            return false;
        } finally {
            wcAccess.close();
        }
    }

    /**
     * @param versionedDir
     *            a versioned directory to check
     * @param externalIsRoot
     * @return <span class="javakeyword">true</span> if
     *         <code>versionedDir</code> is versioned and the WC root (or the
     *         root of externals if <code>considerExternalAsRoot</code> is
     *         <span class="javakeyword">true</span>), otherwise <span
     *         class="javakeyword">false</span>
     * @throws SVNException
     * @deprecated use {@link #isWorkingCopyRoot(File)}} instead
     */
    public static boolean isWorkingCopyRoot(final File versionedDir, boolean externalIsRoot) throws SVNException {
        if (isWorkingCopyRoot(versionedDir)) {
            if (!externalIsRoot) {
                return true;

            }
            File root = getWorkingCopyRoot(versionedDir, false);
            return root.equals(versionedDir);
        }
        return false;
    }

    /**
     * Returns the Working Copy root directory given a versioned directory that
     * belongs to the Working Copy.
     * 
     * <p>
     * If both <span>versionedDir</span> and its parent directory are not
     * versioned this method returns <span class="javakeyword">null</span>.
     * 
     * @param versionedDir
     *            a directory belonging to the WC which root is to be searched
     *            for
     * @param stopOnExtenrals
     *            if <span class="javakeyword">true</span> then this method
     *            will stop at the directory on which any externals definitions
     *            are set
     * @return the WC root directory (if it is found) or <span
     *         class="javakeyword">null</span>.
     * @throws SVNException
     */
    public static File getWorkingCopyRoot(File versionedDir, boolean stopOnExtenrals) throws SVNException {
        versionedDir = versionedDir.getAbsoluteFile();
        if (versionedDir == null || 
                (!isVersionedDirectory(versionedDir) && 
                (versionedDir.getParentFile() == null || !isVersionedDirectory(versionedDir.getParentFile())))) {
            // both this dir and its parent are not versioned, 
            // or dir is root and not versioned
            return null;
        }

        File parent = versionedDir.getParentFile();
        if (parent == null) {
            return versionedDir;
        }

        if (isWorkingCopyRoot(versionedDir)) {
            // this is root.
            if (stopOnExtenrals) {
                return versionedDir;
            }
            File parentRoot = getWorkingCopyRoot(parent, stopOnExtenrals);
            if (parentRoot == null) {
                // if parent is not versioned return this dir.
                return versionedDir;
            }
            // parent is versioned. we have to check if it contains externals
            // definition for this dir.

            while (parent != null) {
                SVNWCAccess parentAccess = SVNWCAccess.newInstance(null);
                try {
                    SVNAdminArea dir = parentAccess.open(parent, false, 0);
                    SVNVersionedProperties props = dir.getProperties(dir.getThisDirName());
	                final String externalsProperty = props.getStringPropertyValue(SVNProperty.EXTERNALS);
	                SVNExternal[] externals = externalsProperty != null ? SVNExternal.parseExternals(dir.getRoot().getAbsolutePath(), externalsProperty) : new SVNExternal[0];
                    // now externals could point to our dir.
                    for (int i = 0; i < externals.length; i++) {
                        SVNExternal external = externals[i];
                        File externalFile = new Resource(parent, external.getPath());
                        if (externalFile.equals(versionedDir)) {
                            return parentRoot;
                        }
                    }
                } catch (SVNException e) {
                    if (e instanceof SVNCancelException) {
                        throw e;
                    }
                } finally {
                    parentAccess.close();
                }
                if (parent.equals(parentRoot)) {
                    break;
                }
                parent = parent.getParentFile();
            }
            return versionedDir;
        }

        return getWorkingCopyRoot(parent, stopOnExtenrals);
    }

    private static boolean isEclipse() {
        if (ourIsEclipse == null) {
            try {
                ClassLoader loader = SVNWCUtil.class.getClassLoader();
                if (loader == null) {
                    loader = ClassLoader.getSystemClassLoader();
                }
                Class platform = loader.loadClass("org.eclipse.core.runtime.Platform");
                Method isRunning = platform.getMethod("isRunning", new Class[0]);
                Object result = isRunning.invoke(null, new Object[0]);
                if (result != null && Boolean.TRUE.equals(result)) {
                    ourIsEclipse = Boolean.TRUE;
                    return true;
                }
            } catch (Throwable th) {
            }
            ourIsEclipse = Boolean.FALSE;
        }
        return ourIsEclipse.booleanValue();
    }
}