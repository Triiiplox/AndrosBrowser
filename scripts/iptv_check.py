#!/usr/bin/env python3
"""Checks the streams of an M3U playlist and writes the ones that play.

A stream passes when its HLS playlist downloads, a variant (for a master playlist) downloads,
and the first media segment returns data. Other URLs pass when they return data.

Usage: iptv_check.py <playlist url or file> <output.m3u> <report.md>
"""
import concurrent.futures
import sys
import urllib.parse
import urllib.request

UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36'
TIMEOUT = 12


def get(url, limit=1 << 20):
    req = urllib.request.Request(url, headers={'User-Agent': UA})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
        return r.geturl(), r.read(limit)


def first_uri(base, text):
    for line in text.splitlines():
        line = line.strip()
        if line and not line.startswith('#'):
            return urllib.parse.urljoin(base, line)
    return None


def check(url):
    try:
        final, body = get(url)
        text = body.decode('utf-8', 'replace')
        if '#EXTM3U' not in text:
            return len(body) > 0, 'not HLS, %d bytes' % len(body)
        if '#EXT-X-STREAM-INF' in text:
            # The variant URI is on the line after the tag, not in the rest of the tag line
            tag_and_rest = text.split('#EXT-X-STREAM-INF', 1)[1].split('\n', 1)
            variant = first_uri(final, tag_and_rest[1]) if len(tag_and_rest) > 1 else None
            if not variant:
                return False, 'master without variants'
            final, body = get(variant)
            text = body.decode('utf-8', 'replace')
        seg = first_uri(final, text)
        if not seg:
            return False, 'playlist without segments'
        _, data = get(seg, 64 << 10)
        return len(data) > 0, 'ok'
    except Exception as e:  # any failure means the stream does not play from here
        return False, type(e).__name__ + ': ' + str(e)[:80]


def parse(text):
    entries, info = [], None
    for line in text.splitlines():
        line = line.strip()
        if line.startswith('#EXTINF'):
            info = [line]
        elif line.startswith('#') and info is not None:
            info.append(line)
        elif line and info is not None:
            entries.append((info, line))
            info = None
    return entries


def main():
    src, out, report = sys.argv[1:4]
    text = get(src, 64 << 20)[1].decode('utf-8', 'replace') if '://' in src else open(src).read()
    entries = parse(text)
    with concurrent.futures.ThreadPoolExecutor(48) as ex:
        results = list(ex.map(lambda e: check(e[1]), entries))
    ok = [(e, r) for e, r in zip(entries, results) if r[0]]
    with open(out, 'w') as f:
        f.write('#EXTM3U\n')
        for (info, url), _ in ok:
            f.write('\n'.join(info) + '\n' + url + '\n')
    with open(report, 'w') as f:
        f.write('## IPTV check: %d of %d streams play\n\nSource: %s\n\n' % (len(ok), len(entries), src))
        f.write('| | Channel | Result |\n|---|---|---|\n')
        for (info, url), (passed, why) in zip(entries, results):
            name = info[0].rsplit(',', 1)[-1].replace('|', '/')
            f.write('| %s | %s | %s |\n' % ('✅' if passed else '❌', name, why.replace('|', '/')))
    print('%d of %d streams play' % (len(ok), len(entries)))


if __name__ == '__main__':
    main()
