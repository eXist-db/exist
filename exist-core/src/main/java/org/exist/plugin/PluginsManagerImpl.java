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
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.*;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.LifeCycle;
import org.exist.backup.BackupHandler;
import org.exist.backup.RestoreHandler;
import org.exist.collections.Collection;
import org.exist.config.*;
import org.exist.config.Configuration;
import org.exist.config.annotation.*;
import org.exist.security.Permission;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import static java.lang.invoke.MethodType.methodType;

/**
 * Plugins manager.
 * It control search procedure, activation and de-actication (including runtime).
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
@ConfigurationClass("plugin-manager")
public class PluginsManagerImpl implements Configurable, BrokerPoolService, PluginsManager, LifeCycle {

    private static final Logger LOG = LogManager.getLogger(PluginsManagerImpl.class);

    private static final XmldbURI COLLETION_URI = XmldbURI.SYSTEM.append("plugins");
    private static final XmldbURI CONFIG_FILE_URI = XmldbURI.create("config.xml");

    @ConfigurationFieldAsAttribute("version")
    private String version = "1.0";

    @ConfigurationFieldAsElement("plugin")
    private List<String> runPlugins = new ArrayList<>();

    private Map<String, Plug> jacks = new HashMap<>();

    private Configuration configuration = null;

    private Collection collection;

    private Database db;

	@Override
	public void prepare(final BrokerPool brokerPool) {
		this.db = brokerPool;

		//Temporary for testing
		addPlugin("org.exist.scheduler.SchedulerManager");
		addPlugin("org.exist.storage.md.MDStorageManager");
	}

	@Override
	public void startSystem(final DBBroker systemBroker) throws BrokerPoolServiceException {
		try {
			start(systemBroker);
		} catch(final EXistException e) {
			throw new BrokerPoolServiceException(e);
		}
	}

    @Override
	public void start(DBBroker broker) throws EXistException {
        final TransactionManager transaction = broker.getBrokerPool().getTransactionManager();

        boolean interrupted = false;
        try {
            try (final Txn txn = transaction.beginTransaction()) {
                collection = broker.getCollection(COLLETION_URI);
                if (collection == null) {
                    collection = broker.getOrCreateCollection(txn, COLLETION_URI);
                    if (collection == null) {
                        return;
                    }
                    //if db corrupted it can lead to unrunnable issue
                    //throw new ConfigurationException("Collection '/db/system/plugins' can't be created.");

                    collection.setPermissions(Permission.DEFAULT_SYSTEM_SECURITY_COLLECTION_PERM);
                    broker.saveCollection(txn, collection);
                }

                transaction.commit(txn);
            } catch (final Exception e) {
                LOG.warn("Loading PluginsManager configuration failed: " + e.getMessage());
            }

            final Configuration _config_ = Configurator.parse(this, broker, collection, CONFIG_FILE_URI);
            configuration = Configurator.configure(this, _config_);

            //load plugins by META-INF/services/
            try {

                final MethodHandles.Lookup lookup = MethodHandles.lookup();

                for (final Class<? extends Plug> pluginClazz : listServices(Plug.class)) {
                    try {
                        final MethodHandle methodHandle = lookup.findConstructor(pluginClazz, methodType(void.class, PluginsManager.class));
                        final Function<PluginsManager, Plug> ctor = (Function<PluginsManager, Plug>)
                                LambdaMetafactory.metafactory(
                                        lookup, "apply", methodType(Function.class),
                                        methodHandle.type().erase(), methodHandle, methodHandle.type()).getTarget().invokeExact();

                        final Plug plgn = ctor.apply(this);

                        jacks.put(pluginClazz.getName(), plgn);
                    } catch (final Throwable e) {
                        if (e instanceof InterruptedException) {
                            // NOTE: must set interrupted flag
                            interrupted = true;
                        }
                        LOG.error(e);
                    }
                }
            } catch (final Throwable e) {
                LOG.error(e);
            }
            //UNDERSTAND: call save?

//		try {
//			configuration.save(broker);
//		} catch (PermissionDeniedException e) {
//			//LOG?
//		}

            for (final Plug jack : jacks.values()) {
                jack.start(broker);
            }
        } finally {
            if (interrupted) {
                // NOTE: must set interrupted flag
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void sync(final DBBroker broker) {
        for (final Plug plugin : jacks.values()) {
            try {
                plugin.sync(broker);
            } catch (final Throwable e) {
                LOG.error(e);
            }
        }
    }

	@Override
	public void stop(final DBBroker broker) {
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

	@Override
	public Database getDatabase() {
		return db; //TODO(AR) get rid of this, maybe expand the BrokerPoolService arch to replace PluginsManagerImpl etc
	}

	@SuppressWarnings("unchecked")
    @Override
	public void addPlugin(final String className) {
		//check if already run
		if (jacks.containsKey(className))
			{return;}
		
		try {
			final Class<? extends Plug> pluginClazz = (Class<? extends Plug>) Class.forName(className);

            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            final MethodHandle methodHandle = lookup.findConstructor(pluginClazz, methodType(void.class, PluginsManager.class));
            final Function<PluginsManager, Plug> ctor = (Function<PluginsManager, Plug>)
                    LambdaMetafactory.metafactory(
                            lookup, "apply", methodType(Function.class),
                            methodHandle.type().erase(), methodHandle, methodHandle.type()).getTarget().invokeExact();

			final Plug plgn = ctor.apply(this);

			jacks.put(pluginClazz.getName(), plgn);
			runPlugins.add(className);
			
			//TODO: if (jack instanceof Startable) { ((Startable) jack).startUp(broker); }
		} catch (final Throwable e) {
            if (e instanceof InterruptedException) {
                // NOTE: must set interrupted flag
                Thread.currentThread().interrupt();
            }
            LOG.warn(e);
		}
	}

    /*
     * Generate list of service implementations
     */
    private <S> Iterable<Class<? extends S>> listServices(final Class<S> ifc) throws Exception {
        final ClassLoader ldr = Thread.currentThread().getContextClassLoader();
        final Enumeration<URL> e = ldr.getResources("META-INF/services/" + ifc.getName());
        final Set<Class<? extends S>> services = new HashSet<>();
        while (e.hasMoreElements()) {
            final URL url = e.nextElement();
            try (final InputStream is = url.openStream();
                 final BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) {

                    final int comment = line.indexOf('#');
                    if (comment >= 0) {
                        line = line.substring(0, comment);
                    }
                    final String name = line.trim();
                    if (name.length() == 0) {
                        continue;
                    }
                    final Class<?> clz = Class.forName(name, true, ldr);
                    final Class<? extends S> impl = clz.asSubclass(ifc);
                    services.add(impl);
                }
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
    public BackupHandler getBackupHandler(final Logger logger) {
        return new BH(logger);
    }

    class BH implements BackupHandler {
        Logger LOG;

        BH(final Logger logger) {
            LOG = logger;
        }

        @Override
        public void backup(final Collection colection, final AttributesImpl attrs) {
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
        public void backup(final Collection colection, final SAXSerializer serializer) {
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
        public void backup(final Document document, final AttributesImpl attrs) {
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
        public void backup(final Document document, final SAXSerializer serializer) {
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
        public void setDocumentLocator(final Locator locator) {
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
        public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
            for (final Plug plugin : jacks.values()) {
                if (plugin instanceof RestoreHandler) {
                    ((RestoreHandler) plugin).startPrefixMapping(prefix, uri);
                }
            }
        }

        @Override
        public void endPrefixMapping(final String prefix) throws SAXException {
            for (final Plug plugin : jacks.values()) {
                if (plugin instanceof RestoreHandler) {
                    ((RestoreHandler) plugin).endPrefixMapping(prefix);
                }
            }
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes atts) throws SAXException {
            for (final Plug plugin : jacks.values()) {
                if (plugin instanceof RestoreHandler) {
                    ((RestoreHandler) plugin).startElement(uri, localName, qName, atts);
                }
            }
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) throws SAXException {
            for (final Plug plugin : jacks.values()) {
                if (plugin instanceof RestoreHandler) {
                    ((RestoreHandler) plugin).endElement(uri, localName, qName);
                }
            }
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) throws SAXException {
            for (final Plug plugin : jacks.values()) {
                if (plugin instanceof RestoreHandler) {
                    ((RestoreHandler) plugin).characters(ch, start, length);
                }
            }
        }

        @Override
        public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
            for (final Plug plugin : jacks.values()) {
                if (plugin instanceof RestoreHandler) {
                    ((RestoreHandler) plugin).ignorableWhitespace(ch, start, length);
                }
            }
        }

        @Override
        public void processingInstruction(final String target, final String data) throws SAXException {
            for (final Plug plugin : jacks.values()) {
                if (plugin instanceof RestoreHandler) {
                    ((RestoreHandler) plugin).processingInstruction(target, data);
                }
            }
        }

        @Override
        public void skippedEntity(final String name) throws SAXException {
            for (final Plug plugin : jacks.values()) {
                if (plugin instanceof RestoreHandler) {
                    ((RestoreHandler) plugin).skippedEntity(name);
                }
            }
        }

        @Override
        public void startCollectionRestore(final Collection colection, final Attributes atts) {
            for (final Plug plugin : jacks.values()) {
                if (plugin instanceof RestoreHandler) {
                    ((RestoreHandler) plugin).startCollectionRestore(colection, atts);
                }
            }
        }

        @Override
        public void endCollectionRestore(final Collection colection) {
            for (final Plug plugin : jacks.values()) {
                if (plugin instanceof RestoreHandler) {
                    ((RestoreHandler) plugin).endCollectionRestore(colection);
                }
            }
        }

        @Override
        public void startDocumentRestore(final Document document, final Attributes atts) {
            for (final Plug plugin : jacks.values()) {
                if (plugin instanceof RestoreHandler) {
                    ((RestoreHandler) plugin).startDocumentRestore(document, atts);
                }
            }
        }

        @Override
        public void endDocumentRestore(final Document document) {
            for (final Plug plugin : jacks.values()) {
                if (plugin instanceof RestoreHandler) {
                    ((RestoreHandler) plugin).endDocumentRestore(document);
                }
            }
        }
    }
}
