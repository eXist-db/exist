<?xml version="1.0" encoding="UTF-8"?>

<!-- XSP logicsheet for eXist. This logicsheet is based on the XML:DB
     API. It should basically work with other database products implementing
     the API, however it has only been tested with eXist.
-->
<xsl:stylesheet
	version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xsp="http://apache.org/xsp"
	xmlns:xmldb="http://exist-db.org/xmldb/1.0"
    xmlns:xsp-session="http://apache.org/xsp/session/2.0"
    xsp-session:create-session="true"
>
    <xsl:variable name="namespace-uri">http://exist-db.org/xmldb/1.0</xsl:variable>
    <xsl:variable name="namespace-prefix">xdb</xsl:variable>
	
    <xsl:template match="xsp:page">
        <xsp:page>
          	<xsl:apply-templates select="@*"/>
            <xsp:structure>
                <xsp:include>java.net.*</xsp:include>
                <xsp:include>java.io.UnsupportedEncodingException</xsp:include>
                <xsp:include>java.util.Vector</xsp:include>
                <xsp:include>java.util.Hashtable</xsp:include>
                <xsp:include>java.util.ArrayList</xsp:include>
                <xsp:include>java.util.TreeMap</xsp:include>
                <xsp:include>java.util.Iterator</xsp:include>
                <xsp:include>org.w3c.dom.*</xsp:include>
                <xsp:include>org.xmldb.api.*</xsp:include>
                <xsp:include>org.xmldb.api.base.*</xsp:include>
                <xsp:include>org.xmldb.api.modules.*</xsp:include>
                <xsp:include>org.apache.cocoon.components.language.markup.xsp.XSPUtil</xsp:include>
                <xsp:include>org.exist.util.IncludeXMLFilter</xsp:include>
                <xsp:include>org.exist.storage.serializers.EXistOutputKeys</xsp:include>
                <xsp:include>javax.xml.transform.OutputKeys</xsp:include>
                <xsp:include>org.exist.xmldb.UserManagementService</xsp:include>
                <xsp:include>org.exist.security.User</xsp:include>
                <xsp:include>org.exist.storage.serializers.Serializer</xsp:include>
                <xsp:include>org.apache.excalibur.xml.sax.SAXParser</xsp:include>
            </xsp:structure>
            <xsp:logic>
                private String _errmsg = null;
                private String _errdesc = null;
                    
                private  ArrayList getHitsByDocument(String _collectionName, 
                    String _documentName, TreeMap _collections) {
                    TreeMap _documents = (TreeMap) _collections.get(_collectionName);
                    if(_documents == null) 
                        return new ArrayList();
                    else {
                        ArrayList _hitsByDoc = (ArrayList) _documents.get(_documentName);
                        return (_hitsByDoc == null ? new ArrayList() : _hitsByDoc); 
                    } 
                }
            </xsp:logic>
            <xsl:apply-templates/>
        </xsp:page>
    </xsl:template>

    <!-- Register a driver class with the XML:DB DatabaseManager
     @param class the name of the driver class. 
        Default: org.exist.xmldb.LocalDatabase
    -->
    <xsl:template match="xmldb:driver">
        <xsl:variable name="classname">
            <xsl:choose>
                <xsl:when test="@class">
                    "<xsl:value-of select="@class"/>"
                </xsl:when>
                <xsl:otherwise>
                    "org.exist.xmldb.LocalDatabase"
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsp:logic>
            try {
                Class cl = Class.forName(<xsl:value-of select="$classname"/>);
                Database database = (Database)cl.newInstance();
                DatabaseManager.registerDatabase(database);
                getLogger().info("database " + database + " registered");
            } catch(Exception _e) {
                getLogger().error("unable to initialize driver" , _e);
                _errmsg = _e.getMessage();
                _errdesc = "driver initialization failed: " + <xsl:value-of select="$classname"/>;
                <xsl:apply-templates select="xmldb:error/node()"/>
            }
        </xsp:logic>
    </xsl:template>

    <!-- Retrieve a collection object from the database.
    
        As required by the XML:DB API, every action has to be enclosed
        in an xmldb:collection element. All child elements operate on this
        collection.
        
        @param uri The fully qualified URI of the collection.
    -->
    <xsl:template match="xmldb:collection[not(ancestor::xmldb:results)]">
        <xsl:variable name="uri">
            <xsl:call-template name="get-parameter">
                <xsl:with-param name="name">uri</xsl:with-param>
                <xsl:with-param name="required">true</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="user">
            <xsl:call-template name="get-parameter">
                <xsl:with-param name="name">user</xsl:with-param>
                <xsl:with-param name="default" select="guest"/>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="password">
            <xsl:call-template name="get-parameter">
                <xsl:with-param name="name">password</xsl:with-param>
                <xsl:with-param name="default" select="null"/>
            </xsl:call-template>
        </xsl:variable>
        <xsp:logic>
            {
                String _user = <xsl:value-of select="$user"/>;
                String _password = <xsl:value-of select="$password"/>;
                Collection collection = null;
                // create a cocoon parser component for later use
                SAXParser
                    newParser = null;
                try {
                	newParser = (SAXParser) manager.lookup(SAXParser.ROLE);
                } catch(Exception e) {
                }
                try {
                    collection =
                        DatabaseManager.getCollection(<xsl:value-of select="$uri"/>,
                            _user, _password);
                    collection.setProperty("sax-document-events", "false");
                    String[] _childCollections = collection.listChildCollections();
                    String[] _resources = collection.listResources();
                    <xsl:apply-templates/>
                } catch(XMLDBException _e) {
                    _errmsg = _e.getMessage();
                    _errdesc = "Failed to get collection " + <xsl:value-of select="$uri"/>;
                    <xsl:apply-templates select="xmldb:error/node()"/>
                }
            }
        </xsp:logic>
    </xsl:template>

    <!-- Returns the current collection object.
    
        @return the current collection object
    -->
    <xsl:template match="xmldb:collection//xmldb:get-collection">
	<xsp:expr>(collection)</xsp:expr>
    </xsl:template>

    <!-- Execute a XPath query on the current collection.
    
        @param xpath the XPath expression to execute.
        @param encoding the character encoding used by enclosed tags.
            Default: ISO-8859-1.
    -->
    <xsl:template match="xmldb:collection//xmldb:execute">
        <xsl:variable name="xpath">
            <xsl:call-template name="get-parameter">
		    <xsl:with-param name="name">xpath</xsl:with-param>
                <xsl:with-param name="required">true</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="encoding">
            <xsl:call-template name="get-parameter">
                <xsl:with-param name="name">encoding</xsl:with-param>
                <xsl:with-param name="default" select="ISO-8859-1"/>
            </xsl:call-template>
        </xsl:variable>
        <xsp:logic>
            try {
                String _query = <xsl:value-of select="$xpath"/>;
                int _hits = 0;
                long _queryTime = System.currentTimeMillis();
                ResourceSet _result;
                if((_result = (ResourceSet)session.getAttribute(_query)) == null) {
                    XPathQueryService _service =
                        ( XPathQueryService ) collection.getService( "XPathQueryService", "1.0" );
                    _service.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, "elements");
                    _service.setProperty(OutputKeys.INDENT, "no");
                    _service.setProperty("sax-document-events", "false");
                    <xsl:for-each select="./xmldb:namespace">
                    	<xsp:logic>
                    		_service.setNamespace("<xsl:value-of select="@prefix"/>", "<xsl:value-of select="."/>");
                    	</xsp:logic>
                    </xsl:for-each>
                    _result = _service.query(_query);
                    if(_result != null)
                        session.setAttribute(_query, _result);
                }
                _hits = (_result == null ? 0 : (int) _result.getSize());
                _queryTime = System.currentTimeMillis() - _queryTime;
                TreeMap _collections = new TreeMap();
                TreeMap _documents;
                ArrayList _hitsByDoc;
                XMLResource _resource;
                Collection _currentCollection;
                for(int _i = 0; _i &lt; _hits; _i++) {
                    _resource = (XMLResource)_result.getResource( ( long ) _i );
                    _currentCollection = _resource.getParentCollection();
                    if(_currentCollection == null)
                    	break;
                    if((_documents = (TreeMap)_collections.get(_currentCollection.getName())) == null) {
                        _documents = new TreeMap();
                        _collections.put(_currentCollection.getName(), _documents);
                    }
                    if((_hitsByDoc = (ArrayList)_documents.get(_resource.getDocumentId())) == null) {
                        _hitsByDoc = new ArrayList();
                        _documents.put(_resource.getDocumentId(), _hitsByDoc);
                    }
                    _hitsByDoc.add(_resource);
                }
                <xsl:apply-templates/>
            } catch(XMLDBException _xe) {
                _errmsg = _xe.getMessage();
                _errdesc = "Query execution failed. Query: " + <xsl:value-of select="$xpath"/>;
                <xsl:apply-templates select="xmldb:error/node()"/>
            }
        </xsp:logic>
    </xsl:template>
    
    <!-- Returns the query execution time needed to process the query.
    
        @return query execution time in ms.
        @see #"xmldb:collection//xmldb:execute"
    -->
    <xsl:template match="xmldb:execute//xmldb:get-query-time">
        <xsp:expr>_queryTime</xsp:expr>
    </xsl:template>
    
    <!-- Create a summary of hits by collection and start 
        iterating over it. 
        
        @see #"xmldb:collection//xmldb:execute"
    -->
    <xsl:template match="xmldb:execute//xmldb:result-summary">
        <xsp:logic>
            String _collectionName;
            for(Iterator _i = _collections.keySet().iterator(); _i.hasNext(); ) {
                _collectionName = (String)_i.next();
                <xsl:apply-templates/>
            }
        </xsp:logic>
    </xsl:template>
    
    <!-- Get the name of the collection which is currently processed by
        the result-summary iterator.
    
        @return the name of the current collection.
        @see #"xmldb:execute//xmldb:result-summary"
    -->
    <xsl:template match="xmldb:result-summary//xmldb:collection-name">
        <xsp:expr>_collectionName</xsp:expr>
    </xsl:template>
    
    <!-- Get the documents for which hits have been found in the 
    collection currently selected by the result-summary iterator. 
    
    @return Iterator over all documents in the current collection.
    @see #"xmldb:execute//xmldb:result-summary" 
    -->
    <xsl:template match="xmldb:result-summary//xmldb:documents">
        <xsp:logic>
            _documents = (TreeMap) _collections.get(_collectionName);
            String _documentName;
            for(Iterator _j = _documents.keySet().iterator(); _j.hasNext(); ) {
                _documentName = (String)_j.next();
                _hitsByDoc = (ArrayList) _documents.get(_documentName);
                <xsl:apply-templates/>
            }
        </xsp:logic>
    </xsl:template>
    
    <!-- Get the number of hits found for the document currently selected
    by the xmldb:documents iterator 
    @see #"xmldb:result-summary//xmldb:documents"
    -->
    <xsl:template match="xmldb:documents//xmldb:document-hit-count">
        <xsp:expr>_hitsByDoc.size()</xsp:expr>
    </xsl:template>
    
    <!-- Get the name of the document currently selected
    by the xmldb:documents iterator 
    
    @see #"xmldb:result-summary//xmldb:documents"
    -->
    <xsl:template match="xmldb:documents//xmldb:document-name">
        <xsp:expr>_documentName</xsp:expr>
    </xsl:template>
    
    <!-- Iterate over the result set items in the current result set. Select
    only those items which belong to the specified document or collection.
    
    @param pos the position of the first result set item to select.
    @param count the number of items to iterate over, beginning at pos.
    @param document the name of the document for which results should be
        retrieved. If null, all documents in the collection will be selected.
    @param collection the name of the collection for which results should
        be retrieved.
    @see #"xmldb:execute//xmldb:results[not(@document|xmldb:document)]"
    -->
    <xsl:template match="xmldb:execute//xmldb:results[@document|xmldb:document]">
        <xsl:variable name="pos">
            <xsl:call-template name="get-parameter">
		    <xsl:with-param name="name">position</xsl:with-param>
                <xsl:with-param name="default">1</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="count">
            <xsl:call-template name="get-parameter">
		    <xsl:with-param name="name">count</xsl:with-param>
                <xsl:with-param name="default">10</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="document">
            <xsl:call-template name="get-parameter">
		    <xsl:with-param name="name">document</xsl:with-param>
                <xsl:with-param name="default">null</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="collection">
            <xsl:call-template name="get-parameter">
		    <xsl:with-param name="name">collection</xsl:with-param>
                <xsl:with-param name="required">true</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsp:logic>
            int _pos = Integer.parseInt(<xsl:value-of select="$pos"/>) ;
            int _count = Integer.parseInt(<xsl:value-of select="$count"/>);
            String _documentName = <xsl:value-of select="$document"/>;
            String _collectionName = <xsl:value-of select="$collection"/>;
            _documents = (TreeMap) _collections.get(_collectionName);
            _hitsByDoc = getHitsByDocument(_collectionName, _documentName,
                _collections);
            _hits = _hitsByDoc.size();
            if(_pos &lt; 1 || _pos > _hits || _hits == 0) {
                _errmsg = "index out of bound: " + _pos + "(" + _hits + ")";
                <xsl:apply-templates select="ancestor::xmldb:execute//xmldb:error/node()"/>
            } else {
                if(_pos + _count &gt; _hits) 
                    _count = _hits - _pos + 1;
                for(int _i = _pos; _i &lt; _pos + _count; ++_i) {
                    _resource = (XMLResource)_hitsByDoc.get(_i - 1);
                    if(_resource == null)
                        continue;
                    <xsl:apply-templates/>
                }
            }
        </xsp:logic>
    </xsl:template>
    
    <!-- Iterate over the result set items in the current result set.
    
    @param pos the position of the first result set item to select.
    @param count the number of items to iterate over, beginning at pos.
    @param document the name of the document for which results should be
        retrieved. If null, all documents in the collection will be selected.
    @param collection the name of the collection for which results should
        be retrieved.
        
    @see #"xmldb:execute//xmldb:results[@document|xmldb:document]"
    -->
    <xsl:template match="xmldb:execute//xmldb:results[not(@document|xmldb:document)]">
        <xsl:variable name="pos">
            <xsl:call-template name="get-parameter">
		    <xsl:with-param name="name">position</xsl:with-param>
                <xsl:with-param name="default">1</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="count">
            <xsl:call-template name="get-parameter">
		    <xsl:with-param name="name">count</xsl:with-param>
                <xsl:with-param name="default">10</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsp:logic>
            int _pos = Integer.parseInt(<xsl:value-of select="$pos"/>) - 1;
            int _count = Integer.parseInt(<xsl:value-of select="$count"/>);
            if(_result == null || _pos &lt; 0 || _pos &gt; _result.getSize()) {
                _errmsg = "start parameter out of range";
                <xsl:apply-templates select="ancestor::xmldb:execute//xmldb:error/node()"/>
            } else {
                if(_pos + _count &gt;= _result.getSize()) 
                    _count = (int)_result.getSize() - _pos;
                for(int _i = _pos; _i &lt; _pos + _count; ++_i) {
                    _resource = (XMLResource)_result.getResource( ( long ) _i );
                    if(_resource == null)
                        continue;
                    <xsl:apply-templates/>
                }
                getLogger().info("serialized results " + _pos + " to " + _count);
            }
        </xsp:logic>
     </xsl:template>
     
    <xsl:template match="xmldb:results//xmldb:document-name">
            <xsp:expr>_resource.getDocumentId()</xsp:expr>
    </xsl:template>
    
    <xsl:template match="xmldb:results//xmldb:parent-collection">
        <xsp:expr>_resource.getParentCollection()</xsp:expr>
    </xsl:template>
    
    <!-- Retrieve a result set item from the current result set. The
        item is inserted into the document at the current position. 
        
        @param as if set to "string", the item will be included as
            string value, which means that the XML markup is escaped.
            If set to "xml", the item will be inserted into the current
            SAX stream processed by Cocoon. This allows to post-process the
            generated results.
        @see #"xmldb:execute//xmldb:results[not(@document|xmldb:document)]"
        @see #"xmldb:execute//xmldb:results[@document|xmldb:document]"
    -->
     <xsl:template match="xmldb:results//xmldb:get-xml">
        <xsl:variable name="as">
            <xsl:call-template name="get-parameter">
		    <xsl:with-param name="name">as</xsl:with-param>
                <xsl:with-param name="default">xml</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsp:logic>
	        if(<xsl:value-of select="$as"/>.equals("xml")) {
                if(_resource instanceof org.exist.xmldb.RemoteXMLResource)
                    ((org.exist.xmldb.RemoteXMLResource)_resource).setXMLReader(new org.exist.cocoon.XMLReaderWrapper(newParser));
                IncludeXMLFilter _consumer = 
                    new IncludeXMLFilter(this.contentHandler);
                _resource.getContentAsSAX(_consumer);
		    } else {
                <xsp:content><xsp:expr>(String)_resource.getContent()</xsp:expr></xsp:content>
		    }
        </xsp:logic>
    </xsl:template>
    
    <!-- Remove a document from the current collection.
    
        @param name the name of the document to remove.
    -->
    <xsl:template match="xmldb:collection//xmldb:remove-document">
        <xsl:variable name="name">
            <xsl:call-template name="get-parameter">
                <xsl:with-param name="name">name</xsl:with-param>
                <xsl:with-param name="required">true</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsp:logic>
            XMLResource _res = 
                (XMLResource) collection.getResource(<xsl:value-of select="$name"/>);
            collection.removeResource(_res);
        </xsp:logic>
    </xsl:template>
    
    <!-- Remove a subcollection from the current collection.
    
        @param name the name of the subcollection to remove.
    -->
    <xsl:template match="xmldb:collection//xmldb:remove-collection">
        <xsl:variable name="name">
            <xsl:call-template name="get-parameter">
                <xsl:with-param name="name">name</xsl:with-param>
                <xsl:with-param name="required">true</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsp:logic>
            CollectionManagementService mgtService =
                ( CollectionManagementService ) collection.getService( "CollectionManagementService", "1.0" );
            mgtService.removeCollection(<xsl:value-of select="$name"/>);
        </xsp:logic>
    </xsl:template>
    
    <!-- Create a new subcollection in the current collection.
    
        @param name the name of the new subcollection.
    -->
    <xsl:template match="xmldb:collection//xmldb:create-collection">
        <xsl:variable name="name">
            <xsl:call-template name="get-parameter">
                <xsl:with-param name="name">name</xsl:with-param>
                <xsl:with-param name="required">true</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsp:logic>
            CollectionManagementService mgtService =
                ( CollectionManagementService ) collection.getService( "CollectionManagementService", "1.0" ); 
            mgtService.createCollection( <xsl:value-of select="$name"/> );
        </xsp:logic>
    </xsl:template>
    
    <!-- Retrieve a document from the current collection.
    
        @param name the name of the document.
        @param as if set to "string", the document's content will be
            included as string, escaping all XML markup. If set to "xml",
            it will be inserted into the current SAX stream provided by
            Cocoon.
        @param encoding the character encoding to use for the retrieved
            document data.
    -->
    <xsl:template match="xmldb:collection//xmldb:get-document">
        <xsl:variable name="name">
            <xsl:call-template name="get-parameter">
                <xsl:with-param name="name">name</xsl:with-param>
                <xsl:with-param name="required">true</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="as">
            <xsl:call-template name="get-parameter">
                <xsl:with-param name="name" select="'as'"/>
                <xsl:with-param name="default" select="'xml'"/>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="encoding">
            <xsl:call-template name="get-parameter">
                <xsl:with-param name="name">encoding</xsl:with-param>
                <xsl:with-param name="default" select="'ISO-8859-1'"/>
            </xsl:call-template>
        </xsl:variable>
        <xsp:logic>
            collection.setProperty(OutputKeys.INDENT, "no");
            collection.setProperty(OutputKeys.ENCODING, <xsl:value-of select="$encoding"/>);
            collection.setProperty("sax-document-events", "false");
            XMLResource _res = (XMLResource) collection.getResource(<xsl:value-of select="$name"/>);
            if(<xsl:value-of select="$as"/>.equals("xml")) {
                if(_res instanceof org.exist.xmldb.RemoteXMLResource)
                    ((org.exist.xmldb.RemoteXMLResource)_res).setXMLReader(new org.exist.cocoon.XMLReaderWrapper(newParser));
                //String _content = (String)_res.getContent();
                //XSPUtil.include(new InputSource(new StringReader(_content)),
                //    this.contentHandler, newParser);
                IncludeXMLFilter _consumer = 
                    new IncludeXMLFilter(this.contentHandler);
                _res.getContentAsSAX(_consumer);
            } else {
                <xsp:content>
                    <xsp:expr>_res.getContent()</xsp:expr>
                </xsp:content>
            }
        </xsp:logic>
    </xsl:template>
    
    <!-- Get the number of hits generated by the query 
        @see #"xmldb:execute"
    -->
    <xsl:template match="xmldb:execute//xmldb:get-hit-count[not(@document or xmldb:document)]">
        <xsp:expr>(int) _hits</xsp:expr>
    </xsl:template>
    
    <!-- Get the number of hits found for a specified document or collection
    
        @param document the document for which to return the number of hits.
            If null, the number of hits for the specified collection is returned.
            Default: null.
        @param collection the collection for which to return the number of hits.
        @see #"xmldb:execute"
    -->
    <xsl:template match="xmldb:execute//xmldb:get-hit-count[@document or xmldb:document]">
        <xsl:variable name="document">
            <xsl:call-template name="get-parameter">
                <xsl:with-param name="name">document</xsl:with-param>
                <xsl:with-param name="default">null</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="collection">
            <xsl:call-template name="get-parameter">
		    <xsl:with-param name="name">collection</xsl:with-param>
                <xsl:with-param name="required">true</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsp:expr>getHitsByDocument(<xsl:value-of select="$collection"/>,
            <xsl:value-of select="$document"/>, _collections).size()</xsp:expr>
    </xsl:template>
    
    <xsl:template match="xmldb:get-error-description">
        <xsp:content>
            <xsp:expr>_errdesc</xsp:expr>
        </xsp:content>
    </xsl:template>

    <xsl:template match="xmldb:get-error">
        <xsp:content>
            <xsp:expr>_errmsg</xsp:expr>
        </xsp:content>
    </xsl:template>
    
    <xsl:template match="xmldb:uri"/>
    
    <!-- Iterate over the list of subcollections contained in the
        current collection. 
    -->
    <xsl:template match="xmldb:subcollections">
        <xsp:logic>
            for(int _i = 0; _i &lt; _childCollections.length; _i++) {
                <xsl:apply-templates/>
            }
        </xsp:logic>
    </xsl:template>

    <!-- Get the number of subcollections in the current collection. -->
    <xsl:template match="xmldb:collection//xmldb:get-subcollection-count">
        <xsp:expr>(_childCollections.length)</xsp:expr>
    </xsl:template>
    
    <!-- Get the name of the current subcollection selected by the
        xmldb:subcollections iterator.
        
        @see #"xmldb:subcollections"
    -->
    <xsl:template match="xmldb:subcollections//xmldb:child-collection-name">
        <xsp:expr>_childCollections[_i]</xsp:expr>
    </xsl:template>

    <!-- Get the number of resources contained in the current collection -->
    <xsl:template match="xmldb:collection//xmldb:get-resource-count">
        <xsp:expr>(_resources.length)</xsp:expr>
    </xsl:template>
    
    <xsl:template match="xmldb:collection//xmldb:resource-name[@count|xmldb:count]">
        <xsl:variable name="count">
            <xsl:call-template name="get-parameter">
                <xsl:with-param name="name">count</xsl:with-param>
                <xsl:with-param name="required">true</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsp:expr>(_resources[Integer.parseInt(<xsl:value-of select="$count"/>)])</xsp:expr>
    </xsl:template>
    
    <!-- Iterate over the list of resources contained in the
        current collection. 
        
        Usage:<br/>
        <pre>
