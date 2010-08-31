/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
package org.exist.config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.config.annotation.ConfigurationFieldAsElement;
import org.exist.config.annotation.ConfigurationFieldSettings;
import org.exist.dom.DocumentAtExist;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementAtExist;
import org.exist.memtree.SAXAdapter;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.ConfigurationHelper;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Configurator {
	
	protected final static Logger LOG = Logger.getLogger(Configurator.class);

	protected static ConcurrentMap<XmldbURI, Configuration> hotConfigs = new ConcurrentHashMap<XmldbURI, Configuration>();
	
	private static Map<Class<Configurable>, Map<String, Field>> map = 
		new HashMap<Class<Configurable>, Map<String, Field>>();
	
	protected static Map<String, Field> getProperyFieldMap(Class<?> clazz) {
		if (map.containsKey(clazz))
			return map.get(clazz);
			
		Map<String, Field> link = new HashMap<String, Field>();
    	for (Field field : clazz.getDeclaredFields()) {
    		if (field.isAnnotationPresent(ConfigurationFieldAsAttribute.class)) {
    			link.put(field.getAnnotation(ConfigurationFieldAsAttribute.class).value(), field);
    		
    		} else if (field.isAnnotationPresent(ConfigurationFieldAsElement.class)) {
    			link.put(field.getAnnotation(ConfigurationFieldAsElement.class).value(), field);
    		}
    	}
    	
    	Class<?> superClass = clazz.getSuperclass();
		if (superClass.isAnnotationPresent(ConfigurationClass.class)) {
			//if (superClass.getAnnotation(ConfigurationClass.class).value().equals( clazz.getAnnotation(ConfigurationClass.class).value() ))
				link.putAll( getProperyFieldMap(superClass) );
		}

    	return link;
	}
	
	public static Method searchForSetMethod(Class<?> clazz, Field field) {
		try {
			String methodName = "set"+field.getName();
			methodName = methodName.toLowerCase();
			
			for (Method method : clazz.getMethods()) {
				if (method.getName().toLowerCase().equals(methodName))
					return method;
			}
			
		} catch (SecurityException e) {
		} catch (NoClassDefFoundError e) {
		}
		
		return null;
	}
	
	public static Configuration configure(Configurable instance, Configuration configuration) {
		
		if (configuration == null) return null;
		
		Class<?> clazz = instance.getClass();
		instance.getClass().getAnnotations();
		if (!clazz.isAnnotationPresent(ConfigurationClass.class)) {
			//LOG.info("no configuration name at "+instance.getClass());
			return null;
		}
		
		String configName = clazz.getAnnotation(ConfigurationClass.class).value();
		
		Configuration config = configuration.getConfiguration(configName);
		if (config == null) {
			System.out.println("no configuration ["+configName+"]");
			return null;
		}
		
		if (config instanceof ConfigurationImpl) {
			ConfigurationImpl impl = (ConfigurationImpl) config;
			
			//XXX: lock issue here, fix it
			Configurable configurable = null;
			
			if (impl.configuredObjectReferene != null)
				configurable = impl.configuredObjectReferene.get();
			
			if (configurable != null) {
				if (configurable != instance)
					throw new IllegalArgumentException(
							"Configuration can't be used by "+instance+", " +
							"because allready in use by "+configurable);
			
			} else 
				impl.configuredObjectReferene = new WeakReference<Configurable>(instance);
			//end (lock issue)
		}
		
		return configureByCurrent(instance, config);
	}

	private static Configuration configureByCurrent(Configurable instance, Configuration configuration) {
		Map<String, Field> properyFieldMap = getProperyFieldMap(instance.getClass());
		Set<String> properties = configuration.getProperties();
		
		if (properties.size() == 0) {
			//LOG.info("no properties for "+instance.getClass()+" @ "+configuration);
			return configuration;
		}
		
		//process simple types: String, int, long, boolean
		for (String property : properties) {
			if (!properyFieldMap.containsKey(property)) {
				System.out.println("unused property "+property+" @"+configuration.getName());
				continue;
			}
			
			Field field = properyFieldMap.get(property);
			field.setAccessible(true);
			
			Object value = null;
			String typeName = field.getType().getName();
			try {
				if (typeName.equals("java.lang.String")) {
					value = configuration.getProperty(property);

				} else if (typeName.equals("int") || typeName.equals("java.lang.Integer")) {

		    		if (field.isAnnotationPresent(ConfigurationFieldSettings.class)) {
		    			String settings = field.getAnnotation(ConfigurationFieldSettings.class).value();
		    			int radix = 10;
		    			if (settings.startsWith("radix=")) {
		    				radix = Integer.valueOf(settings.substring(6));
		    			}
		    			value = Integer.valueOf( configuration.getProperty(property), radix );
		    			
		    		} else {
						value = configuration.getPropertyInteger(property);
						
		    		}

				} else if (typeName.equals("long") || typeName.equals("java.lang.Long")) {
					value = configuration.getPropertyLong(property);

				} else if (typeName.equals("boolean") || typeName.equals("java.lang.Boolean")) {
					value = configuration.getPropertyBoolean(property);

				} else if (typeName.equals("java.util.List")) {
					//skip, it will be processed as structure
					
				} else if (typeName.equals("org.exist.xmldb.XmldbURI")) {
					value = org.exist.xmldb.XmldbURI.create(
								configuration.getProperty(property)
							);
					
				} else {
					LOG.warn("skip unsupported configuration value type "+field.getType());
				}
			
				if (value != null && !value.equals( field.get(instance) ) ) {
					
					Method method = searchForSetMethod(instance.getClass(), field);
					if (method != null) {
						try {
							method.invoke(instance, value);
						} catch (InvocationTargetException e) {
							method = null;
						}
					}
					if (method == null) 
						field.set(instance, value);
				}

			} catch (IllegalArgumentException e) {
				LOG.error("configuration error: \n" +
						" config: "+configuration.getName()+"\n" +
						" property: "+property+"\n" +
						" message: "+e.getMessage());
				return null; //XXX: throw configuration error
			} catch (IllegalAccessException e) {
				LOG.error("security error: "+e.getMessage());
				return null; //XXX: throw configuration error
			}
		}
		
		//process simple structures: List
		try {
			for (Field field : properyFieldMap.values()) {
				String typeName = field.getType().getName();
	
				if (typeName.equals("java.util.List")) {
					if (!field.isAnnotationPresent(ConfigurationFieldAsElement.class)) {
						LOG.warn("Wrong annotation for strucure: "+field.getName()+", list can't be configurated throw attribute.");
						continue;
					}
						
					String property = field.getAnnotation(ConfigurationFieldAsElement.class).value();
					
					field.setAccessible(true);
					List<Configurable> list = (List<Configurable>) field.get(instance);
					
					List<Configuration> confs = configuration.getConfigurations(property);

					if (list == null) {
						list = new ArrayList<Configurable>(confs.size());
						field.set(instance, list);
					}
					
					if (confs != null) {
						//remove & update
						for (Iterator<Configurable> iterator = list.iterator() ; iterator.hasNext() ; ) {
							Configurable obj = iterator.next();
							Configuration current_conf = obj.getConfiguration();
							if (current_conf == null) {
								//skip internal staff
								if (obj instanceof org.exist.security.internal.RealmImpl) {
									//TODO: static list
									continue;
								} else {
									LOG.warn("Unconfigured instance ["+obj+"], remove the object.");
									iterator.remove();
									continue;
								}
							}
							
							String id = current_conf.getProperty(Configuration.ID);
							if (id == null) {
								LOG.warn("Subconfiguration must have id ["+obj+"], remove the object.");
								iterator.remove();
								continue;
							}
							
							for (Iterator<Configuration> i = confs.iterator() ; i.hasNext() ;) {
								Configuration conf = i.next();
								if (id.equals( conf.getProperty(Configuration.ID) )) {
									current_conf.checkForUpdates(conf.getElement());
									i.remove();
									break;
								}
							}
							
							LOG.info("Configuration was removed, remove the object ["+obj+"].");
							iterator.remove();
						}

						//create
						for (Configuration conf : confs) {
							String id = conf.getProperty(Configuration.ID);
							if (id == null) {
								LOG.warn("Subconfiguration must have id ["+conf+"], skip instance creation.");
								continue;
							}
							
							String clazzName = "org.exist.security.realm."+id.toLowerCase()+"."+id+"Realm";
							Class<?> clazz;
							try {
								clazz = Class.forName(clazzName);
								Constructor<Configurable> constructor = 
									(Constructor<Configurable>) clazz.getConstructor(instance.getClass(), Configuration.class);
								
								list.add( constructor.newInstance(instance, conf) );
							
							} catch (ClassNotFoundException e) {
								LOG.warn("Class ["+clazzName+"] not found, skip instance creation.");
								continue;
							} catch (SecurityException e) {
								LOG.warn("Security exception on class ["+clazzName+"] creation, skip instance creation.");
								continue;
							} catch (NoSuchMethodException e) {
								LOG.warn("Class ["+clazzName+"] not found, skip instance creation.");
								continue;
							} catch (InstantiationException e) {
								LOG.warn("Instantiation exception on class ["+clazzName+"] creation, skip instance creation.");
								continue;
							} catch (InvocationTargetException e) {
								LOG.warn("Invocation target exception on class ["+clazzName+"] creation, skip instance creation.");
								continue;
							}
							
						}
					}
				}
			}
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			return null;
		} catch (IllegalAccessException e) {
			e.printStackTrace();
			return null;
		}
		
		return configuration;
	}

//	public static Configuration parse(InputStream is) throws ExceptionConfiguration {
//		throw new ExceptionConfiguration("parser was not implemented");
//	}

	public static Configuration parse(File file) throws ConfigurationException {
		try {
			return parse(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			throw new ConfigurationException(e);
		}
	}

//	public static Configuration parseDefault() throws ExceptionConfiguration {
//		throw new ExceptionConfiguration("default configuration parser was not implemented");
//	}

	public static Configuration parse(InputStream is) throws ConfigurationException {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);
			InputSource src = new InputSource(is);
			SAXParser parser = factory.newSAXParser();
			XMLReader reader = parser.getXMLReader();
			SAXAdapter adapter = new SAXAdapter();
			reader.setContentHandler(adapter);
			reader.parse(src);
    
			return new ConfigurationImpl((ElementAtExist) adapter.getDocument().getDocumentElement());
		} catch (ParserConfigurationException e) {
			throw new ConfigurationException(e);
		} catch (SAXException e) {
			throw new ConfigurationException(e);
		} catch (IOException e) {
			throw new ConfigurationException(e);
		}
	}
	
	public static Configuration parseDefault() throws ConfigurationException {
		try {
			return parse(new FileInputStream(ConfigurationHelper.lookup("conf.xml")));
		} catch (FileNotFoundException e) {
			throw new ConfigurationException(e);
		}
	}

	protected static void asXMLtoBuffer(Configurable instance, StringBuilder buf) throws ConfigurationException {
		Class<?> clazz = instance.getClass();
		instance.getClass().getAnnotations();
		if (!clazz.isAnnotationPresent(ConfigurationClass.class)) {
			return; //UNDERSTAND: throw exception
		}
		
		String configName = clazz.getAnnotation(ConfigurationClass.class).value();

		//open tag
		buf.append("<");
		buf.append(configName);
		buf.append(" xmlns='"+Configuration.NS+"'");
		
		StringBuilder bufContext = new StringBuilder();
		StringBuilder bufferToUse;
		boolean simple = true;
		
		//store filed's values as attributes or elements depends on annotation
		Map<String, Field> properyFieldMap = getProperyFieldMap(instance.getClass());
		for (Entry<String, Field> entry : properyFieldMap.entrySet()) {

			final Field field = entry.getValue();
			field.setAccessible(true);

			try {
				//skip null values
				if (field.get(instance) == null) continue;
			
				boolean storeAsAttribute = true;
				if (field.isAnnotationPresent(ConfigurationFieldAsElement.class)) {
					storeAsAttribute = false;
				}
				
				if (storeAsAttribute) {
					buf.append(" ");
					buf.append(entry.getKey());
					buf.append("='");
					
					bufferToUse = buf;
				} else {
					bufferToUse = new StringBuilder();
				}
				
				String typeName = field.getType().getName();

				if (typeName.equals("java.lang.String")) {
					bufferToUse.append(field.get(instance));
					
				} else if (typeName.equals("int") || typeName.equals("java.lang.Integer")) {
					if (field.isAnnotationPresent(ConfigurationFieldSettings.class)) {
		    			String settings = field.getAnnotation(ConfigurationFieldSettings.class).value();
		    			int radix = 10;
		    			if (settings.startsWith("radix=")) {
		    				try {
		    					radix = Integer.valueOf(settings.substring(6));
		    				} catch (Exception e) {
		    					//UNDERSTAND: ignore, set back to default or throw error? 
		    					radix = 10;
							}
		    			}
		    			bufferToUse.append(Integer.toString((Integer)field.get(instance), radix) );
					} else { 
						bufferToUse.append(field.get(instance));
					}
				} else if (typeName.equals("long") || typeName.equals("java.lang.Long")) {
					bufferToUse.append(field.get(instance));

				} else if (typeName.equals("boolean") || typeName.equals("java.lang.Boolean")) {
					if ((Boolean) field.get(instance)) {
						bufferToUse.append("true");
					} else { 
						bufferToUse.append("false");
					}

				} else if (typeName.equals("java.util.List")) {
					simple = false;
					
					@SuppressWarnings("unchecked")
					List<Configurable> list = (List<Configurable>) field.get(instance);
					for (Configurable el : list) {
						asXMLtoBuffer(el, bufferToUse);
					}

				} else {
					LOG.warn("field '"+field.getName()+"' have unsupported type ["+typeName+"] - skiped");
					//unsupported type - skip
					//buf.append(field.get(instance));
				}

	    		if (storeAsAttribute) {
	    			buf.append("'");
	    		} else if (bufferToUse.length() > 0){
	    			if (simple) {
	    				bufContext.append("<");
	    				bufContext.append(entry.getKey());
	    				bufContext.append(">");
	    			}
	
	    			bufContext.append(bufferToUse);
	    			
	    			if (simple) {
	    				bufContext.append("</");
	    				bufContext.append(entry.getKey());
	    				bufContext.append(">");
	    			}
	    		}
			} catch (IllegalArgumentException e) {
				throw new ConfigurationException(e.getMessage(), e);

			} catch (IllegalAccessException e) {
				throw new ConfigurationException(e.getMessage(), e);
			
			}
		}

		buf.append(">");

		buf.append(bufContext);
		
		//close tag
		buf.append("</");
		buf.append(configName);
		buf.append(">");
	}
	
	public static Configuration parse(Configurable instance, DBBroker broker, Collection collection, XmldbURI fileURL) throws ConfigurationException {
		Configuration conf;
		synchronized (hotConfigs) {
			conf = hotConfigs.get(collection.getURI().append(fileURL));
		}
		if (conf != null) return conf;
		
		//XXX: locking required
		DocumentAtExist document = collection.getDocument(broker, fileURL);
		
		if (document == null) {
			if (broker.isReadOnly()) {
				//database in read-only mode & there no configuration file, 
				//create in memory document & configuration 
				try {
					StringBuilder buf = new StringBuilder();

					asXMLtoBuffer(instance, buf);

					if (buf.length() == 0)
						return null;
					
					return parse( new ByteArrayInputStream(buf.toString().getBytes("UTF-8")) );
				} catch (UnsupportedEncodingException e) {
					return null;
				}
			} else {
				
				try {
					document = save(instance, broker, collection, fileURL);
				} catch (IOException e) {
					return null;
				}
				
			}
		}
		
		if (document == null)
			return null; //possible on corrupted database, find better solution (recovery flag?)
			//throw new ConfigurationException("The configuration file can't be found, url = "+collection.getURI().append(fileURL));
		
		ElementAtExist confElement = (ElementAtExist) document.getDocumentElement();

		if (confElement == null)
			return null; //possible on corrupted database, find better solution (recovery flag?)
			//throw new ConfigurationException("The configuration file is empty, url = "+collection.getURI().append(fileURL));
		
		conf = new ConfigurationImpl(confElement);
		
		synchronized (hotConfigs) {
			hotConfigs.put(document.getURI(), conf);
		}
		
		return conf;
	}
	
	public static Configuration parse(DocumentAtExist document) throws ConfigurationException {
		if (document == null) return null;
		
		Configuration conf;
		synchronized (hotConfigs) {
			conf = hotConfigs.get(document.getURI());
		}
		
		if (conf != null) return conf;
		
		ElementAtExist confElement = (ElementAtExist) document.getDocumentElement();

		if (confElement == null)
			return null; //possible on corrupted database, find better solution (recovery flag?)
			//throw new ConfigurationException("The configuration file is empty, url = "+collection.getURI().append(fileURL));
		
		conf = new ConfigurationImpl(confElement);
		
		synchronized (hotConfigs) {
			hotConfigs.put(document.getURI(), conf);
		}
		
		return conf;
	}

	
	public static DocumentAtExist save(Configurable instance, XmldbURI uri) throws IOException, ConfigurationException {
		BrokerPool database;
		try {
			database = BrokerPool.getInstance();
		} catch (EXistException e) {
			throw new IOException(e);
		}

		DBBroker broker = null;
		try {
			broker = database.get(null);

			Collection collection = broker.getCollection(uri.removeLastSegment());
			if (collection == null) throw new IOException("Collection URI = "+uri.removeLastSegment()+" not found.");

			return save(instance, broker, collection, uri.lastSegment());
		} catch (EXistException e) {
			throw new IOException(e);
			
		} finally {
			database.release(broker);
		}
		
		
	}

	public static DocumentAtExist save(Configurable instance, DBBroker broker, Collection collection, XmldbURI uri) throws IOException, ConfigurationException {

		StringBuilder buf = new StringBuilder();
		
		asXMLtoBuffer(instance, buf);
		
		if (buf.length() == 0)
			return null;

		BrokerPool pool = broker.getBrokerPool();
		TransactionManager transact = pool.getTransactionManager();
		Txn txn = transact.beginTransaction();

		LOG.info("STORING CONFIGURATION collection = "+collection.getURI()+" document = "+uri);
		
		Subject currentUser = broker.getUser();
		try {
			broker.setUser(pool.getSecurityManager().getSystemSubject());
			
            String data = buf.toString();

            txn.acquireLock(collection.getLock(), Lock.WRITE_LOCK);

			IndexInfo info = collection.validateXMLResource(txn, broker, uri, data);

            DocumentImpl doc = info.getDocument();

            doc.getMetadata().setMimeType(MimeType.XML_TYPE.getName());
			doc.setPermissions(0770);

			collection.store(txn, broker, info, data, false);
			
			broker.saveCollection(txn, doc.getCollection());
			
			transact.commit(txn);
			
		} catch (Exception e) {
			transact.abort(txn);
			
			e.printStackTrace();
			
			throw new IOException(e);

		} finally {
			broker.setUser(currentUser);
		}
	
		broker.flush();
		broker.sync(Sync.MAJOR_SYNC);
		
		return collection.getDocument(broker, uri.lastSegment());
	}

	public static synchronized void clear() {
		synchronized (hotConfigs) {
			for (Configuration conf : hotConfigs.values()) {
				if (conf instanceof ConfigurationImpl) {
					((ConfigurationImpl) conf).configuredObjectReferene = null;
				}
			}
			
			hotConfigs.clear();
		}
		map.clear();
	}

	public static void unregister(Configuration configuration) {
		if (configuration == null) return;
		
		synchronized (hotConfigs) {
			if (hotConfigs.containsValue(configuration)) { 
				for (Entry<XmldbURI, Configuration> entry : hotConfigs.entrySet()) {
					if (entry.getValue() == configuration) {
						hotConfigs.remove(entry.getKey());
						return;
					}
				}
			}
		}
	}
}