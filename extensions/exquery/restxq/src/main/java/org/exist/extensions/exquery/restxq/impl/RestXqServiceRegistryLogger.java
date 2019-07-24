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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exquery.restxq.RestXqService;
import org.exquery.restxq.RestXqServiceRegistryListener;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
class RestXqServiceRegistryLogger implements RestXqServiceRegistryListener {

    private final Logger log = LogManager.getLogger(getClass());
    
    @Override
    public void registered(final RestXqService service) {
        log.info("Registered RESTXQ Resource Function: " + getIdentifier(service));
    }

    @Override
    public void deregistered(final RestXqService service) {
        log.info("De-registered RESTXQ Resource Function: " + getIdentifier(service));
    }
    
    private String getIdentifier(final RestXqService service) {
        final StringBuilder builder = new StringBuilder();
        
        builder.append(service.getResourceFunction().getXQueryLocation());
        builder.append(RestXqServiceRegistryPersistence.FIELD_SEP);
        builder.append(RestXqServiceRegistryPersistence.qnameToClarkNotation(service.getResourceFunction().getFunctionSignature().getName()));
        builder.append(RestXqServiceRegistryPersistence.ARITY_SEP);
        builder.append(service.getResourceFunction().getFunctionSignature().getArgumentCount());
        
        return builder.toString();
    }
}