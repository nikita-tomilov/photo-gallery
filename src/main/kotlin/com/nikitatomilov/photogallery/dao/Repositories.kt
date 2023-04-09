package com.nikitatomilov.photogallery.dao

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface MediaEntityRepository: JpaRepository<MediaEntity, Long> {

  @Query(value = "SELECT e FROM MediaEntity e WHERE e.fileName = ?1")
  fun findByFileName(fileName: String): List<MediaEntity>

  @Query(value = "SELECT e FROM MediaEntity e WHERE e.fullPath = ?1")
  fun findByFullPath(fullPath: String): List<MediaEntity>

  fun findAllByParsedDateBetween(lowerBound: Long, upperBound: Long): List<MediaEntity>

  fun findTop1ByOrderByParsedDate(): MediaEntity

  fun findTop1ByOrderByParsedDateDesc(): MediaEntity

  fun findTop1ByOrderByOverrideDate(): MediaEntity

  fun findTop1ByOrderByOverrideDateDesc(): MediaEntity
}