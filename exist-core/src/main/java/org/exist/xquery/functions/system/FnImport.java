/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012-2013 The eXist Project
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
 *  $Id$
 */
package org.exist.xquery.functions.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
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

import org.exist.backup.SystemImport;
import org.exist.backup.restore.listener.AbstractRestoreListener;
import org.exist.backup.restore.listener.RestoreListener;

import javax.annotation.Nullable;

public class FnImport extends BasicFunction {

	protected final static Logger logger = LogManager.getLogger(FnImport.class);

	protected final static QName NAME = 
			new QName("import", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);

	protected final static String DESCRIPTION = 
		"Restore the database or a section of the database (admin user only).";
	
	protected final static FunctionParameterSequenceType DIRorFILE =
		new FunctionParameterSequenceType("dir-or-file", Type.STRING, Cardinality.EXACTLY_ONE,
				"This is either a backup directory with the backup descriptor (__contents__.xml) or a backup ZIP file.");
			
	protected final static FunctionParameterSequenceType ADMIN_PASS =
		new FunctionParameterSequenceType("admin-pass", Type.STRING, Cardinality.ZERO_OR_ONE,
			"The password for the admin user");

	protected final static FunctionParameterSequenceType NEW_ADMIN_PASS =
		new FunctionParameterSequenceType("new-admin-pass", Type.STRING, Cardinality.ZERO_OR_ONE,
				"Set the admin password to this new password.");

	protected final static FunctionReturnSequenceType RETURN =
		new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "the import results");

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			NAME,
			DESCRIPTION,
			new SequenceType[] {
				DIRorFILE,
				ADMIN_PASS,
				NEW_ADMIN_PASS
			}, 
			RETURN
		),
		new FunctionSignature(
			new QName("import-silently", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			DESCRIPTION +
			" Messagers from exporter reroute to logs.",
			new SequenceType[] {
				DIRorFILE,
				ADMIN_PASS,
				NEW_ADMIN_PASS
			}, 
			RETURN
		)
	};

	public final static QName IMPORT_ELEMENT = new QName("import", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
	

	public FnImport(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		if( !context.getSubject().hasDbaRole() )
			{throw( new XPathException( this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to kill a running xquery" ) );}

    	final String dirOrFile = args[0].getStringValue();
        String adminPass = null;
        if (args[1].hasOne())
                {adminPass = args[1].getStringValue();}
        String adminPassAfter = null;
        if (args[2].hasOne())
                {adminPassAfter = args[2].getStringValue();}

        MemTreeBuilder builder = null;
        if (NAME.equals( mySignature.getName() )) {
            builder = context.getDocumentBuilder();
	        builder.startDocument();
	        builder.startElement(IMPORT_ELEMENT, null);
        }
        
        try {
        	final SystemImport restore = new SystemImport(context.getDatabase());
            final RestoreListener listener = new XMLRestoreListener(builder);
            restore.restore(org.exist.security.SecurityManager.DBA_USER, adminPass, adminPassAfter, Paths.get(dirOrFile), listener);
        } catch (final Exception e) {
            throw new XPathException(this, "restore failed with exception: " + e.getMessage(), e);
        }
        
        if (builder == null) {
        	return Sequence.EMPTY_SEQUENCE;
        } else {
	        builder.endElement();
	        builder.endDocument();
	        return (NodeValue) builder.getDocument().getDocumentElement();
        }
    }

    private static class XMLRestoreListener extends AbstractRestoreListener {

        public final static QName COLLECTION_ELEMENT = new QName("collection", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
        public final static QName RESOURCE_ELEMENT = new QName("resource", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
        public final static QName INFO_ELEMENT = new QName("info", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
        public final static QName WARN_ELEMENT = new QName("warn", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
        public final static QName ERROR_ELEMENT = new QName("error", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
	
        @Nullable private final MemTreeBuilder builder;

        private XMLRestoreListener(@Nullable final MemTreeBuilder builder) {
            this.builder = builder;
        }

        @Override
        public void createdCollection(final String collection) {
			if (builder == null) {
				SystemImport.LOG.info("Create collection "+collection);
			} else {
	            builder.startElement(COLLECTION_ELEMENT, null);
	            builder.characters(collection);
	            builder.endElement();
			}
        }

        @Override
        public void restoredResource(final String resource) {
			if (builder == null) {
				SystemImport.LOG.info("Restore resource "+resource);
			} else {
	            builder.startElement(RESOURCE_ELEMENT, null);
	            builder.characters(resource);
	            builder.endElement();
			}
        }

        @Override
        public void info(final String message) {
			if (builder == null) {
				SystemImport.LOG.info(message);
			} else {
	            builder.startElement(INFO_ELEMENT, null);
	            builder.characters(message);
	            builder.endElement();
			}
        }

        @Override
        public void warn(final String message) {
			if (builder == null) {
				SystemImport.LOG.warn(message);
			} else {
	            builder.startElement(WARN_ELEMENT, null);
	            builder.characters(message);
	            builder.endElement();
			}
        }

        @Override
        public void error(final String message) {
			if (builder == null) {
				SystemImport.LOG.error(message);
			} else {
	            builder.startElement(ERROR_ELEMENT, null);
	            builder.characters(message);
	            builder.endElement();
			}
        }
    }
}
