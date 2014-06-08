/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
 */
package org.exist.indexing.lucene;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import org.apache.log4j.Logger;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.Version;

import org.exist.collections.CollectionConfiguration;
import org.exist.util.DatabaseConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

public class AnalyzerConfig {

    /*

     Supported configurations

     <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>

     <analyzer id="ws" class="org.apache.lucene.analysis.WhitespaceAnalyzer"/>

     <analyzer id="stdstops" class="org.apache.lucene.analysis.standard.StandardAnalyzer">
     ..<param name="stopwords" type="java.io.File" value="/tmp/stop.txt"/>
     </analyzer>

     <analyzer id="stdstops" class="org.apache.lucene.analysis.standard.StandardAnalyzer">
     ..<param name="stopwords" type="java.util.Set">
     ....<value>the</value>
     ....<value>this</value>
     ....<value>and</value>
     ....<value>that</value>
     ..</param>
     </analyzer>

     <analyzer id="sbstops" class="org.apache.lucene.analysis.snowball.SnowballAnalyzer">
     ..<param name="name" value="English"/>
     ..<param name="stopwords" type="java.util.Set">
     ....<value>the</value>
     ....<value>this</value>
     ....<value>and</value>
     ....<value>that</value>
     ..</param>
     </analyzer>

     */
    private static final Logger LOG = Logger.getLogger(AnalyzerConfig.class);

    private static final String ID_ATTRIBUTE = "id";

    private static final String NAME_ATTRIBUTE = "name";
    private static final String TYPE_ATTRIBUTE = "type";
    private static final String CLASS_ATTRIBUTE = "class";
    private static final String PARAM_VALUE_ENTRY = "value";
    private static final String PARAM_ELEMENT_NAME = "param";

    private Map<String, Analyzer> analyzers = new TreeMap<String, Analyzer>();
    private Analyzer defaultAnalyzer = null;

    public Analyzer getAnalyzerById(String id) {
        return analyzers.get(id);
    }

    public Analyzer getDefaultAnalyzer() {
        return defaultAnalyzer;
    }

    /**
     * Parse <analyzer/> element and register configured analyzer.
     *
     * @param config The analyzer element from .xconf file.
     *
     * @throws DatabaseConfigurationException Something unexpected happened.
     */
    public void addAnalyzer(Element config) throws DatabaseConfigurationException {

        // Configure lucene analuzer with configuration
        final Analyzer analyzer = configureAnalyzer(config);
        if (analyzer == null) {
            return;
        }

        // Get (optional) id-attribute of analyzer
        final String id = config.getAttribute(ID_ATTRIBUTE);

        // If no ID is provided, register as default analyzer
        // else register analyzer
        if (StringUtils.isBlank(id)) {
            setDefaultAnalyzer(analyzer);
        } else {
            analyzers.put(id, analyzer);
        }
    }

    /**
     * Set default the analyzer.
     *
     * @param analyzer Lucene analyzer
     */
    public void setDefaultAnalyzer(Analyzer analyzer) {
        defaultAnalyzer = analyzer;
    }

