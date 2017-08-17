/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2007-2009 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.xquery.modules.httpclient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;


/**
 * HTTPClient module
 *
 * @author   Adam Retter <adam.retter@devon.gov.uk>
 * @author   Andrzej Taramina <andrzej@chaeron.com>
 * @author   ljo
 * @version  1.3
 * @serial   20100228
 */
public class HTTPClientModule extends AbstractInternalModule
{
    private final static Logger LOG = LogManager.getLogger(HTTPClientModule.class);

    public final static String         NAMESPACE_URI                  = "http://exist-db.org/xquery/httpclient";

    public final static String         PREFIX                         = "httpclient";
    public final static String         INCLUSION_DATE                 = "2007-09-06";
    public final static String         RELEASED_IN_VERSION            = "eXist-1.2";

    public final static String         HTTP_MODULE_PERSISTENT_STATE   = "_eXist_httpclient_module_persistent_state";
    public final static String         HTTP_MODULE_PERSISTENT_OPTIONS = "_eXist_httpclient_module_persistent_options";

    final static HttpClient httpClient = setupHttpClient();


    private final static FunctionDef[] functions                      = {
        new FunctionDef( GETFunction.signatures[0], GETFunction.class ),
        new FunctionDef( GETFunction.signatures[1], GETFunction.class ),
        new FunctionDef( PUTFunction.signatures[0], PUTFunction.class ),
        new FunctionDef( PUTFunction.signatures[1], PUTFunction.class ),
        new FunctionDef( DELETEFunction.signature, DELETEFunction.class ),
        new FunctionDef( POSTFunction.signatures[0], POSTFunction.class ),
        new FunctionDef( POSTFunction.signatures[1], POSTFunction.class ),
        new FunctionDef( HEADFunction.signature, HEADFunction.class ),
        new FunctionDef( OPTIONSFunction.signature, OPTIONSFunction.class ),
        new FunctionDef( ClearFunction.signatures[0], ClearFunction.class ),
        new FunctionDef( SetOptionsFunction.signatures[0], SetOptionsFunction.class)
    };
	

    public HTTPClientModule(Map<String, List<?>> parameters)
    {
        super( functions, parameters );
    }
	

    public String getNamespaceURI()
    {
        return( NAMESPACE_URI );
    }


    public String getDefaultPrefix()
    {
        return( PREFIX );
    }


    public String getDescription()
    {
        return( "A module for performing HTTP requests as a client" );
    }


    public String getReleaseVersion()
    {
        return( RELEASED_IN_VERSION );
    }

    private static HttpClient setupHttpClient() {
        final HttpConnectionManager httpConnectionManager = new MultiThreadedHttpConnectionManager();
        final HttpClient client = new HttpClient(httpConnectionManager);

        //config from file if present
        final String configFile = System.getProperty("http.configfile");
        if(configFile != null) {
            final Path f = Paths.get(configFile);
            if(Files.exists(f)) {
                setConfigFromFile(f, client);
            } else {
                LOG.warn("http.configfile '" + f.toAbsolutePath() + "' does not exist!");
            }
        }
        
        // Legacy: set the proxy server (if any) from system properties
        final String proxyHost = System.getProperty("http.proxyHost");
        if(proxyHost != null) {
            //TODO: support for http.nonProxyHosts e.g. -Dhttp.nonProxyHosts="*.devonline.gov.uk|*.devon.gov.uk"
            final ProxyHost proxy = new ProxyHost(proxyHost, Integer.parseInt(System.getProperty("http.proxyPort")));
            client.getHostConfiguration().setProxyHost(proxy);
        }

        return client;
    }

    private static void setConfigFromFile(final Path configFile, final HttpClient http) {
        if(LOG.isDebugEnabled()) {
            LOG.debug("http.configfile='" + configFile.toAbsolutePath() + "'");
        }

        final Properties props = new Properties();
        try(final InputStream is = Files.newInputStream(configFile)) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Loading proxy settings from " + configFile.toAbsolutePath());
            }

            props.load(is);

            // Hostname / port
            final String proxyHost = props.getProperty("proxy.host");
            final int proxyPort = Integer.parseInt(props.getProperty("proxy.port", "8080"));

            // Username / password
            final String proxyUser = props.getProperty("proxy.user");
            final String proxyPassword = props.getProperty("proxy.password");

            // NTLM specifics
            String proxyDomain = props.getProperty("proxy.ntlm.domain");
            if ("NONE".equalsIgnoreCase(proxyDomain)) {
                if(LOG.isDebugEnabled()) {
                    LOG.debug("Forcing removal NTLM");
                }
                proxyDomain = null;
            }

            // Set scope
            final AuthScope authScope = new AuthScope(proxyHost, proxyPort);

            // Setup right credentials
            final Credentials credentials;
            if (proxyDomain == null) {
                credentials = new UsernamePasswordCredentials(proxyUser, proxyPassword);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Using NTLM authentication for '" + proxyDomain + "'");
                }
                credentials = new NTCredentials(proxyUser, proxyPassword, proxyHost, proxyDomain);
            }

            // Set details
            final HttpState state = http.getState();
            http.getHostConfiguration().setProxy(proxyHost, proxyPort);
            state.setProxyCredentials(authScope, credentials);

            if (LOG.isDebugEnabled()) {
                LOG.info("Set proxy: " + proxyUser + "@" + proxyHost + ":"
                        + proxyPort + (proxyDomain == null ? "" : " (NTLM:'"
                        + proxyDomain + "')"));
            }
        } catch (final IOException ex) {
            LOG.error("Failed to read proxy configuration from '" + configFile + "'");
        }
    }
}
