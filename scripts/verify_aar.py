#!/usr/bin/env python3
"""Verify the gomobile-produced snishaper-core AAR against the bridge contract.

Checks (mirrors scripts/verify-aar.ps1, but runs anywhere Python 3 runs):
  1. AAR exists and can be opened;
  2. AndroidManifest.xml minSdkVersion == expected API (24);
  3. jni ABIs contain arm64-v8a, armeabi-v7a and x86_64;
  4. classes.jar application surface: App + Listener present, only gobind
     runtime extras (Mobile*, go/**, META-INF/**) allowed, no cert/ca classes;
  5. no certificate files (*.crt/*.pem/*.key/*.p12/*.jks) inside the AAR;
  6. 64-bit ABIs (arm64-v8a, x86_64) have PT_LOAD alignment >= 0x4000.

Exit codes: 0 = PASS, 1 = validation failure, 2 = AAR missing / bad argument.
"""

from __future__ import annotations

import argparse
import io
import re
import struct
import sys
import zipfile
from pathlib import Path

APP_CLASSES = {
    "com/snishaper/core/mobile/App.class",
    "com/snishaper/core/mobile/Listener.class",
}
ALLOWED_CLASS_PREFIXES = (
    "com/snishaper/core/mobile/Mobile",
    "go/",
    "META-INF/",
)
CERT_EXTENSIONS = (".crt", ".pem", ".key", ".p12", ".jks")
PROBLEMS: list[str] = []


def fail(message: str) -> None:
    PROBLEMS.append(message)


def read_min_sdk(manifest: bytes, expected_api: int) -> None:
    text = manifest.decode("utf-8", errors="replace")
    match = re.search(r'minSdkVersion\s*=\s*["\'](\d+)["\']', text)
    if not match:
        fail("AndroidManifest.xml has no minSdkVersion")
        return
    value = int(match.group(1))
    if value != expected_api:
        fail(f"minSdkVersion={value}, expected {expected_api}")
    else:
        print(f"[OK] minSdkVersion = {value}")


def check_abis(names: list[str], required: list[str]) -> None:
    found = sorted(
        {
            name.split("/")[1]
            for name in names
            if name.startswith("jni/") and name.endswith("/libgojni.so")
        }
    )
    print(f"[INFO] ABIs found: {', '.join(found) if found else '(none)'}")
    for abi in required:
        if abi in found:
            print(f"[OK] ABI present: {abi}")
        else:
            fail(f"missing ABI: {abi}")


def check_classes(classes_jar: bytes) -> None:
    with zipfile.ZipFile(io.BytesIO(classes_jar)) as jar:
        entries = [info.filename for info in jar.infolist() if not info.is_dir()]
    print(f"[INFO] classes.jar entries: {len(entries)}")
    for required in sorted(APP_CLASSES):
        if required in entries:
            print(f"[OK] class present: {required}")
        else:
            fail(f"missing class: {required}")
    for name in entries:
        if name in APP_CLASSES:
            continue
        if name.startswith(ALLOWED_CLASS_PREFIXES):
            continue
        fail(f"unexpected application class: {name}")
    lowered = [name.lower() for name in entries]
    cert_like = [name for name in lowered if "cert" in name]
    ca_segments = [
        name
        for name in lowered
        if "ca" in name.replace(".class", "").split("/")
    ]
    if cert_like or ca_segments:
        fail(f"CA/certificate-looking class names found: {cert_like + ca_segments}")
    else:
        print("[OK] no cert/CA class names")


def check_cert_files(names: list[str]) -> None:
    hits = [name for name in names if name.lower().endswith(CERT_EXTENSIONS)]
    if hits:
        fail(f"certificate files inside the AAR: {hits}")
    else:
        print("[OK] no certificate files inside the AAR")


def elf_max_load_align(data: bytes) -> int | None:
    if data[:4] != b"\x7fELF":
        return None
    elf_class = data[4]
    if elf_class == 2:  # ELF64
        e_phoff = struct.unpack_from("<Q", data, 32)[0]
        e_phentsize = struct.unpack_from("<H", data, 54)[0]
        e_phnum = struct.unpack_from("<H", data, 56)[0]
        align_offset = 48
    elif elf_class == 1:  # ELF32
        e_phoff = struct.unpack_from("<I", data, 28)[0]
        e_phentsize = struct.unpack_from("<H", data, 42)[0]
        e_phnum = struct.unpack_from("<H", data, 44)[0]
        align_offset = 28
    else:
        return None
    max_align = 0
    for index in range(e_phnum):
        base = e_phoff + index * e_phentsize
        if base + e_phentsize > len(data):
            break
        p_type = struct.unpack_from("<I", data, base)[0]
        if p_type != 1:  # PT_LOAD
            continue
        p_align = struct.unpack_from("<Q" if elf_class == 2 else "<I", data, base + align_offset)[0]
        max_align = max(max_align, p_align)
    return max_align


def check_page_alignment(aar: zipfile.ZipFile, names: list[str], abis: list[str]) -> None:
    for abi in abis:
        member = f"jni/{abi}/libgojni.so"
        if member not in names:
            continue
        data = aar.read(member)
        align = elf_max_load_align(data)
        if align is None:
            fail(f"{member}: not a valid ELF file")
            continue
        if align >= 0x4000:
            print(f"[OK] {member}: PT_LOAD Align = 0x{align:x} (16 KB)")
        else:
            fail(f"{member}: PT_LOAD Align = 0x{align:x} < 0x4000 (16 KB required)")


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify the snishaper-core AAR")
    parser.add_argument("--aar", default=str(Path("app/libs/snishaper-core.aar")))
    parser.add_argument("--api", type=int, default=24)
    parser.add_argument(
        "--abis",
        default="arm64-v8a,armeabi-v7a,x86_64",
        help="comma separated required ABIs",
    )
    args = parser.parse_args()

    aar_path = Path(args.aar)
    if not aar_path.is_file():
        print(f"FAIL  AAR not found: {aar_path}")
        return 2
    required_abis = [item.strip() for item in args.abis.split(",") if item.strip()]
    print(f"=== verify_aar: {aar_path} ({aar_path.stat().st_size:,} bytes) ===")

    with zipfile.ZipFile(aar_path) as aar:
        names = [info.filename for info in aar.infolist() if not info.is_dir()]
        manifest = aar.read("AndroidManifest.xml")
        classes_jar = aar.read("classes.jar")

        print("\n[1/5] AndroidManifest.xml minSdkVersion")
        read_min_sdk(manifest, args.api)

        print("\n[2/5] jni ABIs")
        check_abis(names, required_abis)

        print("\n[3/5] classes.jar surface (App + Listener, CA-free)")
        check_classes(classes_jar)

        print("\n[4/5] certificate files")
        check_cert_files(names)

        print("\n[5/5] 16 KB page alignment (64-bit ABIs)")
        check_page_alignment(aar, names, ["arm64-v8a", "x86_64"])

    print("\n=== summary ===")
    if PROBLEMS:
        print(f"FAIL  {len(PROBLEMS)} problem(s):")
        for problem in PROBLEMS:
            print(f"  - {problem}")
        return 1
    print("PASS  AAR matches the CA-free Android bridge contract")
    return 0


if __name__ == "__main__":
    sys.exit(main())
