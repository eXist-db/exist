/*
 * eXist Open Source Native XML Database
 *
 * Copyright (C) 2001-06 Wolfgang M. Meier wolfgang@exist-db.org
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
package org.exist.client;

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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.Set;

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
	
	private LinkedHashMap customNamespaces = null;		//custom namespaces
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
			return;
		
		//get the resource from the db
        String[] resources = collection.listResources();
        for (int i = 0; i < resources.length; i++) {
            if (resources[i].endsWith(CollectionConfiguration.COLLECTION_CONFIG_SUFFIX)) {
                resConfig = collection.getResource(resources[i]);
                break;
            }
        }
		
		if(resConfig == null) //if, no config file exists for that collection
			return;
		
		//Parse the configuration file into a DOM
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		Document docConfig = null;
		try
		{
			DocumentBuilder builder = factory.newDocumentBuilder();
			docConfig = builder.parse( new java.io.ByteArrayInputStream(resConfig.getContent().toString().getBytes()) );
		}
		catch(ParserConfigurationException pce)
		{
			//TODO: do something here, throw xmldbexception?
		} 
		catch(SAXException se)
		{
			//TODO: do something here, throw xmldbexception?
		}
		catch(IOException ioe)
		{
			//TODO: do something here, throw xmldbexception?
		}
		
		//Get the root of the collection.xconf
		Element xconf = docConfig.getDocumentElement();
		
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
            fulltextIndex.setType(index, type);

        if(XPath != null)
			fulltextIndex.setPath(index, XPath);
		
		if(action != null)
			fulltextIndex.setAction(index, action);
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
            rangeIndexes[index].setType(type);
        
        if(XPath != null)
			rangeIndexes[index].setXPath(XPath);
		
		if(xsType != null)
			rangeIndexes[index].setxsType(xsType);
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
	 * 
	 */
	public void updateTrigger(int index, String triggerClass, boolean STORE_DOCUMENT_EVENT, boolean UPDATE_DOCUMENT_EVENT, boolean REMOVE_DOCUMENT_EVENT, boolean CREATE_COLLECTION_EVENT, boolean RENAME_COLLECTION_EVENT, boolean DELETE_COLLECTION_EVENT, Properties parameters)
	{
		//TODO: finish this!!! - need to add code for parameters
		
		hasChanged = true;
		
		if(triggerClass != null)
			triggers[index].setTriggerClass(triggerClass);
		
		triggers[index].setStoreDocumentEvent(STORE_DOCUMENT_EVENT);
		triggers[index].setUpdateDocumentEvent(UPDATE_DOCUMENT_EVENT);
		triggers[index].setRemoveDocumentEvent(REMOVE_DOCUMENT_EVENT);
		triggers[index].setCreateCollectionEvent(CREATE_COLLECTION_EVENT);
		triggers[index].setRenameCollectionEvent(RENAME_COLLECTION_EVENT);
		triggers[index].setDeleteCollectionEvent(DELETE_COLLECTION_EVENT);
	}
	
	/**
	 * Add a Trigger
	 *
	 * @param triggerClass The class for the Trigger
	 * 
	 */
	public void addTrigger(String triggerClass, boolean STORE_DOCUMENT_EVENT, boolean UPDATE_DOCUMENT_EVENT, boolean REMOVE_DOCUMENT_EVENT, boolean CREATE_COLLECTION_EVENT, boolean RENAME_COLLECTION_EVENT, boolean DELETE_COLLECTION_EVENT, Properties parameters)
	{
		//TODO: finish this!!! seee updateTrigger
		
		hasChanged = true;
		
		if(triggers == null)
		{
			triggers = new Trigger[1];
			triggers[0] = new Trigger(triggerClass, STORE_DOCUMENT_EVENT, UPDATE_DOCUMENT_EVENT, REMOVE_DOCUMENT_EVENT, CREATE_COLLECTION_EVENT, RENAME_COLLECTION_EVENT, DELETE_COLLECTION_EVENT, parameters);
		}
		else
		{
			Trigger newTriggers[] = new Trigger[triggers.length + 1];
			System.arraycopy(triggers, 0, newTriggers, 0, triggers.length);
			newTriggers[triggers.length] = new Trigger(triggerClass, STORE_DOCUMENT_EVENT, UPDATE_DOCUMENT_EVENT, REMOVE_DOCUMENT_EVENT, CREATE_COLLECTION_EVENT, RENAME_COLLECTION_EVENT, DELETE_COLLECTION_EVENT, parameters);
			triggers = newTriggers;
		}
	}
	
	//given the root element of collection.xconf it will return the fulltext index
	private LinkedHashMap getCustomNamespaces(Element xconf)
	{
		NamedNodeMap attrs = xconf.getAttributes();
		
		//there will always be one attribute - the default namespace
		if(attrs.getLength() > 1)
		{
			LinkedHashMap namespaces = new LinkedHashMap();
			
			for(int i = 0; i < attrs.getLength(); i++)
			{
				Node a = attrs.item(i);
				if(a.getNodeName().startsWith("xmlns:"))
				{
					String namespaceLocalName = a.getNodeName().substring(a.getNodeName().indexOf(":")+1); 
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
		NodeList nlFullTextIndex = xconf.getElementsByTagName("fulltext");
		if(nlFullTextIndex.getLength() > 0)
		{
			boolean defaultAll = true;
			boolean attributes = false;
			boolean alphanum = false;
			FullTextIndexPath[] paths = null;

            Element elemFullTextIndex = (Element)nlFullTextIndex.item(0);
			defaultAll = elemFullTextIndex.getAttribute("default").equals("all");
			attributes = elemFullTextIndex.getAttribute("attributes").equals("true");
			alphanum = elemFullTextIndex.getAttribute("alphanum").equals("true");
			
			NodeList nlInclude = elemFullTextIndex.getElementsByTagName("include");
			NodeList nlExclude = elemFullTextIndex.getElementsByTagName("exclude");
			NodeList nlQName = elemFullTextIndex.getElementsByTagName("create");

            int iPaths = nlInclude.getLength() + nlExclude.getLength() + nlQName.getLength();
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
		NodeList nlRangeIndexes = xconf.getElementsByTagName("create");
        if(nlRangeIndexes.getLength() > 0)
		{
            List rl = new ArrayList();
            for(int i = 0; i < nlRangeIndexes.getLength(); i++)
			{	
				Element rangeIndex = (Element)nlRangeIndexes.item(i);
                if (rangeIndex.hasAttribute("type")) {
                    if (rangeIndex.hasAttribute("qname"))
                        rl.add(new RangeIndex(TYPE_QNAME, rangeIndex.getAttribute("qname"), rangeIndex.getAttribute("type")));
                    else
                        rl.add(new RangeIndex(TYPE_PATH, rangeIndex.getAttribute("path"), rangeIndex.getAttribute("type")));
                }
            }
            RangeIndex[] rangeIndexes = new RangeIndex[rl.size()];
            rangeIndexes = (RangeIndex[]) rl.toArray(rangeIndexes);
            return rangeIndexes;
		}
		return null;
	}
	
	//given the root element of collection.xconf it will return an array of triggers
	private Trigger[] getTriggers(Element xconf)
	{
		NodeList nlTriggers = xconf.getElementsByTagName("trigger");
		if(nlTriggers.getLength() > 0)
		{
			Trigger[] triggers = new Trigger[nlTriggers.getLength()]; 
			
			for(int i = 0; i < nlTriggers.getLength(); i++)
			{	
				Element trigger = (Element)nlTriggers.item(i);
				
				Properties parameters = new Properties();
				NodeList nlTriggerParameters = trigger.getElementsByTagName("parameter");
				if(nlTriggerParameters.getLength() > 0)
				{
					for(int x = 0; x < nlTriggerParameters.getLength(); x++)
					{
						Element parameter = (Element)nlTriggerParameters.item(x);
						parameters.setProperty(parameter.getAttribute("name"), parameter.getAttribute("value"));
					}
				}
				
				//create the trigger
				triggers[i] = new Trigger(trigger.getAttribute("event"), trigger.getAttribute("class"), parameters);
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
		StringBuffer xconf = new StringBuffer();
		
		xconf.append("<collection xmlns=\"http://exist-db.org/collection-config/1.0\"");
		if(customNamespaces != null)
		{
			Set namespaceKeys = customNamespaces.keySet();
			Iterator itKeys = namespaceKeys.iterator();
			while(itKeys.hasNext())
			{
				xconf.append(" ");
				String namespaceLocalName = (String)itKeys.next();
				String namespaceURL = (String)customNamespaces.get(namespaceLocalName);
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
		catch(XMLDBException xmldbe)
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
			StringBuffer fulltext = new StringBuffer();
			
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
			StringBuffer range = new StringBuffer();

            if (TYPE_PATH.equals(type))
                range.append("<create path=\"");
            else
                range.append("<create qname=\"");
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
		private boolean STORE_DOCUMENT_EVENT = false;
		private boolean UPDATE_DOCUMENT_EVENT = false;
		private boolean REMOVE_DOCUMENT_EVENT = false;
		private boolean CREATE_COLLECTION_EVENT = false;
		private boolean RENAME_COLLECTION_EVENT = false;
		private boolean DELETE_COLLECTION_EVENT = false;		
		private Properties parameters = null;
		
		/**
		 * Constructor
		 * 
		 * @param triggerClass				The fully qualified java class name of the trigger
		 * @param STORE_DOCUMENT_EVENT		true indicates that the trigger should receive the Store Document Event
		 * @param UPDATE_DOCUMENT_EVENT		true indicates that the trigger should receive the Update Document Event
		 * @param REMOVE_DOCUMENT_EVENT		true indicates that the trigger should receive the Remove Document Event
		 * @param CREATE_COLLECTION_EVENT	true indicates that the trigger should receive the Create Collection Event
		 * @param RENAME_COLLECTION_EVENT	true indicates that the trigger should receive the Rename Collection Event
		 * @param DELETE_COLLECTION_EVENT	true indicates that the trigger should receive the Delete Collection Event
		 * @param parameters				Properties describing any name=value parameters for the trigger
		 */
		Trigger(String triggerClass, boolean STORE_DOCUMENT_EVENT, boolean UPDATE_DOCUMENT_EVENT, boolean REMOVE_DOCUMENT_EVENT, boolean CREATE_COLLECTION_EVENT, boolean RENAME_COLLECTION_EVENT, boolean DELETE_COLLECTION_EVENT, Properties parameters)
		{
			//set properties
			this.triggerClass = triggerClass;
			this.STORE_DOCUMENT_EVENT =  STORE_DOCUMENT_EVENT;
			this.UPDATE_DOCUMENT_EVENT =  UPDATE_DOCUMENT_EVENT;
			this.REMOVE_DOCUMENT_EVENT =  REMOVE_DOCUMENT_EVENT;
			this.CREATE_COLLECTION_EVENT = CREATE_COLLECTION_EVENT;
			this.RENAME_COLLECTION_EVENT = RENAME_COLLECTION_EVENT;
			this.DELETE_COLLECTION_EVENT = DELETE_COLLECTION_EVENT;
			this.parameters = parameters;
		}
		
		Trigger(String triggerClass, String triggerEvents, Properties parameters)
		{
			this.triggerClass = triggerClass;
			if(triggerEvents.indexOf("store") > -1)
				STORE_DOCUMENT_EVENT = true;
			if(triggerEvents.indexOf("update") > -1)
				UPDATE_DOCUMENT_EVENT = true;
			if(triggerEvents.indexOf("remove") > -1)
				REMOVE_DOCUMENT_EVENT = true;
			if(triggerEvents.indexOf("create") > -1)
				CREATE_COLLECTION_EVENT = true;
			if(triggerEvents.indexOf("rename") > -1)
				RENAME_COLLECTION_EVENT = true;
			if(triggerEvents.indexOf("delete") > -1)
				DELETE_COLLECTION_EVENT = true;
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
		
		public boolean getStoreDocumentEvent()
		{
			return STORE_DOCUMENT_EVENT;
		}
		
		public void setStoreDocumentEvent(boolean store)
		{
			STORE_DOCUMENT_EVENT = store;
		}
		
		public boolean getUpdateDocumentEvent()
		{
			return UPDATE_DOCUMENT_EVENT;
		}
		
		public void setUpdateDocumentEvent(boolean update)
		{
			UPDATE_DOCUMENT_EVENT = update;
		}
		
		public boolean getRemoveDocumentEvent()
		{
			return REMOVE_DOCUMENT_EVENT;
		}
		
		public void setRemoveDocumentEvent(boolean remove)
		{
			REMOVE_DOCUMENT_EVENT = remove;
		}
		
		public boolean getCreateCollectionEvent()
		{
			return CREATE_COLLECTION_EVENT;
		}
		
		public void setCreateCollectionEvent(boolean create)
		{
			CREATE_COLLECTION_EVENT = create;
		}
		
		public boolean getRenameCollectionEvent()
		{
			return RENAME_COLLECTION_EVENT;
		}
		
		public void setRenameCollectionEvent(boolean rename)
		{
			RENAME_COLLECTION_EVENT = rename;
		}
		
		public boolean getDeleteCollectionEvent()
		{
			return DELETE_COLLECTION_EVENT;
		}
		
		public void setDeleteCollectionEvent(boolean delete)
		{
			DELETE_COLLECTION_EVENT = delete;
		}
		
		//produces a collection.xconf suitable string of XML describing the trigger
		protected String toXMLString()
		{
			StringBuffer trigger = new StringBuffer();
			
			if(!triggerClass.equals(""))
			{
			
				trigger.append("<trigger class=\"");
				trigger.append(triggerClass);
				trigger.append("\" event=\"");
				
				//events
				if(STORE_DOCUMENT_EVENT)
					trigger.append("store,");
				if(UPDATE_DOCUMENT_EVENT)
					trigger.append("update,");
				if(REMOVE_DOCUMENT_EVENT)
					trigger.append("remove,");
				if(CREATE_COLLECTION_EVENT)
					trigger.append("create,");
				if(RENAME_COLLECTION_EVENT)
					trigger.append("rename,");
				if(DELETE_COLLECTION_EVENT)
					trigger.append("delete,");
				
				//remove possible trailing comma in events attribute
				if(trigger.charAt(trigger.length() -1 ) == ',')
				{
					trigger.deleteCharAt(trigger.length() - 1);
				}
				trigger.append("\">");
				
				//parameters if any
				if(parameters != null)
				{
					if(parameters.size() > 0)
					{
						Enumeration pKeys = parameters.keys();
						while(pKeys.hasMoreElements())
						{
							String name = (String)pKeys.nextElement();
							String value = parameters.getProperty(name);
						
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
