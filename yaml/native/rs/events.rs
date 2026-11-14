//! granit-parser events as flat `td_yaml_rs_event` records (safe code only).
//!
//! One record per YAML node or structure event, in document order. Text that granit borrows from
//! the input is passed as an input offset (`FLAG_TEXT_IN_INPUT`); owned text (escapes, folding,
//! block scalars) and resolved tag URIs are appended to a per-batch scratch buffer that stays valid
//! until the next batch. Anchor names follow their node in granit's stream (`emit_anchor_events`)
//! and are folded into the node's record; alias names are read from the `*name` token.

use granit_parser::{Event, Parser, ScalarStyle, ScanError, Scanner, Span, StrInput, StructureStyle, Tag, TokenType};

pub const KIND_DOC_START: u8 = 1;
pub const KIND_DOC_END: u8 = 2;
pub const KIND_SEQ_START: u8 = 3;
pub const KIND_SEQ_END: u8 = 4;
pub const KIND_MAP_START: u8 = 5;
pub const KIND_MAP_END: u8 = 6;
pub const KIND_SCALAR: u8 = 7;
pub const KIND_ALIAS: u8 = 8;
/// `%TAG handle prefix` of the document: handle in the anchor fields, prefix in the tag fields
/// (both in scratch). Delivered before the document's start record.
pub const KIND_TAG_DIRECTIVE: u8 = 9;

/// Scalar styles share the decoder's `scalar_style` values; collections use 5 and 6.
pub const STYLE_PLAIN: u8 = 0;
pub const STYLE_SINGLE: u8 = 1;
pub const STYLE_DOUBLE: u8 = 2;
pub const STYLE_LITERAL: u8 = 3;
pub const STYLE_FOLDED: u8 = 4;
pub const STYLE_BLOCK: u8 = 5;
pub const STYLE_FLOW: u8 = 6;

/// `---` on a document start, `...` on a document end.
pub const FLAG_EXPLICIT: u8 = 1 << 0;
/// An empty plain scalar (`a:`), never the spelled `~`.
pub const FLAG_EMPTY: u8 = 1 << 1;
/// `text_offset` indexes the input; otherwise the batch scratch.
pub const FLAG_TEXT_IN_INPUT: u8 = 1 << 2;
/// The node carries an anchor; `anchor_offset` indexes the input or (with
/// `FLAG_ANCHOR_IN_SCRATCH`) the scratch. Alias records always carry their target name.
pub const FLAG_ANCHOR: u8 = 1 << 3;
pub const FLAG_TAG: u8 = 1 << 4;
pub const FLAG_ANCHOR_IN_SCRATCH: u8 = 1 << 5;

pub const STATUS_SYNTAX: i32 = -1;
pub const STATUS_INTERNAL: i32 = -2;
pub const STATUS_ARGUMENT: i32 = -3;
/// A syntax error the rapidyaml backend raises in the decoder rather than in its parser (so it
/// carries the decoder's message and is never a stream truncation): unsupported %YAML major
/// versions and invalid or unpaired surrogate escapes, which granit rejects while scanning.
pub const STATUS_DECODER_SYNTAX: i32 = -4;

/// The C record (`td_yaml_rs_event` in `yaml/native/rust_source.h`). Offsets are bytes; `line`
/// is 1-based and `column` is a 1-based code-point column of the node's first character (after
/// its properties). `raw_*` is the source token: quotes included for quoted scalars, the content
/// lines for block scalars (from the first content character), `*name` for aliases.
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct RsEvent {
    pub kind: u8,
    pub style: u8,
    pub flags: u8,
    pub reserved: u8,
    pub text_offset: u32,
    pub text_length: u32,
    pub raw_offset: u32,
    pub raw_length: u32,
    pub anchor_offset: u32,
    pub anchor_length: u32,
    pub tag_offset: u32,
    pub tag_length: u32,
    pub line: u32,
    pub column: u32,
    /// Document start: `%YAML major.minor` as `major << 16 | minor`, 0 when absent.
    pub version: u32,
}

// rust_source.h static_asserts the same size.
const _: () = assert!(std::mem::size_of::<RsEvent>() == 48);

