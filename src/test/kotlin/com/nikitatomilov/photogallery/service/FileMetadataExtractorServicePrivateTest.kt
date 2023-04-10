package com.nikitatomilov.photogallery.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class FileMetadataExtractorServicePrivateTest {

  private val service = FileMetadataExtractorService()

  private val testFiles = Files.readAllLines(getFile("files.csv").toPath()).map {
    val sp = it.split("|")
    sp[0] to sp[1]
  }

  @TestFactory
  fun `parses data correctly`(): Iterable<DynamicTest> {
    return testFiles.map { f -> DynamicTest.dynamicTest(f.first) {
      //given/when
      val extracted =
          service.extractTimestamp(File(f.first))
      val actual = Instant.ofEpochMilli(extracted.first)
      val expected = LocalDateTime.parse(f.second).atOffset(ZoneOffset.UTC).toInstant()
      //then
      assertThat(actual).isEqualTo(expected)
    } }
  }

  private fun getFile(name: String): File {
    return File("/opt/test-files-for-photogallery", name)
  }
}