/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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
package org.exist.config.mapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.NewClass;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Constructor {
	
	private static Map<Object, Configuration> configurations = new HashMap<Object, Configuration>();
	
	public static Configuration getConfiguration(Object obj) {
		return configurations.get(obj);
	}

	/**
	 * Create new java object by mapping instructions.
	 * 
	 * @param className
	 * @param instance 
	 * @param conf 
	 */
    public static Object load(NewClass newClazz, Configurable instance, Configuration conf) {
    	
    	String url = newClazz.mapper();
    	if (url == null) {
    		Configurator.LOG.error("Filed must have 'ConfigurationFieldClassMask' annotation or " +
    				"registered mapping instruction for class '"+newClazz.name()+"' ["+conf.getName()+"], " +
    				"skip instance creation.");
    		return null;
    	}
    	
    	InputStream is = instance.getClass().getClassLoader().getResourceAsStream(url);
    	if (is == null) {
    		Configurator.LOG.error("Registered mapping instruction for class '"+newClazz.name()+"' missing resource '"+url+"', " +
					"skip instance creation.");
    		return null;
    	}
    	
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        try {
			XMLStreamReader reader = inputFactory.createXMLStreamReader(is);
			
			Object obj = null;
			Stack<Object> objs = new Stack<Object>();
			Stack<CallMathod> instructions = new Stack<CallMathod>();
			
			int eventType;
			while (reader.hasNext()) {
				eventType = reader.next();
				
				switch (eventType) {
					case XMLEvent.START_ELEMENT:
						String localName = reader.getLocalName();
			            
			            if ("class".equals(localName)) {
				            if (!"name".equals(reader.getAttributeLocalName(0))) {
				            	Configurator.LOG.error("class element first attribute must be 'name', skip instance creation.");
			            		return null;
				            }
				            
				            String clazzName = reader.getAttributeValue(0);
				    	    Class<?> clazz = Class.forName(clazzName);
				    	    java.lang.reflect.Constructor<?> constructor = clazz.getConstructor();
				    	    
				    	    Object newInstance = constructor.newInstance();
				    	    if (obj == null) obj = newInstance;
				    	    objs.add(newInstance);
				    	    
				    	    if (!instructions.empty())
				    	    	instructions.peek().setValue(newInstance);
				    	    
			            } else if ("callMethod".equals(localName)) {
			            	
			            	Configuration _conf_ = conf;
			            	if (!instructions.empty())
			            		_conf_ = instructions.peek().getConfiguration();

			            	CallMathod call = new CallMathod(objs.peek(), _conf_);
			            	
				            for (int i = 0; i < reader.getAttributeCount(); i++) {
				            	call.set(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
				            }
				            
				            instructions.add(call);
			            }
			            break;
			        case XMLEvent.END_ELEMENT:
						localName = reader.getLocalName();
						//System.out.println("END_ELEMENT "+localName);
						
						if ("class".equals(localName)) {
							objs.pop();
						} else if ("callMethod".equals(localName)) {
							CallMathod call = instructions.pop();
							call.eval();
						}
						
			            break;
				}
			}
			
			configurations.put(obj, conf);
			return obj;

        } catch (Exception e) {
			Configurator.LOG.error(e);
		}
        return null;
	}
}