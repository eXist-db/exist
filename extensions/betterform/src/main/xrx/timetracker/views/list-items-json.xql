xquery version "1.0";

import module namespace json="http://www.json.org";

declare option exist:serialize "method=text media-type=text/json";

declare function local:main() as xs:string * {
for $task in collection('/db/betterform/apps/timetracker/data/task')//task
    let $task-json := json:xml-to-json($task)
    return
        $task-json
};

local:main()