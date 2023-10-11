xquery version "3.1";
<tests>
    <static-base-uri>{ static-base-uri() }</static-base-uri>
    <does-sbu-xqs-exist>{ exists(static-base-uri()) }</does-sbu-xqs-exist>
    <resolve-explicit>{ resolve-uri('#foobaz', static-base-uri() ) }</resolve-explicit>
    <resolve-implicit>{ resolve-uri('#foobar') }</resolve-implicit>
    <resolve-path>{ resolve-uri('path/to/file#foobar') }</resolve-path>
</tests>
