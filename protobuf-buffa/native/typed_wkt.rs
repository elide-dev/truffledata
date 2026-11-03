//! Schema-selected WKT codecs. No calendar objects, descriptor lookup, or intermediate JSON tree.
use super::*;

impl<'a, 'b> Decoder<'a, 'b> {
    pub(super) fn mapping(&self, ty: u32) -> Result<u32> {
        Ok((self.schema.word(self.schema.ty(ty)? + 4)? >> 1) & 127)
    }
    pub(super) fn value_message(&self, f: Contract<'a>) -> Result<bool> {
        Ok(f.kind()? == 11 && self.mapping(f.word(12)?)? == 5)
    }
    fn numbered(&self, ty: u32, number: u32) -> Result<Contract<'a>> {
        self.schema.find(ty, number)?.ok_or(MALFORMED)
    }
    pub(super) fn json_special(
        &mut self,
        ty: u32,
        mapping: u32,
        json: &mut Json<'a>,
        depth: u32,
    ) -> Result<()> {
        match mapping {
            1 | 2 => {
                let text = self.json_string(json)?;
                let (seconds, nanos) = if mapping == 1 {
                    timestamp(&text)?
                } else {
                    duration(&text)?
                };
                self.push(self.numbered(ty, 1)?, seconds as u64, 0)?;
                self.push(self.numbered(ty, 2)?, nanos as u64, 0)?;
            }
            3 => {
                let text = self.json_string(json)?;
                let f = self.numbered(ty, 1)?;
                for path in text.split(',').filter(|p| !p.is_empty()) {
                    let start = self.used;
                    for byte in path.bytes() {
                        if byte.is_ascii_uppercase() {
                            *self.payload.get_mut(self.used).ok_or(BUDGET)? = b'_';
                            self.used += 1;
                        }
                        *self.payload.get_mut(self.used).ok_or(BUDGET)? = byte.to_ascii_lowercase();
                        self.used += 1;
                    }
                    let bytes = self.payload.get(start..self.used).ok_or(BUDGET)?;
                    let units: u64 = std::str::from_utf8(bytes)
                        .map_err(|_| MALFORMED)?
                        .chars()
                        .map(|c| c.len_utf16() as u64 * 2)
                        .sum();
                    self.strings = self.strings.checked_sub(units).ok_or(BUDGET)?;
                    self.push(f, start as u64, (self.used - start) as u64)?;
                }
            }
            4 => self.json_map(self.numbered(ty, 1)?, json, depth)?,
            5 => {
                json.space();
                let number = match json.text.as_bytes().get(json.at) {
                    Some(b'n') => 1,
                    Some(b'"') => 3,
                    Some(b't' | b'f') => 4,
                    Some(b'{') => 5,
                    Some(b'[') => 6,
                    Some(b'-' | b'0'..=b'9') => 2,
                    _ => return Err(MALFORMED),
                };
                self.json_value(self.numbered(ty, number)?, json, depth, false)?;
            }
            6 => {
                json.need(b'[')?;
                if !json.eat(b']') {
                    loop {
                        // Value null is a present sentinel, including in lists.
                        let f = self.numbered(ty, 1)?;
                        let i = self.push(f, 0, 0)?;
                        self.json_object(f.word(12)?, json, depth + 1, false)?;
                        self.fields[i].end = self.fields.len() as u64;
                        if json.eat(b']') {
                            break;
                        }
                        json.need(b',')?;
                    }
                }
            }
            7 => self.json_any(ty, json, depth)?,
            8 => {
                json.space();
                if json.text[json.at..].starts_with("null") {
                    return Err(MALFORMED);
                }
                self.json_value(self.numbered(ty, 1)?, json, depth, false)?;
            }
            _ => return Err(MALFORMED),
        }
        Ok(())
    }
    fn skip_json(&mut self, json: &mut Json<'a>, depth: u32) -> Result<()> {
        if depth > self.depth {
            return Err(BUDGET);
        }
        self.charge(1)?;
        json.space();
        match json.text.as_bytes().get(json.at) {
            Some(b'{') => {
                json.need(b'{')?;
                if !json.eat(b'}') {
                    loop {
                        self.json_string(json)?;
                        json.need(b':')?;
                        self.skip_json(json, depth + 1)?;
                        if json.eat(b'}') {
                            break;
                        }
                        json.need(b',')?;
                    }
                }
            }
            Some(b'[') => {
                json.need(b'[')?;
                if !json.eat(b']') {
                    loop {
                        self.skip_json(json, depth + 1)?;
                        if json.eat(b']') {
                            break;
                        }
                        json.need(b',')?;
                    }
                }
            }
            Some(b'"') => {
                self.json_string(json)?;
            }
            _ => {
                let literal = json.literal()?;
                if !["true", "false", "null"].contains(&literal) {
                    serde_json::from_str::<serde_json::Number>(literal).map_err(|_| MALFORMED)?;
                }
            }
        }
        Ok(())
    }
    fn json_any(&mut self, ty: u32, json: &mut Json<'a>, depth: u32) -> Result<()> {
        json.space();
        let start = json.at;
        json.need(b'{')?;
        if json.eat(b'}') {
            return Ok(());
        }
        let mut url = None;
        loop {
            let key = self.json_string(json)?;
            json.need(b':')?;
            if key == "@type" {
                if url.is_some() {
                    return Err(MALFORMED);
                }
                url = Some(self.json_string(json)?);
            } else {
                self.skip_json(json, depth + 1)?;
            }
            if json.eat(b'}') {
                break;
            }
            json.need(b',')?;
        }
        let url = url.ok_or(MALFORMED)?;
        let (_, name) = url.rsplit_once('/').ok_or(MALFORMED)?;
        let mut target = None;
        for index in 0..self.schema.word(4)? {
            let at = self.schema.ty(index)?;
            if self.schema.word(at + 4)? & 256 != 0 && self.schema.text(at + 8)? == name {
                target = Some(index);
                break;
            }
        }
        let target = target.ok_or(MALFORMED)?;
        self.store(self.numbered(ty, 1)?, url.as_bytes())?;
        let i = self.push(self.numbered(ty, 2)?, target as u64, 0)?;
        self.fields[i].wire = 101;
        json.at = start;
        if self.mapping(target)? == 0 {
            self.json_object(target, json, depth + 1, true)?;
        } else {
            json.need(b'{')?;
            let mut seen = false;
            loop {
                let key = self.json_string(json)?;
                json.need(b':')?;
                match key.as_ref() {
                    "@type" => {
                        self.json_string(json)?;
                    }
                    "value" if !seen => {
                        seen = true;
                        self.json_object(target, json, depth + 1, false)?;
                    }
                    _ => return Err(MALFORMED),
                }
                if json.eat(b'}') {
                    break;
                }
                json.need(b',')?;
            }
            if !seen {
                return Err(MALFORMED);
            }
        }
        self.fields[i].end = self.fields.len() as u64;
        Ok(())
    }
}
fn digits(text: &str) -> Result<i64> {
    if text.is_empty() || !text.bytes().all(|b| b.is_ascii_digit()) {
        return Err(MALFORMED);
    }
    text.parse().map_err(|_| MALFORMED)
}
fn nanos(text: &str) -> Result<i64> {
    if text.len() > 9 {
        return Err(MALFORMED);
    }
    Ok(digits(text)? * 10i64.pow(9 - text.len() as u32))
}
fn duration(text: &str) -> Result<(i64, i64)> {
    if text.len() > 128 {
        return Err(BUDGET);
    }
    let body = text.strip_suffix('s').ok_or(MALFORMED)?;
    let negative = body.starts_with('-');
    let body = body.strip_prefix('-').unwrap_or(body);
    let (whole, fraction) = body
        .split_once('.')
        .map_or((body, None), |(a, b)| (a, Some(b)));
    let seconds = digits(whole)?;
    if seconds > 315576000000 {
        return Err(MALFORMED);
    }
    let ns = fraction.map(nanos).transpose()?.unwrap_or(0);
    Ok(if negative {
        (-seconds, -ns)
    } else {
        (seconds, ns)
    })
}
fn timestamp(text: &str) -> Result<(i64, i64)> {
    if text.len() < 20 || text.len() > 64 || !text.is_ascii() {
        return Err(MALFORMED);
    }
    let b = text.as_bytes();
    if b[4] != b'-' || b[7] != b'-' || b[10] != b'T' || b[13] != b':' || b[16] != b':' {
        return Err(MALFORMED);
    }
    let year = digits(&text[..4])?;
    let month = digits(&text[5..7])?;
    let day = digits(&text[8..10])?;
    let hour = digits(&text[11..13])?;
    let minute = digits(&text[14..16])?;
    let second = digits(&text[17..19])?;
    let leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
    if year == 0 || !(1..=12).contains(&month) || hour > 23 || minute > 59 || second > 59 {
        return Err(MALFORMED);
    }
    let days = [
        31,
        if leap { 29 } else { 28 },
        31,
        30,
        31,
        30,
        31,
        31,
        30,
        31,
        30,
        31,
    ][month as usize - 1];
    if day == 0 || day > days {
        return Err(MALFORMED);
    }
    let mut at = 19;
    let mut ns = 0;
    if b[at] == b'.' {
        at += 1;
        let start = at;
        while at < b.len() && b[at].is_ascii_digit() {
            at += 1;
        }
        ns = nanos(&text[start..at])?;
    }
    let offset = match text.get(at..).ok_or(MALFORMED)? {
        "Z" => 0,
        zone if zone.len() == 6
            && matches!(zone.as_bytes()[0], b'+' | b'-')
            && zone.as_bytes()[3] == b':' =>
        {
            let h = digits(&zone[1..3])?;
            let m = digits(&zone[4..])?;
            if h > 23 || m > 59 {
                return Err(MALFORMED);
            }
            (h * 3600 + m * 60) * if zone.starts_with('-') { -1 } else { 1 }
        }
        _ => return Err(MALFORMED),
    };
    // Proleptic Gregorian civil date -> days since Unix epoch, with floor division for year 1.
    let y = year - if month <= 2 { 1 } else { 0 };
    let era = y.div_euclid(400);
    let yoe = y - era * 400;
    let mp = month + if month > 2 { -3 } else { 9 };
    let doy = (153 * mp + 2) / 5 + day - 1;
    let days = era * 146097 + yoe * 365 + yoe / 4 - yoe / 100 + doy - 719468;
    let seconds = days * 86400 + hour * 3600 + minute * 60 + second - offset;
    if !(-62135596800..=253402300799).contains(&seconds) {
        return Err(MALFORMED);
    }
    Ok((seconds, ns))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn temporal_boundaries() {
        assert_eq!(timestamp("1970-01-01T00:00:00Z"), Ok((0, 0)));
        assert_eq!(timestamp("1970-01-01T01:00:00+01:00"), Ok((0, 0)));
        assert_eq!(
            timestamp("0001-01-01T00:00:00.000000001Z"),
            Ok((-62135596800, 1))
        );
        assert_eq!(
            timestamp("9999-12-31T23:59:59.999999999Z"),
            Ok((253402300799, 999999999))
        );
        assert_eq!(duration("-0.000000001s"), Ok((0, -1)));
        assert_eq!(
            duration("315576000000.999999999s"),
            Ok((315576000000, 999999999))
        );
        for input in [
            "1900-02-29T00:00:00Z",
            "0000-01-01T00:00:00Z",
            "2000-01-01T24:00:00Z",
            "2000-01-01T00:00:60Z",
            "2000-01-01T00:00:00.1234567890Z",
            "2000-01-01T00:00:00+24:00",
            "9999-12-31T23:59:59-01:00",
        ] {
            assert!(timestamp(input).is_err(), "{input}");
        }
        for input in ["315576000001s", "1.1234567890s", "+1s", "1.s", "1", "--1s"] {
            assert!(duration(input).is_err(), "{input}");
        }
    }
}
