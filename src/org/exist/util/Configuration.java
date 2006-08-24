/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;

import org.exist.memtree.SAXAdapter;
import org.exist.security.User;
import org.exist.security.xacml.XACMLConstants;
import org.exist.storage.IndexSpec;
import org.exist.storage.NativeBroker;
import org.exist.validation.resolver.eXistCatalogResolver;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

public class Configuration implements ErrorHandler
{
	
	/* FIXME:  It's not clear whether this class is meant to be a singleton (due to the static
	 * file and existHome fields and static methods), or if we should allow many instances to
	 * run around in the system.  Right now, any attempts to create multiple instances will
	 * likely get the system confused.  Let's decide which one it should be and fix it properly.
	 * 
	 * I vote for a Singleton (like Descriptor.java) - deliriumsky
	 */
    
    private final static Logger LOG = Logger.getLogger(Configuration.class);	//Logger
    protected static String file = null;												//config file (conf.xml by default)
    protected static File existHome = null;

    protected DocumentBuilder builder = null;									
    protected HashMap config = new HashMap();									//Configuration						
    
    public static final class SystemTaskConfig {
        
        private String className;
        private long period;
        private Properties params = new Properties();
        
        public SystemTaskConfig(String className, long period) {
            this.className = className;
            this.period = period;
        }
        
        public String getClassName() {
            return className;
        }
        
        public long getPeriod() {
            return period;
        }
        
        public Properties getProperties() {
            return params;
        }
    }
    
    public Configuration() throws DatabaseConfigurationException {
        this("conf.xml", null);
    }

    public Configuration(String configFilename) throws DatabaseConfigurationException {
        this(configFilename, null);
    }
    
    public Configuration(String configFilename, String existHomeDirname) throws DatabaseConfigurationException {
        try {
            InputStream is = null;

            // firstly, try to read the configuration from a file within the
            // classpath
            try {
				if (configFilename != null) {
				    is = Configuration.class.getClassLoader().getResourceAsStream(configFilename);
				    if (is != null) LOG.info("Reading configuration from classloader");
				} else {
				    // Default file name
				    configFilename = "conf.xml";
				}
			} catch (Exception e) {
				// EB: ignore and go forward, e.g. in case there is an absolute
				// file name for configFileName
				LOG.debug(e);
			}

            // otherise, secondly try to read configuration from file. Guess the
            // location if necessary
            if (is == null) {
                Configuration.existHome = (existHomeDirname != null) ? new File(existHomeDirname) : getExistHome(existHomeDirname);
                if (Configuration.existHome == null) {
                	// EB: try to create existHome based on location of config file
                	// when config file points to absolute file location
                	File absoluteConfigFile = new File(configFilename);
                	if (absoluteConfigFile.isAbsolute() &&
                			absoluteConfigFile.exists() && absoluteConfigFile.canRead())
                		Configuration.existHome = absoluteConfigFile.getParentFile();
                }
                File configFile = lookup(configFilename);
                if (!configFile.exists() || !configFile.canRead())
                    throw new DatabaseConfigurationException("Unable to read configuration file at " + config);
                Configuration.file = configFile.getAbsolutePath();
                is = new FileInputStream(configFile);
                // set dbHome to parent of the conf file found, to resolve relative
                // path from conf file
                existHomeDirname = configFile.getParentFile().getCanonicalPath();
                LOG.info("Reading configuration from file " + configFile);
            }
            
            
            // Create resolver
            LOG.debug("Creating eXist catalog resolver");
            System.setProperty("xml.catalog.verbosity", "10");
            eXistCatalogResolver resolver = new eXistCatalogResolver(true);
            config.put("resolver", resolver);
            
            // initialize xml parser
            // we use eXist's in-memory DOM implementation to work
            // around a bug in Xerces
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
//            factory.setFeature("http://apache.org/xml/features/validation/schema", true);
//            factory.setFeature("http://apache.org/xml/features/validation/dynamic", true);
            InputSource src = new InputSource(is);
            SAXParser parser = factory.newSAXParser();
            XMLReader reader = parser.getXMLReader();
            SAXAdapter adapter = new SAXAdapter();
            reader.setContentHandler(adapter);
            reader.parse(src);
            
            Document doc = adapter.getDocument();
            
            //indexer settings
            NodeList indexer = doc.getElementsByTagName("indexer");
            if (indexer.getLength() > 0) {
                configureIndexer(existHomeDirname, doc, indexer);
            }
            
            //db connection settings
            NodeList dbcon = doc.getElementsByTagName("db-connection");
            if (dbcon.getLength() > 0) {
                configureBackend(existHomeDirname, dbcon);
            }
            
            //transformer settings
            NodeList transformers = doc.getElementsByTagName("transformer");
            if(transformers.getLength() > 0) {
                configureTransformer(transformers);
            }
            
            //serializer settings
            NodeList serializers = doc.getElementsByTagName("serializer");
            if (serializers.getLength() > 0) {
                configureSerializer(serializers);
            }
            
            //XUpdate settings
            NodeList xupdates = doc.getElementsByTagName("xupdate");
            if (xupdates.getLength() > 0) {
                configureXUpdate(xupdates);
            }
            
            //XQuery settings
            NodeList xquery = doc.getElementsByTagName("xquery");
            if (xquery.getLength() > 0) {
                configureXQuery((Element) xquery.item(0));
            }
            
            //XACML settings
            NodeList xacml = doc.getElementsByTagName("xacml");
            if (xacml.getLength() > 0) {
                configureXACML((Element)xacml.item(0));
            }
            
            /*
            CLUSTER CONFIGURATION...
             */
            NodeList clusters = doc.getElementsByTagName("cluster");
            if(clusters.getLength() > 0) {
                configureCluster((Element)clusters.item(0));
            }
            /*
            END CLUSTER CONFIGURATION....
             */
        }
        catch (SAXException e)
		{
            LOG.warn("error while reading config file: " + configFilename, e);
            throw new DatabaseConfigurationException(e.getMessage());
        }
        catch (ParserConfigurationException cfg)
		{
            LOG.warn("error while reading config file: " + configFilename, cfg);
            throw new DatabaseConfigurationException(cfg.getMessage());
        }
        catch (IOException io)
		{
            LOG.warn("error while reading config file: " + configFilename, io);
            throw new DatabaseConfigurationException(io.getMessage());
        }
    }
    
