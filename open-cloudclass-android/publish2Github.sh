#!/usr/bin/env sh

updateGradlePropertyAndSync() {
    echo "updateGradlePropertyAndSync: $1"
    ./gradlew -b readyPublishGithub.gradle -PREADY_PUBLISH_GITHUB=$1
    if [ $? -eq 0 ]; then
        echo "updateGradleProperty successful!"
        echo "start to clean and build project"
        ./gradlew clean
        #./gradlew build
        if [ $? -eq 0 ]; then
            echo "clean and build successful!"
        else
            echo "clean and build failed! please check..."
            exit -5
        fi
    else
        echo "updateGradleProperty failed! please check..."
        exit -4
    fi
}

branchName=$1
if [ ! $1 ]; then
  echo "Miss parameter of branchName!"
  exit -1
fi
# confirm branchName
echo "branchName is '$branchName'"
read -r -p "Are You Sure? [Y/n] " input
case $input in
    [yY][eE][sS]|[yY])
		  echo "Yes"
		  ;;
    [nN][oO]|[nN])
		  echo "No"
		  exit -6
      ;;
    *)
		  echo "Invalid input..."
		  exit -7
		  ;;
esac
updateGradlePropertyAndSync true
echo "Config project successful!"
echo "Start delete internal file."
rm -r ./AgoraEduCore ./rte-arsenal ./rte-rtc ./build.sh ./Jenkinsfile_bitbucket.groovy ./maven.gradle ./module_Maven.gradle ./publish2Github.sh ./publish2Maven.sh ./readyPublishGithub.gradle ./readyPublishMaven.gradle ./replaceRte.gradle ./rtcSwitch.sh ./libs
echo "test0"
echo $?
echo "test1"
if [ $? -eq 0 ]; then
  echo "Delete internal file successful!"
else
  echo "Delete internal file failed!"
  exit -2
fi
echo "Start to init repository and publish source to github"
git init
git add .
git remote add origin git@github.com:AgoraIO-Community/CloudClass-Android.git
git commit -m "init $branchName"
git checkout -b $branchName
git push origin $branchName:$branchName
if [ $? -eq 0 ]; then
  echo "git push successful!"
else
  echo "git push failed!"
  exit -3
fi