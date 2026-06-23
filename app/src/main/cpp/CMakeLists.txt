cmake_minimum_required(VERSION 3.22.1)
project(meetmind_whisper LANGUAGES C CXX)

include(FetchContent)

set(FETCHCONTENT_QUIET FALSE)

FetchContent_Declare(
        whispercpp
        GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
        GIT_TAG v1.9.1
)
FetchContent_Populate(whispercpp)

set(WHISPER_CPP_DIR ${whispercpp_SOURCE_DIR})

message(STATUS "Using whisper.cpp source: ${WHISPER_CPP_DIR}")

add_subdirectory(${WHISPER_CPP_DIR}/ggml ${CMAKE_BINARY_DIR}/ggml-build EXCLUDE_FROM_ALL)

add_library(whisper SHARED
        whisper_jni.cpp
        ${WHISPER_CPP_DIR}/src/whisper.cpp
)

target_compile_features(whisper PRIVATE cxx_std_17)
target_compile_definitions(whisper PRIVATE
        GGML_USE_CPU
        WHISPER_VERSION=\"v1.9.1\"
)

target_include_directories(whisper PRIVATE
        ${WHISPER_CPP_DIR}
        ${WHISPER_CPP_DIR}/include
        ${WHISPER_CPP_DIR}/src
        ${WHISPER_CPP_DIR}/ggml/include
        ${WHISPER_CPP_DIR}/ggml/src
        ${WHISPER_CPP_DIR}/ggml/src/ggml-cpu
)

find_library(LOG_LIB log)
find_library(ANDROID_LIB android)

target_link_libraries(whisper PRIVATE
        ${LOG_LIB}
        ${ANDROID_LIB}
        ggml
)
