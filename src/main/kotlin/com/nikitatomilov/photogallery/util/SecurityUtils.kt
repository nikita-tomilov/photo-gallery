package com.nikitatomilov.photogallery.util

import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.web.bind.annotation.ResponseStatus
import java.security.Principal

object SecurityUtils {

  fun extractEmail(principal: Principal?): String? {
    if (principal == null) return null
    if (principal !is OAuth2AuthenticationToken) return null
    return principal.principal.getAttribute<String>("email")
  }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class NotFoundException : RuntimeException()