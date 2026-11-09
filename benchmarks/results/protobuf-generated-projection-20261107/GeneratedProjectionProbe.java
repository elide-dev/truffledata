package dev.elide.truffledata.comparison;
import probe.TypeContracts;
import dev.elide.truffledata.*;
import dev.elide.truffledata.protobuf.*;
import java.util.*;
public class GeneratedProjectionProbe {
 static volatile Object output;
 static long pairs;
 static byte[] decode(ProtoPlan plan,byte[] wire,int off,int len,SlabLimits limits,Projection p,boolean generated) {
  try{return generated?plan.decodeSlabBytes(wire,off,len,limits,p):SlabCodec.decode(plan.layout(),wire,off,len,limits,p);}catch(ContractViolation invalid){return null;}
 }
 static void pair(ProtoPlan plan,byte[] wire,int off,int len,SlabLimits limits,Projection p) {
  byte[] expected=decode(plan,wire,off,len,limits,p,false),actual=decode(plan,wire,off,len,limits,p,true);
  if(!Arrays.equals(expected,actual))throw new AssertionError("pair "+pairs+" wire="+HexFormat.of().formatHex(Arrays.copyOfRange(wire,off,off+len))+" expected="+(expected==null?null:HexFormat.of().formatHex(expected))+" actual="+(actual==null?null:HexFormat.of().formatHex(actual)));
  pairs++;
 }
 static double[] sample(ProtoPlan plan,byte[][] wires,Projection p,long budget,boolean generated) {
  var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();long before=bean.getThreadAllocatedBytes(Thread.currentThread().threadId()),start=System.nanoTime(),n=0,end;
  do {for(byte[]wire:wires) {output=decode(plan,wire,0,wire.length,SlabLimits.forInput(plan.layout(),wire.length),p,generated);n++;}end=System.nanoTime();}while(end-start<budget);
  return new double[]{(end-start)/(double)n,(bean.getThreadAllocatedBytes(Thread.currentThread().threadId())-before)/(double)n};
 }
 public static void main(String[]args)throws Exception {
  var plan=TypeContracts.create(null,Limits.DEFAULT,32,1<<22).type("google.protobuf.Type");
  var sparse=Projection.builder(plan).select("name","syntax").build().lazyChildren();
  var selected=Projection.builder(plan).select("fields").selectType("google.protobuf.Field","name").build().lazyChildren();
  var projections=List.of(Projection.all(plan).lazyChildren(),Projection.builder(plan).build().lazyChildren(),sparse,selected,sparse.lax(),sparse.borrowBytes(1));
  Random random=new Random(734106);
  for(String mode:List.of("small","holdout","large"))for(byte[]wire:SlabWriterProbe.fixture(mode))for(var p:projections) {
   var limits=SlabLimits.forInput(plan.layout(),wire.length);
   pair(plan,wire,0,wire.length,limits,p);
   byte[] window=new byte[wire.length+11];System.arraycopy(wire,0,window,5,wire.length);pair(plan,window,5,wire.length,limits,p);
   byte[] expected=SlabCodec.decode(plan.layout(),wire,0,wire.length,limits,p);
   pair(plan,wire,0,wire.length,new SlabLimits(32,expected.length),p);
   pair(plan,wire,0,wire.length,new SlabLimits(32,expected.length-1),p);
   if(!mode.equals("large"))for(int i=0;i<20;i++) {
    byte[] corrupt=wire.clone();corrupt[random.nextInt(corrupt.length)]=(byte)random.nextInt(256);pair(plan,corrupt,0,corrupt.length,limits,p);
    pair(plan,wire,0,random.nextInt(wire.length),limits,p);
   }
  }
  System.out.println("PARITY "+pairs);
  for(String mode:List.of("small","holdout","large"))for(boolean generated:new boolean[]{false,true}) {
   byte[][] wires=SlabWriterProbe.fixture(mode);sample(plan,wires,sparse,3_000_000_000L,generated);
   for(int i=0;i<5;i++)System.out.println(mode+" generated="+generated+" "+Arrays.toString(sample(plan,wires,sparse,200_000_000L,generated)));
  }
 }
}