    private void configureCluster(Element cluster) {
        String protocol = cluster.getAttribute("protocol");
        if(protocol != null) {
            config.put("cluster.protocol", protocol);
            LOG.debug("cluster.protocol: " + config.get("cluster.protocol"));
        }
        String user = cluster.getAttribute("dbaUser");
        if(user != null) {
            config.put("cluster.user", user);
            LOG.debug("cluster.user: " + config.get("cluster.user"));
        }
        String pwd = cluster.getAttribute("dbaPassword");
        if(pwd != null) {
            config.put("cluster.pwd", pwd);
            LOG.debug("cluster.pwd: " + config.get("cluster.pwd"));
        }
        String dir = cluster.getAttribute("journalDir");
        if(dir != null) {
            config.put("cluster.journalDir", dir);
            LOG.debug("cluster.journalDir: " + config.get("cluster.journalDir"));
        }
        String excludedColl = cluster.getAttribute("exclude");
        ArrayList list = new ArrayList();
        if(excludedColl != null) {
            String[] excl = excludedColl.split(",");
            for(int i=0;i<excl.length;i++){
                list.add(excl[i]);
                
            }
        }
        if(!list.contains(NativeBroker.TEMP_COLLECTION))
            list.add(NativeBroker.TEMP_COLLECTION);
        config.put("cluster.exclude", list);
        LOG.debug("cluster.exlude: " + config.get("cluster.exclude"));
        
        /*Cluster parameters for test*/
        String maxStore = cluster.getAttribute("journalMaxItem");
        if(maxStore == null || maxStore.trim().length()==0) {
            maxStore = "65000";
        }
        config.put("cluster.journal.maxStore", Integer.valueOf(maxStore));
        LOG.debug("cluster.journal.maxStore: " + config.get("cluster.journal.maxStore"));
        
        String shift = cluster.getAttribute("journalIndexShift");
        if(shift == null || shift.trim().length()==0 ) {
            shift = "100";
        }
        config.put("cluster.journal.shift", Integer.valueOf(shift));
        LOG.debug("cluster.journal.shift: " + config.get("cluster.journal.shift"));        
    }
    
    private void configureXQuery(Element xquery) throws DatabaseConfigurationException {
        
    	//java binding
    	String javabinding = xquery.getAttribute("enable-java-binding");
    	if(javabinding != null) {
            config.put("xquery.enable-java-binding", javabinding);
            LOG.debug("xquery.enable-java-binding: " + config.get("xquery.enable-java-binding"));
        }

    	//builin-modules
    	NodeList builtins = xquery.getElementsByTagName("builtin-modules");
        if (builtins.getLength() > 0) {
            Element elem = (Element) builtins.item(0);
            NodeList modules = elem.getElementsByTagName("module");
            String moduleList[][] = new String[modules.getLength()][2];
            for (int i = 0; i < modules.getLength(); i++) {
                elem = (Element) modules.item(i);
                String uri = elem.getAttribute("uri");
                String clazz = elem.getAttribute("class");
                if (uri == null)
                    throw new DatabaseConfigurationException("element 'module' requires an attribute 'uri'");
                if (clazz == null)
                    throw new DatabaseConfigurationException("element 'module' requires an attribute 'class'");
                moduleList[i][0] = uri;
                moduleList[i][1] = clazz;
                LOG.debug("Configured module '" + uri + "' implemented in '" + clazz + "'");
            }
            config.put("xquery.modules", moduleList);
        }
    }
    
    public Object getProperty(String name) {
        return config.get(name);
    }
    
    public boolean hasProperty(String name) {
        return config.containsKey(name);
    }
    
    public void setProperty(String name, Object obj) {
        config.put(name, obj);
    }