    /**
     * Parse <analyzer/> element from xconf and initialize an analyzer with the
     * parameters.
     *
     * @param config The analyzer element
     * @return Initialized Analyzer object
     *
     * @throws DatabaseConfigurationException Something unexpected happened.
     */
    protected static Analyzer configureAnalyzer(Element config) throws DatabaseConfigurationException {

        // Get classname from attribute
        final String className = config.getAttribute(CLASS_ATTRIBUTE);
        
        Analyzer newAnalyzer=null;

        if (StringUtils.isBlank(className)) {
            // No classname is defined.
            LOG.error("Missing class attribute or attribute is empty.");
            // DW: throw exception?
            
        } else {
            // Classname is defined.

            // Probe class
            Class<?> clazz = null;
            try {
                clazz = Class.forName(className);

            } catch (ClassNotFoundException e) {
                LOG.error(String.format("Lucene index: analyzer class %s not found. (%s)", className, e.getMessage()));
                return null;
            }

            // CHeck if class is an Analyzer
            if (!Analyzer.class.isAssignableFrom(clazz)) {
                LOG.error(String.format("Lucene index: analyzer class has to be a subclass of %s", Analyzer.class.getName()));
                return null;
            }

            // Get list of parameters
            List<KeyTypedValue> cParams;
            try {
                cParams = getAllConstructorParameters(config);

            } catch (ParameterException pe) {
                // Unable to parse parameters.
                LOG.error(String.format("Unable to get parameters for %s: %s", className, pe.getMessage()), pe);
                cParams = new ArrayList<KeyTypedValue>();
            }

            // Iterate over all parameters, convert data to two arrays
            // that can be used in the reflection code
            final Class<?> cParamClasses[] = new Class<?>[cParams.size()];
            final Object cParamValues[] = new Object[cParams.size()];
            for (int i = 0; i < cParams.size(); i++) {
                KeyTypedValue ktv = cParams.get(i);
                cParamClasses[i] = ktv.getValueClass();
                cParamValues[i] = ktv.getValue();
            }

            // Create new analyzer
            if (cParamClasses.length > 0 && cParamClasses[0] == Version.class) {

                if (LOG.isDebugEnabled()) {
                    Version version = (Version) cParamValues[0];
                    LOG.debug(String.format("An explicit Version %s of lucene has been specified.", version.toString()));
                }

                // A lucene Version object has been provided, so it shall be used
                newAnalyzer = createInstance(clazz, cParamClasses, cParamValues);

            } else {
                    // Either no parameters have been provided or more than one parameter

                // Extend arrays with (default) Version object info, add to front.
                Class<?>[] vcParamClasses = addVersionToClasses(cParamClasses);
                Object[] vcParamValues = addVersionValueToValues(cParamValues);

                // Finally create Analyzer
                newAnalyzer = createInstance(clazz, vcParamClasses, vcParamValues);

                // Fallback scenario: a special (not standard type of) Analyzer has been specified without 
                // a 'Version' argument on purpose. For this (try) to create the Analyzer with 
                // the original parameters.
                if (newAnalyzer == null) {
                    newAnalyzer = createInstance(clazz, cParamClasses, cParamValues);
                }

            }

        }

        if (newAnalyzer == null) {
            LOG.error(String.format("Unable to create analyzer '%s'", className));
        }

        return newAnalyzer;
    }

    /**
     * Create instance of the lucene analyzer with provided arguments
     *
     * @param clazz The analyzer class
     * @param vcParamClasses The parameter classes
     * @param vcParamValues The parameter values
     * @return The lucene analyzer
     */
    private static Analyzer createInstance(Class<?> clazz, Class<?>[] vcParamClasses, Object[] vcParamValues) {

        String className = clazz.getName();

        try {
            final Constructor<?> cstr = clazz.getDeclaredConstructor(vcParamClasses);
            cstr.setAccessible(true);
            
            if(LOG.isDebugEnabled()){
                LOG.debug(String.format("Using analyzer %s", className));
            }

            return (Analyzer) cstr.newInstance(vcParamValues);
            
        } catch (IllegalArgumentException e) {
            LOG.error(String.format("Exception while instantiating analyzer class %s: %s", className, e.getMessage()), e);
        } catch (IllegalAccessException e) {
            LOG.error(String.format("Exception while instantiating analyzer class %s: %s", className, e.getMessage()), e);
        } catch (InstantiationException e) {
            LOG.error(String.format("Exception while instantiating analyzer class %s: %s", className, e.getMessage()), e);
        } catch (InvocationTargetException ite) {
            LOG.error(String.format("Exception while instantiating analyzer class %s: %s", className, ite.getMessage()), ite);
        } catch (NoSuchMethodException ex) {
            LOG.error(String.format("Could not find matching analyzer class constructor%s: %s", className, ex.getMessage()), ex);
        } catch (SecurityException ex) {
            LOG.error(String.format("Exception while instantiating analyzer class %s: %s", className, ex.getMessage()), ex);
        }
        
        return null;
    }

