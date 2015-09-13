package org.exist.util;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.xmldb.DatabaseImpl;

public class ConfigurationHelper {
    private final static Logger LOG = LogManager.getLogger(ConfigurationHelper.class); //Logger

    /**
     * Returns a file handle for eXist's home directory.
     * Order of tests is designed with the idea, the more precise it is,
     * the more the developer know what he is doing
     * <ol>
     *   <li>Brokerpool      : if eXist was already configured.
     *   <li>exist.home      : if exists
     *   <li>user.home       : if exists, with a conf.xml file
     *   <li>user.dir        : if exists, with a conf.xml file
     *   <li>classpath entry : if exists, with a conf.xml file
     * </ol>
     *
     * @return the path to exist home if known
     */
    public static Optional<Path> getExistHome() {
    	return getExistHome(DatabaseImpl.CONF_XML);
    }

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
     * @return the path to exist home if known
     */
    public static Optional<Path> getExistHome(final String config) {
    	// If eXist was already configured, then return 
    	// the existHome of this instance.
    	try {
    		final BrokerPool broker = BrokerPool.getInstance();
    		if(broker != null) {
    			final Optional<Path> existHome = broker.getConfiguration().getExistHome();
                if(existHome.isPresent()) {
                    LOG.debug("Got eXist home from broker: " + existHome);
                    return existHome;
                }
    		}
    	} catch(final Throwable e) {
            // Catch all potential problems
            LOG.debug("Could not retrieve instance of brokerpool: " + e.getMessage());
    	}
    	
        // try exist.home
        if (System.getProperty("exist.home") != null) {
            final Path existHome = ConfigurationHelper.decodeUserHome(System.getProperty("exist.home"));
            if (Files.isDirectory(existHome)) {
                LOG.debug("Got eXist home from system property 'exist.home': " + existHome.toAbsolutePath().toString());
                return Optional.of(existHome);
            }
        }
        
        // try user.home
        final Path userHome = Paths.get(System.getProperty("user.home"));
        if (Files.isDirectory(userHome) && Files.isRegularFile(userHome.resolve(config))) {
            LOG.debug("Got eXist home from system property 'user.home': " + userHome.toAbsolutePath().toString());
            return Optional.of(userHome);
        }
        
        
        // try user.dir
        final Path userDir = Paths.get(System.getProperty("user.dir"));
        if (Files.isDirectory(userDir) && Files.isRegularFile(userDir.resolve(config))) {
            LOG.debug("Got eXist home from system property 'user.dir': " + userDir.toAbsolutePath().toString());
            return Optional.of(userDir);
        }
        
        // try classpath
        final URL configUrl = ConfigurationHelper.class.getClassLoader().getResource(config);
        if (configUrl != null) {
            final Path existHome = Paths.get(configUrl.getPath()).getParent();
            LOG.debug("Got eXist home from classpath: " + existHome.toAbsolutePath().toString());
            return Optional.of(existHome);
        }
        
        return Optional.empty();
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
    public static Path lookup(final String path) {
        return lookup(path, Optional.empty());
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
    public static Path lookup(final String path, final Optional<Path> parent) {
        // resolvePath is used for things like ~user/folder
        final Path p = decodeUserHome(path);
        if (p.isAbsolute()) {
            return p;
        }

        return parent
                .orElse(getExistHome().orElse(Paths.get(System.getProperty("user.dir"))))
                .resolve(path);
    }
    
    
    /**
     * Resolves the given path by means of eventually replacing <tt>~</tt> with the users
     * home directory, taken from the system property <code>user.home</code>.
     *
     * @param path the path to resolve
     * @return the resolved path
     */
    public static Path decodeUserHome(final String path) {
        if (path != null && path.startsWith("~") && path.length() > 1) {
            return Paths.get(System.getProperty("user.home")).resolve(path.substring(1));
        } else {
            return Paths.get(path);
        }
    }
}
