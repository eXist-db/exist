/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2014 The eXist Project
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
package org.exist.collections.triggers;

import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.PermissionFactory;
import org.exist.security.SecurityManager;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.storage.DBBroker;
import org.exist.storage.StartupTrigger;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

/**
 * Startup Trigger to fire XQuery scripts during database startup.
 *
 * Load scripts into /db/system/autostart as DBA.
 *
 * <pre>
 * {@code
 * <startup>
 *   <triggers>
 *     <trigger class="org.exist.collections.triggers.XQueryStartupTrigger"/>
 *   </triggers>
 * </startup>
 * }
 * </pre>
 *
 * Due to security reasons individual scripts cannot be specified anymore. The permissions were not checked per file.
 *
 * <pre>
 * {@code
 *       <parameter name="xquery" value="/db/script1.xq"/>
 *       <parameter name="xquery" value="/db/script2.xq"/>
 * }
 * </pre>
 *
 * @author Dannes Wessels
 */
public class XQueryStartupTrigger implements StartupTrigger {

    protected final static Logger LOG = LogManager.getLogger(XQueryStartupTrigger.class);

    private static final String XQUERY = "xquery";
    private static final String AUTOSTART_COLLECTION = "/db/system/autostart";
    private static final String[] XQUERY_EXTENSIONS = {".xq", ".xquery", ".xqy"};
    private static final String REQUIRED_MIMETYPE = "application/xquery";

    @Override
    public void execute(DBBroker broker, final Txn transaction, Map<String, List<? extends Object>> params) {

        LOG.info("Starting Startup Trigger for stored XQueries");

        for (String path : getScriptsInStartupCollection(broker)) {
            executeQuery(broker, path);
        }

//        for (String path : getParameters(params)) {
//            executeQuery(broker, path);
//        }
    }

    /**
     * List all xquery scripts in /db/system/autostart
     *
     * @param broker The exist-db broker
     * @return List of xquery scripts
     */
    private List<String> getScriptsInStartupCollection(DBBroker broker) {

        // Return values
        List<String> paths = new ArrayList<>();

        XmldbURI uri = XmldbURI.create(AUTOSTART_COLLECTION);

        try(final Collection collection = broker.openCollection(uri, LockMode.READ_LOCK)) {
            if (collection == null) {
                LOG.debug(String.format("Collection '%s' not found.", AUTOSTART_COLLECTION));
                createAutostartCollection(broker);

            } else {
                LOG.debug(String.format("Scanning collection '%s'.", AUTOSTART_COLLECTION));

                if (isPermissionsOK(collection)) {

                    Iterator<DocumentImpl> documents = collection.iteratorNoLock(broker);
                    while (documents.hasNext()) {
                        DocumentImpl document = documents.next();
                        String docPath = document.getURI().toString();

                        if (isPermissionsOK(document)) {

                            if (StringUtils.endsWithAny(docPath, XQUERY_EXTENSIONS)) {
                                paths.add(XmldbURI.EMBEDDED_SERVER_URI_PREFIX + docPath);

                            } else {
                                LOG.error(String.format("Skipped document '%s', not an xquery script.", docPath));
                            }

                        } else {
                            LOG.error(String.format("Document %s should be owned by DBA, mode %s, mimetype %s",
                                    docPath, Permission.DEFAULT_SYSTEM_SECURITY_COLLECTION_PERM, REQUIRED_MIMETYPE));
                        }
                    }

                } else {
                    LOG.error(String.format("Collection %s should be owned by SYSTEM/DBA, mode %s.", AUTOSTART_COLLECTION,
                            Permission.DEFAULT_SYSTEM_SECURITY_COLLECTION_PERM));
                }

            }

            LOG.debug(String.format("Found %s XQuery scripts in '%s'.", paths.size(), AUTOSTART_COLLECTION));

        } catch (PermissionDeniedException ex) {
            LOG.error(ex.getMessage());
        }

        return paths;

    }

    /**
     * Verify that the permissions for a collection are SYSTEM/DBA/770
     *
     * @param collection The collection
     * @return TRUE if the conditions are met, else FALSE
     */
    private boolean isPermissionsOK(Collection collection) {

        Permission perms = collection.getPermissions();

        return (perms.getOwner().getName().equals(SecurityManager.SYSTEM)
                && perms.getGroup().getName().equals(SecurityManager.DBA_GROUP)
                && perms.getMode() == Permission.DEFAULT_SYSTEM_SECURITY_COLLECTION_PERM);

    }