    private void configureXACML(Element xacml) {
    	String enable = xacml.getAttribute(XACMLConstants.ENABLE_XACML_ATTRIBUTE);
    	config.put(XACMLConstants.ENABLE_XACML_PROPERTY, parseBoolean(enable, false));
    	LOG.debug(XACMLConstants.ENABLE_XACML_PROPERTY + ": " + config.get(XACMLConstants.ENABLE_XACML_PROPERTY));
    	
    	String loadDefaults = xacml.getAttribute(XACMLConstants.LOAD_DEFAULT_POLICIES_ATTRIBUTE);
    	config.put(XACMLConstants.LOAD_DEFAULT_POLICIES_PROPERTY, parseBoolean(loadDefaults, true));
    	LOG.debug(XACMLConstants.LOAD_DEFAULT_POLICIES_PROPERTY + ": " + config.get(XACMLConstants.LOAD_DEFAULT_POLICIES_PROPERTY));
    }
    
    /**
     * Takes the passed string and converts it to a non-null
     * <code>Boolean</code> object.  If value is null, the specified
     * default value is used.  Otherwise, Boolean.TRUE is returned if
     * and only if the passed string equals &quot;yes&quot; or
     * &quot;true&quot;, ignoring case. 
     * 
     * @param value The string to parse
     * @param defaultValue The default if the string is null
     * @return The parsed <code>Boolean</code>
     */
    private Boolean parseBoolean(String value, boolean defaultValue)
    {
    	if(value == null)
    		return Boolean.valueOf(defaultValue);
    	value = value.toLowerCase();
    	return Boolean.valueOf(value.equals("yes") || value.equals("true"));
    }
    
    /**
     * @param xupdates
     * @throws NumberFormatException
     */
    private void configureXUpdate(NodeList xupdates) throws NumberFormatException {
        Element xupdate = (Element) xupdates.item(0);
        
        String growth = xupdate.getAttribute("growth-factor");
        if (growth != null) {
            config.put("xupdate.growth-factor", new Integer(growth));
            LOG.debug("xupdate.growth-factor: " + config.get("xupdate.growth-factor"));    
        }
        
        String fragmentation = xupdate.getAttribute("allowed-fragmentation");
        if (fragmentation != null) {
        	config.put("xupdate.fragmentation", new Integer(fragmentation));
        	LOG.debug("xupdate.fragmentation: " + config.get("xupdate.fragmentation"));
        }
        
        String consistencyCheck = xupdate.getAttribute("enable-consistency-checks");
        if (consistencyCheck != null) {
            config.put("xupdate.consistency-checks", Boolean.valueOf(consistencyCheck.equals("yes")));
            LOG.debug("xupdate.consistency-checks: " + config.get("xupdate.consistency-checks"));
        }
    }
    
    /**
     * @param transformer
     */
    private void configureTransformer(NodeList transformers)
    {
        Element transformer = (Element)transformers.item(0);
        
        String className = transformer.getAttribute("class");
        if (className != null) {
            config.put("transformer.class", className);
            LOG.debug("transformer.class: " + config.get("transformer.class"));
        }
    }
    
    /**
     * @param serializers
     */
    private void configureSerializer(NodeList serializers) {
        Element serializer = (Element) serializers.item(0);
        
        String xinclude = serializer.getAttribute("enable-xinclude");
        if (xinclude != null) {
            config.put("serialization.enable-xinclude", xinclude);
            LOG.debug("serialization.enable-xinclude: " + config.get("serialization.enable-xinclude"));
        }
        
        String xsl = serializer.getAttribute("enable-xsl");
        if (xsl != null) {
            config.put("serialization.enable-xsl", xsl);
            LOG.debug("serialization.enable-xsl: " + config.get("serialization.enable-xsl"));
        }
        
        String indent = serializer.getAttribute("indent");
        if (indent != null) {
            config.put("serialization.indent", indent);
            LOG.debug("serialization.indent: " + config.get("serialization.indent"));
        }
        String compress = serializer.getAttribute("compress-output");
        if (compress != null)
            config.put("serialization.compress-output", compress);
        String internalId = serializer.getAttribute("add-exist-id");
        if (internalId != null) {
            config.put("serialization.add-exist-id", internalId);
            LOG.debug("serialization.add-exist-id: " + config.get("serialization.add-exist-id"));
        }
        
        String tagElementMatches = serializer.getAttribute("match-tagging-elements");
        if (tagElementMatches != null) {
            config.put("serialization.match-tagging-elements", tagElementMatches);
            LOG.debug("serialization.match-tagging-elements: " + config.get("serialization.match-tagging-elements"));
        }
        
        String tagAttributeMatches = serializer.getAttribute("match-tagging-attributes");
        if (tagAttributeMatches != null) {
            config.put("serialization.match-tagging-attributes", tagAttributeMatches);
            LOG.debug("serialization.match-tagging-attributes: " + config.get("serialization.match-tagging-attributes"));
        }
    }
    
