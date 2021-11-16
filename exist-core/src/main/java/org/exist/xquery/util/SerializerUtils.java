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
import org.exist.xquery.value.*;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

/**
 * Serializer utilities used by several XQuery functions.
 */
public class SerializerUtils {

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
     * See https://www.w3.org/TR/xpath-functions-31/#func-serialize
     */
    public enum W3CParameterConvention implements ParameterConvention<String> {
        ALLOW_DUPLICATE_NAMES("allow-duplicate-names", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.FALSE),
        BYTE_ORDER_MARK("byte-order-mark", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.FALSE),
        CDATA_SECTION_ELEMENTS(OutputKeys.CDATA_SECTION_ELEMENTS, Type.QNAME, Cardinality.ZERO_OR_MORE, Sequence.EMPTY_SEQUENCE),
        DOCTYPE_PUBLIC(OutputKeys.DOCTYPE_PUBLIC, Type.STRING, Cardinality.ZERO_OR_ONE, Sequence.EMPTY_SEQUENCE),   //default: () means "absent"
        DOCTYPE_SYSTEM(OutputKeys.DOCTYPE_SYSTEM, Type.STRING, Cardinality.ZERO_OR_ONE, Sequence.EMPTY_SEQUENCE),   //default: () means "absent"
        ENCODING(OutputKeys.ENCODING, Type.STRING, Cardinality.ZERO_OR_ONE, new StringValue("utf-8")),
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
        USE_CHARACTER_MAPS("use-character-maps", Type.MAP, Cardinality.ZERO_OR_ONE, Sequence.EMPTY_SEQUENCE),
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
        EXPAND_XINCLUDE("expand-xincludes", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.TRUE),
        PROCESS_XSL_PI("process-xsl-pi", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.TRUE),
        JSON_IGNORE_WHITE_SPACE_TEXT_NODES("json-ignore-whitespace-text-nodes", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.TRUE),
        HIGHLIGHT_MATCHES("highlight-matches", Type.STRING, Cardinality.ZERO_OR_ONE, new StringValue("none")),
        JSONP("jsonp", Type.STRING, Cardinality.ZERO_OR_ONE, Sequence.EMPTY_SEQUENCE),
        ADD_EXIST_ID("add-exist-id", Type.STRING, Cardinality.ZERO_OR_ONE, new StringValue("none"));


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

    private static final Map<String, ParameterConvention<String>> W3C_PARAMETER_CONVENTIONS_BY_NAME = new HashMap<>();
    static {
        for (final W3CParameterConvention w3cParameterConvention : W3CParameterConvention.values()) {
            W3C_PARAMETER_CONVENTIONS_BY_NAME.put(w3cParameterConvention.getParameterName(), w3cParameterConvention);
        }
    }

    /**
     * Parse output:serialization-parameters XML fragment into serialization
     * properties as defined by the fn:serialize function.
     *
     * @param parent     the parent expression calling this method
     * @param parameters root node of the XML fragment
     * @param properties parameters are added to the given properties
     * @throws XPathException in case of dynamic error
     */
    public static void getSerializationOptions(final Expression parent, final NodeValue parameters, final Properties properties) throws XPathException {
        try {
            final XMLStreamReader reader = parent.getContext().getXMLStreamReader(parameters);
            while (reader.hasNext() && (reader.next() != XMLStreamReader.START_ELEMENT)) {
                /* advance to the first starting element (root node) of the options */
            }

            if (!Namespaces.XSLT_XQUERY_SERIALIZATION_NS.equals(reader.getNamespaceURI())) {
                throw new XPathException(parent, FnModule.SENR0001, "serialization parameter elements should be in the output namespace");
            }

            final int thisLevel = ((NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID)).getTreeLevel();

            while (reader.hasNext()) {
                final int status = reader.next();
                if (status == XMLStreamReader.START_ELEMENT) {

                    final String key = reader.getLocalName();
                    if (properties.contains(key)) {
                        throw new XPathException(parent, FnModule.SEPM0019, "serialization parameter specified twice: " + key);
                    }

                    String value = reader.getAttributeValue(XMLConstants.NULL_NS_URI, "value");
                    if (value == null) {
                        // backwards compatibility: use element text as value
                        value = reader.getElementText();
                    }

                    setProperty(key, value, properties, reader.getNamespaceContext()::getNamespaceURI);

                } else if(status == XMLStreamReader.END_ELEMENT) {
                    final NodeId otherId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
                    final int otherLevel = otherId.getTreeLevel();
                    if (otherLevel == thisLevel) {
                        // finished `optRoot` element...
                        break;  // exit-while
                    }
                }
            }
        } catch (final XMLStreamException | IOException e) {
            throw new XPathException(parent, ErrorCodes.EXXQDY0001, e.getMessage());
        }
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
                if (qnamesValue.length() > 0) {
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
        if (providedParameterValue == null || providedParameterValue.isEmpty() || (parameterConvention.getType() == Type.STRING && isEmptyStringValue(providedParameterValue))) {
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
        switch(method) {
            case "xml":
            case "microxml":
                return new StringValue("application/xml");

            case "xhtml":
                return new StringValue("application/xhtml+xml");

            case "json":
                return new StringValue("application/json");

            case "jsonp":
                return new StringValue("application/javascript");

            case "html":
                return new StringValue("text/html");

            case "adaptive":
            case "text":
                return new StringValue("text/plain");

            case "binary":
                return new StringValue("application/octet-stream");

            default:
                throw new UnsupportedOperationException("Unrecognised serialization method: " + method);

        }
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
                value = ((StringValue) parameterValue.itemAt(0)).getStringValue();
                properties.setProperty(localParameterName, value);
                break;
            case Type.DECIMAL:
                value = ((DecimalValue) parameterValue.itemAt(0)).getStringValue();
                properties.setProperty(localParameterName, value);
                break;
            case Type.QNAME:
                if (Cardinality._MANY.isSuperCardinalityOrEqualOf(parameterConvention.getCardinality())) {
                    final SequenceIterator iterator = parameterValue.iterate();
                    while (iterator.hasNext()) {
                        final String existingValue = (String) properties.get(localParameterName);
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
            case Type.MAP:
                //TODO(AR) implement `use-character-maps`
                throw new UnsupportedOperationException(
                        "Not yet implemented support for the map serialization parameter: " + localParameterName);
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
}