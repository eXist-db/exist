/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2011 The eXist Project
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
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.config.annotation.*;
import org.exist.dom.DocumentAtExist;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementAtExist;
import org.exist.dom.QName;
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
import org.exist.util.serializer.SAXSerializer;
import org.exist.xmldb.FullXmldbURI;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * This class handle all configuration needs: extracting and saving, reconfiguring & etc.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Configurator {

    public final static Logger LOG = Logger.getLogger(Configurator.class);

    protected static ConcurrentMap<FullXmldbURI, Configuration> hotConfigs = new ConcurrentHashMap<FullXmldbURI, Configuration>();

    /*private static Map<Class<Configurable>, Map<String, Field>> map =
        new HashMap<Class<Configurable>, Map<String, Field>>();
    */

    //TODO should be replaced with a naturally ordered List, we need to maintain the order of XML elements based on the order of class members!!!
    protected static ConfigurationAnnotatedFields getConfigurationAnnotatedFields(Class<?> clazz) {

        /*if (map.containsKey(clazz))
            return map.get(clazz);*/

        ConfigurationAnnotatedFields fields = new ConfigurationAnnotatedFields();

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(ConfigurationFieldAsAttribute.class)) {
                fields.addAttribute(new ConfigurationAnnotatedField<ConfigurationFieldAsAttribute>(field.getAnnotation(ConfigurationFieldAsAttribute.class), field));
            } else if (field.isAnnotationPresent(ConfigurationFieldAsElement.class)) {
                fields.addElement(new ConfigurationAnnotatedField<ConfigurationFieldAsElement>(field.getAnnotation(ConfigurationFieldAsElement.class), field));
            }
        }

        Class<?> superClass = clazz.getSuperclass();
        if (superClass.isAnnotationPresent(ConfigurationClass.class)) {
            ConfigurationAnnotatedFields superFields = getConfigurationAnnotatedFields(superClass);

            fields.addAllAttributes(superFields.getAttributes());
            fields.addAllElements(superFields.getElements());
        }

        return fields;
    }

    protected static <T extends Annotation> T getAnnotation(Field field, Class<T> annotation) {

        if (field.isAnnotationPresent(annotation))
            return field.getAnnotation(annotation);

        return null;
    }

    public static Method searchForGetMethod(Class<?> clazz, String property) {
        try {
            String methodName = "get"+ property;
            methodName = methodName.toLowerCase();
            for (Method method : clazz.getMethods()) {
                if (method.getName().toLowerCase().equals(methodName))
                    return method;
            }
        } catch (SecurityException e) {
            //Nothing to do
        } catch (NoClassDefFoundError e) {
            //Nothing to do
        }
        return null;
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
            //Nothing to do
        } catch (NoClassDefFoundError e) {
            //Nothing to do
        }
        return null;
    }

    public static Method searchForAddMethod(Class<?> clazz, String property) {
        try {
            String methodName = "add"+property;
            methodName = methodName.toLowerCase();
            for (Method method : clazz.getMethods()) {
                if (method.getName().toLowerCase().equals(methodName)
                    && method.getParameterTypes().length == 1
                    && method.getParameterTypes()[0].getName().equals("java.lang.String")
                    //&& method.getParameterTypes()[0].getName().equals("org.exist.config.Configuration")
                    )
                    return method;
            }
        } catch (SecurityException e) {
            //Nothing to do
        } catch (NoClassDefFoundError e) {
            //Nothing to do
        }
        return null;
    }

    public static Configuration configure(Configurable instance, Configuration configuration) {
        if (configuration == null)
            return null;
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
            if (impl.configuredObjectReference != null)
                configurable = impl.configuredObjectReference.get();
            if (configurable != null) {
                if (configurable != instance)
                    throw new IllegalArgumentException(
                        "Configuration can't be used by "+instance+", " +
                        "because allready in use by "+configurable);
            } else 
                impl.configuredObjectReference = new WeakReference<Configurable>(instance);
                //end (lock issue)
        }
        return configureByCurrent(instance, config);
    }

    private static Configuration configureByCurrent(Configurable instance, Configuration configuration) {
        ConfigurationAnnotatedFields annotatedFields = getConfigurationAnnotatedFields(instance.getClass());
        Set<String> properties = configuration.getProperties();

        if(properties.isEmpty()) {
            //LOG.info("no properties for "+instance.getClass()+" @ "+configuration);
            return configuration;
        }

        //process simple types: String, int, long, boolean
        for (String property : properties) {
            ConfigurationAnnotatedField annotatedField = annotatedFields.findByAnnotationValue(property);
            if(annotatedField == null) {
                System.out.println("unused property "+property+" @"+configuration.getName());
                continue;
            }

            Field field = annotatedField.getField();
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
                } else if(typeName.equals("java.util.Map")) {
                    value = configuration.getPropertyMap(property);
                    //TODO: skip, it will be processed as structure
                } else if (typeName.equals("java.util.List")) {
                    //skip, it will be processed as structure
                    //TODO what about simple generic types?

                } else if (typeName.equals("org.exist.xmldb.XmldbURI")) {
                    //use annotation ConfigurationFieldClassMask
                    value = org.exist.xmldb.XmldbURI.create(
                            configuration.getProperty(property)
                    );
                //TODO: remove, use methods instead
                //use annotation ConfigurationFieldClassMask
                } else if(typeName.equals("org.exist.security.realm.ldap.LdapContextFactory")){
                    value = instantiateObject("org.exist.security.realm.ldap.LdapContextFactory", configuration);
                } else if(typeName.equals("org.exist.security.realm.ldap.LDAPSearchContext")){
                    value = instantiateObject("org.exist.security.realm.ldap.LDAPSearchContext", configuration);
                } else if(typeName.equals("org.exist.security.realm.ldap.LDAPSearchAccount")){
                    value = instantiateObject("org.exist.security.realm.ldap.LDAPSearchAccount", configuration);
                } else if(typeName.equals("org.exist.security.realm.ldap.LDAPSearchGroup")){
                    value = instantiateObject("org.exist.security.realm.ldap.LDAPSearchGroup", configuration);
                } else if(typeName.equals("org.exist.security.realm.ldap.LDAPSearchPrincipalMetadata")){
                    value = instantiateObject("org.exist.security.realm.ldap.LDAPSearchPrincipalMetadata", configuration);
                } else if(typeName.equals("org.exist.security.realm.ldap.LDAPTransformationContext")) {
                    value = instantiateObject("org.exist.security.realm.ldap.LDAPTransformationContext", configuration);
                } else if(typeName.equals("org.exist.security.realm.ldap.LDAPPrincipalBlackList")) {
                    value = instantiateObject("org.exist.security.realm.ldap.LDAPPrincipalBlackList", configuration);
                } else if(typeName.equals("org.exist.security.realm.ldap.LDAPPrincipalWhiteList")) {
                    value = instantiateObject("org.exist.security.realm.ldap.LDAPPrincipalWhiteList", configuration);
                } else {
                	value = configuration.getProperty(property);
                    //LOG.warn("skip unsupported configuration value type "+field.getType());
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
            } catch(ConfigurationException ce) {
                LOG.error(ce.getMessage(), ce);
                return null;
            }
        }
        //process simple structures: List
        try {
            for (ConfigurationAnnotatedField<ConfigurationFieldAsElement> element : annotatedFields.getElements()) {
                final Field field = element.getField();
                String typeName = field.getType().getName();
                if (typeName.equals("java.util.List")) {
                
                    String confName = element.getAnnotation().value();
                    field.setAccessible(true);
                    List list = (List) field.get(instance);

                    String referenceBy;
                    List<Configuration> confs;
                    if (field.isAnnotationPresent(ConfigurationReferenceBy.class)) {
                        confs = configuration.getConfigurations(confName);
                        referenceBy = field.getAnnotation(ConfigurationReferenceBy.class).value();
                    } else {
                        confs = configuration.getConfigurations(confName);
                        referenceBy = null;
                    }

                    if (list == null) {
                        list = new ArrayList<Configurable>(confs.size());
                        field.set(instance, list);
                    }
                    
                    if (confs != null) {
                        //remove & update
                        for (Iterator<?> iterator = list.iterator() ; iterator.hasNext() ; ) {
                            Object obj = iterator.next();
                            Configuration current_conf = null;
                            if (!(obj instanceof Configurable)) {
                            	current_conf = org.exist.config.mapper.Constructor.getConfiguration(obj);
							} else {
	                            current_conf = ((Configurable) obj).getConfiguration();
							}
                            if (current_conf == null) {
                                //skip internal staff //TODO: static list
                                if (obj instanceof org.exist.security.internal.RealmImpl) {
                                    continue;
                                }
                                LOG.warn("Unconfigured instance ["+obj+"], remove the object.");
                                iterator.remove();
                                continue;
                            }
                            
                            //lookup for new configuration, update if found
                            boolean found = false;
                            for (Iterator<Configuration> i = confs.iterator() ; i.hasNext() ;) {
                                Configuration conf = i.next();

                                if (referenceBy != null && current_conf.equals( conf, referenceBy )) {
                                    i.remove();
                                    found = true;
                                    break;
                                } else if (referenceBy == null && current_conf.equals( conf )) {  
                                	current_conf.checkForUpdates(conf.getElement());
                                    i.remove();
                                    found = true;
                                    break;
                                }
                            }

                            if (!found) {
	                            LOG.info("Configuration was removed, remove the object ["+obj+"].");
	                            iterator.remove();
                            }
                        }
                        //create
                        for (Configuration conf : confs) {
                            if (referenceBy != null) {
                                String value = conf.getProperty(referenceBy);
                                if (value != null) {
                                    Method method = searchForAddMethod(instance.getClass(), confName);
                                    if (method != null) {
                                        try {
                                            method.invoke(instance, value);
                                            continue;
                                        } catch (Exception e) {
                                            method = null;
                                        }
                                    }
                                }
                            } else { 
                            	Type genericType = field.getGenericType();
                            	if (genericType != null) {
                            		if ("java.util.List<java.lang.String>".equals(genericType.toString())) {
                                        String value = conf.getValue();
                                        if (value != null) {
                                            Method method = searchForAddMethod(instance.getClass(), confName);
                                            if (method != null) {
                                                try {
                                                    method.invoke(instance, value);
                                                    continue;
                                                } catch (Exception e) {
                                                    method = null;
                                                }
                                            }
                                        }
                            		}
                            	}
                            	
                                //TODO: AddMethod with Configuration argument
                            }
                            
                            ConfigurationFieldClassMask annotation = getAnnotation(field, ConfigurationFieldClassMask.class);
                            if (annotation == null) {
                            	NewClass newClass = getAnnotation(field, NewClass.class);
                                if (newClass != null) {
                                	Object obj = org.exist.config.mapper.Constructor.load(newClass, instance, conf);
                                	list.add(obj);
                                } else
                                	LOG.error("Filed must have 'ConfigurationFieldClassMask' annotation ["+conf.getName()+"], skip instance creation.");
                            	
                                continue;
                            }
                            
                            String id = conf.getProperty(Configuration.ID);

                            Object[] objs;
                            if (id == null) { 
                            	objs = new Object[] {"", ""};
                                //LOG.warn("Subconfiguration don't have 'id' property ["+conf.getName()+"], skip instance creation.");
                            } else
                            	objs = new Object[] {id.toLowerCase(), id};
                            
                            String clazzName = 
                            	String.format(
                        			annotation.value(), 
                        			objs
                            	);
                            //String clazzName = "org.exist.security.realm."+id.toLowerCase()+"."+id+"Realm";
                            //org.exist.security.realm.openid.OpenIDRealm
                            Class<?> clazz;
                            try {
                                clazz = Class.forName(clazzName);
                                Constructor<Configurable> constructor = 
                                    (Constructor<Configurable>) clazz.getConstructor(instance.getClass(), Configuration.class);
                                
                                Configurable obj = constructor.newInstance(instance, conf);
                                
                                if (obj instanceof Startable) {
                                	BrokerPool db = null;
                                	try {
                                		db = BrokerPool.getInstance();
                                	} catch (EXistException e) {
                                		//ignore if database starting-up
									}
                                	if (db != null) {
                                		DBBroker broker = null;
                                		try {
                                			broker = db.get(null);
                                			((Startable) obj).startUp(broker);
                                		} finally {
                                			db.release(broker);
                                		}
                                	}
                                }

                                list.add( obj );
                            } catch (ClassNotFoundException e) {
                                LOG.error("Class ["+clazzName+"] not found, skip instance creation.");
                                continue;
                            } catch (SecurityException e) {
                                LOG.error("Security exception on class ["+clazzName+"] creation, skip instance creation.");
                                continue;
                            } catch (NoSuchMethodException e) {
                                LOG.error("Class ["+clazzName+"] constructor (with Configuration) not found, skip instance creation.");
                                continue;
                            } catch (InstantiationException e) {
                                LOG.error("Instantiation exception on class ["+clazzName+"] creation, skip instance creation.");
                                continue;
                            } catch (InvocationTargetException e) {
                                LOG.error("Invocation target exception on class ["+clazzName+"] creation, skip instance creation.");
                                continue;
                            } catch (EXistException e) {
                                LOG.error("Databasse exception on class ["+clazzName+"] startup, skip instance creation.");
                                LOG.error(e);
                                continue;
							}
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            LOG.error(e);
            e.printStackTrace();
            return null;
        } catch (IllegalAccessException e) {
            LOG.error(e);
            e.printStackTrace();
            return null;
        }
        return configuration;
    }

	private static void proccessExternalObject() {
		// TODO Auto-generated method stub
		
	}

	//public static Configuration parse(InputStream is) throws ExceptionConfiguration {
        //throw new ExceptionConfiguration("parser was not implemented");
    //}

    public static Configuration parse(File file) throws ConfigurationException {
        try {
            return parse(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            throw new ConfigurationException(e);
        }
    }

    //public static Configuration parseDefault() throws ExceptionConfiguration {
        //throw new ExceptionConfiguration("default configuration parser was not implemented");
    //}

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
    
    private static Boolean implementsInterface(Class<?> object, Class<?> interf){
        for (Class<?> c : object.getInterfaces()) {
            if (c.equals(interf)) {
                return true;
            }
        }
        return false;
    }

    protected static void serializeByReference(Configurable instance, SAXSerializer serializer, String fieldAsElementName, String referenceBy) throws SAXException {
        Configurable resolved = ((ReferenceImpl)instance).resolve();
        
        Method getMethod = searchForGetMethod(resolved.getClass(), referenceBy);
        
        Object value;
        try {
             value = getMethod.invoke(resolved);
        } catch (IllegalArgumentException iae) {
            LOG.error(iae);
            return;
        } catch (IllegalAccessException iae) {
            LOG.error(iae);
            return;
        } catch(InvocationTargetException ite) {
            LOG.error(ite);
            return;
        }
        
        QName qnConfig = new QName(fieldAsElementName, Configuration.NS);
        if(value == null) {
        	String comment = "<"+qnConfig+" "+referenceBy+"=''/>";
        	char[] ch = comment.toCharArray();
        	serializer.characters(new char[] {'\n'}, 0, 1);
        	serializer.comment(ch, 0, ch.length);
        } else {
            serializer.startElement(qnConfig, null);
            serializer.attribute(new QName(referenceBy, null), value.toString());
            serializer.endElement(qnConfig);
        }
        
    }
    
    protected static void serialize(Configurable instance, SAXSerializer serializer, String fieldAsElementName, String referenceBy) throws SAXException {
        
        if(instance instanceof ReferenceImpl) {
            serializeByReference(instance, serializer, fieldAsElementName, referenceBy);
            return;
        }
        
        Class<?> clazz = instance.getClass();
        instance.getClass().getAnnotations();
        if (!clazz.isAnnotationPresent(ConfigurationClass.class)) {
            return; //UNDERSTAND: throw exception
        }
        
        ConfigurationAnnotatedFields annotatedFields = getConfigurationAnnotatedFields(instance.getClass());
        ConfigurationAnnotatedField<?> annotatedField = annotatedFields.findByAnnotationValue(referenceBy);
        if(annotatedField == null) {
            return; //UNDERSTAND: throw exception
        }

        final Field field = annotatedField.getField();
        if (field == null) {
            LOG.error("Reference field '"+referenceBy+"' can't be found for class '"+clazz+"'");
            return;
        }
        field.setAccessible(true);
        String value;
        try {
            value = extractFieldValue(field, instance);
        } catch (IllegalArgumentException e) {
            LOG.error(e);
            return;
        } catch (IllegalAccessException e) {
            LOG.error(e);
            return;
        }

        QName qnConfig = new QName(fieldAsElementName, Configuration.NS);
        if(value == null) {
        	String comment = "<"+qnConfig+" "+referenceBy+"=''/>";
        	char[] ch = comment.toCharArray();
        	serializer.characters(new char[] {'\n'}, 0, 1);
        	serializer.comment(ch, 0, ch.length);
        } else {
        	serializer.startElement(qnConfig, null);
        	serializer.attribute(new QName(referenceBy, null), value);
        	serializer.endElement(qnConfig);
        }
    }

    private static String extractFieldValue(Field field, Configurable instance) throws IllegalArgumentException, IllegalAccessException {
        String typeName = field.getType().getName();
        if (typeName.equals("java.lang.String")) {
            return field.get(instance).toString();
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
                return Integer.toString((Integer)field.get(instance), radix);
            } else {
                return field.get(instance).toString();
            }
        } else if (typeName.equals("long") || typeName.equals("java.lang.Long")) {
            return field.get(instance).toString();
        } else if (typeName.equals("boolean") || typeName.equals("java.lang.Boolean")) {
            return Boolean.valueOf(field.get(instance).toString()).toString();
        } else if (typeName.equals("org.exist.xmldb.XmldbURI")) {
        	return field.get(instance).toString();
        }

        return null;
    }

    protected static void serialize(Configurable instance, SAXSerializer serializer) throws ConfigurationException {
        Class<?> clazz = instance.getClass();
        instance.getClass().getAnnotations();
        if (!clazz.isAnnotationPresent(ConfigurationClass.class)) {
            return; //UNDERSTAND: throw exception
        }

        String configName = clazz.getAnnotation(ConfigurationClass.class).value();

        try
        {
            //open tag
            QName qnConfig = new QName(configName, Configuration.NS);
            serializer.startElement(qnConfig, null);
            boolean simple = true;

            //store field's values as attributes or elements depends on annotation
            ConfigurationAnnotatedFields annotatedFields = getConfigurationAnnotatedFields(instance.getClass());

            try {
                //pass one - extract just attributes
                for (ConfigurationAnnotatedField<ConfigurationFieldAsAttribute> attr : annotatedFields.getAttributes()) {
                    final Field field = attr.getField();
                    field.setAccessible(true);

                    //XXX: artifact? remove?
                    //skip elements
                    if(field.isAnnotationPresent(ConfigurationFieldAsElement.class)) {
                        continue;
                    }

                    //skip null values
                    if(field.get(instance) == null) {
                        continue;
                    }

                    //now we just have attributes
                    String value = extractFieldValue(field, instance);
                    serializer.attribute(new QName(attr.getAnnotation().value(), null), value);
                }

                //pass two - just elements or text nodes
                for (ConfigurationAnnotatedField<ConfigurationFieldAsElement> element : annotatedFields.getElements()) {
                    simple = true;
                    final Field field = element.getField();
                    field.setAccessible(true);
                
                    //XXX: artifact? remove?
                    //skip attributes
                    if(!field.isAnnotationPresent(ConfigurationFieldAsElement.class)) {
                        continue;
                    }

                    String referenceBy = null;
                    if (field.isAnnotationPresent(ConfigurationReferenceBy.class)) {
                        referenceBy = field.getAnnotation(ConfigurationReferenceBy.class).value();
                    }

                    //skip null values
                    if(field.get(instance) == null) {
                    	String tagName = element.getAnnotation().value();
                    	String comment = "<"+tagName;
                    	
                    	if (referenceBy != null)
                    		comment += " "+referenceBy+"=\"\"/>";
                    	else
                    		comment += "></"+tagName+">";
                    	
                    	char[] ch = comment.toCharArray();
                    	serializer.characters(new char[] {'\n'}, 0, 1);
                    	serializer.comment(ch, 0, ch.length);

                        continue;
                    }
                    
                    String value = null;
                    String typeName = field.getType().getName();
                    if(typeName.equals("java.util.List")) {
                        simple = false;
                        List<Configurable> list = (List<Configurable>) field.get(instance);
                        for (Configurable el : list) {
                            if (referenceBy == null) {
                                    serialize(el, serializer);
                            } else {
                                    serialize(el, serializer, element.getAnnotation().value(), referenceBy);
                            }
                        }
                    } else if(implementsInterface(field.getType(), Configurable.class)) {
                        simple = false;
                        Configurable subInstance = (Configurable) field.get(instance);
                        serialize(subInstance, serializer);
                    } else if(typeName.equals("java.util.Map")) {
                        serializeMap(element.getAnnotation().value(), (Map<String, String>)field.get(instance), serializer);
                    } else {

                        value = extractFieldValue(field, instance);

                        if(value == null) {
                            LOG.error("field '"+field.getName()+"' has unsupported type ["+typeName+"] - skiped");
                            //unsupported type - skip
                            //buf.append(field.get(instance));
                        }
                    }
                    
                    if (value != null && value.length() > 0){
                        if (simple) {
                            QName qnSimple = new QName(element.getAnnotation().value(), Configuration.NS);
                            serializer.startElement(qnSimple, null);
                            serializer.characters(value);
                            serializer.endElement(new QName(element.getAnnotation().value(), null));
                        } else {
                            serializer.characters(value);
                        }
                    } else {
                    	String tagName = element.getAnnotation().value();
                    	String comment = "<"+tagName;
                    	
                    	if (referenceBy != null)
                    		comment += " "+referenceBy+"=\"\"/>";
                    	else
                    		comment += "></"+tagName+">";
                    	
                    	char[] ch = comment.toCharArray();
                    	serializer.characters(new char[] {'\n'}, 0, 1);
                    	serializer.comment(ch, 0, ch.length);
                    }
                }

            } catch (IllegalArgumentException e) {
                throw new ConfigurationException(e.getMessage(), e);
            } catch (IllegalAccessException e) {
                throw new ConfigurationException(e.getMessage(), e);
            }

            //close tag
            serializer.endElement(qnConfig);
        } catch(SAXException saxe) {
            throw new ConfigurationException(saxe.getMessage(), saxe);
        }
    }

    private static void serializeMap(String mapName, Map<String, String> map, SAXSerializer serializer) throws SAXException {
        if(map != null){
            final QName mapQName = new QName(mapName, Configuration.NS);
            final QName attrQName = new QName("key");
            for(Map.Entry<String, String> entry : map.entrySet()) {
                serializer.startElement(mapQName, null);

                serializer.attribute(attrQName, entry.getKey());
                serializer.characters(entry.getValue());

                serializer.endElement(mapQName);
            }
        }
    }
    
    public static FullXmldbURI getFullURI(Database db, XmldbURI uri) {
    	if (uri instanceof FullXmldbURI) {
			return (FullXmldbURI) uri;
		}
    	
        StringBuilder accessor = new StringBuilder(XmldbURI.XMLDB_URI_PREFIX);
        accessor.append(db.getId());
        accessor.append("://");
        accessor.append("");
        
        return (FullXmldbURI)XmldbURI.create(accessor.toString(), uri.toString());
    }

    public static Configuration parse(Configurable instance, DBBroker broker, Collection collection, XmldbURI fileURL) throws ConfigurationException {
        Configuration conf = null;
        
        FullXmldbURI key = getFullURI(broker.getBrokerPool(), collection.getURI().append(fileURL));

        conf = hotConfigs.get( key );

        if (conf != null)
            return conf;
        //XXX: locking required
        DocumentAtExist document = collection.getDocument(broker, fileURL);
        if (document == null) {
            if (broker.isReadOnly()) {
                //database in read-only mode & there no configuration file, 
                //create in memory document & configuration 
                try {
                        StringWriter writer = new StringWriter();
                        SAXSerializer serializer = new SAXSerializer(writer, null);
                        serializer.startDocument();
                        serialize(instance, serializer);
                        serializer.endDocument();

                        String data = writer.toString();

                        if (data == null || data.length() == 0){
                            return null;
                        }

                        return parse( new ByteArrayInputStream(data.getBytes("UTF-8")) );
                    } catch (SAXException saxe){
                        throw new ConfigurationException(saxe.getMessage(), saxe);
                    } catch (UnsupportedEncodingException e) {
                        return null;
                    }
            }
            try {
                document = save(instance, broker, collection, fileURL);
            } catch (IOException e) {
                return null;
            }
        }
        if (document == null)
            return null; //possibly on corrupted database, find better solution (recovery flag?)
            //throw new ConfigurationException("The configuration file can't be found, url = "+collection.getURI().append(fileURL));
        ElementAtExist confElement = (ElementAtExist) document.getDocumentElement();
        if (confElement == null)
            return null; //possibly on corrupted database, find better solution (recovery flag?)
            //throw new ConfigurationException("The configuration file is empty, url = "+collection.getURI().append(fileURL));
        conf = new ConfigurationImpl(confElement);

        hotConfigs.put(key, conf);

        return conf;
    }

    public static Configuration parse(DocumentAtExist document) {
        if (document == null)
            return null;
        Configuration conf;
        
        FullXmldbURI key = getFullURI(document.getDatabase(), document.getURI());

        conf = hotConfigs.get(key);
        
        if (conf != null)
            return conf;
        
        ElementAtExist confElement = (ElementAtExist) document.getDocumentElement();
        if (confElement == null)
            return null; //possibly on corrupted database, find better solution (recovery flag?)
            //throw new ConfigurationException("The configuration file is empty, url = "+collection.getURI().append(fileURL));
        
        conf = new ConfigurationImpl(confElement);
        
        hotConfigs.put(key, conf);
        
        return conf;
    }

    public static DocumentAtExist save(Configurable instance, XmldbURI uri) throws IOException {
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
            if (collection == null)
                throw new IOException("Collection URI = "+uri.removeLastSegment()+" not found.");
            return save(instance, broker, collection, uri.lastSegment());
        } catch (EXistException e) {
            throw new IOException(e);
        } finally {
            database.release(broker);
        }
    }
    
    protected static Set<FullXmldbURI> saving = new HashSet<FullXmldbURI>();

    public static DocumentAtExist save(Configurable instance, DBBroker broker, Collection collection, XmldbURI uri) throws IOException, ConfigurationException {

        StringWriter writer = new StringWriter();
        SAXSerializer serializer = new SAXSerializer(writer, null);

        try{
            serializer.startDocument();
            serialize(instance, serializer);
            serializer.endDocument();
        } catch (SAXException saxe){
            throw new ConfigurationException(saxe.getMessage(), saxe);
        }

        String data = writer.toString();

        if (data == null || data.length() == 0) {
            return null;
        }
        FullXmldbURI fullURI = null;
        
        BrokerPool pool = broker.getBrokerPool();
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = null;
        LOG.info("STORING CONFIGURATION collection = "+collection.getURI()+" document = "+uri);
        Subject currentUser = broker.getSubject();
        try {
            broker.setSubject(pool.getSecurityManager().getSystemSubject());
            
            txn = transact.beginTransaction();
            txn.acquireLock(collection.getLock(), Lock.WRITE_LOCK);
            
            IndexInfo info = collection.validateXMLResource(txn, broker, uri, data);
            DocumentImpl doc = info.getDocument();
            doc.getMetadata().setMimeType(MimeType.XML_TYPE.getName());
            doc.getPermissions().setMode(0770);
            
            fullURI = getFullURI(broker.getBrokerPool(), doc.getURI()); 

            saving.add(fullURI);
            collection.store(txn, broker, info, data, false);
            broker.saveCollection(txn, doc.getCollection());
            transact.commit(txn);
            txn = null;
            
            saving.remove(fullURI);

            broker.flush();
            broker.sync(Sync.MAJOR_SYNC);
            return collection.getDocument(broker, uri.lastSegment());
        } catch (Exception e) {
        	if (txn != null)
        		transact.abort(txn);
            
        	if (fullURI != null)
            	saving.remove(fullURI);
            
        	LOG.error(e);
            throw new IOException(e);
        } finally {
            broker.setSubject(currentUser);
        }
    }

    public static synchronized void clear(Database db) {
        for (Entry<FullXmldbURI, Configuration> entry : hotConfigs.entrySet()) {
        	FullXmldbURI uri = entry.getKey();
        	if (uri.getInstanceName().equals(db.getId())) {
        		Configuration conf = entry.getValue();
        		if (conf instanceof ConfigurationImpl)
            		((ConfigurationImpl) conf).configuredObjectReference = null;
            }
        	hotConfigs.remove(uri);
        }
        //hotConfigs.clear();
        //map.clear();
    }

    public static void unregister(Configuration configuration) {
        if (configuration == null)
            return;

        if (hotConfigs.containsValue(configuration)) { 
            for (Entry<FullXmldbURI, Configuration> entry : hotConfigs.entrySet()) {
                if (entry.getValue() == configuration) {
                    hotConfigs.remove(entry.getKey());
                    return;
                }
            }
        }
    }

    private static Object instantiateObject(String className, Configuration configuration) throws ConfigurationException {
        try {
            Class<?> clazz = Class.forName(className);
            Constructor<?> cstr = clazz.getConstructor(Configuration.class);
            return cstr.newInstance(configuration);
        } catch(Exception e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }

    private static class ConfigurationAnnotatedFields implements Iterable<ConfigurationAnnotatedField> {
        private List<ConfigurationAnnotatedField<ConfigurationFieldAsAttribute>> attributes = new ArrayList<ConfigurationAnnotatedField<ConfigurationFieldAsAttribute>>();
        private List<ConfigurationAnnotatedField<ConfigurationFieldAsElement>> elements = new ArrayList<ConfigurationAnnotatedField<ConfigurationFieldAsElement>>();

        public void addAttribute(ConfigurationAnnotatedField<ConfigurationFieldAsAttribute> attribute) {
            this.attributes.add(attribute);
        }

        public void addAllAttributes(List<ConfigurationAnnotatedField<ConfigurationFieldAsAttribute>> attributes) {
            this.attributes.addAll(attributes);
        }

        public void addElement(ConfigurationAnnotatedField<ConfigurationFieldAsElement> element) {
            this.elements.add(element);
        }

        public void addAllElements(List<ConfigurationAnnotatedField<ConfigurationFieldAsElement>> elements) {
            this.elements.addAll(elements);
        }

        public List<ConfigurationAnnotatedField<ConfigurationFieldAsAttribute>> getAttributes() {
            return attributes;
        }

        public List<ConfigurationAnnotatedField<ConfigurationFieldAsElement>> getElements() {
            return elements;
        }

        public ConfigurationAnnotatedField findByAnnotationValue(String value) {
            for(ConfigurationAnnotatedField<ConfigurationFieldAsAttribute> attr : attributes) {
                if(attr.getAnnotation().value().equals(value)) {
                    return attr;
                }
            }

            for(ConfigurationAnnotatedField<ConfigurationFieldAsElement> element : elements) {
                if(element.getAnnotation().value().equals(value)) {
                    return element;
                }
            }

            return null;
        }

        @Override
        public Iterator<ConfigurationAnnotatedField> iterator() {
            return new Iterator<ConfigurationAnnotatedField>(){

                private Iterator<ConfigurationAnnotatedField<ConfigurationFieldAsAttribute>> itAttributes = attributes.iterator();
                private Iterator<ConfigurationAnnotatedField<ConfigurationFieldAsElement>> itElements = elements.iterator();

                @Override
                public boolean hasNext() {
                    return itAttributes.hasNext() | itElements.hasNext();
                }

                @Override
                public ConfigurationAnnotatedField next() {
                    if(itAttributes.hasNext()){
                        return itAttributes.next();
                    } else if(itElements.hasNext()){
                        return itElements.next();
                    } else {
                        throw new NoSuchElementException();
                    }
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("Not supported yet.");
                }

            };
        }
    }

    private static class ConfigurationAnnotatedField<T> {
        private final T annotation;
        private final Field field;

        public ConfigurationAnnotatedField(T annotation, Field field) {
            this.annotation = annotation;
            this.field = field;
        }

        public T getAnnotation() {
            return annotation;
        }

        public Field getField() {
            return field;
        }
    }

	public static Configuration getConfigurtion(BrokerPool db, XmldbURI uri) {
		return hotConfigs.get(Configurator.getFullURI(db, uri));
	}
}