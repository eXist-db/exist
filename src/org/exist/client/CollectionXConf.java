/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2012 The eXist Project
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
package org.exist.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.exist.collections.CollectionConfiguration;
import org.exist.collections.CollectionConfigurationManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * Class to represent a collection.xconf which holds the configuration data for a collection
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-08-25
 * @version 1.2
 */
public class CollectionXConf
{

    public final static String TYPE_QNAME = "qname";
    public final static String TYPE_PATH = "path";

    public final static String ACTION_INCLUDE = "include";
    public final static String ACTION_EXCLUDE = "exclude";

    private InteractiveClient client = null;	//the client
	private String path = null;				//path of the collection.xconf file
	Collection collection = null;				//the configuration collection
	Resource resConfig = null;					//the collection.xconf resource
	
	private LinkedHashMap<String, String> customNamespaces = null;		//custom namespaces
	private FullTextIndex fulltextIndex = null;		//fulltext index model
	private RangeIndex[] rangeIndexes = null;			//range indexes model
	private Trigger[] triggers = null;					//triggers model
	
	private boolean hasChanged = false;	//indicates if changes have been made to the current collection configuration
	
	
	/**
	 * Constructor
	 * 
	 * @param CollectionName	The path of the collection to retreive the collection.xconf for
	 * @param client	The interactive client
	 */
	CollectionXConf(String CollectionName, InteractiveClient client) throws XMLDBException
	{
		this.client = client;
		
		//get configuration collection for the named collection
		//TODO : use XmldbURIs
		path = CollectionConfigurationManager.CONFIG_COLLECTION + CollectionName;
		collection = client.getCollection(path);
		
		if(collection == null) //if no config collection for this collection exists, just return
			{return;}
		
		//get the resource from the db
        final String[] resources = collection.listResources();
        for (int i = 0; i < resources.length; i++) {
            if (resources[i].endsWith(CollectionConfiguration.COLLECTION_CONFIG_SUFFIX)) {
                resConfig = collection.getResource(resources[i]);
                break;
            }
        }
		
		if(resConfig == null) //if, no config file exists for that collection
			{return;}
		
		//Parse the configuration file into a DOM
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		Document docConfig = null;
		try
		{
			final DocumentBuilder builder = factory.newDocumentBuilder();
			docConfig = builder.parse( new java.io.ByteArrayInputStream(resConfig.getContent().toString().getBytes()) );
		}
		catch(final ParserConfigurationException pce)
		{
			//TODO: do something here, throw xmldbexception?
		} 
		catch(final SAXException se)
		{
			//TODO: do something here, throw xmldbexception?
		}
		catch(final IOException ioe)
		{
			//TODO: do something here, throw xmldbexception?
		}
		
		//Get the root of the collection.xconf
		final Element xconf = docConfig.getDocumentElement();
		
		//Read any custom namespaces from xconf
		customNamespaces = getCustomNamespaces(xconf);
		
		//Read FullText Index from xconf
		fulltextIndex = getFullTextIndex(xconf);
		
		//Read Range Indexes from xconf
		rangeIndexes = getRangeIndexes(xconf);
		
		//read Triggers from xconf
		triggers = getTriggers(xconf);
	}
	
	/**
	 * Indicates whether the fulltext index defaults to indexing all nodes
	 *
	 * @return true indicates all nodes are indexed, false indicates no nodes are indexed by default
	 */
	public boolean getFullTextIndexDefaultAll()
	{
		return fulltextIndex != null ? fulltextIndex.getDefaultAll() : false;
	}
	
	/**
	 * Set whether all nodes should be indexed into the fulltext index
	 * 
	 * @param defaultAll	true indicates all nodes should be indexed, false indicates no nodes should be indexed by default
	 */
	public void setFullTextIndexDefaultAll(boolean defaultAll)
	{
		hasChanged = true;
		if(fulltextIndex == null)
		{
			fulltextIndex = new FullTextIndex(true, false, false, null);
		}
		else
		{
			fulltextIndex.setDefaultAll(defaultAll);
		}
	}
	
	/**
	 * Indicates whether the fulltext index indexes attributes
	 *
	 * @return true indicates attributes are indexed, false indicates attributes are not indexed
	 */
	public boolean getFullTextIndexAttributes()
	{
		return fulltextIndex != null ? fulltextIndex.getAttributes() : false;
	}
	
