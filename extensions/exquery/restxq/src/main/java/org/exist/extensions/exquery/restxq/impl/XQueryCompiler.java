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

import java.io.IOException;
import java.net.URI;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentMetadata;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
class XQueryCompiler {
    
    public final static String XQUERY_MIME_TYPE = "application/xquery";
    
    public static CompiledXQuery compile(final DBBroker broker, final URI xqueryLocation) throws RestXqServiceCompilationException {
        
        try {
            final DocumentImpl document = broker.getResource(XmldbURI.create(xqueryLocation), Permission.READ);
            if(document != null) {
                return compile(broker, document);
            } else {
                throw new RestXqServiceCompilationException("Invalid document location for XQuery: " + xqueryLocation.toString());
            }
        } catch(PermissionDeniedException pde) {
            throw new RestXqServiceCompilationException("Permission to access XQuery denied: "  + xqueryLocation.toString() + ": " + pde.getMessage(), pde);
        }
    }
    
    public static CompiledXQuery compile(final DBBroker broker, final DocumentImpl document) throws RestXqServiceCompilationException {
        
        try {
            if(document instanceof BinaryDocument) {
                final DocumentMetadata metadata = document.getMetadata();
                if(metadata.getMimeType().equals(XQUERY_MIME_TYPE)){
            
                    //compile the query
                    final XQueryContext context = new XQueryContext(broker.getBrokerPool());
                    final Source source = new DBSource(broker, (BinaryDocument)document, true);

                    //set the module load path for any module imports that are relative
                    context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI_PREFIX + ((XmldbURI)source.getKey()).removeLastSegment());
                    
                    return broker.getBrokerPool().getXQueryService().compile(broker, context, source);
                } else {
                    throw new RestXqServiceCompilationException("Invalid mimetype '" +  metadata.getMimeType() + "' for XQuery: "  + document.getURI().toString());
                }
            } else {
                throw new RestXqServiceCompilationException("Invalid document location for XQuery: " + document.getURI().toString());
            }
        } catch(XPathException xpe) {
            throw new RestXqServiceCompilationException("Unable to compile XQuery: "   + document.getURI().toString() + ": " + xpe.getMessage(), xpe);
        } catch(IOException ioe) {
            throw new RestXqServiceCompilationException("Unable to access XQuery: "  + document.getURI().toString() + ": " + ioe.getMessage(), ioe);
        } catch(PermissionDeniedException pde) {
            throw new RestXqServiceCompilationException("Permission to access XQuery denied: "  + document.getURI().toString() + ": " + pde.getMessage(), pde);
        }
    }
}