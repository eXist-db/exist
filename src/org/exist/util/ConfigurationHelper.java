package org.exist.util;

import java.io.File;
import java.net.URL;

import org.apache.log4j.Logger;
import org.exist.storage.BrokerPool;

public class ConfigurationHelper {
    private final static Logger LOG = Logger.getLogger(ConfigurationHelper.class); //Logger

    /**
     * Returns a file handle for eXist's home directory.
     * Order of tests is designed with the idea, the more precise it is,
     * the more the developper know what he is doing
     * <ol>
     *   <li>Brokerpool      : if eXist was already configured.
     *   <li>exist.home      : if exists
     *   <li>user.home       : if exists, with a conf.xml file
     *   <li>user.dir        : if exists, with a conf.xml file
     *   <li>classpath entry : if exists, with a conf.xml file
     * </ol>
     *
     * @return the file handle or <code>null</code>
     */
    public static File getExistHome() {
    	File existHome = null;
    	
    	// If eXist was allready configured, then return 
    	// the existHome of this instance.
    	try {
    		BrokerPool broker = BrokerPool.getInstance();
    		if(broker != null) {
    			existHome = broker.getConfiguration().getExistHome();
                        LOG.debug("Got eXist home from broker: " + existHome);
    			return existHome;
    		}
    	} catch(Throwable e) {
            // Catch all potential problems
            LOG.debug("Could not retieve instance of brokerpool: " + e.getMessage());
    	}
    	
    	String config = "conf.xml";
        
        // try exist.home
        if (System.getProperty("exist.home") != null) {
            existHome = new File(ConfigurationHelper.decodeUserHome(System.getProperty("exist.home")));
            if (existHome.isDirectory()) {
                LOG.debug("Got eXist home from system property 'exist.home': " + existHome);
                return existHome;
            }
        }
        
        // try user.home
        existHome = new File(System.getProperty("user.home"));
        if (existHome.isDirectory() && new File(existHome, config).isFile()) {
            LOG.debug("Got eXist home from system property 'user.home': " + existHome);
            return existHome;
        }
        
        
        // try user.dir
        existHome = new File(System.getProperty("user.dir"));
        if (existHome.isDirectory() && new File(existHome, config).isFile()) {
            LOG.debug("Got eXist home from system property 'user.dir': " + existHome);
            return existHome;
        }
        
        // try classpath
        URL configUrl = ConfigurationHelper.class.getClassLoader().getResource(config);
        if (configUrl != null) {
            existHome = new File(configUrl.getPath()).getParentFile();
            LOG.debug("Got eXist home from classpath: " + existHome);
            return existHome;
        }
        
        existHome = null;
        return existHome;
    }
    
	/**
     * Returns a file handle for the given path, while <code>path</code> specifies
     * the path to an eXist configuration file or directory.
     * <br>
     * Note that relative paths are being interpreted relative to <code>exist.home</code>
     * or the current working directory, in case <code>exist.home</code> was not set.
     *
     * @param path the file path
     * @return the file handle
     */
    public static File lookup(String path) {
        return lookup(path, null);
    }
    
    /**
     * Returns a file handle for the given path, while <code>path</code> specifies
     * the path to an eXist configuration file or directory.
     * <br>
     * If <code>parent</code> is null, then relative paths are being interpreted
     * relative to <code>exist.home</code> or the current working directory, in
     * case <code>exist.home</code> was not set.
     *
     * @param path path to the file or directory
     * @param parent parent directory used to lookup <code>path</code>
     * @return the file handle
     */
    public static File lookup(String path, String parent) {
        // resolvePath is used for things like ~user/folder
        File f = new File(decodeUserHome(path));
        if (f.isAbsolute()) return f;
        if (parent == null) {
            File home = getExistHome();
            if (home == null)
                home = new File(System.getProperty("user.dir"));
            parent = home.getPath();
        }
        return new File(parent, path);
    }
    
    
    /**
     * Resolves the given path by means of eventually replacing <tt>~</tt> with the users
     * home directory, taken from the system property <code>user.home</code>.
     *
     * @param path the path to resolve
     * @return the resolved path
     */
    public static String decodeUserHome(String path) {
        if (path != null && path.startsWith("~") && path.length() > 1) {
            path = System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
