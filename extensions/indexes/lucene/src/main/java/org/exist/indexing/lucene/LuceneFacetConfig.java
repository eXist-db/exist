/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.indexing.lucene;

import org.apache.lucene.facet.FacetsConfig;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

import java.util.Map;

public class LuceneFacetConfig {

    public final static String CATEGORY_ATTR = "category";
    public final static String XPATH_ATTR = "xpath";

    protected String category;
    protected String expression;

    protected FacetsBuilder builder;

    public LuceneFacetConfig(Element configElement, FacetsConfig facetsConfig, Map<String, String> namespaces) throws DatabaseConfigurationException {
        this.category = configElement.getAttribute(CATEGORY_ATTR);

        final String xpath = configElement.getAttribute(XPATH_ATTR);
        if (xpath == null || xpath.isEmpty()) {
            throw new DatabaseConfigurationException("facet definition needs an attribute 'xpath': " + configElement.toString());
        }

        final StringBuilder sb = new StringBuilder();
        namespaces.forEach((prefix, uri) -> {
            if (!prefix.equals("xml")) {
                sb.append("declare namespace ").append(prefix);
                sb.append("=\"").append(uri).append("\";\n");
            }
        });
        sb.append(xpath);

        this.expression = sb.toString();

        facetsConfig.setMultiValued(category, true);

        builder = new FacetsBuilder(this);
    }

    public String getCategory() {
        return category;
    }

    public String getExpression() {
        return expression;
    }
}
