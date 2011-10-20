 package org.exist.xquery.modules.cssparser;

import com.steadystate.css.parser.LexicalUnitImpl;
import com.steadystate.css.sac.DocumentHandlerExt;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.w3c.css.sac.CSSException;
import org.w3c.css.sac.ElementSelector;
import org.w3c.css.sac.InputSource;
import org.w3c.css.sac.LexicalUnit;
import org.w3c.css.sac.Locator;
import org.w3c.css.sac.SACMediaList;
import org.w3c.css.sac.Selector;
import org.w3c.css.sac.SelectorList;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

/**
 * @author Adam Retter <adam@exist-db.org>
 */
public class XMLCSSDocumentHandler implements DocumentHandlerExt {

    public final static String XMLCSS_NS = "http://exist-db.org/xmlcss/";

    private final static QName QN_SELECTOR = new QName("selector", XMLCSS_NS);
    private final static QName QN_PROPERTY = new QName("property", XMLCSS_NS);
    
    private final static String AN_ELEMENT = "element";
    private final static String AN_NAME = "name";
    private final static String AN_LINE_NUMBER = "lineNum";
    private final static String AN_COLUMN_NUMBER = "colNum";
    
    private final MemTreeBuilder builder;

    public XMLCSSDocumentHandler(MemTreeBuilder builder) {
        this.builder = builder;
    }
    
    @Override
    public void startDocument(InputSource source) throws CSSException {
        builder.startDocument();
    }

    @Override
    public void endDocument(InputSource source) throws CSSException {
        builder.endDocument();
    }

    @Override
    public void comment(String text) throws CSSException {
        builder.comment(text);
    }

    @Override
    public void ignorableAtRule(String atRule) throws CSSException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void namespaceDeclaration(String prefix, String uri) throws CSSException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void importStyle(String uri, SACMediaList media, String defaultNamespaceURI) throws CSSException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void startMedia(SACMediaList media) throws CSSException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void endMedia(SACMediaList media) throws CSSException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void startPage(String name, String pseudo_page) throws CSSException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void endPage(String name, String pseudo_page) throws CSSException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void startFontFace() throws CSSException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void endFontFace() throws CSSException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void startSelector(SelectorList selectors) throws CSSException {
        
        for(int i = 0; i < selectors.getLength(); i++) {
            Selector selector = selectors.item(i);
            Attributes attrs = getAttributesFromSelector(selector);
            
            builder.startElement(QN_SELECTOR, attrs);
        }
        
    }

    @Override
    public void endSelector(SelectorList selectors) throws CSSException {
        for(int i = 0; i < selectors.getLength(); i++) {
            builder.endElement();
        }
    }

    @Override
    public void property(String name, LexicalUnit value, boolean important) throws CSSException {
        
        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", AN_NAME, AN_NAME, "string", name);
        if(value instanceof LexicalUnitImpl) {
            Locator locator = ((LexicalUnitImpl)value).getLocator();
            attrs.addAttribute("", AN_LINE_NUMBER, AN_LINE_NUMBER, "int", String.valueOf(locator.getLineNumber()));
            attrs.addAttribute("", AN_COLUMN_NUMBER, AN_COLUMN_NUMBER, "int", String.valueOf(locator.getColumnNumber()));
        }
        
        builder.startElement(QN_PROPERTY, attrs);
        
        propertyValue(value);
        
        builder.endElement();
    }

    @Override
    public void charset(String characterEncoding) throws CSSException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private Attributes getAttributesFromSelector(Selector selector) {
        NameAndValue selectorNv = getSelectorNameAndValue(selector);
        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", selectorNv.getName(), selectorNv.getName(), "string", selectorNv.getValue());
        
        return attrs;
    }
    
    private NameAndValue getSelectorNameAndValue(Selector selector) {
        switch(selector.getSelectorType()) {
            
            case Selector.SAC_ELEMENT_NODE_SELECTOR:
                return new NameAndValue(AN_ELEMENT, ((ElementSelector)selector).getLocalName());
            
            default:
                return null;
        }
    }

    private void propertyValue(LexicalUnit value) {
        switch(value.getLexicalUnitType()) {
            
            case LexicalUnit.SAC_IDENT:
            case LexicalUnit.SAC_STRING_VALUE:
                builder.characters(value.getStringValue());
                break;
                
            default:
                //do nothing
        }
    }
    
    private class NameAndValue {
        final String name;
        final String value;

        public NameAndValue(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }
}