/*
 * Atom.java
 *
 * Created on June 14, 2006, 10:28 PM
 *
 * (C) R. Alexander Milowski alex@milowski.com
 */

package org.exist.atom;

import java.net.URI;

import org.exist.dom.QName;

/**
 *
 * @author R. Alexander Milowski
 */
public interface Atom {
   String MIME_TYPE = "application/atom+xml";
   URI NAMESPACE = URI.create("http://www.w3.org/2005/Atom");
   String NAMESPACE_STRING = NAMESPACE.toString();
   QName FEED = new QName("feed",NAMESPACE_STRING,"atom");
   QName ENTRY = new QName("entry",NAMESPACE_STRING,"atom");
   QName TITLE = new QName("title",NAMESPACE_STRING,"atom");
   QName UPDATED = new QName("updated",NAMESPACE_STRING,"atom");
   QName PUBLISHED = new QName("published",NAMESPACE_STRING,"atom");
   QName SUMMARY = new QName("summary",NAMESPACE_STRING,"atom");
   
}
