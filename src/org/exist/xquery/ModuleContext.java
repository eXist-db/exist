/*
 * Created on Jul 15, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.exist.xquery;

import java.util.Iterator;

import org.exist.dom.DocumentSet;
import org.exist.memtree.MemTreeBuilder;


/**
 * @author wolf
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class ModuleContext extends XQueryContext {

	private XQueryContext parentContext;
	
	/**
	 * @param broker
	 */
	public ModuleContext(XQueryContext parentContext) {
		super();
		this.parentContext = parentContext;
		this.broker = parentContext.getBroker();
		loadDefaults();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.XQueryContext#getStaticallyKnownDocuments()
	 */
	public DocumentSet getStaticallyKnownDocuments() throws XPathException {
		return parentContext.getStaticallyKnownDocuments();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.XQueryContext#getModule(java.lang.String)
	 */
	public Module getModule(String namespaceURI) {
		return parentContext.getModule(namespaceURI);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.XQueryContext#getModules()
	 */
	public Iterator getModules() {
		return parentContext.getModules();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.XQueryContext#getWatchDog()
	 */
	public XQueryWatchDog getWatchDog() {
		return parentContext.getWatchDog();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.XQueryContext#getBaseURI()
	 */
	public String getBaseURI() {
		return parentContext.getBaseURI();
	}
}
