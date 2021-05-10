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
package org.exist.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;

/**
 * Global table of mime types. This singleton class maintains a list
 * of mime types known to the system. It is used to look up the
 * mime type for a specific file extension and to check if a file
 * is an XML or binary resource.
 * 
 * The mime type table is read from a file "mime-types.xml",
 * which should reside in the directory identified in the exist home
 * directory. If no such file is found, the class tries
 * to load the default map from the org.exist.util package via the 
 * class loader.
 * 
 * @author wolf
 */
public class MimeTable {

    private final static Logger LOG = LogManager.getLogger(MimeTable.class);

    private static final String FILE_LOAD_FAILED_ERR = "Failed to load mime-type table from ";
    private static final String LOAD_FAILED_ERR = "Failed to load mime-type table from class loader";
    
    private static final String MIME_TYPES_XML = "mime-types.xml";
    private static final String MIME_TYPES_XML_DEFAULT = "org/exist/util/" + MIME_TYPES_XML;    
    
    private static MimeTable instance = null;
    /** From where the mime table is loaded for message purpose */
    private String src;
    
    /**
     * Returns the singleton.
     *
     * @return the mimetable
     */
    public static MimeTable getInstance() {
        if(instance == null) {
            instance = new MimeTable();
        }
        return instance;
    }
    
    /**
     * Returns the singleton, using a custom mime-types.xml file
     *
     * @param path the path to the mime-types.xml file.
     *
     * @return the mimetable
     */
    public static MimeTable getInstance(final Path path) {
        if (instance == null) {
            instance = new MimeTable(path);
        }
        return instance;
    }
    
    /**
     * Returns the singleton, using a custom mime-types.xml stream,
     * like for instance an internal database resource.
     *
     * @param stream the input stream
     * @param src the name of the input
     *
     * @return the mimetable
     */
    public static MimeTable getInstance(final InputStream stream, final String src) {
        if (instance == null) {
            instance = new MimeTable(stream, src);
        }
        return instance;
    }

    private MimeType defaultMime = null;
    private Map<String, MimeType> mimeTypes = new TreeMap<>();
    private Map<String, MimeType> extensions = new TreeMap<>();
    private Map<String, String> preferredExtension = new TreeMap<>();
    
    public MimeTable() {
        load();
    }
    
    public MimeTable(final Path path) {
        if (Files.isReadable(path)) {
            try {
                LOG.info("Loading mime table from file: {}", path.toAbsolutePath().toString());
                try(final InputStream is = Files.newInputStream(path)) {
                    loadMimeTypes(is);
                }
                this.src = path.toUri().toString();
            } catch (final ParserConfigurationException | SAXException | IOException e) {
                LOG.error(FILE_LOAD_FAILED_ERR + "{}", path.toAbsolutePath().toString(), e);
            }
        }
    }
    
    public MimeTable(final InputStream stream, final String src) {
        load(stream, src);
    }
    
    /**
     * Inform from where a mime-table is loaded.
     *
     * @return the source.
     */
    public String getSrc() {
        return this.src;
    }
    
    //TODO: deprecate?
    public MimeType getContentTypeFor(String fileName) {
        final String ext = getExtension(fileName);
        final MimeType mt = (ext == null) ? defaultMime : extensions.get(ext);
        return (mt == null) ? defaultMime : mt;
    }
    
    public MimeType getContentTypeFor(XmldbURI fileName) {
    	return getContentTypeFor(fileName.toString());
    }
    
    public MimeType getContentType(String mimeType) {
        return mimeTypes.get(mimeType);
    }
    
    public List<String> getAllExtensions(MimeType mimeType) {
    	return getAllExtensions(mimeType.getName());
    }
    
    public List<String> getAllExtensions(String mimeType) {
    	final List<String> extns = new ArrayList<>();
    	
    	for(final Map.Entry<String, MimeType> extension : extensions.entrySet()) {
            final MimeType mt = extension.getValue();
            if(mt.getName().equals(mimeType)) {
                extns.add(extension.getKey());
            }
    	}
    	
    	final String preferred = preferredExtension.get(mimeType);
    	if(preferred != null && !extns.contains(preferred)) {
            extns.add(0, preferred);
    	}
    	
    	return extns;
    }
    
    public String getPreferredExtension(MimeType mimeType) {
        return getPreferredExtension(mimeType.getName());
     }
    
    public String getPreferredExtension(String mimeType) {
       return preferredExtension.get(mimeType);
    }
    
    public boolean isXMLContent(String fileName) {
        final String ext = getExtension(fileName);
        if(ext == null) {
            return false;
        }
        final MimeType type = extensions.get(ext);
        if(type == null) {
            return false;
        }
        return type.getType() == MimeType.XML;
    }
    
    /**
     * Determine if the passed mime type is text, i.e. may require a charset
     * declaration.
     * 
     * @param mimeType the mimetype
     * @return TRUE if mimetype is for text content else FALSE
     */
    public boolean isTextContent(String mimeType) {
    	final MimeType mime = getContentType(mimeType);
    	return mimeType.startsWith("text/") || mimeType.endsWith("xquery") ||
    		mime.isXMLType();
    }
    
    private String getExtension(String fileName) {
        final Path path = Paths.get(fileName);
        fileName = FileUtils.fileName(path);
        final int p = fileName.lastIndexOf('.');
        if(p < 0 || p + 1 == fileName.length()) {
            return null;
        }
        return fileName.substring(p).toLowerCase();
    }
    
