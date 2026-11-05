#!/usr/bin/env python3
"""Patch Sync for Reddit v23.06.30 so it works against today's Reddit API.

What it changes (see NOTES.md for how each target was found):
  * OAuth client ID, token-exchange auth header and redirect URI   (w8/b)
  * User-Agent sent with every API request                         (singleton/a)
  * ssl.reddit.com -> www.reddit.com (ssl.* now 301s, which turns the token POST into a GET)
  * Removes the "Sync is shutting down / join Sync for Lemmy" sheet on launch

Usage:
  python3 patch_sync.py sync.apk --client-id <id> [--redirect-uri ...] [--user-agent ...]
"""
import argparse
import base64
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent

# Defaults follow Boost for Reddit's REDDIT_API_SETUP.md (RedReader's registered values).
DEFAULT_REDIRECT_URI = "redreader://rr_oauth_redir"
DEFAULT_USER_AGENT = "org.quantumbadger.redreader/1.25.1"

KEYSTORE_PASS = "android"
KEY_ALIAS = "sync"


def run(*cmd):
    print("+", " ".join(str(c) for c in cmd), flush=True)
    subprocess.run([str(c) for c in cmd], check=True)


def find_build_tools(explicit):
    if explicit:
        return Path(explicit)
    for root in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"),
                 Path.home() / "Android/Sdk"):
        if root and (Path(root) / "build-tools").is_dir():
            versions = sorted((Path(root) / "build-tools").iterdir(),
                              key=lambda p: [int(x) if x.isdigit() else 0 for x in re.split(r"[.-]", p.name)])
            if versions:
                return versions[-1]
    sys.exit("Android build-tools not found; pass --build-tools")


def parse_client_id(raw):
    # Accept the whole "App ID: xxxx" line from Reddit's email, like Boost does.
    cid = raw.strip().split()[-1]
    if not re.fullmatch(r"[A-Za-z0-9_-]{10,40}", cid):
        sys.exit(f"That doesn't look like a Reddit client ID: {raw!r}")
    return cid


def smali_str(s):
    return s.replace("\\", "\\\\").replace('"', '\\"')


class Patcher:
    def __init__(self, root):
        self.root = root

    def file(self, rel):
        hits = list(self.root.glob(f"smali*/{rel}"))
        if len(hits) != 1:
            sys.exit(f"Expected exactly one {rel}, found {len(hits)}")
        return hits[0]

    def replace(self, rel, old, new, count=1):
        path = self.file(rel)
        text = path.read_text()
        found = text.count(old)
        if found != count:
            sys.exit(f"{rel}: expected {count} x {old!r}, found {found}")
        path.write_text(text.replace(old, new))

    def sub(self, rel, pattern, repl):
        path = self.file(rel)
        text, n = re.subn(pattern, repl, path.read_text(), flags=re.S)
        if n != 1:
            sys.exit(f"{rel}: pattern {pattern!r} matched {n} times")
        path.write_text(text)

    def replace_everywhere(self, old, new, min_count):
        total = 0
        for path in self.root.glob("smali*/**/*.smali"):
            text = path.read_text()
            if old in text:
                total += text.count(old)
                path.write_text(text.replace(old, new))
        if total < min_count:
            sys.exit(f"expected at least {min_count} x {old!r}, found {total}")
        return total


