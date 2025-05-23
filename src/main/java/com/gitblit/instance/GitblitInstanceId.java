package com.gitblit.instance;

import com.gitblit.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

/**
 * The instance id is a unique identifier for an installed Gitblit instance.
 *
 * This is used to track the number of Gitblit instances in the field.
 * Its purpose is to gauge the popularity of Gitblit and to help
 * prioritize feature requests.
 *
 * The instance id should be unique between different instances, even
 * on the same machine. But it should stay the same between restarts of
 * the same instance. It should also stay the same between upgrades of
 * the same instance. Therefore, it must be stored in a file that is
 * not overwritten during upgrades, once it has been created.
 */
public class GitblitInstanceId
{
    static final String STORAGE_FILE = "gbins";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final File idFileBase;

    private UUID id;


    /**
     * Constructor.
     */
    public GitblitInstanceId()
    {
        this.idFileBase = null;
    }

    /**
     * Constructor.
     */
    public GitblitInstanceId(File idFileBase)
    {
        this.idFileBase = idFileBase;
    }


    /**
     * Get the instance id.
     *
     * @return the instance id.
     */
    public UUID getId() {
        if (this.id == null) {
            load();
        }
        return this.id;
    }


    /**
     * Load the instance id from the file.
     */
    private void load()
    {
        if (this.idFileBase == null) {
            // Not working with stored id.
            log.debug("No id file base directory specified. Generated id is not persisted.");
            generate();
            return;
        }

        File idFile = new File(this.idFileBase, STORAGE_FILE);
        if (idFile.exists()) {
            // Read the file
            String uuidString = readFromFile(idFile);

            // Parse the UUID
            try {
                this.id = UUID.fromString(uuidString);
                return;
            }
            catch (IllegalArgumentException e) {
                log.debug("Unable to parse instance id. Will generate a new one: {}", e.getMessage(), e);
            }
        }

        // Generate a new instance id and persist it to disk.
        generate();
        storeToFile(idFile);
    }


    private String readFromFile(File idfile)
    {
//        log.debug("Loading instance id from file: {}", idfile.getAbsolutePath());

        String string = FileUtils.readContent(idfile, null).trim();
        String uuidString = string.replaceAll("\\s+","");
        return uuidString.trim();
    }

    private void storeToFile(File idfile)
    {
        // Make sure that the directory exists
        if (!idfile.getParentFile().exists()) {
            if (!idfile.getParentFile().mkdirs()) {
                log.debug("Unable to create directory for instance id file: {}", idfile.getParentFile().getAbsolutePath());
                return;
            }
        }

        // Write the UUID to the file
        String uuidString = this.id.toString();
        FileUtils.writeContent(idfile, uuidString);
    }


    /**
     * Generate a new instance id and persist it to disk.
     *
     * UUID is variant, i.e. OSF DCE, version 8, a custom format.
     * xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
     *    date -rand-8rnd-8rnd- rand  OUI
     *
     * The variant nibble has the variant (1) in the upper two bits as 0b10xx,
     * and the lower two bits are used as a version, currently 0bxx00.
     * Should the format of this UUID change, the version can be incremented
     * to 0bxx01 or 0bxx10. Further increments would set the bits in the variant
     * nibble to 0bxx11 and employ more bits from the next nibble for further
     * differentiation.
     */
    private void generate()
    {
        // Start with a random UUID
        UUID id = UUID.randomUUID();
        long upper = id.getMostSignificantBits();
        long lower = id.getLeastSignificantBits();


        // Set the variant bits to 0b1000, variant 1, our version 0.
        lower &= 0x0FFFFFFFFFFFFFFFL; // Clear the variant bits
        lower |= 0x8000000000000000L; // Set the variant bits to 0b1000

        // Set the version bits to 0b1000, version 8.
        upper &= 0xFFFFFFFFFFFF0FFFL; // Clear the version bits
        upper |= 0x0000000000008000L; // Set the version bits to 0b1000


        // Set the first four bytes to represent the date.
        long date = System.currentTimeMillis();
        date &= 0xFFFFFFFFFFFF0000L; // Clear the last two bytes, those are only a few minutes.
        date <<= 2 * 8;  // We do not need the upper two bytes, that is too far into the future.

        upper &= 0x00000000FFFFFFFFL; // Clear the date bits.
        upper |= date; // Set the date in the upper 32 bits.


        // Set the OUI in the lower three bytes.
        Long oui = getNodeOUI();
        if (oui != null) {
            lower &= 0xFFFFFFFFFF000000L; // Clear the OUI bits.
            lower |= (0x1000000L | oui); // Set the OUI in the lower three bytes. Mark as valid OUI in bit above them.
        }
        else {
            // Mark this as an invalid OUI, i.e. random bits, by setting the bit above the OUI bits to zero.
            lower &= 0xFFFFFFFFFEFFFFFFL; // Clear the valid OUI indicator bit.
        }

        this.id = new UUID(upper, lower);
    }


    /**
     * Get the OUI of one NIC of this host.
     *
     * @return null if no OUI could be detected, otherwise the OUI in the lower three bytes of a Long.
     */
    private Long getNodeOUI()
    {
        byte[] node = null;
        String logPrefix = "Unable to detect host. Use random value.";

        try {
            InetAddress ipa = InetAddress.getLocalHost();
            NetworkInterface iface = NetworkInterface.getByInetAddress(ipa);
            if (iface != null) {
                node = iface.getHardwareAddress();
                logPrefix = "From getLocalHost:";
            }

            if (node == null) {
                List<byte[]> macs = new ArrayList<>();
                List<byte[]> offmacs = new ArrayList<>();
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    iface = interfaces.nextElement();
                    byte[] mac = iface.getHardwareAddress();
                    if (mac != null) {
                        if (iface.isLoopback()) {
                            continue;
                        }
                        if (iface.isVirtual() || iface.isPointToPoint()) {
                            continue;
                        }
                        if (iface.isUp()) {
                            macs.add(mac);
                        }
                        else {
                            offmacs.add(mac);
                        }
                    }
                }

                if (macs.size() == 1) {
                    node = macs.get(0);
                    logPrefix = "From up iface:";
                }
                else if (offmacs.size() == 1) {
                    node = offmacs.get(0);
                    logPrefix = "From down iface:";
                }
            }

            if (node == null) {
                Socket socket = new Socket("www.gitblit.dev", 80);
                ipa = socket.getLocalAddress();
                socket.close();
                iface = NetworkInterface.getByInetAddress(ipa);
                if (iface != null) {
                    node = iface.getHardwareAddress();
                    logPrefix = "From socket:";
                }
            }

            if (node == null) {
                log.debug(logPrefix);
                return null;
            }

            if (log.isDebugEnabled()) {
                log.debug("{} {}", logPrefix, String.format("%02X:%02X:%02X", node[0], node[1], node[2]));
            }

            long l = (((long)node[0]) << 16) & 0xff0000;
            l |= (((long)node[1]) << 8) & 0xff00;
            l |= ((long)node[2]) & 0xff;
            return l;
        }
        catch (IOException e) {
            log.debug("Exception while getting OUI: {}", e.getMessage(), e);
            return null;
        }
    }
}
