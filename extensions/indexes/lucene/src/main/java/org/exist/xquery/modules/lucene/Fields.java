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
package org.exist.xquery.modules.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.exist.collections.Collection;
import org.exist.dom.QName;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.indexing.lucene.AbstractFieldConfig;
import org.exist.indexing.lucene.LuceneConfig;
import org.exist.indexing.lucene.LuceneFacetConfig;
import org.exist.indexing.lucene.LuceneFieldConfig;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexConfig;
import org.exist.indexing.lucene.LuceneVectorFieldConfig;
import org.exist.indexing.lucene.analyzers.MetaAnalyzer;
import org.exist.storage.IndexSpec;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * {@code ft:fields($scope)} — introspects the Lucene index configuration in scope and returns the
 * configured fields and facets, one {@code map} per entry. It is the schema-discovery companion to
 * {@code ft:query-scope}/{@code ft:search-scope}: where they search, this describes what is searchable.
 *
 * <p>Each result map describes a configured field or facet:</p>
 * <pre>
 * map { "field": xs:string,                 (: the field name / facet dimension :)
 *       "element": xs:string,               (: the element the index is defined on (or named-index name) :)
 *       "kind": "field" | "facet",
 *       "analyzer": xs:string,              (: effective analyzer (fields only) :)
 *       "type": xs:string,                  (: declared XDM type, e.g. "xs:string" (fields only) :)
 *       "returnable": xs:boolean }          (: whether the field value is stored (fields only) :)
 * </pre>
 *
 * <p>It reflects the <em>resolved</em> configuration for {@code $scope} (handling collection inheritance,
 * analyzer-id resolution, and merged qname/wildcard/named indexes), so callers do not have to parse
 * {@code collection.xconf} themselves. It <b>aggregates across every collection in scope</b> the way
 * {@code ft:search-scope} aggregates documents: a scope spanning several producer collections returns
 * the union of their configured fields/facets (one record per occurrence; the caller dedups).</p>
 *
 * <p>It is <b>permission-agnostic</b>: it returns the full configured field set. Any per-caller field
 * visibility policy (e.g. exposing some fields only to certain groups) is the application layer's job.</p>
 */
public class Fields extends BasicFunction {

    private static final FunctionParameterSequenceType FS_PARAM_SCOPE =
            new FunctionParameterSequenceType("scope", Type.STRING, Cardinality.ZERO_OR_MORE,
                    "Collection (or document) URIs whose Lucene index configuration to describe. "
                            + "Collection URIs are resolved recursively.");
    private static final FunctionReturnSequenceType FS_RETURN =
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_MORE,
                    "One map per configured field or facet in scope: { field, element, kind, analyzer, type, returnable }.");

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("fields", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                    "Returns the configured Lucene fields and facets in scope, one map per entry "
                            + "(field name, element, kind, analyzer, type, returnable). The full set is returned; "
                            + "any per-caller field visibility policy is the application's responsibility.",
                    new SequenceType[]{FS_PARAM_SCOPE},
                    FS_RETURN)
    };

    public Fields(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final MutableDocumentSet docs = LuceneScope.resolveScope(this, args[0]);
        if (docs.getDocumentCount() == 0) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // Aggregate across every collection in scope. A $scope can span collections with different (or
        // no) configs — and LuceneIndexWorker.getLuceneConfig() returns only the first one it finds, so
        // it would silently drop the rest. Union each distinct collection config so ft:fields($scope)
        // discovers the full field set, the way ft:search-scope aggregates documents across the scope.
        final ValueSequence result = new ValueSequence();
        final Set<LuceneConfig> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final Iterator<Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {
            try (final Collection collection = i.next()) {
                final IndexSpec indexSpec = collection.getIndexConfiguration(context.getBroker());
                if (indexSpec == null) {
                    continue;
                }
                final LuceneConfig config = (LuceneConfig) indexSpec.getCustomIndexSpec(LuceneIndex.ID);
                if (config != null && seen.add(config)) {
                    appendFields(result, config);
                }
            }
        }
        return result;
    }

    private void appendFields(final ValueSequence result, final LuceneConfig config) throws XPathException {
        for (final LuceneIndexConfig head : config.getAllIndexConfigurations()) {
            for (LuceneIndexConfig ic = head; ic != null; ic = ic.getNext()) {
                final String element = ic.isNamed() ? ic.getName() : qnameString(ic.getQName());
                for (final AbstractFieldConfig fc : ic.getFacetsAndFields()) {
                    result.add(describe(config, fc, ic, element));
                }
            }
        }
    }

    private MapType describe(final LuceneConfig config, final AbstractFieldConfig fc, final LuceneIndexConfig ic,
                             final String element) throws XPathException {
        final MapType map = new MapType(this, context);
        map.add(new StringValue(this, "element"), new StringValue(this, element));
        if (fc instanceof LuceneFieldConfig field) {
            map.add(new StringValue(this, "field"), new StringValue(this, field.getName()));
            map.add(new StringValue(this, "kind"), new StringValue(this, "field"));
            map.add(new StringValue(this, "analyzer"), new StringValue(this, effectiveAnalyzer(config, field, ic)));
            map.add(new StringValue(this, "type"), new StringValue(this, Type.getTypeName(field.getType())));
            map.add(new StringValue(this, "returnable"), new BooleanValue(this, field.isStore()));
        } else if (fc instanceof LuceneFacetConfig facet) {
            map.add(new StringValue(this, "field"), new StringValue(this, facet.getDimension()));
            map.add(new StringValue(this, "kind"), new StringValue(this, "facet"));
        } else if (fc instanceof LuceneVectorFieldConfig vector) {
            map.add(new StringValue(this, "field"), new StringValue(this, vector.getName()));
            map.add(new StringValue(this, "kind"), new StringValue(this, "vector"));
        } else {
            // any other configured index entry (e.g. an element index with no named field): make it
            // self-distinguishing so every map carries field + kind, keyed by the element name.
            map.add(new StringValue(this, "field"), new StringValue(this, element));
            map.add(new StringValue(this, "kind"), new StringValue(this, "index"));
        }
        return map;
    }

    /**
     * The resolved analyzer class for a field on a given element: the field's own analyzer if set,
     * otherwise the element's index analyzer, otherwise the config's default analyzer. Always the
     * concrete class (resolving an {@code analyzer-id} reference and the default), never the raw id.
     */
    private static String effectiveAnalyzer(final LuceneConfig config, final LuceneFieldConfig field,
                                            final LuceneIndexConfig ic) {
        Analyzer analyzer = field.getAnalyzer();
        if (analyzer == null) {
            analyzer = ic.getAnalyzer();
        }
        if (analyzer == null) {
            analyzer = config.getAnalyzer((QName) null);
        }
        // eXist wraps the configured analyzers in a per-field MetaAnalyzer; unwrap it to the concrete
        // analyzer actually used for this field (the default, or a per-field/analyzer-id override).
        if (analyzer instanceof MetaAnalyzer meta) {
            analyzer = meta.getConfiguredAnalyzer(field.getName());
        }
        return analyzer != null ? analyzer.getClass().getName() : "(default)";
    }

    private static String qnameString(final QName qname) {
        return qname == null ? "" : qname.getStringValue();
    }
}
