package org.exist.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Utility class for extracting parameters from 
 * DOM representation into a Map
 *
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class ParametersExtractor {

    public final static String PARAMETERS_ELEMENT_NAME = "parameters";
    public final static String PARAMETER_ELEMENT_NAME = "parameter";
    private final static String PARAMETER_NAME_ATTRIBUTE = "name";
    private final static String PARAMETER_VALUE_ATTRIBUTE = "value";

    /**
     * @param parameters A "parameters" element, which may contain "parameter" child elements
     */
    public static Map<String, List<? extends Object>> extract(final Element parameters) {

        final Map<String, List<? extends Object>> result;

        if(parameters == null || !parameters.getLocalName().equals(PARAMETERS_ELEMENT_NAME)) {
            result = new HashMap<String, List<? extends Object>>(0);
        } else {

            final String namespace = parameters.getNamespaceURI();
            final NodeList nlParameter = parameters.getElementsByTagNameNS(namespace, PARAMETER_ELEMENT_NAME);

            result = extract(nlParameter);
        }

        return result;
    }

    /**
     * @param nlParameter A NodeList of "parameter" elements
     */
    public static Map<String, List<? extends Object>> extract(final NodeList nlParameter) {

        final Map<String, List<? extends Object>> result;

        if(nlParameter == null || nlParameter.getLength() == 0) {
            result = new HashMap<String, List<? extends Object>>(0);
        } else {
            result = extractParameters(nlParameter);
        }


        return result;
    }

    private static Map<String, List<? extends Object>> extractParameters(final NodeList nlParameter) {

        final Map<String, List<?>> parameters = new HashMap<String, List<?>>(nlParameter.getLength());

        for (int i = 0 ; i < nlParameter.getLength();  i++) {
            final Element param = (Element)nlParameter.item(i);
            //TODO : rely on schema-driven validation -pb
            final String name = param.getAttribute(PARAMETER_NAME_ATTRIBUTE);
            /*if(name == null) {
                throwOrLog("Expected attribute '" + PARAMETER_NAME_ATTRIBUTE + "' for element '" + PARAMETER_ELEMENT_NAME + "' in trigger's configuration.", testOnly);
            }*/

            List values = parameters.get(name);

            final String value = param.getAttribute(PARAMETER_VALUE_ATTRIBUTE);
            if(value != null && value.length() > 0) {
                if(values == null) {
                    values = new ArrayList<String>();
                }
                values.add(value);
            } else {
                //are there child nodes?
                if(param.getChildNodes().getLength() > 0) {

                    if(values == null) {
                        values = new ArrayList<Map<String, List>>();
                    }

                    values.add(getParameterChildParameters(param));
                }
            }

            parameters.put(name, values);
        }

        return parameters;
    }

    private static Map<String, List> getParameterChildParameters(final Element parameter) {

        final Map<String, List> results = new HashMap<String, List>();

        final NodeList childParameters = parameter.getChildNodes();
        for(int i = 0; i < childParameters.getLength(); i++) {
            final Node nChildParameter = childParameters.item(i);
            if(nChildParameter instanceof Element) {
                final Element childParameter = (Element)nChildParameter;
                final String name = childParameter.getLocalName();

                if(childParameter.getAttributes().getLength() > 0){
                    List<Properties> childParameterProperties = (List<Properties>)results.get(name);
                    if(childParameterProperties == null) {
                        childParameterProperties = new ArrayList<Properties>();
                    }

                    final NamedNodeMap attrs = childParameter.getAttributes();
                    final Properties props = new Properties();
                    for(int a = 0; a < attrs.getLength(); a++) {
                        final Node attr = attrs.item(a);
                        props.put(attr.getLocalName(), attr.getNodeValue());
                    }
                    childParameterProperties.add(props);

                    results.put(name, childParameterProperties);
                } else {
                    List<String> strings = (List<String>)results.get(name);
                    if(strings == null) {
                        strings = new ArrayList<String>();
                    }
                    strings.add(childParameter.getNodeValue());
                    results.put(name, strings);
                }
            }
        }

        return results;
    }
}