/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2016 The eXist Project
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
package org.exist.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evolvedbinary.j8fu.Either;
import org.xml.sax.*;

import java.util.Map;
import java.util.Optional;

/**
 * @author Adam Retter
 */
public class HtmlToXmlParser {
    private final static Logger LOG = LogManager.getLogger(HtmlToXmlParser.class);

    public static final String HTML_TO_XML_PARSER_ELEMENT = "html-to-xml";
    public static final String HTML_TO_XML_PARSER_CLASS_ATTRIBUTE = "class";
    public static final String HTML_TO_XML_PARSER_PROPERTIES_ELEMENT = "properties";
    public static final String HTML_TO_XML_PARSER_FEATURES_ELEMENT = "features";
    public static final String HTML_TO_XML_PARSER_PROPERTY = "parser.html-to-xml-parser";
    public static final String HTML_TO_XML_PARSER_PROPERTIES_PROPERTY = "parser.html-to-xml-parser.properties";
    public static final String HTML_TO_XML_PARSER_FEATURES_PROPERTY = "parser.html-to-xml-parser.features";
    public static final String PARSER_ELEMENT_NAME = "parser";

    /**
     * Returns the Configured HTML to XML parser
     *
     * @param config The configuration which specifies the classname of the parser to use
     *
     * @return If the configuration specifies a valid HTML to XML Parser and it
     * is available on the classpath, then the result of instantiating it will be
     * returned, otherwise {@link Optional#EMPTY}
     */
    public static Optional<Either<Throwable, XMLReader>> getHtmlToXmlParser(final Configuration config) {
        final Optional<String> parserClassName =
                Optional.ofNullable((String)config.getProperty(HTML_TO_XML_PARSER_PROPERTY));

        // instantiate the parser
        final Optional<Either<Throwable, XMLReader>> inst = parserClassName.map(cn -> {
            Either<Throwable, XMLReader> result;
            try {
                final Class clazz = Class.forName(cn);
                if (XMLReader.class.isAssignableFrom(clazz)) {
                    final XMLReader reader = (XMLReader) clazz.newInstance();

                    final Map<String, Boolean> features = (Map<String, Boolean>)config.getProperty(HTML_TO_XML_PARSER_FEATURES_PROPERTY);
                    if(features != null) {
                        for(final Map.Entry<String, Boolean> feature : features.entrySet()) {
                            reader.setFeature(feature.getKey(), feature.getValue());
                        }
                    }

                    final Map<String, Object> properties = (Map<String, Object>)config.getProperty(HTML_TO_XML_PARSER_PROPERTIES_PROPERTY);
                    if(properties != null) {
                        for(final Map.Entry<String, Object> property : properties.entrySet()) {
                            reader.setProperty(property.getKey(), property.getValue());
                        }
                    }

                    result = Either.Right(reader);
                } else {
                    result = Either.Left(new ClassCastException(cn + " does not implement org.xml.sax.XMLReader"));
                }
            } catch(final ClassNotFoundException | InstantiationException | IllegalAccessException | SAXNotRecognizedException | SAXNotSupportedException e) {
                result = Either.Left(e);
            }
            return result;
        });

        return inst;
    }
}
