# Log Documentation

## How to Read Logs
The module does not use a proprietary log file. Instead, it integrates directly with Android's standard logging system. You must read the logs through `logcat`.

## Best Logcat Filter
To view logs specifically for this module, use the following filter in your terminal:
```bash
adb logcat -s cleverestricky
```

## What to Pay Attention To First
When analyzing logs, pay attention to the following:
1. **Initialization:** Look for the startup message `Welcome to Service!` to ensure the module is loading.
2. **Errors:** Look for any `E/cleverestricky` tags which indicate errors (e.g., failure to load config, server start failures).
3. **WebUI:** Check if `Web server started on port` appears.
4. **Interceptors:** Verify that `PropertyHiderService registered successfully` and similar Binder interceptor logs appear.

## Additional Debug Logging
If you are using a `debug` build of the module, additional, more verbose logging is automatically enabled to help trace application flow and troubleshoot issues.

## Testing Functionality
We test functionality across a variety of devices (e.g., Pixel series, Xiaomi) running Android 12+. Key components such as the WebUI, Binder interception, and property spoofing are verified through Android instrumented tests and manual device flashing.