    /**
     * @param dbHome
     * @param dbcon
     * @throws DatabaseConfigurationException
     */
    private void configureBackend(String dbHome, NodeList dbcon) throws DatabaseConfigurationException {
        Element con = (Element) dbcon.item(0);
        
        String mysql = con.getAttribute("database");
        if (mysql != null) {
            config.put("database", mysql);
            LOG.debug("database: " + config.get("database"));
        }
        
        // directory for database files
        String dataFiles = con.getAttribute("files");
        if (dataFiles != null) {
        	File df = lookup(dataFiles, dbHome);
            if (!df.canRead())
                throw new DatabaseConfigurationException(
                        "cannot read data directory: "
                        + df.getAbsolutePath());            
            config.put("db-connection.data-dir", df.getAbsolutePath());
            LOG.info("data directory = " + df.getAbsolutePath());
        }

        String cacheMem = con.getAttribute("cacheSize");
        if (cacheMem != null) {
            if (cacheMem.endsWith("M") || cacheMem.endsWith("m"))
                cacheMem = cacheMem.substring(0, cacheMem.length() - 1);
            try {
                config.put("db-connection.cache-size", new Integer(cacheMem));
                LOG.debug("db-connection.cache-size: " + config.get("db-connection.cache-size") + "m");
            } catch (NumberFormatException nfe) {
            	LOG.warn(nfe);
            }
        }
        
        String buffers = con.getAttribute("buffers");
        if (buffers != null) {
            try {
                config.put("db-connection.buffers", new Integer(buffers));
                LOG.debug("db-connection.buffers: " + config.get("db-connection.buffers"));
            } catch (NumberFormatException nfe) {
            	LOG.warn(nfe);
            }
        }

        String pageSize = con.getAttribute("pageSize");
        if (pageSize != null) {
            try {
                config.put("db-connection.page-size", new Integer(pageSize));
                LOG.debug("db-connection.page-size: " + config.get("db-connection.page-size"));
            } catch (NumberFormatException nfe) {
            	LOG.warn(nfe);
            }
        }  
            
        String collBuffers = con.getAttribute("collection_buffers");            
        if (collBuffers != null) {
            try {
                config.put("db-connection.collections.buffers", new Integer(collBuffers));
                LOG.debug("db-connection.collections.buffers: " + config.get("db-connection.collections.buffers"));               
            } catch (NumberFormatException nfe) {
            	LOG.warn(nfe);
            }
        }

        String wordBuffers = con.getAttribute("words_buffers");
        if (wordBuffers != null)
        try {
            config.put("db-connection.words.buffers", new Integer(wordBuffers));
            LOG.debug("db-connection.words.buffers: " + config.get("db-connection.words.buffers"));
        } catch (NumberFormatException nfe) {
        	LOG.warn(nfe);
        }

        String elementBuffers = con.getAttribute("elements_buffers");
        if (elementBuffers != null) {
            try {
                config.put("db-connection.elements.buffers", new Integer(elementBuffers));
                LOG.debug("db-connection.elements.buffers: " + config.get("db-connection.elements.buffers"));
            } catch (NumberFormatException nfe) {
            	LOG.warn(nfe);
            }
        }

        String freeMem = con.getAttribute("free_mem_min");
        if (freeMem != null) {
            try {
                config.put("db-connection.min_free_memory", new Integer(freeMem));
                LOG.debug("db-connection.min_free_memory: " + config.get("db-connection.min_free_memory"));
            } catch (NumberFormatException nfe) {
            	LOG.warn(nfe);
            }
        }
        
        String collCacheSize = con.getAttribute("collectionCacheSize");
        if (collCacheSize != null) {
            try {
                config.put("db-connection.collection-cache-size", new Integer(collCacheSize));
                LOG.debug("db-connection.collection-cache-size: " + config.get("db-connection.collection-cache-size"));
            } catch (NumberFormatException nfe) {
            	LOG.warn(nfe);
            }
        }
        
        NodeList securityConf = con.getElementsByTagName("security");
        String securityManagerClassName = "org.exist.security.XMLSecurityManager";
        if (securityConf.getLength()>0) {
           Element security = (Element)securityConf.item(0);
           securityManagerClassName = security.getAttribute("class");
           String encoding = security.getAttribute("password-encoding");
           config.put("db-connection.security.password-encoding",encoding);
           if (encoding!=null) {
        	   LOG.info("db-connection.security.password-encoding: " + config.get("db-connection.security.password-encoding"));
              User.setPasswordEncoding(encoding);
           } else {
              LOG.info("No password encoding set, defaulting.");
           }
           String realm = security.getAttribute("password-realm");
           config.put("db-connection.security.password-realm",realm);
           if (realm!=null) {
        	   LOG.info("db-connection.security.password-realm: " + config.get("db-connection.security.password-realm"));
              User.setPasswordRealm(realm);
           } else {
              LOG.info("No password realm set, defaulting.");
           }
        }
        
        try {
           config.put("db-connection.security.class",Class.forName(securityManagerClassName));
           LOG.debug("db-connection.security.class: " + config.get("db-connection.security.class"));
        } catch (Throwable ex) {
           if (ex instanceof ClassNotFoundException) {
              throw new DatabaseConfigurationException("Cannot find security manager class "+securityManagerClassName);
           } else {
              throw new DatabaseConfigurationException("Cannot load security manager class "+securityManagerClassName+" due to "+ex.getMessage());
           }
        }
        
        NodeList poolConf = con.getElementsByTagName("pool");
        if (poolConf.getLength() > 0) {
            configurePool(poolConf);
        }
        NodeList queryPoolConf = con.getElementsByTagName("query-pool");
        if (queryPoolConf.getLength() > 0) {
            configureXQueryPool(queryPoolConf);
        }
        NodeList watchConf = con.getElementsByTagName("watchdog");
        if (watchConf.getLength() > 0) {
            configureWatchdog(watchConf);
        }
        NodeList sysTasks = con.getElementsByTagName("system-task");
        if (sysTasks.getLength() > 0) {
            configureSystemTasks(sysTasks);
        }
        NodeList recovery = con.getElementsByTagName("recovery");
        if (recovery.getLength() > 0) {
            configureRecovery(recovery);
        }        
        configurePermissions(con.getElementsByTagName("default-permissions"));
    }
    
