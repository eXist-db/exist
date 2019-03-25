/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2018 The eXist Project
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
 */
package org.exist.xquery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.debuggee.DebuggeeJoint;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.security.Subject;
import org.exist.storage.UpdateListener;
import org.exist.util.FileUtils;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.Sequence;

import javax.annotation.Nullable;
import javax.xml.datatype.XMLGregorianCalendar;

import java.net.URISyntaxException;
import java.util.Iterator;
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

    private static final Logger LOG = LogManager.getLogger(ModuleContext.class);

    private XQueryContext parentContext;
    private String modulePrefix;
    private String moduleNamespace;
    private final String location;

    public ModuleContext(final XQueryContext parentContext, final String modulePrefix, final String moduleNamespace,
            final String location) {
        super();
        this.modulePrefix = modulePrefix;
        this.moduleNamespace = moduleNamespace;
        this.location = location;
        setParentContext(parentContext);

        loadDefaults(getBroker().getConfiguration());
        this.profiler = new Profiler(getBroker().getBrokerPool());
    }

    @Override
    public Subject getRealUser() {
        //the real and effective users are set at execution time on the root XQuery Context
        return getRootContext().getRealUser();
    }

    String getLocation() {
        return location;
    }

    String getModuleNamespace() {
        return moduleNamespace;
    }

    public void setModuleNamespace(final String prefix, final String namespaceURI) {
        this.modulePrefix = prefix;
        this.moduleNamespace = namespaceURI;
    }

    @Override
    protected void setModulesChanged() {
        parentContext.setModulesChanged();
    }

    private void setParentContext(final XQueryContext parentContext) {
        this.parentContext = parentContext;
        //XXX: raise error on null!
        if (parentContext != null) {
            this.db = parentContext.db;
            this.baseURI = parentContext.baseURI;
            try {
                if (location.startsWith(XmldbURI.XMLDB_URI_PREFIX) ||
                        (location.indexOf(':') < 0 &&
                                parentContext.getModuleLoadPath().startsWith(XmldbURI.XMLDB_URI_PREFIX))) {
                    // use XmldbURI resolution - unfortunately these are not interpretable as URIs
                    // because the scheme xmldb:exist: is not a valid URI scheme
                    final XmldbURI locationUri = XmldbURI.xmldbUriFor(FileUtils.dirname(location));
                    if (".".equals(parentContext.getModuleLoadPath())) {
                        setModuleLoadPath(locationUri.toString());
                    } else {
                        try {
                            final XmldbURI parentLoadUri = XmldbURI.xmldbUriFor(parentContext.getModuleLoadPath());
                            final XmldbURI moduleLoadUri = parentLoadUri.resolveCollectionPath(locationUri);
                            setModuleLoadPath(moduleLoadUri.toString());
                        } catch (final URISyntaxException e) {
                            setModuleLoadPath(locationUri.toString());
                        }
                    }
                } else {
                    final String dir = FileUtils.dirname(location);
                    if (dir.matches("^[a-z]+:.*")) {
                        moduleLoadPath = dir;
                    } else if (".".equals(parentContext.moduleLoadPath)) {
                        if (!".".equals(dir)) {
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

    @Override
    public void setModule(final String namespaceURI, final Module module) {
        if (module == null) {
            modules.remove(namespaceURI);   // unbind the module
        } else {
            modules.put(namespaceURI, module);
        }
        setRootModule(namespaceURI, module);
    }

    private XQueryContext getParentContext() {
        return parentContext;
    }

    @Override
    public boolean hasParent() {
        return true;
    }

    @Override
    public XQueryContext getRootContext() {
        return parentContext.getRootContext();
    }

    @Override
    public void updateContext(final XQueryContext from) {
        if (from.hasParent()) {
            // TODO: shouldn't this call setParentContext ? - sokolov
            this.parentContext = ((ModuleContext) from).parentContext;
        }
    }

    @Override
    public XQueryContext copyContext() {
        final ModuleContext ctx = new ModuleContext(parentContext, modulePrefix, moduleNamespace, location);
        copyFields(ctx);
        try {
            ctx.declareNamespace(modulePrefix, moduleNamespace);
        } catch (final XPathException e) {
            LOG.error(e);
        }
        return ctx;
    }

    @Override
    public void addDynamicOption(final String name, final String value) throws XPathException {
        parentContext.addDynamicOption(name, value);
    }

    @Override
    public DocumentSet getStaticallyKnownDocuments() throws XPathException {
        return parentContext.getStaticallyKnownDocuments();
    }

    @Override
    public Module getModule(final String namespaceURI) {
        Module module = super.getModule(namespaceURI);
        // TODO: I don't think modules should be able to access their parent context's modules,
        // since that breaks lexical scoping.  However, it seems that some eXist modules rely on
        // this so let's leave it for now.  (pkaminsk2)
        if (module == null) {
            module = parentContext.getModule(namespaceURI);
        }
        return module;
    }

    @Override
    protected void setRootModule(final String namespaceURI, final Module module) {
        parentContext.setRootModule(namespaceURI, module);
    }

    @Override
    public Iterator<Module> getRootModules() {
        return parentContext.getRootModules();
    }

    @Override
    public Iterator<Module> getAllModules() {
        return parentContext.getAllModules();
    }

    @Override
    public Module getRootModule(final String namespaceURI) {
        return parentContext.getRootModule(namespaceURI);
    }

    @Override
    final protected XPathException moduleLoadException(final String message, final String moduleLocation) throws XPathException {
        return moduleLoadException(message, moduleLocation, null);
    }

    @Override
    final protected XPathException moduleLoadException(final String message, final String moduleLocation, final Exception e) throws XPathException {
        String dependantModule;
        try {
            if (location != null && location.startsWith(XmldbURI.LOCAL_DB)) {
                dependantModule = location;
            } else {
                dependantModule = XmldbURI.create(getParentContext().getModuleLoadPath(), false).append(location).toString();
            }
        } catch (final Exception ex) {
            dependantModule = location;
        }

        if (e == null) {
            return new XPathException(ErrorCodes.XQST0059, message, new ValueSequence(new StringValue(moduleLocation), new StringValue(dependantModule)));
        } else {
            return new XPathException(ErrorCodes.XQST0059, message, new ValueSequence(new StringValue(moduleLocation), new StringValue(dependantModule)), e);
        }
    }

    @Override
    public XQueryWatchDog getWatchDog() {
        return parentContext.getWatchDog();
    }

    @Override
    public Profiler getProfiler() {
        return parentContext.getProfiler();
    }

    @Override
    public XMLGregorianCalendar getCalendar() {
        return parentContext.getCalendar();
    }

    @Override
    public AnyURIValue getBaseURI() throws XPathException {
        return parentContext.getBaseURI();
    }

    @Override
    public void setBaseURI(final AnyURIValue uri) {
        parentContext.setBaseURI(uri);
    }

    @Override
    public MemTreeBuilder getDocumentBuilder() {
        return parentContext.getDocumentBuilder();
    }

    @Override
    public MemTreeBuilder getDocumentBuilder(final boolean explicitCreation) {
        return parentContext.getDocumentBuilder(explicitCreation);
    }

    @Override
    public void pushDocumentContext() {
        parentContext.pushDocumentContext();
    }

    @Override
    public LocalVariable markLocalVariables(final boolean newContext) {
        return parentContext.markLocalVariables(newContext);
    }

    @Override
    public void popLocalVariables(final LocalVariable var) {
        parentContext.popLocalVariables(var);
    }

    @Override
    public void popLocalVariables(final LocalVariable var, final Sequence resultSequence) {
        parentContext.popLocalVariables(var, resultSequence);
    }

    @Override
    public LocalVariable declareVariableBinding(final LocalVariable var) throws XPathException {
        return parentContext.declareVariableBinding(var);
    }

    @Override
    protected Variable resolveLocalVariable(final QName qname) throws XPathException {
        return parentContext.resolveLocalVariable(qname);
    }

    @Override
    public Variable resolveVariable(final QName qname) throws XPathException {
        // check if the variable is declared local
        Variable var = resolveLocalVariable(qname);

        // check if the variable is declared in a module
        if (var == null) {
            Module module;
            if (moduleNamespace.equals(qname.getNamespaceURI())) {
                module = getRootModule(moduleNamespace);
            } else {
                module = getModule(qname.getNamespaceURI());
            }
            if (module != null) {
                var = module.resolveVariable(qname);
            }
        }

        // check if the variable is declared global
        if (var == null) {
            var = globalVariables.get(qname);
        }
        //if (var == null)
        //	throw new XPathException("variable $" + qname + " is not bound");
        return var;
    }

    @Override
    public Map<QName, Variable> getVariables() {
        return parentContext.getVariables();
    }

    @Override
    public Map<QName, Variable> getLocalVariables() {
        return parentContext.getLocalVariables();
    }

    @Override
    public List<ClosureVariable> getLocalStack() {
        return parentContext.getLocalStack();
    }

    @Override
    public Map<QName, Variable> getGlobalVariables() {
        return parentContext.getGlobalVariables();
    }

    @Nullable
    @Override
    public HttpContext getHttpContext() {
        return parentContext.getHttpContext();
    }

    @Override
    public void setHttpContext(final HttpContext httpContext) {
        parentContext.setHttpContext(httpContext);
    }

    @Override
    public void restoreStack(final List<ClosureVariable> stack) throws XPathException {
        parentContext.restoreStack(stack);
    }

    @Override
    public int getCurrentStackSize() {
        return parentContext.getCurrentStackSize();
    }

    @Override
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
    @Override
    public String getURIForPrefix(final String prefix) {
        String uri = getInScopeNamespace(prefix);
        if (uri != null) {
            return uri;
        }
        //TODO : test NS inheritance
        uri = getInheritedNamespace(prefix);
        if (uri != null) {
            return uri;
        }
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
    @Override
    public String getPrefixForURI(final String uri) {
        String prefix = getInScopePrefix(uri);
        if (prefix != null) {
            return prefix;
        }
        //TODO : test the NS inheritance
        prefix = getInheritedPrefix(uri);
        if (prefix != null) {
            return prefix;
        }
        return staticPrefixes.get(uri);
    }

    @Override
    public String getInScopeNamespace(final String prefix) {
        return parentContext.getInScopeNamespace(prefix);
    }

    @Override
    public String getInScopePrefix(final String uri) {
        return parentContext.getInScopePrefix(uri);
    }

    @Override
    public String getInheritedNamespace(final String prefix) {
        return parentContext.getInheritedNamespace(prefix);
    }

    @Override
    public String getInheritedPrefix(final String uri) {
        return parentContext.getInheritedPrefix(uri);
    }

    @Override
    public void declareInScopeNamespace(final String prefix, final String uri) {
        parentContext.declareInScopeNamespace(prefix, uri);
    }

    @Override
    public void pushInScopeNamespaces(final boolean inherit) {
        parentContext.pushInScopeNamespaces(inherit);
    }

    @Override
    public void pushInScopeNamespaces() {
        parentContext.pushInScopeNamespaces();
    }

    @Override
    public void popInScopeNamespaces() {
        parentContext.popInScopeNamespaces();
    }

    @Override
    public void registerUpdateListener(final UpdateListener listener) {
        parentContext.registerUpdateListener(listener);
    }

    @Override
    protected void clearUpdateListeners() {
        // will be cleared by the parent context
    }

    @Override
    public DebuggeeJoint getDebuggeeJoint() {
        return parentContext.getDebuggeeJoint();
    }

    @Override
    public boolean isDebugMode() {
        return parentContext.isDebugMode();
    }

    @Override
    public void expressionStart(final Expression expr) throws TerminatedException {
        parentContext.expressionStart(expr);
    }

    @Override
    public void expressionEnd(final Expression expr) {
        parentContext.expressionEnd(expr);
    }

    @Override
    public void stackEnter(final Expression expr) throws TerminatedException {
        parentContext.stackEnter(expr);
    }

    @Override
    public void stackLeave(final Expression expr) {
        parentContext.stackLeave(expr);
    }

    @Override
    public void registerBinaryValueInstance(final BinaryValue binaryValue) {
        parentContext.registerBinaryValueInstance(binaryValue);
    }

    @Override
    public void saveState() {
        super.saveState();
        parentContext.saveState();
    }
}
