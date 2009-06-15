/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.xmldb.XmldbURI;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

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
    
    private static final String FILE_LOAD_FAILED_ERR = "Failed to load mime-type table from ";
    private static final String LOAD_FAILED_ERR = "Failed to load mime-type table from class loader";
    
    private static final String MIME_TYPES_XML = "mime-types.xml";
    private static final String MIME_TYPES_XML_DEFAULT = "org/exist/util/" + MIME_TYPES_XML;    
    
    private static MimeTable instance = null;
    /** From where the mime table is loeaded for message purpose */
    private String src;
    
    /**
     * Returns the singleton.
     */
    public static MimeTable getInstance() {
        if (instance == null)
            instance = new MimeTable();
        return instance;
    }
    
    /**
     * Returns the singleton, using a custom mime-types.xml file
     */
    public static MimeTable getInstance(File f) {
        if (instance == null)
            instance = new MimeTable(f);
        return instance;
    }
    
    /**
     * Returns the singleton, using a custom mime-types.xml stream,
     * like for instance an internal database resource.
     */
    public static MimeTable getInstance(InputStream stream,String src) {
        if (instance == null)
            instance = new MimeTable(stream,src);
        return instance;
    }
    
    private Map mimeTypes = new TreeMap();
    private Map extensions = new TreeMap();
    private Map preferredExtension = new TreeMap();
    
    public MimeTable() {
        load();
    }
    
    public MimeTable(File f) {
        load(f);
    }
    
    public MimeTable(InputStream stream,String src) {
        load(stream,src);
    }
    
    /**
     * Inform from where a mime-table is loaded
     */
    public String getSrc() {
        return this.src;
    }
    
    //TODO: deprecate?
    public MimeType getContentTypeFor(String fileName) {
        String ext = getExtension(fileName);
        return ext == null ? null : (MimeType) extensions.get(ext);
    }
    
    public MimeType getContentTypeFor(XmldbURI fileName) {
    	return getContentTypeFor(fileName.toString());
    }
    
    public MimeType getContentType(String mimeType) {
        return (MimeType) mimeTypes.get(mimeType);
    }
    
    public Vector getAllExtensions(MimeType mimeType)
    {
    	return getAllExtensions(mimeType.getName());
    }
    
    public Vector getAllExtensions(String mimeType)
    {
    	Vector extns = new Vector();
    	
    	for(Iterator itExtensions = extensions.keySet().iterator(); itExtensions.hasNext();)
    	{
    		String extKey = (String)itExtensions.next();
    		MimeType mt = (MimeType)extensions.get(extKey);
    		if(mt.getName().equals(mimeType))
    		{
    			extns.add(extKey);
    		}
    	}
    	
    	String preferred = (String)preferredExtension.get(mimeType);
    	if(preferred != null && !extns.contains(preferred))
    	{
    		extns.add(0, preferred);
    	}
    	
    	return extns;
    }
    
    public String getPreferredExtension(MimeType mimeType) {
        return getPreferredExtension(mimeType.getName());
     }
    
    public String getPreferredExtension(String mimeType) {
       return (String)preferredExtension.get(mimeType);
    }
    
    public boolean isXMLContent(String fileName) {
        String ext = getExtension(fileName);
        if (ext == null)
            return false;
        MimeType type = (MimeType) extensions.get(ext);
        if (type == null)
            return false;
        return type.getType() == MimeType.XML;
    }
    
    private String getExtension(String fileName) {
        File f = new File(fileName);
        fileName = f.getName();
        int p = fileName.lastIndexOf('.');
        if (p < 0 || p + 1 == fileName.length())
            return null;
        return fileName.substring(p).toLowerCase();
    }
    
    private void load() {
        load(ConfigurationHelper.lookup(MIME_TYPES_XML));
    }
    
    private void load(InputStream stream,String src) {
        boolean loaded = false;
        System.out.println("Loading mime table from stream "+src);
        try {
        	loadMimeTypes(stream);
        	this.src=src;
        } catch (ParserConfigurationException e) {
            System.err.println(LOAD_FAILED_ERR);
        } catch (SAXException e) {
            System.err.println(LOAD_FAILED_ERR);
        } catch (IOException e) {
            System.err.println(LOAD_FAILED_ERR);
        }
    	
        if (!loaded) {
            ClassLoader cl = MimeTable.class.getClassLoader();
            InputStream is = cl.getResourceAsStream(MIME_TYPES_XML_DEFAULT);
            if (is == null) {
                System.err.println(LOAD_FAILED_ERR);
            }
            try {
                loadMimeTypes(is);
                this.src="resource://"+MIME_TYPES_XML_DEFAULT;
            } catch (ParserConfigurationException e) {
                System.err.println(LOAD_FAILED_ERR);
            } catch (SAXException e) {
                System.err.println(LOAD_FAILED_ERR);
            } catch (IOException e) {
                System.err.println(LOAD_FAILED_ERR);
            }
        }
    }
    
    private void load(File f) {
        boolean loaded = false;
        if (f.canRead()) {
            try {
                System.out.println("Loading mime table from file " + f.getAbsolutePath());
                loadMimeTypes(new FileInputStream(f));
                loaded = true;
                this.src=f.toURI().toString();
            } catch (FileNotFoundException e) {
                System.err.println(FILE_LOAD_FAILED_ERR + f.getAbsolutePath());
            } catch (ParserConfigurationException e) {
                System.err.println(FILE_LOAD_FAILED_ERR + f.getAbsolutePath());
            } catch (SAXException e) {
                System.err.println(FILE_LOAD_FAILED_ERR + f.getAbsolutePath());
            } catch (IOException e) {
                System.err.println(FILE_LOAD_FAILED_ERR + f.getAbsolutePath());
            }
        }
        if (!loaded) {
            ClassLoader cl = MimeTable.class.getClassLoader();
            InputStream is = cl.getResourceAsStream(MIME_TYPES_XML_DEFAULT);
            if (is == null) {
                System.err.println(LOAD_FAILED_ERR);
            }
            try {
                loadMimeTypes(is);
                this.src="resource://"+MIME_TYPES_XML_DEFAULT;
            } catch (ParserConfigurationException e) {
                System.err.println(LOAD_FAILED_ERR);
            } catch (SAXException e) {
                System.err.println(LOAD_FAILED_ERR);
            } catch (IOException e) {
                System.err.println(LOAD_FAILED_ERR);
            }
        }
    }

    /**
     * @param stream
     * @throws SAXException 
     * @throws ParserConfigurationException 
     * @throws IOException 
     */
    private void loadMimeTypes(InputStream stream) throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        InputSource src = new InputSource(stream);
        SAXParser parser = factory.newSAXParser();
        XMLReader reader = parser.getXMLReader();
        reader.setContentHandler(new MimeTableHandler());
        reader.parse(new InputSource(stream));
    }

    private class MimeTableHandler extends DefaultHandler {

        private static final String EXTENSIONS = "extensions";
        private static final String DESCRIPTION = "description";
        private static final String MIME_TYPE = "mime-type";
        
        private MimeType mime = null;
        private FastStringBuffer charBuf = new FastStringBuffer(64);
        
        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
         */
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) throws SAXException {
            if (MIME_TYPE.equals(qName)) {
                String name = attributes.getValue("name");
                if (name == null || name.length() == 0) {
                    System.err.println("No name specified for mime-type");
                    return;
                }
                int type = MimeType.BINARY;
                String typeAttr = attributes.getValue("type");
                if (typeAttr != null && "xml".equals(typeAttr))
                    type = MimeType.XML;
                mime = new MimeType(name, type);
                mimeTypes.put(name, mime);
            }
            charBuf.setLength(0);
        }
        
        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
         */
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if (MIME_TYPE.equals(qName)) {
                mime = null;
            } else if (DESCRIPTION.equals(qName)) {
                if (mime != null) {
                    String description = charBuf.getNormalizedString(FastStringBuffer.SUPPRESS_BOTH);
                    mime.setDescription(description);
                }
            } else if (EXTENSIONS.equals(qName)) {
                if (mime != null) {
                    String extList = charBuf.getNormalizedString(FastStringBuffer.SUPPRESS_BOTH);
                    StringTokenizer tok = new StringTokenizer(extList, ", ");
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
        
        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
         */
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            charBuf.append(ch, start, length);
        }
    }
    
    public static void main(String[] args) {
        MimeTable table = MimeTable.getInstance();
        MimeType type = table.getContentTypeFor("samples/xquery/fibo.svg");
        if (type == null) {
            System.out.println("Not found!");
        } else {
            System.out.println(type.getName());
            System.out.println(type.getDescription());
        }
    }
}
