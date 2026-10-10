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

import org.exist.dom.QName;
import org.exist.util.SchemaVersion;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * Reads the {@code xs:schema/@version} of one of eXist's native config-file
 * XSDs (shipped under {@code $EXIST_HOME/schema/}) from the
 * {@link SchemaVersion} constants generated at build time — no disk access.
 */
public class GetConfigurationSchemaVersion extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("get-configuration-schema-version", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
            "Returns the xs:schema/@version of a native eXist config-file schema, by name: 'conf', " +
            "'collection.xconf', 'descriptor', 'mime-types', or 'controller-config'. Read from " +
            "build-time constants, not disk. This function is only available to the DBA role.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("schema", Type.STRING, Cardinality.EXACTLY_ONE,
                            "the schema name: 'conf', 'collection.xconf', 'descriptor', 'mime-types', or 'controller-config'")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the schema's version string"));

    public GetConfigurationSchemaVersion(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (!context.getSubject().hasDbaRole()) {
            throw new XPathException(this, "Only a DBA can call system:get-configuration-schema-version()");
        }

        final String schema = args[0].getStringValue();
        final String version = switch (schema) {
            case "conf" -> SchemaVersion.CONF;
            case "collection.xconf" -> SchemaVersion.COLLECTION_XCONF;
            case "descriptor" -> SchemaVersion.DESCRIPTOR;
            case "mime-types" -> SchemaVersion.MIME_TYPES;
            case "controller-config" -> SchemaVersion.CONTROLLER_CONFIG;
            default -> throw new XPathException(this, ErrorCodes.FOER0000,
                    "Unknown schema '" + schema + "'; expected one of 'conf', 'collection.xconf', 'descriptor', " +
                    "'mime-types', 'controller-config'");
        };
        return new StringValue(this, version);
    }
}
