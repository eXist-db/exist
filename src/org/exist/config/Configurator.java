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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationField;
import org.exist.dom.ElementAtExist;
import org.exist.memtree.SAXAdapter;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Configurator {
	
	private final static Logger LOG = Logger.getLogger(Configurator.class);

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
    	
    	return link;
	}
	
	public static boolean configure(Configurable instance, ConfigElement configuration) {
		Class<?> clazz = instance.getClass();
		instance.getClass().getAnnotations();
		if (!clazz.isAnnotationPresent(ConfigurationClass.class)) {
			LOG.info("no configuration name at "+instance.getClass());
			return false;
		}
		
		String configName = clazz.getAnnotation(ConfigurationClass.class).value();
		
		return configureByCurrent(instance, configuration.getConfiguration(configName));
	}

	private static boolean configureByCurrent(Configurable instance, ConfigElement configuration) {
		Map<String, Field> properyFieldMap = getProperyFieldMap(instance.getClass());
		List<String> properties = configuration.getProperties();
		
		if (properties.size() == 0)
			LOG.info("no properties for "+instance.getClass()+" @ "+configuration);
		
		for (String property : properties) {
			if (!properyFieldMap.containsKey(property)) {
				LOG.warn("unused property "+property);
				continue;
			}
			
			Field field = properyFieldMap.get(property);
			String typeName = field.getType().getName();
			try {
				if (typeName.equals("java.lang.String")) {
					String value = configuration.getProperty(property);
					if (value != null)
						field.set(instance, value);
				} else if (typeName.equals("int") || typeName.equals("java.lang.Integer")) {
					Integer value = configuration.getPropertyInteger(property);
					if (value != null)
						field.set(instance, value);
				} else if (typeName.equals("boolean") || typeName.equals("java.lang.Boolean")) {
					Boolean value = configuration.getPropertyBoolean(property);
					if (value != null)
						field.set(instance, value);
				} else {
					throw new IllegalArgumentException("unsupported configuration value type "+field.getType());
				}
			} catch (IllegalArgumentException e) {
				LOG.warn("configuration error",e);
				return false;
			} catch (IllegalAccessException e) {
				LOG.debug("security error",e);
				return false;
			}
		}
		
		return true;
	}

	public static ConfigElement parse(InputStream is) throws IOException {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);
			InputSource src = new InputSource(is);
			SAXParser parser = factory.newSAXParser();
			XMLReader reader = parser.getXMLReader();
			SAXAdapter adapter = new SAXAdapter();
			reader.setContentHandler(adapter);
			reader.parse(src);
    
			return new ConfigElementImpl((ElementAtExist) adapter.getDocument().getDocumentElement());
		} catch (ParserConfigurationException e) {
			throw new IOException(e);
		} catch (SAXException e) {
			throw new IOException(e);
		}
	}
}
