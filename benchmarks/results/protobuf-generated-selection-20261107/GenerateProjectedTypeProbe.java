import com.google.protobuf.*;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import dev.elide.truffledata.protobuf.ContractPlugin;
import java.nio.file.*;
import java.util.*;
public class GenerateProjectedTypeProbe {
 static void visit(FileDescriptor file,Map<String,FileDescriptor> seen){if(seen.containsKey(file.getName()))return;for(var d:file.getDependencies())visit(d,seen);seen.put(file.getName(),file);}
 public static void main(String[] args)throws Exception{
  var files=new LinkedHashMap<String,FileDescriptor>();visit(Type.getDescriptor().getFile(),files);
  var request=CodeGeneratorRequest.newBuilder().setParameter("package=probe,class=TypeContracts").addFileToGenerate(Type.getDescriptor().getFile().getName());
  for(var file:files.values())request.addProtoFile(file.toProto());
  var response=ContractPlugin.generate(request.build());if(!response.getError().isEmpty())throw new AssertionError(response.getError());
  Files.writeString(Path.of("/tmp/td-generated-projection/TypeContracts.java"),response.getFile(0).getContent());
 }
}
