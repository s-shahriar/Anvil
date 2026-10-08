package com.syed.slate.core

import java.io.File

/**
 * Replaces the file's contents in one step: written to a sibling temp file, synced to disk, then renamed over the
 * original. A crash or kill mid-write leaves the previous version intact instead of a half-written file — which
 * for the offline queues would read as "corrupt" and lose every change that was waiting to sync.
 */
fun File.writeAtomic(text: String) {
    val tmp = File(parentFile, "$name.tmp")
    java.io.FileOutputStream(tmp).use { out -> out.write(text.toByteArray()); out.fd.sync() }
    if (!tmp.renameTo(this)) { delete(); check(tmp.renameTo(this)) { "Could not save $name" } }
}