	/**
	 * Set whether attributes should be indexed into the fulltext index
	 * 
	 * @param attributes	true indicates attributes should be indexed, false indicates attributes should not be indexed
	 */
	public void setFullTextIndexAttributes(boolean attributes)
	{
		hasChanged = true;
		
		if(fulltextIndex == null)
		{
			fulltextIndex = new FullTextIndex(false, true, false, null);
		}
		else
		{
			fulltextIndex.setAttributes(attributes);
		}
	}
	
	/**
	 * Indicates whether the fulltext index indexes alphanumeric values
	 *
	 * @return true indicates alphanumeric values are indexed, false indicates alphanumeric values are not indexed
	 */
	public boolean getFullTextIndexAlphanum()
	{
		return fulltextIndex != null ? fulltextIndex.getAlphanum() : false;
	}
	
	/**
	 * Set whether alphanumeric values should be indexed into the fulltext index
	 * 
	 * @param alphanum	true indicates alphanumeric values should be indexed, false indicates alphanumeric values should not be indexed
	 */
	public void setFullTextIndexAlphanum(boolean alphanum)
	{
		hasChanged = true;
		
		if(fulltextIndex == null)
		{
			fulltextIndex = new FullTextIndex(false, false, true, null);
		}
		else
		{
			fulltextIndex.setAlphanum(alphanum);
		}
	}
	
	/**
	 * Returns a full text index path
	 * 
	 * @param index	The numeric index of the fulltext index path to retreive
	 * 
	 * @return The XPath
	 */
	public String getFullTextIndexPath(int index)
	{
		return fulltextIndex.getPath(index);
	}

    public String getFullTextIndexType(int index) {
        return fulltextIndex.getType(index);
    }

    /**
	 * Returns a full text index path action
	 * 
	 * @param index	The numeric index of the fulltext index path action to retreive
	 * 
	 * @return The Action, either "include" or "exclude"
	 */
	public String getFullTextIndexPathAction(int index)
	{
		return fulltextIndex.getAction(index);
	}
	
	/**
	 * Returns the number of full text index paths defined
	 *  
	 * @return The number of paths
	 */
	public int getFullTextPathCount()
	{
		if(fulltextIndex != null)
		{
			return fulltextIndex.getLength();
		}
		else
		{
			return 0;
		}
	}
	
	/**
	 * Add a path to the full text index
	 *
	 * @param XPath		The XPath to index
	 * @param action	The action to take on the path, either "include" or "exclude"
	 */
	public void addFullTextIndex(String type, String XPath, String action)
	{
		hasChanged = true;
		
		
		if(fulltextIndex == null)
		{
			fulltextIndex = new FullTextIndex(false, false, false, null);
		}
		
		fulltextIndex.addIndex(type, XPath, action);
	}
	
	/**
	 * Update the details of a full text index path
	 *
	 * @param index		The numeric index of the path to update
	 * @param XPath		The new XPath, or null to just set the action
	 * @param action	The new action, either "include" or "exclude", or null to just set the XPath
	 */
	public void updateFullTextIndex(int index, String type, String XPath, String action)
	{
		hasChanged = true;

        if (type != null)
            {fulltextIndex.setType(index, type);}

        if(XPath != null)
			{fulltextIndex.setPath(index, XPath);}
		
		if(action != null)
			{fulltextIndex.setAction(index, action);}
	}

	/**
	 * Delete a path from the full text index
	 * 
	 * @param index	The numeric index of the path to delete
	 */
	public void deleteFullTextIndex(int index)
	{
		hasChanged = true;
		
		fulltextIndex.deleteIndex(index);
	}
	
	/**
	 * Returns an array of the Range Indexes
	 * 
	 * @return Array of Range Indexes
	 */
	public RangeIndex[] getRangeIndexes()
	{
		return rangeIndexes;
	}
	
	/**
	 * Returns n specific Range Index
	 * 
	 * @param index	The numeric index of the Range Index to return
	 * 
	 * @return The Range Index
	 */
	public RangeIndex getRangeIndex(int index)
	{
		return rangeIndexes[index];
	}
	
	/**
	 * Returns the number of Range Indexes defined
	 *  
	 * @return The number of Range indexes
	 */
	public int getRangeIndexCount()
	{
		if(rangeIndexes != null)
		{
			return rangeIndexes.length;
		}
		else
		{
			return 0;
		}
	}
	
