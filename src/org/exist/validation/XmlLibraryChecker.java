/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-08 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.validation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.xml.parsers.SAXParserFactory;
import org.apache.log4j.Logger;
import org.xml.sax.XMLReader;

/**
 *  Class for checking dependencies with XML libraries.
 *
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class XmlLibraryChecker {

    /**
     * Possible XML Parsers, at least one must be valid
     */
    private final static ClassVersion[] validParsers = {
        new ClassVersion("Xerces", "Xerces-J 2.9.1", 
                "org.apache.xerces.impl.Version.getVersion()")
    };
    
    /**
     * Possible XML Transformers, at least one must be valid
     */
    private final static ClassVersion[] validTransformers = {
        new ClassVersion("Saxon", "8.9.0", 
                "net.sf.saxon.Version.getProductVersion()"),
        new ClassVersion("Xalan", "Xalan Java 2.7.1", 
                "org.apache.xalan.Version.getVersion()"),
    };
    
    /**
     * Possible XML resolvers, at least one must be valid
     */
    private final static ClassVersion[] validResolvers = {
        new ClassVersion("Resolver", "XmlResolver 1.2", 
                "org.apache.xml.resolver.Version.getVersion()"),
    };

    /**
     *  Remove "@" from string.
     */
    private static String getClassName(String classid) {
        String className;

        int lastChar = classid.lastIndexOf("@");
        if (lastChar == -1) {
            className = classid;
        } else {
            className = classid.substring(0, lastChar);
        }
        return className;
    }

    /**
     *  Determine the class that is actually used during XML parsing. Possible
     * values:
     * - com.bluecast.xml.Piccolo
     * - com.sun.org.apache.xerces.internal.jaxp.SAXParserImpl$JAXPSAXParser
     * - org.apache.xerces.jaxp.SAXParserImpl$JAXPSAXParser
     * 
     * @return Full classname of parser.
     */
    private static String determineActualParserClass() {

        String parserClass = "unknown";
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            XMLReader xmlReader = factory.newSAXParser().getXMLReader();
            String classId = xmlReader.toString();
            parserClass = getClassName(classId);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return parserClass;
    }

    /**
     * Convert a parser classname into human readable name.
     */
    private static String determineActualUsedParser(String parserClass) {

        if (parserClass.startsWith("com.bluecast.xml")) {
            return "Piccolo XML Parser for Java";
        } else if (parserClass.startsWith("org.apache.xerces")) {
            return "Apache Xerces2 Java Parser";
        } else if (parserClass.startsWith("com.sun.org.apache.xerces")) {
            return "Sun Java embedded Xerces2 Java Parser";
        } else {
            return "Unknown parser";
        }
    }

    /**
     *  Perform checks on parsers, transformers and resolvers.
     */
    public static void check() {
        StringBuffer message = new StringBuffer();

        if (hasValidClassVersion("Parser", validParsers, message)) {
            System.out.println(message.toString());
        } else {
            System.err.println(message.toString());
        }

        message = new StringBuffer();
        if (hasValidClassVersion("Transformers", validTransformers, message)) {
            System.out.println(message.toString());
        } else {
            System.err.println(message.toString());
        }

        message = new StringBuffer();
        if (hasValidClassVersion("Resolver", validResolvers, message)) {
            System.out.println(message.toString());
        } else {
            System.err.println(message.toString());
        }

        String actualParserClassName = determineActualParserClass();
        String msg = "Used parser: " 
                +  determineActualUsedParser(actualParserClassName) 
                + " (" + actualParserClassName + ").";
        System.out.println(msg);
        System.out.println();
    }

    public static boolean hasValidClassVersion(String type, 
                        ClassVersion[] validClasses, StringBuffer message) {

        String sep = System.getProperty("line.separator");

        message.append("Looking for a valid " + type + "..." + sep);

        for (int i = 0; i < validClasses.length; i++) {
            String actualVersion = validClasses[i].getActualVersion();

            message.append("Checking for " + validClasses[i].getSimpleName());

            if (actualVersion != null) {
                message.append(", found version " + actualVersion);

                if (actualVersion.compareToIgnoreCase(
                                validClasses[i].getRequiredVersion()) >= 0) {
                    message.append(sep + "OK!" + sep);
                    return true;
                } else {
                    message.append(" needed version " + 
                                validClasses[i].getRequiredVersion() + sep);
                }
            } else {
                message.append(", not found!" + sep);
            }
        }

        message.append("Warning: Failed find a valid " + type + "!" + sep);
        message.append(sep + "Please add an appropriate " + type 
                + " to the " + "class-path, e.g. in the 'endorsed' folder of " 
                + "the servlet container or in the 'endorsed' folder " 
                + "of the JRE." + sep);

        return false;
    }

    /**
     * Checks to see if a valid XML Parser exists
     * 
     * @return boolean true indicates a valid Parser was found, false otherwise
     */
    public static boolean hasValidParser() {
        return hasValidParser(new StringBuffer());
    }

    /**
     * Checks to see if a valid XML Parser exists
     * 
     * @param message	Messages about the status of available Parser's will 
     *                  be appended to this buffer
     * 
     * @return boolean true indicates a valid Parser was found, false otherwise
     */
    public static boolean hasValidParser(StringBuffer message) {
        return hasValidClassVersion("Parser", validParsers, message);
    }

    /**
     * Checks to see if a valid XML Transformer exists
     * 
     * @return boolean true indicates a valid Transformer was found, 
     *         false otherwise
     */
    public static boolean hasValidTransformer() {
        return hasValidTransformer(new StringBuffer());
    }

    /**
     * Checks to see if a valid XML Transformer exists
     * 
     * @param message	Messages about the status of available Transformer's 
     *                  will be appended to this buffer
     * 
     * @return boolean true indicates a valid Transformer was found, 
     *         false otherwise
     */
    public static boolean hasValidTransformer(StringBuffer message) {
        return hasValidClassVersion("Transformer", validTransformers, message);
    }

    /**
     * Simple class to describe a class, its required version and how to 
     * obtain the actual version 
     */
    public static class ClassVersion {

        private String simpleName;
        private String requiredVersion;
        private String versionFunction;

        /**
         * Default Constructor
         * 
         * @param simpleName		The simple name for the class (just a 
         *                          descriptor really)
         * @param requiredVersion	The required version of the class
         * @param versionFunction	The function to be invoked to obtain the 
         *                          actual version of the class, must be fully 
         *                          qualified (i.e. includes the package name)
         */
        ClassVersion(String simpleName, String requiredVersion, String versionFunction) {
            this.simpleName = simpleName;
            this.requiredVersion = requiredVersion;
            this.versionFunction = versionFunction;
        }

        /**
         *  @return the simple name of the class
         */
        public String getSimpleName() {
            return simpleName;
        }

        /**
         *  @return the required version of the class
         */
        public String getRequiredVersion() {
            return requiredVersion;
        }

        /**
         * Invokes the specified versionFunction using reflection to get the 
         * actual version of the class
         * 
         *  @return the actual version of the class
         */
        public String getActualVersion() {
            String actualVersion = null;

            //get the class name from the specifiec version function string
            String versionClassName = versionFunction
                    .substring(0, versionFunction.lastIndexOf('.'));

            //get the function name from the specifiec version function string
            String versionFunctionName = versionFunction.substring(
                    versionFunction.lastIndexOf('.') + 1, versionFunction.lastIndexOf('('));

            try {
                //get the class
                Class versionClass = Class.forName(versionClassName);

                //get the method
                Method getVersionMethod = versionClass
                        .getMethod(versionFunctionName, (Class[]) null);

                //invoke the method on the class
                actualVersion = (String) getVersionMethod
                        .invoke(versionClass, (Object[]) null);
                
            } catch (ClassNotFoundException cfe) {
            } catch (NoSuchMethodException nsme) {
            } catch (InvocationTargetException ite) {
            } catch (IllegalAccessException iae) {
            }

            //return the actual version
            return actualVersion;
        }
    }
}
