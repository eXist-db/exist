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
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationField;
import org.exist.config.annotation.ConfigurationFieldSettings;
import org.exist.dom.DocumentAtExist;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementAtExist;
import org.exist.memtree.SAXAdapter;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.ConfigurationHelper;
import org.exist.util.LockException;
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
	
	private static Map<Class<Configurable>, Map<String, Field>> map = 
		new HashMap<Class<Configurable>, Map<String, Field>>();
	
	private static Map<String, Field> getProperyFieldMap(Class<?> clazz) {
		if (map.containsKey(clazz))
			return map.get(clazz);
			
		Map<String, Field> link = new HashMap<String, Field>();
    	for (Field field : clazz.getDeclaredFields())
    		if (field.isAnnotationPresent(ConfigurationField.class)) {
    			link.put(field.getAnnotation(ConfigurationField.class).value(), field);
    		}
    	
    	Class<?> superClass = clazz.getSuperclass();
		if (superClass.isAnnotationPresent(ConfigurationClass.class)) {
			if (superClass.getAnnotation(ConfigurationClass.class).value().equals( clazz.getAnnotation(ConfigurationClass.class).value() ))
				link.putAll( getProperyFieldMap(superClass) );
		}

    	return link;
	}
	
	public static Configuration configure(Configurable instance, Configuration configuration) {
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
		
		return configureByCurrent(instance, config);
	}

	private static Configuration configureByCurrent(Configurable instance, Configuration configuration) {
		Map<String, Field> properyFieldMap = getProperyFieldMap(instance.getClass());
		Set<String> properties = configuration.getProperties();
		
		if (properties.size() == 0) {
			//LOG.info("no properties for "+instance.getClass()+" @ "+configuration);
			return configuration;
		}
		
		for (String property : properties) {
			if (!properyFieldMap.containsKey(property)) {
				System.out.println("unused property "+property+" @"+configuration.getName());
				continue;
			}
			
			Field field = properyFieldMap.get(property);
			String typeName = field.getType().getName();
			try {
				if (typeName.equals("java.lang.String")) {
					String value = configuration.getProperty(property);
					if (value != null) {
						field.setAccessible(true);
						field.set(instance, value);
					}
				} else if (typeName.equals("int") || typeName.equals("java.lang.Integer")) {

					Integer value;
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
		    		
					if (value != null) {
						field.setAccessible(true);
						field.set(instance, value);
					}
				} else if (typeName.equals("long") || typeName.equals("java.lang.Long")) {
					Long value = configuration.getPropertyLong(property);
					if (value != null) {
						field.setAccessible(true);
						field.set(instance, value);
					}
				} else if (typeName.equals("boolean") || typeName.equals("java.lang.Boolean")) {
					Boolean value = configuration.getPropertyBoolean(property);
					if (value != null) {
						field.setAccessible(true);
						field.set(instance, value);
					}
				} else {
					throw new IllegalArgumentException("unsupported configuration value type "+field.getType());
				}
			} catch (IllegalArgumentException e) {
				System.out.println("configuration error: \n" +
						" config: "+configuration.getName()+"\n" +
						" property: "+property+"\n" +
						" message: "+e.getMessage());
				return null; //XXX: throw configuration error
			} catch (IllegalAccessException e) {
				System.out.println("security error: "+e.getMessage());
				return null; //XXX: throw configuration error
			}
		}
		
		return configuration;
	}