    private void configureRecovery(NodeList nodes) throws DatabaseConfigurationException {
        Element recovery = (Element) nodes.item(0);
        String option = recovery.getAttribute("enabled");
        boolean value = true;
        if (option != null) {
            value = option.equals("yes");
        }
        setProperty("db-connection.recovery.enabled", new Boolean(value));
        LOG.debug("db-connection.recovery.enabled: " + config.get("db-connection.recovery.enabled"));
        
        option = recovery.getAttribute("sync-on-commit");
        value = true;
        if (option != null) {
            value = option.equals("yes");
        }
        setProperty("db-connection.recovery.sync-on-commit", new Boolean(value));
        LOG.debug("db-connection.recovery.sync-on-commit: " + config.get("db-connection.recovery.sync-on-commit"));
        
        option = recovery.getAttribute("group-commit");
        value = false;
        if (option != null) {
            value = option.equals("yes");
        }
        setProperty("db-connection.recovery.group-commit", new Boolean(value));
        LOG.debug("db-connection.recovery.group-commit: " + config.get("db-connection.recovery.group-commit"));
        
        option = recovery.getAttribute("journal-dir");
        if (option != null) {
            setProperty("db-connection.recovery.journal-dir", option);
            LOG.debug("db-connection.recovery.journal-dir: " + config.get("db-connection.recovery.journal-dir"));
        }
        
        option = recovery.getAttribute("size");
        if (option != null) {
            if (option.endsWith("M") || option.endsWith("m"))
                option = option.substring(0, option.length() - 1);
            try {
                Integer size = new Integer(option);
                setProperty("db-connection.recovery.size-limit", size);
                LOG.debug("db-connection.recovery.size-limit: " + config.get("db-connection.recovery.size-limit") + "m");
            } catch (NumberFormatException e) {
                throw new DatabaseConfigurationException("size attribute in recovery section needs to be a number");
            }
        }
    }
    
    private void configurePermissions(NodeList nodes) throws DatabaseConfigurationException {
        if (nodes.getLength() > 0) {
            Element node = (Element) nodes.item(0);
            String option = node.getAttribute("collection");
            if (option != null && option.length() > 0) {
                try {
                    Integer perms = new Integer(Integer.parseInt(option, 8));
                    setProperty("indexer.permissions.collection", perms);
                    LOG.debug("indexer.permissions.collection: " + config.get("indexer.permissions.collection"));
                } catch (NumberFormatException e) {
                    throw new DatabaseConfigurationException("collection attribute in default-permissions section needs " +
                            "to be an octal number");
                }
            }
            option = node.getAttribute("resource");
            if (option != null && option.length() > 0) {
                try {
                    Integer perms = new Integer(Integer.parseInt(option, 8));
                    setProperty("indexer.permissions.resource", perms);
                    LOG.debug("indexer.permissions.resource: " + config.get("indexer.permissions.resource"));
                } catch (NumberFormatException e) {
                    throw new DatabaseConfigurationException("resource attribute in default-permissions section needs " +
                            "to be an octal number");
                }
            }
        }
    }
    
    /**
     * @param sysTasks
     */
    private void configureSystemTasks(NodeList sysTasks) throws DatabaseConfigurationException {
        SystemTaskConfig taskList[] = new SystemTaskConfig[sysTasks.getLength()];
        for (int i = 0; i < sysTasks.getLength(); i++) {
            Element taskDef = (Element) sysTasks.item(i);
            String classAttr = taskDef.getAttribute("class");
            if (classAttr == null || classAttr.length() == 0)
                throw new DatabaseConfigurationException("No class specified for system-task");
            String periodAttr = taskDef.getAttribute("period");
            if (periodAttr == null || periodAttr.length() == 0)
                throw new DatabaseConfigurationException("No period specified for system-task");
            long period;
            try {
                period = Long.parseLong(periodAttr);
            } catch (NumberFormatException e) {
                throw new DatabaseConfigurationException("Attribute period is not a number: " + e.getMessage());
            }
            SystemTaskConfig sysTask = new SystemTaskConfig(classAttr, period);
            NodeList params = taskDef.getElementsByTagName("parameter");
            for (int j = 0; j < params.getLength(); j++) {
                Element param = (Element) params.item(j);
                String name = param.getAttribute("name");
                String value = param.getAttribute("value");
                if (name == null || name.length() == 0)
                    throw new DatabaseConfigurationException("No name specified for parameter");
                sysTask.params.setProperty(name, value);
            }
            taskList[i] = sysTask;
        }
        config.put("db-connection.system-task-config", taskList);
        LOG.debug("db-connection.system-task-config: " + config.get("db-connection.system-task-config"));
    }
    