	/**
	 * Delete a Range Index
	 * 
	 * @param index	The numeric index of the Range Index to delete
	 */
	public void deleteRangeIndex(int index)
	{
		//can only remove an index which is in the array
		if(index < rangeIndexes.length)
		{
			hasChanged = true;
			
			//if its the last item in the array just null the array 
			if(rangeIndexes.length == 1)
			{
				rangeIndexes = null;
			}
			else
			{
				//else remove the item at index from the array
				RangeIndex newRangeIndexes[] = new RangeIndex[rangeIndexes.length - 1];
				int x = 0;
				for(int i = 0; i < rangeIndexes.length; i++)
				{
					if(i != index)
					{
						newRangeIndexes[x] = rangeIndexes[i];
						x++;
					}
				}	
				rangeIndexes = newRangeIndexes;
			}
		}
	}
	
	/**
	 * Update the details of a Range Index
	 *
	 * @param index		The numeric index of the range index to update
	 * @param XPath		The new XPath, or null to just set the type
	 * @param xsType	The new type of the path, a valid xs:type, or just null to set the path
	 */
	public void updateRangeIndex(int index, String type, String XPath, String xsType)
	{
		hasChanged = true;

        if (type != null)
            {rangeIndexes[index].setType(type);}
        
        if(XPath != null)
			{rangeIndexes[index].setXPath(XPath);}
		
		if(xsType != null)
			{rangeIndexes[index].setxsType(xsType);}
	}
	
	/**
	 * Add a Range Index
	 *
	 * @param XPath		The XPath to index
	 * @param xsType	The type of the path, a valid xs:type
	 */
	public void addRangeIndex(String type, String XPath, String xsType)
	{
		hasChanged = true;
		
		if(rangeIndexes == null)
		{
			rangeIndexes = new RangeIndex[1];
			rangeIndexes[0] = new RangeIndex(type, XPath, xsType);
		}
		else
		{
			RangeIndex newRangeIndexes[] = new RangeIndex[rangeIndexes.length + 1];
			System.arraycopy(rangeIndexes, 0, newRangeIndexes, 0, rangeIndexes.length);
			newRangeIndexes[rangeIndexes.length] = new RangeIndex(type, XPath, xsType);
			rangeIndexes = newRangeIndexes;
		}
	}
	
	/**
	 * Returns an array of Triggers
	 * 
	 * @return Array of Range Indexes
	 */
	public Trigger[] getTriggers()
	{
		return triggers;
	}
	
	/**
	 * Returns n specific Trigger
	 * 
	 * @param index	The numeric index of the Trigger to return
	 * 
	 * @return The Trigger
	 */
	public Trigger getTrigger(int index)
	{
		return triggers[index];
	}
	
	/**
	 * Returns the number of Triggers defined
	 *  
	 * @return The number of Triggers
	 */
	public int getTriggerCount()
	{
		if(triggers != null)
		{
			return triggers.length;
		}
		else
		{
			return 0;
		}
	}
	
	/**
	 * Delete a Trigger
	 * 
	 * @param index	The numeric index of the Trigger to delete
	 */
	public void deleteTrigger(int index)
	{
		//can only remove an index which is in the array
		if(index < triggers.length)
		{
			hasChanged = true;
			
			//if its the last item in the array just null the array 
			if(triggers.length == 1)
			{
				triggers = null;
			}
			else
			{
				//else remove the item at index from the array
				Trigger newTriggers[] = new Trigger[triggers.length - 1];
				int x = 0;
				for(int i = 0; i < triggers.length; i++)
				{
					if(i != index)
					{
						newTriggers[x] = triggers[i];
						x++;
					}
				}	
				triggers = newTriggers;
			}
		}
	}
	
	/**
	 * Update the details of a Trigger
	 *
	 * @param index		The numeric index of the range index to update
	 * @param triggerClass	The name of the new class for the trigger
         * @param parameters The parameters to the trigger
	 * 
	 */
	public void updateTrigger(int index, String triggerClass, Properties parameters)
	{
		//TODO: finish this!!! - need to add code for parameters
		
		hasChanged = true;
		
		if(triggerClass != null) {
			triggers[index].setTriggerClass(triggerClass);
                }
	}
	
