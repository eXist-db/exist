/* eXist Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.util.serializer;

import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import org.exist.memtree.NodeImpl;
import org.exist.util.serializer.json.JSONWriter;
import org.w3c.dom.Attr;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Comment;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.xml.sax.helpers.NamespaceSupport;

public class DOMSerializer {

	private final static Properties defaultProperties = new Properties();

    static {
        defaultProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
        defaultProperties.setProperty(OutputKeys.INDENT, "false");
    }
    
	private final static int XML_WRITER = 0;
    private final static int XHTML_WRITER = 1;
    private final static int TEXT_WRITER = 2;
    private final static int JSON_WRITER = 3;
    
    private XMLWriter writers[] = {
        new IndentingXMLWriter(),
        new XHTMLWriter(), 
        new TEXTWriter(),
        new JSONWriter()
    };
    
    protected XMLWriter receiver;
    protected NamespaceSupport nsSupport = new NamespaceSupport();
    protected Map<String, String> namespaceDecls = new HashMap<String, String>();
    protected Properties outputProperties;

    public DOMSerializer() {
        super();
        this.receiver = writers[XML_WRITER];
    }

    public DOMSerializer(Writer writer, Properties outputProperties) {
        super();
        setOutput(writer, outputProperties);
    }

    public void setOutput(Writer writer, Properties properties) {
        if (properties == null)
            {outputProperties = defaultProperties;}
        else
            {outputProperties = properties;}
        
        final String method = outputProperties.getProperty("method", "xml");

        if ("xhtml".equalsIgnoreCase(method))
            {receiver = writers[XHTML_WRITER];}
        else if("text".equalsIgnoreCase(method))
            {receiver = writers[TEXT_WRITER];}
        else if ("json".equalsIgnoreCase(method))
        	{receiver = writers[JSON_WRITER];}
        else
            {receiver = writers[XML_WRITER];}
        
        receiver.setWriter(writer);
        receiver.setOutputProperties(outputProperties);
    }

    public void setWriter(Writer writer) {
        receiver.setWriter(writer);
    }

    public void reset() {
        nsSupport.reset();
        namespaceDecls.clear();
        for (int i = 0; i < writers.length; i++)
            writers[i].reset();
    }

    public void serialize(Node node) throws TransformerException {
    	receiver.startDocument();
        final Node top = node;
        while (node != null) {
            startNode(node);
            Node nextNode = node.getNodeType() == NodeImpl.REFERENCE_NODE ? null : node.getFirstChild();
            while (nextNode == null) {
                endNode(node);
                if (top != null && top.equals(node))
                    {break;}
                nextNode = node.getNextSibling();
                if (nextNode == null) {
                    node = node.getParentNode();
                    if (node == null || (top != null && top.equals(node))) {
                        endNode(node);
                        //nextNode = null;
                        break;
                    }
                }
            }
            node = nextNode;
        }
        receiver.endDocument();
    }

    protected void startNode(Node node) throws TransformerException {
        switch (node.getNodeType()) {
        case Node.DOCUMENT_NODE :
        case Node.DOCUMENT_FRAGMENT_NODE :
            break;
        case Node.ELEMENT_NODE :
            namespaceDecls.clear();
            nsSupport.pushContext();
            receiver.startElement(node.getNamespaceURI(), node.getLocalName(), node.getNodeName());
            String uri = node.getNamespaceURI();
            String prefix = node.getPrefix();
            if (uri == null)
                {uri = "";}
            if (prefix == null)
                {prefix = "";}
            if (nsSupport.getURI(prefix) == null) {
                namespaceDecls.put(prefix, uri);
                nsSupport.declarePrefix(prefix, uri);
            }
            // check attributes for required namespace declarations
            final NamedNodeMap attrs = node.getAttributes();
            Attr nextAttr;
            String attrName;
            for (int i = 0; i < attrs.getLength(); i++) {
                nextAttr = (Attr) attrs.item(i);
                attrName = nextAttr.getName();
                if ("xmlns".equals(attrName)) {
                    final String oldURI = nsSupport.getURI("");
                    uri = nextAttr.getValue();
                    if (oldURI == null || (!oldURI.equals(uri))) {
                        namespaceDecls.put("", uri);
                        nsSupport.declarePrefix("", uri);
                    }
                } else if (attrName.startsWith("xmlns:")) {
                    prefix = attrName.substring(6);
                    if (nsSupport.getURI(prefix) == null) {
                        uri = nextAttr.getValue();
                        namespaceDecls.put(prefix, uri);
                        nsSupport.declarePrefix(prefix, uri);
                    }
                } else if (attrName.indexOf(':') > 0) {
                    prefix = nextAttr.getPrefix();
                    uri = nextAttr.getNamespaceURI();
                    if (prefix == null){
                        prefix = attrName.split(":")[0];
                    }
                    if (nsSupport.getURI(prefix) == null) {
                        namespaceDecls.put(prefix, uri);
                        nsSupport.declarePrefix(prefix, uri);
                    }
                }
            }
            // output all namespace declarations
            for (final Map.Entry<String, String> nsEntry : namespaceDecls.entrySet()) {
                receiver.namespace( nsEntry.getKey(), nsEntry.getValue());
            }
            // output attributes
            String name;
            for (int i = 0; i < attrs.getLength(); i++) {
                nextAttr = (Attr) attrs.item(i);
                name = nextAttr.getName();
                if(name.startsWith("xmlns"))
                    {continue;}
                receiver.attribute(nextAttr.getName(), nextAttr.getValue());
            }
            break;
        case Node.TEXT_NODE :
        case Node.CDATA_SECTION_NODE :
            receiver.characters(((CharacterData) node).getData());
            break;
        case Node.ATTRIBUTE_NODE :
            break;
        case Node.PROCESSING_INSTRUCTION_NODE :
            receiver.processingInstruction(
                ((ProcessingInstruction) node).getTarget(),
                ((ProcessingInstruction) node).getData());
            break;
        case Node.COMMENT_NODE :
            receiver.comment(((Comment) node).getData());
            break;
        default :
            //TODO : what kind of defaut here ?!! -pb
            break;
        }
    }

    protected void endNode(Node node) throws TransformerException {
        if (node == null)
            {return;}
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            nsSupport.popContext();
            receiver.endElement(node.getNamespaceURI(), node.getLocalName(), node.getNodeName());
        }
    }
}
