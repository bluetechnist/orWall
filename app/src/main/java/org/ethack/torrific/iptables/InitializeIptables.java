package org.ethack.torrific.iptables;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.ethack.torrific.lib.CheckSum;
import org.ethack.torrific.lib.Shell;

import java.io.File;

/**
 * Initialize IPTables. The application has
 * to run at least once before this can be called.
 * This initialization is the second steps needed in order to get
 * Orbot working.
 */
public class InitializeIptables {

    private final IptRules iptRules;
    private final String dir_dst = "/data/local";
    private final String dst_file = String.format("%s/userinit.sh", dir_dst);
    private final Shell shell = new Shell();

    /**
     * Construtor
     *
     */
    public InitializeIptables() {
        this.iptRules = new IptRules();
    }



    public void LANPolicy(final boolean allow) {
        String[] lans = {
                "10.0.0.0/8",
                "172.16.0.0/12",
                "192.168.0.0/16"
        };
        if (allow) {
            if (iptRules.genericRule("-N LAN")) {
                iptRules.genericRule("-A LAN -p tcp -m tcp --dport 53 -j REJECT --reject-with icmp-port-unreachable");
                iptRules.genericRule("-A LAN -p udp -m udp --dport 53 -j REJECT --reject-with icmp-port-unreachable");
                iptRules.genericRule("-A LAN -j ACCEPT");
            }
        }
        for (String lan : lans) {
            if (!iptRules.LanNoNat(lan, allow)) {
                Log.e(
                        InitializeIptables.class.getName(),
                        String.format("Unable to bypass NAT for %s", lan));
            }
        }
        if (!allow) {
            iptRules.genericRule("-F LAN");
            iptRules.genericRule("-X LAN");
        }
    }

    public void initOutputs(final long orbot_uid) {
        String[] rules = {
                "-I INPUT 1 -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT -m comment --comment \"Allow established and related connections\"",
                String.format("-t nat -I OUTPUT 1 -m owner --uid-owner %d -j RETURN -m comment --comment \"Orbot bypasses itself.\"", orbot_uid),
                "-t nat -I OUTPUT 2 ! -o lo -p udp -m udp --dport 53 -j REDIRECT --to-ports 5400",
                "-I OUTPUT 1 -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT",
                String.format("-I OUTPUT 2 -m owner --uid-owner %d -j ACCEPT -m comment --comment \"Allow Orbot output\"", orbot_uid),
                "-I OUTPUT 3 -d 127.0.0.1/32 -p udp -m udp --dport 5400 -j ACCEPT -m comment --comment \"DNS Requests on Tor DNSPort\"",
                "-I OUTPUT 3 -d 127.0.0.1/32 -p tcp -m tcp --dport 8118 --tcp-flags FIN,SYN,RST,ACK SYN -j ACCEPT -m comment --comment \"Local traffic to Polipo\"",
                "-I OUTPUT 3 -d 127.0.0.1/32 -p tcp -m tcp --dport 9040 --tcp-flags FIN,SYN,RST,ACK SYN -j ACCEPT -m comment --comment \"Local traffic to TransPort\"",
                "-I OUTPUT 3 -d 127.0.0.1/32 -p tcp -m tcp --dport 9050 --tcp-flags FIN,SYN,RST,ACK SYN -j ACCEPT -m comment --comment \"Local traffic to SOCKSPort\"",
                // Remove the first reject we installed with the init-script
                "-D OUTPUT -j REJECT",
                // This will *break* quota management. But we have no choice, the POLICY is bypassed by quota chains :(.
                "-I OUTPUT 7 -j REJECT",
        };
        for (String rule : rules) {
            if (!iptRules.genericRule(rule)) {
                Log.e(InitializeIptables.class.getName(), "Unable to initialize");
                Log.e(InitializeIptables.class.getName(), rule);
            }
        }
    }

    public void installInitScript(final Context context) {

        final String src_file = new File(context.getDir("bin", 0), "userinit.sh").getAbsolutePath();

        CheckSum check_src = new CheckSum(src_file);
        CheckSum check_dst = new CheckSum(dst_file);

        if (!check_dst.hash().equals(check_src.hash())) {

            String CMD = String.format("cp %s %s", src_file, dst_file);
            if (shell.suExec(CMD)) {
                Log.d("Init", "Successfully installed userinit.sh script");
                CMD = String.format("chmod 0755 %s", dst_file);
                if (shell.suExec(CMD)) {
                    Log.d("Init", "Successfully chmod file");
                } else {
                    Log.e("Init", "ERROR while doing chmod on initscript");
                }
            } else {
                Log.e("Init", "ERROR while copying file to " + dst_file);
            }
        }
    }

    public void removeIniScript() {
        String CMD = String.format("rm -f %s", dst_file);
        if (shell.suExec(CMD)) {
            Log.d("Init", "file removed");
        } else {
            Log.e("Init", "ERROR while removing file");
        }
    }

    public void enableTethering(boolean status) {
        // TODO: find how it works
    }

    public void enableCaptiveDetection(boolean status, Context context) {
        // TODO: find a way to disable it on android <4.4
        if (Build.VERSION.SDK_INT > 18) {

            String CMD;
            if (status) {
                CMD = new File(context.getDir("bin", 0), "activate_portal.sh").getAbsolutePath();;
            } else {
                CMD = new File(context.getDir("bin", 0), "deactivate_portal.sh").getAbsolutePath();;
            }
            Shell threaded = new Shell("sh "+CMD);
            threaded.run();
        }
    }


}
