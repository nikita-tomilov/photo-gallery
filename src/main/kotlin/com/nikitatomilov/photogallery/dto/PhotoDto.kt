package com.nikitatomilov.photogallery.dto

import java.io.File
import java.time.Instant

data class PhotoDto(
  val id: Long,
  val displayName: String,
  val path: String,
  val timestamp: Long
) {

  fun getParsedDate(): String = Instant.ofEpochMilli(timestamp).toString()

  companion object {
    fun empty(f: File) = PhotoDto(-1L, f.name, f.absolutePath, 0L)
  }
}