module namespace jnlp = "http://exist-db.org/xquery/jnlp";

import module namespace system   = "http://exist-db.org/xquery/system";
import module namespace request  = "http://exist-db.org/xquery/request";
import module namespace response = "http://exist-db.org/xquery/response";

declare function local:filter-jar($jar){
    let $lib := system:get-lib-info($jar/@href)
    return if ($lib) then element jar {
                              attribute href {$lib/@name},
                              $lib/@size,
                              $jar/(@* except @href)
                          }
                     else ()
};

declare function jnlp:prepare($jnlp){

    let $jnlp := element jnlp {
                    $jnlp/@*,
                    attribute codebase {
                       request:get-attribute("codebase")
                    },
                    $jnlp/(* except resources),
                    element resources {
                        $jnlp/resources/(* except jar),
                        for $jar in $jnlp/resources/jar
                        return local:filter-jar($jar)
                    }
                 },
        $last-modified := system:get-lib-info($jnlp//jar[@main eq "true"]/@href)/@modified,
        $dummy := response:set-header("Content-Type", "application/x-java-jnlp-file"),
        $dummy := response:set-date-header("Last-Modified", $last-modified)
    return $jnlp    
    
};