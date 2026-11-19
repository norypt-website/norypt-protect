# C9's orderly-reboot mark (shutdown_signal.c, shutdown_watcher.c). CMake rather than ndk-build: GNU make cannot
# build from a path with a space in it.
cmake_minimum_required(VERSION 3.22.1)
project(norypt_shutdown C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

set(HARDENING_CFLAGS -O2 -Wall -Wextra -Werror -fvisibility=hidden -fstack-protector-strong -D_FORTIFY_SOURCE=2)
set(HARDENING_LDFLAGS -Wl,-z,relro -Wl,-z,now -Wl,-z,noexecstack -Wl,--build-id=sha1)

# Loaded by the app (System.loadLibrary): the second-chance SIGTERM handler and the watcher's start.
add_library(norypt_shutdown SHARED shutdown_signal.c)
target_compile_options(norypt_shutdown PRIVATE ${HARDENING_CFLAGS})
target_link_options(norypt_shutdown PRIVATE ${HARDENING_LDFLAGS})

# Executed by the app (fork + execve). AGP packages only libraries, so app/build.gradle.kts (CopyNoryptWatcher) copies
# it in as libnorypt_watcher.so: installed in the native library directory, the only place an app may execute its own
# files. A PIE executable, never loaded as a library.
add_executable(norypt_watcher shutdown_watcher.c)
set_target_properties(norypt_watcher PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_compile_options(norypt_watcher PRIVATE ${HARDENING_CFLAGS})
target_link_options(norypt_watcher PRIVATE ${HARDENING_LDFLAGS})
