/*==================================================
 *  Timeline API
 *
 *  This file will load all the Javascript files
 *  necessary to make the standard timeline work.
 *  It also detects the default locale.
 *
 *  To run from the MIT copy of Timeline:
 *  Include this file in your HTML file as follows:
 *
 *    <script src="http://static.simile.mit.edu/timeline/api-2.3.0/timeline-api.js" 
 *     type="text/javascript"></script>
 *
 *
 * To host the Timeline files on your own server:
 *   1) Install the Timeline and Simile-Ajax files onto your webserver using
 *      timeline_libraries.zip or timeline_source.zip
 * 
 *   2) Set global js variables used to send parameters to this script:
 *        Timeline_ajax_url -- url for simile-ajax-api.js
 *        Timeline_urlPrefix -- url for the *directory* that contains timeline-api.js
 *          Include trailing slash
 *        Timeline_parameters='bundle=true'; // you must set bundle to true if you are using
 *                                           // timeline_libraries.zip since only the
 *                                           // bundled libraries are included
 *      
 * eg your html page would include
 *
 *   <script>
 *     Timeline_ajax_url="http://YOUR_SERVER/javascripts/timeline/timeline_ajax/simile-ajax-api.js";
 *     Timeline_urlPrefix='http://YOUR_SERVER/javascripts/timeline/timeline_js/';       
 *     Timeline_parameters='bundle=true';
 *   </script>
 *   <script src="http://YOUR_SERVER/javascripts/timeline/timeline_js/timeline-api.js"    
 *     type="text/javascript">
 *   </script>
 *
 * SCRIPT PARAMETERS
 * This script auto-magically figures out locale and has defaults for other parameters 
 * To set parameters explicity, set js global variable Timeline_parameters or include as
 * parameters on the url using GET style. Eg the two next lines pass the same parameters:
 *     Timeline_parameters='bundle=true';                    // pass parameter via js variable
 *     <script src="http://....timeline-api.js?bundle=true"  // pass parameter via url
 * 
 * Parameters 
 *   timeline-use-local-resources -- 
 *   bundle -- true: use the single js bundle file; false: load individual files (for debugging)
 *   locales -- 
 *   defaultLocale --
 *   forceLocale -- force locale to be a particular value--used for debugging. Normally locale is determined
 *                  by browser's and server's locale settings.
 *================================================== 
 */