//	public static Configuration parse(InputStream is) throws ExceptionConfiguration {
//		throw new ExceptionConfiguration("parser was not implemented");
//	}

	public static Configuration parse(File file) throws ExceptionConfiguration {
		try {
			return parse(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			throw new ExceptionConfiguration(e);
		}
	}

//	public static Configuration parseDefault() throws ExceptionConfiguration {
//		throw new ExceptionConfiguration("default configuration parser was not implemented");
//	}

	public static Configuration parse(InputStream is) throws ExceptionConfiguration {
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
			throw new ExceptionConfiguration(e);
		} catch (SAXException e) {
			throw new ExceptionConfiguration(e);
		} catch (IOException e) {
			throw new ExceptionConfiguration(e);
		}
	}
	
	public static Configuration parseDefault() throws ExceptionConfiguration {
		try {
			return parse(new FileInputStream(ConfigurationHelper.lookup("conf.xml")));
		} catch (FileNotFoundException e) {
			throw new ExceptionConfiguration(e);
		}
	}

	public static Configuration parse(Configurable instance, DBBroker broker, Collection collection, XmldbURI fileURL) throws ExceptionConfiguration {
		DocumentAtExist document = collection.getDocument(broker, fileURL);
		
		if (document == null) {
			
			Class<?> clazz = instance.getClass();
			instance.getClass().getAnnotations();
			if (!clazz.isAnnotationPresent(ConfigurationClass.class)) {
				return null; //UNDERSTAND: throw exception
			}
			
			String configName = clazz.getAnnotation(ConfigurationClass.class).value();

			StringBuilder buf = new StringBuilder();
			//open tag
			buf.append("<");
			buf.append(configName);
			
			//attributes
			Map<String, Field> properyFieldMap = getProperyFieldMap(instance.getClass());
			for (Entry<String, Field> entry : properyFieldMap.entrySet()) {

				buf.append(" ");
				buf.append(entry.getKey());
				buf.append("='");
				try {
					final Field field = entry.getValue();
					
					field.setAccessible(true);
					
					String typeName = field.getType().getName();

					if (typeName.equals("int") || typeName.equals("java.lang.Integer")) {
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
		    				buf.append(Integer.toString((Integer)field.get(instance), radix) );
						} else { 
		    				buf.append(field.get(instance));
						}
					} else {
						buf.append(field.get(instance));
					}

				
				} catch (IllegalArgumentException e) {
					throw new ExceptionConfiguration(e.getMessage(), e);

				} catch (IllegalAccessException e) {
					throw new ExceptionConfiguration(e.getMessage(), e);
				
				}
				buf.append("'");
			}

			buf.append(">");

			//close tag
			buf.append("</");
			buf.append(configName);
			buf.append(">");
			
			if (broker.isReadOnly()) {
				//database in read-only mode & there no configuration file, 
				//create in memory document & configuration 
				try {
					return parse( new ByteArrayInputStream(buf.toString().getBytes("UTF-8")) );
				} catch (UnsupportedEncodingException e) {
					return null;
				}
				
			} else {
				//create & save configuration file
				broker.flush();
				broker.sync(Sync.MAJOR_SYNC);
	
				BrokerPool pool = broker.getBrokerPool();
				TransactionManager transact = pool.getTransactionManager();
				Txn txn = transact.beginTransaction();
	
				User currentUser = broker.getUser();
				try {
					broker.setUser(pool.getSecurityManager().getSystemAccount());
					
		            String data = buf.toString();
		            IndexInfo info = collection.validateXMLResource(txn, broker, fileURL, data);
	
		            //TODO : unlock the collection here ?
		            DocumentImpl doc = info.getDocument();
		            doc.getMetadata().setMimeType(MimeType.XML_TYPE.getName());
		            collection.store(txn, broker, info, data, false);
					doc.setPermissions(0770);
					
					broker.saveCollection(txn, doc.getCollection());
					
					transact.commit(txn);
	
				} catch (IOException e) {
					throw new ExceptionConfiguration(e.getMessage(), e);
	
				} catch (TriggerException e) {
		            throw new ExceptionConfiguration(e.getMessage(), e);
				
				} catch (SAXException e) {
					throw new ExceptionConfiguration(e.getMessage(), e);
				
				} catch (PermissionDeniedException e) {
					throw new ExceptionConfiguration(e.getMessage(), e);
				
				} catch (LockException e) {
					throw new ExceptionConfiguration(e.getMessage(), e);
				
				} catch (TransactionException e) {
					throw new ExceptionConfiguration(e.getMessage(), e);
				
				} catch (EXistException e) {
					throw new ExceptionConfiguration(e.getMessage(), e);
				
				} finally {
					broker.setUser(currentUser);
				}
				
				broker.flush();
				broker.sync(Sync.MAJOR_SYNC);

				document = collection.getDocument(broker, fileURL);
				if (document == null)
					throw new ExceptionConfiguration("The configuration file can't be found, url = "+collection.getURI().append(fileURL));
			}
		}
		
		return new ConfigurationImpl((ElementAtExist) document.getDocumentElement());
	}
}