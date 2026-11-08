window.BENCHMARK_DATA = {
  "lastUpdate": 1794144948991,
  "repoUrl": "https://github.com/eXist-db/exist",
  "entries": {
    "exist-indexes-jmh": [
      {
        "commit": {
          "author": {
            "name": "Duncan Paterson",
            "username": "duncdrum",
            "email": "duncdrum@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a8afa8f2485c59748d851d798281cfa659a4e566",
          "message": "Merge pull request #6475 from joewiz/feat/external-var-default-override\n\n[bugfix] External variable: supplied value overrides declared default",
          "timestamp": "2026-08-19T08:41:10Z",
          "url": "https://github.com/eXist-db/exist/commit/a8afa8f2485c59748d851d798281cfa659a4e566"
        },
        "date": 1787132766582,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 8.804973288979285,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 8.751569656381408,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 8.68558109153129,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.9126941106436994,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.9011330763285381,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.8884633955592485,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 10.820950214414077,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 104.48611874426902,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 206.8982504018182,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 11.536157145080356,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 102.00988573283207,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 202.55270450000003,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 1.4785359478163034,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 1.4745116982369382,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 1.43565838129124,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 1.424466212319895,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 1.4227753569596873,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 1.3920730944161892,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 5.608887253199219,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 47.84475322393287,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 96.35276150835497,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 5.835497296113558,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 50.74279326328321,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 99.42449889419913,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.6189439290369102,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.6355463144531635,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.5923380411333334,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.5091890895168112,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.5426921435135976,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.5096945163168537,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.6545563452410534,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 11.103200760347177,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 21.22731747184401,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.639821417762758,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 10.88156948747292,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 21.45273385493868,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.8602539026399793,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.8907601486392569,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.873147297742762,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.8351316904129146,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.8588019648047853,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.8182546751175053,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 2.149753710927206,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 16.825141530719353,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 30.471817514408674,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 2.1040557307439256,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 15.730373940354841,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 30.58961955017559,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.5481689402652772,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.5518291566143193,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.5410816889794852,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.48954577500982455,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.4866966307376462,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.49274746214588705,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 0.7529923184460372,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 2.9335511396352247,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 5.1697724476676425,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 0.676793975229273,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 2.899034721214361,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 5.09016209273465,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Duncan Paterson",
            "username": "duncdrum",
            "email": "duncdrum@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "966ad039233fe46effd26d9055a82f7b194f0db6",
          "message": "Merge pull request #6497 from joewiz/bugfix/xmldb-store-binary-xml-mime-npe\n\n[bugfix] xmldb:store: parse binary content stored under an XML mime type",
          "timestamp": "2026-08-20T17:04:29Z",
          "url": "https://github.com/eXist-db/exist/commit/966ad039233fe46effd26d9055a82f7b194f0db6"
        },
        "date": 1787465636504,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 6.22104868376272,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 6.245853695920194,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 6.355347003357898,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.7345653482318664,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.7392342971212562,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.7212767476604673,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 7.856775222896803,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 73.6658799910053,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 147.66388858241757,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 7.843577295272017,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 73.67996572802198,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 149.79062194761906,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 1.2737057174249782,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 1.2752938344261946,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 1.3337237566506541,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 1.2288342215557255,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 1.2283758786618517,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 1.2496310198050122,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 4.335223622163596,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 37.04832476007208,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 72.04047968468964,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 4.491291778444783,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 38.20890894039108,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 74.246409623879,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.5010802435242233,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.49815070956103896,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.5163796630845013,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.4416577274955394,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.4127834362679841,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.42675815864798644,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.5168166435682249,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 9.552413794849517,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 18.558918622937355,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.4627155512460202,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 9.399640307120398,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 18.208722037240943,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.7063957644402563,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.7089347844829558,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.702000642283175,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.6369795814092687,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.6429873878659981,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.6425923380820023,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.6403319991509846,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 10.952609817565062,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 20.787277585767555,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.6430923864799005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 11.383155159412883,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 22.07739889214047,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.4247607570673931,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.39749899977229064,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.39381116071918004,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.3651565504874888,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.37390381402296946,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.3702255516262532,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 0.6042600409640846,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 2.4232422236084203,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 4.208361095518238,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 0.5921820656467893,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 2.3456898378819164,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 4.113680752456105,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Dannes Wessels",
            "username": "dizzzz",
            "email": "dizzzz@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "d0b6f3a981455a38e88ff3da1ec7d68c168e3e17",
          "message": "Merge pull request #6659 from eXist-db/dependabot/maven/greenmail.version-2.1.13\n\nBump greenmail.version from 2.1.12 to 2.1.13",
          "timestamp": "2026-08-27T17:15:28Z",
          "url": "https://github.com/eXist-db/exist/commit/d0b6f3a981455a38e88ff3da1ec7d68c168e3e17"
        },
        "date": 1788088798903,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 8.52821453542337,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 8.718486264612897,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 8.815855667190707,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.8568391418325101,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.900971112967816,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.8685770562228345,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 10.845822681030835,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 102.00384894285715,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 201.08813711636364,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 10.455484477582297,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 103.27264793539682,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 200.65121644545457,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 1.4599886883408169,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 1.4808059232250859,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 1.45268375169708,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 1.4251499808258392,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 1.4252760620147582,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 1.444303079314466,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 5.622777956562503,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 48.23440432636386,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 93.24085009205722,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 5.582314111143772,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 50.368889564280174,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 99.94172457701754,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.6209704519747301,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.5986771753323624,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.5960521417668905,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.531643918887528,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.5065473932750476,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.5313655982157612,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.677421826292005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 10.773669285552069,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 20.994374252650246,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.6552583804469154,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 10.842689148115943,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 21.130326201873153,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.8472397988580141,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.8448869938322459,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.83418108937685,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.7646385656617782,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.801263178467581,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.8041309860868407,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 2.0172119304313716,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 15.69107107052534,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 28.91659158073532,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 2.0939388339675267,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 15.721071123574314,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 30.267210684271287,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.5394169895516068,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.5334688282104768,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.5297823845767786,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.4930053733309879,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.47656266517000534,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.4849635865803525,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 0.7031685492312783,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 2.936126062461647,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 5.054360820956409,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 0.7136668235619126,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 2.944128463038953,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 5.056779615605765,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Dannes Wessels",
            "username": "dizzzz",
            "email": "dizzzz@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "efc7873121c87ee695c5b9abd6d0255996254dac",
          "message": "Merge pull request #6673 from eXist-db/dependabot/maven/maven-plugins-apache-af29a0722d",
          "timestamp": "2026-09-06T06:51:18Z",
          "url": "https://github.com/eXist-db/exist/commit/efc7873121c87ee695c5b9abd6d0255996254dac"
        },
        "date": 1788689771231,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 9.135692605525925,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 8.714951118339695,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 8.901669418311863,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.8605374569609946,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.8762931480115315,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.8453408104991469,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 10.917442435702903,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 102.57885786523809,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 211.48974993333331,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 11.055256741420383,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 101.67326039974938,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 191.17157012727273,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 1.4515796480420864,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 1.4858273569752811,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 1.5046064461512754,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 1.449958366609467,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 1.4627336704682452,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 1.418317370671276,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 5.401414114055324,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 51.841885660395086,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 94.85507537556936,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 5.4767384478831875,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 50.020085809747336,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 96.13141706995671,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.6102772994996575,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.5939710467195776,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.6086672826041761,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.5474929752142041,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.5373238307705749,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.531621860972829,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.7034106115592054,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 11.140183851811294,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 21.263014861350804,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.743047972352619,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 11.09422726608569,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 21.287000249523935,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.8753177011701905,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.8960590878492447,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.8654623174580106,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.8088531997038316,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.8537961021984757,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.8333216395724232,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 2.064200668220314,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 16.05858786009356,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 30.547056970231278,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 2.3204407870566666,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 15.981647378788725,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 32.09280722969636,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.5385361319219093,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.5235089139435327,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.5328484118230092,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.4880571635448628,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.49451699615741473,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.5043981782180997,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 0.7054709586234532,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 3.108419412547298,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 5.0622665214570475,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 0.6675073279442554,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 3.1190575249047674,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 5.191680167027033,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Duncan Paterson",
            "username": "duncdrum",
            "email": "duncdrum@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "43da0ab7ba57c6042ed9f75ce7f81f748f55c7c2",
          "message": "Merge pull request #6666 from AzazelSensei/feature/xmldb-create-collection-recursion",
          "timestamp": "2026-09-12T20:07:11Z",
          "url": "https://github.com/eXist-db/exist/commit/43da0ab7ba57c6042ed9f75ce7f81f748f55c7c2"
        },
        "date": 1789295687137,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 7.17151290420963,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 7.103795480232658,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 7.103027939091083,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.6455020440254946,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.6413573202396977,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.6221531669635227,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 8.64249178394205,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 82.529216941,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 163.76728462307693,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 8.846964691032344,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 82.048749601,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 163.09026863076923,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 1.155920279766707,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 1.1549684408585965,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 1.1419777581589752,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 1.1242636440942793,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 1.0956618166957042,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 1.1031522084734677,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 4.2695325438133,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 39.11125044829874,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 75.42405476066341,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 4.403324077732347,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 39.81794344699896,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 76.5740409788148,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.4221378491687398,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.41683621832029355,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.42372862432204605,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.3576961727249135,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.37015684660804143,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.3523006381771726,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.3249421218356179,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 8.83984846282577,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 17.107536746293214,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.309189518654001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 8.896587197908056,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 16.954237463829756,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.6246967203087382,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.6344986619527704,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.6161269194167938,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.5941955185092969,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.5896876021395772,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.594102344091493,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.5852974748947861,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 12.464817790860723,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 23.23655728086073,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.634376218714086,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 12.729846502942156,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 24.70066890489939,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.38262537090654786,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.37322230087998937,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.3851021000757181,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.331170249112444,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.3387590112125472,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.34754713408964005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 0.496757108951297,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 2.2384148655284974,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 4.108201413557227,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 0.4745842886250333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 2.167319537121937,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 3.968056862495056,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Duncan Paterson",
            "username": "duncdrum",
            "email": "duncdrum@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "e9a0834880afac6078f5d5b7d2ce6cf11e179c92",
          "message": "Merge pull request #6727 from eXist-db/dependabot/maven/com.microsoft.onnxruntime-onnxruntime-1.30.0\n\nBump com.microsoft.onnxruntime:onnxruntime from 1.29.0 to 1.30.0",
          "timestamp": "2026-09-18T14:45:27Z",
          "url": "https://github.com/eXist-db/exist/commit/e9a0834880afac6078f5d5b7d2ce6cf11e179c92"
        },
        "date": 1789901487334,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 7.192313447117591,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 7.057232517380561,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 7.1520355167161345,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.6396195005187829,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.6601159509465132,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.6472123404203358,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 9.043310387902462,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 83.35919019800001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 164.98336973717946,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 8.193236600442523,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 81.480157187,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 164.50049038076924,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 1.16785799196655,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 1.1357539222067092,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 1.1723761453562962,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 1.1332432811879574,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 1.1253596704145803,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 1.1264600152520614,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 4.289362909233473,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 38.43571806523743,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 76.09344257958892,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 4.456113928705582,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 41.03480805562657,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 80.65006527452545,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.4226107781430217,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.42833494398518807,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.42921743923270805,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.35955409340166233,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.36397671960640593,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.3701865666593182,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.342117373621011,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 8.857697479421345,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 17.013538407498384,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.3203762425151566,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 8.852101844583732,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 17.306823790802753,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.65517139467027,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.6407788604253983,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.647006797476261,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.6043466142401559,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.5873912620505125,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.592817161902281,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.6476549569433314,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 12.337892401462005,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 23.52442698640275,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.6762707848327552,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 12.92829602200953,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 24.807941828677354,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.3843020809626857,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.3765837545307273,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.3979634178875801,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.34518341218161364,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.3371644953797721,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.3258876663827251,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 0.5052128795316526,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 2.3048241200148856,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 4.229678618483211,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 0.48014694559552495,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 2.178152547276852,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 4.209521561306704,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Duncan Paterson",
            "username": "duncdrum",
            "email": "duncdrum@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "218df41e8be6ad7713524a6fe4b89f944cc43143",
          "message": "Merge pull request #6765 from joewiz/bugfix/stored-pi-name\n\n[bugfix] Give stored processing instructions their name",
          "timestamp": "2026-09-26T10:44:11Z",
          "url": "https://github.com/eXist-db/exist/commit/218df41e8be6ad7713524a6fe4b89f944cc43143"
        },
        "date": 1790508960113,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 6.959165182462239,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 7.139230213807792,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 7.096297553204084,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.6259172217449555,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.627618377781358,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.6202845923695101,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 8.57987577295339,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 84.07622582191304,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 166.2851531551282,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 8.640627744578815,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 82.43031902370569,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 164.42352560128205,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 1.1473899144177702,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 1.1408804148309342,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 1.1442093255139298,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 1.1249305484616212,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 1.131326462005683,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 1.1309411348886311,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 4.376098690958778,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 38.88358083266142,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 75.48068494560846,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 4.5900576704362726,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 39.85528147670396,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 78.32382600668896,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.43590774478410904,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.427434521066688,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.4253167814226437,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.3718166945388682,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.35896267499999385,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.3635039272303511,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.3370971541959924,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 8.956359477189597,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 17.024696621614822,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.322759710641264,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 8.907514736269022,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 17.040473320531753,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.6351589774427661,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.654563445653339,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.6522726096000206,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.5849230368998282,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.6411461478553474,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.5870655878670419,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.6359389321060263,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 12.262099761856394,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 24.18727136879518,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.676009002594381,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 12.937700439620993,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 24.675935210664083,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.38074474141301334,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.3690701975850895,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.37854228914076826,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.32545899014229185,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.3593021361680173,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.3341659408276494,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 0.5144983461032943,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 2.25778211157595,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 4.086983353153974,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 0.4846219605072898,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 2.1737359887355403,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 3.992165936514735,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Duncan Paterson",
            "username": "duncdrum",
            "email": "duncdrum@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "c68693484d6d2625ee4df769d31f7ae952ecb48f",
          "message": "Merge pull request #6820 from joewiz/bugfix/6815-rest-in-query-json",
          "timestamp": "2026-10-09T18:31:23Z",
          "url": "https://github.com/eXist-db/exist/commit/c68693484d6d2625ee4df769d31f7ae952ecb48f"
        },
        "date": 1791721745696,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 8.655665327246401,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 8.676747257324108,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 8.708672023799206,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.9226885728131151,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.8667331232960074,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.8309831605595169,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 10.788269504207582,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 103.80890397894737,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 205.5727520218182,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 11.05035393754691,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 102.97778794736841,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 207.53746008363638,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 1.4662498063191116,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 1.4878766749494328,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 1.4954776732541402,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 1.4545478413470476,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 1.465821143424963,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 1.4402202669349184,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 5.305712558203495,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 47.84093742539941,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 97.70598959346401,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 5.78744706257166,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 50.7069799323349,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 98.56256527238095,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.6398975890097084,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.6434982824753848,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.624841726746552,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.5298903365964718,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.5472385548785913,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.5458016734618548,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.6901447859098884,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 10.890202165475612,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 21.16912729134617,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.627561961037357,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 10.95837653032673,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 21.025128223503778,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.8717519961766971,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.8388737260429538,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.8916553704922137,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.8181009367437785,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.8088655003510821,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.856046015702472,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 2.0706510641762366,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 15.142994433793888,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 29.43550789757893,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 2.1302790491208086,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 16.411493059465435,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 30.552530983924406,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.5711284448676426,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.5083312234907117,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.5583204080699046,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.4757703680549178,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.47446016320571865,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.48743776634025887,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 0.7294472672549661,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 2.9661425065584117,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 5.302923960861867,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 0.6909335472750482,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 2.903981491518424,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 5.184307538895321,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Dannes Wessels",
            "username": "dizzzz",
            "email": "dizzzz@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "970242ae9855f7dc3fee11bcb5d7ab902e8a0d45",
          "message": "Merge pull request #6860 from duncdrum/dp-deprecate-stx\n\n[refactor] Deprecate the STX trigger and its templates cache for removal",
          "timestamp": "2026-10-17T17:20:29Z",
          "url": "https://github.com/eXist-db/exist/commit/970242ae9855f7dc3fee11bcb5d7ab902e8a0d45"
        },
        "date": 1792319552105,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.720306877852182,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.7217249599312214,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.7059713681300125,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.6671486199309766,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.6663157986972923,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.6674978668926357,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.1821086962686103,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 7.327315116581756,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 14.391969078561576,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.1653071032398896,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 7.918983171110884,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 14.42175663140969,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 2.2492267426983066,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 1.1332700169485208,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 50.02557923089431,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 7.175534967025759,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 213.76778353999998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 28.47397050862176,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 2.315356125809131,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 1.2251231513030594,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 51.747662397051286,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 7.330721016532728,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 216.71529198,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 26.891542516811068,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 2.230891782204279,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 2.272631418957429,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 48.70566995156795,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 49.58015557536585,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 209.72898302,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 207.69068752000004,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 2.3118637789620844,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 1.1239158139281518,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 48.90132767305458,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 6.732093243482926,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 208.96067826000004,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 26.962988338924607,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 0.5063055368610254,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 0.5194447132017324,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 5.418704771636184,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 5.384651038176492,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 21.81109824527794,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 21.906784858601362,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 1.735336710982331,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 1.72804748454216,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 44.46311848593324,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 44.78141350966183,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 187.28105881818183,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 185.03218439999998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 1.2039222585667235,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 1.2185313861178033,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 1.2046523494121224,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 1.1425102796005955,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 1.140033559149383,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 1.1453255742194197,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 4.458106460757198,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 39.499848562974925,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 76.02815102433863,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 4.509061338341697,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 39.747042892180254,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 77.450515705698,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.4165313392006955,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.4169056024096812,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.4293698879256686,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.3914768210372522,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.3821985628616805,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.3944057359031622,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.1970114397131701,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 7.404023554009977,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 13.72094223125749,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.1971809811255911,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 7.294326151710761,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 13.86042178646295,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.6888544125577379,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.6714568415864465,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.6793849498855853,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.6156230692124435,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.6230031020660938,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.6312395837431852,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.6329993696644691,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 12.497771925782574,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 23.70555207553832,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.6831086052166846,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 12.782805594993297,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 24.825931133112768,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.3999710666882385,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.4027929853153613,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.39705477330086825,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.3619549847590123,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.3535907525851248,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.3505476402984747,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 0.5280229229601996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 2.2619687567909295,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 4.07509353487702,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 0.5157949887597171,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 2.357426101345163,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 3.991708612893312,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Juri Leino",
            "username": "line-o",
            "email": "github@line-o.de"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "3f9b349bb2dd4fd2209bd2320c0db9407eadb031",
          "message": "Merge pull request #6905 from duncdrum/dp-ci-jacoco-merged-artifact\n\n[ci] Publish the JaCoCo reports as a develop artifact",
          "timestamp": "2026-10-24T17:44:30Z",
          "url": "https://github.com/eXist-db/exist/commit/3f9b349bb2dd4fd2209bd2320c0db9407eadb031"
        },
        "date": 1792926078038,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.7297185799457224,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.7196258567782085,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.7163688097389544,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.6601227983557209,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.6789763657048611,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.6524464614554752,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.1317909413620242,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 7.624541142681169,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 14.167686766663271,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.1148817208037218,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 7.518031114345786,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 14.319380063138857,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeDirect ( {\"corpusCopies\":\"1\"} )",
            "value": 0.407405713337622,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeDirect ( {\"corpusCopies\":\"10\"} )",
            "value": 0.6030281309480566,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeDirect ( {\"corpusCopies\":\"40\"} )",
            "value": 1.3292230309481539,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSource ( {\"corpusCopies\":\"1\"} )",
            "value": 0.597614217877496,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSource ( {\"corpusCopies\":\"10\"} )",
            "value": 1.9800998368043061,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSource ( {\"corpusCopies\":\"40\"} )",
            "value": 7.668008075371621,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceAbbreviated ( {\"corpusCopies\":\"1\"} )",
            "value": 0.6372235309257206,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceAbbreviated ( {\"corpusCopies\":\"10\"} )",
            "value": 2.12400263333357,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceAbbreviated ( {\"corpusCopies\":\"40\"} )",
            "value": 7.811117729515115,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceExtraPredicate ( {\"corpusCopies\":\"1\"} )",
            "value": 1.9746313727837346,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceExtraPredicate ( {\"corpusCopies\":\"10\"} )",
            "value": 13.781365066967954,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceExtraPredicate ( {\"corpusCopies\":\"40\"} )",
            "value": 68.77360278781609,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 2.3206187430606917,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 1.0966983948800677,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 51.81385667272605,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 7.148622734562762,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 217.0558708,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 28.05940932047813,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 2.3807791274817918,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 1.2958534136132756,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 52.01353513188842,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 6.907133535153248,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 215.1706263,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 27.02989004315587,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 2.2798999607001713,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 2.2608800017351545,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 49.9093698802439,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 49.426302648054595,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 208.24377488,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 213.98871184,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 2.28487744741754,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 1.1736090318400858,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 50.63519075615384,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 7.033199733143057,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 225.31745660222222,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 27.256423130383602,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 0.5050639518370293,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 0.49732339517331015,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 5.498002562147993,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 5.353518912619364,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 22.40720159285518,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 22.82405478498607,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 1.756585890632524,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 1.7549394367237372,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 44.92894438318841,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 44.50542391731665,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 191.30484656363637,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 188.2652964181818,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 1.2065042689064742,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 1.1846692566785255,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 1.2081099899942853,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 1.1521685753046635,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 1.1495855618145978,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 1.1597663105145355,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 4.428119672702392,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 39.07911917042202,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 75.54728653778997,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 4.652246721730602,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 41.04114862974201,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 79.34941871196581,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.432886717289698,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.4386333431675259,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.4265548088244241,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.3922765031011983,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.38708360821515664,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.37524020848994716,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.227464193582437,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 7.377386932247175,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 13.827994431506848,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.2024434557983048,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 7.467748798737735,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 14.06881783606153,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.6663946642227332,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.6856872646994077,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.6701285350337773,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.6168184659913292,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.6434277835023094,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.6246413565587917,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.6890825021492994,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 12.327197621973346,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 23.82345997090642,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.6980473847752067,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 12.80543525939577,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 25.485835365000003,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.3980329935780756,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.40102609123634886,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.4003560868343129,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.3634416828822611,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.3634531168299564,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.3560225122160391,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 0.5296708078913066,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 2.246174276734346,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 4.088824915506254,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 0.5244603835297891,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 2.2255194878638678,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 3.9695242159680055,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Juri Leino",
            "username": "line-o",
            "email": "github@line-o.de"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "962b3941e7c879d83795c2fe21c9ed7cbf71429b",
          "message": "Merge pull request #6941 from joewiz/optimize/6895-direct-attribute-select",
          "timestamp": "2026-10-30T18:25:23Z",
          "url": "https://github.com/eXist-db/exist/commit/962b3941e7c879d83795c2fe21c9ed7cbf71429b"
        },
        "date": 1793538182543,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.4314761136881562,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.4623099410722971,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.45447447237198474,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.4219056011422345,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.4101192838892046,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.4256244385792794,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 0.6904407080114194,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 5.043142965860353,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 10.466910015255522,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 0.7283740819312474,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 5.40431681088082,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 10.412887267001187,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeDirect ( {\"corpusCopies\":\"1\"} )",
            "value": 0.24900847708004492,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeDirect ( {\"corpusCopies\":\"10\"} )",
            "value": 0.3702493256553486,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeDirect ( {\"corpusCopies\":\"40\"} )",
            "value": 0.8560090718835051,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSource ( {\"corpusCopies\":\"1\"} )",
            "value": 0.3548898607057327,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSource ( {\"corpusCopies\":\"10\"} )",
            "value": 1.3862925877327532,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSource ( {\"corpusCopies\":\"40\"} )",
            "value": 6.208753789888265,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceAbbreviated ( {\"corpusCopies\":\"1\"} )",
            "value": 0.3955527412486185,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceAbbreviated ( {\"corpusCopies\":\"10\"} )",
            "value": 1.4462848960863148,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceAbbreviated ( {\"corpusCopies\":\"40\"} )",
            "value": 6.242260586645146,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceExtraPredicate ( {\"corpusCopies\":\"1\"} )",
            "value": 1.2441188083566508,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceExtraPredicate ( {\"corpusCopies\":\"10\"} )",
            "value": 9.863079651237493,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceExtraPredicate ( {\"corpusCopies\":\"40\"} )",
            "value": 56.32985764164458,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 1.7753724372620439,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 0.9095915527201048,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 41.308187291543206,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 6.690804658450604,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 178.89016400909088,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 26.690548946210527,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 1.8984363505942041,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 1.056152684070915,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 46.2325557965487,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 7.11176151334129,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 197.20405394545452,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 27.594153825510553,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 1.882968317159775,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 1.8153402340089904,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 42.63628042251317,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 44.557450737662954,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 187.2821084909091,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 191.72867007272728,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 1.867763388022062,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 0.9471566906140436,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 43.49207528799465,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 6.737813122913695,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 186.39374232727275,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 26.721689797261337,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 0.37290275364236003,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 0.35626445613666785,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 4.928690355944822,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 4.4455934173525335,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 18.28624166238236,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 19.027958400315654,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 1.33572509995824,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 1.390259994776351,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 36.75523974496658,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 37.6632152626052,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 154.56289784725274,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 156.82844882857142,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.8002407020869846,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.7935062585784676,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.7965823980263176,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.7685744958733858,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.7536866664400597,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.7681989974622214,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 3.1805734397754537,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 28.010510857173585,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 54.82453941265212,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 3.1607184121750387,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 29.65565294308298,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 57.480439532315586,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.2714571809372222,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.27755327091860804,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.2787240953688355,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.25985124327611897,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.251565793582646,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.25088573676721343,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 0.785515647443128,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 5.204667721029801,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 10.037746764104046,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 0.7409881947581676,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 4.8158349487264385,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 9.46008276758665,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.4159442830864717,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.4093164110739149,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.4101489535216512,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.3998686074584028,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.40705649669317207,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.40881409571631905,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.1292499834724068,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 9.36011608270771,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 17.52013465356304,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.1314125003249331,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 9.40931756954167,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 18.318604394402236,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.2651964599522617,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.2661191539612451,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.25429528304219695,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.23971495144128988,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.23302862272308542,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.23712283749094998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 0.35046989767936354,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 1.6329279350506998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 3.1578212587895442,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 0.36287830264565213,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 1.6710485530172154,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 3.2502980587291574,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Juri Leino",
            "username": "line-o",
            "email": "github@line-o.de"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "5afeb9bf2d56fe13c6b5aaa5e3b141707915c033",
          "message": "Merge pull request #6777 from duncdrum/bugfix/ldap-hasaccount-recursion",
          "timestamp": "2026-11-07T12:06:09Z",
          "url": "https://github.com/eXist-db/exist/commit/5afeb9bf2d56fe13c6b5aaa5e3b141707915c033"
        },
        "date": 1794144947737,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.7077003411006205,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.7432082663532251,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.732122958582303,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.7109701059601956,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.7002659965651138,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.6577694552908865,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.1182166752770324,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 7.951829104733703,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 13.490530769840571,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.1524139954717278,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 7.369003433470363,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.GeneralComparisonWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 14.16509171491828,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeDirect ( {\"corpusCopies\":\"1\"} )",
            "value": 0.412725463212874,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeDirect ( {\"corpusCopies\":\"10\"} )",
            "value": 0.6510107560238116,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeDirect ( {\"corpusCopies\":\"40\"} )",
            "value": 1.3991379954436753,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSource ( {\"corpusCopies\":\"1\"} )",
            "value": 0.5956328031267566,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSource ( {\"corpusCopies\":\"10\"} )",
            "value": 2.0000375721977433,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSource ( {\"corpusCopies\":\"40\"} )",
            "value": 7.053192674825813,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceAbbreviated ( {\"corpusCopies\":\"1\"} )",
            "value": 0.6723897161499528,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceAbbreviated ( {\"corpusCopies\":\"10\"} )",
            "value": 2.018256397935599,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceAbbreviated ( {\"corpusCopies\":\"40\"} )",
            "value": 6.6841500467128,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceExtraPredicate ( {\"corpusCopies\":\"1\"} )",
            "value": 1.7787200291734404,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceExtraPredicate ( {\"corpusCopies\":\"10\"} )",
            "value": 12.277449027127892,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LetBoundSourceLuceneBenchmark.shapeLetBoundSourceExtraPredicate ( {\"corpusCopies\":\"40\"} )",
            "value": 71.31164119893899,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 1.7342017833848424,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 0.6224715332637314,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 48.35431406829268,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 2.4611638171936376,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 206.20259046,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLiteral ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 8.86680685401418,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 1.9201236275744897,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 0.7825807241697292,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 48.55553972729384,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 2.562827581027612,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 206.81863549999997,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVar ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 8.737386131547666,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 1.8380188070384533,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 1.9072515123425688,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 48.61448065528455,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 48.95504729644275,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 200.0491825872727,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateLoopVarUnoptimized ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 205.93664352000002,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 1.873017133258175,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 0.6875764354432117,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 48.23725683386814,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 2.493119055033193,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 203.44968448,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.predicateOuterVar ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 8.650638509399275,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 0.4673605516536531,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 0.4632365800246598,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 5.244260815230932,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 5.275662380411293,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 20.57960665830593,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereNavigating ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 21.228311878027284,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"100\",\"indexed\":\"false\"} )",
            "value": 1.7221755369398817,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"100\",\"indexed\":\"true\"} )",
            "value": 1.7611732974119576,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"1000\",\"indexed\":\"false\"} )",
            "value": 48.00427784863733,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"1000\",\"indexed\":\"true\"} )",
            "value": 48.05204266554034,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"4000\",\"indexed\":\"false\"} )",
            "value": 197.96174536363637,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LoopVariablePredicateBenchmark.whereVariableAsKey ( {\"docCount\":\"4000\",\"indexed\":\"true\"} )",
            "value": 205.44241618,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 1.3290443515951647,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 1.3220074781478366,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 1.3317394580183461,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 1.2857682584579302,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 1.2955117421578284,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 1.286209778100246,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 5.066711933937059,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 43.92751949093862,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 87.48994171920496,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 5.470798851581824,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 45.214581449035954,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.LuceneWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 87.91388061284583,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.manyMatchesPerNode ( {\"termCount\":\"5\"} )",
            "value": 9684.801570200001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.manyMatchesPerNode ( {\"termCount\":\"50\"} )",
            "value": 9707.1893908,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.manyMatchesPerNode ( {\"termCount\":\"100\"} )",
            "value": 9657.696752400001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.45785040995459003,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.44997173017484593,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.44708945951032647,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.42184116616916967,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.4026112793522455,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.40393178036108246,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.2830914697466382,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 8.126085841393454,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 15.207243231127094,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.2503480522752715,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 8.061577660722893,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.NgramWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 15.51745159993089,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.723472322379126,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.7137438541924719,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.6826494047457647,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.6460668145018708,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.6541268974254761,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.6409214350987061,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 1.672759484033652,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 12.393327554363514,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 24.114136483905966,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 1.7642243240767002,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 14.14115953113242,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 25.614599092870368,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"5\"} )",
            "value": 0.3889748923392457,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"50\"} )",
            "value": 0.38631381080949717,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALetVar ( {\"termCount\":\"100\"} )",
            "value": 0.3717947151895172,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"5\"} )",
            "value": 0.32995487114813427,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"50\"} )",
            "value": 0.33287327466811384,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeALiteral ( {\"termCount\":\"100\"} )",
            "value": 0.3626079724311889,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"5\"} )",
            "value": 0.512036806609993,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"50\"} )",
            "value": 2.284507565943027,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate ( {\"termCount\":\"100\"} )",
            "value": 4.140187635000844,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"5\"} )",
            "value": 0.49948520396226737,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"50\"} )",
            "value": 2.2502308344320534,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere ( {\"termCount\":\"100\"} )",
            "value": 4.165388605817389,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.TypedArgumentSourceLuceneBenchmark.countWrapped",
            "value": 10.12604024039393,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.jmh.TypedArgumentSourceLuceneBenchmark.direct",
            "value": 4.31422098959825,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ]
  }
}