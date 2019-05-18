/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
package org.exist.xmlrpc;

import com.evolvedbinary.j8fu.lazy.AtomicLazyVal;
import com.evolvedbinary.j8fu.lazy.LazyVal;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcRequest;
import org.apache.xmlrpc.common.XmlRpcHttpRequestConfig;
import org.apache.xmlrpc.server.RequestProcessorFactoryFactory;
import org.exist.EXistException;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.util.NamedThreadFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Factory creates a new handler for each XMLRPC request. For eXist, the handler is implemented
 * by class {@link org.exist.xmlrpc.RpcConnection}. The factory is needed to make sure that each
 * RpcConnection is properly initialized.
 */
public class XmldbRequestProcessorFactory implements RequestProcessorFactoryFactory.RequestProcessorFactory {

    private final static Logger LOG = LogManager.getLogger(XmldbRequestProcessorFactory.class);
    public final static int CHECK_INTERVAL = 2000;

    private final boolean useDefaultUser;
    private final BrokerPool brokerPool;
    protected final QueryResultCache resultSets = new QueryResultCache();

    protected final AtomicLazyVal<ExecutorService> restoreExecutorService;
    protected final Map<UUID, Tuple2<RpcConnection.BufferingRestoreListener, Future<Void>>> restoreTasks = new ConcurrentHashMap<>();

    /**
     * id of the database registered against the BrokerPool
     */
    protected String databaseId = BrokerPool.DEFAULT_INSTANCE_NAME;

    public XmldbRequestProcessorFactory(final String databaseId, final boolean useDefaultUser) throws EXistException {
        this.useDefaultUser = useDefaultUser;
        if (databaseId != null && !databaseId.isEmpty()) {
            this.databaseId = databaseId;
        }
        this.brokerPool = BrokerPool.getInstance(this.databaseId);
        this.restoreExecutorService = new AtomicLazyVal<>(() -> Executors.newCachedThreadPool(new NamedThreadFactory(brokerPool, "rpc-db-restore")));
    }

    @Override
    public Object getRequestProcessor(final XmlRpcRequest pRequest) throws XmlRpcException {
        final XmlRpcHttpRequestConfig config = (XmlRpcHttpRequestConfig) pRequest.getConfig();
        final Subject user = authenticate(config.getBasicUserName(), config.getBasicPassword());
        return new RpcConnection(this, user);
    }

    protected Subject authenticate(String username, String password) throws XmlRpcException {
        // assume guest user if no user is specified
        // set a password for admin to permit this
        if (username == null) {
            username = SecurityManager.GUEST_USER;
            password = username;
        }

        if (!useDefaultUser && username.equalsIgnoreCase(SecurityManager.GUEST_USER)) {
            final String message = "The user " + SecurityManager.GUEST_USER + " is prohibited from logging in through XML-RPC.";
            LOG.debug(message);
            throw new XmlRpcException(0, message);
        }
        // check user
        try {
            return brokerPool.getSecurityManager().authenticate(username, password);
        } catch (final AuthenticationException e) {
            LOG.debug(e.getMessage());
            throw new XmlRpcException(0, e.getMessage());
        }
    }

    protected BrokerPool getBrokerPool() {
        return brokerPool;
    }

    public synchronized void shutdown() {
        brokerPool.shutdown();
    }
}
