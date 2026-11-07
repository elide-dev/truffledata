package dev.elide.truffledata.json.tests;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import dev.elide.truffledata.json.JsonSvm;
import dev.elide.truffledata.tape.TapeValue;
import java.nio.charset.StandardCharsets;

/** Compiled guest reads of members and indexed array elements, over live messages and snapshots. */
final class CompiledArrayReads {
  private static final String SOURCE =
      "{\"n\":2000,\"d\":2.5,\"b\":false,\"a\":[0,1,2,3,4,5,6,7,8,9,10,11]}";

  static void run() {
    try (var message = JsonSvm.decode(SOURCE.getBytes(StandardCharsets.UTF_8))) {
      var snapshot = message.snapshot();
      gate("message", message.root());
      gate("snapshot", snapshot.root());
    }
  }

  private static void gate(String label, TapeValue root) {
    var reads = new Reads(root);
    var target = reads.getCallTarget();
    for (int i = 0; i < 200 && !reads.compiled; i++) target.call(100000);
    CompiledReads.check(reads.compiled, label + " array reads must execute compiled code");
    // Per iteration: n + (long) d + b + a[i % 12] = 2002 + (i % 12); 1e6 iterations.
    long expected = 2002L * 1000000 + (1000000 / 12) * 66 + (0 + 1 + 2 + 3);
    CompiledReads.check(((Long) target.call(1000000)) == expected, label + " array checksum");
    CompiledReads.check(NodeUtil.countNodes(reads) <= 192, label + " array AST budget");
    var bean =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    bean.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId();
    long before = bean.getThreadAllocatedBytes(thread);
    target.call(1000000);
    double bytes = (bean.getThreadAllocatedBytes(thread) - before) / 1000000.0;
    CompiledReads.check(bytes >= 0 && bytes <= 0.05, label + " array bytes/read budget: " + bytes);
    System.out.println("JSON " + label + " array interop compiled=true bytes/read=" + bytes);
  }

  private static final class Reads extends RootNode {
    private final Object value;
    // One read site per property, as guest code has; each caches its constant member name.
    @Child private InteropLibrary readA = InteropLibrary.getFactory().createDispatched(3);
    @Child private InteropLibrary readN = InteropLibrary.getFactory().createDispatched(3);
    @Child private InteropLibrary readD = InteropLibrary.getFactory().createDispatched(3);
    @Child private InteropLibrary readB = InteropLibrary.getFactory().createDispatched(3);
    @Child private InteropLibrary values = InteropLibrary.getFactory().createDispatched(3);
    boolean compiled;

    Reads(Object value) {
      super(TestLanguage.current());
      this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      int count = (Integer) frame.getArguments()[0];
      long sum = 0;
      try {
        Object array = readA.readMember(value, "a");
        long size = values.getArraySize(array);
        for (int i = 0; i < count; i++) {
          sum +=
              values.asLong(readN.readMember(value, "n"))
                  + (long) values.asDouble(readD.readMember(value, "d"))
                  + (values.asBoolean(readB.readMember(value, "b")) ? 1 : 0)
                  + values.asLong(values.readArrayElement(array, i % size));
        }
      } catch (InteropException failure) {
        throw CompilerDirectives.shouldNotReachHere(failure);
      }
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }
}