    /**
     * Extend list of values, add version-value to front
     */
    private static Object[] addVersionValueToValues(final Object[] cParamValues) {
        final Object vcParamValues[] = new Object[cParamValues.length + 1];
        vcParamValues[0] = LuceneIndex.LUCENE_VERSION_IN_USE;
        System.arraycopy(cParamValues, 0, vcParamValues, 1, cParamValues.length);
        return vcParamValues;
    }

    /**
     * Extend list of classes, add version-class to front
     */
    private static Class<?>[] addVersionToClasses(final Class<?>[] cParamClasses) {
        final Class<?> vcParamClasses[] = new Class<?>[cParamClasses.length + 1];
        vcParamClasses[0] = Version.class;
        System.arraycopy(cParamClasses, 0, vcParamClasses, 1, cParamClasses.length);
        return vcParamClasses;
    }

    /**
     * Retrieve parameter info from all <param/> elements.
     *
     * @param config The <analyzer/> element from the provided configuration
     * @return List of triples key-value-valueType
     * @throws org.exist.indexing.lucene.AnalyzerConfig.ParameterException
     */
    private static List<KeyTypedValue> getAllConstructorParameters(Element config) throws ParameterException {
        final List<KeyTypedValue> parameters = new ArrayList<KeyTypedValue>();
        final NodeList params = config.getElementsByTagNameNS(CollectionConfiguration.NAMESPACE, PARAM_ELEMENT_NAME);

        // iterate over all <param/> elements
        for (int i = 0; i < params.getLength(); i++) {
            parameters.add(getConstructorParameter((Element) params.item(i)));
        }

        return parameters;
    }

    /**
     * Retrieve configuration information from one <param/> element. Type
     * information is used to construct actual data containing objects.
     *
     * @param param Element that represents <param/>
     * @return Triple key-value-value-type
     * @throws org.exist.indexing.lucene.AnalyzerConfig.ParameterException
     */
    private static KeyTypedValue getConstructorParameter(Element param) throws ParameterException {

        // Get attributes
        final NamedNodeMap attrs = param.getAttributes();

        // Get name of parameter, NULL when no value is present
        final String name;
        if (attrs.getNamedItem(NAME_ATTRIBUTE) != null) {
            name = attrs.getNamedItem(NAME_ATTRIBUTE).getNodeValue();
        } else {
            // DW: TODO need to check if the NULL value is safe to use.
            name = null;
        }
        

        // Get value type information of parameter, NULL when not available
        final String type;
        if (attrs.getNamedItem(TYPE_ATTRIBUTE) != null) {
            type = attrs.getNamedItem(TYPE_ATTRIBUTE).getNodeValue();
        } else {
            // Safe to use, NULL check done.
            type = null;
        }

        // Get actual value from attribute, or NULL when not available.
        final String value;
        if (attrs.getNamedItem(PARAM_VALUE_ENTRY) != null) {
            value = attrs.getNamedItem(PARAM_VALUE_ENTRY).getNodeValue();
        } else {
            // DW: TODO need to check if the NULL value is safe to use.
            // This is dangerous, unless a Set is filled
            value = null;
        }

        // Place holder return value
        KeyTypedValue parameter = null;
        
        if(StringUtils.isBlank(type)){
            // No type is provided, assume string.
            parameter = new KeyTypedValue(name, value);
            
        } else if ("java.lang.reflect.Field".equals(type)) {

            if (value == null) {
                throw new ParameterException("The 'value' attribute must exist and must contain a full classname.");
            }

            // Use reflection
            // - retrieve classname from the value field
            // - retrieve fieldname from the value field
            final String clazzName = value.substring(0, value.lastIndexOf("."));
            final String fieldName = value.substring(value.lastIndexOf(".") + 1);

            try {
                // Retrieve value from Field
                final Class<?> fieldClazz = Class.forName(clazzName);
                final Field field = fieldClazz.getField(fieldName);
                field.setAccessible(true);
                final Object fValue = field.get(fieldClazz.newInstance());
                parameter = new KeyTypedValue(name, fValue);

            } catch (NoSuchFieldException nsfe) {
                throw new ParameterException(nsfe.getMessage(), nsfe);
            } catch (ClassNotFoundException nsfe) {
                throw new ParameterException(nsfe.getMessage(), nsfe);
            } catch (InstantiationException nsfe) {
                throw new ParameterException(nsfe.getMessage(), nsfe);
            } catch (IllegalAccessException nsfe) {
                throw new ParameterException(nsfe.getMessage(), nsfe);
            }

        } else if ("java.io.File".equals(type)) {
            // This is actually decrecated now, "Reader" must be used
            final File f = new File(value);
            parameter = new KeyTypedValue(name, f, File.class);

        } else if ("java.io.FileReader".equals(type) || "file".equals(type)) {

            // DW: Experimental
            File f = new File(value);
            Reader fileReader = null;

            try {
                fileReader = new FileReader(f);
                parameter = new KeyTypedValue(name, fileReader, Reader.class);

            } catch (FileNotFoundException ex) {
                LOG.error(String.format("File %s could not be found.", f.getAbsolutePath()), ex);
                IOUtils.closeQuietly(fileReader);
            }

        } else if ("java.util.Set".equals(type)) {
            // This is actually deprecated now, Lucene4 requires CharArraySet
            final Set s = getConstructorParameterSetValues(param);
            parameter = new KeyTypedValue(name, s, Set.class);

        } else if ("org.apache.lucene.analysis.util.CharArraySet".equals(type) || "set".equals(type)) {
            // This is mandatory to use iso a normal Set since Lucene 4
            final CharArraySet s = getConstructorParameterCharArraySetValues(param);
            parameter = new KeyTypedValue(name, s, CharArraySet.class);

        } else if ("java.lang.Integer".equals(type) || "int".equals(type)) {
            try {
                final Integer n = Integer.parseInt(value);
                parameter = new KeyTypedValue(name, n);
            } catch (NumberFormatException ex) {
                LOG.error(String.format("Value %s could not be converted to an integer.", value));
            }

        } else if ("java.lang.Boolean".equals(type) || "boolean".equals(type)) {
            // Straight forward
            final boolean b = Boolean.parseBoolean(value);
            parameter = new KeyTypedValue(name, b);

        } else {
            // FallBack type == null or did not match 
            try {
                //if the type is an Enum then use valueOf()
                final Class clazz = Class.forName(type);
                if (clazz.isEnum()) {
                    parameter = new KeyTypedValue(name, Enum.valueOf(clazz, value), clazz);
                } else {
                    //default, assume java.lang.String
                    parameter = new KeyTypedValue(name, value);
                }

            } catch (ClassNotFoundException cnfe) {
                throw new ParameterException(String.format("Class for type: %s not found. %s", type, cnfe.getMessage()), cnfe);
            }
        }

        return parameter;
    }

