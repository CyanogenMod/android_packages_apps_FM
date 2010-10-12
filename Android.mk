LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-subdir-java-files) \
	src/com/android/fm/radio/IFMRadioServiceCallbacks.aidl \
	src/com/android/fm/radio/IFMRadioService.aidl \

LOCAL_PACKAGE_NAME := FM
LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)
