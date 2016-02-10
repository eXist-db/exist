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
package org.exist.util;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *  Helper class for accepting self-signed SSL certificates.
 * 
 * @author Dannes Wessels
 */
public class SSLHelper {

    private final static Logger LOG = LogManager.getLogger(SSLHelper.class);
    private static TrustManager[] nonvalidatingTrustManager = null;
    private static HostnameVerifier dummyHostnameVerifier = null;

    private SSLHelper() {
        // No
    }

    private static void createTrustManager() {

        if (nonvalidatingTrustManager == null) {
            
            // Create trust manager that does not validate certificate chains
            nonvalidatingTrustManager = new TrustManager[]{
                new X509TrustManager() {

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Always trust
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Alway trust
                    }
                }
            };

        }
    }

    private static void createHostnameVerifier() {

        if (dummyHostnameVerifier == null) {
            
            // Create dummy HostnameVerifier
            dummyHostnameVerifier = (hostname, session) -> true;
        }
    }

    /**
     *  Initialize HttpsURLConnection with (optionally) a non validating SSL 
     * trust manager and (optionally) a dummy hostname verifier.
     * 
     * @param sslAllowSelfsigned    Set to TRUE to allow selfsigned certificates
     * @param sslVerifyHostname     Set to FALSE for not verifying hostnames.
     * @return TRUE if initialization was OK, else FALSE
     */
    public static boolean initialize(boolean sslAllowSelfsigned, boolean sslVerifyHostname) {

        // Set it up
        createTrustManager();
        createHostnameVerifier();

        SSLContext sc = null;
        try {
            sc = SSLContext.getInstance("SSL");
        } catch (final NoSuchAlgorithmException ex) {
            LOG.error("Unable to initialize SSL.", ex);
            return false;
        }


        // Set accept of selfsigned certificates
        if (sslAllowSelfsigned) {
            try {
                // Install the all-trusting trust manager
                LOG.debug("Installing SSL trust manager");
                sc.init(null, nonvalidatingTrustManager, new java.security.SecureRandom());

            } catch (final KeyManagementException ex) {
                LOG.error("Unable to initialize keychain validation.", ex);
                return false;
            }
        }


        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        // Set dummy hostname verifier
        if (!sslVerifyHostname) {
            LOG.debug("Registering hostname verifier");
            HttpsURLConnection.setDefaultHostnameVerifier(dummyHostnameVerifier);
        }

        return true;

    }

    /**
     *  Initialize HttpsURLConnection with  a non validating SSL 
     * trust manager and a dummy hostname verifier. Note that this makes
     * the SSL connection less secure!
     * 
     * @return TRUE if initialization was OK, else FALSE
     */
    public static boolean initialize() {
        return initialize(true, false);
    }
}
