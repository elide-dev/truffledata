#include <stdio.h>
#include <string.h>
#include <yaml.h>

int main(int argc, char **argv) {
  const char *src = argc > 1 ? argv[1] : "a: [1, 2]";
  yaml_parser_t p;
  yaml_event_t e;
  int n = 0;
  yaml_parser_initialize(&p);
  yaml_parser_set_input_string(&p, (const unsigned char *)src, strlen(src));
  for (;;) {
    if (!yaml_parser_parse(&p, &e)) return 1;
    int done = e.type == YAML_STREAM_END_EVENT;
    yaml_event_delete(&e);
    n++;
    if (done) break;
  }
  yaml_parser_delete(&p);
  printf("%d\n", n);
  return 0;
}
