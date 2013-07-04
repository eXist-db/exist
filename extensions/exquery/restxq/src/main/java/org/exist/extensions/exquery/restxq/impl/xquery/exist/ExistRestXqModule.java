/*
Copyright (c) 2013, Adam Retter
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
package org.exist.extensions.exquery.restxq.impl.xquery.exist;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exquery.restxq.Namespace;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com?
 */
public class ExistRestXqModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = Namespace.ANNOTATION_NS + "/exist";
    public final static String PREFIX = "ex" + Namespace.ANNOTATION_PREFIX;
    public final static String RELEASED_IN_VERSION = "2.1";
    
    private final static FunctionDef[] signatures = {
        new FunctionDef(RegistryFunctions.FNS_REGISTER_MODULE, RegistryFunctions.class),
        new FunctionDef(RegistryFunctions.FNS_DEREGISTER_MODULE, RegistryFunctions.class),
        new FunctionDef(RegistryFunctions.FNS_FIND_RESOURCE_FUNCTIONS, RegistryFunctions.class),
        new FunctionDef(RegistryFunctions.FNS_REGISTER_RESOURCE_FUNCTION, RegistryFunctions.class),
        new FunctionDef(RegistryFunctions.FNS_DEREGISTER_RESOURCE_FUNCTION, RegistryFunctions.class)
    };
    
    public ExistRestXqModule(final Map<String, List<? extends Object>> parameters) {
        super(signatures, parameters);
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
        return "eXist specific extension functions for RESTXQ";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
