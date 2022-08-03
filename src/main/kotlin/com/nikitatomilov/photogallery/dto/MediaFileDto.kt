package com.nikitatomilov.photogallery.dto

import java.io.File
import java.time.Instant

data class MediaFileDto(
  val id: Long,
  val displayName: String,
  val path: String,
  val timestamp: Long,
  val type: MediaFileTypeDto
) {

  fun getParsedDate(): String = Instant.ofEpochMilli(timestamp).toString()

  companion object {
    fun empty(f: File) = MediaFileDto(-1L, f.name, f.absolutePath, 0L, MediaFileTypeDto.PHOTO)
  }
}

enum class MediaFileTypeDto {
  PHOTO, VIDEO
}