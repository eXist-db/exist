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
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.Map.Entry;
import javax.xml.namespace.QName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.FileUtils;
import org.exist.util.io.TemporaryFileManager;
import org.exist.xquery.CompiledXQuery;
import org.exquery.ExQueryException;
import org.exquery.restxq.RestXqService;
import org.exquery.restxq.RestXqServiceRegistry;
import org.exquery.restxq.RestXqServiceRegistryListener;
import org.exquery.xquery3.FunctionSignature;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class RestXqServiceRegistryPersistence implements RestXqServiceRegistryListener {   

    public final static int REGISTRY_FILE_VERSION = 0x1;
    private final static String VERSION_LABEL = "version";
    private final static String LABEL_SEP = ": ";
    public final static String FIELD_SEP = ",";
    public final static String ARITY_SEP = "#";
    
    public final static String REGISTRY_FILENAME = "restxq.registry";
    
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
        getRegistryFile(false)
                .filter(r -> Files.exists(r))
                .filter(r -> Files.isRegularFile(r))
                .ifPresent(this::loadRegistry);
    }
    
    private void loadRegistry(final Path fRegistry) {
        
        log.info("Loading RESTXQ registry from: " + fRegistry.toAbsolutePath().toString());

        try(final LineNumberReader reader = new LineNumberReader(Files.newBufferedReader(fRegistry));
                final DBBroker broker = getBrokerPool().getBroker()) {
            
            //read version line first
            String line = reader.readLine();
            final String versionStr = line.substring(line.indexOf(VERSION_LABEL) + VERSION_LABEL.length() + LABEL_SEP.length());
            if(REGISTRY_FILE_VERSION != Integer.parseInt(versionStr)) {
                log.error("Unable to load RESTXQ registry file: " + fRegistry.toAbsolutePath().toString() + ". Expected version: " + REGISTRY_FILE_VERSION + " but saw version: " + versionStr);
            } else {
                while((line = reader.readLine()) != null) {
                    final String xqueryLocation = line.substring(0, line.indexOf(FIELD_SEP));

                    final CompiledXQuery xquery = XQueryCompiler.compile(broker, new URI(xqueryLocation));
                    final List<RestXqService> services = XQueryInspector.findServices(xquery);

                    getRegistry().register(services);
                }
            }
        } catch(final ExQueryException | IOException | EXistException | URISyntaxException eqe) {
            log.error(eqe.getMessage(), eqe);
        }

        log.info("RESTXQ registry loaded.");
    }
    
    @Override
    public void registered(final RestXqService service) {
        //TODO consider a pause before writing to disk of maybe 1 second or so
        //to allow updates to batched together i.e. when one xquery has many resource functions
        updateRegistryOnDisk(service, UpdateAction.ADD);
    }

    @Override
    public void deregistered(final RestXqService service) {
        //TODO consider a pause before writing to disk of maybe 1 second or so
        //to allow updates to batched together i.e. when one xquery has many resource functions
        updateRegistryOnDisk(service, UpdateAction.REMOVE);
    }
    
    private synchronized void updateRegistryOnDisk(final RestXqService restXqService, final UpdateAction updateAction) {
        //we can ignore the change in service provided to this function as args, as we just write the details of all
        //services to disk, overwriting the old registry
        
        final Optional<Path> optTmpNewRegistry = getRegistryFile(true);
        
        if(!optTmpNewRegistry.isPresent()) {
            log.error("Could not save RESTXQ Registry to disk!");
        } else {
            final Path tmpNewRegistry = optTmpNewRegistry.get();
            log.info("Preparing new RESTXQ registry on disk: " + tmpNewRegistry.toAbsolutePath().toString());

            try {
                try (final PrintWriter writer = new PrintWriter(Files.newBufferedWriter(tmpNewRegistry, StandardOpenOption.TRUNCATE_EXISTING))) {

                    writer.println(VERSION_LABEL + LABEL_SEP + REGISTRY_FILE_VERSION);

                    //get details of RESTXQ functions in XQuery modules
                    final Map<URI, List<FunctionSignature>> xqueryServices = new HashMap<>();
                    for (final RestXqService service : getRegistry()) {
                        List<FunctionSignature> fnNames = xqueryServices.get(service.getResourceFunction().getXQueryLocation());
                        if (fnNames == null) {
                            fnNames = new ArrayList<>();
                        }
                        fnNames.add(service.getResourceFunction().getFunctionSignature());
                        xqueryServices.put(service.getResourceFunction().getXQueryLocation(), fnNames);
                    }

                    //iterate and save to disk
                    for (final Entry<URI, List<FunctionSignature>> xqueryServiceFunctions : xqueryServices.entrySet()) {
                        writer.print(xqueryServiceFunctions.getKey() + FIELD_SEP);

                        final List<FunctionSignature> fnSigs = xqueryServiceFunctions.getValue();
                        for (final FunctionSignature fnSig : fnSigs) {
                            writer.print(qnameToClarkNotation(fnSig.getName()) + ARITY_SEP + fnSig.getArgumentCount());
                        }
                        writer.println();
                    }
                }

                final Optional<Path> optRegistry = getRegistryFile(false);
                if (optRegistry.isPresent()) {
                    final Path registry = optRegistry.get();

                    //replace the original registry with the new registry
                    final Path localTmpNewRegistry = Files.copy(tmpNewRegistry, registry.getParent().resolve(tmpNewRegistry.getFileName()));
                    Files.move(localTmpNewRegistry, registry, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

                    log.info("Replaced RESTXQ registry: " + FileUtils.fileName(tmpNewRegistry) + " -> " + FileUtils.fileName(registry));
                } else {
                    throw new IOException("Unable to retrieve existing RESTXQ registry");
                }
            } catch(final IOException ioe) {
                log.error(ioe.getMessage(), ioe);
            } finally {
                TemporaryFileManager.getInstance().returnTemporaryFile(tmpNewRegistry);
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
        REMOVE
    }
    
    private Optional<Path> getRegistryFile(final boolean temp) {
        try(final DBBroker broker = getBrokerPool().getBroker()) {
            final Configuration configuration = broker.getConfiguration();
            final Path dataDir = (Path)configuration.getProperty(BrokerPool.PROPERTY_DATA_DIR);

            final Path registryFile;
            if(temp) {
                final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
                registryFile = temporaryFileManager.getTemporaryFile();
            } else {
                registryFile = dataDir.resolve(REGISTRY_FILENAME);
            }
            return Optional.of(registryFile);
        } catch(final EXistException | IOException e) {
            log.error(e.getMessage(), e);
            return Optional.empty();
        }
    }
}