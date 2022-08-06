package com.nikitatomilov.photogallery.dto

data class YearWithContentsDto(
  val year: Int,
  val months: List<MonthWithContentsDto>
) {

  companion object {
    fun empty() = YearWithContentsDto(0, emptyList())
  }
}