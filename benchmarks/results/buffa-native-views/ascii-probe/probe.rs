use std::{hint::black_box, time::{Duration, Instant}};
#[inline(never)]
fn baseline(bytes: &[u8]) -> Option<usize> {
    std::str::from_utf8(bytes).ok().map(|s| s.encode_utf16().count())
}
#[inline(never)]
fn ascii(bytes: &[u8]) -> Option<usize> {
    if bytes.len() < 64 && bytes.is_ascii() { Some(bytes.len()) } else { baseline(bytes) }
}
fn main() {
    let args: Vec<_> = std::env::args().collect();
    let input = match args[2].as_str() {
        "ascii8" => vec![b'a'; 8], "ascii32" => vec![b'a'; 32],
        "ascii63" => vec![b'a'; 63], "unicode" => "user-123-λ".as_bytes().to_vec(),
        "supplementary" => "a😀中é".as_bytes().to_vec(),
        "malformed" => vec![b'a', 0xff], _ => panic!(),
    };
    assert_eq!(ascii(&input), baseline(&input));
    let f = if args[1] == "ascii" { ascii } else { baseline };
    let start = Instant::now();
    while start.elapsed() < Duration::from_millis(30) { black_box(f(black_box(&input))); }
    let start = Instant::now(); let mut n = 0u64;
    while start.elapsed() < Duration::from_millis(200) {
        for _ in 0..1024 { black_box(f(black_box(&input))); }
        n += 1024;
    }
    println!("{} {} {:.4}", args[1], args[2], start.elapsed().as_nanos() as f64 / n as f64);
}
