package com.nikitatomilov.photogallery.dao

import java.io.File
import java.time.Instant

enum class TimestampSource {
  FILENAME, PATH, FILE_METADATA, FILESYSTEM_METADATA
}

data class FilesystemMediaEntity(
  val rootFile: File,
  val file: File,
  var timestamp: Long,
  var timestampSource: TimestampSource
) {

  constructor(rootFile: File, file: File) : this(
      rootFile,
      file,
      file.lastModified(),
      TimestampSource.FILESYSTEM_METADATA)

  override fun toString(): String {
    return file.absolutePath + " @ " + Instant.ofEpochMilli(timestamp) + " via $timestampSource"
  }

  fun withNewTimestamp(timestamp: Long, source: TimestampSource) =
      FilesystemMediaEntity(this.rootFile, this.file, timestamp, source)
}