def apply_patches(root, client_id, redirect_uri, user_agent):
    p = Patcher(root)

    # --- OAuth (w8/b = OAuthLoginHelper) ---
    p.replace("w8/b.smali",
              '"https://ssl.reddit.com/api/v1/authorize.compact?client_id=Q43fSpTe8LckEg&response_type=code&state="',
              f'"https://www.reddit.com/api/v1/authorize.compact?client_id={client_id}&response_type=code&state="')
    p.replace("w8/b.smali", '"http://redditsync/auth"', f'"{smali_str(redirect_uri)}"')
    # c() builds "Basic base64(id:secret)" from an obfuscated string; installed apps have no secret.
    basic = "Basic " + base64.b64encode(f"{client_id}:".encode()).decode()
    p.sub("w8/b.smali",
          r"\.method public static c\(\)Ljava/lang/String;.*?\.end method",
          ".method public static c()Ljava/lang/String;\n"
          "    .locals 1\n\n"
          f'    const-string v0, "{basic}"\n\n'
          "    return-object v0\n"
          ".end method")

    # --- User-Agent (singleton/a.g()) ---
    p.replace("com/laurencedawson/reddit_sync/singleton/a.smali",
              '"android:com.laurencedawson.reddit_sync:vv23.06.30-13:39 (by /u/ljdawson)"',
              f'"{smali_str(user_agent)}"')

    # --- ssl.reddit.com now 301s; OkHttp turns a redirected POST into a GET and drops the body ---
    # 7 = 4 token requests (b9/*), login-page check (w8/a$a), 2 in y8/k0; authorize URL was done above.
    n = p.replace_everywhere('"https://ssl.reddit.com/', '"https://www.reddit.com/', min_count=7)
    print(f"  ssl.reddit.com -> www.reddit.com: {n} strings")

    # --- Don't show the shutdown / Sync for Lemmy sheet (MainActivity: g.f(aa.c.class, ...)) ---
    p.sub("com/laurencedawson/reddit_sync/ui/activities/MainActivity.smali",
          r"(const-class (\w+), Laa/c;\n(?:(?!\.end method).)*?)"
          r"\n    invoke-static \{\2, (\w+)\}, Ls9/g;->f\(Ljava/lang/Class;Landroidx/fragment/app/FragmentManager;\)V\n",
          r"\1\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("apk", type=Path, help="original Sync for Reddit APK (v23.06.30)")
    ap.add_argument("--client-id", required=True, help="Reddit OAuth client ID (or the whole 'App ID: ...' line)")
    ap.add_argument("--redirect-uri", default=DEFAULT_REDIRECT_URI)
    ap.add_argument("--user-agent", default=DEFAULT_USER_AGENT)
    ap.add_argument("-o", "--out", type=Path, default=HERE / "sync-patched.apk")
    ap.add_argument("--keystore", type=Path, default=HERE / "sync-patch.keystore",
                    help="created on first run; keep it, updates must be signed with the same key")
    ap.add_argument("--apktool", type=Path, default=HERE / "tools/apktool.jar")
    ap.add_argument("--build-tools", help="Android SDK build-tools dir (default: newest in ~/Android/Sdk)")
    ap.add_argument("--keep-work", action="store_true", help="keep the decoded smali tree for inspection")
    args = ap.parse_args()

    client_id = parse_client_id(args.client_id)
    bt = find_build_tools(args.build_tools)
    if not args.apktool.is_file():
        sys.exit(f"apktool not found at {args.apktool}")

    work = Path(tempfile.mkdtemp(prefix="syncpatch-"))
    try:
        decoded = work / "decoded"
        # -r: leave resources/manifest binary; we only touch code, and it avoids aapt round-trip issues.
        run("java", "-jar", args.apktool, "d", "-r", "-f", "-o", decoded, args.apk)
        apply_patches(decoded, client_id, args.redirect_uri, args.user_agent)

        unsigned = work / "unsigned.apk"
        aligned = work / "aligned.apk"
        run("java", "-jar", args.apktool, "b", "-o", unsigned, decoded)
        # -P 16: page-align uncompressed .so files (manifest has extractNativeLibs=false).
        run(bt / "zipalign", "-f", "-P", "16", "4", unsigned, aligned)

        if not args.keystore.exists():
            run("keytool", "-genkeypair", "-keystore", args.keystore, "-alias", KEY_ALIAS,
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
                "-storepass", KEYSTORE_PASS, "-keypass", KEYSTORE_PASS, "-dname", "CN=Sync patch")
        run(bt / "apksigner", "sign", "--ks", args.keystore, "--ks-key-alias", KEY_ALIAS,
            "--ks-pass", f"pass:{KEYSTORE_PASS}", "--key-pass", f"pass:{KEYSTORE_PASS}",
            "--out", args.out, aligned)
        # verify prints a wall of harmless "META-INF/... not protected by signature" warnings; keep only errors.
        check = subprocess.run([str(bt / "apksigner"), "verify", str(args.out)], capture_output=True, text=True)
        if check.returncode != 0:
            sys.exit(check.stdout + check.stderr)
        print(f"\nDone (signature verified): {args.out}")
    finally:
        if args.keep_work:
            print(f"work dir kept: {work}")
        else:
            shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
