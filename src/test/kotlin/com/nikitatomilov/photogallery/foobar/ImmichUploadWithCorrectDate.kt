package com.nikitatomilov.photogallery.foobar

import com.nikitatomilov.photogallery.dao.TimestampSource
import com.nikitatomilov.photogallery.service.FileMetadataExtractorService
import com.nikitatomilov.photogallery.util.isMediaFile
import org.junit.jupiter.api.Test
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ImmichUploadWithCorrectDate {

  val BASE_URL: String = "http://192.168.123.1:2283/api"
  val API_KEY: String = "ynZPRLTDbrrNDIaVWzfFFI3s8LPWCAL7aHI5TKM6Eo"

  val sourceDir = File("/home/hotaro/Downloads/photos-for-immich-migration/PhotosFixedTimestamps")
  val targetDir =
      File("/home/hotaro/Downloads/photos-for-immich-migration/PhotosFixedTimestampsUploaded")

  val service = FileMetadataExtractorService()

  @Test
  fun upload() {

    // val f = File("/home/hotaro/Downloads/photos-for-immich-migration/PhotosFixedTimestamps/2015/IMG_20150731_102216.jpg")
    // val f1 = upload(f.absolutePath, service.extractTimestamp(f).first)
    // val i = 1

    val sourceFiles = ArrayList<File>()
    sourceDir.walk().forEach {
      if (it.isFile && it.isMediaFile()) {
        sourceFiles.add(it)
        if (sourceFiles.size % 1000 == 0) {
          println("Found ${sourceFiles.size} files and growing")
        }
      }
    }

    println("Found ${sourceFiles.size} files")

    val trustedInformationSets = setOf(TimestampSource.FILE_METADATA, TimestampSource.FILENAME)
    sourceFiles.mapIndexed { index, it ->
      var (timestamp, source) = service.extractTimestamp(it)
      var trustedInfo: Boolean = true

      if (!trustedInformationSets.contains(source)) {
        trustedInfo = false
        val exifInfo = service.extractTimestampExifTool(it)

        val i = 1
        if (exifInfo != null && exifInfo < timestamp) {
          timestamp = exifInfo
          trustedInfo = true
          println(
              "Recovered timestamp from exiftool for ${it.absolutePath}: ${
                Instant.ofEpochMilli(
                    timestamp)
              }")
        }
      }

      if (!trustedInfo) {
        println("Skipped ${it.absolutePath}")
      } else {

        var ans: Pair<Int, String> = 0 to ""
        try {
          ans = upload(it.absolutePath, timestamp)
        println("$index/${sourceFiles.size} ${it.absolutePath} ${ans.first} ${ans.second}")
        } catch (e: Exception) {
          println("Failed to upload ${it.absolutePath}")
          e.printStackTrace()
          ans = -1 to e.toString()
        }

        if (ans.first != 201) {
          val i = 1
        } else {

          val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
          val targetFolder = File(targetDir, date.year.toString())
          targetFolder.mkdirs()

          val targetFile = File(targetFolder, it.name)
          if (targetFile.exists()) {
            println("Target file ${targetFile.absolutePath} already exists")
            val f = it.delete()
            if (!f) {
              println("Failed to delete ${it.absolutePath}")
            }
          } else {
            val f = it.renameTo(targetFile)
            if (!f) {
              println("Failed to move ${it.absolutePath} to ${targetFile.absolutePath}")
            }
          }
        }
      }
    }
  }

  @Throws(IOException::class)
  private fun upload(filePath: String, creationTimestamp: Long): Pair<Int, String> {
    val file = File(filePath)
    if (!file.exists()) {
      throw FileNotFoundException("File not found: $filePath")
    }
    if (file.length() > Int.MAX_VALUE - 1) {
      throw IllegalArgumentException("File is too large: $filePath")
    }

    val formattedTime = DateTimeFormatter.ISO_INSTANT
        .withZone(ZoneId.of("UTC"))
        .format(Instant.ofEpochMilli(creationTimestamp))

    // Prepare headers
    val headers: MutableMap<String, String> = HashMap()
    headers["Accept"] = "application/json"
    headers["x-api-key"] = API_KEY

    // Prepare data
    val boundary = "Boundary-" + System.currentTimeMillis()
    val lineSeparator = "\r\n"
    val dataBuilder = StringBuilder()

    dataBuilder.append("--").append(boundary).append(lineSeparator)
    dataBuilder.append("Content-Disposition: form-data; name=\"deviceAssetId\"")
        .append(lineSeparator)
    dataBuilder.append(lineSeparator)
    dataBuilder.append(file.getName()).append("-").append(creationTimestamp).append(lineSeparator)

    dataBuilder.append("--").append(boundary).append(lineSeparator)
    dataBuilder.append("Content-Disposition: form-data; name=\"deviceId\"").append(lineSeparator)
    dataBuilder.append(lineSeparator)
    dataBuilder.append("java").append(lineSeparator)

    // The provided data will be used if/when it fails to find the date from EXIF
    dataBuilder.append("--").append(boundary).append(lineSeparator)
    dataBuilder.append("Content-Disposition: form-data; name=\"fileCreatedAt\"")
        .append(lineSeparator)
    dataBuilder.append(lineSeparator)
    dataBuilder.append(formattedTime).append(lineSeparator)

    dataBuilder.append("--").append(boundary).append(lineSeparator)
    dataBuilder.append("Content-Disposition: form-data; name=\"fileModifiedAt\"")
        .append(lineSeparator)
    dataBuilder.append(lineSeparator)
    dataBuilder.append(formattedTime).append(lineSeparator)

    dataBuilder.append("--").append(boundary).append(lineSeparator)
    dataBuilder.append("Content-Disposition: form-data; name=\"isFavorite\"").append(lineSeparator)
    dataBuilder.append(lineSeparator)
    dataBuilder.append("false").append(lineSeparator)

    // Prepare file
    val fileBuilder = StringBuilder()
    fileBuilder.append("--").append(boundary).append(lineSeparator)
    fileBuilder.append("Content-Disposition: form-data; name=\"assetData\"; filename=\"")
        .append(file.getName())
        .append("\"")
        .append(lineSeparator)
    fileBuilder.append("Content-Type: application/octet-stream").append(lineSeparator)
    fileBuilder.append(lineSeparator)

    // Open connection
    val url: URL = URL("$BASE_URL/assets")
    val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
    connection.setDoOutput(true)
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

    // Set headers
    for ((key, value) in headers) {
      connection.setRequestProperty(key, value)
    }

    connection.outputStream.use { outputStream ->
      FileInputStream(file).use { fileInputStream ->
        outputStream.write(dataBuilder.toString().toByteArray())
        outputStream.write(fileBuilder.toString().toByteArray())

        val buffer = ByteArray(1024)
        var bytesRead: Int
        while ((fileInputStream.read(buffer).also { bytesRead = it }) != -1) {
          outputStream.write(buffer, 0, bytesRead)
        }
        outputStream.write(lineSeparator.toByteArray())
        outputStream.write("--$boundary--$lineSeparator".toByteArray())
      }
    }
    // Get response
    var ans = StringBuilder()
    val responseCode: Int = connection.getResponseCode()
    (if ((responseCode in 200..299)) connection.inputStream
    else connection.errorStream)
        .use { responseStream ->
          BufferedReader(InputStreamReader(responseStream)).use { reader ->
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
              ans = ans.append(line).append('\n')
            }
          }
        }
    return responseCode to ans.toString()
  }
}