/// A parse failure: status, 1-based line and code-point column, input byte offset, message.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Failure {
    pub status: i32,
    pub line: u32,
    pub column: u32,
    pub offset: u32,
    pub message: String,
}

impl Failure {
    pub fn internal(message: &str) -> Failure {
        Failure {
            status: STATUS_INTERNAL,
            line: 0,
            column: 0,
            offset: 0,
            message: message.to_owned(),
        }
    }
    fn scan(error: &ScanError) -> Failure {
        let mark = error.marker();
        Failure {
            status: STATUS_SYNTAX,
            line: clamp(mark.line()),
            column: clamp(mark.col().saturating_add(1)),
            offset: clamp(mark.byte_offset().unwrap_or(0)),
            message: error.info(),
        }
    }
}

fn clamp(value: usize) -> u32 {
    u32::try_from(value).unwrap_or(u32::MAX)
}

/// Parser options used for every decode: spec indentation rules (yaml-test-suite 9C9N), anchor
/// names delivered as events, and aliases to unknown anchors delivered as aliases (the decoder
/// rejects them itself, with rapidyaml's status and message). Nesting limits stay at granit's
/// defaults (255), above the decoder's own depth limit (at most 128), so the handler's limit error
/// is the one users see. Implicit keys keep YAML's 1024-character limit.
pub fn options() -> granit_parser::Options {
    granit_parser::options! { strict_indentation: true, emit_anchor_events: true, emit_unresolved_aliases: true }
}

/// One document stream's records, pulled batch by batch.
pub struct Batcher<'a> {
    input: &'a str,
    parser: Parser<'a, StrInput<'a>>,
    directives: Vec<(String, String)>,
    next_directive: usize,
    pending: Option<Failure>,
    done: bool,
}

