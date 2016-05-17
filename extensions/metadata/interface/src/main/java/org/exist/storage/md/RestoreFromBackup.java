/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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
 */
package org.exist.storage.md;

import org.exist.backup.BackupDescriptor;
import org.exist.backup.SystemImport;
import org.exist.dom.QName;
import org.exist.util.EXistInputSource;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Stack;

import static org.exist.storage.md.MetaData.NAMESPACE_URI;
import static org.exist.storage.md.MetaData.PREFIX;

import static org.exist.storage.md.MDStorageManager.*;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class RestoreFromBackup extends BasicFunction {

    private static final QName NAME = new QName("restore-from-backup", NAMESPACE_URI, PREFIX);
    private static final String DESCRIPTION = "Restore metadata fields from backup.";
    private static final SequenceType RETURN = new SequenceType(Type.EMPTY, Cardinality.ZERO);

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    NAME,
                    DESCRIPTION,
                    new SequenceType[] {
                            new FunctionParameterSequenceType("backup-url", Type.STRING, Cardinality.ONE_OR_MORE, "The backup's url.")
                    },
                    RETURN
            )
    };

    public RestoreFromBackup(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        Path f = Paths.get(args[0].getStringValue());

        try {
            //get the backup descriptors, can be more than one if it was an incremental backup
            final Stack<BackupDescriptor> descriptors = SystemImport.getBackupDescriptors(f);

            final SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            saxFactory.setNamespaceAware(true);
            saxFactory.setValidating(false);
            final SAXParser sax = saxFactory.newSAXParser();
            final XMLReader reader = sax.getXMLReader();

            while (!descriptors.isEmpty()) {
                final BackupDescriptor descriptor = descriptors.pop();
                final EXistInputSource is = descriptor.getInputSource();
                is.setEncoding("UTF-8");

                final Handler handler = new Handler();

                reader.setContentHandler(handler);
                reader.parse(is);
            }

            return BooleanValue.TRUE;
        } catch (Exception e) {
            MDStorageManager.LOG.error(e.getMessage(), e);
        }

        return BooleanValue.FALSE;
    }

    class Handler extends DefaultHandler {

        MetaData md = MetaData.get();

        Metas colMetas = null;
        Metas docMetas = null;

        @Override
        public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {

            if (localName.equals("collection")) {
                String uuid = atts.getValue(NAMESPACE_URI, UUID);
                colMetas = md.getMetas(uuid);

            } else if (localName.equals("resource")) {
                String uuid = atts.getValue(NAMESPACE_URI, UUID);
                docMetas = md.getMetas(uuid);

            } else if (META.equals(localName) && NAMESPACE_URI.equals(namespaceURI)) {
                String uuid = atts.getValue(NAMESPACE_URI, UUID);
                String key = atts.getValue(NAMESPACE_URI, KEY);
                String value = atts.getValue(NAMESPACE_URI, VALUE);

                Meta meta = md.getMeta(uuid);

                if (docMetas != null) {
                    if (!meta.getObject().equals(docMetas.getUUID())) {
                        meta.delete();

                        if (docMetas.get(key) == null) {
                            md._addMeta(docMetas, uuid, key, value);
                        }
                    }
                } else if (colMetas != null) {
                    if (!meta.getObject().equals(colMetas.getUUID())) {
                        meta.delete();

                        if (colMetas.get(key) == null) {
                            md._addMeta(colMetas, uuid, key, value);
                        }
                    }
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (localName.equals("collection")) {
                colMetas = null;
                docMetas = null;

            } else if (localName.equals("resource")) {
                docMetas = null;
            }
        }
    }
}
