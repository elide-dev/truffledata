//! Wire indexes and layout-compiled slabs (ABI 11, slab.rs): Buffa reads the
//! caller's slice; index payloads stay in that slice, slabs copy them into the caller's buffer.
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

mod slab;
mod typed;
mod unicode;
use unicode::td_buffa_utf8_to_utf16;

const MALFORMED: i32 = 1;
const BUDGET: i32 = 2;
const ALLOCATION: i32 = 3;
const PANIC: i32 = 4;
// Status 5 is reserved. The slab did not fit: `SlabResult.bytes` is exact.
const NEEDS_CAPACITY: i32 = slab::NEEDS_CAPACITY;
use seam::Field;
const FIELD_BYTES: usize = mem::size_of::<Field>();

// Both native memory and input regions are bounded by i32::MAX at the ABI. Keep
// lengths Java-indexable even for internal callers; subtree ends are bounded by
// the maximum index allocation (i32::MAX / FIELD_BYTES entries).
fn index_length(length: u64) -> Result<u32, i32> {
    i32::try_from(length).map(|n| n as u32).map_err(|_| BUDGET)
}

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
                    self.fields[index].length = index_length(length)?;
                    // Borrow; never allocate/copy the payload.
                    let _borrow = self.take(length as usize)?;
                }
                WireType::StartGroup => self.group(tag.field_number(), depth + 1)?,
                _ => return Err(MALFORMED),
            }
            self.fields[index].end = self.fields.len() as u32;
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
    unsafe {
        typed_impl(
            false,
            schema,
            schema_length,
            data,
            length,
            format,
            payload,
            payload_length,
            max_cells,
            max_string_bytes,
            max_depth,
            max_native_bytes,
            result,
        )
    }
}

/// # Safety
/// Same regions as typed; result is zero-initialized or a still-owned result from typed.
/// No view into the old index may remain live. Ownership is consumed even on failure.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn td_buffa_typed_reuse(
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
    unsafe {
        typed_impl(
            true,
            schema,
            schema_length,
            data,
            length,
            format,
            payload,
            payload_length,
            max_cells,
            max_string_bytes,
            max_depth,
            max_native_bytes,
            result,
        )
    }
}

unsafe fn typed_impl(
    reuse: bool,
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
    let fields = if reuse {
        let previous = unsafe { &*result };
        if previous.capacity == 0 {
            Vec::new()
        } else {
            // Result provenance and exclusive ownership are part of this unsafe ABI contract.
            unsafe {
                Vec::from_raw_parts(
                    previous.data.cast::<Field>(),
                    previous.count as usize,
                    previous.capacity as usize,
                )
            }
        }
    } else {
        Vec::new()
    };
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
        typed::decode_reusing(
            slice::from_raw_parts(schema, schema_length as usize),
            slice::from_raw_parts(data, length as usize),
            format,
            slice::from_raw_parts_mut(payload, payload_length as usize),
            max_cells,
            max_string_bytes,
            max_depth,
            max_native_bytes,
            fields,
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

/// ABI 11: compile a schema program (TDB3 tables and the TDL1 layout section) into slab
/// descriptors once. On success `result.data` is an owned handle (released by
/// td_buffa_release_layout) and `result.count` the number of types.
///
/// # Safety
/// schema is a live immutable region; result is an aligned, writable seam::Result.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn td_buffa_compile_layout(
    schema: *const u8,
    schema_length: u64,
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
    if schema.is_null() || schema_length > i32::MAX as u64 {
        return MALFORMED;
    }
    match catch_unwind(AssertUnwindSafe(|| {
        let compiled =
            slab::compile(unsafe { slice::from_raw_parts(schema, schema_length as usize) })?;
        let types = compiled.types.len();
        // A fallible single-element allocation; Box::new would abort on exhaustion.
        let mut owner = Vec::new();
        owner.try_reserve_exact(1).map_err(|_| ALLOCATION)?;
        owner.push(compiled);
        Ok::<_, i32>((Box::into_raw(owner.into_boxed_slice()), types))
    })) {
        Ok(Ok((handle, types))) => {
            unsafe {
                result.write(seam::Result {
                    data: handle.cast(),
                    count: types as u64,
                    capacity: 0,
                });
            }
            0
        }
        Ok(Err(code)) => code,
        Err(_) => PANIC,
    }
}

/// # Safety
/// compiled is null or a handle from td_buffa_compile_layout, released exactly once, with no
/// concurrent or later use.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn td_buffa_release_layout(compiled: *mut u8) {
    if !compiled.is_null() {
        drop(unsafe {
            Box::from_raw(std::ptr::slice_from_raw_parts_mut(
                compiled.cast::<slab::Compiled>(),
                1,
            ))
        });
    }
}

