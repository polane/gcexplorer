package za.co.jumpingbean.gc.testApp;


import za.co.jumpingbean.gc.testApp.jmx.GCGenerator;
import com.codahale.metrics.MetricRegistry;
import java.lang.management.ManagementFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author mark
 */
public class GarbageGeneratorApp {

    private MBeanServer server;
    private final GCGenerator bean;

    public GarbageGeneratorApp() {
        server = ManagementFactory.getPlatformMBeanServer();
        System.out.println("Starting up a new GC Generator App");
        Analiser analiser = new Analiser(new MetricRegistry());
        LocalObjectGenerator sl = new LocalObjectGenerator(analiser);
        LongLivedObjectGenerator ll = new LongLivedObjectGenerator(analiser);

        bean = new GCGenerator(sl, ll, this);
        try {
            ObjectName name = new ObjectName("JumpingBean:name=GCGenerator");
            server.registerMBean(bean, name);
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException |
                MalformedObjectNameException | NotCompliantMBeanException ex) {
            System.out.println("Error initialising MBean. Exiting...");
            System.exit(1);
        }
    }

    public boolean isRunning() {
        return bean.isRunning();
    }

    public static void main(String[] args) {
        System.out.println("Starting Generator....");
        GarbageGeneratorApp gen = new GarbageGeneratorApp();
        synchronized (gen) {
            System.out.println("Generator started...");
            while (gen.isRunning()) {
                try {
                    System.out.println("waiting....");
                    gen.wait();
                } catch (InterruptedException ex) {
                    Logger.getLogger(GarbageGeneratorApp.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
}