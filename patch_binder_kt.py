import re

kt_path = "service/src/main/java/cleveres/tricky/cleverestech/binder/BinderInterceptor.kt"
with open(kt_path, "r") as f:
    content = f.read()

patch = """        fun registerBinderInterceptor(backdoor: IBinder, target: IBinder, interceptor: BinderInterceptor, filteredCodes: IntArray = intArrayOf()) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeStrongBinder(target)
                data.writeStrongBinder(interceptor)
                data.writeInt(filteredCodes.size)
                for (code in filteredCodes) data.writeInt(code)
                // The C++ BinderInterceptor's onTransact will expect code 1 for REGISTER_INTERCEPTOR
                backdoor.transact(1, data, reply, 0)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }"""

content = content.replace("""        fun registerBinderInterceptor(backdoor: IBinder, target: IBinder, interceptor: BinderInterceptor) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeStrongBinder(target)
                data.writeStrongBinder(interceptor)
                // The C++ BinderInterceptor's onTransact will expect code 1 for REGISTER_INTERCEPTOR
                backdoor.transact(1, data, reply, 0)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }""", patch)

with open(kt_path, "w") as f:
    f.write(content)

print("done")
