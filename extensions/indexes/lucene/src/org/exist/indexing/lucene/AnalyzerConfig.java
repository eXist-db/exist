package org.exist.indexing.lucene;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.w3c.dom.Element;
import org.exist.util.DatabaseConfigurationException;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.exist.collections.CollectionConfiguration;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

public class AnalyzerConfig {

    private static final Logger LOG = Logger.getLogger(AnalyzerConfig.class);
	
    private final static String ID_ATTRIBUTE = "id";
    private final static String CLASS_ATTRIBUTE = "class";

    private Map<String, Analyzer> analyzers = new TreeMap<String, Analyzer>();
    private Analyzer defaultAnalyzer = null;

    public Analyzer getAnalyzerById(String id) {
        return analyzers.get(id);
    }

    public Analyzer getDefaultAnalyzer() {
        return defaultAnalyzer;
    }
    
    public void addAnalyzer(Element config) throws DatabaseConfigurationException {
        final String id = config.getAttribute(ID_ATTRIBUTE);
        final Analyzer analyzer = configureAnalyzer(config);
        
        if(analyzer == null) {
            return;
        }
        
        if(id == null || id.length() == 0) {
            defaultAnalyzer = analyzer;
        } else {
            analyzers.put(id, analyzer);
        }
    }

    protected static Analyzer configureAnalyzer(Element config) throws DatabaseConfigurationException {
        final String className = config.getAttribute(CLASS_ATTRIBUTE);
        
        if(className != null && className.length() != 0) {
            try {
                Class<?> clazz = Class.forName(className);
                if (!Analyzer.class.isAssignableFrom(clazz)) {
                    LOG.warn("Lucene index: analyzer class has to be a subclass of " + Analyzer.class.getName());
                    return null;
                }
                
                final List<KeyTypedValue> cParams = getConstructorParameters(config);
                final Class<?>[] cParamClasses = new Class<?>[cParams.size()];
                final Object[] cParamValues = new Object[cParams.size()];
                for(int i = 0; i < cParams.size(); i++) {
                    KeyTypedValue ktv = cParams.get(i);
                    cParamClasses[i] = ktv.getValueClass();
                    cParamValues[i] = ktv.getValue();
                }
                
                final Constructor<?> cstr = clazz.getDeclaredConstructor(cParamClasses);
                cstr.setAccessible(true);
                
                return (Analyzer)cstr.newInstance(cParamValues);
            } catch (ClassNotFoundException e) {
                LOG.error("Lucene index: analyzer class " + className + " not found.");
            } catch (IllegalAccessException e) {
                LOG.error("Exception while instantiating analyzer class " + className + ": " + e.getMessage(), e);
            } catch (InstantiationException e) {
                LOG.error("Exception while instantiating analyzer class " + className + ": " + e.getMessage(), e);
            } catch (NoSuchMethodException nsme) {
                LOG.error("Exception while instantiating analyzer class " + className + ": " + nsme.getMessage(), nsme);
            } catch (InvocationTargetException ite) {
                LOG.error("Exception while instantiating analyzer class " + className + ": " + ite.getMessage(), ite);
            } catch (ParameterException pe) {
                LOG.error("Exception while instantiating analyzer class " + className + ": " + pe.getMessage(), pe);
            }
        }
        return null;
    }
    
    private static List<KeyTypedValue> getConstructorParameters(Element config) throws ParameterException {
        final List<KeyTypedValue> parameters = new ArrayList<KeyTypedValue>();
        final NodeList params = config.getElementsByTagNameNS(CollectionConfiguration.NAMESPACE, "param");
        
        for(int i = 0; i < params.getLength(); i++) {
            parameters.add(getConstructorParameter((Element)params.item(i)));
        }
        
        return parameters;
    }
    
    private static KeyTypedValue getConstructorParameter(Element param) throws ParameterException {
        final NamedNodeMap attrs = param.getAttributes();
        final String name = attrs.getNamedItem("name").getNodeValue();

        final String type;
        if(attrs.getNamedItem("type") != null) {
            type = attrs.getNamedItem("type").getNodeValue();
        } else {
            type = null;
        }
        
        final String value;
        if(attrs.getNamedItem("value") != null) {
            value = attrs.getNamedItem("value").getNodeValue();
        } else {
            value = null;
        }
        
        final KeyTypedValue parameter;
        if(type != null && type.equals("java.lang.reflect.Field")){
            final String clazzName = value.substring(0, value.lastIndexOf("."));
            final String fieldName = value.substring(value.indexOf(".") + 1);

            try {
                final Class fieldClazz = Class.forName(clazzName);
                final Field field = fieldClazz.getField(fieldName);
                final Object fValue = field.get(fieldClazz.newInstance());
                parameter = new KeyTypedValue(name, fValue);
            } catch(NoSuchFieldException nsfe) {
                throw new ParameterException(nsfe.getMessage(), nsfe);
            } catch(ClassNotFoundException nsfe) {
                throw new ParameterException(nsfe.getMessage(), nsfe);
            } catch(InstantiationException nsfe) {
                throw new ParameterException(nsfe.getMessage(), nsfe);
            } catch(IllegalAccessException nsfe) {
                throw new ParameterException(nsfe.getMessage(), nsfe);
            }
        } else if(type != null && type.equals("java.io.File")) {
            final File f = new File(value);
            parameter = new KeyTypedValue(name, f, File.class);
        } else if(type != null && type.equals("java.util.Set")) {
            final Set s = getConstructorParameterSetValues(param);
            parameter = new KeyTypedValue(name, s, Set.class);
        } else if(type != null && (type.equals("java.lang.Integer") || type.equals("int"))) {
            final Integer n = Integer.parseInt(value);
            parameter = new KeyTypedValue(name, n);
        } else if(type != null && (type.equals("java.lang.Boolean") || type.equals("boolean"))) {
            final boolean b = Boolean.parseBoolean(value);
            parameter = new KeyTypedValue(name, b);
        } else {
            //assume java.lang.String
            parameter = new KeyTypedValue(name, value);
        }
        
        return parameter;
    }
    
    private static Set<String> getConstructorParameterSetValues(Element param) {
        final Set<String> set = new HashSet<String>();
        final NodeList values = param.getElementsByTagNameNS(CollectionConfiguration.NAMESPACE, "value");
        for(int i = 0; i < values.getLength(); i++) {
            final Element value = (Element)values.item(i);
            
            //TODO getNodeValue() on org.exist.dom.ElementImpl should return null according to W3C spec!
            if(value instanceof org.exist.dom.ElementImpl) {
               set.add(value.getNodeValue());
            } else {
                set.add(value.getTextContent());
            }
        }
        
        return set;
    }

    private static class KeyTypedValue {
        private final String key;
        private final Object value;
        private final Class<?> valueClass;
        
        public KeyTypedValue(String key, Object value) {
            this(key, value, value.getClass());
        }
        
        public KeyTypedValue(String key, Object value, Class<?> valueClass) {
            this.key = key;
            this.value = value;
            this.valueClass = valueClass;
        }
        
        public String getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }
        
        public Class<?> getValueClass() {
            return valueClass;
        }
    }

    private static class ParameterException extends Exception {
        public ParameterException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
