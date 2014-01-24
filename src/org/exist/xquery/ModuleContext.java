/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import org.exist.debuggee.DebuggeeJoint;
import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.storage.UpdateListener;
import org.exist.util.FileUtils;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.Sequence;

import javax.xml.datatype.XMLGregorianCalendar;

import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.ValueSequence;


/**
 * Subclass of {@link org.exist.xquery.XQueryContext} for
 * imported modules.
 * 
 * @author wolf
 */
public class ModuleContext extends XQueryContext {

	private XQueryContext parentContext;
    private String modulePrefix;
    private String moduleNamespace;
    private final String location;

	/**
	 * @param parentContext
	 */
	public ModuleContext(XQueryContext parentContext, String modulePrefix, String moduleNamespace, String location) {
		super(parentContext.getAccessContext());
        this.moduleNamespace = moduleNamespace;
        this.modulePrefix = modulePrefix;
        this.location = location;
        setParentContext(parentContext);
		loadDefaults(getBroker().getConfiguration());
		this.profiler = new Profiler( getBroker().getBrokerPool() );
    }
	
	String getLocation() {
	    return location;
	}
   
	String getModuleNamespace() {
		return moduleNamespace;
	}

    public void setModuleNamespace(String prefix, String namespaceURI) {
        this.modulePrefix = prefix;
        this.moduleNamespace = namespaceURI;
    }

	void setModulesChanged() {
		parentContext.setModulesChanged();
	}
	
	private void setParentContext(XQueryContext parentContext) {
        this.parentContext = parentContext;
        //XXX: raise error on null!
        if (parentContext != null) {
        	this.db = parentContext.db;
		    baseURI = parentContext.baseURI;
		    try {
		        if (location.startsWith(XmldbURI.XMLDB_URI_PREFIX) ||
                    (location.indexOf(':') < 0 &&
                     parentContext.getModuleLoadPath().startsWith(XmldbURI.XMLDB_URI_PREFIX))) {
		            // use XmldbURI resolution - unfortunately these are not interpretable as URIs 
		            // because the scheme xmldb:exist: is not a valid URI scheme
                    final XmldbURI locationUri = XmldbURI.xmldbUriFor(FileUtils.dirname(location));
                    if (".".equals(parentContext.getModuleLoadPath()))
                        {setModuleLoadPath(locationUri.toString());}
                    else {
                    	try {
	                        final XmldbURI parentLoadUri = XmldbURI.xmldbUriFor(parentContext.getModuleLoadPath());
	                        final XmldbURI moduleLoadUri = parentLoadUri.resolveCollectionPath(locationUri);
	                        setModuleLoadPath(moduleLoadUri.toString());
                    	} catch (final URISyntaxException e) {
                            setModuleLoadPath(locationUri.toString());
						}
                    }
		        } else {
                    String dir = FileUtils.dirname(location);
                    if (dir.matches("^[a-z]+:.*")) {
                        moduleLoadPath = dir;
                    } else if (".".equals(parentContext.moduleLoadPath)) {
		                if (! ".".equals(dir)) {
		                    if (dir.startsWith("/")) {
		                        setModuleLoadPath("." + dir);
                                    } else {
		                        setModuleLoadPath("./" + dir);
                                    }
		                }
		            } else {
		                if (dir.startsWith("/")) {
		                    setModuleLoadPath(dir);
                                } else {
		                    setModuleLoadPath(FileUtils.addPaths(parentContext.getModuleLoadPath(), dir));
                                }
		            }
		        }
		    } catch (final URISyntaxException e) {
                e.printStackTrace();
            }
        }
	}

    public void setModule(String namespaceURI, Module module) {
        if (module == null) {
            modules.remove(namespaceURI);   // unbind the module
        } else {
            modules.put(namespaceURI, module);
        }
        setRootModule(namespaceURI, module);
    }

    XQueryContext getParentContext() {
		return parentContext;
	}

    public boolean hasParent() {
        return true;
    }

    public XQueryContext getRootContext() {
        return parentContext.getRootContext();
    }

    public void updateContext(XQueryContext from) {
        if (from.hasParent()) {
            // TODO: shouldn't this call setParentContext ? - sokolov
            this.parentContext = ((ModuleContext)from).parentContext;
        }
        
        //workaround for shared context issue, remove after fix
        try {
        	final Variable var = from.getRootContext().resolveVariable(ResponseModule.PREFIX + ":response");
        	if (var != null)
        		{declareVariable(
					ResponseModule.PREFIX + ":response", 
					var.getValue()
				);}
		} catch (final Exception e) {
			//ignore if not set
		}

        setModule(
    		ResponseModule.NAMESPACE_URI, 
    		from.getRootContext().getModule(ResponseModule.NAMESPACE_URI)
    	);
    }