    /**
     * Verify that the owner of the document is DBA, the document is owned by the DBA group and that the permissions are
     * set 0770, and the mimetype is set application/xquery.
     *
     * @param document The document
     * @return TRUE if the conditions are met, else FALSE
     */
    private boolean isPermissionsOK(DocumentImpl document) {

        Permission perms = document.getPermissions();

        return (perms.getOwner().hasDbaRole()
                && perms.getGroup().getName().equals(SecurityManager.DBA_GROUP)
                && perms.getMode() == Permission.DEFAULT_SYSTEM_SECURITY_COLLECTION_PERM
                && document.getMetadata().getMimeType().equals(REQUIRED_MIMETYPE));

    }

    /**
     * Get all XQuery paths from provided parameters in conf.xml
     */
    private List<String> getParameters(Map<String, List<? extends Object>> params) {

        // Return values
        List<String> paths = new ArrayList<>();

        // The complete data map
        Set<Map.Entry<String, List<? extends Object>>> data = params.entrySet();

        // Iterate over all entries
        for (Map.Entry<String, List<? extends Object>> entry : data) {

            // only the 'xpath' parameter is used.
            if (XQUERY.equals(entry.getKey())) {

                // Iterate over all values (object lists)
                List<? extends Object> list = entry.getValue();
                for (Object o : list) {

                    if (o instanceof String) {
                        String value = (String) o;

                        if (value.startsWith("/")) {

                            // Rewrite to URL in database
                            value = XmldbURI.EMBEDDED_SERVER_URI_PREFIX + value;

                            // Prevent double entries
                            if (!paths.contains(value)) {
                                paths.add(value);
                            }

                        } else {
                            LOG.error(String.format("Path '%s' should start with a '/'", value));
                        }
                    }
                }
            }

        }

        LOG.debug(String.format("Found %s 'xquery' entries.", paths.size()));

        return paths;
    }

    /**
     * Execute xquery on path
     *
     * @param broker eXist database broker
     * @param path path to query, formatted as xmldb:exist:///db/...
     */
    private void executeQuery(DBBroker broker, String path) {

        XQueryContext context = null;
        try {
            // Get path to xquery
            Source source = SourceFactory.getSource(broker, null, path, false);

            if (source == null) {
                LOG.info(String.format("No XQuery found at '%s'", path));

            } else {
                // Setup xquery service
                XQuery service = broker.getBrokerPool().getXQueryService();
                context = new XQueryContext(broker.getBrokerPool());

                // Allow use of modules with relative paths
                String moduleLoadPath = StringUtils.substringBeforeLast(path, "/");
                context.setModuleLoadPath(moduleLoadPath);

                // Compile query
                CompiledXQuery compiledQuery = service.compile(broker, context, source);

                LOG.info(String.format("Starting XQuery at '%s'", path));

                // Finish preparation
                context.prepareForExecution();

                // Execute
                Sequence result = service.execute(broker, compiledQuery, null);

                // Log results
                LOG.info(String.format("Result XQuery: '%s'", result.getStringValue()));

            }

        } catch (Throwable t) {
            // Dirty, catch it all
            LOG.error(String.format("An error occurred during preparation/execution of the XQuery script %s: %s", path, t.getMessage()), t);

        } finally {
            if (context != null) {
                context.runCleanupTasks();
            }
        }
    }

    /**
     * Create autostart collection when not existent
     *
     * @param broker The exist-db broker
     */
    private void createAutostartCollection(DBBroker broker) {

        LOG.info(String.format("Creating %s", AUTOSTART_COLLECTION));

        final TransactionManager txnManager = broker.getBrokerPool().getTransactionManager();
        try(final Txn txn = txnManager.beginTransaction()) {
            XmldbURI newCollection = XmldbURI.create(AUTOSTART_COLLECTION, true);

            // Create collection
            final Collection created = broker.getOrCreateCollection(txn, newCollection);

            // Set ownership and mode
            PermissionFactory.chown(broker, created, Optional.of(SecurityManager.SYSTEM), Optional.of(SecurityManager.DBA_GROUP));
            PermissionFactory.chmod(broker, created, Optional.of(Permission.DEFAULT_SYSTEM_SECURITY_COLLECTION_PERM), Optional.empty());

            broker.saveCollection(txn, created);
            broker.flush();

            // Commit change
            txnManager.commit(txn);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Finished creation of collection");
            }

        } catch (Throwable ex) {
            LOG.error(ex);
        }
    }

}
