cmake_minimum_required(VERSION 3.22.1)
project("solver")

add_library(solver SHARED solver.cpp)

find_library(log-lib log)

target_link_libraries(solver ${log-lib})
