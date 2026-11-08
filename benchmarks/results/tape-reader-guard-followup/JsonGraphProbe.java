package dev.elide.truffledata.json.tests;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import dev.elide.truffledata.comparison.JsonFixture;
import dev.elide.truffledata.json.JsonJvm;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
public final class JsonGraphProbe {
 static final class HeapMessage extends dev.elide.truffledata.tape.TapeOwner {
   HeapMessage(dev.elide.truffledata.tape.TapeReader reader) {super(reader, Thread.currentThread());}
   protected boolean aliases(){return false;}
   protected boolean confined(){return true;}
   public dev.elide.truffledata.tape.TapeFormat format(){return dev.elide.truffledata.tape.TapeFormat.of("JSON");}
   public com.oracle.truffle.api.Assumption assumption(){return com.oracle.truffle.api.Assumption.ALWAYS_VALID;}
   protected long lexical(long node){return tape.span(node);}
   protected byte lexicalByte(long offset){return tape.inputByte(offset);}
 }

 static final class FusedHeapMessage extends dev.elide.truffledata.tape.TapeOwner implements dev.elide.truffledata.tape.TapeReader {
   final dev.elide.truffledata.tape.HeapTape source;
   FusedHeapMessage(dev.elide.truffledata.tape.TapeReader source){super(Thread.currentThread());this.source=(dev.elide.truffledata.tape.HeapTape)source;}
   protected dev.elide.truffledata.tape.TapeReader reader(){return this;}
   protected boolean aliases(){return false;} protected boolean confined(){return true;}
   public dev.elide.truffledata.tape.TapeFormat format(){return dev.elide.truffledata.tape.TapeFormat.of("JSON");}
   public com.oracle.truffle.api.Assumption assumption(){return com.oracle.truffle.api.Assumption.ALWAYS_VALID;}
   protected long lexical(long node){return source.span(node);} protected byte lexicalByte(long offset){return source.inputByte(offset);}
   public long nodeCount(){return source.nodeCount();} public long poolSize(){return source.poolSize();} public long inputSize(){return source.inputSize();}
   public long directorySize(){return source.directorySize();} public int flags(){return source.flags();}
   public long readWord(long index){return source.readWord(index);} public byte textByte(boolean owned,long offset){return source.textByte(owned,offset);}
   public long textWord(boolean owned,long offset,int length){return source.textWord(owned,offset,length);}
   public long directoryEntry(long index){return source.directoryEntry(index);} public int metadata(long index){return source.metadata(index);}
 }
 static final class Reads extends RootNode {
   private final Object[] values;
   @Child JsonFixture.InteropReader reader = new JsonFixture.InteropReader();
   Reads(Object[] values) { super(TestLanguage.current());this.values=values; }
   public Object execute(VirtualFrame frame) {
     long sum=0; int rotation=(Integer)frame.getArguments()[0];
     for(int i=0;i<32;i++)sum+=reader.read(values[(i+rotation)&31]);
     return sum;
   }
 }
 public static void main(String[] args) throws Exception {
   try(var context=Context.newBuilder("json-test").allowExperimentalOptions(true)
       .option("engine.BackgroundCompilation","false").option("engine.CompilationFailureAction","Throw")
       .option("compiler.TreatPerformanceWarningsAsErrors","all")
       .option("compiler.TraceMethodExpansion","truffleTier").option("compiler.NodeSourcePositions","true").build();
       var decoder=JsonJvm.open(Path.of("/tmp/libcompact-json.dylib"))) {
     Object[] values=new Object[32];
     for(int i=0;i<32;i++)try(var message=decoder.decode(JsonFixture.document(i))){var snapshot=message.snapshot();
       if(System.getenv("JSON_PROBE_CONFINED")!=null){var field=dev.elide.truffledata.tape.TapeOwner.class.getDeclaredField("tape");field.setAccessible(true);var source=(dev.elide.truffledata.tape.TapeReader)field.get(snapshot);values[i]=(System.getenv("JSON_PROBE_FUSED")!=null?new FusedHeapMessage(source):new HeapMessage(source)).root();}
       else values[i]=snapshot.root();}
     context.initialize("json-test");context.enter();
     try {var root=new Reads(values);var target=root.getCallTarget(); for(int i=0;i<20000;i++)target.call(i&31);System.out.println(target.call(0));}
     finally{context.leave();}
   }
 }
}
