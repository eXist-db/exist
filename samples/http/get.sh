#!/bin/bash

URL="http://localhost:8080/exist/rest/$1"

curl -i $URL
