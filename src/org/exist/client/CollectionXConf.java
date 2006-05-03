package org.exist.client;

import java.io.IOException;
import java.util.Properties;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.exist.storage.DBBroker;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * @author adam
 * 
 * */

//class to represent a collection.xconf
public class CollectionXConf
{
	
	private String path = null;
	
	private FullTextIndex fulltextIndex = null;
	private RangeIndex[] rangeIndexes = null;
	private QNameIndex[] qnameIndexes;
	private Trigger[] triggers = null;
	
	
	CollectionXConf(String CollectionName, InteractiveClient client) throws XMLDBException
	{
		path = DBBroker.CONFIG_COLLECTION + CollectionName;
		Collection collection = client.getCollection(path);
		
		if(collection == null) //if no config collection for this collection exists, just return
			return;
		
		//get the resource from the db
		Resource resConfig = collection.getResource(DBBroker.COLLECTION_CONFIG_FILENAME);
		
		if(resConfig == null) //if, no config file exists for that collection, just return
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
		
		//Read FullText Index from xconf
		fulltextIndex = getFullTextIndex(xconf);
		
		//Read Range Indexes from xconf
		rangeIndexes = getRangeIndexes(xconf);
		
		//Read QName Indexes from xconf
		qnameIndexes = getQNameIndexes(xconf);
		
		//read Triggers from xconf
		triggers = getTriggers(xconf);
	}
	
	public boolean getFullTextIndexDefaultAll()
	{
		return fulltextIndex != null ? fulltextIndex.getDefaultAll() : false;
	}
	
	public boolean getFullTextIndexAttributes()
	{
		return fulltextIndex != null ? fulltextIndex.getAttributes() : false;
	}
	
	public boolean getFullTextIndexAlphanum()
	{
		return fulltextIndex != null ? fulltextIndex.getAlphanum() : false;
	}
	
	public String getFullTextIndexPath(int index)
	{
		return fulltextIndex.getXPath(index);
	}
	
	public String getFullTextIndexPathAction(int index)
	{
		return fulltextIndex.getAction(index);
	}
	
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
	
	public void addFullTextIndex(String XPath, String action)
	{
		fulltextIndex.addIndex(XPath, action);
	}
	
	public void updateFullTextIndex(int index, String XPath, String action)
	{
		if(XPath != null)
			fulltextIndex.setXPath(index, XPath);
		
		if(action != null)
			fulltextIndex.setAction(index, action);
	}
	
	public void deleteFullTextIndex(int index)
	{
		fulltextIndex.deleteIndex(index);
	}
	
	public RangeIndex[] getRangeIndexes()
	{
		return rangeIndexes;
	}
	
	public RangeIndex getRangeIndex(int index)
	{
		return rangeIndexes[index];
	}
	
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
	
