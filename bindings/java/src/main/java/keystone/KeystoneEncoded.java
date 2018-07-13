/*
 * Copyright (c) 2018 Jämes Ménétrey <james@menetrey.me>
 *
 * This file is part of the Keystone Java bindings which is released under MIT.
 * See file LICENSE in the Java bindings folder for full license details.
 */

package keystone;

/**
 * Wrap the result of an assemble using Keystone.
 */
public class KeystoneEncoded {
    /**
     * The machine code generated by Keystone.
     */
    private final byte[] machineCode;

    /**
     * The address of the first assembly instruction.
     */
    private final int address;

    /**
     * The number of statements successfully processed.
     */
    private final int numberOfStatements;

    /**
     * Creates a new instance of the class {@link KeystoneEncoded}.
     *
     * @param machineCode The machine code generated by Keystone.
     * @param address The address of the first assembly instruction.
     * @param numberOfStatements The number of statements successfully processed.
     */
    public KeystoneEncoded(byte[] machineCode, int address, int numberOfStatements) {
        this.machineCode = machineCode;
        this.address = address;
        this.numberOfStatements = numberOfStatements;
    }

    /**
     * Gets the machine code generated by Keystone.
     */
    public byte[] getMachineCode() {
        return machineCode;
    }

    /**
     * Gets the address of the first assembly instruction.
     */
    public int getAddress() {
        return address;
    }

    /**
     * Gets the number of statements successfully processed.
     */
    public int getNumberOfStatements() {
        return numberOfStatements;
    }
}