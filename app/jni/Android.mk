LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

OPENCV_INSTALL_MODULES:=on
OPENCV_LIB_TYPE:=SHARED
# include C:\Users\craigdev\Development\no-camera-opencv\OpenCV-android-sdk/sdk/native/jni/OpenCV.mk
include F:\ming_android\studio_ws\Ricoh\opencvdepth\OpenCV-android-sdk\sdk\native\jni\OpenCV.mk
LOCAL_MODULE := opencvdepth
LOCAL_SRC_FILES := sample.cpp
include $(BUILD_SHARED_LIBRARY)