    /**
     * @param watchConf
     */
    private void configureWatchdog(NodeList watchConf) {
        Element watchDog = (Element) watchConf.item(0);

        String timeout = watchDog.getAttribute("query-timeout");
        if (timeout != null) {
            try {
                config.put("db-connection.watchdog.query-timeout", new Long(timeout));
                LOG.debug("db-connection.watchdog.query-timeout: " + config.get("db-connection.watchdog.query-timeout"));
            } catch (NumberFormatException e) {
            	LOG.warn(e);
            }
        }
        
        String maxOutput = watchDog.getAttribute("output-size-limit");
        if (maxOutput != null) {
            try {
                config.put("db-connection.watchdog.output-size-limit", new Integer(maxOutput));
                LOG.debug("db-connection.watchdog.output-size-limit: " + config.get("db-connection.watchdog.output-size-limit"));
            } catch (NumberFormatException e) {
            	LOG.warn(e);
            }
        }
    }
    
    /**
     * @param queryPoolConf
     */
    private void configureXQueryPool(NodeList queryPoolConf) {
        Element queryPool = (Element) queryPoolConf.item(0);

        String maxStackSize = queryPool.getAttribute("max-stack-size");
        if (maxStackSize != null) {
            try {
                config.put("db-connection.query-pool.max-stack-size", new Integer(maxStackSize));
                LOG.debug("db-connection.query-pool.max-stack-size: " + config.get("db-connection.query-pool.max-stack-size"));
            } catch (NumberFormatException e) {
            	LOG.warn(e);
            }
        }
    
        String maxPoolSize = queryPool.getAttribute("size");
        if (maxPoolSize != null) {
        	try {
        		config.put("db-connection.query-pool.size", new Integer(maxPoolSize));
        		LOG.debug("db-connection.query-pool.size: " + config.get("db-connection.query-pool.size"));
        	} catch (NumberFormatException e) {
        		LOG.warn(e);
        	}
        }

        String timeout = queryPool.getAttribute("timeout");
        if (timeout != null) {
            try {
                config.put("db-connection.query-pool.timeout", new Long(timeout));
                LOG.debug("db-connection.query-pool.timeout: " + config.get("db-connection.query-pool.timeout"));
            } catch (NumberFormatException e) {
            	LOG.warn(e);
            }
        }

        String timeoutCheckInterval = queryPool.getAttribute("timeout-check-interval");           
        if (timeoutCheckInterval != null) {
            try {
                config.put("db-connection.query-pool.timeout-check-interval", new Long(timeoutCheckInterval));
                LOG.debug("db-connection.query-pool.timeout-check-interval: " + config.get("db-connection.query-pool.timeout-check-interval"));
            } catch (NumberFormatException e) {
            	LOG.warn(e);
            }
        }
    }
    
    /**
     * @param poolConf
     */
    private void configurePool(NodeList poolConf) {
        Element pool = (Element) poolConf.item(0);
        
        String min = pool.getAttribute("min");
        if (min != null) {
            try {
                config.put("db-connection.pool.min", new Integer(min));
                LOG.debug("db-connection.pool.min: " + config.get("db-connection.pool.min"));
            } catch (NumberFormatException e) {
            	LOG.warn(e);
            }
        }

        String max = pool.getAttribute("max");
        if (max != null) {
	        try {
	            config.put("db-connection.pool.max", new Integer(max));
	            LOG.debug("db-connection.pool.max: " + config.get("db-connection.pool.max"));
	        } catch (NumberFormatException e) {
	        	LOG.warn(e);
	        }
        }
        
        String sync = pool.getAttribute("sync-period");
        if (sync != null) {
            try {
                config.put("db-connection.pool.sync-period", new Long(sync));
                LOG.debug("db-connection.pool.sync-period: " + config.get("db-connection.pool.sync-period"));
            } catch (NumberFormatException e) {
            	LOG.warn(e);
            }
        }
        
        String maxShutdownWait = pool.getAttribute("wait-before-shutdown");
        if (maxShutdownWait != null) {
            try {
                config.put("wait-before-shutdown", new Long(maxShutdownWait));
                LOG.debug("wait-before-shutdown: " + config.get("wait-before-shutdown"));
            } catch (NumberFormatException e) {
            	LOG.warn(e);
            }
        }
    }
    
