//! Schema-directed borrowed decoding. The index contains typed values, never Java wire work.
use super::{ALLOCATION, BUDGET, Field, MALFORMED};
use buffa::encoding::{Tag, WireType, decode_varint};
use buffa::text::{TokenKind, Tokenizer};
use std::mem::size_of;

#[path = "typed_json.rs"]
mod json;

type Result<T> = std::result::Result<T, i32>;
#[derive(Clone, Copy)]
struct Schema<'a>(&'a [u8]);
#[derive(Clone, Copy)]
struct Contract<'a> {
    schema: Schema<'a>,
    at: usize,
    ordinal: u32,
}
impl<'a> Schema<'a> {
    fn word(self, at: usize) -> Result<u32> {
        Ok(u32::from_le_bytes(
            self.0
                .get(at..at.checked_add(4).ok_or(MALFORMED)?)
                .ok_or(MALFORMED)?
                .try_into()
                .unwrap(),
        ))
    }
    fn text(self, at: usize) -> Result<&'a str> {
        let start = self.word(at)? as usize;
        let len = self.word(at + 4)? as usize;
        super::unicode::validated(
            self.0
                .get(start..start.checked_add(len).ok_or(MALFORMED)?)
                .ok_or(MALFORMED)?,
        )
        .map_err(|_| MALFORMED)
    }
    fn ty(self, index: u32) -> Result<usize> {
        if self.word(0)? != 0x54444233 || index >= self.word(4)? {
            return Err(MALFORMED);
        }
        Ok(self.word(8 + index as usize * 4)? as usize)
    }
    fn field(self, ty: u32, ordinal: u32) -> Result<Contract<'a>> {
        let base = self.ty(ty)?;
        if ordinal >= self.word(base)? {
            return Err(MALFORMED);
        }
        let at = base
            .checked_add(16 + ordinal as usize * 40)
            .ok_or(MALFORMED)?;
        if self.0.get(at..at + 40).is_none() {
            return Err(MALFORMED);
        }
        Ok(Contract {
            schema: self,
            at,
            ordinal,
        })
    }
    fn find(self, ty: u32, number: u32) -> Result<Option<Contract<'a>>> {
        for i in 0..self.word(self.ty(ty)?)? {
            let f = self.field(ty, i)?;
            if f.word(0)? == number {
                return Ok(Some(f));
            }
        }
        Ok(None)
    }
    fn name(self, ty: u32, name: &str, json: bool) -> Result<Contract<'a>> {
        let mut found = None;
        for i in 0..self.word(self.ty(ty)?)? {
            let f = self.field(ty, i)?;
            if f.schema.text(f.at + 24)? == name || (json && f.schema.text(f.at + 32)? == name) {
                if found.is_some() {
                    return Err(MALFORMED);
                }
                found = Some(f);
            }
        }
        found.ok_or(MALFORMED)
    }
}
impl Contract<'_> {
    fn word(self, offset: usize) -> Result<u32> {
        self.schema.word(self.at + offset)
    }
    fn kind(self) -> Result<u32> {
        self.word(4)
    }
    fn repeated(self) -> Result<bool> {
        Ok(self.word(8)? & 1 != 0)
    }
    fn enumeration(self, value: i32) -> Result<bool> {
        if self.kind()? != 14 || self.word(8)? & 16 != 0 {
            return Ok(true);
        }
        let table = self.word(16)? as usize;
        for i in 0..self.word(20)? as usize {
            if self.schema.word(table + i * 12)? as i32 == value {
                return Ok(true);
            }
        }
        Ok(false)
    }
    fn enum_name(self, name: &str) -> Result<Option<i32>> {
        let table = self.word(16)? as usize;
        for i in 0..self.word(20)? as usize {
            if self.schema.text(table + i * 12 + 4)? == name {
                return Ok(Some(self.schema.word(table + i * 12)? as i32));
            }
        }
        Ok(None)
    }
}
fn wire(kind: u32) -> Result<u32> {
    match kind {
        1 | 6 | 16 => Ok(1),
        2 | 7 | 15 => Ok(5),
        9 | 11 | 12 => Ok(2),
        10 => Ok(3),
        3 | 4 | 5 | 8 | 13 | 14 | 17 | 18 => Ok(0),
        _ => Err(MALFORMED),
    }
}
pub struct Decoder<'a, 'b> {
    schema: Schema<'a>,
    input: &'a [u8],
    payload: &'b mut [u8],
    used: usize,
    fields: Vec<Field>,
    cells: u64,
    strings: u64,
    depth: u32,
    native: u64,
    view_ready: bool,
}
impl<'a, 'b> Decoder<'a, 'b> {
    fn charge(&mut self, n: u64) -> Result<()> {
        self.cells = self.cells.checked_sub(n).ok_or(BUDGET)?;
        Ok(())
    }
    fn scratch(&self, n: usize) -> Result<()> {
        if (self.fields.capacity() * size_of::<Field>()) as u64 + n as u64 > self.native {
            Err(BUDGET)
        } else {
            Ok(())
        }
    }
    fn push(&mut self, f: Contract<'_>, value: u64, length: u64) -> Result<usize> {
        self.charge(1)?;
        let i = self.fields.len();
        if i == self.fields.capacity() {
            let maximum = (self.native / size_of::<Field>() as u64) as usize;
            let capacity = i.saturating_mul(2).max(4).min(maximum.saturating_sub(i));
            if capacity <= i {
                return Err(BUDGET);
            }
            self.fields
                .try_reserve_exact(capacity - i)
                .map_err(|_| ALLOCATION)?;
        }
        self.fields.push(Field {
            number: f.ordinal + 1,
            wire: f.kind()?,
            value,
            length: super::index_length(length)?,
            end: (i + 1) as u32,
        });
        Ok(i)
    }
    fn bytes(&mut self, kind: u32, bytes: &[u8]) -> Result<()> {
        if kind == 9 {
            let n = 2 * super::unicode::utf16_length(bytes)? as u64;
            self.strings = self.strings.checked_sub(n).ok_or(BUDGET)?;
        } else {
            self.charge(bytes.len() as u64)?;
        }
        Ok(())
    }
    fn store(&mut self, f: Contract<'_>, bytes: &[u8]) -> Result<()> {
        self.bytes(f.kind()?, bytes)?;
        let start = self.used;
        self.used = self.used.checked_add(bytes.len()).ok_or(BUDGET)?;
        self.payload
            .get_mut(start..self.used)
            .ok_or(BUDGET)?
            .copy_from_slice(bytes);
        self.push(f, start as u64, bytes.len() as u64)?;
        Ok(())
    }
    fn binary(&mut self, ty: u32, input: &mut &'a [u8], group: u32, depth: u32) -> Result<bool> {
        if depth > self.depth {
            return Err(BUDGET);
        }
        let base = self.schema.ty(ty)?;
        let count = self.schema.word(base)?;
        self.charge(count as u64)?;
        // Bit 9 is a compiler-established fact: this type has no required fields. Java retains
        // its semantic fallback for required-field graphs and unsupported schema shapes.
        if self.view_ready && (count > 64 || self.schema.word(base + 4)? & 512 == 0) {
            self.view_ready = false;
        }
        let mut seen_messages = 0u64;
        let mut invalid_map = false;
        while !input.is_empty() {
            let tag = Tag::decode(input).map_err(|_| MALFORMED)?;
            let number = tag.field_number();
            let wt = tag.wire_type() as u32;
            if wt == 4 {
                return if group == number && group != 0 {
                    Ok(invalid_map)
                } else {
                    Err(MALFORMED)
                };
            }
            let Some(f) = self.schema.find(ty, number)? else {
                self.skip(input, number, wt, depth)?;
                continue;
            };
            let kind = f.kind()?;
            if f.repeated()? && ![9, 10, 11, 12].contains(&kind) && wt == 2 {
                let mut packed = take_bytes(input)?;
                while !packed.is_empty() {
                    let value = scalar(kind, &mut packed)?;
                    if f.enumeration(value as i32)? {
                        self.push(f, value, 0)?;
                    }
                }
                continue;
            }
            if wire(kind)? != wt {
                self.skip(input, number, wt, depth)?;
                continue;
            }
            if kind == 10 || kind == 11 {
                if self.view_ready && !f.repeated()? {
                    let bit = 1u64 << f.ordinal;
                    if seen_messages & bit != 0 {
                        self.view_ready = false;
                    }
                    seen_messages |= bit;
                }
                let i = self.push(f, 0, 0)?;
                let invalid = if kind == 11 {
                    let mut nested = take_bytes(input)?;
                    self.binary(f.word(12)?, &mut nested, 0, depth + 1)?
                } else {
                    self.binary(f.word(12)?, input, number, depth + 1)?
                };
                self.fields[i].length = invalid as u32;
                self.fields[i].end = self.fields.len() as u32;
            } else if kind == 9 || kind == 12 {
                let bytes = take_bytes(input)?;
                self.bytes(kind, bytes)?;
                self.push(
                    f,
                    (bytes.as_ptr() as usize - self.input.as_ptr() as usize) as u64,
                    bytes.len() as u64,
                )?;
            } else {
                let value = scalar(kind, input)?;
                let accepted = f.enumeration(value as i32)?;
                if self.schema.word(self.schema.ty(ty)? + 4)? & 1 != 0 && number == 2 && kind == 14
                {
                    invalid_map = !accepted;
                }
                if accepted {
                    self.push(f, value, 0)?;
                }
            }
        }
        if group != 0 {
            Err(MALFORMED)
        } else {
            Ok(invalid_map)
        }
    }
    fn skip(&mut self, input: &mut &[u8], number: u32, wt: u32, depth: u32) -> Result<()> {
        match wt {
            0 => {
                varint(input)?;
            }
            1 => {
                take(input, 8)?;
            }
            2 => {
                take_bytes(input)?;
            }
            5 => {
                take(input, 4)?;
            }
            3 => {
                if depth >= self.depth {
                    return Err(BUDGET);
                }
                loop {
                    let t = Tag::decode(input).map_err(|_| MALFORMED)?;
                    if t.wire_type() == WireType::EndGroup {
                        if t.field_number() != number {
                            return Err(MALFORMED);
                        }
                        break;
                    }
                    self.skip(input, t.field_number(), t.wire_type() as u32, depth + 1)?;
                }
            }
            _ => return Err(MALFORMED),
        }
        Ok(())
    }
    fn text(
        &mut self,
        ty: u32,
        tokens: &mut Tokenizer<'a>,
        depth: u32,
        nested: bool,
    ) -> Result<()> {
        if depth > self.depth {
            return Err(BUDGET);
        }
        self.charge(self.schema.word(self.schema.ty(ty)?)? as u64)?;
        loop {
            let token = tokens.read().map_err(|_| MALFORMED)?;
            match token.kind {
                TokenKind::Eof if !nested => return Ok(()),
                TokenKind::MessageClose if nested => return Ok(()),
                TokenKind::Name => {}
                _ => return Err(MALFORMED),
            }
            let f = self.schema.name(ty, token.raw, false)?;
            if tokens.peek().map_err(|_| MALFORMED)?.kind == TokenKind::ListOpen {
                if !f.repeated()? {
                    return Err(MALFORMED);
                }
                let open = tokens.read().map_err(|_| MALFORMED)?;
                if ![10, 11].contains(&f.kind()?)
                    && !self.input[token.pos + token.raw.len()..open.pos].contains(&b':')
                {
                    return Err(MALFORMED);
                }
                while tokens.peek().map_err(|_| MALFORMED)?.kind != TokenKind::ListClose {
                    self.text_value(f, tokens, depth)?;
                }
                tokens.read().map_err(|_| MALFORMED)?;
            } else {
                self.text_value(f, tokens, depth)?;
            }
        }
    }
    fn text_value(
        &mut self,
        f: Contract<'a>,
        tokens: &mut Tokenizer<'a>,
        depth: u32,
    ) -> Result<()> {
        let token = tokens.read().map_err(|_| MALFORMED)?;
        let kind = f.kind()?;
        if kind == 10 || kind == 11 {
            if token.kind != TokenKind::MessageOpen {
                return Err(MALFORMED);
            }
            let i = self.push(f, 0, 0)?;
            self.text(f.word(12)?, tokens, depth + 1, true)?;
            self.fields[i].end = self.fields.len() as u32;
        } else {
            if token.kind != TokenKind::Scalar {
                return Err(MALFORMED);
            }
            if kind == 9 || kind == 12 {
                self.scratch(token.raw.len())?;
                if kind == 9 {
                    let text = buffa::text::unescape_str(token.raw).map_err(|_| MALFORMED)?;
                    self.store(f, text.as_bytes())?;
                } else {
                    let bytes = buffa::text::unescape(token.raw).map_err(|_| MALFORMED)?;
                    self.store(f, &bytes)?;
                }
            } else {
                let value = text_scalar(f, token.raw)?;
                if !f.enumeration(value as i32)? {
                    return Err(MALFORMED);
                }
                self.push(f, value, 0)?;
            }
        }
        Ok(())
    }
}
fn varint(input: &mut &[u8]) -> Result<u64> {
    decode_varint(input).map_err(|_| MALFORMED)
}
fn take<'a>(input: &mut &'a [u8], n: usize) -> Result<&'a [u8]> {
    if n > input.len() {
        return Err(MALFORMED);
    }
    let (head, tail) = input.split_at(n);
    *input = tail;
    Ok(head)
}
fn take_bytes<'a>(input: &mut &'a [u8]) -> Result<&'a [u8]> {
    let n = usize::try_from(varint(input)?).map_err(|_| MALFORMED)?;
    take(input, n)
}
fn scalar(kind: u32, input: &mut &[u8]) -> Result<u64> {
    Ok(match kind {
        1 | 6 | 16 => u64::from_le_bytes(take(input, 8)?.try_into().unwrap()),
        2 | 7 | 15 => u32::from_le_bytes(take(input, 4)?.try_into().unwrap()) as u64,
        3 | 4 | 5 | 13 | 14 => varint(input)?,
        8 => (varint(input)? != 0) as u64,
        17 => {
            let n = varint(input)? as u32;
            ((n >> 1) as i32 ^ -((n & 1) as i32)) as u64
        }
        18 => {
            let n = varint(input)?;
            ((n >> 1) as i64 ^ -((n & 1) as i64)) as u64
        }
        _ => return Err(MALFORMED),
    })
}
fn text_scalar(f: Contract<'_>, text: &str) -> Result<u64> {
    if text.len() > 128 {
        return Err(BUDGET);
    }
    let kind = f.kind()?;
    if kind == 8 {
        return match text {
            "true" | "True" | "t" | "1" => Ok(1),
            "false" | "False" | "f" | "0" => Ok(0),
            _ => Err(MALFORMED),
        };
    }
    if kind == 1 || kind == 2 {
        let number = match text.to_ascii_lowercase().as_str() {
            "inf" | "infinity" | "inff" | "infinityf" => f64::INFINITY,
            "-inf" | "-infinity" | "-inff" | "-infinityf" => f64::NEG_INFINITY,
            "nan" | "nanf" => f64::NAN,
            _ => text
                .trim_end_matches(['f', 'F'])
                .parse::<f64>()
                .map_err(|_| MALFORMED)?,
        };
        return Ok(if kind == 2 {
            (number as f32).to_bits() as u64
        } else {
            number.to_bits()
        });
    }
    if kind == 14 {
        if let Some(n) = f.enum_name(text)? {
            return Ok(n as u64);
        }
    }
    let negative = text.starts_with('-');
    let digits = text.strip_prefix(['-', '+']).unwrap_or(text);
    let (radix, digits) = if let Some(d) = digits
        .strip_prefix("0x")
        .or_else(|| digits.strip_prefix("0X"))
    {
        (16, d)
    } else if digits.len() > 1 && digits.starts_with('0') {
        (8, digits)
    } else {
        (10, digits)
    };
    let magnitude = u64::from_str_radix(digits, radix).map_err(|_| MALFORMED)?;
    integer(kind, negative, magnitude)
}
fn integer(kind: u32, negative: bool, magnitude: u64) -> Result<u64> {
    let signed = [3, 5, 14, 15, 16, 17, 18].contains(&kind);
    let narrow = [5, 7, 13, 14, 15, 17].contains(&kind);
    let maximum = match (signed, narrow, negative) {
        (true, true, false) => i32::MAX as u64,
        (true, true, true) => 1 << 31,
        (true, false, false) => i64::MAX as u64,
        (true, false, true) => 1 << 63,
        (false, true, _) => u32::MAX as u64,
        (false, false, _) => u64::MAX,
    };
    if (!signed && negative) || magnitude > maximum {
        return Err(MALFORMED);
    }
    Ok(if negative {
        0u64.wrapping_sub(magnitude)
    } else {
        magnitude
    })
}

