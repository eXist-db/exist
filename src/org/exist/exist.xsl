<?xml version="1.0"?>

<xsl:stylesheet
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xsp="http://apache.org/xsp"
    xmlns:exist-rpc="http://exist.sourceforge.net/exist/1.0"
    version="1.0"
>
    <xsl:variable name="namespace-uri">http://exist.sourceforge.net/exist/1.0</xsl:variable>
    <xsl:variable name="prefix">exist-rpc</xsl:variable>
    
    <xsl:template match="xsp:page">
        <xsp:page>
            <xsl:apply-templates select="@*"/>
            <xsp:structure>
                <xsp:include>org.exist.*</xsp:include>
                <xsp:include>java.net.*</xsp:include>
                <xsp:include>java.io.UnsupportedEncodingException</xsp:include>
                <xsp:include>java.util.Vector</xsp:include>
                <xsp:include>java.util.Hashtable</xsp:include>
                <xsp:include>org.w3c.dom.*</xsp:include>
                <xsp:include>org.apache.xmlrpc.*</xsp:include>
                <xsp:include>org.apache.cocoon.components.language.markup.xsp.XSPUtil</xsp:include>
            </xsp:structure>
            <xsp:logic>
                // character encoding for content returned by the server
                protected final static String _ENCODING = "ISO-8859-1";

                // url of the XMLRPC server
                protected final static String _URI = "http://localhost:8081";
             
                private int getHitCount(Hashtable _summary) {        
                    if(_summary == null) {
                        getLogger().error("no result set found. Execute query first.");
                        return -1;
                    }
                    Integer _hits = (Integer)_summary.get("hits");
                    if(_hits == null) return 0;
                    return _hits.intValue();
                  }
                  
                private int getQueryTime(Hashtable _summary) {
                    if(_summary == null) {
                        getLogger().error("no result set found. Execute query first.");
                        return -1;
                    }
                    Integer _qtime = (Integer)_summary.get("queryTime");
                    if(_qtime == null)
                        return -1;
                    return _qtime.intValue();
                }                

                private Vector getCollectionsList(String _root, 
                    Vector _result, XmlRpcClient _xmlrpc) {
                    _result.addElement(_root);
                    try {
                        Vector params = new Vector();
                        if(_root != null)
                            params.addElement(_root);
                        Hashtable _collection = 
                            (Hashtable)_xmlrpc.execute("getCollectionDesc", params);
                        Vector _subcollections = (Vector)_collection.get("collections");
                        String _sub;
                        for(int i = 0; i &lt; _subcollections.size(); i++) {
                            _sub = (String)_subcollections.elementAt(i);
                            _sub = (_root.equals("/") ? "/" + _sub 
                                : _root + "/" + _sub);
                            getCollectionsList(_sub, _result, _xmlrpc);
                        }
                        return _result;
                    } catch(Exception e) {
                        getLogger().error("getCollectionDesc failed: ", e);
                        return null;
                    }
                }
                
                private void includeXML(String xml) {
                    org.apache.cocoon.components.parser.Parser _newParser = null;
                    try {
                        _newParser =
                            (org.apache.cocoon.components.parser.Parser) 
                                this.manager.lookup(org.apache.cocoon.components.parser.Parser.ROLE);
                        InputSource __is = new InputSource(
                        new StringReader(
                                xml
                              )
                        );
        
                        XSPUtil.include(__is, this.contentHandler, _newParser);
                    } catch (Exception e) {
                        getLogger().error("Could not include page", e);
                    } finally {
                        if (_newParser != null) this.manager.release((Component) _newParser);
                    }
                }
            </xsp:logic>
            <xsl:apply-templates/>
        </xsp:page>
    </xsl:template>

    <xsl:template match="exist-rpc:connection">
        <xsl:variable name="uri">
            <xsl:choose>
              <xsl:when test="@uri">
                 <xsl:value-of select="@uri"/>
              </xsl:when>
              <xsl:otherwise>
                 http://localhost:8081
              </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsp:logic>
            {
                XmlRpcClient _xmlrpc = null;
                try {
                    _xmlrpc = new XmlRpcClientLite("<xsl:copy-of select="$uri"/>");
                } catch(Exception e) {
                    getLogger().error("error while connecting to server:" , e);
                    <error type="server">
                        <description>An error occured while connecting to the server</description>
                        <exception><xsp:expr>e.getMessage()</xsp:expr></exception>
                    </error>
                }
                <xsl:apply-templates/>
            }
        </xsp:logic>
    </xsl:template>
    
    <xsl:template match="exist-rpc:execute">
        <xsl:variable name="xpath">
            <xsl:choose>
                <xsl:when test="@xpath">
                    "<xsl:value-of select="@xpath"/>"
                </xsl:when>
                <xsl:when test="@xpath-param">
                    request.getParameter("<xsl:value-of select="@xpath-param"/>")
                </xsl:when>
                <xsl:when test="@xpath-expr">
                    <xsl:value-of select="@xpath-expr"/>
                </xsl:when>
                <xsl:otherwise>
                    null
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="result-set-id">
            <xsl:choose>
                <xsl:when test="@result-set">
                    "<xsl:value-of select="@result-set"/>"
                </xsl:when>
                <xsl:when test="@result-set-param">
                    request.getParameter("<xsl:value-of select="@result-set-param"/>")
                </xsl:when>
                <xsl:otherwise>
                    null
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
                
        <xsp:logic>            
            if(_xmlrpc == null) {
                <error type="user">
                    <message>no connection available</message>
                </error>
            }
            
            Integer _resultId = null;
            Hashtable _summary = null;
            int _hitCount = -1;
            Vector _params;
            String _rset = <xsl:copy-of select="$result-set-id"/>;
            if(_rset != null) {
                try {
                    _resultId = new Integer(Integer.parseInt(_rset));
                } catch(NumberFormatException nfe) {
                    <error type="user">
                        <message>result-set-id is not a number</message>
                    </error>
                    _resultId = null;
                }
            }
            // no result-set-id: execute query
            if(_resultId == null) {
                String _xpath = <xsl:copy-of select="$xpath"/>;
                if(_xpath != null &amp;&amp; _xpath.length() > 0) {
                    try {
                        _params = new Vector();
                        _params.addElement(_xpath.getBytes("UTF-8"));
                        _resultId = (Integer)_xmlrpc.execute("executeQuery", _params);
                    } catch(Exception e) {
                        getLogger().error("error while executing query: ", e);
                        <error type="user">
                            <message>Query execution failed</message>
                            <exception><xsp:expr>e.getMessage()</xsp:expr></exception>
                        </error>
                    }   
                } else {
                    <error type="user">
                        <message>No query argument found</message>
                    </error>
                }
            }
            // get summary and apply templates
            try {
                _params = new Vector();
                _params.addElement(_resultId);
                _summary = (Hashtable)_xmlrpc.execute("querySummary", _params);
                <xsl:apply-templates/>
            } catch(Exception e) {
                getLogger().error("error while executing query: ", e);
                <error type="user">
                    <message>Query execution failed</message>
                    <exception><xsp:expr>e.getMessage()</xsp:expr></exception>
                </error>
            }
        </xsp:logic>
    </xsl:template>
    
    <xsl:template match="exist-rpc:get-hit-count">
        <xsp:expr>getHitCount(_summary)</xsp:expr>
    </xsl:template>

    <xsl:template match="exist-rpc:result-documents">
        <xsp:logic>
            if(_summary == null) {
                <error type="user">
                    <message>no result set found</message>
                </error>
            } else {
                Vector _docs = (Vector)_summary.get("documents");
                if(_docs == null) {
                    <error type="server">
                        <message>no documents found in this result set</message>
                    </error>
                } else {
                    Vector _doc;
                    for(int _i = 0; _i &lt; _docs.size(); _i++) {
                        _doc = (Vector)_docs.elementAt(_i);
                        <xsl:apply-templates/>
                    }
                }
            }                            
        </xsp:logic>
    </xsl:template>
    
    <xsl:template match="exist-rpc:result-document-name">
        <xsp:expr>(String)_doc.elementAt(0)</xsp:expr>
    </xsl:template>

    <xsl:template match="exist-rpc:result-document-hit-count">
        <xsp:expr>(Integer)_doc.elementAt(2)</xsp:expr>
    </xsl:template>
    
    <xsl:template match="exist-rpc:get-query-time">
        <xsp:expr>getQueryTime(_summary)</xsp:expr>
    </xsl:template>

    <xsl:template match="exist-rpc:get-result-id">
        <xsp:expr>_resultId</xsp:expr>
    </xsl:template>
        
    <xsl:template match="exist-rpc:results">
        <xsl:variable name="pos">
            <xsl:choose>
                <xsl:when test="@position">
                    <xsl:value-of select="@position"/>
                </xsl:when>
                <xsl:otherwise>
                    1
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="count">
            <xsl:choose>
                <xsl:when test="@count">
                    <xsl:value-of select="@count"/>
                </xsl:when>
                <xsl:otherwise>
                    1
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsp:logic>
            if(_resultId == null) {
                getLogger().error("no result id. Execute query first.");
                <error type="user">
                    <message>No valid result set found. Please do a query first</message>
                </error>
            } else {
                int _pos = <xsl:value-of select="$pos"/>;
                int _count = <xsl:value-of select="$count"/>;
                _hitCount = getHitCount(_summary);
                if(_pos >= _hitCount) {
                    <error type="user">
                        <message>Start parameter out of range: <xsp:expr>_pos</xsp:expr></message>
                    </error>
                } else {
                    if(_pos + _count > _hitCount)
                        _count = _hitCount - _pos;
                    try {
                        for(int _i = 0; _i &lt; _count; _i++) {
                            <xsl:apply-templates/>
                        }
                    } catch(Exception e) {
                        getLogger().error("error while retrieving hit-count: ", e);
                        throw new RuntimeException("error while retrieving hit-count");
                    }
                }
            }
        </xsp:logic>
        
    </xsl:template>
    
    <xsl:template match="exist-rpc:get-xml">
        <xsl:variable name="encoding">
            <xsl:choose>
                <xsl:when test="@encoding">
                    <xsl:value-of select="@encoding"/>
                </xsl:when>
                <xsl:otherwise>UTF-8</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>    
        <xsl:variable name="as">
            <xsl:choose>
                <xsl:when test="@as">
                    "<xsl:value-of select="@as"/>"
                </xsl:when>
                <xsl:otherwise>
                    "xml"
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>        
        <xsp:logic>
            {
                _params = new Vector();
                _params.addElement(_resultId);
                _params.addElement(new Integer(_pos + _i));
                _params.addElement(new Integer(0));
                _params.addElement("<xsl:copy-of select="$encoding"/>");
                byte[] _data = (byte[])_xmlrpc.execute("retrieve", _params);
                String _xml = new String(_data, "<xsl:copy-of select="$encoding"/>");
                String _as = <xsl:copy-of select="$as"/>;
                if(_as.equals("string")) {
                    <xsp:content>
                        <xsp:expr>_xml</xsp:expr>
                    </xsp:content>
                } else
                    includeXML(_xml);
            }
        </xsp:logic>
    </xsl:template>

    <xsl:template match="exist-rpc:get-collection">
        <xsl:variable name="name">
            <xsl:choose>
                <xsl:when test="@name">
                    "<xsl:value-of select="@name"/>"
                </xsl:when>
                <xsl:when test="@expr">
                    <xsl:value-of select="@expr"/>
                </xsl:when>
                <xsl:otherwise>
                    null
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsp:logic>
            try {
                String _name = <xsl:copy-of select="$name"/>;
                Vector _params = new Vector();
                if(_name != null)
                    _params.addElement(_name);
                Hashtable _collection = 
                    (Hashtable)_xmlrpc.execute("getCollectionDesc", _params);
                Vector _subcollections = (Vector)_collection.get("collections");
                Vector _documents = (Vector)_collection.get("documents");
                <xsl:apply-templates/>
            } catch(Exception e) {
                <error type="user">
                    <description>could not get collection description</description>
                    <exception><xsp:expr>e</xsp:expr></exception>
                </error>
            }
        </xsp:logic>
    </xsl:template>
    
    <xsl:template match="exist-rpc:collection-documents">
        <xsp:logic>
            for(int _i = 0; _i &lt; _documents.size(); _i++) {
                <xsl:apply-templates/>
            }
        </xsp:logic>
    </xsl:template>
    
    <xsl:template match="exist-rpc:document-name">
        <xsp:expr>(String)_documents.elementAt(_i)</xsp:expr>
    </xsl:template>
    
    <xsl:template match="exist-rpc:collection-subcollections">
        <xsp:logic>
            for(int _i = 0; _i &lt; _subcollections.size(); _i++) {
                <xsl:apply-templates/>
            }
        </xsp:logic>
    </xsl:template>
    
    <xsl:template match="exist-rpc:subcollection-name">
        <xsp:expr>(String)_subcollections.elementAt(_i)</xsp:expr>
    </xsl:template>
    
    <xsl:template match="exist-rpc:get-document">
        <xsl:variable name="name">
            <xsl:choose>
                <xsl:when test="@name">
                    "<xsl:value-of select="@name"/>"
                </xsl:when>
                <xsl:when test="@expr">
                    <xsl:value-of select="@expr"/>
                </xsl:when>
                <xsl:otherwise>
                    null
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="collection">
            <xsl:choose>
                <xsl:when test="@collection">
                    "<xsl:value-of select="@collection"/>"
                </xsl:when>
                <xsl:otherwise>
                    null
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="encoding">
            <xsl:choose>
                <xsl:when test="@encoding">
                    <xsl:value-of select="@encoding"/>
                </xsl:when>
                <xsl:otherwise>UTF-8</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="as">
            <xsl:choose>
                <xsl:when test="@as">
                    "<xsl:value-of select="@as"/>"
                </xsl:when>
                <xsl:otherwise>
                    "xml"
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsp:logic>
            {
                if(_xmlrpc == null) {
                    <error type="user">no connection available</error>
                }
                String _name = <xsl:copy-of select="$name"/>;
                String _collection = <xsl:copy-of select="$collection"/>;
                String _encoding = "<xsl:copy-of select="$encoding"/>";
                if(_name == null) {
                    getLogger().error("no document name specified");
                    <error type="user">
                        <description>missing argument: no document name specified</description>
                    </error>
                }
                if(_collection != null)
                    _name = _collection + "/" + _name;
                
                try {                                
                    Vector _params = new Vector();
                    _params.addElement(_name);
                    _params.addElement(_encoding);
                    _params.addElement(new Integer(0));
                    byte[] _data = (byte[])_xmlrpc.execute("getDocument", _params);
                    String _xml;
                    try {
                        _xml = new String(_data, _encoding);
                    } catch(UnsupportedEncodingException uee) {
                        _xml = new String(_data);
                    }
                    String _as = <xsl:copy-of select="$as"/>;
                    if(_as.equals("string")) {
                        <xsp:content>
                            <xsp:expr>_xml</xsp:expr>
                        </xsp:content>
                    } else
                        includeXML(_xml);
                } catch(Exception e) {
                    getLogger().error("could not retrieve document: ", e);
                    <error type="user">
                        <description>could not retrieve document: <xsp:expr>_name</xsp:expr></description>
                        <exception><xsp:expr>e.getMessage()</xsp:expr></exception>
                    </error>
                }          
            }
        </xsp:logic>
    </xsl:template>

    <xsl:template match="exist-rpc:get-collection-names">
        <xsl:variable name="root">
            <xsl:choose>
                <xsl:when test="@root">
                    "<xsl:value-of select="@root"/>"
                </xsl:when>
                <xsl:otherwise>
                    null
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsp:logic>
            {
                String _root = <xsl:copy-of select="$root"/>;
                Vector _collections = new Vector();
                _collections = getCollectionsList(_root, _collections, _xmlrpc);
                if(_collections != null) {
                    <xsp:logic>
                        for(int i = 0; i &lt; _collections.size(); i++) {
                            <xsl:apply-templates/>
                        }
                    </xsp:logic>               
                }
            }
        </xsp:logic>
    </xsl:template>
    
    <xsl:template match="exist-rpc:collection-name">
        <xsp:expr>_collections.elementAt(i)</xsp:expr>
    </xsl:template>

    <xsl:template match="exist-rpc:remove-collection">
        <xsl:variable name="name">
            <xsl:choose>
                <xsl:when test="@name">
                    "<xsl:value-of select="@name"/>"
                </xsl:when>
                <xsl:when test="@expr">
                    <xsl:value-of select="@expr"/>
                </xsl:when>
                <xsl:otherwise>
                    null
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        {
            String _name = <xsl:copy-of select="$name"/>;
            if(_name == null) {
                <error type="user">
                    <description>no collection name specified!</description>
                </error>
            } else {
                try {
                    Vector _params = new Vector();
                    _params.addElement(_name);
                    _xmlrpc.execute("removeCollection", _params);
                } catch(Exception e) {
                    <error type="user">
                        <description>could not remove collection
                        <xsp:expr>_name</xsp:expr></description>
                        <exception><xsp:expr>e.getMessage()</xsp:expr></exception>
                    </error>
                }
            }
        }
    </xsl:template>

    <xsl:template match="exist-rpc:remove-document">
        <xsl:variable name="name">
            <xsl:choose>
                <xsl:when test="@name">
                    "<xsl:value-of select="@name"/>"
                </xsl:when>
                <xsl:when test="@expr">
                    <xsl:value-of select="@expr"/>
                </xsl:when>
                <xsl:otherwise>
                    null
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="collection">
            <xsl:choose>
                <xsl:when test="@collection">
                    "<xsl:value-of select="@collection"/>"
                </xsl:when>
                <xsl:otherwise>
                    null
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        {
            String _name = <xsl:copy-of select="$name"/>;
            String _collection = <xsl:copy-of select="$collection"/>;
            if(_name == null) {
                <error type="user">
                    <description>no document name specified!</description>
                </error>
            } else {
                if(_collection != null)
                    _name = (_name.equals("/") ? "/" + _name : _collection + "/" + _name);
                try {
                    Vector _params = new Vector();
                    _params.addElement(_name);
                    _xmlrpc.execute("remove", _params);
                } catch(Exception e) {
                    <error type="user">
                        <description>could not remove document
                        <xsp:expr>_name</xsp:expr></description>
                        <exception><xsp:expr>e.getMessage()</xsp:expr></exception>
                    </error>
                }
            }
        }
    </xsl:template>
    
    <xsl:template match="exist-rpc:parse">
        <xsl:variable name="content">
            <xsl:choose>
                <xsl:when test="@content">
                    "<xsl:value-of select="@content"/>"
                </xsl:when>
                <xsl:when test="@expr">
                    <xsl:value-of select="@expr"/>
                </xsl:when>
                <xsl:otherwise>
                    null
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="name">
            <xsl:choose>
                <xsl:when test="@name">
                    "<xsl:value-of select="@name"/>"
                </xsl:when>
                <xsl:when test="@name-expr">
                    <xsl:value-of select="@name-expr"/>
                </xsl:when>
                <xsl:otherwise>
                    null
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsp:logic>
            {
                String _content = <xsl:copy-of select="$content"/>;
                byte[] _data;
                try {
                   _data = _content.getBytes("UTF-8");
                } catch(UnsupportedEncodingException uee) {
                   _data = _content.getBytes();
                }
                String _name = <xsl:copy-of select="$name"/>;
                if(_content == null || _name == null) {
                    <error type="user">
                        <message>name or content parameter missing</message>
                    </error>
                } else {
                    try {
                      Vector _params = new Vector();
                      _params.addElement(_data);
                      _params.addElement(_name);
                      _params.addElement(new Integer(1));
                      _xmlrpc.execute("parse", _params);
                    } catch(Exception e) {
                        <error type="user">
                            <message>could not parse document</message>
                            <exception><xsp:expr>e</xsp:expr></exception>
                        </error>
                    }
                }
            }
        </xsp:logic>
    </xsl:template>
    
    <xsl:template match="exist-rpc:include-expr">
        <xsl:variable name="expr">
            <xsl:choose>
                <xsl:when test="@expr">
                    <xsl:value-of select="@expr"/>
                </xsl:when>
                <xsl:otherwise>
                    null
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsp:logic>
            {
                String _xml = <xsl:copy-of select="$expr"/>;
                if(_xml != null) {
                    includeXML(_xml);
                } else {
                    getLogger().error("expression is null");
                }
            }
        </xsp:logic>
    </xsl:template>
    
    <xsl:template match="@*|node()" priority="-1">
      <xsl:copy>
        <xsl:apply-templates select="@*|node()"/>
      </xsl:copy>
    </xsl:template>
    
  <!-- Utility templates -->
  <xsl:template name="get-parameter">
    <xsl:param name="name"/>
    <xsl:param name="default"/>
    <xsl:param name="required">false</xsl:param>

    <xsl:variable name="qname">
      <xsl:value-of select="concat($prefix, ':param')"/>
    </xsl:variable>

    <xsl:choose>
      <xsl:when test="@*[name(.) = $name]">"<xsl:value-of select="@*[name(.) = $name]"/>"</xsl:when>
      <xsl:when test="(*[name(.) = $qname])[@name = $name]">
        <xsl:call-template name="get-nested-content">
          <xsl:with-param name="content"
                          select="(*[name(.) = $qname])[@name = $name]"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:choose>
          <xsl:when test="string-length($default) = 0">
            <xsl:choose>
              <xsl:when test="$required = 'true'">
                <xsl:call-template name="error">
                  <xsl:with-param name="message">[Logicsheet processor]
Parameter '<xsl:value-of select="$name"/>' missing in dynamic tag &lt;<xsl:value-of select="name(.)"/>&gt;
                  </xsl:with-param>
                </xsl:call-template>
              </xsl:when>
              <xsl:otherwise>""</xsl:otherwise>
            </xsl:choose>
          </xsl:when>
          <xsl:otherwise><xsl:copy-of select="$default"/></xsl:otherwise>
        </xsl:choose>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="get-nested-content">
    <xsl:param name="content"/>
    <xsl:choose>
      <xsl:when test="$content/*">
      <xsl:apply-templates select="$content/*|$content/text()"/>
      </xsl:when>
      <xsl:otherwise>"<xsl:value-of select="$content"/>"</xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
</xsl:stylesheet>