    /**
     * @param dbHome
     * @param doc
     * @param indexer
     * @throws DatabaseConfigurationException
     * @throws MalformedURLException
     * @throws IOException
     */
    private void configureIndexer(String dbHome, Document doc, NodeList indexer) throws DatabaseConfigurationException, MalformedURLException {
        
        Element p = (Element) indexer.item(0);

        String parseNum = p.getAttribute("parseNumbers");
        if (parseNum != null) {
            config.put("indexer.indexNumbers", Boolean.valueOf(parseNum.equals("yes")));
            LOG.debug("indexer.indexNumbers: " + config.get("indexer.indexNumbers"));
        }

        String stemming = p.getAttribute("stemming");
        if (stemming != null) {
            config.put("indexer.stem", Boolean.valueOf(stemming.equals("yes")));
            LOG.debug("indexer.stem: " + config.get("indexer.stem"));
        }

        String termFreq = p.getAttribute("track-term-freq");
        if (termFreq != null) {
            config.put("indexer.store-term-freq", Boolean.valueOf(termFreq.equals("yes")));
            LOG.debug("indexer.store-term-freq: " + config.get("indexer.store-term-freq"));
        }

        String caseSensitive = p.getAttribute("caseSensitive");
        if (caseSensitive != null) {
            config.put("indexer.case-sensitive", Boolean.valueOf(caseSensitive.equals("yes")));
            LOG.debug("indexer.case-sensitive: " + config.get("indexer.case-sensitive"));
        }

        String suppressWS = p.getAttribute("suppress-whitespace");
        if (suppressWS != null) {
            config.put("indexer.suppress-whitespace", suppressWS);
            LOG.debug("indexer.suppress-whitespace: " + config.get("indexer.suppress-whitespace"));
        }

        String validation = p.getAttribute("validation");         
        if (validation != null) {
            config.put("indexer.validation", validation);
            LOG.debug("indexer.validation: " + config.get("indexer.validation"));
        }
        
        String tokenizer = p.getAttribute("tokenizer");
        if (tokenizer != null) {
            config.put("indexer.tokenizer", tokenizer);
            LOG.debug("indexer.tokenizer: " + config.get("indexer.tokenizer"));
        }
        int depth = 2;
        String indexDepth = p.getAttribute("index-depth");
        if (indexDepth != null) {
            try {
                depth = Integer.parseInt(indexDepth);
                if (depth < 3) {
                	LOG.warn("parameter index-depth should be >= 3 or you will experience a severe " +
                			"performance loss for node updates (XUpdate or XQuery update extensions)");
                	depth = 3;
                }
                config.put("indexer.index-depth", new Integer(depth));
                LOG.debug("indexer.index-depth: " + config.get("indexer.index-depth"));
            } catch (NumberFormatException e) {
            	LOG.warn(e);
            }
        }

        String suppressWSmixed = p.getAttribute("preserve-whitespace-mixed-content");
        if (suppressWSmixed != null) {
            config.put("indexer.preserve-whitespace-mixed-content", Boolean.valueOf(suppressWSmixed.equals("yes")));
            LOG.debug("indexer.preserve-whitespace-mixed-content: " + config.get("indexer.preserve-whitespace-mixed-content"));
        }
        
        // index settings
        NodeList cl = doc.getElementsByTagName("index");
        if (cl.getLength() > 0) {
            Element elem = (Element) cl.item(0);
            IndexSpec spec = new IndexSpec(elem);
            spec.setIndexDepth(depth);
            config.put("indexer.config", spec);
        }
        
        // stopwords
        NodeList stopwords = p.getElementsByTagName("stopwords");
        if (stopwords.getLength() > 0) {
            String stopwordFile = ((Element) stopwords.item(0)).getAttribute("file");
            File sf = lookup(stopwordFile, dbHome);
            if (sf.canRead()) {
                config.put("stopwords", stopwordFile);
                LOG.debug("stopwords: " + config.get("stopwords"));
            }
        }
        
        //TODO : what does the following code makes here ??? -pb
        
        eXistCatalogResolver resolver = (eXistCatalogResolver) config.get("resolver");
        NodeList entityResolver = p.getElementsByTagName("entity-resolver");
        
        if (entityResolver.getLength() > 0) {
            Element r = (Element) entityResolver.item(0);
            NodeList catalogs = r.getElementsByTagName("catalog");
            
            // TODO remove. log function does not work yet
            System.out.println("Found "+catalogs.getLength()+" catalog entries.");
            
            for (int i = 0; i < catalogs.getLength(); i++) {
                String catalog = ((Element) catalogs.item(i)).getAttribute("file");
                           
                File catalogFile = lookup(catalog, dbHome);                
                if (catalogFile!=null && catalogFile.exists()) {
                    LOG.info("Loading catalog '"+catalogFile.getAbsolutePath()+"'.");
                    // TODO dizzzz remove debug
                    //System.out.println("Loading catalog '"+catalogFile.getAbsolutePath()+"'.");
                    try {
                        resolver.getCatalog()
                                .parseCatalog( catalogFile.getAbsolutePath() );
                        
                    } catch (IOException e) {
                        String message = "An exception occurred while reading catalog: " 
                                + catalogFile.getAbsolutePath() + ": " 
                                + e.getMessage();
                        
                        LOG.warn(message, e);
                        System.out.println(message);
                    }
                } else {
                    String message ="Could not load catalog '"
                                           +catalogFile.getAbsolutePath()+"'.";
                    LOG.debug(message);
                    System.out.println(message);
                }
            }
        }
    }
    
    public int getInteger(String name) {
        Object obj = getProperty(name);
        
        if ((obj == null) || !(obj instanceof Integer))
            return -1;
        
        return ((Integer) obj).intValue();
    }
    
    /**
     * Returns the absolut path to the configuration file.
     * 
     * @return the path to the configuration file
     */
    public static String getPath() {
    		if (file == null) {
    			File f = lookup("conf.xml");
    			return f.getAbsolutePath();
    		}
        return file;
    }
   
