package com.nikitatomilov.photogallery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.nikitatomilov.photogallery.dao.MediaEntity
import com.nikitatomilov.photogallery.dto.AccessRules
import com.nikitatomilov.photogallery.dto.AccessRulesForUser
import com.nikitatomilov.photogallery.util.contains
import com.nikitatomilov.photogallery.util.pathWithoutName
import mu.KLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import javax.annotation.PostConstruct
import kotlin.io.path.exists

@Service
class AccessRulesService(
  @Value("\${lib.rulesLocation}") private val accessRulesFile: String
) {

  //Allow all by default unless the email is in the rules list; then - whitelist only
  private lateinit var rules: AccessRules

  @PostConstruct
  fun readRulesFile() {
    val rulesFile = File(accessRulesFile).toPath()
    if (!rulesFile.exists()) {
      logger.warn { "No rules file found, whitelisting for all users" }
      rules = AccessRules(emptyList())
    }
    rules = ObjectMapper(YAMLFactory()).registerKotlinModule()
        .readValue(Files.readString(rulesFile.toAbsolutePath()))
  }

  fun isAllowed(email: String, e: MediaEntity): Boolean {
    return isAllowed(email, e.asFile())
  }

  fun isAllowed(email: String, file: File): Boolean {
    val rules = getRules(email) ?: return true
    rules.folderWhitelist.forEach {
      val whitelistedDir = File(it)
      if (whitelistedDir.contains(file)) return true
      if (file.isDirectory) {
        if (file.contains(whitelistedDir)) return true
      }
    }
    return false
  }

  private fun getRules(email: String): AccessRulesForUser? {
    return rules.rules.firstOrNull { it.email == email }
  }

  companion object : KLogging()
}