/// Shared body of both slab entry points; `small` forbids heap allocation.
#[allow(clippy::too_many_arguments)]
unsafe fn decode_slab_impl(
    small: bool,
    compiled: *const u8,
    data: *const u8,
    length: u64,
    flags: u32,
    projection: *const u8,
    out: *mut u8,
    capacity: u64,
    max_depth: u32,
    max_slab_bytes: u64,
    result: *mut std::ffi::c_void,
) -> i32 {
    if result.is_null() {
        return MALFORMED;
    }
    let result = result.cast::<seam::SlabResult>();
    unsafe {
        result.write(seam::SlabResult {
            bytes: 0,
            records: 0,
            handles: 0,
            flags: 0,
            error: 0,
            reserved: 0,
        });
    }
    if small && (length > slab::SMALL_LIMIT || capacity > slab::SMALL_LIMIT) {
        return BUDGET;
    }
    // A projection is an aligned TDP1 buffer matching this compiled layout.
    if compiled.is_null()
        || data.is_null()
        || length > i32::MAX as u64
        || capacity > isize::MAX as u64
        || (out.is_null() && capacity != 0)
    {
        return MALFORMED;
    }
    let mut error_at = 0usize;
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let compiled = unsafe { &*compiled.cast::<slab::Compiled>() }.checked()?;
        let projection = if projection.is_null() { None } else {
            Some(unsafe { slab::Projection::from_raw(compiled, projection) }?)
        };
        slab::decode_projected(
            compiled,
            unsafe { slice::from_raw_parts(data, length as usize) },
            flags,
            projection,
            out,
            capacity as usize,
            max_depth,
            max_slab_bytes,
            !small,
            &mut error_at,
        )
    }));
    let write = |o: slab::Outcome, error: usize| unsafe {
        result.write(seam::SlabResult {
            bytes: o.bytes,
            records: o.records,
            handles: o.handles,
            flags: o.flags,
            error: error as u32,
            reserved: 0,
        });
    };
    match outcome {
        Ok(Ok(o)) => {
            write(o, 0);
            if o.bytes > capacity || flags & slab::FLAG_SIZING_ONLY != 0 {
                NEEDS_CAPACITY
            } else {
                0
            }
        }
        Ok(Err(code)) => {
            write(slab::Outcome::default(), error_at);
            code
        }
        Err(_) => PANIC,
    }
}

