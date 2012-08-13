/*
Copyright (c) 2012, Adam Retter
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Adam Retter Consulting nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl.adapters;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exquery.xquery.Cardinality;
import org.exquery.xquery.FunctionArgument;
import org.exquery.xquery.Type;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class FunctionParameterSequenceTypeAdapter implements FunctionArgument {
    private String name;
    private Type primaryType;
    private Cardinality cardinality;

    protected FunctionParameterSequenceTypeAdapter() {
    }
    
    public FunctionParameterSequenceTypeAdapter(final FunctionParameterSequenceType functionParameterSequenceType) {
        this.name = functionParameterSequenceType.getAttributeName();
        this.primaryType = TypeAdapter.toExQueryType(functionParameterSequenceType.getPrimaryType());
        this.cardinality = CardinalityAdapter.getCardinality(functionParameterSequenceType.getCardinality());
    }
    
    @Override
    public String getName() {
        return name;
    }

    private void setName(final String name) {
        this.name = name;
    }
    
    @Override
    public Type getType() {
        return primaryType;
    }
    
    private void setPrimaryType(final Type primaryType) {
        this.primaryType = primaryType;
    }

    @Override
    public Cardinality getCardinality() {
        return cardinality;
    }
    
    private void setCardinality(final Cardinality cardinality) {
        this.cardinality = cardinality;
    }
}