	/**
	 * Add a Trigger
	 *
	 * @param triggerClass The class for the Trigger
	 * 
	 */
	public void addTrigger(String triggerClass, Properties parameters)
	{
		//TODO: finish this!!! seee updateTrigger
		
		hasChanged = true;
		
		if(triggers == null)
		{
			triggers = new Trigger[1];
			triggers[0] = new Trigger(triggerClass, parameters);
		}
		else
		{
			Trigger newTriggers[] = new Trigger[triggers.length + 1];
			System.arraycopy(triggers, 0, newTriggers, 0, triggers.length);
			newTriggers[triggers.length] = new Trigger(triggerClass, parameters);
			triggers = newTriggers;
		}
	}
	
	//given the root element of collection.xconf it will return the fulltext index
	private LinkedHashMap<String, String> getCustomNamespaces(Element xconf)
	{
		final NamedNodeMap attrs = xconf.getAttributes();
		
		//there will always be one attribute - the default namespace
		if(attrs.getLength() > 1)
		{
			final LinkedHashMap<String, String> namespaces = new LinkedHashMap<String, String>();
			
			for(int i = 0; i < attrs.getLength(); i++)
			{
				final Node a = attrs.item(i);
				if(a.getNodeName().startsWith("xmlns:"))
				{
					final String namespaceLocalName = a.getNodeName().substring(a.getNodeName().indexOf(":")+1); 
					namespaces.put(namespaceLocalName, a.getNodeValue());
				}
			}
			
			return namespaces;
		}
		
		return null;
	}
	
	//given the root element of collection.xconf it will return the fulltext index
	private FullTextIndex getFullTextIndex(Element xconf)
	{
		final NodeList nlFullTextIndex = xconf.getElementsByTagName("fulltext");
		if(nlFullTextIndex.getLength() > 0)
		{
			boolean defaultAll = true;
			boolean attributes = false;
			boolean alphanum = false;
			FullTextIndexPath[] paths = null;

            final Element elemFullTextIndex = (Element)nlFullTextIndex.item(0);
			defaultAll = "all".equals(elemFullTextIndex.getAttribute("default"));
			attributes = "true".equals(elemFullTextIndex.getAttribute("attributes"));
			alphanum = "true".equals(elemFullTextIndex.getAttribute("alphanum"));
			
			final NodeList nlInclude = elemFullTextIndex.getElementsByTagName("include");
			final NodeList nlExclude = elemFullTextIndex.getElementsByTagName("exclude");
			final NodeList nlQName = elemFullTextIndex.getElementsByTagName("create");

            final int iPaths = nlInclude.getLength() + nlExclude.getLength() + nlQName.getLength();
			int pos = 0;
			if(iPaths > 0 )
			{
				paths = new FullTextIndexPath[iPaths];
			
				if(nlInclude.getLength() > 0)
				{
					for(int i = 0; i < nlInclude.getLength(); i++)
					{
						paths[pos++] = new FullTextIndexPath(TYPE_PATH, ((Element)nlInclude.item(i)).getAttribute("path"), ACTION_INCLUDE);
					}
				}
				
				if(nlExclude.getLength() > 0)
				{	
					for(int i = 0; i < nlExclude.getLength(); i++)
					{
						paths[pos++] = new FullTextIndexPath(TYPE_PATH, ((Element)nlExclude.item(i)).getAttribute("path"), ACTION_EXCLUDE);
					}
				}

                if (nlQName.getLength() > 0) {
                    for (int i = 0; i < nlQName.getLength(); i++) {
                        paths[pos++] = new FullTextIndexPath(TYPE_QNAME, ((Element)nlQName.item(i)).getAttribute("qname"), ACTION_EXCLUDE);
                    }
                }
            }
            return new FullTextIndex(defaultAll, attributes, alphanum, paths);
			
		}
		return null;
		
	}
	
