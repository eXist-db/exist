xquery version "1.0";

declare namespace exist = "http://exist.sourceforge.net/NS/exist";
declare namespace jmx = "http://exist-db.org/jmx";
declare namespace json = "http://www.json.org";

import module namespace request = "http://exist-db.org/xquery/request";

declare option exist:serialize "method=json media-type=application/json";
(: declare option exist:serialize "method=text media-type=application/json"; :)

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

(: stacked bar graph :)
declare function local:cache-use-stacked-bar-graph($caches as element(jmx:Cache)*) as element(json:value) {
    <json:value>
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
    </json:value>
};

declare function local:disk-use-stacked-bar-graph($dataFolder, $journalFolder) as element(json:value) {
    <json:value>
            <json:value>
                <label>Disk Space Used</label>
                <color>rgb(255,0,0)</color>
                <data>
                    <json:value>
                        <json:value json:literal="true">1</json:value>
                        <json:value json:literal="true">{local:bytes-to-megabytes($dataFolder[1])}</json:value>
                    </json:value>
                    <json:value>
                        <json:value json:literal="true">2</json:value>
                        <json:value json:literal="true">{local:bytes-to-megabytes($journalFolder[1])}</json:value>
                    </json:value> 
                </data>
            </json:value>
            <json:value>
                <label>Disk Space Available</label>
                <color>rgb(0,255,0)</color>
                <data>
                    <json:value>
                        <json:value json:literal="true">1</json:value>
                        <json:value json:literal="true">{local:bytes-to-megabytes($dataFolder[2])}</json:value>
                    </json:value>
                    <json:value>
                        <json:value json:literal="true">2</json:value>
                        <json:value json:literal="true">{local:bytes-to-megabytes($journalFolder[2])}</json:value>
                    </json:value> 
                </data>
            </json:value>
    </json:value>
};

declare function local:broker-use-stacked-bar-graph($active as xs:integer, $available as xs:integer, $max as xs:integer) as element(json:value) {
    <json:value>
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
    </json:value>
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
declare function local:cache-efficiency-time-graph($caches as element(jmx:Cache)*) as element(json:value) {

    <json:value>{
        for $cache in $caches return
            <json:value>
                <label>{fn:string($cache/jmx:FileName)}</label>
                    <data json:array="true">
                        <json:value json:literal="true">{local:get-epoch-seconds(fn:current-dateTime()) * 1000}</json:value>
                        <json:value json:literal="true">{local:calc-percentage-score($cache/jmx:Hits, $cache/jmx:Fails)}</json:value>
                    </data>
            </json:value>
    }</json:value>
};

declare function local:memory-use-time-graph($memory-use as element()) as element(json:value) {
    <json:value>
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
    </json:value>
};

let $jmx := doc(fn:concat(local:get-server-uri(), $local:jmx-servlet-path))/jmx:jmx return

let $dataset := request:get-parameter("ds",()) return
    if($dataset eq "btreeCacheUse")then (
        local:cache-use-stacked-bar-graph($jmx/jmx:Cache[jmx:Type eq "BTREE"])
    ) else if($dataset eq "dataCacheUse")then (
        local:cache-use-stacked-bar-graph($jmx/jmx:Cache[jmx:Type eq "DATA"])
    ) else if($dataset eq "btreeCacheEfficiency")then (
        local:cache-efficiency-time-graph($jmx/jmx:Cache[jmx:Type eq "BTREE"])
    ) else if($dataset eq "dataCacheEfficiency")then (
        local:cache-efficiency-time-graph($jmx/jmx:Cache[jmx:Type eq "DATA"])
    ) else if($dataset eq "heapMemoryUse")then (
        local:memory-use-time-graph($jmx/jmx:MemoryImpl/jmx:HeapMemoryUsage)
    ) else if($dataset eq "nonHeapMemoryUse")then (
        local:memory-use-time-graph($jmx/jmx:MemoryImpl/jmx:NonHeapMemoryUsage)
    ) else if($dataset eq "diskUse")then(
        let $disk-useage := $jmx/jmx:DiskUsage return
        local:disk-use-stacked-bar-graph(($disk-useage/jmx:DataDirectoryUsedSpace,$disk-useage/jmx:DataDirectoryFreeSpace), ($disk-useage/jmx:JournalDirectoryUsedSpace,$disk-useage/jmx:JournalDirectoryFreeSpace))
    ) else if($dataset eq "brokerUse")then(
        let $database := $jmx/jmx:Database return
        local:broker-use-stacked-bar-graph($database/jmx:ActiveBrokers, $database/jmx:AvailableBrokers, $database/jmx:MaxBrokers)
    ) else (
        $jmx
    )