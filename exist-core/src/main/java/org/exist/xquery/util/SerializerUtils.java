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
package org.exist.xquery.util;

import io.lacuna.bifurcan.IEntry;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.apache.commons.lang3.StringUtils;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.fn.FnModule;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Serializer utilities used by several XQuery functions.
 */
public class SerializerUtils {

    // Constant strings
    public static final String OUTPUT_NAMESPACE = "output";
    public static final String CHARACTER_MAP_ELEMENT_KEY = "character-map";
    public static final String CHARACTER_ATTR_KEY = "character";
    public static final String MAP_STRING_ATTR_KEY = "map-string";

    public static final String MSG_NON_VALUE_ATTRIBUTE = "Attribute other than value in serialization parameter";

    public static final String MSG_UNSUPPORTED_TYPE = "Unsupported type ";
    public static final String MSG_FOR_PARAMETER_VALUE =  " for parameter value";

    private static final Set<String> W3CParameterConventionKeys = new HashSet<>();
    static {
        for (W3CParameterConvention convention : W3CParameterConvention.values()) {
            W3CParameterConventionKeys.add(convention.getParameterName());
        }
    }

    private static final Map<String, ParameterConvention<String>> W3C_PARAMETER_CONVENTIONS_BY_NAME = new HashMap<>();
    static {
        for (final W3CParameterConvention w3cParameterConvention : W3CParameterConvention.values()) {
            W3C_PARAMETER_CONVENTIONS_BY_NAME.put(w3cParameterConvention.getParameterName(), w3cParameterConvention);
        }
    }

    public interface ParameterConvention<T> {

        /**
         * Get the Parameter name.
         *
         * @return the parameter name
         */
        T getParameterName();

        /**
         * Get the Local name (i.e. without prefix or namespace)
         * of the parameter.
         *
         * @return the local parameter name
         */
        String getLocalParameterName();

        /**
         * Get the Type of the parameter.
         *
         * @return the type
         */
        int getType();

        /**
         * Get the Cardinality of the parameter.
         *
         * @return the cardinality
         */
        Cardinality getCardinality();

        /**
         * Get the Default Value of the parameter.
         *
         * @return the default value
         */
        Sequence getDefaultValue();
    }

    /**
     * See <a href="https://www.w3.org/TR/xpath-functions-31/#func-serialize">fn:serialize</a>
     */
    public enum W3CParameterConvention implements ParameterConvention<String> {
        ALLOW_DUPLICATE_NAMES("allow-duplicate-names", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.FALSE),
        BYTE_ORDER_MARK("byte-order-mark", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.FALSE),
        CDATA_SECTION_ELEMENTS(OutputKeys.CDATA_SECTION_ELEMENTS, Type.QNAME, Cardinality.ZERO_OR_MORE, Sequence.EMPTY_SEQUENCE),
        DOCTYPE_PUBLIC(OutputKeys.DOCTYPE_PUBLIC, Type.STRING, Cardinality.ZERO_OR_ONE, Sequence.EMPTY_SEQUENCE),   //default: () means "absent"
        DOCTYPE_SYSTEM(OutputKeys.DOCTYPE_SYSTEM, Type.STRING, Cardinality.ZERO_OR_ONE, Sequence.EMPTY_SEQUENCE),   //default: () means "absent"
        ENCODING(OutputKeys.ENCODING, Type.STRING, Cardinality.ZERO_OR_ONE, new StringValue(UTF_8.name())),
        ESCAPE_URI_ATTRIBUTES("escape-uri-attributes", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.TRUE),
        HTML_VERSION(EXistOutputKeys.HTML_VERSION, Type.DECIMAL, Cardinality.ZERO_OR_ONE, new DecimalValue(5)),
        INCLUDE_CONTENT_TYPE("include-content-type", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.TRUE),
        INDENT(OutputKeys.INDENT, Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.FALSE),
        ITEM_SEPARATOR(EXistOutputKeys.ITEM_SEPARATOR, Type.STRING, Cardinality.ZERO_OR_ONE, Sequence.EMPTY_SEQUENCE),  //default: () means "absent"
        JSON_NODE_OUTPUT_METHOD(EXistOutputKeys.JSON_NODE_OUTPUT_METHOD, Type.STRING, Cardinality.ZERO_OR_ONE, new StringValue("xml")),
        MEDIA_TYPE(OutputKeys.MEDIA_TYPE, Type.STRING, Cardinality.ZERO_OR_ONE, Sequence.EMPTY_SEQUENCE),    // default: a media type suitable for the chosen method
        METHOD(OutputKeys.METHOD, Type.STRING, Cardinality.ZERO_OR_ONE, new StringValue("xml")),
        NORMALIZATION_FORM("normalization-form", Type.STRING, Cardinality.ZERO_OR_ONE, new StringValue("none")),
        OMIT_XML_DECLARATION(OutputKeys.OMIT_XML_DECLARATION, Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.TRUE),
        STANDALONE(OutputKeys.STANDALONE, Type.BOOLEAN, Cardinality.ZERO_OR_ONE, Sequence.EMPTY_SEQUENCE),   //default: () means "omit"
        SUPPRESS_INDENTATION("suppress-indentation", Type.QNAME, Cardinality.ZERO_OR_MORE, Sequence.EMPTY_SEQUENCE),
        UNDECLARE_PREFIXES("undeclare-prefixes", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.FALSE),
        USE_CHARACTER_MAPS(EXistOutputKeys.USE_CHARACTER_MAPS, Type.MAP_ITEM, Cardinality.ZERO_OR_ONE, Sequence.EMPTY_SEQUENCE),
        VERSION(OutputKeys.VERSION, Type.STRING, Cardinality.ZERO_OR_ONE, new StringValue("1.0"));

