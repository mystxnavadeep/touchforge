cmake_minimum_required(VERSION 3.22.1)
project(touchforge_native LANGUAGES CXX)

add_library(touchforge_native SHARED touchforge_grab.cpp)

target_compile_features(touchforge_native PRIVATE cxx_std_17)
target_compile_options(touchforge_native PRIVATE -Wall -Wextra -fno-exceptions)
target_link_libraries(touchforge_native PRIVATE log)
