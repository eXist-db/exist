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

public class Configuration implements ErrorHandler {
    
    private final static Logger LOG = Logger.getLogger(Configuration.class);	//Logger
    protected DocumentBuilder builder = null;									
    protected HashMap config = new HashMap();									//Configuration						
    protected static String file = null;												//config file (conf.xml)
    protected static File existHome = null;
    protected static boolean configDetected = false;
    
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
    //Constructor (wrapper)
    public Configuration(String file) throws DatabaseConfigurationException
	{
        this(file, null);
    }
    
    //Constructor
    public Configuration(String file, String dbHome) throws DatabaseConfigurationException
	{
        try
		{
            InputStream is = null;
        
            //firstly, try to read the configuration from a file within the classpath
            if(file != null)
            {
            	is = Configuration.class.getClassLoader().getResourceAsStream(file);
            	if(is != null)
            	{
            		LOG.info("Reading configuration from classloader");
            	}
            }
            else
            {
            	//Default file name
            	file = "conf.xml";
            }

            //otherise, secondly try to read configuration from file. Guess the location if necessary
            if (is == null) {
            		File eXistHome = getExistHome(dbHome);
            		if (eXistHome == null) {
                      throw new DatabaseConfigurationException("Unable to locate eXist home directory");
            		}
            		File config = lookup(file);
            		if (!config.exists() || !config.canRead()) {
                      throw new DatabaseConfigurationException("Unable to read configuration file at " + config);
            		}
                this.file = config.getAbsolutePath();
                is = new FileInputStream(config);
            }
            
            // Create resolver
            System.out.println("Creating CatalogResolver");
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
                configureIndexer(dbHome, doc, indexer);
            }
            
            //db connection settings
            NodeList dbcon = doc.getElementsByTagName("db-connection");
            if (dbcon.getLength() > 0) {
                configureBackend(dbHome, dbcon);
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
            LOG.warn("error while reading config file: " + file, e);
            throw new DatabaseConfigurationException(e.getMessage());
        }
        catch (ParserConfigurationException cfg)
		{
            LOG.warn("error while reading config file: " + file, cfg);
            throw new DatabaseConfigurationException(cfg.getMessage());
        }
        catch (IOException io)
		{
            LOG.warn("error while reading config file: " + file, io);
            throw new DatabaseConfigurationException(io.getMessage());
        }
    }
    
    private void configureCluster(Element cluster) {
        String protocol = cluster.getAttribute("protocol");
        if(protocol != null) {
            config.put("cluster.protocol", protocol);
        }
        String user = cluster.getAttribute("dbaUser");
        if(user != null) {
            config.put("cluster.user", user);
        }
        String pwd = cluster.getAttribute("dbaPassword");
        if(pwd != null) {
            config.put("cluster.pwd", pwd);
        }
        String dir = cluster.getAttribute("journalDir");
        if(dir != null) {
            config.put("cluster.journalDir", dir);
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
        
        /*Cluster parameters for test*/
        String maxStore = cluster.getAttribute("journalMaxItem");
        if(maxStore == null || maxStore.trim().length()==0) {
            maxStore = "65000";
        }
        config.put("cluster.journal.maxStore", Integer.valueOf(maxStore));
        String shift = cluster.getAttribute("journalIndexShift");
        if(shift == null || shift.trim().length()==0 ) {
            shift = "100";
        }
        config.put("cluster.journal.shift", Integer.valueOf(shift));
        
        System.out.println("IN CONFIGURE");
        System.out.println("cluster.journal.maxStore " + this.getProperty("cluster.journal.maxStore"));
        System.out.println("cluster.journal.shift " + this.getProperty("cluster.journal.shift"));
    }
    
    private void configureXQuery(Element xquery) throws DatabaseConfigurationException {
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
    	
    	String loadDefaults = xacml.getAttribute(XACMLConstants.LOAD_DEFAULT_POLICIES_ATTRIBUTE);
    	config.put(XACMLConstants.LOAD_DEFAULT_POLICIES_PROPERTY, parseBoolean(loadDefaults, true));
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
        }
        String fragmentation = xupdate
                .getAttribute("allowed-fragmentation");
        if (fragmentation != null)
            config.put("xupdate.fragmentation", new Integer(
                    fragmentation));
        String consistencyCheck = xupdate
                .getAttribute("enable-consistency-checks");
        if (consistencyCheck != null)
            config.put("xupdate.consistency-checks", Boolean
                    .valueOf(consistencyCheck.equals("yes")));
    }
    
