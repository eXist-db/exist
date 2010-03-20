xquery version "1.0";

import module namespace request = "http://exist-db.org/xquery/request";

if (ends-with($exist:path, ".jnlp")) 
   then <exist:dispatch>
            <exist:forward url="/jnlp/{substring-before($exist:path, '.jnlp')}.xql">
                <exist:set-attribute name="codebase" 
                    value="{substring-before(request:get-url(), $exist:resource)}"/>
            </exist:forward>
        </exist:dispatch>
   else if (ends-with($exist:path, ".jar") or ends-with($exist:path, ".jar.pack.gz"))
           then <exist:dispatch>
                    <exist:forward url="/jnlp/get-jar.xql">
                        <exist:set-attribute name="name" value="{$exist:resource}"/>
                    </exist:forward>
                </exist:dispatch>
           else <exist:ignore/>