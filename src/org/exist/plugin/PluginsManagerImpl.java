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
package org.exist.plugin;

import java.io.*;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.*;
import org.apache.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.LifeCycle;
import org.exist.backup.BackupHandler;
import org.exist.backup.RestoreHandler;
import org.exist.collections.Collection;
import org.exist.config.*;
import org.exist.config.annotation.*;
import org.exist.dom.DocumentAtExist;
import org.exist.security.Permission;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Plugins manager. 
 * It control search procedure, activation and de-actication (including runtime).
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
@ConfigurationClass("plugin-manager")
public class PluginsManagerImpl implements Configurable, PluginsManager, LifeCycle {

	private final static Logger LOG = Logger.getLogger(PluginsManagerImpl.class);

	public final static XmldbURI COLLETION_URI = XmldbURI.SYSTEM.append("plugins");
	public final static XmldbURI CONFIG_FILE_URI = XmldbURI.create("config.xml");

	@ConfigurationFieldAsAttribute("version")
	private String version = "1.0";

	@ConfigurationFieldAsElement("plugin")
	private List<String> runPlugins = new ArrayList<String>();

//	@ConfigurationFieldAsElement("search-path")
//	private Map<String, File> placesToSearch = new LinkedHashMap<String, File>();

//	private Map<String, PluginInfo> foundClasses = new LinkedHashMap<String, PluginInfo>();
	
	private Map<String, Plug> jacks = new HashMap<String, Plug>();
	
	private Configuration configuration = null;
	
	private Collection collection;
	
	private Database db;
	
	public PluginsManagerImpl(Database db, DBBroker broker) throws ConfigurationException {
		this.db = db;
		
		//Temporary for testing
		addPlugin("org.exist.scheduler.SchedulerManager");
		addPlugin("org.exist.storage.md.MDStorageManager");
		addPlugin("org.exist.monitoring.MonitoringManager");
	}

	@Override
	public void start(DBBroker broker) throws EXistException {
        final TransactionManager transaction = db.getTransactionManager();
        Txn txn = null;

        try {
	        collection = broker.getCollection(COLLETION_URI);
			if (collection == null) {
				txn = transaction.beginTransaction();
				collection = broker.getOrCreateCollection(txn, COLLETION_URI);
				if (collection == null) {return;}
					//if db corrupted it can lead to unrunnable issue
					//throw new ConfigurationException("Collection '/db/system/plugins' can't be created.");
				
				collection.setPermissions(Permission.DEFAULT_SYSTEM_SECURITY_COLLECTION_PERM);
				broker.saveCollection(txn, collection);

				transaction.commit(txn);
			} 
        } catch (final Exception e) {
			transaction.abort(txn);
			e.printStackTrace();
			LOG.debug("loading configuration failed: " + e.getMessage());
		} finally {
            transaction.close(txn);
        }

        final Configuration _config_ = Configurator.parse(this, broker, collection, CONFIG_FILE_URI);
		configuration = Configurator.configure(this, _config_);
		
		//load plugins by META-INF/services/
		try {
//			File libFolder = new File(((BrokerPool)db).getConfiguration().getExistHome(), "lib");
//			File pluginsFolder = new File(libFolder, "plugins");
//			placesToSearch.put(pluginsFolder.getAbsolutePath(), pluginsFolder);
			
			for (final Class<? extends Plug> plugin : listServices(Plug.class)) {
				//System.out.println("found plugin "+plugin);
				
				try {
					final Constructor<? extends Plug> ctor = plugin.getConstructor(PluginsManager.class);
					final Plug plgn = ctor.newInstance(this);
					
					jacks.put(plugin.getName(), plgn);
				} catch (final Throwable e) {
					e.printStackTrace();
				}
			}
		} catch (final Throwable e) {
			e.printStackTrace();
		}
		//UNDERSTAND: call save?

//		try {
//			configuration.save(broker);
//		} catch (PermissionDeniedException e) {
//			//LOG?
//		}
		
		for (final Plug jack : jacks.values()) {
			if (jack instanceof LifeCycle) {
				((LifeCycle) jack).start(broker);
			}
		}
	}
	
	@Override
	public void sync(DBBroker broker) {
		for (final Plug plugin : jacks.values()) {
			try {
				plugin.sync(broker);
			} catch (final Throwable e) {
				LOG.error(e);
			}
		}
	}

	@Override
	public void stop(DBBroker broker) throws EXistException {
		for (final Plug plugin : jacks.values()) {
			try {
				plugin.stop(broker);
			} catch (final Throwable e) {
				LOG.error(e);
			}
		}
	}
	
	public String version() {
		return version;
	}
	
	@SuppressWarnings("unchecked")
	public void addPlugin(String className) {
		//check if already run
		if (jacks.containsKey(className))
			{return;}
		
		try {
			final Class<? extends Plug> plugin = (Class<? extends Plug>) Class.forName(className);
			
			final Constructor<? extends Plug> ctor = plugin.getConstructor(PluginsManager.class);
			final Plug plgn = ctor.newInstance(this);
			
			jacks.put(plugin.getName(), plgn);

			runPlugins.add(className);
			
			//TODO: if (jack instanceof Startable) { ((Startable) jack).startUp(broker); }
		} catch (final Throwable e) {
//			e.printStackTrace();
		}
	}

	public Database getDatabase() {
		return db;
	}
	
