package org.exist.util.serializer;

import java.io.Writer;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

public class HTML5Writer extends XHTMLWriter {

	public HTML5Writer() {
		super();
	}

	public HTML5Writer(Writer writer) {
		super(writer);
	}

	@Override
	protected void writeDoctype(String rootElement) throws TransformerException {
		if (doctypeWritten)
            return;
		
        documentType(rootElement, null, null);
        
        doctypeWritten = true;
	}
}
