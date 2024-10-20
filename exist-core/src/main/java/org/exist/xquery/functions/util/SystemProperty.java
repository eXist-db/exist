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
package org.exist.xquery.functions.util;

import io.lacuna.bifurcan.IMap;
import io.lacuna.bifurcan.ISet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.ExistSystemProperties;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.AccessUtil;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.HashSet;
import java.util.Set;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.util.UtilModule.functionSignature;

/**
 * Library function to retrieve the value of a system property.
 *
 * @author Wolfgang Meier
 * @author Loren Cahlander
 * @author <a href="mailto:adam@evolvedbinary.com>Adam Retter</a>
 */
public class SystemProperty extends BasicFunction {

    private static final Logger LOGGER = LogManager.getLogger(SystemProperty.class);

    private final static String FS_AVAILABLE_SYSTEM_PROPERTIES_NAME = "available-system-properties";
    public final static FunctionSignature FS_AVAILABLE_SYSTEM_PROPERTIES = functionSignature(
            FS_AVAILABLE_SYSTEM_PROPERTIES_NAME,
            "Returns a list of available system properties. " +
                    "Predefined properties are: vendor, vendor-url, product-name, product-version, product-build, and all Java " +
                    "System Properties.",
            returnsOptMany(Type.STRING, "The names of the available system properties")
    );

    private final static String FS_SYSTEM_PROPERTY_NAME = "system-property";
    public final static FunctionSignature FS_SYSTEM_PROPERTY = functionSignature(
            FS_SYSTEM_PROPERTY_NAME,
            "Returns the value of a system property. Similar to the corresponding XSLT function. " +
                    "Predefined properties are: vendor, vendor-url, product-name, product-version, product-build, and all Java " +
                    "System Properties.",
            returnsOpt(Type.STRING, "the value of the named system property"),
            param("property-name", Type.STRING, "The name of the system property to retrieve the value of.")
    );

    public SystemProperty(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final UtilModule utilModule = (UtilModule) getParentModule();
        final IMap<String, ISet<String>> systemPropertyAccessGroups = utilModule.getSystemPropertyAccessGroups();
        final IMap<String, ISet<String>> systemPropertyAccessUsers = utilModule.getSystemPropertyAccessUsers();

        if (isCalledAs(FS_AVAILABLE_SYSTEM_PROPERTIES_NAME)) {

            final Set<String> availableProperties = new HashSet<>();
            availableProperties.addAll(ExistSystemProperties.getInstance().getAvailableExistSystemProperties());

            // add any Java System Properties that the user has access to
            for (final String systemPropertyName : context.getJavaSystemProperties().keys()) {
                if (AccessUtil.isAllowedAccess(context.getEffectiveUser(), systemPropertyAccessGroups, systemPropertyAccessUsers, systemPropertyName)) {
                    availableProperties.add(systemPropertyName);
                }
            }

            final ValueSequence result = new ValueSequence(availableProperties.size());
            for (final String availableProperty : availableProperties) {
                result.add(new StringValue(availableProperty));
            }

            return result;

        } else {
            final String systemPropertyName = args[0].getStringValue();

            // always allow access to all eXist-db System Properties
            String value = ExistSystemProperties.getInstance().getExistSystemProperty(systemPropertyName, null);
            if (value == null) {

                // check for a Java System Property that the user has access to
                if (!AccessUtil.isAllowedAccess(context.getEffectiveUser(), systemPropertyAccessGroups, systemPropertyAccessUsers, systemPropertyName)) {
                    final String txt = "Permission denied, calling user '" + context.getSubject().getName() + "' must be granted access to the Java System Property: " + systemPropertyName + ".";
                    LOGGER.error(txt);
                    return Sequence.EMPTY_SEQUENCE;
                }

                value = context.getJavaSystemProperties().get(systemPropertyName, null);
            }

            return value == null ? Sequence.EMPTY_SEQUENCE : new StringValue(value);
        }
    }
}