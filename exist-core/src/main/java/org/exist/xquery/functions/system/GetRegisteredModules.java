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
package org.exist.xquery.functions.system;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.exist.dom.QName;
import org.exist.repo.ExistRepository;
import org.exist.util.Configuration;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.ModuleRegistration;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Successor to {@code util:registered-modules-info()} (deprecated, see
 * {@link org.exist.xquery.functions.util.ModuleInfo}). Reports every XQuery
 * module known to the system, including {@code enabled="no"}-suppressed
 * built-in modules that {@code util:registered-modules-info()} could never
 * see because they never made it into a live {@link Module} instance.
 *
 * @see org.exist.xquery.ModuleRegistration
 */
public class GetRegisteredModules extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("get-registered-modules", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
            "Returns a sequence of maps, one per XQuery module known to the system: built in, " +
            "EXPath package, conf.xml-mapped, and enabled=\"no\"-suppressed built-in modules. Each " +
            "map has keys: 'uri' (namespace URI), 'prefix' (default prefix, empty for a suppressed " +
            "entry with no live module instance), 'source' (one of 'built-in', 'package', or " +
            "'mapped'), 'registration-source' (how a 'built-in' entry was registered: 'spi', " +
            "'conf.xml', or 'built-in' for modules that are always present regardless of " +
            "configuration; equal to 'source' for 'package'/'mapped' entries), and 'enabled' " +
            "('yes' or 'no' - 'no' only for a conf.xml/SPI entry suppressed via enabled=\"no\"). " +
            "This function is only available to the DBA role.",
            FunctionSignature.NO_ARGS,
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_MORE,
                    "sequence of maps with keys 'uri', 'prefix', 'source', 'registration-source', and 'enabled'"));

    public GetRegisteredModules(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (!context.getSubject().hasDbaRole()) {
            throw new XPathException(this, "Only a DBA can call system:get-registered-modules()");
        }

        final Configuration configuration = context.getBroker().getConfiguration();
        final List<ModuleRegistration> registrations =
                (List<ModuleRegistration>) configuration.getProperty(XQueryContext.PROPERTY_MODULE_REGISTRATIONS);
        final Map<String, ModuleRegistration> registrationByUri = new HashMap<>();
        if (registrations != null) {
            for (final ModuleRegistration registration : registrations) {
                registrationByUri.put(registration.uri(), registration);
            }
        }

        final ValueSequence resultSeq = new ValueSequence();
        final Set<String> seen = new HashSet<>();
        // The calling context already has every built-in module loaded (done eagerly for any
        // XQueryContext at construction, see loadDefaults()/addBuiltInModuleOrDeclareNamespace()),
        // and getRepository()/getMappedModuleURIs() read from configuration rather than per-context
        // state — so reuse it instead of constructing a throwaway XQueryContext, which would
        // reflectively re-instantiate every built-in module class and re-run its prepare() hook.
        addBuiltInModules(context, registrationByUri, seen, resultSeq);
        addPackageModules(context, seen, resultSeq);
        addMappedModules(context, seen, resultSeq);
        addSuppressedModules(registrationByUri, seen, resultSeq);
        return resultSeq;
    }

    /** Java built-in modules actually loaded (active classMap entries). */
    private void addBuiltInModules(final XQueryContext queryContext, final Map<String, ModuleRegistration> registrationByUri,
            final Set<String> seen, final ValueSequence resultSeq) throws XPathException {
        for (final Iterator<Module> i = queryContext.getRootModules(); i.hasNext(); ) {
            final Module module = i.next();
            final String nsUri = module.getNamespaceURI();
            if (seen.add(nsUri)) {
                final ModuleRegistration registration = registrationByUri.get(nsUri);
                final String registrationSource = registration != null ? registration.source() : ModuleRegistration.SOURCE_BUILT_IN;
                resultSeq.add(createEntry(nsUri, module.getDefaultPrefix(), "built-in", registrationSource, true));
            }
        }
    }

    /** Java and XQuery EXPath package modules. */
    private void addPackageModules(final XQueryContext queryContext, final Set<String> seen, final ValueSequence resultSeq)
            throws XPathException {
        if (queryContext.getRepository().isEmpty()) {
            return;
        }
        final ExistRepository repo = queryContext.getRepository().get();
        for (final URI uri : repo.getJavaModules()) {
            addPackageModule(queryContext, uri, seen, resultSeq);
        }
        for (final URI uri : repo.getXQueryModules()) {
            addPackageModule(queryContext, uri, seen, resultSeq);
        }
    }

    private void addPackageModule(final XQueryContext queryContext, final URI uri, final Set<String> seen,
            final ValueSequence resultSeq) throws XPathException {
        final String nsUri = uri.toString();
        if (seen.add(nsUri)) {
            resultSeq.add(createEntry(nsUri, getModulePrefix(queryContext, nsUri), "package", "package", true));
        }
    }

    /** Conf.xml-mapped XQuery modules. */
    private void addMappedModules(final XQueryContext queryContext, final Set<String> seen, final ValueSequence resultSeq)
            throws XPathException {
        for (final Iterator<String> i = queryContext.getMappedModuleURIs(); i.hasNext(); ) {
            final String nsUri = i.next();
            if (seen.add(nsUri)) {
                resultSeq.add(createEntry(nsUri, getModulePrefix(queryContext, nsUri), "mapped", "mapped", true));
            }
        }
    }

    /** enabled="no"-suppressed built-in modules: never reached a live Module instance. */
    private void addSuppressedModules(final Map<String, ModuleRegistration> registrationByUri, final Set<String> seen,
            final ValueSequence resultSeq) throws XPathException {
        for (final ModuleRegistration registration : registrationByUri.values()) {
            if (!registration.enabled() && seen.add(registration.uri())) {
                resultSeq.add(createEntry(registration.uri(), "", "built-in", registration.source(), false));
            }
        }
    }

    private String getModulePrefix(final XQueryContext queryContext, final String namespaceURI) {
        final Module[] modules = queryContext.getRootModules(namespaceURI);
        if (modules != null && modules.length > 0) {
            return modules[0].getDefaultPrefix();
        }
        return "";
    }

    private MapType createEntry(final String uri, final String prefix, final String source,
            final String registrationSource, final boolean enabled) throws XPathException {
        final MapType map = new MapType(this, context);
        map.add(new StringValue(this, "uri"), new StringValue(this, uri));
        map.add(new StringValue(this, "prefix"), new StringValue(this, prefix));
        map.add(new StringValue(this, "source"), new StringValue(this, source));
        map.add(new StringValue(this, "registration-source"), new StringValue(this, registrationSource));
        map.add(new StringValue(this, "enabled"), new StringValue(this, enabled ? "yes" : "no"));
        return map;
    }
}
