project(external LANGUAGES CXX)

# lsplt
execute_process(
    COMMAND patch -p1 -N -i ${CMAKE_CURRENT_SOURCE_DIR}/lsplt_sendto.patch
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/LSPlt
    RESULT_VARIABLE patch_result
    OUTPUT_QUIET
    ERROR_QUIET
)

set(SOURCES LSPlt/lsplt/src/main/jni/lsplt.cc LSPlt/lsplt/src/main/jni/elf_util.cc)

add_library(lsplt STATIC ${SOURCES})
target_include_directories(lsplt PUBLIC LSPlt/lsplt/src/main/jni/include)
target_include_directories(lsplt PRIVATE LSPlt/lsplt/src/main/jni)
target_link_libraries(lsplt PUBLIC log)
# end lsplt
