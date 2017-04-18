/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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
 *  $Id$
 */
package org.exist.security.realm.openid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.SchemaType;
import org.exist.security.Subject;
import org.exist.source.Source;
import org.exist.source.DBSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

import java.util.Optional;
import java.util.Properties;
import org.exist.security.AXSchemaType;

/**
 *
 */
public class OpenIDUtility {

    private final static Logger LOG = LogManager.getLogger(OpenIDUtility.class);
    private static final String REGISTER_XQUERY_SCRIPT_PROPERTY = "org.exist.security.openid.verify_logging_script";

    //TODO: implement this at eXist's security core 
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
    public static boolean registerUser(Subject principal) {

        if (principal == null) {
            LOG.error("No principal value exists.  Returning with no actions performed.");
            return false;
        }

        String userInfo = "registerUser: [" + principal.getMetadataValue(AXSchemaType.ALIAS_USERNAME) + ", ";
        for(SchemaType metadataKey : principal.getMetadataKeys()) {
            userInfo += metadataKey.getNamespace() +"(" + principal.getMetadataValue(metadataKey) + "), ";
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

        try {
            DocumentImpl resource = null;
            Source source = null;

            pool = BrokerPool.getInstance();

            try(final DBBroker broker = pool.get(Optional.of(principal))) {
                if (broker == null) {
                    LOG.error("Unable to retrieve DBBroker for " + principal.getMetadataValue(AXSchemaType.ALIAS_USERNAME));
                    return false;
                }

                XmldbURI pathUri = XmldbURI.create(xqueryResourcePath);


                resource = broker.getXMLResource(pathUri, LockMode.READ_LOCK);

                if (resource != null) {
                    LOG.info("Resource " + xqueryResourcePath + " exists.");
                    source = new DBSource(broker, (BinaryDocument) resource, true);
                } else {
                    LOG.info("Resource " + xqueryResourcePath + " does not exist.");
                    LOG.info("pathURI " + pathUri);
                    return true;
                }


                XQuery xquery = pool.getXQueryService();

                if (xquery == null) {
                    LOG.error("broker unable to retrieve XQueryService");
                    return false;
                }

                XQueryContext context = new XQueryContext(broker.getBrokerPool());

                CompiledXQuery compiled = xquery.compile(broker, context, source);

                Properties outputProperties = new Properties();

                Sequence result = xquery.execute(broker, compiled, null, outputProperties);
                LOG.info("XQuery execution results: " + result.toString());
            } finally {
                if(resource != null) {
                    resource.getUpdateLock().release(LockMode.READ_LOCK);
                }
            }
        } catch (Exception e) {
            LOG.error("Exception while executing OpenID registration script for " + principal.getMetadataValue(AXSchemaType.ALIAS_USERNAME), e);
            return false;
        }
        return true;
    }
}