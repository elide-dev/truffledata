//! The validator. Every check, its order and its reason code mirror `BebopCodec`'s `validate`
//! methods and `MessageIndex.parse`; the Java differential tests hold the two together.

use std::collections::HashSet;

type R<T> = Result<T, u8>;

const TRUNCATED: u8 = 1;
const LENGTH: u8 = 2;
const COUNT_BYTES: u8 = 3;
const COUNT_LIMIT: u8 = 4;
const WORK_LIMIT: u8 = 5;
const ALLOCATION_LIMIT: u8 = 6;
const DEPTH_LIMIT: u8 = 7;
const STRING_BYTES: u8 = 8;
const STRING_TERMINATOR: u8 = 9;
const STRING_UTF8: u8 = 10;
const BOOLEAN: u8 = 11;
const NAN: u8 = 12;
const TIMESTAMP: u8 = 13;
const DURATION: u8 = 14;
const ENUM_MEMBER: u8 = 15;
const SET_ORDER: u8 = 16;
const MAP_ORDER: u8 = 17;
const MAP_DUPLICATE: u8 = 18;
const UNKNOWN_TAG: u8 = 19;
const FIELD_RANGE: u8 = 20;
const UNKNOWN_BRANCH: u8 = 21;
const BRANCH_RANGE: u8 = 22;
const TRAILING: u8 = 23;
const BYTE_LIMIT: u8 = 24;
const CONTROL_RESERVED: u8 = 25;
const CONTROL_WIDTH: u8 = 26;
const DIRECTORY_TRUNCATED: u8 = 27;
const DIRECTORY_ORDER: u8 = 28;
const DIRECTORY_RANK: u8 = 29;
const DIRECTORY_EMPTY_BLOCK: u8 = 30;
const DIRECTORY_TAG_256: u8 = 31;
const DIRECTORY_CANONICAL: u8 = 32;
const INDEX_SIZE: u8 = 33;
const PAYLOAD_NO_DIRECTORY: u8 = 34;
const WIDTH_MINIMAL: u8 = 35;
const BOUNDARY: u8 = 36;
const UTC_OFFSET: u8 = 37;
const PROGRAM: u8 = 255;

const MAGIC: u32 = 0x3150_4242;
const HEADER_WORDS: usize = 16;

const OP_SCALAR: u32 = 1;
const OP_ENUM: u32 = 2;
const OP_BYTES: u32 = 3;
const OP_ARRAY: u32 = 4;
const OP_MAP: u32 = 5;
const OP_STRUCT: u32 = 6;
const OP_MESSAGE: u32 = 7;
const OP_UNION: u32 = 8;

// Scalar kinds, in the order of the Java enum.
const BOOL: u32 = 0;
const BYTE: u32 = 1;
const INT8: u32 = 2;
const INT16: u32 = 3;
const UINT16: u32 = 4;
const INT32: u32 = 5;
const UINT32: u32 = 6;
const FLOAT16: u32 = 11;
const FLOAT32: u32 = 12;
const FLOAT64: u32 = 13;
const BFLOAT16: u32 = 14;
const TIMESTAMP_KIND: u32 = 16;
const DURATION_KIND: u32 = 17;
const STRING: u32 = 18;
const WIDTHS: [usize; 18] = [1, 1, 1, 2, 2, 4, 4, 8, 8, 16, 16, 2, 4, 8, 2, 16, 16, 12];

struct Program<'a> {
    bytes: &'a [u8],
}

impl Program<'_> {
    fn word(&self, index: usize) -> R<u32> {
        let at = index.checked_mul(4).ok_or(PROGRAM)?;
        let end = at.checked_add(4).ok_or(PROGRAM)?;
        let bytes = self.bytes.get(at..end).ok_or(PROGRAM)?;
        Ok(u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
    }

    fn long(&self, index: usize) -> R<u64> {
        Ok(self.word(index)? as u64 | (self.word(index + 1)? as u64) << 32)
    }
}

