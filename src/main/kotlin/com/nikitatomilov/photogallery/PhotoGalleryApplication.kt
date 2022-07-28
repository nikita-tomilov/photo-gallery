package com.nikitatomilov.photogallery

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.PropertySource

@SpringBootApplication
@PropertySource("classpath:application.yml")
class PhotoGalleryApplication

fun main(args: Array<String>) {
  runApplication<PhotoGalleryApplication>(*args)
}