        private final String parameterName;
        private final int type;
        private final Cardinality cardinality;
        private final Sequence defaultValue;

        W3CParameterConvention(final String parameterName, final int type, final Cardinality cardinality, final Sequence defaultValue) {
            this.parameterName = parameterName;
            this.type = type;
            this.cardinality = cardinality;
            this.defaultValue = defaultValue;
        }

        @Override
        public String getParameterName() {
            return parameterName;
        }

        @Override
        public String getLocalParameterName() {
            return parameterName;
        }

        @Override
        public int getType() {
            return type;
        }

        @Override
        public Cardinality getCardinality() {
            return cardinality;
        }

        @Override
        public Sequence getDefaultValue() {
            return defaultValue;
        }
    }

    /**
     * for Exist xquery specific functions
     */
    public enum ExistParameterConvention implements ParameterConvention<QName> {
        OUTPUT_DOCTYPE(EXistOutputKeys.OUTPUT_DOCTYPE, Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.FALSE),
        EXPAND_XINCLUDE(EXistOutputKeys.EXPAND_XINCLUDES, Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.TRUE),
        PROCESS_XSL_PI(EXistOutputKeys.PROCESS_XSL_PI, Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.TRUE),
        JSON_IGNORE_WHITE_SPACE_TEXT_NODES(EXistOutputKeys.JSON_IGNORE_WHITESPACE_TEXT_NODES, Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.TRUE),
        HIGHLIGHT_MATCHES(EXistOutputKeys.HIGHLIGHT_MATCHES, Type.STRING, Cardinality.ZERO_OR_ONE, new StringValue("none")),
        JSONP(EXistOutputKeys.JSONP, Type.STRING, Cardinality.ZERO_OR_ONE, Sequence.EMPTY_SEQUENCE),
        ADD_EXIST_ID(EXistOutputKeys.ADD_EXIST_ID, Type.STRING, Cardinality.ZERO_OR_ONE, new StringValue("none")),
        // default of 4 corresponds to the existing eXist default, although 3 is in the spec
        INDENT_SPACES("indent-spaces", Type.INTEGER, Cardinality.ZERO_OR_ONE, new IntegerValue(4)),
        INSERT_FINAL_NEWLINE(EXistOutputKeys.INSERT_FINAL_NEWLINE, Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.FALSE);


        private final QName parameterName;
        private final int type;
        private final Cardinality cardinality;
        private final Sequence defaultValue;

        ExistParameterConvention(final String parameterName, final int type, final Cardinality cardinality, final Sequence defaultValue) {
            this.parameterName = new QName(parameterName,Namespaces.EXIST_NS,Namespaces.EXIST_NS_PREFIX);
            this.type = type;
            this.cardinality = cardinality;
            this.defaultValue = defaultValue;
        }

        @Override
        public QName getParameterName() {
            return parameterName;
        }

        @Override
        public String getLocalParameterName() {
            return parameterName.getLocalPart();
        }

        @Override
        public int getType() {
            return type;
        }

        @Override
        public Cardinality getCardinality() {
            return cardinality;
        }

        @Override
        public Sequence getDefaultValue() {
            return defaultValue;
        }
    }

