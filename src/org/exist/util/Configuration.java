/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;

import org.exist.Indexer;
import org.exist.memtree.SAXAdapter;
import org.exist.protocolhandler.eXistURLStreamHandlerFactory;
import org.exist.scheduler.Scheduler;
import org.exist.security.User;
import org.exist.security.xacml.XACMLConstants;
import org.exist.storage.BrokerPool;
import org.exist.storage.CollectionCacheManager;
import org.exist.storage.DBBroker;
import org.exist.storage.DefaultCacheManager;
import org.exist.storage.IndexSpec;
import org.exist.storage.NativeBroker;
import org.exist.storage.NativeValueIndex;
import org.exist.storage.TextSearchEngine;
import org.exist.storage.XQueryPool;
import org.exist.validation.GrammarPool;
import org.exist.validation.resolver.eXistXMLCatalogResolver;
import org.exist.xquery.XQueryWatchDog;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

public class Configuration implements ErrorHandler {
    private final static Logger LOG = Logger.getLogger(Configuration.class); //Logger
    protected String configFilePath = null;
    protected File existHome = null;
    
    protected DocumentBuilder builder = null;
    protected HashMap config = new HashMap(); //Configuration
    
    //TODO : extract this
    public static final class SystemTaskConfig {
    	
    	public static final String CONFIGURATION_ELEMENT_NAME = "system-task";
        protected String className;
        protected long period = -1;
        protected String cronExpr = null;
        protected Properties params = new Properties();
        
        public SystemTaskConfig(String className) {
            this.className = className;
        }
        
        public String getClassName() {
            return className;
        }
        
        public void setPeriod(long period) {
            this.period = period;
        }
        
        public long getPeriod() {
            return period;
        }
        
        public String getCronExpr() {
            return cronExpr;
        }
        
        public void setCronExpr(String cronExpr) {
            this.cronExpr = cronExpr;
        }
        
        public Properties getProperties() {
            return params;
        }
    }
    
    public static final class IndexModuleConfig {
        protected String id;
        protected String className;
        protected Element config;
        
        public IndexModuleConfig(String id, String className, Element config) {
            this.id = id;
            this.className = className;
            this.config = config;
        }
        
        public String getClassName() {
            return className;
        }
        
        public Element getConfig() {
            return config;
        }
        
