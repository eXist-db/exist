package org.exist.client.xacml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import org.exist.client.ClientFrame;
import org.exist.security.xacml.XACMLConstants;
import org.exist.security.xacml.XACMLUtil;

import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import com.sun.xacml.ParsingException;
import com.sun.xacml.Policy;
import com.sun.xacml.PolicySet;

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
		if(systemCollection == null)
			throw new NullPointerException("System collection cannot be null");
			
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
			
			String content = XACMLUtil.toString(in);
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
				node.commit(true);
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
		try
		{
			String[] resourceIds = policyCollection.listResources();
			for(int i = 0; i < resourceIds.length; ++i)
			{
				String resourceId = resourceIds[i];
				Resource resource = policyCollection.getResource(resourceId);
				if(resource != null && resource instanceof XMLResource)
					handleResource((XMLResource)resource, root);
			}
		}
		catch (XMLDBException xe)
		{
			ClientFrame.showErrorMessage("Error scanning for policies", xe);
		}
	}
	private void handleResource(XMLResource xres, RootNode root) throws XMLDBException
	{
		String documentName = xres.getDocumentId();
		Node content = xres.getContentAsDOM();
		Element rootElement;
		if(content instanceof Document)
			rootElement = ((Document)content).getDocumentElement();
		else if(content instanceof Element)
			rootElement = (Element)content;
		else
		{
			LOG.warn("The DOM representation of resource '" + documentName + "' in the policy collection was not a Document or Element node.");
			return;
		}
		
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
}
