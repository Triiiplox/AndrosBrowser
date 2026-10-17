#!/usr/bin/env python3
"""Profiles a private M3U playlist (e.g. an Xtream Codes m3u_plus list) without exposing it.

The playlist URL comes from the IPTV_PRIVATE_URL environment variable. Stream URLs carry the
account's user name and password, so the report holds only counts, group names, titles and
attribute statistics, never a URL or a host. A few streams of each kind are probed one at a
time: providers often allow a single connection per account.

Usage: IPTV_PRIVATE_URL=... iptv_profile.py <report.md>
"""
import collections
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

UA = 'VLC/3.0.20 LibVLC/3.0.20'
# Panels may refuse some players or datacenter addresses: each try reports its status code only
AGENTS = [UA, 'IPTVSmartersPro', 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 '
          '(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36']
ATTR = re.compile(r'([\w-]+)="([^"]*)"')
EPISODE = re.compile(r'\s*[-|]?\s*S(\d{1,2})\s*E(\d{1,3})\b.*$', re.I)
PROBES = 4


def kind(url):
    path = urllib.parse.urlparse(url).path
    if '/movie/' in path:
        return 'movie'
    if '/series/' in path:
        return 'series'
    return 'live'


def parse(lines):
    entries, info = [], None
    for line in lines:
        line = line.strip()
        if line.startswith('#EXTINF'):
            info = line
        elif line and not line.startswith('#') and info:
            attrs = dict(ATTR.findall(info))
            title = info.rsplit(',', 1)[-1].strip() if ',' in info else ''
            entries.append((attrs, title, line))
            info = None
    return entries


def probe(url):
    t = time.time()
    try:
        req = urllib.request.Request(url, headers={'User-Agent': UA})
        with urllib.request.urlopen(req, timeout=15) as r:
            data = r.read(256 << 10)
            ctype = r.headers.get('Content-Type', '?')
        return 'ok, %d KB in %.1fs, %s' % (len(data) >> 10, time.time() - t, ctype)
    except Exception as e:  # report the failure type only, the message may hold the URL
        return 'failed: %s' % type(e).__name__


def fetch(url, timeout, w):
    global UA
    for ua in AGENTS:
        try:
            req = urllib.request.Request(url, headers={'User-Agent': ua})
            with urllib.request.urlopen(req, timeout=timeout) as r:
                data = r.read()
            UA = ua
            return data
        except urllib.error.HTTPError as e:
            w('- agent %s: HTTP %d %s\n' % (ua.split('/')[0][:20], e.code, e.reason))
        except Exception as e:
            w('- agent %s: %s\n' % (ua.split('/')[0][:20], type(e).__name__))
    return None


def account(url, w):
    # The Xtream API of the panel tells whether the account is active, without listing it
    u = urllib.parse.urlparse(url)
    q = urllib.parse.parse_qs(u.query)
    if not (q.get('username') and q.get('password')):
        return
    api = urllib.parse.urlunparse(u._replace(path='/player_api.php', query=urllib.parse.urlencode(
        {'username': q['username'][0], 'password': q['password'][0]})))
    w('\n## Account (Xtream API)\n\n')
    body = fetch(api, 30, w)
    if body is None:
        return
    try:
        info = json.loads(body).get('user_info', {})
    except Exception as e:
        w('- not JSON: %s, %d bytes\n' % (type(e).__name__, len(body)))
        return
    exp = info.get('exp_date')
    exp = time.strftime('%Y-%m-%d %H:%M UTC', time.gmtime(int(exp))) if str(exp).isdigit() else exp
    w('- status: %s, auth: %s, trial: %s, expires: %s\n' % (info.get('status'), info.get('auth'),
                                                           info.get('is_trial'), exp))
    w('- connections: %s active of %s, formats: %s\n' % (info.get('active_cons'),
                                                         info.get('max_connections'),
                                                         info.get('allowed_output_formats')))


def main():
    url = os.environ.get('IPTV_PRIVATE_URL', '').strip()
    out = open(sys.argv[1], 'w')
    if not url:
        out.write('IPTV_PRIVATE_URL secret not set, nothing to profile.\n')
        return
    account(url, out.write)
    out.write('\n## Download\n\n')
    t = time.time()
    raw = fetch(url, 180, out.write)
    if raw is None:
        out.write('Download failed with every agent.\n')
        return
    secs = time.time() - t
    text = raw.decode('utf-8', 'replace')
    entries = parse(text.splitlines())
    w = out.write
    w('## Playlist\n\n- size: %.1f MB, download %.0fs\n- lines: %d, entries: %d\n'
      % (len(raw) / 1e6, secs, text.count('\n'), len(entries)))

    by_kind = collections.defaultdict(list)
    for e in entries:
        by_kind[kind(e[2])].append(e)
    w('- by kind: %s\n' % ', '.join('%s %d' % (k, len(v)) for k, v in sorted(by_kind.items())))

    keys = collections.Counter(k for a, _, _ in entries for k in a)
    w('- attributes: %s\n' % ', '.join('%s %d' % kv for kv in keys.most_common()))
    exts = collections.Counter(os.path.splitext(urllib.parse.urlparse(e[2]).path)[1] or '(none)'
                               for e in entries)
    w('- stream extensions: %s\n' % ', '.join('%s %d' % kv for kv in exts.most_common(8)))

    for k, items in sorted(by_kind.items()):
        groups = collections.Counter(a.get('group-title', '(no group)') for a, _, _ in items)
        logos = sum(1 for a, _, _ in items if a.get('tvg-logo'))
        w('\n## %s: %d entries, %d groups, %d with logo\n\n' % (k, len(items), len(groups), logos))
        if k == 'series':
            shows = collections.Counter(EPISODE.sub('', t) for _, t, _ in items)
            w('- shows (title without SxxEyy): %d, episodes per show max %d\n'
              % (len(shows), max(shows.values())))
            w('- titles matching SxxEyy: %d of %d\n' % (sum(1 for _, t, _ in items if EPISODE.search(t)),
                                                        len(items)))
        w('- largest groups: %s\n' % '; '.join('%s (%d)' % kv for kv in groups.most_common(25)))
        w('- sample titles: %s\n' % '; '.join(t for _, t, _ in items[:: max(1, len(items) // 12)][:12]))
        w('\nProbes:\n\n')
        for a, title, u in items[:: max(1, len(items) // PROBES)][:PROBES]:
            w('- %s: %s\n' % (title[:60], probe(u)))
            time.sleep(2)


if __name__ == '__main__':
    main()
