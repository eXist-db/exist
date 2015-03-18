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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import org.exist.EXistException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.admin.SVNTranslator;
import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.exist.versioning.svn.wc.ISVNOptions;
import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNUUIDGenerator;
import org.tmatesoft.svn.core.internal.util.jna.SVNJNAUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNInputFile;
import org.tmatesoft.svn.core.internal.wc.ISVNReturnValueCallback;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author TMate Software Ltd., Peter Skoog
 */
public class SVNFileUtil {

    private static final String ID_COMMAND;
    private static final String LN_COMMAND;
    public static final String LS_COMMAND;
    private static final String CHMOD_COMMAND;
    private static final String ATTRIB_COMMAND;
    private static final String ENV_COMMAND;

    public static final boolean is32Bit;
    public static final boolean is64Bit;

    public static final int STREAM_CHUNK_SIZE = 16384;

    public final static OutputStream DUMMY_OUT = new OutputStream() {

        public void write(int b) throws IOException {
        }
    };
    public final static InputStream DUMMY_IN = new InputStream() {

        public int read() throws IOException {
            return -1;
        }
    };

    private static boolean ourUseUnsafeCopyOnly = Boolean.TRUE.toString().equalsIgnoreCase(System.getProperty("exist.svnkit.no.safe.copy", "true"));
    private static boolean ourCopyOnSetWritable = Boolean.TRUE.toString().equalsIgnoreCase(System.getProperty("exist.svnkit.fast.setWritable", "true"));
    private static boolean ourUseNIOCopying = Boolean.TRUE.toString().equalsIgnoreCase(System.getProperty("exist.svnkit.nio.copy", "false"));

    private static String nativeEOLMarker;
    private static File ourAppDataPath;
    private static String ourAdminDirectoryName;
    private static File ourSystemAppDataPath;
    
    private static volatile boolean ourIsSleepForTimeStamp = true;
    
    public static final String BINARY_MIME_TYPE = "application/octet-stream";

    static {
        is32Bit = "32".equals(System.getProperty("sun.arch.data.model", "32"));
        is64Bit = "64".equals(System.getProperty("sun.arch.data.model", "64"));
        
        String prefix = "svnkit.program.";

        Properties props = new Properties();
        InputStream is = SVNFileUtil.class.getResourceAsStream("/svnkit.runtime.properties");
        if (is != null) {
            try {
                props.load(is);
            } catch (IOException e) {
            } finally {
                SVNFileUtil.closeFile(is);
            }
        }

        ID_COMMAND = props.getProperty(prefix + "id", "id");
        LN_COMMAND = props.getProperty(prefix + "ln", "ln");
        LS_COMMAND = props.getProperty(prefix + "ls", "ls");
        CHMOD_COMMAND = props.getProperty(prefix + "chmod", "chmod");
        ATTRIB_COMMAND = props.getProperty(prefix + "attrib", "attrib");
        ENV_COMMAND = props.getProperty(prefix + "env", "env");
    }

    public static synchronized boolean useUnsafeCopyOnly() {
        return ourUseUnsafeCopyOnly;
    }

    public static synchronized void setUseUnsafeCopyOnly(boolean useUnsafeCopyOnly) {
        ourUseUnsafeCopyOnly = useUnsafeCopyOnly;
    }

    public static synchronized boolean useCopyOnSetWritable() {
        return ourCopyOnSetWritable;
    }

    public static synchronized void setUseCopyOnSetWritable(boolean useCopyOnSetWritable) {
        ourCopyOnSetWritable = useCopyOnSetWritable;
    }

    public static synchronized boolean useNIOCopying() {
        return ourUseNIOCopying;
    }

    public static synchronized void setUseNIOCopying(boolean useNIOCopy) {
        ourUseNIOCopying = useNIOCopy;
    }

    public static String getIdCommand() {
        return ID_COMMAND;
    }

    public static String getLnCommand() {
        return LN_COMMAND;
    }

    public static String getLsCommand() {
        return LS_COMMAND;
    }

    public static String getChmodCommand() {
        return CHMOD_COMMAND;
    }

    public static String getAttribCommand() {
        return ATTRIB_COMMAND;
    }

    public static String getEnvCommand() {
        return ENV_COMMAND;
    }
    
    public static File getParentFile(File file) {
        String path = file.getAbsolutePath();
        path = path.replace(File.separatorChar, '/');
        path = SVNPathUtil.canonicalizePath(path);
        int up = 0;
        while (path.endsWith("/..")) {
            path = SVNPathUtil.removeTail(path);
            up++;
        } 
        for(int i = 0; i < up; i++) {
            path = SVNPathUtil.removeTail(path);
        }
        path = path.replace('/', Resource.separatorChar);
        file = new Resource(path);
        return file.getParentFile();
    }

    public static String readFile(File file) throws SVNException {
        InputStream is = null;
        try {
            is = openFileForReading(file, SVNLogType.WC);
            return readFile(is);
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot read from file ''{0}'': {1}", new Object[] { file, ioe.getMessage() });
            SVNErrorManager.error(err, Level.FINE, SVNLogType.WC);
        } finally { 
            closeFile(is);
        }
        return null;
    }
    
