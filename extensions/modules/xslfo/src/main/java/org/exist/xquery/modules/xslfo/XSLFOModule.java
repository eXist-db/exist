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
package org.exist.xquery.modules.xslfo;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDSL;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;

/**
 * @author <a href="mailto:craiggoodyer@gmail.com">Craig Goodyer</a>
 * @author <a href="mailto:adam.retter@devon.gov.uk">Adam Retter</a>
 * @author ljo
 */
public class XSLFOModule extends AbstractInternalModule {

    private static final Logger logger = LogManager.getLogger(XSLFOModule.class);

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/xslfo";
    public final static String PREFIX = "xslfo";
    public final static String INCLUSION_DATE = "2007-10-04";
    public final static String RELEASED_IN_VERSION = "eXist-1.2";

    private final static FunctionDef[] functions = {
            new FunctionDef(RenderFunction.signatures[0], RenderFunction.class),
            new FunctionDef(RenderFunction.signatures[1], RenderFunction.class)
    };

    public XSLFOModule(final Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "A module for performing XSL-FO transformations";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

    private ProcessorAdapter adapter = null;

    public synchronized ProcessorAdapter getProcessorAdapter() {

        if (adapter == null) {
            final List<String> processorAdapterParamList = (List<String>) getParameter("processorAdapter");
            if (!processorAdapterParamList.isEmpty()) {
                final String processorAdapter = processorAdapterParamList.getFirst();

                try {
                    final Class<ProcessorAdapter> clazzAdapter = (Class<ProcessorAdapter>) Class.forName(processorAdapter);
                    adapter = clazzAdapter.getDeclaredConstructor().newInstance();
                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException |
                         NoSuchMethodException | InvocationTargetException cnfe) {
                    logger.error("Unable to instantiate FO Processor Adapter:{}", cnfe.getMessage(), cnfe);
                }
            }
        }
        return adapter;
    }

    static FunctionSignature[] functionSignatures(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType[][] variableParamTypes) {
        return FunctionDSL.functionSignatures(new QName(name, NAMESPACE_URI, PREFIX), description, returnType, variableParamTypes);
    }
}