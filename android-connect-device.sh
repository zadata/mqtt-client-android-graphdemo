#! /bin/bash

cd ~/android-sdks/platform-tools
su
./adb kill-server 
./adb start-server 
./adb devices 
exit
