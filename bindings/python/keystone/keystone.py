# Keystone Python bindings, by Nguyen Anh Quynnh <aquynh@gmail.com>
import sys
_python2 = sys.version_info[0] < 3
if _python2:
    range = xrange
from . import arm_const, arm64_const, mips_const, sparc_const, hexagon_const, ppc_const, systemz_const, x86_const
from .keystone_const import *

from ctypes import *
from platform import system
from os.path import split, join, dirname, exists
import distutils.sysconfig, sys


import inspect
if not hasattr(sys.modules[__name__], '__file__'):
    __file__ = inspect.getfile(inspect.currentframe())

_lib_path = split(__file__)[0]
_all_libs = ('keystone.dll', 'libkeystone.so', 'libkeystone.dylib')
# Windows DLL in dependency order
_all_windows_dlls = ("libwinpthread-1.dll", "libgcc_s_seh-1.dll", "libgcc_s_dw2-1.dll",  "libiconv-2.dll", "libintl-8.dll", "libglib-2.0-0.dll")
_found = False

for _lib in _all_libs:
    try:
        if _lib == 'keystone.dll':
            for dll in _all_windows_dlls:    # load all the rest DLLs first
                _lib_file = join(_lib_path, dll)
                if exists(_lib_file):
                    cdll.LoadLibrary(_lib_file)
        _lib_file = join(_lib_path, _lib)
        _ks = cdll.LoadLibrary(_lib_file)
        _found = True
        break
    except OSError:
        pass

if _found == False:
    # try loading from default paths
    for _lib in _all_libs:
        try:
            #print(">> 1: Trying to load %s" %_lib);
            _ks = cdll.LoadLibrary(_lib)
            _found = True
            break
        except OSError:
            pass

if _found == False:
    # last try: loading from python lib directory
    _lib_path = distutils.sysconfig.get_python_lib()
    for _lib in _all_libs:
        try:
            if _lib == 'keystone.dll':
                for dll in _all_windows_dlls:    # load all the rest DLLs first
                    _lib_file = join(_lib_path, 'keystone', dll)
                    if exists(_lib_file):
                        #print(">> 2: Trying to load %s" %_lib_file);
                        cdll.LoadLibrary(_lib_file)
            _lib_file = join(_lib_path, 'keystone', _lib)
            #print(">> 2: Trying to load %s" %_lib_file);
            _ks = cdll.LoadLibrary(_lib_file)
            _found = True
            break
        except OSError:
            pass

# Attempt Darwin specific load (10.11 specific),
# since LD_LIBRARY_PATH is not guaranteed to exist
if (_found == False) and (system() == 'Darwin'):
    _lib_path = '/usr/local/lib/'
    for _lib in _all_libs:
        try:
            _lib_file = join(_lib_path, _lib)
            #print(">> 3: Trying to load %s" %_lib_file);
            _ks = cdll.LoadLibrary(_lib_file)
            _found = True
            break
        except OSError:
            pass

if _found == False:
    raise ImportError("ERROR: fail to load the dynamic library.")

__version__ = "%s.%s" %(KS_API_MAJOR, KS_API_MINOR) 

# setup all the function prototype
def _setup_prototype(lib, fname, restype, *argtypes):
    getattr(lib, fname).restype = restype
    getattr(lib, fname).argtypes = argtypes

kserr = c_int
ks_engine = c_void_p
ks_hook_h = c_size_t

_setup_prototype(_ks, "ks_version", c_uint, POINTER(c_int), POINTER(c_int))
_setup_prototype(_ks, "ks_arch_supported", c_bool, c_int)
_setup_prototype(_ks, "ks_open", kserr, c_uint, c_uint, POINTER(ks_engine))
_setup_prototype(_ks, "ks_close", kserr, ks_engine)
_setup_prototype(_ks, "ks_strerror", c_char_p, kserr)
_setup_prototype(_ks, "ks_errno", kserr, ks_engine)
_setup_prototype(_ks, "ks_option", kserr, ks_engine, c_int, c_void_p)
# int ks_asm(ks_engine *ks, const char *string, uint64_t address, unsigned char **encoding, size_t *encoding_size, size_t *stat_count);
_setup_prototype(_ks, "ks_asm", c_int, ks_engine, c_char_p, c_uint64, POINTER(POINTER(c_ubyte)), POINTER(c_size_t), POINTER(c_size_t))
_setup_prototype(_ks, "ks_free", None, c_void_p)


