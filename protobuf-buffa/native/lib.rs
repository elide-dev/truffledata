//! Borrowed wire decoding: Buffa reads the caller's slice; payloads stay in that slice.
//! The caller owns input memory and lends it immutably for the duration of the call.
//! Only the bounded field index is allocated in Rust. No unwind may cross the ABI.
use buffa::encoding::{Tag, WireType, decode_varint};
use std::{
    mem,
    panic::{AssertUnwindSafe, catch_unwind},
    slice,
};
#[allow(dead_code, unused_imports)]
mod seam {
    include!("generated/seam.rs");
}

mod typed;

const MALFORMED: i32 = 1;
const BUDGET: i32 = 2;
const ALLOCATION: i32 = 3;
const PANIC: i32 = 4;
use seam::Field;
const FIELD_BYTES: usize = mem::size_of::<Field>();

struct Parser<'a> {
    input: &'a [u8],
    remaining: &'a [u8],
    fields: Vec<Field>,
    cells: u64,
    depth: u32,
    native_bytes: u64,
}
impl<'a> Parser<'a> {
    fn charge(&mut self, amount: u64) -> Result<(), i32> {
        self.cells = self.cells.checked_sub(amount).ok_or(BUDGET)?;
        Ok(())
    }
    fn push(&mut self, field: Field) -> Result<usize, i32> {
        self.charge(4)?;
        let index = self.fields.len();
        if index == self.fields.capacity() {
            let max = (self.native_bytes / FIELD_BYTES as u64) as usize;
            // A relocating allocation may keep the old buffer alive until the new one exists.
            let available = max.saturating_sub(self.fields.capacity());
            let capacity = (index.saturating_mul(2)).max(4).min(available);
            if capacity <= index {
                return Err(BUDGET);
            }
            self.fields
                .try_reserve_exact(capacity - index)
                .map_err(|_| ALLOCATION)?;
        }
        self.fields.push(field);
        Ok(index)
    }
    fn take(&mut self, length: usize) -> Result<&'a [u8], i32> {
        if length > self.remaining.len() {
            return Err(MALFORMED);
        }
        let (head, tail) = self.remaining.split_at(length);
        self.remaining = tail;
        Ok(head)
    }
    fn group(&mut self, number: u32, depth: u32) -> Result<(), i32> {
        if depth > self.depth {
            return Err(BUDGET);
        }
        while !self.remaining.is_empty() {
            let tag = Tag::decode(&mut self.remaining).map_err(|_| MALFORMED)?;
            if tag.wire_type() == WireType::EndGroup {
                return if tag.field_number() == number && number != 0 {
                    Ok(())
                } else {
                    Err(MALFORMED)
                };
            }
            let index = self.push(Field {
                number: tag.field_number(),
                wire: tag.wire_type() as u32,
                value: 0,
                length: 0,
                end: 0,
            })?;
            match tag.wire_type() {
                WireType::Varint => {
                    self.fields[index].value =
                        decode_varint(&mut self.remaining).map_err(|_| MALFORMED)?
                }
                WireType::Fixed64 => {
                    self.fields[index].value = u64::from_le_bytes(self.take(8)?.try_into().unwrap())
                }
                WireType::Fixed32 => {
                    self.fields[index].value =
                        u32::from_le_bytes(self.take(4)?.try_into().unwrap()) as u64
                }
                WireType::LengthDelimited => {
                    let length = decode_varint(&mut self.remaining).map_err(|_| MALFORMED)?;
                    if length > self.remaining.len() as u64 {
                        return Err(MALFORMED);
                    }
                    self.charge(length)?;
                    self.fields[index].value = (self.input.len() - self.remaining.len()) as u64;
                    self.fields[index].length = length;
                    // Borrow; never allocate/copy the payload.
                    let _borrow = self.take(length as usize)?;
                }
                WireType::StartGroup => self.group(tag.field_number(), depth + 1)?,
                _ => return Err(MALFORMED),
            }
            self.fields[index].end = self.fields.len() as u64;
        }
        if number != 0 { Err(MALFORMED) } else { Ok(()) }
    }
}
fn parse(input: &[u8], cells: u64, depth: u32, native_bytes: u64) -> Result<Vec<Field>, i32> {
    if depth == 0 || depth > 128 || native_bytes > i32::MAX as u64 {
        return Err(BUDGET);
    }
    let mut parser = Parser {
        input,
        remaining: input,
        fields: Vec::new(),
        cells,
        depth,
        native_bytes,
    };
    parser.group(0, 1)?;
    Ok(parser.fields)
}

#[unsafe(no_mangle)]
pub extern "C" fn td_buffa_fingerprint() -> *mut u8 {
    seam::ABI_FINGERPRINT.as_ptr().cast_mut()
}

/// # Safety
/// data points to length immutable, live bytes. result points to a writable aligned
/// seam::Result. Both regions remain alive and disjoint for this call. No callback.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn td_buffa_decode(
    data: *const u8,
    length: u64,
    max_cells: u64,
    max_depth: u32,
    max_native_bytes: u64,
    result: *mut std::ffi::c_void,
) -> i32 {
    if result.is_null() {
        return MALFORMED;
    }
    let result = result.cast::<seam::Result>();
    unsafe {
        result.write(seam::Result {
            data: std::ptr::null_mut(),
            count: 0,
            capacity: 0,
        });
    }
    if data.is_null() || length > i32::MAX as u64 {
        return MALFORMED;
    }
    match catch_unwind(AssertUnwindSafe(|| {
        let input = unsafe { slice::from_raw_parts(data, length as usize) };
        parse(input, max_cells, max_depth, max_native_bytes)
    })) {
        Ok(Ok(mut fields)) => {
            unsafe {
                result.write(seam::Result {
                    data: fields.as_mut_ptr().cast(),
                    count: fields.len() as u64,
                    capacity: fields.capacity() as u64,
                });
            }
            mem::forget(fields);
            0
        }
        Ok(Err(code)) => code,
        Err(_) => PANIC,
    }
}