    /**
     * Parse output:serialization-parameters XML fragment into serialization
     * properties as defined by the fn:serialize function.
     *
     * @param parent     the parent expression calling this method
     * @param parameters root node of the XML fragment
     * @param serializationProperties parameters are added to the given properties
     * @throws XPathException in case of dynamic error
     */
    public static void getSerializationOptions(final Expression parent, final NodeValue parameters, final Properties serializationProperties) throws XPathException {
        try {
            final Properties propertiesInXML = new Properties();
            final XMLStreamReader reader = parent.getContext().getXMLStreamReader(parameters);
            while (reader.hasNext()) {
                /* advance to the first starting element (root node) of the options */
                final int status = reader.next();
                if (status == XMLStreamConstants.START_ELEMENT) break;
            }

            if (!Namespaces.XSLT_XQUERY_SERIALIZATION_NS.equals(reader.getNamespaceURI())) {
                throw new XPathException(parent, FnModule.SENR0001, "serialization parameter elements should be in the output namespace");
            }

            final int thisLevel = ((NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID)).getTreeLevel();

            while (reader.hasNext()) {
                final int status = reader.next();
                if (status == XMLStreamConstants.START_ELEMENT) {
                    readStartElement(parent, reader, propertiesInXML);
                } else if (status == XMLStreamConstants.END_ELEMENT && readEndElementLevel(reader) == thisLevel) {
                    // finished `optRoot` element ? exit-while
                    break;
                }
            }
            
            // Update properties with all the new ones
            serializationProperties.putAll(propertiesInXML);
            
        } catch (final XMLStreamException | IOException e) {
            throw new XPathException(parent, ErrorCodes.EXXQDY0001, e.getMessage());
        }
    }

    private static void readStartElement(final Expression parent, final XMLStreamReader reader, final Properties properties) throws XPathException, XMLStreamException {

        final javax.xml.namespace.QName key = reader.getName();
        final String local = key.getLocalPart();
        final String prefix = key.getPrefix();
        if (properties.containsKey(local)) {
            throw new XPathException(parent, FnModule.SEPM0019, "serialization parameter specified twice: " + key);
        }
        if (prefix.equals(OUTPUT_NAMESPACE) && !W3CParameterConventionKeys.contains(local)) {
            throw new XPathException(ErrorCodes.SEPM0017, "serialization parameter not recognized: " + key);
        }

        readSerializationProperty(reader, local, properties);
    }

    private static int readEndElementLevel(final XMLStreamReader reader) {
        final NodeId otherId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
        return otherId.getTreeLevel();
    }

    public static void setCharacterMap(final Properties serializationProperties, final Int2ObjectMap<String> characterMap) {
        serializationProperties.put(EXistOutputKeys.USE_CHARACTER_MAPS, characterMap);
    }

    public static Int2ObjectMap<String> getCharacterMap(final Properties serializationProperties) {
        return (Int2ObjectMap<String>) serializationProperties.get(EXistOutputKeys.USE_CHARACTER_MAPS);
    }

    private static void readSerializationProperty(final XMLStreamReader reader, final String key, final Properties serializationProperties) throws XPathException, XMLStreamException {

        final int attributeCount = reader.getAttributeCount();
        if (W3CParameterConvention.USE_CHARACTER_MAPS.parameterName.equals(key)) {
            if (attributeCount > 0) {
                throw new XPathException(ErrorCodes.SEPM0017, MSG_NON_VALUE_ATTRIBUTE + ": " + key);
            }
            final Int2ObjectMap<String> characterMap = readUseCharacterMaps(reader);
            setCharacterMap(serializationProperties, characterMap);
        } else {
            String value = reader.getAttributeValue(XMLConstants.NULL_NS_URI, "value");
            if (value == null) {
                if (attributeCount > 0) {
                    throw new XPathException(ErrorCodes.SEPM0017, MSG_NON_VALUE_ATTRIBUTE + ": " + key);
                }
                // backwards compatibility: use element text as value
                value = reader.getElementText();
            }
            if (attributeCount > 1) {
                throw new XPathException(ErrorCodes.SEPM0017, MSG_NON_VALUE_ATTRIBUTE + ": " + key);
            }

            setProperty(key, value, serializationProperties, reader.getNamespaceContext()::getNamespaceURI);
        }
    }