# access to error code via @errno of KsError
class KsError(Exception):
    def __init__(self, errno):
        self.errno = errno

    def __str__(self):
        return _ks.ks_strerror(self.errno)


# return the core's version
def ks_version():
    major = c_int()
    minor = c_int()
    combined = _ks.ks_version(byref(major), byref(minor))
    return (major.value, minor.value, combined)


# return the binding's version
def version_bind():
    return (KS_API_MAJOR, KS_API_MINOR, (KS_API_MAJOR << 8) + KS_API_MINOR)


# check to see if this engine supports a particular arch
def ks_arch_supported(query):
    return _ks.ks_arch_supported(query)


class Ks(object):
    def __init__(self, arch, mode):
        # verify version compatibility with the core before doing anything
        (major, minor, _combined) = ks_version()
        if major != KS_API_MAJOR or minor != KS_API_MINOR:
            self._ksh = None
            # our binding version is different from the core's API version
            raise KsError(KS_ERR_VERSION)

        self._arch, self._mode = arch, mode
        self._ksh = c_void_p()
        status = _ks.ks_open(arch, mode, byref(self._ksh))
        if status != KS_ERR_OK:
            self._ksh = None
            raise KsError(status)

        if arch == KS_ARCH_X86:
            # Intel syntax is default for X86
            self._syntax = KS_OPT_SYNTAX_INTEL
        else:
            self._syntax = None


    # destructor to be called automatically when object is destroyed.
    def __del__(self):
        if self._ksh:
            try:
                status = _ks.ks_close(self._ksh)
                self._ksh = None
                if status != KS_ERR_OK:
                    raise KsError(status)
            except: # _ks might be pulled from under our feet
                pass


    # return assembly syntax.
    @property
    def syntax(self):
        return self._syntax


    # syntax setter: modify assembly syntax.
    @syntax.setter
    def syntax(self, style):
        status = _ks.ks_option(self._ksh, KS_OPT_SYNTAX, style)
        if status != KS_ERR_OK:
            raise KsError(status)
        # save syntax
        self._syntax = style


    # assemble a string of assembly
    def asm(self, string, addr = 0):
        encode = POINTER(c_ubyte)()
        encode_size = c_size_t()
        stat_count = c_size_t()
        status = _ks.ks_asm(self._ksh, string, addr, byref(encode), byref(encode_size), byref(stat_count))
        if (status != 0):
            errno = _ks.ks_errno(self._ksh)
            raise KsError(errno)
        else:
            if stat_count.value == 0:
                return (None, 0)
            else:
                encoding = []
                for i in range(encode_size.value):
                    encoding.append(encode[i])
                _ks.ks_free(encode)
                return (encoding, stat_count.value)


# print out debugging info
def debug():
    archs = { "arm": KS_ARCH_ARM, "arm64": KS_ARCH_ARM64, \
        "mips": KS_ARCH_MIPS, "sparc": KS_ARCH_SPARC, \
        "systemz": KS_ARCH_SYSTEMZ, "ppc": KS_ARCH_PPC, \
        "hexagon": KS_ARCH_HEXAGON, "x86": KS_ARCH_X86 }

    all_archs = ""
    keys = archs.keys()
    for k in sorted(keys):
        if ks_arch_supported(archs[k]):
            all_archs += "-%s" % k

    (major, minor, _combined) = ks_version()

    return "python-%s-c%u.%u-b%u.%u" % (all_archs, major, minor, KS_API_MAJOR, KS_API_MINOR)

