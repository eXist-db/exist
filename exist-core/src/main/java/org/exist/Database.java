/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.exist;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Observer;
import java.util.Optional;

import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.triggers.CollectionTrigger;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.TriggerProxy;
import org.exist.debuggee.Debuggee;
import org.exist.dom.persistent.SymbolTable;
import org.exist.indexing.IndexManager;
import org.exist.numbering.NodeIdFactory;
import org.exist.plugin.PluginsManager;
import org.exist.scheduler.Scheduler;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.CacheManager;
import org.exist.storage.DBBroker;
import org.exist.storage.NotificationService;
import org.exist.storage.ProcessMonitor;
import org.exist.storage.blob.BlobStore;
import org.exist.storage.journal.JournalManager;
import org.exist.storage.txn.TransactionManager;
import org.exist.util.Configuration;
import org.exist.xquery.PerformanceStats;

/**
 * Database controller, all operation synchronized by this instance. (singleton)
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public interface Database {

    // TODO: javadocs

    public String getId();

    ThreadGroup getThreadGroup();

    /**
     * 
     * @return SecurityManager
     */
    public SecurityManager getSecurityManager();

    /**
     * 
     * @return IndexManager
     */
    public IndexManager getIndexManager();

    /**
     * 
     * @return TransactionManager
     */
    public TransactionManager getTransactionManager();

    /**
     * Get the database Journal Manager.
     *
     * @return the Journal Manager
     */
    Optional<JournalManager> getJournalManager();

    /**
     * 
     * @return CacheManager
     */
    public CacheManager getCacheManager();

    /**
     * 
     * @return Scheduler
     */
    public Scheduler getScheduler();

    /**
	 * 
	 */
    public void shutdown();

    // TODO: remove 'throws EXistException'?
    public DBBroker getBroker() throws EXistException; 

    public DBBroker authenticate(String username, Object credentials) throws AuthenticationException;

    /*
     * 
     * try { 
     *     broker = database.authenticate(account, credentials);
     * 
     *     broker1 = database.get(); 
     *     broker2 = database.get(); 
     *     ... 
     *     brokerN = database.get();
     * 
     * } finally { 
     *     database.release(broker);
     * }
     */
    public DBBroker get(Optional<Subject> subject) throws EXistException;

    public DBBroker getActiveBroker(); // throws EXistException;

    /**
     * Returns the number of brokers currently serving requests for the database
     * instance.
     * 
     * @return The brokers count
     */
    public int countActiveBrokers();

    /**
     * 
     * @return Debuggee
     */
    public Debuggee getDebuggee();

    public PerformanceStats getPerformanceStats();

    // old configuration
    public Configuration getConfiguration();

    public NodeIdFactory getNodeFactory();

    public Path getStoragePlace();

    public CollectionConfigurationManager getConfigurationManager();

    /**
     * Master document triggers.
     *
     * @return the document triggers.
     */
    public Collection<TriggerProxy<? extends DocumentTrigger>> getDocumentTriggers();

    // public DocumentTrigger getDocumentTrigger();

    /**
     * Master Collection triggers.
     *
     * @return the collection triggers.
     */
    public Collection<TriggerProxy<? extends CollectionTrigger>> getCollectionTriggers();

    // public CollectionTrigger getCollectionTrigger();

    public void registerDocumentTrigger(Class<? extends DocumentTrigger> clazz);

    public void registerCollectionTrigger(Class<? extends CollectionTrigger> clazz);

    public ProcessMonitor getProcessMonitor();

    public boolean isReadOnly();

    public NotificationService getNotificationService();

    public PluginsManager getPluginsManager();

    public BlobStore getBlobStore();

    public SymbolTable getSymbols();

    void addStatusObserver(final Observer statusObserver);
    boolean removeStatusObserver(final Observer statusObserver);

}
