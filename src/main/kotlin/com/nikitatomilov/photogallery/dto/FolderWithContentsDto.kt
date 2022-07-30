package com.nikitatomilov.photogallery.dto

import java.io.File

data class FolderWithContentsDto(
  val current: FolderDto,
  val folders: List<FolderDto>,
  val photos: List<PhotoDto>
) {

  companion object {
    fun empty(f: File) = FolderWithContentsDto(FolderDto(f), emptyList(), emptyList())
  }
}