/*
 * Extended and slightly adopted version of the original XMLDBSource found in Apache Cocoon.
 * The original license is:
 *
 *  Copyright 1999-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *  
 *  $Id$
 */
package org.exist.cocoon;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.avalon.framework.thread.ThreadSafe;
import org.apache.cocoon.components.source.helpers.SourceCredential;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceFactory;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;

/**
 * This class implements the xmldb:// pseudo-protocol and allows to get XML
 * content from an XML:DB enabled XML database.
 * <p>
 * The configuration of this protocol is as follows:
 * <pre>
 *   &lt;source-factory name="xmldb" src="org.exist.cocoon.XMLDBSourceFactory&gt;
 *     &lt;driver type="foo" class="org.foomaker.FooXMLDBDriver"
 *             user="scott" password="tiger"
 *             collection="//localhost:8080/foo/base-path/"/&gt;
 *     &lt;driver...
 *   &lt;source-factory&gt;
 * </pre>
 * <p>
 * The <code>type</code> attribute indicates the database type that will be used for URLs (e.g.
 * <code>xmldb:foo:/path/</code>). The <code>collection</code> attribute specifies a base collection
 * for paths that do not start with "<code>//</code>".
 * <p>
 * The returned sources are traversable, modifiable and xml-izable.
 * 
 * <p>
 * This class is an import of Cocoon trunk
 * <a 
 *  href="http://svn.apache.org/viewcvs.cgi/cocoon/blocks/xmldb/trunk/java/org/apache/cocoon/components/source/impl/XMLDBSourceFactory.java?rev=349157&view=markup"
 * >XMLDBSourceFactory</a>, to ensure connexion with a modified XMLDBSource.
 * </p>
 *  
 * @author cziegeler
 * @version $Id$
 */
public final class XMLDBSourceFactory extends AbstractLogEnabled
                                      implements SourceFactory, Configurable, ThreadSafe {

    /** A Map containing the authentication credentials */
    protected HashMap credentialMap;
    
    /** An optional base collection for each of the drivers */
    protected HashMap baseMap;

    /**
     * Configure the instance and initialize XML:DB connections (load and register the drivers).
     */
    public void configure(final Configuration conf)
    throws ConfigurationException {

        credentialMap = new HashMap();
        baseMap = new HashMap();

        Configuration[] drivers = conf.getChildren("driver");
        for (int i = 0; i < drivers.length; i++) {
            String type = drivers[i].getAttribute("type");
            String driver = drivers[i].getAttribute("class");

            SourceCredential credential = new SourceCredential(null, null);
            credential.setPrincipal(drivers[i].getAttribute("user", null));
            credential.setPassword(drivers[i].getAttribute("password", null));
            credentialMap.put(type, credential);
            
            String base = drivers[i].getAttribute("collection", null);
            if (base != null && base.length() > 0) {
                // Ensure the base collection ends with a '/'
                if (base.charAt(base.length() -  1) != '/') {
                    base = base + '/';
                }
                baseMap.put(type, base);
            }

            if (getLogger().isDebugEnabled()) {
                getLogger().debug("Initializing XML:DB connection, using driver " + driver);
            }

            try {
                Database db = (Database)Class.forName(driver).newInstance();
                
                Configuration[] params = drivers[i].getChildren();
                for (int j = 0; j < params.length; j++) {
                    db.setProperty(params[j].getName(), params[j].getValue());
                }

                DatabaseManager.registerDatabase(db);

            } catch (XMLDBException e) {
                String msg = "Unable to connect to the XMLDB database '" + type + "'." +
                             " Error " + e.errorCode + ": " + e.getMessage();
                getLogger().debug(msg, e);
                throw new ConfigurationException(msg, e);

            } catch (Exception e) {
                String msg = "Unable to load XMLDB database driver '" + driver + "'." +
                             " Make sure that the driver is available. Error: " + e.getMessage();
                getLogger().debug(msg, e);
                throw new ConfigurationException(msg, e);
            }
        }
    }

    /**
     * Resolve the source
     */
    public Source getSource(String location, Map parameters)
    throws MalformedURLException, IOException {

        int start = location.indexOf(':') + 1;
        int end = location.indexOf(':', start);

        if (start == 0 || end == -1) {
            throw new MalformedURLException("Mispelled XML:DB URL. " +
                                            "The syntax is \"xmldb:databasetype://host/collection/resource\"");
        }

        String type = location.substring(start, end);
        SourceCredential credential = (SourceCredential)credentialMap.get(type);
        
        if (credential == null) {
            throw new MalformedURLException("xmldb type '" + type + "' is unknown for URL " + location);
        }
        
        String base = (String)baseMap.get(type);

        if (base != null && base.length() > 0) {
            String path = location.substring(end+1);
            if (!path.startsWith("//")) {
                // URL is not absolute, add base, avoiding to double the '/'
                if (path.charAt(0) == '/') {
                    path = path.substring(1);
                }
                location = location.substring(0, end + 1) + base + path;
            }
        }

        return new XMLDBSource(this.getLogger(), credential.getPrincipal(), credential.getPassword(), location);
    }

    public void release(org.apache.excalibur.source.Source source) {
        // nothing to do here
    }
}
