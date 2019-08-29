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
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.nio.file.Paths;
import java.util.Optional;

import org.exist.backup.restore.listener.AbstractRestoreListener;
import org.exist.backup.restore.listener.RestoreListener;

import static org.exist.xquery.functions.system.SystemModule.functionSignatures;
import static org.exist.xquery.FunctionDSL.*;

public class Restore extends BasicFunction {

	protected final static Logger logger = LogManager.getLogger(Restore.class);

    public static final FunctionParameterSequenceType PARAM_DIR_OR_FILE = param("dir-or-file", Type.STRING,
            "This is either a backup directory with the backup descriptor (__contents__.xml) or a backup ZIP file.");
    public static final FunctionParameterSequenceType PARAM_ADMIN_PASS = optParam("admin-pass", Type.STRING,
            "The password for the admin user");
    public static final FunctionParameterSequenceType PARAM_NEW_ADMIN_PASS = optParam("new-admin-pass", Type.STRING,
            "Set the admin password to this new password.");

    private static final String FS_RESTORE_NAME = "restore";

    static final FunctionSignature[] FS_RESTORE = functionSignatures(
            FS_RESTORE_NAME,
            "Restore the database or a section of the database (admin user only).",
            returns(Type.NODE, "the restore results"),
            arities(
                 arity(
                         PARAM_DIR_OR_FILE,
                         PARAM_ADMIN_PASS,
                         PARAM_NEW_ADMIN_PASS
                 ),
                 arity(
                         PARAM_DIR_OR_FILE,
                         PARAM_ADMIN_PASS,
                         PARAM_NEW_ADMIN_PASS,
                         param("overwrite", Type.BOOLEAN,
                                 "Should newer versions of apps installed in the database be overwritten " +
                                         "by those found in the backup? False by default.")
                 )
            )
    );

	public final static QName RESTORE_ELEMENT = new QName("restore", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
	

	public Restore(XQueryContext context, FunctionSignature signature) {
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

        final boolean overwriteApps = args.length == 4 && args[3].effectiveBooleanValue();

        final MemTreeBuilder builder = context.getDocumentBuilder();
        builder.startDocument();
        builder.startElement(RESTORE_ELEMENT, null);

        final BrokerPool pool = context.getBroker().getBrokerPool();
        try {
            final Subject admin = pool.getSecurityManager().authenticate(SecurityManager.DBA_USER, adminPass);
            try (final DBBroker broker = pool.get(Optional.of(admin));
                    final Txn transaction = broker.continueOrBeginTransaction()) {

                final RestoreListener listener = new XMLRestoreListener(builder);
                final org.exist.backup.Restore restore = new org.exist.backup.Restore();
                restore.restore(broker, transaction, adminPassAfter, Paths.get(dirOrFile), listener, overwriteApps);

                transaction.commit();
            }
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

        private XMLRestoreListener(final MemTreeBuilder builder) {
            this.builder = builder;
        }

        @Override
        public void createdCollection(final String collection) {
            builder.startElement(COLLECTION_ELEMENT, null);
            builder.characters(collection);
            builder.endElement();
        }

        @Override
        public void restoredResource(final String resource) {
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
        public void warn(final String message) {
            builder.startElement(WARN_ELEMENT, null);
            builder.characters(message);
            builder.endElement();
        }

        @Override
        public void error(final String message) {
            builder.startElement(ERROR_ELEMENT, null);
            builder.characters(message);
            builder.endElement();
        }
    }
}
