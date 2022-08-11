package com.nikitatomilov.photogallery.dto

data class AccessRules(
  val defaultAction: Rule,
  val rules: List<AccessRulesForUser>
) {
  fun default(): Boolean = (defaultAction == Rule.ALLOW)
}

data class AccessRulesForUser(
  val email: String,
  val action: Rule,
  val folderWhitelist: List<String>,
  val folderBlacklist: List<String>
) {
  fun default(): Boolean = (action == Rule.ALLOW)
}

enum class Rule {
  ALLOW, DENY
}