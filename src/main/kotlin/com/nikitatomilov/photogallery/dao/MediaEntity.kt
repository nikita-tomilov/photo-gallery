package com.nikitatomilov.photogallery.dao

import java.io.File
import java.time.Instant
import javax.persistence.*

@Entity
data class MediaEntity(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null,

  @Column(nullable = false) var fileName: String,
  @Column(nullable = false) var fullPath: String,
  @Column(nullable = false) var parsedDate: Long,
  @Column(nullable = true) var overrideDate: Long? = null,
  @Column(nullable = false) var isBroken: Boolean = false
) {

  fun getDate(): Long {
    val override = overrideDate
    if (override != null) return override
    return parsedDate
  }

  fun getInstant(): Instant = Instant.ofEpochMilli(getDate())

  fun asFile(): File = File(this.fullPath)
}