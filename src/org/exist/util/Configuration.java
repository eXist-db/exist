/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang M. Meier
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 *  $Id$
 */
package org.exist.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.exist.memtree.SAXAdapter;
import org.exist.storage.IndexSpec;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

public class Configuration implements ErrorHandler {

    private final static Logger LOG = Logger.getLogger(Configuration.class);

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
    
    protected DocumentBuilder builder = null;

    protected HashMap config = new HashMap();

    protected String file = null;

    public Configuration(String file) throws DatabaseConfigurationException {
        this(file, null);
    }

    public Configuration(String file, String dbHome)
            throws DatabaseConfigurationException {
        try {
            InputStream is = null;
            // first try to read the configuration from a file within the
            // classpath
            if (file == null) {
                is = Configuration.class.getClassLoader()
                    .getResourceAsStream(file);
                if (is != null) {
                    LOG.info("Reading configuration from classloader");
                } else
                    file = "conf.xml";
            }
            if (is == null) {
                // try to read configuration from file. Guess the location if
                // necessary
                File f = new File(file);
                if ((!f.isAbsolute()) && dbHome != null) {
                    file = dbHome + File.separatorChar + file;
                    f = new File(file);
                }
                if (!f.canRead()) {
                    LOG
                            .info("unable to read configuration. Trying to guess location ...");

                    // fall back and try to read from home directory
                    if (dbHome == null) {
                        // try to determine exist home directory
                        dbHome = System.getProperty("exist.home");

                        if (dbHome == null)
                            dbHome = System.getProperty("user.dir");
                    }

                    if (dbHome != null)
                        file = dbHome + File.separatorChar + file;
                    f = new File(file);
                    if (!f.canRead()) {
                        LOG.warn("giving up");
                        throw new DatabaseConfigurationException(
                                "unable to read configuration file");
                    }
                }
                this.file = file;
                is = new FileInputStream(file);
            }

			CatalogResolver resolver = new CatalogResolver(true);
	        System.setProperty("xml.catalog.verbosity", "10");
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
            Element root = doc.getDocumentElement();

            // indexer settings
            NodeList indexer = doc.getElementsByTagName("indexer");
            if (indexer.getLength() > 0) {
                configureIndexer(dbHome, doc, indexer);
            }

            NodeList dbcon = doc.getElementsByTagName("db-connection");
            if (dbcon.getLength() > 0) {
                configureBackend(dbHome, dbcon); 
            }

            NodeList serializers = doc.getElementsByTagName("serializer");
            if (serializers.getLength() > 0) {
                configureSerializer(serializers);
            }

            NodeList xupdates = doc.getElementsByTagName("xupdate");
            if (xupdates.getLength() > 0) {
                configureXUpdate(xupdates);
            }
			
			NodeList xquery = doc.getElementsByTagName("xquery");
            if (xquery.getLength() > 0) {
                configureXQuery((Element) xquery.item(0));
            }
        } catch (SAXException e) {
            LOG.warn("error while reading config file: " + file, e);
            throw new DatabaseConfigurationException(e.getMessage());
        } catch (ParserConfigurationException cfg) {
            LOG.warn("error while reading config file: " + file, cfg);
            throw new DatabaseConfigurationException(cfg.getMessage());
        } catch (IOException io) {
            LOG.warn("error while reading config file: " + file, io);
            throw new DatabaseConfigurationException(io.getMessage());
        }
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
            File df = new File(dataFiles);
            if ((!df.isAbsolute()) && dbHome != null) {
                dataFiles = dbHome + File.separatorChar + dataFiles;
                df = new File(dataFiles);
            }
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
    }

    private void configureRecovery(NodeList nodes) {
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
        
        option = recovery.getAttribute("journal-dir");
        if (option != null)
            setProperty("db-connection.recovery.journal-dir", option);
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
    private void configureIndexer(String dbHome, Document doc, NodeList indexer) throws DatabaseConfigurationException, MalformedURLException, IOException {
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
            File sf = new File(stopwordFile);

            if (!sf.canRead()) {
                stopwordFile = dbHome + File.separatorChar + stopwordFile;
                sf = new File(stopwordFile);
            }
            if (sf.canRead())
                config.put("stopwords", stopwordFile);
        }
		
		CatalogResolver resolver = (CatalogResolver) config.get("resolver");
        NodeList entityResolver = p
                .getElementsByTagName("entity-resolver");
        if (entityResolver.getLength() > 0) {
            Element r = (Element) entityResolver.item(0);
            NodeList catalogs = r.getElementsByTagName("catalog");
            String catalog;
            File catalogFile;

            for (int i = 0; i < catalogs.getLength(); i++) {
                catalog = ((Element) catalogs.item(i))
                        .getAttribute("file");

                if (File.separatorChar == '\\')
                    catalog = catalog.replace('/', '\\');

                if (dbHome == null)
                    catalogFile = new File(catalog);
                else
                    catalogFile = new File(dbHome + File.separatorChar + catalog);
                if (catalogFile.exists()) {
                    resolver.getCatalog().parseCatalog(
                            catalogFile.getAbsolutePath());
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

    public String getPath() {
        return file;
    }

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