        public String getId() {
            return id;
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
            
            if (configFilename == null) {
                // Default file name
                configFilename = "conf.xml";
            }
            
            // firstly, try to read the configuration from a file within the
            // classpath
            try {
                is = Configuration.class.getClassLoader().getResourceAsStream(configFilename);
                if (is != null) LOG.info("Reading configuration from classloader");
            } catch (Exception e) {
                // EB: ignore and go forward, e.g. in case there is an absolute
                // file name for configFileName
                LOG.debug(e);
            }
            
            // otherwise, secondly try to read configuration from file. Guess the
            // location if necessary
            if (is == null) {
                existHome = (existHomeDirname != null) ? new File(existHomeDirname) : ConfigurationHelper.getExistHome();
                if (existHome == null) {
                    // EB: try to create existHome based on location of config file
                    // when config file points to absolute file location
                    File absoluteConfigFile = new File(configFilename);
                    if (absoluteConfigFile.isAbsolute() &&
                        absoluteConfigFile.exists() && absoluteConfigFile.canRead()) {
                        existHome = absoluteConfigFile.getParentFile();
                        configFilename = absoluteConfigFile.getName();
                    }
                }
                File configFile = new File(configFilename);
                if (!configFile.isAbsolute() && existHome != null)
                    // try the passed or constructed existHome first
                    configFile = new File(existHome, configFilename);
                if (configFile == null)
                    configFile = ConfigurationHelper.lookup(configFilename);
                if (!configFile.exists() || !configFile.canRead())
                    throw new DatabaseConfigurationException("Unable to read configuration file at " + config);
                configFilePath = configFile.getAbsolutePath();
                is = new FileInputStream(configFile);
                // set dbHome to parent of the conf file found, to resolve relative
                // path from conf file
                existHomeDirname = configFile.getParentFile().getCanonicalPath();
                LOG.info("Reading configuration from file " + configFile);
            }
            
            
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
            NodeList indexer = doc.getElementsByTagName(Indexer.CONFIGURATION_ELEMENT_NAME);
            if (indexer.getLength() > 0) {
                configureIndexer(existHomeDirname, doc, indexer);
            }
            
            //scheduler settings
            NodeList schedulers = doc.getElementsByTagName("scheduler");
            if(schedulers.getLength() > 0) {
                configureScheduler(schedulers);
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
            NodeList xacml = doc.getElementsByTagName(XACMLConstants.CONFIGURATION_ELEMENT_NAME);
            if (xacml.getLength() > 0) {
                configureXACML((Element)xacml.item(0));
            }
            
            //Cluster configuration
            NodeList clusters = doc.getElementsByTagName("cluster");
            if(clusters.getLength() > 0) {
                configureCluster((Element)clusters.item(0));
            }
            
            //Validation
            NodeList validation = doc.getElementsByTagName("validation");
            if (validation.getLength() > 0) {
                configureValidation(existHomeDirname, doc, validation);
            }
            
        } catch (SAXException e) {
            LOG.warn("error while reading config file: " + configFilename, e);
            throw new DatabaseConfigurationException(e.getMessage());
        } catch (ParserConfigurationException cfg) {
            LOG.warn("error while reading config file: " + configFilename, cfg);
            throw new DatabaseConfigurationException(cfg.getMessage());
        } catch (IOException io) {
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
        
        String optimize = xquery.getAttribute("enable-query-rewriting");
        if (optimize != null && optimize.length() > 0) {
            config.put("xquery.enable-query-rewriting", optimize);
            LOG.debug("xquery.enable-query-rewriting: " + config.get("xquery.enable-query-rewriting"));
        }
        
        String backwardCompatible = xquery.getAttribute("backwardCompatible");
        if (backwardCompatible != null && backwardCompatible.length() > 0) {
            config.put("xquery.backwardCompatible", backwardCompatible);
            LOG.debug("xquery.backwardCompatible: " + config.get("xquery.backwardCompatible"));
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
    
    private void configureXACML(Element xacml) {
        String enable = xacml.getAttribute(XACMLConstants.ENABLE_XACML_ATTRIBUTE);
        config.put(XACMLConstants.ENABLE_XACML_PROPERTY, parseBoolean(enable, false));
        LOG.debug(XACMLConstants.ENABLE_XACML_PROPERTY + ": " + config.get(XACMLConstants.ENABLE_XACML_PROPERTY));
        
        String loadDefaults = xacml.getAttribute(XACMLConstants.LOAD_DEFAULT_POLICIES_ATTRIBUTE);
        config.put(XACMLConstants.LOAD_DEFAULT_POLICIES_PROPERTY, parseBoolean(loadDefaults, true));
        LOG.debug(XACMLConstants.LOAD_DEFAULT_POLICIES_PROPERTY + ": " + config.get(XACMLConstants.LOAD_DEFAULT_POLICIES_PROPERTY));
    }
    
    /**
     * @param xupdates
     * @throws NumberFormatException
     */
    private void configureXUpdate(NodeList xupdates) throws NumberFormatException {
        Element xupdate = (Element) xupdates.item(0);
        
        String growth = xupdate.getAttribute("growth-factor");
        if (growth != null) {
            config.put(DBBroker.PROPERTY_XUPDATE_GROWTH_FACTOR, new Integer(growth));
            LOG.debug(DBBroker.PROPERTY_XUPDATE_GROWTH_FACTOR + ": "
                + config.get(DBBroker.PROPERTY_XUPDATE_GROWTH_FACTOR));
        }
        
        String fragmentation = xupdate.getAttribute("allowed-fragmentation");
        if (fragmentation != null) {
            config.put(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR, new Integer(fragmentation));
            LOG.debug(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR + ": "
                + config.get(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR));
        }
        
        String consistencyCheck = xupdate.getAttribute("enable-consistency-checks");
        if (consistencyCheck != null) {
            config.put(DBBroker.PROPERTY_XUPDATE_CONSISTENCY_CHECKS, Boolean.valueOf(consistencyCheck.equals("yes")));
            LOG.debug(DBBroker.PROPERTY_XUPDATE_CONSISTENCY_CHECKS + ": "
                + config.get(DBBroker.PROPERTY_XUPDATE_CONSISTENCY_CHECKS));
        }
    }
    
    private void configureTransformer(NodeList transformers) {
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
     * Reads the scheduler configuration
     */
    private void configureScheduler(NodeList Schedulers) {
        Element scheduler = (Element)Schedulers.item(0);
        NodeList nlJobs = scheduler.getElementsByTagName("job");
        
        if(nlJobs == null)
            return;
        
        String jobList[][] = new String[nlJobs.getLength()][2];
        String jobResource = null;
        String jobSchedule = null;
        
        for(int i = 0; i < nlJobs.getLength(); i++) {
            Element job = (Element)nlJobs.item(i);
            
            //get the job resource
            jobResource = job.getAttribute("class");
            if(jobResource == null)
                jobResource = job.getAttribute("xquery");
            
            //get the job schedule
            jobSchedule = job.getAttribute("cron-trigger");
            if(jobSchedule == null)
                jobSchedule = job.getAttribute("period");
            
            //check we have both a resource and a schedule
            if(jobResource == null | jobSchedule == null)
                return;
            
            jobList[i][0] = jobResource;
            jobList[i][1] = jobSchedule;
            LOG.debug("Configured scheduled job '" + jobResource + "' with trigger '" + jobSchedule + "'");
        }
        config.put(Scheduler.PROPERTY_SCHEDULER_JOBS, jobList);
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
            File df = ConfigurationHelper.lookup(dataFiles, dbHome);
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
                config.put(DefaultCacheManager.PROPERTY_CACHE_SIZE, new Integer(cacheMem));
                LOG.debug(DefaultCacheManager.PROPERTY_CACHE_SIZE + ": " + config.get(DefaultCacheManager.PROPERTY_CACHE_SIZE) + "m");
            } catch (NumberFormatException nfe) {
                LOG.warn(nfe);
            }
        }
        
        String collectionCache = con.getAttribute("collectionCache");
        if (collectionCache != null) {
            if (collectionCache.endsWith("M") || collectionCache.endsWith("m"))
                collectionCache = collectionCache.substring(0, collectionCache.length() - 1);
            try {
                config.put(CollectionCacheManager.PROPERTY_CACHE_SIZE, new Integer(collectionCache));
                LOG.debug(CollectionCacheManager.PROPERTY_CACHE_SIZE + ": " + config.get(CollectionCacheManager.PROPERTY_CACHE_SIZE) + "m");
            } catch (NumberFormatException nfe) {
                LOG.warn(nfe);
            }
        }
        
        //Unused !
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
                config.put(NativeBroker.PROPERTY_PAGE_SIZE, new Integer(pageSize));
                LOG.debug(NativeBroker.PROPERTY_PAGE_SIZE + ": " + config.get(NativeBroker.PROPERTY_PAGE_SIZE));
            } catch (NumberFormatException nfe) {
                LOG.warn(nfe);
            }
        }
        
        //Unused !
        String collBuffers = con.getAttribute("collection_buffers");
        if (collBuffers != null) {
            try {
                config.put("db-connection.collections.buffers", new Integer(collBuffers));
                LOG.debug("db-connection.collections.buffers: " + config.get("db-connection.collections.buffers"));
            } catch (NumberFormatException nfe) {
                LOG.warn(nfe);
            }
        }
        
        //Unused !
        String wordBuffers = con.getAttribute("words_buffers");
        if (wordBuffers != null)
            try {
                config.put("db-connection.words.buffers", new Integer(wordBuffers));
                LOG.debug("db-connection.words.buffers: " + config.get("db-connection.words.buffers"));
            } catch (NumberFormatException nfe) {
                LOG.warn(nfe);
            }
        
        //Unused !
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
                config.put(NativeBroker.PROPERTY_MIN_FREE_MEMORY, new Integer(freeMem));
                LOG.debug(NativeBroker.PROPERTY_MIN_FREE_MEMORY + ": " + config.get(NativeBroker.PROPERTY_MIN_FREE_MEMORY));
            } catch (NumberFormatException nfe) {
                LOG.warn(nfe);
            }
        }
        
