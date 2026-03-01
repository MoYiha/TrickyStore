with open("service/src/main/java/cleveres/tricky/cleverestech/util/KeyboxAutoCleaner.kt", "r") as f:
    c = f.read()

c = c.replace(
'''            // Post a high-priority, actionable notification
            val cmd = arrayOf(
                "su", "-c", "cmd notification post -S bigtext -t CleveresTricky 'Keybox Revoked Alert' '$count keybox(es) were found to be revoked/invalid and have been disabled. Check WebUI!'"
            )''',
'''            // Post a high-priority, actionable notification
            val cmd = arrayOf(
                "su", "-c", "cmd notification post -S bigtext -t CleveresTricky 'Keybox Revoked Alert' '$count keybox(es) were found to be revoked/invalid and have been disabled. Check WebUI!' -a 'android.intent.action.VIEW' -d 'http://localhost:5623'"
            )'''
)

with open("service/src/main/java/cleveres/tricky/cleverestech/util/KeyboxAutoCleaner.kt", "w") as f:
    f.write(c)
