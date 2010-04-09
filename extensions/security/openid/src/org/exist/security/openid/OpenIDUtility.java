/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 *
 *  $Id:$
 */
package org.exist.security.openid;

import org.apache.log4j.Logger;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.User;
import org.exist.security.xacml.AccessContext;
import org.exist.source.Source;
import org.exist.source.DBSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XQueryService;

import java.util.Properties;

/**
 *
 */
public class OpenIDUtility {

    private final static Logger LOG = Logger.getLogger(OpenIDUtility.class);
    private static final String REGISTER_XQUERY_SCRIPT_PROPERTY = "org.exist.security.openid.verify_logging_script";

    /**
     * Executes an XQuery script whose filename is retrieved from the
     * java option 'org.exist.security.openid.verify_logging_script'.
     *
     * If the java option is not set, then log that fact and then return.
     *
     * If the java option is set, then retrieve the script from the file
     * or resource designated by the value of the property.  Execute the
     * XQuery script executed with the context of the given principal.
     *
     * @param principal The OpenID user to be registered in the database.
     * @return true if the resource exists and the script successfully executed.
     */
    public static boolean registerUser(User principal) {

        if (principal == null) {
            LOG.error("No principal value exists.  Returning with no actions performed.");
            return false;
        }

        String userInfo = "registerUser: [" + principal.getAttribute("id") + ", ";
        for (String name : principal.getAttributeNames()) {
            userInfo += name +"(" + principal.getAttribute(name) + "), ";
        }
        userInfo += "]";
        LOG.info(userInfo);

        String xqueryResourcePath = System.getProperty(REGISTER_XQUERY_SCRIPT_PROPERTY);

        if (xqueryResourcePath == null || xqueryResourcePath.length() <= 0) {
            LOG.info("no property set for " + REGISTER_XQUERY_SCRIPT_PROPERTY);
            return true;
        }
        xqueryResourcePath = xqueryResourcePath.trim();
        LOG.info("org.exist.security.openid.verify_logging_script = \"" + xqueryResourcePath + "\"");
        
        BrokerPool pool = null;
        DBBroker broker = null;

        try {
            DocumentImpl resource = null;
            Source source = null;

            pool = BrokerPool.getInstance();

            broker = pool.get(principal);
            if (broker == null) {
                LOG.error("Unable to retrieve DBBroker for " + principal.getAttribute("id"));
                return false;
            }

            XmldbURI pathUri = XmldbURI.create(xqueryResourcePath);


            resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);

            if(resource != null) {
                LOG.info("Resource " + xqueryResourcePath + " exists.");
                source = new DBSource(broker, (BinaryDocument)resource, true);
            } else {
                LOG.info("Resource " + xqueryResourcePath + " does not exist.");
                LOG.info("pathURI " + pathUri );
                return true;
            }


            XQuery xquery = broker.getXQueryService();

            if (xquery == null) {
                LOG.error("broker unable to retrieve XQueryService");
                return false;
            }

            XQueryContext context = xquery.newContext(AccessContext.REST);

            CompiledXQuery compiled = xquery.compile(context, source);

            Properties outputProperties = new Properties();

            Sequence result = xquery.execute(compiled, null, outputProperties);
            LOG.info("XQuery execution results: " + result.toString());

        } catch (Exception e) {
            LOG.error("Exception while executing OpenID registration script for " + principal.getAttribute("id"), e);
            return false;
        }
        finally {
            if (pool != null)
                pool.release(broker);
        }
        return true;
    }
}