    public XQueryContext copyContext() {
        final ModuleContext ctx = new ModuleContext(parentContext, modulePrefix, moduleNamespace, location);
        copyFields(ctx);
        try {
            ctx.declareNamespace(modulePrefix, moduleNamespace);
        } catch (final XPathException e) {
            e.printStackTrace();
        }
        return ctx;
    }

	public void addDynamicOption(String qnameString, String contents) throws XPathException
	{
		parentContext.addDynamicOption(qnameString, contents);
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
		// TODO: I don't think modules should be able to access their parent context's modules,
		// since that breaks lexical scoping.  However, it seems that some eXist modules rely on
		// this so let's leave it for now.  (pkaminsk2)
		if(module == null)
			{module = parentContext.getModule(namespaceURI);}
		return module;
	}
	
	protected void setRootModule(String namespaceURI, Module module) {
		allModules.put(namespaceURI, module);
		parentContext.setRootModule(namespaceURI, module);
	}

	public Iterator<Module> getRootModules() {
		return parentContext.getRootModules();
	}
	
	public Iterator<Module> getAllModules() {
		return allModules.values().iterator();
	}
	
	public Module getRootModule(String namespaceURI) {
		return parentContext.getRootModule(namespaceURI);
	}
	
    /**
     * Overwritten method: the module will be loaded by the parent context, but
     * we need to declare its namespace in the module context. 
     */
    public Module loadBuiltInModule(String namespaceURI, String moduleClass) {
        Module module = getModule(namespaceURI);
        if (module == null)
            {module = initBuiltInModule(namespaceURI, moduleClass);}
        if (module != null) {
            try {
            	final String defaultPrefix = module.getDefaultPrefix();
            	if (!"".equals(defaultPrefix))
            		{declareNamespace(defaultPrefix, module.getNamespaceURI());}
            } catch (final XPathException e) {
                LOG.warn("error while loading builtin module class " + moduleClass, e);
            }
        }
        return module;
    }

    public void updateModuleRefs(XQueryContext rootContext) {
        HashMap<String, Module> newModules = new HashMap<String, Module>(modules.size());
        for (Module module : modules.values()) {
            if (module.isInternalModule()) {
                Module updated = rootContext.getModule(module.getNamespaceURI());
                if (updated == null)
                    {updated = module;}
                newModules.put(module.getNamespaceURI(), updated);
            } else
                {newModules.put(module.getNamespaceURI(), module);}
        }
        modules = newModules;
    }
    
    @Override
    final protected XPathException moduleLoadException(final String message, final String moduleLocation) throws XPathException {
       return moduleLoadException(message, moduleLocation, null);
    }
    
    @Override
    final protected XPathException moduleLoadException(final String message, final String moduleLocation, final Exception e) throws XPathException {
        //final String dependantModule = XmldbURI.create(moduleLoadPath).append(location).toString();
        String dependantModule;
        try {
            dependantModule = XmldbURI.create(getParentContext().getModuleLoadPath(), false).append(location).toString();
        } catch (final Exception ex) {
            dependantModule = location;
        }
        
        if(e == null) {
            return new XPathException(ErrorCodes.XQST0059, message, new ValueSequence(new StringValue(moduleLocation), new StringValue(dependantModule)));
        } else {
            return new XPathException(ErrorCodes.XQST0059, message, new ValueSequence(new StringValue(moduleLocation), new StringValue(dependantModule)), e);
        }
    }

	/* (non-Javadoc)
	 * @see org.exist.xquery.XQueryContext#getWatchDog()
	 */
	public XQueryWatchDog getWatchDog() {
		return parentContext.getWatchDog();
	}

    public Profiler getProfiler() {
        return parentContext.getProfiler();
    }

    /* (non-Javadoc)
	 * @see org.exist.xquery.XQueryContext#getCalendar()
	 */
    public XMLGregorianCalendar getCalendar(){
        return parentContext.getCalendar();
    }

