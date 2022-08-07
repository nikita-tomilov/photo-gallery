package com.nikitatomilov.photogallery.util

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import java.security.Principal

object SecurityUtils {

  fun extractEmail(principal: Principal?): String? {
    if (principal == null) return null
    if (principal !is OAuth2AuthenticationToken) return null
    return principal.principal.getAttribute<String>("email")
  }
}