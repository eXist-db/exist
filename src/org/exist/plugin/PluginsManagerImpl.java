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
import org.exist.collections.Collection;
import org.exist.config.*;
import org.exist.config.annotation.*;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

/**
 * Plugins manager. 
 * It control search procedure, activation and de-actication (including runtime).
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
@ConfigurationClass("plugin-manager")
public class PluginsManagerImpl implements Configurable, PluginsManager {

	private final static Logger LOG = Logger.getLogger(PluginsManagerImpl.class);

	public final static XmldbURI PLUGINS_COLLETION_URI = XmldbURI.SYSTEM_COLLECTION_URI.append("plugins");
	public final static XmldbURI CONFIG_FILE_URI = XmldbURI.create("config.xml");

	@ConfigurationFieldAsAttribute("version")
	private String version = "1.0";

	@ConfigurationFieldAsElement("plugin")
	private List<String> runPlugins = new ArrayList<String>();

//	@ConfigurationFieldAsElement("search-path")
//	private Map<String, File> placesToSearch = new LinkedHashMap<String, File>();

//	private Map<String, PluginInfo> foundClasses = new LinkedHashMap<String, PluginInfo>();
	
	private Map<String, Jack> jacks = new HashMap<String, Jack>();
	
	private Configuration configuration = null;
	
	private Collection collection;
	
	private Database db;
	
	public PluginsManagerImpl(Database db, DBBroker broker) throws ConfigurationException {
		this.db = db;
		
        TransactionManager transaction = db.getTransactionManager();
        Txn txn = null;

        try {
	        collection = broker.getCollection(PLUGINS_COLLETION_URI);
			if (collection == null) {
				txn = transaction.beginTransaction();
				collection = broker.getOrCreateCollection(txn, PLUGINS_COLLETION_URI);
				if (collection == null) return;
					//if db corrupted it can lead to unrunnable issue
					//throw new ConfigurationException("Collection '/db/system/plugins' can't be created.");
				
				collection.setPermissions(0770);
				broker.saveCollection(txn, collection);

				transaction.commit(txn);
			} 
        } catch (Exception e) {
			transaction.abort(txn);
			e.printStackTrace();
			LOG.debug("loading configuration failed: " + e.getMessage());
		}

        Configuration _config_ = Configurator.parse(this, broker, collection, CONFIG_FILE_URI);
		configuration = Configurator.configure(this, _config_);
		
		//load plugins by META-INF/services/
		try {
//			File libFolder = new File(((BrokerPool)db).getConfiguration().getExistHome(), "lib");
//			File pluginsFolder = new File(libFolder, "plugins");
//			placesToSearch.put(pluginsFolder.getAbsolutePath(), pluginsFolder);
			
			for (Class<? extends Jack> plugin : listServices(Jack.class)) {
				//System.out.println("found plugin "+plugin);
				
				try {
					Constructor<? extends Jack> ctor = plugin.getConstructor(PluginsManager.class);
					Jack plgn = ctor.newInstance(this);
					
					jacks.put(plugin.getName(), plgn);
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
		//load defined at configuration
		for (String className : runPlugins) {
			//check if already run
			if (jacks.containsKey(className))
				continue;
			
			try {
				Class<? extends Jack> plugin = (Class<? extends Jack>) Class.forName(className);
				
				Constructor<? extends Jack> ctor = plugin.getConstructor(PluginsManager.class);
				Jack plgn = ctor.newInstance(this);
				
				jacks.put(plugin.getName(), plgn);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}
	
	public String version() {
		return version;
	}
	
	public void shutdown() {
		for (Jack plugin : jacks.values()) {
			try {
				plugin.stop();
			} catch (Throwable e) {
				LOG.error(e);
			}
		}
	}
	
	public Database getDatabase() {
		return db;
	}
	
	/*
	 * Generate list of service implementations 
	 */
	private <S> Iterable<Class<? extends S>> listServices(Class<S> ifc) throws Exception {
		ClassLoader ldr = Thread.currentThread().getContextClassLoader();
		Enumeration<URL> e = ldr.getResources("META-INF/services/" + ifc.getName());
		Set<Class<? extends S>> services = new HashSet<Class<? extends S>>();
		while (e.hasMoreElements()) {
			URL url = e.nextElement();
			InputStream is = url.openStream();
			try {
				BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
				while (true) {
					String line = r.readLine();
					if (line == null)
						break;
					int comment = line.indexOf('#');
					if (comment >= 0)
						line = line.substring(0, comment);
					String name = line.trim();
					if (name.length() == 0)
						continue;
					Class<?> clz = Class.forName(name, true, ldr);
					Class<? extends S> impl = clz.asSubclass(ifc);
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
}
