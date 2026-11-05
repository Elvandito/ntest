cmake_minimum_required(VERSION 3.22.1)
project(netband_arp C)

add_executable(netband_arp arp_native.c)
# Android NDK's pthread interface is provided by libc; there is no separate
# libpthread to link.

# Package as .so so Android Gradle extracts it with the exec bit into
# nativeLibraryDir; it is actually a plain ELF executable, not a library.
set_target_properties(netband_arp PROPERTIES
    PREFIX "lib"
    SUFFIX ".so"
)