struct Validator<'a> {
    program: Program<'a>,
    buf: &'a [u8],
    pos: usize,
    end: usize,
    strict: bool,
    depth: u32,
    work: u64,
    allocation: u64,
    max_count: u64,
    max_depth: u32,
    max_work: u64,
    max_allocation: u64,
    nan16: u32,
    nan_brain16: u32,
    nan32: u32,
    nan64: u64,
}

pub fn run(program: &[u8], data: &[u8]) -> R<()> {
    let program = Program { bytes: program };
    if program.word(0)? != MAGIC || program.word(15)? as usize * 4 != program.bytes.len() {
        return Err(PROGRAM);
    }
    if data.len() as u64 > program.word(3)? as u64 {
        return Err(BYTE_LIMIT);
    }
    let root = program.word(2)? as usize;
    let mut v = Validator {
        buf: data,
        pos: 0,
        end: data.len(),
        strict: program.word(1)? & 1 != 0,
        depth: 0,
        work: 0,
        allocation: 0,
        max_count: program.word(4)? as u64,
        max_depth: program.word(5)?,
        max_work: program.long(6)?,
        max_allocation: program.long(8)?,
        nan16: program.word(10)?,
        nan_brain16: program.word(11)?,
        nan32: program.word(12)?,
        nan64: program.long(13)?,
        program,
    };
    v.node(root)?;
    if v.pos != v.end {
        return Err(TRAILING);
    }
    Ok(())
}