    /**
     * Get parameter configuration data as standard Java (Hash)Set.
     *
     * @param param The parameter-configuration element.
     * @return Set of parameter values
     */
    private static Set<String> getConstructorParameterSetValues(Element param) {
        final Set<String> set = new HashSet<String>();
        final NodeList values = param.getElementsByTagNameNS(CollectionConfiguration.NAMESPACE, PARAM_VALUE_ENTRY);
        for (int i = 0; i < values.getLength(); i++) {
            final Element value = (Element) values.item(i);

            //TODO getNodeValue() on org.exist.dom.ElementImpl should return null according to W3C spec!
            if (value instanceof org.exist.dom.ElementImpl) {
                set.add(value.getNodeValue());
            } else {
                set.add(value.getTextContent());
            }
        }

        return set;
    }

    /**
     * Get parameter configuration data as a Lucene CharArraySet.
     *
     * @param param The parameter-configuration element.
     * @return Parameter data as Lucene CharArraySet
     */
    private static CharArraySet getConstructorParameterCharArraySetValues(Element param) {
        final Set<String> set = getConstructorParameterSetValues(param);
        return CharArraySet.copy(LuceneIndex.LUCENE_VERSION_IN_USE, set);
    }

    /**
     * CLass for containing the Triple : key (name), corresponding value and
     * class type of value.
     */
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

    /**
     * Exception class to for reporting problems with the parameters.
     */
    private static class ParameterException extends Exception {

        public ParameterException(String message) {
            super(message);
        }

        public ParameterException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
