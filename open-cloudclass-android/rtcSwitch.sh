#!/usr/bin/env sh

isEmptyParameter() {
    if [ ! -n "$1" ] ;then
        return 0
    else
        return 1
    fi
}

replaceRte() {
    # use rte-sdk to build project
    if [ $1 = "arsenal" ]; then
        cp -f -r ./rte-arsenal ./AgoraEduCore/src/main/java/io/agora/agoraeducore/core/internal/rte
        if [ $? -eq 0 ]; then
            echo "copy arsenal to rte successful!"
            return 1
        else
            echo "copy arsenal to rte failed!"
            return 0
        fi
    # use old rtc to build project
    elif [ $1 = "rtc" ]; then
        cp -f -r ./rte-rtc ./AgoraEduCore/src/main/java/io/agora/agoraeducore/core/internal/rte
        if [ $? -eq 0 ]; then
            echo "copy rtc to rte successful!"
            return 2
        else
            echo "copy rtc to rte failed!"
            return 0
        fi
    fi
}

updateGradlePropertyAndSync() {
    echo "updateGradlePropertyAndSync: $1"
    ./gradlew -i -b replaceRte.gradle -PUSE_ARSENAL=$1
    if [ $? -eq 0 ]; then
        echo "updateGradleProperty successful!"
        echo "start to clean and build project"
        ./gradlew clean
        #./gradlew build
        if [ $? -eq 0 ]; then
            echo "clean and build successful!"
        else
            echo "clean and build failed! please check..."
            return -5
        fi
    else
        echo "updateGradleProperty failed! please check..."
        return -4
    fi
}

isEmptyParameter $1
emptyParameter=$?
if [ $emptyParameter -eq 0 ]; then
    echo "empty parameter!"
    exit -1
else
    rm -f -r ./AgoraEduCore/src/main/java/io/agora/agoraeducore/core/internal/rte
    if [ $? -eq 0 ]; then
        echo "remove old rte dir successful!"
        replaceRte $1
        replace=$?
        echo $replace
        if [ $replace -eq 1 ]; then
            echo "replace rte dir successful!"
            echo "start to modify remote dependence."
            updateGradlePropertyAndSync true
        elif [ $replace -eq 2 ]; then
            echo "replace rte dir successful!"
            echo "start to modify remote dependence."
            updateGradlePropertyAndSync false
        else
            echo "replace rte dir failed! please check."
            exit -3
        fi
    else
        echo "remove old rte dir failed, please check!"
        exit -2
    fi
fi