	public void deleteRangeIndex(int index)
	{
		//can only remove an index which is in the array
		if(index < rangeIndexes.length)
		{
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
	
	public void updateRangeIndex(int index, String XPath, String xsType)
	{
		if(XPath != null)
			rangeIndexes[index].setXPath(XPath);
		
		if(xsType != null)
			rangeIndexes[index].setxsType(xsType);
	}
	
	public void addRangeIndex(String XPath, String xsType)
	{
		if(rangeIndexes == null)
		{
			rangeIndexes = new RangeIndex[1];
			rangeIndexes[0] = new RangeIndex(XPath, xsType);
		}
		else
		{
			RangeIndex newRangeIndexes[] = new RangeIndex[rangeIndexes.length + 1];
			System.arraycopy(rangeIndexes, 0, newRangeIndexes, 0, rangeIndexes.length);
			newRangeIndexes[rangeIndexes.length] = new RangeIndex(XPath, xsType);
			rangeIndexes = newRangeIndexes;
		}
	}
	
	public QNameIndex[] getQNameIndexes()
	{
		return qnameIndexes;
	}
	
	public QNameIndex getQNameIndex(int index)
	{
		return qnameIndexes[index];
	}
	
	public int getQNameIndexCount()
	{
		if(qnameIndexes != null)
		{
			return qnameIndexes.length;
		}
		else
		{
			return 0;
		}
	}
	
	public void deleteQNameIndex(int index)
	{
		//can only remove an index which is in the array
		if(index < qnameIndexes.length)
		{
			//if its the last item in the array just null the array 
			if(qnameIndexes.length == 1)
			{
				qnameIndexes = null;
			}
			else
			{
				QNameIndex newQNameIndexes[] = new QNameIndex[qnameIndexes.length - 1];
				int x = 0;
				for(int i = 0; i < qnameIndexes.length; i++)
				{
					if(i != index)
					{
						newQNameIndexes[x] = qnameIndexes[i];
						x++;
					}
				}
				
				qnameIndexes = newQNameIndexes;
			}
		}
	}
	
	public void updateQNameIndex(int index, String QName, String xsType)
	{
		if(QName != null)
			qnameIndexes[index].setQName(QName);
		
		if(xsType != null)
			qnameIndexes[index].setxsType(xsType);
	}
	
	public void addQNameIndex(String QName, String xsType)
	{
		if(qnameIndexes == null)
		{
			qnameIndexes = new QNameIndex[1];
			qnameIndexes[0] = new QNameIndex(QName, xsType);
		}
		else
		{
			QNameIndex newQNameIndexes[] = new QNameIndex[qnameIndexes.length + 1];
			System.arraycopy(qnameIndexes, 0, newQNameIndexes, 0, qnameIndexes.length);
			newQNameIndexes[qnameIndexes.length] = new QNameIndex(QName, xsType);
			qnameIndexes = newQNameIndexes;
		}
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
			
			int iPaths = nlInclude.getLength() + nlExclude.getLength();
			
			if(iPaths > 0 )
			{
				paths = new FullTextIndexPath[iPaths];
			
				if(nlInclude.getLength() > 0)
				{
					for(int i = 0; i < nlInclude.getLength(); i++)
					{
						paths[i] = new FullTextIndexPath(((Element)nlInclude.item(i)).getAttribute("path"), FullTextIndexPath.ACTION_INCLUDE);
					}
				}
				
				if(nlExclude.getLength() > 0)
				{	
					for(int i = 0; i < nlExclude.getLength(); i++)
					{
						paths[i] = new FullTextIndexPath(((Element)nlExclude.item(i)).getAttribute("path"), FullTextIndexPath.ACTION_EXCLUDE);
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
		Vector vecRangeIndexes = new Vector();
		
		NodeList nlRangeIndexes = xconf.getElementsByTagName("create");
		if(nlRangeIndexes.getLength() > 0)
		{
			for(int i = 0; i < nlRangeIndexes.getLength(); i++)
			{	
				Element rangeIndex = (Element)nlRangeIndexes.item(i);
				//is it a range index or a qname index
				if(rangeIndex.getAttribute("path").length() > 0)
				{
					vecRangeIndexes.add(new RangeIndex(rangeIndex.getAttribute("path"), rangeIndex.getAttribute("type")));
				}
			}
			
			RangeIndex[] rangeIndexes = new RangeIndex[vecRangeIndexes.size()];
			for(int i=0; i < vecRangeIndexes.size(); i++)
			{
				rangeIndexes[i] = (RangeIndex)vecRangeIndexes.get(i);
			}
			return rangeIndexes;
		}
		return null;
	}

	//given the root element of collection.xconf it will return an array of qname indexes
	private QNameIndex[] getQNameIndexes(Element xconf)
	{		
		Vector vecQNameIndexes = new Vector();
		
		NodeList nlQNameIndexes = xconf.getElementsByTagName("create");
		if(nlQNameIndexes.getLength() > 0)
		{ 
			for(int i = 0; i < nlQNameIndexes.getLength(); i++)
			{	
				Element qnameIndex = (Element)nlQNameIndexes.item(i);
				//is it a range index or a qname index
				if(qnameIndex.getAttribute("qname").length() > 0)
				{
					vecQNameIndexes.add(new QNameIndex(qnameIndex.getAttribute("qname"), qnameIndex.getAttribute("type")));
				}
			}
			
			QNameIndex[] qnameIndexes = new QNameIndex[vecQNameIndexes.size()];
			for(int i=0; i < vecQNameIndexes.size(); i++)
			{
				qnameIndexes[i] = (QNameIndex)vecQNameIndexes.get(i);
			}
			return qnameIndexes;
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
	
	//represents a path in the fulltext index in the collection.xconf
	protected class FullTextIndexPath
	{
		public final static String ACTION_INCLUDE = "include";
		public final static String ACTION_EXCLUDE = "exclude";
		
		private String xpath = null;
		private String action = null;
		
		FullTextIndexPath(String xpath, String action)
		{
			this.xpath = xpath;
			this.action = action;
		}
		
		public String getXPath()
		{
			return xpath;
		}
		
		public String getAction()
		{
			return action;
		}
		
		public void setXPath(String xpath)
		{
			this.xpath = xpath;
		}
		
		public void setAction(String action)
		{
			this.action = action;
		}
		
	}
	
	//represents the fulltext index in the collection.xconf
	protected class FullTextIndex
	{	
		boolean defaultAll = true;
		boolean attributes = false;
		boolean alphanum = false;
		FullTextIndexPath[] xpaths = null;
	
		
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
		
		public boolean getAttributes()
		{
			return attributes;
		}
		
		public boolean getAlphanum()
		{
			return alphanum;
		}
		
		public String getXPath(int index)
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
		
		public void setXPath(int index, String XPath)
		{
			xpaths[index].setXPath(XPath);
		}
		
		public void setAction(int index, String action)
		{
			xpaths[index].setAction(action);
		}
		
		public void addIndex(String XPath, String action)
		{
			if(xpaths == null)
			{
				xpaths = new FullTextIndexPath[1];
				xpaths[0] = new FullTextIndexPath(XPath, action);
			}
			else
			{
				FullTextIndexPath newxpaths[] = new FullTextIndexPath[xpaths.length + 1];
				System.arraycopy(xpaths, 0, newxpaths, 0, xpaths.length);
				newxpaths[xpaths.length] = new FullTextIndexPath(XPath, action);
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
	}
	
	//represents a range index in the collection.xconf
	protected class RangeIndex
	{
		private String XPath = null;
		private String xsType = null;
		
		RangeIndex(String XPath, String xsType)
		{
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
		
		public void setXPath(String XPath)
		{
			this.XPath = XPath;
		}
		
		public void setxsType(String xsType)
		{
			this.xsType = xsType;
		}
	}
	
	//represents a qname index in the collection.xconf
	protected class QNameIndex
	{
		private String QName = null;
		private String xsType = null;
		
		QNameIndex(String QName, String xsType)
		{
			this.QName = QName;
			this.xsType = xsType;
		}
		
		public String getQName()
		{
			return(QName);	
		}
		
		public String getxsType()
		{
			return(xsType);
		}
		
		public void setQName(String QName)
		{
			this.QName = QName;	
		}
		
		public void setxsType(String xsType)
		{
			this.xsType = xsType;
		}
	}
	
	//represents a Trigger in the collection.xconf
	protected class Trigger
	{
		/*public final static int EVENT_STORE_DOCUMENT = 1;
		public final static int EVENT_UPDATE_DOCUMENT = 2;
		public final static int EVENT_REMOVE_DOCUMENT = 3;
		public final static int EVENT_RENAME_COLLECTION = 4;
		public final static int EVENT_CREATE_COLLECTION = 5;
		
		private int triggerEvent = -1;*/
		private String triggerEvent = null;
		private String triggerClass = null;
		Properties parameters = null;
		
		Trigger(String triggerEvent, String triggerClass, Properties parameters)
		{
			this.triggerEvent = triggerEvent;
			this.triggerClass = triggerClass;
			this.parameters = parameters;
		}
		
	}
}
