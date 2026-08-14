cmake_minimum_required(VERSION 3.22...3.31)
project(cleverestricky LANGUAGES CXX)

if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_compile_definitions(DEBUG_BUILD)
    add_compile_options(-O0 -g3 -fstack-protector-strong)
else()
    add_compile_options(
        -Oz
        -flto
        -fvisibility=hidden
        -fvisibility-inlines-hidden
        -fstack-protector-strong
        -D_FORTIFY_SOURCE=2
    )
    add_link_options(-flto -s -Wl,--exclude-libs,ALL)
endif()

add_compile_options(
    -Wall
    -Wextra
    -Werror
    -Wno-unused-parameter
    -fno-exceptions
    -fno-rtti
    -ffunction-sections
    -fdata-sections
)
add_link_options(
    -Wl,--gc-sections
    -Wl,-z,relro
    -Wl,-z,now
    -Wl,-z,noexecstack
)

add_subdirectory(external)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(RUST_TARGET_TRIPLE "aarch64-linux-android")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(RUST_TARGET_TRIPLE "x86_64-linux-android")
else()
    message(FATAL_ERROR "Unsupported Rust target ABI: ${ANDROID_ABI}")
endif()

add_library(rust_native_core STATIC IMPORTED)
set_target_properties(rust_native_core PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../rust/target/${RUST_TARGET_TRIPLE}/release/libcleverestricky_native_core.a
)

set(PLATFORM_LINK_STUB_DIR "${CMAKE_CURRENT_BINARY_DIR}/platform_link_stubs")
file(MAKE_DIRECTORY "${PLATFORM_LINK_STUB_DIR}")
file(WRITE "${PLATFORM_LINK_STUB_DIR}/binder_link_stub.cpp"
    "extern \"C\" __attribute__((visibility(\"hidden\"))) void cleverestricky_binder_link_stub() {}\n"
)
file(WRITE "${PLATFORM_LINK_STUB_DIR}/utils_link_stub.cpp"
    "extern \"C\" __attribute__((visibility(\"hidden\"))) void cleverestricky_utils_link_stub() {}\n"
)

add_library(platform_binder_link_stub SHARED "${PLATFORM_LINK_STUB_DIR}/binder_link_stub.cpp")
set_target_properties(platform_binder_link_stub PROPERTIES OUTPUT_NAME binder)
add_library(platform_utils_link_stub SHARED "${PLATFORM_LINK_STUB_DIR}/utils_link_stub.cpp")
set_target_properties(platform_utils_link_stub PROPERTIES OUTPUT_NAME utils)

add_library(${MODULE_NAME} SHARED binder_interceptor.cpp kernel_identity.cpp)
target_include_directories(${MODULE_NAME} PRIVATE
    binder/include
    ../../../../rust/native-core/include
)
target_link_libraries(${MODULE_NAME} PRIVATE
    log
    dl
    platform_binder_link_stub
    platform_utils_link_stub
    lsplt
    rust_native_core
)
target_link_options(${MODULE_NAME} PRIVATE
    "-Wl,--no-as-needed"
    "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/exports.map"
)
set_property(TARGET ${MODULE_NAME} APPEND PROPERTY LINK_DEPENDS
    ${CMAKE_CURRENT_SOURCE_DIR}/exports.map
)

set_target_properties(${MODULE_NAME} PROPERTIES LINK_FLAGS
    "-Wl,--unresolved-symbols=ignore-all"
)
