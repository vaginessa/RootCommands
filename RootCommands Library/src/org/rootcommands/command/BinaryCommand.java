/*
 * Copyright (C) 2012 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.rootcommands.command;

import java.io.File;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;

public abstract class BinaryCommand extends Command {
    public static final String BINARY_PREFIX = "lib";
    public static final String BINARY_SUFFIX = "_bin.so";

    /**
     * This class provides a way to use your own binaries!
     * 
     * Include your own binaries, renamed from * to lib*_bin.so, in your libs folder under the
     * architecture directories. Now they will be deployed by Android the same way libraries are
     * deployed!
     * 
     * See README for more information how to use your own binaries!
     * 
     * @param context
     * @param binaryName
     * @param parameters
     */
    public BinaryCommand(Context context, String binaryName, String parameters) {
        super(getLibDirectory(context) + File.separator + BINARY_PREFIX + binaryName
                + BINARY_SUFFIX + " " + parameters);
    }

    /**
     * Get full path to lib directory of app
     * 
     * @return dir as String
     */
    @SuppressLint("NewApi")
    private static String getLibDirectory(Context context) {
        if (Build.VERSION.SDK_INT >= 9) {
            return context.getApplicationInfo().nativeLibraryDir;
        } else {
            return context.getApplicationInfo().dataDir + File.separator + "lib";
        }
    }

    public abstract void output(int id, String line);

    public abstract void afterExecution(int id, int exitCode);

}