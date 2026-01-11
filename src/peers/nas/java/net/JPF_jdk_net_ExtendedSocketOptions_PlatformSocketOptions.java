package nas.java.net;

import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.NativePeer;

/**
 * Critical Native Peer for ExtendedSocketOptions$PlatformSocketOptions
 * This class prevents the null string startsWith() error in Java 11
 */
public class JPF_jdk_net_ExtendedSocketOptions_PlatformSocketOptions extends NativePeer {

    // Static initialization - called when the peer class is loaded
    static {
    }

    /**
     * This is the critical method that prevents the null string error
     * The original method calls startsWith() on a null string
     */
    @MJI
    public static int create____Ljdk_net_ExtendedSocketOptions_PlatformSocketOptions_2(MJIEnv env, int clsObjRef) {
        // Return a mock PlatformSocketOptions instance
        // This completely bypasses the problematic native code
        return env.newObject("jdk.net.ExtendedSocketOptions$PlatformSocketOptions");
    }

    @MJI
    public static int get____Ljdk_net_ExtendedSocketOptions_PlatformSocketOptions_2(MJIEnv env, int clsObjRef) {
        // Return the same mock instance
        return env.newObject("jdk.net.ExtendedSocketOptions$PlatformSocketOptions");
    }

    // Platform capability methods - all return false to disable features
    @MJI
    public static boolean quickAckSupported____Z(MJIEnv env, int objRef) {
        return false;
    }

    @MJI
    public static boolean keepAliveOptionsSupported____Z(MJIEnv env, int objRef) {
        return false;
    }

    @MJI
    public static boolean peerCredentialsSupported____Z(MJIEnv env, int objRef) {
        return false;
    }

    @MJI
    public static boolean incomingNapiIdSupported____Z(MJIEnv env, int objRef) {
        return false;
    }

    @MJI
    public static boolean ipDontFragmentSupported____Z(MJIEnv env, int objRef) {
        return false;
    }

    @MJI
    public static boolean flowSupported____Z(MJIEnv env, int objRef) {
        return false;
    }

    @MJI
    public static void setQuickAckOption__Ljava_io_FileDescriptor_2Z__V(MJIEnv env, int objRef, int fdRef, boolean enable) {
    }

    @MJI
    public static void setTcpKeepAliveProbes__Ljava_io_FileDescriptor_2I__V(MJIEnv env, int objRef, int fdRef, int value) {
    }

    @MJI
    public static void setTcpKeepAliveTime__Ljava_io_FileDescriptor_2I__V(MJIEnv env, int objRef, int fdRef, int value) {
    }

    @MJI
    public static void setTcpKeepAliveIntvl__Ljava_io_FileDescriptor_2I__V(MJIEnv env, int objRef, int fdRef, int value) {
        // Mock implementation - do nothing
    }

    @MJI
    public static void setIpDontFragment__Ljava_io_FileDescriptor_2ZZ__V(MJIEnv env, int objRef, int fdRef, boolean enable, boolean isIPv6) {
        // Mock implementation - do nothing
    }
}