        //Not clear : rather looks like a buffers count
        String collCacheSize = con.getAttribute("collectionCacheSize");
        if (collCacheSize != null) {
            try {
                config.put(BrokerPool.PROPERTY_COLLECTION_CACHE_SIZE, new Integer(collCacheSize));
                LOG.debug(BrokerPool.PROPERTY_COLLECTION_CACHE_SIZE + ": " + config.get(BrokerPool.PROPERTY_COLLECTION_CACHE_SIZE));
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
        
        NodeList poolConf = con.getElementsByTagName(BrokerPool.CONFIGURATION_ELEMENT_NAME);
        if (poolConf.getLength() > 0) {
            configurePool(poolConf);
        }
        NodeList queryPoolConf = con.getElementsByTagName(XQueryPool.CONFIGURATION_ELEMENT_NAME);
        if (queryPoolConf.getLength() > 0) {
            configureXQueryPool(queryPoolConf);
        }
        NodeList watchConf = con.getElementsByTagName(XQueryWatchDog.CONFIGURATION_ELEMENT_NAME);
        if (watchConf.getLength() > 0) {
            configureWatchdog(watchConf);
        }
        NodeList sysTasks = con.getElementsByTagName(SystemTaskConfig.CONFIGURATION_ELEMENT_NAME);
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
            SystemTaskConfig sysTask = new SystemTaskConfig(classAttr);
            String cronAttr = taskDef.getAttribute("cron-trigger");
            String periodAttr = taskDef.getAttribute("period");
            if (cronAttr != null && cronAttr.length() > 0) {
                sysTask.setCronExpr(cronAttr);
            } else {
                if (periodAttr == null || periodAttr.length() == 0)
                    throw new DatabaseConfigurationException("No period or cron-trigger specified for system-task");
                long period;
                try {
                    period = Long.parseLong(periodAttr);
                } catch (NumberFormatException e) {
                    throw new DatabaseConfigurationException("Attribute period is not a number: " + e.getMessage());
                }
                sysTask.setPeriod(period);
            }
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
                config.put(XQueryWatchDog.PROPERTY_QUERY_TIMEOUT, new Long(timeout));
                LOG.debug(XQueryWatchDog.PROPERTY_QUERY_TIMEOUT + ": " + config.get(XQueryWatchDog.PROPERTY_QUERY_TIMEOUT));
            } catch (NumberFormatException e) {
                LOG.warn(e);
            }
        }
        
