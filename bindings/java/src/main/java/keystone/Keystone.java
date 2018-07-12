/*
 * Copyright (c) 2018 Jämes Ménétrey <james@menetrey.me>
 *
 * This file is part of the Keystone Java bindings which is released under MIT.
 * See file LICENSE in the Java bindings folder for full license details.
 */

package keystone;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import keystone.exceptions.AssembleFailedKeystoneException;
import keystone.exceptions.OpenFailedKeystoneException;
import keystone.jna.KeystoneTypeMapper;
import keystone.natives.CleanerContainer;
import keystone.natives.KeystoneCleanerContainer;
import keystone.natives.KeystoneNative;
import keystone.utilities.Version;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Keystone engine.
 */
public class Keystone implements AutoCloseable {
    /**
     * The native proxy for calling the C library.
     */
    private static final KeystoneNative ksNative = initializeKeystoneNativeProxy();

    /**
     * The pointer to the Keystone native resource.
     */
    private final Pointer ksEngine;

    /**
     * The cleaner container that frees up the native resource if this object is not properly closed and is
     * candidate for garbage collection.
     */
    private final CleanerContainer ksEngineCleaner;

    /**
     * Indicates whether the current instance of Keystone has been closed.
     */
    private final AtomicBoolean hasBeenClosed;

    /**
     * Initializes a new instance of the class {@link Keystone}.
     * <p>
     * Some architectures are not supported. Use the static method {@link #isArchitectureSupported} to determine
     * whether the engine support the architecture.
     *
     * @param architecture The architecture of the code generated by Keystone.
     * @param mode         The mode type.
     * @throws OpenFailedKeystoneException if the Keystone library cannot be opened properly.
     */
    public Keystone(KeystoneArchitecture architecture, KeystoneMode mode) {
        ksEngine = initializeKeystoneEngine(architecture, mode);
        ksEngineCleaner = initializeKeystoneCleanerContainer();
        hasBeenClosed = new AtomicBoolean(false);
    }

    /**
     * Initializes the JNA proxy to call original library functions.
     */
    private static KeystoneNative initializeKeystoneNativeProxy() {
        Map<String, Object> options = new HashMap<>();
        options.put(Library.OPTION_TYPE_MAPPER, new KeystoneTypeMapper());

        return Native.loadLibrary("keystone", KeystoneNative.class, options);
    }

    /**
     * Opens an handle of Keystone.
     *
     * @param architecture The architecture of the code generated by Keystone.
     * @param mode         The mode type.
     * @return The return value is a pointer to the handle of Keystone.
     */
    private Pointer initializeKeystoneEngine(KeystoneArchitecture architecture, KeystoneMode mode) {
        var pointerToEngine = new PointerByReference();
        var openResult = ksNative.ks_open(architecture, mode, pointerToEngine);

        if (openResult != KeystoneError.Ok) {
            throw new OpenFailedKeystoneException(ksNative, openResult);
        }

        return pointerToEngine.getValue();
    }

    /**
     * Initializes the cleaner object, that is going to close the native handle of Keystone if
     * the instance is garbage collected.
     *
     * @return The return value is a cleaner container.
     */
    private CleanerContainer initializeKeystoneCleanerContainer() {
        return new KeystoneCleanerContainer(ksEngine, ksNative);
    }

    /**
     * Assembles a string that contains assembly code.
     *
     * @param assembly The assembly instructions. Use ; or \n to separate statements.
     * @return The return value is the machine code of the assembly instructions.
     * @throws AssembleFailedKeystoneException if the assembly code cannot be assembled properly.
     */
    public KeystoneEncoded assemble(String assembly) {
        return assemble(assembly, 0);
    }

    /**
     * Assembles a string that contains assembly code, located at a given address location.
     *
     * @param assembly The assembly instructions. Use ; or \n to separate statements.
     * @param address  The address of the first assembly instruction.
     * @return The return value is the machine code of the assembly instructions.
     * @throws AssembleFailedKeystoneException if the assembly code cannot be assembled properly.
     */
    public KeystoneEncoded assemble(String assembly, int address) {
        var pointerToMachineCodeBuffer = new PointerByReference();
        var pointerToMachineCodeSize = new IntByReference();
        var pointerToNumberOfStatements = new IntByReference();

        var result = ksNative.ks_asm(ksEngine, assembly, address, pointerToMachineCodeBuffer,
                pointerToMachineCodeSize, pointerToNumberOfStatements);

        if (result != 0) {
            var errorCode = ksNative.ks_errno(ksEngine);
            throw new AssembleFailedKeystoneException(ksNative, errorCode, assembly);
        }

        var machineCodeBuffer = pointerToMachineCodeBuffer.getValue();
        var machineCode = machineCodeBuffer.getByteArray(0, pointerToMachineCodeSize.getValue());

        ksNative.ks_free(machineCodeBuffer);

        return new KeystoneEncoded(machineCode, address, pointerToNumberOfStatements.getValue());
    }

    /**
     * Assembles a string that contains assembly code.
     *
     * @param assembly A collection of assembly instructions.
     * @return The return value is the machine code of the assembly instructions.
     * @throws AssembleFailedKeystoneException if the assembly code cannot be assembled properly.
     */
    public KeystoneEncoded assemble(Iterable<String> assembly) {
        return assemble(assembly, 0);
    }

    /**
     * Assembles a string that contains assembly code, located at a given address location.
     *
     * @param assembly A collection of assembly instructions.
     * @param address  The address of the first assembly instruction.
     * @return The return value is the machine code of the assembly instructions.
     * @throws AssembleFailedKeystoneException if the assembly code cannot be assembled properly.
     */
    public KeystoneEncoded assemble(Iterable<String> assembly, int address) {
        return assemble(String.join(";", assembly), address);
    }

    /**
     * Determines whether the given architecture is supported by Keystone.
     *
     * @param architecture The architecture type to check.
     * @return The return value is {@code true} if the architecture is supported, otherwise {@code false}.
     */
    public static boolean isArchitectureSupported(KeystoneArchitecture architecture) {
        return ksNative.ks_arch_supported(architecture);
    }

    /**
     * Gets the major and minor version numbers.
     *
     * @return The returned value is an instance of the class {@link Version}, containing the major and minor version numbers.
     */
    public Version version() {
        var major = new IntByReference();
        var minor = new IntByReference();

        ksNative.ks_version(major, minor);

        return new Version(major.getValue(), minor.getValue());
    }

    /**
     * Closes this resource, relinquishing any underlying resources.
     * This method is invoked automatically on objects managed by the
     * {@code try}-with-resources statement.
     * <p>
     * The call to this method is thread-safe.
     */
    @Override
    public void close() {
        var hasBeenAlreadyClosed = hasBeenClosed.getAndSet(true);

        if (!hasBeenAlreadyClosed) {
            ksEngineCleaner.close();
        }
    }
}
