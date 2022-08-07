package com.nikitatomilov.photogallery.dto

data class AccessRules(
  val rules: List<AccessRulesForUser>
)

data class AccessRulesForUser(
  val email: String,
  val folderWhitelist: List<String>
)