        String maxOutput = watchDog.getAttribute("output-size-limit");
        if (maxOutput != null) {
            try {
                config.put(XQueryWatchDog.PROPERTY_OUTPUT_SIZE_LIMIT, new Integer(maxOutput));
                LOG.debug(XQueryWatchDog.PROPERTY_OUTPUT_SIZE_LIMIT + ": " + config.get(XQueryWatchDog.PROPERTY_OUTPUT_SIZE_LIMIT));
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
                config.put(XQueryPool.PROPERTY_MAX_STACK_SIZE, new Integer(maxStackSize));
                LOG.debug(XQueryPool.PROPERTY_MAX_STACK_SIZE + ": " + config.get(XQueryPool.PROPERTY_MAX_STACK_SIZE));
            } catch (NumberFormatException e) {
                LOG.warn(e);
            }
        }
        
        String maxPoolSize = queryPool.getAttribute("size");
        if (maxPoolSize != null) {
            try {
                config.put(XQueryPool.PROPERTY_POOL_SIZE, new Integer(maxPoolSize));
                LOG.debug(XQueryPool.PROPERTY_POOL_SIZE + ": " + config.get(XQueryPool.PROPERTY_POOL_SIZE));
            } catch (NumberFormatException e) {
                LOG.warn(e);
            }
        }
        