/// ABI 11: decode one protobuf message into a slab (docs/SLAB.md) written to `out[..capacity]`.
/// Returns 0 with `result.bytes` the slab length, `NEEDS_CAPACITY` with the exact length when it
/// did not fit (or under `SIZING_ONLY`), or an error status with `result.error` the input offset
/// of the rejecting field (of the string payload for invalid UTF-8). Flags: exactly one of
/// `OWNING = 1` or `BORROWING = 8`, plus optional `LAX = 2` and `SIZING_ONLY = 4`.
/// A non-null projection is the matching immutable TDP1 mask; LAX must match its header.
/// Borrowing permits lazy children and external byte payloads; the caller retains immutable
/// input until every consumer of the resulting slab is closed or has copied those payloads.
///
/// # Safety
/// compiled is a live handle from td_buffa_compile_layout; data a live immutable region of
/// `length` bytes; out null (capacity 0) or `capacity` writable bytes disjoint from every other
/// region; result an aligned, writable seam::SlabResult. A non-null projection points to a
/// 4-byte-aligned readable 24-byte TDP1 header and its declared complete mask extent, disjoint
/// from output. Borrowed slabs retain input offsets, never raw input addresses. No callback.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn td_buffa_decode_slab(
    compiled: *const u8,
    data: *const u8,
    length: u64,
    flags: u32,
    projection: *const u8,
    out: *mut u8,
    capacity: u64,
    max_depth: u32,
    max_slab_bytes: u64,
    result: *mut std::ffi::c_void,
) -> i32 {
    unsafe {
        decode_slab_impl(
            false,
            compiled,
            data,
            length,
            flags,
            projection,
            out,
            capacity,
            max_depth,
            max_slab_bytes,
            result,
        )
    }
}

