// Benchmark only: a SIGPROF stack sampler for profiling on RBE workers, where perf is unavailable.
// Every `interval_us` of CPU time the handler records the interrupted PC and up to 30 callers by
// walking frame pointers (the toolchain keeps them: -fno-omit-frame-pointer). write() emits one
// line per sample, the addresses relative to the executable's load base, for offline
// symbolization with llvm-symbolizer against the same binary (yaml/native/README.md).
// Linux x86-64 and aarch64 only; elsewhere start() reports false.
#pragma once

#include <cstdint>
#include <cstdio>
#include <cstring>

#if defined(__linux__) && (defined(__x86_64__) || defined(__aarch64__))
#include <link.h>
#include <signal.h>
#include <sys/time.h>
#include <ucontext.h>
#define TD_SAMPLER 1
#endif

namespace truffledata::yaml::sampler {

constexpr int depth = 32;
constexpr size_t capacity = 200000;

struct sample {
  uint8_t frames;
  uintptr_t pc[depth];
};

inline sample* samples = nullptr;
inline volatile size_t count = 0;
inline uintptr_t stack_low = 0, stack_high = 0;

#if defined(TD_SAMPLER)
inline void on_signal(int, siginfo_t*, void* context) {
  if (count >= capacity) return;
  sample& s = samples[count];
  auto* uc = static_cast<ucontext_t*>(context);
#if defined(__x86_64__)
  uintptr_t pc = uintptr_t(uc->uc_mcontext.gregs[REG_RIP]);
  uintptr_t fp = uintptr_t(uc->uc_mcontext.gregs[REG_RBP]);
#else
  uintptr_t pc = uintptr_t(uc->uc_mcontext.pc);
  uintptr_t fp = uintptr_t(uc->uc_mcontext.regs[29]);
#endif
  int n = 0;
  s.pc[n++] = pc;
  while (n < depth && fp >= stack_low && fp + 16 <= stack_high && (fp & 7) == 0) {
    uintptr_t next = reinterpret_cast<uintptr_t*>(fp)[0];
    uintptr_t ret = reinterpret_cast<uintptr_t*>(fp)[1];
    if (!ret) break;
    s.pc[n++] = ret - 1;  // inside the call instruction
    if (next <= fp) break;
    fp = next;
  }
  s.frames = uint8_t(n);
  count = count + 1;
}

inline uintptr_t base() {
  uintptr_t b = 0;
  dl_iterate_phdr(
      [](dl_phdr_info* info, size_t, void* out) {
        *static_cast<uintptr_t*>(out) = info->dlpi_addr;
        return 1;  // the first object is the executable
      },
      &b);
  return b;
}
#endif

// Starts sampling the calling thread's process CPU time; `stack_top` is an address near the top of
// the sampled thread's stack (a local of main).
inline bool start(int interval_us, const void* stack_top) {
#if defined(TD_SAMPLER)
  samples = new sample[capacity];
  count = 0;
  stack_high = reinterpret_cast<uintptr_t>(stack_top) + 4096;
  stack_low = stack_high > (uintptr_t(64) << 20) ? stack_high - (uintptr_t(64) << 20) : 0;
  struct sigaction action;
  std::memset(&action, 0, sizeof action);
  action.sa_sigaction = on_signal;
  action.sa_flags = SA_SIGINFO | SA_RESTART;
  sigemptyset(&action.sa_mask);
  if (sigaction(SIGPROF, &action, nullptr) != 0) return false;
  itimerval timer{{0, interval_us}, {0, interval_us}};
  return setitimer(ITIMER_PROF, &timer, nullptr) == 0;
#else
  (void)interval_us;
  (void)stack_top;
  return false;
#endif
}

inline void stop() {
#if defined(TD_SAMPLER)
  itimerval timer{{0, 0}, {0, 0}};
  setitimer(ITIMER_PROF, &timer, nullptr);
#endif
}

// One line per sample: hex addresses relative to the executable's base, innermost first.
inline void write(std::FILE* out) {
#if defined(TD_SAMPLER)
  uintptr_t b = base();
  for (size_t i = 0; i < count; ++i) {
    for (int k = 0; k < samples[i].frames; ++k) std::fprintf(out, k ? " %lx" : "%lx", (unsigned long)(samples[i].pc[k] - b));
    std::fputc('\n', out);
  }
#else
  (void)out;
#endif
}

}  // namespace truffledata::yaml::sampler
