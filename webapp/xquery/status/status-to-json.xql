xquery version "1.0";

declare namespace exist = "http://exist.sourceforge.net/NS/exist";
declare namespace jmx = "http://exist-db.org/jmx";
declare namespace json = "http://www.json.org";

import module namespace request = "http://exist-db.org/xquery/request";
import module namespace util = "http://exist-db.org/xquery/util";

declare option exist:serialize "method=json media-type=application/json";

declare variable $local:jmx-servlet-path := "/status";

declare function local:get-server-uri() as xs:string {
    fn:concat("http://", request:get-server-name(), ":", request:get-server-port(), request:get-context-path())
};

declare function local:get-epoch-seconds($dt as xs:dateTime) as xs:unsignedLong {
    xs:unsignedLong(
        ($dt - xs:dateTime('1970-01-01T00:00:00Z'))
        div
        xs:dayTimeDuration('PT1S')
    )
};

declare function local:chart($name as xs:string, $type as xs:string, $options-function as item()?, $series-function as item(), $data as element()*) as element(chart) {
    <chart>
        <name>{$name}</name>
        <type>{$type}</type>
        {
            if(not(empty($options-function)))then
                util:call($options-function, $data)
            else(),
            
            util:call($series-function, $data)
        }
    </chart>
};

declare function local:cache-xaxis-ticks-options($data as element(jmx:Cache)+) as element(xaxis) {
    <xaxis>
        <ticks>
        {
            for $filename at $i in $data/jmx:FileName return
                <json:value>
                    <json:value json:literal="true">{$i}</json:value>
                    <json:value>{$filename/text()}</json:value>
                </json:value>
        }
        </ticks>
    </xaxis>
};

declare function local:cache-yaxis-max-options($data as element(jmx:Cache)+) as element(xaxis) {
    <yaxis>
        <max json:literal="true">100</max>
    </yaxis>
};

(: stacked bar graph :)
declare function local:cache-use-stacked-bar-graph($caches as element(jmx:Cache)+) as element(series) {
    <series>
            <json:value>
                <label>Cache Used</label>
                <color>rgb(255,0,0)</color>
                <data>
                    {
                        for $cache at $i in $caches return
                            <json:value>
                                <json:value json:literal="true">{$i}</json:value>
                                <json:value json:literal="true">{string($cache/jmx:Used)}</json:value>
                            </json:value> 
                    }
                </data>
            </json:value>
            <json:value>
                <label>Cache Available</label>
                <color>rgb(0,255,0)</color>
                <data>
                    {
                        for $cache at $i in $caches return
                            <json:value>
                                <json:value json:literal="true">{$i}</json:value>
                                <json:value json:literal="true">{$cache/jmx:Size - $cache/jmx:Used}</json:value>
                            </json:value>
                    }
                </data>
            </json:value>
    </series>
};

declare function local:disk-use-xaxis-ticks-options($disk-use as element(jmx:DiskUsage)) as element(xaxis) {
    <xaxis>
        <ticks>
            <json:value>
                <json:value json:literal="true">1</json:value>
                <json:value>Data</json:value>
            </json:value>
            <json:value>
                <json:value json:literal="true">2</json:value>
                <json:value>Journal</json:value>
            </json:value>
        </ticks>
    </xaxis>
};

declare function local:disk-use-stacked-bar-graph($disk-use as element(jmx:DiskUsage)) as element(series) {
    <series>
            <json:value>
                <label>Disk Space Used</label>
                <color>rgb(255,0,0)</color>
                <data>
                    <json:value>
                        <json:value json:literal="true">1</json:value>
                        <json:value json:literal="true">{local:bytes-to-megabytes($disk-use/jmx:DataDirectoryUsedSpace)}</json:value>
                    </json:value>
                    <json:value>
                        <json:value json:literal="true">2</json:value>
                        <json:value json:literal="true">{local:bytes-to-megabytes($disk-use/jmx:JournalDirectoryUsedSpace)}</json:value>
                    </json:value> 
                </data>
            </json:value>
            <json:value>
                <label>Disk Space Available</label>
                <color>rgb(0,255,0)</color>
                <data>
                    <json:value>
                        <json:value json:literal="true">1</json:value>
                        <json:value json:literal="true">{local:bytes-to-megabytes($disk-use/jmx:DataDirectoryFreeSpace)}</json:value>
                    </json:value>
                    <json:value>
                        <json:value json:literal="true">2</json:value>
                        <json:value json:literal="true">{local:bytes-to-megabytes($disk-use/jmx:JournalDirectoryFreeSpace)}</json:value>
                    </json:value> 
                </data>
            </json:value>
    </series>
};

declare function local:broker-use-xaxis-ticks-options($database as element(jmx:Database)) as element(xaxis) {
    <xaxis>
        <ticks json:array="true">
            <json:value json:literal="true">1</json:value>
            <json:value>Brokers</json:value>
        </ticks>
    </xaxis>
};

