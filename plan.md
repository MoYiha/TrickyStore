1. *Refactor Toasts to Snackbar in Encryptor App.*
   - Replace `Toast.makeText` with `Snackbar` in `encryptor-app/src/main/java/cleveres/tricky/encryptor/MainActivity.kt` to provide a better UI/UX.
   - We need to use `SnackbarHostState` to show Snackbars since `MainActivity` uses Compose.
2. *Pre-commit verification.*
   - Ensure the app builds successfully with `compileDebugKotlin`.
3. *Commit and submit.*
   - Create a commit titled "🎨 Palette: WebUI/UX Enhancement" as requested, and explain the change to Snackbar for better UI.
