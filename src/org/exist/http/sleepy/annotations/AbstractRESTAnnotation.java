package org.exist.http.sleepy.annotations;

import org.exist.xquery.Annotation;

/**
 *
 * @author aretter
 */
abstract class AbstractRESTAnnotation implements RESTAnnotation {
    private final Annotation annotation;
    
    protected AbstractRESTAnnotation(Annotation annotation) {
        this.annotation = annotation;
    }
}
