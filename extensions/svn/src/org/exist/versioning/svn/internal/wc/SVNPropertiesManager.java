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
package org.exist.versioning.svn.internal.wc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import org.exist.versioning.svn.internal.wc.admin.ISVNEntryHandler;
import org.exist.versioning.svn.internal.wc.admin.SVNAdminArea;
import org.exist.versioning.svn.internal.wc.admin.SVNEntry;
import org.exist.versioning.svn.internal.wc.admin.SVNLog;
import org.exist.versioning.svn.internal.wc.admin.SVNTranslator;
import org.exist.versioning.svn.internal.wc.admin.SVNTranslatorOutputStream;
import org.exist.versioning.svn.internal.wc.admin.SVNVersionedProperties;
import org.exist.versioning.svn.internal.wc.admin.SVNWCAccess;
import org.exist.versioning.svn.wc.ISVNOptions;
import org.exist.versioning.svn.wc.SVNEvent;
import org.exist.versioning.svn.wc.SVNEventAction;
import org.exist.versioning.svn.wc.SVNStatusType;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.wc.IOExceptionWrapper;
import org.tmatesoft.svn.core.internal.wc.ISVNFileContentFetcher;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class SVNPropertiesManager {

    private static final Collection NOT_ALLOWED_FOR_FILE = new SVNHashSet();
    private static final Collection NOT_ALLOWED_FOR_DIR = new SVNHashSet();

    static {
        NOT_ALLOWED_FOR_FILE.add(SVNProperty.IGNORE);
        NOT_ALLOWED_FOR_FILE.add(SVNProperty.EXTERNALS);

        NOT_ALLOWED_FOR_DIR.add(SVNProperty.EXECUTABLE);
        NOT_ALLOWED_FOR_DIR.add(SVNProperty.KEYWORDS);
        NOT_ALLOWED_FOR_DIR.add(SVNProperty.EOL_STYLE);
        NOT_ALLOWED_FOR_DIR.add(SVNProperty.CHARSET);
        NOT_ALLOWED_FOR_DIR.add(SVNProperty.NEEDS_LOCK);
        NOT_ALLOWED_FOR_DIR.add(SVNProperty.MIME_TYPE);
    }

    public static void validateRevisionProperties(SVNProperties revisionProperties) throws SVNException {
        if (hasSVNProperties(revisionProperties)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, 
                    "Standard properties can't be set explicitly as revision properties");
            SVNErrorManager.error(err, SVNLogType.NETWORK);
        }
    }
    
    public static boolean setWCProperty(SVNWCAccess access, File path, String propName, SVNPropertyValue propValue, boolean write) throws SVNException {
        SVNEntry entry = access.getVersionedEntry(path, false);
        SVNAdminArea dir = entry.getKind() == SVNNodeKind.DIR ? access.retrieve(path) : access.retrieve(path.getParentFile());
        SVNVersionedProperties wcProps = dir.getWCProperties(entry.getName());
        SVNPropertyValue oldValue = wcProps.getPropertyValue(propName);
        wcProps.setPropertyValue(propName, propValue);
        if (write) {
            dir.saveWCProperties(false);
        }
        return oldValue == null ? propValue != null : !oldValue.equals(propValue);
    }

    public static SVNPropertyValue getWCProperty(SVNWCAccess access, File path, String propName) throws SVNException {
        SVNEntry entry = access.getEntry(path, false);
        if (entry == null) {
            return null;
        }
        SVNAdminArea dir = entry.getKind() == SVNNodeKind.DIR ? access.retrieve(path) : access.retrieve(path.getParentFile());
        return dir.getWCProperties(entry.getName()).getPropertyValue(propName);
    }

    public static void deleteWCProperties(SVNAdminArea dir, String name, boolean recursive) throws SVNException {
        if (name != null) {
            SVNVersionedProperties props = dir.getWCProperties(name);
            if (props != null) {
                props.removeAll();
            }
        }
        if (recursive || name == null) {
            for (Iterator entries = dir.entries(false); entries.hasNext();) {
                SVNEntry entry = (SVNEntry) entries.next();
                SVNVersionedProperties props = dir.getWCProperties(entry.getName());
                if (props != null) {
                    props.removeAll();
                }
                if (entry.isFile() || dir.getThisDirName().equals(entry.getName())) {
                    continue;
                }
                if (recursive) {
                    SVNAdminArea childDir = dir.getWCAccess().retrieve(dir.getFile(entry.getName()));
                    deleteWCProperties(childDir, null, true);
                }
            }
        }
        dir.saveWCProperties(false);
    }

    public static SVNPropertyValue getProperty(SVNWCAccess access, File path, String propName) throws SVNException {
        SVNEntry entry = access.getEntry(path, false);
        if (entry == null) {
            return null;
        }
        String[] cachableProperties = entry.getCachableProperties();
        if (cachableProperties != null && contains(cachableProperties, propName)) {
            String[] presentProperties = entry.getPresentProperties();
            if (presentProperties == null || !contains(presentProperties, propName)) {
                return null;
            }
            if (SVNProperty.isBooleanProperty(propName)) {
                return SVNProperty.getValueOfBooleanProperty(propName);
            }
        }
        if (SVNProperty.isWorkingCopyProperty(propName)) {
            return getWCProperty(access, path, propName);
        } else if (SVNProperty.isEntryProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_PROP_KIND, "Property ''{0}'' is an entry property", propName);
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        SVNAdminArea dir = entry.getKind() == SVNNodeKind.DIR ? access.retrieve(path) : access.retrieve(path.getParentFile());
        return dir.getProperties(entry.getName()).getPropertyValue(propName);
    }

    public static boolean setProperty(final SVNWCAccess access, final File path, final String propName, SVNPropertyValue propValue,
                                      boolean skipChecks) throws SVNException {
        if (SVNProperty.isWorkingCopyProperty(propName)) {
            return setWCProperty(access, path, propName, propValue, true);
        } else if (SVNProperty.isEntryProperty(propName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_PROP_KIND, "Property ''{0}'' is an entry property", propName);
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        SVNEntry entry = access.getVersionedEntry(path, false);
        SVNAdminArea dir = entry.isDirectory() ? access.retrieve(path) : access.retrieve(path.getParentFile());
        boolean updateTimeStamp = SVNProperty.EOL_STYLE.equals(propName) || SVNProperty.CHARSET.equals(propName);


        if (propValue != null && SVNProperty.isSVNProperty(propName)) {
            propValue = validatePropertyValue(path.getAbsolutePath(), entry.getKind(), propName, propValue, skipChecks, access.getOptions(), new ISVNFileContentFetcher() {

                public void fetchFileContent(OutputStream os) throws SVNException {
                    InputStream is = SVNFileUtil.openFileForReading(path, SVNLogType.WC);
                    try {
                        SVNTranslator.copy(is, os);
                    } catch (IOExceptionWrapper ioew) {
                        throw ioew.getOriginalException();
                    } catch (IOException e) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e, SVNLogType.DEFAULT);
                    } finally {
                        SVNFileUtil.closeFile(is);
                    }
                }

                public boolean fileIsBinary() throws SVNException {
                    SVNPropertyValue mimeType = SVNPropertiesManager.getProperty(access, path, SVNProperty.MIME_TYPE);
                    return mimeType != null && SVNProperty.isBinaryMimeType(mimeType.getString());
                }
		public SVNPropertyValue getProperty(String propertyName) throws SVNException {
		    SVNVersionedProperties wcProps = dir.getWCProperties(entry.getName());
		    return wcProps.getPropertyValue(propertyName);
	        }
            });
        }

        if (entry.getKind() == SVNNodeKind.FILE && SVNProperty.EXECUTABLE.equals(propName)) {
            if (propValue == null) {
                SVNFileUtil.setExecutable(path, false);
            } else {
                propValue = SVNProperty.getValueOfBooleanProperty(propName);
                SVNFileUtil.setExecutable(path, true);
            }
        }
        if (entry.getKind() == SVNNodeKind.FILE && SVNProperty.NEEDS_LOCK.equals(propName)) {
            if (propValue == null) {
                SVNFileUtil.setReadonly(path, false);
            } else {
                propValue = SVNProperty.getValueOfBooleanProperty(propName);
            }
        }
        SVNVersionedProperties properties = dir.getProperties(entry.getName());
        SVNPropertyValue oldValue = properties.getPropertyValue(propName);
        SVNEventAction action;
        if (oldValue == null) {
            if (propValue == null) {
                action = SVNEventAction.PROPERTY_DELETE_NONEXISTENT;
            } else {
                action = SVNEventAction.PROPERTY_ADD;
            }
        } else {
            if (propValue == null) {
                action = SVNEventAction.PROPERTY_DELETE;
            } else {
                action = SVNEventAction.PROPERTY_MODIFY;
            }
           
        }
        if (!updateTimeStamp && (entry.getKind() == SVNNodeKind.FILE && SVNProperty.KEYWORDS.equals(propName))) {
            Collection oldKeywords = getKeywords(oldValue == null ? null : oldValue.getString());
            Collection newKeywords = getKeywords(propValue == null ? null : propValue.getString());
            updateTimeStamp = !oldKeywords.equals(newKeywords);
        }
        SVNLog log = dir.getLog();
        if (updateTimeStamp) {
            SVNProperties command = new SVNProperties();
            command.put(SVNLog.NAME_ATTR, entry.getName());
            command.put(SVNProperty.shortPropertyName(SVNProperty.TEXT_TIME), (String) null);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
        }
        properties.setPropertyValue(propName, propValue);
        dir.saveVersionedProperties(log, false);
        log.save();
        dir.runLogs();
        final boolean modified = oldValue == null ? propValue != null : !oldValue.equals(propValue);
        if (modified || action == SVNEventAction.PROPERTY_DELETE_NONEXISTENT) {
            dir.getWCAccess().handleEvent(new SVNEvent(path, entry.getKind(), null, -1, null, null, null, null, action, action, null, null, null));
        }
        return modified;
    }

    public static SVNStatusType mergeProperties(SVNWCAccess wcAccess, File path, SVNProperties baseProperties, SVNProperties diff, boolean baseMerge, boolean dryRun) throws SVNException {
        SVNEntry entry = wcAccess.getVersionedEntry(path, false);
        File parent = null;
        String name = null;
        if (entry.isDirectory()) {
            parent = path;
            name = "";
        } else if (entry.isFile()) {
            parent = path.getParentFile();
            name = entry.getName();
        }

        SVNLog log = null;
        SVNAdminArea dir = wcAccess.retrieve(parent);
        if (!dryRun) {
            log = dir.getLog();
        }
        SVNStatusType result = dir.mergeProperties(name, baseProperties, diff, null, null, baseMerge, dryRun, log);
        if (!dryRun) {
            log.save();
            dir.runLogs();
        }
        return result;
    }

    public static Map computeAutoProperties(ISVNOptions options, File file, Map properties) throws SVNException {
        properties = options.applyAutoProperties(file, properties);
        if (!properties.containsKey(SVNProperty.MIME_TYPE)) {
            String mimeType = SVNFileUtil.detectMimeType(file, options.getFileExtensionsToMimeTypes());
            if (mimeType != null) {
                properties.put(SVNProperty.MIME_TYPE, mimeType);
            }
        }
        if (SVNProperty.isBinaryMimeType((String) properties.get(SVNProperty.MIME_TYPE))) {
            properties.remove(SVNProperty.EOL_STYLE);
            properties.remove(SVNProperty.CHARSET);
        }
        if (!properties.containsKey(SVNProperty.EXECUTABLE)) {
            if (SVNFileUtil.isExecutable(file)) {
                properties.put(SVNProperty.EXECUTABLE, "");
            }
        }
        return properties;
    }

    public static Map getWorkingCopyPropertyValues(File path, SVNEntry entry, final String propName,
                                                   SVNDepth depth, final boolean base) throws SVNException {
        final Map pathsToPropValues = new SVNHashMap();

        ISVNEntryHandler handler = new ISVNEntryHandler() {
            public void handleEntry(File itemPath, SVNEntry itemEntry) throws SVNException {
                SVNAdminArea adminArea = itemEntry.getAdminArea();
                if (itemEntry.isDirectory() && !itemEntry.getName().equals(adminArea.getThisDirName())) {
                    return;
                }

                if ((itemEntry.isScheduledForAddition() && base) ||
                        (itemEntry.isScheduledForDeletion() && !base)) {
                    return;
                }

                SVNPropertyValue propValue = null;
                SVNWCAccess access = adminArea.getWCAccess();
                if (base) {
                    SVNEntry pathEntry = access.getEntry(itemPath, false);
                    if (pathEntry != null) {
                        SVNAdminArea pathArea = pathEntry.getAdminArea();
                        SVNVersionedProperties baseProps = pathArea.getBaseProperties(pathEntry.getName());
                        propValue = baseProps.getPropertyValue(propName);
                    }
                } else {
                    SVNEntry pathEntry = access.getEntry(itemPath, true);
                    if (pathEntry != null) {
                        SVNAdminArea pathArea = pathEntry.getAdminArea();
                        SVNVersionedProperties workingProps = pathArea.getProperties(pathEntry.getName());
                        propValue = workingProps.getPropertyValue(propName);
                    }
                }

                if (propValue != null) {
                    pathsToPropValues.put(itemPath, propValue);
                }
            }

            public void handleError(File path, SVNErrorMessage error) throws SVNException {
                while (error.hasChildErrorMessage()) {
                    error = error.getChildErrorMessage();
                }
                if (error.getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                    return;
                }
                SVNErrorManager.error(error, SVNLogType.WC);
            }
        };

        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }

        SVNAdminArea adminArea = entry.getAdminArea();
        if (entry.isDirectory() && depth.compareTo(SVNDepth.FILES) >= 0) {
            SVNWCAccess wcAccess = adminArea.getWCAccess();
            wcAccess.walkEntries(path, handler, false, depth);
        } else {
            handler.handleEntry(path, entry);
        }

        return pathsToPropValues;
    }

    public static void recordWCMergeInfo(File path, Map mergeInfo, SVNWCAccess wcAccess) throws SVNException {
        SVNPropertyValue value = null;
        if (mergeInfo != null) {
            value = SVNPropertyValue.create(SVNMergeInfoUtil.formatMergeInfoToString(mergeInfo, null));
        }
        setProperty(wcAccess, path, SVNProperty.MERGE_INFO, value, true);
    }

    public static Map parseMergeInfo(File path, SVNEntry entry, boolean base) throws SVNException {
        Map fileToProp = SVNPropertiesManager.getWorkingCopyPropertyValues(path, entry, SVNProperty.MERGE_INFO,
                SVNDepth.EMPTY, base);

        Map result = null;
        SVNPropertyValue propValue = (SVNPropertyValue) fileToProp.get(path);
        if (propValue != null && propValue.getString() != null) {
            result = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(propValue.getString()), result);
        }
        return result;
    }

    public static boolean isValidPropertyName(String name) throws SVNException {
        if (name == null || name.length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME,
                    "Property name is empty");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }

        if (!(Character.isLetter(name.charAt(0)) || name.charAt(0) == ':' || name.charAt(0) == '_')) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!(Character.isLetterOrDigit(name.charAt(i))
                    || name.charAt(i) == '-' || name.charAt(i) == '.'
                    || name.charAt(i) == ':' || name.charAt(i) == '_')) {
                return false;
            }
        }
        return true;
    }

    public static boolean propNeedsTranslation(String propertyName) {
        return SVNProperty.isSVNProperty(propertyName);
    }

    private static Collection getKeywords(String value) {
        Collection keywords = new SVNHashSet();
        if (value == null || "".equals(value.trim())) {
            return keywords;
        }
        for (StringTokenizer tokens = new StringTokenizer(value, " \t\n\r"); tokens.hasMoreTokens();) {
            keywords.add(tokens.nextToken().toLowerCase());
        }
        return keywords;
    }

    private static boolean contains(String[] values, String value) {
        for (int i = 0; value != null && i < values.length; i++) {
            if (values[i].equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static SVNPropertyValue validatePropertyValue(String path, SVNNodeKind kind, String name, SVNPropertyValue value, boolean force, ISVNOptions options, ISVNFileContentFetcher fileContentFetcher) throws SVNException {
        if (value == null) {
            return value;
        }

        validatePropertyName(path, name, kind);

        if (SVNProperty.isSVNProperty(name) && value.isString()) {
            String str = value.getString();
            str = str.replaceAll("\r\n", "\n");
            str = str.replace('\r', '\n');
            value = SVNPropertyValue.create(str);
        }

        if (!force && SVNProperty.EOL_STYLE.equals(name)) {
            value = SVNPropertyValue.create(value.getString().trim());
            if (SVNTranslator.getEOL(value.getString(), options) == null) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.IO_UNKNOWN_EOL, "Unrecognized line ending style for ''{0}''", path);
                SVNErrorManager.error(error, SVNLogType.DEFAULT);
            }
            validateEOLProperty(path, fileContentFetcher);
        } else if (!force && SVNProperty.CHARSET.equals(name)) {
            value = SVNPropertyValue.create(value.getString().trim());
            try {
                SVNTranslator.getCharset(value.getString(), path, options);
            } catch (SVNException e) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Charset ''{0}'' is not supported on this computer", value.getString());
                SVNErrorManager.error(error, SVNLogType.DEFAULT);
            }
            if (fileContentFetcher.fileIsBinary()) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "File ''{0}'' has binary mime type property", path);
                SVNErrorManager.error(error, SVNLogType.DEFAULT);
            }
        } else if (!force && SVNProperty.MIME_TYPE.equals(name)) {
            value = SVNPropertyValue.create(value.getString().trim());
            validateMimeType(value.getString());
        } else if (SVNProperty.IGNORE.equals(name) || SVNProperty.EXTERNALS.equals(name)) {
            if (!value.getString().endsWith("\n")) {
                value = SVNPropertyValue.create(value.getString().concat("\n"));
            }
            if (SVNProperty.EXTERNALS.equals(name)) {
                SVNExternal.parseExternals(path, value.getString());
            }
        } else if (SVNProperty.KEYWORDS.equals(name)) {
            value = SVNPropertyValue.create(value.getString().trim());
        } else
        if (SVNProperty.EXECUTABLE.equals(name) || SVNProperty.SPECIAL.equals(name) || SVNProperty.NEEDS_LOCK.equals(name)) {
            value = SVNPropertyValue.create("*");
        } else if (SVNProperty.MERGE_INFO.equals(name)) {
            SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(value.getString()), null);
        }
        return value;
    }

    private static boolean hasSVNProperties(SVNProperties props) {
        if (props == null) {
            return false;
        }
        for (Iterator names = props.nameSet().iterator(); names.hasNext();) {
            String propName = (String) names.next();
            if (SVNProperty.isSVNProperty(propName)) {
                return true;
            }
        }
        return false;
    }

    private static void validatePropertyName(String path, String name, SVNNodeKind kind) throws SVNException {
        SVNErrorMessage err = null;
        if (kind == SVNNodeKind.DIR) {
            if (NOT_ALLOWED_FOR_DIR.contains(name)) {
                err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot set ''{0}'' on a directory (''{1}'')", new Object[]{name, path});
            }
        } else if (kind == SVNNodeKind.FILE) {
            if (NOT_ALLOWED_FOR_FILE.contains(name)) {
                err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot set ''{0}'' on a file (''{1}'')", new Object[]{name, path});
            }
        } else {
            err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "''{0}'' is not a file or directory", path);
        }
        if (err != null) {
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
    }

    public static void validateMimeType(String value) throws SVNException {
        String type = value.indexOf(';') >= 0 ? value.substring(0, value.indexOf(';')) : value;
        SVNErrorMessage err = null;
        if (type.length() == 0) {
            err = SVNErrorMessage.create(SVNErrorCode.BAD_MIME_TYPE, "MIME type ''{0}'' has empty media type", value);
        } else if (type.indexOf('/') < 0) {
            err = SVNErrorMessage.create(SVNErrorCode.BAD_MIME_TYPE, "MIME type ''{0}'' does not contain ''/''", value);
        } else if (!Character.isLetterOrDigit(type.charAt(type.length() - 1))) {
            err = SVNErrorMessage.create(SVNErrorCode.BAD_MIME_TYPE, "MIME type ''{0}'' ends with non-alphanumeric character", value);
        }
        if (err != null) {
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
    }

    public static void validateEOLProperty(String path, ISVNFileContentFetcher fetcher) throws SVNException {
        SVNTranslatorOutputStream out = new SVNTranslatorOutputStream(SVNFileUtil.DUMMY_OUT, new byte[0], false, null, false);

        try {
            fetcher.fetchFileContent(out);
        } catch (SVNException e) {
            handleInconsistentEOL(e, path);
            throw e;
        } finally {
            try {
                out.close();
            } catch (IOExceptionWrapper wrapper) {
                handleInconsistentEOL(wrapper.getOriginalException(), path);
                throw wrapper.getOriginalException();
            } catch (IOException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), SVNLogType.DEFAULT);
            }
        }

        if (fetcher.fileIsBinary()) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "File ''{0}'' has binary mime type property", path);
            SVNErrorManager.error(error, SVNLogType.WC);
        }
    }

    private static void handleInconsistentEOL(SVNException svne, String path) throws SVNException {
        SVNErrorMessage errorMessage = svne.getErrorMessage();
        while (errorMessage != null && errorMessage.getErrorCode() != SVNErrorCode.IO_INCONSISTENT_EOL) {
            errorMessage = errorMessage.getChildErrorMessage();
        }
        if (errorMessage != null && errorMessage.getErrorCode() == SVNErrorCode.IO_INCONSISTENT_EOL) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "File ''{0}'' has inconsistent newlines", path);
            SVNErrorManager.error(error, SVNLogType.DEFAULT);
        }
        Throwable cause = svne.getCause();
        if (cause == null) {
            return;
        }
        if (cause instanceof SVNException) {
            handleInconsistentEOL((SVNException) cause, path);
        } else if (cause instanceof IOExceptionWrapper) {
            IOExceptionWrapper wrapper = (IOExceptionWrapper) cause;
            handleInconsistentEOL(wrapper.getOriginalException(), path);
        }
    }
}