    /**
     * Returns a file handle for the given path, while <code>path</code> specifies
     * the path to an eXist configuration file or directory.
     * <br>
     * Note that relative paths are being interpreted relative to <code>exist.home</code>
     * or the current working directory, in case <code>exist.home</code> was not set.
     * 
     * @param path the file path
     * @return the file handle
     */
    public static File lookup(String path) {
        return lookup(path, null);
    }
        
    /**
     * Returns a file handle for the given path, while <code>path</code> specifies
     * the path to an eXist configuration file or directory.
     * <br>
     * If <code>parent</code> is null, then relative paths are being interpreted
     * relative to <code>exist.home</code> or the current working directory, in
     * case <code>exist.home</code> was not set.
     * 
     * @param path path to the file or directory
     * @param parent parent directory used to lookup <code>path</code>
     * @return the file handle
     */
    public static File lookup(String path, String parent) {
        // resolvePath is used for things like ~user/folder 
    		File f = new File(resolvePath(path));
         if (f.isAbsolute()) return f;
         if (parent == null) {
            File home = getExistHome();
            if (home == null)
                home = new File(System.getProperty("user.dir"));
            parent = home.getPath();
         }
		return new File(parent, path);
    }
    
    /**
     * Returns a file handle for eXist's home directory.
     * <p>
     * If either none of the directories identified by the system properties
     * <code>exist.home</code> and <code>user.home</code> exist or none of
     * them contain a configuration file, this method returns <code>null</code>.
     * 
     * @return the file handle or <code>null</code>
     */
    public static File getExistHome() {
        return getExistHome(null);
    }

    /**
     * Returns a file handle for eXist's home directory.
     * Order of tests is designed with the idea, the more precise it is,
     * the more the developper know what he is doing
     * <ol>
     *   <li>proposed path : if exists
     *   <li>exist.home    : if exists
     *   <li>user.home     : if exists, with a conf.xml file
     *   <li>user.dir      : if exists, with a conf.xml file
     * </ol>
     *
     * @param path path to eXist home directory
     * @return the file handle or <code>null</code>
     */
    public static File getExistHome(String path) {
		if (existHome != null) return existHome;
		
        String config = "conf.xml";

        // try path argument
        if (path != null) {
            existHome = new File(path);            
            if (existHome.isDirectory()) {
            	LOG.debug("Got eXist home from provided argument:" + existHome);
            	return existHome; 
            }
        }
        // try exist.home
        if (System.getProperty("exist.home") != null) {
            existHome = new File(resolvePath(System.getProperty("exist.home")));
            if (existHome.isDirectory()) {
            	LOG.debug("Got eXist home from system property 'exist.home': " + existHome);
            	return existHome; 
            }
        }
        
        // try user.home
        existHome = new File(System.getProperty("user.home"));
        if (existHome.isDirectory() && new File(existHome, config).isFile()) {
        	LOG.debug("Got eXist home from system property 'user.home': " + existHome);
        	return existHome; 
        }
        
        
        // try user.dir
        existHome = new File(System.getProperty("user.dir"));
        if (existHome.isDirectory() && new File(existHome, config).isFile()) {
        	LOG.debug("Got eXist home from system property 'user.dir': " + existHome);
        	return existHome; 
        }

        existHome = null;
        return existHome;
	}
    
    /**
     * Returns <code>true</code> if the directory <code>dir</code> contains a file
     * named <tt>conf.xml</tt>.
     * 
     * @param dir the directory
     * @return <code>true</code> if the directory contains a configuration file
     */
    private static boolean containsConfig(File dir, String config) {
    		if (dir != null && dir.exists() && dir.isDirectory() && dir.canRead()) {
    			File c = new File(dir, config);
    			return c.exists() && c.isFile() && c.canRead();
    		}
    		return false;
    }
    
    /**
     * Resolves the given path by means of eventually replacing <tt>~</tt> with the users
     * home directory, taken from the system property <code>user.home</code>.
     * 
     * @param path the path to resolve
     * @return the resolved path
     */
    private static String resolvePath(String path) {
        if (path != null && path.startsWith("~") && path.length() > 1) {
            path = System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
     
    /*
     * (non-Javadoc)
     * 
     * @see org.xml.sax.ErrorHandler#error(org.xml.sax.SAXParseException)
     */
    public void error(SAXParseException exception) throws SAXException {
        System.err.println("error occured while reading configuration file "
                + "[line: " + exception.getLineNumber() + "]:"
                + exception.getMessage());
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.xml.sax.ErrorHandler#fatalError(org.xml.sax.SAXParseException)
     */
    public void fatalError(SAXParseException exception) throws SAXException {
        System.err.println("error occured while reading configuration file "
                + "[line: " + exception.getLineNumber() + "]:"
                + exception.getMessage());
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.xml.sax.ErrorHandler#warning(org.xml.sax.SAXParseException)
     */
    public void warning(SAXParseException exception) throws SAXException {
        System.err.println("error occured while reading configuration file "
                + "[line: " + exception.getLineNumber() + "]:"
                + exception.getMessage());
    }
    
}
