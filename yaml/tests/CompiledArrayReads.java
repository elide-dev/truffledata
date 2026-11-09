package dev.elide.truffledata.yaml.tests;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import dev.elide.truffledata.tape.TapeValue;
import dev.elide.truffledata.yaml.YamlSession;

/** Compiled array size/element reads on retained and borrowed compact YAML views. */
final class CompiledArrayReads {
  private CompiledArrayReads() {}

  static void run(YamlSession session) {
    byte[] input = SvmAcceptance.bytes("[1000, 1000]");
    try (var first = session.decode(input); var sibling = session.decode(input)) {
      Reads retained = verify(first.root());
      Reads borrowed = session.with(input, CompiledArrayReads::verify);
      closed(borrowed);
      first.close();
      closed(retained);
      if (sibling.root().element(1).longValue() != 1000) {
        throw new AssertionError("YAML array sibling survives close");
      }
    }
  }

  private static Reads verify(TapeValue value) {
    Reads root = new Reads(value);
    var target = root.getCallTarget();
    for (int i = 0; i < 200 && !root.compiled; i++) target.call(100000);
    if (!root.compiled) throw new AssertionError("YAML array reads must execute compiled code");
    if (((Long) target.call(1000000)) != 1002000000L) {
      throw new AssertionError("YAML array checksum");
    }
    if (NodeUtil.countNodes(root) > 192) throw new AssertionError("YAML array AST budget");
    var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    bean.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(thread);
    target.call(1000000);
    double bytes = (bean.getThreadAllocatedBytes(thread) - before) / 1000000.0;
    if (bytes < 0 || bytes > 0.05) throw new AssertionError("YAML array bytes/read budget: " + bytes);
    System.out.println("YAML compiled=true array=true bytes/read=" + bytes);
    return root;
  }

  private static void closed(Reads root) {
    try {
      root.getCallTarget().call(1);
      throw new AssertionError("Compiled closed YAML array read");
    } catch (IllegalStateException expected) {
    }
  }

  private static final class Reads extends RootNode {
    @Child private InteropLibrary arrays = InteropLibrary.getFactory().createDispatched(3);
    @Child private InteropLibrary numbers = InteropLibrary.getFactory().createDispatched(3);
    private final TapeValue value;
    boolean compiled;

    Reads(TapeValue value) {
      super(TestLanguage.current());
      this.value = value;
    }

    @Override public Object execute(VirtualFrame frame) {
      int count = (int) frame.getArguments()[0];
      long sum = 0;
      try {
        for (int i = 0; i < count; i++) {
          sum += arrays.getArraySize(value);
          sum += numbers.asLong(arrays.readArrayElement(value, i & 1));
          if (CompilerDirectives.inCompiledCode()) compiled = true;
        }
      } catch (InteropException failure) {
        throw new AssertionError(failure);
      }
      LoopNode.reportLoopCount(this, count);
      return sum;
    }
  }
}
