"""Validate Android ABI, 16 KiB page alignment and ELF linker hardening."""
from pathlib import Path
import struct
import sys


def check(path, abi):
    data = Path(path).read_bytes()
    expected = {"arm64-v8a": (2, 183), "x86_64": (2, 62), "armeabi-v7a": (1, 40), "x86": (1, 3)}[abi]
    if len(data) < 64 or data[:4] != b"\x7fELF" or data[5] != 1 or data[4] != expected[0]:
        raise ValueError("Unexpected native ELF format")
    wide = data[4] == 2
    kind, machine = struct.unpack_from("<HH", data, 16)
    if kind != 3 or machine != expected[1]:
        raise ValueError("Native ELF ABI mismatch")
    offset = struct.unpack_from("<Q" if wide else "<I", data, 32 if wide else 28)[0]
    size, count = struct.unpack_from("<HH", data, 54 if wide else 42)
    if size != (56 if wide else 32) or count > 256 or offset + size * count > len(data):
        raise ValueError("Invalid ELF program headers")
    stack, relro, now, loads = False, False, False, 0
    for index in range(count):
        fields = struct.unpack_from("<IIQQQQQQ" if wide else "<IIIIIIII", data, offset + size * index)
        if wide:
            ptype, flags, file_offset, _, _, file_size, _, alignment = fields
        else:
            ptype, file_offset, _, _, file_size, _, flags, alignment = fields
        if file_offset + file_size > len(data):
            raise ValueError("ELF segment extends beyond file")
        if ptype == 1:
            loads += 1
            if alignment < 16384:
                raise ValueError("Native library lacks 16 KiB page alignment")
            if flags & 3 == 3:
                raise ValueError("Writable executable ELF segment")
        elif ptype == 0x6474E551:
            if flags & 1:
                raise ValueError("Executable native stack")
            stack = True
        elif ptype == 0x6474E552:
            relro = True
        elif ptype == 2:
            entry = 16 if wide else 8
            for position in range(file_offset, file_offset + file_size, entry):
                tag, value = struct.unpack_from("<QQ" if wide else "<II", data, position)
                if tag == 0:
                    break
                if tag == 22:
                    raise ValueError("Native ELF has text relocations")
                now |= tag == 24 or (tag == 30 and bool(value & 8)) or (tag == 0x6FFFFFFB and bool(value & 1))
    if not (loads and stack and relro and now):
        raise ValueError("Missing native stack/RELRO/immediate-binding hardening")
    print("PASS: native ABI, 16 KiB alignment, NX stack and full RELRO:", abi)


if __name__ == "__main__":
    check(sys.argv[1], sys.argv[2])