        String timeout = queryPool.getAttribute("timeout");
        if (timeout != null) {
            try {
                config.put(XQueryPool.PROPERTY_TIMEOUT, new Long(timeout));
                LOG.debug(XQueryPool.PROPERTY_TIMEOUT + ": " + config.get(XQueryPool.PROPERTY_TIMEOUT));
            } catch (NumberFormatException e) {
                LOG.warn(e);
            }
        }
        
        String timeoutCheckInterval = queryPool.getAttribute("timeout-check-interval");
        if (timeoutCheckInterval != null) {
            try {
                config.put(XQueryPool.PROPERTY_TIMEOUT_CHECK_INTERVAL, new Long(timeoutCheckInterval));
                LOG.debug(XQueryPool.PROPERTY_TIMEOUT_CHECK_INTERVAL + ": " + config.get(XQueryPool.PROPERTY_TIMEOUT_CHECK_INTERVAL));
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
                config.put(BrokerPool.PROPERTY_MIN_CONNECTIONS, new Integer(min));
                LOG.debug(BrokerPool.PROPERTY_MIN_CONNECTIONS + ": " + config.get(BrokerPool.PROPERTY_MIN_CONNECTIONS));
            } catch (NumberFormatException e) {
                LOG.warn(e);
            }
        }
        
        String max = pool.getAttribute("max");
        if (max != null) {
            try {
                config.put(BrokerPool.PROPERTY_MAX_CONNECTIONS, new Integer(max));
                LOG.debug(BrokerPool.PROPERTY_MAX_CONNECTIONS + ": " + config.get(BrokerPool.PROPERTY_MAX_CONNECTIONS));
            } catch (NumberFormatException e) {
                LOG.warn(e);
            }
        }
        
        String sync = pool.getAttribute("sync-period");
        if (sync != null) {
            try {
                config.put(BrokerPool.PROPERTY_SYNC_PERIOD, new Long(sync));
                LOG.debug(BrokerPool.PROPERTY_SYNC_PERIOD + ": " + config.get(BrokerPool.PROPERTY_SYNC_PERIOD));
            } catch (NumberFormatException e) {
                LOG.warn(e);
            }
        }
        
