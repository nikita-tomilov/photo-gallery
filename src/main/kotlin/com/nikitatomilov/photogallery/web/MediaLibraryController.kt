package com.nikitatomilov.photogallery.web

import com.nikitatomilov.photogallery.service.FilesService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
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
    model.addAttribute("folders", contents.folders)
    model.addAttribute("photos", contents.photos)
    model.addAttribute("back", BACK_TO_FOLDER_VIEW + path)
    return "folder"
  }

  @GetMapping("/file")
  fun viewFile(
    @RequestParam("id") id: Long,
    @RequestParam("back") back: String,
    model: Model): String {
    val contents = filesService.getPhotoContent(id, File(back.replace(BACK_TO_FOLDER_VIEW, "")))
    val photoDto = contents.first
    val positionDto = contents.second
    model.addAttribute("photo", photoDto)
    model.addAttribute("back", back)
    model.addAttribute("pos", positionDto)
    return "file"
  }

  companion object {
    private const val BACK_TO_FOLDER_VIEW = "/folder?path="
  }
}