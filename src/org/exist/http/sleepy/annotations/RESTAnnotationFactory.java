package org.exist.http.sleepy.annotations;

import org.exist.http.sleepy.annotations.RESTAnnotation.AnnotationName;
import org.exist.xquery.Annotation;

/**
 *
 * @author aretter
 */


public class RESTAnnotationFactory {
    
    public static RESTAnnotation getAnnotation(Annotation annotation) {
        final AnnotationName an = AnnotationName.valueOf(annotation.getName());
        
        switch(an) {
            case GET:
                return new GETAnnotation(annotation);
            
            case PATH:
                return new PATHAnnotation(annotation);
                
            default:
                throw new IllegalArgumentException("Unknown annotation: " + annotation.getName().toString());
        }
    }
}
