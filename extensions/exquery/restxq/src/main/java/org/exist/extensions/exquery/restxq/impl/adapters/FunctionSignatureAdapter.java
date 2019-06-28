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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exquery.xquery.FunctionArgument;
import org.exquery.xquery3.Annotation;
import org.exquery.xquery3.FunctionSignature;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
class FunctionSignatureAdapter implements FunctionSignature {
    private QName name;
    private int argumentCount;
    private FunctionParameterSequenceTypeAdapter[] arguments;
    private AnnotationAdapter[] annotations;
    
    protected FunctionSignatureAdapter() {
    }
    
    public FunctionSignatureAdapter(final org.exist.xquery.FunctionSignature functionSignature) {
        this.name = functionSignature.getName().toJavaQName();
        this.argumentCount = functionSignature.getArgumentCount();
        
        final SequenceType[] fnArgumentTypes = functionSignature.getArgumentTypes();
        if(fnArgumentTypes != null) {
            arguments = new FunctionParameterSequenceTypeAdapter[fnArgumentTypes.length];
            for(int i = 0; i < fnArgumentTypes.length; i++) {
                arguments[i] = new FunctionParameterSequenceTypeAdapter((FunctionParameterSequenceType)fnArgumentTypes[i]);
            }
        } else {
            arguments = null;
        }
        
        final org.exist.xquery.Annotation[] fnAnnotations = functionSignature.getAnnotations();
        if(fnAnnotations != null) {
            this.annotations = new AnnotationAdapter[fnAnnotations.length];
            for(int i = 0; i < fnAnnotations.length; i++) {
                this.annotations[i] = new AnnotationAdapter(fnAnnotations[i], this);
            }
        } else {
            this.annotations = null;
        }
    }

    @Override
    public String toString() {
        String str;
        if(name.getPrefix() != null) {
            str = name.getPrefix() + ":" + name.getLocalPart();
        } else {
            str = name.toString(); //clark-notation
        }
        str += "#" + getArgumentCount();
        
        return str;
    }
    

    @Override
    public QName getName() {
        return name;
    }
    
    private void setName(final QName name) {
        this.name = name;
    }

    @Override
    public int getArgumentCount() {
        return argumentCount;
    }
    
    private void setArgumentCount(int argumentCount) {
        this.argumentCount = argumentCount;
    }
    
    @Override
    public Annotation[] getAnnotations() {
        return annotations;
    }
    
    private void setAnnotations(AnnotationAdapter[] annotations) {
        this.annotations = annotations;
    }

    @Override
    public FunctionArgument[] getArguments() {
        return arguments;
    }
    
    private void setParameters(final FunctionParameterSequenceTypeAdapter[] parameters) {
        this.arguments = parameters;
    }
}