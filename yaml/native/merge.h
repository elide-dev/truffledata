#ifndef TRUFFLEDATA_YAML_MERGE_H
#define TRUFFLEDATA_YAML_MERGE_H
// Merge keys (https://yaml.org/type/merge.html; docs/superpowers/plans/2026-11-06-yaml.md, D4),
// resolved when their mapping closes. Included at the end of decoder.h.
//
//   - The value of `<<` is an alias of a mapping, an inline mapping, or a sequence of those; any
//     other value is "YAML merge of a non-mapping".
//   - Merged entries take the position of `<<`: sources in order, each source's entries in order.
//     Explicit keys of the mapping win (wherever they appear), and earlier sources win.
//   - An entry merged from an aliased mapping is a copy of its key node plus an alias-reference
//     value (a REF word pointing at the source's value word): no deep copy, so
//     memory stays linear. Entries of an inline source mapping move into place (their value
//     subtrees are not copied either).
//   - The mapping's other entries keep their order. The `<<` entry itself, an inline source's own
//     node and its overridden entries disappear; an anchor or alias target among them is an error.
//
// The rewrite touches only the closing mapping's children, which end the tape: O(entries).

namespace truffledata::yaml {

inline void handler::apply_merge(frame& f) {
  leave_template(f);  // merged keys are checked against every explicit key
  const uint64_t* live = writer_->words();
  const size_t map = f.node, start = map + 2, end = writer_->word_count();
  const size_t merge_key = f.merge, merge_value = step(merge_key);
  const size_t merge_end = after(merge_value);
  f.merge = none;

  // ---- sources: (node, by reference) in order -------------------------------------------------
  struct source {
    size_t node;
    bool inline_map;
  };
  size_t capacity = 1;
  if (ct::kind(live[merge_value]) == ct::kind_array) capacity += ct::field(live[merge_value]);
  auto* sources = static_cast<source*>(memory_->allocate(capacity * sizeof(source), alignof(source)));
  size_t count = 0;
  auto add = [&](size_t node) {
    if (meta_[node] & meta_alias) {
      uint64_t target = alias_targets_[meta_[node] >> meta_tag_shift];
      if (ct::kind(live[target]) != ct::kind_object) fail("YAML merge of a non-mapping");
      sources[count++] = {size_t(target), false};
    } else if (ct::kind(live[node]) == ct::kind_object) {
      sources[count++] = {node, true};
    } else {
      fail("YAML merge of a non-mapping");
    }
  };
  uint32_t value_kind = ct::kind(live[merge_value]);
  if (value_kind == ct::kind_array && !(meta_[merge_value] & meta_alias)) {
    if (ct::field(live[merge_value]) && (meta_[merge_value + 2] & meta_style_mask) == style_pair)
      fail("YAML merge of a non-mapping");
    for (size_t e = merge_value + 2; e < merge_end; e = after(e)) add(e);
  } else {
    add(merge_value);
  }

  // ---- rewrite ----------------------------------------------------------------------------
  const size_t total = end - start;
  saved_nodes* s = save(start, total);
  auto node_at = [&](size_t i) -> uint64_t {
    return i >= start && i < end ? s->nodes[i - start] : writer_->words()[i];
  };
  auto meta_at = [&](size_t i) { return i >= start && i < end ? s->meta[i - start] : meta_[i]; };
  auto lexical_at = [&](size_t i) { return i >= start && i < end ? raw_span(s->nodes, i - start) : raw_span(writer_->words(), i); };
  auto next_at = [&](size_t i) { return ct::after(i, node_at(i)); };
  restore_truncate(start);

  struct block {
    size_t from, to;  // old [from, to)
    size_t at;        // new start
  };
  size_t block_capacity = total + 1;
  auto* blocks = static_cast<block*>(memory_->allocate(block_capacity * sizeof(block), alignof(block)));
  size_t block_count = 0;
  auto move = [&](size_t from, size_t to) {
    size_t at = writer_->word_count();
    replay(s, from - start, to - from, int64_t(at) - int64_t(from));
    blocks[block_count++] = {from, to, at};
  };

  uint64_t entries = 0;
  for (size_t k = start; k < end;) {
    size_t next = next_at(ct::step(k, node_at(k)));
    if (k != merge_key) {
      move(k, next);
      ++entries;
      k = next;
      continue;
    }
    for (size_t i = 0; i < count; ++i) {
      const source& src = sources[i];
      size_t src_end = ct::low(node_at(src.node));
      for (size_t kj = src.node + 2; kj < src_end;) {
        size_t vj = ct::step(kj, node_at(kj));
        size_t after_value = next_at(vj);
        uint64_t key_word = node_at(kj);
        std::string_view text = stable_copy(ct::text_at((key_word & ct::owned_flag) ? writer_->pool_data() : reinterpret_cast<const uint8_t*>(input_), ct::low(key_word), ct::field(key_word)));
        bool plain;
        uint32_t hash = full_key_hash(text, plain);
        if (f.keys.insert(memory_, hash, text)) {
          // The key words are copied; their pool or input text span is unchanged.
          size_t at = writer_->word_count();
          uint64_t key[2] = {key_word, ct::is_wide(key_word) ? node_at(kj + 1) : 0};
          writer_->append(key, ct::is_wide(key_word) ? 2 : 1, int64_t(at) - int64_t(kj));
          side(lexical_at(kj), tape::key_meta(hash, at - map, plain), meta_at(kj) & ~meta_anchored);
          if (src.inline_map) {
            move(vj, after_value);
          } else {
            uint64_t target = (meta_at(vj) & meta_alias) ? alias_targets_[meta_at(vj) >> meta_tag_shift] : vj;
            if (alias_targets_.size() >= rules_.max_aliases || alias_targets_.size() + 1 >= meta_tag_limit)
              throw limit_error("aliases");
            uint32_t ordinal = add_alias_target(target);
            writer_->ref(target);
            side(0, 0, meta_alias | (ordinal << meta_tag_shift));
          }
          ++entries;
        }
        kj = after_value;
      }
    }
    k = next;
  }
  f.count = entries;

  // ---- anchors and alias targets follow their nodes; removed ones are errors -----------------
  auto relocate = [&](uint64_t& node) {
    if (node < start || node >= end) return;
    for (size_t b = 0; b < block_count; ++b) {
      if (node >= blocks[b].from && node < blocks[b].to) {
        node = node - blocks[b].from + blocks[b].at;
        return;
      }
    }
    fail("YAML merge removes an anchored node");
  };
  for (size_t i = 0; i < anchors_.size(); ++i) relocate(anchors_[i].node);
  for (size_t i = 0; i < alias_targets_.size(); ++i) relocate(alias_targets_[i]);
  sync_refs();
}

}  // namespace truffledata::yaml
#endif
