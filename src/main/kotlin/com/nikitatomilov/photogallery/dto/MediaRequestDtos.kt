package com.nikitatomilov.photogallery.dto

import java.io.File

interface MediaRequest

data class FolderRequest(
  val folder: File
) : MediaRequest

data class YearlyRequest(
  val year: Long
) : MediaRequest

fun byBackLink(back: String): MediaRequest {
  if (back.startsWith(BACK_TO_FOLDER_VIEW)) {
    return FolderRequest(File(back.replace(BACK_TO_FOLDER_VIEW, "")))
  }
  return YearlyRequest(back.replace(BACK_TO_YEAR_VIEW, "").toLong())
}

const val BACK_TO_FOLDER_VIEW = "/folder?path="
const val BACK_TO_YEAR_VIEW = "/year/"