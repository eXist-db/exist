/*
 * eXist Open Source Native XML Database Copyright (C) 2001-06 Wolfgang M.
 * Meier meier@ifs.tu-darmstadt.de http://exist.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 * $Id$
 */

package org.exist.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.exist.memtree.SAXAdapter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 *
 * Class representation of an XQuery Web Application Descriptor file
 * 
 */

public class Descriptor implements ErrorHandler
{
	private final static Logger LOG = Logger.getLogger(Descriptor.class);	//Logger
	private String file = null;											//descriptor file (descriptor.xml)
	
	//Data
	private String mapList[][] = null;	//Array of Mappings
	
	
	//Constructor (wrapper)
	public Descriptor(String file)
	{
        this(file, null);
    }
    
	//Constructor
    public Descriptor(String file, String dbHome)
	{
        try
		{
        	InputStream is = null;
            
            //firstly, try to read the Descriptor from a file within the classpath
            if(file != null)
            {
            	is = Descriptor.class.getClassLoader().getResourceAsStream(file);
            	if(is != null)
            	{
            		LOG.info("Reading descriptor from classloader");
            	}
            }
            else
            {
            	//Default file name
            	file = "descriptor.xml";
            }
                
                
            //otherise, secondly try to read Descriptor from file. Guess the location if necessary
            if(is == null)
            {
                //try and read the Descriptor file from the specified home folder 
            	File f = new File(file);
                if((!f.isAbsolute()) && dbHome != null)
                {
                    file = dbHome + File.separatorChar + file;
                    f = new File(file);
                }
                
                //if cant read Descriptor from specified home folder
                if(!f.canRead())
                {
                    LOG.info("Unable to read descriptor. Trying to guess location ...");
                    
                    //Read from the Descriptor file from the guessed home folder
                    if(dbHome == null)
                    {
                        // try to determine exist home directory
                        dbHome = System.getProperty("exist.home");
                        
                        if(dbHome == null)
                            dbHome = System.getProperty("user.dir");
                    }
                    if(dbHome != null)
                        file = dbHome + File.separatorChar + file;
                    f = new File(file);
                    if(!f.canRead())
                    {
                        LOG.warn("giving up unable to read descriptor file");
                        return;
                    }
                    
                }
                this.file = file;
                is = new FileInputStream(file);
            }
            
            // initialize xml parser
            // we use eXist's in-memory DOM implementation to work
            // around a bug in Xerces
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);;
            
            InputSource src = new InputSource(is);
            SAXParser parser = factory.newSAXParser();
            XMLReader reader = parser.getXMLReader();
            SAXAdapter adapter = new SAXAdapter();
            reader.setContentHandler(adapter);
            reader.parse(src);
            
            Document doc = adapter.getDocument();
            
            //maps settings
            NodeList maps = doc.getElementsByTagName("maps");
            if (maps.getLength() > 0)
            {
                configureMaps((Element) maps.item(0));
            }
            
        }
        catch (SAXException e)
		{
            LOG.warn("error while reading descriptor file: " + file, e);
            return;
        }
        catch (ParserConfigurationException cfg)
		{
            LOG.warn("error while reading descriptor file: " + file, cfg);
            return;
        }
        catch (IOException io)
		{
            LOG.warn("error while reading descriptor file: " + file, io);
            return;
        }
    }
    
    
    private void configureMaps(Element maps)
	{
    	//Get the map element(s)
    	NodeList nlMap = maps.getElementsByTagName("map");
    	
    	//Setup the hashmap to hold the map elements
    	mapList = new String[nlMap.getLength()][2];
    	
    	Element elem = null; //temporary holds map elements
    	
    	//Iterate through the map elements
        for(int i = 0; i < nlMap.getLength(); i++)
        {
            elem = (Element) nlMap.item(i);					//<map>
            String path = elem.getAttribute("path");		//@path
            //String pattern = elem.getAttribute("pattern");//@pattern
            String view = elem.getAttribute("view");		//@view

            //must be a path or a pattern to map from
            if (path == null /*&& pattern == null*/)
            {
                LOG.warn("error element 'map' requires an attribute 'path' or an attribute 'pattern'");
            	return;
            }
            //must be a view to map to
            if (view == null)
            {
            	LOG.warn("error element 'map' requires an attribute 'view'");
            	return;
            }
            
            //store what to map from
           /* if(path != null)
            {*/
            	//store the path
            	mapList[i][0] = path;
            /*}
            else
            {
            	//store the pattern
            	mapList[i][0] = pattern;
            }*/

            //store what to map to
            mapList[i][1] = view;
        }
    }
    
    //takes a path such as that from RESTServer.doGet()
    //if it finds a matching map path then it returns the map view
    //else it returns the passed in path
    public String mapPath(String path)
    {
    	//Iterate through the mappings
    	for(int i = 0; i < mapList.length; i++)
    	{
    		//does the path or the path/ match the map path
    		if(mapList[i][0].equals(path) || new String(mapList[i][0] + "/").equals(path))
    		{
    			//return the view
    			return(mapList[i][1]);
    		}
    	}
    	
    	//no match return the original path
    	return(path);
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.xml.sax.ErrorHandler#error(org.xml.sax.SAXParseException)
     */
    public void error(SAXParseException exception) throws SAXException {
        System.err.println("error occured while reading descriptor file "
                + "[line: " + exception.getLineNumber() + "]:"
                + exception.getMessage());
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.xml.sax.ErrorHandler#fatalError(org.xml.sax.SAXParseException)
     */
    public void fatalError(SAXParseException exception) throws SAXException
	{
        System.err.println("error occured while reading descriptor file "
                + "[line: " + exception.getLineNumber() + "]:"
                + exception.getMessage());
    }
    
    /*
     * (non-Javadoc)
     *
     * @see org.xml.sax.ErrorHandler#warning(org.xml.sax.SAXParseException)
     */
    public void warning(SAXParseException exception) throws SAXException {
        System.err.println("error occured while reading descriptor file "
                + "[line: " + exception.getLineNumber() + "]:"
                + exception.getMessage());
    }
	
}
