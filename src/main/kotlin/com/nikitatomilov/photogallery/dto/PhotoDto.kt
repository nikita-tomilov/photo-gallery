package com.nikitatomilov.photogallery.dto

import java.io.File

data class PhotoDto(
  val displayName: String,
  val path: String
) {
  constructor(f: File): this(f.name, f.absolutePath)
}