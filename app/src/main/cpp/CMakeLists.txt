cmake_minimum_required(VERSION 3.22.1)
project(dynllama C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# Static llama/ggml archives end up inside a shared library.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# llama.cpp is fetched at configure time (pinned tag) and linked statically into one .so.
include(FetchContent)

set(BUILD_SHARED_LIBS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP       OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI        OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL         OFF CACHE BOOL "" FORCE)

set(GGML_NATIVE           OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL       OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP           OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI     OFF CACHE BOOL "" FORCE)
# Snapdragon 8 Gen 1+ / Dimensity 9000+ (Armv9 cores): dotprod + fp16 + i8mm.
# The Kotlin side checks /proc/cpuinfo for i8mm before loading this library.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16+i8mm" CACHE STRING "" FORCE)
endif()

FetchContent_Declare(
    llama
    URL https://github.com/ggml-org/llama.cpp/archive/refs/tags/v1.0.0.tar.gz
)
FetchContent_MakeAvailable(llama)

add_library(dynllama SHARED llama_jni.cpp)
target_link_libraries(dynllama PRIVATE llama ggml)
if(ANDROID)
    target_link_libraries(dynllama PRIVATE android log)
    target_link_options(dynllama PRIVATE "-Wl,-z,max-page-size=16384")
endif()
