package com.nikitatomilov.photogallery

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PhotoGalleryApplication

fun main(args: Array<String>) {
	runApplication<PhotoGalleryApplication>(*args)
}
