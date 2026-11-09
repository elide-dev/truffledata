package dev.elide.truffledata.json.tests;
import dev.elide.truffledata.comparison.JsonFixture;
import dev.elide.truffledata.json.*;
import dev.elide.truffledata.tape.*;
import java.nio.file.Path;
public final class JsonExportProbe {
 static volatile Object keep;
 public static void main(String[] args) throws Exception {
  try(var decoder=JsonJvm.open(Path.of("/tmp/libcompact-json.dylib"))) {
   JsonMessage[] messages=new JsonMessage[32]; TapeValue[] values=new TapeValue[32];
   for(int i=0;i<32;i++){messages[i]=decoder.decode(JsonFixture.document(i));values[i]=messages[i].root();}
   var keys=new JavaSink.KeyCache(); var export=TapeExportNode.create();
   long sum=0; int n=200000;
   for(int round=0;round<12;round++) {
    long start=System.nanoTime();
    for(int i=0;i<n;i++){var sink=new JavaSink(keys);export.execute(values[i&31],sink);Object tree=sink.result();keep=tree;sum+=JsonFixture.javaRead(tree);}
    System.out.printf("%d %.2f ns %d%n",round,(System.nanoTime()-start)/(double)n,sum);
   }
   for(var message:messages)message.close();
  }
 }
}
