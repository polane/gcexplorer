/* 
 * Copyright (C) 2014 Mark Clarke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package za.co.jumpingbean.gc.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.management.remote.JMXServiceURL;
import za.co.jumpingbean.gc.service.constants.DESC;
import za.co.jumpingbean.gc.testapp.GarbageGeneratorApp;

public class GeneratorService {

    final Map<UUID, ProcessObject> processes = new HashMap<>();
    ReadWriteLock rwLock = new ReentrantReadWriteLock();
    Lock wlock = rwLock.writeLock();
    Lock rlock = rwLock.readLock();

    public UUID startTestApp(String javaPath, String port, String classPath,
            String mainClass, List<String> gcOptions, boolean isWebStart)
            throws IllegalStateException, IOException {
        ProcessParams params;

        if (gcOptions == null) {
            params = new ProcessParams(port, classPath, mainClass);
        } else {
            params = new ProcessParams(port, classPath, mainClass, gcOptions);
        }
        return this.startTestApp(javaPath, params, isWebStart);
    }

    public UUID connectToJavaProcess(String url, String username, String password) throws IOException {
        try {
            ProcessObject procObj = new ProcessObject(
                    GarbageGeneratorApp.class.getCanonicalName(),
                    JMXQueryRunner.createJMXQueryRunner(new JMXServiceURL(url),
                            username, password));
            try {
                wlock.lock();
                processes.put(procObj.getId(), procObj);
            } finally {
                wlock.unlock();
            }
            return procObj.getId();
        } catch (IOException ex) {
            throw ex;
        }
    }

    public UUID connectToJavaProcess(int pid) throws IOException {
        try {
            ProcessObject procObj = new ProcessObject(
                    GarbageGeneratorApp.class.getCanonicalName(),
                    JMXQueryRunner.createJMXQueryRunner(pid));
            try {
                wlock.lock();
                processes.put(procObj.getId(), procObj);
            } finally {
                wlock.unlock();
            }
            return procObj.getId();
        } catch (IOException ex) {
            throw ex;
        }
    }

    public UUID startTestApp(String javaPath, ProcessParams params, boolean isWebStart) throws IllegalStateException, IOException {
        System.out.println("Starting up....");
        List<String> cmd = new LinkedList<>();
        cmd.add(javaPath);
        if (isWebStart) {
            cmd.add("-J-Dcom.sun.management.jmxremote");
            cmd.add("-J-Dcom.sun.management.jmxremote.port=" + params.getPort());
            cmd.add("-J-Dcom.sun.management.jmxremote.ssl=false");
            cmd.add("-J-Dcom.sun.management.jmxremote.authenticate=false");
            cmd.add("-J-Djava.rmi.server.hostname=127.0.0.1");
        } else {
            if (params.getClassPath() != null && !params.getClassPath().isEmpty()) {
                cmd.add("-cp");
                cmd.add(params.getClassPath());
            }
            cmd.add("-Dcom.sun.management.jmxremote");
            cmd.add("-Dcom.sun.management.jmxremote.port=" + params.getPort());
            cmd.add("-Dcom.sun.management.jmxremote.ssl=false");
            cmd.add("-Dcom.sun.management.jmxremote.authenticate=false");
            cmd.add("-Djava.rmi.server.hostname=127.0.0.1");
        }
        if (!params.getGcOptions().isEmpty()) {
            cmd.addAll(params.getGcOptions());
        }
        cmd.add(params.getMainClass());

        ProcessBuilder procBuilder = new ProcessBuilder(cmd);
        Process proc = procBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                proc.getInputStream()));

        BufferedReader error = new BufferedReader(new InputStreamReader(
                proc.getErrorStream()));
        try {
            if (proc.exitValue() != 0) {
                String str = getError(error);
                error.close();
                reader.close();
                throw new IllegalStateException("Error starting process: " + str);
            } else {
                throw new IllegalStateException("Process terminate too early.");
            }
        } catch (IllegalThreadStateException ex) {
            //process has not yet exited which is fine.
        }
        //Only start stdin reader if proc was launched successfully.

        //give proc time to start up before
        //connecting to JMX server

        synchronized (this) {
            try {
                if (isWebStart){
                    this.wait(5000L);
                }else{
                    this.wait(1000L);
                }
            } catch (InterruptedException ex) {

            }
        }

        //See if proc exits early. It should be still be alive at this point
        //if (proc.isAlive()) {
            ProcOutputReaderList outputList = new ProcOutputReaderList(proc, reader);
            outputList.init();
            try {
                ControlableProcess procObj = new ControlableProcess(
                        GarbageGeneratorApp.class.getCanonicalName(), proc, params,
                        outputList, JMXQueryRunner.createJMXQueryRunner(params.getPort()));
                try {
                    wlock.lock();
                    processes.put(procObj.getId(), procObj);
                } finally {
                    wlock.unlock();
                }
                error.close();
                return procObj.getId();
            } catch (IOException ex) {
                outputList.stop();
                reader.close();
                error.close();
                proc.destroy();
                throw new IllegalStateException(ex.getMessage());
            }
//        } else {
//            String str = getError(error);
//            error.close();
//            reader.close();
//            proc.destroy();
//            throw new IllegalStateException("Process terminate before initialistion could complete. " + str);
//        }
    }

    public void stopTestApp(UUID id) {
        try {
            wlock.lock();
            if (isControlableProcess(id)) {
                ((ControlableProcess) processes.get(id)).stop();
                processes.remove(id);
            }
        } finally {
            wlock.unlock();
        }
    }

    public void stopAllTestApps() {
        try {
            wlock.lock();
            for (ProcessObject proc : processes.values()) {
                if (isControlableProcess(proc.getId())) {
                    ((ControlableProcess) proc).stop();
                }
            }
            processes.clear();
        } finally {
            wlock.unlock();
        }
    }

    public JMXQueryRunner getJMXQueryRunner(UUID id) {
        try {
            rlock.lock();
            return this.processes.get(id).getQry();
        } finally {
            rlock.unlock();
        }
    }

    public ProcessParams getProcessParams(UUID id) {
        try {
            rlock.lock();
            if (this.isControlableProcess(id)) {
                return ((ControlableProcess) this.processes.get(id)).getParams();
            } else {
                return new ProcessParams("", "", "");
            }
        } finally {
            rlock.unlock();
        }
    }

    public String getProcessOutput(UUID id) {
        try {
            rlock.lock();
            if (isControlableProcess(id)) {
                return ((ControlableProcess) this.processes.get(id)).readProcessOutputLine();
            } else {
                return "";
            }
        } finally {
            rlock.unlock();
        }
    }

    public Number queryJMXForValue(UUID id, DESC desc) throws GCExplorerServiceException {
        Number result = 0;
        try {
            rlock.lock();
            if (processes.get(id) == null) {
                throw new GCExplorerServiceException("Process has been removed");
            }
            switch (desc) {
                case EDENSPACEUSED:
                    result = processes.get(id).getQry().getEdenSpace().getUsage().getUsed();
                    break;
                case EDENSPACECOMMITTED:
                    result = processes.get(id).getQry().getEdenSpace().getUsage().getCommitted();
                    break;
                case EDENSPACEMAX:
                    result = processes.get(id).getQry().getEdenSpace().getUsage().getMax();
                    break;
                case EDENSPACEFREE:
                    result = (processes.get(id).getQry().getEdenSpace().
                            getUsage().getCommitted()
                            - processes.get(id).getQry().getEdenSpace().
                            getUsage().getUsed());
                    break;
                case SURVIVORSPACEUSED:
                    result = processes.get(id).getQry().getSurvivorSpace().getUsage().getUsed();
                    break;
                case SURVIVORSPACECOMMITTED:
                    result = processes.get(id).getQry().getSurvivorSpace().getUsage().getCommitted();
                    break;
                case SURVIVORSPACEMAX:
                    result = processes.get(id).getQry().getSurvivorSpace().getUsage().getMax();
                    break;
                case SURVIVORSPACEFREE:
                    result = (processes.get(id).getQry().getSurvivorSpace().
                            getUsage().getCommitted()
                            - processes.get(id).getQry().getSurvivorSpace().
                            getUsage().getUsed());
                    break;
                case OLDGENSPACEUSED:
                    result = processes.get(id).getQry().getOldGenSpace().getUsage().getUsed();
                    break;
                case OLDGENSPACECOMMITTED:
                    result = processes.get(id).getQry().getOldGenSpace().getUsage().getCommitted();
                    break;
                case OLDGENSPACEMAX:
                    result = processes.get(id).getQry().getOldGenSpace().getUsage().getMax();
                    break;
                case OLDGENSPACEFREE:
                    result = (processes.get(id).getQry().getOldGenSpace().
                            getUsage().getCommitted()
                            - processes.get(id).getQry().getOldGenSpace().
                            getUsage().getUsed());
                    break;
                case PERMGENSPACEUSED:
                    result = processes.get(id).getQry().getPermGenSpace().getUsage().getUsed();
                    break;
                case PERMGENSPACECOMMITTED:
                    result = processes.get(id).getQry().getPermGenSpace().getUsage().getCommitted();
                    break;
                case PERMGENSPACEMAX:
                    result = processes.get(id).getQry().getPermGenSpace().getUsage().getMax();
                    break;
                case PERMGENSPACEFREE:
                    result = (processes.get(id).getQry().getPermGenSpace().
                            getUsage().getCommitted()
                            - processes.get(id).getQry().getPermGenSpace().
                            getUsage().getUsed());
                    break;
            }
        } finally {
            rlock.unlock();
        }
        return result;
    }

    public String genLocalInstances(UUID id, int numInstances, int instanceSize, int creationPauseTime) {
        rlock.lock();
        try {
            String str = processes.get(id).getQry().getGCGenerator().runLocalObjectCreator(numInstances, instanceSize, creationPauseTime);
            return str;
        } finally {
            rlock.unlock();
        }
    }

    public String genLongLivedInstances(UUID id, int numInstances, int instanceSize, int creationPauseTime) {
        rlock.lock();
        try {
            String str = processes.get(id).getQry().getGCGenerator().runLongLivedObjectCreator(numInstances, instanceSize, creationPauseTime);
            return str;
        } finally {
            rlock.unlock();
        }
    }

    public void releaseLongLivedInstances(UUID id, int numInstances, boolean reverse) {
        rlock.lock();
        try {
            processes.get(id).getQry().getGCGenerator().releaseLongLivedObjects(numInstances, reverse);
        } finally {
            rlock.unlock();
        }
    }

    public String getGCInfo(UUID procId) {
        rlock.lock();
        try {
            return processes.get(procId).getQry().getGCInfo();
        } finally {
            rlock.unlock();
        }
    }

    private boolean isControlableProcess(UUID id) {
        rlock.lock();
        try {
            return processes.get(id) instanceof ControlableProcess;
        } finally {
            rlock.unlock();
        }
    }

    public List<String> getLocalProcessesList() {
        return LocalJavaProcessFinder.getLocalJavaProcesses();
    }

    public String getJavaVersion(UUID id) {
        rlock.lock();
        try {
            return processes.get(id).getQry().getJavaVersion();
        } finally {
            rlock.unlock();
        }
    }


    private String getError(BufferedReader error) throws IOException {
        StringBuilder str = new StringBuilder();
        while (error.ready()) {
            str.append(error.readLine() );
        }
        return str.toString();
    }
    
    public String getConsoleLog(UUID procId) {
         try {
            rlock.lock();
            return ((ControlableProcess)this.processes.get(procId)).readProcessOutputLine();
        } finally {
            rlock.unlock();
        }       

    }

}
