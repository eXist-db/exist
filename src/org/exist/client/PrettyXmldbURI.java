package org.exist.client;

import org.exist.xmldb.XmldbURI;
import org.exist.xquery.util.URIUtils;

public class PrettyXmldbURI {

	private XmldbURI target;
	
	public PrettyXmldbURI(XmldbURI target) {
		this.target=target;
	}
	
	public XmldbURI getTargetURI() {
		return target;
	}
	
	public String toString() {
		return URIUtils.urlDecodeUtf8(target.toString());
	}
}
