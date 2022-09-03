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
package org.exist.indexing.range;

import com.ibm.icu.text.Collator;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.collation.ICUCollationAttributeFactory;
import org.apache.lucene.util.AttributeFactory;
import org.exist.util.Collations;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.w3c.dom.Element;

import java.io.Reader;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.lang.invoke.MethodType.methodType;

/**
 * Lucene analyzer used by the range index. Based on {@link KeywordTokenizer}, it allows additional
 * filters to be added to the pipeline through the collection.xconf configuration. A collation may be
 * specified as well.
 */
public class RangeIndexAnalyzer extends Analyzer {

    private static class FilterConfig {
        private Function<TokenStream, TokenStream> constructor;

        FilterConfig(final Element config) throws DatabaseConfigurationException {
            final String className = config.getAttribute("class");
            if (className == null) {
                throw new DatabaseConfigurationException("No class specified for filter");
            }
            try {
                final Class clazz = Class.forName(className);
                if (!TokenFilter.class.isAssignableFrom(clazz)) {
                    throw new DatabaseConfigurationException("Filter " + className + " is not a subclass of " +
                        TokenFilter.class.getName());
                }
                final MethodHandles.Lookup lookup = MethodHandles.lookup();
                final MethodHandle methodHandle = lookup.findConstructor(clazz, methodType(void.class, TokenStream.class));

                this.constructor = (Function<TokenStream, TokenStream>)
                        LambdaMetafactory.metafactory(
                                lookup, "apply", methodType(Function.class),
                                methodHandle.type().erase(), methodHandle, methodHandle.type()).getTarget().invokeExact();

            } catch (final Throwable e) {
                if (e instanceof InterruptedException) {
                    // NOTE: must set interrupted flag
                    Thread.currentThread().interrupt();
                }

                throw new DatabaseConfigurationException("Filter not found: " + className, e);
            }
        }
    }

    private List<FilterConfig> filterConfigs = new ArrayList<>();
    private Collator collator = null;

    public RangeIndexAnalyzer() {
    }

    public void addFilter(Element filter) throws DatabaseConfigurationException {
        filterConfigs.add(new FilterConfig(filter));
    }

    public void addCollation(String uri) throws DatabaseConfigurationException {
        try {
            collator = Collations.getCollationFromURI(uri, null, ErrorCodes.FOCH0002);
        } catch (XPathException e) {
            throw new DatabaseConfigurationException(e.getMessage(), e);
        }
    }

    @Override
    protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
        AttributeFactory factory = AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY;
        if (collator != null) {
            factory = new ICUCollationAttributeFactory(collator);
        }
        final Tokenizer src = new KeywordTokenizer(factory, reader, 256);
        TokenStream tok = src;
        for (final FilterConfig filter: filterConfigs) {
            tok = filter.constructor.apply(tok);
        }
        return new TokenStreamComponents(src, tok);
    }
}
