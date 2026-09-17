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

import java.util.List;

import org.exist.dom.QName;
import org.exist.indexing.IndexManager;
import org.exist.util.Configuration;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Reports every index module known at startup — active and
 * {@code enabled="no"}-suppressed — with no equivalent function existing
 * before #6563. See {@link Configuration.IndexModuleConfig}.
 */
public class GetRegisteredIndexes extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("get-registered-indexes", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
            "Returns a sequence of maps, one per index module known at startup (active and " +
            "enabled=\"no\"-suppressed). Each map has keys: 'id' (index id), 'class' " +
            "(implementation class name), 'registration-source' ('spi' or 'conf.xml'), and " +
            "'enabled' ('yes' or 'no'). This function is only available to the DBA role.",
            FunctionSignature.NO_ARGS,
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_MORE,
                    "sequence of maps with keys 'id', 'class', 'registration-source', and 'enabled'"));

    public GetRegisteredIndexes(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (!context.getSubject().hasDbaRole()) {
            throw new XPathException(this, "Only a DBA can call system:get-registered-indexes()");
        }

        final Configuration configuration = context.getBroker().getConfiguration();
        final List<Configuration.IndexModuleConfig> registry =
                (List<Configuration.IndexModuleConfig>) configuration.getProperty(IndexManager.PROPERTY_INDEXER_MODULES_REGISTRY);

        final ValueSequence resultSeq = new ValueSequence();
        if (registry != null) {
            for (final Configuration.IndexModuleConfig entry : registry) {
                final String registrationSource = entry.config() == null ? "spi" : "conf.xml";
                final MapType map = new MapType(this, context);
                map.add(new StringValue(this, "id"), new StringValue(this, entry.id()));
                map.add(new StringValue(this, "class"), new StringValue(this, entry.className()));
                map.add(new StringValue(this, "registration-source"), new StringValue(this, registrationSource));
                map.add(new StringValue(this, "enabled"), new StringValue(this, entry.enabled() ? "yes" : "no"));
                resultSeq.add(map);
            }
        }
        return resultSeq;
    }
}
