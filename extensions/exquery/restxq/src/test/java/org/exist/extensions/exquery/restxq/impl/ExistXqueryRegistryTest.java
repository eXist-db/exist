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
package org.exist.extensions.exquery.restxq.impl;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.xmldb.XmldbURI;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for ResourceFunctionFactory
 * 
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@RunWith(ParallelRunner.class)
public class ExistXqueryRegistryTest {
    
    @Test
    public void getAbsoluteModuleHint_relative() {
        
        final String absoluteModulePath = ExistXqueryRegistry.getInstance().getAbsoluteModuleHint("b.xqm", XmldbURI.create("/db/code/a.xqm"));
        
        assertEquals("/db/code/b.xqm", absoluteModulePath);
    }
    
    @Test
    public void getAbsoluteModuleHint_relativeParent() {
        
        final String absoluteModulePath = ExistXqueryRegistry.getInstance().getAbsoluteModuleHint("../b.xqm", XmldbURI.create("/db/code/a.xqm"));
        
        assertEquals("/db/b.xqm", absoluteModulePath);
    }
    
    @Test
    public void getAbsoluteModuleHint_absoluteEmbedded() {
        
        final String absoluteModulePath = ExistXqueryRegistry.getInstance().getAbsoluteModuleHint("xmldb:exist://embedded-eXist-server/db/code/b.xqm", XmldbURI.create("/db/code/a.xqm"));
        
        assertEquals("/db/code/b.xqm", absoluteModulePath);
    }
    
    @Test
    public void getAbsoluteModuleHint_absoluteLocal() {
        
        final String absoluteModulePath = ExistXqueryRegistry.getInstance().getAbsoluteModuleHint("xmldb:exist:///db/code/b.xqm", XmldbURI.create("/db/code/a.xqm"));
        
        assertEquals("/db/code/b.xqm", absoluteModulePath);
    }
    
    @Test
    public void getAbsoluteModuleHint_absoluteSimple() {
        
        final String absoluteModulePath = ExistXqueryRegistry.getInstance().getAbsoluteModuleHint("/db/b.xqm", XmldbURI.create("/db/code/a.xqm"));
        
        assertEquals("/db/b.xqm", absoluteModulePath);
    }
}