	//given the root element of collection.xconf it will return an array of range indexes
	private RangeIndex[] getRangeIndexes(Element xconf)
	{
		final NodeList nlRangeIndexes = xconf.getElementsByTagName("create");
        if(nlRangeIndexes.getLength() > 0)
		{
            final List<RangeIndex> rl = new ArrayList<RangeIndex>();
            for(int i = 0; i < nlRangeIndexes.getLength(); i++)
			{	
				final Element rangeIndex = (Element)nlRangeIndexes.item(i);
                if (rangeIndex.hasAttribute("type")) {
                    if (rangeIndex.hasAttribute("qname"))
                        {rl.add(new RangeIndex(TYPE_QNAME, rangeIndex.getAttribute("qname"), rangeIndex.getAttribute("type")));}
                    else
                        {rl.add(new RangeIndex(TYPE_PATH, rangeIndex.getAttribute("path"), rangeIndex.getAttribute("type")));}
                }
            }
            RangeIndex[] rangeIndexes = new RangeIndex[rl.size()];
            rangeIndexes = rl.toArray(rangeIndexes);
            return rangeIndexes;
		}
		return null;
	}
	
	//given the root element of collection.xconf it will return an array of triggers
	private Trigger[] getTriggers(Element xconf)
	{
		final NodeList nlTriggers = xconf.getElementsByTagName("trigger");
		if(nlTriggers.getLength() > 0)
		{
			final Trigger[] triggers = new Trigger[nlTriggers.getLength()]; 
			
			for(int i = 0; i < nlTriggers.getLength(); i++)
			{	
				final Element trigger = (Element)nlTriggers.item(i);
				
				final Properties parameters = new Properties();
				final NodeList nlTriggerParameters = trigger.getElementsByTagName("parameter");
				if(nlTriggerParameters.getLength() > 0)
				{
					for(int x = 0; x < nlTriggerParameters.getLength(); x++)
					{
						final Element parameter = (Element)nlTriggerParameters.item(x);
						parameters.setProperty(parameter.getAttribute("name"), parameter.getAttribute("value"));
					}
				}
				
				//create the trigger
				triggers[i] = new Trigger(trigger.getAttribute("class"), parameters);
			}
			
			return triggers;
		}
		return null;
	}
	
	//has the collection.xconf been modified?
	/**
	 * Indicates whether the collection configuration has changed
	 * 
	 * @return true if the configuration has changed, false otherwise
	 */
	public boolean hasChanged()
	{
		return hasChanged;
	}
	
	//produces a string of XML describing the collection.xconf
	private String toXMLString()
	{
		final StringBuilder xconf = new StringBuilder();
		
		xconf.append("<collection xmlns=\"http://exist-db.org/collection-config/1.0\"");
		if(customNamespaces != null)
		{
			for (final Map.Entry<String, String> entry : customNamespaces.entrySet())
			{
				xconf.append(" ");
				final String namespaceLocalName = entry.getKey();
				final String namespaceURL = entry.getValue();
				xconf.append("xmlns:" + namespaceLocalName + "=\"" + namespaceURL + "\"");
			}
		}
		xconf.append(">");
		xconf.append(System.getProperty("line.separator"));
		
		//index
		if(fulltextIndex != null || rangeIndexes != null)
		{
			xconf.append('\t');
			xconf.append("<index>");
			xconf.append(System.getProperty("line.separator"));
		
			//fulltext indexes
			if(fulltextIndex != null)
			{
				xconf.append("\t\t");
				xconf.append(fulltextIndex.toXMLString());
				xconf.append(System.getProperty("line.separator"));
			}
			
			//range indexes
			if(rangeIndexes != null)
			{
				for(int r = 0; r < rangeIndexes.length; r ++)
				{
					xconf.append("\t\t\t");
					xconf.append(rangeIndexes[r].toXMLString());
					xconf.append(System.getProperty("line.separator"));
				}
			}
			
			xconf.append('\t');
			xconf.append("</index>");
			xconf.append(System.getProperty("line.separator"));
		}
		
		//triggers
		if(triggers != null)
		{
			xconf.append('\t');
			xconf.append("<triggers>");
			
			for(int t = 0; t < triggers.length; t ++)
			{
				xconf.append("\t\t\t");
				xconf.append(triggers[t].toXMLString());
				xconf.append(System.getProperty("line.separator"));
			}
			
			xconf.append('\t');
			xconf.append("</triggers>");
			xconf.append(System.getProperty("line.separator"));
		}
		
		xconf.append("</collection>");
		
		return xconf.toString();
	}
	
