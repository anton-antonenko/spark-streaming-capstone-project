#!/usr/bin/env bash

cd "$(dirname "${BASH_SOURCE[0]}")"
./sbt clean assembly -no-colors -batch --addPluginSbtFile=$PWD/project/plugins.sbt