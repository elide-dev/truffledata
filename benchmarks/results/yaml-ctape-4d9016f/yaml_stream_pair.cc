#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <dlfcn.h>
#include <fstream>
#include <iterator>
#include <string>
#include <vector>
struct Options { uint64_t limits[6]={1u<<26,1u<<28,10000000,1u<<26,10000,1000000}; uint32_t policies[8]={128,0,0,0,0,0,0,0}; };
struct Reader {
 void* library;void* session;
 void*(*create)(void*,uint64_t,uint64_t);
 uint64_t(*feed)(void*,const uint8_t*,uint64_t);
 void(*finish)(void*);int32_t(*next)(void*,void*);void(*release)(void*);void(*reset)(void*);void(*close)(void*);
 explicit Reader(const char* path) {
  library=dlopen(path,RTLD_NOW|RTLD_LOCAL);if(!library) {puts(dlerror());std::abort();}
  auto symbol=[&](const char* n){void* p=dlsym(library,n);if(!p)std::abort();return p;};
  auto open=(void*(*)(const Options*))symbol("td_yaml_session_create");Options options;session=open(&options);if(!session)std::abort();
  create=(decltype(create))symbol("td_yaml_stream_create");feed=(decltype(feed))symbol("td_yaml_stream_feed");
  finish=(decltype(finish))symbol("td_yaml_stream_finish");next=(decltype(next))symbol("td_yaml_stream_next");
  release=(decltype(release))symbol("td_yaml_stream_release");reset=(decltype(reset))symbol("td_yaml_session_reset");close=(decltype(close))symbol("td_yaml_session_release");
 }
 double run(const std::string& input,unsigned iterations) {
  auto start=std::chrono::steady_clock::now();
  for(unsigned j=0;j<iterations;++j) {
   void* stream=create(session,1u<<20,1u<<20);if(!stream)std::abort();
   if(feed(stream,(const uint8_t*)input.data(),input.size())!=input.size())std::abort();finish(stream);
   uint64_t result[56]{};unsigned docs=0;int32_t status;
   while((status=next(stream,result))==0) ++docs;
   if(status!=8||docs!=1000){fprintf(stderr,"status%d docs%u\n",status,docs);std::abort();}
   release(stream);reset(session);
  }
  return double(std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now()-start).count())/iterations;
 }
 ~Reader(){close(session);dlclose(library);}
};
int main(int argc,char** argv) {
 if(argc!=3)return 2;
 std::ifstream in("yaml/native/testdata/records-stream.yaml",std::ios::binary);std::string source((std::istreambuf_iterator<char>(in)),{});
 Reader first(argv[1]),second(argv[2]);Reader* readers[]={&first,&second};first.run(source,10);second.run(source,10);
 double best[]={1e100,1e100};
 for(unsigned pass=0;pass<7;++pass){double value[2];for(unsigned order=0;order<2;++order){unsigned i=pass%2?1-order:order;value[i]=1e100;for(unsigned round=0;round<3;++round)value[i]=std::min(value[i],readers[i]->run(source,100));best[i]=std::min(best[i],value[i]);}printf("pass%u ns%.0f,%.0f speedup%.6f\n",pass,value[0],value[1],value[0]/value[1]);fflush(stdout);}
 printf("best ns%.0f,%.0f speedup%.6f\n",best[0],best[1],best[0]/best[1]);
}
