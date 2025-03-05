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
package org.exist.collections.triggers;

import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Extracts CSV data from an element into a number of new child elements
 *
 * Mainly designed to be used at STORE event, but should also be usable at UPDATE event
 *
 * Example configuration -
 *
 * &lt;collection xmlns="http://exist-db.org/collection-config/1.0"&gt;
 *   &lt;triggers&gt;
 *       &lt;trigger event="store" class="org.exist.collections.triggers.CSVExtractingTrigger"&gt;
 *
 *           &lt;parameter name="separator" value="|"/&gt;
 *
 *           &lt;parameter name="path"&gt;
 *               &lt;xpath&gt;/content/properties/value[@key eq "product_model"]&lt;/xpath&gt;
 *               &lt;extract index="0" element-name="product_name"/&gt;
 *               &lt;extract index="1" element-name="product_code"/&gt;
 *           &lt;/parameter&gt;
 *
 *       &lt;/trigger&gt;
 *   &lt;/triggers&gt;
 * &lt;/collection&gt;
 *
 * Currently the configuration of this trigger only supports basic attribute predicates or a name eq value syntax.
 *
 *
 * So for example, when storing a Document with content like the following -
 *
 * &lt;content&gt;
 *      &lt;properties&gt;
 *          &lt;value key="product_model"&gt;SomeName|SomeCode12345&lt;/value&gt;
 *      &lt;/properties&gt;
 * &lt;/content&gt;
 *
 * The document will be translated at insertion time into -
 *
 * &lt;content&gt;
 *      &lt;properties&gt;
 *          &lt;value key="product_model"&gt;
 *              &lt;product_name&gt;SomeName&lt;/product_name&gt;
 *              &lt;product_code&gt;SomeCode12345&lt;/product_code&gt;
 *          &lt;/value&gt;
 *      &lt;/properties&gt;
 * &lt;/content&gt;
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class CSVExtractingTrigger extends FilteringTrigger {

    //the separator characted for CSV files
    private String separator;

    //key is the xpath to extract for, and value is the extractions to make from the value at that path
    private Map<String, Extraction> extractions = new HashMap<>();

    //the current node path of the SAX stream
    private NodePath currentNodePath = new NodePath();

    private boolean capture = false; //flag to indicate whether to buffer character data for extraction of csv values
    private StringBuilder charactersBuf = new StringBuilder(); //buffer for character data, which will then be parsed to extract csv values


    @SuppressWarnings("unchecked")
    @Override
    public void configure(final DBBroker broker, final Txn transaction, final Collection parent, final Map<String, List<?>> parameters) throws TriggerException {
        super.configure(broker, transaction, parent, parameters);

        //get the separator
        final List<String> separators = (List<String>)parameters.get("separator");
        if(separators == null || separators.size() != 1) {
            throw new TriggerException("A separator parameter must be provided to the CSVExtractingTrigger configuration");
        } else {
            this.separator = separators.getFirst();
        }

        //get the extractions
        final List<Map<String, List>> paths = (List<Map<String, List>>)parameters.get("path");
        for(final Map<String, List> path : paths){
            final List<String> xpaths = path.get("xpath");
            if(xpaths != null && xpaths.size() == 1) {
                String xpath = xpaths.getFirst();

                //split out the path and preficate (if present) from the xpath
                String pathExpr;
                String attrPredicate = null;
                if(xpath.contains("[")) {
                    pathExpr = xpath.substring(0, xpath.indexOf('['));
                    if(xpath.contains("[@")) {
                        attrPredicate = xpath.substring(xpath.indexOf("[@")+2, xpath.indexOf(']'));
                    }
                } else {
                    pathExpr = xpath;
                }

                Extraction extraction = extractions.get(pathExpr);
                if(extraction == null) {
                    extraction = new Extraction();
                    if(attrPredicate != null) {
                        final String[] attrNameValueMatch = attrPredicate.split(" eq ");
                        extraction.setMatchAttribute(attrNameValueMatch[0], attrNameValueMatch[1]);
                    }
                }

                final List<Properties> extracts = path.get("extract");
                if(extracts != null) {
                    for(final Properties extract : extracts) {
                        final ExtractEntry extractEntry = new ExtractEntry(Integer.parseInt(extract.getProperty("index")), extract.getProperty("element-name"));
                        extraction.getExtractEntries().add(extractEntry);
                    }
                }
                Collections.sort(extraction.getExtractEntries()); //pre sort
                extractions.put(pathExpr, extraction);
            }
        }
    }

    @Override
    public void startElement(final String namespaceURI, final String localName, final String qname, final Attributes attributes) throws SAXException {
        //skips nested elements or already extracted nodes (i.e. during update events)
        //TODO needs through testing during update phase
        if(capture) {
            capture = false;
            charactersBuf.delete(0, charactersBuf.length());
        }

        super.startElement(namespaceURI, localName, qname, attributes);
        currentNodePath.add(namespaceURI, localName);

        final Extraction extraction = extractions.get(currentNodePath.toLocalPath());
        if(extraction != null)
        {
            //do we have to match an attribute predicate from the xpath in the trigger config?
            if(extraction.mustMatchAttribute()){
                //yes - so try and match
                for(int i = 0; i < attributes.getLength(); i++){
                    if(extraction.matchesAttribute(attributes.getLocalName(i), attributes.getValue(i))){
                        //matched the predicate, so staty capturing
                        capture = true;
                        break;
                    }
                }
            } else {
                //no, so start capturing
                capture = true;
            }
        }
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if(capture){
            charactersBuf.append(ch, start, length);
        } else {
            super.characters(ch, start, length);
        }
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qname) throws SAXException {
        if(capture) {
            extractCSVValuesToElements();

            capture = false;
            charactersBuf.delete(0, charactersBuf.length());
        }

        super.endElement(namespaceURI, localName, qname);
        currentNodePath.removeLast();
    }

    private void extractCSVValuesToElements() throws SAXException {

        //split the csv values
        final String[] seperatedValues = charactersBuf.toString().split(getEscapedSeparatorForRegExp());

        //get the extractions for the current path
        final Extraction extraction = extractions.get(currentNodePath.toLocalPath());
        for(final ExtractEntry extractEntry : extraction.getExtractEntries()) {

            //extract the value by index
            final int index = extractEntry.getIndex();
            if(index < seperatedValues.length) {
                final char[] seperatedValue = seperatedValues[index].toCharArray();

                //create a new element for the extracted value
                final String localName = extractEntry.getElementName();
                
                super.startElement(XMLConstants.NULL_NS_URI, localName, localName, new EmptyAttributes());
                
                super.characters(seperatedValue, 0, seperatedValue.length);

                super.endElement(XMLConstants.NULL_NS_URI, localName, localName);
            }
        }
    }

    private String getEscapedSeparatorForRegExp() {
        if(separator.length() == 1) {
            //escape the separator character if it is a java regexp character
            if("|".equals(separator) || ",".equals(separator) || "$".equals(separator) || "^".equals(separator)) {
                return "\\" + separator;
            }
        }

        return separator;
    }

    private static class NodePath {
        private final Deque<QName> pathSegments = new ArrayDeque<>();

        public void add(final String namespaceUri, final String localName) {
            pathSegments.push(new QName(namespaceUri, localName));
        }

        public void removeLast() {
            pathSegments.pop();
        }

        public int length() {
            return pathSegments.size();
        }

        //TODO replace with qname path once we understand how to pass in qnames in the xpath parameter to the trigger
        public String toLocalPath() {
            final StringBuilder localPath = new StringBuilder();
            localPath.append("/");
            int i = 0;
            for (final Iterator<QName> it = pathSegments.descendingIterator(); it.hasNext(); ) {
                localPath.append(it.next());
                if(i + 1 < pathSegments.size()) {
                    localPath.append("/");
                }
                i++;
            }

            return localPath.toString();
        }
    }

    /*** configuration data classes ***/
    private static class Extraction {

        private final List<ExtractEntry> extractEntries = new ArrayList<>();

        private String matchAttrName;
        private String matchAttrValue;

        public List<ExtractEntry> getExtractEntries() {
            return extractEntries;
        }

        public void setMatchAttribute(final String attrName, final String attrValue) {
            this.matchAttrName = attrName.trim();
            this.matchAttrValue = attrValue.replaceAll("\"", "").trim();
        }

        public boolean mustMatchAttribute() {
            return(this.matchAttrName != null && this.matchAttrValue != null);
        }

        public boolean matchesAttribute(final String attrName, final String attrValue) {

            //if there is no matching then return true
            if(!mustMatchAttribute()) {
                return true;
            }
            else {
                return this.matchAttrName.equals(attrName) && this.matchAttrValue.equals(attrValue);
            }
        }
    }

    private static class ExtractEntry implements Comparable<ExtractEntry> {
        private final int index;
        private final String elementName;

        public ExtractEntry(final int index, final String elementName) {
            this.index = index;
            this.elementName = elementName;
        }

        public int getIndex() {
            return index;
        }

        public String getElementName() {
            return elementName;
        }

        @Override
        public int compareTo(final ExtractEntry other) {
            if(other == null) {
                return -1;
            } else {
                return other.getIndex() - this.getIndex();
            }
        }
    }

    private static class EmptyAttributes implements Attributes {

        @Override
        public int getLength() {
            return 0;
        }

        @Override
        public String getURI(final int index) {
            return null;
        }

        @Override
        public String getLocalName(final int index) {
            return null;
        }

        @Override
        public String getQName(final int index) {
            return null;
        }

        @Override
        public String getType(final int index) {
            return null;
        }

        @Override
        public String getValue(final int index) {
            return null;
        }

        @Override
        public int getIndex(final String uri, final String localName) {
            return -1;
        }

        @Override
        public int getIndex(final String qName) {
            return -1;
        }

        @Override
        public String getType(final String uri, final String localName) {
            return null;
        }

        @Override
        public String getType(final String qName) {
            return null;
        }

        @Override
        public String getValue(final String uri, final String localName) {
            return null;
        }

        @Override
        public String getValue(final String qName) {
            return null;
        }

    }

	@Override
	public void beforeCreateDocument(final DBBroker broker, final Txn txn, final XmldbURI uri) {
        //no-op
	}

	@Override
	public void afterCreateDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) {
        //no-op
	}

	@Override
	public void beforeUpdateDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) {
        //no-op
	}

	@Override
	public void afterUpdateDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) {
        //no-op
	}

	@Override
	public void beforeCopyDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI newUri) {
        //no-op
	}

	@Override
	public void afterCopyDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI newUri) {
        //no-op
	}

	@Override
	public void beforeMoveDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI newUri) {
        //no-op
	}

	@Override
	public void afterMoveDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI newUri) {
        //no-op
	}

	@Override
	public void beforeDeleteDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) {
        //no-op
	}

	@Override
	public void afterDeleteDocument(final DBBroker broker, final Txn txn, final XmldbURI uri) {
        //no-op
	}

	@Override
	public void beforeUpdateDocumentMetadata(final DBBroker broker, final Txn txn, final DocumentImpl document) {
        //no-op
	}

	@Override
	public void afterUpdateDocumentMetadata(final DBBroker broker, final Txn txn, final DocumentImpl document) {
        //no-op
	}
}
