package org.exist.client.xacml;

import com.sun.xacml.ParsingException;
import com.sun.xacml.Policy;
import com.sun.xacml.PolicySet;

import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.client.ClientFrame;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.security.xacml.XACMLConstants;
import org.exist.security.xacml.XACMLUtil;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.DatabaseStatus;
import org.w3c.dom.Element;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

public class DatabaseInterface
{
	private static final Logger LOG = Logger.getLogger(DatabaseInterface.class);
	
	private Collection policyCollection;

	private DatabaseInterface() {}
	public DatabaseInterface(Collection systemCollection)
	{
		setup(systemCollection);
	}

	public Collection getPolicyCollection()
	{
		return policyCollection;
	}
	
	private void setup(Collection systemCollection)
	{
		InputStream in = null;
		try
		{
			CollectionManagementService service = (CollectionManagementService)systemCollection.getService("CollectionManagementService", "1.0");
			policyCollection = service.createCollection(XACMLConstants.POLICY_COLLECTION_NAME);
			Collection confCol = service.createCollection("config" + XACMLConstants.POLICY_COLLECTION);
			
			String confName = XACMLConstants.POLICY_COLLECTION_NAME + ".xconf";
			XMLResource res = (XMLResource)confCol.createResource(confName, "XMLResource");
			
			in = DatabaseInterface.class.getResourceAsStream(confName);
			if(in == null)
				LOG.warn("Could not find policy collection configuration file '" + confName + "'");
			
			String content = toString(in);
			res.setContent(content);
			confCol.storeResource(res);
		}
		catch(IOException ioe)
		{
			ClientFrame.showErrorMessage("Error setting up XACML editor", ioe);
		}
		catch(XMLDBException xe)
		{
			ClientFrame.showErrorMessage("Error setting up XACML editor", xe);
		}
		finally
		{
			if(in != null)
			{
				try { in.close(); }
				catch(IOException ioe) {}
			}
		}
	}
	
	public void writePolicies(RootNode root)
	{
		Set removeDocs;
		try
		{
			removeDocs = new TreeSet(Arrays.asList(policyCollection.listResources()));
		}
		catch(XMLDBException xe)
		{
			LOG.warn("Could not list policy collection resources", xe);
			removeDocs = null;
		}
		
		int size = root.getChildCount();
		for(int i = 0; i < size; ++i)
		{
			AbstractPolicyNode node = (AbstractPolicyNode)root.getChild(i);
			
			String documentName = node.getDocumentName();
			if(documentName != null && removeDocs != null)
				removeDocs.remove(documentName);

			if(!node.isModified(true))
				continue;
			node.commit(true);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			node.create().encode(out);
			try
			{
				XMLResource xres;
				Resource res = (documentName == null) ? null : policyCollection.getResource(documentName);
				if(res == null)
					xres = null;
				else if(res instanceof XMLResource)
					xres = (XMLResource)res;
				else
				{
					xres = null;
					policyCollection.removeResource(res);
				}
				
				if(xres == null)
				{
					xres = (XMLResource)policyCollection.createResource(documentName, "XMLResource");
					if(documentName == null)
					{
						documentName = xres.getDocumentId();
						node.setDocumentName(documentName);
					}
				}
				
				xres.setContent(out.toString());
				policyCollection.storeResource(xres);
			}
			catch (XMLDBException e)
			{
				StringBuffer message = new StringBuffer();
				message.append("Error saving policy '");
				message.append(node.getId());
				message.append("' ");
				if(documentName != null)
				{
					message.append(" to document '");
					message.append(documentName);
					message.append("' ");
				}
				ClientFrame.showErrorMessage(message.toString(), e);
			}
		}
		if(removeDocs == null)
			return;
		for(Iterator it = removeDocs.iterator(); it.hasNext();)
		{
			String documentName = (String)it.next();
			try
			{
				Resource removeResource = policyCollection.getResource(documentName);
				policyCollection.removeResource(removeResource);
			}
			catch (XMLDBException xe)
			{
				LOG.warn("Could not remove resource '" + documentName + "'", xe);
			}
		}
	}
	
	public RootNode getPolicies()
	{
		RootNode root = new RootNode();
		findPolicies(root);
		root.commit(true);
		return root;
	}
	private void findPolicies(RootNode root)
	{
		BrokerPool pool = getBrokerPool();
		if(pool == null)
			return;
		
		DBBroker broker;
		try
		{
			broker = pool.get();
		}
		catch(EXistException ee)
		{
			LOG.warn("Could not get DBBroker", ee);
			return;
		}
		
		try
		{
			findPolicies(broker, root);
		}
		finally
		{
			pool.release(broker);
		}
	}
	private void findPolicies(DBBroker broker, RootNode root)
	{
		DocumentSet policies = XACMLUtil.getPolicyDocuments(broker, false);
		if(policies == null)
			return;
		
		for(Iterator it = policies.iterator(); it.hasNext();)
			handleDocument((DocumentImpl)it.next(), root);
	}
	private void handleDocument(DocumentImpl doc, RootNode root)
	{
		String documentName = doc.getFileName();
		Element rootElement = doc.getDocumentElement();
		if(rootElement == null)
			return;
		String namespace = rootElement.getNamespaceURI();
		String tagName = rootElement.getTagName();

		//sunxacml does not do namespaces, so this part is commented out for now
		if(/*XACMLConstants.XACML_POLICY_NAMESPACE.equals(namespace) && */XACMLConstants.POLICY_ELEMENT_LOCAL_NAME.equals(tagName))
		{
			Policy policy;
			try
			{
				policy = Policy.getInstance(rootElement);
			}
			catch(ParsingException pe)
			{
				ClientFrame.showErrorMessage("Error parsing policy document '" + documentName +"'", pe);
				return;
			}
			root.add(new PolicyNode(root, documentName, policy));
		}
		else if(/*XACMLConstants.XACML_POLICY_NAMESPACE.equals(namespace) && */XACMLConstants.POLICY_SET_ELEMENT_LOCAL_NAME.equals(tagName))
		{
			PolicySet policySet;
			try
			{
				policySet = PolicySet.getInstance(rootElement);
			}
			catch(ParsingException pe)
			{
				ClientFrame.showErrorMessage("Error parsing policy set document '" + documentName +"'", pe);
				return;
			}
			root.add(new PolicySetNode(root, documentName, policySet));
		}
		else
			LOG.warn("Document '" + documentName + "' in policy collection is not a policy: root tag has namespace '" + namespace + "' and name '" + tagName + "'");
	}
	//reads a stream into a string
	private String toString(InputStream in) throws IOException
	{
		if(in == null)
			return null;
		Reader reader = new InputStreamReader(in);
		char[] buffer = new char[100];
		CharArrayWriter writer = new CharArrayWriter(1000);
		int read;
		while((read = reader.read(buffer)) > -1)
			writer.write(buffer, 0, read);
		return writer.toString();
	}
	public BrokerPool getBrokerPool()
	{
		DatabaseStatus status;
		try
		{
			DatabaseInstanceManager manager = (DatabaseInstanceManager)policyCollection.getService("DatabaseInstanceManager", "1.0");
			status = manager.getStatus();
		}
		catch(XMLDBException xe)
		{
			LOG.warn("Could not get BrokerPool instance: " + xe.getMessage(), xe);
			return null;
		}
		
		String id = status.getId();
		try
		{
			return BrokerPool.getInstance(id);
		}
		catch(EXistException ee)
		{
			LOG.warn("Could not get BrokerPool instance: " + ee.getMessage(), ee);
			return null;
		}
	}
}
