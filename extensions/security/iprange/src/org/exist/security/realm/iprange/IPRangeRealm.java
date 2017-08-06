/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2017 The eXist Project
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
package org.exist.security.realm.iprange;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.security.*;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.security.internal.SubjectAccreditedImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.Properties;

/**
 * @author <a href="mailto:wshager@gmail.com">Wouter Hager</a>
 */
@ConfigurationClass("realm") //TODO: id = IPRange
public class IPRangeRealm extends AbstractRealm {

    @ConfigurationFieldAsAttribute("id")
    public final static String ID = "IPRange";

    @ConfigurationFieldAsAttribute("version")
    public final static String version = "1.0";

    private final static Logger LOG = LogManager.getLogger(IPRangeRealm.class);
    private static IPRangeRealm instance = null;

    public IPRangeRealm(final SecurityManagerImpl sm, final Configuration config) throws ConfigurationException {
        super(sm, config);
        instance = this;
    }

    static IPRangeRealm getInstance(){
        return instance;
    }

    private static long ipToLong(final InetAddress ip) {
        final byte[] octets = ip.getAddress();
        long result = 0;
        for (final byte octet : octets) {
            result <<= 8;
            result |= octet & 0xff;
        }
        return result;
    }

	/*@Override
    public void start(DBBroker broker) throws EXistException {
		super.start(broker);
	}*/

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean deleteAccount(final Account account) throws PermissionDeniedException, EXistException, ConfigurationException {
        // Auto-generated method stub
        return false;
    }

    @Override
    public boolean deleteGroup(final Group group) throws PermissionDeniedException, EXistException, ConfigurationException {
        // Auto-generated method stub
        return false;
    }

    @Override
    public Subject authenticate(final String ipAddress, final Object credentials) throws AuthenticationException {

        // Elevaste to system privileges
        try (final DBBroker broker = getSecurityManager().database().get(Optional.of(getSecurityManager().getSystemSubject()))) {

            // Convert IP address
            final long ipToTest = ipToLong(InetAddress.getByName(ipAddress));

            // Get xquery service
            final XQuery queryService = broker.getBrokerPool().getXQueryService();
            if (queryService == null) {
                LOG.error("IPRange broker unable to retrieve XQueryService");
                return null;
            }

            // Construct XQuery
            final String query = "collection('/db/system/security/iprange/accounts')/account/" +
                    "iprange[" + ipToTest + " ge number(start) and " + ipToTest + " le number(end)]/../name";
            final XQueryContext context = new XQueryContext(broker.getBrokerPool());

            final CompiledXQuery compiled = queryService.compile(broker, context, query);

            final Properties outputProperties = new Properties();

            // Execute xQuery
            final Sequence result = queryService.execute(broker, compiled, null, outputProperties);
            final SequenceIterator i = result.iterate();

            // Get FIRST username when present
            final String username = i.hasNext() ? i.nextItem().getStringValue() : "";

            if (i.hasNext()) {
                LOG.warn("IP address " + ipAddress + " matched multiple ipranges. Using first result only.");
            }

            if (!username.isEmpty()) {
                final Account account = getSecurityManager().getAccount(username);
                if (account != null) {
                    LOG.info("IPRangeRealm trying " + account.getName());
                    return new SubjectAccreditedImpl((AbstractAccount) account, ipAddress);
                } else {
                    LOG.info("IPRangeRealm couldn't resolve account for " + username);
                }

            } else {
                LOG.info("IPRangeRealm xquery found no matches");
            }
            return null;

        } catch (final EXistException | UnknownHostException | XPathException | PermissionDeniedException e) {
            throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage());
        }
    }
}