        String maxShutdownWait = pool.getAttribute(BrokerPool.PROPERTY_SHUTDOWN_DELAY);
        if (maxShutdownWait != null) {
            try {
                config.put(BrokerPool.PROPERTY_SHUTDOWN_DELAY, new Long(maxShutdownWait));
                LOG.debug(BrokerPool.PROPERTY_SHUTDOWN_DELAY + ": " + config.get(BrokerPool.PROPERTY_SHUTDOWN_DELAY));
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
            config.put(TextSearchEngine.PROPERTY_INDEX_NUMBERS, Boolean.valueOf(parseNum.equals("yes")));
            LOG.debug(TextSearchEngine.PROPERTY_INDEX_NUMBERS + ": " + config.get(TextSearchEngine.PROPERTY_INDEX_NUMBERS));
        }
        
        String stemming = p.getAttribute("stemming");
        if (stemming != null) {
            config.put(TextSearchEngine.PROPERTY_STEM, Boolean.valueOf(stemming.equals("yes")));
            LOG.debug(TextSearchEngine.PROPERTY_STEM + ": " + config.get(TextSearchEngine.PROPERTY_STEM));
        }
        
        String termFreq = p.getAttribute("track-term-freq");
        if (termFreq != null) {
            config.put(TextSearchEngine.PROPERTY_STORE_TERM_FREQUENCY, Boolean.valueOf(termFreq.equals("yes")));
            LOG.debug(TextSearchEngine.PROPERTY_STORE_TERM_FREQUENCY + ": " + config.get(TextSearchEngine.PROPERTY_STORE_TERM_FREQUENCY));
        }
        
        String caseSensitive = p.getAttribute("caseSensitive");
        if (caseSensitive != null) {
            config.put(NativeValueIndex.PROPERTY_INDEX_CASE_SENSITIVE, Boolean.valueOf(caseSensitive.equals("yes")));
            LOG.debug(NativeValueIndex.PROPERTY_INDEX_CASE_SENSITIVE + ": " + config.get(NativeValueIndex.PROPERTY_INDEX_CASE_SENSITIVE));
        }
        
        String suppressWS = p.getAttribute("suppress-whitespace");
        if (suppressWS != null) {
            config.put(Indexer.PROPERTY_SUPPRESS_WHITESPACE, suppressWS);
            LOG.debug(Indexer.PROPERTY_SUPPRESS_WHITESPACE + ": " + config.get(Indexer.PROPERTY_SUPPRESS_WHITESPACE));
        }
        
        String tokenizer = p.getAttribute("tokenizer");
        if (tokenizer != null) {
            config.put(TextSearchEngine.PROPERTY_TOKENIZER, tokenizer);
            LOG.debug(TextSearchEngine.PROPERTY_TOKENIZER + ": " + config.get(TextSearchEngine.PROPERTY_TOKENIZER));
        }
        int depth = 3;
        String indexDepth = p.getAttribute("index-depth");
        if (indexDepth != null) {
            try {
                depth = Integer.parseInt(indexDepth);
                if (depth < 3) {
                    LOG.warn("parameter index-depth should be >= 3 or you will experience a severe " +
                        "performance loss for node updates (XUpdate or XQuery update extensions)");
                    depth = 3;
                }
                config.put(NativeBroker.PROPERTY_INDEX_DEPTH, new Integer(depth));
                LOG.debug(NativeBroker.PROPERTY_INDEX_DEPTH + ": " + config.get(NativeBroker.PROPERTY_INDEX_DEPTH));
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
            IndexSpec spec = new IndexSpec(null, elem);
            config.put("indexer.config", spec);
        }
        
        // stopwords
        NodeList stopwords = p.getElementsByTagName("stopwords");
        if (stopwords.getLength() > 0) {
            String stopwordFile = ((Element) stopwords.item(0)).getAttribute("file");
            File sf = ConfigurationHelper.lookup(stopwordFile, dbHome);
            if (sf.canRead()) {
                config.put("stopwords", stopwordFile);
                LOG.debug("stopwords: " + config.get("stopwords"));
            }
        }
        
        // index modules
        NodeList modules = p.getElementsByTagName("modules");
        if (modules.getLength() > 0) {
            modules = ((Element) modules.item(0)).getElementsByTagName("module");
            IndexModuleConfig modConfig[] = new IndexModuleConfig[modules.getLength()];
            for (int i = 0; i < modules.getLength(); i++) {
                Element elem = (Element) modules.item(i);
                String className = elem.getAttribute("class");
                String id = elem.getAttribute("id");
                if (className == null || className.length() == 0)
                    throw new DatabaseConfigurationException("Required attribute class is missing for module");
                if (id == null || id.length() == 0)
                    throw new DatabaseConfigurationException("Required attribute id is missing for module");
                modConfig[i] = new IndexModuleConfig(id, className, elem);
            }
            config.put("indexer.modules", modConfig);
        }
    }
    
    private void configureValidation(String dbHome, Document doc, NodeList validation) 
                                        throws DatabaseConfigurationException {
        
        // Register custom protocol URL
        // TODO DWES move to different location?
        eXistURLStreamHandlerFactory.init();
        
        Element p = (Element) validation.item(0);
        
        // Determine validation mode
        String mode = p.getAttribute("mode");
        if (mode != null) {
            config.put(XMLReaderObjectFactory.PROPERTY_VALIDATION, mode);
            LOG.debug(XMLReaderObjectFactory.PROPERTY_VALIDATION + ": " 
                + config.get(XMLReaderObjectFactory.PROPERTY_VALIDATION));
        }
        
        
        // Extract catalogs
        LOG.debug("Creating eXist catalog resolver");
        eXistXMLCatalogResolver resolver = new eXistXMLCatalogResolver();
        
        NodeList entityResolver = p.getElementsByTagName("entity-resolver");
        if (entityResolver.getLength() > 0) {
            Element r = (Element) entityResolver.item(0);
            NodeList catalogs = r.getElementsByTagName("catalog");
            
            LOG.debug("Found "+catalogs.getLength()+" catalog uri entries.");
            LOG.debug("Using dbHome="+dbHome);
            
            // Determine webapps directory. SingleInstanceConfiguration cannot
            // be used at this phase. Trick is to check wether dbHOME is
            // pointing to a WEB-INF directory, meaning inside war file)
            File webappHome=null;
            if(dbHome==null){  /// DWES Why? let's make jUnit happy
                webappHome=new File("webapp").getAbsoluteFile();
            } else if(dbHome.endsWith("WEB-INF")){
                webappHome = new File(dbHome).getParentFile().getAbsoluteFile();
            } else {
                webappHome = new File(dbHome, "webapp").getAbsoluteFile();
            }
            LOG.debug("using webappHome="+webappHome.toURI().toString());
            
            // Get and store all URIs
            List allURIs= new ArrayList();
            for (int i = 0; i < catalogs.getLength(); i++) {
                String uri = ((Element) catalogs.item(i)).getAttribute("uri");
                
                if(uri!=null){ // when uri attribute is filled in
                    
                    // Substitute string, creating an uri from a local file
                    if(uri.indexOf("${WEBAPP_HOME}")!=-1){
                        uri=uri.replaceAll("\\$\\{WEBAPP_HOME\\}", webappHome.toURI().toString() );
                    }
                    
                    // Add uri to confiuration
                    LOG.info("Add catalog uri "+uri+"");
                    allURIs.add(uri);
                }
                
            }
            resolver.setCatalogs(allURIs);
            
            // Store all configured URIs
            config.put(XMLReaderObjectFactory.CATALOG_URIS, allURIs);
            
        }
        
        // Store resolver
        config.put(XMLReaderObjectFactory.CATALOG_RESOLVER, resolver);
        
        // cache
        GrammarPool gp = new GrammarPool();
        config.put(XMLReaderObjectFactory.GRAMMER_POOL, gp);
        
    }
    
    
    public String getConfigFilePath() {
        return configFilePath;
    }
    
    public File getExistHome() {
        return existHome;
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
    private Boolean parseBoolean(String value, boolean defaultValue) {
        if(value == null)
            return Boolean.valueOf(defaultValue);
        value = value.toLowerCase();
        return Boolean.valueOf(value.equals("yes") || value.equals("true"));
    }
    
    public int getInteger(String name) {
        Object obj = getProperty(name);
        
        if ((obj == null) || !(obj instanceof Integer))
            return -1;
        
        return ((Integer) obj).intValue();
    }
    
    /**
     * (non-Javadoc)
     *
     * @see org.xml.sax.ErrorHandler#error(org.xml.sax.SAXParseException)
     */
    public void error(SAXParseException exception) throws SAXException {
        System.err.println("error occured while reading configuration file "
            + "[line: " + exception.getLineNumber() + "]:"
            + exception.getMessage());
    }
    
    /**
     * (non-Javadoc)
     *
     * @see org.xml.sax.ErrorHandler#fatalError(org.xml.sax.SAXParseException)
     */
    public void fatalError(SAXParseException exception) throws SAXException {
        System.err.println("error occured while reading configuration file "
            + "[line: " + exception.getLineNumber() + "]:"
            + exception.getMessage());
    }
    
    /**
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
