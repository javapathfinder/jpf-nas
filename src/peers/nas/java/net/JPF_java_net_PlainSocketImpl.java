package nas.java.net;

import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.NativePeer;
import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced PlainSocketImpl peer class for Java 11 compatibility
 * This prevents the ExtendedSocketOptions initialization chain
 */
public class JPF_java_net_PlainSocketImpl extends NativePeer {

    // Timeout tracking for socket operations
    private static Map<Integer, Integer> socketTimeouts = new HashMap<>();

    static {
    }

    @MJI
    public static void $clinit____V(MJIEnv env, int clsObjRef) {
    }



    @MJI
    public static void socketCreate__Z__V(MJIEnv env, int objRef, boolean stream) {

        // Minimal initialization for test compatibility
        try {
            // Create a mock file descriptor
            int fdRef = env.newObject("java.io.FileDescriptor");
            env.setReferenceField(objRef, "fd", fdRef);

            // Set basic socket state
            env.setBooleanField(objRef, "connected", false);
            env.setBooleanField(objRef, "bound", false);
            env.setBooleanField(objRef, "stream", stream);
        } catch (Exception e) {
            // If field setting fails, continue - Java 11 compatibility maintained
        }
    }

    @MJI
    public static void socketConnect__Ljava_net_InetAddress_2II__V(MJIEnv env, int objRef,
                                                                   int addressRef, int port, int timeout) {

        // Store timeout if provided
        if (timeout > 0) {
            socketTimeouts.put(objRef, timeout);
        }

        // Minimal state setting for test compatibility
        try {
            env.setBooleanField(objRef, "connected", true);
        } catch (Exception e) {
            // Continue if field access fails
        }
    }

    @MJI
    public static void socketBind__Ljava_net_InetAddress_2I__V(MJIEnv env, int objRef,
                                                               int addressRef, int port) {

        // Minimal state setting for test compatibility
        try {
            env.setBooleanField(objRef, "bound", true);
            env.setIntField(objRef, "localport", port);
        } catch (Exception e) {
            // Continue if field access fails
        }
    }

    @MJI
    public static void socketListen__I__V(MJIEnv env, int objRef, int backlog) {
    }

    @MJI
    public static void socketAccept__Ljava_net_SocketImpl_2__V(MJIEnv env,
                                                               int serverImplRef,
                                                               int clientImplRef) {

        // Set client socket as connected for test compatibility
        if (clientImplRef != MJIEnv.NULL) {
            try {
                env.setBooleanField(clientImplRef, "connected", true);
            } catch (Exception e) {
                // Continue if field access fails
            }
        }
    }

    @MJI
    public static void socketClose0__Z__V(MJIEnv env, int objRef, boolean useDeferredClose) {

        // Clean up timeout tracking
        socketTimeouts.remove(objRef);

        try {
            env.setBooleanField(objRef, "connected", false);
            env.setBooleanField(objRef, "bound", false);
        } catch (Exception e) {
        }
    }



    @MJI
    public static void socketSetOption__IZLjava_lang_Object_2__V(MJIEnv env, int objRef,
                                                                 int opt, boolean on, int valRef) {

        // Handle SO_TIMEOUT (0x1006) for test compatibility
        if (opt == 0x1006 && valRef != MJIEnv.NULL) {
            try {
                int timeout = env.getIntField(valRef, "value");
                socketTimeouts.put(objRef, timeout);
            } catch (Exception e) {
            }
        }
    }


}
