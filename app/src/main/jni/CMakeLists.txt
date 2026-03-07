cmake_minimum_required(VERSION 3.22)

project("meowhub_native")

set(CMAKE_CXX_STANDARD 17)

add_compile_options(-Werror=format -fdata-sections -ffunction-sections -fno-exceptions -fno-rtti -fno-threadsafe-statics)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_compile_options(-Os -flto -fvisibility=hidden -fvisibility-inlines-hidden)
    add_link_options(-flto -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,--strip-all)
endif ()

find_package(boringssl REQUIRED CONFIG)

add_library(adb SHARED adb_pairing.cpp)

target_link_libraries(adb log boringssl::crypto_static)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET adb POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libadb.so")
endif ()

# Termux bootstrap library
enable_language(ASM)
set(BOOTSTRAP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../cpp")
add_library(termux-bootstrap SHARED
    ${BOOTSTRAP_DIR}/termux-bootstrap.c
    ${BOOTSTRAP_DIR}/termux-bootstrap-zip.S
)
set_source_files_properties(${BOOTSTRAP_DIR}/termux-bootstrap-zip.S PROPERTIES
    COMPILE_FLAGS "-I${BOOTSTRAP_DIR}"
)
target_include_directories(termux-bootstrap PRIVATE ${BOOTSTRAP_DIR})
