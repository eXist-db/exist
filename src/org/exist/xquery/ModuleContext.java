package org.exist.xquery;

import java.util.Iterator;

import org.exist.dom.DocumentSet;


/**
 * Subclass of {@link org.exist.xquery.XQueryContext} for
 * imported modules.
 * 
 * @author wolf
 */
public class ModuleContext extends XQueryContext {

	private XQueryContext parentContext;
	
	/**
	 * @param broker
	 */
	public ModuleContext(XQueryContext parentContext) {
		super();
		this.parentContext = parentContext;
		this.broker = parentContext.broker;
		baseURI = parentContext.baseURI;
		moduleLoadPath = parentContext.moduleLoadPath;
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
		Module module = super.getModule(namespaceURI);
		if(module == null)
			module = parentContext.getModule(namespaceURI);
		return module;
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
