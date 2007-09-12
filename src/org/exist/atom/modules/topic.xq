declare namespace atom="http://www.w3.org/2005/Atom";
<feed xmlns="http://www.w3.org/2005/Atom">
   { 
     "&#x0a;",
     /atom:feed/atom:title,
     "&#x0a;",
     /atom:feed/atom:id,
     "&#x0a;",
     /atom:feed/atom:updated,
     "&#x0a;",
     /atom:feed/atom:entry[atom:category[concat(@scheme,@term)='http://www.atomojo.org/ontology/relation/subtopic']],
     "&#x0a;",
     /atom:feed/atom:link
   }
   {
      let $current := substring-before(base-uri(/atom:feed),'/.feed.atom'),
           $current-path := substring-after($current,'/db')
         return (
            <link rel="alternate" href="/atom/content{substring-after($current,'/db')}" type="application/atom+xml"/>,
           "&#x0a;",
            let $parts := tokenize($current-path,'/')
               for $i in (1 to count($parts)-1)
                   let $apath := string-join(subsequence($parts,1,$i),'/'),
                       $feed := xmldb:document(concat($apath,'/.feed.atom'))/atom:feed
                     return if ($feed and not($feed/atom:category[concat(@scheme,@term)='http://www.atomojo.org/ontology/display' and @value='none']))
                       then (<entry>
                            {
                                "&#x0a;",
                                $feed/atom:id,
                                "&#x0a;",
                                $feed/atom:title,
                                "&#x0a;",
                                $feed/atom:updated,
                                "&#x0a;",
                                $feed/atom:author,
                                "&#x0a;",
                                <category scheme="http://www.atomojo.org/ontology/relation/" term="ancestor"/>,
                                "&#x0a;",
                                <link rel="alternate" href="/atom/content/{$apath}" type="application/atom+xml"/>,
                                "&#x0a;",
                                <summary>
                                     {
                                         $feed/atom:subtitle/@type,
                                         $feed/atom:subtitle/node()
                                     }
                                </summary>,
                                "&#x0a;"
                             }
                            </entry>,
                            "&#x0a;"
                            )
                       else (),
            for $i in (collection($current)/atom:feed) 
               let $path :=  substring-before(base-uri($i),'/.feed.atom'),
                   $prefix := concat($current,'/')
                  return if ($current!=$path and not(contains(substring-after($path,$prefix),'/'))
                             and not($i/atom:category[concat(@scheme,@term)='http://www.atomojo.org/ontology/display' and @value='none']))
                     then (<entry>
                            {
                                "&#x0a;",
                                $i/atom:id,
                                "&#x0a;",
                                $i/atom:title,
                                "&#x0a;",
                                $i/atom:updated,
                                "&#x0a;",
                                $i/atom:author,
                                "&#x0a;",
                                <category scheme="http://www.atomojo.org/ontology/relation/" term="subtopic"/>,
                                "&#x0a;",
                                <link rel="alternate" href="/atom/content{substring-after($path,'/db')}" type="application/atom+xml"/>,
                                "&#x0a;",
                                <summary>
                                     {
                                         $i/atom:subtitle/@type,
                                         $i/atom:subtitle/node()
                                     }
                                </summary>,
                                "&#x0a;"
                             }
                            </entry>,
                            "&#x0a;"
                            )
                     else ()
         )
   }
</feed>