/**
 * Package memtree implements a fast, in-memory DOM tree, which is used by eXist for the
 * nodes created inside an XQuery expression.
 * The code is an adoption of the tinytree implementation found in Michael H. Kay's
 * Saxon. The implementation should be very memory efficient: the data for every node in the document
 * node tree is stored in the document object itself, using simple arrays.
 *
 * @author Wolfgang
 * @since 0.9.3
 */
package org.exist.dom.memtree;