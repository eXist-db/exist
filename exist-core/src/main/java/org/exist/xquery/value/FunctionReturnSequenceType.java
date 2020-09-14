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
package org.exist.xquery.value;

import org.exist.xquery.Cardinality;

/**
 * This class is used to specify the name and description of an XQuery function parameter.
 *
 * @author lcahlander
 * @version 1.3
 */
public class FunctionReturnSequenceType extends SequenceType {

    private String description = null;

    /**
     * @param primaryType The <strong>Type</strong> of the parameter.
     * @param cardinality The <strong>Cardinality</strong> of the parameter.
     * @param description A description of the parameter in the <strong>FunctionSignature</strong>.
     * @see org.exist.xquery.FunctionSignature @see Type @see org.exist.xquery.Cardinality
     */
    public FunctionReturnSequenceType(final int primaryType, final Cardinality cardinality, final String description) {
        super(primaryType, cardinality);
        this.description = description;
    }

    /**
     * @param primaryType The <strong>Type</strong> of the parameter.
     * @param cardinality The <strong>Cardinality</strong> of the parameter.
     * @param description A description of the parameter in the <strong>FunctionSignature</strong>.
     * @see org.exist.xquery.FunctionSignature @see Type @see org.exist.xquery.Cardinality
     *
     * @deprecated Use {@link #FunctionReturnSequenceType(int, Cardinality, String)}
     */
    @Deprecated
    public FunctionReturnSequenceType(final int primaryType, final int cardinality, final String description) {
        super(primaryType, Cardinality.fromInt(cardinality));
        this.description = description;
    }

    public FunctionReturnSequenceType() {
        super();
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(final String description) {
        this.description = description;
    }


}
