/****************************************************************************/
/*  File:       DomElement.java                                             */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2015-01-08                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2015 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package org.expath.tools.model.dom;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.expath.tools.ToolsException;
import org.expath.tools.model.Attribute;
import org.expath.tools.model.Element;
import org.expath.tools.model.Sequence;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Trivial, in-memory implementation, for test purposes.
 *
 * @author Florent Georges
 * @date   2015-01-08
 */
public class DomElement
        implements Element
{
    public static Element parseString(String xml)
            throws ToolsException
    {
        try {
            // the input source
            Reader reader = new StringReader(xml);
            InputSource source = new InputSource(reader);
            // the DOM builder
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            // parse
            Document doc = builder.parse(source);
            // the root element
            org.w3c.dom.Element root = doc.getDocumentElement();
            return new DomElement(root);
        }
        catch ( ParserConfigurationException ex ) {
            throw new ToolsException("Error instantiating the DOM parser", ex);
        }
        catch ( SAXException | IOException ex ) {
            throw new ToolsException("Error parsing the XML string", ex);
        }
    }

    public DomElement(org.w3c.dom.Element elem)
    {
        myElem = elem;
    }

    @Override
    public String getLocalName()
    {
        return myElem.getLocalName();
    }

    @Override
    public String getNamespaceUri()
    {
        String ns = myElem.getNamespaceURI();
        return ns == null ? "" : ns;
    }

    @Override
    public String getDisplayName()
    {
        // never return any prefix
        return getLocalName();
    }

    @Override
    public String getAttribute(String local_name)
    {
        return myElem.getAttribute(local_name);
    }

    @Override
    public Iterable<Attribute> attributes()
    {
        List<Attribute> attrs = new ArrayList<>();
        NamedNodeMap map = myElem.getAttributes();
        for ( int i = 0; i < map.getLength(); ++i ) {
            Attr a = (Attr) map.item(i);
            attrs.add(new DomAttribute(a));
        }
        return attrs;
    }

    @Override
    public boolean hasNoNsChild()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Iterable<Element> children()
    {
        List<Element> children = new ArrayList<>();
        NodeList list = myElem.getChildNodes();
        for ( int i = 0; i < list.getLength(); ++i ) {
            Node n = list.item(i);
            if ( n.getNodeType() == Node.ELEMENT_NODE ) {
                org.w3c.dom.Element e = (org.w3c.dom.Element) n;
                children.add(new DomElement(e));
            }
        }
        return children;
    }

    @Override
    public Iterable<Element> children(String ns)
    {
        List<Element> children = new ArrayList<>();
        NodeList list = myElem.getChildNodes();
        for ( int i = 0; i < list.getLength(); ++i ) {
            Node n = list.item(i);
            if ( n.getNodeType() == Node.ELEMENT_NODE && ns.equals(n.getNamespaceURI()) ) {
                org.w3c.dom.Element e = (org.w3c.dom.Element) n;
                children.add(new DomElement(e));
            }
        }
        return children;
    }

    @Override
    public void noOtherNCNameAttribute(String[] names, String[] forbidden_ns)
            throws ToolsException
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Sequence getContent()
    {
        NodeList children = myElem.getChildNodes();
        return new DomSequence(children);
    }

    @Override
    public QName parseQName(String value)
            throws ToolsException
    {
        int colon = value.indexOf(':');
        // ':' not found
        if ( colon < 0 ) {
            String ns = myElem.lookupNamespaceURI(null);
            // no default namespace
            if ( ns == null ) {
                return new QName(value);
            }
            else {
                return new QName(ns, value);
            }
        }
        // ':' found
        else {
            String prefix = value.substring(0, colon);
            String ns = myElem.lookupNamespaceURI(prefix);
            // no namespace for prefix
            if ( ns == null ) {
                throw new ToolsException("No namespace in scope for prefix of QName: " + value);
            }
            else {
                String local = value.substring(colon + 1);
                return new QName(ns, local);
            }
        }
    }

    private final org.w3c.dom.Element myElem;
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