    private static Int2ObjectMap<String> readUseCharacterMaps(final XMLStreamReader reader) throws XMLStreamException, XPathException {
        if (reader.getAttributeCount() > 0) {
            throw new XPathException(ErrorCodes.SEPM0017, EXistOutputKeys.USE_CHARACTER_MAPS + " element has attributes. It should contain only character-map children");
        }

        final Int2ObjectMap<String> characterMap = new Int2ObjectOpenHashMap(Hash.DEFAULT_INITIAL_SIZE, Hash.FAST_LOAD_FACTOR);
        int depth = 0;
        while (reader.hasNext()) {
            /* advance to the next child element, or the end, of the use-character-maps element */
            final int status = reader.next();
            if (status == XMLStreamConstants.START_ELEMENT) {
                depth += 1;
                readCharacterMap(reader, characterMap);
            } else if (status == XMLStreamConstants.END_ELEMENT) {
                if (depth == 0) return characterMap;
                depth -= 1;
            }
        }
        return characterMap;
    }

    /**
     * Read a single map element (k --> s) of a character map
     * @param reader which we are reading the input element from
     * @param characterMap to add the element to
     *
     * @throws XPathException if the element has a bad prefix, or unrecognized attributes
     */
    private static void readCharacterMap(final XMLStreamReader reader, final Int2ObjectMap<String> characterMap) throws XPathException {

        final javax.xml.namespace.QName qName = reader.getName();
        if (!qName.getPrefix().equals(OUTPUT_NAMESPACE)) {
            throw new XPathException(ErrorCodes.SEPM0017, EXistOutputKeys.USE_CHARACTER_MAPS + " element with unexpected prefix: " + qName);
        }
        if (qName.getLocalPart().equals(CHARACTER_MAP_ELEMENT_KEY)) {
            if (reader.getAttributeCount() > 2) {
                throw new XPathException(ErrorCodes.SEPM0017, EXistOutputKeys.USE_CHARACTER_MAPS + " element has unexpected attributes");
            }
            final String character = readCharacterMapAttribute(reader, CHARACTER_ATTR_KEY);
            if (character.length() != 1) {
                throw new XPathException(ErrorCodes.SEPM0017,
                        EXistOutputKeys.USE_CHARACTER_MAPS + " element character must be a single character string, was: " + character);
            }

            final int mapKey = character.charAt(0);
            if (characterMap.containsKey(mapKey)) {
                throw new XPathException(ErrorCodes.SEPM0018, "Duplicate character map entry for key: " + character);
            }

            final String mapString = readCharacterMapAttribute(reader, MAP_STRING_ATTR_KEY);
            characterMap.put(mapKey, mapString);
        } else {
            throw new XPathException(ErrorCodes.SEPM0017, EXistOutputKeys.USE_CHARACTER_MAPS + " element must be character-map, was: " + qName);
        }
    }

    private static String readCharacterMapAttribute(final XMLStreamReader reader, final String key) throws XPathException {
        final String value = reader.getAttributeValue(XMLConstants.NULL_NS_URI, key);
        if (StringUtils.isEmpty(value)) {
            throw new XPathException(ErrorCodes.SEPM0017, "Bad character-map element missing: " + key + " attribute");
        }
        return value;
    }

    public static void setProperty(final String key, final String value, final Properties properties,
                                   final Function<String, String> prefixToNs) {
        final ParameterConvention<String> parameterConvention = W3C_PARAMETER_CONVENTIONS_BY_NAME.get(key);
        if (parameterConvention == null || parameterConvention.getType() != Type.QNAME) {
            properties.setProperty(key, value);
        } else {
            final StringBuilder qnamesValue = new StringBuilder();
            final String[] qnameStrs = value.split("\\s");
            for (final String qnameStr : qnameStrs) {
                if (!qnamesValue.isEmpty()) {
                    //separate entries with a space
                    qnamesValue.append(' ');
                }

                final String[] prefixAndLocal = qnameStr.split(":");
                if (prefixAndLocal.length == 1) {
                    qnamesValue.append("{}").append(prefixAndLocal[0]);
                } else if (prefixAndLocal.length == 2) {
                    final String prefix = prefixAndLocal[0];
                    final String ns = prefixToNs.apply(prefix);
                    qnamesValue.append('{').append(ns).append('}').append(prefixAndLocal[1]);
                }
            }

            properties.setProperty(key, qnamesValue.toString());
        }
    }

