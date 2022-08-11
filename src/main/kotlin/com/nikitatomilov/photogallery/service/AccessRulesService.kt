package com.nikitatomilov.photogallery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.nikitatomilov.photogallery.dao.MediaEntity
import com.nikitatomilov.photogallery.dto.AccessRules
import com.nikitatomilov.photogallery.dto.AccessRulesForUser
import com.nikitatomilov.photogallery.dto.Rule
import com.nikitatomilov.photogallery.util.contains
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

  private lateinit var rules: AccessRules

  @PostConstruct
  fun readRulesFile() {
    val rulesFile = File(accessRulesFile).toPath()
    if (!rulesFile.exists()) {
      logger.warn { "No rules file found, allowing all for all users" }
      rules = AccessRules(Rule.ALLOW, emptyList())
    }
    rules = ObjectMapper(YAMLFactory()).registerKotlinModule()
        .readValue(Files.readString(rulesFile.toAbsolutePath()))
  }

  fun getRules() = rules.copy()

  fun isAllowed(email: String, e: MediaEntity): Boolean {
    return isAllowed(email, e.asFile())
  }

  fun isAllowed(email: String, file: File): Boolean {
    val userRules = getRules(email) ?: return rules.default()
    if (isInList(file, userRules.folderBlacklist, false)) return false
    if (isInList(file, userRules.folderWhitelist, true)) return true
    return userRules.default()
  }

  private fun isInList(file: File, list: List<String>, reverseLookup: Boolean): Boolean {
    list.forEach {
      val dir = File(it)
      if (dir.contains(file)) return true
      if (file.isDirectory && reverseLookup) {
        if (file.contains(dir)) return true
      }
    }
    return false
  }

  private fun getRules(email: String): AccessRulesForUser? {
    return rules.rules.firstOrNull { it.email == email }
  }

  companion object : KLogging()
}