package com.nikitatomilov.photogallery.dto

data class PhotoPositionDto(
  val id: Long,
  val prevId: Long,
  val nextId: Long,
  val position: Int,
  val total: Int
) {
  companion object {
    fun empty(id: Long) = PhotoPositionDto(id, id, id, 1, 1)
  }
}