	/**
	 * Saves the collection configuation back to the collection.xconf
	 * 
	 * @return true if the save succeeds, false otherwise
	 */
	public boolean Save()
	{
		try
		{
			//is there an existing config file?
			if(resConfig == null)
			{
				//no
				
				//is there an existing configuration collection?
				if(collection == null)
				{
					//no
					client.process("mkcol " + path);
					collection = client.getCollection(path);
				}
				
				resConfig = collection.createResource(CollectionConfigurationManager.COLLECTION_CONFIG_FILENAME, "XMLResource");
			}
			
			//set the content of the collection.xconf
			resConfig.setContent(toXMLString());
			
			//store the collection.xconf
			collection.storeResource(resConfig);
		}
		catch(final XMLDBException xmldbe)
		{
			return false;
		}
		
		return true;
	}
	
	//represents a path in the fulltext index in the collection.xconf
	protected class FullTextIndexPath
	{
        private String type = TYPE_QNAME;

        private String path = null;
        private String action = ACTION_INCLUDE;
		
		FullTextIndexPath(String type, String path, String action)
		{
            this.type = type;
            this.path = path;
			this.action = action;
		}
		
		public String getXPath()
		{
			return path;
		}
		
		public String getAction()
		{
			return action;
		}

        public String getType() {
            return type;
        }

        public void setPath(String xpath)
		{
			this.path = xpath;
		}

        public void setType(String type) {
            this.type = type;
        }

        public void setAction(String action)
		{
			this.action = action;
		}	
	}
	
	/**
	 * Represents the Full Text Index in the collection.xconf
	 */
	protected class FullTextIndex
	{	
		boolean defaultAll = true;
		boolean attributes = false;
		boolean alphanum = false;
		FullTextIndexPath[] xpaths = null;
        
        /**
		 * Constructor
		 * 
		 * @param defaultAll	Should the fulltext index default to indexing all nodes
		 * @param attributes	Should attributes be indexed into the fulltext index 
		 * @param alphanum		Should alphanumeric values be indexed into the fulltext index
		 * @param xpaths		Explicit fulltext index paths to include or exclude, null if there are no explicit paths		
		 */
		FullTextIndex(boolean defaultAll, boolean attributes, boolean alphanum, FullTextIndexPath[] xpaths)
		{
			this.defaultAll = defaultAll;
			this.attributes = attributes;
			this.alphanum = alphanum;
			
			this.xpaths = xpaths;
        }
		
		public boolean getDefaultAll()
		{
			return defaultAll;
		}
		
		public void setDefaultAll(boolean defaultAll)
		{
			this.defaultAll = defaultAll;
		}
		
		public boolean getAttributes()
		{
			return attributes;
		}
		
		public void setAttributes(boolean attributes)
		{
			this.attributes = attributes;
		}
		
		public boolean getAlphanum()
		{
			return alphanum;
		}
		
		public void setAlphanum(boolean alphanum)
		{
			this.alphanum = alphanum;
		}
		
		public String getPath(int index)
		{
			return xpaths[index].getXPath();
		}
		
		public String getAction(int index)
		{
			return xpaths[index].getAction();
		}

        public int getLength()
		{
			return xpaths != null ? xpaths.length : 0;
		}

        public void setType(int index, String type) {
            xpaths[index].setType(type);
        }

        public String getType(int index) {
            return xpaths[index].getType();
        }
        
        public void setPath(int index, String XPath)
		{
            xpaths[index].setPath(XPath);
        }
        
        public void setAction(int index, String action)
		{
			xpaths[index].setAction(action);
		}
		
		public void addIndex(String type, String XPath, String action)
		{
            if(xpaths == null)
            {
                xpaths = new FullTextIndexPath[1];
                xpaths[0] = new FullTextIndexPath(type, XPath, action);
            }
            else
            {
                FullTextIndexPath newxpaths[] = new FullTextIndexPath[xpaths.length + 1];
                System.arraycopy(xpaths, 0, newxpaths, 0, xpaths.length);
                newxpaths[xpaths.length] = new FullTextIndexPath(type, XPath, action);
                xpaths = newxpaths;
            }
        }
		
		public void deleteIndex(int index)
		{
			//can only remove an index which is in the array
			if(index < xpaths.length)
			{
				//if its the last item in the array just null the array 
				if(xpaths.length == 1)
				{
					xpaths = null;
				}
				else
				{
					//else remove the item at index from the array
					FullTextIndexPath newxpaths[] = new FullTextIndexPath[xpaths.length - 1];
					int x = 0;
					for(int i = 0; i < xpaths.length; i++)
					{
						if(i != index)
						{
							newxpaths[x] = xpaths[i];
							x++;
						}
					}	
					xpaths = newxpaths;
				}
			}
		}
		
