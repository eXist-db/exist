(: not used :)
let $docs := for $doc in collection($collection)
return document-uri($doc) for $doc in $docs
where ends-with($doc, '.dtd') return $doc