pub fn decode(
    schema: &[u8],
    input: &[u8],
    format: u32,
    payload: &mut [u8],
    cells: u64,
    strings: u64,
    depth: u32,
    native: u64,
) -> Result<Vec<Field>> {
    decode_reusing(
        schema,
        input,
        format,
        payload,
        cells,
        strings,
        depth,
        native,
        Vec::new(),
    )
}

/// Reuse only scratch index storage. No field value or source region survives a decode.
/// On failure the owned scratch allocation is dropped, just like the fresh-buffer route.
pub fn decode_reusing(
    schema: &[u8],
    input: &[u8],
    format: u32,
    payload: &mut [u8],
    cells: u64,
    strings: u64,
    depth: u32,
    native: u64,
    fields: Vec<Field>,
) -> Result<Vec<Field>> {
    let mut status = 0;
    decode_reusing_with_status(
        schema,
        input,
        format,
        payload,
        cells,
        strings,
        depth,
        native,
        fields,
        &mut status,
    )
}

/// ABI 8: format 3 requests binary view certification; status 5 proves that visited types
/// have no required fields and no singular-message fragments require merging. Status 0
/// retains the ordinary Java semantic check. Schema facts come from the trusted compiler.
pub fn decode_reusing_with_status(
    schema: &[u8],
    input: &[u8],
    format: u32,
    payload: &mut [u8],
    cells: u64,
    strings: u64,
    depth: u32,
    native: u64,
    mut fields: Vec<Field>,
    status: &mut i32,
) -> Result<Vec<Field>> {
    *status = 0;
    if depth == 0 || depth > 128 || native > i32::MAX as u64 {
        return Err(BUDGET);
    }
    // Reserve scratch headroom independently of index growth (including overlapping reallocations).
    // Text token/escape scratch is bounded by input length; the caller owns the payload region.
    let reserve = if format == 0 || format == 3 {
        0
    } else {
        (input.len() as u64).checked_mul(4).ok_or(BUDGET)?
    };
    let native = native.checked_sub(reserve).ok_or(BUDGET)?;
    fields.clear();
    // A smaller per-call budget must not inherit a larger retained allocation. Release it
    // before allocating again, so growth's existing old-plus-new peak accounting still holds.
    let maximum = native / size_of::<Field>() as u64;
    let capacity = fields.capacity() as u64;
    // A previously budget-truncated capacity can require a relocation that a fresh decode
    // would avoid. Restart such growth from the canonical 4, 8, 16... sequence so reuse
    // cannot reject an input which fits through the fresh-buffer route at this budget.
    if capacity > maximum || (capacity < maximum && (capacity < 4 || !capacity.is_power_of_two())) {
        fields = Vec::new();
    }
    let mut decoder = Decoder {
        schema: Schema(schema),
        input,
        payload,
        used: 0,
        fields,
        cells,
        strings,
        depth,
        native,
        view_ready: format == 3,
    };
    match format {
        0 | 3 => {
            decoder.binary(0, &mut &input[..], 0, 1)?;
        }
        1 => {
            let text = super::unicode::validated(input).map_err(|_| MALFORMED)?;
            decoder.text(0, &mut Tokenizer::new(text), 1, false)?;
        }
        2 => decoder.json()?,
        _ => return Err(MALFORMED),
    }
    if decoder.view_ready {
        *status = 5;
    }
    Ok(decoder.fields)
}

