<feed xmlns="http://www.w3.org/2005/Atom" 
xmlns:atom="http://www.w3.org/2005/Atom">
   { 
     "&#x0a;",
     /atom:feed/atom:title,
     "&#x0a;",
     /atom:feed/atom:id,
     "&#x0a;",
     /atom:feed/atom:updated,
     "&#x0a;"
   }
   {
      let $current := substring-before(base-uri(/atom:feed),'.feed.atom')
         return (
            <link rel="alternate" href="../content{substring-after($current,'/db')}" type="application/atom+xml"/>,
           "&#x0a;",
            for $i in (collection($current)/atom:feed) 
               let $path :=  substring-before(base-uri($i),'.feed.atom')
                  return if ($current!=$path) 
                     then (<entry>
                            {
                                "&#x0a;",
                                $i/atom:id,
                                "&#x0a;",
                                $i/atom:title,
                                "&#x0a;",
                                $i/atom:updated,
                                "&#x0a;",
                                <atom scheme="http://www.smallx.com/Ontology/Atopic/2006/1/0/topic/relation" term="subtopic"/>,
                                "&#x0a;",
                                <link rel="alternate" href="../content{substring-after($path,'/db')}" type="application/atom+xml"/>,
                                "&#x0a;",
                                <atom:summary>
                                     {
                                         $i/atom:subtitle/@type,
                                         $i/atom:subtitle/node()
                                     }
                                </atom:summary>,
                                "&#x0a;"
                             }
                            </entry>,
                            "&#x0a;"
                            )
                     else ()
         )
   }
</feed>