<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:jmx="http://exist-db.org/jmx" xmlns:l="local" version="2.0" exclude-result-prefixes="#all">
    <xsl:output method="xhtml" media-type="text/xml" omit-xml-declaration="no" indent="yes" encoding="UTF-8"/>
    
    <xsl:template match="jmx:jmx">
        <book>
            <bookinfo>
                <graphic fileref="logo.jpg"/>
                
                <productname>Open Source Native XML Database</productname>
                <title>Server Status (via JMX)</title>
                <script language="javascript" type="text/javascript" src="../../scripts/jquery/jquery-1.4.2.min.js"></script>
                <script language="javascript" type="text/javascript" src="../../scripts/jquery/flot/jquery.flot.js"></script>
                <script language="javascript" type="text/javascript" src="../../scripts/jquery/flot/jquery.flot.stack.js"></script>
                <source>status.xslt/source</source>
            </bookinfo>
            
            <!-- xi:include xmlns:xi="http://www.w3.org/2001/XInclude" href="sidebar.xml"/ -->
            
            <chapter>
                <title>Server Status</title>
                
                <xsl:apply-templates/>
                
            </chapter>
        </book>
    </xsl:template>
    
    <xsl:template match="jmx:SystemInfo">
        <section>
            <title>System Information</title>
            <table>
                <title>System Infomation</title>
                <tgroup cols="2">
                    <tbody>
                        <xsl:call-template name="jmx-children-to-table-rows" />
                    </tbody>
                </tgroup>
            </table>
        </section>
    </xsl:template>
    
    <xsl:template match="jmx:CacheManager[@name eq 'org.exist.management.exist:type=CacheManager']">
        <section>
            <title>Cache Manager</title>
            <div id="cacheManagerChart" style="width: 600px; height: 300px; position: relative;"/>
            <script id="source" language="javascript" type="text/javascript">
                <![CDATA[
                $(function () {
                    
                    var updateInterval = 5000;
                    
                    var options = {
                        lines: { show: true },
                        points: { show: true },
                        
                        /*xaxis: {
                            mode: "time",
                            minTickSize: [2, "second"]
                        }*/
                        
                        //xaxis: { tickDecimals: 0, tickSize: 1}
                        
                        xaxis: {
                            mode: "time",
                            timeformat: "%H:%M:%S"
                        }
                    };
                    var data = [];
                    var placeholder = $("#cacheManagerChart");
                    
                    $.plot(placeholder, data, options);
                 
                    // fetch one series, adding to what we got
                    var alreadyFetched = {};
                 
                    // initiate a recurring data update
                    $(document).ready(function () {
                        // reset data
                        data = [];
                        alreadyFetched = {};
                        
                        $.plot(placeholder, data, options);
                 
                        var iteration = 0;
                        
                        function fetchData() {
                 
                            function onDataReceived(series) {
                                // we get all the data in one go, if we only got partial
                                // data, we could merge it with what we already got
                                
                                //data = [ series ]; //for all data in one go
                                
                                if(data.length == 0){
                                    data = series;
                                } else {
                                
                                    //merge data
                                
                                    $(data).each(function(index){
                                        //$(this).each(function(index){
                                            $.merge(this.data, series[index].data)
                                        //});
                                    });
                                }
                                
                                $.plot(placeholder, data, options);
                                
                            }
                        
                            $.ajax({
                                // usually, we'll just call the same URL, a script
                                // connected to a database, but in this case we only
                                // have static example files so we need to modify the
                                // URL
                                url: "http://localhost:8080/exist/xquery/status/status-to-json.xql",
                                method: 'GET',
                                dataType: 'json',
                                success: onDataReceived
                            });
                            
                            
                            setTimeout(fetchData, updateInterval);
                        }
                 
                        setTimeout(fetchData, updateInterval);
                    });
                });
                ]]>
            </script> 
        </section>
    </xsl:template>
    
    <xsl:template match="jmx:*" priority="-1"/>
    
    <xsl:template name="jmx-children-to-table-rows">
        <xsl:for-each select="child::jmx:*">
            <row>
                <entry><xsl:value-of select="l:split-camel-case(local-name(.))"/></entry>
                <entry><xsl:value-of select="."/></entry>
            </row>
        </xsl:for-each>
    </xsl:template>
    
    <xsl:function name="l:split-camel-case">
        <xsl:param name="str"/>
        <xsl:choose>
            <xsl:when test="matches($str, '[A-Z][a-z]*[A-Z].*')">
                <xsl:variable name="newStr" select="replace($str, '([A-Z][a-z]*)([A-Z].*)', '$1 $2')"/>
                <xsl:value-of select="l:split-camel-case($newStr)"/>  <!-- recursive call -->
            </xsl:when>
            <xsl:otherwise><xsl:value-of select="$str"></xsl:value-of></xsl:otherwise>
        </xsl:choose>
        
    </xsl:function>
    
</xsl:stylesheet>