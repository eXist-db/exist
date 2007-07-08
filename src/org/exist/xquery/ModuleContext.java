package org.exist.xquery;

import java.util.Iterator;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.storage.DBBroker;
import org.exist.storage.UpdateListener;
import org.exist.xquery.value.AnyURIValue;


/**
 * Subclass of {@link org.exist.xquery.XQueryContext} for
 * imported modules.
 * 
 * @author wolf
 */
public class ModuleContext extends XQueryContext {

	private final XQueryContext parentContext;
	private boolean initializing = true;

    /**
	 * @param parentContext
	 */
	public ModuleContext(XQueryContext parentContext) {
		super(parentContext.getAccessContext());
		this.parentContext = parentContext;
		this.broker = parentContext.broker;
		baseURI = parentContext.baseURI;
		moduleLoadPath = parentContext.moduleLoadPath;
		loadDefaults(broker.getConfiguration());
        initializing = false;
    }

    public XQueryContext copyContext() {
        ModuleContext ctx = new ModuleContext(this.parentContext);
        copyFields(ctx);
        return ctx;
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

    /**
     * Overwritten method: the module will be loaded by the parent context, but
     * we need to declare its namespace in the module context. 
     */
    public Module loadBuiltInModule(String namespaceURI, String moduleClass) {
        Module module = getModule(namespaceURI);
        if (module == null)
            module = initBuiltInModule(namespaceURI, moduleClass);
        if (module != null) {
            try {
            	String defaultPrefix = module.getDefaultPrefix();
            	if (!"".equals(defaultPrefix))
            		declareNamespace(defaultPrefix, module.getNamespaceURI());
            } catch (XPathException e) {
                LOG.warn("error while loading builtin module class " + moduleClass, e);
            }
        }
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
	public AnyURIValue getBaseURI() {
		return parentContext.getBaseURI();
	}
    
    public void setBaseURI(AnyURIValue uri) {
        parentContext.setBaseURI(uri);
    }
    

    /**
     * Delegate to parent context
     * 
     * @see org.exist.xquery.XQueryContext#setXQueryContextVar(String, Object)
     */
    public void setXQueryContextVar(String name, Object XQvar)
    {
    	parentContext.setXQueryContextVar(name, XQvar);
    }

    /**
     * Delegate to parent context
     * 
     * @see org.exist.xquery.XQueryContext#getXQueryContextVar(String)
     */
    public Object getXQueryContextVar(String name)
    {
    	return(parentContext.getXQueryContextVar(name));
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

    public LocalVariable markLocalVariables(boolean newContext) {
        return parentContext.markLocalVariables(newContext);
    }

    public void popLocalVariables(LocalVariable var) {
        parentContext.popLocalVariables(var);
    }

    public LocalVariable declareVariableBinding(LocalVariable var) throws XPathException {
        return parentContext.declareVariableBinding(var);
    }

    protected Variable resolveLocalVariable(QName qname) throws XPathException {
        return parentContext.resolveLocalVariable(qname);
    }

    public int getCurrentStackSize() {
        return parentContext.getCurrentStackSize();
    }

    /* (non-Javadoc)
	 * @see org.exist.xquery.XQueryContext#popDocumentContext()
	 */
	public void popDocumentContext() {
		parentContext.popDocumentContext();
	}
	
    public void registerUpdateListener(UpdateListener listener) {
		parentContext.registerUpdateListener(listener);
	}
	
	protected void clearUpdateListeners() {
		// will be cleared by the parent context
	}
}
