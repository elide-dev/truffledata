#include "json/native/session.h"
#include <cstdio>
using namespace truffledata;
int main() {
  for(bool validate:{false,true}) {
    json::session s({},{});
    for(const char* input:{R"({"aa":1,"bb":2,"cc":3})",R"({"aa":1,"bb":2,"dd":3})",R"({"\u0000\u0000":1,"\u0000\u0000":2,"dd":3})"}) {
      try {s.decode(input,strlen(input),json::session::encoding::utf8,validate);std::printf("validate=%d accepted %s\n",validate,input);}
      catch(const std::exception& e){std::printf("validate=%d rejected %s: %s\n",validate,input,e.what());}
    }
  }
}
