package com.nikitatomilov.photogallery.dto

import java.io.File

data class PhotoDto(
  val id: Long,
  val displayName: String,
  val path: String,
  val timestamp: Long
) {
  companion object {
    fun empty(f: File) = PhotoDto(-1L, f.name, f.absolutePath, 0L)
  }
}