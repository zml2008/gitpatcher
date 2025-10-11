/*
 * Copyright (c) 2025, Stellardrift and contributors
 * Copyright (c) 2015, Minecrell <https://github.com/Minecrell>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ca.stellardrift.gitpatcher.internal;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.gradle.api.logging.Logger;

public class DefaultGitFactory implements GitFactory {
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();

    @Override
    public Git create(final Path path, final Logger logger) {
        return new Git(path, this.ioExecutor, logger);
    }

    @Override
    public void close() {
        this.ioExecutor.shutdown();
        try {
            if (!this.ioExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                this.ioExecutor.shutdownNow();
            }
        } catch (final InterruptedException ex) {
            this.ioExecutor.shutdownNow();
            throw new RuntimeException("Interrupted during shutdown");
        }
    }
}
