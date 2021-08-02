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

    /**
     * See https://www.w3.org/TR/xpath-functions-31/#func-serialize
     */
    public enum ParameterConvention {
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


        final String parameterName;
        final int type;
        final Cardinality cardinality;
        final Sequence defaultValue;

        ParameterConvention(final String parameterName, final int type, final Cardinality cardinality, final Sequence defaultValue) {
            this.parameterName = parameterName;
            this.type = type;
            this.cardinality = cardinality;
            this.defaultValue = defaultValue;
        }
    }

    /**
     * for Exist xquery specific functions
     */
    public enum ExistParameterConvention {
        EXPAND_XINCLUDE("expand-xincludes", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, BooleanValue.TRUE);

        final QName parameterName;
        final int type;
        final Cardinality cardinality;
        final Sequence defaultValue;

        ExistParameterConvention(final String parameterName, final int type, final Cardinality cardinality, final Sequence defaultValue) {
            this.parameterName = new QName(parameterName,Namespaces.EXIST_NS,Namespaces.EXIST_NS_PREFIX);
            this.type = type;
            this.cardinality = cardinality;
            this.defaultValue = defaultValue;
        }
    }

    public final static Map<String, ParameterConvention> PARAMETER_CONVENTIONS_BY_NAME = new HashMap<>();
    static {
        for(final ParameterConvention parameterConvention : ParameterConvention.values()) {
            PARAMETER_CONVENTIONS_BY_NAME.put(parameterConvention.parameterName, parameterConvention);
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
        final ParameterConvention parameterConvention = PARAMETER_CONVENTIONS_BY_NAME.get(key);
        if (parameterConvention == null || parameterConvention.type != Type.QNAME) {
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

            for (final ParameterConvention parameterConvention : ParameterConvention.values()) {
                final Sequence providedParameterValue = entries.get(new StringValue(parameterConvention.parameterName));

                final Sequence parameterValue;

                // should we use the default value
                if (providedParameterValue == null || providedParameterValue.isEmpty() || (parameterConvention.type == Type.STRING && isEmptyStringValue(providedParameterValue))) {
                    // use default value

                    if (ParameterConvention.MEDIA_TYPE == parameterConvention) {
                        // the default value of MEDIA_TYPE is dependent on the METHOD
                        parameterValue = getDefaultMediaType(entries.get(new StringValue(ParameterConvention.METHOD.parameterName)));

                    } else {
                        parameterValue = parameterConvention.defaultValue;
                    }

                } else if (checkTypes(parameterConvention, providedParameterValue)) {
                    // use provided value
                    parameterValue = providedParameterValue;

                } else {
                    throw new XPathException(parent, ErrorCodes.XPTY0004, "The supplied value is of the wrong type for the particular parameter: " + parameterConvention.parameterName);
                }

                setPropertyForMap(properties, parameterConvention, parameterValue);
            }

            for (final ExistParameterConvention existParameterConvention : ExistParameterConvention.values()) {
                final Sequence providedParameterValue = entries.get(new QNameValue(null,existParameterConvention.parameterName));

                final Sequence parameterValue;

                // should we use the default value
                if (providedParameterValue == null || providedParameterValue.isEmpty() || (existParameterConvention.type == Type.STRING && isEmptyStringValue(providedParameterValue))) {
                    // use default value
                    parameterValue = existParameterConvention.defaultValue;
                } else if (checkExistTypes(existParameterConvention, providedParameterValue)) {
                    // use provided value
                    parameterValue = providedParameterValue;
                } else {
                    throw new XPathException(parent, ErrorCodes.XPTY0004, "The supplied value is of the wrong type for the particular parameter: " + existParameterConvention.parameterName);
                }


                setExistPropertyForMap(properties, existParameterConvention, parameterValue);
            }

            return properties;
        } catch (final UnsupportedOperationException e) {
            throw new XPathException(parent, FnModule.SENR0001, e.getMessage());
        }
    }

    private static Sequence getDefaultMediaType(final Sequence providedMethod) throws XPathException {
        final Sequence methodValue;

        // should we use the default method
        if(providedMethod == null || providedMethod.isEmpty()) {
            //use default
            methodValue = ParameterConvention.METHOD.defaultValue;

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

            case "adaptive":
                return new StringValue("text/plain");

            case "json":
                return new StringValue("application/json");

            case "jsonp":
                return new StringValue("application/javascript");

            case "html":
                return new StringValue("text/html");

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
    private static boolean checkTypes(final ParameterConvention parameterConvention, final Sequence sequence) throws XPathException {
        if(parameterConvention.cardinality.isSuperCardinalityOrEqualOf(sequence.getCardinality())) {
            final SequenceIterator iterator = sequence.iterate();
            while(iterator.hasNext()) {
                final Item item = iterator.nextItem();
                if(parameterConvention.type != item.getType()) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    private static void setPropertyForMap(final Properties properties, final ParameterConvention parameterConvention, final Sequence parameterValue) throws XPathException {
        if(Type.BOOLEAN == parameterConvention.type) {
            // ignore "admit" i.e. "standalone" empty sequence
            if(!parameterValue.isEmpty()) {
                if (((BooleanValue) parameterValue.itemAt(0)).getValue()) {
                    properties.setProperty(parameterConvention.parameterName, "yes");
                } else {
                    properties.setProperty(parameterConvention.parameterName, "no");
                }
            }
        } else if(Type.STRING == parameterConvention.type) {
            // ignore "absent" i.e. empty sequence
            if(!parameterValue.isEmpty()) {
                properties.setProperty(parameterConvention.parameterName, ((StringValue) parameterValue.itemAt(0)).getStringValue());
            }
        } else if(Type.DECIMAL == parameterConvention.type) {
            properties.setProperty(parameterConvention.parameterName, ((DecimalValue) parameterValue.itemAt(0)).getStringValue());
        } else if(Type.QNAME == parameterConvention.type) {
            if(!parameterValue.isEmpty()) {
                if (Cardinality._MANY.isSuperCardinalityOrEqualOf(parameterConvention.cardinality)) {
                    final SequenceIterator iterator = parameterValue.iterate();
                    while (iterator.hasNext()) {
                        final String existingValue = (String) properties.get(parameterConvention.parameterName);
                        if (existingValue == null || existingValue.isEmpty()) {
                            properties.setProperty(parameterConvention.parameterName, ((QNameValue) iterator.nextItem()).getQName().toURIQualifiedName());
                        } else {
                            properties.setProperty(parameterConvention.parameterName, existingValue + " " + ((QNameValue) iterator.nextItem()).getQName().toURIQualifiedName());
                        }
                    }
                } else {
                    properties.setProperty(parameterConvention.parameterName, ((QNameValue) parameterValue.itemAt(0)).getQName().toURIQualifiedName());
                }
            }
        } else if(Type.MAP == parameterConvention.type) {
            if(!parameterValue.isEmpty()) {
                //TODO(AR) implement `use-character-maps`
                throw new UnsupportedOperationException("Not yet implemented support for the map serialization parameter: " + parameterConvention.parameterName);
            }
        }
    }

    /**
     * setPropertyForMap & check Types duplication for Exist
     *
     */

    private static boolean checkExistTypes(final ExistParameterConvention existParameterConvention, final Sequence sequence) throws XPathException {
        if(existParameterConvention.cardinality.isSuperCardinalityOrEqualOf(sequence.getCardinality())) {
            final SequenceIterator iterator = sequence.iterate();
            while(iterator.hasNext()) {
                final Item item = iterator.nextItem();
                if(existParameterConvention.type != item.getType()) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    private static void setExistPropertyForMap(final Properties properties, final ExistParameterConvention existParameterConvention, final Sequence parameterValue) throws XPathException {
        if(Type.BOOLEAN == existParameterConvention.type && !parameterValue.isEmpty()) {
            // ignore "admit" i.e. "standalone" empty sequence
            if (((BooleanValue) parameterValue.itemAt(0)).getValue()) {
                properties.setProperty(existParameterConvention.parameterName.getLocalPart(), "yes");
            } else {
                properties.setProperty(existParameterConvention.parameterName.getLocalPart(), "no");
            }
        } else if(Type.STRING == existParameterConvention.type && !parameterValue.isEmpty()) {
            // ignore "absent" i.e. empty sequence
            properties.setProperty(existParameterConvention.parameterName.getLocalPart(), ((StringValue) parameterValue.itemAt(0)).getStringValue());
        } else if(Type.DECIMAL == existParameterConvention.type) {
            properties.setProperty(existParameterConvention.parameterName.getLocalPart(), ((DecimalValue) parameterValue.itemAt(0)).getStringValue());
        } else if(Type.QNAME == existParameterConvention.type && !parameterValue.isEmpty()) {
            if (Cardinality._MANY.isSuperCardinalityOrEqualOf(existParameterConvention.cardinality)) {
                final SequenceIterator iterator = parameterValue.iterate();
                while (iterator.hasNext()) {
                    final String existingValue = (String) properties.get(existParameterConvention.parameterName);
                    if (existingValue == null || existingValue.isEmpty()) {
                        properties.setProperty(existParameterConvention.parameterName.getLocalPart(), ((QNameValue) iterator.nextItem()).getQName().toURIQualifiedName());
                    } else {
                        properties.setProperty(existParameterConvention.parameterName.getLocalPart(), existingValue + " " + ((QNameValue) iterator.nextItem()).getQName().toURIQualifiedName());
                    }
                }
            } else {
                properties.setProperty(existParameterConvention.parameterName.getLocalPart(), ((QNameValue) parameterValue.itemAt(0)).getQName().toURIQualifiedName());
            }
        } else if(Type.MAP == existParameterConvention.type && !parameterValue.isEmpty()) {
            //TODO(AR) implement `use-character-maps`
            throw new UnsupportedOperationException("Not yet implemented support for the map serialization parameter: " + existParameterConvention.parameterName);
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