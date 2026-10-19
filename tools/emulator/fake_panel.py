#!/usr/bin/env python3
"""A small Xtream Codes panel for the emulator tests: a few channels and movies whose streams are
real video files (made by ffmpeg in the CI job), served with byte ranges as the players ask.

    python3 fake_panel.py <port> <dir with live.ts and movie.mp4>

The emulator reaches it at http://10.0.2.2:<port>, user "u", password "p".
"""
import base64
import json
import os
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
MEDIA = sys.argv[2] if len(sys.argv) > 2 else "."
CHANNELS = ["Canal Teste 1", "Canal Teste 2", "Canal Teste 3"]
MOVIES = ["Filme Teste", "Outro Filme"]


def b64(s):
    return base64.b64encode(s.encode()).decode()


def api(action, q):
    now = int(time.time())
    if action is None:
        return {"user_info": {"username": "u", "auth": 1, "status": "Active",
                              "exp_date": str(now + 40 * 86400), "is_trial": "0",
                              "active_cons": "0", "max_connections": "1"},
                "server_info": {"timezone": "America/Sao_Paulo"}}
    if action == "get_live_categories":
        return [{"category_id": "1", "category_name": "Abertos"}]
    if action == "get_vod_categories":
        return [{"category_id": "10", "category_name": "Lançamentos"}]
    if action in ("get_series_categories", "get_series"):
        return []
    if action == "get_live_streams":
        return [{"num": i + 1, "name": n, "stream_type": "live", "stream_id": 101 + i,
                 "stream_icon": "", "epg_channel_id": "ch%d" % i, "added": str(now - i * 3600),
                 "category_id": "1", "tv_archive": 1 if i == 0 else 0, "tv_archive_duration": "2"}
                for i, n in enumerate(CHANNELS)]
    if action == "get_vod_streams":
        return [{"num": i + 1, "name": n, "stream_id": 201 + i, "stream_icon": "",
                 "rating": "7.5", "added": str(now - i * 86400), "category_id": "10",
                 "container_extension": "mp4"} for i, n in enumerate(MOVIES)]
    if action == "get_vod_info":
        return {"info": {"plot": "Um filme de teste, com cores e um tom.", "genre": "Teste",
                         "releasedate": "2024-01-01", "duration_secs": 120,
                         "duration": "00:02:00", "rating": "7.5"},
                "movie_data": {"stream_id": int(q.get("vod_id", ["201"])[0]),
                               "container_extension": "mp4"}}
    if action in ("get_short_epg", "get_simple_data_table"):
        first = now - (now % 3600) - (24 * 3600 if action == "get_simple_data_table" else 0)
        listings = []
        for i in range(30 if action == "get_simple_data_table" else 3):
            start = first + i * 3600
            if action == "get_short_epg" and start + 3600 <= now:
                continue
            listings.append({"title": b64("Programa %d" % (i + 1)), "description": b64("Teste"),
                             "start": time.strftime("%Y-%m-%d %H:%M:%S",
                                                    time.gmtime(start - 3 * 3600)),
                             "start_timestamp": str(start), "stop_timestamp": str(start + 3600),
                             "has_archive": 1 if start + 3600 <= now else 0})
        return {"epg_listings": listings}
    return []


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("panel: " + (fmt % args) + "\n")

    def do_GET(self):
        u = urlparse(self.path)
        if u.path.endswith("player_api.php"):
            q = parse_qs(u.query)
            if q.get("username") != ["u"] or q.get("password") != ["p"]:
                return self.send_json({"user_info": {"auth": 0}})
            return self.send_json(api(q.get("action", [None])[0], q))
        if re.match(r"^/(live|timeshift)/", u.path):
            return self.send_file(os.path.join(MEDIA, "live.ts"), "video/mp2t")
        if re.match(r"^/movie/", u.path):
            return self.send_file(os.path.join(MEDIA, "movie.mp4"), "video/mp4")
        self.send_response(404)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def send_json(self, o):
        b = json.dumps(o).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    # The players seek with byte ranges: a movie must answer them to be seekable
    def send_file(self, path, mime):
        size = os.path.getsize(path)
        start, end = 0, size - 1
        m = re.match(r"bytes=(\d*)-(\d*)", self.headers.get("Range", ""))
        if m and (m.group(1) or m.group(2)):
            if m.group(1):
                start = int(m.group(1))
                if m.group(2):
                    end = min(int(m.group(2)), size - 1)
            else:
                start = size - int(m.group(2))
            self.send_response(206)
            self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, size))
        else:
            self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(end - start + 1))
        self.end_headers()
        with open(path, "rb") as f:
            f.seek(start)
            left = end - start + 1
            try:
                while left > 0:
                    chunk = f.read(min(left, 256 * 1024))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    left -= len(chunk)
            except (BrokenPipeError, ConnectionResetError):
                pass


if __name__ == "__main__":
    print("Fake Xtream panel on port %d, media in %s" % (PORT, MEDIA), flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
