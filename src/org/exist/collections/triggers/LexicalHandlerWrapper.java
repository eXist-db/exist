package org.exist.collections.triggers;

import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

/**
 *
 * @author aretter
 */


public class LexicalHandlerWrapper implements LexicalHandler {

    private final LexicalHandler output;
    
    LexicalHandlerWrapper(LexicalHandler output, DocumentTrigger trigger) {
        this.output = output;
        trigger.setLexicalOutputHandler(output);
    }

    @Override
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        output.startDTD(name, publicId, systemId);
    }

    @Override
    public void endDTD() throws SAXException {
        output.endDTD();
    }

    @Override
    public void startEntity(String name) throws SAXException {
        output.startEntity(name);
    }

    @Override
    public void endEntity(String name) throws SAXException {
        output.endEntity(name);
    }

    @Override
    public void startCDATA() throws SAXException {
        output.startCDATA();
    }

    @Override
    public void endCDATA() throws SAXException {
        output.endCDATA();
    }

    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        output.comment(ch, start, length);
    }   
}