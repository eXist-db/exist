/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2012 The eXist Project
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
import java.lang.reflect.ParameterizedType;
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
import org.exist.LifeCycle;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.config.annotation.*;
import org.exist.config.annotation.ConfigurationFieldSettings.SettingKey;
import org.exist.dom.DocumentAtExist;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementAtExist;
import org.exist.dom.QName;
import org.exist.memtree.SAXAdapter;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
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
 * This class handle all configuration needs: extracting and saving,
 * reconfiguring & etc.
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Configurator {

    public final static Logger LOG = Logger.getLogger(Configurator.class);
    private final static String EOL = System.getProperty("line.separator", "\n");
    protected static ConcurrentMap<FullXmldbURI, Configuration> hotConfigs = new ConcurrentHashMap<FullXmldbURI, Configuration>();

    //TODO should be replaced with a naturally ordered List, we need to maintain the order of XML elements based on the order of class members!!!
    protected static AFields getConfigurationAnnotatedFields(Class<?> clazz) {
        final AFields fields = new AFields();
        for (final Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(ConfigurationFieldAsAttribute.class)) {
                fields.addAttribute(new AField<ConfigurationFieldAsAttribute>(field.getAnnotation(ConfigurationFieldAsAttribute.class), field));
            } else if (field.isAnnotationPresent(ConfigurationFieldAsElement.class)) {
                fields.addElement(new AField<ConfigurationFieldAsElement>(field.getAnnotation(ConfigurationFieldAsElement.class), field));
            }
        }
        final Class<?> superClass = clazz.getSuperclass();
        if (superClass.isAnnotationPresent(ConfigurationClass.class)) { //XXX: remove? this force to have annotation at superclass
            final AFields superFields = getConfigurationAnnotatedFields(superClass);
            fields.addAllAttributes(superFields.getAttributes());
            fields.addAllElements(superFields.getElements());
        }
        return fields;
    }

    protected static <T extends Annotation> T getAnnotation(Field field, Class<T> annotation) {
        if (field.isAnnotationPresent(annotation)) {
            return field.getAnnotation(annotation);
        } else {
            return null;
        }
    }

    public static Method searchForGetMethod(Class<?> clazz, String property) {
        try {
            String methodName = "get" + property;
            methodName = methodName.toLowerCase();
            for (final Method method : clazz.getMethods()) {
                if (method.getName().toLowerCase().equals(methodName)) {
                    return method;
                }
            }
            
        } catch (final SecurityException e) {
            //Nothing to do
        } catch (final NoClassDefFoundError e) {
            //Nothing to do
        }
        
        return null;
    }

    public static Method searchForSetMethod(Class<?> clazz, Field field) {
        try {
            String methodName = "set" + field.getName();
            methodName = methodName.toLowerCase();
            for (final Method method : clazz.getMethods()) {
                if (method.getName().toLowerCase().equals(methodName)) {
                    return method;
                }
            }
            
        } catch (final SecurityException e) {
            //Nothing to do
        } catch (final NoClassDefFoundError e) {
            //Nothing to do
        }
        
        return null;
    }

    public static Method searchForAddMethod(Class<?> clazz, String property) {
        try {
            String methodName = "add" + property;
            methodName = methodName.toLowerCase();
            for (final Method method : clazz.getMethods()) {
                if (method.getName().toLowerCase().equals(methodName)
                        && method.getParameterTypes().length == 1
                        && "java.lang.String".equals(method.getParameterTypes()[0].getName())) {
                    return method;
                }
            }
            
        } catch (final SecurityException e) {
            //Nothing to do
        } catch (final NoClassDefFoundError e) {
            //Nothing to do
        }
        
        return null;
    }

    public static Configuration configure(Configurable instance, Configuration configuration) {
        if (configuration == null) {
            return null;
        }
        
        final Class<?> clazz = instance.getClass();
        instance.getClass().getAnnotations();
        if (!clazz.isAnnotationPresent(ConfigurationClass.class)) {
            System.out.println("Instance '"+instance+"' don't have annotaion 'ConfigurationClass'");
            return null;
            //XXX: throw new ConfigurationException("Instance '"+instance+"' don't have annotaion 'ConfigurationClass'");
        }
        
        final String configName = clazz.getAnnotation(ConfigurationClass.class).value();
        final Configuration config = configuration.getConfiguration(configName);
        if (config == null) {
            System.out.println("No configuration [" + configName + "]");
            return null;
            //XXX: throw new ConfigurationException("No configuration [" + configName + "]");
        }
        
        if (config instanceof ConfigurationImpl) {
            final ConfigurationImpl impl = (ConfigurationImpl) config;
            //XXX: lock issue here, fix it
            Configurable configurable = null;
            if (impl.configuredObjectReference != null) {
                configurable = impl.configuredObjectReference.get();
            }
            
            if (configurable != null) {
                if (configurable != instance) {
                    throw new IllegalArgumentException(
                            "Configuration can't be used by " + instance + ", "
                            + "because already in use by " + configurable);
                }
                
            } else {
                impl.configuredObjectReference = new WeakReference<Configurable>(instance);
            }
            //end (lock issue)
        }
        
        try {
            return configureByCurrent(instance, config);
        } catch (Throwable e) {
            if (config instanceof ConfigurationImpl) {
                final ConfigurationImpl impl = (ConfigurationImpl) config;
                impl.configuredObjectReference = null;
            }
        }
        //XXX: must be exception
        return null;
    }

    private static Configuration configureByCurrent(Configurable instance, Configuration configuration) throws ConfigurationException {
        final AFields annotatedFields = getConfigurationAnnotatedFields(instance.getClass());
        final Set<String> properties = configuration.getProperties();
        if (properties.isEmpty()) {
            return configuration;
        }
        
        //process simple types: String, int, long, boolean
        for (final String property : properties) {
            final AField annotatedField = annotatedFields.findByAnnotationValue(property);
            if (annotatedField == null) {
                System.out.println("Unused property " + property + " @" + configuration.getName());
                continue;
            }
            
            final Field field = annotatedField.getField();
            field.setAccessible(true);
            Object value = null;
            final String typeName = field.getType().getName();
            
            try {
                final NewClass newClass = getAnnotation(field, NewClass.class);
                if (newClass != null) {
                    value = org.exist.config.mapper.Constructor.load(newClass,
                            instance, configuration.getConfiguration(property));
                    
                } else if ("java.lang.String".equals(typeName)) {
                    value = configuration.getProperty(property);
                    
                } else if ("int".equals(typeName) || "java.lang.Integer".equals(typeName)) {
                    
                    if (field.isAnnotationPresent(ConfigurationFieldSettings.class)) {
                        final String settings = field.getAnnotation(ConfigurationFieldSettings.class).value();
                        final SettingKey settingKey = SettingKey.forSettings(settings);

                        try {
                            if (settingKey == SettingKey.RADIX) {
                                final int radix = Integer.valueOf(settingKey.extractValueFromSettings(settings));
                                value = Integer.valueOf(configuration.getProperty(property), radix);
                            } else if (settingKey == SettingKey.OCTAL_STRING) {
                                value = Integer.valueOf(configuration.getProperty(property), 8);
                            } else {
                                value = Integer.valueOf(configuration.getProperty(property));
                            }
                        } catch (NumberFormatException e) {
                            LOG.error(e);
                            //ignore
                            continue;
                        }
                        
                    } else {
                        value = configuration.getPropertyInteger(property);
                    }
                    
                } else if ("long".equals(typeName) || "java.lang.Long".equals(typeName)) {
                    value = configuration.getPropertyLong(property);
                    
                } else if ("boolean".equals(typeName) || "java.lang.Boolean".equals(typeName)) {
                    value = configuration.getPropertyBoolean(property);
                    
                } else if ("java.util.Map".equals(typeName)) {
                    value = configuration.getPropertyMap(property);
                    //TODO: skip, it will be processed as structure
                    
                } else if ("java.util.List".equals(typeName)) {
                    //skip, it will be processed as structure
                    //TODO what about simple generic types?
                    
                } else if ("org.exist.xmldb.XmldbURI".equals(typeName)) {
                    //use annotation ConfigurationFieldClassMask
                    value = org.exist.xmldb.XmldbURI.create(configuration.getProperty(property));
                    
                } else {
                    
                    Configuration conf = configuration.getConfiguration(property);
                    if (conf == null) {
                        conf = configuration;
                    }
                    
                    value = create(conf, instance, typeName);
                    if (value == null) {
                        value = configuration.getProperty(property);
                    }
                }
                
                if (value != null && !value.equals(field.get(instance))) {
                    Method method = searchForSetMethod(instance.getClass(), field);
                    if (method != null) {
                        try {
                            method.invoke(instance, value);
                        } catch (final InvocationTargetException e) {
                            method = null;
                        }
                    }
                    if (method == null) {
                        field.set(instance, value);
                    }
                }
                
            } catch (final IllegalArgumentException e) {
                final String msg = "Configuration error: " + EOL
                        + " config: " + configuration.getName() + EOL
                        + " property: " + property + EOL
                        + " message: " + e.getMessage();
                LOG.error(msg, e);
                throw new ConfigurationException(msg, e);
//                return null; //XXX: throw configuration error
                
            } catch (final IllegalAccessException e) {
                LOG.error("Security error: " + e.getMessage());
                return null; //XXX: throw configuration error
            }
        }
        
        //process simple structures: List
        try {
            for (final AField<ConfigurationFieldAsElement> element : annotatedFields.getElements()) {
                
                final Field field = element.getField();
                final String typeName = field.getType().getName();
                
                if ("java.util.List".equals(typeName)) {
                    final String confName = element.getAnnotation().value();
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
                        for (final Iterator<?> iterator = list.iterator(); iterator.hasNext();) {
                            final Object obj = iterator.next();
                            Configuration current_conf = null;
                            
                            if (!(obj instanceof Configurable)) {
                                iterator.remove();
                                continue;
                                
                            } else {
                                current_conf = ((Configurable) obj).getConfiguration();
                            }
                            
                            if (current_conf == null) {
                                //skip internal staff //TODO: static list
                                if (obj instanceof org.exist.security.internal.RealmImpl) {
                                    continue;
                                }
                                
                                LOG.debug("Unconfigured instance [" + obj + "], remove the object.");
                                //XXX: remove by method call
                                iterator.remove();
                                continue;
                            }
                            
                            //Lookup for new configuration, update if found
                            boolean found = false;
                            for (final Iterator<Configuration> i = confs.iterator(); i.hasNext();) {
                                final Configuration conf = i.next();
                                if (referenceBy != null && current_conf.equals(conf, referenceBy)) {
                                    i.remove();
                                    found = true;
                                    break;
                                    
                                } else if (referenceBy == null && current_conf.equals(conf)) {
                                    current_conf.checkForUpdates(conf.getElement());
                                    i.remove();
                                    found = true;
                                    break;
                                }
                            }
                            
                            if (!found) {
                                LOG.debug("Configuration was removed, remove the object [" + obj + "].");
                                //XXX: remove by method call
                                iterator.remove();
                            }
                        }
                        
                        //create
                        for (final Configuration conf : confs) {
                            
                            if (referenceBy != null) {
                                final String value = conf.getProperty(referenceBy);
                                if (value != null) {
                                    Method method = searchForAddMethod(instance.getClass(), confName);
                                    if (method != null) {
                                        try {
                                            method.invoke(instance, value);
                                            continue;
                                            
                                        } catch (final Exception e) {
                                            LOG.warn("Could not execute method on class " + instance.getClass().getName() + " for configuration '" + conf.getName() + "' referenceBy '" + referenceBy + "' for value '" + value + "'", e);
                                            method = null;
                                        }
                                    }
                                }
                                
                            } else {
                                final Type genericType = field.getGenericType();
                                if (genericType != null) {
                                    
                                    if ("java.util.List<java.lang.String>".equals(genericType.toString())) {
                                        
                                        final String value = conf.getValue();
                                        
                                        if (value != null) {
                                            Method method = searchForAddMethod(instance.getClass(), confName);
                                            if (method != null) {
                                                try {
                                                    method.invoke(instance, value);
                                                    continue;
                                                    
                                                } catch (final Exception e) {
                                                    method = null;
                                                }
                                            }
                                        }
                                    }
                                }
                                //TODO: AddMethod with Configuration argument
                            }
                            
                            final ConfigurationFieldClassMask annotation =
                                    getAnnotation(field, ConfigurationFieldClassMask.class);
                            
                            if (annotation == null) {
                                final NewClass newClass = getAnnotation(field, NewClass.class);
                                if (newClass != null) {
                                    final Object obj = org.exist.config.mapper.Constructor.load(
                                            newClass, instance, conf);
                                    if (obj != null) {
                                        list.add(obj);
                                    }
                                    
                                } else {
                                    LOG.error("Field '"+field.getName()+"' must have 'ConfigurationFieldClassMask' annotation ["
                                            + conf.getName() + "], skipping instance creation.");
                                }
                                
                                continue;
                            }
                            
                            final String id = conf.getProperty(Configuration.ID);
                            Object[] objs;
                            
                            if (id == null) {
                                objs = new Object[]{"", ""};
                                
                            } else {
                                objs = new Object[]{id.toLowerCase(), id};
                            }
                            
                            final String clazzName = String.format(annotation.value(), objs);
                            final Configurable obj = create(conf, instance, clazzName);
                            
                            if (obj != null) {
                                list.add(obj);
                            }
                        }
                    }
                }
            }
            
        } catch (final IllegalArgumentException e) {
            LOG.error(e);
            e.printStackTrace();
            return null;
            
        } catch (final IllegalAccessException e) {
            LOG.error(e);
            e.printStackTrace();
            return null;
            
        }
        return configuration;
    }

    private static Configurable create(Configuration conf, Configurable instance, String clazzName) {
        Class<?> clazz;
        
        try {
            clazz = Class.forName(clazzName);

            Configurable obj = null;
            try {
                final Constructor<Configurable> constructor = (Constructor<Configurable>) clazz.getConstructor(instance.getClass(), Configuration.class);
                obj = constructor.newInstance(instance, conf);
                
            } catch (final NoSuchMethodException e) {
                final Constructor<Configurable> constructor = (Constructor<Configurable>) clazz.getConstructor(Configuration.class);
                obj = constructor.newInstance(conf);
            }
            
            if (obj == null) {
                return null;
            }

            if (obj instanceof LifeCycle) {
                BrokerPool db = null;
                
                try {
                    db = BrokerPool.getInstance();
                } catch (final EXistException e) {
                    //ignore if database is starting-up
                    //TODO: add to BrokerPool static list to activate when ready
                }
                
                if (db != null) {
                    DBBroker broker = null;
                    try {
                        broker = db.get(null);
                        ((LifeCycle) obj).start(broker);
                        
                    } finally {
                        db.release(broker);
                    }
                }
                
            }
            return obj;
            
        } catch (final ClassNotFoundException e) {
            LOG.error("Class [" + clazzName + "] not found, "
                    + "skip instance creation.");
            
        } catch (final SecurityException e) {
            LOG.error("Security exception on class [" + clazzName
                    + "] creation, skip instance creation.");
            
        } catch (final NoSuchMethodException e) {
            LOG.error("Class [" + clazzName + "] constructor "
                    + "(" + instance.getClass().getName() + ", " + Configuration.class.getName() + ")"
                    + " or "
                    + "(" + Configuration.class.getName() + ")"
                    + "not found, skip instance creation.");
            
        } catch (final InstantiationException e) {
            LOG.error("Instantiation exception on class [" + clazzName
                    + "] creation, skip instance creation.");
            
        } catch (final InvocationTargetException e) {
            LOG.error("Invocation target exception on class ["
                    + clazzName + "] creation, skip instance creation.");
            
        } catch (final EXistException e) {
            LOG.error("Databasse exception on class [" + clazzName
                    + "] startup, skip instance creation.");
            
        } catch (final IllegalAccessException e) {
            LOG.error(e);
        }
        
        return null;
    }

    public static Configuration parse(File file) throws ConfigurationException {
        try {
            return parse(new FileInputStream(file));
            
        } catch (final FileNotFoundException e) {
            throw new ConfigurationException(e);
        }
    }

    public static Configuration parse(InputStream is) throws ConfigurationException {
        try {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            final InputSource src = new InputSource(is);
            final SAXParser parser = factory.newSAXParser();
            final XMLReader reader = parser.getXMLReader();
            final SAXAdapter adapter = new SAXAdapter();
            reader.setContentHandler(adapter);
            reader.parse(src);
            
            return new ConfigurationImpl((ElementAtExist) adapter.getDocument().getDocumentElement());
        } catch (final ParserConfigurationException e) {
            throw new ConfigurationException(e);
            
        } catch (final SAXException e) {
            throw new ConfigurationException(e);
            
        } catch (final IOException e) {
            throw new ConfigurationException(e);
        }
        
    }

    public static Configuration parseDefault() throws ConfigurationException {
        try {
            return parse(new FileInputStream(ConfigurationHelper.lookup("conf.xml")));
            
        } catch (final FileNotFoundException e) {
            throw new ConfigurationException(e);
        }
    }

    private static Boolean implementsInterface(Class<?> object, Class<?> interf) {
        for (final Class<?> c : object.getInterfaces()) {
            if (c.equals(interf)) {
                return true;
            }
        }
        
        return false;
    }

    protected static void serializeByReference(Configurable instance,
            SAXSerializer serializer, String fieldAsElementName,
            String referenceBy) throws SAXException {
        final Configurable resolved = ((ReferenceImpl) instance).resolve();
        final Method getMethod = searchForGetMethod(resolved.getClass(), referenceBy);
        Object value;
        
        try {
            value = getMethod.invoke(resolved);
            
        } catch (final IllegalArgumentException iae) {
            LOG.error(iae);
            //TODO : throw exception ? -pb
            return;
            
        } catch (final IllegalAccessException iae) {
            LOG.error(iae);
            //TODO : throw exception ? -pb
            return;
            
        } catch (final InvocationTargetException ite) {
            LOG.error(ite);
            //TODO : throw exception ? -pb
            return;
        }
        
        final QName qnConfig = new QName(fieldAsElementName, Configuration.NS);
        
        if (value == null) {
            final String comment = "<" + qnConfig + " " + referenceBy + "=''/>";
            final char[] ch = comment.toCharArray();
            serializer.characters(EOL.toCharArray(), 0, EOL.length());
            serializer.comment(ch, 0, ch.length);
            
        } else {
            serializer.startElement(qnConfig, null);
            serializer.attribute(new QName(referenceBy, null), value.toString());
            serializer.endElement(qnConfig);
        }
    }

    protected static void serialize(Configurable instance, SAXSerializer serializer,
            String fieldAsElementName, String referenceBy) throws SAXException {
        
        if (instance instanceof ReferenceImpl) {
            serializeByReference(instance, serializer, fieldAsElementName, referenceBy);
            return;
        }
        
        final Class<?> clazz = instance.getClass();
        instance.getClass().getAnnotations();
        if (!clazz.isAnnotationPresent(ConfigurationClass.class)) {
            return; //UNDERSTAND: throw exception
        }
        
        final AFields annotatedFields = getConfigurationAnnotatedFields(instance.getClass());
        final AField<?> annotatedField = annotatedFields.findByAnnotationValue(referenceBy);
        if (annotatedField == null) {
            return; //UNDERSTAND: throw exception
        }
        
        final Field field = annotatedField.getField();
        if (field == null) {
            LOG.error("Reference field '" + referenceBy + "' can't be found for class '" + clazz + "'");
            //TODO : throw eception ? -pb
            return;
        }
        
        field.setAccessible(true);
        String value;
        try {
            value = extractFieldValue(field, instance);
            
        } catch (final IllegalArgumentException e) {
            LOG.error(e);
            //TODO : throw exception , -pb
            return;
            
        } catch (final IllegalAccessException e) {
            LOG.error(e);
            //TODO : throw exception ? -pb
            return;
        }
        
        final QName qnConfig = new QName(fieldAsElementName, Configuration.NS);
        if (value == null) {
            final String comment = "<" + qnConfig + " " + referenceBy + "=''/>";
            final char[] ch = comment.toCharArray();
            serializer.characters(EOL.toCharArray(), 0, EOL.length());
            serializer.comment(ch, 0, ch.length);
            
        } else {
            serializer.startElement(qnConfig, null);
            serializer.attribute(new QName(referenceBy, null), value);
            serializer.endElement(qnConfig);
        }
    }

    private static String extractFieldValue(Field field, Configurable instance) throws IllegalArgumentException, IllegalAccessException {
        final String typeName = field.getType().getName();
        
        if ("java.lang.String".equals(typeName)) {
            return field.get(instance).toString();
            
        } else if ("int".equals(typeName) || "java.lang.Integer".equals(typeName)) {
            
            if (field.isAnnotationPresent(ConfigurationFieldSettings.class)) {
                final String settings = field.getAnnotation(ConfigurationFieldSettings.class).value();
                final SettingKey settingKey = SettingKey.forSettings(settings);

                if (settingKey == SettingKey.RADIX) {
                    try {
                        final int radix = Integer.valueOf(settingKey.extractValueFromSettings(settings));
                        return Integer.toString((Integer) field.get(instance), radix);
                    } catch (final Exception e) {
                        //UNDERSTAND: ignore, set back to default or throw error?
                    }
                    
                } else if (settingKey == SettingKey.OCTAL_STRING) {
                    return "0" + Integer.toString((Integer) field.get(instance), 8);
                    
                } else {
                    return Integer.toString((Integer) field.get(instance));
                }
                
            } else {
                return field.get(instance).toString();
            }
            
        } else if ("long".equals(typeName) || "java.lang.Long".equals(typeName)) {
            return field.get(instance).toString();
            
        } else if ("boolean".equals(typeName) || "java.lang.Boolean".equals(typeName)) {
            return Boolean.valueOf(field.get(instance).toString()).toString();
            
        } else if ("org.exist.xmldb.XmldbURI".equals(typeName)) {
            return field.get(instance).toString();
        }
        
        return null;
    }

    protected static void serialize(final Configurable instance, final SAXSerializer serializer) 
            throws ConfigurationException {
        
        final Class<?> clazz = instance.getClass();
        instance.getClass().getAnnotations();
        
        if (!clazz.isAnnotationPresent(ConfigurationClass.class)) {
            return; //UNDERSTAND: throw exception
        }
        
        final String configName = clazz.getAnnotation(ConfigurationClass.class).value();
        
        try {
            //open tag
            final QName qnConfig = new QName(configName, Configuration.NS);
            serializer.startElement(qnConfig, null);
            boolean simple = true;
            
            //store field's values as attributes or elements depends on annotation
            final AFields annotatedFields = getConfigurationAnnotatedFields(instance.getClass());
            try {
                //pass one - extract just attributes
                for (final AField<ConfigurationFieldAsAttribute> attr : annotatedFields.getAttributes()) {
                    final Field field = attr.getField();
                    field.setAccessible(true);
                    //XXX: artifact? remove?
                    //skip elements
                    if (field.isAnnotationPresent(ConfigurationFieldAsElement.class)) {
                        continue;
                    }
                    
                    //skip null values
                    if (field.get(instance) == null) {
                        continue;
                    }
                    
                    //now we just have attributes
                    final String value = extractFieldValue(field, instance);
                    serializer.attribute(new QName(attr.getAnnotation().value(), null), value);
                }
                
                //pass two - just elements or text nodes
                for (final AField<ConfigurationFieldAsElement> element : annotatedFields.getElements()) {
                    simple = true;
                    final Field field = element.getField();
                    field.setAccessible(true);
                    
                    //XXX: artifact? remove?
                    //skip attributes
                    if (!field.isAnnotationPresent(ConfigurationFieldAsElement.class)) {
                        continue;
                    }
                    
                    //ignore mapped fields for now, TODO: need to code back mapping.
                    if (field.isAnnotationPresent(NewClass.class)) {
                        continue;
                    }
                    
                    String referenceBy = null;
                    if (field.isAnnotationPresent(ConfigurationReferenceBy.class)) {
                        referenceBy = field.getAnnotation(ConfigurationReferenceBy.class).value();
                    }
                    
                    //skip null values
                    if (field.get(instance) == null) {
                        final String tagName = element.getAnnotation().value();
                        String comment = "<" + tagName;
                        if (referenceBy != null) {
                            comment += " " + referenceBy + "=\"\"/>";
                        } else {
                            comment += "></" + tagName + ">";
                        }
                        
                        serializer.characters(EOL.toCharArray(), 0, EOL.length());
                        final char[] ch = comment.toCharArray();
                        serializer.comment(ch, 0, ch.length);
                        continue;
                    }
                    
                    String value = null;
                    final String typeName = field.getType().getName();
                    if ("java.util.List".equals(typeName)) {
                        serializeList(instance, element, serializer);
                        continue;
                        
                    } else if (implementsInterface(field.getType(), Configurable.class)) {
                        final Configurable subInstance = (Configurable) field.get(instance);
                        serialize(subInstance, serializer);
                        continue;
                        
                    } else if ("java.util.Map".equals(typeName)) {
                        serializeMap(element.getAnnotation().value(), (Map<String, String>) field.get(instance), serializer);
                        continue;
                        
                    } else {
                        value = extractFieldValue(field, instance);
                        if (value == null) {
                            LOG.error("field '" + field.getName() + "' has unsupported type [" + typeName + "] - skiped");
                            //TODO : throw exception ? -pb
                        }
                    }
                    
                    if (value != null && value.length() > 0) {
                        if (simple) {
                            final QName qnSimple = new QName(element.getAnnotation().value(), Configuration.NS);
                            serializer.startElement(qnSimple, null);
                            serializer.characters(value);
                            serializer.endElement(new QName(element.getAnnotation().value(), null));
                            
                        } else {
                            serializer.characters(value);
                        }
                        
                    } else {
                        final String tagName = element.getAnnotation().value();
                        String comment = "<" + tagName;
                        
                        if (referenceBy != null) {
                            comment += " " + referenceBy + "=\"\"/>";
                        } else {
                            comment += "></" + tagName + ">";
                        }
                        
                        serializer.characters(EOL.toCharArray(), 0, EOL.length());
                        final char[] ch = comment.toCharArray();
                        serializer.comment(ch, 0, ch.length);
                    }
                }
                
            } catch (final IllegalArgumentException e) {
                throw new ConfigurationException(e.getMessage(), e);
                
            } catch (final IllegalAccessException e) {
                throw new ConfigurationException(e.getMessage(), e);
            }
            //close tag
            serializer.endElement(qnConfig);
            
        } catch (final SAXException saxe) {
            throw new ConfigurationException(saxe.getMessage(), saxe);
        }
    }

    private static void serializeList(Configurable instance,
            AField<ConfigurationFieldAsElement> element,
            SAXSerializer serializer) throws ConfigurationException,
            IllegalArgumentException, IllegalAccessException, SAXException {
        
        final Field field = element.getField();
        field.setAccessible(true);
        
        //determine the list entries type from its generic type
        final Type fieldGenericType = field.getGenericType();
        if (fieldGenericType instanceof ParameterizedType) {
            final Type genericTypeArgs[] = ((ParameterizedType) fieldGenericType).getActualTypeArguments();
            if (genericTypeArgs != null && genericTypeArgs.length == 1) {
                final Type genericListType = genericTypeArgs[0];
                if (genericListType.equals(String.class)) {
                    serializeStringList((List<String>) field.get(instance), element, serializer);
                } else {
                    //assume List<Configurable>
                    serializeConfigurableList((List<Configurable>) field.get(instance),
                            field, element, serializer);
                }
            }
            
        } else {
            //assume List<Configurable>
            serializeConfigurableList((List<Configurable>) field.get(instance), field, element, serializer);
        }
    }

    private static void serializeStringList(List<String> list,
            AField<ConfigurationFieldAsElement> element,
            SAXSerializer serializer) throws SAXException {
        final String fieldAsElementName = element.getAnnotation().value();
        final QName qnConfig = new QName(fieldAsElementName, Configuration.NS);
        
        for (final String listItem : list) {
            serializer.startElement(qnConfig, null);
            serializer.characters(listItem);
            serializer.endElement(qnConfig);
        }
    }

    private static void serializeConfigurableList(List<Configurable> list,
            Field field, AField<ConfigurationFieldAsElement> element,
            SAXSerializer serializer) throws ConfigurationException, SAXException {
        
        String referenceBy = null;
        if (field.isAnnotationPresent(ConfigurationReferenceBy.class)) {
            referenceBy = field.getAnnotation(ConfigurationReferenceBy.class).value();
        }
        
        for (final Configurable el : list) {
            if (referenceBy == null) {
                serialize(el, serializer);
                
            } else {
                serialize(el, serializer, element.getAnnotation().value(), referenceBy);
            }
        }
    }

    private static void serializeMap(String mapName, Map<String, String> map,
            SAXSerializer serializer) throws SAXException {
        
        if (map != null) {
            final QName mapQName = new QName(mapName, Configuration.NS);
            final QName attrQName = new QName("key");
            
            for (final Map.Entry<String, String> entry : map.entrySet()) {
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
        
        final StringBuilder accessor = new StringBuilder(XmldbURI.XMLDB_URI_PREFIX);
        accessor.append(db.getId());
        accessor.append("://");
        accessor.append("");
        
        return (FullXmldbURI) XmldbURI.create(accessor.toString(), uri.toString());
    }

    public static Configuration parse(Configurable instance, DBBroker broker,
            Collection collection, XmldbURI fileURL) throws ConfigurationException {
        
        Configuration conf = null;
        final FullXmldbURI key = getFullURI(broker.getBrokerPool(), collection.getURI().append(fileURL));
        conf = hotConfigs.get(key);
        if (conf != null) {
            return conf;
        }

        //XXX: locking required
        DocumentAtExist document = null;
        try {
            document = collection.getDocument(broker, fileURL);
            
        } catch (final PermissionDeniedException pde) {
            throw new ConfigurationException(pde.getMessage(), pde);
        }
        
        if (document == null) {
            if (broker.isReadOnly()) {
                //database in read-only mode & there no configuration file, 
                //create in memory document & configuration 
                try {
                    final StringWriter writer = new StringWriter();
                    final SAXSerializer serializer = new SAXSerializer(writer, null);
                    serializer.startDocument();
                    serialize(instance, serializer);
                    serializer.endDocument();
                    final String data = writer.toString();
                    if (data == null || data.length() == 0) {
                        return null;
                    }
                    return parse(new ByteArrayInputStream(data.getBytes("UTF-8")));
                    
                } catch (final SAXException saxe) {
                    throw new ConfigurationException(saxe.getMessage(), saxe);
                    
                } catch (final UnsupportedEncodingException e) {
                    return null;
                }
            }
            
            try {
                document = save(instance, broker, collection, fileURL);
                
            } catch (final IOException e) {
                LOG.error(e.getMessage(), e);
                //TODO : throw exception ? -pb
                return null;
            }
        }
        
        if (document == null) {
            return null; //possibly on corrupted database, find better solution (recovery flag?)
        }
        
        final ElementAtExist confElement = (ElementAtExist) document.getDocumentElement();
        if (confElement == null) {
            return null; //possibly on corrupted database, find better solution (recovery flag?)
        }
        
        conf = new ConfigurationImpl(confElement);
        hotConfigs.put(key, conf);
        return conf;
    }

    public static Configuration parse(DocumentAtExist document) {
        if (document == null) {
            return null;
        }
        
        Configuration conf;
        final FullXmldbURI key = getFullURI(document.getDatabase(), document.getURI());
        
        conf = hotConfigs.get(key);
        if (conf != null) {
            return conf;
        }
        
        final ElementAtExist confElement = (ElementAtExist) document.getDocumentElement();
        if (confElement == null) {
            return null; //possibly on corrupted database, find better solution (recovery flag?)
        }
        conf = new ConfigurationImpl(confElement);
        
        hotConfigs.put(key, conf);
        return conf;
    }

    public static DocumentAtExist save(Configurable instance, XmldbURI uri) throws IOException {
        BrokerPool database;
        try {
            database = BrokerPool.getInstance();
            
        } catch (final EXistException e) {
            throw new IOException(e);
        }
        
        DBBroker broker = null;
        try {
            broker = database.get(null);

            return save(broker, instance, uri);

        } catch (final EXistException e) {
            throw new IOException(e);
            
        } finally {
            database.release(broker);
        }
    }

    public static DocumentAtExist save(DBBroker broker, Configurable instance, XmldbURI uri) throws IOException {
        try {
            final Collection collection = broker.getCollection(uri.removeLastSegment());
            if (collection == null) {
                throw new IOException("Collection URI = " + uri.removeLastSegment() + " not found.");
            }
            return save(instance, broker, collection, uri.lastSegment());
            
        } catch (final PermissionDeniedException pde) {
            throw new IOException(pde);
            
        } catch (final EXistException e) {
            throw new IOException(e);
        }
    }
    
    protected static Set<FullXmldbURI> saving = new HashSet<FullXmldbURI>();

    public static DocumentAtExist save(Configurable instance, DBBroker broker, Collection collection, XmldbURI uri) throws IOException, ConfigurationException {
        
        final StringWriter writer = new StringWriter();
        final SAXSerializer serializer = new SAXSerializer(writer, null);
        
        try {
            serializer.startDocument();
            serialize(instance, serializer);
            serializer.endDocument();
            
        } catch (final SAXException saxe) {
            throw new ConfigurationException(saxe.getMessage(), saxe);
        }
        
        final String data = writer.toString();
        if (data == null || data.length() == 0) {
            return null;
        }
        
        FullXmldbURI fullURI = null;
        final BrokerPool pool = broker.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        Txn txn = null;
        LOG.info("Storing configuration " + collection.getURI() + "/" + uri);
        final Subject currentUser = broker.getSubject();
        
        try {
            broker.setSubject(pool.getSecurityManager().getSystemSubject());
            txn = transact.beginTransaction();
            txn.acquireLock(collection.getLock(), Lock.WRITE_LOCK);
            final IndexInfo info = collection.validateXMLResource(txn, broker, uri, data);
            final DocumentImpl doc = info.getDocument();
            doc.getMetadata().setMimeType(MimeType.XML_TYPE.getName());
            doc.getPermissions().setMode(Permission.DEFAULT_SYSTSEM_RESOURCE_PERM);
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
            
        } catch (final Exception e) {

            if (fullURI != null) {
                saving.remove(fullURI);
            }

            if (txn != null) {
                transact.abort(txn);
            }

            LOG.error(e);
            throw new IOException(e);
            
        } finally {
            transact.close(txn);
            broker.setSubject(currentUser);
        }
    }

    public static synchronized void clear(Database db) {
        for (final Entry<FullXmldbURI, Configuration> entry : hotConfigs.entrySet()) {
            final FullXmldbURI uri = entry.getKey();
            if (uri.getInstanceName().equals(db.getId())) {
                final Configuration conf = entry.getValue();
                if (conf instanceof ConfigurationImpl) {
                    ((ConfigurationImpl) conf).configuredObjectReference = null;
                }
            }
            hotConfigs.remove(uri);
        }
    }

    public static void unregister(Configuration configuration) {
        if (configuration == null) {
            return;
        }
        
        if (hotConfigs.containsValue(configuration)) {
            for (final Entry<FullXmldbURI, Configuration> entry : hotConfigs.entrySet()) {
                if (entry.getValue() == configuration) {
                    hotConfigs.remove(entry.getKey());
                    return;
                }
            }
        }
    }

    private static Object instantiateObject(String className, Configuration configuration) throws ConfigurationException {
        try {
            final Class<?> clazz = Class.forName(className);
            final Constructor<?> cstr = clazz.getConstructor(Configuration.class);
            return cstr.newInstance(configuration);
            
        } catch (final Exception e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }

    private static class AFields implements Iterable<AField> {

        private List<AField<ConfigurationFieldAsAttribute>> attributes =
                new ArrayList<AField<ConfigurationFieldAsAttribute>>();
        
        private List<AField<ConfigurationFieldAsElement>> elements =
                new ArrayList<AField<ConfigurationFieldAsElement>>();

        public void addAttribute(AField<ConfigurationFieldAsAttribute> attribute) {
            this.attributes.add(attribute);
        }

        public void addAllAttributes(List<AField<ConfigurationFieldAsAttribute>> attributes) {
            this.attributes.addAll(attributes);
        }

        public void addElement(AField<ConfigurationFieldAsElement> element) {
            this.elements.add(element);
        }

        public void addAllElements(List<AField<ConfigurationFieldAsElement>> elements) {
            this.elements.addAll(elements);
        }

        public List<AField<ConfigurationFieldAsAttribute>> getAttributes() {
            return attributes;
        }

        public List<AField<ConfigurationFieldAsElement>> getElements() {
            return elements;
        }

        public AField findByAnnotationValue(String value) {
            for (final AField<ConfigurationFieldAsAttribute> attr : attributes) {
                if (attr.getAnnotation().value().equals(value)) {
                    return attr;
                }
            }
            
            for (final AField<ConfigurationFieldAsElement> element : elements) {
                if (element.getAnnotation().value().equals(value)) {
                    return element;
                }
            }
            return null;
        }

        @Override
        public Iterator<AField> iterator() {
            return new Iterator<AField>() {
                
                private Iterator<AField<ConfigurationFieldAsAttribute>> itAttributes = attributes.iterator();
                
                private Iterator<AField<ConfigurationFieldAsElement>> itElements = elements.iterator();

                @Override
                public boolean hasNext() {
                    return itAttributes.hasNext() | itElements.hasNext();
                }

                @Override
                public AField next() {
                    if (itAttributes.hasNext()) {
                        return itAttributes.next();
                        
                    } else if (itElements.hasNext()) {
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

    private static class AField<T> {

        private final T annotation;
        private final Field field;

        public AField(T annotation, Field field) {
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