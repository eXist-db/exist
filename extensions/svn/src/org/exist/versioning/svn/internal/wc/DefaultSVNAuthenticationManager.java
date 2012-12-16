/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.exist.versioning.svn.internal.wc;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.net.ssl.TrustManager;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.wc.SVNWCUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.ISVNProxyManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.wc.ISVNAuthStoreHandler;
import org.tmatesoft.svn.core.internal.wc.ISVNAuthenticationStorage;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class DefaultSVNAuthenticationManager implements ISVNAuthenticationManager {

    private boolean myIsStoreAuth;
    private ISVNAuthenticationProvider[] myProviders;
    private File myConfigDirectory;
    
    private SVNAuthentication myPreviousAuthentication;
    private SVNErrorMessage myPreviousErrorMessage;
    private SVNCompositeConfigFile myServersFile;
    private ISVNAuthenticationStorage myRuntimeAuthStorage;
    private int myLastProviderIndex;
    private SVNCompositeConfigFile myConfigFile;
    private boolean myIsAuthenticationForced;
    private SVNAuthentication myLastLoadedAuth;
    private ISVNAuthStoreHandler myAuthStoreHandler;
    private Map myServersOptions;
    private Map myConfigOptions;
    
    public DefaultSVNAuthenticationManager(File configDirectory, boolean storeAuth, String userName, String password) {
        this(configDirectory, storeAuth, userName, password, null, null);
    }

    public DefaultSVNAuthenticationManager(File configDirectory, boolean storeAuth, String userName, String password, File privateKey, String passphrase) {
        //password = password == null ? "" : password;

        myIsStoreAuth = storeAuth;
        myConfigDirectory = configDirectory;
        if (myConfigDirectory == null) {
            myConfigDirectory = SVNWCUtil.getDefaultConfigurationDirectory();
        }   
        
        myProviders = new ISVNAuthenticationProvider[4];
        myProviders[0] = createDefaultAuthenticationProvider(userName, password, privateKey, passphrase, myIsStoreAuth);
        myProviders[1] = createRuntimeAuthenticationProvider();
        myProviders[2] = createCacheAuthenticationProvider(new Resource(myConfigDirectory, "auth"), userName);
    }

    public void setAuthStoreHandler(ISVNAuthStoreHandler authStoreHandler) {
        myAuthStoreHandler = authStoreHandler;
    }
    
    public void setInMemoryServersOptions(Map serversOptions) {
        myServersOptions = serversOptions;
    }

    public void setInMemoryConfigOptions(Map configOptions) {
        myConfigOptions = configOptions;
    }

    public void setAuthenticationProvider(ISVNAuthenticationProvider provider) {
        // add provider to list
        myProviders[3] = provider; 
    }

    public Collection getAuthTypes(SVNURL url) {
        List schemes = new ArrayList();

        String host = url.getHost();
        Map hostProperties = getHostProperties(host);
        String authTypes = (String) hostProperties.get("http-auth-types");
        if (authTypes == null || "".equals(authTypes.trim())) {
            return schemes;
        }
        
        for(StringTokenizer tokens = new StringTokenizer(authTypes, ";"); tokens.hasMoreTokens();) {
            String scheme = tokens.nextToken();
            if (!schemes.contains(scheme)) {
                schemes.add(scheme);
            }
        }
        return schemes;
    }
    
    public ISVNProxyManager getProxyManager(SVNURL url) throws SVNException {
        String host = url.getHost();
        
        Map properties = getHostProperties(host);
        String proxyHost = (String) properties.get("http-proxy-host");
        if (proxyHost == null || "".equals(proxyHost.trim())) {
            proxyHost = System.getProperty("http.proxyHost");
            properties.put("http-proxy-port", System.getProperty("http.proxyPort"));
        }
        if (proxyHost == null || "".equals(proxyHost.trim())) {
            return null;
        }
        String proxyExceptions = (String) properties.get("http-proxy-exceptions");
        String proxyExceptionsSeparator = ",";
        if (proxyExceptions == null) {
            proxyExceptions = System.getProperty("http.nonProxyHosts");
            proxyExceptionsSeparator = "|";
        }
        if (proxyExceptions != null) {
          for(StringTokenizer exceptions = new StringTokenizer(proxyExceptions, proxyExceptionsSeparator); exceptions.hasMoreTokens();) {
              String exception = exceptions.nextToken().trim();
              if (DefaultSVNOptions.matches(exception, host)) {
                  return null;
              }
          }
        }
        String proxyPort = (String) properties.get("http-proxy-port");
        String proxyUser = (String) properties.get("http-proxy-username");
        String proxyPassword = (String) properties.get("http-proxy-password");
        return new SimpleProxyManager(proxyHost, proxyPort, proxyUser, proxyPassword);
    }

	public TrustManager getTrustManager(SVNURL url) throws SVNException {
		String host = url.getHost();

		Map properties = getHostProperties(host);
		boolean trustAll = !"no".equalsIgnoreCase((String) properties.get("ssl-trust-default-ca")); // jdk keystore
		String sslAuthorityFiles = (String) properties.get("ssl-authority-files"); // "pem" files
		Collection trustStorages = new ArrayList();
		if (sslAuthorityFiles != null) {
		    for(StringTokenizer files = new StringTokenizer(sslAuthorityFiles, ";"); files.hasMoreTokens();) {
		        String fileName = files.nextToken();
                fileName = fileName == null ? null : fileName.trim();
		        if (fileName != null && !"".equals(fileName)) {
		            trustStorages.add(new Resource(fileName));
		        }
		    }
		}
		File[] serverCertFiles = (File[]) trustStorages.toArray(new Resource[trustStorages.size()]);
		File authDir = new Resource(myConfigDirectory, "auth/svn.ssl.server");
		return new DefaultSVNSSLTrustManager(authDir, url, serverCertFiles, trustAll, this);
	}

    private Map getHostProperties(String host) {
        Map globalProps = getServersFile().getProperties("global");
        String groupName = getGroupName(getServersFile().getProperties("groups"), host);
        if (groupName != null) {
            Map hostProps = getServersFile().getProperties(groupName);
            globalProps.putAll(hostProps);
        }
        return globalProps;
    }

    public SVNAuthentication getFirstAuthentication(String kind, String realm, SVNURL url) throws SVNException {
        myPreviousAuthentication = null;
        myPreviousErrorMessage = null;
        myLastProviderIndex = 0;
        myLastLoadedAuth = null;
        // iterate over providers and ask for auth till it is found.
        for (int i = 0; i < myProviders.length; i++) {
            if (myProviders[i] == null) {
                continue;
            }
            SVNAuthentication auth = myProviders[i].requestClientAuthentication(kind, url, realm, null, myPreviousAuthentication, myIsStoreAuth);
            if (auth != null) {
                if (i == 2) {
                    myLastLoadedAuth = auth;
                }

                myPreviousAuthentication = auth;
                myLastProviderIndex = i;

                if (auth.isPartial()) {
                    continue;
                }
                return auth;
            }
            if (i == 3) {
                SVNErrorManager.cancel("authentication cancelled", SVNLogType.WC);
            }
        }
        // end of probe. if we were asked for username for ssh and didn't find anything 
        // report something default.
        if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
            // user auth shouldn't be null.
            return new SVNUserNameAuthentication("", isAuthStorageEnabled(url), url, false);
        }
        SVNErrorManager.authenticationFailed("Authentication required for ''{0}''", realm);
        return null;
    }

    public SVNAuthentication getNextAuthentication(String kind, String realm, SVNURL url) throws SVNException {
        int index = Math.min(myLastProviderIndex + 1, 3);
        for(int i = index; i < myProviders.length; i++) {
            if (myProviders[i] == null) {
                continue;
            }
            if ((i == 1 || i == 2) && hasExplicitCredentials(kind)) {
                continue;
            }
            SVNAuthentication auth = myProviders[i].requestClientAuthentication(kind, url, realm, myPreviousErrorMessage, myPreviousAuthentication, myIsStoreAuth);
            if (auth != null) {
                if (i == 2) {
                    myLastLoadedAuth = auth;
                }
                
                myPreviousAuthentication = auth;
                myLastProviderIndex = i;

                if (auth.isPartial()) {
                    continue;
                }

                return auth;
            }
            if (i == 3) {
                SVNErrorManager.cancel("authentication cancelled", SVNLogType.WC);
            }
        }
        SVNErrorManager.authenticationFailed("Authentication required for ''{0}''", realm);
        return null;
    }

    public void acknowledgeAuthentication(boolean accepted, String kind, String realm, SVNErrorMessage errorMessage, SVNAuthentication authentication) throws SVNException {
        if (!accepted) {
            myPreviousErrorMessage = errorMessage;
            myPreviousAuthentication = authentication;
            myLastLoadedAuth = null;
            return;
        }
        if (myIsStoreAuth && authentication.isStorageAllowed() && myProviders[2] instanceof IPersistentAuthenticationProvider) {
            // compare this authentication with last loaded from provider[2].
            if (myLastLoadedAuth == null || myLastLoadedAuth != authentication) {
                ((IPersistentAuthenticationProvider) myProviders[2]).saveAuthentication(authentication, kind, realm);
            }
        }
        myLastLoadedAuth = null;
        if (!hasExplicitCredentials(kind)) {
            // do not cache explicit credentials in runtime cache?
            ((CacheAuthenticationProvider) myProviders[1]).saveAuthentication(authentication, realm);
        }
    }

	public void acknowledgeTrustManager(TrustManager manager) {
	}

    private boolean hasExplicitCredentials(String kind) {
        if (ISVNAuthenticationManager.PASSWORD.equals(kind) || ISVNAuthenticationManager.USERNAME.equals(kind) || ISVNAuthenticationManager.SSH.equals(kind)) {
            if (myProviders[0] instanceof DumbAuthenticationProvider) {
                DumbAuthenticationProvider authProvider = (DumbAuthenticationProvider) myProviders[0];
                // for user name has to be user
                String userName = authProvider.myUserName;
                String password = authProvider.myPassword;
                if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
                    return userName != null && !"".equals(userName);
                }
                // do not look into cache when both password and user name specified
                // if only username is specified, then do look, but only for that username
                return password != null && !"".equals(password) && userName != null && !"".equals(userName);
            }
        }
        return false;
    }
    
    protected SVNCompositeConfigFile getServersFile() {
        if (myServersFile == null) {
            SVNConfigFile.createDefaultConfiguration(myConfigDirectory);
            SVNConfigFile userConfig = new SVNConfigFile(new Resource(myConfigDirectory, "servers"));
            SVNConfigFile systemConfig = new SVNConfigFile(new Resource(SVNFileUtil.getSystemConfigurationDirectory(), "servers"));
            myServersFile = new SVNCompositeConfigFile(systemConfig, userConfig);
            myServersFile.setGroupsToOptions(myServersOptions);
        }
        return myServersFile;
    }

    protected SVNCompositeConfigFile getConfigFile() {
        if (myConfigFile == null) {
            SVNConfigFile.createDefaultConfiguration(myConfigDirectory);
            SVNConfigFile userConfig = new SVNConfigFile(new Resource(myConfigDirectory, "config"));
            SVNConfigFile systemConfig = new SVNConfigFile(new Resource(SVNFileUtil.getSystemConfigurationDirectory(), "config"));
            myConfigFile = new SVNCompositeConfigFile(systemConfig, userConfig);
            myConfigFile.setGroupsToOptions(myConfigOptions);
        }
        return myConfigFile;
    }

    /**
     * Sets a specific runtime authentication storage manager. This storage
     * manager will be asked by this auth manager for cached credentials as
     * well as used to cache new ones accepted recently.
     *
     * @param storage a custom auth storage manager
     */
    public void setRuntimeStorage(ISVNAuthenticationStorage storage) {
        myRuntimeAuthStorage = storage;
    }
    
    protected ISVNAuthenticationStorage getRuntimeAuthStorage() {
        if (myRuntimeAuthStorage == null) {
            myRuntimeAuthStorage = new ISVNAuthenticationStorage() {
                private Map myData = new SVNHashMap(); 

                public void putData(String kind, String realm, Object data) {
                    myData.put(kind + "$" + realm, data);
                }
                public Object getData(String kind, String realm) {
                    return myData.get(kind + "$" + realm);
                }
            };
        }
        return myRuntimeAuthStorage;
    }
    
    protected boolean isAuthStorageEnabled(SVNURL url) {
        String host = url != null ? url.getHost() : null;
        Map properties = getHostProperties(host);
        String storeAuthCreds = (String) properties.get("store-auth-creds");
        if (storeAuthCreds == null) {
            return myIsStoreAuth;
        }
        
        return "yes".equalsIgnoreCase(storeAuthCreds) || "on".equalsIgnoreCase(storeAuthCreds) || "true".equalsIgnoreCase(storeAuthCreds);
    }
    
    protected boolean isStorePasswords(SVNURL url) {
        boolean store = true;
        String value = getConfigFile().getPropertyValue("auth", "store-passwords");
        if (value != null) {
            store = "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
        } 

        String host = url != null ? url.getHost() : null;
        Map properties = getHostProperties(host);
        String storePasswords = (String) properties.get("store-passwords");
        if (storePasswords == null) {
            return store;
        }
        
        return "yes".equalsIgnoreCase(storePasswords) || "on".equalsIgnoreCase(storePasswords) || "true".equalsIgnoreCase(storePasswords);
    }
    
    protected boolean isStorePlainTextPasswords(String realm, SVNAuthentication auth) throws SVNException {
        SVNURL url = auth.getURL();
        String host = url != null ? url.getHost() : null;
        Map properties = getHostProperties(host);
        String storePlainTextPasswords = (String) properties.get("store-plaintext-passwords");
        
        if (storePlainTextPasswords == null) {
            if (myAuthStoreHandler != null) {
                return myAuthStoreHandler.canStorePlainTextPasswords(realm, auth);
            }
            return false;
        }

        return "yes".equalsIgnoreCase(storePlainTextPasswords) || "on".equalsIgnoreCase(storePlainTextPasswords) || 
               "true".equalsIgnoreCase(storePlainTextPasswords);
    }
    
    protected boolean isStoreSSLClientCertificatePassphrases(SVNURL url) {
        String host = url != null ? url.getHost() : null;
        Map properties = getHostProperties(host);
        String storeCertPassphrases = (String) properties.get("store-ssl-client-cert-pp");
        
        if (storeCertPassphrases == null) {
            return true;
        }

        return "yes".equalsIgnoreCase(storeCertPassphrases) || "on".equalsIgnoreCase(storeCertPassphrases) || 
               "true".equalsIgnoreCase(storeCertPassphrases);
    }

    protected boolean isStorePlainTextPassphrases(String realm, SVNAuthentication auth) throws SVNException {
        SVNURL url = auth.getURL();
        String host = url != null ? url.getHost() : null;
        Map properties = getHostProperties(host);
        String storePlainTextPassphrases = (String) properties.get("store-ssl-client-cert-pp-plaintext");
        
        if (storePlainTextPassphrases == null) {
            if (myAuthStoreHandler != null) {
                return myAuthStoreHandler.canStorePlainTextPassphrases(realm, auth);
            }
            return false;
        }

        return "yes".equalsIgnoreCase(storePlainTextPassphrases) || "on".equalsIgnoreCase(storePlainTextPassphrases) || 
               "true".equalsIgnoreCase(storePlainTextPassphrases);
    }

    protected String getUserName(SVNURL url) {
        String host = url != null ? url.getHost() : null;
//        if (url != null && url.getUserInfo() != null) {
//            return url.getUserInfo(); 
//        }
        Map properties = getHostProperties(host);
        String userName = (String) properties.get("username");
        return userName; 
    }
    
    protected ISVNAuthenticationProvider getAuthenticationProvider() {
        return myProviders[3];
    }
    
    protected int getDefaultSSHPortNumber() {
        Map tunnels = getConfigFile().getProperties("tunnels");
        if (tunnels == null || !tunnels.containsKey("ssh")) {
            return -1;
        }
        String sshProgram = (String) tunnels.get("ssh");
        if (sshProgram == null) {
            return -1;
        }
        String port = getOptionValue(sshProgram, sshProgram.toLowerCase().trim().startsWith("plink") ? "-p" : "-P");
        port = port == null ? System.getProperty("svnkit.ssh2.port", System.getProperty("javasvn.ssh2.port")) : port;
        if (port != null) {
            try {
                return Integer.parseInt(port);
            } catch (NumberFormatException e) {
                //
            }
        }
        return -1;
    }
    
    protected SVNSSHAuthentication getDefaultSSHAuthentication() {
        Map tunnels = getConfigFile().getProperties("tunnels");
        if (tunnels == null || !tunnels.containsKey("ssh")) {
            tunnels = new SVNHashMap();
        }
        
        String sshProgram = (String) tunnels.get("ssh");
        String userName = getOptionValue(sshProgram, "-l");
        String password = getOptionValue(sshProgram, "-pw");
        String keyFile = getOptionValue(sshProgram, "-i");
        String port = getOptionValue(sshProgram, sshProgram != null && sshProgram.toLowerCase().trim().startsWith("plink") ? "-P" : "-p");
        String passphrase = null; 
        // fallback to system properties.
        userName = userName == null ? System.getProperty("svnkit.ssh2.username", System.getProperty("javasvn.ssh2.username")) : userName;
        keyFile = keyFile == null ? System.getProperty("svnkit.ssh2.key", System.getProperty("javasvn.ssh2.key")) : keyFile;
        passphrase = passphrase == null ? System.getProperty("svnkit.ssh2.passphrase", System.getProperty("javasvn.ssh2.passphrase")) : passphrase;
        password = password == null ? System.getProperty("svnkit.ssh2.password", System.getProperty("javasvn.ssh2.password")) : password;
        port = port == null ? System.getProperty("svnkit.ssh2.port", System.getProperty("javasvn.ssh2.port")) : port;

        if (userName == null) {
            userName = System.getProperty("user.name");
        }
        int portNumber = -1;
        if (port != null) {
            try {
                portNumber = Integer.parseInt(port);
            } catch (NumberFormatException e) {}
        }
        
        if (userName != null && password != null) {
            return new SVNSSHAuthentication(userName, password, portNumber, isAuthStorageEnabled(null), null, false);
        } else if (userName != null && keyFile != null) {
            return new SVNSSHAuthentication(userName, new Resource(keyFile), passphrase, portNumber, isAuthStorageEnabled(null), null, false);
        }
        return null;
    }
    
    protected ISVNAuthenticationProvider createDefaultAuthenticationProvider(String userName, String password, File privateKey, String passphrase, boolean allowSave) {
        return new DumbAuthenticationProvider(userName, password, privateKey, passphrase, allowSave);
    }

    protected ISVNAuthenticationProvider createRuntimeAuthenticationProvider() {
        return new CacheAuthenticationProvider();
    }
    protected ISVNAuthenticationProvider createCacheAuthenticationProvider(File authDir, String userName) {
        return new PersistentAuthenticationProvider(authDir, userName);
    }
    
    private static String getOptionValue(String commandLine, String optionName) {
        if (commandLine == null || optionName == null) {
            return null;
        }
        for(StringTokenizer options = new StringTokenizer(commandLine, " \r\n\t"); options.hasMoreTokens();) {
            String option = options.nextToken().trim();
            if (optionName.equals(option) && options.hasMoreTokens()) {
                return options.nextToken();
            } else if (option.startsWith(optionName)) {
                return option.substring(optionName.length());
            }
        }
        return null;
    }
    
    
    protected class DumbAuthenticationProvider implements ISVNAuthenticationProvider {
        
        private String myUserName;
        private String myPassword;
        private boolean myIsStore;
        private String myPassphrase;
        private File myPrivateKey;

        public DumbAuthenticationProvider(String userName, String password, File privateKey, String passphrase, boolean store) {
            myUserName = userName;
            myPassword = password;
            myPrivateKey = privateKey;
            myPassphrase = passphrase;
            myIsStore = store;
        }

        public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage,
                SVNAuthentication previousAuth, boolean authMayBeStored) {
            if (previousAuth == null) {
                if (ISVNAuthenticationManager.SSH.equals(kind)) {
                    SVNSSHAuthentication sshAuth = getDefaultSSHAuthentication();
                    if (myUserName == null || "".equals(myUserName.trim())) {
                        return sshAuth;
                    }
                    if (myPrivateKey != null) {
                        return new SVNSSHAuthentication(myUserName, myPrivateKey, myPassphrase, sshAuth != null ? sshAuth.getPortNumber() : -1, 
                                myIsStore, url, false);
                    }
                    return new SVNSSHAuthentication(myUserName, myPassword, sshAuth != null ? sshAuth.getPortNumber() : -1, myIsStore, url, false);
                } else if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                    if (myUserName == null || "".equals(myUserName.trim())) {
                        String defaultUserName = getUserName(url);
                        defaultUserName = defaultUserName == null ? System.getProperty("user.name") : defaultUserName; 
                        if (defaultUserName != null) {
                            //return new SVNUserNameAuthentication(defaultUserName, false);
                            SVNPasswordAuthentication partialAuth = new SVNPasswordAuthentication(defaultUserName, null, false, null, true);
                            return partialAuth;
                        } 
                        return null;
                    }
                    
                    if (myPassword == null) {
                        return new SVNPasswordAuthentication(myUserName, null, false, null, true);
                    }
                    return new SVNPasswordAuthentication(myUserName, myPassword, myIsStore, url, false);
                } else if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
                    if (myUserName == null || "".equals(myUserName)) {
                        String userName = System.getProperty("svnkit.ssh2.author", System.getProperty("javasvn.ssh2.author"));
                        if (userName != null) {
                            return new SVNUserNameAuthentication(userName, myIsStore, url, false);
                        }
                        return null;
                    }
                    return new SVNUserNameAuthentication(myUserName, myIsStore, url, false);
                }
            }
            return null;
        }
        public int acceptServerAuthentication(SVNURL url, String r, Object serverAuth, boolean resultMayBeStored) {
            return ACCEPTED;
        }
    }

    private static String getGroupName(Map groups, String host) {
        for (Iterator names = groups.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            String pattern = (String) groups.get(name);
            for(StringTokenizer tokens = new StringTokenizer(pattern, ","); tokens.hasMoreTokens();) {
                String token = tokens.nextToken();
                if (DefaultSVNOptions.matches(token, host)) {
                    return name;
                }
            }
        }
        return null;
    }

    private class CacheAuthenticationProvider implements ISVNAuthenticationProvider {        

        public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
            return (SVNAuthentication) getRuntimeAuthStorage().getData(kind, realm);
        }
        
        public void saveAuthentication(SVNAuthentication auth, String realm) {
            if (auth == null || realm == null) {
                return;
            }
            String kind = auth.getKind();
            getRuntimeAuthStorage().putData(kind, realm, auth);
        }
        
        public int acceptServerAuthentication(SVNURL url, String r, Object serverAuth, boolean resultMayBeStored) {
            return ACCEPTED;
        }
    }

    /**
     * @version 1.3
     * @author  TMate Software Ltd.
     */
    public interface IPersistentAuthenticationProvider {
        public void saveAuthentication(SVNAuthentication auth, String kind, String realm) throws SVNException;        
    }

    private class PersistentAuthenticationProvider implements ISVNAuthenticationProvider, IPersistentAuthenticationProvider {
        
        private File myDirectory;
        private String myUserName;
        
        public PersistentAuthenticationProvider(File directory, String userName) {
            myDirectory = directory;
            myUserName = userName;
        }

        public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, 
                SVNAuthentication previousAuth, boolean authMayBeStored) {
	        if (ISVNAuthenticationManager.SSL.equals(kind)) {
		        String host = url.getHost();
		        Map properties = getHostProperties(host);
		        String sslClientCert = (String) properties.get("ssl-client-cert-file"); // PKCS#12
		        if (sslClientCert != null && !"".equals(sslClientCert)) {
		            if (isMSCapi(sslClientCert)) {
		                String alias = null;
		                if (sslClientCert.lastIndexOf(';') > 0) {
		                    alias = sslClientCert.substring(sslClientCert.lastIndexOf(';') + 1);		                    
		                }
	                    return new SVNSSLAuthentication(SVNSSLAuthentication.MSCAPI, alias, authMayBeStored, url, false);
	                }
	                String sslClientCertPassword = (String) properties.get("ssl-client-cert-password");
	                File clientCertFile = sslClientCert != null ? new Resource(sslClientCert) : null;
	                return new SVNSSLAuthentication(clientCertFile, sslClientCertPassword, authMayBeStored, url, false);
		        }
                //try looking in svn.ssl.client-passphrase directory cache 
	        }

            File dir = new Resource(myDirectory, kind);
            if (!dir.isDirectory()) {
                return null;
            }
            String fileName = SVNFileUtil.computeChecksum(realm);
            File authFile = new Resource(dir, fileName);
            if (authFile.exists()) {
                SVNWCProperties props = new SVNWCProperties(authFile, "");
                try {
                    SVNProperties values = props.asMap();
                    String storedRealm = values.getStringValue("svn:realmstring");
                    String cipherType = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("passtype"));
                    if (cipherType != null && !SVNPasswordCipher.hasCipher(cipherType)) {
                        return null;
                    }
                    SVNPasswordCipher cipher = SVNPasswordCipher.getInstance(cipherType);
                    if (storedRealm == null || !storedRealm.equals(realm)) {
                        return null;
                    }

                    String userName = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("username"));
                    
                    if (!ISVNAuthenticationManager.SSL.equals(kind)) {
                        if (userName == null || "".equals(userName.trim())) {
                            return null;
                        }
                        if (myUserName != null && !myUserName.equals(userName)) {
                            return null;
                        }
                    }
                    
                    String password = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("password"));
                    password = cipher.decrypt(password);

                    String path = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("key"));
                    String passphrase = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("passphrase"));
                    passphrase = cipher.decrypt(passphrase);
                    String port = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("port"));
                    port = port == null ? ("" + getDefaultSSHPortNumber()) : port;
                    String sslKind = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("ssl-kind"));
                    
                    if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                        if (password == null) {
                            return new SVNPasswordAuthentication(userName, password, authMayBeStored, null, true);
                        }
                        return new SVNPasswordAuthentication(userName, password, authMayBeStored, url, false);
                    } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
                        // get port from config file or system property?
                        int portNumber;
                        try {
                            portNumber = Integer.parseInt(port);
                        } catch (NumberFormatException nfe) {
                            portNumber = getDefaultSSHPortNumber();
                        }
                        if (path != null) {
                            return new SVNSSHAuthentication(userName, new Resource(path), passphrase, portNumber, authMayBeStored, url, false);
                        } else if (password != null) {
                            return new SVNSSHAuthentication(userName, password, portNumber, authMayBeStored, url, false);
                        }                    
                    } else if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
                        return new SVNUserNameAuthentication(userName, authMayBeStored, url, false);
                    } else if (ISVNAuthenticationManager.SSL.equals(kind)) {
                        if (isMSCapi(sslKind)) {
                            String alias = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("alias"));
                            return new SVNSSLAuthentication(SVNSSLAuthentication.MSCAPI, alias, authMayBeStored, url, false);
                        }
                        return new SVNSSLAuthentication(new Resource(path), passphrase, authMayBeStored, url, false);
                    }
                } catch (SVNException e) {
                    //
                }
            }
            return null;
        }

        public boolean isMSCapi(String filepath) {
            if (filepath != null && filepath.startsWith(SVNSSLAuthentication.MSCAPI)) {
                return true;
            }
            return false;
        }

        public void saveAuthentication(SVNAuthentication auth, String kind, String realm) throws SVNException {
            File dir = new Resource(myDirectory, kind);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if (!dir.isDirectory()) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot create directory ''{0}''", dir.getAbsolutePath());
                SVNErrorManager.error(error, SVNLogType.DEFAULT);
            }
            if (!ISVNAuthenticationManager.SSL.equals(kind) && ("".equals(auth.getUserName()) || auth.getUserName() == null)) {
                return;
            }
            
            Map values = new SVNHashMap();
            values.put("svn:realmstring", realm);

            if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                savePasswordCredential(values, auth, realm);
            } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
                saveSSHCredential(values, auth, realm);
            } else if (ISVNAuthenticationManager.SSL.equals(kind)) {
                saveSSLCredential(values, auth, realm);
            } else if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
                saveUserNameCredential(values, auth);
            }
            // get file name for auth and store password.
            String fileName = SVNFileUtil.computeChecksum(realm);
            File authFile = new Resource(dir, fileName);
            
            if (authFile.isFile()) {
                SVNWCProperties props = new SVNWCProperties(authFile, "");
                try {
                    if (values.equals(props.asMap())) {
                        return;
                    }
                } catch (SVNException e) {
                    // 
                }
            }            
            File tmpFile = SVNFileUtil.createUniqueFile(dir, "auth", ".tmp", true);
            try {
                SVNWCProperties.setProperties(SVNProperties.wrap(values), authFile, tmpFile, SVNWCProperties.SVN_HASH_TERMINATOR);
            } finally {
                SVNFileUtil.deleteFile(tmpFile);
            }
        }

        public int acceptServerAuthentication(SVNURL url, String r, Object serverAuth, boolean resultMayBeStored) {
            return ACCEPTED;
        }

        private void saveUserNameCredential(Map values, SVNAuthentication auth) {
            values.put("username", auth.getUserName());
        }
        
        private void savePasswordCredential(Map values, SVNAuthentication auth, String realm) throws SVNException {
            values.put("username", auth.getUserName());
            
            boolean storePasswords = isStorePasswords(auth.getURL());
            boolean maySavePassword = false;
            
            SVNPasswordCipher cipher = null;
            
            if (storePasswords) {
                String cipherType = SVNPasswordCipher.getDefaultCipherType();
                cipher = SVNPasswordCipher.getInstance(cipherType);
                if (cipherType != null) {
                    if (!SVNPasswordCipher.SIMPLE_CIPHER_TYPE.equals(cipherType)) {
                        maySavePassword = true;
                    } else {
                        maySavePassword = isStorePlainTextPasswords(realm, auth);
                    }
                    
                    if (maySavePassword) {
                        values.put("passtype", cipherType);
                    }
                }
            }

            if (maySavePassword) {
                SVNPasswordAuthentication passwordAuth = (SVNPasswordAuthentication) auth;
                values.put("password", cipher.encrypt(passwordAuth.getPassword()));
            }
        }

        private void saveSSHCredential(Map values, SVNAuthentication auth, String realm) throws SVNException {
            values.put("username", auth.getUserName());
            
            boolean storePasswords = isStorePasswords(auth.getURL());
            boolean maySavePassword = false;
            
            SVNPasswordCipher cipher = null;
            
            if (storePasswords) {
                String cipherType = SVNPasswordCipher.getDefaultCipherType();
                cipher = SVNPasswordCipher.getInstance(cipherType);
                if (cipherType != null) {
                    if (!SVNPasswordCipher.SIMPLE_CIPHER_TYPE.equals(cipherType)) {
                        maySavePassword = true;
                    } else {
                        maySavePassword = isStorePlainTextPasswords(realm, auth);
                    }
                    
                    if (maySavePassword) {
                        values.put("passtype", cipherType);
                    }
                }
            }

            SVNSSHAuthentication sshAuth = (SVNSSHAuthentication) auth;
            if (maySavePassword) { 
                values.put("password", cipher.encrypt(sshAuth.getPassword()));
            }

            int port = sshAuth.getPortNumber();
            if (sshAuth.getPortNumber() < 0) {
                port = getDefaultSSHPortNumber() ;
            }
            values.put("port", Integer.toString(port));
            
            if (sshAuth.getPrivateKeyFile() != null) { 
                String path = sshAuth.getPrivateKeyFile().getAbsolutePath();
                if (maySavePassword) {
                    values.put("passphrase", cipher.encrypt(sshAuth.getPassphrase()));
                }
                values.put("key", path);
            }
        }

        private void saveSSLCredential(Map values, SVNAuthentication auth, String realm) throws SVNException {
            boolean storePassphrases = isStoreSSLClientCertificatePassphrases(auth.getURL());
            boolean maySavePassphrase = false;
            
            SVNPasswordCipher cipher = null;
            
            if (storePassphrases) {
                String cipherType = SVNPasswordCipher.getDefaultCipherType();
                cipher = SVNPasswordCipher.getInstance(cipherType);
                if (cipherType != null) {
                    if (!SVNPasswordCipher.SIMPLE_CIPHER_TYPE.equals(cipherType)) {
                        maySavePassphrase = true;
                    } else {
                        maySavePassphrase = isStorePlainTextPassphrases(realm, auth);
                    }
                    
                    if (maySavePassphrase) {
                        values.put("passtype", cipherType);
                    }
                }
            }
            
            SVNSSLAuthentication sslAuth = (SVNSSLAuthentication) auth;
            if (maySavePassphrase) {
                values.put("passphrase", cipher.encrypt(sslAuth.getPassword()));
            }
            if (SVNSSLAuthentication.SSL.equals(sslAuth.getSSLKind())) {
                if (sslAuth.getCertificateFile() != null) {
                    String path = sslAuth.getCertificateFile().getAbsolutePath();
                    values.put("key", path);
                }
            } else if (SVNSSLAuthentication.MSCAPI.equals(sslAuth.getSSLKind())) {
                values.put("ssl-kind", sslAuth.getSSLKind());
                values.put("alias", sslAuth.getAlias());
            }

        }
    }
    
    private static final class SimpleProxyManager implements ISVNProxyManager {

        private String myProxyHost;
        private String myProxyPort;
        private String myProxyUser;
        private String myProxyPassword;

        public SimpleProxyManager(String host, String port, String user, String password) {
            myProxyHost = host;
            myProxyPort = port == null ? "3128" : port;
            myProxyUser = user;
            myProxyPassword = password;
        }
        
        public String getProxyHost() {
            return myProxyHost;
        }

        public int getProxyPort() {
            try {
                return Integer.parseInt(myProxyPort);
            } catch (NumberFormatException nfe) {
                //
            }
            return 3128;
        }

        public String getProxyUserName() {
            return myProxyUser;
        }

        public String getProxyPassword() {
            return myProxyPassword;
        }

        public void acknowledgeProxyContext(boolean accepted, SVNErrorMessage errorMessage) {
        }
    }

    public boolean isAuthenticationForced() {
        return myIsAuthenticationForced;
    }

    /**
     * Specifies the way how credentials are to be supplied to a
     * repository server.
     *
     * @param forced  <span class="javakeyword">true</span> to force
     *                credentials sending; <span class="javakeyword">false</span>
     *                to put off sending credentials till a server challenge
     * @see           #isAuthenticationForced()
     */
    public void setAuthenticationForced(boolean forced) {
        myIsAuthenticationForced = forced;
    }

    public int getReadTimeout(SVNRepository repository) {
        String protocol = repository.getLocation().getProtocol();
        if ("http".equals(protocol) || "https".equals(protocol)) {
            String host = repository.getLocation().getHost();
            Map properties = getHostProperties(host);
            String timeout = (String) properties.get("http-timeout");
            if (timeout != null) {
                try {
                    return Integer.parseInt(timeout)*1000;
                } catch (NumberFormatException nfe) {
                }
            }
            return 3600*1000;
        }
        return 0;
    }

    public int getConnectTimeout(SVNRepository repository) {
        String protocol = repository.getLocation().getProtocol();
        if ("http".equals(protocol) || "https".equals(protocol)) {
            return 60*1000;
        }
        return 0; 
    }
}
