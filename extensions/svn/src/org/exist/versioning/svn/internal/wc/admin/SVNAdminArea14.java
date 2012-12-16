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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.core.internal.io.fs.FSFile;
import org.exist.versioning.svn.internal.util.SVNDate;
import org.exist.versioning.svn.internal.wc.SVNAdminUtil;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNFileListUtil;
import org.exist.versioning.svn.internal.wc.SVNFileType;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.SVNWCProperties;
import org.exist.versioning.svn.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminArea14 extends SVNAdminArea {

    public static final int WC_FORMAT = SVNAdminArea14Factory.WC_FORMAT;

    public static final String[] ourCachableProperties = new String[] {
        SVNProperty.SPECIAL,
        SVNProperty.EXTERNALS, 
        SVNProperty.NEEDS_LOCK
    };
    
    protected static final String ATTRIBUTE_COPIED = "copied";
    protected static final String ATTRIBUTE_DELETED = "deleted";
    protected static final String ATTRIBUTE_ABSENT = "absent";
    protected static final String ATTRIBUTE_INCOMPLETE = "incomplete";
    protected static final String ATTRIBUTE_HAS_PROPS = "has-props";
    protected static final String ATTRIBUTE_HAS_PROP_MODS = "has-prop-mods";
    protected static final String KILL_ADM_ONLY = "adm-only";
    protected static final String THIS_DIR = "";

    private static final Set INAPPLICABLE_PROPERTIES = new SVNHashSet();

    static {
        INAPPLICABLE_PROPERTIES.add(SVNProperty.KEEP_LOCAL);
        INAPPLICABLE_PROPERTIES.add(SVNProperty.CHANGELIST);
        INAPPLICABLE_PROPERTIES.add(SVNProperty.WORKING_SIZE);
        INAPPLICABLE_PROPERTIES.add(SVNProperty.DEPTH);
        INAPPLICABLE_PROPERTIES.add(SVNProperty.PROP_TIME);
        INAPPLICABLE_PROPERTIES.add(SVNProperty.FILE_EXTERNAL_PATH);
        INAPPLICABLE_PROPERTIES.add(SVNProperty.FILE_EXTERNAL_REVISION);
        INAPPLICABLE_PROPERTIES.add(SVNProperty.FILE_EXTERNAL_PEG_REVISION);
        INAPPLICABLE_PROPERTIES.add(SVNProperty.TREE_CONFLICT_DATA);
    }

    private File myLockFile;
    private File myEntriesFile;

    private static boolean ourIsOptimizedWritingEnabled;

    public SVNAdminArea14(File dir) {
        super(dir);
        myLockFile = new Resource(getAdminDirectory(), "lock");
        myEntriesFile = new Resource(getAdminDirectory(), "entries");
    }
    
    public static void setOptimizedWritingEnabled(boolean enabled) {
        ourIsOptimizedWritingEnabled = enabled;
    }

    public static String[] getCachableProperties() {
        return ourCachableProperties;
    }
    
    public void saveWCProperties(boolean close) throws SVNException {
        Map wcPropsCache = getWCPropertiesStorage(false);
        if (wcPropsCache == null) {
            return;
        }
        
        boolean hasAnyProps = false;
        File dstFile = getAdminFile("all-wcprops");
        File tmpFile = getAdminFile("tmp/all-wcprops");

        for(Iterator entries = wcPropsCache.keySet().iterator(); entries.hasNext();) {
            String name = (String)entries.next();
            SVNVersionedProperties props = (SVNVersionedProperties)wcPropsCache.get(name);
            if (!props.isEmpty()) {
                hasAnyProps = true;
                break;
            }
        }

        if (hasAnyProps) {
            OutputStream target = null;
            try {
                target = SVNFileUtil.openFileForWriting(tmpFile);
                SVNVersionedProperties props = (SVNVersionedProperties)wcPropsCache.get(getThisDirName());
                if (props != null && !props.isEmpty()) {
                    SVNWCProperties.setProperties(props.asMap(), target, SVNWCProperties.SVN_HASH_TERMINATOR);
                } else {
                    SVNWCProperties.setProperties(new SVNProperties(), target, SVNWCProperties.SVN_HASH_TERMINATOR);
                }
    
                for(Iterator entries = wcPropsCache.keySet().iterator(); entries.hasNext();) {
                    String name = (String)entries.next();
                    if (getThisDirName().equals(name)) {
                        continue;
                    }
                    props = (SVNVersionedProperties)wcPropsCache.get(name);
                    if (!props.isEmpty()) {
                        target.write(name.getBytes("UTF-8"));
                        target.write('\n');
                        SVNWCProperties.setProperties(props.asMap(), target, SVNWCProperties.SVN_HASH_TERMINATOR);
                    }
                }
            } catch (IOException ioe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                SVNErrorManager.error(err, ioe, SVNLogType.WC);
            } finally {
                SVNFileUtil.closeFile(target);
            }
            SVNFileUtil.rename(tmpFile, dstFile);
            SVNFileUtil.setReadonly(dstFile, true);
        } else {
            SVNFileUtil.deleteFile(dstFile);
        }
        if (close) {
            closeWCProperties();
        }
    }
    
    public SVNVersionedProperties getBaseProperties(String name) throws SVNException {
        Map basePropsCache = getBasePropertiesStorage(true);
        SVNVersionedProperties props = (SVNVersionedProperties)basePropsCache.get(name); 
        if (props != null) {
            return props;
        }
        
        SVNProperties baseProps = null;
        try {
            baseProps = readBaseProperties(name);
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Failed to load properties from disk");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }

        props = new SVNProperties13(baseProps);
        basePropsCache.put(name, props);
        return props;
    }

    public SVNVersionedProperties getRevertProperties(String name) throws SVNException {
        Map revertPropsCache = getRevertPropertiesStorage(true);
        SVNVersionedProperties props = (SVNVersionedProperties)revertPropsCache.get(name); 
        if (props != null) {
            return props;
        }
        
        SVNProperties revertProps = null;
        try {
            revertProps = readRevertProperties(name);
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Failed to load properties from disk");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }

        props = new SVNProperties13(revertProps);
        revertPropsCache.put(name, props);
        return props;
    }

    public SVNVersionedProperties getProperties(String name) throws SVNException {
        Map propsCache = getPropertiesStorage(true);
        SVNVersionedProperties props = (SVNVersionedProperties)propsCache.get(name); 
        if (props != null) {
            return props;
        }
        
        final String entryName = name;
        props =  new SVNProperties14(null, this, name){

            protected SVNProperties loadProperties() throws SVNException {
                SVNProperties props = getProperties();
                if (props == null) {
                    try {
                        props = readProperties(entryName);
                    } catch (SVNException svne) {
                        SVNErrorMessage err = svne.getErrorMessage().wrap("Failed to load properties from disk");
                        SVNErrorManager.error(err, SVNLogType.DEFAULT);
                    }
                    props = props != null ? props : new SVNProperties();
                    setPropertiesMap(props);
                }
                return props;
            }
        };

        propsCache.put(name, props);
        return props;
    }

    public SVNVersionedProperties getWCProperties(String entryName) throws SVNException {
        SVNEntry entry = getEntry(entryName, false);
        if (entry == null) {
            return null;
        } 
        
        Map wcPropsCache = getWCPropertiesStorage(true);
        SVNVersionedProperties props = (SVNVersionedProperties)wcPropsCache.get(entryName); 
        if (props != null) {
            return props;
        }
        
        if (wcPropsCache.isEmpty()) {
            wcPropsCache = readAllWCProperties();
        }

        props = (SVNVersionedProperties)wcPropsCache.get(entryName); 
        if (props == null) {
            props = new SVNProperties13(new SVNProperties());
            wcPropsCache.put(entryName, props);
        }
        return props;
    }
    
    private Map readAllWCProperties() throws SVNException {
        Map wcPropsCache = getWCPropertiesStorage(true);
        wcPropsCache.clear();
        File propertiesFile = getAdminFile("all-wcprops");
        if (!propertiesFile.exists()) {
            return wcPropsCache;
        } 

        FSFile wcpropsFile = null;
        try {
            wcpropsFile = new FSFile(propertiesFile);
            SVNProperties wcProps = wcpropsFile.readProperties(false, true);
            SVNVersionedProperties entryWCProps = new SVNProperties13(wcProps); 
            wcPropsCache.put(getThisDirName(), entryWCProps);
            
            String name = null;
            StringBuffer buffer = new StringBuffer();
            while(true) {
                try {
                    name = wcpropsFile.readLine(buffer);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.STREAM_UNEXPECTED_EOF && buffer.length() > 0) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Missing end of line in wcprops file for ''{0}''", getRoot());
                        SVNErrorManager.error(err, e, SVNLogType.WC);
                    }
                    break;
                }
                wcProps = wcpropsFile.readProperties(false, true);
                entryWCProps = new SVNProperties13(wcProps);
                wcPropsCache.put(name, entryWCProps);
                buffer.delete(0, buffer.length());
            }
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Failed to load properties from disk");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        } finally {
            wcpropsFile.close();
        }
        return wcPropsCache;
    }
    
    private SVNProperties readBaseProperties(String name) throws SVNException {
        File propertiesFile = getBasePropertiesFile(name, false);
        SVNWCProperties props = new SVNWCProperties(propertiesFile, null);
        return props.asMap();
    }

    private SVNProperties readRevertProperties(String name) throws SVNException {
        File propertiesFile = getRevertPropertiesFile(name, false);
        SVNWCProperties props = new SVNWCProperties(propertiesFile, null);
        return props.asMap();
    }
    
    private SVNProperties readProperties(String name) throws SVNException {
        if (hasPropModifications(name)) {
            File propertiesFile = getPropertiesFile(name, false);
            SVNWCProperties props = new SVNWCProperties(propertiesFile, null);
            return props.asMap();
        } 
            
        Map basePropsCache = getBasePropertiesStorage(true);
        if (basePropsCache != null ) {
            SVNVersionedProperties baseProps = (SVNVersionedProperties) basePropsCache.get(name);
            if (baseProps != null) {
                return baseProps.asMap();
            }
        } 
        if (hasProperties(name)) {
            return readBaseProperties(name);
        }        
        return new SVNProperties();
    }

    public void saveVersionedProperties(SVNLog log, boolean close) throws SVNException {
        SVNProperties command = new SVNProperties();
        Set processedEntries = new SVNHashSet();
        
        Map propsCache = getPropertiesStorage(false);
        if (propsCache != null && !propsCache.isEmpty()) {
            for(Iterator entries = propsCache.keySet().iterator(); entries.hasNext();) {
                String name = (String)entries.next();
                SVNVersionedProperties props = (SVNVersionedProperties)propsCache.get(name);
                if (props.isModified()) {
                    SVNVersionedProperties baseProps = getBaseProperties(name);
                    SVNVersionedProperties propsDiff = baseProps.compareTo(props);
                    String[] cachableProps = SVNAdminArea14.getCachableProperties();
                    command.put(SVNProperty.shortPropertyName(SVNProperty.CACHABLE_PROPS), 
                            asString(cachableProps, " "));
                    SVNProperties propsMap = props.loadProperties();
                    LinkedList presentProps = new LinkedList();
                    for (int i = 0; i < cachableProps.length; i++) {
                        if (propsMap.containsName(cachableProps[i])) {
                            presentProps.addLast(cachableProps[i]);
                        }
                    }

                    if (presentProps.size() > 0) {
                        String presentPropsString = asString((String[])presentProps.toArray(new String[presentProps.size()]), " ");
                        command.put(SVNProperty.shortPropertyName(SVNProperty.PRESENT_PROPS), presentPropsString);
                    } else {
                        command.put(SVNProperty.shortPropertyName(SVNProperty.PRESENT_PROPS), "");
                    }
                        
                    command.put(SVNProperty.shortPropertyName(SVNProperty.HAS_PROPS), 
                            SVNProperty.toString(!props.isEmpty()));
        
                    boolean hasPropModifications = !propsDiff.isEmpty();
                    command.put(SVNProperty.shortPropertyName(SVNProperty.HAS_PROP_MODS), SVNProperty.toString(hasPropModifications));
                    command.put(SVNLog.NAME_ATTR, name);
                    log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
                    processedEntries.add(name);
                    command.clear();
                        
                    String dstPath = getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
                    dstPath = getAdminDirectory().getName() + "/" + dstPath;
        
                    if (hasPropModifications) {
                        String tmpPath = "tmp/";
                        tmpPath += getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
                        File tmpFile = getAdminFile(tmpPath);
                        String srcPath = getAdminDirectory().getName() + "/" + tmpPath;
                        SVNWCProperties tmpProps = new SVNWCProperties(tmpFile, srcPath);
                        if (!props.isEmpty()) {
                            tmpProps.setProperties(props.asMap());
                        } else {
                            SVNFileUtil.createEmptyFile(tmpFile);
                        }
                        command.put(SVNLog.NAME_ATTR, srcPath);
                        command.put(SVNLog.DEST_ATTR, dstPath);
                        log.addCommand(SVNLog.MOVE, command, false);
                        command.clear();
                        command.put(SVNLog.NAME_ATTR, dstPath);
                        log.addCommand(SVNLog.READONLY, command, false);
                    } else {
                        command.put(SVNLog.NAME_ATTR, dstPath);
                        log.addCommand(SVNLog.DELETE, command, false);
                    }
                    command.clear();
                    props.setModified(false);
                }
            }
        }
        
        Map basePropsCache = getBasePropertiesStorage(false);
        if (basePropsCache != null && !basePropsCache.isEmpty()) {
            for(Iterator entries = basePropsCache.keySet().iterator(); entries.hasNext();) {
                String name = (String)entries.next();
                SVNVersionedProperties baseProps = (SVNVersionedProperties)basePropsCache.get(name);
                if (baseProps.isModified()) {
                    String dstPath = getThisDirName().equals(name) ? "dir-prop-base" : "prop-base/" + name + ".svn-base";
                    dstPath = getAdminDirectory().getName() + "/" + dstPath;
                    boolean isEntryProcessed = processedEntries.contains(name);
                    if (!isEntryProcessed) {
                        SVNVersionedProperties props = getProperties(name);
                        
                        String[] cachableProps = SVNAdminArea14.getCachableProperties();
                        command.put(SVNProperty.shortPropertyName(SVNProperty.CACHABLE_PROPS), asString(cachableProps, " "));
                        
                        SVNProperties propsMap = props.loadProperties();
                        LinkedList presentProps = new LinkedList();
                        for (int i = 0; i < cachableProps.length; i++) {
                            if (propsMap.containsName(cachableProps[i])) {
                                presentProps.addLast(cachableProps[i]);
                            }
                        }
                        
                        if (presentProps.size() > 0) {
                            String presentPropsString = asString((String[])presentProps.toArray(new String[presentProps.size()]), " ");
                            command.put(SVNProperty.shortPropertyName(SVNProperty.PRESENT_PROPS), presentPropsString);
                        } else {
                            command.put(SVNProperty.shortPropertyName(SVNProperty.PRESENT_PROPS), "");
                        }
                        
                        command.put(SVNProperty.shortPropertyName(SVNProperty.HAS_PROPS), SVNProperty.toString(!props.isEmpty()));
                        SVNVersionedProperties propsDiff = baseProps.compareTo(props);
                        boolean hasPropModifications = !propsDiff.isEmpty();
                        command.put(SVNProperty.shortPropertyName(SVNProperty.HAS_PROP_MODS), SVNProperty.toString(hasPropModifications));
                        command.put(SVNLog.NAME_ATTR, name);
                        log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
                        command.clear();
                        
                        if (!hasPropModifications) {
                            String workingPropsPath = getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
                            workingPropsPath = getAdminDirectory().getName() + "/" + workingPropsPath;
                            command.put(SVNLog.NAME_ATTR, workingPropsPath);
                            log.addCommand(SVNLog.DELETE, command, false);
                            command.clear();
                        }
                    }
                
                    if (baseProps.isEmpty()) {
                        command.put(SVNLog.NAME_ATTR, dstPath);
                        log.addCommand(SVNLog.DELETE, command, false);
                    } else {
                        String tmpPath = "tmp/";
                        tmpPath += getThisDirName().equals(name) ? "dir-prop-base" : "prop-base/" + name + ".svn-base";
                        File tmpFile = getAdminFile(tmpPath);
                        String srcPath = getAdminDirectory().getName() + "/" + tmpPath;
                        SVNWCProperties tmpProps = new SVNWCProperties(tmpFile, srcPath);
                        tmpProps.setProperties(baseProps.asMap());

                        command.put(SVNLog.NAME_ATTR, srcPath);
                        command.put(SVNLog.DEST_ATTR, dstPath);
                        log.addCommand(SVNLog.MOVE, command, false);
                        command.clear();
                        command.put(SVNLog.NAME_ATTR, dstPath);
                        log.addCommand(SVNLog.READONLY, command, false);
                    }
                    baseProps.setModified(false);
                }
            }
        }
        
        if (close) {
            closeVersionedProperties();
        }
    }

    public void installProperties(String name, SVNProperties baseProps, SVNProperties workingProps, SVNLog log, 
            boolean writeBaseProps, boolean close) throws SVNException {
        SVNProperties command = new SVNProperties();
        SVNNodeKind kind = name.equals(getThisDirName()) ? SVNNodeKind.DIR : SVNNodeKind.FILE;
        SVNProperties propDiff = baseProps.compareTo(workingProps);
        
        boolean hasPropMods = !propDiff.isEmpty();
        command.put(SVNProperty.shortPropertyName(SVNProperty.HAS_PROP_MODS), 
                SVNProperty.toString(hasPropMods));

        command.put(SVNProperty.shortPropertyName(SVNProperty.HAS_PROPS), 
                SVNProperty.toString(!workingProps.isEmpty()));
        
        String[] cachableProps = SVNAdminArea14.getCachableProperties();
        command.put(SVNProperty.shortPropertyName(SVNProperty.CACHABLE_PROPS), 
                asString(cachableProps, " "));

        LinkedList presentProps = new LinkedList();
        for (int i = 0; i < cachableProps.length; i++) {
            if (workingProps.containsName(cachableProps[i])) {
                presentProps.addLast(cachableProps[i]);
            }
        }

        if (presentProps.size() > 0) {
            String presentPropsString = asString((String[]) presentProps.toArray(new String[presentProps.size()]), " ");
            command.put(SVNProperty.shortPropertyName(SVNProperty.PRESENT_PROPS), presentPropsString);
        } else {
            command.put(SVNProperty.shortPropertyName(SVNProperty.PRESENT_PROPS), "");
        }

        command.put(SVNLog.NAME_ATTR, name);
        log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
        command.clear();

        String dstPath = SVNAdminUtil.getPropPath(name, kind, false);

        if (hasPropMods) {
            String tmpPath = SVNAdminUtil.getPropPath(name, kind, true);
            File tmpFile = getFile(tmpPath);
            SVNWCProperties tmpProps = new SVNWCProperties(tmpFile, tmpPath);
            if (!workingProps.isEmpty()) {
                tmpProps.setProperties(workingProps);
            } else {
                SVNFileUtil.createEmptyFile(tmpFile);
            }
            command.put(SVNLog.NAME_ATTR, tmpPath);
            command.put(SVNLog.DEST_ATTR, dstPath);
            log.addCommand(SVNLog.MOVE, command, false);
            command.clear();
            command.put(SVNLog.NAME_ATTR, dstPath);
            log.addCommand(SVNLog.READONLY, command, false);
        } else {
            if (hasPropModifications(name)) {
                command.put(SVNLog.NAME_ATTR, dstPath);
                log.addCommand(SVNLog.DELETE, command, false);
            }
        }

        command.clear();
        
        if (writeBaseProps) {
            String basePath = SVNAdminUtil.getPropBasePath(name, kind, false);
            if (!baseProps.isEmpty()) {
                String tmpPath = SVNAdminUtil.getPropBasePath(name, kind, true);
                File tmpFile = getFile(tmpPath);
                SVNWCProperties tmpProps = new SVNWCProperties(tmpFile, tmpPath);
                tmpProps.setProperties(baseProps);

                command.put(SVNLog.NAME_ATTR, tmpPath);
                command.put(SVNLog.DEST_ATTR, basePath);
                log.addCommand(SVNLog.MOVE, command, false);
                command.clear();
                command.put(SVNLog.NAME_ATTR, basePath);
                log.addCommand(SVNLog.READONLY, command, false);
            } else {
                if (hasProperties(name)) {
                    command.put(SVNLog.NAME_ATTR, basePath);
                    log.addCommand(SVNLog.DELETE, command, false);
                }
            }
        }
        
        if (close) {
            closeVersionedProperties();
        }
    }

    public void handleKillMe() throws SVNException {
        boolean killMe = isKillMe();
        if (killMe) {
            String contents = SVNFileUtil.readFile(getAdminFile(ADM_KILLME));
            boolean killAdmOnly = KILL_ADM_ONLY.equals(contents);
            SVNEntry entry = getEntry(getThisDirName(), false);
            long dirRevision = entry != null ? entry.getRevision() : -1;
            // deleted dir, files and entry in parent.
            File dir = getRoot();
            SVNWCAccess access = getWCAccess(); 
            boolean isWCRoot = access.isWCRoot(getRoot());
            try {
                removeFromRevisionControl(getThisDirName(), !killAdmOnly, false);
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.WC_LEFT_LOCAL_MOD) {
                    throw svne;
                }
            }
            if (isWCRoot) {
                return;
            }
            // compare revision with parent's one
            SVNAdminArea parentArea = access.retrieve(dir.getParentFile());
            SVNEntry parentEntry = parentArea.getEntry(parentArea.getThisDirName(), false);
            if (dirRevision > parentEntry.getRevision()) {
                SVNEntry entryInParent = parentArea.addEntry(dir.getName());
                Map attributes = new SVNHashMap();
                attributes.put(SVNProperty.DELETED, Boolean.TRUE.toString());
                attributes.put(SVNProperty.KIND, SVNProperty.KIND_DIR);
                attributes.put(SVNProperty.REVISION, Long.toString(dirRevision));
                parentArea.modifyEntry(entryInParent.getName(), attributes, true, false);
            }
        }
    }

    public void saveEntries(boolean close) throws SVNException {
        if (myEntries != null) {
            if (!isLocked()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "No write-lock in ''{0}''", getRoot());
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
            
            SVNEntry rootEntry = (SVNEntry) myEntries.get(getThisDirName());
            if (rootEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "No default entry in directory ''{0}''", getRoot());
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
            
            String reposURL = rootEntry.getRepositoryRoot();
            String url = rootEntry.getURL();
            if (reposURL != null && !SVNPathUtil.isAncestor(reposURL, url)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Entry ''{0}'' has inconsistent repository root and url", getThisDirName());
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
            
            Resource tmpFile = new Resource(getAdminDirectory(), "tmp/entries");
            Writer os = null;
            try {
                //os = new OutputStreamWriter(SVNFileUtil.openFileForWriting(tmpFile), "UTF-8");
                os = SVNFileUtil.openFileForWriting(tmpFile);
                writeEntries(os);
            } catch (IOException e) {
                SVNFileUtil.closeFile(os);
                SVNFileUtil.deleteFile(tmpFile);
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot write entries file ''{0}'': {1}", new Object[] {myEntriesFile, e.getMessage()});
                SVNErrorManager.error(err, e, SVNLogType.WC);
            } finally {
                SVNFileUtil.closeFile(os);
            }
            SVNFileUtil.setReadonly(tmpFile, true);
            SVNFileUtil.rename(tmpFile, myEntriesFile);

            if (close) {
                closeEntries();
            }
        }
    }

    protected Map fetchEntries() throws SVNException {
        if (!myEntriesFile.exists()) {
            return null;
        }
        
        Map entries = new SVNHashMap();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(myEntriesFile, SVNLogType.WC), "UTF-8"));
            //skip format line
            reader.readLine();
            int entryNumber = 1;
            while(true){
                try {
                    SVNEntry entry = readEntry(reader, entryNumber); 
                    if (entry == null) {
                        break;
                    } 
                    entries.put(entry.getName(), entry);
                } catch (SVNException svne) {
                    SVNErrorMessage err = svne.getErrorMessage().wrap("Error at entry {0} in entries file for ''{1}'':", new Object[]{new Integer(entryNumber), getRoot()});
                    SVNErrorManager.error(err, svne, SVNLogType.WC);
                }
                ++entryNumber;
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {myEntriesFile, e.getMessage()});
            SVNErrorManager.error(err, e, SVNLogType.WC);
        } finally {
            SVNFileUtil.closeFile(reader);
        }

        SVNEntry defaultEntry = (SVNEntry)entries.get(getThisDirName());
        if (defaultEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Missing default entry");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        Map defaultEntryAttrs = defaultEntry.asMap();
        if (defaultEntryAttrs.get(SVNProperty.REVISION) == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_REVISION, "Default entry has no revision number");
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (defaultEntryAttrs.get(SVNProperty.URL) == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Default entry is missing URL");
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        for (Iterator entriesIter = entries.keySet().iterator(); entriesIter.hasNext();) {
            String name = (String)entriesIter.next();
            SVNEntry entry = (SVNEntry)entries.get(name);
            if (getThisDirName().equals(name)) {
                continue;
            }
            
            Map entryAttributes = entry.asMap();
            SVNNodeKind kind = SVNNodeKind.parseKind((String)entryAttributes.get(SVNProperty.KIND));
            if (kind == SVNNodeKind.FILE) {
                if (entryAttributes.get(SVNProperty.REVISION) == null || Long.parseLong((String) entryAttributes.get(SVNProperty.REVISION), 10) < 0) {
                    entryAttributes.put(SVNProperty.REVISION, defaultEntryAttrs.get(SVNProperty.REVISION));
                }
                if (entryAttributes.get(SVNProperty.URL) == null) {
                    String rootURL = (String)defaultEntryAttrs.get(SVNProperty.URL);
                    String url = SVNPathUtil.append(rootURL, SVNEncodingUtil.uriEncode(name));
                    entryAttributes.put(SVNProperty.URL, url);
                }
                if (entryAttributes.get(SVNProperty.REPOS) == null) {
                    entryAttributes.put(SVNProperty.REPOS, defaultEntryAttrs.get(SVNProperty.REPOS));
                }
                if (entryAttributes.get(SVNProperty.UUID) == null) {
                    String schedule = (String)entryAttributes.get(SVNProperty.SCHEDULE);
                    if (!(SVNProperty.SCHEDULE_ADD.equals(schedule) || SVNProperty.SCHEDULE_REPLACE.equals(schedule))) {
                        entryAttributes.put(SVNProperty.UUID, defaultEntryAttrs.get(SVNProperty.UUID));
                    }
                }
                if (entryAttributes.get(SVNProperty.CACHABLE_PROPS) == null) {
                    entryAttributes.put(SVNProperty.CACHABLE_PROPS, defaultEntryAttrs.get(SVNProperty.CACHABLE_PROPS));
                }
            }
        }
        return entries;
    }

    protected SVNEntry readEntry(BufferedReader reader, int entryNumber) throws IOException, SVNException {
        String line = reader.readLine();
        if (line == null && entryNumber > 1) {
            return null;
        }

        String name = parseString(line);
        name = name != null ? name : getThisDirName();

        Map entryAttrs = new SVNHashMap();
        entryAttrs.put(SVNProperty.NAME, name);
        SVNEntry entry = new SVNEntry(entryAttrs, this, name);
        entry.setDepth(SVNDepth.INFINITY);
        
        line = reader.readLine();
        String kind = parseValue(line);
        if (kind != null) {
            SVNNodeKind parsedKind = SVNNodeKind.parseKind(kind); 
            if (parsedKind != SVNNodeKind.UNKNOWN && parsedKind != SVNNodeKind.NONE) {
                entryAttrs.put(SVNProperty.KIND, kind);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Entry ''{0}'' has invalid node kind", name);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        } else {
            entryAttrs.put(SVNProperty.KIND, SVNNodeKind.NONE.toString());
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String revision = parseValue(line);
        if (revision != null) {
            entryAttrs.put(SVNProperty.REVISION, revision);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String url = parseString(line);
        if (url != null) {
            entryAttrs.put(SVNProperty.URL, url);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String reposRoot = parseString(line);
        if (reposRoot != null && url != null && !SVNPathUtil.isAncestor(reposRoot, url)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Entry for ''{0}'' has invalid repository root", name);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (reposRoot != null) {
            entryAttrs.put(SVNProperty.REPOS, reposRoot);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String schedule = parseValue(line);
        if (schedule != null) {
            if (SVNProperty.SCHEDULE_ADD.equals(schedule) || SVNProperty.SCHEDULE_DELETE.equals(schedule) || SVNProperty.SCHEDULE_REPLACE.equals(schedule)) {
                entryAttrs.put(SVNProperty.SCHEDULE, schedule);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_ATTRIBUTE_INVALID, "Entry ''{0}'' has invalid ''{1}'' value", new Object[]{name, SVNProperty.SCHEDULE});
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String timestamp = parseValue(line);
        if (timestamp != null) {
            entryAttrs.put(SVNProperty.TEXT_TIME, timestamp);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String checksum = parseString(line);
        if (checksum != null) {
            entryAttrs.put(SVNProperty.CHECKSUM, checksum);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String committedDate = parseValue(line);
        if (committedDate != null) {
            entryAttrs.put(SVNProperty.COMMITTED_DATE, committedDate);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String committedRevision = parseValue(line);
        if (committedRevision != null) {
            entryAttrs.put(SVNProperty.COMMITTED_REVISION, committedRevision);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String committedAuthor = parseString(line);
        if (committedAuthor != null) {
            entryAttrs.put(SVNProperty.LAST_AUTHOR, committedAuthor);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean hasProps = parseBoolean(line, ATTRIBUTE_HAS_PROPS);
        if (hasProps) {
            entryAttrs.put(SVNProperty.HAS_PROPS, SVNProperty.toString(hasProps));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean hasPropMods = parseBoolean(line, ATTRIBUTE_HAS_PROP_MODS);
        if (hasPropMods) {
            entryAttrs.put(SVNProperty.HAS_PROP_MODS, SVNProperty.toString(hasPropMods));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String cachablePropsStr = parseValue(line);
        if (cachablePropsStr != null) {
            String[] cachableProps = fromString(cachablePropsStr, " ");
            entryAttrs.put(SVNProperty.CACHABLE_PROPS, cachableProps);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String presentPropsStr = parseValue(line);
        if (presentPropsStr != null) {
            String[] presentProps = fromString(presentPropsStr, " ");
            entryAttrs.put(SVNProperty.PRESENT_PROPS, presentProps);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String prejFile = parseString(line);
        if (prejFile != null) {
            entryAttrs.put(SVNProperty.PROP_REJECT_FILE, prejFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String conflictOldFile = parseString(line);
        if (conflictOldFile != null) {
            entryAttrs.put(SVNProperty.CONFLICT_OLD, conflictOldFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String conflictNewFile = parseString(line);
        if (conflictNewFile != null) {
            entryAttrs.put(SVNProperty.CONFLICT_NEW, conflictNewFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String conflictWorkFile = parseString(line);
        if (conflictWorkFile != null) {
            entryAttrs.put(SVNProperty.CONFLICT_WRK, conflictWorkFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean isCopied = parseBoolean(line, ATTRIBUTE_COPIED);
        if (isCopied) {
            entryAttrs.put(SVNProperty.COPIED, SVNProperty.toString(isCopied));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String copyfromURL = parseString(line);
        if (copyfromURL != null) {
            entryAttrs.put(SVNProperty.COPYFROM_URL, copyfromURL);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String copyfromRevision = parseValue(line);
        if (copyfromRevision != null) {
            entryAttrs.put(SVNProperty.COPYFROM_REVISION, copyfromRevision);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean isDeleted = parseBoolean(line, ATTRIBUTE_DELETED);
        if (isDeleted) {
            entryAttrs.put(SVNProperty.DELETED, SVNProperty.toString(isDeleted));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean isAbsent = parseBoolean(line, ATTRIBUTE_ABSENT);
        if (isAbsent) {
            entryAttrs.put(SVNProperty.ABSENT, SVNProperty.toString(isAbsent));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean isIncomplete = parseBoolean(line, ATTRIBUTE_INCOMPLETE);
        if (isIncomplete) {
            entryAttrs.put(SVNProperty.INCOMPLETE, SVNProperty.toString(isIncomplete));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String uuid = parseString(line);
        if (uuid != null) {
            entryAttrs.put(SVNProperty.UUID, uuid);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String lockToken = parseString(line);
        if (lockToken != null) {
            entryAttrs.put(SVNProperty.LOCK_TOKEN, lockToken);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String lockOwner = parseString(line);
        if (lockOwner != null) {
            entryAttrs.put(SVNProperty.LOCK_OWNER, lockOwner);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String lockComment = parseString(line);
        if (lockComment != null) {
            entryAttrs.put(SVNProperty.LOCK_COMMENT, lockComment);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String lockCreationDate = parseValue(line);
        if (lockCreationDate != null) {
            entryAttrs.put(SVNProperty.LOCK_CREATION_DATE, lockCreationDate);
        }

        if (readExtraOptions(reader, entryAttrs)) {
            return entry;
        }
        
        do {
            line = reader.readLine();
            if (line == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Missing entry terminator");
                SVNErrorManager.error(err, SVNLogType.WC);
            } else if (line.length() == 1 && line.charAt(0) == '\f') {
                break;
            }
        } while (line != null);

        return entry;
    }
    
    protected boolean isEntryFinished(String line) {
        return line != null && line.length() > 0 && line.charAt(0) == '\f';
    }
    
    protected boolean parseBoolean(String line, String field) throws SVNException {
        line = parseValue(line);
        if (line != null) {
            if (!line.equals(field)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid value for field ''{0}''", field);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            return true;
        }
        return false;
    }
    
    protected String parseString(String line) throws SVNException {
        if (line == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Unexpected end of entry");
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if ("".equals(line)) {
            return null;
        }
        
        int fromIndex = 0;
        int ind = -1;
        StringBuffer buffer = null;
        String escapedString = null;
        while ((ind = line.indexOf('\\', fromIndex)) != -1) {
            if (line.length() < ind + 4 || line.charAt(ind + 1) != 'x' || !SVNEncodingUtil.isHexDigit(line.charAt(ind + 2)) || !SVNEncodingUtil.isHexDigit(line.charAt(ind + 3))) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid escape sequence");
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (buffer == null) {
                buffer = new StringBuffer();
            }

            escapedString = line.substring(ind + 2, ind + 4);  
            int escapedByte = Integer.parseInt(escapedString, 16);
            
            if (ind > fromIndex) {
                buffer.append(line.substring(fromIndex, ind));
                buffer.append((char)(escapedByte & 0xFF));
            } else {
                buffer.append((char)(escapedByte & 0xFF));
            }
            fromIndex = ind + 4;
        }
        
        if (buffer != null) {
            if (fromIndex < line.length()) {
                buffer.append(line.substring(fromIndex));
            }
            return buffer.toString();
        }   
        return line;
    }
    
    protected String parseValue(String line) throws SVNException {
        if (line == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Unexpected end of entry");
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if ("".equals(line)) {
            return null;
        }
        return line;
    }
    
    public String getThisDirName() {
        return THIS_DIR;
    }
    
    protected boolean readExtraOptions(BufferedReader reader, Map entryAttrs) throws SVNException, IOException {
        return false;
    }
    
    protected void writeEntries(Writer writer) throws IOException, SVNException {
        SVNEntry rootEntry = (SVNEntry)myEntries.get(getThisDirName());
        writer.write(getFormatVersion() + "\n");
        writeEntry(writer, getThisDirName(), rootEntry.asMap(), null);

        List names = new ArrayList(myEntries.keySet());
        Collections.sort(names);
        for (Iterator entries = names.iterator(); entries.hasNext();) {
            String name = (String)entries.next();
            SVNEntry entry = (SVNEntry)myEntries.get(name);
            if (getThisDirName().equals(name)) {
                continue;
            }

            Map entryAttributes = entry.asMap();
            Map defaultEntryAttrs = rootEntry.asMap();
            SVNNodeKind kind = SVNNodeKind.parseKind((String)entryAttributes.get(SVNProperty.KIND));
            if (kind == SVNNodeKind.FILE) {
                if (entryAttributes.get(SVNProperty.REVISION) == null || Long.parseLong((String) entryAttributes.get(SVNProperty.REVISION), 10) < 0) {
                    entryAttributes.put(SVNProperty.REVISION, defaultEntryAttrs.get(SVNProperty.REVISION));
                }
                if (entryAttributes.get(SVNProperty.URL) == null) {
                    String rootURL = (String)defaultEntryAttrs.get(SVNProperty.URL);
                    String url = SVNPathUtil.append(rootURL, SVNEncodingUtil.uriEncode(name));
                    entryAttributes.put(SVNProperty.URL, url);
                }
                if (entryAttributes.get(SVNProperty.REPOS) == null) {
                    entryAttributes.put(SVNProperty.REPOS, defaultEntryAttrs.get(SVNProperty.REPOS));
                }
                if (entryAttributes.get(SVNProperty.UUID) == null) {
                    String schedule = (String)entryAttributes.get(SVNProperty.SCHEDULE);
                    if (!(SVNProperty.SCHEDULE_ADD.equals(schedule) || SVNProperty.SCHEDULE_REPLACE.equals(schedule))) {
                        entryAttributes.put(SVNProperty.UUID, defaultEntryAttrs.get(SVNProperty.UUID));
                    }
                }
                if (entryAttributes.get(SVNProperty.CACHABLE_PROPS) == null) {
                    entryAttributes.put(SVNProperty.CACHABLE_PROPS, defaultEntryAttrs.get(SVNProperty.CACHABLE_PROPS));
                }
            }

            writeEntry(writer, name, entryAttributes, rootEntry.asMap());
        }
    }

    private void writeEntry(Writer writer, String name, Map entry, Map rootEntry) throws IOException, SVNException {
        boolean isThisDir = getThisDirName().equals(name);
        boolean isSubDir = !isThisDir && SVNProperty.KIND_DIR.equals(entry.get(SVNProperty.KIND)); 
        int emptyFields = 0;
        
        if (!writeString(writer, name, emptyFields)) {
            ++emptyFields;
        }
        
        String kind = (String)entry.get(SVNProperty.KIND);
        if (writeValue(writer, kind, emptyFields)){
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String revision = null;
        if (isThisDir){ 
            revision = (String)entry.get(SVNProperty.REVISION);
        } else if (!isSubDir){
            revision = (String)entry.get(SVNProperty.REVISION);
            if (revision != null && revision.equals(rootEntry.get(SVNProperty.REVISION))) {
                revision = null;
            }
        }
        if (writeRevision(writer, revision, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String url = null;
        if (isThisDir) {
            url = (String)entry.get(SVNProperty.URL);
        } else if (!isSubDir) {
            url = (String)entry.get(SVNProperty.URL);
            String expectedURL = SVNPathUtil.append((String)rootEntry.get(SVNProperty.URL), SVNEncodingUtil.uriEncode(name));
            if (url != null && url.equals(expectedURL)) {
                url = null;
            }
        }
        if (writeString(writer, url, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String root = null;
        if (isThisDir) {
            root = (String)entry.get(SVNProperty.REPOS);
        } else if (!isSubDir) {
            String thisDirRoot = (String)rootEntry.get(SVNProperty.REPOS);
            root = (String)entry.get(SVNProperty.REPOS);
            if (root != null && root.equals(thisDirRoot)) {
                root = null;
            }
        }
        if (writeString(writer, root, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String schedule = (String)entry.get(SVNProperty.SCHEDULE);
        if (schedule != null && (!SVNProperty.SCHEDULE_ADD.equals(schedule) && !SVNProperty.SCHEDULE_DELETE.equals(schedule) && !SVNProperty.SCHEDULE_REPLACE.equals(schedule))) {
            schedule = null;
        }
        if (writeValue(writer, schedule, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String textTime = (String)entry.get(SVNProperty.TEXT_TIME);
        if (writeTime(writer, textTime, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String checksum = (String)entry.get(SVNProperty.CHECKSUM);
        if (writeValue(writer, checksum, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String committedDate = (String)entry.get(SVNProperty.COMMITTED_DATE);
        if (writeTime(writer, committedDate, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String committedRevision = (String)entry.get(SVNProperty.COMMITTED_REVISION);
        if (writeRevision(writer, committedRevision, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String committedAuthor = (String)entry.get(SVNProperty.LAST_AUTHOR);
        if (writeString(writer, committedAuthor, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String hasProps = (String)entry.get(SVNProperty.HAS_PROPS);
        if (SVNProperty.booleanValue(hasProps)) {
            writeValue(writer, ATTRIBUTE_HAS_PROPS, emptyFields);
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String hasPropMods = (String)entry.get(SVNProperty.HAS_PROP_MODS);
        if (SVNProperty.booleanValue(hasPropMods)) {
            writeValue(writer, ATTRIBUTE_HAS_PROP_MODS, emptyFields);
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String cachableProps = asString((String[])entry.get(SVNProperty.CACHABLE_PROPS), " ");
        if (!isThisDir) {             
            String thisDirCachableProps = asString((String[])rootEntry.get(SVNProperty.CACHABLE_PROPS), " ");
            if (thisDirCachableProps != null && cachableProps != null && thisDirCachableProps.equals(cachableProps)) {
                cachableProps = null;
            }
        }
        if (writeValue(writer, cachableProps, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String presentProps = asString((String[])entry.get(SVNProperty.PRESENT_PROPS), " ");
        if (writeValue(writer, presentProps, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String propRejectFile = (String)entry.get(SVNProperty.PROP_REJECT_FILE);
        if (writeString(writer, propRejectFile, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String conflictOldFile = (String)entry.get(SVNProperty.CONFLICT_OLD);
        if (writeString(writer, conflictOldFile, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String conflictNewFile = (String)entry.get(SVNProperty.CONFLICT_NEW);
        if (writeString(writer, conflictNewFile, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
    
        String conflictWrkFile = (String)entry.get(SVNProperty.CONFLICT_WRK);
        if (writeString(writer, conflictWrkFile, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String copiedAttr = (String)entry.get(SVNProperty.COPIED);
        if (SVNProperty.booleanValue(copiedAttr)) {
            writeValue(writer, ATTRIBUTE_COPIED, emptyFields);
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String copyfromURL = (String)entry.get(SVNProperty.COPYFROM_URL);
        if (writeString(writer, copyfromURL, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String copyfromRevision = (String)entry.get(SVNProperty.COPYFROM_REVISION);
        if (writeRevision(writer, copyfromRevision, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String deletedAttr = (String)entry.get(SVNProperty.DELETED);
        if (SVNProperty.booleanValue(deletedAttr)) {
            writeValue(writer, ATTRIBUTE_DELETED, emptyFields);
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String absentAttr = (String)entry.get(SVNProperty.ABSENT);
        if (SVNProperty.booleanValue(absentAttr)) {
            writeValue(writer, ATTRIBUTE_ABSENT, emptyFields);
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String incompleteAttr = (String)entry.get(SVNProperty.INCOMPLETE);
        if (SVNProperty.booleanValue(incompleteAttr)) {
            writeValue(writer, ATTRIBUTE_INCOMPLETE, emptyFields);
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String uuid = (String)entry.get(SVNProperty.UUID);
        if (!isThisDir) {             
            String thisDirUUID = (String)rootEntry.get(SVNProperty.UUID);
            if (thisDirUUID != null && uuid != null && thisDirUUID.equals(uuid)) {
                uuid = null;
            }
        }
        if (writeValue(writer, uuid, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String lockToken = (String)entry.get(SVNProperty.LOCK_TOKEN);
        if (writeString(writer, lockToken, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String lockOwner = (String)entry.get(SVNProperty.LOCK_OWNER);
        if (writeString(writer, lockOwner, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String lockComment = (String)entry.get(SVNProperty.LOCK_COMMENT);
        if (writeString(writer, lockComment, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String lockCreationDate = (String)entry.get(SVNProperty.LOCK_CREATION_DATE);
        if (writeTime(writer, lockCreationDate, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        writeExtraOptions(writer, name, entry, emptyFields);
        writer.write("\f\n");
        writer.flush();
    }
    
    protected int writeExtraOptions(Writer writer, String entryName, Map entryAttrs, int emptyFields) throws SVNException, IOException {
        return emptyFields;
    }
    
    protected boolean writeString(Writer writer, String str, int emptyFields) throws IOException {
        if (str != null && str.length() > 0) {
            for (int i = 0; i < emptyFields; i++) {
                writer.write('\n');
            }
            for (int i = 0; i < str.length(); i++) {
                char ch = str.charAt(i);
                if (SVNEncodingUtil.isASCIIControlChar(ch) || ch == '\\') {
                    writer.write("\\x");
                    writer.write(SVNFormatUtil.getHexNumberFromByte((byte)ch));
                } else {
                    writer.write(ch);
                }
            }
            writer.write('\n');
            return true;
        }
        return false;
    }
    
    protected boolean writeValue(Writer writer, String val, int emptyFields) throws IOException {
        if (val != null && val.length() > 0) {
            for (int i = 0; i < emptyFields; i++) {
                writer.write('\n');
            }
            writer.write(val);
            writer.write('\n');
            return true;
        }
        return false;
    }
    
    protected boolean writeTime(Writer writer, String val, int emptyFields) throws IOException {
        if (val != null && val.length() > 0) {
            long time = SVNDate.parseDateAsMilliseconds(val);
            if (time > 0) {
                for (int i = 0; i < emptyFields; i++) {
                    writer.write('\n');
                }
                writer.write(val);
                writer.write('\n');
                return true;
            }
        }
        return false;
    }

    protected boolean writeRevision(Writer writer, String rev, int emptyFields) throws IOException {
        long revValue = -1;
        try {
            revValue = Long.parseLong(rev);
        } catch (NumberFormatException nfe) {
            //
        }
        if (rev != null && rev.length() > 0 && revValue >= 0) {
            for (int i = 0; i < emptyFields; i++) {
                writer.write('\n');
            }
            writer.write(rev);
            writer.write('\n');
            return true;
        }
        return false;
    }
    
    public boolean hasPropModifications(String name) throws SVNException {
        SVNEntry entry = getEntry(name, true);
        if (entry != null) {
            Map entryAttrs = entry.asMap();
            return SVNProperty.booleanValue((String)entryAttrs.get(SVNProperty.HAS_PROP_MODS));
        }
        return false;
    }
    
    public boolean hasProperties(String name) throws SVNException {
        SVNEntry entry = getEntry(name, true);
        if (entry != null) {
            Map entryAttrs = entry.asMap();
            return SVNProperty.booleanValue((String)entryAttrs.get(SVNProperty.HAS_PROPS));
        }
        return false;
    }
    
    public boolean lock() throws SVNException {
        return lock(false);
    }

    public boolean lock(boolean stealLock) throws SVNException {
        if (!isVersioned()) {
            return false;
        }
        if (stealLock && myLockFile.isFile()) {
            setLocked(true);
            return true;
        }
        boolean created = false;
        try {
            // Stian was here.
            created = SVNFileUtil.createNewFile(myLockFile);
        } catch (IOException e) {
            SVNErrorCode code = e.getMessage().indexOf("denied") >= 0 ? SVNErrorCode.WC_LOCKED : SVNErrorCode.WC_NOT_LOCKED;
            SVNErrorMessage err = SVNErrorMessage.create(code, "Cannot lock working copy ''{0}'': {1}", 
                    new Object[] {getRoot(), e.getLocalizedMessage()});
            SVNErrorManager.error(err, e, SVNLogType.WC);
        }
        if (created) {
            setLocked(true);
            return created;
        }
        if (myLockFile.isFile()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' locked; try performing ''cleanup''", getRoot());
            SVNErrorManager.error(err, SVNLogType.WC);
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Cannot lock working copy ''{0}''", getRoot());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return false;
    }
    
    public SVNAdminArea createVersionedDirectory(File dir, String url, String rootURL, String uuid, 
            long revNumber, boolean createMyself, SVNDepth depth) throws SVNException {
        dir = createMyself ? getRoot() : dir;
        dir.mkdirs();
        File adminDir = createMyself ? getAdminDirectory() : new Resource(dir, SVNFileUtil.getAdminDirectoryName());
        adminDir.mkdir();
        SVNFileUtil.setHidden(adminDir, true);
        // lock dir.
        File lockFile = createMyself ? myLockFile : new Resource(adminDir, "lock");
        SVNFileUtil.createEmptyFile(lockFile);

        File[] tmp = {
                createMyself ? getAdminFile("tmp") : new Resource(adminDir, "tmp"),
                createMyself ? getAdminFile("tmp" + Resource.separatorChar + "props") : new Resource(adminDir, "tmp" + Resource.separatorChar + "props"),
                createMyself ? getAdminFile("tmp" + Resource.separatorChar + "prop-base") : new Resource(adminDir, "tmp" + Resource.separatorChar + "prop-base"),
                createMyself ? getAdminFile("tmp" + Resource.separatorChar + "text-base") : new Resource(adminDir, "tmp" + Resource.separatorChar + "text-base"),
                createMyself ? getAdminFile("props") : new Resource(adminDir, "props"), 
                createMyself ? getAdminFile("prop-base") : new Resource(adminDir, "prop-base"),
                createMyself ? getAdminFile("text-base") : new Resource(adminDir, "text-base")
                };

        for (int i = 0; i < tmp.length; i++) {
            tmp[i].mkdir();
        }
        // for backward compatibility 
        createFormatFile(createMyself ? null : new Resource(adminDir, "format"), createMyself);

        SVNAdminArea adminArea = createMyself ? this : createAdminAreaForDir(dir);
        adminArea.setLocked(true);
        SVNEntry rootEntry = adminArea.getEntry(adminArea.getThisDirName(), true);
        if (rootEntry == null) {
            rootEntry = adminArea.addEntry(adminArea.getThisDirName());
        }
        if (url != null) {
            rootEntry.setURL(url);
        }
        rootEntry.setRepositoryRoot(rootURL);
        rootEntry.setRevision(revNumber);
        rootEntry.setKind(SVNNodeKind.DIR);
        rootEntry.setDepth(depth);
        if (uuid != null) {
            rootEntry.setUUID(uuid);
        }
        if (revNumber > 0) {
            rootEntry.setIncomplete(true);
        }
        rootEntry.setCachableProperties(ourCachableProperties);
        try {
            adminArea.saveEntries(false);
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Error writing entries file for ''{0}''", dir);
            SVNErrorManager.error(err, svne, SVNLogType.WC);
        }
        
        // unlock dir.
        SVNFileUtil.deleteFile(lockFile);
        return adminArea;
    }

    protected SVNVersionedProperties formatBaseProperties(SVNProperties srcProperties) {
        SVNProperties props = new SVNProperties(srcProperties);
        return new SVNProperties13(props);
    }

    protected SVNVersionedProperties formatProperties(SVNEntry entry, SVNProperties srcProperties) {
        SVNProperties props = new SVNProperties(srcProperties);
        return new SVNProperties14(props, this, entry.getName()) {

            protected SVNProperties loadProperties() throws SVNException {
                return getProperties();
            }
        };
    }

    private void makeKillMe(boolean killAdminOnly) throws SVNException {
        File killMe = getAdminFile(ADM_KILLME);
        if (killMe.getParentFile().isDirectory()) {
            SVNFileUtil.createFile(killMe, killAdminOnly ? KILL_ADM_ONLY : null, null);
        }
    }

    public void postCommit(String fileName, long revisionNumber, boolean implicit, boolean rerun, SVNErrorCode errorCode) throws SVNException {
        SVNEntry entry = getEntry(fileName, true);
        if (entry == null || (!getThisDirName().equals(fileName) && entry.getKind() != SVNNodeKind.FILE)) {
            SVNErrorMessage err = SVNErrorMessage.create(errorCode, "Log command for directory ''{0}'' is mislocated", getRoot()); 
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (!implicit && entry.isScheduledForDeletion()) {
            if (getThisDirName().equals(fileName)) {
                entry.setRevision(revisionNumber);
                entry.setKind(SVNNodeKind.DIR);
                if (rerun) {
                    File killMe = getAdminFile(ADM_KILLME);
                    if (killMe.isFile()) {
                        return;
                    }
                }
                makeKillMe(entry.isKeepLocal());
            } else {
                removeFromRevisionControl(fileName, false, false);
                SVNEntry parentEntry = getEntry(getThisDirName(), true);
                if (revisionNumber > parentEntry.getRevision()) {
                    SVNEntry fileEntry = addEntry(fileName);
                    fileEntry.setKind(SVNNodeKind.FILE);
                    fileEntry.setDeleted(true);
                    fileEntry.setRevision(revisionNumber);
                }
            }
            return;
        }

        if (!implicit && entry.isScheduledForReplacement() && getThisDirName().equals(fileName)) {
            for (Iterator ents = entries(true); ents.hasNext();) {
                SVNEntry currentEntry = (SVNEntry) ents.next();
                if (!currentEntry.isScheduledForDeletion()) {
                    continue;
                }
                if (currentEntry.getKind() == SVNNodeKind.FILE || currentEntry.getKind() == SVNNodeKind.DIR) {
                    removeFromRevisionControl(currentEntry.getName(), false, false);
                }
            }
        }

        long fileLength = 0;
        if (!getThisDirName().equals(fileName)) {
            File workingFile = getFile(fileName);  
            fileLength = workingFile.length();
        }

        long textTime = 0;
        if (!implicit && !getThisDirName().equals(fileName)) {
            File tmpFile = getBaseFile(fileName, true);
            SVNFileType fileType = SVNFileType.getType(tmpFile);
            if (fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK) {
                boolean modified = false;
                File workingFile = getFile(fileName);  
                long tmpTimestamp = tmpFile.lastModified();
                long wkTimestamp = workingFile.lastModified(); 
                if (tmpTimestamp != wkTimestamp) {
                    // check if wc file is not modified
                    File tmpFile2 = SVNFileUtil.createUniqueFile(tmpFile.getParentFile(), fileName, ".tmp", true);
                    try {
                        String tmpFile2Path = SVNFileUtil.getBasePath(tmpFile2);
                        SVNTranslator.translate(this, fileName, fileName, tmpFile2Path, false);
                        modified = !SVNFileUtil.compareFiles(tmpFile, tmpFile2, null);
                    } catch (SVNException svne) {
                        SVNErrorMessage err = SVNErrorMessage.create(errorCode, "Error comparing ''{0}'' and ''{1}''", new Object[] {workingFile, tmpFile});
                        SVNErrorManager.error(err, svne, SVNLogType.WC);
                    } finally {
                        tmpFile2.delete();
                    }
                }
            
                textTime = modified ? tmpTimestamp : wkTimestamp;
            }
        }
        if (!implicit && entry.isScheduledForReplacement()) {
            SVNFileUtil.deleteFile(getBasePropertiesFile(fileName, false));
        }

        boolean setReadWrite = false;
        boolean setNotExecutable = false;
        SVNVersionedProperties baseProps = getBaseProperties(fileName);
        SVNVersionedProperties wcProps = getProperties(fileName);

        //TODO: to work properly we must create a tmp working props file
        //instead of tmp base props one
        File tmpPropsFile = getPropertiesFile(fileName, true);
        File wcPropsFile = getPropertiesFile(fileName, false);
        File basePropertiesFile = getBasePropertiesFile(fileName, false);
        SVNFileType tmpPropsType = SVNFileType.getType(tmpPropsFile);
        // tmp may be missing when there were no prop change at all!
        if (tmpPropsType == SVNFileType.FILE) {
            if (!getThisDirName().equals(fileName)) {
                SVNVersionedProperties propDiff = baseProps.compareTo(wcProps);
                setReadWrite = propDiff != null && propDiff.containsProperty(SVNProperty.NEEDS_LOCK)
                        && propDiff.getPropertyValue(SVNProperty.NEEDS_LOCK) == null;
                setNotExecutable = propDiff != null
                        && propDiff.containsProperty(SVNProperty.EXECUTABLE)
                        && propDiff.getPropertyValue(SVNProperty.EXECUTABLE) == null;
            }
            try {
                if (!tmpPropsFile.exists() || tmpPropsFile.length() <= 4) {
                    SVNFileUtil.deleteFile(basePropertiesFile);
                } else {
                    SVNFileUtil.copyFile(tmpPropsFile, basePropertiesFile, true);
                    SVNFileUtil.setReadonly(basePropertiesFile, true);
                }
            } finally {
                SVNFileUtil.deleteFile(tmpPropsFile);
            }
        }
        
        if (!getThisDirName().equals(fileName) && !implicit) {
            File tmpFile = getBaseFile(fileName, true);
            File baseFile = getBaseFile(fileName, false);
            File wcFile = getFile(fileName);
            File tmpFile2 = null;
            try {
                tmpFile2 = SVNFileUtil.createUniqueFile(tmpFile.getParentFile(), fileName, ".tmp", false);
                boolean overwritten = false;
                SVNFileType fileType = SVNFileType.getType(tmpFile);
                boolean special = getProperties(fileName).getPropertyValue(SVNProperty.SPECIAL) != null;
                if (!SVNFileUtil.symlinksSupported() || !special) {
                    if (fileType == SVNFileType.FILE) {
                        SVNTranslator.translate(this, fileName, 
                                SVNFileUtil.getBasePath(tmpFile), SVNFileUtil.getBasePath(tmpFile2), true);
                    } else {
                        SVNTranslator.translate(this, fileName, fileName,
                                SVNFileUtil.getBasePath(tmpFile2), true, true);
                    }
                    if (!SVNFileUtil.compareFiles(tmpFile2, wcFile, null)) {
                        SVNFileUtil.copyFile(tmpFile2, wcFile, true);
                        overwritten = true;
                    }
                }
                boolean needsReadonly = getProperties(fileName).getPropertyValue(SVNProperty.NEEDS_LOCK) != null && entry.getLockToken() == null;
                boolean needsExecutable = getProperties(fileName).getPropertyValue(SVNProperty.EXECUTABLE) != null;
                if (needsReadonly) {
                    SVNFileUtil.setReadonly(wcFile, true);
                    overwritten = true;
                }
                if (needsExecutable) {
                    SVNFileUtil.setExecutable(wcFile, true);
                    overwritten = true;
                }
                if (fileType == SVNFileType.FILE) {
                    SVNFileUtil.rename(tmpFile, baseFile);
                }
                if (setReadWrite) {
                    SVNFileUtil.setReadonly(wcFile, false);
                    overwritten = true;
                }
                if (setNotExecutable) {
                    SVNFileUtil.setExecutable(wcFile, false);
                    overwritten = true;
                }
                if (overwritten) {
                    textTime = wcFile.lastModified();
                    fileLength = wcFile.length();
                }
            } catch (SVNException svne) {
                SVNErrorMessage err = SVNErrorMessage.create(errorCode, "Error replacing text-base of ''{0}''", fileName);
                SVNErrorManager.error(err, svne, SVNLogType.WC);
            } finally {
                tmpFile2.delete();
                tmpFile.delete();
            }
        }
        
        // update entry
        Map entryAttrs = new SVNHashMap();
        entryAttrs.put(SVNProperty.REVISION, SVNProperty.toString(revisionNumber));
        entryAttrs.put(SVNProperty.KIND, getThisDirName().equals(fileName) ? SVNProperty.KIND_DIR : SVNProperty.KIND_FILE);
        if (!implicit) {
            entryAttrs.put(SVNProperty.SCHEDULE, null);
        }
        entryAttrs.put(SVNProperty.COPIED, SVNProperty.toString(false));
        entryAttrs.put(SVNProperty.DELETED, SVNProperty.toString(false));
        if (textTime != 0 && !implicit) {
            entryAttrs.put(SVNProperty.TEXT_TIME, SVNDate.formatDate(new Date(textTime)));
        }
        entryAttrs.put(SVNProperty.CONFLICT_NEW, null);
        entryAttrs.put(SVNProperty.CONFLICT_OLD, null);
        entryAttrs.put(SVNProperty.CONFLICT_WRK, null);
        entryAttrs.put(SVNProperty.PROP_REJECT_FILE, null);
        entryAttrs.put(SVNProperty.COPYFROM_REVISION, null);
        entryAttrs.put(SVNProperty.COPYFROM_URL, null);
        entryAttrs.put(SVNProperty.HAS_PROP_MODS, SVNProperty.toString(false));
        entryAttrs.put(SVNProperty.WORKING_SIZE, Long.toString(fileLength));

        
        try {
            modifyEntry(fileName, entryAttrs, false, true);
        } catch (SVNException svne) {
            SVNErrorMessage err = SVNErrorMessage.create(errorCode, "Error modifying entry of ''{0}''", fileName);
            SVNErrorManager.error(err, svne, SVNLogType.WC);
        }
        SVNFileUtil.deleteFile(wcPropsFile);
        
        if (!getThisDirName().equals(fileName)) {
            return;
        }
        // update entry in parent.
        File dirFile = getRoot();
        if (getWCAccess().isWCRoot(getRoot())) {
            return;
        }
        
        boolean unassociated = false;
        SVNAdminArea parentArea = null;
        try {
            parentArea = getWCAccess().retrieve(dirFile.getParentFile());
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                parentArea = getWCAccess().open(dirFile.getParentFile(), true, false, 0);
                unassociated = true;
            } else {
                throw svne;
            }
        }
        
        SVNEntry entryInParent = parentArea.getEntry(dirFile.getName(), false);
        if (entryInParent != null) {
            entryAttrs.clear();

            if (!implicit) {
                entryAttrs.put(SVNProperty.SCHEDULE, null);
            }
            entryAttrs.put(SVNProperty.COPIED, SVNProperty.toString(false));
            entryAttrs.put(SVNProperty.COPYFROM_REVISION, null);
            entryAttrs.put(SVNProperty.COPYFROM_URL, null);
            entryAttrs.put(SVNProperty.DELETED, SVNProperty.toString(false));
            try {
                parentArea.modifyEntry(entryInParent.getName(), entryAttrs, true, true);
            } catch (SVNException svne) {
                SVNErrorMessage err = SVNErrorMessage.create(errorCode, "Error modifying entry of ''{0}''", fileName);
                SVNErrorManager.error(err, svne, SVNLogType.WC);
            }
        }
        parentArea.saveEntries(false);
        
        if (unassociated) {
            getWCAccess().closeAdminArea(dirFile.getParentFile());
        }
    }
    
    public boolean unlock() throws SVNException {
        if (!myLockFile.exists()) {
            return true;
        }
        // only if there are not locks or killme files.
        boolean killMe = getAdminFile(ADM_KILLME).exists();
        if (killMe) {
            return false;
        }
        File[] logs = SVNFileListUtil.listFiles(getAdminDirectory());
        for (int i = 0; logs != null && i < logs.length; i++) {
            File log = logs[i];
            if ("log".equals(log.getName()) || log.getName().startsWith("log.")) {
                if (log.isFile() && log.exists()) {
                    SVNDebugLog.getDefaultLog().logFiner(SVNLogType.WC, "unlock: log file: '" + log.getName() + "', listed, and exists.");
                    return false;
                }
                SVNDebugLog.getDefaultLog().logFiner(SVNLogType.WC, "unlock: log file: '" + log.getName() + "', listed, but does not exist.");
            }
        }
        boolean deleted = SVNFileUtil.deleteFile(myLockFile);
        if (!deleted) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Failed to unlock working copy ''{0}''", getRoot());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return deleted;
    }

    public boolean isVersioned() {
        if (getAdminDirectory().isDirectory() && myEntriesFile.canRead()) {
            try {
                if (getEntry("", false) != null) {
                    return true;
                }
            } catch (SVNException e) {
                //
            }
        }
        return false;
    }

    public boolean isLocked() throws SVNException {
        if (!myWasLocked) {
            return false;
        }
        SVNFileType type = SVNFileType.getType(myLockFile);
        if (type == SVNFileType.FILE) {
            return true;
        } else if (type == SVNFileType.NONE) {
            return false;
        } 
        
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Lock file ''{0}'' is not a regular file", myLockFile);
        SVNErrorManager.error(err, SVNLogType.WC);
        return false;
    }

    public boolean hasTreeConflict(String name) throws SVNException {
        return false;
    }

    public SVNTreeConflictDescription getTreeConflict(String name) throws SVNException {
        return null;
    }

    public void addTreeConflict(SVNTreeConflictDescription conflict) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                "This feature is not supported in version {0} of working copy format", String.valueOf(getFormatVersion()));
        SVNErrorManager.error(err, SVNLogType.WC);
    }

    public SVNTreeConflictDescription deleteTreeConflict(String name) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                "This feature is not supported in version {0} of working copy format", String.valueOf(getFormatVersion()));
        SVNErrorManager.error(err, SVNLogType.WC);
        return null;
    }

    public void setFileExternalLocation(String name, SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNURL reposRootURL) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                "This feature is not supported in version {0} of working copy format", String.valueOf(getFormatVersion()));
        SVNErrorManager.error(err, SVNLogType.WC);
    }

    public int getFormatVersion() {
        return WC_FORMAT;
    }

    protected SVNAdminArea createAdminAreaForDir(File dir) {
        return new SVNAdminArea14(dir);
    }
    
    protected boolean isEntryPropertyApplicable(String propName) {
        return propName != null && !INAPPLICABLE_PROPERTIES.contains(propName);
    }

}
