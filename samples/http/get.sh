#!/bin/bash

URL="http://localhost:8080/exist/servlet/$1"

curl -i $URL
