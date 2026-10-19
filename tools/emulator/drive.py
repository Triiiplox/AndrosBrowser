#!/usr/bin/env python3
"""Drives Fermata Auto on an Android emulator at the car's size (800x400 dp), as a finger and the
steering wheel would: IPTV with real video from tools/emulator/fake_panel.py, Back out of the
video and the split screen, the same movie again (the loading emblem must go), Menu, the page of
a channel, YouTube. Each step is written to <out>/report.txt as OK or FALHA, with screenshots,
Fermata.log and the logcat. Fails on a FALHA or a crash of the app.

    python3 drive.py <apk> <out dir>
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

APK = sys.argv[1]
OUT = sys.argv[2]
PANEL = "http://10.0.2.2:8080"
# The nav bar of the car shows icons only: its buttons are found by the id of their screen
NAV = {"IPTV": "xtream_fragment", "Youtube": "youtube_fragment", "Pastas": "folders_fragment"}
# The words of the app in Portuguese, and in English should the locale not take
WORDS = {"Adicionar conta": ["Add account"], "Ao vivo": ["Live"], "Filmes": ["Movies"],
         "Assistir": ["Play"], "Continuar": ["Resume"], "Configurações": ["Settings"],
         "Assistir ao vivo": ["Watch live"], "Assistir de novo": ["Watch again"],
         "Reiniciar": ["Restart"], "OK": ["Ok"]}
report = []
fails = 0
shots = 0
pkg = None


def adb(*args, check=False, timeout=60):
    r = subprocess.run(["adb"] + list(args), capture_output=True, text=True, timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError("adb %s: %s" % (" ".join(args), r.stderr))
    return r.stdout


def sh(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


def log(line):
    print(line, flush=True)
    report.append(line)


def check(ok, what):
    global fails
    log(("OK     " if ok else "FALHA  ") + what)
    if not ok:
        fails += 1
    return ok


def shot(name):
    global shots
    shots += 1
    path = os.path.join(OUT, "%02d-%s.png" % (shots, name))
    with open(path, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True,
                               timeout=60).stdout)
    # What uiautomator saw, to tell a wrong selector from a wrong screen
    sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1")
    with open(path[:-4] + ".xml", "w") as f:
        f.write(adb("exec-out", "cat", "/sdcard/ui.xml"))


# --- The screen, as uiautomator sees it ---

def nodes():
    for _ in range(3):
        sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1")
        xml = adb("exec-out", "cat", "/sdcard/ui.xml")
        try:
            return list(ET.fromstring(xml).iter("node"))
        except ET.ParseError:
            time.sleep(1)
    return []


def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(text=None, rid=None, desc=None, contains=False):
    texts = None if text is None else [text] + WORDS.get(text, [])
    for n in nodes():
        t = n.get("text") or ""
        if texts is not None and not any((w in t) if contains else (t == w) for w in texts):
            continue
        if rid is not None and not (n.get("resource-id") or "").endswith(":id/" + rid):
            continue
        if desc is not None and (n.get("content-desc") or "") != desc:
            continue
        return n
    return None


def wait_for(timeout=20, **kw):
    end = time.time() + timeout
    while time.time() < end:
        n = find(**kw)
        if n is not None:
            return n
        time.sleep(1)
    return None


def tap(n):
    x, y = center(n)
    sh("input tap %d %d" % (x, y))


def long_press(n):
    x, y = center(n)
    sh("input swipe %d %d %d %d 900" % (x, y, x, y))


def key(name):
    sh("input keyevent " + name)
    time.sleep(1.5)


def tap_nav(name, timeout=20):
    n = wait_for(timeout=timeout, rid=NAV[name])
    if n is not None:
        tap(n)
    return n


def tap_text(text, timeout=20, **kw):
    n = wait_for(timeout=timeout, text=text, **kw)
    if n is not None:
        tap(n)
    return n


# --- The player, as the media session tells ---

def playback_state():
    out = sh("dumpsys media_session")
    # The session of the app, then the first state of its PlaybackState: "state=3" on older
    # Androids, "state=PLAYING(3)" on newer ones
    i = out.find("package=" + pkg)
    if i < 0:
        i = out.find(pkg)
    if i < 0:
        return None
    m = re.search(r"state=PlaybackState \{state=(?:[A-Z_]+\()?(\d+)", out[i:])
    return int(m.group(1)) if m else None


def wait_state(states, timeout=25):
    end = time.time() + timeout
    st = None
    while time.time() < end:
        st = playback_state()
        if st in states:
            return st
        time.sleep(1)
    return st


PLAYING, PAUSED, STOPPED, NONE, ERROR = 3, 2, 1, 0, 7


def loading_shown():
    return find(rid="content_loading_progress") is not None


def video_shown():
    return find(rid="video_view") is not None


# --- Steps ---

def setup():
    global pkg
    adb("wait-for-device")
    adb("root")
    time.sleep(3)
    adb("wait-for-device")
    # The car's screen: 800x400 dp at density 1, in Portuguese
    sh("setprop persist.sys.locale pt-BR; setprop ctl.restart zygote")
    time.sleep(20)
    adb("wait-for-device")
    for _ in range(60):
        if sh("getprop sys.boot_completed").strip() == "1":
            break
        time.sleep(2)
    sh("wm size 800x400; wm density 160; settings put system screen_off_timeout 1800000;"
       " svc power stayon true; input keyevent 82")
    # No on-screen keyboard: it covers the fields of a dialog at 400 dp, and its autocorrect
    # changes what is typed. The text goes straight to the focused field.
    for ime in sh("ime list -s").split():
        sh("ime disable " + ime)
    log(adb("install", "-r", "-g", APK, timeout=300).strip().splitlines()[-1])
    pkg = [l.split(":")[1] for l in sh("pm list packages fermata").split() if "fermata" in l][0]
    log("Pacote: " + pkg)
    adb("logcat", "-c")


def start():
    comp = sh("cmd package resolve-activity --brief " + pkg).strip().splitlines()[-1]
    sh("am start -n %s --ez fermata.test.car true" % comp)
    time.sleep(10)
    shot("inicio")
    check(find(rid=NAV["IPTV"]) is not None, "abre no modo carro com a barra (IPTV na barra)")


def add_account():
    tap_nav("IPTV")
    time.sleep(3)
    add = wait_for(text="Adicionar conta", timeout=15)
    if not check(add is not None, "IPTV sem conta: botão Adicionar conta"):
        return False
    tap(add)
    time.sleep(2)
    edits = [n for n in nodes() if n.get("class") == "android.widget.EditText"]
    if not check(len(edits) >= 3, "formulário da conta (%d campos)" % len(edits)):
        return False
    # The on-screen keyboard covers the fields at 400 dp (in landscape it takes the whole
    # screen): the first field is tapped, the next ones reached with Tab, and the keyboard
    # closed with Back before OK
    tap(edits[0])
    time.sleep(1)
    for i, value in enumerate([PANEL, "u", "p"]):
        if i > 0:
            sh("input keyevent KEYCODE_TAB")
            time.sleep(0.5)
        sh("input text '%s'" % value)
        time.sleep(0.5)
    shot("conta")
    sh("input keyevent KEYCODE_BACK")
    time.sleep(1.5)
    edits = [n for n in nodes() if n.get("class") == "android.widget.EditText"]
    typed = [e.get("text") for e in edits[:3]]
    shot("conta-preenchida")
    check(typed[:2] == [PANEL, "u"], "campos preenchidos: %s" % typed[:2])
    tap_text("OK", timeout=5)
    ch = wait_for(text="Canal Teste 1", timeout=40)
    shot("iptv-inicio")
    return check(ch is not None or wait_for(text="Filme Teste", timeout=5) is not None,
                 "a conta entra e o catálogo aparece")


def live_channel():
    tap_text("Ao vivo")
    time.sleep(3)
    ch = wait_for(text="Canal Teste 1", timeout=15)
    if not check(ch is not None, "aba Ao vivo com os canais"):
        return
    shot("ao-vivo")
    tap(ch)
    st = wait_state({PLAYING}, timeout=30)
    time.sleep(4)
    shot("canal-tocando")
    check(st == PLAYING, "o canal toca (estado %s)" % st)
    check(not loading_shown(), "sem o emblema de carregamento por cima do vídeo")
    key("KEYCODE_BACK")
    shot("canal-voltar-1")
    check(playback_state() == PLAYING, "Voltar 1: o vídeo continua (tela dividida)")
    key("KEYCODE_BACK")
    time.sleep(2)
    shot("canal-voltar-2")
    st = playback_state()
    check(st in (STOPPED, NONE) and not video_shown(),
          "Voltar 2: sai do vídeo, o IPTV fica com a tela (estado %s, vídeo %s)" %
          (st, video_shown()))


def movie_twice():
    tap_text("Filmes")
    time.sleep(3)
    m = wait_for(text="Filme Teste", timeout=15)
    if not check(m is not None, "aba Filmes com o filme"):
        return
    tap(m)
    play = wait_for(text="Assistir", timeout=15)
    shot("filme-detalhes")
    if not check(play is not None, "detalhes do filme com Assistir"):
        return
    tap(play)
    st = wait_state({PLAYING}, timeout=30)
    time.sleep(8)
    shot("filme-tocando")
    check(st == PLAYING, "o filme toca (estado %s)" % st)
    key("KEYCODE_BACK")
    key("KEYCODE_BACK")
    time.sleep(2)
    check(playback_state() in (STOPPED, NONE), "Voltar duas vezes para o filme")
    # The same movie again: the item does not change, the emblem must still go away
    cont = wait_for(text="Continuar", timeout=10) or wait_for(text="Assistir", timeout=5)
    if not check(cont is not None, "detalhes de novo, com Continuar"):
        return
    tap(cont)
    st = wait_state({PLAYING}, timeout=30)
    time.sleep(6)
    shot("filme-de-novo")
    check(st == PLAYING, "o mesmo filme toca de novo (estado %s)" % st)
    check(not loading_shown(), "o emblema não fica por cima do mesmo filme tocado de novo")
    key("KEYCODE_BACK")
    key("KEYCODE_BACK")
    key("KEYCODE_BACK")


def menu():
    key("KEYCODE_MENU")
    time.sleep(1)
    shot("menu")
    shown = wait_for(text="Configurações", timeout=5) is not None
    check(shown, "Menu abre o menu")
    key("KEYCODE_BACK")
    check(find(text="Reiniciar") is None, "Voltar fecha o menu")


def channel_page():
    tap_text("Ao vivo")
    time.sleep(2)
    ch = wait_for(text="Canal Teste 1", timeout=10)
    if ch is None:
        return
    long_press(ch)
    time.sleep(3)
    shot("pagina-do-canal")
    check(wait_for(text="Assistir ao vivo", timeout=10) is not None,
          "toque longo no canal abre a página dele")
    check(find(text="Assistir de novo") is not None, "catch-up: Assistir de novo")
    key("KEYCODE_BACK")


def youtube():
    tap_nav("Youtube")
    time.sleep(15)
    shot("youtube")
    check(find(rid=NAV["IPTV"]) is not None, "YouTube abre e a barra continua")


def collect():
    with open(os.path.join(OUT, "logcat.txt"), "w") as f:
        f.write(adb("logcat", "-d", timeout=120))
    crash = adb("logcat", "-d", "-b", "crash", timeout=60)
    with open(os.path.join(OUT, "crash.txt"), "w") as f:
        f.write(crash)
    adb("pull", "/sdcard/Android/data/%s/files/Fermata.log" % pkg, OUT, timeout=60)
    check(pkg not in crash, "o app não caiu" + ("" if pkg not in crash else
                                                ": " + crash[crash.find(pkg) - 300:][:900]))


def main():
    os.makedirs(OUT, exist_ok=True)
    try:
        setup()
        start()
        if add_account():
            live_channel()
            movie_twice()
            menu()
            channel_page()
        youtube()
    except Exception as ex:
        check(False, "erro no roteiro: %r" % ex)
    finally:
        try:
            shot("fim")
            collect()
        except Exception as ex:
            check(False, "erro ao juntar os logs: %r" % ex)
        with open(os.path.join(OUT, "report.txt"), "w") as f:
            f.write("\n".join(report) + "\n\n%d falha(s)\n" % fails)
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
