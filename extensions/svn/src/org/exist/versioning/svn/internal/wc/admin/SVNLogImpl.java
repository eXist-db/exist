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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNLogImpl extends SVNLog {

    private Resource myFile;
    private Resource myTmpFile;

    public SVNLogImpl(Resource logFile, Resource tmpFile, SVNAdminArea adminArea) {
        super(adminArea);
        myFile = logFile;
        myTmpFile = tmpFile;
    }

    public void save() throws SVNException {
        if (myTmpFile == null || myCache == null) {
            return;
        }
        
        Writer os = null;
        try {
//            os = new OutputStreamWriter(SVNFileUtil.openFileForWriting(myTmpFile), "UTF-8");
          os = SVNFileUtil.openFileForWriting(myTmpFile);
            for (Iterator commands = myCache.iterator(); commands.hasNext();) {
                SVNProperties command = (SVNProperties) commands.next();
                SVNPropertyValue name = command.remove("");
                os.write("<");
                os.write(name.getString());
                for (Iterator attrs = command.nameSet().iterator(); attrs.hasNext();) {
                    String attr = (String) attrs.next();
                    SVNPropertyValue value = command.getSVNPropertyValue(attr);
                    String str = null;
                    if (value == null) {
                        str = "";
                    } else {
                        str = SVNPropertyValue.getPropertyAsString(value);
                    }
                    str = SVNEncodingUtil.xmlEncodeAttr(str);
                    os.write("\n   ");
                    os.write(attr);
                    os.write("=\"");
                    os.write(str);
                    os.write("\"");
                }
                os.write("/>\n");
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot write log file ''{0}'': {1}", new Object[] {myFile, e.getLocalizedMessage()});
            SVNErrorManager.error(err, e, SVNLogType.WC);
        } finally {
            SVNFileUtil.closeFile(os);
            myCache = null;
        }
        SVNFileUtil.rename(myTmpFile, myFile);
        SVNFileUtil.setReadonly(myFile, true);
    }

    public Collection readCommands() throws SVNException {
        if (!myFile.exists()) {
            return null;
        }
        BufferedReader reader = null;
        Collection commands = new ArrayList();
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(myFile, SVNLogType.WC), "UTF-8"));
            String line;
            SVNProperties attrs = new SVNProperties();
            String name = null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("<")) {
                    name = line.substring(1);
                    continue;
                }
                int index = line.indexOf('=');
                if (index > 0) {
                    String attrName = line.substring(0, index).trim();
                    String value = line.substring(index + 1).trim();
                    if (value.endsWith("/>")) {
                        value = value.substring(0, value.length() - "/>".length());
                    }
                    if (value.startsWith("\"")) {
                        value = value.substring(1);
                    }
                    if (value.endsWith("\"")) {
                        value = value.substring(0, value.length() - 1);
                    }
                    value = SVNEncodingUtil.xmlDecode(value);
                    if ("".equals(value) && !SVNLog.NAME_ATTR.equals(attrName)) {
                        value = null;
                    }
                    attrs.put(attrName, value);
                }
                if (line.endsWith("/>") && name != null) {
                    // run command
                    attrs.put("", name);
                    commands.add(attrs);
                    attrs = new SVNProperties();
                    name = null;
                }
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read log file ''{0}'': {1}", new Object[] {myFile, e.getLocalizedMessage()});
            SVNErrorManager.error(err, e, SVNLogType.WC);
        } finally {
            SVNFileUtil.closeFile(reader);
        }
        
        return commands;
    }

    public String toString() {
        return "Log: " + myFile;
    }

    public void delete() throws SVNException {
        SVNFileUtil.deleteFile(myFile);
        SVNFileUtil.deleteFile(myTmpFile);
    }

    public boolean exists() {
        return myFile.exists();
    }

}
