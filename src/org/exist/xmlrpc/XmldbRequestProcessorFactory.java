/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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
package org.exist.xmlrpc;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcRequest;
import org.apache.xmlrpc.common.XmlRpcHttpRequestConfig;
import org.apache.xmlrpc.server.RequestProcessorFactoryFactory;
import org.exist.EXistException;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;

/**
 * Factory creates a new handler for each XMLRPC request. For eXist, the handler is implemented
 * by class {@link org.exist.xmlrpc.RpcConnection}. The factory is needed to make sure that each
 * RpcConnection is properly initialized.
 */
public class XmldbRequestProcessorFactory implements RequestProcessorFactoryFactory.RequestProcessorFactory {

    private final static Logger LOG = Logger.getLogger(XmldbRequestProcessorFactory.class);
    
    public final static int CHECK_INTERVAL = 2000;

    protected boolean useDefaultUser = true;

    protected BrokerPool brokerPool;

    protected int connections = 0;

    protected long lastCheck = System.currentTimeMillis();

    protected QueryResultCache resultSets = new QueryResultCache();

    /** id of the database registred against the BrokerPool */
    protected String databaseId = BrokerPool.DEFAULT_INSTANCE_NAME;

    public XmldbRequestProcessorFactory(final String databaseId, final boolean useDefaultUser) throws EXistException {
        this.useDefaultUser = useDefaultUser;
        if(databaseId != null &&  !databaseId.isEmpty()) {
            this.databaseId = databaseId;
        }
        brokerPool = BrokerPool.getInstance(this.databaseId);
    }

    @Override
    public Object getRequestProcessor(final XmlRpcRequest pRequest) throws XmlRpcException {
        checkResultSets();
        final XmlRpcHttpRequestConfig config = (XmlRpcHttpRequestConfig)pRequest.getConfig();
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

    protected void checkResultSets() {
        if(System.currentTimeMillis() - lastCheck > CHECK_INTERVAL) {
            resultSets.checkTimestamps();
            lastCheck = System.currentTimeMillis();
        }
    }

    public synchronized void shutdown() {
        try {
            BrokerPool.stop();
        } catch (final EXistException e) {
            LOG.warn("shutdown failed", e);
        }
    }
}