    public static Properties getSerializationOptions(final Expression parent, final AbstractMapType entries) throws XPathException {
        try {
            final Properties properties = new Properties();

            for (final W3CParameterConvention w3cParameterConvention : W3CParameterConvention.values()) {
                final Sequence parameterValue = getParameterValue(parent, entries, w3cParameterConvention,
                        new StringValue(w3cParameterConvention.getParameterName()));
                setPropertyForMap(properties, w3cParameterConvention, parameterValue);
            }

            for (final ExistParameterConvention existParameterConvention : ExistParameterConvention.values()) {
                final Sequence parameterValue = getParameterValue(parent, entries, existParameterConvention,
                        new QNameValue(null, existParameterConvention.getParameterName()));
                setPropertyForMap(properties, existParameterConvention, parameterValue);
            }

            return properties;
        } catch (final UnsupportedOperationException e) {
            throw new XPathException(parent, FnModule.SENR0001, e.getMessage());
        }
    }

    private static Sequence getParameterValue(final Expression parent, final AbstractMapType entries, final ParameterConvention<?> parameterConvention, final AtomicValue parameterConventionEntryKey)
            throws XPathException {
        final Sequence providedParameterValue = entries.get(parameterConventionEntryKey);

        // should we use the default value
        if (providedParameterValue == null || providedParameterValue.isEmpty() || (
                parameterConvention.getType() == Type.STRING && isEmptyStringValue(providedParameterValue) &&
                        // allow empty separator #4704
                        parameterConvention.getParameterName() != EXistOutputKeys.ITEM_SEPARATOR
        )
        ) {
            // use default value

            if (W3CParameterConvention.MEDIA_TYPE == parameterConvention) {
                // the default value of MEDIA_TYPE is dependent on the METHOD
                return getDefaultMediaType(entries.get(new StringValue(W3CParameterConvention.METHOD.getParameterName())));

            } else {
                return parameterConvention.getDefaultValue();
            }

        } else {
            // use provided value

            if (checkTypes(parameterConvention, providedParameterValue)) {
                return providedParameterValue;
            } else {
                throw new XPathException(parent, ErrorCodes.XPTY0004, "The supplied value is of the wrong type for the particular parameter: " + parameterConvention.getParameterName());
            }
        }
    }

    private static Sequence getDefaultMediaType(final Sequence providedMethod) throws XPathException {
        final Sequence methodValue;

        // should we use the default method
        if (providedMethod == null || providedMethod.isEmpty()) {
            //use default
            methodValue = W3CParameterConvention.METHOD.getDefaultValue();

        } else {
            //use provided
            methodValue = providedMethod;
        }

        final String method = methodValue.itemAt(0).getStringValue().toLowerCase();
        return switch (method) {
            case "xml", "microxml" -> new StringValue("application/xml");
            case "xhtml" -> new StringValue("application/xhtml+xml");
            case "json" -> new StringValue("application/json");
            case "jsonp" -> new StringValue("application/javascript");
            case "html" -> new StringValue("text/html");
            case "adaptive", "text" -> new StringValue("text/plain");
            case "binary" -> new StringValue("application/octet-stream");
            default -> throw new UnsupportedOperationException("Unrecognised serialization method: " + method);
        };
    }