impl<'a> Batcher<'a> {
    pub fn new(input: &'a str) -> Batcher<'a> {
        Batcher {
            input,
            parser: Parser::new_from_str_with_options(input, options()),
            directives: tag_directives(input),
            next_directive: 0,
            pending: tag_separation(input),
            done: false,
        }
    }

    /// Fills `out` from the front and returns the number of records; 0 at the end of the stream.
    /// Records before a failure are returned first and the failure on the next call, so the
    /// handler sees every event that precedes an error, as with rapidyaml. Scratch is cleared.
    pub fn next_batch(&mut self, out: &mut [RsEvent], scratch: &mut Vec<u8>) -> Result<usize, Failure> {
        scratch.clear();
        if let Some(failure) = self.pending.take() {
            self.done = true;
            return Err(failure);
        }
        let mut count = 0;
        while count < out.len() && !self.done {
            match self.next_record(scratch) {
                Ok(Some(record)) => {
                    if let Some(slot) = out.get_mut(count) {
                        *slot = record;
                    }
                    count += 1;
                }
                Ok(None) => {}
                Err(failure) => {
                    self.done = true;
                    if count == 0 {
                        return Err(failure);
                    }
                    self.pending = Some(failure);
                    break;
                }
            }
        }
        Ok(count)
    }

    // One record, None for events that produce none (stream start and end).
    fn next_record(&mut self, scratch: &mut Vec<u8>) -> Result<Option<RsEvent>, Failure> {
        if let Some((handle, prefix)) = self.directives.get(self.next_directive) {
            self.next_directive += 1;
            let mut r = RsEvent {
                kind: KIND_TAG_DIRECTIVE,
                ..RsEvent::default()
            };
            (r.anchor_offset, r.anchor_length) = push(scratch, handle.as_bytes())?;
            (r.tag_offset, r.tag_length) = push(scratch, prefix.as_bytes())?;
            return Ok(Some(r));
        }
        let (event, span) = match self.parser.next_event() {
            None => {
                self.done = true;
                return Ok(None);
            }
            Some(Err(error)) => return Err(self.scan_failure(&error)),
            Some(Ok(next)) => next,
        };
        let mut r = self.record(&span)?;
        match event {
            Event::StreamStart => return Ok(None),
            Event::StreamEnd => {
                self.done = true;
                return Ok(None);
            }
            Event::DocumentStart(explicit, version) => {
                r.kind = KIND_DOC_START;
                if explicit {
                    r.flags |= FLAG_EXPLICIT;
                }
                if let Some(v) = version {
                    r.version = (v.major.min(0xffff) << 16) | v.minor.min(0xffff);
                }
            }
            Event::DocumentEnd => {
                r.kind = KIND_DOC_END;
                if self.slice(&span) == Some("...") {
                    r.flags |= FLAG_EXPLICIT;
                }
            }
            Event::SequenceStart(style, anchor, tag) => {
                r.kind = KIND_SEQ_START;
                r.style = collection_style(style);
                self.properties(&mut r, anchor, tag.as_deref(), scratch)?;
            }
            Event::SequenceEnd => r.kind = KIND_SEQ_END,
            Event::MappingStart(style, anchor, tag) => {
                r.kind = KIND_MAP_START;
                r.style = collection_style(style);
                self.properties(&mut r, anchor, tag.as_deref(), scratch)?;
            }
            Event::MappingEnd => r.kind = KIND_MAP_END,
            Event::Scalar(text, style, anchor, tag) => {
                r.kind = KIND_SCALAR;
                r.style = scalar_style(style);
                // granit reports an empty plain scalar as `~` (or "" when tagged) with an empty span.
                if style == ScalarStyle::Plain && span.is_empty() {
                    r.flags |= FLAG_EMPTY | FLAG_TEXT_IN_INPUT;
                    r.text_offset = r.raw_offset;
                    r.text_length = 0;
                } else if self.fabricated_final_break(style, &span, &text) {
                    // granit-parser 2.1.0 appends a line break to a clip or keep block scalar whose
                    // last content line ends the input without one; YAML 1.2.2 [165]
                    // (b-chomped-last: b-as-line-feed | end-of-input) keeps none.
                    let trimmed = text.get(..text.len() - 1).unwrap_or("");
                    (r.text_offset, r.text_length) = push(scratch, trimmed.as_bytes())?;
                } else {
                    self.text(&mut r, &text, scratch)?;
                }
                self.properties(&mut r, anchor, tag.as_deref(), scratch)?;
            }
            // Unresolved aliases are delivered too: the handler reports "undefined YAML alias" (as
            // with rapidyaml) and applies reject_aliases, max_aliases and recursion checks.
            Event::Alias(_) | Event::UnresolvedAlias(_) => {
                r.kind = KIND_ALIAS;
                r.flags |= FLAG_ANCHOR;
                // The span is the `*name` token; the handler resolves the name itself.
                match self.slice(&span) {
                    Some(token) if token.len() > 1 && token.starts_with('*') => {
                        r.anchor_offset = r.raw_offset + 1;
                        r.anchor_length = r.raw_length - 1;
                    }
                    _ => return Err(Failure::internal("YAML alias without its *name token")),
                }
            }
            _ => return Err(Failure::internal("unexpected YAML parser event")),
        }
        Ok(Some(r))
    }

    // A scan error. Errors caused by the input ending inside a construct (an open flow collection
    // or quoted scalar, a dangling key) are placed at the end of the input, as rapidyaml places
    // them: yaml::stream reports a final document that fails there as truncated
    // (TD_YAML_STREAM_TRUNCATED), so the position decides the status. granit reports some of them
    // at the construct's start (an unclosed bracket at the bracket).
    fn scan_failure(&self, error: &ScanError) -> Failure {
        use granit_parser::ErrorKind as K;
        let mut failure = Failure::scan(error);
        let decoder_message = match error.kind() {
            K::UnsupportedYamlMajorVersion => Some("unsupported YAML version (only 1.x)"),
            K::InvalidLowSurrogate | K::MissingLowSurrogate | K::UnpairedLowSurrogate => {
                Some("unpaired surrogate in a YAML quoted scalar")
            }
            K::InvalidUnicodeEscape => Some("invalid Unicode escape in a YAML quoted scalar"),
            _ => None,
        };
        if let Some(message) = decoder_message {
            failure.status = STATUS_DECODER_SYNTAX;
            failure.message = message.to_owned();
            return failure;
        }
        if matches!(
            error.kind(),
            K::UnexpectedEof
                | K::UnexpectedEofFlowSequence
                | K::UnexpectedEofFlowMapping
                | K::UnexpectedEofImplicitFlowMapping
                | K::UnexpectedEofBlockSequence
                | K::UnexpectedEofBlockMapping
                | K::UnclosedFlowCollection { .. }
                | K::UnclosedQuotedScalar
        ) {
            let last_line = self.input.rsplit('\n').next().unwrap_or("");
            failure.line = clamp(self.input.matches('\n').count() + 1);
            failure.column = clamp(last_line.chars().count() + 1);
            failure.offset = clamp(self.input.len());
        }
        failure
    }

    fn fabricated_final_break(&self, style: ScalarStyle, span: &Span, text: &str) -> bool {
        if !matches!(style, ScalarStyle::Literal | ScalarStyle::Folded) || !text.ends_with('\n') {
            return false;
        }
        if span.end.byte_offset() != Some(self.input.len()) || self.input.ends_with(['\n', '\r']) {
            return false;
        }
        // The input's last line is content of this scalar (not a blank line).
        let last = self.input.rsplit('\n').next().unwrap_or("");
        !last.trim_matches([' ', '\t']).is_empty()
    }

    // Position and raw span of an event.
    fn record(&self, span: &Span) -> Result<RsEvent, Failure> {
        let (Some(start), Some(end)) = (span.start.byte_offset(), span.end.byte_offset()) else {
            return Err(Failure::internal("YAML parser span without byte offsets"));
        };
        if start > end || end > self.input.len() {
            return Err(Failure::internal("YAML parser span outside the input"));
        }
        Ok(RsEvent {
            raw_offset: clamp(start),
            raw_length: clamp(end - start),
            line: clamp(span.start.line()),
            column: clamp(span.start.col().saturating_add(1)),
            ..RsEvent::default()
        })
    }

    fn slice(&self, span: &Span) -> Option<&'a str> {
        let input: &'a str = self.input;
        input.get(span.start.byte_offset()?..span.end.byte_offset()?)
    }