&lt;xmldb:collection name="xmldb:exist:///db"&gt;
    &lt;xmldb:resources&gt;
        &lt;li&gt;&lt;xmldb:resource-name/&gt;&lt;/li&gt;
    &lt;/xmldb:resources&gt;
&lt;/xmldb:collection&gt;
        </pre>
    -->
    <xsl:template match="xmldb:resources">
        <xsp:logic>
            for(int _j = 0; _j &lt; _resources.length; _j++) {
                <xsl:apply-templates/>
            }
        </xsp:logic>
    </xsl:template>
    
    <!-- Get the name of the resource currently selected by
        the xmldb:resources iterator. 
        
        @see #"xmldb:resources"
    -->
    <xsl:template match="xmldb:resources//xmldb:resource-name">
        <xsp:expr>_resources[_j]</xsp:expr>
    </xsl:template>
    
    <!-- Store a document into the database.
    
        Usage:<br/>
<pre>&lt;xmldb:collection name="xmldb:exist:///db"&gt;
    &lt;xmldb:store name="test.xml" overwrite="true"&gt;
        &lt;xmldb:xml&gt;data&lt;/xmldb:xml&gt;
    &lt;/xmldb:store&gt;
&lt;/xmldb:collection&gt;
</pre>

        @param name the name of the document to create. If null, a unique
            name will be automatically generated.
        @param xml the xml contents of the document.
        @param overwrite if set to "true", an existing document with the
            same name will be overwritten.
    -->
    <xsl:template match="xmldb:store">
        <xsl:variable name="name">
            <xsl:call-template name="get-parameter">
                <xsl:with-param name="name">name</xsl:with-param>
                <xsl:with-param name="default">null</xsl:with-param>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="xml">
            <xsl:call-template name="get-parameter">
                <xsl:with-param name="name">source</xsl:with-param>
                <xsl:with-param name="required">true</xsl:with-param>
            </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="overwrite">
	    <xsl:call-template name="get-parameter">
            <xsl:with-param name="name">overwrite</xsl:with-param>
            <xsl:with-param name="default">false</xsl:with-param>
	    </xsl:call-template>
	</xsl:variable>
        <xsp:logic>
		String _docId = <xsl:value-of select="$name"/>;
        try {
            XMLResource _resource =
                (XMLResource)collection.getResource(_docId);
            if(_resource != null) {
                if(<xsl:value-of select="$overwrite"/>.equals("true")) {
                    collection.removeResource(_resource);
                    _resource = 
                        ( XMLResource ) collection.createResource( _docId, "XMLResource" );
					_resource.setContent(<xsl:value-of select="$xml"/>);
                    collection.storeResource(_resource);
                } else {
                    _errmsg = "Resource " + _docId + " does already exist";
                    _errdesc = "Resource " + _docId + " does already exist";
                    <xsl:apply-templates select="ancestor::xmldb:collection//xmldb:error/node()"/>
                }
            } else {
                _resource = 
                	( XMLResource ) collection.createResource( _docId, "XMLResource" );
                _resource.setContent(<xsl:value-of select="$xml"/>);
                collection.storeResource(_resource);
            }
        } catch( XMLDBException x) {
        }
        </xsp:logic>
    </xsl:template>
    
    <xsl:template match="xmldb:error|xmldb:xpath|xmldb:uri|xmldb:as|xmldb:count|xmldb:position"/>
    <xsl:template match="xmldb:results//xmldb:collection|xmldb:results//xmldb:document"/>
    <xsl:template match="xmldb:user|xmldb:password"/>
    
    <xsl:template match="*|@*|text()|processing-instruction()" priority="-1">
    	<xsl:copy>
      		<xsl:apply-templates select="@*|*|text()|processing-instruction()"/>
    	</xsl:copy>
  	</xsl:template>
  	
  <!-- This is a utility template to retrieve parameters passed to 
    other templates. -->
  <xsl:template name="get-parameter">
    <xsl:param name="name"/>
    <xsl:param name="default"/>
    <xsl:param name="required">false</xsl:param>

    <xsl:choose>
    <xsl:when test="@*[local-name(.) = $name or name(.) = $name]"><xsl:text>"</xsl:text><xsl:value-of select="@*[local-name(.) = $name or name(.) = $name]"/><xsl:text>"</xsl:text></xsl:when>
    <xsl:when test="*[local-name(.) = $name]">
        String.valueOf(
			<xsl:copy>
        		<xsl:apply-templates select="*[local-name() = $name]/node()"/>
        	</xsl:copy>
        )
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
          <xsl:otherwise>"<xsl:copy-of select="$default"/>"</xsl:otherwise>
        </xsl:choose>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="get-nested-content">
    <xsl:param name="content"/>
    <xsl:choose>
      <xsl:when test="$content/xsp:text">"<xsl:value-of select="$content"/>"</xsl:when>
      <xsl:when test="$content/*">
        <xsl:apply-templates select="$content/*"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$content"/></xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="error">
    <xsl:param name="message"/>
    <xsl:message terminate="yes"><xsl:value-of select="$message"/></xsl:message>
  </xsl:template>
</xsl:stylesheet>
