#!/usr/bin/env python3
"""
Побайтовая замена путей Winlator на пути WinArs во всех бинарниках ядра.

Бинарники из rootfs.tzst / box64 / драйверов собраны с жёстким путём
    /data/data/com.winlator/files/rootfs/...   (interpreter, RUNPATH, glibc, ld.so.cache)
Заменяем на строку ТОЙ ЖЕ ДЛИНЫ (25 байт):
    com.winlator/files/rootfs  ->  com.winars.app/files/root
Длина не меняется, поэтому ELF/tar/ld.so.cache остаются валидными.

Использование:  patch_assets.py <assets_dir> [<jniLibs_dir>]
Нужен `zstd` в PATH. Уровень сжатия: env ZSTD_LEVEL (по умолчанию 19).
Идемпотентно: архивы без старого пути не трогаются.
"""
import io, os, subprocess, sys, tarfile, tempfile

OLD = b"com.winlator/files/rootfs"
NEW = b"com.winars.app/files/root"
assert len(OLD) == len(NEW) == 25
PAIRS = [(OLD, NEW), (OLD.decode().encode("utf-16le"), NEW.decode().encode("utf-16le"))]
RESIDUAL = [b"/data/data/com.winlator", "/data/data/com.winlator".encode("utf-16le")]
LEVEL = os.environ.get("ZSTD_LEVEL", "19")


def patch(data: bytes):
    n = 0
    for o, w in PAIRS:
        c = data.count(o)
        if c:
            n += c
            data = data.replace(o, w)
    return data, n


def residual(data: bytes) -> int:
    return sum(data.count(r) for r in RESIDUAL)


def open_tar_in(path):
    p = subprocess.Popen(["zstd", "-dc", path], stdout=subprocess.PIPE)
    return p, tarfile.open(fileobj=p.stdout, mode="r|")


def scan(path):
    """Сколько вхождений нужно заменить (без записи)."""
    total = 0
    p, tf = open_tar_in(path)
    for m in tf:
        if m.isreg():
            total += patch(tf.extractfile(m).read())[1]
        elif m.issym() or m.islnk():
            total += patch(m.linkname.encode("utf-8", "surrogateescape"))[1]
    tf.close(); p.stdout.close(); p.wait()
    return total


def rewrite(path):
    tmp = path + ".patched"
    sp, src = open_tar_in(path)
    with open(tmp, "wb") as out:
        cp = subprocess.Popen(["zstd", "-T0", "-" + LEVEL, "-q", "-c"], stdin=subprocess.PIPE, stdout=out)
        dst = tarfile.open(fileobj=cp.stdin, mode="w|", format=tarfile.GNU_FORMAT)
        done = left = 0
        for m in src:
            if m.isreg():
                data, n = patch(src.extractfile(m).read())
                left += residual(data)
                done += n
                m.size = len(data)
                dst.addfile(m, io.BytesIO(data))
            else:
                if m.issym() or m.islnk():
                    lb, n = patch(m.linkname.encode("utf-8", "surrogateescape"))
                    if n:
                        m.linkname = lb.decode("utf-8", "surrogateescape"); done += n
                dst.addfile(m)
        dst.close(); cp.stdin.close()
        if cp.wait() != 0:
            raise SystemExit("zstd compress failed: " + path)
    src.close(); sp.stdout.close(); sp.wait()
    os.replace(tmp, path)
    return done, left


def patch_plain(path):
    """Обычный файл (jniLibs/*.so и т.п.): замена на месте."""
    with open(path, "rb") as f:
        data = f.read()
    new, n = patch(data)
    if n:
        with open(path, "wb") as f:
            f.write(new)
    return n, residual(new)


def main():
    assets = sys.argv[1]
    libs = sys.argv[2] if len(sys.argv) > 2 else None
    bad = 0
    for dp, _, fn in os.walk(assets):
        for f in sorted(fn):
            if not f.endswith(".tzst"):
                continue
            path = os.path.join(dp, f)
            need = scan(path)
            if need == 0:
                print(f"[skip ] {os.path.relpath(path, assets)}: уже пропатчен/путей нет")
                continue
            done, left = rewrite(path)
            print(f"[patch] {os.path.relpath(path, assets)}: заменено {done}, остаток /data/data/com.winlator: {left}")
            bad += left
    if libs:
        for dp, _, fn in os.walk(libs):
            for f in fn:
                n, left = patch_plain(os.path.join(dp, f))
                if n or left:
                    print(f"[patch] jniLibs/{f}: заменено {n}, остаток: {left}")
                bad += left
    if bad:
        print(f"ОШИБКА: остались непропатченные пути ({bad})", file=sys.stderr)
        sys.exit(1)
    print("OK: пути заменены")


if __name__ == "__main__":
    main()