    /**
     * @param serializers
     */
    private void configureSerializer(NodeList serializers) {
        Element serializer = (Element) serializers.item(0);
        String xinclude = serializer.getAttribute("enable-xinclude");
        if (xinclude != null)
            config.put("serialization.enable-xinclude", xinclude);
        String xsl = serializer.getAttribute("enable-xsl");
        if (xsl != null)
            config.put("serialization.enable-xsl", xsl);
        String indent = serializer.getAttribute("indent");
        if (indent != null)
            config.put("serialization.indent", indent);
        String internalId = serializer.getAttribute("add-exist-id");
        if (internalId != null)
            config.put("serialization.add-exist-id", internalId);
        String tagElementMatches = serializer
                .getAttribute("match-tagging-elements");
        if (tagElementMatches != null)
            config.put("serialization.match-tagging-elements",
                    tagElementMatches);
        String tagAttributeMatches = serializer
                .getAttribute("match-tagging-attributes");
        if (tagAttributeMatches != null)
            config.put("serialization.match-tagging-attributes",
                    tagAttributeMatches);
    }
    
    /**
     * @param dbHome
     * @param dbcon
     * @throws DatabaseConfigurationException
     */
    private void configureBackend(String dbHome, NodeList dbcon) throws DatabaseConfigurationException {
        Element con = (Element) dbcon.item(0);
        String cacheMem = con.getAttribute("cacheSize");
        String pageSize = con.getAttribute("pageSize");
        String dataFiles = con.getAttribute("files");
        String buffers = con.getAttribute("buffers");
        String collBuffers = con.getAttribute("collection_buffers");
        String wordBuffers = con.getAttribute("words_buffers");
        String elementBuffers = con.getAttribute("elements_buffers");
        String freeMem = con.getAttribute("free_mem_min");
        String mysql = con.getAttribute("database");
        if (mysql != null)
            config.put("database", mysql);
        // directory for database files
        if (dataFiles != null) {
        		File df = lookup(dataFiles);
            if (!df.canRead())
                throw new DatabaseConfigurationException(
                        "cannot read data directory: "
                        + df.getAbsolutePath());
            
            config.put("db-connection.data-dir", df.getAbsolutePath());
            LOG.info("data directory = " + df.getAbsolutePath());
        }
        if (cacheMem != null) {
            if (cacheMem.endsWith("M") || cacheMem.endsWith("m"))
                cacheMem = cacheMem.substring(0, cacheMem.length() - 1);
            try {
                config.put("db-connection.cache-size", new Integer(
                        cacheMem));
            } catch (NumberFormatException nfe) {
            }
        }
        if (buffers != null)
            try {
                config.put("db-connection.buffers",
                        new Integer(buffers));
            } catch (NumberFormatException nfe) {
            }
        if (pageSize != null)
            try {
                config.put("db-connection.page-size", new Integer(
                        pageSize));
            } catch (NumberFormatException nfe) {
            }
        if (collBuffers != null)
            try {
                config.put("db-connection.collections.buffers",
                        new Integer(collBuffers));
            } catch (NumberFormatException nfe) {
            }
        if (wordBuffers != null)
            try {
                config.put("db-connection.words.buffers", new Integer(
                        wordBuffers));
            } catch (NumberFormatException nfe) {
            }
        if (elementBuffers != null)
            try {
                config.put("db-connection.elements.buffers",
                        new Integer(elementBuffers));
            } catch (NumberFormatException nfe) {
            }
        if (freeMem != null)
            try {
                config.put("db-connection.min_free_memory",
                        new Integer(freeMem));
            } catch (NumberFormatException nfe) {
            }
        NodeList securityConf = con.getElementsByTagName("security");
        String securityManagerClassName = "org.exist.security.XMLSecurityManager";
        if (securityConf.getLength()>0) {
           securityManagerClassName = ((Element)securityConf.item(0)).getAttribute("class");
        }
        try {
           config.put("db-connection.security.class",config.getClass().forName(securityManagerClassName));
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
        
        option = recovery.getAttribute("sync-on-commit");
        value = true;
        if (option != null) {
            value = option.equals("yes");
        }
        setProperty("db-connection.recovery.sync-on-commit", new Boolean(value));
        
        option = recovery.getAttribute("group-commit");
        value = false;
        if (option != null) {
            value = option.equals("yes");
        }
        setProperty("db-connection.recovery.group-commit", new Boolean(value));
        
        option = recovery.getAttribute("journal-dir");
        if (option != null)
            setProperty("db-connection.recovery.journal-dir", option);
        
        option = recovery.getAttribute("size");
        if (option != null) {
            if (option.endsWith("M") || option.endsWith("m"))
                option = option.substring(0, option.length() - 1);
            try {
                Integer size = new Integer(option);
                setProperty("db-connection.recovery.size-limit", size);
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
    }
    
    /**
     * @param watchConf
     */
    private void configureWatchdog(NodeList watchConf) {
        Element watchDog = (Element) watchConf.item(0);
        String timeout = watchDog.getAttribute("query-timeout");
        String maxOutput = watchDog
                .getAttribute("output-size-limit");
        if (timeout != null) {
            try {
                config.put("db-connection.watchdog.query-timeout",
                        new Long(timeout));
            } catch (NumberFormatException e) {
            }
        }
        if (maxOutput != null) {
            try {
                config.put(
                        "db-connection.watchdog.output-size-limit",
                        new Integer(maxOutput));
            } catch (NumberFormatException e) {
            }
        }
    }
    
    /**
     * @param queryPoolConf
     */
    private void configureXQueryPool(NodeList queryPoolConf) {
        Element queryPool = (Element) queryPoolConf.item(0);
        String maxStackSize = queryPool
                .getAttribute("max-stack-size");
        String maxPoolSize = queryPool.getAttribute("size");
        String timeout = queryPool.getAttribute("timeout");
        String timeoutCheckInterval = queryPool
                .getAttribute("timeout-check-interval");
        if (maxStackSize != null)
            try {
                config.put(
                        "db-connection.query-pool.max-stack-size",
                        new Integer(maxStackSize));
            } catch (NumberFormatException e) {
            }
        if (maxPoolSize != null) {
        	try {
        		config.put("db-connection.query-pool.size", new Integer(maxPoolSize));
        	} catch (NumberFormatException e) {
        	}
        }
        if (timeout != null)
            try {
                config.put("db-connection.query-pool.timeout",
                        new Long(timeout));
            } catch (NumberFormatException e) {
            }
        if (timeoutCheckInterval != null)
            try {
                config
                        .put(
                        "db-connection.query-pool.timeout-check-interval",
                        new Long(timeoutCheckInterval));
            } catch (NumberFormatException e) {
            }
    }
    
    /**
     * @param poolConf
     */
    private void configurePool(NodeList poolConf) {
        Element pool = (Element) poolConf.item(0);
        String min = pool.getAttribute("min");
        String max = pool.getAttribute("max");
        String sync = pool.getAttribute("sync-period");
        String maxShutdownWait = pool
                .getAttribute("wait-before-shutdown");
        if (min != null)
            try {
                config.put("db-connection.pool.min", new Integer(
                        min));
            } catch (NumberFormatException e) {
            }
        if (max != null)
            try {
                config.put("db-connection.pool.max", new Integer(
                        max));
            } catch (NumberFormatException e) {
            }
        if (sync != null)
            try {
                config.put("db-connection.pool.sync-period",
                        new Long(sync));
            } catch (NumberFormatException e) {
            }
        if (maxShutdownWait != null)
            try {
                config.put("db-connection.pool.shutdown-wait",
                        new Long(maxShutdownWait));
            } catch (NumberFormatException e) {
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
        String indexDepth = p.getAttribute("index-depth");
        String stemming = p.getAttribute("stemming");
        String termFreq = p.getAttribute("track-term-freq");
        String suppressWS = p.getAttribute("suppress-whitespace");
        String caseSensitive = p.getAttribute("caseSensitive");
        String tokenizer = p.getAttribute("tokenizer");
        String validation = p.getAttribute("validation");
        String suppressWSmixed = p
                .getAttribute("preserve-whitespace-mixed-content");
        if (parseNum != null)
            config.put("indexer.indexNumbers", Boolean.valueOf(parseNum
                    .equals("yes")));
        if (stemming != null)
            config.put("indexer.stem", Boolean.valueOf(stemming
                    .equals("yes")));
        if (termFreq != null)
            config.put("indexer.store-term-freq", Boolean
                    .valueOf(termFreq.equals("yes")));
        if (caseSensitive != null)
            config.put("indexer.case-sensitive", Boolean
                    .valueOf(caseSensitive.equals("yes")));
        if (suppressWS != null)
            config.put("indexer.suppress-whitespace", suppressWS);
        if (validation != null)
            config.put("indexer.validation", validation);
        if (tokenizer != null)
            config.put("indexer.tokenizer", tokenizer);
        if (indexDepth != null)
            try {
                int depth = Integer.parseInt(indexDepth);
                config.put("indexer.index-depth", new Integer(depth));
            } catch (NumberFormatException e) {
            }
        if (suppressWSmixed != null)
            config.put("indexer.preserve-whitespace-mixed-content",
                    Boolean.valueOf(suppressWSmixed.equals("yes")));
        // index settings
        NodeList cl = doc.getElementsByTagName("index");
        if (cl.getLength() > 0) {
            Element elem = (Element) cl.item(0);
            IndexSpec spec = new IndexSpec(elem);
            config.put("indexer.config", spec);
        }
        // stopwords
        NodeList stopwords = p.getElementsByTagName("stopwords");
        if (stopwords.getLength() > 0) {
            String stopwordFile = ((Element) stopwords.item(0))
            .getAttribute("file");
            File sf = lookup(stopwordFile);
            if (sf.canRead())
                config.put("stopwords", stopwordFile);
        }
        
        eXistCatalogResolver resolver = (eXistCatalogResolver) config.get("resolver");
        NodeList entityResolver = p.getElementsByTagName("entity-resolver");
        
        if (entityResolver.getLength() > 0) {
            Element r = (Element) entityResolver.item(0);
            NodeList catalogs = r.getElementsByTagName("catalog");
            
            // TODO remove. log function does not work yet
            System.out.println("Found "+catalogs.getLength()+" catalog entries.");
            
            for (int i = 0; i < catalogs.getLength(); i++) {
                String catalog = ((Element) catalogs.item(i)).getAttribute("file");
                           
                File catalogFile = lookup(catalog);                
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
     * Note that relative paths are beeing interpreted relative to <code>exist.home</code>.
     * 
     * @param path path to the file or directory
     * @return the file handle
     */
    public static File lookup(String path) {
    		File f = null;
		if (path.startsWith("~") && path.length() > 1) {
			f = new File(System.getProperty("user.home") + path.substring(1));
		} else if (!(new File(path)).isAbsolute()) {
			try {
				return new File(getExistHome(), path);
			} catch (DatabaseConfigurationException e) {
				throw new IllegalStateException(
					"Unable to locate " + path + " because exist home directory cannot be found!"
				);
			}
		} else {
			f = new File(path);
		}
		return f;
    }
    
    /**
     * Returns a file handle for eXist's home directory.
     * <p>
     * If either none of the directories identified by the system properties
     * <code>exist.home</code> and <code>user.home</code> exist or none
     * of them contain a configuration file, this method returns <code>null</code>.
     * 
     * @return the file handle or <code>null</code>
     */
    public static File getExistHome() throws DatabaseConfigurationException {
    		return getExistHome(null);
    }

    /**
     * Returns a file handle for eXist's home directory.
     * <p>
     * If either the proposed home directory does not exist, does not contain a
     * configuration file or none of the directories identified by the system properties
     * <code>exist.home</code> and <code>user.home</code> exist or none of
     * them contains a configuration file, this method returns <code>null</code>.
     * 
     * @param path path to eXist home directory
     * @return the file handle or <code>null</code>
     */
    public static File getExistHome(String path) throws DatabaseConfigurationException {
    		if (configDetected || existHome != null)
    			return existHome;
    		
    		String home = null;
    		configDetected = true;
    		if (path != null) {
    			if (path.startsWith("~") && path.length() > 1) {
    				path = System.getProperty("user.home") + path.substring(1);
    			}
    			home = path;
    		} else {
			home = System.getProperty("exist.home");
			if (home == null) {
				LOG.info("Environment variabe 'exist.home' not set.");
				home = System.getProperty("user.home");
				LOG.info("Trying home directory '" + home + "' to locate eXist configuration ...");
			} else if (home.startsWith("~") && home.length() > 1) {
				home = System.getProperty("user.home") + home.substring(1);
			}
    		}

		// check if we found an existing directory
		File homeDir = new File(home);
		if (!homeDir.exists()) {
			LOG.warn("Unable to find exist home directory: " + home + " does not exist!");
			homeDir = new File(System.getProperty("user.dir"));
			LOG.info("Trying working directory '" + homeDir + "' to locate eXist configuration ...");
		} else if (!homeDir.isDirectory()) {
			LOG.warn("Unable to find exist home directory: " + home + " is not a directory!");
			homeDir = new File(System.getProperty("user.dir"));
			LOG.info("Trying working directory '" + homeDir + "' to locate eXist configuration ...");
		}
		
		// see if the config file is there...
		File config = new File(homeDir, "conf.xml");
		if (!config.exists()) {
			homeDir = new File(System.getProperty("user.dir"));
			config = new File(homeDir, "conf.xml");
			LOG.info("Trying working directory '" + homeDir + "' to locate eXist configuration ...");
			if (!config.exists()) {
				LOG.warn("Unable to find exist home directory!");
				return null;
			}
		}
		
		// done!
		LOG.info("Configuring eXist using " + config);
		existHome = homeDir;
		return homeDir;
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