		//produces a collection.xconf suitable string of XML describing the fulltext index
		protected String toXMLString()
		{
			final StringBuilder fulltext = new StringBuilder();
			
			fulltext.append("<fulltext default=\"");
			fulltext.append(defaultAll ? "all" : "none");
			fulltext.append("\" attributes=\"");
			fulltext.append(attributes);
			fulltext.append("\" alphanum=\"");
			fulltext.append(alphanum);
			fulltext.append("\">");
			
			fulltext.append(System.getProperty("line.separator"));
			
            // Patch 1694080 prevents NPE
            if (xpaths != null ) {
                for(int i = 0; i < xpaths.length; i++) {
                    fulltext.append('\t');
                    
                    fulltext.append("<");
                    if (TYPE_PATH.equals(xpaths[i].getType())) {
                        fulltext.append(xpaths[i].getAction());
                        fulltext.append(" path=\"");
                        fulltext.append(xpaths[i].getXPath());
                    } else {
                        fulltext.append("create qname=\"");
                        fulltext.append(xpaths[i].getXPath());
                    }
                    fulltext.append("\"/>");
                    
                    fulltext.append(System.getProperty("line.separator"));
                }
            }
			
			fulltext.append("</fulltext>");
			return fulltext.toString();
		}
	}
	
	/**
	 * Represents a Range Index in the collection.xconf
	 */
	protected class RangeIndex
	{
        private String type = TYPE_QNAME;
        private String XPath = null;
		private String xsType = null;
		
		/**
		 * Constructor
		 * 
		 * @param XPath		The XPath to create a range index on
		 * @param xsType	The data type pointed to by the XPath as an xs:type 
		 */
		RangeIndex(String type, String XPath, String xsType)
		{
            this.type = type;
            this.XPath = XPath;
			this.xsType = xsType;
		}
		
		public String getXPath()
		{
			return(XPath);	
		}
		
		public String getxsType()
		{
			return(xsType);
		}

        public String getType() {
            return type;
        }

        public void setXPath(String XPath)
		{
			this.XPath = XPath;
		}
		
		public void setxsType(String xsType)
		{
			this.xsType = xsType;
		}

        public void setType(String type) {
            this.type = type;
        }
        
        //produces a collection.xconf suitable string of XML describing the range index
		protected String toXMLString()
		{
			final StringBuilder range = new StringBuilder();

            if (TYPE_PATH.equals(type))
                {range.append("<create path=\"");}
            else
                {range.append("<create qname=\"");}
            range.append(XPath);
			range.append("\" type=\"");
			range.append(xsType);
			range.append("\"/>");
			
			return range.toString();
		}
	}
	
	/**
	 * Represents a Trigger in the collection.xconf
	 */
	protected class Trigger
	{
		private String triggerClass = null;
		private Properties parameters = null;
		
		/**
		 * Constructor
		 * 
		 * @param triggerClass				The fully qualified java class name of the trigger
		 * @param parameters				Properties describing any name=value parameters for the trigger
		 */
		Trigger(final String triggerClass, final Properties parameters)
		{
			this.triggerClass = triggerClass;
			this.parameters = parameters;
		}
		
		public String getTriggerClass()
		{
			return triggerClass;
		}
		
		public void setTriggerClass(String triggerClass)
		{
			this.triggerClass = triggerClass;
		}
		
		//produces a collection.xconf suitable string of XML describing the trigger
		protected String toXMLString()
		{
			final StringBuilder trigger = new StringBuilder();
			
			if(!"".equals(triggerClass))
			{
			
				trigger.append("<trigger class=\"");
				trigger.append(triggerClass);
				trigger.append("\">");
				
				//parameters if any
				if(parameters != null)
				{
					if(parameters.size() > 0)
					{
						final Enumeration pKeys = parameters.keys();
						while(pKeys.hasMoreElements())
						{
							final String name = (String)pKeys.nextElement();
							final String value = parameters.getProperty(name);
						
							trigger.append("<parameter name=\"");
							trigger.append(name);
							trigger.append("\" value=\"");
							trigger.append(value);
							trigger.append("\"/>");
						}
					}
				}
				
				trigger.append("</trigger>");
			}
			return trigger.toString();
		}
	}
}