    public static String readFile(InputStream input) throws IOException {
        byte[] buf = new byte[STREAM_CHUNK_SIZE];
        StringBuffer result = new StringBuffer();
        int r = -1;
        while ((r = input.read(buf)) != -1) {
            if (r == 0) {
                continue;
            }
            result.append(new String(buf, 0, r, "UTF-8"));
        }
        return result.toString();
    }
    
    public static int readIntoBuffer(InputStream is, byte[] buff, int off, int len) throws IOException {
        int read = 0;
        while (len > 0) {
            int r = is.read(buff, off + read, len);
            if (r < 0) {
                if (read == 0) {
                    read = -1;
                }
                break;
            }
            
            read += r;
            len -= r;
        }
        return read;
    }
    
    public static String getBasePath(File file) {
        File base = file.getParentFile();
        while (base != null) {
            if (base.isDirectory()) {
                File adminDir = new Resource(base, getAdminDirectoryName());
                if (adminDir.exists() && adminDir.isDirectory()) {
                    break;
                }
            }
            base = base.getParentFile();
        }
        String path = file.getAbsolutePath();
        if (base != null) {
            path = path.substring(base.getAbsolutePath().length());
        }
        path = path.replace(File.separatorChar, '/');
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    public static void createEmptyFile(File file) throws SVNException {
        boolean created;
        if (file != null && file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        
        IOException ioError = null;
        try {
            created = createNewFile(file);
        } catch (IOException ioe) {
            created = false;
            ioError = ioe;
        }
        if (!created) {
            SVNErrorMessage err = null;
            if (ioError != null) {
                err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot create new file ''{0}'': {1}", 
                        new Object[] { file, ioError.getMessage() });
            } else {
                err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot create new file ''{0}''", file);
            }
            SVNErrorManager.error(err, ioError != null ? ioError : new Exception(), Level.FINE, SVNLogType.WC);
        }
    }

    public static boolean createNewFile(File file) throws IOException {
        if (file == null) {
            return false;
        }
        
        return file.createNewFile();
    }

    /**
     * An internal method for ASCII bytes to write only!
     * 
     * @param file
     * @param contents
     * @throws SVNException
     */
    public static void createFile(File file, String contents, String charSet) throws SVNException {
        createEmptyFile(file);
        if (contents == null || contents.length() == 0) {
            return;
        }
        
        OutputStream os = null;
        try {
            os = SVNFileUtil.openFileForWriting(file); 
            if (charSet != null) {
                os.write(contents.getBytes(charSet));
            } else {
                os.write(contents.getBytes());
            }
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot write to file ''{0}'': {1}", new Object[] {file, ioe.getMessage()});
            SVNErrorManager.error(err, ioe, Level.FINE, SVNLogType.DEFAULT);
        } catch (SVNException svne) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot write to file ''{0}''", file);
            SVNErrorManager.error(err, svne, Level.FINE, SVNLogType.DEFAULT);
        } finally {
            SVNFileUtil.closeFile(os);
        }
    }

