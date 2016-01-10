/****************************************************************************/
/*  File:       SerialParameters.java                                       */
/*  Author:     F. Georges                                                  */
/*  Company:    H2O Consulting                                              */
/*  Date:       2015-01-08                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2015 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package org.expath.tools.serial;

import java.util.HashSet;
import java.util.Set;
import javax.xml.namespace.QName;
import org.expath.tools.ToolsException;
import org.expath.tools.model.Element;

/**
 * A set of serialization parameters.
 * 
 * @author Florent Georges
 * @date   2015-01-08
 */
public class SerialParameters
{
    public static SerialParameters parse(Element elem)
            throws ToolsException
    {
        if ( null == elem ) {
            throw new NullPointerException("Element for serialization parameters is null");
        }
        String ns = elem.getNamespaceUri();
        String name = elem.getLocalName();
        if ( ! SERIAL_NS_URI.equals(ns) || ! SERIAL_PARAMS_NAME.equals(name) ) {
            throw new ToolsException("Element name incorrect: {" + ns + "}" + name);
        }
        SerialParameters params = new SerialParameters();
        for ( Element child : elem.children() ) {
            String child_ns = child.getNamespaceUri();
            String child_name = child.getLocalName();
            String value = child.getAttribute(SERIAL_VALUE_NAME);
            if ( null == value ) {
                throw new ToolsException("No @" + SERIAL_VALUE_NAME + " on the element: {" + ns + "}" + name);
            }
            if ( SERIAL_NS_URI.equals(child_ns) ) {
                switch ( child_name )
                {
                    case BYTE_ORDER_MARK_NAME :
                        params.setByteOrderMark(value);
                        break;
                    case CDATA_SECTION_ELEMENTS_NAME :
                        params.setCdataSectionElements(value, child);
                        break;
                    case DOCTYPE_PUBLIC_NAME :
                        params.setDoctypePublic(value);
                        break;
                    case DOCTYPE_SYSTEM_NAME :
                        params.setDoctypeSystem(value);
                        break;
                    case ENCODING_NAME :
                        params.setEncoding(value);
                        break;
                    case ESCAPE_URI_ATTRIBUTES_NAME :
                        params.setEscapeUriAttributes(value);
                        break;
                    case HTML_VERSION_NAME :
                        params.setHtmlVersion(value);
                        break;
                    case INCLUDE_CONTENT_TYPE_NAME :
                        params.setIncludeContentType(value);
                        break;
                    case INDENT_NAME :
                        params.setIndent(value);
                        break;
                    case ITEM_SEPARATOR_NAME :
                        params.setItemSeparator(value);
                        break;
                    case MEDIA_TYPE_NAME :
                        params.setMediaType(value);
                        break;
                    case METHOD_NAME :
                        // default namespace must not be used for method
                        QName qname = value.indexOf(':') < 0
                                ? new QName(value)
                                : child.parseQName(value);
                        params.setMethod(qname);
                        break;
                    case NORMALIZATION_FORM_NAME :
                        params.setNormalizationForm(value);
                        break;
                    case OMIT_XML_DECLARATION_NAME :
                        params.setOmitXmlDeclaration(value);
                        break;
                    case STANDALONE_NAME :
                        params.setStandalone(value);
                        break;
                    case SUPPRESS_INDENTATION_NAME :
                        params.setSuppressIndentation(value, child);
                        break;
                    case UNDECLARE_PREFIXES_NAME :
                        params.setUndeclarePrefixes(value);
                        break;
                    case USE_CHARACTER_MAPS_NAME :
                        params.setUseCharacterMaps(value, child);
                        break;
                    case VERSION_NAME :
                        params.setVersion(value);
                        break;
                    default :
                        throw new ToolsException("Unknown serialization param: {" + child_ns + "}" + child_name);
                }
            }
            else {
                QName qn = new QName(child_ns, child_name);
                params.setExtension(qn, value);
            }
        }
        return params;
    }

    /**
     * Set an extension handler.
     */
    public void setExtensionHandler(ExtensionHandler handler)
            throws ToolsException {
        myHandler = handler;
    }

    /**
     * Get an extension output property.
     * 
     * If the property is not known by the specific implementation, it must
     * raise a technical exception.
     */
    public String getExtension(QName n)
            throws ToolsException {
        if ( myHandler == null ) {
            return null;
        }
        return myHandler.getExtension(n);
    }

    /**
     * Set an extension output property.
     * 
     * If the property is not known by the specific implementation, it must
     * raise a technical exception.
     */
    public void setExtension(QName n, String v)
            throws ToolsException {
        if ( myHandler == null ) {
            throw new ToolsException("No handler set for extension serialization parameters, name=" + n);
        }
        myHandler.setExtension(n, v);
    }

