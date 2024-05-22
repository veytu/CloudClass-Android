echo "start build_android"
# debug mode
set -x
#env

. ../apaas-cicd-android/utils/v1/build_utils.sh
. ../apaas-cicd-android/utils/v1/file_utils.sh
. ../apaas-cicd-android/build/v1/build_module.sh

projectPath=$(pwd)

# branch
branch_name=$open_cloudclass_android_branch
# release/2.8.80 -> release_2.8.80
branch_name=$(echo "$branch_name" | sed 's/\//_/g')

echo $branch_name

# 打包机很多语法不支持，比如+=
buildMyModule() {
  module_is_build=$1
  module_name=$2
  module_aar_path=$module_name/build/outputs/aar/

  if [ "$module_is_build" = "true" ]; then
    buildModule $module_name
    file_path=${module_aar_path}${module_name}-release.aar
    echo "init module_name = $module_name , file_path = $file_path , branch_name = $branch_name"
    uploadFile $file_path $module_name $branch_name
    # upload_file_url from build_utils.sh
    aarUrl=${upload_file_url}/${module_name}-release.aar
    notifyWeChat "$aarUrl"
  fi
}

build() {
  buildGradleZIP $projectPath $GRADLE_HOME
  $projectPath/gradlew -Dhttps.proxyHost=10.10.114.51 -Dhttps.proxyPort=1080

  if [ $publishMaven = true ]; then
    echo "publish maven,publishMavenType="$publishMavenType
    cat ./config.gradle
    if [ "$publishMavenType" = "snapshot" ]; then
      setConfigSnapshotVersion
    fi
    cat ./config.gradle
    copyMavenSecret $projectPath/AgoraEduUIKit
    copyMavenSecret $projectPath/AgoraCloudScene
    copyMavenSecret $projectPath/AgoraClassSDK
    sh ./publish2Maven.sh
  else
    echo "build module aar"
    buildMyModule $buildAgoraClassSDK AgoraEduUIKit
    buildMyModule $buildAgoraCloudScene AgoraCloudScene
    buildMyModule $buildAgoraClassSDK AgoraClassSDK
  fi
}

build
