/*
 * Copyright (C) 2012 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (c) 2012 Stephen Erickson, Chris Ravenscroft, Adam Shanks (RootTools)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.rootcommands;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.rootcommands.command.BinaryCommand;
import org.rootcommands.command.Command;
import org.rootcommands.command.SimpleCommand;
import org.rootcommands.util.BrokenBusyboxException;
import org.rootcommands.util.Constants;
import org.rootcommands.util.Log;

import android.os.StatFs;

/**
 * All methods in this class are working with Androids toolbox. Toolbox is similar to busybox, but
 * normally shipped on every Android OS. You can find toolbox commands on
 * https://github.com/CyanogenMod/android_system_core/tree/ics/toolbox
 * 
 * This means that these commands are designed to work on every Android OS, with a _working_ toolbox
 * binary on it. They don't require busybox!
 * 
 */
public class Toolbox {
    private Shell shell;

    /**
     * All methods in this class are working with Androids toolbox. Toolbox is similar to busybox,
     * but normally shipped on every Android OS.
     * 
     * @param shell
     *            where to execute commands on
     */
    public Toolbox(Shell shell) {
        super();
        this.shell = shell;
    }

    /**
     * Checks if user accepted root access
     * 
     * (commands: id)
     * 
     * @return true if user has given root access
     * @throws IOException
     * @throws TimeoutException
     * @throws BrokenBusyboxException
     */
    public boolean isRootAccessGiven() throws BrokenBusyboxException, TimeoutException, IOException {
        SimpleCommand idCommand = new SimpleCommand("id");
        shell.add(idCommand).waitForFinish();

        if (idCommand.getOutput().contains("uid=0")) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * This command class gets all pids to a given process name
     */
    private class PsCommand extends Command {
        private String processName;
        private ArrayList<String> pids;
        private String psRegex;
        private Pattern psPattern;

        public PsCommand(String processName) {
            super("ps");
            this.processName = processName;
            pids = new ArrayList<String>();

            /**
             * regex to get pid out of ps line, example:
             * 
             * <pre>
             *  root    24736    1   12140  584   ffffffff 40010d14 S /data/data/org.adaway/files/blank_webserver
             * ^\\S \\s ([0-9]+)                          .*                                      processName    $
             * </pre>
             */
            psRegex = "^\\S+\\s+([0-9]+).*" + Pattern.quote(processName) + "$";
            psPattern = Pattern.compile(psRegex);
        }

        public ArrayList<String> getPids() {
            return pids;
        }

        public String getPidsString() {
            StringBuilder sb = new StringBuilder();
            for (String s : pids) {
                sb.append(s);
                sb.append(" ");
            }

            return sb.toString();
        }

        @Override
        public void output(int id, String line) {
            // generell check if line contains processName
            if (line.contains(processName)) {
                Matcher psMatcher = psPattern.matcher(line);

                // try to match line exactly
                try {
                    if (psMatcher.find()) {
                        String pid = psMatcher.group(1);
                        // add to pids list
                        pids.add(pid);
                        Log.d(Constants.TAG, "Found pid: " + pid);
                    } else {
                        Log.d(Constants.TAG, "Matching in ps command failed!");
                    }
                } catch (Exception e) {
                    Log.e(Constants.TAG, "Error with regex!", e);
                }
            }
        }

        @Override
        public void afterExecution(int id, int exitCode) {
        }

    }

    /**
     * This method can be used to kill a running process
     * 
     * (commands: ps, kill)
     * 
     * @param processName
     *            name of process to kill
     * @return <code>true</code> if process was found and killed successfully
     * @throws IOException
     * @throws TimeoutException
     * @throws BrokenBusyboxException
     */
    public boolean killAll(String processName) throws BrokenBusyboxException, TimeoutException,
            IOException {
        Log.d(Constants.TAG, "Killing process " + processName);

        PsCommand psCommand = new PsCommand(processName);
        shell.add(psCommand).waitForFinish();

        // kill processes
        if (!psCommand.getPids().isEmpty()) {
            // example: kill -9 1234 1222 5343
            SimpleCommand killCommand = new SimpleCommand("kill -9 " + psCommand.getPidsString());
            shell.add(killCommand).waitForFinish();

            if (killCommand.getExitCode() == 0) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Kill a running binary
     * 
     * See README for more information how to use your own binaries!
     * 
     * @param binaryName
     * @return
     * @throws BrokenBusyboxException
     * @throws TimeoutException
     * @throws IOException
     */
    public boolean killAllBinary(String binaryName) throws BrokenBusyboxException,
            TimeoutException, IOException {
        return killAll(BinaryCommand.BINARY_PREFIX + binaryName + BinaryCommand.BINARY_SUFFIX);
    }

    /**
     * This method can be used to to check if a process is running
     * 
     * @param processName
     *            name of process to check
     * @return <code>true</code> if process was found
     * @throws IOException
     * @throws BrokenBusyboxException
     * @throws TimeoutException
     *             (Could not determine if the process is running)
     */
    boolean isProcessRunning(final String processName) throws BrokenBusyboxException,
            TimeoutException, IOException {
        PsCommand psCommand = new PsCommand(processName);
        shell.add(psCommand).waitForFinish();

        // if pids are available process is running!
        if (!psCommand.getPids().isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Ls command to get permissions or symlinks
     */
    private class LsCommand extends Command {
        private String file;
        private String lsRegex;
        private Pattern lsPattern;
        private String symlinkRegex;
        private Pattern symlinkPattern;

        private String symlink;
        private String permissions;

        public String getSymlink() {
            return symlink;
        }

        public String getPermissions() {
            return permissions;
        }

        public LsCommand(String file) {
            super("ls -l " + file, "busybox ls -l " + file, "/system/bin/failsafe/toolbox ls -l "
                    + file, "toolbox ls -l " + file);
            this.file = file;

            /**
             * regex to get pid out of ps line, example:
             * 
             * <pre>
             *  lrwxrwxrwx     1 root root            15 Aug 13 12:14 dev/stdin -> /proc/self/fd/0
             * ^(\\S{10}) \\s+     .*                                 file      (.*)              $
             * </pre>
             */
            lsRegex = "^(\\S{10})\\s+.*" + Pattern.quote(file) + "(.*)$";
            lsPattern = Pattern.compile(lsRegex);

            /**
             * regex to get symlink
             * 
             * <pre>
             *  ->           /proc/self/fd/0
             * ^\\-\\> \\s+  (.*)           $
             * </pre>
             */
            symlinkRegex = "^\\-\\>\\s+(.*)$";
            symlinkPattern = Pattern.compile(symlinkRegex);
        }

        /**
         * Converts permission string from ls command to numerical value. Example: -rwxrwxrwx gets
         * to 777
         * 
         * @param permissions
         * @return
         */
        private String convertPermissions(String permissions) {
            int owner = getGroupPermission(permissions.substring(1, 3));
            int group = getGroupPermission(permissions.substring(4, 6));
            int world = getGroupPermission(permissions.substring(7, 9));

            return "" + owner + group + world;
        }

        /**
         * Calculates permission for one group
         * 
         * @param permission
         * @return value of permission string
         */
        private int getGroupPermission(String permission) {
            int value = 0;

            if (permission.charAt(0) == 'r') {
                value += 4;
            }
            if (permission.charAt(1) == 'w') {
                value += 2;
            }
            if (permission.charAt(2) == 'x') {
                value += 1;
            }

            return value;
        }

        @Override
        public void output(int id, String line) {
            // general check if line contains file
            if (line.contains(file)) {
                Matcher lsMatcher = lsPattern.matcher(line);

                // try to match line exactly
                try {
                    if (lsMatcher.find()) {
                        permissions = convertPermissions(lsMatcher.group(1));

                        Log.d(Constants.TAG, "Found permissions: " + permissions);

                        // if there is more it could be a symlink
                        String symlinkGroup = lsMatcher.group(2);
                        if (symlinkGroup != null) {
                            // try to parse for symlink
                            Matcher symlinkMatcher = symlinkPattern.matcher(symlinkGroup);

                            /*
                             * TODO: If symlink points to a file in the same directory the path is
                             * not absolute!!!
                             */
                            if (symlinkMatcher.find()) {
                                symlink = symlinkMatcher.group(1);
                                Log.d(Constants.TAG, "Symlink found: " + symlink);
                            } else {
                                Log.d(Constants.TAG, "No symlink found!");
                            }
                        }
                    } else {
                        Log.d(Constants.TAG, "Matching in ls command failed!");
                    }
                } catch (Exception e) {
                    Log.e(Constants.TAG, "Error with regex!", e);
                }
            }
        }

        @Override
        public void afterExecution(int id, int exitCode) {
        }

    }

    /**
     * @param file
     *            String that represent the file, including the full path to the file and its name.
     * @param followSymlinks
     * @return File permissions as String, for example: 777, returns null on error
     * @throws IOException
     * @throws TimeoutException
     * @throws BrokenBusyboxException
     * 
     */
    public String getFilePermissions(String file) throws BrokenBusyboxException, TimeoutException,
            IOException {
        Log.d(Constants.TAG, "Checking permissions for " + file);

        String permissions = null;

        if (fileExists(file)) {
            Log.d(Constants.TAG, file + " was found.");

            LsCommand lsCommand = new LsCommand(file);
            shell.add(lsCommand).waitForFinish();

            permissions = lsCommand.getPermissions();
        }

        return permissions;
    }

    /**
     * Sets permission of file
     * 
     * @param file
     *            absolute path to file
     * @param permissions
     *            String like 777
     * @return true if command worked
     * @throws BrokenBusyboxException
     * @throws TimeoutException
     * @throws IOException
     */
    public boolean setFilePermissions(String file, String permissions)
            throws BrokenBusyboxException, TimeoutException, IOException {
        Log.d(Constants.TAG, "Set permissions of " + file + " to " + permissions);

        SimpleCommand chmodCommand = new SimpleCommand("chmod " + permissions + " " + file);
        shell.add(chmodCommand).waitForFinish();

        if (chmodCommand.getExitCode() == 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * This will return a String that represent the symlink for a specified file.
     * 
     * @param file
     *            The path to the file to get the Symlink for. (must have absolute path)
     * 
     * @return A String that represent the symlink for a specified file or null if no symlink
     *         exists.
     * @throws IOException
     * @throws TimeoutException
     * @throws BrokenBusyboxException
     */
    public String getSymlink(String file) throws BrokenBusyboxException, TimeoutException,
            IOException {
        Log.d(Constants.TAG, "Find symlink for " + file);

        String symlink = null;

        if (fileExists(file)) {
            Log.d(Constants.TAG, file + " was found.");

            LsCommand lsCommand = new LsCommand(file);
            shell.add(lsCommand).waitForFinish();

            symlink = lsCommand.getSymlink();
        }

        return symlink;
    }

    /**
     * Copys a file to a destination. Because cp is not available on all android devices, we use dd
     * or cat.
     * 
     * @param source
     *            example: /data/data/org.adaway/files/hosts
     * @param destination
     *            example: /system/etc/hosts
     * @param remountAsRw
     *            remounts the destination as read/write before writing to it
     * @param preserveFileAttributes
     *            tries to copy file attributes from source to destination, if only cat is available
     *            only permissions are preserved
     * @return true if it was successfully copied
     * @throws BrokenBusyboxException
     * @throws IOException
     * @throws TimeoutException
     */
    public boolean copyFile(String source, String destination, boolean remountAsRw,
            boolean preservePermissions) throws BrokenBusyboxException, IOException,
            TimeoutException {

        /*
         * dd can only copy files, but we can not check if the source is a file without invoking
         * shell commands, because from Java we probably have no read access, thus we only check if
         * they are ending with trailing slashes
         */
        if (source.endsWith("/") || destination.endsWith("/")) {
            throw new FileNotFoundException("dd can only copy files!");
        }

        // remount destination as read/write before copying to it
        if (remountAsRw) {
            if (!remount(destination, "RW")) {
                throw new FileNotFoundException("Remounting failed!");
            }
        }

        // get permissions of source before overwriting
        String permissions = null;
        if (preservePermissions) {
            permissions = getFilePermissions(source);
        }

        boolean commandSuccess = false;

        SimpleCommand ddCommand = new SimpleCommand("dd if=" + source + " of=" + destination);
        shell.add(ddCommand).waitForFinish();

        if (ddCommand.getExitCode() == 0) {
            commandSuccess = true;
        } else {
            // try cat if dd fails
            SimpleCommand catCommand = new SimpleCommand("cat " + source + " > " + destination);
            shell.add(catCommand).waitForFinish();

            if (catCommand.getExitCode() == 0) {
                commandSuccess = true;
            }
        }

        // set back permissions from source to destination
        if (preservePermissions) {
            setFilePermissions(destination, permissions);
        }

        // remount destination back to read only
        if (remountAsRw) {
            if (!remount(destination, "RO")) {
                throw new FileNotFoundException("Remounting failed!");
            }
        }

        return commandSuccess;
    }

    public static final int REBOOT_HOTREBOOT = 1;
    public static final int REBOOT_REBOOT = 2;
    public static final int REBOOT_SHUTDOWN = 3;
    public static final int REBOOT_RECOVERY = 4;

    /**
     * Shutdown or reboot device. Possible actions are REBOOT_HOTREBOOT, REBOOT_REBOOT,
     * REBOOT_SHUTDOWN, REBOOT_RECOVERY
     * 
     * @param action
     * @throws IOException
     * @throws TimeoutException
     * @throws BrokenBusyboxException
     */
    public void reboot(int action) throws BrokenBusyboxException, TimeoutException, IOException {
        if (action == REBOOT_HOTREBOOT) {
            killAll("system_server");
            // or: killAll("zygote");
        } else {
            String command;
            switch (action) {
            case REBOOT_REBOOT:
                command = "reboot";
                break;
            case REBOOT_SHUTDOWN:
                command = "reboot -p";
                break;
            case REBOOT_RECOVERY:
                command = "reboot recovery";
                break;
            default:
                command = "reboot";
                break;
            }

            SimpleCommand rebootCommand = new SimpleCommand(command);
            shell.add(rebootCommand).waitForFinish();

            if (rebootCommand.getExitCode() == -1) {
                Log.e(Constants.TAG, "Reboot failed!");
            }
        }
    }

    /**
     * This command checks if a file exists
     */
    private class FileExistsCommand extends Command {
        private String file;
        private boolean fileExists = false;

        public FileExistsCommand(String file) {
            super("ls " + file);
            this.file = file;
        }

        public boolean isFileExists() {
            return fileExists;
        }

        @Override
        public void output(int id, String line) {
            if (line.trim().equals(file)) {
                fileExists = true;
            }
        }

        @Override
        public void afterExecution(int id, int exitCode) {
        }

    }

    /**
     * Use this to check whether or not a file exists on the filesystem.
     * 
     * @param file
     *            String that represent the file, including the full path to the file and its name.
     * 
     * @return a boolean that will indicate whether or not the file exists.
     * @throws IOException
     * @throws TimeoutException
     * @throws BrokenBusyboxException
     * 
     */
    public boolean fileExists(String file) throws BrokenBusyboxException, TimeoutException,
            IOException {
        FileExistsCommand fileExistsCommand = new FileExistsCommand(file);
        shell.add(fileExistsCommand).waitForFinish();

        if (fileExistsCommand.isFileExists()) {
            return true;
        } else {
            return false;
        }
    }

    public abstract class WithWritePermission {
        abstract void whileHavingWritePermission();
    }

    /**
     * Execute user defined Java code while having temporary write permissions on a file using chmod
     * 777
     * 
     * @param file
     * @param withWritePermission
     * @throws BrokenBusyboxException
     * @throws TimeoutException
     * @throws IOException
     */
    public void withWritePermissions(String file, WithWritePermission withWritePermission)
            throws BrokenBusyboxException, TimeoutException, IOException {

        String oldPermissions = getFilePermissions(file);

        // set write permissions for everyone, then Dalvik VM can also write to that file!
        setFilePermissions(file, "777");

        // execute user defined code
        withWritePermission.whileHavingWritePermission();

        // set back to old permissions
        setFilePermissions(file, oldPermissions);
    }

    /**
     * This will take a path, which can contain the file name as well, and attempt to remount the
     * underlying partition.
     * 
     * For example, passing in the following string:
     * "/system/bin/some/directory/that/really/would/never/exist" will result in /system ultimately
     * being remounted. However, keep in mind that the longer the path you supply, the more work
     * this has to do, and the slower it will run.
     * 
     * @param file
     *            file path
     * @param mountType
     *            mount type: pass in RO (Read only) or RW (Read Write)
     * @return a <code>boolean</code> which indicates whether or not the partition has been
     *         remounted as specified.
     */
    public boolean remount(String file, String mountType) {
        // Recieved a request, get an instance of Remounter
        Remounter remounter = new Remounter(shell);
        // send the request
        return (remounter.remount(file, mountType));
    }

    /**
     * This will tell you how the specified mount is mounted. rw, ro, etc...
     * 
     * @param The
     *            mount you want to check
     * 
     * @return <code>String</code> What the mount is mounted as.
     * @throws Exception
     *             if we cannot determine how the mount is mounted.
     */
    public String getMountedAs(String path) throws Exception {
        ArrayList<Mount> mounts = Remounter.getMounts();
        if (mounts != null) {
            for (Mount mount : mounts) {
                if (path.contains(mount.getMountPoint().getAbsolutePath())) {
                    Log.d(Constants.TAG, (String) mount.getFlags().toArray()[0]);
                    return (String) mount.getFlags().toArray()[0];
                }
            }

            throw new Exception();
        } else {
            throw new Exception();
        }
    }

    /**
     * Check if there is enough space on partition where target is located
     * 
     * @param size
     *            size of file to put on partition
     * @param target
     *            path where to put the file
     * 
     * @return true if it will fit on partition of target, false if it will not fit.
     */
    public boolean hasEnoughSpaceOnPartition(String target, long size) {
        try {
            // new File(target).getFreeSpace() (API 9) is not working on data partition

            // get directory without file
            String directory = new File(target).getParent().toString();

            StatFs stat = new StatFs(directory);
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            long availableSpace = availableBlocks * blockSize;

            Log.i(Constants.TAG, "Checking for enough space: Target: " + target + ", directory: "
                    + directory + " size: " + size + ", availableSpace: " + availableSpace);

            if (size < availableSpace) {
                return true;
            } else {
                Log.e(Constants.TAG, "Not enough space on partition!");
                return false;
            }
        } catch (Exception e) {
            // if new StatFs(directory) fails catch IllegalArgumentException and just return true as
            // workaround
            Log.e(Constants.TAG, "Problem while getting available space on partition!", e);
            return true;
        }
    }

    /**
     * TODO: Not tested!
     * 
     * @param toggle
     * @throws IOException
     * @throws TimeoutException
     * @throws BrokenBusyboxException
     */
    public void toggleAdbDaemon(boolean toggle) throws BrokenBusyboxException, TimeoutException,
            IOException {
        SimpleCommand disableAdb = new SimpleCommand("setprop persist.service.adb.enable 0",
                "stop adbd");
        SimpleCommand enableAdb = new SimpleCommand("setprop persist.service.adb.enable 1",
                "stop adbd", "sleep 1", "start adbd");

        if (toggle) {
            shell.add(enableAdb).waitForFinish();
        } else {
            shell.add(disableAdb).waitForFinish();
        }
    }

}
