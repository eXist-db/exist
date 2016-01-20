/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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
package org.exist.security.realm.iprange;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;
import java.util.Optional;
import java.net.*;

import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.Configurator;
import org.exist.config.annotation.*;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.AbstractAccount;
import org.exist.security.AbstractRealm;
import org.exist.security.Account;
import org.exist.security.AuthenticationException;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SchemaType;
import org.exist.security.Subject;
import org.exist.security.internal.HttpSessionAuthentication;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.security.internal.SubjectAccreditedImpl;
import org.exist.security.internal.aider.UserAider;
import org.exist.security.xacml.AccessContext;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.XPathException;

/**
 * @author <a href="mailto:wshager@gmail.com">Wouter Hager</a>
 *
 */
@ConfigurationClass("realm") //TODO: id = IPRange
public class IPRangeRealm extends AbstractRealm {
	protected final static Logger LOG = LogManager.getLogger(IPRangeRealm.class);
	
	protected static IPRangeRealm instance = null;
	
	@ConfigurationFieldAsAttribute("id")
	public final static String ID = "IPRange";
	
	@ConfigurationFieldAsAttribute("version")
	public final static String version = "1.0";
	
	public IPRangeRealm(final SecurityManagerImpl sm, Configuration config) throws ConfigurationException {
		super(sm, config);
		instance = this;
	}

	@Override
	public String getId() {
		return ID;
	}
	
	/*@Override
	public void start(DBBroker broker) throws EXistException {
		super.start(broker);
	}*/
	
	@Override
	public boolean deleteAccount(Account account) throws PermissionDeniedException, EXistException, ConfigurationException {
		// Auto-generated method stub
		return false;
	}

	@Override
	public boolean deleteGroup(Group group) throws PermissionDeniedException, EXistException, ConfigurationException {
		// Auto-generated method stub
		return false;
	}
	
	@Override
	public Subject authenticate(final String ip, Object credentials) throws AuthenticationException {
		//elevate to system privs
		try(final DBBroker broker = BrokerPool.getInstance().get(Optional.of(getSecurityManager().getSystemSubject()))) {
			long ipToTest = ipToLong(InetAddress.getByName(ip));
			
			XQuery xquery = broker.getBrokerPool().getXQueryService();

			if (xquery == null) {
				LOG.error("IPRange broker unable to retrieve XQueryService");
			}
			
			String query = "collection('/db/system/security/IPRange/accounts')/account/iprange[number(start) ge " + ipToTest + " and number(end) le " + ipToTest + "]/../name";
			
			XQueryContext context = new XQueryContext(broker.getBrokerPool(), AccessContext.REST);

			CompiledXQuery compiled = xquery.compile(broker, context, query);

			Properties outputProperties = new Properties();

			Sequence result = xquery.execute(broker, compiled, null, outputProperties);
			SequenceIterator i = result.iterate();
			String username = "";
			if(i.hasNext()) {
				username = i.nextItem().getStringValue();
			}
			Account account = null;
			if(username!="") {
				account = getSecurityManager().getAccount(username);
				if(account != null){
					LOG.info("IPRangeRealm trying "+account.getName());
					return new SubjectAccreditedImpl((AbstractAccount) account,ip);
				} else {
					LOG.info("IPRangeRealm couldn't resolve account for "+username);
				}
			} else {
				LOG.info("IPRangeRealm xquery found no matches");
			}
			return null;
		} catch (EXistException e) {
			throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage());
		} catch (PermissionDeniedException e) {
			throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage());
		} catch (XPathException e) {
			throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage());
		} catch (UnknownHostException e) {
			throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage());
		}
	}
	
	private static long ipToLong(InetAddress ip) {
		byte[] octets = ip.getAddress();
		long result = 0;
		for (byte octet : octets) {
			result <<= 8;
			result |= octet & 0xff;
		}
		return result;
	}
}
