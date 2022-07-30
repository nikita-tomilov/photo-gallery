package com.nikitatomilov.photogallery.dao

import org.springframework.data.jpa.repository.JpaRepository

interface MediaEntityRepository: JpaRepository<MediaEntity, Long> {

  fun findAllByFileName(fileName: String): List<MediaEntity>

  fun findAllByFullPath(fileName: String): List<MediaEntity>

  fun findAllByParsedDateBetween(lowerBound: Long, upperBound: Long): List<MediaEntity>
}