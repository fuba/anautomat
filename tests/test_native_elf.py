"""Regression tests for native ABI and linker-hardening release checks."""
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("native_elf", Path(__file__).parents[1] / "scripts/check-native.py")
native_elf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native_elf)


def fixture():
    data = bytearray(544)
    data[:7] = b"\x7fELF\x02\x01\x01"
    struct.pack_into("<HH", data, 16, 3, 183)
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<HH", data, 54, 56, 4)
    for index, values in enumerate(((1, 5, 0, 0, 0, 16, 16, 16384), (0x6474E551, 6, 0, 0, 0, 0, 0, 16), (0x6474E552, 4, 256, 256, 0, 32, 32, 1), (2, 6, 512, 512, 0, 32, 32, 8))):
        struct.pack_into("<IIQQQQQQ", data, 64 + index * 56, *values)
    struct.pack_into("<QQ", data, 512, 30, 8)
    return data


class NativeELFTests(unittest.TestCase):
    def test_expected_hardening_passes(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder, "lib.so")
            path.write_bytes(fixture())
            native_elf.check(path, "arm64-v8a")

    def test_missing_alignment_nx_relro_now_and_wrong_abi_fail(self):
        for offset, format, value in ((64 + 48, "<Q", 4096), (64 + 4, "<I", 7), (64 + 56 + 4, "<I", 7), (64 + 112, "<I", 0), (512 + 8, "<Q", 0), (18, "<H", 62), (32, "<Q", 999999)):
            with self.subTest(offset=offset), tempfile.TemporaryDirectory() as folder:
                data = fixture()
                struct.pack_into(format, data, offset, value)
                path = Path(folder, "lib.so")
                path.write_bytes(data)
                with self.assertRaises(ValueError):
                    native_elf.check(path, "arm64-v8a")
