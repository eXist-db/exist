/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.backup.xquery;

import org.exist.Namespaces;
import org.exist.backup.BackupDirectory;
import org.exist.backup.BackupDescriptor;
import org.exist.backup.ZipArchiveBackupDescriptor;
import org.exist.backup.FileSystemBackupDescriptor;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.storage.BrokerPool;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.helpers.AttributesImpl;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ListBackups extends BasicFunction {

    public final static FunctionSignature signature =
            new FunctionSignature(
                new QName("list", BackupModule.NAMESPACE_URI, BackupModule.PREFIX),
                "Returns an XML fragment listing all eXist backups found in a specific " +
                "backup directory. The directory is passed in the argument.",
                new SequenceType[] { new FunctionParameterSequenceType("directory", Type.STRING, Cardinality.EXACTLY_ONE, "Directory name to show list of backus on.") },
                new SequenceType(Type.NODE, Cardinality.ONE_OR_MORE)
            );

    public final static QName DIRECTORY_ELEMENT = new QName("directory", Namespaces.EXIST_NS, "");
    public final static QName BACKUP_ELEMENT = new QName("backup", Namespaces.EXIST_NS, "");

    public ListBackups(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        String exportDir = args[0].getStringValue();
        File dir = new File(exportDir);
        if (!dir.isAbsolute())
            dir = new File((String)context.getBroker().getConfiguration()
                    .getProperty(BrokerPool.PROPERTY_DATA_DIR), exportDir);

        context.pushDocumentContext();
        try {
            MemTreeBuilder builder = context.getDocumentBuilder();
            int nodeNr = builder.startElement(DIRECTORY_ELEMENT, null);
            if (dir.isDirectory() && dir.canRead()) {
                Pattern pattern = Pattern.compile(BackupDirectory.FILE_REGEX);
                Matcher matcher = pattern.matcher("");
                File[] files = dir.listFiles();
                for (int i = 0; i < files.length; i++) {
                    matcher.reset(files[i].getName());
                    if (matcher.matches()) {
                        BackupDescriptor descriptor;
                        try {
                            if (files[i].getName().endsWith(".zip"))
                                descriptor = new ZipArchiveBackupDescriptor(files[i]);
                            else
                                descriptor = new FileSystemBackupDescriptor(files[i]);
                            Properties properties = descriptor.getProperties();
                            if (properties != null) {
                                AttributesImpl attrs = new AttributesImpl();
                                attrs.addAttribute("", "file", "file", "CDATA", files[i].getName());
                                builder.startElement(BACKUP_ELEMENT, attrs);
                                for (Iterator iter = properties.keySet().iterator(); iter.hasNext();) {
                                        String key = iter.next().toString();
                                        builder.startElement(new QName(key, Namespaces.EXIST_NS, ""), null);
                                        builder.characters((String) properties.get(key));
                                        builder.endElement();
                                    }
                                builder.endElement();
                            }
                        } catch (IOException e) {
                        }
                    }
                }
            }
            builder.endElement();
            return builder.getDocument().getNode(nodeNr);
        } finally {
            context.popDocumentContext();
        }
    }
}