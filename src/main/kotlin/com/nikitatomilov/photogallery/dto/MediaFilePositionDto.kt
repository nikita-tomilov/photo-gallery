package com.nikitatomilov.photogallery.dto

data class MediaFilePositionDto(
  val id: Long,
  val prevId: Long,
  val nextId: Long,
  val position: Int,
  val total: Int
) {
  companion object {
    fun empty(id: Long) = MediaFilePositionDto(id, id, id, 1, 1)
  }
}