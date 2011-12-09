#!/bin/sh

NAME=iplant-notificationagent-`cat *.spec | grep Version | cut -d ' ' -f 2` 
BUILD=build/$NAME

if [ -d build ]; then
    rm -rf build
fi

mkdir -p $BUILD
cp -r src $BUILD
cp -r conf $BUILD
cp iplant-notificationagent.spec $BUILD

pushd build
tar czf $NAME.tar.gz $NAME/
popd