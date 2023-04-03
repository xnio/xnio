/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xnio.nio.test;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xnio.FileChangeCallback;
import org.xnio.FileChangeEvent;
import org.xnio.FileSystemWatcher;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.xnio.FileChangeEvent.Type.MODIFIED;
import static org.xnio.FileChangeEvent.Type.REMOVED;

/**
 * Test file system watcher, non poll based
 *
 * @author Stuart Douglas
 */
public class FileSystemWatcherTestCase {
    public static final String DIR_NAME = "/fileSystemWatcherTest";
    public static final String EXISTING_FILE_NAME = "a.txt";
    public static final String EXISTING_DIR = "existingDir";

    private static final int WAIT_SECONDS = 20;
    private static final int NUM_THREADS = 5;
    private static final int NUM_FILES = 6;

    private final BlockingDeque<Collection<FileChangeEvent>> results = new LinkedBlockingDeque<>();
    private final BlockingDeque<Collection<FileChangeEvent>> secondResults = new LinkedBlockingDeque<>();

    File rootDir;
    File existingSubDir;

    protected AcceptingChannel<? extends ConnectedStreamChannel> server;

    private Xnio createXnio() {
        return Xnio.getInstance("nio", FileSystemWatcherTestCase.class.getClassLoader());
    }

    @Before
    public void setup() throws Exception {

        rootDir = new File(System.getProperty("java.io.tmpdir") + DIR_NAME);
        deleteRecursive(rootDir);

        rootDir.mkdirs();
        File existing = new File(rootDir, EXISTING_FILE_NAME);
        touchFile(existing);
        existingSubDir = new File(rootDir, EXISTING_DIR);
        existingSubDir.mkdir();
        existing = new File(existingSubDir, EXISTING_FILE_NAME);
        touchFile(existing);
    }

    private static void touchFile(File existing) throws IOException {
        FileOutputStream out = new FileOutputStream(existing);
        try {
            out.write(("data" + System.currentTimeMillis()).getBytes());
            out.flush();
        } finally {
            IoUtils.safeClose(out);
        }
    }

    @After
    public void after() {
        deleteRecursive(rootDir);
    }

    @Test
    public void testFileSystemWatcher() throws Exception {
        try (FileSystemWatcher watcher = createXnio().createFileSystemWatcher("testWatcher", OptionMap.create(Options.WATCHER_POLL_INTERVAL, 10))) {
            watcher.watchPath(rootDir, new FileChangeCallback() {
                @Override
                public void handleChanges(Collection<FileChangeEvent> changes) {
                    results.add(changes);
                }
            });
            watcher.watchPath(rootDir, new FileChangeCallback() {
                @Override
                public void handleChanges(Collection<FileChangeEvent> changes) {
                    secondResults.add(changes);
                }
            });
            //first add a file
            File added = new File(rootDir, "newlyAddedFile.txt").getAbsoluteFile();
            touchFile(added);
            checkResult(added, FileChangeEvent.Type.ADDED);
            added.setLastModified(500);
            checkResult(added, MODIFIED);
            added.delete();
            Thread.sleep(1);
            checkResult(added, REMOVED);
            added = new File(existingSubDir, "newSubDirFile.txt");
            touchFile(added);
            checkResult(added, FileChangeEvent.Type.ADDED);
            added.setLastModified(500);
            checkResult(added, MODIFIED);
            added.delete();
            Thread.sleep(1);
            checkResult(added, REMOVED);
            File existing = new File(rootDir, EXISTING_FILE_NAME);
            existing.delete();
            Thread.sleep(1);
            checkResult(existing, REMOVED);
            File newDir = new File(rootDir, "newlyCreatedDirectory");
            newDir.mkdir();
            checkResult(newDir, FileChangeEvent.Type.ADDED);
            added = new File(newDir, "newlyAddedFileInNewlyAddedDirectory.txt").getAbsoluteFile();
            touchFile(added);
            checkResult(added, FileChangeEvent.Type.ADDED);
            added.setLastModified(500);
            checkResult(added, MODIFIED);
            added.delete();
            Thread.sleep(1);
            checkResult(added, REMOVED);
        }
        results.clear();
        secondResults.clear();
    }

    @Test
    public void testMultiThread() throws Exception {
        try (FileSystemWatcher watcher = createXnio().createFileSystemWatcher(
                "testWatcher", OptionMap.create(Options.WATCHER_POLL_INTERVAL, 10))) {
            watcher.watchPath(rootDir, new FileChangeCallback() {
                @Override
                public void handleChanges(Collection<FileChangeEvent> changes) {
                    results.add(changes);
                }
            });

            Thread[] array = new Thread[NUM_THREADS];
            for (int i = 0; i< array.length; i++) {
                array[i] = new Thread(new FileAdder(i));
                array[i].start();
            }

            // mark each file received in a set
            Set<String> files = new HashSet<>(NUM_THREADS * NUM_FILES);
            // get changes until all the adds are in the set
            Collection<FileChangeEvent> events = this.results.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            while (files.size() < NUM_THREADS * NUM_FILES && events != null) {
                for (FileChangeEvent e : events) {
                    if (e.getType() == FileChangeEvent.Type.ADDED) {
                        files.add(e.getFile().getName());
                    }
                }
                if (files.size() < NUM_THREADS * NUM_FILES) {
                    events = this.results.poll(WAIT_SECONDS, TimeUnit.SECONDS);
                }
            }
            // check the files created are all received
            for (int i = 0; i < NUM_THREADS; i++) {
                for (int j = 0; j < NUM_FILES; j++) {
                    Assert.assertTrue("Add for file [" + i + "," + j + "] was not received",
                            files.contains("thread-" + i + "-" + j));
                }
            }
        }
        results.clear();
    }

    private void checkResult(File file, FileChangeEvent.Type type) throws InterruptedException {
        Assert.assertTrue("File " + file + " operation " + type + " not received in results", checkResult(file, type, results));
        Assert.assertTrue("File " + file + " operation " + type + " not received in secondResults", checkResult(file, type, secondResults));
    }

    private static boolean checkResult(File file, FileChangeEvent.Type type, BlockingDeque<Collection<FileChangeEvent>> deque) throws InterruptedException {
        // sometime OS will give a MODIFIED event on its parent folder when a file is ADDED
        // consume all extra events until the expected one is received
        Collection<FileChangeEvent> events = deque.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        while (events != null) {
            for (FileChangeEvent e : events) {
                if (file.equals(e.getFile()) && type == e.getType()) {
                    return true;
                }
            }
            events = deque.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        }
        return false;
    }

    public static void deleteRecursive(final File file) {
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                deleteRecursive(f);
            }
        }
        file.delete();
    }

    /**
     * Helper runnable to create NUM_FILES files in the working directory with
     * the name: "thread-" + i + "-" + j. Where i is thread number and j the
     * iteration [0-NUM_FILES). Between each file creation the thread waits a
     * random time [0-100ms).
     */
    class FileAdder implements Runnable {

        private final int number;
        private final Random random;

        FileAdder(int number) {
            this.number = number;
            this.random = new Random();
        }

        @Override
        public void run() {
            for (int j = 0; j < NUM_FILES; j++) {
                try {
                    Path added = rootDir.toPath().resolve("thread-" + number + "-" + j);
                    Files.write(added, added.getFileName().toString().getBytes());
                    final int timeout = random.nextInt(100);
                    if (timeout > 0) {
                        TimeUnit.MILLISECONDS.sleep(timeout);
                    }
                } catch (IOException | InterruptedException e) {
                    Assert.fail("Thread " + number + " failed " + e.getMessage());
                }
            }
        }
    }
}
