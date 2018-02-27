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
import java.util.Collection;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.xnio.FileChangeEvent.Type.ADDED;
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

    private final BlockingDeque<Collection<FileChangeEvent>> results = new LinkedBlockingDeque<Collection<FileChangeEvent>>();
    private final BlockingDeque<Collection<FileChangeEvent>> secondResults = new LinkedBlockingDeque<Collection<FileChangeEvent>>();

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
        FileSystemWatcher watcher = createXnio().createFileSystemWatcher("testWatcher", OptionMap.create(Options.WATCHER_POLL_INTERVAL, 10));
        try {
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


        } finally {
            watcher.close();
        }

    }

    private void checkResult(File file, FileChangeEvent.Type type) throws InterruptedException {
        Collection<FileChangeEvent> results = this.results.poll(20, TimeUnit.SECONDS);
        Collection<FileChangeEvent> secondResults = this.secondResults.poll(20, TimeUnit.SECONDS);
        Assert.assertNotNull(results);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(1, secondResults.size());
        FileChangeEvent res = results.iterator().next();
        FileChangeEvent res2 = secondResults.iterator().next();

        //sometime OS's will give a MODIFIED event before the REMOVED one
        //We consume these events here
        long endTime = System.currentTimeMillis() + 10000;
        while (type == REMOVED
                && (res.getType() == MODIFIED || res2.getType() == MODIFIED)
                && System.currentTimeMillis() < endTime) {
            FileChangeEvent[] nextEvents = consumeEvents();
            res = nextEvents[0];
            res2 = nextEvents[1];
        }

        //sometime OS's will give a MODIFIED event on its parent folder before the ADDED one
        //We consume these events here
        endTime = System.currentTimeMillis() + 10000;
        while (type == ADDED
                && (res.getType() == MODIFIED || res2.getType() == MODIFIED)
                && (res.getFile().equals(file.getParentFile()) || res2.getFile().equals(file.getParentFile()))
                && !file.isDirectory()
                && System.currentTimeMillis() < endTime) {
            FileChangeEvent[] nextEvents = consumeEvents();
            res = nextEvents[0];
            res2 = nextEvents[1];
        }

        Assert.assertEquals(file, res.getFile());
        Assert.assertEquals(type, res.getType());
        Assert.assertEquals(file, res2.getFile());
        Assert.assertEquals(type, res2.getType());
    }

    private FileChangeEvent[] consumeEvents() throws InterruptedException {
        FileChangeEvent[] nextEvents = new FileChangeEvent[2];
        Collection<FileChangeEvent> results = this.results.poll(1, TimeUnit.SECONDS);
        Collection<FileChangeEvent> secondResults = this.secondResults.poll(1, TimeUnit.SECONDS);
        Assert.assertNotNull(results);
        Assert.assertNotNull(secondResults);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(1, secondResults.size());
        nextEvents[0] = results.iterator().next();
        nextEvents[1] = secondResults.iterator().next();

        return nextEvents;
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

}
