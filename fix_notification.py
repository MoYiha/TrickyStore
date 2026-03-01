import re

with open('service/src/main/java/cleveres/tricky/cleverestech/util/KeyboxAutoCleaner.kt', 'r') as f:
    content = f.read()

# Replace the command array setup
old_cmd = '''            val cmd = arrayOf(
                "cmd", "notification", "post",
                "-S", "bigtext",
                "-t", "CleveresTricky",
                "Keybox Revocation Alert",
                "$count keybox(es) were found to be revoked/invalid and have been disabled."
            )'''
new_cmd = '''            val cmd = arrayOf(
                "cmd", "notification", "post",
                "-S", "bigtext",
                "-t", "CleveresTricky",
                "Keybox Revocation Alert",
                "$count keybox(es) were found to be revoked/invalid and have been disabled."
            )
            // Note: `cmd notification post` has limited priority/action options depending on Android version
            // Adding channel/priority where supported. Easiest actionable feedback is a high-priority alert.
            // On newer Androids, we can use specific flags, but an intent to the WebUI is even better.

            // Wait, let's use Android intents via `am start` for an actionable notification if we had a full app.
            // But we can at least set a higher visibility icon or channel if possible. Actually, standard `cmd notification post` has limited options.
            // Let me look at standard Android `cmd notification` help.'''
# This was just a thought process. Let's do it properly.
