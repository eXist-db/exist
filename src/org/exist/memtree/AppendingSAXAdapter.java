package org.exist.memtree;

import org.xml.sax.SAXException;

/**
 *
 * @author aretter
 */
public class AppendingSAXAdapter extends SAXAdapter {

    public AppendingSAXAdapter(MemTreeBuilder builder) {
        setBuilder(builder);
    }

    @Override
    public void endDocument() throws SAXException {
        //do nothing
    }

    @Override
    public void startDocument() throws SAXException {
        //do nothing
    }
}