    // Input offset of text granit borrowed from the input.
    fn input_offset(&self, text: &str) -> Option<u32> {
        let base = self.input.as_ptr() as usize;
        let at = text.as_ptr() as usize;
        if at >= base && at.checked_add(text.len())? <= base + self.input.len() {
            u32::try_from(at - base).ok()
        } else {
            None
        }
    }

    fn text(&self, r: &mut RsEvent, text: &str, scratch: &mut Vec<u8>) -> Result<(), Failure> {
        match self.input_offset(text) {
            Some(offset) => {
                r.flags |= FLAG_TEXT_IN_INPUT;
                r.text_offset = offset;
                r.text_length = clamp(text.len());
            }
            _ => (r.text_offset, r.text_length) = push(scratch, text.as_bytes())?,
        }
        Ok(())
    }

    // Anchor (the Anchor event that immediately follows an anchored node) and tag of a node.
    fn properties(
        &mut self,
        r: &mut RsEvent,
        anchor: usize,
        tag: Option<&Tag>,
        scratch: &mut Vec<u8>,
    ) -> Result<(), Failure> {
        if anchor != 0 {
            match self.parser.next_event() {
                Some(Ok((Event::Anchor(id, name), _))) if id == anchor => {
                    r.flags |= FLAG_ANCHOR;
                    match self.input_offset(&name) {
                        Some(offset) => r.anchor_offset = offset,
                        None => {
                            r.flags |= FLAG_ANCHOR_IN_SCRATCH;
                            let (offset, _) = push(scratch, name.as_bytes())?;
                            r.anchor_offset = offset;
                        }
                    }
                    r.anchor_length = clamp(name.len());
                }
                Some(Err(error)) => return Err(self.scan_failure(&error)),
                _ => return Err(Failure::internal("YAML anchor event missing after its node")),
            }
        }
        if let Some(tag) = tag {
            r.flags |= FLAG_TAG;
            let start = scratch.len();
            scratch.extend_from_slice(tag.handle().as_bytes());
            scratch.extend_from_slice(tag.suffix().as_bytes());
            r.tag_offset = u32::try_from(start).map_err(|_| Failure::internal("YAML parser scratch overflow"))?;
            r.tag_length = clamp(scratch.len() - start);
        }
        Ok(())
    }
}