	/*
	 * Generate list of service implementations 
	 */
	private <S> Iterable<Class<? extends S>> listServices(Class<S> ifc) throws Exception {
		final ClassLoader ldr = Thread.currentThread().getContextClassLoader();
		final Enumeration<URL> e = ldr.getResources("META-INF/services/" + ifc.getName());
		final Set<Class<? extends S>> services = new HashSet<Class<? extends S>>();
		while (e.hasMoreElements()) {
			final URL url = e.nextElement();
			final InputStream is = url.openStream();
			try {
				final BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
				while (true) {
					String line = r.readLine();
					if (line == null)
						{break;}
					final int comment = line.indexOf('#');
					if (comment >= 0)
						{line = line.substring(0, comment);}
					final String name = line.trim();
					if (name.length() == 0)
						{continue;}
					final Class<?> clz = Class.forName(name, true, ldr);
					final Class<? extends S> impl = clz.asSubclass(ifc);
					services.add(impl);
				}
			} finally {
				is.close();
			}
		}
		return services;
	}

	@Override
	public boolean isConfigured() {
		return configuration != null;
	}

	@Override
	public Configuration getConfiguration() {
		return configuration;
	}
	
	@Override
	public BackupHandler getBackupHandler(Logger logger) {
		return new BH(logger);
	}
	
	class BH implements BackupHandler {
		Logger LOG;
		
		public BH(Logger logger) {
			LOG = logger;
		}

		@Override
		public void backup(Collection colection, AttributesImpl attrs) {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof BackupHandler) {
					try {
						((BackupHandler) plugin).backup(colection, attrs);
					} catch (final Exception e) {
						LOG.error(e.getMessage(), e);
					}
				}
			}
		}

		@Override
		public void backup(Collection colection, SAXSerializer serializer) throws SAXException {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof BackupHandler) {
					try {
						((BackupHandler) plugin).backup(colection, serializer);
					} catch (final Exception e) {
						LOG.error(e.getMessage(), e);
					}
				}
			}
		}

		@Override
		public void backup(DocumentAtExist document, AttributesImpl attrs) {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof BackupHandler) {
					try {
						((BackupHandler) plugin).backup(document, attrs);
					} catch (final Exception e) {
						LOG.error(e.getMessage(), e);
					}
				}
			}
		}

		@Override
		public void backup(DocumentAtExist document, SAXSerializer serializer) throws SAXException {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof BackupHandler) {
					try {
						((BackupHandler) plugin).backup(document, serializer);
					} catch (final Exception e) {
						LOG.error(e.getMessage(), e);
					}
				}
			}
		}
	}

	private RestoreHandler rh = new RH();

	@Override
	public RestoreHandler getRestoreHandler() {
		return rh;
	}

	class RH implements RestoreHandler {

		@Override
		public void setDocumentLocator(Locator locator) {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof RestoreHandler) {
					((RestoreHandler) plugin).setDocumentLocator(locator);
				}
			}
		}

		@Override
		public void startDocument() throws SAXException {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof RestoreHandler) {
					((RestoreHandler) plugin).startDocument();
				}
			}
		}

		@Override
		public void endDocument() throws SAXException {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof RestoreHandler) {
					((RestoreHandler) plugin).endDocument();
				}
			}
		}

		@Override
		public void startPrefixMapping(String prefix, String uri) throws SAXException {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof RestoreHandler) {
					((RestoreHandler) plugin).startPrefixMapping(prefix, uri);
				}
			}
		}

		@Override
		public void endPrefixMapping(String prefix) throws SAXException {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof RestoreHandler) {
					((RestoreHandler) plugin).endPrefixMapping(prefix);
				}
			}
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof RestoreHandler) {
					((RestoreHandler) plugin).startElement(uri, localName, qName, atts);
				}
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof RestoreHandler) {
					((RestoreHandler) plugin).endElement(uri, localName, qName);
				}
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof RestoreHandler) {
					((RestoreHandler) plugin).characters(ch, start, length);
				}
			}
		}

		@Override
		public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof RestoreHandler) {
					((RestoreHandler) plugin).ignorableWhitespace(ch, start, length);
				}
			}
		}

		@Override
		public void processingInstruction(String target, String data) throws SAXException {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof RestoreHandler) {
					((RestoreHandler) plugin).processingInstruction(target, data);
				}
			}
		}

		@Override
		public void skippedEntity(String name) throws SAXException {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof RestoreHandler) {
					((RestoreHandler) plugin).skippedEntity(name);
				}
			}
		}

		@Override
		public void startCollectionRestore(Collection colection, Attributes atts) {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof RestoreHandler) {
					((RestoreHandler) plugin).startCollectionRestore(colection, atts);
				}
			}
		}

		@Override
		public void endCollectionRestore(Collection colection) {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof RestoreHandler) {
					((RestoreHandler) plugin).endCollectionRestore(colection);
				}
			}
		}

		@Override
		public void startDocumentRestore(DocumentAtExist document, Attributes atts) {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof RestoreHandler) {
					((RestoreHandler) plugin).startDocumentRestore(document, atts);
				}
			}
		}

		@Override
		public void endDocumentRestore(DocumentAtExist document) {
			for (final Plug plugin : jacks.values()) {
				if (plugin instanceof RestoreHandler) {
					((RestoreHandler) plugin).endDocumentRestore(document);
				}
			}
		}
	}
}