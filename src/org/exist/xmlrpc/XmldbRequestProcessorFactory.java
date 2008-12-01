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
package org.exist.xmlrpc;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcRequest;
import org.apache.xmlrpc.common.XmlRpcHttpRequestConfig;
import org.apache.xmlrpc.server.RequestProcessorFactoryFactory;
import org.exist.EXistException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;

/**
 * Factory creates a new handler for each XMLRPC request. For eXist, the handler is implemented
 * by class {@link org.exist.xmlrpc.RpcConnection}. The factory is needed to make sure that each
 * RpcConnection is properly initialized.
 */
public class XmldbRequestProcessorFactory implements RequestProcessorFactoryFactory.RequestProcessorFactory {

    private final static Logger LOG = Logger.getLogger(XmldbRequestProcessorFactory.class);
    
    public final static int CHECK_INTERVAL = 2000;

    protected BrokerPool brokerPool;

    protected int connections = 0;

    protected long lastCheck = System.currentTimeMillis();

    protected QueryResultCache resultSets = new QueryResultCache();

    /** id of the database registred against the BrokerPool */
    protected String databaseid = BrokerPool.DEFAULT_INSTANCE_NAME;

    public XmldbRequestProcessorFactory(String databaseid) throws EXistException {
        if (databaseid != null && !"".equals(databaseid))
            this.databaseid = databaseid;
        brokerPool = BrokerPool.getInstance(this.databaseid);
    }

    public Object getRequestProcessor(XmlRpcRequest pRequest) throws XmlRpcException {
        checkResultSets();
        XmlRpcHttpRequestConfig config = (XmlRpcHttpRequestConfig) pRequest.getConfig();
        User user = authenticate(config.getBasicUserName(), config.getBasicPassword());
        return new RpcConnection(this, user);
    }

    protected User authenticate(String username, String password) throws XmlRpcException {
        // assume guest user if no user is specified
        // set a password for admin to permit this
        if (username == null) {
            username = "guest";
            password = "guest";
        }
        // check user
        User u = brokerPool.getSecurityManager().getUser(username);
        if (u == null)
            throw new XmlRpcException(0, "User " + username + " unknown" );
        if (!u.validate(password)) {
            LOG.debug("login denied for user " + username);
            throw new XmlRpcException(0, "Invalid password for user " + username);
        }
        return u;
    }

    protected BrokerPool getBrokerPool() {
        return brokerPool;
    }

    protected void checkResultSets() {
        if (System.currentTimeMillis() - lastCheck > CHECK_INTERVAL) {
            resultSets.checkTimestamps();
            lastCheck = System.currentTimeMillis();
        }
    }

    public synchronized void shutdown() {
        try {
            BrokerPool.stop();
        } catch (EXistException e) {
            LOG.warn("shutdown failed", e);
        }
    }
}
