cmake_minimum_required(VERSION 3.31.6)

project(opendevice_llama LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

# Keep native inference representative in the installable debug APK, while
# retaining symbols and assertions. No device-specific ISA or clock override.
add_compile_options("$<$<CONFIG:Debug>:-O3>")

add_subdirectory(third_party/llama.cpp EXCLUDE_FROM_ALL)

add_library(opendevice_llama SHARED
    src/main/cpp/opendevice_llama_jni.cpp
)

target_compile_options(opendevice_llama PRIVATE
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)

target_link_options(opendevice_llama PRIVATE
    -Wl,--gc-sections
)

target_link_libraries(opendevice_llama PRIVATE
    llama
    android
    log
)
