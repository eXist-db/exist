window.BENCHMARK_DATA = {
  "lastUpdate": 1787465637696,
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
      }
    ]
  }
}