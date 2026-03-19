import re

header_path = "module/src/main/cpp/binder_interceptor.h"
with open(header_path, "r") as f:
    content = f.read()

# Add filtered_codes to InterceptItem
content = content.replace("        sp<IBinder> interceptor;\n    };\n", "        sp<IBinder> interceptor;\n        std::vector<uint32_t> filtered_codes;\n    };\n")

# Replace needIntercept with shouldIntercept
content = content.replace("bool needIntercept(const wp<BBinder>& target);", "bool shouldIntercept(const wp<BBinder>& target, uint32_t code);")

with open(header_path, "w") as f:
    f.write(content)

cpp_path = "module/src/main/cpp/binder_interceptor.cpp"
with open(cpp_path, "r") as f:
    content = f.read()

# Replace needIntercept with shouldIntercept
content = content.replace("if (gBinderInterceptor->needIntercept(wb)) {", "if (gBinderInterceptor->shouldIntercept(wb, txn.code)) {")
content = content.replace("bool BinderInterceptor::needIntercept(const wp<BBinder> &target) {", "bool BinderInterceptor::shouldIntercept(const wp<BBinder> &target, uint32_t code) {")

content = content.replace(
"""  ReadGuard g{lock};
  return items.find(target) != items.end();
}""",
"""  ReadGuard g{lock};
  auto it = items.find(target);
  if (it == items.end()) return false;
  const auto &codes = it->second.filtered_codes;
  return codes.empty() || std::find(codes.begin(), codes.end(), code) != codes.end();
}""")

# Read filtered codes in REGISTER_INTERCEPTOR
patch = """    if (data.readStrongBinder(&interceptor) != OK) {
      return BAD_VALUE;
    }
    std::vector<uint32_t> codes;
    int32_t code_count = 0;
    if (data.dataAvail() >= sizeof(int32_t) && data.readInt32(&code_count) == OK && code_count > 0) {
        codes.reserve(code_count);
        for (int32_t i = 0; i < code_count; i++) {
            uint32_t c = 0;
            if (data.readUint32(&c) == OK) codes.push_back(c);
        }
        LOGI("Interceptor registered for binder %p with %zu filtered codes", target.get(), codes.size());
    } else {
        LOGI("Interceptor registered for binder %p (all codes)", target.get());
    }
    {
      WriteGuard wg{lock};
      wp<IBinder> t = target;
      auto it = items.find(t);
      if (it == items.end()) {
        auto result = items.insert({t, InterceptItem{}});
        it = result.first;
        it->second.target = t;
      } else if (it->second.interceptor != nullptr &&
                 it->second.interceptor != interceptor) {
        Parcel data, reply;
        it->second.interceptor->transact(INTERCEPTOR_REPLACED, data, &reply,
                                         IBinder::FLAG_ONEWAY);
      }
      it->second.interceptor = interceptor;
      it->second.filtered_codes = std::move(codes);
      return OK;
    }"""

content = content.replace(
"""    if (data.readStrongBinder(&interceptor) != OK) {
      return BAD_VALUE;
    }
    {
      WriteGuard wg{lock};
      wp<IBinder> t = target;
      auto it = items.find(t);
      if (it == items.end()) {
        auto result = items.insert({t, InterceptItem{}});
        it = result.first;
        it->second.target = t;
      } else if (it->second.interceptor != nullptr &&
                 it->second.interceptor != interceptor) {
        Parcel data, reply;
        it->second.interceptor->transact(INTERCEPTOR_REPLACED, data, &reply,
                                         IBinder::FLAG_ONEWAY);
      }
      it->second.interceptor = interceptor;
      return OK;
    }""", patch)

with open(cpp_path, "w") as f:
    f.write(content)

print("done")
