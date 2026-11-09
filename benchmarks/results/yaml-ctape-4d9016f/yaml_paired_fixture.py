import ctypes as C, time, sys, os
from pathlib import Path
class Options(C.Structure):
 _fields_=[(n,C.c_uint64) for n in ['input','index','nodes','text','aliases','expansion']]+[(n,C.c_uint32) for n in ['depth','schema','duplicates','merges','allow_aliases','tags','version','complex']]
options=Options(1<<26,1<<28,10000000,1<<26,10000,1000000,128,0,0,0,0,0,0,0)
source=Path(os.environ.get('YAML_FIXTURE', 'yaml/native/testdata/records-block.yaml')).read_bytes(); data=C.create_string_buffer(source)
readers=[]
for name in sys.argv[1:]:
 lib=C.CDLL(name,mode=C.RTLD_LOCAL)
 create=lib.td_yaml_session_create;create.argtypes=[C.POINTER(Options)];create.restype=C.c_void_p
 decode=lib.td_yaml_session_decode;decode.argtypes=[C.c_void_p,C.c_void_p,C.c_uint64,C.c_uint32,C.c_void_p,C.c_uint64,C.c_void_p];decode.restype=C.c_int32
 reset=lib.td_yaml_session_reset;reset.argtypes=[C.c_void_p];reset.restype=None
 close=lib.td_yaml_session_release;close.argtypes=[C.c_void_p];close.restype=None
 session=create(options);assert session
 result=(C.c_uint64*56)()
 def run(n,decode=decode,reset=reset,session=session,result=result):
  start=time.perf_counter_ns()
  for i in range(n):
   status=decode(session,data,len(source),0,None,0,result)
   if status: raise RuntimeError(status)
   reset(session)
  return (time.perf_counter_ns()-start)/n
 readers.append((name,run,close,session));run(100)
try:
 best=[float('inf')]*len(readers)
 for pass_ in range(7):
  values=[0.0]*len(readers)
  for i in (range(len(readers)) if pass_%2==0 else reversed(range(len(readers)))):
   values[i]=min(readers[i][1](200) for _ in range(3));best[i]=min(best[i],values[i])
  print('pass',pass_, 'ns',','.join(str(round(n)) for n in values), 'speedup',values[0]/values[1],flush=True)
 print('best ns',best,'speedup',best[0]/best[1],flush=True)
finally:
 for _,_,close,session in readers:close(session)
