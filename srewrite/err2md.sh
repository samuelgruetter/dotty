#!/bin/sh

grep raw-lib-compile-errors.txt -e ': error:' | sed -e 's/\(.*\/\([^/]*\)\): error: \(.*\)/*    \3 [\2](\1)/g' | sort | sed -e 's/\.\.\/scala/https:\/\/github.com\/scala\/scala\/tree\/2.10.x/g' | sed -e 's/\.scala:/.scala#L/g'

# grep raw-lib-compile-errors.txt -e ': error:' | sed -e 's/\(.*\): error: \(.*\)/*    \2 [link](\1)/g' | sort | sed -e 's/\.\.\/scala/https:\/\/github.com\/scala\/scala\/tree\/2.10.x/g' | sed -e 's/\.scala:/.scala#L/g'

