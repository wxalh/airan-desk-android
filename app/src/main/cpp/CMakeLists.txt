cmake_minimum_required(VERSION 3.18.1)

project(airan_terminal)

set(LIBVTERM_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/libvterm)
set(GENERATED_DIR ${CMAKE_CURRENT_BINARY_DIR}/generated/libvterm)

file(MAKE_DIRECTORY ${GENERATED_DIR}/encoding)
configure_file(${LIBVTERM_DIR}/src/encoding/DECdrawing.tbl
        ${GENERATED_DIR}/encoding/DECdrawing.inc COPYONLY)
configure_file(${LIBVTERM_DIR}/src/encoding/uk.tbl
        ${GENERATED_DIR}/encoding/uk.inc COPYONLY)

add_library(vterm STATIC
        ${LIBVTERM_DIR}/src/encoding.c
        ${LIBVTERM_DIR}/src/keyboard.c
        ${LIBVTERM_DIR}/src/mouse.c
        ${LIBVTERM_DIR}/src/parser.c
        ${LIBVTERM_DIR}/src/pen.c
        ${LIBVTERM_DIR}/src/screen.c
        ${LIBVTERM_DIR}/src/state.c
        ${LIBVTERM_DIR}/src/unicode.c
        ${LIBVTERM_DIR}/src/vterm.c)

target_include_directories(vterm PUBLIC
        ${LIBVTERM_DIR}/include
        ${LIBVTERM_DIR}/src
        ${GENERATED_DIR})

add_library(airan_terminal SHARED native_terminal_jni.cpp)

target_include_directories(airan_terminal PRIVATE
        ${LIBVTERM_DIR}/include
        ${LIBVTERM_DIR}/src
        ${GENERATED_DIR})

target_link_libraries(airan_terminal vterm log)
