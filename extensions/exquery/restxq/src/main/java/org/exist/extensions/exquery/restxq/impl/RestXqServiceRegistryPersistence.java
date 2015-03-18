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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.namespace.QName;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.xquery.CompiledXQuery;
import org.exquery.ExQueryException;
import org.exquery.restxq.RestXqService;
import org.exquery.restxq.RestXqServiceRegistry;
import org.exquery.restxq.RestXqServiceRegistryListener;
import org.exquery.xquery3.FunctionSignature;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class RestXqServiceRegistryPersistence implements RestXqServiceRegistryListener {   

    public final static int REGISTRY_FILE_VERSION = 0x1;
    private final static String VERSION_LABEL = "version";
    private final static String LABEL_SEP = ": ";
    public final static String FIELD_SEP = ",";
    public final static String ARITY_SEP = "#";
    
    public final static String REGISTRY_FILENAME = "restxq.registry";
    public final static String REGISTRY_FILENAME_TMP = REGISTRY_FILENAME + ".tmp";
    
    private final Logger log = LogManager.getLogger(getClass());
    private final BrokerPool pool;
    private final RestXqServiceRegistry registry;
    
    public RestXqServiceRegistryPersistence(final BrokerPool pool, final RestXqServiceRegistry registry) {
        this.pool = pool;
        this.registry = registry;
    }
    
    private BrokerPool getBrokerPool() {
        return pool;
    }

    private RestXqServiceRegistry getRegistry() {
        return registry;
    }
    
    public void loadRegistry() {

        //only load the registry if a serialized registry exists on disk
        final File fRegistry = getRegistryFile(false);
        
        if(fRegistry != null && fRegistry.exists() && fRegistry.isFile()) {
            loadRegistry(fRegistry);
        }
    }
    
    private void loadRegistry(final File fRegistry) {
        
        log.info("Loading RESTXQ registry from: " + fRegistry.getAbsolutePath());
        
        LineNumberReader reader = null;
        DBBroker broker = null;
        try {
            reader = new LineNumberReader(new FileReader(fRegistry));
            broker = getBrokerPool().getBroker();
    
            String line = null;
            
            //read version line first
            line = reader.readLine();
            final String versionStr = line.substring(line.indexOf(VERSION_LABEL) + VERSION_LABEL.length() + LABEL_SEP.length());
            if(REGISTRY_FILE_VERSION != Integer.parseInt(versionStr)) {
                log.error("Unable to load RESTXQ registry file: " + fRegistry.getAbsolutePath() + ". Expected version: " + REGISTRY_FILE_VERSION + " but saw version: " + versionStr);
            } else {
                while((line = reader.readLine()) != null) {
                    final String xqueryLocation = line.substring(0, line.indexOf(FIELD_SEP));

                    final CompiledXQuery xquery = XQueryCompiler.compile(broker, new URI(xqueryLocation));
                    final List<RestXqService> services = XQueryInspector.findServices(xquery);

                    getRegistry().register(services);
                }
            }
        } catch(final ExQueryException eqe) {
            log.error(eqe.getMessage(), eqe);
        } catch(final URISyntaxException use) {
            log.error(use.getMessage(), use);
        } catch(final EXistException ee) {
            log.error(ee.getMessage(), ee);
        } catch(final IOException ioe) {
            log.error(ioe.getMessage(), ioe);
        } finally {
            getBrokerPool().release(broker);
            if(reader != null) {
                try { reader.close(); } catch(final IOException ioe) { log.warn(ioe.getMessage(), ioe); }
            }
        }
        
        log.info("RESTXQ registry loaded.");
    }
    
    @Override
    public void registered(final RestXqService service) {
        //TODO consider a pause before writting to disk of maybe 1 second or so
        //to allow updates to batched together i.e. when one xquery has many resource functions
        updateRegistryOnDisk(service, UpdateAction.ADD);
    }

    @Override
    public void deregistered(final RestXqService service) {
        //TODO consider a pause before writting to disk of maybe 1 second or so
        //to allow updates to batched together i.e. when one xquery has many resource functions
        updateRegistryOnDisk(service, UpdateAction.REMOVE);
    }
    
    private synchronized void updateRegistryOnDisk(final RestXqService restXqService, final UpdateAction updateAction) {
        //we can ignore the change in service provided to this function as args, as we just write the details of all
        //services to disk, overwritting the old registry
        
        final File fNewRegistry = getRegistryFile(true);
        
        if(fNewRegistry == null) {
            log.error("Could not save RESTXQ Registry to disk!");
        } else {
        
            //make sure the file doesnt exist
            if(fNewRegistry.exists()) {
                fNewRegistry.delete();
            }
            
            log.info("Updating new RESTXQ registry on disk: " + fNewRegistry.getAbsolutePath());
            
            PrintWriter writer = null;
            try {
                writer = new PrintWriter(fNewRegistry);

                writer.println(VERSION_LABEL + LABEL_SEP + REGISTRY_FILE_VERSION);
                
                //get details of RESTXQ functions in XQuery modules
                final Map<URI, List<FunctionSignature>> xqueryServices = new HashMap<URI, List<FunctionSignature>>();
                for(final RestXqService service : getRegistry()) {
                    List<FunctionSignature> fnNames = xqueryServices.get(service.getResourceFunction().getXQueryLocation());
                    if(fnNames == null) {
                        fnNames = new ArrayList<FunctionSignature>();
                    }
                    fnNames.add(service.getResourceFunction().getFunctionSignature());
                    xqueryServices.put(service.getResourceFunction().getXQueryLocation(), fnNames);
                }
                
                //iterate and save to disk
                for(final Entry<URI, List<FunctionSignature>> xqueryServiceFunctions : xqueryServices.entrySet()) {
                    writer.print(xqueryServiceFunctions.getKey() + FIELD_SEP);
                    
                    final List<FunctionSignature> fnSigs = xqueryServiceFunctions.getValue();
                    for(int i = 0; i < fnSigs.size(); i++) {
                        final FunctionSignature fnSig = fnSigs.get(i);
                        writer.print(qnameToClarkNotation(fnSig.getName()) + ARITY_SEP + fnSig.getArgumentCount());
                    }
                    writer.println();
                }
            } catch(final IOException ioe) {
                log.error(ioe.getMessage(), ioe);
            } finally {
                if(writer != null) {
                    writer.close();
                    
                    if(writer.checkError()) {
                         log.error("An error occured whilst writting the RESTXQ registry file: " + fNewRegistry);
                    }
                }
            }
            
            try {
                final File fRegistry = getRegistryFile(false);

                //replace the original reistry with the new registry
                FileUtils.deleteQuietly(fRegistry);
                FileUtils.moveFile(fNewRegistry, fRegistry);
                
                log.info("Replaced RESTXQ registry with new registry: " + fRegistry.getAbsolutePath());
                
            } catch(final IOException ioe) {
                log.error("Could not replace RESTXQ registry with updated registry: " + ioe.getMessage(), ioe);
            }
        }
    }
    
    public static String qnameToClarkNotation(final QName qname) {
        if(qname.getNamespaceURI() == null) {
            return qname.getLocalPart();
        } else {
            return "{" + qname.getNamespaceURI() + "}" + qname.getLocalPart();
        }
    }
    
    private enum UpdateAction {
        ADD,
        REMOVE;
    }
    
    private File getRegistryFile(boolean temp) {
        
        DBBroker broker = null;
        try {
            broker = getBrokerPool().getBroker();
            final Configuration configuration = broker.getConfiguration();
            final File dataDir = new File((String)configuration.getProperty(BrokerPool.PROPERTY_DATA_DIR));
            
            return new File(dataDir, temp != true ? REGISTRY_FILENAME : REGISTRY_FILENAME_TMP);
          } catch(EXistException ee) {
            log.error(ee.getMessage(), ee);
            return null;
          } finally {
            if(broker != null) {
                getBrokerPool().release(broker);
            }
        }
    }
}