    /**
     * Get the output property {@code byte-order-mark}.
     */
    public Boolean getByteOrderMark() {
        return myByteOrderMark;
    }

    /**
     * Set the output property {@code byte-order-mark}.
     */
    public void setByteOrderMark(Boolean v) {
        myByteOrderMark = v;
    }

    /**
     * Set the output property {@code byte-order-mark}.
     */
    public void setByteOrderMark(String v) throws ToolsException {
        setByteOrderMark(parseBoolean(v));
    }

    /**
     * Get the output property {@code cdata-section-elements}.
     */
    public Iterable<QName> getCdataSectionElements() {
        return myCdataSectionElements;
    }

    /**
     * Set the output property {@code cdata-section-elements}.
     */
    public void setCdataSectionElements(Iterable<QName> v) {
        myCdataSectionElements = new HashSet<>();
        for ( QName q : v ) {
            myCdataSectionElements.add(q);
        }
    }

    /**
     * Set the output property {@code cdata-section-elements}.
     */
    public void setCdataSectionElements(String v, Element scope) throws ToolsException {
        myCdataSectionElements = parseQNames(v, scope);
    }

    /**
     * Get the output property {@code doctype-public}.
     */
    public String getDoctypePublic() {
        return myDoctypePublic;
    }

    /**
     * Set the output property {@code doctype-public}.
     */
    public void setDoctypePublic(String v) {
        myDoctypePublic = v;
    }

    /**
     * Get the output property {@code doctype-system}.
     */
    public String getDoctypeSystem() {
        return myDoctypeSystem;
    }

    /**
     * Set the output property {@code doctype-system}.
     */
    public void setDoctypeSystem(String v) {
        myDoctypeSystem = v;
    }

    /**
     * Get the output property {@code encoding}.
     */
    public String getEncoding() {
        return myEncoding;
    }

    /**
     * Set the output property {@code encoding}.
     */
    public void setEncoding(String v) {
        myEncoding = v;
    }

    /**
     * Get the output property {@code escape-uri-attributes}.
     */
    public Boolean getEscapeUriAttributes() {
        return myEscapeUriAttributes;
    }

    /**
     * Set the output property {@code escape-uri-attributes}.
     */
    public void setEscapeUriAttributes(Boolean v) {
        myEscapeUriAttributes = v;
    }

    /**
     * Set the output property {@code escape-uri-attributes}.
     */
    public void setEscapeUriAttributes(String v) throws ToolsException {
        setEscapeUriAttributes(parseBoolean(v));
    }

    /**
     * Get the output property {@code html-version}.
     */
    public Double getHtmlVersion() {
        return myHtmlVersion;
    }

    /**
     * Set the output property {@code html-version}.
     */
    public void setHtmlVersion(Double v) {
        myHtmlVersion = v;
    }

    /**
     * Set the output property {@code html-version}.
     */
    public void setHtmlVersion(String v) throws ToolsException {
        setHtmlVersion(parseDecimal(v));
    }

    /**
     * Get the output property {@code include-content-type}.
     */
    public Boolean getIncludeContentType() {
        return myIncludeContentType;
    }

    /**
     * Set the output property {@code include-content-type}.
     */
    public void setIncludeContentType(Boolean v) {
        myIncludeContentType = v;
    }

    /**
     * Set the output property {@code include-content-type}.
     */
    public void setIncludeContentType(String v) throws ToolsException {
        setIncludeContentType(parseBoolean(v));
    }

    /**
     * Get the output property {@code indent}.
     */
    public Boolean getIndent() {
        return myIndent;
    }

    /**
     * Set the output property {@code indent}.
     */
    public void setIndent(Boolean v) {
        myIndent = v;
    }

    /**
     * Set the output property {@code indent}.
     */
    public void setIndent(String v) throws ToolsException {
        setIndent(parseBoolean(v));
    }

    /**
     * Get the output property {@code item-separator}.
     */
    public String getItemSeparator() {
        return myItemSeparator;
    }

    /**
     * Set the output property {@code item-separator}.
     */
    public void setItemSeparator(String v) {
        myItemSeparator = v;
    }

    /**
     * Get the output property {@code media-type}.
     */
    public String getMediaType() {
        return myMediaType;
    }

    /**
     * Set the output property {@code media-type}.
     */
    public void setMediaType(String v) {
        myMediaType = v;
    }

    /**
     * Get the output property {@code method}.
     */
    public QName getMethod() {
        return myMethod;
    }