/// ABI 11 bounded leaf: td_buffa_decode_slab for `length` and `capacity` at most 64 KiB (else
/// `BUDGET` before any work). It performs no heap allocation and takes no lock: scratch is fixed
/// stack storage, and an input that overflows it returns `BUDGET` (the caller retries through
/// td_buffa_decode_slab). No panic escapes.
///
/// # Safety
/// As td_buffa_decode_slab.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn td_buffa_decode_slab_small(
    compiled: *const u8,
    data: *const u8,
    length: u64,
    flags: u32,
    projection: *const u8,
    out: *mut u8,
    capacity: u64,
    max_depth: u32,
    max_slab_bytes: u64,
    result: *mut std::ffi::c_void,
) -> i32 {
    unsafe {
        decode_slab_impl(
            true,
            compiled,
            data,
            length,
            flags,
            projection,
            out,
            capacity,
            max_depth,
            max_slab_bytes,
            result,
        )
    }
}
seam::assert_implementations!(seam);

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn compact_index_extents_are_java_indexable() {
        assert_eq!(FIELD_BYTES, 24);
        assert_eq!(index_length(0), Ok(0));
        assert_eq!(index_length(i32::MAX as u64), Ok(i32::MAX as u32));
        for length in [i32::MAX as u64 + 1, u32::MAX as u64, u64::MAX] {
            assert_eq!(index_length(length), Err(BUDGET));
        }
        // A precisely one-entry budget accepts one scalar, and never its successor.
        let bytes = FIELD_BYTES as u64;
        assert_eq!(parse(&[8, 1], 100, 8, bytes).unwrap().capacity(), 1);
        assert_eq!(parse(&[8, 1, 8, 2], 100, 8, bytes).err(), Some(BUDGET));
        assert!(i32::MAX as usize / FIELD_BYTES < i32::MAX as usize);
    }

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
        assert!(fields.capacity() * FIELD_BYTES <= 1024);
    }
    #[test]
    fn groups_and_budgets() {
        let fields = parse(&[11, 16, 1, 12, 24, 2], 12, 2, 128).unwrap();
        assert_eq!(fields[0].end, 2);
        assert_eq!(fields[2].number, 3);
        assert_eq!(parse(&[11, 16, 1, 12], 100, 1, 1024).err().unwrap(), BUDGET);
        assert_eq!(parse(&[8, 1], 3, 1, 1024).err().unwrap(), BUDGET);
        assert_eq!(
            parse(&[8, 1], 4, 1, FIELD_BYTES as u64 - 1).err().unwrap(),
            BUDGET
        );
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

#[cfg(test)]
mod allocation_probe {
    use std::alloc::{GlobalAlloc, Layout, System};
    use std::cell::Cell;
    thread_local! {
        static COUNT: Cell<Option<u64>> = const { Cell::new(None) };
        // Failure injection: Some(n) lets n more allocations succeed, then every one fails.
        static FAIL: Cell<Option<u64>> = const { Cell::new(None) };
        // Live bytes allocated (and not yet freed) while a failure-injection scope is active.
        static LIVE: Cell<Option<i64>> = const { Cell::new(None) };
    }
    struct Counting;
    #[global_allocator]
    static ALLOCATOR: Counting = Counting;
    /// Count one allocation; false when an injected failure must be returned instead.
    fn charge() -> bool {
        let _ = COUNT.try_with(|count| {
            if let Some(value) = count.get() {
                count.set(Some(value + 1));
            }
        });
        FAIL.try_with(|fail| match fail.get() {
            Some(0) => false,
            Some(n) => {
                fail.set(Some(n - 1));
                true
            }
            None => true,
        })
        .unwrap_or(true)
    }
    fn live(delta: i64) {
        let _ = LIVE.try_with(|live| {
            if let Some(value) = live.get() {
                live.set(Some(value + delta));
            }
        });
    }
    unsafe impl GlobalAlloc for Counting {
        unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
            if !charge() {
                return std::ptr::null_mut();
            }
            let result = unsafe { System.alloc(layout) };
            if !result.is_null() {
                live(layout.size() as i64);
            }
            result
        }
        unsafe fn alloc_zeroed(&self, layout: Layout) -> *mut u8 {
            if !charge() {
                return std::ptr::null_mut();
            }
            let result = unsafe { System.alloc_zeroed(layout) };
            if !result.is_null() {
                live(layout.size() as i64);
            }
            result
        }
        unsafe fn realloc(&self, ptr: *mut u8, layout: Layout, size: usize) -> *mut u8 {
            if !charge() {
                return std::ptr::null_mut();
            }
            let result = unsafe { System.realloc(ptr, layout, size) };
            if !result.is_null() {
                live(size as i64 - layout.size() as i64);
            }
            result
        }
        unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
            live(-(layout.size() as i64));
            unsafe { System.dealloc(ptr, layout) }
        }
    }
    /// Count allocations (including reallocations) made by `action` on this thread.
    pub fn allocations<T>(action: impl FnOnce() -> T) -> (T, u64) {
        struct Reset;
        impl Drop for Reset {
            fn drop(&mut self) {
                COUNT.with(|count| count.set(None));
            }
        }
        COUNT.with(|count| {
            assert!(count.get().is_none());
            count.set(Some(0));
        });
        let _reset = Reset;
        let result = action();
        (result, COUNT.with(|count| count.get().unwrap()))
    }
    /// Run `action` with allocation number `succeed` (zero-based) and every later one failing.
    /// Everything `action` allocates must be released before it returns: the scope checks that
    /// no live bytes remain, so failure paths cannot leak partially built results.
    pub fn failing<T>(succeed: u64, action: impl FnOnce() -> T) -> T {
        struct Reset;
        impl Drop for Reset {
            fn drop(&mut self) {
                FAIL.with(|fail| fail.set(None));
                LIVE.with(|live| live.set(None));
            }
        }
        FAIL.with(|fail| {
            assert!(fail.get().is_none());
            fail.set(Some(succeed));
        });
        LIVE.with(|live| live.set(Some(0)));
        let _reset = Reset;
        let result = action();
        FAIL.with(|fail| fail.set(None));
        assert_eq!(LIVE.with(|live| live.get().unwrap()), 0, "allocation leaked");
        result
    }
    pub fn without_allocation<T>(action: impl FnOnce() -> T) -> T {
        struct Reset;
        impl Drop for Reset {
            fn drop(&mut self) {
                COUNT.with(|count| count.set(None));
            }
        }
        COUNT.with(|count| {
            assert!(count.get().is_none());
            count.set(Some(0));
        });
        let _reset = Reset;
        let result = action();
        let allocations = COUNT.with(|count| count.get().unwrap());
        assert_eq!(allocations, 0, "Native scalar decoder allocated");
        result
    }
}
