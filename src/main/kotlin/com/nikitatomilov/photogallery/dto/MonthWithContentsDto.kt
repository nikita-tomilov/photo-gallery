package com.nikitatomilov.photogallery.dto

import java.io.File

data class MonthWithContentsDto(
  val month: Int,
  val displayName: String,
  val files: List<MediaFileDto>
) {

  companion object {
    fun empty(f: File) = MonthWithContentsDto(0, f.name, emptyList())
  }
}