    /* (non-Javadoc)
	 * @see org.exist.xquery.XQueryContext#getBaseURI()
	 */
	public AnyURIValue getBaseURI() throws XPathException {
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
    
//    /* (non-Javadoc)
//     * @see org.exist.xquery.XQueryContext#getBroker()
//     */
//    public DBBroker getBroker() {
//        return parentContext.getBroker();
//    }
    
    /* (non-Javadoc)
	 * @see org.exist.xquery.XQueryContext#getDocumentBuilder()
	 */
	public MemTreeBuilder getDocumentBuilder() {
		return parentContext.getDocumentBuilder();
	}

    public MemTreeBuilder getDocumentBuilder(boolean explicitCreation) {
        return parentContext.getDocumentBuilder(explicitCreation);
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

    public void popLocalVariables(LocalVariable var, Sequence resultSequence) {
        parentContext.popLocalVariables(var, resultSequence);
    }

    public LocalVariable declareVariableBinding(LocalVariable var) throws XPathException {
        return parentContext.declareVariableBinding(var);
    }

    protected Variable resolveLocalVariable(QName qname) throws XPathException {
        return parentContext.resolveLocalVariable(qname);
    }

    /**
     * Try to resolve a variable.
     *
     * @param qname the qualified name of the variable
     * @return the declared Variable object
     * @throws XPathException if the variable is unknown
     */
    public Variable resolveVariable(QName qname) throws XPathException {
        Variable var;

        // check if the variable is declared local
        var = resolveLocalVariable(qname);

        // check if the variable is declared in a module
        if (var == null) {
            Module module;
            if (moduleNamespace.equals(qname.getNamespaceURI())) {
                module = getRootModule(moduleNamespace);
            } else {
                module = getModule(qname.getNamespaceURI());
            }
            if(module != null) {
                var = module.resolveVariable(qname);
            }
        }

        // check if the variable is declared global
        if (var == null)
            {var = globalVariables.get(qname);}
        //if (var == null)
        //	throw new XPathException("variable $" + qname + " is not bound");
        return var;
    }

    public Map<QName, Variable> getVariables() {
    	return parentContext.getVariables();
    }

    public Map<QName, Variable> getLocalVariables() {
    	return parentContext.getLocalVariables();
    }

    public List<Variable> getLocalStack() {
    	return parentContext.getLocalStack();
    }
    
    public Map<QName, Variable> getGlobalVariables() {
    	return parentContext.getGlobalVariables();
    }

    public void restoreStack(List<Variable> stack) throws XPathException {
    	parentContext.restoreStack(stack);
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

    /**
     * First checks the parent context for in-scope namespaces,
     * then the module's static context.
     *
     * @param prefix the prefix to look up
     * @return the namespace currently mapped to that prefix
     */
    public String getURIForPrefix(String prefix) {
        String uri = getInScopeNamespace(prefix);
        if (uri != null)
            {return uri;}
        //TODO : test NS inheritance
        uri = getInheritedNamespace(prefix);
        if (uri != null)
            {return uri;}
        // Check global declarations
        return staticNamespaces.get(prefix);
    }

    /**
     * First checks the parent context for in-scope namespaces,
     * then the module's static context.
     *
     * @param uri the URI to look up
     * @return a prefix for the URI
     */
    public String getPrefixForURI(String uri) {
        String prefix = getInScopePrefix(uri);
		if (prefix != null)
			{return prefix;}
		//TODO : test the NS inheritance
        prefix = getInheritedPrefix(uri);
		if (prefix != null)
			{return prefix;}
		return staticPrefixes.get(uri);
    }

    public String getInScopeNamespace(String prefix) {
        return parentContext.getInScopeNamespace(prefix);
    }

    public String getInScopePrefix(String uri) {
        return parentContext.getInScopePrefix(uri);
    }

    public String getInheritedNamespace(String prefix) {
        return parentContext.getInheritedNamespace(prefix);
    }

    public String getInheritedPrefix(String uri) {
        return parentContext.getInheritedPrefix(uri);
    }

    public void declareInScopeNamespace(String prefix, String uri) {
        parentContext.declareInScopeNamespace(prefix, uri);
    }

    public void pushInScopeNamespaces(boolean inherit) {
        parentContext.pushInScopeNamespaces(inherit);
    }

    public void pushInScopeNamespaces() {
        parentContext.pushInScopeNamespaces();
    }

    public void popInScopeNamespaces() {
        parentContext.popInScopeNamespaces();
    }

    public void registerUpdateListener(UpdateListener listener) {
		parentContext.registerUpdateListener(listener);
	}
	
	protected void clearUpdateListeners() {
		// will be cleared by the parent context
	}

	public DebuggeeJoint getDebuggeeJoint() {
		return parentContext.getDebuggeeJoint();
	}
	
	public boolean isDebugMode() {
		return parentContext.isDebugMode();
	}

    public void expressionStart(Expression expr) throws TerminatedException {
       	parentContext.expressionStart(expr);
    }

    public void expressionEnd(Expression expr) {
       	parentContext.expressionEnd(expr);
    }

    public void stackEnter(Expression expr) throws TerminatedException {
       	parentContext.stackEnter(expr);
    }

    public void stackLeave(Expression expr) {
       	parentContext.stackLeave(expr);
    }

    @Override
    public void registerBinaryValueInstance(BinaryValue binaryValue) {
        parentContext.registerBinaryValueInstance(binaryValue);
    }

    @Override
    public void saveState() {
        super.saveState();
        parentContext.saveState();
    }
}
