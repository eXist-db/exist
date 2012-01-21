package org.exist.http.sleepy.annotations;

import org.exist.dom.QName;

/**
 *
 * @author aretter
 */


public interface RESTAnnotation {
    
    public final static String ANNOTATION_NS = "http://exquery.org/ns/rest/annotation/";
    
    public enum AnnotationName {
        GET,
        HEAD,
        POST,
        PUT,
        DELETE,
        TRACE,
        OPTIONS,
        CONNECT,
        
        PATH;
        
        final QName name;
        AnnotationName() {
            this.name = new QName(name(), ANNOTATION_NS);
        }
        
        public static AnnotationName valueOf(QName name) { 
            
            for(AnnotationName an : AnnotationName.values()) {
                if(an.name.equalsSimple(name)) {
                    return an;
                }
            }
            
            throw new IllegalArgumentException("Unknown name: " + name.toString());
        }
    }
}