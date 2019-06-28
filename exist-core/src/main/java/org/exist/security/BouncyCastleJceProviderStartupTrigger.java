/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
package org.exist.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.exist.storage.DBBroker;
import org.exist.storage.StartupTrigger;
import org.exist.storage.txn.Txn;

import java.util.List;
import java.util.Map;

/**
 * Startup Trigger to register the Bouncy Castle JCE Provider
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class BouncyCastleJceProviderStartupTrigger implements StartupTrigger {

    private final static Logger LOG = LogManager.getLogger(
        BouncyCastleJceProviderStartupTrigger.class);

    @Override
    public void execute(final DBBroker sysBroker, final Txn transaction,
            final Map<String, List<? extends Object>> params) {

      java.security.Security.addProvider(new BouncyCastleProvider());

      LOG.info("Registered JCE Security Provider: org.bouncycastle.jce.provider.BouncyCastleProvider");
    }
}
