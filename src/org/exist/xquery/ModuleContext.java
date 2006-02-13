package org.exist.xquery;

import java.util.Iterator;

import org.exist.dom.DocumentSet;
import org.exist.memtree.MemTreeBuilder;
import org.exist.storage.DBBroker;


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
		super(parentContext.getAccessContext());
		this.parentContext = parentContext;
		this.broker = parentContext.broker;
		baseURI = parentContext.baseURI;
		moduleLoadPath = parentContext.moduleLoadPath;
		loadDefaults(broker.getConfiguration());
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
    
    public void setBaseURI(String uri) {
        parentContext.setBaseURI(uri);
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.XQueryContext#getBroker()
     */
    public DBBroker getBroker() {
        return parentContext.getBroker();
    }
    
    /* (non-Javadoc)
	 * @see org.exist.xquery.XQueryContext#getDocumentBuilder()
	 */
	public MemTreeBuilder getDocumentBuilder() {
		return parentContext.getDocumentBuilder();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.XQueryContext#pushDocumentContext()
	 */
	public void pushDocumentContext() {
		parentContext.pushDocumentContext();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.XQueryContext#popDocumentContext()
	 */
	public void popDocumentContext() {
		parentContext.popDocumentContext();
	}
}