fn push(scratch: &mut Vec<u8>, bytes: &[u8]) -> Result<(u32, u32), Failure> {
    let offset = u32::try_from(scratch.len()).map_err(|_| Failure::internal("YAML parser scratch overflow"))?;
    scratch.extend_from_slice(bytes);
    Ok((offset, clamp(bytes.len())))
}

fn collection_style(style: StructureStyle) -> u8 {
    match style {
        StructureStyle::Block => STYLE_BLOCK,
        StructureStyle::Flow => STYLE_FLOW,
    }
}

fn scalar_style(style: ScalarStyle) -> u8 {
    match style {
        ScalarStyle::Plain => STYLE_PLAIN,
        ScalarStyle::SingleQuoted => STYLE_SINGLE,
        ScalarStyle::DoubleQuoted => STYLE_DOUBLE,
        ScalarStyle::Literal => STYLE_LITERAL,
        ScalarStyle::Folded => STYLE_FOLDED,
    }
}

/// The `%TAG` directives before the first document. granit's parser applies them but does not
/// report them, so a scanner pass reads them for the directives side table; it runs only when a
/// directive line opens the input (after blank and comment lines). Scan errors end the pass; the
/// parser reports them.
/// granit-parser 2.1.0 accepts `%TAG !e!tag:x,2000:` (no separation between the handle and the
/// prefix), which YAML 1.2.2 production [88] forbids and rapidyaml rejects. Until upstream fixes
/// it, the directive lines that open the input are checked here: `%TAG`, blanks, a handle (`!`,
/// `!!` or `!word!`), then a blank. Only that defect is checked; granit validates the rest.
fn tag_separation(input: &str) -> Option<Failure> {
    let mut offset = 0usize;
    for (number, line) in input.split('\n').enumerate() {
        let trimmed = line.trim_start_matches([' ', '\t']);
        let start = offset;
        offset += line.len() + 1;
        if trimmed.is_empty() || trimmed.starts_with('#') {
            continue;
        }
        if !line.starts_with('%') {
            return None;
        }
        let Some(rest) = line.strip_prefix("%TAG") else { continue };
        let handle = rest.trim_start_matches([' ', '\t']);
        if handle.len() == rest.len() || !handle.starts_with('!') {
            continue; // other malformations: granit reports them
        }
        let bytes = handle.as_bytes();
        let mut end = 1;
        while bytes.get(end).is_some_and(|b| b.is_ascii_alphanumeric() || *b == b'-') {
            end += 1;
        }
        if bytes.get(end) == Some(&b'!') {
            end += 1;
        }
        if bytes.get(end).is_some_and(|b| *b != b' ' && *b != b'\t' && *b != b'\r') {
            let column = line.len() - handle.len() + end;
            return Some(Failure {
                status: STATUS_SYNTAX,
                line: clamp(number + 1),
                column: clamp(column + 1),
                offset: clamp(start + column),
                message: "expected whitespace between the %TAG handle and prefix".into(),
            });
        }
    }
    None
}

fn tag_directives(input: &str) -> Vec<(String, String)> {
    let opens_with_directive = input
        .lines()
        .find(|line| {
            let trimmed = line.trim_start_matches([' ', '\t']);
            !(trimmed.is_empty() || trimmed.starts_with('#'))
        })
        .is_some_and(|line| line.starts_with('%'));
    let mut out = Vec::new();
    if !opens_with_directive {
        return out;
    }
    for token in Scanner::with_options(StrInput::new(input), options()) {
        match token.as_ref().map(granit_parser::Token::token_type) {
            Ok(TokenType::TagDirective(handle, prefix)) => out.push((handle.to_string(), prefix.to_string())),
            Ok(TokenType::StreamStart | TokenType::VersionDirective(..) | TokenType::ReservedDirective(..)) => {}
            _ => break,
        }
    }
    out
}
