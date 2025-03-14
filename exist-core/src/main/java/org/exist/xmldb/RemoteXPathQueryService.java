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
package org.exist.xmldb;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.exist.source.Source;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.Leasable;
import org.exist.xmlrpc.RpcAPI;
import org.exist.xquery.XPathException;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.XMLResource;

import javax.xml.XMLConstants;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.xmldb.api.base.ResourceType.XML_RESOURCE;

public class RemoteXPathQueryService extends AbstractRemoteService implements EXistXPathQueryService, EXistXQueryService {

    private final Leasable<XmlRpcClient> leasableXmlRpcClient;
    private final Map<String, String> namespaceMappings = new HashMap<>();
    private final Map<String, Object> variableDecls = new HashMap<>();
    private final Properties outputProperties;
    private String moduleLoadPath = null;
    private boolean protectedMode = false;

    /**
     * Creates a new RemoteXPathQueryService instance.
     *
     * @param leasableXmlRpcClient the XML-RPC client lease
     * @param collection a RemoteCollection value
     */
    public RemoteXPathQueryService(final Leasable<XmlRpcClient> leasableXmlRpcClient, final RemoteCollection collection) {
        super(collection);
        this.leasableXmlRpcClient = leasableXmlRpcClient;
        this.outputProperties = collection.getProperties();
    }

    @Override
    public String getName() throws XMLDBException {
        return "XPathQueryService";
    }

    @Override
    public String getVersion() throws XMLDBException {
        return "1.0";
    }

    @Override
    public ResourceSet query(final String query) throws XMLDBException {
        return query(query, null);
    }

    @Override
    public ResourceSet query(final String query, final String sortExpr)
            throws XMLDBException {
        final Map<String, Object> optParams = new HashMap<>();

        if (sortExpr != null) {
            optParams.put(RpcAPI.SORT_EXPR, sortExpr);
        }
        if (!namespaceMappings.isEmpty()) {
            optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
        }
        if (!variableDecls.isEmpty()) {
            optParams.put(RpcAPI.VARIABLES, variableDecls);
        }
        optParams.put(RpcAPI.BASE_URI, outputProperties.getProperty(RpcAPI.BASE_URI, collection.getPath()));
        if (moduleLoadPath != null) {
            optParams.put(RpcAPI.MODULE_LOAD_PATH, moduleLoadPath);
        }
        if (protectedMode) {
            optParams.put(RpcAPI.PROTECTED_MODE, collection.getPath());
        }
        final List<Object> params = new ArrayList<>();
        params.add(query.getBytes(UTF_8));
        params.add(optParams);
        final Map result = (Map) collection.execute("queryPT", params);

        if (result.get(RpcAPI.ERROR) != null) {
            throwException(result);
        }

        final Object[] resources = (Object[]) result.get("results");
        int handle = -1;
        int hash = -1;
        if (resources != null && resources.length > 0) {
            handle = (Integer) result.get("id");
            hash = (Integer) result.get("hash");
        }
        final Properties resourceSetProperties = new Properties(outputProperties);
        resourceSetProperties.setProperty(EXistOutputKeys.XDM_SERIALIZATION, "yes");
        return new RemoteResourceSet(leasableXmlRpcClient, collection, resourceSetProperties, resources, handle, hash);
    }

