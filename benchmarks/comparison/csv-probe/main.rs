//! Native reference probe (benchmark only): the Rust `csv` crate family on the same fixtures and
//! host as the Java cells. Prints one JSON line per measurement with raw samples (ns per byte).
//!
//! - `csv-core-scan`: `csv_core::Reader::read_record` (the allocation-free core state machine;
//!   it copies field bytes into an output buffer), records and fields.
//! - `csv-byte-record`: `csv::Reader::read_byte_record`, records and fields.
//! - `csv-string-record`: `csv::Reader::read_record` into a `StringRecord` (UTF-8 validated).
//! - `csv-typed`: `StringRecord` fields parsed with `str::parse` (i64, f64) and booleans, with the
//!   Java harness's typed checksum, so the result is cross-checked against the Java engines.
//!
//! usage: csv_probe_rs <dataset> <fixture path>
use std::hint::black_box;
use std::time::{Duration, Instant};

fn measure(bytes: usize, expected: u64, mut run: impl FnMut() -> u64) -> Vec<f64> {
    let warm = Instant::now() + Duration::from_secs(1);
    let mut i = 0;
    while i < 3 || Instant::now() < warm {
        assert_eq!(run(), expected, "checksum");
        i += 1;
    }
    (0..5)
        .map(|_| {
            let start = Instant::now();
            let mut ops = 0u64;
            loop {
                assert_eq!(black_box(run()), expected);
                ops += 1;
                if start.elapsed() >= Duration::from_millis(300) {
                    break;
                }
            }
            start.elapsed().as_nanos() as f64 / (ops as f64 * bytes as f64)
        })
        .collect()
}

fn core_scan(data: &[u8]) -> u64 {
    let mut reader = csv_core::Reader::new();
    let mut output = vec![0u8; 1 << 16];
    let mut ends = vec![0usize; 4096];
    let (mut input, mut records, mut fields) = (data, 0u64, 0u64);
    loop {
        let (result, nin, _, nend) = reader.read_record(input, &mut output, &mut ends);
        input = &input[nin..];
        match result {
            csv_core::ReadRecordResult::Record => {
                records += 1;
                fields += nend as u64;
            }
            csv_core::ReadRecordResult::End => break,
            csv_core::ReadRecordResult::InputEmpty => {}
            other => panic!("csv-core: {other:?}"),
        }
    }
    records * 1_000_003 + fields
}

fn byte_records(data: &[u8]) -> u64 {
    let mut reader = csv::ReaderBuilder::new().has_headers(false).from_reader(data);
    let mut record = csv::ByteRecord::new();
    let (mut records, mut fields) = (0u64, 0u64);
    while reader.read_byte_record(&mut record).unwrap() {
        records += 1;
        fields += record.len() as u64;
    }
    records * 1_000_003 + fields
}

fn string_records(data: &[u8]) -> u64 {
    let mut reader = csv::ReaderBuilder::new().has_headers(true).from_reader(data);
    let mut record = csv::StringRecord::new();
    let mut bytes = 0u64;
    while reader.read_record(&mut record).unwrap() {
        for field in record.iter() {
            bytes += field.len() as u64;
        }
    }
    bytes
}

/// Column kinds as the Java harness's CsvFixture.kinds: L INT64, D FLOAT64, B BOOLEAN, S skip.
fn kinds(dataset: &str) -> Option<&'static [u8]> {
    match dataset {
        "numeric" => Some(b"LLLDDLBD"),
        "mixed" => Some(b"LSSSSLDDBSSSDSSLDDLDBSLS"),
        _ => None,
    }
}

fn typed(data: &[u8], kinds: &[u8]) -> u64 {
    let mut reader = csv::ReaderBuilder::new().has_headers(true).from_reader(data);
    let mut record = csv::StringRecord::new();
    let (mut longs, mut doubles, mut trues) = (0i64, 0f64, 0i64);
    while reader.read_record(&mut record).unwrap() {
        for (column, field) in record.iter().enumerate() {
            match kinds[column] {
                b'L' => longs = longs.wrapping_add(field.parse::<i64>().unwrap()),
                b'D' => doubles += field.parse::<f64>().unwrap(),
                b'B' => {
                    if field.eq_ignore_ascii_case("true") {
                        trues += 1
                    } else {
                        assert!(field.eq_ignore_ascii_case("false"))
                    }
                }
                _ => {}
            }
        }
    }
    longs.wrapping_mul(31).wrapping_add(doubles.to_bits() as i64).wrapping_add(trues) as u64
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    assert_eq!(args.len(), 3, "usage: csv_probe_rs <dataset> <fixture>");
    let dataset = &args[1];
    let data = std::fs::read(&args[2]).expect("fixture");
    let emit = |workload: &str, engine: &str, checksum: u64, ns: Vec<f64>| {
        println!(
            "{{\"workload\":\"{workload}\",\"engine\":\"{engine}\",\"kernel\":\"\",\"dataset\":\"{dataset}\",\"bytes\":{},\"stage\":\"ingest\",\"runtime\":\"native\",\"checksum\":{checksum},\"ns_per_byte\":{ns:?}}}",
            data.len()
        );
    };
    let expected = core_scan(&data);
    emit("scan", "csv-core-scan", expected, measure(data.len(), expected, || core_scan(&data)));
    let expected = byte_records(&data);
    emit("scan", "csv-byte-record", expected, measure(data.len(), expected, || byte_records(&data)));
    let expected = string_records(&data);
    emit("strings", "csv-string-record", expected, measure(data.len(), expected, || string_records(&data)));
    if let Some(kinds) = kinds(dataset) {
        let expected = typed(&data, kinds);
        emit("typed", "csv-typed", expected, measure(data.len(), expected, || typed(&data, kinds)));
    }
}
