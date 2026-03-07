---
description: How to prepare and push a release-ready commit for CleveresTricky
---

# Release Preparation Workflow

This workflow ensures the module is production-ready before pushing to master or creating a release.

// turbo-all

## Step 1: Run Safety Checks Locally

1. Check sepolicy semicolons:
```bash
grep -n '^allow\|^dontaudit\|^neverallow' module/template/sepolicy.rule | grep -v ';$'
```

2. Check for insecure PRNG:
```bash
grep -rn "java\.util\.Random" --include="*.kt" --include="*.java" service/src/main/ module/src/main/
```

3. Check for unwrap in FFI:
```bash
grep -n "unwrap\|expect" rust/cbor-cose/src/ffi.rs | grep -v "cfg(test)" | grep -v "//"
```

## Step 2: Run Tests

4. Run Rust tests:
```bash
cd rust/cbor-cose && cargo test --verbose
```

5. Run Gradle unit tests:
```bash
./gradlew testDebugUnitTest
```

## Step 3: Build

6. Build release:
```bash
./gradlew zipRelease
```

## Step 4: Push

7. Push to master:
```bash
git push origin master
```