    private void load() {
        final ClassLoader cl = MimeTable.class.getClassLoader();
        final InputStream is = cl.getResourceAsStream(MIME_TYPES_XML_DEFAULT);
        if (is == null) {
            LOG.error(LOAD_FAILED_ERR);
        }

        try {
            loadMimeTypes(is);
            this.src = "resource://" + MIME_TYPES_XML_DEFAULT;
        } catch (final ParserConfigurationException | SAXException | IOException e) {
            LOG.error(LOAD_FAILED_ERR, e);
        }
    }
    
    private void load(final InputStream stream, final String src) {
        boolean loaded = false;
        LOG.info("Loading mime table from stream: {}", src);
        try {
        	loadMimeTypes(stream);
        	this.src=src;
        } catch (final ParserConfigurationException | SAXException | IOException e) {
            LOG.error(LOAD_FAILED_ERR, e);
        }
    	
        if (!loaded) {
            final ClassLoader cl = MimeTable.class.getClassLoader();
            final InputStream is = cl.getResourceAsStream(MIME_TYPES_XML_DEFAULT);
            if (is == null) {
                LOG.error(LOAD_FAILED_ERR);
            }
            try {
                loadMimeTypes(is);
                this.src="resource://"+MIME_TYPES_XML_DEFAULT;
            } catch (final ParserConfigurationException | SAXException | IOException e) {
                LOG.error(LOAD_FAILED_ERR, e);
            }
        }
    }

    /**
     * Load Mime Types
     *
     * @param stream input stream.
     *
     * @throws SAXException if an error occurs whilst reading the XML stream
     * @throws ParserConfigurationException if an error occurs whilst parsing the stream
     * @throws IOException if an error occurs whilst reading the stream
     */
    private void loadMimeTypes(final InputStream stream) throws ParserConfigurationException, SAXException, IOException {
        final SAXParserFactory factory = ExistSAXParserFactory.getSAXParserFactory();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
		final InputSource src = new InputSource(stream);
        final SAXParser parser = factory.newSAXParser();
        final XMLReader reader = parser.getXMLReader();

        reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        reader.setFeature(FEATURE_SECURE_PROCESSING, true);

        reader.setContentHandler(new MimeTableHandler());
        reader.parse(src);
    }

    private class MimeTableHandler extends DefaultHandler {

        private static final String EXTENSIONS = "extensions";
        private static final String DESCRIPTION = "description";
        private static final String MIME_TYPE = "mime-type";
        private static final String MIME_TYPES = "mime-types";
        
        private MimeType mime = null;
        private final StringBuilder charBuf = new StringBuilder(64);

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) throws SAXException {


            if (MIME_TYPES.equals(qName)) {
                // Check for a default mime type settings
                final String defaultMimeAttr = attributes.getValue("default-mime-type");
                final String defaultTypeAttr = attributes.getValue("default-resource-type");

                // Resource type default is XML
                int type = MimeType.XML;
                if (defaultTypeAttr != null) {
                    if ("binary".equals(defaultTypeAttr)) {
                        type = MimeType.BINARY;
                    }
                }

                // If a default-mime-type is specified, create a new default mime type
                if (defaultMimeAttr != null
                        && !defaultMimeAttr.isEmpty()) {
                    defaultMime = new MimeType(defaultMimeAttr, type);

                    // If the default-resource-type is specified, and the default-mime-type is unspecified, use a predefined type
                } else if (defaultTypeAttr != null) {
                    if (type == MimeType.XML) {
                        defaultMime = MimeType.XML_TYPE;
                    } else if (type == MimeType.BINARY) {
                        defaultMime = MimeType.BINARY_TYPE;
                    }
                } else {
                    // the defaultMime is left to null, for backward compatibility with 1.2
                }

                // Put the default mime into the mime map
                if (defaultMime != null) {
                    mimeTypes.put(defaultMime.getName(), defaultMime);
                }
            }

            if (MIME_TYPE.equals(qName)) {
                final String name = attributes.getValue("name");
                if (name == null || name.isEmpty()) {
                    LOG.error("No name specified for mime-type");
                    return;
                }
                int type = MimeType.BINARY;
                final String typeAttr = attributes.getValue("type");
                if (typeAttr != null && "xml".equals(typeAttr))
                    {type = MimeType.XML;}
                mime = new MimeType(name, type);
                mimeTypes.put(name, mime);
            }
            charBuf.setLength(0);
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if (MIME_TYPE.equals(qName)) {
                mime = null;
            } else if (DESCRIPTION.equals(qName)) {
                if (mime != null) {
                    final String description = charBuf.toString().trim();
                    mime.setDescription(description);
                }
            } else if (EXTENSIONS.equals(qName)) {
                if (mime != null) {
                    final String extList = charBuf.toString().trim();
                    final StringTokenizer tok = new StringTokenizer(extList, ", ");
                    String preferred = null;
                    while (tok.hasMoreTokens()) {
                        String ext = tok.nextToken().toLowerCase();
                        if (!extensions.containsKey(ext)) {
                            extensions.put(ext, mime);
                        }
                        if (preferred==null) {
                           preferred = ext;
                        }
                    }
                    preferredExtension.put(mime.getName(),preferred);
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            charBuf.append(ch, start, length);
        }
    }
}
