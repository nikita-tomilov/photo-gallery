package com.nikitatomilov.photogallery.web

import com.nikitatomilov.photogallery.dto.BACK_TO_FOLDER_VIEW
import com.nikitatomilov.photogallery.dto.BACK_TO_YEAR_VIEW
import com.nikitatomilov.photogallery.dto.MediaFileTypeDto
import com.nikitatomilov.photogallery.dto.byBackLink
import com.nikitatomilov.photogallery.service.FilesService
import com.nikitatomilov.photogallery.util.NotFoundException
import com.nikitatomilov.photogallery.util.SecurityUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.io.File
import java.security.Principal

@Controller
class MediaLibraryController(
  @Autowired private val filesService: FilesService
) {

  @GetMapping("/")
  fun viewRoot(model: Model, principal: Principal): String {
    val email = SecurityUtils.extractEmailOrThrowException(principal)
    val folders = filesService.getRootDirs(email)
    val years = filesService.getYears(email)
    model.addAttribute("email", email)
    model.addAttribute("folders", folders)
    model.addAttribute("years", years)
    return "root"
  }

  @GetMapping("/folder")
  fun viewFolder(@RequestParam("path") path: String, model: Model, principal: Principal): String {
    val email = SecurityUtils.extractEmailOrThrowException(principal) ?: throw NotFoundException()
    val contents = filesService.getFolderContent(email, File(path))
    model.addAttribute("cur", contents.current)
    model.addAttribute("parent", contents.parent)
    model.addAttribute("folders", contents.folders)
    model.addAttribute("files", contents.files)
    model.addAttribute("back", BACK_TO_FOLDER_VIEW + path)
    return "folder"
  }

  @GetMapping("/year/{year}")
  fun viewYear(@PathVariable("year") year: Long, model: Model, principal: Principal): String {
    val email = SecurityUtils.extractEmailOrThrowException(principal)
    val contents = filesService.getYearContent(email, year)
    model.addAttribute("year", contents.year)
    model.addAttribute("months", contents.months)

    model.addAttribute("back", BACK_TO_YEAR_VIEW + year.toString())
    return "year"
  }

  @GetMapping("/file")
  fun viewFile(
    @RequestParam("id") id: Long,
    @RequestParam("back") back: String,
    model: Model,
    principal: Principal
  ): String {
    val email = SecurityUtils.extractEmailOrThrowException(principal)
    val contents = filesService.getPhotoContent(email, id, byBackLink(email, back))
    val fileDto = contents.first
    val positionDto = contents.second
    model.addAttribute("back", back)
    model.addAttribute("pos", positionDto)
    model.addAttribute("file", fileDto)
    model.addAttribute("type", if (fileDto.type == MediaFileTypeDto.VIDEO) "VID" else "IMG")
    return "file"
  }
}