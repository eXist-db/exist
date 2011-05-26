<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:jmx="http://exist-db.org/jmx" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:l="local" version="2.0" exclude-result-prefixes="#all">
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
                        <xsl:call-template name="jmx-children-to-table-rows">
                            <xsl:with-param name="seq" select="child::jmx:*"/>
                        </xsl:call-template>
                    </tbody>
                </tgroup>
            </table>
        </section>
    </xsl:template>
    
    <xsl:template match="jmx:Database">
        <section>
            <title>Database</title>
            <itemizedlist>
                <listitem>
                    <para>Instance ID: <xsl:value-of select="jmx:InstanceId"/></para>
                </listitem>
            </itemizedlist>
            <para>
                <xsl:call-template name="flot-chart">
                    <xsl:with-param name="id" select="'brokerUseChart'"/>
                    <xsl:with-param name="json-data-uri" select="xs:anyURI('http://localhost:8080/exist/xquery/status/status-to-json.xql?ds=brokerUse')"/>
                    <xsl:with-param name="type" select="'stack'"/>
                    <xsl:with-param name="xaxis-ticks">[1, "Brokers"]</xsl:with-param>
                </xsl:call-template>
            </para>
        </section>
    </xsl:template>
    
    <xsl:template match="jmx:ProcessReport">
        <section>
            <title>Processes</title>
            <section>
                <title>Executing Queries</title>
                <table border="1">
                    <title>Queries</title>
                    <tgroup cols="2">
                        <thead style="font-weight: bold">
                            <row>
                                <entry>ID</entry>
                                <entry>Source</entry>
                                <entry>Type</entry>
                                <entry>Terminating</entry>
                            </row>
                        </thead>
                        <tbody>
                            <xsl:apply-templates select="jmx:RunningQueries"/>
                        </tbody>
                    </tgroup>
                </table>
            </section>
            <section>
                <title>Recent Query History</title>
                <table border="1">
                    <title>Queries</title>
                    <tgroup cols="2">
                        <thead style="font-weight: bold">
                            <row>
                                <entry>Source</entry>
                                <entry>Invocation Count</entry>
                                <entry>Most Recent Starting Time</entry>
                                <entry>Most Recent Execution Duration</entry>
                            </row>
                        </thead>
                        <tbody>
                            <xsl:apply-templates select="jmx:RecentQueryHistory"/>
                        </tbody>
                    </tgroup>
                </table>
            </section>
            <section>
                <title>Scheduler</title>
                <table border="1">
                    <title>Jobs</title>
                    <tgroup cols="2">
                        <thead style="font-weight: bold">
                            <row>
                                <entry>Status</entry>
                                <entry>Action</entry>
                                <entry>ID</entry>
                                <entry>Info</entry>
                            </row>
                        </thead>
                        <tbody>
                            <xsl:apply-templates select="jmx:ScheduledJobs|jmx:RunningJobs"/>
                        </tbody>
                    </tgroup>
                </table>
            </section>
        </section>
    </xsl:template>
    
    <xsl:template match="jmx:RecentQueryHistory">
        <xsl:for-each select="jmx:row">
            <row>
                <entry><xsl:value-of select="jmx:sourceKey"/></entry>
                <entry><xsl:value-of select="jmx:recentInvocationCount"/></entry>
                <entry><xsl:value-of select="jmx:mostRecentExecutionTime"/></entry>
                <entry><xsl:value-of select="jmx:mostRecentExecutionDuration"/></entry>
            </row>
        </xsl:for-each>
    </xsl:template>
    
    <xsl:template match="jmx:RunningQueries">
        <xsl:for-each select="jmx:row">
            <row>
                <entry><xsl:value-of select="jmx:id"/></entry>
                <entry><xsl:value-of select="jmx:sourceKey"/></entry>
                <entry><xsl:value-of select="jmx:sourceType"/></entry>
                <entry><xsl:value-of select="jmx:isTerminating"/></entry>
            </row>
        </xsl:for-each>
    </xsl:template>
    
    <xsl:template match="jmx:ScheduledJobs|jmx:RunningJobs">
        <xsl:for-each select="jmx:row">
            <row>
                <entry>
                    <xsl:choose>
                        <xsl:when test="parent::jmx:RunningJobs">Active</xsl:when>
                        <xsl:otherwise>Inactive</xsl:otherwise>
                    </xsl:choose>
                </entry>
                <entry><xsl:value-of select="jmx:action"/></entry>
                <entry><xsl:value-of select="jmx:id"/></entry>
                <entry><xsl:value-of select="jmx:info"/></entry>
            </row>
        </xsl:for-each>
    </xsl:template>
    
    <xsl:template match="jmx:RuntimeImpl">
        <section>
            <title>JVM Information</title>
            <table>
                <title>JVM Information</title>
                <tgroups cols="2">
                    <tbody>
                        <xsl:variable name="java.version"><jmx:JavaVersion><xsl:value-of select="jmx:SystemProperties/jmx:row[jmx:key eq 'java.version']/jmx:value"/></jmx:JavaVersion></xsl:variable>
                        <xsl:call-template name="jmx-children-to-table-rows">
                            <xsl:with-param name="seq" select="jmx:VmName,jmx:VmVendor,jmx:VmVersion,$java.version,jmx:Uptime"/>
                        </xsl:call-template> 
                    </tbody>
                </tgroups>
            </table>
        </section>
    </xsl:template>
    
    <xsl:template match="jmx:MemoryImpl">
        <section>
            <title>Memory Use</title>
            <para><em>Measured in megabytes.</em></para>
            <section>
                <title>Heap</title>
                <para>
                    <xsl:call-template name="flot-chart">
                        <xsl:with-param name="id" select="'heapMemoryUseChart'"/>
                        <xsl:with-param name="json-data-uri" select="xs:anyURI('http://localhost:8080/exist/xquery/status/status-to-json.xql?ds=heapMemoryUse')"/>
                        <xsl:with-param name="type" select="'time'"/>
                        <xsl:with-param name="y-max" select="jmx:HeapMemoryUsage/jmx:max div (1024 * 1024)"/>
                    </xsl:call-template>
                </para>
            </section>
            <section>
                <title>Non-Heap</title>
                <para>
                    <xsl:call-template name="flot-chart">
                        <xsl:with-param name="id" select="'nonHeapMemoryUseChart'"/>
                        <xsl:with-param name="json-data-uri" select="xs:anyURI('http://localhost:8080/exist/xquery/status/status-to-json.xql?ds=nonHeapMemoryUse')"/>
                        <xsl:with-param name="type" select="'time'"/>
                        <xsl:with-param name="y-max" select="jmx:NonHeapMemoryUsage/jmx:max div (1024 * 1024)"/>
                    </xsl:call-template>
                </para>
            </section>
        </section>
    </xsl:template>
    
    <xsl:template match="jmx:CacheManager[@name eq 'org.exist.management.exist:type=CacheManager']">
        <section>
            <title>Cache Manager</title>
            <itemizedlist>
                <listitem>
                    <para>Size: <xsl:value-of select="jmx:CurrentSize"/> of maximum <xsl:value-of select="jmx:MaxTotal"/> pages</para>
                </listitem>
            </itemizedlist>
            <section>
                <title>BTree Cache</title>
                <section>
                    <title>Cache Useage</title>
                    <para>
                        <xsl:call-template name="flot-chart">
                            <xsl:with-param name="id" select="'btreeCacheUseChart'"/>
                            <xsl:with-param name="json-data-uri" select="xs:anyURI('http://localhost:8080/exist/xquery/status/status-to-json.xql?ds=btreeCacheUse')"/>
                            <xsl:with-param name="type" select="'stack'"/>
                            <xsl:with-param name="xaxis-ticks" select="l:cache-ticks-json(../jmx:Cache[jmx:Type eq 'BTREE'], 1)"/>
                        </xsl:call-template>
                    </para>
                </section>
                <section>
                    <title>Cache Efficiency</title>
                    <para>
                        <xsl:call-template name="flot-chart">
                            <xsl:with-param name="id" select="'btreeCacheEfficiencyChart'"/>
                            <xsl:with-param name="json-data-uri" select="xs:anyURI('http://localhost:8080/exist/xquery/status/status-to-json.xql?ds=btreeCacheEfficiency')"/>
                            <xsl:with-param name="type" select="'time'"/>
                        </xsl:call-template>
                    </para>
                </section>
            </section>
            <section>
                <title>Data Cache</title>
                <section>
                    <title>Cache Useage</title>
                    <para>
                        <xsl:call-template name="flot-chart">
                            <xsl:with-param name="id" select="'dataCacheChart'"/>
                            <xsl:with-param name="json-data-uri" select="xs:anyURI('http://localhost:8080/exist/xquery/status/status-to-json.xql?ds=dataCacheUse')"/>
                            <xsl:with-param name="type" select="'stack'"/>
                            <xsl:with-param name="xaxis-ticks" select="l:cache-ticks-json(../jmx:Cache[jmx:Type eq 'DATA'], 1)"/>
                        </xsl:call-template>
                    </para>
                </section>
                <section>
                    <title>Cache Efficiency</title>
                    <para>
                        <xsl:call-template name="flot-chart">
                            <xsl:with-param name="id" select="'dataCacheEfficiencyChart'"/>
                            <xsl:with-param name="json-data-uri" select="xs:anyURI('http://localhost:8080/exist/xquery/status/status-to-json.xql?ds=dataCacheEfficiency')"/>
                            <xsl:with-param name="type" select="'time'"/>
                        </xsl:call-template>
                    </para>
                </section>
            </section>
        </section>
    </xsl:template>
    
    <xsl:template match="jmx:DiskUsage">
        <section>
            <title>Disk Useage</title>
            <para><em>Measured in megabytes.</em></para>
            <itemizedlist>
                <listitem>
                    <para>Data Folder: <xsl:value-of select="jmx:DataDirectory"/></para>
                </listitem>
                <listitem>
                    <para>Journal Folder: <xsl:value-of select="jmx:JournalDirectory"/></para>
                </listitem>
                <listitem>
                    <para>Journal Files: <xsl:value-of select="jmx:JournalDirectoryNumberOfFiles"/></para>
                </listitem>
            </itemizedlist>
            <section>
                <title>Disk Useage</title>
                <para>
                    <xsl:call-template name="flot-chart">
                        <xsl:with-param name="id" select="'diskUseChart'"/>
                        <xsl:with-param name="json-data-uri" select="xs:anyURI('http://localhost:8080/exist/xquery/status/status-to-json.xql?ds=diskUse')"/>
                        <xsl:with-param name="type" select="'stack'"/>
                        <xsl:with-param name="xaxis-ticks">[1, "Data"],[2, "Journal"]</xsl:with-param>
                    </xsl:call-template>
                </para>
            </section>
        </section>
    </xsl:template>
    
    <xsl:template name="flot-chart">
        <xsl:param name="id" as="xs:string" required="yes"/>
        <xsl:param name="json-data-uri" as="xs:anyURI" required="yes"/>
        <xsl:param name="type" as="xs:string" required="yes"/>
        <xsl:param name="xaxis-ticks"/>
        <xsl:param name="y-max"/>
        
        <div id="{$id}" style="width: 600px; height: 300px; position: relative;"/>
        <script id="source" language="javascript" type="text/javascript">
                <![CDATA[
                $(function () {
                
                    var placeholder = $("#]]><xsl:value-of select="$id"/><![CDATA[");
                    
                    var updateInterval = 5000;
                    ]]>
                    <xsl:choose>
                        <xsl:when test="$type eq 'stack'">
                            <![CDATA[
                            var stack = 0, bars = true, lines = false, steps = false;
                            var options = {
                                series: {
                                    stack: stack,
                                    lines: { show: lines, steps: steps },
                                    bars: { show: bars, barWidth: 0.5}
                                },
                                grid: {
                                    tickColor: "rgb(255,255,255)"
                                },
                                xaxis: {
                                    ticks: [ ]]><xsl:value-of select="$xaxis-ticks"/><![CDATA[
                                ]}
                            };
                            ]]>
                        </xsl:when>
                        <xsl:when test="$type eq 'time'">
                            <![CDATA[
                            var options = {
                                lines: { show: true, fill: false },
                                points: { show: false },
                                
                                xaxis: {
                                    mode: "time",
                                    timeformat: "%H:%M:%S"
                                } ]]>
                                <xsl:if test="$y-max">
                                    <![CDATA[
                                    , yaxis: {
                                        max: ]]><xsl:value-of select="$y-max"/><![CDATA[
                                    }
                                    ]]>
                                </xsl:if>
                                <![CDATA[
                            };
                            ]]>
                        </xsl:when>
                    </xsl:choose>
            
                    <![CDATA[
                    
                    var data = [];
                    
                    $.plot(placeholder, data, options);
                 
                    // fetch one series, adding to what we got
                    var alreadyFetched = {};
                 
                    // initiate a recurring data update
                    $(document).ready(function () {
                        // reset data
                        data = [];
                        alreadyFetched = {};
                        
                        $.plot(placeholder, data, options);
                        
                        function fetchData() {
                 
                            function onDataReceived(series) {
                                // we get all the data in one go, if we only got partial
                                // data, we could merge it with what we already got
                                ]]>
                                <xsl:choose>
                                    <xsl:when test="$type eq 'stack'">
                                        <![CDATA[
                                        data = series;
                                        ]]>
                                    </xsl:when>
                                    <xsl:when test="$type eq 'time'">
                                        <![CDATA[
                                        if(data.length == 0){
                                            data = series;
                                        } else {
                                        
                                            //merge data
                                            $(data).each(function(index){
                                                $.merge(this.data, series[index].data)
                                            });
                                        }
                                        ]]>
                                    </xsl:when>
                                </xsl:choose>
                                <![CDATA[
                                $.plot(placeholder, data, options);
                                
                            }
                        
                            $.ajax({
                                // usually, we'll just call the same URL, a script
                                // connected to a database, but in this case we only
                                // have static example files so we need to modify the
                                // URL
                                url: "]]><xsl:value-of select="$json-data-uri"/><![CDATA[",
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
    </xsl:template>
    
    
    <xsl:template match="jmx:*" priority="-1"/>
    
    <xsl:function name="l:cache-ticks-json">
        <xsl:param name="cache" as="element(jmx:Cache)*"/>
        <xsl:param name="pos"/>
        <xsl:value-of select='concat("[", $pos, ",", """", $cache[$pos]/jmx:FileName, """","]", if($cache[$pos+1])then(",")else(""))'/>
        <xsl:if test="$cache[$pos+1]">
            <xsl:value-of select="l:cache-ticks-json($cache, $pos+1)"/>
        </xsl:if>
    </xsl:function>
    
    
    <xsl:template name="jmx-children-to-table-rows">
        <xsl:param name="seq"/>
        <xsl:for-each select="$seq">
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