impl Validator<'_> {
    fn take(&mut self, n: usize) -> R<usize> {
        let at = self.pos;
        if n > self.end - at {
            return Err(TRUNCATED);
        }
        self.pos = at + n;
        Ok(at)
    }

    fn u16_at(&self, at: usize) -> u32 {
        u16::from_le_bytes([self.buf[at], self.buf[at + 1]]) as u32
    }

    fn u32_at(&self, at: usize) -> u32 {
        u32::from_le_bytes([self.buf[at], self.buf[at + 1], self.buf[at + 2], self.buf[at + 3]])
    }

    fn u64_at(&self, at: usize) -> u64 {
        self.u32_at(at) as u64 | (self.u32_at(at + 4) as u64) << 32
    }

    fn u32(&mut self) -> R<u64> {
        let at = self.take(4)?;
        Ok(self.u32_at(at) as u64)
    }

    fn length(&mut self, min: u64) -> R<usize> {
        let length = self.u32()?;
        if length < min || length > (self.end - self.pos) as u64 {
            return Err(LENGTH);
        }
        Ok(length as usize)
    }

    fn count(&mut self, min_element: u64) -> R<u64> {
        let count = self.u32()?;
        if min_element > 0 && count > (self.end - self.pos) as u64 / min_element {
            return Err(COUNT_BYTES);
        }
        if count > self.max_count {
            return Err(COUNT_LIMIT);
        }
        self.charge(count)?;
        Ok(count)
    }

    fn charge(&mut self, units: u64) -> R<()> {
        self.work += units;
        if self.work > self.max_work { Err(WORK_LIMIT) } else { Ok(()) }
    }

    fn enter(&mut self) -> R<()> {
        self.depth += 1;
        if self.depth > self.max_depth { Err(DEPTH_LIMIT) } else { Ok(()) }
    }

    fn node(&mut self, at: usize) -> R<()> {
        if at < HEADER_WORDS {
            return Err(PROGRAM);
        }
        match self.program.word(at)? {
            OP_SCALAR => {
                let word = self.program.word(at + 1)?;
                self.scalar(word & 0xFF, word & 0x100 != 0)
            }
            OP_ENUM => self.enumeration(at),
            OP_BYTES => {
                let length = self.program.word(at + 1)? as i32;
                let n = if length < 0 { self.length(0)? } else { length as usize };
                self.take(n).map(|_| ())
            }
            OP_ARRAY => self.array(at),
            OP_MAP => self.map(at),
            OP_STRUCT => {
                let fields = self.program.word(at + 1)? as usize;
                self.enter()?;
                self.charge(fields as u64)?;
                for i in 0..fields {
                    self.node(self.program.word(at + 2 + i)? as usize)?;
                }
                self.depth -= 1;
                Ok(())
            }
            OP_MESSAGE => self.message(at),
            OP_UNION => self.union(at),
            _ => Err(PROGRAM),
        }
    }

    fn scalar(&mut self, kind: u32, utc: bool) -> R<()> {
        if kind == STRING {
            let length = self.u32()?;
            if length >= (self.end - self.pos) as u64 {
                return Err(STRING_BYTES);
            }
            let length = length as usize;
            let at = self.take(length + 1)?;
            if self.buf[at + length] != 0 {
                return Err(STRING_TERMINATOR);
            }
            if !simdutf::validate_utf8(&self.buf[at..at + length]) {
                return Err(STRING_UTF8);
            }
            return Ok(());
        }
        let width = *WIDTHS.get(kind as usize).ok_or(PROGRAM)?;
        let at = self.take(width)?;
        // A UTC instant keeps its 16 bytes; only a zero offset is accepted.
        let offset_free = !utc || kind != TIMESTAMP_KIND || self.u32_at(at + 12) == 0;
        if !self.strict {
            return if offset_free { Ok(()) } else { Err(UTC_OFFSET) };
        }
        match kind {
            BOOL if self.buf[at] & 0xFE != 0 => Err(BOOLEAN),
            FLOAT16 => {
                let bits = self.u16_at(at);
                let nan = bits & 0x7C00 == 0x7C00 && bits & 0x3FF != 0;
                if nan && bits != self.nan16 { Err(NAN) } else { Ok(()) }
            }
            BFLOAT16 => {
                let bits = self.u16_at(at);
                let nan = bits & 0x7F80 == 0x7F80 && bits & 0x7F != 0;
                if nan && bits != self.nan_brain16 { Err(NAN) } else { Ok(()) }
            }
            FLOAT32 => {
                let bits = self.u32_at(at);
                if f32::from_bits(bits).is_nan() && bits != self.nan32 { Err(NAN) } else { Ok(()) }
            }
            FLOAT64 => {
                let bits = self.u64_at(at);
                if f64::from_bits(bits).is_nan() && bits != self.nan64 { Err(NAN) } else { Ok(()) }
            }
            TIMESTAMP_KIND => {
                let nanos = self.u32_at(at + 8) as i32;
                let offset = self.u32_at(at + 12) as i32;
                let ok = (0..=999_999_999).contains(&nanos)
                    && (-86_400_000..=86_400_000).contains(&offset);
                if !ok {
                    Err(TIMESTAMP)
                } else if !offset_free {
                    Err(UTC_OFFSET)
                } else {
                    Ok(())
                }
            }
            DURATION_KIND => {
                let seconds = self.u64_at(at) as i64;
                let nanos = self.u32_at(at + 8) as i32;
                let ok = nanos > -1_000_000_000
                    && nanos < 1_000_000_000
                    && !(seconds > 0 && nanos < 0)
                    && !(seconds < 0 && nanos > 0);
                if ok { Ok(()) } else { Err(DURATION) }
            }
            _ => Ok(()),
        }
    }

    fn enumeration(&mut self, at: usize) -> R<()> {
        let kind = self.program.word(at + 1)?;
        let width = *WIDTHS.get(kind as usize).ok_or(PROGRAM)?;
        let offset = self.take(width)?;
        if !self.strict {
            return Ok(());
        }
        let value = match kind {
            BYTE => self.buf[offset] as i64,
            INT8 => self.buf[offset] as i8 as i64,
            INT16 => self.u16_at(offset) as u16 as i16 as i64,
            UINT16 => self.u16_at(offset) as i64,
            INT32 => self.u32_at(offset) as i32 as i64,
            UINT32 => self.u32_at(offset) as i64,
            _ => self.u64_at(offset) as i64,
        };
        // Members are sorted as signed 64-bit values.
        let (mut low, mut high) = (0usize, self.program.word(at + 2)? as usize);
        while low < high {
            let middle = (low + high) / 2;
            let member = self.program.long(at + 3 + 2 * middle)? as i64;
            if member == value {
                return Ok(());
            }
            if member < value {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        Err(ENUM_MEMBER)
    }

    fn array(&mut self, at: usize) -> R<()> {
        let element = self.program.word(at + 1)? as usize;
        let ordered = self.program.word(at + 2)? != 0 && self.strict;
        let length = self.program.word(at + 3)? as i32;
        let n = if length < 0 {
            self.count(self.program.word(at + 4)? as u64)?
        } else {
            self.charge(length as u64)?;
            length as u64
        };
        self.enter()?;
        let mut previous = 0..0;
        for i in 0..n {
            let start = self.pos;
            self.node(element)?;
            if ordered {
                if i > 0 && self.buf[previous.clone()] >= self.buf[start..self.pos] {
                    return Err(SET_ORDER);
                }
                previous = start..self.pos;
            }
        }
        self.depth -= 1;
        Ok(())
    }

    fn map(&mut self, at: usize) -> R<()> {
        let key = self.program.word(at + 1)? as usize;
        let value = self.program.word(at + 2)? as usize;
        let n = self.count(self.program.word(at + 3)? as u64)?;
        let boolean_key = self.program.word(at + 4)? != 0;
        self.enter()?;
        if self.strict {
            let mut previous = 0..0;
            for i in 0..n {
                let start = self.pos;
                self.node(key)?;
                if i > 0 && self.buf[previous.clone()] >= self.buf[start..self.pos] {
                    return Err(MAP_ORDER);
                }
                previous = start..self.pos;
                self.node(value)?;
            }
        } else if boolean_key {
            // Any nonzero byte is true, so two encodings can be one key.
            let mut seen = 0;
            for _ in 0..n {
                let offset = self.take(1)?;
                let bit = if self.buf[offset] != 0 { 2 } else { 1 };
                self.node(value)?;
                // As in decoding, an entry is a duplicate only once it is whole.
                if seen & bit != 0 {
                    return Err(MAP_DUPLICATE);
                }
                seen |= bit;
            }
        } else {
            self.allocation += 12 * n;
            if self.allocation > self.max_allocation {
                return Err(ALLOCATION_LIMIT);
            }
            let buf = self.buf;
            let mut keys: HashSet<&[u8]> = HashSet::with_capacity(n as usize);
            for _ in 0..n {
                let start = self.pos;
                self.node(key)?;
                let key_end = self.pos;
                self.node(value)?;
                if !keys.insert(&buf[start..key_end]) {
                    return Err(MAP_DUPLICATE);
                }
            }
        }
        self.depth -= 1;
        Ok(())
    }

    fn message(&mut self, at: usize) -> R<()> {
        let length = self.length(1)?;
        let start = self.pos;
        let region_end = start + length;
        let index = Index::parse(self.buf, start, region_end)?;
        self.enter()?;
        self.charge(index.count as u64)?;
        let outer_end = self.end;
        for rank in 0..index.count {
            let field = self.program.word(at + 1 + index.tags[rank] as usize)? as usize;
            if field == 0 {
                if self.strict {
                    return Err(UNKNOWN_TAG);
                }
                continue;
            }
            self.pos = index.start(self.buf, rank);
            self.end = index.end(self.buf, rank);
            self.node(field)?;
            if self.pos != self.end {
                return Err(FIELD_RANGE);
            }
        }
        self.end = outer_end;
        self.pos = region_end;
        self.depth -= 1;
        Ok(())
    }

    fn union(&mut self, at: usize) -> R<()> {
        let length = self.length(1)?;
        let region_end = self.pos + length;
        let offset = self.take(1)?;
        let branch = self.program.word(at + 1 + self.buf[offset] as usize)? as usize;
        self.enter()?;
        if branch == 0 {
            if self.strict {
                return Err(UNKNOWN_BRANCH);
            }
            self.pos = region_end;
        } else {
            let outer_end = self.end;
            self.end = region_end;
            self.node(branch)?;
            if self.pos != region_end {
                return Err(BRANCH_RANGE);
            }
            self.end = outer_end;
        }
        self.depth -= 1;
        Ok(())
    }
}

/// The trailing index of a message, validated in full and accepted only in canonical form.
struct Index {
    tags: [u8; 255],
    count: usize,
    payload_start: usize,
    payload_size: usize,
    boundaries: usize,
    width: usize,
}

fn load(buf: &[u8], at: usize, width: usize) -> u64 {
    let mut value = 0u64;
    for i in 0..width {
        value |= (buf[at + i] as u64) << (8 * i);
    }
    value
}

impl Index {
    fn start(&self, buf: &[u8], rank: usize) -> usize {
        self.payload_start
            + if rank == 0 { 0 } else { load(buf, self.boundaries + (rank - 1) * self.width, self.width) as usize }
    }

    fn end(&self, buf: &[u8], rank: usize) -> usize {
        self.payload_start
            + if rank == self.count - 1 {
                self.payload_size
            } else {
                load(buf, self.boundaries + rank * self.width, self.width) as usize
            }
    }

    fn fill(&mut self, mut mask: u32, base: u32) {
        while mask != 0 {
            self.tags[self.count] = (base + mask.trailing_zeros() + 1) as u8;
            self.count += 1;
            mask &= mask - 1;
        }
    }

    /// `buf[start..end]` is the message content and is not empty.
    fn parse(buf: &[u8], start: usize, end: usize) -> R<Index> {
        let control = buf[end - 1] as u32;
        if control >> 5 != 0 {
            return Err(CONTROL_RESERVED);
        }
        let code = control & 3;
        if code == 3 {
            return Err(CONTROL_WIDTH);
        }
        let width = 1usize << code;
        let kind = control >> 2 & 7;
        let dir_end = end - 1;
        let avail = dir_end - start;
        let mut index =
            Index { tags: [0; 255], count: 0, payload_start: start, payload_size: 0, boundaries: 0, width };
        let dir_bytes: usize;
        match kind {
            0 => dir_bytes = 0,
            1..=3 => {
                dir_bytes = kind as usize;
                if avail < dir_bytes {
                    return Err(DIRECTORY_TRUNCATED);
                }
                let mut previous = 0;
                for i in 0..dir_bytes {
                    let tag = buf[dir_end - dir_bytes + i];
                    if tag <= previous {
                        return Err(DIRECTORY_ORDER);
                    }
                    previous = tag;
                    index.tags[i] = tag;
                }
                index.count = dir_bytes;
            }
            4..=6 => {
                dir_bytes = 1 << (kind - 4);
                if avail < dir_bytes {
                    return Err(DIRECTORY_TRUNCATED);
                }
                index.fill(load(buf, dir_end - dir_bytes, dir_bytes) as u32, 0);
            }
            _ => {
                if avail < 1 {
                    return Err(DIRECTORY_TRUNCATED);
                }
                let blocks = buf[dir_end - 1] as u32;
                dir_bytes = 1 + 5 * blocks.count_ones() as usize;
                if avail < dir_bytes {
                    return Err(DIRECTORY_TRUNCATED);
                }
                let first = dir_end - dir_bytes;
                let mut entry = first;
                let mut n = 0u32;
                for block in 0..8 {
                    if blocks & 1 << block == 0 {
                        continue;
                    }
                    let mask = load(buf, entry + 1, 4) as u32;
                    if buf[entry] as u32 != n {
                        return Err(DIRECTORY_RANK);
                    }
                    if mask == 0 {
                        return Err(DIRECTORY_EMPTY_BLOCK);
                    }
                    if block == 7 && mask & 0x8000_0000 != 0 {
                        return Err(DIRECTORY_TAG_256);
                    }
                    n += mask.count_ones();
                    entry += 5;
                }
                entry = first;
                for block in 0..8 {
                    if blocks & 1 << block == 0 {
                        continue;
                    }
                    index.fill(load(buf, entry + 1, 4) as u32, block * 32);
                    entry += 5;
                }
            }
        }
        let n = index.count;
        let max_tag = if n == 0 { 0 } else { index.tags[n - 1] as u32 };
        if kind != canonical_kind(n, max_tag) {
            return Err(DIRECTORY_CANONICAL);
        }
        let payload = avail as i64 - dir_bytes as i64 - (n.max(1) - 1) as i64 * width as i64;
        if payload < 0 {
            return Err(INDEX_SIZE);
        }
        if n == 0 && payload != 0 {
            return Err(PAYLOAD_NO_DIRECTORY);
        }
        let minimal = if payload <= 0xFF { 0 } else if payload <= 0xFFFF { 1 } else { 2 };
        if code != minimal {
            return Err(WIDTH_MINIMAL);
        }
        index.payload_size = payload as usize;
        index.boundaries = start + payload as usize;
        let mut previous = 0u64;
        for i in 0..n.max(1) - 1 {
            let boundary = load(buf, index.boundaries + i * width, width);
            if boundary < previous || boundary > payload as u64 {
                return Err(BOUNDARY);
            }
            previous = boundary;
        }
        Ok(index)
    }
}

fn canonical_kind(n: usize, max_tag: u32) -> u32 {
    if n == 0 {
        return 0;
    }
    let kind = if max_tag <= 8 { 4 } else if max_tag <= 16 { 5 } else if max_tag <= 32 { 6 } else { 7 };
    let size = if kind == 7 { 6 } else { 1 << (kind - 4) };
    if n <= 3 && n < size { n as u32 } else { kind }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn words(values: &[u32]) -> Vec<u8> {
        values.iter().flat_map(|w| w.to_le_bytes()).collect()
    }

    /// `message { 1: int32, 2: bool }` under the given flags, with generous limits.
    fn user_request(flags: u32) -> Vec<u8> {
        let mut program = vec![MAGIC, flags, 20, 1 << 20, 1 << 20, 64, 1 << 20, 0, 1 << 20, 0, 0, 0, 0, 0, 0, 0];
        program.extend([OP_SCALAR, 5, OP_SCALAR, BOOL]);
        let mut message = vec![OP_MESSAGE];
        message.extend([0u32; 256]);
        message[2] = 16;
        message[3] = 18;
        program.extend(message);
        program[15] = program.len() as u32;
        words(&program)
    }

    #[test]
    fn specification_examples() {
        let general = user_request(0);
        let strict = user_request(1);
        let both = [0x08, 0, 0, 0, 0x2a, 0, 0, 0, 0x01, 0x04, 0x03, 0x10];
        assert_eq!(run(&general, &both), Ok(()));
        assert_eq!(run(&strict, &both), Ok(()));
        assert_eq!(run(&general, &[0x06, 0, 0, 0, 0x2a, 0, 0, 0, 0x01, 0x10]), Ok(()));
        assert_eq!(run(&general, &[0x01, 0, 0, 0, 0x00]), Ok(()));
    }

    #[test]
    fn rejections() {
        let general = user_request(0);
        let strict = user_request(1);
        let truth = [0x08, 0, 0, 0, 0x2a, 0, 0, 0, 0x02, 0x04, 0x03, 0x10];
        assert_eq!(run(&general, &truth), Ok(()));
        assert_eq!(run(&strict, &truth), Err(BOOLEAN));
        // Tiny 1 where Mask 8 ties, a reserved bit, a trailing byte, an unknown tag.
        assert_eq!(run(&general, &[0x06, 0, 0, 0, 0x2a, 0, 0, 0, 0x01, 0x04]), Err(DIRECTORY_CANONICAL));
        assert_eq!(run(&general, &[0x06, 0, 0, 0, 0x2a, 0, 0, 0, 0x01, 0x30]), Err(CONTROL_RESERVED));
        assert_eq!(run(&general, &[0x01, 0, 0, 0, 0x00, 0x00]), Err(TRAILING));
        let unknown = [0x09, 0, 0, 0, 0x2a, 0, 0, 0, 0xff, 0xff, 0x04, 0x05, 0x10];
        assert_eq!(run(&general, &unknown), Ok(()));
        assert_eq!(run(&strict, &unknown), Err(UNKNOWN_TAG));
        assert_eq!(run(&general, &[]), Err(TRUNCATED));
        assert_eq!(run(&[], &[]), Err(PROGRAM));
        assert_eq!(run(&general[..general.len() - 4], &both_len()), Err(PROGRAM));
    }

    fn both_len() -> [u8; 5] {
        [0x01, 0, 0, 0, 0x00]
    }
}
