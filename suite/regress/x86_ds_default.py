#!/usr/bin/python
# Nguyen Anh Quynh, 2016

# Fill in the information in the form below when you create a new regression

# Github issue: #9
# Author: Nguyen Anh Quynh

from keystone import *

import regress

class TestX86(regress.RegressTest):
    def runTest(self):
        # Initialize Keystone engine
        ks = Ks(KS_ARCH_X86, KS_MODE_32)
        # Assemble to get back insn encoding & statement count
        encoding, count = ks.asm(b"JMP DWORD PTR DS:[100]")
        # Assert the result
        self.assertEqual(encoding, [ 0xFF, 0x25, 0x64, 0x00, 0x00, 0x00 ])

if __name__ == '__main__':
    regress.main()
