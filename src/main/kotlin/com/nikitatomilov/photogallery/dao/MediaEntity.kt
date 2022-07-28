package com.nikitatomilov.photogallery.dao

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
)