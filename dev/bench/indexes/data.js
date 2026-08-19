window.BENCHMARK_DATA = {
  "lastUpdate": 1787132767565,
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
      }
    ]
  }
}