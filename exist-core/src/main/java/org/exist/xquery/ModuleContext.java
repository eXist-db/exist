/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
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
 * Subclass of {@link org.exist.xquery.XQueryContext} for imported modules.
 *
 * @author wolf
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ModuleContext extends XQueryContext {

    private static final Logger LOG = LogManager.getLogger(ModuleContext.class);

    private XQueryContext parentContext;
    private String moduleNamespace;
    private String modulePrefix;
    private final String location;

    public ModuleContext(final XQueryContext parentContext, final String moduleNamespace, final String modulePrefix, final String location) {
        super(parentContext != null ? parentContext.db : null,
                parentContext != null ? parentContext.getConfiguration() : null,
                null,
                false);
        this.moduleNamespace = moduleNamespace;
        this.modulePrefix = modulePrefix;
        this.location = location;

        setParentContext(parentContext);

        loadDefaults(this.configuration);
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
    protected void addModuleVertex(final ModuleVertex moduleVertex) {
        getRootContext().addModuleVertex(moduleVertex);
    }

    protected boolean hasModuleVertex(final ModuleVertex moduleVertex) {
        return getRootContext().hasModuleVertex(moduleVertex);
    }

    @Override
    protected void addModuleEdge(final ModuleVertex source, final ModuleVertex sink) {
        getRootContext().addModuleEdge(source, sink);
    }

    @Override
    protected boolean hasModulePath(final ModuleVertex source, final ModuleVertex sink) {
        return getRootContext().hasModulePath(source, sink);
    }

    @Override
    public @Nullable Module[] importModule(@Nullable String namespaceURI, @Nullable String prefix, @Nullable AnyURIValue[] locationHints) throws XPathException {
        final ModuleVertex thisModuleVertex = new ModuleVertex(moduleNamespace, location);

        for (final AnyURIValue locationHint : locationHints) {
            final ModuleVertex imporedModuleVertex = new ModuleVertex(namespaceURI, locationHint.toString());

            if (!hasModuleVertex(imporedModuleVertex)) {
                addModuleVertex(imporedModuleVertex);
            } else {
                // Check if there is already a path from the imported module to this module
                if (getXQueryVersion() == 10 && namespaceURI != null && locationHints != null && hasModulePath(imporedModuleVertex, thisModuleVertex)) {
                    throw new XPathException(ErrorCodes.XQST0093, "Detected cyclic import between modules: " + getModuleNamespace() + " at: " + getLocation() + ", and: " + namespaceURI + " at: " + locationHint);
                }
            }

            if (!hasModuleVertex(thisModuleVertex)) {
                // NOTE(AR) may occur when the actual module has a different namespace from that of the `import module namespace`... will later raise an XQST0047 error
                addModuleVertex(thisModuleVertex);
            }

            addModuleEdge(thisModuleVertex, imporedModuleVertex);
        }

        return super.importModule(namespaceURI, prefix, locationHints);
    }

    @Override
    protected @Nullable Module importModuleFromLocation(final String namespaceURI, @Nullable final String prefix, final AnyURIValue locationHint) throws XPathException {
        // guard against self-recursive import - see: https://github.com/eXist-db/exist/issues/3448
        if (moduleNamespace.equals(namespaceURI) && location.equals(locationHint.toString())) {
            final StringBuilder builder = new StringBuilder("The XQuery Library Module '");
            builder.append(namespaceURI);
            builder.append("'");
            if (locationHint != null) {
                builder.append(" at '");
                builder.append(location);
                builder.append("'");
            }
            builder.append(" has invalidly attempted to import itself; this will be skipped!");
            LOG.warn(builder.toString());

            return null;
        }

        return super.importModuleFromLocation(namespaceURI, prefix, locationHint);
    }

    @Override
    protected void setModulesChanged() {
        parentContext.setModulesChanged();
    }

    private void setParentContext(final XQueryContext parentContext) {
        this.parentContext = parentContext;
        //XXX: raise error on null!
        if (parentContext != null) {
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
                    if (dir.matches("^[A-Za-z]+:.*")) {
                        setModuleLoadPath(dir);
                    } else if (".".equals(parentContext.moduleLoadPath)) {
                        if (!".".equals(dir)) {
                            if (dir.matches("(?:\\/.*)|(?:[a-zA-Z]:\\\\.*)")) {
                                setModuleLoadPath(dir);
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
        final ModuleContext ctx = new ModuleContext(parentContext, moduleNamespace, modulePrefix, location);
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
    public Module[] getModules(final String namespaceURI) {
        Module[] modules = super.getModules(namespaceURI);
        // TODO: I don't think modules should be able to access their parent context's modules,
        // since that breaks lexical scoping.  However, it seems that some eXist modules rely on
        // this so let's leave it for now.  (pkaminsk2)
        if (modules == null) {
            modules = parentContext.getModules(namespaceURI);
        }
        return modules;
    }

    @Override
    protected void setRootModules(final String namespaceURI, final Module[] modules) {
        parentContext.setRootModules(namespaceURI, modules);
    }

    @Override
    protected void addRootModule(final String namespaceURI, final Module module) {
        parentContext.addRootModule(namespaceURI, module);
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
    public Module[] getRootModules(final String namespaceURI) {
        return parentContext.getRootModules(namespaceURI);
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
            return new XPathException(getRootExpression(), ErrorCodes.XQST0059, message, new ValueSequence(new StringValue(getRootExpression(), moduleLocation), new StringValue(getRootExpression(), dependantModule)));
        } else {
            return new XPathException(getRootExpression(), ErrorCodes.XQST0059, message, new ValueSequence(new StringValue(getRootExpression(), moduleLocation), new StringValue(getRootExpression(), dependantModule)), e);
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
    public void popLocalVariables(final LocalVariable var, @Nullable final Sequence resultSequence) {
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
            Module[] modules;
            if (moduleNamespace.equals(qname.getNamespaceURI())) {
                modules = getRootModules(moduleNamespace);
            } else {
                modules = getModules(qname.getNamespaceURI());
            }
            if (modules != null) {
                for (final Module module : modules) {
                    var = module.resolveVariable(qname);
                    if (var != null) {
                        break;
                    }
                }
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
    public void addImportedContext(final XQueryContext importedContext) {
        parentContext.addImportedContext(importedContext);
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