    /**
     * Set the output property {@code method}.
     */
    public void setMethod(QName v) {
        myMethod = v;
    }

    /**
     * Get the output property {@code normalization-form}.
     */
    public String getNormalizationForm() {
        return myNormalizationForm;
    }

    /**
     * Set the output property {@code normalization-form}.
     */
    public void setNormalizationForm(String v) {
        myNormalizationForm = v;
    }

    /**
     * Get the output property {@code omit-xml-declaration}.
     */
    public Boolean getOmitXmlDeclaration() {
        return myOmitXmlDeclaration;
    }

    /**
     * Set the output property {@code omit-xml-declaration}.
     */
    public void setOmitXmlDeclaration(Boolean v) {
        myOmitXmlDeclaration = v;
    }

    /**
     * Set the output property {@code omit-xml-declaration}.
     */
    public void setOmitXmlDeclaration(String v) throws ToolsException {
        setOmitXmlDeclaration(parseBoolean(v));
    }

    /**
     * Get the output property {@code standalone}.
     */
    public Standalone getStandalone() {
        return myStandalone;
    }

    /**
     * Set the output property {@code standalone}.
     */
    public void setStandalone(Standalone v) {
        myStandalone = v;
    }

    /**
     * Set the output property {@code standalone}.
     */
    public void setStandalone(String v) throws ToolsException {
        setStandalone(parseStandalone(v));
    }

    /**
     * Get the output property {@code suppress-indentation}.
     */
    public Iterable<QName> getSuppressIndentation() {
        return mySuppressIndentation;
    }

    /**
     * Set the output property {@code suppress-indentation}.
     */
    public void setSuppressIndentation(Iterable<QName> v) {
        mySuppressIndentation = new HashSet<>();
        for ( QName q : v ) {
            mySuppressIndentation.add(q);
        }
    }

    /**
     * Set the output property {@code suppress-indentation}.
     */
    public void setSuppressIndentation(String v, Element scope) throws ToolsException {
        mySuppressIndentation = parseQNames(v, scope);
    }

    /**
     * Get the output property {@code undeclare-prefixes}.
     */
    public Boolean getUndeclarePrefixes() {
        return myUndeclarePrefixes;
    }

    /**
     * Set the output property {@code undeclare-prefixes}.
     */
    public void setUndeclarePrefixes(Boolean v) {
        myUndeclarePrefixes = v;
    }

    /**
     * Set the output property {@code undeclare-prefixes}.
     */
    public void setUndeclarePrefixes(String v) throws ToolsException {
        setUndeclarePrefixes(parseBoolean(v));
    }

    /**
     * Get the output property {@code use-character-maps}.
     */
    public Iterable<UseChar> getUseCharacterMaps() {
        return myUseCharacterMaps;
    }

    /**
     * Set the output property {@code use-character-maps}.
     */
    public void setUseCharacterMaps(Iterable<UseChar> v) {
        myUseCharacterMaps = new HashSet<>();
        for ( UseChar q : v ) {
            myUseCharacterMaps.add(q);
        }
    }

    /**
     * Set the output property {@code use-character-maps}.
     */
    public void setUseCharacterMaps(String v, Element scope) throws ToolsException {
        myUseCharacterMaps = parseCharMap(v, scope);
    }

    /**
     * Get the output property {@code version}.
     */
    public String getVersion() {
        return myVersion;
    }

    /**
     * Set the output property {@code version}.
     */
    public void setVersion(String v) {
        myVersion = v;
    }

    public enum Standalone {
        YES, NO, OMIT
    }

    public class UseChar {
        public UseChar(String c, String s) {
            character = c;
            stringMap = s;
        }
        public String character;
        public String stringMap;
    }

    private Boolean parseBoolean(String value)
            throws ToolsException
    {
        if ( "yes".equals(value) ) {
            return Boolean.TRUE;
        }
        else if ( "no".equals(value) ) {
            return Boolean.FALSE;
        }
        else {
            throw new ToolsException("Invalid yes/no value: " + value);
        }
    }

    private Standalone parseStandalone(String value)
            throws ToolsException
    {
        if ( "yes".equals(value) ) {
            return Standalone.YES;
        }
        else if ( "no".equals(value) ) {
            return Standalone.NO;
        }
        else if ( "omit".equals(value) ) {
            return Standalone.OMIT;
        }
        else {
            throw new ToolsException("Invalid standalone (yes/no/omit) value: " + value);
        }
    }

