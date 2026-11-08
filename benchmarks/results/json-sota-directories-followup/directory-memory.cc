#include "json/native/session.h"
#include "tape/native/testing.h"
#include <cstdio>
int main() {
  for(unsigned n : {1,20,1000,20000}) {
    std::string input;
    if(n==1)input=truffledata::tape::testing::fixture(1);
    else {input="[";for(unsigned i=0;i<n;i++){if(i)input+=',';input+=truffledata::tape::testing::fixture(i);}input+=']';}
    truffledata::json::session session({},{});
    auto d=session.decode(input.data(),input.size(),truffledata::json::session::encoding::utf8,false,false);
    auto& v=*d.tape;
    std::printf("input %zu words %llu pool %llu directory %llu logical index %.3fx allocated index %.3fx\n",input.size(),v.word_count,v.pool_bytes,v.directory_count,double(v.word_count*8+v.pool_bytes+v.directory_count*8)/input.size(),double(session.memory().current())/input.size());
  }
}
