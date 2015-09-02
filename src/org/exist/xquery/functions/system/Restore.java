/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2011 The eXist-db Project
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
 * \$Id\$
 */
package org.exist.xquery.functions.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.nio.file.Paths;

import org.exist.backup.restore.listener.AbstractRestoreListener;
import org.exist.backup.restore.listener.RestoreListener;

public class Restore extends BasicFunction {

	protected final static Logger logger = LogManager.getLogger(Restore.class);

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("restore", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"Restore the database or a section of the database (admin user only).",
			new SequenceType[] {
					new FunctionParameterSequenceType("dir-or-file", Type.STRING, Cardinality.EXACTLY_ONE,
							"This is either a backup directory with the backup descriptor (__contents__.xml) or a backup ZIP file."),
					new FunctionParameterSequenceType("admin-pass", Type.STRING, Cardinality.ZERO_OR_ONE,
							"The password for the admin user"),
					new FunctionParameterSequenceType("new-admin-pass", Type.STRING, Cardinality.ZERO_OR_ONE,
							"Set the admin password to this new password.") 
			}, 
			new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "the restore results"));

	public final static QName RESTORE_ELEMENT = new QName("restore", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
	

	public Restore(XQueryContext context) {
		super(context, signature);
	}

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final String dirOrFile = args[0].getStringValue();
        String adminPass = null;
        if (args[1].hasOne())
                {adminPass = args[1].getStringValue();}
        String adminPassAfter = null;
        if (args[2].hasOne())
                {adminPassAfter = args[2].getStringValue();}

        final MemTreeBuilder builder = context.getDocumentBuilder();
        builder.startDocument();
        builder.startElement(RESTORE_ELEMENT, null);
        
        try {
            final org.exist.backup.Restore restore = new org.exist.backup.Restore();
            final RestoreListener listener = new XMLRestoreListener(builder);
            restore.restore(listener, org.exist.security.SecurityManager.DBA_USER, adminPass, adminPassAfter, Paths.get(dirOrFile), XmldbURI.EMBEDDED_SERVER_URI.toString());
        } catch (final Exception e) {
            throw new XPathException(this, "restore failed with exception: " + e.getMessage(), e);
        }
        
        builder.endElement();
        builder.endDocument();
        return (NodeValue) builder.getDocument().getDocumentElement();
    }

    private static class XMLRestoreListener extends AbstractRestoreListener {

        public final static QName COLLECTION_ELEMENT = new QName("collection", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
	public final static QName RESOURCE_ELEMENT = new QName("resource", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
        public final static QName INFO_ELEMENT = new QName("info", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
	public final static QName WARN_ELEMENT = new QName("warn", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
        public final static QName ERROR_ELEMENT = new QName("error", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
	
        private final MemTreeBuilder builder;

        private XMLRestoreListener(MemTreeBuilder builder) {
            this.builder = builder;
        }

        @Override
        public void createCollection(String collection) {
            builder.startElement(COLLECTION_ELEMENT, null);
            builder.characters(collection);
            builder.endElement();
        }

        @Override
        public void restored(String resource) {
            builder.startElement(RESOURCE_ELEMENT, null);
            builder.characters(resource);
            builder.endElement();
        }

        @Override
        public void info(String message) {
            builder.startElement(INFO_ELEMENT, null);
            builder.characters(message);
            builder.endElement();
        }

        @Override
        public void warn(String message) {
            super.warn(message);
            
            builder.startElement(WARN_ELEMENT, null);
            builder.characters(message);
            builder.endElement();
        }

        @Override
        public void error(String message) {
            super.error(message);
            
            builder.startElement(ERROR_ELEMENT, null);
            builder.characters(message);
            builder.endElement();
        }
    }
}