    private Double parseDecimal(String value)
            throws ToolsException
    {
        try {
            return Double.valueOf(value);
        }
        catch ( NumberFormatException ex ) {
            throw new ToolsException("Error parsing a decimal serialization parameter: " + value, ex);
        }
    }

    private Set<QName> parseQNames(String value, Element scope)
            throws ToolsException
    {
        Set<QName> qnames = new HashSet<>();
        for ( String v : value.split("\\s+") ) {
            qnames.add(scope.parseQName(v));
        }
        return qnames;
    }

    private Set<UseChar> parseCharMap(String value, Element scope)
            throws ToolsException
    {
        Set<UseChar> result = new HashSet<>();
        for ( Element elem : scope.children() ) {
            String ns = elem.getNamespaceUri();
            String name = elem.getLocalName();
            if ( ! SERIAL_NS_URI.equals(ns) || ! CHAR_MAP_NAME.equals(name) ) {
                throw new ToolsException("Element name incorrect: {" + ns + "}" + name);
            }
            String ch  = elem.getAttribute(CHAR_ATTR_NAME);
            String str = elem.getAttribute(MAP_STRING_ATTR_NAME);
            UseChar us = new UseChar(ch, str);
            result.add(us);
        }
        return result;
    }

    private Boolean myByteOrderMark;
    private Set<QName> myCdataSectionElements;
    private String myDoctypePublic;
    private String myDoctypeSystem;
    private String myEncoding;
    private Boolean myEscapeUriAttributes;
    private Double myHtmlVersion;
    private Boolean myIncludeContentType;
    private Boolean myIndent;
    private String myItemSeparator;
    private String myMediaType;
    private QName myMethod;
    private String myNormalizationForm;
    private Boolean myOmitXmlDeclaration;
    private Standalone myStandalone;
    private Set<QName> mySuppressIndentation;
    private Boolean myUndeclarePrefixes;
    private Set<UseChar> myUseCharacterMaps;
    private String myVersion;

    private ExtensionHandler myHandler;

    private static final String SERIAL_NS_URI = "http://www.w3.org/2010/xslt-xquery-serialization";
    private static final String SERIAL_PARAMS_NAME = "serialization-parameters";
    private static final String CHAR_MAP_NAME = "character-map";
    private static final String CHAR_ATTR_NAME = "character";
    private static final String MAP_STRING_ATTR_NAME = "map-string";
    private static final String SERIAL_VALUE_NAME = "value";
    private static final String BYTE_ORDER_MARK_NAME = "byte-order-mark";
    private static final String CDATA_SECTION_ELEMENTS_NAME = "cdata-section-elements";
    private static final String DOCTYPE_PUBLIC_NAME = "doctype-public";
    private static final String DOCTYPE_SYSTEM_NAME = "doctype-system";
    private static final String ENCODING_NAME = "encoding";
    private static final String ESCAPE_URI_ATTRIBUTES_NAME = "escape-uri-attributes";
    private static final String HTML_VERSION_NAME = "html-version";
    private static final String INCLUDE_CONTENT_TYPE_NAME = "include-content-type";
    private static final String INDENT_NAME = "indent";
    private static final String ITEM_SEPARATOR_NAME = "item-separator";
    private static final String MEDIA_TYPE_NAME = "media-type";
    private static final String METHOD_NAME = "method";
    private static final String NORMALIZATION_FORM_NAME = "normalization-form";
    private static final String OMIT_XML_DECLARATION_NAME = "omit-xml-declaration";
    private static final String STANDALONE_NAME = "standalone";
    private static final String SUPPRESS_INDENTATION_NAME = "suppress-indentation";
    private static final String UNDECLARE_PREFIXES_NAME = "undeclare-prefixes";
    private static final String USE_CHARACTER_MAPS_NAME = "use-character-maps";
    private static final String VERSION_NAME = "version";
}


/* ------------------------------------------------------------------------ */
/*  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS COMMENT.               */
/*                                                                          */
/*  The contents of this file are subject to the Mozilla Public License     */
/*  Version 1.0 (the "License"); you may not use this file except in        */
/*  compliance with the License. You may obtain a copy of the License at    */
/*  http://www.mozilla.org/MPL/.                                            */
/*                                                                          */
/*  Software distributed under the License is distributed on an "AS IS"     */
/*  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.  See    */
/*  the License for the specific language governing rights and limitations  */
/*  under the License.                                                      */
/*                                                                          */
/*  The Original Code is: all this file.                                    */
/*                                                                          */
/*  The Initial Developer of the Original Code is Florent Georges.          */
/*                                                                          */
/*  Contributor(s): none.                                                   */
/* ------------------------------------------------------------------------ */
