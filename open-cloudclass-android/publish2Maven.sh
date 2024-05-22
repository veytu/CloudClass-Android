#!/usr/bin/env sh

publish() {
  moduleName=$1
  relativePath=$2
  echo "current module is $moduleName, path is $relativePath"

  ls -l
  echo $(pwd)

  if [ -a $relativePath ]; then
    ./gradlew :$moduleName:publish -Dhttps.proxyHost=10.10.114.51 -Dhttps.proxyPort=1080
  else
    echo "the $moduleName module path not exists---$relativePath"
  fi
}

updateGradlePropertyAndSync() {
    echo "updateGradlePropertyAndSync: $1"
    ./gradlew -b readyPublishMaven.gradle -PREADY_PUBLISH_MAVEN=$1
    if [ $? -eq 0 ]; then
        echo "updateGradleProperty successful!"
        echo "start to clean and build project"
        ./gradlew clean
        ./gradlew build -Dhttps.proxyHost=10.10.114.51 -Dhttps.proxyPort=1080
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

updateGradlePropertyAndSync true
publish AgoraClassSDK   ./AgoraClassSDK
publish AgoraEduCore    ./AgoraEduCore
publish AgoraEduUIKit   ./AgoraEduUIKit
publish AgoraCloudScene   ./AgoraCloudScene