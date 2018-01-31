LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#opencv
OPENCVROOT:= ${LOCAL_PATH}/../../../../OpenCV-android-sdk
OPENCV_CAMERA_MODULES:=on
OPENCV_INSTALL_MODULES:=on
OPENCV_LIB_TYPE:=SHARED
include ${OPENCVROOT}/sdk/native/jni/OpenCV.mk

LOCAL_SRC_FILES := com_watvision_mainapp_OpenCVNative.cpp

LOCAL_LDLIBS += -llog
LOCAL_MODULE := MyOpencvLibs


include $(BUILD_SHARED_LIBRARY)