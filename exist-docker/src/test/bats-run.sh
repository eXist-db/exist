#!/usr/bin/env

set -xe

parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )

cd "$parent_path"

bats -t ./bats/*.bats
