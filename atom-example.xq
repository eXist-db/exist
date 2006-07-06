<feed xmlns="http://www.w3.org/2005/Atom" xmlns:atom="http://www.w3.org/2005/Atom">
   <title>All Feeds Example</title>
   <id>id:all</id>
   {
     "&#x0a;",
     let $current := substring-before(base-uri(/atom:feed),'/.feed.atom'),
         $current-path := substring-after($current,'/db')
        for $i in (collection($current)/atom:feed)
           let $path :=  substring-after(substring-before(base-uri($i),'/.feed.atom'),'/db')
              return ( <entry>
                        {
                           "&#x0a;",
                           $i/atom:id,
                           "&#x0a;",
                           $i/atom:title,
                           "&#x0a;",
                           <summary> {
                               $i/atom:subtitle/@type,
                               $i/atom:subtitle/node()
                           }</summary>,
                           "&#x0a;",
                           <link rel="alternate" type="application/atom+xml" href="/atom/edit{$path}"/> ,
                           "&#x0a;"
                       }
                       </entry>,
                       "&#x0a;"
                     )
   }
</feed>

