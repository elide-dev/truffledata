//! Streaming schema-aware ProtoJSON. serde_json handles JSON string escaping, not a value tree.
use super::*;
#[path = "typed_wkt.rs"]
mod wkt;
struct Json<'a> {
    text: &'a str,
    at: usize,
}
impl<'a> Json<'a> {
    fn space(&mut self) {
        while self.at < self.text.len()
            && matches!(self.text.as_bytes()[self.at], b' ' | b'\n' | b'\r' | b'\t')
        {
            self.at += 1;
        }
    }
    fn eat(&mut self, c: u8) -> bool {
        self.space();
        if self.text.as_bytes().get(self.at) == Some(&c) {
            self.at += 1;
            true
        } else {
            false
        }
    }
    fn need(&mut self, c: u8) -> Result<()> {
        if self.eat(c) { Ok(()) } else { Err(MALFORMED) }
    }
    fn raw_string(&mut self) -> Result<&'a str> {
        self.space();
        let start = self.at;
        self.need(b'"')?;
        while self.at < self.text.len() {
            let c = self.text.as_bytes()[self.at];
            self.at += 1;
            if c == b'"' {
                return Ok(&self.text[start..self.at]);
            }
            if c == b'\\' {
                self.at += 1;
            }
        }
        Err(MALFORMED)
    }
    fn literal(&mut self) -> Result<&'a str> {
        self.space();
        let start = self.at;
        while self.at < self.text.len()
            && !matches!(
                self.text.as_bytes()[self.at],
                b',' | b']' | b'}' | b' ' | b'\t' | b'\r' | b'\n'
            )
        {
            self.at += 1;
        }
        if start == self.at {
            Err(MALFORMED)
        } else {
            Ok(&self.text[start..self.at])
        }
    }
}
impl<'a, 'b> Decoder<'a, 'b> {
    fn json_string(&self, json: &mut Json<'a>) -> Result<std::borrow::Cow<'a, str>> {
        let raw = json.raw_string()?;
        self.scratch(raw.len())?;
        let inner = &raw[1..raw.len() - 1];
        if inner.bytes().all(|b| b >= 0x20 && b != b'\\') {
            Ok(std::borrow::Cow::Borrowed(inner))
        } else {
            serde_json::from_str::<String>(raw)
                .map(std::borrow::Cow::Owned)
                .map_err(|_| MALFORMED)
        }
    }
    fn json_object(
        &mut self,
        ty: u32,
        json: &mut Json<'a>,
        depth: u32,
        skip_type: bool,
    ) -> Result<()> {
        if depth > self.depth {
            return Err(BUDGET);
        }
        self.charge(self.schema.word(self.schema.ty(ty)?)? as u64)?;
        let mapping = self.mapping(ty)?;
        if mapping != 0 {
            return self.json_special(ty, mapping, json, depth);
        }
        json.need(b'{')?;
        if json.eat(b'}') {
            return Ok(());
        }
        loop {
            let name = self.json_string(json)?;
            json.need(b':')?;
            if skip_type && name == "@type" {
                self.json_string(json)?;
                if json.eat(b'}') {
                    break;
                }
                json.need(b',')?;
                continue;
            }
            let f = self.schema.name(ty, &name, true)?;
            json.space();
            if json.text[json.at..].starts_with("null")
                && (f.repeated()? || !self.value_message(f)?)
            {
                self.json_null(f, json, false)?;
            } else if f.word(8)? & 2 != 0 {
                let marker = self.push(f, 0, 0)?;
                self.fields[marker].wire = 100;
                self.json_map(f, json, depth)?;
            } else if f.repeated()? {
                let marker = self.push(f, 0, 0)?;
                self.fields[marker].wire = 100;
                json.need(b'[')?;
                if !json.eat(b']') {
                    loop {
                        self.json_value(f, json, depth, true)?;
                        if json.eat(b']') {
                            break;
                        }
                        json.need(b',')?;
                    }
                }
            } else {
                self.json_value(f, json, depth, false)?;
            }
            if json.eat(b'}') {
                break;
            }
            json.need(b',')?;
        }
        Ok(())
    }
    fn json_null(&mut self, f: Contract<'a>, json: &mut Json<'a>, element: bool) -> Result<()> {
        if json.literal()? != "null" {
            return Err(MALFORMED);
        }
        let i = self.push(f, 0, element as u64)?;
        self.fields[i].wire = 0;
        Ok(())
    }
    fn json_value(
        &mut self,
        f: Contract<'a>,
        json: &mut Json<'a>,
        depth: u32,
        element: bool,
    ) -> Result<()> {
        json.space();
        if json.text[json.at..].starts_with("null") && !self.value_message(f)? {
            return self.json_null(f, json, element);
        }
        let kind = f.kind()?;
        if kind == 10 || kind == 11 {
            let i = self.push(f, 0, 0)?;
            self.json_object(f.word(12)?, json, depth + 1, false)?;
            self.fields[i].end = self.fields.len() as u64;
            return Ok(());
        }
        let quoted = json.text.as_bytes().get(json.at) == Some(&b'"');
        if kind == 9 || kind == 12 {
            if !quoted {
                return Err(MALFORMED);
            }
            let text = self.json_string(json)?;
            if kind == 9 {
                return self.store(f, text.as_bytes());
            }
            self.scratch(text.len().checked_mul(2).ok_or(BUDGET)?)?;
            let decoded = base64(&text)?;
            return self.store(f, &decoded);
        }
        let owned;
        let text = if quoted {
            owned = self.json_string(json)?;
            owned.as_ref()
        } else {
            json.literal()?
        };
        if kind == 8 {
            if quoted {
                return Err(MALFORMED);
            }
            let value = match text {
                "true" => 1,
                "false" => 0,
                _ => return Err(MALFORMED),
            };
            self.push(f, value, 0)?;
            return Ok(());
        }
        if !quoted {
            serde_json::from_str::<serde_json::Number>(text).map_err(|_| MALFORMED)?;
        }
        let value = json_scalar(f, text, quoted)?;
        if !f.enumeration(value as i32)? {
            return Err(MALFORMED);
        }
        self.push(f, value, 0)?;
        Ok(())
    }
    fn json_map(&mut self, f: Contract<'a>, json: &mut Json<'a>, depth: u32) -> Result<()> {
        if depth >= self.depth {
            return Err(BUDGET);
        }
        let ty = f.word(12)?;
        json.need(b'{')?;
        if json.eat(b'}') {
            return Ok(());
        }
        loop {
            self.charge(2)?;
            let key = self.json_string(json)?;
            let i = self.push(f, 0, 0)?;
            let k = self.schema.field(ty, 0)?;
            match k.kind()? {
                9 => self.store(k, key.as_bytes())?,
                8 => {
                    let v = match key.as_ref() {
                        "true" => 1,
                        "false" => 0,
                        _ => return Err(MALFORMED),
                    };
                    self.push(k, v, 0)?;
                }
                _ => {
                    if key.is_empty()
                        || !key
                            .strip_prefix('-')
                            .unwrap_or(&key)
                            .bytes()
                            .all(|b| b.is_ascii_digit())
                    {
                        return Err(MALFORMED);
                    }
                    let value = decimal_integer(k.kind()?, &key)?;
                    self.push(k, value, 0)?;
                }
            }
            json.need(b':')?;
            self.json_value(self.schema.field(ty, 1)?, json, depth + 1, true)?;
            self.fields[i].end = self.fields.len() as u64;
            if json.eat(b'}') {
                break;
            }
            json.need(b',')?;
        }
        Ok(())
    }
    pub(super) fn json(&mut self) -> Result<()> {
        let text = crate::unicode::validated(self.input).map_err(|_| MALFORMED)?;
        let mut json = Json { text, at: 0 };
        self.json_object(0, &mut json, 1, false)?;
        json.space();
        if json.at != text.len() {
            return Err(MALFORMED);
        }
        Ok(())
    }
}
fn json_scalar(f: Contract<'_>, text: &str, quoted: bool) -> Result<u64> {
    if text.len() > 128 {
        return Err(BUDGET);
    }
    let kind = f.kind()?;
    if kind == 1 || kind == 2 {
        let n = match text {
            "NaN" if quoted => f64::NAN,
            "Infinity" if quoted => f64::INFINITY,
            "-Infinity" if quoted => f64::NEG_INFINITY,
            _ => {
                let n = text.parse::<f64>().map_err(|_| MALFORMED)?;
                if !n.is_finite() {
                    return Err(MALFORMED);
                }
                n
            }
        };
        if kind == 2 {
            let single = n as f32;
            if n.is_finite() && !single.is_finite() {
                return Err(MALFORMED);
            }
            return Ok(single.to_bits() as u64);
        }
        return Ok(n.to_bits());
    }
    if kind == 14 && quoted {
        if let Some(n) = f.enum_name(text)? {
            return Ok(n as u64);
        }
    }
    decimal_integer(kind, text)
}
fn decimal_integer(kind: u32, text: &str) -> Result<u64> {
    if text.len() > 128 {
        return Err(BUDGET);
    }
    let negative = text.starts_with('-');
    let text = text.strip_prefix(['-', '+']).unwrap_or(text);
    let (mantissa, exponent) = if let Some(at) = text.find(['e', 'E']) {
        (
            &text[..at],
            text[at + 1..].parse::<i32>().map_err(|_| MALFORMED)?,
        )
    } else {
        (text, 0)
    };
    let mut value = 0u128;
    let mut fraction = 0i32;
    let mut dot = false;
    let mut digits = 0;
    for b in mantissa.bytes() {
        if b == b'.' && !dot {
            dot = true;
            continue;
        }
        if !b.is_ascii_digit() {
            return Err(MALFORMED);
        }
        value = value
            .checked_mul(10)
            .and_then(|v| v.checked_add((b - b'0') as u128))
            .ok_or(MALFORMED)?;
        if dot {
            fraction += 1;
        }
        digits += 1;
    }
    if digits == 0 {
        return Err(MALFORMED);
    }
    if value == 0 {
        return integer(kind, negative, 0);
    }
    let scale = (exponent as i64) - fraction as i64;
    if !(-38..=20).contains(&scale) {
        return Err(MALFORMED);
    }
    if scale > 0 {
        value = value
            .checked_mul(10u128.pow(scale as u32))
            .ok_or(MALFORMED)?;
    } else if scale < 0 {
        let divisor = 10u128.pow((-scale) as u32);
        if value % divisor != 0 {
            return Err(MALFORMED);
        }
        value /= divisor;
    }
    integer(kind, negative, u64::try_from(value).map_err(|_| MALFORMED)?)
}
fn base64(text: &str) -> Result<Vec<u8>> {
    let mut result = Vec::new();
    result
        .try_reserve_exact(text.len().saturating_mul(3) / 4)
        .map_err(|_| ALLOCATION)?;
    let mut bits = 0u32;
    let mut count = 0;
    let mut padding = 0;
    for b in text.bytes() {
        if b == b'=' {
            padding += 1;
            continue;
        }
        if padding != 0 {
            return Err(MALFORMED);
        }
        let n = match b {
            b'A'..=b'Z' => b - b'A',
            b'a'..=b'z' => b - b'a' + 26,
            b'0'..=b'9' => b - b'0' + 52,
            b'+' | b'-' => 62,
            b'/' | b'_' => 63,
            _ => return Err(MALFORMED),
        };
        bits = (bits << 6) | n as u32;
        count += 6;
        if count >= 8 {
            count -= 8;
            result.push((bits >> count) as u8);
        }
    }
    if count == 6 || padding > 2 || (padding != 0 && (text.len() % 4 != 0 || padding != count / 2))
    {
        return Err(MALFORMED);
    }
    Ok(result)
}
