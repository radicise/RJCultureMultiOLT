#!/bin/sh
set -eu
rm -f JCRoot/*.class
rm -f JCRoot/game/*.class
rm -f JCRoot/menu/*.class
javac JCRoot/Host.java
java JCRoot/Host "$@"