    @Override
    public CompiledExpression compile(final String query) throws XMLDBException {
        try {
            return compileAndCheck(query);
        } catch (final XPathException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public CompiledExpression compileAndCheck(final String query) throws XMLDBException, XPathException {
        final Map<String, Object> optParams = new HashMap<>();
        if (!namespaceMappings.isEmpty()) {
            optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
        }
        if (!variableDecls.isEmpty()) {
            optParams.put(RpcAPI.VARIABLES, variableDecls);
        }
        if (moduleLoadPath != null) {
            optParams.put(RpcAPI.MODULE_LOAD_PATH, moduleLoadPath);
        }
        optParams.put(RpcAPI.BASE_URI,
                outputProperties.getProperty(RpcAPI.BASE_URI, collection.getPath()));
        final List<Object> params = new ArrayList<>();
        params.add(query.getBytes(UTF_8));
        params.add(optParams);
        final Map result = (Map) collection.execute("compile", params);

        if (result.get(RpcAPI.ERROR) != null) {
            throwXPathException(result);
        }
        return new RemoteCompiledExpression(query);
    }

    private void throwException(final Map result) throws XMLDBException {
        final String message = (String) result.get(RpcAPI.ERROR);
        final Integer lineInt = (Integer) result.get(RpcAPI.LINE);
        final Integer columnInt = (Integer) result.get(RpcAPI.COLUMN);
        final int line = lineInt == null ? 0 : lineInt;
        final int column = columnInt == null ? 0 : columnInt;
        final XPathException cause = new XPathException(line, column, message);
        throw new XMLDBException(ErrorCodes.VENDOR_ERROR, message, cause);
    }

    private void throwXPathException(final Map result) throws XPathException {
        final String message = (String) result.get(RpcAPI.ERROR);
        final Integer lineInt = (Integer) result.get(RpcAPI.LINE);
        final Integer columnInt = (Integer) result.get(RpcAPI.COLUMN);
        final int line = lineInt == null ? 0 : lineInt;
        final int column = columnInt == null ? 0 : columnInt;
        throw new XPathException(line, column, message);
    }

    @Override
    public ResourceSet execute(final Source source) throws XMLDBException {
        try {
            final String xq = source.getContent();
            return query(xq, null);
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public ResourceSet executeStoredQuery(final String uri) throws XMLDBException {

        final List<Object> params = new ArrayList<>();
        params.add(uri);
        params.add(new HashMap<String, Object>());

        final Map result = (Map) collection.execute("executeT", params);

        if (result.get(RpcAPI.ERROR) != null) {
            throwException(result);
        }

        final Object[] resources = (Object[]) result.get("results");
        int handle = -1;
        int hash = -1;
        if (resources != null && resources.length > 0) {
            handle = (Integer) result.get("id");
            hash = (Integer) result.get("hash");
        }
        final Properties resourceSetProperties = new Properties(outputProperties);
        resourceSetProperties.setProperty(EXistOutputKeys.XDM_SERIALIZATION, "yes");
        return new RemoteResourceSet(leasableXmlRpcClient, collection, outputProperties, resources, handle, hash);
    }

    @Override
    public ResourceSet query(final XMLResource res, final String query)
            throws XMLDBException {
        return query(res, query, null);
    }

    @Override
    public ResourceSet query(final XMLResource res, final String query, final String sortExpr)
            throws XMLDBException {
        final RemoteXMLResource resource = (RemoteXMLResource) res;
        final Map<String, Object> optParams = new HashMap<>();
        if (!namespaceMappings.isEmpty()) {
            optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
        }
        if (!variableDecls.isEmpty()) {
            optParams.put(RpcAPI.VARIABLES, variableDecls);
        }
        if (sortExpr != null) {
            optParams.put(RpcAPI.SORT_EXPR, sortExpr);
        }
        if (moduleLoadPath != null) {
            optParams.put(RpcAPI.MODULE_LOAD_PATH, moduleLoadPath);
        }
        optParams.put(RpcAPI.BASE_URI,
                outputProperties.getProperty(RpcAPI.BASE_URI, collection.getPath()));
        if (protectedMode) {
            optParams.put(RpcAPI.PROTECTED_MODE, collection.getPath());
        }
        final List<Object> params = new ArrayList<>();
        params.add(query.getBytes(UTF_8));
        params.add(resource.path.toString());
        params.add(resource.idIsPresent() ? resource.getNodeId() : "");
        params.add(optParams);
        final Map result = (Map) collection.execute("queryPT", params);

        if (result.get(RpcAPI.ERROR) != null) {
            throwException(result);
        }

        final Object[] resources = (Object[]) result.get("results");
        int handle = -1;
        int hash = -1;
        if (resources != null && resources.length > 0) {
            handle = (Integer) result.get("id");
            hash = (Integer) result.get("hash");
        }
        final Properties resourceSetProperties = new Properties(outputProperties);
        resourceSetProperties.setProperty(EXistOutputKeys.XDM_SERIALIZATION, "yes");
        return new RemoteResourceSet(leasableXmlRpcClient, collection, resourceSetProperties, resources, handle, hash);
    }

    @Override
    public ResourceSet queryResource(final String resource, final String query) throws XMLDBException {
        final Resource res = collection.getResource(resource);
        try {
            if (res == null) {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Resource " + resource + " not found");
            }
            if (!XML_RESOURCE.equals(res.getResourceType())) {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Resource " + resource + " is not an XML resource");
            }
            return query((XMLResource) res, query);
        }
        finally {
            if (res!=null && res instanceof AbstractRemoteResource) ((AbstractRemoteResource)res).freeResources();
        }
    }

    @Override
    public String getProperty(final String name) throws XMLDBException {
        return outputProperties.getProperty(name);
    }

    @Override
    public String getProperty(String name, String defaultValue) throws XMLDBException {
        return outputProperties.getProperty(name, defaultValue);
    }

    @Override
    public void setProperty(final String property, final String value) throws XMLDBException {
        outputProperties.setProperty(property, value);
    }

    @Override
    public void clearNamespaces() throws XMLDBException {
        namespaceMappings.clear();
    }

    @Override
    public void removeNamespace(final String ns)
            throws XMLDBException {
        namespaceMappings.values().removeIf(s -> s.equals(ns));
    }

    @Override
    public void setNamespace(final String prefix, final String namespace) throws XMLDBException {
        namespaceMappings.put(prefix != null ? prefix : XMLConstants.DEFAULT_NS_PREFIX, namespace);
    }

    @Override
    public String getNamespace(final String prefix) throws XMLDBException {
        return namespaceMappings.get(prefix != null ? prefix : XMLConstants.DEFAULT_NS_PREFIX);
    }

    @Override
    public void declareVariable(final String qname, final Object initialValue) throws XMLDBException {
        variableDecls.put(qname, initialValue);
    }

    @Override
    public void clearVariables() throws XMLDBException {
        variableDecls.clear();
    }

    @Override
    public ResourceSet execute(final CompiledExpression expression) throws XMLDBException {
        return query(((RemoteCompiledExpression) expression).getQuery());
    }

    @Override
    public ResourceSet execute(final XMLResource res, final CompiledExpression expression) throws XMLDBException {
        return query(res, ((RemoteCompiledExpression) expression).getQuery());
    }

    @Override
    public void setXPathCompatibility(final boolean backwardsCompatible) {
        // TODO: not passed
    }

    /**
     * Calling this method has no effect. The server loads modules
     * relative to its own context.
     *
     * @param path the module load path.
     */
    @Override
    public void setModuleLoadPath(final String path) {
        this.moduleLoadPath = path;
    }

    @Override
    public void dump(final CompiledExpression expression, final Writer writer) throws XMLDBException {
        final String query = ((RemoteCompiledExpression) expression).getQuery();
        final Map<String, Object> optParams = new HashMap<>();
        if (!namespaceMappings.isEmpty()) {
            optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
        }
        if (!variableDecls.isEmpty()) {
            optParams.put(RpcAPI.VARIABLES, variableDecls);
        }
        optParams.put(RpcAPI.BASE_URI,
                outputProperties.getProperty(RpcAPI.BASE_URI, collection.getPath()));
        final List<Object> params = new ArrayList<>();
        params.add(query);
        params.add(optParams);
        try {
            final String dump = (String) collection.execute("printDiagnostics", params);
            writer.write(dump);
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public void beginProtected() {
        protectedMode = true;
    }

    @Override
    public void endProtected() {
        protectedMode = false;
    }
}
