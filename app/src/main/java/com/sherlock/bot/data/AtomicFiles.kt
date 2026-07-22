package com.sherlock.bot.data

import java.io.File

/**
 * Best-effort atomic write: temp file + replace. Avoids truncated JSON on crash mid-write.
 */
object AtomicFiles {
    fun writeText(file: File, content: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.${Thread.currentThread().id}.tmp")
        try {
            tmp.writeText(content)
            if (file.exists() && !file.delete()) {
                file.writeText(content)
                return
            }
            if (!tmp.renameTo(file)) {
                file.writeText(content)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
