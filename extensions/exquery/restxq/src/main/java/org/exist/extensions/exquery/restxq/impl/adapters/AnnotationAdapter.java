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

import javax.xml.namespace.QName;
import org.exist.xquery.LiteralValue;
import org.exquery.xquery.Literal;
import org.exquery.xquery3.Annotation;
import org.exquery.xquery3.FunctionSignature;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class AnnotationAdapter implements Annotation {

    private QName name;
    private LiteralValueAdapter literals[];
    private FunctionSignatureAdapter functionSignature;
    
    public AnnotationAdapter() {
    }
    
    public AnnotationAdapter(final org.exist.xquery.Annotation annotation) {
        this(annotation, new FunctionSignatureAdapter(annotation.getFunctionSignature()));
    }

    AnnotationAdapter(final org.exist.xquery.Annotation annotation, final FunctionSignatureAdapter functionSignatureAdapter) {
        this.name = annotation.getName().toJavaQName();
        
        final LiteralValue literalValues[] = annotation.getValue();
        this.literals = new LiteralValueAdapter[literalValues.length];
        for(int i = 0; i < literalValues.length; i++) {
            literals[i] = new LiteralValueAdapter(literalValues[i]);
        }
        
        this.functionSignature = functionSignatureAdapter;
    }

    @Override
    public QName getName() {
        return name;
    }
    
    private void setName(final QName name) {
        this.name = name;
    }

    @Override
    public Literal[] getLiterals() {
        return literals;
    }

    @Override
    public FunctionSignature getFunctionSignature() {
        return functionSignature;
    }
}