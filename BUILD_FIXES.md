# Build Troubleshooting and Fixes

## CMake Deprecation Warnings

### Issue
The build log may show warnings similar to:
```
CMake Deprecation Warning at .../android.toolchain.cmake:35 (cmake_minimum_required):
Compatibility with CMake < 3.10 will be removed from a future version of CMake.
Update the VERSION argument <min> value...
```

### Cause
Newer Android NDK versions use newer CMake versions (e.g., 3.28+) which deprecated compatibility with older CMake versions (like 3.4.1).

### Solution
1.  **Update `cmake_minimum_required`**: Ensure all `CMakeLists.txt` files use a recent version.
    ```cmake
    cmake_minimum_required(VERSION 3.28.0)
    ```
2.  **Suppress Warnings**: If updating the version is not immediately feasible or if warnings persist from toolchain files, you can suppress developer warnings by adding `-Wno-dev` to the CMake arguments in `build.gradle.kts`.

    In `module/build.gradle.kts`:
    ```kotlin
    cmaker {
        default {
            arguments += arrayOf(
                "-Wno-dev",
                // ... other arguments
            )
        }
    }
    ```

## Versioning

The project version is defined in the root `build.gradle.kts` file:
```kotlin
val verName by extra("V2.0.4")
```
Update this value to bump the version number.
