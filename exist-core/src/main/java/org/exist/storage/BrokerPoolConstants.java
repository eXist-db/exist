/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2003-2016 The eXist-db Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage;

/**
 * Just static Constants used by {@link BrokerPool}
 *
 * We keep these here to reduce the visual
 * complexity of the BrokerPool class
 */
public interface BrokerPoolConstants {

    //on-start, ready, go
    /*** initializing sub-components */
    String SIGNAL_STARTUP = "startup";
    /*** ready for recovery &amp; read-only operations */
    String SIGNAL_READINESS = "ready";
    /*** ready for writable operations */
    String SIGNAL_WRITABLE = "writable";
    /*** ready for writable operations */
    String SIGNAL_STARTED = "started";
    /*** running shutdown sequence */
    String SIGNAL_SHUTDOWN = "shutdown";
    /*** recovery aborted, db stopped */
    String SIGNAL_ABORTED = "aborted";

    String CONFIGURATION_CONNECTION_ELEMENT_NAME = "db-connection";
    String CONFIGURATION_STARTUP_ELEMENT_NAME = "startup";
    String CONFIGURATION_POOL_ELEMENT_NAME = "pool";
    String CONFIGURATION_SECURITY_ELEMENT_NAME = "security";
    String CONFIGURATION_RECOVERY_ELEMENT_NAME = "recovery";
    String DISK_SPACE_MIN_ATTRIBUTE = "minDiskSpace";

    String DATA_DIR_ATTRIBUTE = "files";

    //TODO : move elsewhere ?
    String RECOVERY_ENABLED_ATTRIBUTE = "enabled";
    String RECOVERY_POST_RECOVERY_CHECK = "consistency-check";

    //TODO : move elsewhere ?
    String COLLECTION_CACHE_SIZE_ATTRIBUTE = "collectionCacheSize";
    String MIN_CONNECTIONS_ATTRIBUTE = "min";
    String MAX_CONNECTIONS_ATTRIBUTE = "max";
    String SYNC_PERIOD_ATTRIBUTE = "sync-period";
    String SHUTDOWN_DELAY_ATTRIBUTE = "wait-before-shutdown";
    String NODES_BUFFER_ATTRIBUTE = "nodesBuffer";

    //Various configuration property keys (set by the configuration manager)
    String PROPERTY_STARTUP_TRIGGERS = "startup.triggers";
    String PROPERTY_DATA_DIR = "db-connection.data-dir";
    String PROPERTY_MIN_CONNECTIONS = "db-connection.pool.min";
    String PROPERTY_MAX_CONNECTIONS = "db-connection.pool.max";
    String PROPERTY_SYNC_PERIOD = "db-connection.pool.sync-period";
    String PROPERTY_SHUTDOWN_DELAY = "wait-before-shutdown";
    String DISK_SPACE_MIN_PROPERTY = "db-connection.diskSpaceMin";

    //TODO : move elsewhere ?
    String PROPERTY_COLLECTION_CACHE_SIZE = "db-connection.collection-cache-size";

    //TODO : move elsewhere ? Get fully qualified class name ?
    String DEFAULT_SECURITY_CLASS = "org.exist.security.internal.SecurityManagerImpl";
    String PROPERTY_SECURITY_CLASS = "db-connection.security.class";
    String PROPERTY_RECOVERY_ENABLED = "db-connection.recovery.enabled";
    String PROPERTY_RECOVERY_CHECK = "db-connection.recovery.consistency-check";
    String PROPERTY_SYSTEM_TASK_CONFIG = "db-connection.system-task-config";
    String PROPERTY_NODES_BUFFER = "db-connection.nodes-buffer";
    String PROPERTY_EXPORT_ONLY = "db-connection.emergency";

    String PROPERTY_RECOVERY_GROUP_COMMIT = "db-connection.recovery.group-commit";
    String RECOVERY_GROUP_COMMIT_ATTRIBUTE = "group-commit";
    String PROPERTY_RECOVERY_FORCE_RESTART = "db-connection.recovery.force-restart";
    String RECOVERY_FORCE_RESTART_ATTRIBUTE = "force-restart";

    String DOC_ID_MODE_ATTRIBUTE = "doc-ids";
    String DOC_ID_MODE_PROPERTY = "db-connection.doc-ids.mode";

    String PROPERTY_PAGE_SIZE = "db-connection.page-size";

    /**
     * Default values
     */
    long DEFAULT_SYNCH_PERIOD = 120000;
    long DEFAULT_MAX_SHUTDOWN_WAIT = 45000;
    //TODO : move this default setting to org.exist.collections.CollectionCache ?
    int DEFAULT_COLLECTION_BUFFER_SIZE = 64;
    int DEFAULT_PAGE_SIZE = 4096;
    short DEFAULT_DISK_SPACE_MIN = 64; // 64 MB
}
