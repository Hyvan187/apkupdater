package org.uniapk.upgrader.glass

/** Foreground queries/checks/installations and the periodic worker share update state. */
object UpdateOperations { val mutex = kotlinx.coroutines.sync.Mutex() }
