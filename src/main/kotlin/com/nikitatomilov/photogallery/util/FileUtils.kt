package com.nikitatomilov.photogallery.util

import java.io.File
import java.lang.Long.min
import java.text.SimpleDateFormat
import java.time.Instant

fun File.isMediaFile() = this.isPhoto() || this.isVideo()
fun File.isPhoto() = this.isFileWithExtension(setOf("jpg", "png")) && this.isNotMacosPreview()
fun File.isVideo() = this.isFileWithExtension(setOf("avi", "mp4")) && this.isNotMacosPreview()
fun File.isSoftSymlink() = this.isFileWithExtension(setOf("symlink")) && this.isNotMacosPreview()
fun File.pathWithoutName() = this.absolutePath.replace(this.name, "")

fun File.isFileWithExtension(extensions: Set<String>): Boolean {
  return (this.isFile) && extensions.contains(this.extension.lowercase())
}

fun File.isNotMacosPreview(): Boolean {
  return !this.name.startsWith("._")
}

fun File.contains(f: File): Boolean {
  if (f.absolutePath == this.absolutePath) return true
  if (f.parentFile == null) return false
  var cur = f
  while (cur != cur.parentFile) {
    if (cur.absolutePath == this.absolutePath) return true
    if (cur.parentFile == null) break
    cur = cur.parentFile
  }
  return false
}

val lowerThreshold = Instant.parse("2000-01-01T00:00:00.00Z").toEpochMilli()

val filenameRegexes: Map<Regex, (String) -> Long> =
    mapOf(
        Regex("\\d{4}.?\\d{2}.?\\d{2}.?\\d{2}.?\\d{2}.?\\d{2}") to {
          SimpleDateFormat("yyyyMMddHHmmss").parse(it.removeNonDigits()).toInstant().toEpochMilli()
        },
        Regex("\\d{4}.?\\d{2}.?\\d{2}") to {
          SimpleDateFormat("yyyyMMdd").parse(it.removeNonDigits()).toInstant()
              .plusSeconds(60 * 60 * 24 - 1).toEpochMilli()
        },
        Regex("\\d{2}.?\\d{2}.?\\d{2}") to {
          SimpleDateFormat("yyMMdd").parse(it.removeNonDigits()).toInstant()
              .plusSeconds(60 * 60 * 24 - 1).toEpochMilli()
        }
    )

val filenameRegexesInverted: Map<Regex, (String) -> Long> =
    mapOf(
        Regex("\\d{2}.?\\d{2}.?\\d{2}") to {
          SimpleDateFormat("ddMMyy").parse(it.removeNonDigits()).toInstant()
              .plusSeconds(60 * 60 * 24 - 1).toEpochMilli()
        }
    )

fun nullableMin(x: Long, y: Long?): Long {
  if (y == null) return x
  return min(x, y)
}

fun String.removeNonDigits() = this.filter { it in '0'..'9' }

fun <T> List<T>.batched(batchesCount: Int): List<List<T>> {
  if (batchesCount == 1) return listOf(this)
  var chunkSize = this.size / batchesCount + 1
  var ans = this.chunked(chunkSize)
  while (ans.size != batchesCount) {
    chunkSize += 1
    ans = this.chunked(chunkSize)
  }
  return ans
}