(function() {
    var useLocalResources = false;
    if (document.location.search.length > 0) {
        var params = document.location.search.substr(1).split("&");
        for (var i = 0; i < params.length; i++) {
            if (params[i] == "timeline-use-local-resources") {
                useLocalResources = true;
            }
        }
    };
    
    var loadMe = function() {
        if ("Timeline" in window) {
            return;
        }
        
        window.Timeline = new Object();
        window.Timeline.DateTime = window.SimileAjax.DateTime; // for backward compatibility
    
        var bundle = false;
        var javascriptFiles = [
            "timeline.js",
            "band.js",
            "themes.js",
            "ethers.js",
            "ether-painters.js",
            "event-utils.js",
            "labellers.js",
            "sources.js",
            "original-painter.js",
            "detailed-painter.js",
            "overview-painter.js",
            "compact-painter.js",
            "decorators.js",
            "units.js"
        ];
        var cssFiles = [
            "timeline.css",
            "ethers.css",
            "events.css"
        ];
        
        var localizedJavascriptFiles = [
            "timeline.js",
            "labellers.js"
        ];
        var localizedCssFiles = [
        ];
        
        // ISO-639 language codes, ISO-3166 country codes (2 characters)
        var supportedLocales = [
            "cs",       // Czech
            "de",       // German
            "en",       // English
            "es",       // Spanish
            "fr",       // French
            "it",       // Italian
            "nl",       // Dutch (The Netherlands)
            "ru",       // Russian
            "se",       // Swedish
            "tr",       // Turkish
            "vi",       // Vietnamese
            "zh"        // Chinese
        ];
        
        try {
            var desiredLocales = [ "en" ],
                defaultServerLocale = "en",
                forceLocale = null;
            
            var parseURLParameters = function(parameters) {
                var params = parameters.split("&");
                for (var p = 0; p < params.length; p++) {
                    var pair = params[p].split("=");
                    if (pair[0] == "locales") {
                        desiredLocales = desiredLocales.concat(pair[1].split(","));
                    } else if (pair[0] == "defaultLocale") {
                        defaultServerLocale = pair[1];
                    } else if (pair[0] == "forceLocale") {
                        forceLocale = pair[1];
                        desiredLocales = desiredLocales.concat(pair[1].split(","));                        
                    } else if (pair[0] == "bundle") {
                        bundle = pair[1] != "false";
                    }
                }
            };
            
            (function() {
                if (typeof Timeline_urlPrefix == "string") {
                    Timeline.urlPrefix = Timeline_urlPrefix;
                    if (typeof Timeline_parameters == "string") {
                        parseURLParameters(Timeline_parameters);
                    }
                } else {
                    var heads = document.documentElement.getElementsByTagName("head");
                    for (var h = 0; h < heads.length; h++) {
                        var scripts = heads[h].getElementsByTagName("script");
                        for (var s = 0; s < scripts.length; s++) {
                            var url = scripts[s].src;
                            var i = url.indexOf("timeline-api.js");
                            if (i >= 0) {
                                Timeline.urlPrefix = url.substr(0, i);
                                var q = url.indexOf("?");
                                if (q > 0) {
                                    parseURLParameters(url.substr(q + 1));
                                }
                                return;
                            }
                        }
                    }
                    throw new Error("Failed to derive URL prefix for Timeline API code files");
                }
            })();
            
            var includeJavascriptFiles = function(urlPrefix, filenames) {
                SimileAjax.includeJavascriptFiles(document, urlPrefix, filenames);
            }
            var includeCssFiles = function(urlPrefix, filenames) {
                SimileAjax.includeCssFiles(document, urlPrefix, filenames);
            }
            
            /*
             *  Include non-localized files
             */
            if (bundle) {
                includeJavascriptFiles(Timeline.urlPrefix, [ "timeline-bundle.js" ]);
                includeCssFiles(Timeline.urlPrefix, [ "timeline-bundle.css" ]);
            } else {
                includeJavascriptFiles(Timeline.urlPrefix + "scripts/", javascriptFiles);
                includeCssFiles(Timeline.urlPrefix + "styles/", cssFiles);
            }
            
            /*
             *  Include localized files
             */
            var loadLocale = [];
            loadLocale[defaultServerLocale] = true;
            
            var tryExactLocale = function(locale) {
                for (var l = 0; l < supportedLocales.length; l++) {
                    if (locale == supportedLocales[l]) {
                        loadLocale[locale] = true;
                        return true;
                    }
                }
                return false;
            }
            var tryLocale = function(locale) {
                if (tryExactLocale(locale)) {
                    return locale;
                }
                
                var dash = locale.indexOf("-");
                if (dash > 0 && tryExactLocale(locale.substr(0, dash))) {
                    return locale.substr(0, dash);
                }
                
                return null;
            }
            
            for (var l = 0; l < desiredLocales.length; l++) {
                tryLocale(desiredLocales[l]);
            }
            
            var defaultClientLocale = defaultServerLocale;
            var defaultClientLocales = ("language" in navigator ? navigator.language : navigator.browserLanguage).split(";");
            for (var l = 0; l < defaultClientLocales.length; l++) {
                var locale = tryLocale(defaultClientLocales[l]);
                if (locale != null) {
                    defaultClientLocale = locale;
                    break;
                }
            }
            
            for (var l = 0; l < supportedLocales.length; l++) {
                var locale = supportedLocales[l];
                if (loadLocale[locale]) {
                    includeJavascriptFiles(Timeline.urlPrefix + "scripts/l10n/" + locale + "/", localizedJavascriptFiles);
                    includeCssFiles(Timeline.urlPrefix + "styles/l10n/" + locale + "/", localizedCssFiles);
                }
            }
            
            if (forceLocale == null) {
              Timeline.serverLocale = defaultServerLocale;
              Timeline.clientLocale = defaultClientLocale;
            } else {
              Timeline.serverLocale = forceLocale;
              Timeline.clientLocale = forceLocale;
            }            	
        } catch (e) {
            alert(e);
        }
    };
    
    /*
     *  Load SimileAjax if it's not already loaded
     */
    if (typeof SimileAjax == "undefined") {
        window.SimileAjax_onLoad = loadMe;
        
        var url = useLocalResources ?
            "http://127.0.0.1:9999/ajax/api/simile-ajax-api.js?bundle=false" :
            "http://static.simile.mit.edu/ajax/api-2.2.0/simile-ajax-api.js";
        if (typeof Timeline_ajax_url == "string") {
           url = Timeline_ajax_url;
        }
        var createScriptElement = function() {
            var script = document.createElement("script");
            script.type = "text/javascript";
            script.language = "JavaScript";
            script.src = url;
            document.getElementsByTagName("head")[0].appendChild(script);
        }
        if (document.body == null) {
            try {
                document.write("<script src='" + url + "' type='text/javascript'></script>");
            } catch (e) {
                createScriptElement();
            }
        } else {
            createScriptElement();
        }
    } else {
        loadMe();
    }
})();
