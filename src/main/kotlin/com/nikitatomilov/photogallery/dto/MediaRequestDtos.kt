package com.nikitatomilov.photogallery.dto

import java.io.File

interface MediaRequest

data class FolderRequest(
  val email: String,
  val folder: File
) : MediaRequest

data class YearlyRequest(
  val email: String,
  val year: Long
) : MediaRequest

fun byBackLink(email: String, back: String): MediaRequest {
  if (back.startsWith(BACK_TO_FOLDER_VIEW)) {
    return FolderRequest(email, File(back.replace(BACK_TO_FOLDER_VIEW, "")))
  }
  return YearlyRequest(email, back.replace(BACK_TO_YEAR_VIEW, "").toLong())
}

const val BACK_TO_FOLDER_VIEW = "/folder?path="
const val BACK_TO_YEAR_VIEW = "/year/"