/// # Safety
/// Input/schema are live immutable regions; payload/result are disjoint writable regions.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn td_buffa_typed(
    schema: *const u8,
    schema_length: u64,
    data: *const u8,
    length: u64,
    format: u32,
    payload: *mut u8,
    payload_length: u64,
    max_cells: u64,
    max_string_bytes: u64,
    max_depth: u32,
    max_native_bytes: u64,
    result: *mut std::ffi::c_void,
) -> i32 {
    if result.is_null() {
        return MALFORMED;
    }
    let result = result.cast::<seam::Result>();
    unsafe {
        result.write(seam::Result {
            data: std::ptr::null_mut(),
            count: 0,
            capacity: 0,
        });
    }
    if schema.is_null()
        || data.is_null()
        || payload.is_null()
        || schema_length > i32::MAX as u64
        || length > i32::MAX as u64
        || payload_length > i32::MAX as u64
    {
        return MALFORMED;
    }
    match catch_unwind(AssertUnwindSafe(|| unsafe {
        typed::decode(
            slice::from_raw_parts(schema, schema_length as usize),
            slice::from_raw_parts(data, length as usize),
            format,
            slice::from_raw_parts_mut(payload, payload_length as usize),
            max_cells,
            max_string_bytes,
            max_depth,
            max_native_bytes,
        )
    })) {
        Ok(Ok(mut fields)) => {
            unsafe {
                result.write(seam::Result {
                    data: fields.as_mut_ptr().cast(),
                    count: fields.len() as u64,
                    capacity: fields.capacity() as u64,
                });
            }
            mem::forget(fields);
            0
        }
        Ok(Err(code)) => code,
        Err(_) => PANIC,
    }
}

/// # Safety
/// result is a live, aligned result of td_buffa_decode, released exactly once.
/// Its fields must not have been modified. Zero/error results may also be released.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn td_buffa_release(result: *mut std::ffi::c_void) {
    if result.is_null() {
        return;
    }
    let result = unsafe { &mut *result.cast::<seam::Result>() };
    if result.capacity != 0 {
        unsafe {
            drop(Vec::from_raw_parts(
                result.data.cast::<Field>(),
                result.count as usize,
                result.capacity as usize,
            ));
        }
    }
    result.data = std::ptr::null_mut();
    result.count = 0;
    result.capacity = 0;
}
seam::assert_implementations!(seam);

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn borrowed_payload_and_unsigned_values() {
        let input = [8, 150, 1, 18, 3, 1, 2, 3, 29, 255, 255, 255, 255];
        let fields = parse(&input, 100, 8, 1024).unwrap();
        assert_eq!(fields.len(), 3);
        assert_eq!(fields[0].value, 150);
        assert_eq!(
            &input[fields[1].value as usize..][..fields[1].length as usize],
            &[1, 2, 3]
        );
        assert_eq!(fields[2].value, u32::MAX as u64);
        assert!(fields.capacity() * 32 <= 1024);
    }
    #[test]
    fn groups_and_budgets() {
        let fields = parse(&[11, 16, 1, 12, 24, 2], 12, 2, 128).unwrap();
        assert_eq!(fields[0].end, 2);
        assert_eq!(fields[2].number, 3);
        assert_eq!(parse(&[11, 16, 1, 12], 100, 1, 1024).err().unwrap(), BUDGET);
        assert_eq!(parse(&[8, 1], 3, 1, 1024).err().unwrap(), BUDGET);
        assert_eq!(parse(&[8, 1], 4, 1, 31).err().unwrap(), BUDGET);
        assert_eq!(parse(&[18, 2, 0, 0], 5, 1, 1024).err().unwrap(), BUDGET);
    }
    #[test]
    fn malformed() {
        for input in [
            vec![0],
            vec![8, 128],
            vec![9, 0],
            vec![18, 3, 0],
            vec![11, 20],
            vec![11],
            vec![12],
            vec![8, 255, 255, 255, 255, 255, 255, 255, 255, 255, 2],
        ] {
            assert_eq!(parse(&input, 100, 8, 1024).err().unwrap(), MALFORMED);
        }
    }
    #[test]
    fn ffi_result_ownership() {
        let mut out = seam::Result {
            data: std::ptr::null_mut(),
            count: 0,
            capacity: 0,
        };
        let p = (&mut out as *mut seam::Result).cast();
        unsafe {
            assert_eq!(td_buffa_decode([8, 42].as_ptr(), 2, 4, 1, 32, p), 0);
            assert_eq!(out.count, 1);
            assert_eq!((*out.data.cast::<Field>()).value, 42);
            td_buffa_release(p);
            assert_eq!(out.capacity, 0);
            td_buffa_release(p);
            assert_eq!(td_buffa_decode([0].as_ptr(), 1, 4, 1, 32, p), MALFORMED);
            assert!(out.data.is_null());
        }
    }
}