/// Fixed scalar output: no index allocation, input copying, or heap-backed staging.
/// Only singular int32/int64 schemas with at most two fields are accepted.
pub fn scalars(
    schema: &[u8],
    input: &[u8],
    cells: u64,
    depth: u32,
) -> Result<super::seam::Scalars> {
    let schema = Schema(schema);
    let count = schema.word(schema.ty(0)?)?;
    if count == 0 || count > 2 {
        return Err(MALFORMED);
    }
    if depth == 0 {
        return Err(BUDGET);
    }
    for i in 0..count {
        let field = schema.field(0, i)?;
        if field.repeated()? || ![3, 5].contains(&field.kind()?) {
            return Err(MALFORMED);
        }
    }
    let mut decoder = Decoder {
        schema,
        input,
        payload: &mut [],
        used: 0,
        fields: Vec::new(),
        cells,
        strings: 0,
        depth,
        native: 0,
        view_ready: false,
    };
    decoder.charge(count as u64)?;
    let mut result = super::seam::Scalars {
        first: 0,
        second: 0,
        present: 0,
    };
    let mut remaining = input;
    while !remaining.is_empty() {
        let tag = Tag::decode(&mut remaining).map_err(|_| MALFORMED)?;
        let number = tag.field_number();
        let wt = tag.wire_type() as u32;
        if let Some(field) = schema.find(0, number)? {
            if wt == 0 {
                decoder.charge(1)?;
                let value = varint(&mut remaining)?;
                let value = if field.kind()? == 5 {
                    value as i32 as i64 as u64
                } else {
                    value
                };
                if field.ordinal == 0 {
                    result.first = value;
                } else {
                    result.second = value;
                }
                result.present |= 1 << field.ordinal;
                continue;
            }
        }
        decoder.skip(&mut remaining, number, wt, 1)?;
    }
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn schema(kind: u32) -> Vec<u8> {
        let mut bytes = Vec::new();
        for word in [
            0x54444233u32,
            1,
            12,
            1,
            0,
            0,
            0,
            1,
            kind,
            0,
            u32::MAX,
            0,
            0,
            68,
            1,
            68,
            1,
        ] {
            bytes.extend_from_slice(&word.to_le_bytes());
        }
        bytes.push(b'x');
        bytes
    }
    #[test]
    fn binary_view_proof_requires_compiler_facts_and_no_message_merges() {
        fn run(schema: &[u8], input: &[u8], cells: u64) -> (Result<Vec<Field>>, i32) {
            let mut status = -1;
            let result = decode_reusing_with_status(
                schema,
                input,
                3,
                &mut [],
                cells,
                1000,
                16,
                4096,
                Vec::new(),
                &mut status,
            );
            (result, status)
        }
        let mut scalar = schema(5);
        assert_eq!(run(&scalar, &[8, 42], 100).1, 0);
        scalar[16..20].copy_from_slice(&512u32.to_le_bytes());
        let (fields, status) = run(&scalar, &[8, 42, 8, 7], 100);
        assert_eq!(fields.unwrap().len(), 2);
        assert_eq!(status, 5); // Duplicate scalars still use last-value semantics.
        assert_eq!(run(&scalar, &[], 100).1, 5);
        let (result, status) = run(&scalar, &[8, 128], 100);
        assert!(result.is_err());
        assert_eq!(status, 0);
        let (result, status) = run(&scalar, &[8, 42], 1);
        assert!(matches!(result, Err(BUDGET)));
        assert_eq!(status, 0);
        let mut message = schema(11);
        message[16..20].copy_from_slice(&512u32.to_le_bytes());
        message[40..44].copy_from_slice(&0u32.to_le_bytes());
        assert_eq!(run(&message, &[10, 0], 100).1, 5);
        assert_eq!(run(&message, &[10, 2, 10, 0], 100).1, 5);
        assert_eq!(run(&message, &[10, 0, 10, 0], 100).1, 0);
        assert_eq!(run(&message, &[10, 4, 10, 0, 10, 0], 100).1, 0);
        message[36..40].copy_from_slice(&1u32.to_le_bytes()); // repeated messages do not merge
        assert_eq!(run(&message, &[10, 0, 10, 0], 100).1, 5);
    }

    #[test]
    fn scalar_output_matches_index_without_native_allocation() {
        for kind in [3, 5] {
            let s = schema(kind);
            for input in [
                &[][..],
                &[8, 1],
                &[8, 1, 8, 0],
                &[8, 255, 255, 255, 255, 15],
                &[24, 7, 8, 2],
                &[27, 8, 9, 28, 8, 2],
            ] {
                let result = super::super::allocation_probe::without_allocation(|| {
                    scalars(&s, input, 100, 4)
                })
                .unwrap();
                let index = decode(&s, input, 0, &mut [], 100, 0, 4, 1024).unwrap();
                assert_eq!(result.present, u64::from(!index.is_empty()));
                if let Some(last) = index.last() {
                    let expected = if kind == 5 {
                        last.value as i32 as i64 as u64
                    } else {
                        last.value
                    };
                    assert_eq!(result.first, expected);
                }
                assert_eq!(result.second, 0);
            }
            assert_eq!(scalars(&s, &[8, 1], 1, 4).err(), Some(BUDGET));
            assert_eq!(scalars(&s, &[], 100, 0).err(), Some(BUDGET));
            assert_eq!(scalars(&s, &[27, 28], 100, 1).err(), Some(BUDGET));
            for input in [&[8, 128][..], &[0], &[12], &[27, 36], &[27]] {
                assert_eq!(scalars(&s, input, 100, 4).err(), Some(MALFORMED));
            }
        }
        assert_eq!(scalars(&schema(9), &[], 100, 4).err(), Some(MALFORMED));
    }

    #[test]
    fn reused_index_is_allocation_free_and_respects_smaller_budgets() {
        let s = schema(3);
        let input = [8, 1].repeat(32);
        let fields = decode(&s, &input, 0, &mut [], 1000, 0, 4, 8192).unwrap();
        let pointer = fields.as_ptr();
        let capacity = fields.capacity();
        let fields = super::super::allocation_probe::without_allocation(|| {
            decode_reusing(&s, &[8, 9], 0, &mut [], 1000, 0, 4, 8192, fields)
        })
        .unwrap();
        assert_eq!(fields.as_ptr(), pointer);
        assert_eq!(fields.capacity(), capacity);
        assert_eq!(fields.len(), 1);
        assert_eq!(fields[0].value, 9);
        // A retained large buffer cannot quietly exceed the next call's smaller budget.
        let fields = decode_reusing(
            &s,
            &[8, 7],
            0,
            &mut [],
            1000,
            0,
            4,
            size_of::<Field>() as u64,
            fields,
        )
        .unwrap();
        assert_eq!(fields.capacity(), 1);
        assert_eq!(fields[0].value, 7);
        let fields = decode_reusing(&s, &[], 0, &mut [], 1000, 0, 4, 0, fields).unwrap();
        assert_eq!(fields.capacity(), 0);
        assert!(fields.is_empty());
    }

    #[test]
    fn retained_capacity_cannot_make_a_fresh_valid_budget_fail() {
        let s = schema(3);
        for count in 0..=32 {
            let input = [8, 1].repeat(count);
            for budget in 0..=64 {
                let bytes = budget * size_of::<Field>() as u64;
                if let Ok(expected) = decode(&s, &input, 0, &mut [], 1000, 0, 4, bytes) {
                    for previous in [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 16, 32, 64] {
                        let reused = decode_reusing(
                            &s,
                            &input,
                            0,
                            &mut [],
                            1000,
                            0,
                            4,
                            bytes,
                            Vec::with_capacity(previous),
                        )
                        .unwrap();
                        assert_eq!(reused.len(), expected.len());
                        assert!(reused.capacity() as u64 * size_of::<Field>() as u64 <= bytes);
                        for (a, b) in reused.iter().zip(&expected) {
                            assert_eq!(
                                (a.number, a.wire, a.value, a.length, a.end),
                                (b.number, b.wire, b.value, b.length, b.end)
                            );
                        }
                    }
                }
            }
        }
    }

    #[test]
    fn reusable_ffi_result_transfers_ownership_and_recovers_after_failure() {
        let s = schema(3);
        let mut result = super::super::seam::Result {
            data: std::ptr::null_mut(),
            count: 0,
            capacity: 0,
        };
        let call = |result: &mut super::super::seam::Result, input: &[u8], budget| unsafe {
            super::super::td_buffa_typed_reuse(
                s.as_ptr(),
                s.len() as u64,
                input.as_ptr(),
                input.len() as u64,
                0,
                [].as_mut_ptr(),
                0,
                1000,
                0,
                4,
                budget,
                (result as *mut super::super::seam::Result).cast(),
            )
        };
        assert_eq!(call(&mut result, &[8, 1], 8192), 0);
        let pointer = result.data;
        let status =
            super::super::allocation_probe::without_allocation(|| call(&mut result, &[8, 2], 8192));
        assert_eq!(status, 0);
        assert_eq!(result.data, pointer);
        assert_eq!(result.count, 1);
        assert_eq!(unsafe { (*result.data.cast::<Field>()).value }, 2);
        assert_eq!(call(&mut result, &[8, 128], 8192), MALFORMED);
        assert!(result.data.is_null());
        assert_eq!((result.count, result.capacity), (0, 0));
        assert_eq!(call(&mut result, &[8, 3], 8192), 0);
        assert_eq!(call(&mut result, &[], 0), 0);
        assert_eq!((result.count, result.capacity), (0, 0));
        assert_eq!(call(&mut result, &[8, 4], 8192), 0);
        unsafe {
            super::super::td_buffa_release((&mut result as *mut super::super::seam::Result).cast())
        };
        assert!(result.data.is_null());
        assert_eq!((result.count, result.capacity), (0, 0));
    }

    #[test]
    fn typed_binary_borrows_and_enforces_budgets() {
        let s = schema(12);
        let data = [10, 3, 1, 2, 255];
        let index = decode(&s, &data, 0, &mut [], 100, 100, 4, 1024).unwrap();
        assert_eq!((index[0].value, index[0].length), (2, 3));
        assert_eq!(
            decode(&s, &data, 0, &mut [], 2, 100, 4, 1024).err(),
            Some(BUDGET)
        );
        assert_eq!(
            decode(&s, &data, 0, &mut [], 100, 100, 4, 1).err(),
            Some(BUDGET)
        );
        for length in 1..data.len() {
            assert!(decode(&s, &data[..length], 0, &mut [], 100, 100, 4, 1024).is_err());
        }
        for length in 0..s.len() - 1 {
            assert!(decode(&s[..length], &data, 0, &mut [], 100, 100, 4, 1024).is_err());
        }
    }
    #[test]
    fn typed_text_and_json_validate_utf8_and_numeric_ranges() {
        for (format, input) in [(1, "x: 'hello'"), (2, "{\"x\":\"hello\"}")] {
            let mut payload = [0; 64];
            let index = decode(
                &schema(9),
                input.as_bytes(),
                format,
                &mut payload,
                100,
                100,
                4,
                1024,
            )
            .unwrap();
            assert_eq!(index[0].length, 5);
            assert_eq!(&payload[..5], b"hello");
            assert_eq!(
                decode(
                    &schema(9),
                    input.as_bytes(),
                    format,
                    &mut payload,
                    100,
                    1,
                    4,
                    1024
                )
                .err(),
                Some(BUDGET)
            );
            assert!(
                decode(
                    &schema(9),
                    input.as_bytes(),
                    format,
                    &mut [],
                    100,
                    100,
                    4,
                    1024
                )
                .is_err()
            );
        }
        for input in [
            "{\"x\":2147483648}",
            "{\"x\":1e99999999}",
            "{\"x\":1.5}",
            "{\"x\":01}",
        ] {
            assert!(decode(&schema(5), input.as_bytes(), 2, &mut [], 100, 100, 4, 4096).is_err());
        }
        assert!(decode(&schema(9), &[10, 1, 255], 0, &mut [], 100, 100, 4, 1024).is_err());
    }
}
