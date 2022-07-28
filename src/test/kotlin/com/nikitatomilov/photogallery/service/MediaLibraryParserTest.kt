package com.nikitatomilov.photogallery.service

import java.io.File
import java.nio.file.Files
import kotlin.system.exitProcess

class MediaLibraryParserTest {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      if (args.isEmpty()) {
        println("Usage: app <fromDirectory>")
        exitProcess(2)
      }
      val fromDirs = args.map { File(it) }
      val mp = MediaLibraryParser()
      val byYear = mp.parse(fromDirs)
      val years = byYear.keys.toList().sorted()
      years.forEach { year ->
        val photosPerYear = byYear[year]!!
        println("$year - ${photosPerYear.size} files")
        if (photosPerYear.size < 4) {
          photosPerYear.forEach { println(" - $it") }
        }
        Files.write(File("/tmp/$year.txt").toPath(), photosPerYear.map { it.toString() })
      }
    }
  }
}