    public static void writeToFile(File file, String contents, String charSet) throws SVNException {
        if (contents == null || contents.length() == 0) {
            return;
        }
        
        OutputStream os = null;
        try {
            os = SVNFileUtil.openFileForWriting(file); 
            if (charSet != null) {
                os.write(contents.getBytes(charSet));
            } else {
                os.write(contents.getBytes());
            }
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot write to file ''{0}'': {1}", new Object[] {file, ioe.getMessage()});
            SVNErrorManager.error(err, ioe, Level.FINE, SVNLogType.DEFAULT);
        } catch (SVNException svne) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot write to file ''{0}''", file);
            SVNErrorManager.error(err, svne, Level.FINE, SVNLogType.DEFAULT);
        } finally {
            SVNFileUtil.closeFile(os);
        }
    }

    public static void writeVersionFile(File file, int version) throws SVNException {
        if (version < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
                    "Version {0} is not non-negative", new Integer(version));
            SVNErrorManager.error(err, Level.FINE, SVNLogType.WC);
        }
        
        String contents = version + "\n";
        File tmpFile = SVNFileUtil.createUniqueFile(file.getParentFile(), file.getName(), ".tmp", false);
        OutputStream os = null;

        try {
            os = SVNFileUtil.openFileForWriting(tmpFile);
            os.write(contents.getBytes("US-ASCII"));
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
            SVNErrorManager.error(err, e, Level.FINE, SVNLogType.DEFAULT);
        } finally {
            SVNFileUtil.closeFile(os);
        }
        SVNFileUtil.rename(tmpFile, file);
        setReadonly(file, true);
    }

    public static synchronized File createUniqueFile(File parent, String name, String suffix, boolean useUUIDGenerator) throws SVNException {
        StringBuffer fileName = new StringBuffer();
        fileName.append(name);
        if (useUUIDGenerator) {
            fileName.append(".");
            fileName.append(SVNUUIDGenerator.generateUUIDString());
        }
        fileName.append(suffix);
        File file = new Resource(parent, fileName.toString());
        int i = 1;
        do {
            if (SVNFileType.getType(file) == SVNFileType.NONE) {
                createEmptyFile(file);
                return file;
            }
            fileName.setLength(0);
            fileName.append(name);
            fileName.append(".");            
            if (useUUIDGenerator) {
                fileName.append(SVNUUIDGenerator.generateUUIDString());
            } else {
                fileName.append(i);
            }
            fileName.append(suffix);
            file = new Resource(parent, fileName.toString());
            i++;
        } while (i < 99999);

        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_UNIQUE_NAMES_EXHAUSTED, 
                "Unable to make name for ''{0}''", new File(parent, name));
        SVNErrorManager.error(err, Level.FINE, SVNLogType.WC);
        return null;
    }

    public static void rename(File src, File dst) throws SVNException {
        if (SVNFileType.getType(src) == SVNFileType.NONE) {
            deleteFile(dst);
            return;
        }
        if (dst.isDirectory()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot rename file ''{0}'' to ''{1}''; file ''{1}'' is a directory", 
                    new Object[] { src, dst });
            SVNErrorManager.error(err, Level.FINE, SVNLogType.WC);
        }
        boolean renamed = src.renameTo(dst);

        if (!renamed) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot rename file ''{0}'' to ''{1}''", new Object[] {src, dst});
            SVNErrorManager.error(err, Level.FINE, SVNLogType.WC);
        }
    }

    public static boolean setReadonly(File file, boolean readonly) {
        if (!file.exists()) {
            return false;
        }
        if (readonly)
            return file.setReadOnly();
        
        return true;
    }

    public static void setExecutable(File file, boolean executable) {
        if (file == null || !file.exists() || SVNFileType.getType(file) == SVNFileType.SYMLINK) {
            return;
        }
        if (SVNJNAUtil.setExecutable(file, executable)) {
            return;
        }
    }

    public static boolean symlinksSupported() {
        return SVNFileType.isSymlinkSupportEnabled();
    }

    public static void setSGID(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        
        if (SVNJNAUtil.setSGID(dir)) {
            return;
        }
    }

    public static File resolveSymlinkToFile(File file) {
        if (!symlinksSupported()) {
            return null;
        }
        File targetFile = resolveSymlink(file);
        if (targetFile == null || !targetFile.isFile()) {
            return null;
        }
        return targetFile;
    }

    public static File resolveSymlink(File file) {
        if (!symlinksSupported()) {
            return null;
        }
        File targetFile = file;
        while (SVNFileType.getType(targetFile) == SVNFileType.SYMLINK) {
            String symlinkName = getSymlinkName(targetFile);
            if (symlinkName == null) {
                return null;
            }
            if (symlinkName.startsWith("/")) {
                targetFile = new File(symlinkName);
            } else {
                targetFile = new File(targetFile.getParentFile(), symlinkName);
            }
        }
        return targetFile;
    }
    
    public static void copy(File src, File dst, boolean safe, boolean copyAdminDirectories) throws SVNException {
        SVNFileType srcType = SVNFileType.getType(src);
        if (srcType == SVNFileType.FILE) {
            copyFile(src, dst, safe);
        } else if (srcType == SVNFileType.DIRECTORY) {
            copyDirectory(src, dst, copyAdminDirectories, null);
        } else if (srcType == SVNFileType.SYMLINK) {
            String name = SVNFileUtil.getSymlinkName(src);
            if (name != null) {
                SVNFileUtil.createSymlink(dst, name);
            }
        }
    }

    public static void copyFile(File src, File dst, boolean safe) throws SVNException {
        if (src == null || dst == null) {
            return;
        }
        if (src.equals(dst)) {
            return;
        }
        if (!src.exists()) {
            dst.delete();
            return;
        }
        File tmpDst = dst;
        if (SVNFileType.getType(dst) != SVNFileType.NONE) {
            if (safe && !useUnsafeCopyOnly()) {
                tmpDst = createUniqueFile(dst.getParentFile(), ".copy", ".tmp", true);
            } else {
                dst.delete();
            }
        }
        boolean executable = isExecutable(src);
        dst.getParentFile().mkdirs();

        SVNErrorMessage error = null;
        
        InputStream sis = null;
        OutputStream dos = null;
        try {
            sis = SVNFileUtil.openFileForReading(src, SVNLogType.WC);
            dos = SVNFileUtil.openFileForWriting(tmpDst);
            SVNTranslator.copy(sis, dos);
        } catch (IOException e) { 
            error = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot copy file ''{0}'' to ''{1}'': {2}", new Object[] { src, dst, 
                    e.getLocalizedMessage() });
        } finally {
            SVNFileUtil.closeFile(dos);
            SVNFileUtil.closeFile(sis);
        }

        if (error != null) {
            SVNErrorManager.error(error, Level.FINE, SVNLogType.WC);
        }
        if (safe && tmpDst != dst) {
            rename(tmpDst, dst);
        }
        if (executable) {
            setExecutable(dst, true);
        }
        dst.setLastModified(src.lastModified());
    }

    public static boolean createSymlink(File link, File linkName) throws SVNException {
        if (!symlinksSupported()) {
            return false;
        }
        if (SVNFileType.getType(link) != SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot create symbolic link ''{0}''; file already exists", link);
            SVNErrorManager.error(err, Level.FINE, SVNLogType.WC);
        }
        String fileContents = "";
        try {
            fileContents = readSingleLine(linkName);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
            SVNErrorManager.error(err, e, Level.FINE, SVNLogType.DEFAULT);
        }
        if (fileContents.startsWith("link ")) {
            fileContents = fileContents.substring("link".length()).trim();
            return createSymlink(link, fileContents);
        }
        //create file using internal representation
        createFile(link, fileContents, "UTF-8");
        return true;
    }

    public static boolean createSymlink(File link, String linkName) {
        if (!symlinksSupported()) {
            return false;
        }
        if (SVNJNAUtil.createSymlink(link, linkName)) {
            return true;
        }
        try {
            execCommand(new String[] {
                    LN_COMMAND, "-s", linkName, link.getAbsolutePath()
            });
        } catch (Throwable th) {
            SVNDebugLog.getDefaultLog().logFinest(SVNLogType.DEFAULT, th);
        }
        return SVNFileType.getType(link) == SVNFileType.SYMLINK;
    }

    public static boolean detranslateSymlink(File src, File linkFile) throws SVNException {
        if (!symlinksSupported()) {
            return false;
        }
        if (SVNFileType.getType(src) != SVNFileType.SYMLINK) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot detranslate symbolic link ''{0}''; file does not exist or not a symbolic link", src);
            SVNErrorManager.error(err, Level.FINE, SVNLogType.WC);
        }
        String linkPath = getSymlinkName(src);
        if (linkPath == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot detranslate symbolic link ''{0}''; file does not exist or not a symbolic link", src);
            SVNErrorManager.error(err, Level.FINE, SVNLogType.WC);
        }
        OutputStream os = openFileForWriting(linkFile);
        try {
            os.write("link ".getBytes("UTF-8"));
            os.write(linkPath.getBytes("UTF-8"));
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
            SVNErrorManager.error(err, e, Level.FINE, SVNLogType.DEFAULT);
        } finally {
            SVNFileUtil.closeFile(os);
        }
        return true;
    }

    public static String getSymlinkName(File link) {
        if (!symlinksSupported() || link == null) {
            return null;
        }
        String ls = null;
        ls = SVNJNAUtil.getLinkTarget(link);
        if (ls != null) {
            return ls;
        }
        try {
            ls = execCommand(new String[] {
                    LS_COMMAND, "-ld", link.getAbsolutePath()
            });
        } catch (Throwable th) {
            SVNDebugLog.getDefaultLog().logFinest(SVNLogType.DEFAULT, th);
        }
        if (ls == null || ls.lastIndexOf(" -> ") < 0) {
            return null;
        }
        int index = ls.lastIndexOf(" -> ") + " -> ".length();
        if (index <= ls.length()) {
            return ls.substring(index);
        }
        return null;
    }

    public static String computeChecksum(String line) {
        if (line == null) {
            return null;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        if (digest == null) {
            return null;
        }
        digest.update(line.getBytes());
        return toHexDigest(digest);

    }

    public static String computeChecksum(File file) throws SVNException {
        if (file == null || file.isDirectory() || !file.exists()) {
            return null;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "MD5 implementation not found: {0}", e.getMessage());
            SVNErrorManager.error(err, e, Level.FINE, SVNLogType.DEFAULT);
            return null;
        }
        InputStream is = openFileForReading(file, SVNLogType.WC);
        byte[] buffer = new byte[1024 * 16];
        try {
            while (true) {
                int l = is.read(buffer);
                if (l < 0) {
                    break;
                } else if (l == 0) {
                    continue;
                }
                digest.update(buffer, 0, l);
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
            SVNErrorManager.error(err, e, Level.FINE, SVNLogType.DEFAULT);
        } finally {
            closeFile(is);
        }
        return toHexDigest(digest);
    }

    public static boolean compareFiles(File f1, File f2, MessageDigest digest) throws SVNException {
        if (f1 == null || f2 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
                    "NULL paths are supported in compareFiles method");
            SVNErrorManager.error(err, Level.FINE, SVNLogType.WC);
            return false;
        }
        if (f1.equals(f2)) {
            return true;
        }
        boolean equals = true;
        if (f1.length() != f2.length()) {
            if (digest == null) {
                return false;
            }
            equals = false;
        }
        InputStream is1 = openFileForReading(f1, SVNLogType.WC);
        InputStream is2 = openFileForReading(f2, SVNLogType.WC);
        try {
            while (true) {
                int b1 = is1.read();
                int b2 = is2.read();
                if (b1 != b2) {
                    if (digest == null) {
                        return false;
                    }
                    equals = false;
                }
                if (b1 < 0) {
                    break;
                }
                if (digest != null) {
                    digest.update((byte) (b1 & 0xFF));
                }
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
            SVNErrorManager.error(err, e, Level.FINE, SVNLogType.DEFAULT);
        } finally {
            closeFile(is1);
            closeFile(is2);
        }
        return equals;
    }

    public static void truncate(File file, long truncateToSize) throws IOException {
        RandomAccessFile raf = null;
        try {
            raf = openRAFileForWriting(file, false);
            raf.setLength(truncateToSize);
        } catch (SVNException e) {
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        } finally {
            closeFile(raf);
        }
    }

    public static void setHidden(File file, boolean hidden) {
        if (file == null || !file.exists() || file.isHidden()) {
            return;
        }
    }

    public static void deleteAll(File dir, ISVNEventHandler cancelBaton) throws SVNException {
        deleteAll(dir, true, cancelBaton);
    }

    public static void deleteAll(File dir, boolean deleteDirs) {
        try {
            deleteAll(dir, deleteDirs, null);
        } catch (SVNException e) {
            // should never happen as cancell handler is null.
        }
    }

    public static void deleteAll(File dir, boolean deleteDirs, ISVNCanceller cancelBaton) throws SVNException {
        if (dir == null) {
            return;
        }
        SVNFileType fileType = SVNFileType.getType(dir);
        File[] children = fileType == SVNFileType.DIRECTORY ? SVNFileListUtil.listFiles(dir) : null;
        if (children != null) {
            if (cancelBaton != null) {
                cancelBaton.checkCancelled();
            }
            for (int i = 0; i < children.length; i++) {
                File child = children[i];
                deleteAll(child, deleteDirs, cancelBaton);
            }
            if (cancelBaton != null) {
                cancelBaton.checkCancelled();
            }
        }
        if (fileType == SVNFileType.DIRECTORY && !deleteDirs) {
            return;
        }
        deleteFile(dir);
    }

    public static boolean deleteFile(File file) throws SVNException {
        if (file == null) {
            return true;
        }
       return file.delete();
    }

    public static String toHexDigest(MessageDigest digest) {
        if (digest == null) {
            return null;
        }
        byte[] result = digest.digest();
        StringBuffer hexDigest = new StringBuffer();
        for (int i = 0; i < result.length; i++) {
            SVNFormatUtil.appendHexNumber(hexDigest, result[i]);
        }
        return hexDigest.toString();
    }

    public static String toHexDigest(byte[] digest) {
        if (digest == null) {
            return null;
        }

        StringBuffer hexDigest = new StringBuffer();
        for (int i = 0; i < digest.length; i++) {
            SVNFormatUtil.appendHexNumber(hexDigest, digest[i]);
        }
        return hexDigest.toString();
    }
    
    public static byte[] fromHexDigest(String hexDigest) {
        if (hexDigest == null || hexDigest.length() == 0) {
            return null;
        }

        hexDigest = hexDigest.toLowerCase();

        int digestLength = hexDigest.length() / 2;

        if (digestLength == 0 || 2 * digestLength != hexDigest.length()) {
            return null;
        }

        byte[] digest = new byte[digestLength];
        for (int i = 0; i < hexDigest.length() / 2; i++) {
            if (!isHex(hexDigest.charAt(2 * i)) || !isHex(hexDigest.charAt(2 * i + 1))) {
                return null;
            }

            int hi = Character.digit(hexDigest.charAt(2 * i), 16) << 4;

            int lo = Character.digit(hexDigest.charAt(2 * i + 1), 16);
            Integer ib = new Integer(hi | lo);
            byte b = ib.byteValue();

            digest[i] = b;
        }

        return digest;
    }

    public static String getNativeEOLMarker(ISVNOptions options) {
        if (nativeEOLMarker == null) {
            nativeEOLMarker = new String(options.getNativeEOL());
        }
        return nativeEOLMarker;
    }

    public static long roundTimeStamp(long tstamp) {
        return (tstamp / 1000) * 1000;
    }

    public static void sleepForTimestamp() {
        if (!ourIsSleepForTimeStamp) {
            return;
        }
        long time = System.currentTimeMillis();
        time = 1100 - (time - (time / 1000) * 1000);
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            //
        }
    }
    
    public static void setSleepForTimestamp(boolean sleep) {
        ourIsSleepForTimeStamp = sleep;
    }

    public static String readLineFromStream(InputStream is, StringBuffer buffer, CharsetDecoder decoder) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int r = -1;
        while ((r = is.read()) != '\n') {
            if (r == -1) {
                String out = decode(decoder, byteBuffer.toByteArray());
                buffer.append(out);
                return null;
            }
            byteBuffer.write(r);
            
        }
        String out = decode(decoder, byteBuffer.toByteArray());
        buffer.append(out);
        return out;
    }

    public static String detectMimeType(InputStream is) throws IOException {
        byte[] buffer = new byte[1024];
        
        int read = readIntoBuffer(is, buffer, 0, buffer.length);
        
        int binaryCount = 0;
        for (int i = 0; i < read; i++) {
            byte b = buffer[i];
            if (b == 0) {
                return BINARY_MIME_TYPE;
            }
            if (b < 0x07 || (b > 0x0d && b < 0x20) || b > 0x7F) {
                binaryCount++;
            }
        }
        if (read > 0 && binaryCount * 1000 / read > 850) {
            return BINARY_MIME_TYPE;
        }
        return null;
    }

    public static String detectMimeType(File file, Map mimeTypes) throws SVNException {
        if (file == null || !file.exists()) {
            return null;
        }
        
        SVNFileType kind = SVNFileType.getType(file);
        if (kind != SVNFileType.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_FILENAME,
                    "Can''t detect MIME type of non-file ''{0}''", file);
            SVNErrorManager.error(err, Level.FINE, SVNLogType.WC);
        }
        
        if (mimeTypes != null) {
            String name = file.getName();
            String pathExt = "";
            int dotInd = name.lastIndexOf('.'); 
            if (dotInd != -1 && dotInd != 0 && dotInd != name.length() - 1) {
                pathExt = name.substring(dotInd + 1);
            }
            
            String mimeType = (String) mimeTypes.get(pathExt);
            if (mimeType != null) {
                return mimeType;
            }
        }
        
        InputStream is = null;
        try {
            is = openFileForReading(file, SVNLogType.WC);
            return detectMimeType(is);
        } catch (IOException e) {
            return null;
        } catch (SVNException e) {
            return null;
        } finally {
            closeFile(is);
        }
    }

    public static boolean isExecutable(File file) throws SVNException {
        Boolean executable = SVNJNAUtil.isExecutable(file);
        if (executable != null) {
            return executable.booleanValue();
        }
        return false;
    }

    public static File ensureDirectoryExists(File path) throws SVNException {
        SVNFileType type = SVNFileType.getType(path);
        SVNNodeKind kind = SVNFileType.getNodeKind(type);
        if (kind != SVNNodeKind.NONE && kind != SVNNodeKind.DIR) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "''{0}'' is not a directory", path);
            SVNErrorManager.error(error, SVNLogType.WC);
        } else if (kind == SVNNodeKind.NONE) {
            boolean created = path.mkdirs();
            if (!created) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Unable to make directories", path);
                SVNErrorManager.error(error, SVNLogType.WC);
            }
        }
        return path;
    }

    public static void copyDirectory(File srcDir, File dstDir, boolean copyAdminDir, ISVNEventHandler cancel) throws SVNException {
        if (!dstDir.exists()) {
            dstDir.mkdirs();
            dstDir.setLastModified(srcDir.lastModified());
        }
        File[] files = SVNFileListUtil.listFiles(srcDir);
        for (int i = 0; files != null && i < files.length; i++) {
            File file = files[i];
            if (file.getName().equals("..") || file.getName().equals(".") || file.equals(dstDir)) {
                continue;
            }
            if (cancel != null) {
                cancel.checkCancelled();
            }
            if (!copyAdminDir && file.getName().equals(getAdminDirectoryName())) {
                continue;
            }
            SVNFileType fileType = SVNFileType.getType(file);
            File dst = new File(dstDir, file.getName());

            if (fileType == SVNFileType.FILE) {
                boolean executable = isExecutable(file);
                copyFile(file, dst, false);
                if (executable) {
                    setExecutable(dst, executable);
                }
            } else if (fileType == SVNFileType.DIRECTORY) {
                copyDirectory(file, dst, copyAdminDir, cancel);
                if (file.isHidden() || getAdminDirectoryName().equals(file.getName())) {
                    setHidden(dst, true);
                }
            } else if (fileType == SVNFileType.SYMLINK) {
                String name = getSymlinkName(file);
                if (name != null) {
                    createSymlink(dst, name);
                }
            }
        }
    }

    public static OutputStream openFileForWriting(File file) throws SVNException {
        return openFileForWriting(file, false);
    }

    public static OutputStream openFileForWriting(File file, boolean append) throws SVNException {
        if (!prepareFileForWriting(file)) {
            return null;
        }
        try {
            return new BufferedOutputStream(createFileOutputStream(file, append));
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot write to ''{0}'': {1}", new Object[] { file, e.getMessage() });
            SVNErrorManager.error(err, e, Level.FINE, SVNLogType.DEFAULT);
        }
        return null;
    }

    public static Writer openFileForWriting(Resource file) throws SVNException {
        if (!prepareFileForWriting(file)) {
            return null;
        }
        try {
            return file.getWriter();
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot write to ''{0}'': {1}", new Object[] { file, e.getMessage() });
            SVNErrorManager.error(err, e, Level.FINE, SVNLogType.DEFAULT);
        }
        return null;
    }

    public static boolean prepareFileForWriting(File file) throws SVNException {
        if (file == null) {
            return false;
        }
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        if (file.isFile() && !file.canWrite()) {
            // force writable.
            setReadonly(file, false);
        }
        return true;
    }
    
    public static OutputStream createFileOutputStream(File file, boolean append) throws IOException {
    	if (file instanceof Resource)
            return ((Resource)file).getOutputStream(append);

    		
		System.err.println(" !!! createFileOutputStream on File = "+file);
		Exception e = new Exception();
		e.printStackTrace();
		
		return new FileOutputStream(file);
    }

    public static RandomAccessFile openRAFileForWriting(File file, boolean append) throws SVNException {
        if (file == null) {
            return null;
        }
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        RandomAccessFile raFile = null;
        try {
            raFile = new RandomAccessFile(file, "rw");
            if (append) {
                raFile.seek(raFile.length());
            }
        } catch (FileNotFoundException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Can not write to file ''{0}'': {1}", new Object[] { file, e.getMessage() });
            SVNErrorManager.error(err, e, Level.FINE, SVNLogType.DEFAULT);
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Can not set position pointer in file ''{0}'': {1}", 
                    new Object[] { file, ioe.getMessage() });
            SVNErrorManager.error(err, ioe, Level.FINE, SVNLogType.DEFAULT);
        }
        return raFile;
    }

    public static InputStream openFileForReading(File file) throws SVNException {
        return openFileForReading(file, Level.FINE, SVNLogType.DEFAULT);
    }

    public static InputStream openFileForReading(File file, SVNLogType logType) throws SVNException {
        return openFileForReading(file, Level.FINE, logType);
    }
    
    public static InputStream openFileForReading(File file, Level logLevel, SVNLogType logType) throws SVNException {
        if (file == null) {
            return null;
        }
        try {
            return new BufferedInputStream(createFileInputStream(file));
        } catch (FileNotFoundException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot read from ''{0}'': {1}", new Object[] { file, nfe.getMessage() });
            SVNErrorManager.error(err, logLevel, logType);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot read from ''{0}'': {1}", new Object[] { file, e.getMessage() });
            SVNErrorManager.error(err, e, logLevel, logType);
        }
        return null;
    }
    
    public static InputStream createFileInputStream(File file) throws IOException {
        return ((Resource)file).getInputStream();
    }

    public static RandomAccessFile openRAFileForReading(File file) throws SVNException {
        if (file == null) {
            return null;
        }
        if (!file.isFile() || !file.canRead()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot read from ''{0}'': path refers to a directory or read access is denied", file);
            SVNErrorManager.error(err, Level.FINE, SVNLogType.WC);
        }
        if (!file.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "File ''{0}'' does not exist", file);
            SVNErrorManager.error(err, Level.FINE, SVNLogType.WC);
        }
        try {
            return new RandomAccessFile(file, "r");
        } catch (FileNotFoundException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read from ''{0}'': {1}", 
                    new Object[] { file, e.getMessage() });
            SVNErrorManager.error(err, Level.FINE, SVNLogType.WC);
        }
        return null;
    }

    public static void closeFile(InputStream is) {
        if (is == null) {
            return;
        }
        try {
            is.close();
        } catch (IOException e) {
            //
        }
    }

    public static void closeFile(ISVNInputFile inFile) {
        if (inFile == null) {
            return;
        }
        try {
            inFile.close();
        } catch (IOException e) {
            //
        }
    }

    public static void closeFile(OutputStream os) {
        if (os == null) {
            return;
        }
        try {
            os.close();
        } catch (IOException e) {
            //
        }
    }

    public static void closeFile(RandomAccessFile raf) {
        if (raf == null) {
            return;
        }
        try {
            raf.close();
        } catch (IOException e) {
            //
        }
    }

    public static String execCommand(String[] commandLine) throws SVNException {
        return execCommand(commandLine, false, null);
    }

    public static String execCommand(String[] commandLine, boolean waitAfterRead, 
            ISVNReturnValueCallback callback) throws SVNException {
        return execCommand(commandLine, null, waitAfterRead, callback);
    }

    public static String execCommand(String[] commandLine, String[] env, boolean waitAfterRead, 
            ISVNReturnValueCallback callback) throws SVNException {
        InputStream is = null;
        boolean handleOutput = callback != null && callback.isHandleProgramOutput(); 
        StringBuffer result =  handleOutput ? null : new StringBuffer();
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(commandLine, env);
            is = process.getInputStream();
            if (!waitAfterRead) {
                int rc = process.waitFor();
                if (callback != null) {
                    callback.handleReturnValue(rc);
                }
                if (rc != 0) {
                    return null;
                }
            }
            int r;
            while ((r = is.read()) >= 0) {
                char ch = (char) (r & 0xFF);
                if (handleOutput) {
                    callback.handleChar(ch);
                } else {
                    result.append(ch);
                }
            }
            if (waitAfterRead) {
                int rc = process.waitFor();
                if (callback != null) {
                    callback.handleReturnValue(rc);
                }
                if (rc != 0) {
                    return null;
                }
            }
            return handleOutput ? null : result.toString().trim();
        } catch (IOException e) {
            SVNDebugLog.getDefaultLog().logFinest(SVNLogType.DEFAULT, e);
        } catch (InterruptedException e) {
            SVNDebugLog.getDefaultLog().logFinest(SVNLogType.DEFAULT, e);
        } finally {
            closeFile(is);
            if (process != null) {
                process.destroy();
            }
        }
        return null;
    }

    public static void closeFile(Writer os) {
        if (os != null) {
            try {
                os.close();
            } catch (IOException e) {
                //
            }
        }
    }

    public static void closeFile(Reader is) {
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
                //
            }
        }
    }

    public static String getAdminDirectoryName() {
        if (ourAdminDirectoryName == null) {
            String defaultAdminDir = ".svn";
            if (getEnvironmentVariable("SVN_ASP_DOT_NET_HACK") != null) {
                defaultAdminDir = "_svn";
            }
            ourAdminDirectoryName = System.getProperty("svnkit.admindir", System.getProperty("javasvn.admindir", defaultAdminDir));
            if (ourAdminDirectoryName == null || "".equals(ourAdminDirectoryName.trim())) {
                ourAdminDirectoryName = defaultAdminDir;
            }
        }
        return ourAdminDirectoryName;
    }

    public static void setAdminDirectoryName(String name) {
        ourAdminDirectoryName = name;
    }

    public static File getApplicationDataPath() {
        if (ourAppDataPath != null) {
            return ourAppDataPath;
        }
        String jnaAppData = SVNJNAUtil.getApplicationDataPath(false);
        if (jnaAppData != null) {
            ourAppDataPath = new File(jnaAppData);
            return ourAppDataPath;
        }
        
        String envAppData = getEnvironmentVariable("APPDATA");
        if (envAppData == null) {
            // no appdata for that user, fallback to system one.
            ourAppDataPath = getSystemApplicationDataPath();
        } else {
            ourAppDataPath = new File(envAppData);
        }
        return ourAppDataPath;
    }

    public static File getSystemApplicationDataPath() {
        if (ourSystemAppDataPath != null) {
            return ourSystemAppDataPath;
        }
        String jnaAppData = SVNJNAUtil.getApplicationDataPath(true);
        if (jnaAppData != null) {
            ourSystemAppDataPath = new File(jnaAppData);
            return ourSystemAppDataPath;
        }
        String envAppData = getEnvironmentVariable("ALLUSERSPROFILE");
        if (envAppData == null) {
            ourSystemAppDataPath = new File(new File("C:/Documents and Settings/All Users"), "Application Data");
        } else {
            ourSystemAppDataPath = new File(envAppData, "Application Data");
        }
        return ourSystemAppDataPath;
    }

    public static String getEnvironmentVariable(String name) {
        try {
            // pre-Java 1.5 this throws an Error. On Java 1.5 it
            // returns the environment variable
            Method getenv = System.class.getMethod("getenv", new Class[] {String.class});
            if (getenv != null) {
                Object value = getenv.invoke(null, new Object[] {name});
                if (value instanceof String) {
                    return (String) value;
                }
            }
        } catch (Throwable e) {
            try {
                // This means we are on 1.4. Get all variables into
                // a Properties object and get the variable from that
                return getEnvironment().getProperty(name);
            } catch (Throwable e1) {
                SVNDebugLog.getDefaultLog().logFinest(SVNLogType.DEFAULT, e);
                SVNDebugLog.getDefaultLog().logFinest(SVNLogType.DEFAULT, e1);
                return null;
            }
        }
        return null;
    }
    
    private static String ourTestEditor = null;
    private static String ourTestMergeTool = null;
    private static String ourTestFunction = null;
    
    public static void setTestEnvironment(String editor, String mergeTool, String function) {
        ourTestEditor = editor;
        ourTestMergeTool = mergeTool;
        ourTestFunction = function;
    }
    
    public static String[] getTestEnvironment() {
        return new String[] {ourTestEditor, ourTestMergeTool, ourTestFunction};
    }

    public static Properties getEnvironment() throws Throwable {
        Properties envVars = new Properties();
        //UNDERSTAND: code ???
        return envVars;
    }

    public static File createTempDirectory(String name) throws SVNException {
        File tmpFile = null;
        try {
            tmpFile = File.createTempFile("svnkit" + name, ".tmp");
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot create temporary directory: {0}", e.getMessage());
            SVNErrorManager.error(err, e, Level.FINE, SVNLogType.DEFAULT);
        }
        if (tmpFile.exists()) {
            tmpFile.delete();
        }
        tmpFile.mkdirs();
        return tmpFile;
    }

    public static File createTempFile(String prefix, String suffix) throws SVNException {
        File tmpFile = null;
        try {
            if (prefix.length() < 3) {
                prefix = "svn" + prefix;
            }
            tmpFile = File.createTempFile(prefix, suffix);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot create temporary file: {0}", e.getMessage());
            SVNErrorManager.error(err, e, Level.FINE, SVNLogType.DEFAULT);
        }
        return tmpFile;
    }

    public static File getSystemConfigurationDirectory() {
        return new Resource("/system/etc/subversion");
    }

    public static String readSingleLine(File file) throws IOException {
        if (!file.isFile() || !file.canRead()) {
            throw new IOException("can't open file '" + file.getAbsolutePath() + "'");
        }
        BufferedReader reader = null;
        String line = null;
        InputStream is = null;
        try {
            is = createFileInputStream(file);
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            line = reader.readLine();
        } finally {
            closeFile(is);
        }
        return line;
    }

    private static String decode(CharsetDecoder decoder, byte[] in) {
        ByteBuffer inBuf = ByteBuffer.wrap(in);
        CharBuffer outBuf = CharBuffer.allocate(inBuf.capacity()*Math.round(decoder.maxCharsPerByte() + 0.5f));
        decoder.decode(inBuf, outBuf, true);
        decoder.flush(outBuf);
        decoder.reset();
        return outBuf.flip().toString();
    }

    private static Subject getUser() throws SVNException {
    	BrokerPool database;
    	try {
			database = BrokerPool.getInstance();
		} catch (EXistException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot get current user: {0}", e.getMessage());
            SVNErrorManager.error(err, e, Level.FINE, SVNLogType.DEFAULT);
            return null;
		}
		
    	DBBroker broker = null;
		try {
			broker = database.get(null);
	        return broker.getSubject();
		} catch (EXistException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Cannot get current user: {0}", e.getMessage());
            SVNErrorManager.error(err, e, Level.FINE, SVNLogType.DEFAULT);
		} finally {
			database.release(broker);
		}
		
		return null;
    }

    private static String getCurrentUser() throws SVNException {
        return getUser().getName();
    }

    private static String getCurrentGroup() throws SVNException {
        return getUser().getPrimaryGroup();
    }

    private static boolean isHex(char ch) {
        return Character.isDigit(ch) || (Character.toUpperCase(ch) >= 'A' && Character.toUpperCase(ch) <= 'F');
    }

}