declare function local:broker-use-stacked-bar-graph($database as element(jmx:Database)) as element(series) {
        let $active := $database/jmx:ActiveBrokers cast as xs:integer,
        $available := $database/jmx:AvailableBrokers cast as xs:integer,
        $max := $database/jmx:MaxBrokers cast as xs:integer
        return
    
            <series>
                    <json:value>
                        <label>Active Brokers</label>
                        <color>rgb(255,0,0)</color>
                        <data json:array="true">
                            <json:value json:literal="true">1</json:value>
                            <json:value json:literal="true">{$active}</json:value>
                        </data>
                    </json:value>
                    <json:value>
                        <label>Available Brokers</label>
                        <color>rgb(0,255,0)</color>
                        <data json:array="true">
                            <json:value json:literal="true">1</json:value>
                            <json:value json:literal="true">{$available}</json:value>
                        </data>
                    </json:value>
                    <json:value>
                        <label>Max Brokers</label>
                        <color>rgb(255,140,0)</color>
                        <data json:array="true">
                            <json:value json:literal="true">1</json:value>
                            <json:value json:literal="true">{$max}</json:value>
                        </data>
                    </json:value>
            </series>
};

declare function local:bytes-to-megabytes($bytes as xs:long) as xs:long {
    $bytes div (1024 * 1024)
};

(:~
: Returns the score as a percentage
:)
declare function local:calc-percentage-score($correct as xs:integer, $incorrect as xs:integer) as xs:float {
    if($correct eq 0 and $incorrect eq 0)then
        100
    else
        $correct div ($correct + $incorrect) * 100
};

(: plotted time graph :)
declare function local:cache-efficiency-time-graph($caches as element(jmx:Cache)*) as element(series) {

    <series>{
        for $cache in $caches return
            <json:value>
                <label>{fn:string($cache/jmx:FileName)}</label>
                    <data json:array="true">
                        <json:value json:literal="true">{local:get-epoch-seconds(fn:current-dateTime()) * 1000}</json:value>
                        <json:value json:literal="true">{local:calc-percentage-score($cache/jmx:Hits, $cache/jmx:Fails)}</json:value>
                    </data>
            </json:value>
    }</series>
};

declare function local:memory-use-yaxis-max-options($memory-use as element()) as element(yaxis) {
    <yaxis>
        <max json:literal="true">{local:bytes-to-megabytes($memory-use/jmx:max)}</max>
    </yaxis>
};

declare function local:memory-use-time-graph($memory-use as element()) as element(series) {
    <series>
        <json:value>
            <label>Used</label>
            <data json:array="true">
                <json:value json:literal="true">{local:get-epoch-seconds(fn:current-dateTime()) * 1000}</json:value>
                <json:value json:literal="true">{local:bytes-to-megabytes($memory-use/jmx:used)}</json:value>
            </data>
        </json:value>
        <json:value>
            <label>Committed</label>
            <data json:array="true">
                <json:value json:literal="true">{local:get-epoch-seconds(fn:current-dateTime()) * 1000}</json:value>
                <json:value json:literal="true">{local:bytes-to-megabytes($memory-use/jmx:committed)}</json:value>
            </data>
        </json:value>
    </series>
};

let $jmx := doc(fn:concat(local:get-server-uri(), $local:jmx-servlet-path))/jmx:jmx return
    <json:value>
    {
        let $btree-cache := $jmx/jmx:Cache[jmx:Type eq "BTREE"],
        $data-cache := $jmx/jmx:Cache[jmx:Type eq "DATA"]
        return
            (
                local:chart("btreeCacheUseChart", "stack", util:function(xs:QName("local:cache-xaxis-ticks-options"), 1), util:function(xs:QName("local:cache-use-stacked-bar-graph"), 1), $btree-cache),
                local:chart("btreeCacheEfficiencyChart", "time", util:function(xs:QName("local:cache-yaxis-max-options"), 1), util:function(xs:QName("local:cache-efficiency-time-graph"), 1), $btree-cache),
            
                local:chart("dataCacheUseChart", "stack", util:function(xs:QName("local:cache-xaxis-ticks-options"), 1), util:function(xs:QName("local:cache-use-stacked-bar-graph"), 1), $data-cache),
                local:chart("dataCacheEfficiencyChart", "time", util:function(xs:QName("local:cache-yaxis-max-options"), 1), util:function(xs:QName("local:cache-efficiency-time-graph"), 1), $data-cache),
                
                local:chart("heapMemoryUseChart", "time", util:function(xs:QName("local:memory-use-yaxis-max-options"), 1), util:function(xs:QName("local:memory-use-time-graph"), 1), $jmx/jmx:MemoryImpl/jmx:HeapMemoryUsage),
                local:chart("nonHeapMemoryUseChart", "time", util:function(xs:QName("local:memory-use-yaxis-max-options"), 1), util:function(xs:QName("local:memory-use-time-graph"), 1), $jmx/jmx:MemoryImpl/jmx:NonHeapMemoryUsage),
                
                local:chart("diskUseChart", "stack", util:function(xs:QName("local:disk-use-xaxis-ticks-options"), 1), util:function(xs:QName("local:disk-use-stacked-bar-graph"), 1), $jmx/jmx:DiskUsage),
                
                local:chart("brokerUseChart", "stack", util:function(xs:QName("local:broker-use-xaxis-ticks-options"), 1), util:function(xs:QName("local:broker-use-stacked-bar-graph"), 1), $jmx/jmx:Database)
            )
    }
    </json:value>