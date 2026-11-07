#!/usr/bin/env python3
"""Buckets folded stacks (root;...;leaf count) into decoder cost categories.
usage: categorize.py <folded.txt> [runs]   -> percentage of samples (and samples per run)"""
import re, sys, collections

rules = [
    ('validation (UTF-8, c-printable, LF bitmap, copy)', r'^(validate|validate_copy|scan64|yaml::detail::scan|scan|transcode|.*simdutf.*|printable)$'),
    ('stream splitting', r'^(yaml::stream::scan|find_lf|marker|blank_or_comment)$'),
    ('lexical spans', r'^(yaml::handler::lexical|lexical|lexical_slow|yaml::handler::lexical_slow|blank|blank16|short_plain|short_quoted|has_newline)$'),
    ('key hash + duplicate keys', r'^(.*key_hash|cached_hash|insert|.*key_set.*|.*key_table.*|leave_template|drop_record|finish_template|spill|same)$'),
    ('scalar resolution', r'^(resolve.*|yaml::resolve.*|.*resolve_now|core_string|decimal8|decimal_point8|.*is_null|.*is_bool|.*from_chars.*|key_text|yaml::key_text|.*core_int|.*core_float.*|.*float_token|.*decimal_integer|.*parse_fractional.*|.*decimal_exp_to_float.*)$'),
    ('side tables', r'^(side|yaml::handler::side|extend|.*arena_vec.*)$'),
    ('tape node writes', r'^(tape::tape_writer::.*|string_words|push|text|charge|pack_inline|write_value|write_text|integer|floating|null|boolean|begin|end)$'),
    ('session setup (start, reserve, arena)', r'^(start|yaml::handler::start|reserve|yaml::session::recycle|recycle|.*block_arena.*|hint_for|clear|yaml::handler::clear)$'),
    ('handler: other (frames, collections, dispatch)', r'^(yaml::handler::.*|key_fast|value_fast|key_quoted_fast|value_quoted_fast|place_fast_key|set_val_scalar_plain|set_key_scalar_plain|set_.*_scalar_.*|open|close|push_frame|child_done|define_anchor|take_tag|top|ascii.*)$'),
]
compiled = [(name, re.compile(p)) for name, p in rules]
totals = collections.Counter()
n = 0
for line in open(sys.argv[1]):
    stack, count = line.rsplit(' ', 1)
    count = int(count)
    n += count
    frames = [f.strip() for f in stack.split(';')]
    cat = 'rapidyaml engine and the rest'
    in_handler = any(f.startswith('yaml::') or f in ('key_fast', 'value_fast') for f in frames)
    for f in reversed(frames):
        hit = None
        for name, rx in compiled:
            if rx.match(f):
                hit = name
                break
        if hit:
            # Arena/tape frames under the engine (not the handler) stay engine.
            if hit in ('side tables', 'tape node writes') and not in_handler:
                continue
            cat = hit
            break
    totals[cat] += count
runs = float(sys.argv[2]) if len(sys.argv) > 2 else None
for name, c in totals.most_common():
    extra = f'  {c / runs * 1e3:7.2f} samples per 1000 runs' if runs else ''
    print(f'{100.0 * c / n:6.2f}%  {name}{extra}')
print(f'{n} samples')
