package com.nikitatomilov.photogallery.web

import com.nikitatomilov.photogallery.dto.BACK_TO_FOLDER_VIEW
import com.nikitatomilov.photogallery.dto.BACK_TO_YEAR_VIEW
import com.nikitatomilov.photogallery.dto.MediaFileTypeDto
import com.nikitatomilov.photogallery.dto.byBackLink
import com.nikitatomilov.photogallery.service.FilesService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.io.File

@Controller
class MediaLibraryController(
  @Autowired private val filesService: FilesService
) {

  @GetMapping("/")
  fun viewRoot(model: Model): String {
    val folders = filesService.getRootDirs()
    model.addAttribute("folders", folders)
    return "root"
  }

  @GetMapping("/folder")
  fun viewFolder(@RequestParam("path") path: String, model: Model): String {
    val contents = filesService.getFolderContent(File(path))
    model.addAttribute("cur", contents.current)
    model.addAttribute("parent", contents.parent)
    model.addAttribute("folders", contents.folders)
    model.addAttribute("files", contents.files)
    model.addAttribute("back", BACK_TO_FOLDER_VIEW + path)
    return "folder"
  }

  @GetMapping("/year/{year}")
  fun viewYear(@PathVariable("year") year: Long, model: Model): String {
    val contents = filesService.getYearContent(year)
    model.addAttribute("cur", contents.current)
    model.addAttribute("parent", contents.parent)
    model.addAttribute("folders", contents.folders)
    model.addAttribute("files", contents.files)
    model.addAttribute("back", BACK_TO_YEAR_VIEW + year.toString())
    return "folder"
  }

  @GetMapping("/file")
  fun viewFile(
    @RequestParam("id") id: Long,
    @RequestParam("back") back: String,
    model: Model
  ): String {
    val contents = filesService.getPhotoContent(id, byBackLink(back))
    val fileDto = contents.first
    val positionDto = contents.second
    model.addAttribute("back", back)
    model.addAttribute("pos", positionDto)
    if (fileDto.type == MediaFileTypeDto.VIDEO) {
      model.addAttribute("video", fileDto)
      return "file-video"
    }
    model.addAttribute("photo", fileDto)
    return "file-photo"
  }
}