package org.exist.http.sleepy;

import org.exist.http.sleepy.annotations.RESTAnnotation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.exist.collections.triggers.FilteringTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentMetadata;
import org.exist.http.sleepy.annotations.RESTAnnotationFactory;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Annotation;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.UserDefinedFunction;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;

/**
 *
 * @author aretter
 */
public class XQueryCompilationTrigger extends FilteringTrigger {
    

    private final static String XQUERY_MIME_TYPE = "application/xquery";
    
    @Override
    public void prepare(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl existingDocument) throws TriggerException {
        
    }

    @Override
    public void finish(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl document) {
        
    }

    @Override
    public void beforeCreateDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
        
    }

    @Override
    public void afterCreateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
        
        doCompilation(broker, transaction, document);
    }

    @Override
    public void beforeUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
        
    }

    @Override
    public void afterUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
        doCompilation(broker, transaction, document);
    }

    @Override
    public void beforeCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
        
    }

    @Override
    public void afterCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
     
    }

    @Override
    public void beforeMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
     
    }

    @Override
    public void afterMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
        
    }

    @Override
    public void beforeDeleteDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
        
    }

    @Override
    public void afterDeleteDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
        
    }
    
    
    private void doCompilation(DBBroker broker, Txn transaction, DocumentImpl document) {
        
        
        if(document instanceof BinaryDocument) {
            final DocumentMetadata metadata = document.getMetadata();
            if(metadata.getMimeType().equals(XQUERY_MIME_TYPE)){
            
                //compile the query
                XQuery xquery = broker.getXQueryService();
                XQueryContext context = xquery.newContext(AccessContext.REST);

                Source source = new DBSource(broker, (BinaryDocument)document, true);

                try {
                    CompiledXQuery compiled = broker.getXQueryService().compile(context, source);
                    
                    //expire and replace anyversions in the cache with this version
                    //i.e. brokerpool.getCompiledQuery

                    /*** Typically this should be two independent classes one that does compilation
                     and another that is a trigger on compilation events - that updates the REST server
                     ***/
                    
                    //look at each function
                    Iterator<UserDefinedFunction> itFunctions = compiled.getContext().localFunctions();
                                        
                    while(itFunctions.hasNext()) {
                        UserDefinedFunction function = itFunctions.next();
                        Annotation annotations[] = function.getSignature().getAnnotations();
                        
                        List<RESTAnnotation> functionRestAnnotations = null;
                        
                        //process the function annotations
                        for(Annotation annotation : annotations) {
                            if(annotation.getName().getNamespaceURI().equals(RESTAnnotation.ANNOTATION_NS)) {
                                RESTAnnotation restAnnotation = RESTAnnotationFactory.getAnnotation(annotation);
                                if(functionRestAnnotations == null) {
                                    functionRestAnnotations = new ArrayList<RESTAnnotation>();
                                }
                                functionRestAnnotations.add(restAnnotation);
                            }
                        }
                        
                        if(functionRestAnnotations != null) {
                            RESTfulXQueryServiceRegistry.getInstance().register(document.getURI(), function, functionRestAnnotations);
                        }
                    }
                } catch(XPathException xpe) {
                    xpe.printStackTrace(); //TODO
                } catch(IOException ioe) {
                    ioe.printStackTrace(); //TODO
                } catch(PermissionDeniedException pde) {
                    pde.printStackTrace(); //TODO
                }
            }
        }
    }
}