#!/bin/bash

./gradlew publishToMavenLocal --no-daemon
cd sample-app
./gradlew compileKotlin compileTestKotlin
./gradlew checkDependencies
./gradlew checkDependencies -PfullReport=true