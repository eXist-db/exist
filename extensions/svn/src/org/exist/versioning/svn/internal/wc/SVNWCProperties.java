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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.wc.ISVNPropertyComparator;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNWCProperties {
    public static final String SVN_HASH_TERMINATOR = "END";
    
    private File myFile;

    private String myPath;

    public SVNWCProperties(File properitesFile, String path) {
        myFile = properitesFile;
        myPath = path;
    }

    public File getFile() {
        return myFile;
    }

    public String getPath() {
        return myPath;
    }

    public Collection properties(Collection target) throws SVNException {
        target = target == null ? new TreeSet() : target;
        if (isEmpty()) {
            return target;
        }
        ByteArrayOutputStream nameOS = new ByteArrayOutputStream();
        InputStream is = SVNFileUtil.openFileForReading(getFile(), SVNLogType.WC);
        try {
            while (readProperty('K', is, nameOS)) {
                target.add(new String(nameOS.toByteArray(), "UTF-8"));
                nameOS.reset();
                readProperty('V', is, null);
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e, SVNLogType.WC);
        } finally {
            SVNFileUtil.closeFile(is);
        }
        return target;
    }

    public SVNProperties asMap() throws SVNException {
        SVNProperties result = new SVNProperties();
        if (isEmpty()) {
            return result;
        }
        ByteArrayOutputStream nameOS = new ByteArrayOutputStream();
        InputStream is = SVNFileUtil.openFileForReading(getFile(), SVNLogType.WC);
        try {
            while (readProperty('K', is, nameOS)) {
                String name = new String(nameOS.toByteArray(), "UTF-8");
                nameOS.reset();
                readProperty('V', is, nameOS);
                byte[] value = nameOS.toByteArray();
                result.put(name, value);
                nameOS.reset();
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read properties file ''{0}'': {1}", new Object[] {getFile(), e.getLocalizedMessage()});
            SVNErrorManager.error(err, e, SVNLogType.WC);
        } finally {
            SVNFileUtil.closeFile(is);
        }
        return result;
    }

    public boolean compareTo(SVNWCProperties properties,
            ISVNPropertyComparator comparator) throws SVNException {
        boolean equals = true;
        Collection props1 = properties(null);
        Collection props2 = properties.properties(null);

        // missed in props2.
        Collection tmp = new TreeSet(props1);
        tmp.removeAll(props2);
        for (Iterator props = tmp.iterator(); props.hasNext();) {
            String missing = (String) props.next();
            comparator.propertyDeleted(missing);
            equals = false;
        }

        // added in props2.
        tmp = new TreeSet(props2);
        tmp.removeAll(props1);

        File tmpFile = null;
        File tmpFile1 = null;
        File tmpFile2 = null;
        OutputStream os = null;
        InputStream is = null;
        InputStream is1 = null;
        InputStream is2 = null;

        for (Iterator props = tmp.iterator(); props.hasNext();) {
            String added = (String) props.next();
            try {
                tmpFile = SVNFileUtil.createUniqueFile(getFile().getParentFile(), getFile().getName(), ".tmp", true);

                os = SVNFileUtil.openFileForWriting(tmpFile);
                properties.getPropertyValue(added, os);
                SVNFileUtil.closeFile(os);

                is = SVNFileUtil.openFileForReading(tmpFile, SVNLogType.WC);
                comparator.propertyAdded(added, is, (int) tmpFile.length());
                equals = false;
                SVNFileUtil.closeFile(is);
            } finally {
                if (tmpFile != null) {
                    tmpFile.delete();
                }
                SVNFileUtil.closeFile(os);
                SVNFileUtil.closeFile(is);
                tmpFile = null;
                is = null;
                os = null;
            }
        }

        // changed in props2
        props2.retainAll(props1);
        for (Iterator props = props2.iterator(); props.hasNext();) {
            String changed = (String) props.next();

            try {
                tmpFile1 = SVNFileUtil.createUniqueFile(getFile().getParentFile(), getFile().getName(), ".tmp1", true);
                tmpFile2 = SVNFileUtil.createUniqueFile(getFile().getParentFile(), getFile().getName(), ".tmp2", true);

                os = SVNFileUtil.openFileForWriting(tmpFile1);
                getPropertyValue(changed, os);
                os.close();
                os = SVNFileUtil.openFileForWriting(tmpFile2);
                properties.getPropertyValue(changed, os);
                os.close();
                if (tmpFile2.length() != tmpFile1.length()) {
                    is = SVNFileUtil.openFileForReading(tmpFile2, SVNLogType.WC);
                    comparator.propertyChanged(changed, is, (int) tmpFile2
                            .length());
                    equals = false;
                    SVNFileUtil.closeFile(is);
                } else {
                    is1 = SVNFileUtil.openFileForReading(tmpFile1, SVNLogType.WC);
                    is2 = SVNFileUtil.openFileForReading(tmpFile2, SVNLogType.WC);
                    boolean differs = false;
                    for (int i = 0; i < tmpFile1.length(); i++) {
                        if (is1.read() != is2.read()) {
                            differs = true;
                            break;
                        }
                    }
                    SVNFileUtil.closeFile(is1);
                    SVNFileUtil.closeFile(is2);
                    if (differs) {
                        is2 = SVNFileUtil.openFileForReading(tmpFile2, SVNLogType.WC);
                        comparator.propertyChanged(changed, is2, (int) tmpFile2
                                .length());
                        equals = false;
                        SVNFileUtil.closeFile(is2);
                    }
                }
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                SVNErrorManager.error(err, e, SVNLogType.WC);
            } finally {
                if (tmpFile2 != null) {
                    tmpFile2.delete();
                }
                if (tmpFile1 != null) {
                    tmpFile1.delete();
                }
                SVNFileUtil.closeFile(os);
                SVNFileUtil.closeFile(is);
                SVNFileUtil.closeFile(is1);
                SVNFileUtil.closeFile(is2);
                os = null;
                tmpFile1 = tmpFile2 = null;
                is = is1 = is2 = null;
            }
        }
        return equals;
    }

    public String getPropertyValue(String name) throws SVNException {
        if (isEmpty()) {
            return null;
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        os = (ByteArrayOutputStream) getPropertyValue(name, os);
        if (os != null && os.size() >= 0) {
            byte[] bytes = os.toByteArray();
            try {
                return new String(bytes, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return new String(bytes);
            }
        }
        return null;
    }

    public OutputStream getPropertyValue(String name, OutputStream os) throws SVNException {
        if (isEmpty()) {
            return null;
        }
        ByteArrayOutputStream nameOS = new ByteArrayOutputStream();
        InputStream is = SVNFileUtil.openFileForReading(getFile(), SVNLogType.WC);
        try {
            while (readProperty('K', is, nameOS)) {
                String currentName = new String(nameOS.toByteArray(), "UTF-8");
                nameOS.reset();
                if (currentName.equals(name)) {
                    readProperty('V', is, os);
                    return os;
                }
                readProperty('V', is, null);                
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e, SVNLogType.WC);
        } finally {
            SVNFileUtil.closeFile(is);
        }
        return null;
    }

    public void setPropertyValue(String name, SVNPropertyValue value) throws SVNException {
        byte[] bytes = SVNPropertyValue.getPropertyAsBytes(value);
        int length = bytes != null && bytes.length >= 0 ? bytes.length : -1;
        setPropertyValue(name, bytes != null ? new ByteArrayInputStream(bytes) : null, length);
    }

    public void setPropertyValue(String name, InputStream is, int length)
            throws SVNException {
        InputStream src = null;
        OutputStream dst = null;
        File tmpFile = null;
        boolean empty = false;
        try {
            tmpFile = SVNFileUtil.createUniqueFile(getFile().getParentFile(), getFile().getName(), ".tmp", true);
            if (!isEmpty()) {
                src = SVNFileUtil.openFileForReading(getFile(), SVNLogType.WC);
            }
            dst = SVNFileUtil.openFileForWriting(tmpFile);
            empty = !copyProperties(src, dst, name, is, length);
        } finally {
            SVNFileUtil.closeFile(src);
            SVNFileUtil.closeFile(dst);
        }
        if (tmpFile != null) {
            if (!empty) {
                SVNFileUtil.rename(tmpFile, getFile());
                SVNFileUtil.setReadonly(getFile(), true);
            } else {
                SVNFileUtil.deleteFile(tmpFile);
                SVNFileUtil.deleteFile(getFile());
            }
        }
    }

    public void setProperties(SVNProperties properties) throws SVNException {
        if (properties != null) {
            for (Iterator names = properties.nameSet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                SVNPropertyValue value = properties.getSVNPropertyValue(name);
                setPropertyValue(name, value);
            }
        }
    }
    
    public SVNProperties compareTo(SVNWCProperties properties) throws SVNException {
        final SVNProperties locallyChangedProperties = new SVNProperties();
        compareTo(properties, new ISVNPropertyComparator() {
            public void propertyAdded(String name, InputStream value, int length) {
                propertyChanged(name, value, length);
            }

            public void propertyChanged(String name, InputStream newValue,
                    int length) {
                ByteArrayOutputStream os = new ByteArrayOutputStream(length);
                for (int i = 0; i < length; i++) {
                    try {
                        os.write(newValue.read());
                    } catch (IOException e) {
                    }
                }
                byte[] bytes = os.toByteArray();
                try {
                    locallyChangedProperties.put(name, new String(bytes, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    locallyChangedProperties.put(name, new String(bytes));
                }
            }

            public void propertyDeleted(String name) {
                locallyChangedProperties.put(name, (SVNPropertyValue) null);
            }
        });
        return locallyChangedProperties;
    }

    public static void setProperties(SVNProperties namesToValues, File target, File tmpFile, String terminator) throws SVNException {
        OutputStream dst = null;
        try {
            if (tmpFile != null) {
                tmpFile.getParentFile().mkdirs();
            } else {
                target.getParentFile().mkdirs();
            }
            dst = SVNFileUtil.openFileForWriting(tmpFile != null ? tmpFile : target);
            setProperties(namesToValues, dst, terminator);
        } finally {
            SVNFileUtil.closeFile(dst);
        }
        if (tmpFile != null && target != null) {
            target.getParentFile().mkdirs();
            SVNFileUtil.rename(tmpFile, target);
        } 
        if (target != null) {
            SVNFileUtil.setReadonly(target, true);
        }
    }

    public static void setProperties(SVNProperties namesToValues, OutputStream target, String terminator) throws SVNException {
        try {
            Object[] keys = namesToValues.nameSet().toArray();
            Arrays.sort(keys);
            for (int i = 0; i < keys.length; i++) {
                String propertyName = (String) keys[i];
                writeProperty(target, 'K', propertyName.getBytes("UTF-8"));
                writeProperty(target, 'V', SVNPropertyValue.getPropertyAsBytes(namesToValues.getSVNPropertyValue(propertyName)));
            }
            if (terminator != null) {
                target.write(terminator.getBytes("UTF-8"));
                target.write('\n');
            }
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe, SVNLogType.WC);
        }
    }
    
    public static void appendProperty(String name, SVNPropertyValue value, OutputStream target) throws SVNException {
        if (name == null || value == null){
            return;
        }

        byte[] bytes = SVNPropertyValue.getPropertyAsBytes(value);        

        try {
            writeProperty(target, 'K', name.getBytes("UTF-8"));
            writeProperty(target, 'V', bytes);
        }catch(IOException ioe){    
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe, SVNLogType.WC);
        }
    }
    
    public static void appendPropertyDeleted(String name, OutputStream target) throws SVNException {
        if(name == null){
            return;
        }
        try {
            writeProperty(target, 'D', name.getBytes("UTF-8"));
        }catch(IOException ioe){    
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe, SVNLogType.WC);
        }
    }
    
    /** @noinspection ResultOfMethodCallIgnored */
    private static boolean copyProperties(InputStream is, OutputStream os,
            String name, InputStream value, int length) throws SVNException {
        // read names, till name is met, then insert value or skip this
        // property.
        int propCount = 0;
        try {
            if (is != null) {
                int l = 0;
                while ((l = readLength(is, 'K')) > 0) {
                    byte[] nameBytes = new byte[l];
                
                    SVNFileUtil.readIntoBuffer(is, nameBytes, 0, nameBytes.length);
                    is.read();
                    if (name.equals(new String(nameBytes, "UTF-8"))) {
                        // skip property, will be appended.
                        readProperty('V', is, null);
                        continue;
                    }
                    // save name
                    writeProperty(os, 'K', nameBytes);
                    l = readLength(is, 'V');
                    writeProperty(os, 'V', is, l);
                    is.read();
                    propCount++;
                }
            }
            if (value != null && length >= 0) {
                byte[] nameBytes = name.getBytes("UTF-8");
                writeProperty(os, 'K', nameBytes);
                writeProperty(os, 'V', value, length);
                propCount++;
            }
            if (propCount > 0) {
                os.write(new byte[] { 'E', 'N', 'D', '\n' });
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e, SVNLogType.WC);
        }
        return propCount > 0;
    }

    private static boolean readProperty(char type, InputStream is, OutputStream os) throws IOException {
        int length = readLength(is, type);
        if (length < 0) {
            return false;
        }
        if (os != null) {
            byte[] value = new byte[length];
            
            int r = SVNFileUtil.readIntoBuffer(is, value, 0, value.length);
            
            if (r >= 0) {
                os.write(value, 0, r);
            } else {
                return false;
            }
        } else {
            while(length > 0) {
                length -= is.skip(length);
            }
        }
        return is.read() == '\n';
    }

    private static void writeProperty(OutputStream os, char type, byte[] value)
            throws IOException {
        os.write((byte) type);
        os.write(' ');
        os.write(Integer.toString(value.length).getBytes("UTF-8"));
        os.write('\n');
        os.write(value);
        os.write('\n');
    }

    private static void writeProperty(OutputStream os, char type,
            InputStream value, int length) throws IOException {
        os.write((byte) type);
        os.write(' ');
        os.write(Integer.toString(length).getBytes("UTF-8"));
        os.write('\n');
        for (int i = 0; i < length; i++) {
            int r = value.read();
            os.write(r);
        }
        os.write('\n');
    }

    private static int readLength(InputStream is, char type) throws IOException {
        byte[] buffer = new byte[255];
        int r = SVNFileUtil.readIntoBuffer(is, buffer, 0, 4);
        if (r != 4) {
            throw new IOException("invalid properties file format");
        }
        // either END\n or K x\n
        if (buffer[0] == 'E' && buffer[1] == 'N' && buffer[2] == 'D' && buffer[3] == '\n') {
            return -1;
        } else if (buffer[0] == type && buffer[1] == ' ') {
            int i = 4;
            if (buffer[3] != '\n') {
                while (true) {
                    int b = is.read();
                    if (b < 0) {
                        throw new IOException("invalid properties file format");
                    } else if (b == '\n') {
                        break;
                    }
                    buffer[i] = (byte) (0xFF & b);
                    i++;
                }
            } else {
                i = 3;
            }
            String length = new String(buffer, 2, i - 2, "UTF-8");
            return Integer.parseInt(length.trim());
        }
        throw new IOException("invalid properties file format");
    }

    public boolean isEmpty() {
        return !getFile().exists() || getFile().length() <= 4;
    }
}
