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
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.exist.storage.IndexPaths;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
/**
 * Description of the Class
 *
 * @author Wolfgang Meier
 *
 */
public class Configuration implements ErrorHandler {
	
	private final static Logger LOG = 
		Logger.getLogger( Configuration.class );
		
	protected DocumentBuilder builder = null;
	protected HashMap config = new HashMap();
	protected String file = null;

	/**
	 * Constructor for the Configuration object
	 *
	 * @param file Description of the Parameter
	 *
	 * @exception DatabaseConfigurationException Description of the Exception
	 */
	public Configuration(String file) throws DatabaseConfigurationException {
		this(file, null);
	}

	/**
	 * Constructor for the Configuration object
	 *
	 * @param file Description of the Parameter
	 * @param dbHome Description of the Parameter
	 *
	 * @exception DatabaseConfigurationException Description of the Exception
	 */
	public Configuration(String file, String dbHome)
		throws DatabaseConfigurationException {
		BasicConfigurator.configure();
		try {
			String pathSep = System.getProperty("file.separator", "/");
	
			File f = new File(file);
			if((!f.isAbsolute()) && dbHome != null) {
				file = dbHome + pathSep + file;
				f = new File(file);
			}
			if (!f.canRead()) {
				LOG.info("unable to read configuration. Trying to guess location ...");
				
				// fall back and try to read from home directory
				if (dbHome == null) {
					// try to determine exist home directory
					dbHome = System.getProperty("exist.home");

					if (dbHome == null)
						dbHome = System.getProperty("user.dir");
				}

				if (dbHome != null)
					file = dbHome + pathSep + file;
				f = new File(file);
				if(!f.canRead()) {
					LOG.warn("giving up");
					throw new DatabaseConfigurationException("unable to read configuration file");
				}
			}
			this.file = file;
				
			// initialize xml parser
			DocumentBuilderFactory factory =
				DocumentBuilderFactory.newInstance();
			builder = factory.newDocumentBuilder();
			builder.setErrorHandler(this);
			InputSource src = new InputSource(new FileReader(file));
			Document doc = builder.parse(src);
			Element root = doc.getDocumentElement();
			NodeList parser = doc.getElementsByTagName("indexer");

			if (parser.getLength() > 0) {
				Element p = (Element) parser.item(0);
				String tmp = p.getAttribute("tmpDir");
				String batchLoad = p.getAttribute("batchLoad");
				String parseNum = p.getAttribute("parseNumbers");
				String indexDepth = p.getAttribute("index-depth");
				String stemming = p.getAttribute("stemming");
				String ctlDir = p.getAttribute("controls");
				String suppressWS = p.getAttribute("suppress-whitespace");
				String caseSensitive = p.getAttribute("caseSensitive");
				String tokenizer = p.getAttribute("tokenizer");
				String validation = p.getAttribute("validation");

				if (tmp != null)
					config.put("tmpDir", tmp);

				if (batchLoad != null)
					config.put(
						"batchLoad",
						new Boolean(batchLoad.equals("true")));

				if (parseNum != null)
					config.put(
						"indexer.indexNumbers",
						new Boolean(parseNum.equals("true")));

				if (ctlDir != null)
					config.put("parser.ctlDir", ctlDir);

				if (stemming != null)
					config.put("indexer.stem", new Boolean(stemming.equals("true")));

				if (caseSensitive != null)
					config.put(
						"indexer.case-sensitive",
						new Boolean(caseSensitive.equals("true")));

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

				NodeList index = p.getElementsByTagName("index");

				Map indexPathMap = new TreeMap();
				config.put("indexer.map", indexPathMap);
				for (int i = 0; i < index.getLength(); i++) {
					Element idx = (Element) index.item(i);
					String doctype = idx.getAttribute("doctype");
					String def = idx.getAttribute("default");
					IndexPaths paths = new IndexPaths(def.equals("all"));
					String indexAttributes = idx.getAttribute("attributes");
					if (indexAttributes != null)
						paths.setIncludeAttributes(
							indexAttributes.equals("true"));

					String indexAlphaNum = idx.getAttribute("alphanum");
					if (indexAlphaNum != null)
						paths.setIncludeAlphaNum(indexAlphaNum.equals("true"));

					indexDepth = idx.getAttribute("index-depth");
					if (indexDepth != null)
						try {
							int depth = Integer.parseInt(indexDepth);
							paths.setIndexDepth(depth);
						} catch (NumberFormatException e) {
						}

					NodeList include = idx.getElementsByTagName("include");
					String ps;

					for (int j = 0; j < include.getLength(); j++) {
						ps = ((Element) include.item(j)).getAttribute("path");
						paths.addInclude(ps);
					}

					indexPathMap.put(doctype, paths);
					
					NodeList exclude = idx.getElementsByTagName("exclude");

					for (int j = 0; j < exclude.getLength(); j++) {
						ps = ((Element) exclude.item(j)).getAttribute("path");
						paths.addExclude(ps);
					}
				}

				NodeList stopwords = p.getElementsByTagName("stopwords");

				if (stopwords.getLength() > 0) {
					String stopwordFile =
						((Element) stopwords.item(0)).getAttribute("file");
					File sf = new File(stopwordFile);

					if (!sf.canRead()) {
						stopwordFile = dbHome + pathSep + stopwordFile;
						sf = new File(stopwordFile);
					}
					if (sf.canRead())
						config.put("stopwords", stopwordFile);
				}

				CatalogResolver resolver = new CatalogResolver(true);
				//System.setProperty("xml.catalog.verbosity", "10");
				config.put("resolver", resolver);

				NodeList entityResolver =
					p.getElementsByTagName("entity-resolver");

				if (entityResolver.getLength() > 0) {
					Element r = (Element) entityResolver.item(0);
					NodeList catalogs = r.getElementsByTagName("catalog");
					String catalog;
					File catalogFile;

					for (int i = 0; i < catalogs.getLength(); i++) {
						catalog =
							((Element) catalogs.item(i)).getAttribute("file");

						if (pathSep.equals("\\"))
							catalog = catalog.replace('/', '\\');

						if (dbHome == null)
							catalogFile = new File(catalog);
						else
							catalogFile = new File(dbHome + pathSep + catalog);
						if (catalogFile.exists()) {
							resolver.getCatalog().parseCatalog(
								catalogFile.getAbsolutePath());
						}
					}
				}
			}

			NodeList dbcon = doc.getElementsByTagName("db-connection");

			if (dbcon.getLength() > 0) {
				Element con = (Element) dbcon.item(0);
				String cacheMem = con.getAttribute("cacheSize");
				String pageSize = con.getAttribute("pageSize");
				String dataFiles = con.getAttribute("files");
				String buffers = con.getAttribute("buffers");
				String collBuffers = con.getAttribute("collection_buffers");
				String wordBuffers = con.getAttribute("words_buffers");
				String elementBuffers = con.getAttribute("elements_buffers");
				String freeMem = con.getAttribute("free_mem_min");
				String driver = con.getAttribute("driver");
				String url = con.getAttribute("url");
				String user = con.getAttribute("user");
				String pass = con.getAttribute("password");
				String mysql = con.getAttribute("database");
				String service = con.getAttribute("serviceName");
				String encoding = con.getAttribute("encoding");

				if (driver != null)
					config.put("driver", driver);

				if (url != null)
					config.put("url", url);

				if (user != null)
					config.put("user", user);

				if (pass != null)
					config.put("password", pass);

				if (mysql != null)
					config.put("database", mysql);

				if (encoding != null)
					config.put("encoding", encoding);

				if (service != null)
					config.put("db-connection.serviceName", service);

				// directory for database files
				if (dataFiles != null) {
					File df = new File(dataFiles);
					if ((!df.isAbsolute()) && dbHome != null) {
						dataFiles = dbHome + pathSep + dataFiles;
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
					if(cacheMem.endsWith("M") || cacheMem.endsWith("m"))
						cacheMem = cacheMem.substring(0, cacheMem.length() - 1);
					try {
						config.put(
							"db-connection.cache-size",
							new Integer(cacheMem));
					} catch (NumberFormatException nfe) {
					}
				}

				if (buffers != null)
					try {
						config.put(
							"db-connection.buffers",
							new Integer(buffers));
					} catch (NumberFormatException nfe) {
					}

				if (pageSize != null)
					try {
						config.put(
							"db-connection.page-size",
							new Integer(pageSize));
					} catch (NumberFormatException nfe) {
					}

				if (collBuffers != null)
					try {
						config.put(
							"db-connection.collections.buffers",
							new Integer(collBuffers));
					} catch (NumberFormatException nfe) {
					}

				if (wordBuffers != null)
					try {
						config.put(
							"db-connection.words.buffers",
							new Integer(wordBuffers));
					} catch (NumberFormatException nfe) {
					}

				if (elementBuffers != null)
					try {
						config.put(
							"db-connection.elements.buffers",
							new Integer(elementBuffers));
					} catch (NumberFormatException nfe) {
					}

				if (freeMem != null)
					try {
						config.put(
							"db-connection.min_free_memory",
							new Integer(freeMem));
					} catch (NumberFormatException nfe) {
					}
				NodeList poolConf = con.getElementsByTagName("pool");
				if(poolConf.getLength() > 0) {
					Element pool = (Element)poolConf.item(0);
					String min = pool.getAttribute("min");
					String max = pool.getAttribute("max");
					String sync = pool.getAttribute("sync-period");
					if(min != null)
						try {
							config.put("db-connection.pool.min",
								new Integer(min));
						} catch(NumberFormatException e) {
						}
					if(max != null)
						try {
							config.put("db-connection.pool.max",
								new Integer(max));
						} catch(NumberFormatException e) {
						}
					if(sync!= null)
						try {
							config.put("db-connection.pool.sync-period",
								new Long(sync));
						} catch(NumberFormatException e) {
						}
				}
			}

			NodeList serializers = doc.getElementsByTagName("serializer");
			Element serializer;
			if(serializers.getLength() > 0) {
				serializer = (Element)serializers.item(0);
				String xinclude = serializer.getAttribute("enable-xinclude");
				if(xinclude != null)
					config.put("serialization.enable-xinclude", xinclude);
				String xsl = serializer.getAttribute("enable-xsl");
				if(xsl != null)
					config.put("serialization.enable-xsl", xsl);
				String indent = serializer.getAttribute("indent");
				if(indent != null)
					config.put("serialization.indent", indent);
                String internalId = serializer.getAttribute("add-exist-id");
                if(internalId != null)
                    config.put("serialization.add-exist-id", internalId);
                String tagElementMatches = serializer.getAttribute("match-tagging-elements");
                if(tagElementMatches != null)
                	config.put("serialization.match-tagging-elements", tagElementMatches);
                String tagAttributeMatches = serializer.getAttribute("match-tagging-attributes");
                if(tagAttributeMatches != null)
                	config.put("serialization.match-tagging-attributes", tagAttributeMatches);
			}
			
			NodeList log4j = doc.getElementsByTagName("log4j:configuration");

			if (log4j.getLength() > 0) {
				Element logRoot = (Element) log4j.item(0);

				// make files relative if dbHome != null
				if (dbHome != null) {
					NodeList params = logRoot.getElementsByTagName("param");
					Element param;
					String name;
					String path;

					if (pathSep.equals("\\"))
						dbHome = dbHome.replace('\\', '/');

					for (int i = 0; i < params.getLength(); i++) {
						param = (Element) params.item(i);
						name = param.getAttribute("name");

						if ((name != null) && name.equalsIgnoreCase("File")) {
							path = param.getAttribute("value");

							if (path != null) {
								//if ( pathSep.equals( "\\" ) )
								//    path = path.replace( '/', '\\' );
								f = new File(path);

								if (!f.isAbsolute())
									path = dbHome + '/' + path;
							}

							param.setAttribute("value", path);
						}
					}
				}

				DOMConfigurator.configure(logRoot);
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

	/**
	 * Gets the integer attribute of the Configuration object
	 *
	 * @param name Description of the Parameter
	 *
	 * @return The integer value
	 */
	public int getInteger(String name) {
		Object obj = getProperty(name);

		if ((obj == null) || !(obj instanceof Integer))
			return -1;

		return ((Integer) obj).intValue();
	}

	/**
	 * Gets the path attribute of the Configuration object
	 *
	 * @return The path value
	 */
	public String getPath() {
		return file;
	}

	/**
	 * Gets the property attribute of the Configuration object
	 *
	 * @param name Description of the Parameter
	 *
	 * @return The property value
	 */
	public Object getProperty(String name) {
		return config.get(name);
	}

	/**
	 * Description of the Method
	 *
	 * @param name Description of the Parameter
	 *
	 * @return Description of the Return Value
	 */
	public boolean hasProperty(String name) {
		return config.containsKey(name);
	}

	/**
	 * Sets the property attribute of the Configuration object
	 *
	 * @param name The new property value
	 * @param obj The new property value
	 */
	public void setProperty(String name, Object obj) {
		config.put(name, obj);
	}
	/* (non-Javadoc)
	 * @see org.xml.sax.ErrorHandler#error(org.xml.sax.SAXParseException)
	 */
	public void error(SAXParseException exception) throws SAXException {
		System.err.println("error occured while reading configuration file " +
			"[line: " + exception.getLineNumber() + "]:" +
			exception.getMessage());
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ErrorHandler#fatalError(org.xml.sax.SAXParseException)
	 */
	public void fatalError(SAXParseException exception) throws SAXException {
		System.err.println("error occured while reading configuration file " +
					"[line: " + exception.getLineNumber() + "]:" +
					exception.getMessage());
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ErrorHandler#warning(org.xml.sax.SAXParseException)
	 */
	public void warning(SAXParseException exception) throws SAXException {
		System.err.println("error occured while reading configuration file " +
					"[line: " + exception.getLineNumber() + "]:" +
					exception.getMessage());
	}

}
