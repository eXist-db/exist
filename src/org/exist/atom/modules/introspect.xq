declare namespace atom="http://www.w3.org/2005/Atom";
<service xmlns="http://www.w3.org/2007/app">
  {
   if (/atom:feed)
   then
   <workspace>
   { 
     /atom:feed/atom:title,
     "&#x0a;",
     let $current := substring-before(base-uri(/atom:feed),'/.feed.atom'),
         $current-path := substring-after($current,'/db')
        for $i in (collection($current)/atom:feed) 
           let $path :=  substring-after(substring-before(base-uri($i),'/.feed.atom'),'/db')
              return ( <collection href="/atom/edit{$path}">
                         { $i/atom:title }
                         <accept>text/*,image/*,application/*</accept>
                       </collection>,
                       "&#x0a;"
                     )
   }
   </workspace>
   else ()
  }
</service>