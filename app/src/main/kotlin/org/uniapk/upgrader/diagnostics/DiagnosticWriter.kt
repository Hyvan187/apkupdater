package org.uniapk.upgrader.diagnostics

import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Droppable ordinary events, but a flush barrier must be enqueued or fail explicitly. */
internal class DiagnosticWriter(capacity: Int = 256) : Closeable {
    private val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(capacity), { task -> Thread(task, "app-diagnostics").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()).apply { prestartCoreThread() }

    fun execute(task: () -> Unit): Boolean = try {
        executor.execute(task)
        true
    } catch (_: RejectedExecutionException) { false }

    fun flush(timeoutMillis: Long = 5_000) = barrier(timeoutMillis) {}

    fun barrier(timeoutMillis: Long, task: () -> Unit) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val future = FutureTask<Unit> { task() }
        if (!executor.queue.offer(future, timeoutMillis, TimeUnit.MILLISECONDS)) {
            throw TimeoutException("Diagnostic writer queue is busy")
        }
        future.get((deadline - System.nanoTime()).coerceAtLeast(1), TimeUnit.NANOSECONDS)
    }

    override fun close() { executor.shutdownNow() }
}
