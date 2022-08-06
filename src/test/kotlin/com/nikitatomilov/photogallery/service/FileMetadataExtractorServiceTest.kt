package com.nikitatomilov.photogallery.service

import com.nikitatomilov.photogallery.dao.FilesystemMediaEntity
import org.junit.jupiter.api.Test
import java.io.File

class FileMetadataExtractorServiceTest {

  private val service = FileMetadataExtractorService()

  @Test
  fun `parses data correctly 1`() {
    //given/when
    val a = service.extractTimestamp(getFile("2021/DSC_6299.MP4")) //original
    val b = service.extractTimestamp(getFile("2021/DSC_6299_2.MP4")) //dump from Google Photos via by-year
    val c = service.extractTimestamp(getFile("2021/DSC_6299_3.MP4")) //dump from Google Photos via shared-album
    //then
    val i = 1
  }

  private fun getFile(name: String): File {
    return File("/opt/test-files-for-photogallery", name)
  }
}