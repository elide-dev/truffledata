#include <emmintrin.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
static int vector(const unsigned char *s, unsigned n, uint64_t *lo, uint64_t *hi) {
  const __m128i positions = _mm_setr_epi8(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15);
  const __m128i relevant = _mm_cmpgt_epi8(_mm_set1_epi8((char)n), positions);
  __m128i bytes = _mm_loadu_si128((const __m128i *)s);
  const __m128i bad = _mm_cmpeq_epi8(_mm_subs_epu8(bytes,_mm_set1_epi8(0x20)),_mm_setzero_si128());
  if (_mm_movemask_epi8(_mm_and_si128(bad,relevant))) return 0;
  bytes = _mm_and_si128(bytes,relevant);
  *lo = (uint64_t)_mm_cvtsi128_si64(bytes);
  *hi = (uint64_t)_mm_cvtsi128_si64(_mm_srli_si128(bytes,8));
  return 1;
}
int main(void) {
 unsigned char s[16], clean[16]; uint64_t count=0;
 for(unsigned n=1;n<=16;++n) for(unsigned at=0;at<16;++at) for(unsigned b=0;b<256;++b) for(unsigned fill=0;fill<256;++fill) {
   memset(s,fill,sizeof s); s[at]=(unsigned char)b;
   int wanted=1; for(unsigned j=0;j<n;++j) wanted &= s[j]>0x20;
   uint64_t lo=0,hi=0; int got=vector(s,n,&lo,&hi);
   if(got != wanted) return fprintf(stderr,"boolean mismatch %u %u %u %u\n",n,at,b,fill),1;
   if(got) {
     memset(clean,0,sizeof clean);memcpy(clean,s,n);
     uint64_t a,c;memcpy(&a,clean,8);memcpy(&c,clean+8,8);
     if(lo!=a||hi!=c) return fprintf(stderr,"word mismatch\n"),1;
   }
   ++count;
 }
 printf("SSE2 short scalar equivalence: %llu cases passed\n",(unsigned long long)count);
}
