cmake_minimum_required(VERSION 3.22.1)
set(CMAKE_ANDROID_STL_TYPE c++_shared)
project("executorch")

# Force 16 KB Page Alignment Linker Flags for Android 15+ Compatibility
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wl,-z,max-page-size=16384 -std=c++17")

add_library(
        executorch
        SHARED
        executorch_jni.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        executorch
        c++_shared
        ${log-lib}
)