    /**
     * Checks that the types of the items in the sequence match the parameter convention.
     *
     * @param parameterConvention The parameter convention to check against
     * @param sequence The sequence to check the types of
     *
     * @return true if the types are suitable, false otherwise
     */
    private static boolean checkTypes(final ParameterConvention<?> parameterConvention, final Sequence sequence) throws XPathException {
        if (parameterConvention.getCardinality().isSuperCardinalityOrEqualOf(sequence.getCardinality())) {
            final SequenceIterator iterator = sequence.iterate();
            while (iterator.hasNext()) {
                final Item item = iterator.nextItem();
                if (parameterConvention.getType() != item.getType()) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    private static void setPropertyForMap(final Properties properties, final ParameterConvention<?> parameterConvention, final Sequence parameterValue) throws XPathException {
        // ignore "absent","admit" i.e. "standalone" empty sequence
        if(parameterValue.isEmpty()) {
            return;
        }

        final String localParameterName = parameterConvention.getLocalParameterName();
        final String value;

        switch (parameterConvention.getType()) {
            case Type.BOOLEAN:
                value = ((BooleanValue) parameterValue.itemAt(0)).getValue() ? "yes" : "no";
                properties.setProperty(localParameterName, value);
                break;
            case Type.STRING:
                value = ((StringValue)parameterValue.itemAt(0)).getStringValue();
                properties.setProperty(localParameterName, value);
                break;
            case Type.DECIMAL:
                value = parameterValue.itemAt(0).getStringValue();
                properties.setProperty(localParameterName, value);
                break;
            case Type.INTEGER:
                value = ((IntegerValue) parameterValue.itemAt(0)).getStringValue();
                properties.setProperty(localParameterName, value);
                break;
            case Type.QNAME:
                if (Cardinality._MANY.isSuperCardinalityOrEqualOf(parameterConvention.getCardinality())) {
                    final SequenceIterator iterator = parameterValue.iterate();
                    while (iterator.hasNext()) {
                        final String existingValue = properties.getProperty(localParameterName);
                        final String nextValue = ((QNameValue) iterator.nextItem()).getQName().toURIQualifiedName();

                        if (existingValue == null || existingValue.isEmpty()) {
                            properties.setProperty(localParameterName, nextValue);
                        } else {
                            properties.setProperty(localParameterName, existingValue + " " + nextValue);
                        }
                    }
                } else {
                    value = ((QNameValue) parameterValue.itemAt(0)).getQName().toURIQualifiedName();
                    properties.setProperty(localParameterName, value);
                }
                break;
            case Type.MAP_ITEM:
                if (parameterConvention.getParameterName().equals(W3CParameterConvention.USE_CHARACTER_MAPS.parameterName)) {
                    final Int2ObjectMap<String> characterMap = createCharacterMap((MapType) parameterValue, parameterConvention);
                    setCharacterMap(properties, characterMap);
                } else {
                    // There should not be any such parameter, other than use-character-maps
                    throw new UnsupportedOperationException(
                            "Not yet implemented support for the map serialization parameter: " + localParameterName);
                }
                break;
            default:
                throw new UnsupportedOperationException(
                        MSG_UNSUPPORTED_TYPE + Type.getTypeName(parameterConvention.getType()) + MSG_FOR_PARAMETER_VALUE + ": " + localParameterName);
        }
    }

    /**
     * Determines if the provided sequence contains a single empty string
     *
     * @param sequence The sequence to test
     *
     * @return if the sequence is a single empty string
     */
    private static boolean isEmptyStringValue(final Sequence sequence) {
        if(sequence != null && sequence.getItemCount() == 1) {
            final Item firstItem = sequence.itemAt(0);
            return Type.STRING == firstItem.getType() && ((StringValue)firstItem).getStringValue().isEmpty();
        }

        return false;
    }

    private static Int2ObjectMap<String> createCharacterMap(final MapType map, final ParameterConvention<?> parameterConvention) throws XPathException {

        final String localParameterName = parameterConvention.getLocalParameterName();
        final Int2ObjectMap<String> characterMap = new Int2ObjectOpenHashMap<>(Hash.DEFAULT_INITIAL_SIZE, Hash.FAST_LOAD_FACTOR);
        for (final IEntry<AtomicValue, Sequence> entry : map) {
            final AtomicValue key = entry.key();
            if (!Type.subTypeOf(key.getType(), Type.STRING)) {
                throw new XPathException(ErrorCodes.XPTY0004,
                        MSG_UNSUPPORTED_TYPE + Type.getTypeName(key.getType()) + MSG_FOR_PARAMETER_VALUE + ": " + localParameterName +
                                ". Elements of the map for parameter value: " + localParameterName +
                                " must have keys of type " + Type.getTypeName(Type.STRING));
            }
            final Sequence sequence = entry.value();
            if (sequence.isEmpty()) {
                throw new XPathException(ErrorCodes.XPTY0004, "Character map entries cannot be empty, " +
                        MSG_FOR_PARAMETER_VALUE + ": " + localParameterName);
            }
            final Item value = sequence.itemAt(0);
            if (!Type.subTypeOf(value.getType(), Type.STRING)) {
                throw new XPathException(ErrorCodes.XPTY0004,
                        MSG_UNSUPPORTED_TYPE + Type.getTypeName(key.getType()) + MSG_FOR_PARAMETER_VALUE + ": " + localParameterName +
                                ". Elements of the map for parameter value: " + localParameterName +
                                " must have values of type " + Type.getTypeName(Type.STRING));
            }
            if (key.getStringValue().length() != 1) {
                throw new XPathException(ErrorCodes.SEPM0017,
                        "Elements of the map for parameter value: " + localParameterName +
                                " must have keys which are strings composed of a single character");
            }
            characterMap.put(key.getStringValue().codePointAt(0), value.getStringValue());
        }
        return characterMap;
    }

}
