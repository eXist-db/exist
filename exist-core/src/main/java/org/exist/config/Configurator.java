/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.config;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.LifeCycle;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.config.annotation.*;
import org.exist.config.annotation.ConfigurationFieldSettings.SettingKey;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.ExistSAXParserFactory;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import com.evolvedbinary.j8fu.function.ConsumerE;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.util.StringInputSource;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xmldb.FullXmldbURI;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Expression;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import static java.lang.invoke.MethodType.methodType;
import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;

/**
 * This class handle all configuration needs: extracting and saving,
 * reconfiguring &amp; etc.
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Configurator {

    public final static Logger LOG = LogManager.getLogger(Configurator.class);
    private final static String EOL = System.getProperty("line.separator", "\n");
    protected static final ConcurrentMap<FullXmldbURI, Configuration> hotConfigs = new ConcurrentHashMap<>();

    protected static AFields getConfigurationAnnotatedFields(Class<?> clazz) {
        final AFields fields = new AFields();
        for (final Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(ConfigurationFieldAsAttribute.class)) {
                fields.addAttribute(new AField<>(field.getAnnotation(ConfigurationFieldAsAttribute.class), field));
            } else if (field.isAnnotationPresent(ConfigurationFieldAsElement.class)) {
                fields.addElement(new AField<>(field.getAnnotation(ConfigurationFieldAsElement.class), field));
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

    /**
     * Finds the Getter Method for the named property of a class
     *
     * @param clazz The class of methods to search
     * @param property The named property in the class to find a getter method for
     *
     * @return The Getter method for the property or null
     */
    public static Method searchForGetMethod(final Class<?> clazz, final String property) {
        try {
            final String methodName = ("get" + property).toLowerCase();
            for (final Method method : clazz.getMethods()) {
                if (method.getName().equalsIgnoreCase(methodName)) {
                    return method;
                }
            }
        } catch (final SecurityException | NoClassDefFoundError e) {
            LOG.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Finds the Setter Method for a class field
     *
     * @param clazz The class of methods to search
     * @param field The field in the class to find a setter method for
     *
     * @return The Setter method for the field or null
     */
    public static Method searchForSetMethod(final Class<?> clazz, final Field field) {
        try {
            final String methodName = ("set" + field.getName()).toLowerCase();
            for (final Method method : clazz.getMethods()) {
                if (method.getName().equalsIgnoreCase(methodName)) {
                    return method;
                }
            }
        } catch (final SecurityException | NoClassDefFoundError e) {
            LOG.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Finds the Adder Method for the named property of a class
     *
     * @param clazz The class of methods to search
     * @param property The named property in the class to find an adder method for. e.g. if the property is "cog" then we find the method "void addCog(Cog cog)"
     *
     * @return The Adder method for the property or null
     */
    public static Method searchForAddMethod(final Class<?> clazz, final String property) {
        try {
            final String methodName = ("add" + property).toLowerCase();
            for (final Method method : clazz.getMethods()) {
                if (method.getName().equalsIgnoreCase(methodName)
                        && method.getParameterTypes().length == 1
                        && String.class.getName().equals(method.getParameterTypes()[0].getName())) {
                    return method;
                }
            }
        } catch (final SecurityException | NoClassDefFoundError e) {
            LOG.error(e.getMessage(), e);
        }
        return null;
    }

    public static Method searchForInsertMethod(final Class<?> clazz, final String property) {
        try {
            final String methodName = ("insert" + property).toLowerCase();
            for (final Method method : clazz.getMethods()) {
                if (method.getName().equalsIgnoreCase(methodName)
                        && method.getParameterTypes().length == 2
                        && int.class.getName().equals(method.getParameterTypes()[0].getName())
                        && String.class.getName().equals(method.getParameterTypes()[1].getName())) {
                    return method;
                }
            }
        } catch (final SecurityException | NoClassDefFoundError e) {
            LOG.error(e.getMessage(), e);
        }

        return null;
    }

    public static Configuration configure(final Configurable instance, final Configuration configuration) {
        if (configuration == null) {
            return null;
        }
        
        final Class<?> clazz = instance.getClass();
        if (!clazz.isAnnotationPresent(ConfigurationClass.class)) {
            LOG.warn("Instance '{}' is missing annotation '@org.exist.config.annotation.ConfigurationClass'", instance);
            return null;
            //XXX: throw new ConfigurationException("Instance '"+instance+"' don't have annotaion 'ConfigurationClass'");
        }
        
        final String configName = clazz.getAnnotation(ConfigurationClass.class).value();
        final Configuration config = configuration.getConfiguration(configName);
        if (config == null) {
            LOG.warn("No configuration [{}] found for [{}]", configName, clazz.getName());
            return null;
            //XXX: throw new ConfigurationException("No configuration [" + configName + "]");
        }
        
        if (config instanceof ConfigurationImpl impl) {
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
                impl.configuredObjectReference = new WeakReference<>(instance);
            }
            //end (lock issue)
        }
        
        try {
            return configureByCurrent(instance, config);
        } catch (final Throwable e) {
            LOG.error(e.getMessage(), e);
            if (config instanceof ConfigurationImpl impl) {
                impl.configuredObjectReference = null;
            }
        } finally {
            config.clearCache();
        }
        //XXX: must be exception
        return null;
    }

    private static Configuration configureByCurrent(final Configurable instance, final Configuration configuration) throws ConfigurationException {
        final AFields annotatedFields = getConfigurationAnnotatedFields(instance.getClass());
        final Set<String> properties = configuration.getProperties();
        if (properties.isEmpty()) {
            return configuration;
        }
        
        //process simple types: String, int, long, boolean
        for (final String property : properties) {
            final AField annotatedField = annotatedFields.findByAnnotationValue(property);
            if (annotatedField == null) {
                LOG.warn("Unused property {} @{}", property, configuration.getName());
                continue;
            }
            
            final Field field = annotatedField.getField();
            field.setAccessible(true);
            Object value = null;
            final Class<?> fieldType = field.getType();
            
            try {
                final NewClass newClass = getAnnotation(field, NewClass.class);
                if (newClass != null) {
                    value = org.exist.config.mapper.Constructor.load(newClass,
                            instance, configuration.getConfiguration(property));
                    
                } else if (String.class == fieldType) {
                    //String
                    value = configuration.getProperty(property);
                    
                } else if (int.class == fieldType || Integer.class == fieldType) {
                    //int or Integer

                    if (field.isAnnotationPresent(ConfigurationFieldSettings.class)) {
                        final String settings = field.getAnnotation(ConfigurationFieldSettings.class).value();
                        final SettingKey settingKey = SettingKey.forSettings(settings);

                        try {
                            if (settingKey == SettingKey.RADIX) {
                                final int radix = Integer.parseInt(settingKey.extractValueFromSettings(settings));
                                value = Integer.valueOf(configuration.getProperty(property), radix);
                            } else if (settingKey == SettingKey.OCTAL_STRING) {
                                value = Integer.valueOf(configuration.getProperty(property), 8);
                            } else {
                                value = Integer.valueOf(configuration.getProperty(property));
                            }
                        } catch (final NumberFormatException e) {
                            LOG.error(e.getMessage(), e);
                            //ignore
                            continue;
                        }
                        
                    } else {
                        value = configuration.getPropertyInteger(property);
                    }
                    
                } else if (long.class == fieldType || Long.class == fieldType) {
                    //long or Long
                    value = configuration.getPropertyLong(property);
                    
                } else if (boolean.class == fieldType || Boolean.class == fieldType) {
                    //boolean or Boolean
                    value = configuration.getPropertyBoolean(property);
                    
                } else if (Map.class == fieldType) {
                    //Map
                    //skip contents, they will be processed as structure in the next loop on ConfigurationFieldAsElement
                    value = configuration.getPropertyMap(property);

                } else if(List.class == fieldType) {
                    //List
                    //skip, will be processed as structure in the next loop on ConfigurationFieldAsElement
                    //TODO what about simple generic types?

                } else if (XmldbURI.class ==  fieldType) {
                    //use annotation ConfigurationFieldClassMask
                    value = org.exist.xmldb.XmldbURI.create(configuration.getProperty(property));
                    
                } else {
                    Configuration conf = configuration.getConfiguration(property);
                    if (conf == null) {
                        conf = configuration;
                    }
                    
                    value = create(conf, instance, fieldType);
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
                            LOG.warn(e);
                            method = null;
                        }
                    }
                    if (method == null) {
                        field.set(instance, value);
                    }
                }
                
            } catch (final IllegalArgumentException iae) {
                final String msg = "Configuration error: " + EOL
                        + " config: " + configuration.getName() + EOL
                        + " property: " + property + EOL
                        + " message: " + iae.getMessage();
                LOG.error(msg, iae);
                throw new ConfigurationException(msg, iae);
//                return null; //XXX: throw configuration error
                
            } catch (final IllegalAccessException iae) {
                final String msg = "Security error: " + EOL
                        + " config: " + configuration.getName() + EOL
                        + " property: " + property + EOL
                        + " message: " + iae.getMessage();
                LOG.error(msg, iae);
                throw new ConfigurationException(msg, iae);
                
//                LOG.error("Security error: " + iae.getMessage(), iae);
//                return null; //XXX: throw configuration error
            }
        }
        
        //process simple structures: List
        Field field = null;
        try {
            for (final AField<ConfigurationFieldAsElement> element : annotatedFields.getElements()) {
                
                field = element.getField();
                final Class<?> fieldType = field.getType();

                if (List.class == fieldType) {
                    //List
                    final String confName = element.getAnnotation().value();
                    field.setAccessible(true);
                    List list = (List) field.get(instance);

                    final Optional<String> referenceBy;
                    List<Configuration> confs;
                    if (field.isAnnotationPresent(ConfigurationReferenceBy.class)) {
                        confs = configuration.getConfigurations(confName);
                        referenceBy = Optional.ofNullable(field.getAnnotation(ConfigurationReferenceBy.class).value());
                    } else {
                        confs = configuration.getConfigurations(confName);
                        referenceBy = Optional.empty();
                    }
                    
                    if (list == null) {
                        list = new ArrayList(confs.size());  // has to be raw type as we don't know what it should be.
                        field.set(instance, list);
                    }
                    
                    if (confs != null) {
                        //remove & update

                        final Map<String, Integer> removed = new HashMap<>(); //referencedBy -> index

                        for (int i = 0; i < list.size(); i++) {
                            final Object obj = list.get(i);
                            Configuration current_conf = null;
                            
                            if (!(obj instanceof Configurable)) {
                                list.remove(i); //TODO Surely we should log a problem here or throw an exception?
                                continue;

                            } else if (obj instanceof Reference) {

                                if (!referenceBy.isPresent()) {
                                    LOG.error("illegal design '{}' [{}]", configuration.getName(), field);
                                    list.remove(i);
                                    continue;
                                } else {

                                    final String name = ((Reference) obj).getName();

                                    //Lookup for new configuration, update if found
                                    final List<Configuration> applicableConfs = filter(confs, conf ->
                                            Optional.ofNullable(conf.getPropertyBoolean(referenceBy.get()))
                                                    .map(value -> !value.equals(name))
                                                    .orElse(true));

                                    if(applicableConfs.size() == confs.size()) {
                                        LOG.debug("Configuration was removed, will attempt to replace object [{}].", obj);
                                        final Field referee = getFieldRecursive(Optional.of(list.get(i).getClass()), referenceBy.get());
                                        if(referee != null) {
                                            referee.setAccessible(true);
                                            removed.put((String) referee.get(list.remove(i)), i);
                                        } else {
                                            LOG.error("Could not lookup referenced field: {} against: {}", referenceBy.get(), list.get(i).getClass().getName());
                                            list.remove(i);
                                        }
                                    } else {
                                        confs = applicableConfs;
                                    }
                                }
                                
                            } else {
                                current_conf = ((Configurable) obj).getConfiguration();
                            }
                            
                            if (current_conf == null) {
                                //skip internal stuff //TODO: static list
                                if (obj instanceof org.exist.security.internal.RealmImpl) {
                                    continue;
                                }
                                if(list.size() > i) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("No configured instance of [{}], will attempt to replace object...", obj);
                                    }

                                    final Field referee = getFieldRecursive(Optional.of(list.get(i).getClass()), referenceBy.get());
                                    if (referee != null) {
                                        referee.setAccessible(true);
                                        removed.put((String) referee.get(list.remove(i)), i);
                                    } else {
                                        LOG.error("Could not lookup referenced field: {} against: {}", referenceBy.get(), list.get(i).getClass().getName());
                                        list.remove(i);
                                    }
                                }
                                continue;
                            }

                            //Lookup for new configuration, update if found
                            final Configuration final_current_conf = current_conf;
                            final List<Configuration> applicableConfs = filter(confs, conf -> !final_current_conf.equals(conf, referenceBy));

                            if(applicableConfs.size() == confs.size()) {
                                LOG.debug("Configuration was removed, will attempt to replace [{}].", obj);
                                if(list.size() > i) {
                                    Configurable old = ((Configurable) list.remove(i));
                                    String key = old.getConfiguration().getProperty(referenceBy.orElse(Configuration.ID));

                                    if (key != null) {
                                        removed.put(key, i);
                                    }
                                }
                            } else {
                                confs = applicableConfs;
                            }
                        }
                        
                        //create
                        for (final Configuration conf : confs) {
                            if (referenceBy.isPresent()) {
                                final String value = conf.getProperty(referenceBy.get());
                                if(value != null) {
                                    final Optional<ConsumerE<String, ReflectiveOperationException>> updateFn = updateListFn(instance, confName, removed, value);

                                    if(!updateFn.isPresent()) {
                                        LOG.error("Could not insert configured object");
                                    } else {
                                        try {
                                            updateFn.get().accept(value);
                                            continue;
                                        } catch(final ReflectiveOperationException e) {
                                            LOG.warn("Could not update {} for configuration '{}' referenceBy '{}' for value '{}'", instance.getClass().getName(), conf.getName(), referenceBy.get(), value, e);
                                        }
                                    }
                                }
                            } else {
                                final Type genericType = field.getGenericType();
                                if (genericType != null) {
                                    if ("java.util.List<java.lang.String>".equals(genericType.toString())) {
                                        final String value = conf.getValue();
                                        
                                        if (value != null) {
                                            final Optional<ConsumerE<String, ReflectiveOperationException>> updateFn = updateListFn(instance, confName, removed, value);

                                            if(!updateFn.isPresent()) {
                                                LOG.error("Could not insert configured object");
                                            } else {
                                                try {
                                                    updateFn.get().accept(value);
                                                    continue;
                                                } catch (final ReflectiveOperationException e) {
                                                    LOG.warn("Could not update {} for configuration '{}' for value '{}'", instance.getClass().getName(), conf.getName(), value, e);
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
                                    LOG.error("Field '{}' must have '@org.exist.config.annotation.ConfigurationFieldClassMask' annotation [{}], skipping instance creation.", field.getName(), conf.getName());
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
            
        } catch (final IllegalArgumentException iae) {
            final String msg = "Configuration error: " + EOL
                    + " config: " + configuration.getName() + EOL
                    + " field: " + field + EOL
                    + " message: " + iae.getMessage();
            LOG.error(msg, iae);
            throw new ConfigurationException(msg, iae);
            
//            LOG.error(iae.getMessage(), iae);
//            return null;
            
        } catch (final IllegalAccessException iae) {
            final String msg = "Security error: " + EOL
                    + " config: " + configuration.getName() + EOL
                    + " field: " + field + EOL
                    + " message: " + iae.getMessage();
            LOG.error(msg, iae);
            throw new ConfigurationException(msg, iae);

//            LOG.error(iae.getMessage(), iae);
//            return null;
        }

        return configuration;
    }

    /**
     * If the value was previously removed, we can attempt
     * to reinsert it at the same index so as to keep the
     * ordering consistent. Otherwise... we just add it.
     */
    private static Optional<ConsumerE<String, ReflectiveOperationException>> updateListFn(final Configurable instance, final String confName, final Map<String, Integer> removed, final String value) {
        return Optional.ofNullable(removed.get(value)).<Optional<ConsumerE<String, ReflectiveOperationException>>>map(removedIdx -> Optional.ofNullable(
            searchForInsertMethod(instance.getClass(), confName))
                .map(insertMethod -> v -> insertMethod.invoke(instance, removedIdx, v))
        ).orElse(Optional.ofNullable(
            searchForAddMethod(instance.getClass(), confName))
                .map(addMethod -> v -> addMethod.invoke(instance, v)));
    }

    private static Field getFieldRecursive(final Optional<Class> maybeClazz, final String name) {
        return maybeClazz.map(clazz ->
                Stream.of(clazz.getDeclaredFields())
                        .filter(field -> field.getName().equals(name))
                        .findFirst()
                        .orElse(getFieldRecursive(Optional.ofNullable(clazz.getSuperclass()), name)))
                .orElse(null);
    }

    private static List<Configuration> filter(final List<Configuration> configurations, Predicate<Configuration> predicate) {
        return configurations.stream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * @return The Configurable or null
     */
    private static Configurable create(final Configuration conf, final Configurable instance, final String clazzName) {

        Configurable configurable;
        try {
            final Class<?> clazz = Class.forName(clazzName);
            configurable =  create(conf, instance, clazz);
        } catch (final ClassNotFoundException cnfe) {
            LOG.error("Class [{}] not found, skip instance creation.", clazzName);
            configurable = null;
        }
        return configurable;
    }

    /**
     * @return The Configurable or null
     */
    private static Configurable create(final Configuration conf, final Configurable instance, final Class<?> clazz) {
        boolean interrupted = false;
        try {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            Configurable obj = null;
            try {
                final MethodHandle methodHandle = lookup.findConstructor(clazz, methodType(void.class, instance.getClass(), Configuration.class));
                final BiFunction<Configurable, Configuration, Configurable> constructor =
                        (BiFunction<Configurable, Configuration, Configurable>)
                                LambdaMetafactory.metafactory(
                                        lookup, "apply", methodType(BiFunction.class),
                                        methodHandle.type().erase(), methodHandle, methodHandle.type()).getTarget().invokeExact();

                obj = constructor.apply(instance, conf);
            } catch (final Throwable e) {
                if (e instanceof InterruptedException) {
                    interrupted = true;
                }

                if(LOG.isDebugEnabled()) {
                    LOG.debug("Unable to invoke Constructor on Configurable instance '{}', so creating new Constructor...", e.getMessage());
                }

                try {
                    final MethodHandle methodHandle = lookup.findConstructor(clazz, methodType(void.class, Configuration.class));
                    final Function<Configuration, Configurable> constructor =
                            (Function<Configuration, Configurable>)
                                    LambdaMetafactory.metafactory(
                                            lookup, "apply", methodType(Function.class),
                                            methodHandle.type().erase(), methodHandle, methodHandle.type()).getTarget().invokeExact();
                    obj = constructor.apply(conf);
                } catch (final Throwable ee) {
                    if (ee instanceof InterruptedException) {
                        interrupted = true;
                    }

                    LOG.warn("Instantiation exception on {} creation '{}', skipping instance creation.", clazz, ee.getMessage());
                    LOG.debug(e.getMessage(), ee);
                }
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
                    LOG.warn("Unable to start lifecycle object: {}", obj.getClass().getName());
                    //TODO: add to BrokerPool static list to activate when ready
                }
                
                if (db != null) {
                    try(final DBBroker broker = db.getBroker();
                        final Txn transaction = broker.continueOrBeginTransaction()) {
                        ((LifeCycle) obj).start(broker, transaction);
                        transaction.commit();
                    }
                }
                
            }
            return obj;

        } catch (final EXistException ee) {
            LOG.warn("Database exception on {} startup '{}', skipping instance creation.", clazz, ee.getMessage());
            LOG.debug(ee.getMessage(), ee);

            return null;
        } finally {
            if (interrupted) {
                // NOTE: must set interrupted flag
                Thread.currentThread().interrupt();
            }
        }
    }

    public static Configuration parse(final InputStream is) throws ConfigurationException {
        try {
            final SAXParserFactory factory = ExistSAXParserFactory.getSAXParserFactory();
            factory.setNamespaceAware(true);
            final InputSource src = new InputSource(is);
            final SAXParser parser = factory.newSAXParser();
            final XMLReader reader = parser.getXMLReader();

            reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            reader.setFeature(FEATURE_SECURE_PROCESSING, true);

            final SAXAdapter adapter = new SAXAdapter((Expression) null);
            reader.setContentHandler(adapter);
            reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
            reader.parse(src);
            
            return new ConfigurationImpl(adapter.getDocument().getDocumentElement());

        } catch (final ParserConfigurationException | SAXException | IOException e) {
            throw new ConfigurationException(e);
        }
    }

    private static Boolean implementsInterface(final Class<?> object, final Class<?> iface) {
        for (final Class<?> c : object.getInterfaces()) {
            if (c.equals(iface)) {
                return true;
            }
        }
        return false;
    }

    protected static void serializeByReference(final Configurable instance,
            final SAXSerializer serializer, final String fieldAsElementName,
            final String referenceBy) throws SAXException {

        Object value = ((ReferenceImpl) instance).getName();

//        final Configurable resolved = ((ReferenceImpl) instance).resolve();
//        final Method getMethod = searchForGetMethod(resolved.getClass(), referenceBy);
//        Object value;
//
//        try {
//            value = getMethod.invoke(resolved);
//
//        } catch (final IllegalArgumentException iae) {
//            LOG.error(iae.getMessage(), iae);
//            //TODO : throw exception ? -pb
//            return;
//
//        } catch (final IllegalAccessException iae) {
//            LOG.error(iae.getMessage(), iae);
//            //TODO : throw exception ? -pb
//            return;
//
//        } catch (final InvocationTargetException ite) {
//            LOG.error(ite.getMessage(), ite);
//            //TODO : throw exception ? -pb
//            return;
//        }
        
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

    protected static void serialize(final Configurable instance, final SAXSerializer serializer,
            final String fieldAsElementName, final String referenceBy) throws SAXException {
        
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
            LOG.error("Reference field '{}' can't be found for class '{}'", referenceBy, clazz);
            //TODO : throw eception ? -pb
            return;
        }
        
        field.setAccessible(true);
        String value;
        try {
            value = extractFieldValue(field, instance);
            
        } catch (final IllegalArgumentException | IllegalAccessException iae) {
            LOG.error(iae.getMessage(), iae);
            //TODO : throw exception , -pb
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

    private static String extractFieldValue(final Field field, final Configurable instance) throws IllegalArgumentException, IllegalAccessException {
        final Class<?> fieldType = field.getType();
        
        if (String.class == fieldType) {
            return field.get(instance).toString();
            
        } else if (int.class == fieldType || Integer.class == fieldType) {
            
            if (field.isAnnotationPresent(ConfigurationFieldSettings.class)) {
                final String settings = field.getAnnotation(ConfigurationFieldSettings.class).value();
                final SettingKey settingKey = SettingKey.forSettings(settings);

                if (settingKey == SettingKey.RADIX) {
                    try {
                        final int radix = Integer.parseInt(settingKey.extractValueFromSettings(settings));
                        return Integer.toString((Integer) field.get(instance), radix);
                    } catch (final Exception e) {
                        LOG.error(e);
                        //TODO UNDERSTAND: ignore, set back to default or throw error?
                    }
                    
                } else if (settingKey == SettingKey.OCTAL_STRING) {
                    return "0" + Integer.toString((Integer) field.get(instance), 8);
                    
                } else {
                    return Integer.toString((Integer) field.get(instance));
                }
                
            } else {
                return field.get(instance).toString();
            }
            
        } else if (long.class == fieldType || Long.class == fieldType) {
            return field.get(instance).toString();
            
        } else if (boolean.class == fieldType || Boolean.class == fieldType) {
            return Boolean.valueOf(field.get(instance).toString()).toString();
            
        } else if (XmldbURI.class == fieldType) {
            return field.get(instance).toString();
        }
        
        return null;
    }

    protected static void serialize(final Configurable instance, final SAXSerializer serializer) 
            throws ConfigurationException {
        
        final Class<?> clazz = instance.getClass();

        
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
                            LOG.error("field '{}' has unsupported type [{}] - skipped", field.getName(), typeName);
                            //TODO : throw exception ? -pb
                        }
                    }
                    
                    if (value != null && !value.isEmpty()) {
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
                
            } catch (final IllegalArgumentException | IllegalAccessException e) {
                throw new ConfigurationException(e.getMessage(), e);
                
            }
            //close tag
            serializer.endElement(qnConfig);
            
        } catch (final SAXException saxe) {
            throw new ConfigurationException(saxe.getMessage(), saxe);
        }
    }

    private static void serializeList(final Configurable instance,
           final AField<ConfigurationFieldAsElement> element,
           final SAXSerializer serializer) throws ConfigurationException,
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

    private static void serializeStringList(final List<String> list,
            final AField<ConfigurationFieldAsElement> element,
            final SAXSerializer serializer) throws SAXException {
        final String fieldAsElementName = element.getAnnotation().value();
        final QName qnConfig = new QName(fieldAsElementName, Configuration.NS);
        
        for (final String listItem : list) {
            serializer.startElement(qnConfig, null);
            serializer.characters(listItem);
            serializer.endElement(qnConfig);
        }
    }

    private static void serializeConfigurableList(final List<Configurable> list,
            final Field field, final AField<ConfigurationFieldAsElement> element,
            final SAXSerializer serializer) throws ConfigurationException, SAXException {
        
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

    private static void serializeMap(final String mapName, final Map<String, String> map,
            final SAXSerializer serializer) throws SAXException {
        
        if (map != null) {
            final QName mapQName = new QName(mapName, Configuration.NS);
            final QName attrQName = new QName("key", XMLConstants.NULL_NS_URI);
            
            for (final Map.Entry<String, String> entry : map.entrySet()) {
                serializer.startElement(mapQName, null);
                serializer.attribute(attrQName, entry.getKey());
                serializer.characters(entry.getValue());
                serializer.endElement(mapQName);
            }
        }
    }

    public static FullXmldbURI getFullURI(final BrokerPool pool, final XmldbURI uri) {
        if (uri instanceof FullXmldbURI) {
            return (FullXmldbURI) uri;
        }

        final StringBuilder accessor = new StringBuilder(XmldbURI.XMLDB_URI_PREFIX);
        accessor.append(pool.getId());
        accessor.append("://");
        accessor.append("");
        
        return (FullXmldbURI) XmldbURI.create(accessor.toString(), uri.toString());
    }

    public static Configuration parse(final Configurable instance, final DBBroker broker,
            final Collection collection, final XmldbURI fileURL) throws ConfigurationException {
        
        final FullXmldbURI key = getFullURI(broker.getBrokerPool(), collection.getURI().append(fileURL));
        Configuration conf = hotConfigs.get(key);
        if (conf != null) {
            return conf;
        }

        //XXX: locking required
        DocumentImpl document;
        try {
            document = collection.getDocument(broker, fileURL);
            
        } catch (final PermissionDeniedException pde) {
            throw new ConfigurationException(pde.getMessage(), pde);
        }
        
        if (document == null) {
            if (broker.isReadOnly()) {
                //database in read-only mode & there no configuration file, 
                //create in memory document & configuration 
                try (final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream();
                        final Writer writer = new OutputStreamWriter(os)) {
                    final SAXSerializer serializer = new SAXSerializer(writer, null);
                    serializer.startDocument();
                    serialize(instance, serializer);
                    serializer.endDocument();
                    if (os.size() == 0) {
                        return null;
                    }
                    return parse(os.toInputStream());
                    
                } catch (final IOException | SAXException e) {
                    throw new ConfigurationException(e.getMessage(), e);
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
        
        final Element confElement = document.getDocumentElement();
        if (confElement == null) {
            return null; //possibly on corrupted database, find better solution (recovery flag?)
        }
        
        conf = new ConfigurationImpl(confElement);
        hotConfigs.put(key, conf);
        return conf;
    }

    public static Configuration parse(final BrokerPool pool, final DocumentImpl document) {
        if (document == null) {
            return null;
        }

        Configuration conf;
        final FullXmldbURI key = getFullURI(pool, document.getURI());
        
        conf = hotConfigs.get(key);
        if (conf != null) {
            return conf;
        }
        
        final Element confElement = document.getDocumentElement();
        if (confElement == null) {
            return null; //possibly on corrupted database, find better solution (recovery flag?)
        }
        conf = new ConfigurationImpl(confElement);
        
        hotConfigs.put(key, conf);
        return conf;
    }

    public static DocumentImpl save(final Configurable instance, final XmldbURI uri) throws IOException {
        BrokerPool database;
        try {
            database = BrokerPool.getInstance();
            
        } catch (final EXistException e) {
            throw new IOException(e);
        }

        try(final DBBroker broker = database.getBroker()) {
            return save(broker, instance, uri);
        } catch (final EXistException e) {
            throw new IOException(e);
        }
    }

    public static DocumentImpl save(final DBBroker broker, final Configurable instance, final XmldbURI uri) throws IOException {
        try {
            final Collection collection = broker.getCollection(uri.removeLastSegment());
            if (collection == null) {
                throw new IOException("Collection URI = " + uri.removeLastSegment() + " not found.");
            }
            return save(instance, broker, collection, uri.lastSegment());
            
        } catch (final PermissionDeniedException | EXistException pde) {
            throw new IOException(pde);
            
        }
    }
    
    protected static final Set<FullXmldbURI> saving = new ConcurrentSkipListSet<>();

    public static DocumentImpl save(final Configurable instance, final DBBroker broker, final Collection collection, final XmldbURI uri) throws IOException, ConfigurationException {
        
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
        if (data == null || data.isEmpty()) {
            return null;
        }
        
        FullXmldbURI fullURI = null;
        final BrokerPool pool = broker.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        LOG.info("Storing configuration {}/{}", collection.getURI(), uri);

        final SecurityManager securityManager = pool.getSecurityManager();
        try {
            final Subject systemSubject = securityManager.getSystemSubject();
            broker.pushSubject(systemSubject);
            Txn txn = broker.getCurrentTransaction();
            final boolean txnInProgress = txn != null;
            if(!txnInProgress) {
                txn = transact.beginTransaction();
            }

            try {
                txn.acquireCollectionLock(() -> pool.getLockManager().acquireCollectionWriteLock(collection.getURI()));

                fullURI = getFullURI(pool, collection.getURI().append(uri));
                saving.add(fullURI);

                final Permission systemResourcePermission = PermissionFactory.getDefaultResourcePermission(pool.getSecurityManager());
                systemResourcePermission.setOwner(systemSubject);
                systemResourcePermission.setGroup(systemSubject.getDefaultGroup());
                systemResourcePermission.setMode(Permission.DEFAULT_SYSTEM_RESOURCE_PERM);

                broker.storeDocument(txn, uri, new StringInputSource(data), MimeType.XML_TYPE, null, null, systemResourcePermission, null, null, collection);

                broker.saveCollection(txn, collection);
                if (!txnInProgress) {
                    transact.commit(txn);
                }
            } catch(final EXistException | PermissionDeniedException | SAXException | LockException e) {
                if(!txnInProgress) {
                    transact.abort(txn);
                }
                throw e;
            } finally {
                if(!txnInProgress) {
                    txn.close();
                }
            }
            saving.remove(fullURI);
            broker.flush();
            broker.sync(Sync.MAJOR);
            return collection.getDocument(broker, uri.lastSegment());

        } catch(final EXistException | PermissionDeniedException | SAXException | LockException e) {
            LOG.error(e);
            if (fullURI != null) {
                saving.remove(fullURI);
            }
            throw new IOException(e);
        } finally {
            broker.popSubject();
        }
    }

    public static synchronized void clear(final Database db) {
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

    public static void unregister(final Configuration configuration) {
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

    private static class AFields implements Iterable<AField> {

        private List<AField<ConfigurationFieldAsAttribute>> attributes = new ArrayList<>();
        
        private List<AField<ConfigurationFieldAsElement>> elements = new ArrayList<>();

        public void addAttribute(final AField<ConfigurationFieldAsAttribute> attribute) {
            this.attributes.add(attribute);
        }

        public void addAllAttributes(final List<AField<ConfigurationFieldAsAttribute>> attributes) {
            this.attributes.addAll(attributes);
        }

        public void addElement(final AField<ConfigurationFieldAsElement> element) {
            this.elements.add(element);
        }

        public void addAllElements(final List<AField<ConfigurationFieldAsElement>> elements) {
            this.elements.addAll(elements);
        }

        public List<AField<ConfigurationFieldAsAttribute>> getAttributes() {
            return attributes;
        }

        public List<AField<ConfigurationFieldAsElement>> getElements() {
            return elements;
        }

        public AField findByAnnotationValue(final String value) {
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
            return new Iterator<>() {

                private final Iterator<AField<ConfigurationFieldAsAttribute>> itAttributes = attributes.iterator();
                private final Iterator<AField<ConfigurationFieldAsElement>> itElements = elements.iterator();

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

        public AField(final T annotation, final Field field) {
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

    public static Configuration getConfigurtion(final BrokerPool db, final XmldbURI uri) {
        return hotConfigs.get(Configurator.getFullURI(db, uri));
    }
}
