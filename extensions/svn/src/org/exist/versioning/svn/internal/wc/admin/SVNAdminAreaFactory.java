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
package org.exist.versioning.svn.internal.wc.admin;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.logging.Level;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNEventFactory;
import org.exist.versioning.svn.internal.wc.SVNFileType;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.exist.versioning.svn.wc.SVNEvent;
import org.exist.versioning.svn.wc.SVNEventAction;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNAdminAreaFactorySelector;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class SVNAdminAreaFactory implements Comparable {

    public static final int WC_FORMAT_13 = 4;
    public static final int WC_FORMAT_14 = 8;
    public static final int WC_FORMAT_15 = 9;
    public static final int WC_FORMAT_16 = 10;

    private static final Collection ourFactories = new TreeSet();
    private static boolean ourIsUpgradeEnabled = Boolean.valueOf(System.getProperty("svnkit.upgradeWC", System.getProperty("javasvn.upgradeWC", "true"))).booleanValue();
    private static ISVNAdminAreaFactorySelector ourSelector;
    private static ISVNAdminAreaFactorySelector ourDefaultSelector = new DefaultSelector();

    static {
        SVNAdminAreaFactory.registerFactory(new SVNAdminArea16Factory());
//        SVNAdminAreaFactory.registerFactory(new SVNAdminArea15Factory());
//        SVNAdminAreaFactory.registerFactory(new SVNAdminArea14Factory());
//        SVNAdminAreaFactory.registerFactory(new SVNXMLAdminAreaFactory());
    }
    
    public static void setUpgradeEnabled(boolean enabled) {
        ourIsUpgradeEnabled = enabled;
    }

    public static boolean isUpgradeEnabled() {
        return ourIsUpgradeEnabled;
    }

    public static void setSelector(ISVNAdminAreaFactorySelector selector) {
        ourSelector = selector;
    }
    
    public static ISVNAdminAreaFactorySelector getSelector() {
        return ourSelector != null ? ourSelector : ourDefaultSelector;
    }
    
	public static int checkWC(File path, boolean useSelector) throws SVNException {
		return checkWC(path, useSelector, Level.FINE);
	}

    public static int checkWC(File path, boolean useSelector, Level logLevel) throws SVNException {
        Collection enabledFactories = ourFactories;
        if (useSelector) {
            enabledFactories = getSelector().getEnabledFactories(path, enabledFactories, false);
        }
        SVNErrorMessage error = null;
        int version = -1;
        for(Iterator factories = enabledFactories.iterator(); factories.hasNext();) {
            SVNAdminAreaFactory factory = (SVNAdminAreaFactory) factories.next();
            try {
                version = factory.doCheckWC(path, logLevel);
                if (version == 0) {
                    return version;
                }
                
                if (version > factory.getSupportedVersion()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, 
                            "The path ''{0}'' appears to be part of a Subversion 1.7 or greater\n" +
                            "working copy.  Please upgrade your Subversion client to use this\n" +
                            "working copy.", 
                            path);
                    SVNErrorManager.error(err, SVNLogType.WC);
                } else if (version < factory.getSupportedVersion()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, 
                            "Working copy format of {0} is too old ({1}); please check out your working copy again", 
                            new Object[] {path, new Integer(version)});
                    SVNErrorManager.error(err, SVNLogType.WC);
                } 
            } catch (SVNException e) {
                if (error != null) {
                    error.setChildErrorMessage(e.getErrorMessage());
                } else {
                    error = e.getErrorMessage();
                }
                continue;
            }
            return version;
        }
        if (error == null) {
            if (path != null) {
                checkWCNG(path.getAbsoluteFile(), path);
            }
            error = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
        }
        if (error.getErrorCode() == SVNErrorCode.WC_UNSUPPORTED_FORMAT) {
            error.setChildErrorMessage(null);
        }
        SVNErrorManager.error(error, logLevel, SVNLogType.WC);
        return 0;
    }
    
    private static void checkWCNG(File path, File targetPath) throws SVNException {
        if (path == null) {
            return;
        }
        File dbFile = new Resource(path, ".svn/wc.db");
        SVNFileType type = SVNFileType.getType(dbFile);
        if (type == SVNFileType.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, 
                    "The path ''{0}'' appears to be part of Subversion 1.7 (SVNKit 1.4) or greater\n" +
                    "working copy rooted at ''{1}''.\n" +
                    "Please upgrade your Subversion (SVNKit) client to use this working copy.", 
                    new Object[] {targetPath, path});
            SVNErrorManager.error(err, SVNLogType.WC);
        }        
        checkWCNG(path.getParentFile(), targetPath);
    }
    
    public static SVNAdminArea open(File path, Level logLevel) throws SVNException {
        SVNErrorMessage error = null;
        int wcFormatVersion = -1;
        Collection enabledFactories = getSelector().getEnabledFactories(path, ourFactories, false);
        File adminDir = new Resource(path, SVNFileUtil.getAdminDirectoryName());
        File entriesFile = new Resource(adminDir, "entries");
        if (adminDir.isDirectory() && entriesFile.isFile()) {
            for (Iterator factories = enabledFactories.iterator(); factories.hasNext();) {
                SVNAdminAreaFactory factory = (SVNAdminAreaFactory) factories.next();
                try {
                    wcFormatVersion = factory.getVersion(path);
                    if (wcFormatVersion > factory.getSupportedVersion()) {                        
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, 
                                "The path ''{0}'' appears to be part of a Subversion 1.7 or greater\n" +
                                "working copy.  Please upgrade your Subversion client to use this\n" +
                                "working copy.", 
                                path);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    } else if (wcFormatVersion < factory.getSupportedVersion()) {                        
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT,
                                "Working copy format of {0} is too old ({1}); please check out your working copy again",
                                new Object[]{path, new Integer(wcFormatVersion)});
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                } catch (SVNException e) {
                    if (error != null) {
                        error.setChildErrorMessage(e.getErrorMessage());
                    } else {
                        error = e.getErrorMessage();
                    }
                    continue;
                }
                
                SVNAdminArea adminArea = factory.doOpen(path, wcFormatVersion);
                if (adminArea != null) {
                    adminArea.setWorkingCopyFormatVersion(wcFormatVersion);
                    return adminArea;
                }
            }
        }
        if (error == null) {
            if (path != null) {
                checkWCNG(path.getAbsoluteFile(), path);
            }
            error = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
        }
        if (error.getErrorCode() == SVNErrorCode.WC_UNSUPPORTED_FORMAT) {
            error.setChildErrorMessage(null);
        }
        SVNErrorManager.error(error, logLevel, SVNLogType.WC);
        return null;
    }

    public static SVNAdminArea upgrade(SVNAdminArea area) throws SVNException {
        if (isUpgradeEnabled() && !ourFactories.isEmpty()) {
            Collection enabledFactories = getSelector().getEnabledFactories(area.getRoot(), ourFactories, true);
            if (!enabledFactories.isEmpty()) {
                SVNAdminAreaFactory newestFactory = (SVNAdminAreaFactory) enabledFactories.iterator().next();
                SVNAdminArea newArea = newestFactory.doChangeWCFormat(area);
                if (newArea != null && newArea != area && newArea.getWCAccess() != null) {
                    SVNEvent event = SVNEventFactory.createSVNEvent(newArea.getRoot(), SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.UPGRADE, null, null, null);
                    newArea.getWCAccess().handleEvent(event, ISVNEventHandler.UNKNOWN);
                }
                area = newArea;
            }
        }
        return area;
    }

    public static SVNAdminArea changeWCFormat(SVNAdminArea adminArea, int format) throws SVNException {
        SVNAdminAreaFactory factory = getAdminAreaFactory(format);
        SVNAdminArea newArea = factory.doChangeWCFormat(adminArea);
        if (newArea != null && newArea != adminArea && newArea.getWCAccess() != null) {
            SVNEvent event = SVNEventFactory.createSVNEvent(newArea.getRoot(), SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.UPGRADE, null, null, null);
            newArea.getWCAccess().handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
        adminArea = newArea;
        return adminArea;
    }

    private static SVNAdminAreaFactory getAdminAreaFactory(int wcFormat) throws SVNException {
        if (wcFormat == SVNXMLAdminAreaFactory.WC_FORMAT) {
            return new SVNXMLAdminAreaFactory();
        }
        if (wcFormat == SVNAdminArea14Factory.WC_FORMAT) {
            return new SVNAdminArea14Factory();           
        }
        if (wcFormat == SVNAdminArea15Factory.WC_FORMAT) {
            return new SVNAdminArea15Factory();
        }
        if (wcFormat == SVNAdminArea16Factory.WC_FORMAT) {
            return new SVNAdminArea16Factory();
        }
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT), SVNLogType.DEFAULT);
        return null;
    }

    private static int readFormatVersion(File adminDir) throws SVNException {
        SVNErrorMessage error = null;
        int version = -1;
        
        Collection enabledFactories = getSelector().getEnabledFactories(adminDir.getParentFile(), ourFactories, false);
        for(Iterator factories = enabledFactories.iterator(); factories.hasNext();) {
            SVNAdminAreaFactory factory = (SVNAdminAreaFactory) factories.next();
            try {
                version = factory.getVersion(adminDir);
            } catch (SVNException e) {
                error = e.getErrorMessage();
                continue;
            }
            return version;
        }

        if (error == null) {
            error = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", adminDir);
        }
        SVNErrorManager.error(error, SVNLogType.WC);
        return -1;
    }

    public static void createVersionedDirectory(File path, String url, String rootURL, String uuid, long revNumber, SVNDepth depth) throws SVNException {
        if (!ourFactories.isEmpty()) {
            if (!checkAdminAreaExists(path, url, revNumber)) {
                Collection enabledFactories = getSelector().getEnabledFactories(path, ourFactories, true);
                if (!enabledFactories.isEmpty()) {
                    SVNAdminAreaFactory newestFactory = (SVNAdminAreaFactory) enabledFactories.iterator().next();
                    newestFactory.doCreateVersionedDirectory(path, url, rootURL, uuid, revNumber, depth);
                }
            }
        }
    }

    public static void createVersionedDirectory(File path, SVNURL url, SVNURL rootURL, String uuid, long revNumber, SVNDepth depth) throws SVNException {
        createVersionedDirectory(path, url != null ? url.toString() : null, rootURL != null ? rootURL.toString() : null, uuid, revNumber, depth);
    }
        
    private static boolean checkAdminAreaExists(File dir, String url, long revision) throws SVNException {
        File adminDir = new Resource(dir, SVNFileUtil.getAdminDirectoryName());
        if (adminDir.exists() && !adminDir.isDirectory()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "''{0}'' is not a directory", dir);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (!adminDir.exists()) {
            return false;
        } 
        
        boolean wcExists = false;
        try {
            readFormatVersion(adminDir);
            wcExists = true;
        } catch (SVNException svne) {
            return false;
        }
        
        if (wcExists) {
            SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
            SVNAdminArea adminArea = null;
            SVNEntry entry = null;
            try {
                adminArea = wcAccess.open(dir, false, 0);
                entry = adminArea.getVersionedEntry(adminArea.getThisDirName(), false);
            } finally {
                wcAccess.closeAdminArea(dir);
            }
            if (!entry.isScheduledForDeletion()) {
                if (entry.getRevision() != revision) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Revision {0} doesn''t match existing revision {1} in ''{2}''", new Object[]{new Long(revision), new Long(entry.getRevision()), dir});
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                if (!url.equals(entry.getURL())) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "URL ''{0}'' doesn''t match existing URL ''{1}'' in ''{2}''", new Object[]{url, entry.getURL(), dir});
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        }
        return wcExists;
    }

    public abstract int getSupportedVersion();
    
    protected abstract int getVersion(File path) throws SVNException;
    
    protected abstract SVNAdminArea doOpen(File path, int version) throws SVNException;

    protected abstract SVNAdminArea doChangeWCFormat(SVNAdminArea area) throws SVNException;

    protected abstract void doCreateVersionedDirectory(File path, String url, String rootURL, String uuid, long revNumber, SVNDepth depth) throws SVNException;

    protected abstract int doCheckWC(File path, Level logLevel) throws SVNException;

    protected static void registerFactory(SVNAdminAreaFactory factory) {
        if (factory != null) {
            ourFactories.add(factory);
        }
    }

    public int compareTo(Object o) {
        if (o == null || !(o instanceof SVNAdminAreaFactory)) {
            return -1;
        }
        int version = ((SVNAdminAreaFactory) o).getSupportedVersion(); 
        return getSupportedVersion() > version ? -1 : (getSupportedVersion() < version) ? 1 : 0; 
    }
    
    private static class DefaultSelector implements ISVNAdminAreaFactorySelector {

        public Collection getEnabledFactories(File path, Collection factories, boolean writeAccess) throws SVNException {
            return factories;
        }

    }
}
