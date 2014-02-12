package org.exist.xquery.util;

import org.exist.Namespaces;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.fn.FnModule;
import org.exist.xquery.value.NodeValue;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.util.Properties;

/**
 * Serializer utilities used by several XQuery functions.
 */
public class SerializerUtils {

    /**
     * Parse output:serialization-parameters XML fragment into serialization
     * properties as defined by the fn:serialize function.
     *
     * @param parent the parent expression calling this method
     * @param parameters root node of the XML fragment
     * @param properties parameters are added to the given properties
     */
    public static void getSerializationOptions(Expression parent, NodeValue parameters, Properties properties) throws XPathException {
        try {
            final XMLStreamReader reader = parent.getContext().getXMLStreamReader(parameters);
            while (reader.hasNext() && (reader.next() != XMLStreamReader.START_ELEMENT)) {
            }
            if (!reader.getNamespaceURI().equals(Namespaces.XSLT_XQUERY_SERIALIZATION_NS)) {
                throw new XPathException(parent, FnModule.SENR0001, "serialization parameter elements should be in the output namespace");
            }
            while (reader.hasNext()) {
                final int status = reader.next();
                if (status == XMLStreamReader.START_ELEMENT) {
                    final String key = reader.getLocalName();
                    if (properties.contains(key))
                    {throw new XPathException(parent, FnModule.SEPM0019, "serialization parameter specified twice: " + key);}
                    String value = reader.getAttributeValue("", "value");
                    if (value == null) {
                        // backwards compatibility: use element text as value
                        value = reader.getElementText();
                    }
                    properties.put(key, value);
                }
            }
        } catch (final XMLStreamException e) {
            throw new XPathException(parent, ErrorCodes.EXXQDY0001, e.getMessage());
        } catch (final IOException e) {
            throw new XPathException(parent, ErrorCodes.EXXQDY0001, e.getMessage());
        }
    }
}
