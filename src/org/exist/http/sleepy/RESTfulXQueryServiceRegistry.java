package org.exist.http.sleepy;

import java.util.List;
import org.exist.http.sleepy.annotations.RESTAnnotation;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.UserDefinedFunction;

/**
 *
 * @author aretter
 */


public class RESTfulXQueryServiceRegistry {
    
    private static RESTfulXQueryServiceRegistry instance = new RESTfulXQueryServiceRegistry();
    
    
    public static RESTfulXQueryServiceRegistry getInstance() {
        return instance;
    }

    public void register(XmldbURI moduleUri, UserDefinedFunction function, List<RESTAnnotation> functionRestAnnotations) {
        //TODO synchronize accces to the underlying registry
        //replace any entries for this module and function with the new annotations
    }
    
}