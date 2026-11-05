package com.course_aura_hub.common.scheduling;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/** Identifies this running instance, so locks and cluster messages can tell instances apart. */
@Component
public class ClusterNode {

    private final String id;

    public ClusterNode() {
        this.id = hostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String id() {
        return id;
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            return "node";
        }
    }
}
