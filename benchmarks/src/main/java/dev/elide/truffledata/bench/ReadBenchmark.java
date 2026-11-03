package dev.elide.truffledata.bench;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.*;
import dev.elide.truffledata.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/** Diagnostic baselines, not equivalent execution pipelines or a JDBC throughput claim. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ReadBenchmark {
  private final Schema schema = Schema.of(Field.requiredLong("a"));
  private final List<Object[]> inputs = inputs();
  private final Batch batch = Batch.copyOf(schema, inputs, Limits.DEFAULT);
  private final Object[] rows = rows(batch);
  private final CallTarget target = new ReadRoot().getCallTarget();

  private static List<Object[]> inputs() {
    List<Object[]> values = new ArrayList<>();
    for (int i = 0; i < 1024; i++) values.add(new Object[] {1000L + i});
    return values;
  }

  private static Object[] rows(Batch batch) {
    Object[] rows = new Object[batch.size()];
    for (int i = 0; i < rows.length; i++) rows[i] = batch.row(i);
    return rows;
  }

  @Benchmark
  @OperationsPerInvocation(1024)
  public Object typedTruffleLoop() {
    return target.call((Object) rows);
  }

  @Benchmark
  @OperationsPerInvocation(1024)
  public void uncachedInteropHostLoop(Blackhole sink) throws Exception {
    for (Object row : rows) sink.consume(InteropLibrary.getUncached().readMember(row, "a"));
  }

  @Benchmark
  @OperationsPerInvocation(1024)
  public Batch ingestValidatedBatch() {
    return Batch.copyOf(schema, inputs, Limits.DEFAULT);
  }

  @Benchmark
  public Batch.Row createRetainedRow() {
    return batch.row(0);
  }

  private static final class ReadRoot extends RootNode {
    @Child private ReadLongNode read = ReadLongNode.create();

    ReadRoot() {
      super(null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object[] rows = (Object[]) frame.getArguments()[0];
      long sum = 0;
      for (Object row : rows) sum += read.execute(row, 0);
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, rows.length);
      return sum;
    }
  }
}
